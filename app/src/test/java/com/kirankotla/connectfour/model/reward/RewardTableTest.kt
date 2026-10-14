package com.kirankotla.connectfour.model.reward

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RewardTableTest {

    @Test
    fun rollIsDeterministicForSameSeedAndContext() {
        val a = RewardTable.roll(12345L, RewardContext.NORMAL_BOX)
        val b = RewardTable.roll(12345L, RewardContext.NORMAL_BOX)
        assertEquals(a, b)
    }

    @Test
    fun differentSeedsCanProduceDifferentRewards() {
        val rewards = (0 until 50).map { RewardTable.roll(it.toLong(), RewardContext.NORMAL_BOX) }
        assertTrue("Expected variety across seeds", rewards.distinct().size > 1)
    }

    @Test
    fun normalBoxAlwaysDropsAReward() {
        val results = (0 until 2000).map { RewardTable.roll(it.toLong(), RewardContext.NORMAL_BOX) }
        assertTrue(results.all { !it.isNothing })
    }

    @Test
    fun chainContextNeverDropsNothing() {
        val results = (0 until 2000).map { RewardTable.roll(it.toLong(), RewardContext.CHAIN) }
        assertTrue(results.none { it.isNothing })
    }

    @Test
    fun doubleCaptureHasHigherExpectedRewardValueThanNormal() {
        val normal = (0 until 3000).map { RewardTable.roll(it.toLong(), RewardContext.NORMAL_BOX) }
        val double = (0 until 3000).map { RewardTable.roll(it.toLong(), RewardContext.DOUBLE_CAPTURE) }
        fun value(reward: BoxReward): Int = when (reward.kind) {
            RewardKind.COIN -> reward.amount
            RewardKind.GEM -> reward.amount * 10
            RewardKind.JOKER -> 20
            RewardKind.COLLECTIBLE -> 50
            else -> 0
        }
        assertTrue(double.map(::value).average() > normal.map(::value).average())
    }

    @Test
    fun jokerRewardsAlwaysMapToACatalogJoker() {
        val results = (0 until 3000).map { RewardTable.roll(it.toLong(), RewardContext.CHAIN) }
        results.filter { it.kind == RewardKind.JOKER }.forEach {
            assertNotNull("Unknown joker id ${it.id}", JokerCatalog.byId(it.id))
        }
    }

    @Test
    fun currencyRewardsCarryPositiveAmount() {
        val results = (0 until 2000).map { RewardTable.roll(it.toLong(), RewardContext.DOUBLE_CAPTURE) }
        results.filter { it.kind == RewardKind.COIN || it.kind == RewardKind.GEM }.forEach {
            assertTrue("Currency must be positive", it.amount > 0)
        }
    }

    @Test
    fun jokerCatalogIdsAreUnique() {
        val ids = JokerCatalog.ALL.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun fastMultiBoxRewardsAreAtLeastAsValuableAsSlowSingleBoxRewards() {
        val fast = RewardTable.roll(8L, RewardContext.NORMAL_BOX, 1_000L, 2)
        val slow = RewardTable.roll(8L, RewardContext.NORMAL_BOX, 12_000L, 1)
        if (fast.kind == RewardKind.COIN && slow.kind == RewardKind.COIN) {
            assertTrue(fast.amount >= slow.amount)
        } else {
            assertTrue(fast.kind != RewardKind.NOTHING || slow.kind == RewardKind.NOTHING)
        }
    }
}
