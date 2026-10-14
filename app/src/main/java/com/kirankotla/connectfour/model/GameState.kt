package com.kirankotla.connectfour.model

data class GameState(
    val gameId: String,
    val rows: Int = 6,
    val cols: Int = 7,
    val winTarget: Int = 4,
    val mode: GameMode,
    val turnRule: TurnRule = TurnRule.STANDARD,
    val aiDifficulty: AiDifficulty? = null,
    val players: List<Player>,
    val currentPlayerIndex: Int = 0,
    val boardCells: Map<String, Int> = emptyMap(), // "${r}_${c}" -> playerId
    val claimedEdges: Set<String> = emptySet(), // mirrored moves or legacy compatibility
    val boxOwners: Map<String, Int> = emptyMap(), // mirrored to boardCells
    val winningLine: List<Pair<Int, Int>>? = null, // coordinates of winning run
    val lastDrop: Pair<Int, Int>? = null, // (r, c) of the most recent chip drop
    val diceState: DiceState? = null,
    val isGameOver: Boolean = false,
    val winnerId: Int? = null,
    val isDraw: Boolean = false,
    val scoredLines: Set<String> = emptySet(),
    val playerLinesCount: Map<Int, Int> = emptyMap(),
    val moveHistory: List<MoveRecord> = emptyList(),
    val startedAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
    val powerUpState: PowerUpState? = null
) {
    val totalBoxes: Int get() = rows * cols
    val totalCells: Int get() = rows * cols
    val totalEdges: Int get() = rows * cols
    val remainingEdgesCount: Int get() = totalCells - boardCells.size
    val currentPlayer: Player get() = players[currentPlayerIndex]

    fun isColumnFull(col: Int): Boolean {
        if (col !in 0 until cols) return true
        return boardCells.containsKey("0_$col")
    }

    fun getLowestAvailableRow(col: Int): Int? {
        if (col !in 0 until cols) return null
        for (r in (rows - 1) downTo 0) {
            if (!boardCells.containsKey("${r}_$col")) {
                return r
            }
        }
        return null
    }

    fun getCell(r: Int, c: Int): Int? = boardCells["${r}_$c"]

    fun isEdgeClaimed(edgeId: String): Boolean {
        if (edgeId.startsWith("col_")) {
            val col = edgeId.removePrefix("col_").toIntOrNull() ?: return true
            return isColumnFull(col)
        }
        val col = edgeId.toIntOrNull()
        if (col != null) {
            return isColumnFull(col)
        }
        return claimedEdges.contains(edgeId)
    }
}
