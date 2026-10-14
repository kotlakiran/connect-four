package com.kirankotla.connectfour.data

import com.kirankotla.connectfour.model.reward.Rarity

data class DailyRewardTier(
    val day: Int,
    val coins: Int,
    val gems: Int = 0,
    val relicRarity: Rarity? = null
)

data class DailyRewardState(
    val streak: Int,
    val bestStreak: Int,
    val claimedToday: Boolean,
    /** Streak the next claim would reach (1 if the chain was broken). */
    val nextStreak: Int,
    val nextReward: DailyRewardTier
)

data class DailyClaimResult(
    val claimed: Boolean,
    val streak: Int,
    val reward: DailyRewardTier?,
    val relicId: String? = null
)

/** Pure daily-streak rules; day numbers are epoch days so callers control the clock. */
object DailyRewardRules {

    val CYCLE: List<DailyRewardTier> = listOf(
        DailyRewardTier(1, 20),
        DailyRewardTier(2, 30),
        DailyRewardTier(3, 40),
        DailyRewardTier(4, 50),
        DailyRewardTier(5, 75),
        DailyRewardTier(6, 100),
        DailyRewardTier(7, 150, gems = 1, relicRarity = Rarity.RARE)
    )

    fun tierFor(streak: Int): DailyRewardTier = CYCLE[(streak.coerceAtLeast(1) - 1) % CYCLE.size]

    /** lastClaimDay < 0 means never claimed. */
    fun nextStreak(lastClaimDay: Long, currentStreak: Int, today: Long): Int = when {
        lastClaimDay < 0 -> 1
        lastClaimDay == today -> currentStreak
        lastClaimDay == today - 1 -> currentStreak + 1
        else -> 1
    }

    fun canClaim(lastClaimDay: Long, today: Long): Boolean = lastClaimDay != today
}
