package com.kirankotla.connectfour.ai

import com.kirankotla.connectfour.model.AiDifficulty
import com.kirankotla.connectfour.model.GameState
import com.kirankotla.connectfour.rules.GameEngine
import kotlin.math.abs
import kotlin.random.Random

object AiPlayer {

    fun chooseMove(
        state: GameState,
        difficulty: AiDifficulty = state.aiDifficulty ?: AiDifficulty.MEDIUM,
        timeBudgetMs: Long = 1500L,
        seedRandom: Random? = null
    ): String {
        val legalCols = GameEngine.getLegalColumns(state)
        require(legalCols.isNotEmpty()) { "No legal columns available for AI move" }

        val rng = seedRandom ?: Random

        val chosenCol = when (difficulty) {
            AiDifficulty.BEGINNER -> chooseBeginnerCol(legalCols, rng)
            AiDifficulty.EASY -> chooseEasyCol(state, legalCols, rng)
            AiDifficulty.MEDIUM -> chooseMediumCol(state, legalCols, rng)
            AiDifficulty.HARD -> chooseMinimaxCol(state, legalCols, maxDepth = 4, rng)
            AiDifficulty.EXPERT -> chooseMinimaxCol(state, legalCols, maxDepth = 5, rng)
        }

        return "col_$chosenCol"
    }

    fun getScoringEdges(state: GameState, legalEdges: List<String>): List<String> {
        val myId = state.currentPlayer.id
        return legalEdges.filter { edgeId ->
            val col = edgeId.removePrefix("col_").toIntOrNull() ?: return@filter false
            val r = state.getLowestAvailableRow(col) ?: return@filter false
            val sim = state.boardCells + ("${r}_$col" to myId)
            GameEngine.checkWinningLine(sim, r, col, myId, state.rows, state.cols, state.winTarget) != null
        }
    }

    private fun chooseBeginnerCol(legalCols: List<Int>, rng: Random): Int {
        return legalCols.random(rng)
    }

    private fun chooseEasyCol(state: GameState, legalCols: List<Int>, rng: Random): Int {
        val myId = state.currentPlayer.id
        // 1. Take immediate win
        for (col in legalCols) {
            val r = state.getLowestAvailableRow(col) ?: continue
            val simulated = state.boardCells + ("${r}_$col" to myId)
            if (GameEngine.checkWinningLine(simulated, r, col, myId, state.rows, state.cols, state.winTarget) != null) {
                return col
            }
        }
        return legalCols.random(rng)
    }

    private fun chooseMediumCol(state: GameState, legalCols: List<Int>, rng: Random): Int {
        val myId = state.currentPlayer.id
        val opponentId = state.players.firstOrNull { it.id != myId }?.id ?: 2

        // 1. Check for winning move
        for (col in legalCols) {
            val r = state.getLowestAvailableRow(col) ?: continue
            val simulated = state.boardCells + ("${r}_$col" to myId)
            if (GameEngine.checkWinningLine(simulated, r, col, myId, state.rows, state.cols, state.winTarget) != null) {
                return col
            }
        }

        // 2. Block opponent's immediate win
        for (col in legalCols) {
            val r = state.getLowestAvailableRow(col) ?: continue
            val simulated = state.boardCells + ("${r}_$col" to opponentId)
            if (GameEngine.checkWinningLine(simulated, r, col, opponentId, state.rows, state.cols, state.winTarget) != null) {
                return col
            }
        }

        // 3. Filter out blunders (moves that let opponent win on top)
        val safeCols = legalCols.filter { col ->
            val r = state.getLowestAvailableRow(col) ?: return@filter false
            val aboveR = r - 1
            if (aboveR < 0) true
            else {
                val simBoard = state.boardCells + ("${r}_$col" to myId) + ("${aboveR}_$col" to opponentId)
                GameEngine.checkWinningLine(simBoard, aboveR, col, opponentId, state.rows, state.cols, state.winTarget) == null
            }
        }

        val candidates = if (safeCols.isNotEmpty()) safeCols else legalCols

        // 4. Center column preference
        val center = state.cols / 2
        return candidates.minByOrNull { abs(it - center) } ?: candidates.random(rng)
    }

    private fun chooseMinimaxCol(state: GameState, legalCols: List<Int>, maxDepth: Int, rng: Random): Int {
        val myId = state.currentPlayer.id
        val opponentId = state.players.firstOrNull { it.id != myId }?.id ?: 2

        // Direct win/block checks for speed
        for (col in legalCols) {
            val r = state.getLowestAvailableRow(col) ?: continue
            val sim = state.boardCells + ("${r}_$col" to myId)
            if (GameEngine.checkWinningLine(sim, r, col, myId, state.rows, state.cols, state.winTarget) != null) {
                return col
            }
        }
        for (col in legalCols) {
            val r = state.getLowestAvailableRow(col) ?: continue
            val sim = state.boardCells + ("${r}_$col" to opponentId)
            if (GameEngine.checkWinningLine(sim, r, col, opponentId, state.rows, state.cols, state.winTarget) != null) {
                return col
            }
        }

        // Minimax lookahead
        val center = state.cols / 2
        val sortedCols = legalCols.sortedBy { abs(it - center) }

        var bestScore = Int.MIN_VALUE
        var bestCol = sortedCols.first()

        for (col in sortedCols) {
            val r = state.getLowestAvailableRow(col) ?: continue
            val newBoard = state.boardCells + ("${r}_$col" to myId)
            val score = minimax(
                board = newBoard,
                depth = maxDepth - 1,
                alpha = Int.MIN_VALUE,
                beta = Int.MAX_VALUE,
                isMaximizing = false,
                myId = myId,
                opponentId = opponentId,
                rows = state.rows,
                cols = state.cols,
                winTarget = state.winTarget,
                lastR = r,
                lastC = col
            )
            if (score > bestScore) {
                bestScore = score
                bestCol = col
            }
        }

        return bestCol
    }

    private fun minimax(
        board: Map<String, Int>,
        depth: Int,
        alpha: Int,
        beta: Int,
        isMaximizing: Boolean,
        myId: Int,
        opponentId: Int,
        rows: Int,
        cols: Int,
        winTarget: Int,
        lastR: Int,
        lastC: Int
    ): Int {
        val lastPlayer = if (isMaximizing) opponentId else myId
        val win = GameEngine.checkWinningLine(board, lastR, lastC, lastPlayer, rows, cols, winTarget)
        if (win != null) {
            return if (lastPlayer == myId) 10000 + depth else -10000 - depth
        }

        if (depth <= 0 || board.size == rows * cols) {
            return evaluateBoard(board, myId, opponentId, rows, cols)
        }

        var curAlpha = alpha
        var curBeta = beta

        val legalCols = (0 until cols).filter { !board.containsKey("0_$it") }
        val center = cols / 2
        val sortedCols = legalCols.sortedBy { abs(it - center) }

        if (isMaximizing) {
            var maxEval = Int.MIN_VALUE
            for (col in sortedCols) {
                val r = (rows - 1 downTo 0).firstOrNull { !board.containsKey("${it}_$col") } ?: continue
                val eval = minimax(
                    board = board + ("${r}_$col" to myId),
                    depth = depth - 1,
                    alpha = curAlpha,
                    beta = curBeta,
                    isMaximizing = false,
                    myId = myId,
                    opponentId = opponentId,
                    rows = rows,
                    cols = cols,
                    winTarget = winTarget,
                    lastR = r,
                    lastC = col
                )
                maxEval = maxOf(maxEval, eval)
                curAlpha = maxOf(curAlpha, eval)
                if (curBeta <= curAlpha) break
            }
            return maxEval
        } else {
            var minEval = Int.MAX_VALUE
            for (col in sortedCols) {
                val r = (rows - 1 downTo 0).firstOrNull { !board.containsKey("${it}_$col") } ?: continue
                val eval = minimax(
                    board = board + ("${r}_$col" to opponentId),
                    depth = depth - 1,
                    alpha = curAlpha,
                    beta = curBeta,
                    isMaximizing = true,
                    myId = myId,
                    opponentId = opponentId,
                    rows = rows,
                    cols = cols,
                    winTarget = winTarget,
                    lastR = r,
                    lastC = col
                )
                minEval = minOf(minEval, eval)
                curBeta = minOf(curBeta, eval)
                if (curBeta <= curAlpha) break
            }
            return minEval
        }
    }

    private fun evaluateBoard(board: Map<String, Int>, myId: Int, opponentId: Int, rows: Int, cols: Int): Int {
        var score = 0
        val centerCol = cols / 2
        for (r in 0 until rows) {
            val cell = board["${r}_$centerCol"]
            if (cell == myId) score += 6
            else if (cell == opponentId) score -= 6
        }
        return score
    }

    fun chooseHumanSimulatedMove(
        state: GameState,
        mistakeRate: Float = 0.14f,
        seedRandom: Random? = null
    ): String {
        val legalCols = GameEngine.getLegalColumns(state)
        require(legalCols.isNotEmpty()) { "No legal columns available for AI move" }
        val rng = seedRandom ?: Random

        val myId = state.currentPlayer.id
        val opponentId = state.players.firstOrNull { it.id != myId }?.id ?: 2

        // Occasional human lapse
        if (rng.nextFloat() < mistakeRate) {
            return "col_${legalCols.random(rng)}"
        }

        return "col_${chooseMediumCol(state, legalCols, rng)}"
    }
}
