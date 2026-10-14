package com.kirankotla.connectfour.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressionProcessorTest {

    @Test
    fun testXpAndLevelFormula() {
        val level1Req = (100.0 * 1.0).toInt()
        val level2Req = (100.0 * 2.0.toDouble().run { Math.pow(this, 1.35) }).toInt()

        assertEquals(100, level1Req)
        assertEquals(254, level2Req)
    }

    @Test
    fun testAchievementCatalogCount() {
        assertEquals(45, AchievementCatalog.ALL_ACHIEVEMENTS.size)
    }
}
