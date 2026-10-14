package com.kirankotla.connectfour.model.reward

/** Rarity tiers for box rewards and collectibles. */
enum class Rarity {
    COMMON,
    RARE,
    EPIC,
    LEGENDARY,
    MYTHIC
}

/** The category of a reward that can pop out of a completed box. */
enum class RewardKind {
    NOTHING,
    COIN,
    GEM,
    JOKER,
    POWER_UP,
    COLLECTIBLE
}

/**
 * A single reward produced when a box is completed.
 *
 * @param kind reward category
 * @param amount quantity for stackable kinds (coins/gems); 1 for unique items
 * @param id catalog id for JOKER/POWER_UP/COLLECTIBLE; empty for currency
 * @param rarity drop rarity, used for visuals and dedupe-to-coins conversion
 * @param type optional display identity (emoji + sound); purely cosmetic
 */
data class BoxReward(
    val kind: RewardKind,
    val amount: Int = 0,
    val id: String = "",
    val rarity: Rarity = Rarity.COMMON,
    val type: RewardType? = null
) {
    val isNothing: Boolean get() = kind == RewardKind.NOTHING

    companion object {
        val NONE = BoxReward(RewardKind.NOTHING)
    }
}
