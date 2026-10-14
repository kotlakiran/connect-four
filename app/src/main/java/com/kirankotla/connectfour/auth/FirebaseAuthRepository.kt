package com.kirankotla.connectfour.auth

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.kirankotla.connectfour.analytics.ConnectFourAnalytics
import com.kirankotla.connectfour.config.FirebaseManager
import com.kirankotla.connectfour.data.ProgressionProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

class FirebaseAuthRepository(
    private val context: Context,
    private val progressionProcessor: ProgressionProcessor,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : AuthRepository {

    private val TAG = "FirebaseAuthRepo"
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    override val currentUserId: String?
        get() = when (val state = _authState.value) {
            is AuthState.Authenticated -> state.profile.uid
            else -> FirebaseManager.auth?.currentUser?.uid
        }

    private val auth: FirebaseAuth? get() = FirebaseManager.auth
    private val firestore: FirebaseFirestore? get() = FirebaseManager.firestore

    override suspend fun checkInitialAuth() = withContext(Dispatchers.IO) {
        try {
            val fbAuth = auth
            val currentUser = fbAuth?.currentUser
            if (currentUser != null) {
                _authState.value = AuthState.Loading("Restoring session...")
                val profile = loadOrCreateProfile(currentUser.uid, currentUser.displayName, currentUser.email, currentUser.photoUrl?.toString())
                _authState.value = AuthState.Authenticated(profile)
                setOnlineStatus(OnlineStatus.ONLINE)
                ConnectFourAnalytics.logLogin("session_restore", currentUser.uid)
            } else {
                // Not logged in to Firebase
                _authState.value = AuthState.Unauthenticated
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking initial auth: ${e.message}")
            _authState.value = AuthState.Unauthenticated
        }
    }

    override suspend fun signInWithEmail(email: String, password: String): Result<PlayerProfile> = withContext(Dispatchers.IO) {
        _authState.value = AuthState.Loading("Signing in...")
        val fbAuth = auth ?: return@withContext fallbackLocalSignIn(email)
        try {
            val result = fbAuth.signInWithEmailAndPassword(email, password).await()
            val user = result.user ?: throw IllegalStateException("Firebase user was null")
            val profile = loadOrCreateProfile(user.uid, user.displayName, user.email, user.photoUrl?.toString())
            _authState.value = AuthState.Authenticated(profile)
            setOnlineStatus(OnlineStatus.ONLINE)
            ConnectFourAnalytics.logLogin("email_password", user.uid)
            Result.success(profile)
        } catch (e: Exception) {
            Log.e(TAG, "signInWithEmail failed: ${e.message}")
            _authState.value = AuthState.Error(e.message ?: "Authentication failed")
            Result.failure(e)
        }
    }

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String
    ): Result<PlayerProfile> = withContext(Dispatchers.IO) {
        _authState.value = AuthState.Loading("Creating profile...")
        val fbAuth = auth ?: return@withContext fallbackLocalSignUp(displayName, email)
        try {
            val result = fbAuth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user ?: throw IllegalStateException("Firebase user was null")
            val profile = loadOrCreateProfile(user.uid, displayName, user.email, null)
            _authState.value = AuthState.Authenticated(profile)
            setOnlineStatus(OnlineStatus.ONLINE)
            ConnectFourAnalytics.logLogin("email_signup", user.uid)
            Result.success(profile)
        } catch (e: Exception) {
            Log.e(TAG, "signUpWithEmail failed: ${e.message}")
            _authState.value = AuthState.Error(e.message ?: "Registration failed")
            Result.failure(e)
        }
    }

    override suspend fun signInWithGoogleCredential(idToken: String): Result<PlayerProfile> = withContext(Dispatchers.IO) {
        _authState.value = AuthState.Loading("Verifying Google account...")
        val fbAuth = auth ?: return@withContext Result.failure(IllegalStateException("Firebase is uninitialized"))
        try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = fbAuth.signInWithCredential(credential).await()
            val user = result.user ?: throw IllegalStateException("Google sign-in user was null")
            val profile = loadOrCreateProfile(user.uid, user.displayName, user.email, user.photoUrl?.toString())
            _authState.value = AuthState.Authenticated(profile)
            setOnlineStatus(OnlineStatus.ONLINE)
            ConnectFourAnalytics.logLogin("google_signin", user.uid)
            Result.success(profile)
        } catch (e: Exception) {
            Log.e(TAG, "Google sign in failed: ${e.message}")
            _authState.value = AuthState.Error(e.message ?: "Google sign in failed")
            Result.failure(e)
        }
    }

    override suspend fun signInAsGuest(): Result<PlayerProfile> = withContext(Dispatchers.IO) {
        _authState.value = AuthState.Loading("Setting up guest profile...")
        val fbAuth = auth
        if (fbAuth != null) {
            try {
                val authResult = fbAuth.signInAnonymously().await()
                val user = authResult.user
                if (user != null) {
                    val profile = loadOrCreateProfile(
                        user.uid,
                        "Guest_${user.uid.take(4).uppercase()}",
                        null,
                        null
                    )
                    _authState.value = AuthState.Authenticated(profile)
                    setOnlineStatus(OnlineStatus.ONLINE)
                    ConnectFourAnalytics.logLogin("guest_anonymous", user.uid)
                    return@withContext Result.success(profile)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Anonymous Firebase sign-in failed, fallback to local guest: ${e.message}")
            }
        }
        val guestUid = "guest_${UUID.randomUUID().toString().take(8)}"
        val guestProfile = PlayerProfile(
            uid = guestUid,
            displayName = "Guest_${guestUid.takeLast(4).uppercase()}",
            onlineStatus = OnlineStatus.ONLINE
        )
        _authState.value = AuthState.Authenticated(guestProfile)
        Result.success(guestProfile)
    }

    override suspend fun signOut() = withContext(Dispatchers.IO) {
        try {
            setOnlineStatus(OnlineStatus.OFFLINE)
            auth?.signOut()
        } catch (e: Exception) {
            Log.w(TAG, "Sign out exception: ${e.message}")
        }
        val defaultProfile = PlayerProfile(
            uid = "local_player",
            displayName = "Player 1",
            onlineStatus = OnlineStatus.OFFLINE
        )
        _authState.value = AuthState.Authenticated(defaultProfile)
    }

    override suspend fun updateProfile(displayName: String, avatarId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val current = _authState.value
        if (current is AuthState.Authenticated) {
            val updated = current.profile.copy(displayName = displayName, avatarId = avatarId)
            _authState.value = AuthState.Authenticated(updated)

            // Sync with local Room database
            progressionProcessor.updateProfile(displayName, progressionProcessor.getProfile().age)
            progressionProcessor.updateAvatar(avatarId)

            // Sync with Firestore if logged in
            val db = firestore
            val uid = updated.uid
            if (db != null && uid != "local_player") {
                val updates = mapOf(
                    "displayName" to displayName,
                    "avatarId" to avatarId,
                    "lastActiveAt" to System.currentTimeMillis()
                )
                try {
                    db.collection("users").document(uid).update(updates).await()
                    db.collection("public_profiles").document(uid).update(updates).await()
                } catch (e: Exception) {
                    Log.w(TAG, "Firestore profile update warning: ${e.message}")
                }
            }
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("User not authenticated"))
        }
    }

    override suspend fun setOnlineStatus(status: OnlineStatus) = withContext(Dispatchers.IO) {
        val current = _authState.value
        if (current is AuthState.Authenticated) {
            val updated = current.profile.copy(onlineStatus = status, lastActiveAt = System.currentTimeMillis())
            _authState.value = AuthState.Authenticated(updated)

            val db = firestore
            val uid = updated.uid
            if (db != null && uid != "local_player") {
                try {
                    val map = mapOf(
                        "onlineStatus" to status.name,
                        "lastActiveAt" to System.currentTimeMillis()
                    )
                    db.collection("public_profiles").document(uid).set(map, SetOptions.merge())
                } catch (e: Exception) {
                    Log.w(TAG, "Status update warning: ${e.message}")
                }
            }
        }
    }

    override suspend fun recordGameResult(
        isWin: Boolean,
        scoreDelta: Int,
        achievementsUnlocked: List<String>
    ) = withContext(Dispatchers.IO) {
        val current = _authState.value
        if (current is AuthState.Authenticated) {
            val prof = current.profile
            val newPlayed = prof.gamesPlayed + 1
            val newWon = prof.gamesWon + if (isWin) 1 else 0
            val newLost = prof.gamesLost + if (!isWin) 1 else 0
            val newScore = prof.score + scoreDelta
            val newLevel = maxOf(prof.level, 1 + (newScore / 500))
            val newAchievements = (prof.achievements + achievementsUnlocked).distinct()

            val updated = prof.copy(
                gamesPlayed = newPlayed,
                gamesWon = newWon,
                gamesLost = newLost,
                score = newScore,
                level = newLevel,
                achievements = newAchievements,
                lastActiveAt = System.currentTimeMillis()
            )
            _authState.value = AuthState.Authenticated(updated)

            val db = firestore
            val uid = prof.uid
            if (db != null && uid != "local_player") {
                val userMap = mapOf(
                    "gamesPlayed" to newPlayed,
                    "gamesWon" to newWon,
                    "gamesLost" to newLost,
                    "score" to newScore,
                    "level" to newLevel,
                    "achievements" to newAchievements,
                    "lastActiveAt" to System.currentTimeMillis()
                )
                val publicMap = mapOf(
                    "displayName" to prof.displayName,
                    "avatarId" to prof.avatarId,
                    "gamesPlayed" to newPlayed,
                    "gamesWon" to newWon,
                    "score" to newScore,
                    "level" to newLevel,
                    "onlineStatus" to prof.onlineStatus.name,
                    "lastActiveAt" to System.currentTimeMillis()
                )
                try {
                    db.collection("users").document(uid).set(userMap, SetOptions.merge())
                    db.collection("public_profiles").document(uid).set(publicMap, SetOptions.merge())
                } catch (e: Exception) {
                    Log.w(TAG, "Record game result error: ${e.message}")
                }
            }
        }
    }

    private suspend fun loadOrCreateProfile(
        uid: String,
        displayName: String?,
        email: String?,
        photoUrl: String?
    ): PlayerProfile {
        val db = firestore
        val name = displayName?.ifBlank { "Player" } ?: "Player"

        if (db != null) {
            try {
                val doc = db.collection("users").document(uid).get().await()
                if (doc.exists()) {
                    val profile = PlayerProfile(
                        uid = uid,
                        displayName = doc.getString("displayName") ?: name,
                        email = email ?: doc.getString("email"),
                        photoUrl = photoUrl ?: doc.getString("photoUrl"),
                        avatarId = doc.getString("avatarId") ?: "piko",
                        createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                        lastActiveAt = System.currentTimeMillis(),
                        gamesPlayed = doc.getLong("gamesPlayed")?.toInt() ?: 0,
                        gamesWon = doc.getLong("gamesWon")?.toInt() ?: 0,
                        gamesLost = doc.getLong("gamesLost")?.toInt() ?: 0,
                        score = doc.getLong("score")?.toInt() ?: 0,
                        level = doc.getLong("level")?.toInt() ?: 1,
                        achievements = (doc.get("achievements") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                        onlineStatus = OnlineStatus.ONLINE
                    )
                    progressionProcessor.syncFromCloud(profile)
                    // Keep public profile updated
                    db.collection("public_profiles").document(uid).set(profile.toPublicProfile(), SetOptions.merge())
                    return profile
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not fetch user document: ${e.message}")
            }
        }

        // Create new user profile
        val newProfile = PlayerProfile(
            uid = uid,
            displayName = name,
            email = email,
            photoUrl = photoUrl,
            avatarId = "piko",
            createdAt = System.currentTimeMillis(),
            lastActiveAt = System.currentTimeMillis(),
            gamesPlayed = 0,
            gamesWon = 0,
            gamesLost = 0,
            score = 0,
            level = 1,
            achievements = emptyList(),
            onlineStatus = OnlineStatus.ONLINE
        )

        if (db != null) {
            try {
                val privateMap = mapOf(
                    "uid" to uid,
                    "displayName" to newProfile.displayName,
                    "email" to newProfile.email,
                    "photoUrl" to newProfile.photoUrl,
                    "avatarId" to newProfile.avatarId,
                    "createdAt" to newProfile.createdAt,
                    "lastActiveAt" to newProfile.lastActiveAt,
                    "gamesPlayed" to 0,
                    "gamesWon" to 0,
                    "gamesLost" to 0,
                    "score" to 0,
                    "level" to 1,
                    "achievements" to emptyList<String>()
                )
                db.collection("users").document(uid).set(privateMap, SetOptions.merge())
                db.collection("public_profiles").document(uid).set(newProfile.toPublicProfile(), SetOptions.merge())
            } catch (e: Exception) {
                Log.w(TAG, "Could not write initial profile to Firestore: ${e.message}")
            }
        }

        return newProfile
    }

    private fun fallbackLocalSignIn(email: String): Result<PlayerProfile> {
        val localName = email.substringBefore("@").replaceFirstChar { it.uppercase() }
        val prof = PlayerProfile(
            uid = "local_${UUID.nameUUIDFromBytes(email.toByteArray())}",
            displayName = localName,
            email = email,
            avatarId = "piko",
            onlineStatus = OnlineStatus.ONLINE
        )
        _authState.value = AuthState.Authenticated(prof)
        return Result.success(prof)
    }

    private fun fallbackLocalSignUp(name: String, email: String): Result<PlayerProfile> {
        val prof = PlayerProfile(
            uid = "local_${UUID.randomUUID()}",
            displayName = name,
            email = email,
            avatarId = "piko",
            onlineStatus = OnlineStatus.ONLINE
        )
        _authState.value = AuthState.Authenticated(prof)
        return Result.success(prof)
    }
}
