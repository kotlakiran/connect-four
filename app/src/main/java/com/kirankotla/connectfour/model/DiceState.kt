package com.kirankotla.connectfour.model

data class DiceState(
    val rolledValue: Int = 0,
    val remainingAllowance: Int = 0,
    val hasRolledThisTurn: Boolean = false
)
