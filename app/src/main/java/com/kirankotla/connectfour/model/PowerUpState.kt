package com.kirankotla.connectfour.model

/** Local/campaign-only joker state; `GameState.powerUpState == null` means classic rules. */
data class PowerUpState(
    /** Player id whose next box-less move keeps the turn, or null. */
    val extraTurnPlayerId: Int? = null,
    val usedJokerIds: List<String> = emptyList()
)
