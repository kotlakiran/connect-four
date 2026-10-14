package com.kirankotla.connectfour.profile

import com.kirankotla.connectfour.data.AchievementCatalog
import com.kirankotla.connectfour.data.MatchSummaryEntity
import com.kirankotla.connectfour.data.PlayerProfileEntity
import com.kirankotla.connectfour.data.PlayerStatsEntity
import org.junit.Assert.*
import org.junit.Test

class ProfileAndStatsTest {

    @Test
    fun testPlayerProfile_defaultsAndUpdates() {
        var profile = PlayerProfileEntity()
        assertEquals("local_player", profile.profileId)
        assertEquals("Player 1", profile.name)
        assertEquals(18, profile.age)
        assertEquals("piko", profile.avatarId)
        assertEquals(1, profile.level)
        assertEquals(0, profile.lifetimeXp)

        profile = profile.copy(name = "Kiran", avatarId = "bolt", level = 5, lifetimeXp = 2500)
        assertEquals("Kiran", profile.name)
        assertEquals("bolt", profile.avatarId)
        assertEquals(5, profile.level)
        assertEquals(2500, profile.lifetimeXp)
    }

    @Test
    fun testPlayerStats_winRateCalculation() {
        val stats = PlayerStatsEntity(
            gamesStarted = 50,
            gamesCompleted = 40,
            wins = 30,
            losses = 8,
            draws = 2,
            boxesCaptured = 180,
            currentStreak = 4,
            bestStreak = 9
        )

        assertEquals(40, stats.gamesCompleted)
        assertEquals(30, stats.wins)
        
        // Calculate Win Rate percentage
        val winRate = if (stats.gamesCompleted > 0) {
            (stats.wins.toFloat() / stats.gamesCompleted.toFloat()) * 100f
        } else 0f

        assertEquals(75.0f, winRate, 0.01f)
    }

    @Test
    fun testPlayerStats_streakProgression() {
        var stats = PlayerStatsEntity(currentStreak = 2, bestStreak = 5)

        // After another win
        stats = stats.copy(currentStreak = 3, bestStreak = maxOf(3, stats.bestStreak))
        assertEquals(3, stats.currentStreak)
        assertEquals(5, stats.bestStreak)

        // After loss
        stats = stats.copy(currentStreak = 0)
        assertEquals(0, stats.currentStreak)
        assertEquals(5, stats.bestStreak)
    }

    @Test
    fun testAchievementCatalog_containsRequiredAchievements() {
        assertFalse(AchievementCatalog.ALL_ACHIEVEMENTS.isEmpty())
        
        val achievementIds = AchievementCatalog.ALL_ACHIEVEMENTS.map { it.id }.toSet()
        assertTrue(achievementIds.contains("first_line"))
        assertTrue(achievementIds.contains("first_box"))
        assertTrue(achievementIds.contains("first_win"))
        assertTrue(achievementIds.contains("tutorial_grad"))
        assertTrue(achievementIds.contains("streak_3"))
        assertTrue(achievementIds.contains("streak_5"))
        assertTrue(achievementIds.contains("streak_10"))
        assertTrue(achievementIds.contains("perfect_win"))
        assertTrue(achievementIds.contains("double_capture_1"))
        assertTrue(achievementIds.contains("dice_win_1"))
    }

    @Test
    fun testMatchSummary_recordStructure() {
        val summary = MatchSummaryEntity(
            matchId = "match_123",
            mode = "VS_AI",
            rows = 4,
            cols = 4,
            turnRule = "STANDARD",
            aiDifficulty = "HARD",
            result = "WIN",
            userScore = 9,
            opponentScore = 7,
            durationMs = 45000L,
            movesCount = 28,
            chainRecord = 3,
            hintsUsed = 0,
            undosUsed = 0,
            starsEarned = 3
        )

        assertEquals("WIN", summary.result)
        assertEquals(9, summary.userScore)
        assertEquals(7, summary.opponentScore)
        assertEquals(3, summary.starsEarned)
        assertEquals(0, summary.hintsUsed)
    }
}
