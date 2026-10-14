package com.kirankotla.connectfour.rules

import com.kirankotla.connectfour.model.Box
import com.kirankotla.connectfour.model.GameState
import com.kirankotla.connectfour.model.MoveRecord

object MoveInsights {

    fun boxesGivenAway(state: GameState, edgeId: String): Int {
        val col = edgeId.removePrefix("col_").toIntOrNull() ?: return 0
        val row = state.getLowestAvailableRow(col) ?: return 0
        val aboveRow = row - 1
        if (aboveRow < 0) return 0
        val opponentId = state.players.firstOrNull { it.id != state.currentPlayer.id }?.id ?: return 0
        val simulated = state.boardCells + ("${row}_$col" to state.currentPlayer.id) + ("${aboveRow}_$col" to opponentId)
        val opponentWin = GameEngine.checkWinningLine(
            boardCells = simulated,
            row = aboveRow,
            col = col,
            playerId = opponentId,
            rows = state.rows,
            cols = state.cols,
            winTarget = state.winTarget
        )
        return if (opponentWin != null) 1 else 0
    }

    fun isDangerousMove(state: GameState, edgeId: String): Boolean =
        boxesGivenAway(state, edgeId) > 0

    fun hasSafeMove(state: GameState): Boolean =
        GameEngine.getLegalEdges(state).any { boxesGivenAway(state, it) == 0 }

    fun shouldWarn(state: GameState, edgeId: String): Boolean =
        isDangerousMove(state, edgeId) && hasSafeMove(state)

    fun trailingChainCount(history: List<MoveRecord>): Int = 0

    fun rebuildHistory(
        rows: Int,
        cols: Int,
        orderedEdges: List<String>,
        playerIdForEdge: (index: Int, edgeId: String) -> Int
    ): List<MoveRecord> {
        return orderedEdges.mapIndexed { index, edgeId ->
            val col = edgeId.removePrefix("col_").toIntOrNull() ?: 0
            MoveRecord(
                edgeId = edgeId,
                playerId = playerIdForEdge(index, edgeId),
                claimedBoxIds = listOf(edgeId),
                col = col
            )
        }
    }

    data class PlayerMatchStats(
        val playerId: Int,
        val chipsPlaced: Int,
        val longestStreak: Int,
        val turnsTaken: Int,
        val boxes: Int = chipsPlaced,
        val longestChain: Int = longestStreak,
        val linesDrawn: Int = chipsPlaced,
        val boxesGivenAway: Int = turnsTaken
    )

    fun calculateLongestStreak(boardCells: Map<String, Int>, playerId: Int, rows: Int, cols: Int): Int {
        var maxStreak = 0
        val directions = listOf(
            Pair(0, 1),
            Pair(1, 0),
            Pair(1, 1),
            Pair(-1, 1)
        )
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (boardCells["${r}_$c"] == playerId) {
                    for ((dr, dc) in directions) {
                        var len = 1
                        var nr = r + dr
                        var nc = c + dc
                        while (nr in 0 until rows && nc in 0 until cols && boardCells["${nr}_$nc"] == playerId) {
                            len++
                            nr += dr
                            nc += dc
                        }
                        if (len > maxStreak) {
                            maxStreak = len
                        }
                    }
                }
            }
        }
        return maxStreak
    }

    fun matchStats(state: GameState): Map<Int, PlayerMatchStats> {
        return state.players.associate { player ->
            val chips = state.boardCells.values.count { it == player.id }
            val playerMoves = state.moveHistory.filter { it.playerId == player.id }
            val streak = if (state.winnerId == player.id) {
                maxOf(state.winTarget, calculateLongestStreak(state.boardCells, player.id, state.rows, state.cols))
            } else {
                calculateLongestStreak(state.boardCells, player.id, state.rows, state.cols)
            }
            player.id to PlayerMatchStats(
                playerId = player.id,
                chipsPlaced = chips,
                longestStreak = streak,
                turnsTaken = playerMoves.size
            )
        }
    }
}
