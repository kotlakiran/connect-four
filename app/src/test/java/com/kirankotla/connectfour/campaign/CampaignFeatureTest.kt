package com.kirankotla.connectfour.campaign

import com.kirankotla.connectfour.model.AiDifficulty
import com.kirankotla.connectfour.ui.screens.CAMPAIGN_LEVELS
import com.kirankotla.connectfour.ui.screens.CampaignLevel
import org.junit.Assert.*
import org.junit.Test

class CampaignFeatureTest {

    @Test
    fun testCampaignLevels_catalogIntegrity() {
        assertFalse("Campaign levels should not be empty", CAMPAIGN_LEVELS.isEmpty())
        assertEquals(8, CAMPAIGN_LEVELS.size)

        // Verify sequential IDs
        for (i in CAMPAIGN_LEVELS.indices) {
            assertEquals(i + 1, CAMPAIGN_LEVELS[i].id)
        }
    }

    @Test
    fun testCampaignLevels_chaptersAndDifficultyProgression() {
        val chapter1Levels = CAMPAIGN_LEVELS.filter { it.chapter == 1 }
        val chapter2Levels = CAMPAIGN_LEVELS.filter { it.chapter == 2 }

        assertEquals(4, chapter1Levels.size)
        assertEquals(4, chapter2Levels.size)

        // Verify chapter 1 difficulty is beginner/easy
        for (lvl in chapter1Levels) {
            assertTrue(
                "Chapter 1 levels should be beginner or easy",
                lvl.difficulty == AiDifficulty.BEGINNER || lvl.difficulty == AiDifficulty.EASY
            )
        }

        // Verify chapter 2 difficulty ramps up to medium, hard, expert
        val diffs = chapter2Levels.map { it.difficulty }
        assertTrue(diffs.contains(AiDifficulty.MEDIUM))
        assertTrue(diffs.contains(AiDifficulty.HARD))
        assertTrue(diffs.contains(AiDifficulty.EXPERT))
    }

    @Test
    fun testCampaignLevels_gridSizes() {
        // Earlier levels have smaller boards (4x5), later levels have larger boards (6x7, 7x8)
        assertEquals(4, CAMPAIGN_LEVELS[0].rows)
        assertEquals(5, CAMPAIGN_LEVELS[0].cols)

        val lastLevel = CAMPAIGN_LEVELS.last()
        assertEquals(7, lastLevel.rows)
        assertEquals(8, lastLevel.cols)
    }

    @Test
    fun testCampaignLevels_starCalculation() {
        val totalEarned = CAMPAIGN_LEVELS.sumOf { it.stars }
        val maxStars = CAMPAIGN_LEVELS.size * 3

        assertTrue("Earned stars must be >= 0", totalEarned >= 0)
        assertTrue("Earned stars cannot exceed max possible", totalEarned <= maxStars)
        assertEquals(24, maxStars)
    }

    @Test
    fun testCampaignLevel_customStarProgress() {
        val level = CampaignLevel(
            id = 99,
            title = "Test Level",
            chapter = 1,
            rows = 3,
            cols = 3,
            difficulty = AiDifficulty.EASY,
            stars = 2,
            isLocked = false
        )

        assertEquals(2, level.stars)
        assertFalse(level.isLocked)
    }
}
