package com.kirankotla.connectfour.rules

import com.kirankotla.connectfour.model.*
import kotlin.random.Random

object GameEngine {

    fun createInitialState(
        gameId: String = "game_${System.currentTimeMillis()}",
        rows: Int = 6,
        cols: Int = 7,
        winTarget: Int = if (cols >= 8 && rows >= 7) 4 else 4,
        mode: GameMode = GameMode.QUICK_PLAY,
        turnRule: TurnRule = TurnRule.STANDARD,
        aiDifficulty: AiDifficulty? = if (mode == GameMode.VS_AI || mode == GameMode.QUICK_PLAY) AiDifficulty.MEDIUM else null,
        players: List<Player> = defaultPlayers(mode)
    ): GameState {
        val effectiveTurnRule = if (mode == GameMode.DICE_MODE && turnRule == TurnRule.STANDARD) TurnRule.MAX_LINES else turnRule
        val effectiveAiDiff = if (aiDifficulty == null && (mode == GameMode.VS_AI || mode == GameMode.QUICK_PLAY || mode == GameMode.DICE_MODE)) AiDifficulty.MEDIUM else aiDifficulty
        val initialDiceState = if (mode == GameMode.DICE_MODE) DiceState() else null
        return GameState(
            gameId = gameId,
            rows = rows,
            cols = cols,
            winTarget = winTarget,
            mode = mode,
            turnRule = effectiveTurnRule,
            aiDifficulty = effectiveAiDiff,
            players = players,
            currentPlayerIndex = 0,
            diceState = initialDiceState
        )
    }

    private val simulatedOpponents = listOf(
        Pair("Alex", "mavi"),
        Pair("Priya", "bolt"),
        Pair("Marcus", "sprout"),
        Pair("Sora", "ember"),
        Pair("Elena", "luna"),
        Pair("Liam", "pixel")
    )

    fun defaultPlayers(mode: GameMode): List<Player> {
        val opponent = simulatedOpponents.random()
        return when (mode) {
            GameMode.VS_AI, GameMode.QUICK_PLAY, GameMode.CAMPAIGN -> listOf(
                Player(id = 1, name = "Player 1", colorHex = "#FF5252", mascotId = "piko"),
                Player(id = 2, name = opponent.first, colorHex = "#06B6D4", mascotId = opponent.second, isAi = true)
            )
            GameMode.PASS_AND_PLAY -> listOf(
                Player(id = 1, name = "Player 1", colorHex = "#FF5252", mascotId = "piko"),
                Player(id = 2, name = "Player 2", colorHex = "#06B6D4", mascotId = "mavi")
            )
            GameMode.DICE_MODE -> listOf(
                Player(id = 1, name = "Player 1", colorHex = "#FF5252", mascotId = "piko"),
                Player(id = 2, name = opponent.first, colorHex = "#06B6D4", mascotId = opponent.second, isAi = true)
            )
        }
    }

    fun rollDice(state: GameState, seedRandom: Random? = null): GameState {
        require(state.mode == GameMode.DICE_MODE) { "Dice roll is only available in Dice Mode" }
        require(!state.isGameOver) { "Cannot roll dice when game is over" }

        val rng = seedRandom ?: Random
        val roll = rng.nextInt(1, 4) // 1 to 3 drop allowance in Dice Mode
        val allowance = minOf(roll, state.remainingEdgesCount)

        return state.copy(
            diceState = DiceState(
                rolledValue = roll,
                remainingAllowance = allowance,
                hasRolledThisTurn = true
            ),
            updatedAtEpochMs = System.currentTimeMillis()
        )
    }

    fun applyMove(state: GameState, edgeId: String): MoveResult {
        require(!state.isGameOver) { "Game is already over" }

        val col = when {
            edgeId.startsWith("col_") -> edgeId.removePrefix("col_").toIntOrNull() ?: 0
            edgeId.toIntOrNull() != null -> edgeId.toInt()
            else -> 0
        }

        require(col in 0 until state.cols) { "Invalid column: $col" }
        val row = state.getLowestAvailableRow(col)
            ?: throw IllegalStateException("Column $col is full")

        if (state.mode == GameMode.DICE_MODE) {
            val dice = state.diceState ?: throw IllegalStateException("Missing dice state")
            require(dice.hasRolledThisTurn) { "Player must roll dice before making a move" }
            require(dice.remainingAllowance > 0) { "No move allowance remaining for this turn" }
        }

        val currentPlayer = state.currentPlayer
        val newBoardCells = state.boardCells + ("${row}_$col" to currentPlayer.id)
        val newClaimedEdges = state.claimedEdges + "col_$col"

        // Check victory according to turn rule (Classic Sudden Death vs Max Lines Battle)
        val isMaxLinesMode = state.turnRule == TurnRule.MAX_LINES
        val allFilled = newBoardCells.size >= state.rows * state.cols || getLegalColumns(state.copy(boardCells = newBoardCells)).isEmpty()

        val isWin: Boolean
        val isGameOver: Boolean
        val isDraw: Boolean
        val winnerId: Int?
        val updatedPlayers: List<Player>
        val displayWinningLine: List<Pair<Int, Int>>?
        val newScoredLines: Set<String>
        val updatedLinesMap: Map<Int, Int>

        if (isMaxLinesMode) {
            val allWinningLines = findAllWinningLinesAt(
                boardCells = newBoardCells,
                row = row,
                col = col,
                playerId = currentPlayer.id,
                rows = state.rows,
                cols = state.cols,
                winTarget = state.winTarget
            )
            val newLines = allWinningLines.filter { line ->
                val key = line.sortedWith(compareBy({ it.first }, { it.second }))
                    .joinToString("-") { "${it.first}_${it.second}" }
                key !in state.scoredLines
            }
            val newKeys = newLines.map { line ->
                line.sortedWith(compareBy({ it.first }, { it.second }))
                    .joinToString("-") { "${it.first}_${it.second}" }
            }.toSet()
            newScoredLines = state.scoredLines + newKeys

            val linesAdded = newKeys.size
            val currentLinesCount = (state.playerLinesCount[currentPlayer.id] ?: 0) + linesAdded
            updatedLinesMap = state.playerLinesCount + (currentPlayer.id to currentLinesCount)

            updatedPlayers = state.players.map { p ->
                if (p.id == currentPlayer.id && linesAdded > 0) {
                    p.copy(score = p.score + (linesAdded * 100))
                } else p
            }

            isGameOver = allFilled
            displayWinningLine = allWinningLines.firstOrNull() ?: state.winningLine

            if (isGameOver) {
                val p1Lines = updatedLinesMap[state.players.getOrNull(0)?.id ?: 1] ?: 0
                val p2Lines = updatedLinesMap[state.players.getOrNull(1)?.id ?: 2] ?: 0
                when {
                    p1Lines > p2Lines -> {
                        winnerId = state.players.getOrNull(0)?.id ?: 1
                        isWin = true
                        isDraw = false
                    }
                    p2Lines > p1Lines -> {
                        winnerId = state.players.getOrNull(1)?.id ?: 2
                        isWin = true
                        isDraw = false
                    }
                    else -> {
                        winnerId = null
                        isWin = false
                        isDraw = true
                    }
                }
            } else {
                winnerId = null
                isWin = false
                isDraw = false
            }
        } else {
            newScoredLines = state.scoredLines
            updatedLinesMap = state.playerLinesCount
            val winningLine = checkWinningLine(
                boardCells = newBoardCells,
                row = row,
                col = col,
                playerId = currentPlayer.id,
                rows = state.rows,
                cols = state.cols,
                winTarget = state.winTarget
            )
            isWin = winningLine != null
            isDraw = !isWin && allFilled
            isGameOver = isWin || isDraw
            winnerId = if (isWin) currentPlayer.id else null
            displayWinningLine = winningLine
            updatedPlayers = state.players.map { p ->
                if (p.id == currentPlayer.id && isWin) p.copy(score = p.score + 100) else p
            }
        }

        var turnRetained = false
        var nextPlayerIndex = state.currentPlayerIndex
        var newDiceState = state.diceState

        if (state.mode == GameMode.DICE_MODE) {
            val currentAllowance = (state.diceState?.remainingAllowance ?: 1) - 1
            if (currentAllowance > 0 && !isGameOver) {
                turnRetained = true
                newDiceState = state.diceState?.copy(remainingAllowance = currentAllowance)
            } else {
                turnRetained = false
                nextPlayerIndex = (state.currentPlayerIndex + 1) % state.players.size
                newDiceState = DiceState(rolledValue = 0, remainingAllowance = 0, hasRolledThisTurn = false)
            }
        } else {
            if (!isGameOver) {
                nextPlayerIndex = (state.currentPlayerIndex + 1) % state.players.size
            }
        }

        val moveRecord = MoveRecord(
            edgeId = "col_$col",
            playerId = currentPlayer.id,
            claimedBoxIds = listOf("${row}_$col"),
            row = row,
            col = col,
            timestampMs = System.currentTimeMillis()
        )

        val newState = state.copy(
            boardCells = newBoardCells,
            claimedEdges = newClaimedEdges,
            boxOwners = newBoardCells, // mirrored for seamless compatibility
            players = updatedPlayers,
            currentPlayerIndex = nextPlayerIndex,
            winningLine = displayWinningLine,
            lastDrop = Pair(row, col),
            diceState = newDiceState,
            isGameOver = isGameOver,
            winnerId = winnerId,
            isDraw = isDraw,
            scoredLines = newScoredLines,
            playerLinesCount = updatedLinesMap,
            moveHistory = state.moveHistory + moveRecord,
            updatedAtEpochMs = System.currentTimeMillis()
        )

        return MoveResult(
            newState = newState,
            claimedBoxes = emptyList(),
            turnRetained = turnRetained,
            isGameOver = isGameOver,
            winningLine = displayWinningLine
        )
    }

    fun findAllWinningLinesAt(
        boardCells: Map<String, Int>,
        row: Int,
        col: Int,
        playerId: Int,
        rows: Int,
        cols: Int,
        winTarget: Int = 4
    ): List<List<Pair<Int, Int>>> {
        val directions = listOf(
            Pair(0, 1),   // Horizontal
            Pair(1, 0),   // Vertical
            Pair(1, 1),   // Diagonal Down-Right (\)
            Pair(-1, 1)   // Diagonal Up-Right (/)
        )
        val result = mutableListOf<List<Pair<Int, Int>>>()

        for ((dr, dc) in directions) {
            val line = mutableListOf(Pair(row, col))
            var r = row + dr
            var c = col + dc
            while (r in 0 until rows && c in 0 until cols && boardCells["${r}_$c"] == playerId) {
                line.add(Pair(r, c))
                r += dr
                c += dc
            }
            r = row - dr
            c = col - dc
            while (r in 0 until rows && c in 0 until cols && boardCells["${r}_$c"] == playerId) {
                line.add(0, Pair(r, c))
                r -= dr
                c -= dc
            }
            if (line.size >= winTarget) {
                for (start in 0..(line.size - winTarget)) {
                    val sub = line.subList(start, start + winTarget)
                    if (Pair(row, col) in sub) {
                        result.add(sub.toList())
                    }
                }
            }
        }
        return result
    }

    fun checkWinningLine(
        boardCells: Map<String, Int>,
        row: Int,
        col: Int,
        playerId: Int,
        rows: Int,
        cols: Int,
        winTarget: Int = 4
    ): List<Pair<Int, Int>>? {
        val directions = listOf(
            Pair(0, 1),   // Horizontal
            Pair(1, 0),   // Vertical
            Pair(1, 1),   // Diagonal Down-Right (\)
            Pair(-1, 1)   // Diagonal Up-Right (/)
        )

        for ((dr, dc) in directions) {
            val line = mutableListOf(Pair(row, col))

            // Forward
            var r = row + dr
            var c = col + dc
            while (r in 0 until rows && c in 0 until cols && boardCells["${r}_$c"] == playerId) {
                line.add(Pair(r, c))
                r += dr
                c += dc
            }

            // Backward
            r = row - dr
            c = col - dc
            while (r in 0 until rows && c in 0 until cols && boardCells["${r}_$c"] == playerId) {
                line.add(0, Pair(r, c))
                r -= dr
                c -= dc
            }

            if (line.size >= winTarget) {
                return line
            }
        }
        return null
    }

    fun endTurnEarlyInDiceMode(state: GameState): GameState {
        require(state.mode == GameMode.DICE_MODE) { "End turn early is only for Dice Mode" }
        val dice = state.diceState ?: return state
        require(dice.hasRolledThisTurn) { "Must roll dice first" }

        val nextPlayerIndex = (state.currentPlayerIndex + 1) % state.players.size
        return state.copy(
            currentPlayerIndex = nextPlayerIndex,
            diceState = DiceState(rolledValue = 0, remainingAllowance = 0, hasRolledThisTurn = false),
            updatedAtEpochMs = System.currentTimeMillis()
        )
    }

    fun getAdjacentBoxesForEdge(rows: Int, cols: Int, edgeId: String): List<Box> {
        val col = edgeId.removePrefix("col_").toIntOrNull() ?: 0
        return listOf(Box(0, col.coerceIn(0, cols - 1)))
    }

    fun getLegalEdges(state: GameState): List<String> =
        (0 until state.cols).filter { !state.isColumnFull(it) }.map { "col_$it" }

    fun getLegalColumns(state: GameState): List<Int> =
        (0 until state.cols).filter { !state.isColumnFull(it) }

    fun selectAiMove(
        state: GameState,
        difficulty: AiDifficulty = state.aiDifficulty ?: AiDifficulty.MEDIUM,
        seedRandom: Random? = null
    ): String {
        return com.kirankotla.connectfour.ai.AiPlayer.chooseMove(state, difficulty = difficulty, seedRandom = seedRandom)
    }

    fun selectHumanSimulatedMove(
        state: GameState,
        mistakeRate: Float = 0.14f,
        seedRandom: Random? = null
    ): String {
        return com.kirankotla.connectfour.ai.AiPlayer.chooseHumanSimulatedMove(state, mistakeRate, seedRandom)
    }
}
