package com.kirankotla.connectfour.leaderboard

import com.kirankotla.connectfour.leaderboard.model.LeaderboardEntry
import com.kirankotla.connectfour.leaderboard.model.LeaderboardTimeframe
import com.kirankotla.connectfour.leaderboard.repository.LeaderboardRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LeaderboardFeatureTest {

    private val repository = LeaderboardRepository()

    @Test
    fun testLeaderboardTimeframes_exist() {
        val timeframes = LeaderboardTimeframe.values()
        assertTrue(timeframes.contains(LeaderboardTimeframe.DAILY))
        assertTrue(timeframes.contains(LeaderboardTimeframe.WEEKLY))
        assertTrue(timeframes.contains(LeaderboardTimeframe.MONTHLY))
        assertTrue(timeframes.contains(LeaderboardTimeframe.ALL_TIME))
    }

    @Test
    fun testGetLeaderboard_returnsOrderedList() = runBlocking {
        val entries = repository.getLeaderboard(
            timeframe = LeaderboardTimeframe.ALL_TIME,
            currentUid = "user_me",
            currentUserScore = 12000,
            currentUserName = "Me",
            currentUserAvatar = "piko"
        )

        assertFalse(entries.isEmpty())

        // Verify ranks are 1-indexed and sorted by score descending
        for (i in 0 until entries.size - 1) {
            assertEquals(i + 1, entries[i].rank)
            assertTrue(
                "Entry at ${entries[i].rank} (score ${entries[i].score}) should be >= entry at ${entries[i + 1].rank} (score ${entries[i + 1].score})",
                entries[i].score >= entries[i + 1].score
            )
        }
    }

    @Test
    fun testGetLeaderboard_includesCurrentUser() = runBlocking {
        val currentUid = "test_player_uid_123"
        val entries = repository.getLeaderboard(
            timeframe = LeaderboardTimeframe.WEEKLY,
            currentUid = currentUid,
            currentUserScore = 15000,
            currentUserName = "TopPlayer",
            currentUserAvatar = "bolt"
        )

        val userEntry = entries.find { it.uid == currentUid }
        assertNotNull("Current user must be present in leaderboard", userEntry)
        assertTrue(userEntry!!.isCurrentUser)
        assertEquals("TopPlayer", userEntry.displayName)
    }

    @Test
    fun testLeaderboardEntry_modelFields() {
        val entry = LeaderboardEntry(
            rank = 1,
            uid = "u1",
            displayName = "Champion",
            avatarId = "bolt",
            score = 25000,
            wins = 120,
            isCurrentUser = false
        )

        assertEquals(1, entry.rank)
        assertEquals("u1", entry.uid)
        assertEquals("Champion", entry.displayName)
        assertEquals("bolt", entry.avatarId)
        assertEquals(25000, entry.score)
        assertEquals(120, entry.wins)
        assertFalse(entry.isCurrentUser)
    }
}
