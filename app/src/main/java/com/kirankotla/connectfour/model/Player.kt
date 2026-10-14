package com.kirankotla.connectfour.model

data class Player(
    val id: Int,
    val name: String,
    val colorHex: String,
    val mascotId: String,
    val score: Int = 0,
    val isAi: Boolean = false
)
