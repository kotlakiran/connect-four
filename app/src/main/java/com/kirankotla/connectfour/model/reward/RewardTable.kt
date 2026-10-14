package com.kirankotla.connectfour.model.reward

import java.util.Random

/** The gameplay situation in which a box was completed; shifts drop odds. */
enum class RewardContext {
    NORMAL_BOX,
    DOUBLE_CAPTURE,
    CHAIN
}

/**
 * Deterministic weighted drop table for box rewards.
 *
 * [roll] is pure and reproducible: the same seed and context always yield the
 * same reward, so replays and tests stay stable.
 */
object RewardTable {

    private data class Entry(val weight: Int, val build: (Random) -> BoxReward)

    private fun coin(amount: Int, rarity: Rarity) =
        BoxReward(RewardKind.COIN, amount = amount, rarity = rarity)

    private fun gem(amount: Int, rarity: Rarity) =
        BoxReward(RewardKind.GEM, amount = amount, rarity = rarity)

    private fun joker(joker: Joker) =
        BoxReward(RewardKind.JOKER, amount = 1, id = joker.id, rarity = joker.rarity)

    private fun collectible(rarity: Rarity) =
        BoxReward(RewardKind.COLLECTIBLE, amount = 1, rarity = rarity)

    private fun tableFor(context: RewardContext): List<Entry> = when (context) {
        RewardContext.NORMAL_BOX -> listOf(
            Entry(52) { coin(3, Rarity.COMMON) },
            Entry(30) { coin(5, Rarity.COMMON) },
            Entry(8) { coin(15, Rarity.RARE) },
            Entry(4) { joker(JokerCatalog.DOUBLE_COINS) },
            Entry(2) { gem(1, Rarity.RARE) },
            Entry(1) { collectible(Rarity.EPIC) }
        )
        RewardContext.DOUBLE_CAPTURE -> listOf(
            Entry(30) { coin(10, Rarity.COMMON) },
            Entry(28) { coin(25, Rarity.RARE) },
            Entry(20) { joker(JokerCatalog.ALL.random(it)) },
            Entry(14) { gem(1, Rarity.RARE) },
            Entry(6) { gem(3, Rarity.EPIC) },
            Entry(2) { collectible(Rarity.LEGENDARY) }
        )
        RewardContext.CHAIN -> listOf(
            Entry(25) { coin(40, Rarity.RARE) },
            Entry(25) { joker(JokerCatalog.ALL.random(it)) },
            Entry(22) { gem(2, Rarity.RARE) },
            Entry(16) { gem(5, Rarity.EPIC) },
            Entry(8) { joker(JokerCatalog.STEAL_BOX) },
            Entry(4) { collectible(Rarity.LEGENDARY) }
        )
    }

    private fun withType(reward: BoxReward, seed: Long): BoxReward {
        if (reward.kind == RewardKind.NOTHING) return reward
        return reward.copy(type = pickVisualType(seed, reward.rarity))
    }

    /**
     * Picks the cosmetic reward identity shown on the box. Independent of the
     * currency kind so players see varied icons (stars, keys, gems, crowns...)
     * instead of coins every time. Higher base rarity biases toward rarer icons.
     */
    private fun pickVisualType(seed: Long, baseRarity: Rarity): RewardType {
        val rng = Random(seed xor 0x5DEECE66DL)
        // Weighted rarity tiers; coins stay common but rarer icons appear often.
        val tiers = listOf(
            Rarity.COMMON to 24,
            Rarity.RARE to 40,
            Rarity.EPIC to 22,
            Rarity.LEGENDARY to 10,
            Rarity.MYTHIC to 4
        )
        // A higher-rarity currency drop guarantees at least that visual tier.
        val minOrdinal = baseRarity.ordinal
        val eligible = tiers.filter { it.first.ordinal >= minOrdinal }.ifEmpty { tiers }
        val total = eligible.sumOf { it.second }
        var pick = rng.nextInt(total)
        var chosenTier = eligible.first().first
        for ((tier, weight) in eligible) {
            if (pick < weight) { chosenTier = tier; break }
            pick -= weight
        }
        return RewardType.pick(seed, chosenTier)
    }

    /** Roll one reward deterministically for [seed] in a given [context]. */
    fun roll(seed: Long, context: RewardContext): BoxReward {
        val rng = Random(seed)
        val table = tableFor(context)
        val totalWeight = table.sumOf { it.weight }
        var pick = rng.nextInt(totalWeight)
        for (entry in table) {
            if (pick < entry.weight) return withType(entry.build(rng), seed)
            pick -= entry.weight
        }
        return BoxReward.NONE
    }

    /**
     * Roll a reward with performance bonuses. Faster moves and multi-box chains
     * increase currency without changing the board result.
     */
    fun roll(
        seed: Long,
        context: RewardContext,
        moveDurationMs: Long,
        boxesCompleted: Int
    ): BoxReward {
        val base = roll(seed, context)
        val safeDuration = moveDurationMs.coerceAtLeast(0L)
        val speedBonus = when {
            safeDuration <= 1_500L -> 10
            safeDuration <= 3_000L -> 5
            safeDuration <= 6_000L -> 2
            else -> 0
        }
        val boxBonus = (boxesCompleted - 1).coerceAtLeast(0) * 5
        val bonus = speedBonus + boxBonus

        return when (base.kind) {
            RewardKind.COIN -> base.copy(amount = base.amount + bonus)
            RewardKind.NOTHING -> if (bonus > 0) {
                val rarity = if (speedBonus >= 5) Rarity.RARE else Rarity.COMMON
                withType(BoxReward(RewardKind.COIN, amount = bonus, rarity = rarity), seed)
            } else {
                base
            }
            else -> base
        }
    }

    private fun <T> List<T>.random(rng: Random): T = this[rng.nextInt(size)]
}
