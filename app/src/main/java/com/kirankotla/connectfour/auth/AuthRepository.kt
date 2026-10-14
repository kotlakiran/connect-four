package com.kirankotla.connectfour.auth

import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val authState: StateFlow<AuthState>
    val currentUserId: String?

    suspend fun checkInitialAuth()
    suspend fun signInWithEmail(email: String, password: String): Result<PlayerProfile>
    suspend fun signUpWithEmail(email: String, password: String, displayName: String): Result<PlayerProfile>
    suspend fun signInWithGoogleCredential(idToken: String): Result<PlayerProfile>
    suspend fun signInAsGuest(): Result<PlayerProfile>
    suspend fun signOut()
    suspend fun updateProfile(displayName: String, avatarId: String): Result<Unit>
    suspend fun setOnlineStatus(status: OnlineStatus)
    suspend fun recordGameResult(isWin: Boolean, scoreDelta: Int, achievementsUnlocked: List<String>)
}
