package com.kirankotla.connectfour.model

data class MoveRecord(
    val edgeId: String,
    val playerId: Int,
    val claimedBoxIds: List<String> = emptyList(),
    val row: Int = -1,
    val col: Int = -1,
    val timestampMs: Long = System.currentTimeMillis()
)

data class MoveResult(
    val newState: GameState,
    val claimedBoxes: List<Box> = emptyList(),
    val turnRetained: Boolean = false,
    val isGameOver: Boolean = false,
    val winningLine: List<Pair<Int, Int>>? = null
)
