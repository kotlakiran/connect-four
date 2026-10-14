package com.kirankotla.connectfour.data

import com.kirankotla.connectfour.model.reward.Rarity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectiblesAndDailyRewardTest {

    @Test
    fun catalogHasThirtyUniqueRelics() {
        assertEquals(30, CollectibleCatalog.ALL.size)
        assertEquals(30, CollectibleCatalog.ALL.map { it.id }.toSet().size)
        assertEquals(30, CollectibleCatalog.ALL.map { it.iconId }.toSet().size)
        Rarity.values().forEach { assertTrue(CollectibleCatalog.byRarity(it).isNotEmpty()) }
    }

    @Test
    fun nextUnownedSkipsOwnedAndFallsBack() {
        val commons = CollectibleCatalog.byRarity(Rarity.COMMON).map { it.id }.toSet()
        val next = CollectibleCatalog.nextUnowned(Rarity.COMMON, setOf(commons.first()))
        assertNotNull(next)
        assertTrue(next!!.id !in setOf(commons.first()))
        assertEquals(null, CollectibleCatalog.nextUnowned(Rarity.COMMON, CollectibleCatalog.ALL.map { it.id }.toSet()))
    }

    @Test
    fun parseOwnedIgnoresUnknownAndBadJson() {
        val id = CollectibleCatalog.ALL.first().id
        assertEquals(setOf(id), CollectibleCatalog.parseOwned("[\"$id\",\"bogus\"]"))
        assertTrue(CollectibleCatalog.parseOwned("not json").isEmpty())
    }

    @Test
    fun dailyStreakRules() {
        assertEquals(1, DailyRewardRules.nextStreak(-1, 0, 100))
        assertEquals(4, DailyRewardRules.nextStreak(99, 3, 100))
        assertEquals(1, DailyRewardRules.nextStreak(97, 5, 100))
        assertFalse(DailyRewardRules.canClaim(100, 100))
        assertTrue(DailyRewardRules.canClaim(99, 100))
    }

    @Test
    fun dailyCycleWrapsAndDaySevenGrantsRelic() {
        assertEquals(Rarity.RARE, DailyRewardRules.tierFor(7).relicRarity)
        assertEquals(DailyRewardRules.tierFor(1), DailyRewardRules.tierFor(8))
    }

    @Test
    fun newAchievementsRegistered() {
        val ids = AchievementCatalog.ALL_ACHIEVEMENTS.map { it.id }
        listOf("first_joker_used", "collect_10_relics", "coins_1000", "legendary_drop", "power_up_win")
            .forEach { assertTrue(it, it in ids) }
        assertEquals(ids.size, ids.toSet().size)
    }
}
