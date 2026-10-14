package com.kirankotla.connectfour.model.reward

/** First-wave jokers. Only [rankedSafe] jokers may ever affect an online ranked board. */
data class Joker(
    val id: String,
    val title: String,
    val description: String,
    val rarity: Rarity,
    val iconId: String,
    val rankedSafe: Boolean = false
)

object JokerCatalog {

    val EXTRA_TURN = Joker(
        id = "extra_turn",
        title = "Extra Turn",
        description = "Take one additional turn immediately.",
        rarity = Rarity.RARE,
        iconId = "joker_extra_turn"
    )

    val STEAL_BOX = Joker(
        id = "steal_box",
        title = "Steal Box",
        description = "Claim one box currently owned by an opponent.",
        rarity = Rarity.EPIC,
        iconId = "joker_steal_box"
    )

    val SHIELD = Joker(
        id = "shield",
        title = "Shield",
        description = "Block the next steal used against you.",
        rarity = Rarity.RARE,
        iconId = "joker_shield"
    )

    val BOMB = Joker(
        id = "bomb",
        title = "Bomb",
        description = "Highlight and defuse one dangerous edge preview.",
        rarity = Rarity.EPIC,
        iconId = "joker_bomb"
    )

    val DOUBLE_COINS = Joker(
        id = "double_coins",
        title = "Double Coins",
        description = "Double the coins from your next completed box.",
        rarity = Rarity.COMMON,
        iconId = "joker_double_coins"
    )

    val ALL: List<Joker> = listOf(EXTRA_TURN, STEAL_BOX, SHIELD, BOMB, DOUBLE_COINS)

    fun byId(id: String): Joker? = ALL.firstOrNull { it.id == id }
}
