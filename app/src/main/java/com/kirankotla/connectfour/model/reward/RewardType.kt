package com.kirankotla.connectfour.model.reward

import java.util.Random

/** Cosmetic reward identity: icon, rarity tier and the sound profile to play. */
enum class RewardType(val icon: String, val rarity: Rarity, val soundProfile: String) {
    COINS("🪙", Rarity.COMMON, "coin_cling"),

    STARS("⭐", Rarity.RARE, "star_twinkle"),
    KEYS("🔑", Rarity.RARE, "key_click"),
    TICKETS("🎟️", Rarity.RARE, "paper_stamp"),
    PUZZLE_PIECES("🧩", Rarity.RARE, "snap"),
    LUCK_TOKENS("🍀", Rarity.RARE, "lucky_bell"),
    HEARTS("❤️", Rarity.RARE, "heartbeat_pop"),

    DIAMONDS("💎", Rarity.EPIC, "crystal_sparkle"),
    LIGHTNING("⚡", Rarity.EPIC, "electric_zap"),
    ENERGY("🔥", Rarity.EPIC, "energy_charge"),
    MOON_SHARDS("🌙", Rarity.EPIC, "mystical_chime"),
    SUN_CRYSTALS("☀️", Rarity.EPIC, "bright_burst"),
    MAGIC_DUST("🪄", Rarity.EPIC, "fairy_sparkle"),
    ROCKET_FUEL("🚀", Rarity.EPIC, "futuristic_boost"),
    MYSTERY_BOX("📦", Rarity.EPIC, "chest_open"),

    CROWNS("👑", Rarity.LEGENDARY, "royal_chime"),
    TROPHIES("🏆", Rarity.LEGENDARY, "victory_fanfare"),
    MEDALS("🎖️", Rarity.LEGENDARY, "medal"),

    RAINBOW_ORBS("🌈", Rarity.MYTHIC, "epic_fantasy"),
    DRAGON_SCALES("🐉", Rarity.MYTHIC, "whoosh");

    companion object {
        fun forRarity(rarity: Rarity): List<RewardType> = entries.filter { it.rarity == rarity }

        /** Deterministically picks a type of [rarity] for [seed]. */
        fun pick(seed: Long, rarity: Rarity): RewardType {
            val options = forRarity(rarity)
            return options[Random(seed xor (rarity.ordinal * 0x9E3779B97F4A7C15uL.toLong())).nextInt(options.size)]
        }
    }
}
