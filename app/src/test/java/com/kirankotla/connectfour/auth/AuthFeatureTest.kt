package com.kirankotla.connectfour.auth

import org.junit.Assert.*
import org.junit.Test

class AuthFeatureTest {

    @Test
    fun testPlayerProfile_winRateAndPublicProfileConversion() {
        val profile = PlayerProfile(
            uid = "u_123",
            displayName = "Kiran",
            avatarId = "bolt",
            gamesPlayed = 20,
            gamesWon = 15,
            gamesLost = 5,
            score = 3400,
            level = 4,
            onlineStatus = OnlineStatus.ONLINE
        )

        assertEquals(75, profile.winRatePercent)

        val publicProfile = profile.toPublicProfile()
        assertEquals(profile.uid, publicProfile.uid)
        assertEquals(profile.displayName, publicProfile.displayName)
        assertEquals(profile.avatarId, publicProfile.avatarId)
        assertEquals(profile.level, publicProfile.level)
        assertEquals(profile.score, publicProfile.score)
        assertEquals(profile.gamesPlayed, publicProfile.gamesPlayed)
        assertEquals(profile.gamesWon, publicProfile.gamesWon)
        assertEquals(OnlineStatus.ONLINE, publicProfile.onlineStatus)
    }

    @Test
    fun testPlayerProfile_zeroGamesHandledSafely() {
        val profile = PlayerProfile(
            uid = "u_new",
            displayName = "Newbie",
            gamesPlayed = 0,
            gamesWon = 0
        )

        assertEquals(0, profile.winRatePercent)
    }

    @Test
    fun testAuthState_hierarchy() {
        val unauth: AuthState = AuthState.Unauthenticated
        assertTrue(unauth is AuthState.Unauthenticated)

        val loading: AuthState = AuthState.Loading("Loading profile...")
        assertTrue(loading is AuthState.Loading)
        assertEquals("Loading profile...", (loading as AuthState.Loading).message)

        val error: AuthState = AuthState.Error("Network error")
        assertTrue(error is AuthState.Error)
        assertEquals("Network error", (error as AuthState.Error).message)

        val profile = PlayerProfile(uid = "u1", displayName = "Test")
        val authenticated: AuthState = AuthState.Authenticated(profile)
        assertTrue(authenticated is AuthState.Authenticated)
        assertEquals("u1", (authenticated as AuthState.Authenticated).profile.uid)
    }
}
