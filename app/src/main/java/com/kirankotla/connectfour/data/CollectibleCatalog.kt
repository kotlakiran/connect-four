package com.kirankotla.connectfour.data

import com.kirankotla.connectfour.model.reward.Rarity

data class Collectible(
    val id: String,
    val title: String,
    val rarity: Rarity,
    val iconId: String
)

/** The 30 "arcade relic" collectibles. Ids are persisted; never rename them. */
object CollectibleCatalog {

    private fun relic(id: String, title: String, rarity: Rarity) =
        Collectible("relic_$id", title, rarity, "ic_relic_$id")

    val ALL: List<Collectible> = listOf(
        relic("joystick", "Pocket Joystick", Rarity.COMMON),
        relic("token", "Brass Token", Rarity.COMMON),
        relic("cartridge", "Dusty Cartridge", Rarity.COMMON),
        relic("pixel_heart", "Pixel Heart", Rarity.COMMON),
        relic("pixel_star", "Pixel Star", Rarity.COMMON),
        relic("dice", "Lucky Die", Rarity.COMMON),
        relic("gumball", "Gumball Orb", Rarity.COMMON),
        relic("sticker", "Holo Sticker", Rarity.COMMON),
        relic("pin", "Arcade Pin", Rarity.COMMON),
        relic("ticket", "Prize Ticket", Rarity.COMMON),
        relic("headset", "Retro Headset", Rarity.COMMON),
        relic("keychain", "Neon Keychain", Rarity.COMMON),
        relic("high_score", "High Score Plaque", Rarity.RARE),
        relic("pocket_console", "Pocket Console", Rarity.RARE),
        relic("neon_sign", "Neon Sign", Rarity.RARE),
        relic("coin_stack", "Coin Stack", Rarity.RARE),
        relic("mini_cabinet", "Mini Cabinet", Rarity.RARE),
        relic("sound_chip", "Sound Chip", Rarity.RARE),
        relic("glow_pad", "Glow Pad", Rarity.RARE),
        relic("turbo_button", "Turbo Button", Rarity.RARE),
        relic("pixel_crown", "Pixel Crown", Rarity.RARE),
        relic("golden_joystick", "Golden Joystick", Rarity.EPIC),
        relic("crystal_cartridge", "Crystal Cartridge", Rarity.EPIC),
        relic("rainbow_token", "Rainbow Token", Rarity.EPIC),
        relic("star_core", "Star Core", Rarity.EPIC),
        relic("plasma_orb", "Plasma Orb", Rarity.EPIC),
        relic("cosmic_cabinet", "Cosmic Cabinet", Rarity.EPIC),
        relic("founder_chip", "Founder's Chip", Rarity.LEGENDARY),
        relic("infinity_coin", "Infinity Coin", Rarity.LEGENDARY),
        relic("grand_master_trophy", "Grand Master Trophy", Rarity.MYTHIC)
    )

    private val byId = ALL.associateBy { it.id }

    fun find(id: String): Collectible? = byId[id]

    fun byRarity(rarity: Rarity): List<Collectible> = ALL.filter { it.rarity == rarity }

    /** Coins granted when a relic that is already owned drops again. */
    fun duplicateCoins(rarity: Rarity): Int = when (rarity) {
        Rarity.COMMON -> 5
        Rarity.RARE -> 15
        Rarity.EPIC -> 40
        Rarity.LEGENDARY -> 100
        Rarity.MYTHIC -> 250
    }

    /** First unowned relic of [rarity], falling back to any unowned relic; null when the set is complete. */
    fun nextUnowned(rarity: Rarity, owned: Set<String>): Collectible? =
        byRarity(rarity).firstOrNull { it.id !in owned } ?: ALL.firstOrNull { it.id !in owned }

    fun parseOwned(json: String): Set<String> =
        Regex("\"([^\"]+)\"").findAll(json).map { it.groupValues[1] }.filter { it in byId }.toSet()
}

data class CollectibleState(
    val owned: List<Collectible>,
    val missing: List<Collectible>
) {
    val ownedCount: Int get() = owned.size
    val totalCount: Int get() = owned.size + missing.size
}
