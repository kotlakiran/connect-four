package com.kirankotla.connectfour.auth

data class PlayerProfile(
    val uid: String,
    val displayName: String,
    val email: String? = null,
    val photoUrl: String? = null,
    val avatarId: String = "piko",
    val createdAt: Long = System.currentTimeMillis(),
    val lastActiveAt: Long = System.currentTimeMillis(),
    val gamesPlayed: Int = 0,
    val gamesWon: Int = 0,
    val gamesLost: Int = 0,
    val score: Int = 0,
    val level: Int = 1,
    val achievements: List<String> = emptyList(),
    val onlineStatus: OnlineStatus = OnlineStatus.ONLINE
) {
    val winRatePercent: Int get() = if (gamesPlayed > 0) (gamesWon * 100) / gamesPlayed else 0

    fun toPublicProfile(): PublicPlayerProfile = PublicPlayerProfile(
        uid = uid,
        displayName = displayName,
        photoUrl = photoUrl,
        avatarId = avatarId,
        level = level,
        score = score,
        gamesPlayed = gamesPlayed,
        gamesWon = gamesWon,
        onlineStatus = onlineStatus,
        lastActiveAt = lastActiveAt
    )
}

data class PublicPlayerProfile(
    val uid: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val avatarId: String = "piko",
    val level: Int = 1,
    val score: Int = 0,
    val gamesPlayed: Int = 0,
    val gamesWon: Int = 0,
    val onlineStatus: OnlineStatus = OnlineStatus.OFFLINE,
    val lastActiveAt: Long = 0L
)

enum class OnlineStatus(val label: String) {
    ONLINE("Online"),
    IN_GAME("In Game"),
    OFFLINE("Offline")
}

sealed class AuthState {
    object Unauthenticated : AuthState()
    data class Loading(val message: String = "Signing in...") : AuthState()
    data class Authenticated(val profile: PlayerProfile) : AuthState()
    data class Error(val message: String) : AuthState()
}
