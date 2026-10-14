package com.kirankotla.connectfour.leaderboard.model

enum class LeaderboardTimeframe(val displayName: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    ALL_TIME("All-Time")
}

data class LeaderboardEntry(
    val rank: Int,
    val uid: String,
    val displayName: String,
    val avatarId: String = "piko",
    val score: Int,
    val wins: Int,
    val isCurrentUser: Boolean = false
)
