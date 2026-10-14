package com.kirankotla.connectfour.data

data class AchievementDef(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val targetProgress: Int = 1,
    val xpReward: Int = 50,
    val isHidden: Boolean = false
) {
    /** Emoji badge shown in the achievements list, derived from category and tier. */
    val badgeIcon: String get() = when {
        id.startsWith("streak") || category == "Dedication" -> "🔥"
        category == "Getting Started" -> "🏅"
        category == "Tactics" -> "🧠"
        category == "Campaign" -> "🚀"
        category == "Dice Mode" -> "🎲"
        id.contains("legend") || id == "wins_500" -> "🌈"
        xpReward >= 800 -> "🐉"
        xpReward >= 500 -> "👑"
        xpReward >= 300 -> "🏆"
        xpReward >= 150 -> "🥇"
        xpReward >= 75 -> "🥈"
        else -> "🥉"
    }

    /** Rarity tier label from XP value. */
    val rarityLabel: String get() = when {
        xpReward >= 800 -> "Mythic"
        xpReward >= 500 -> "Legendary"
        xpReward >= 300 -> "Epic"
        xpReward >= 150 -> "Rare"
        xpReward >= 75 -> "Uncommon"
        else -> "Common"
    }

    /** Rarity color for the badge ring. */
    val rarityColorHex: Long get() = when {
        xpReward >= 800 -> 0xFFEF4444
        xpReward >= 500 -> 0xFFF97316
        xpReward >= 300 -> 0xFFA855F7
        xpReward >= 150 -> 0xFF3B82F6
        xpReward >= 75 -> 0xFF22C55E
        else -> 0xFF94A3B8
    }
}

object AchievementCatalog {

    val ALL_ACHIEVEMENTS = listOf(
        // Getting Started
        AchievementDef("first_line", "First Chip", "Drop your very first chip in Connect Four", "Getting Started", 1, 20),
        AchievementDef("first_box", "First Connect", "Connect your first row of 4 chips", "Getting Started", 1, 30),
        AchievementDef("first_win", "First Victory", "Win your first match", "Getting Started", 1, 50),
        AchievementDef("first_ai_win", "AI Defeater", "Defeat the AI in any match", "Getting Started", 1, 50),
        AchievementDef("tutorial_grad", "Tutorial Graduate", "Complete the interactive tutorial", "Getting Started", 1, 100),

        // Mastery
        AchievementDef("wins_10", "Veteran", "Win 10 matches", "Mastery", 10, 100),
        AchievementDef("wins_50", "Champion", "Win 50 matches", "Mastery", 50, 250),
        AchievementDef("wins_100", "Grand Champion", "Win 100 matches", "Mastery", 100, 500),
        AchievementDef("wins_500", "Legend", "Win 500 matches", "Mastery", 500, 1000),
        AchievementDef("streak_3", "On a Roll", "Achieve a 3-win streak", "Mastery", 3, 100),
        AchievementDef("streak_5", "Unstoppable", "Achieve a 5-win streak", "Mastery", 5, 200),
        AchievementDef("streak_10", "Dominator", "Achieve a 10-win streak", "Mastery", 10, 500),
        AchievementDef("perfect_win", "Flawless Win", "Win a match without letting opponent create any 3-in-a-row", "Mastery", 1, 200),
        AchievementDef("comeback_win", "Comeback King", "Win after opponent was 1 move away from 4-in-a-row", "Mastery", 1, 200),
        AchievementDef("win_by_one", "Nail Biter", "Win a Max Lines match by exactly 1 line", "Mastery", 1, 100),
        AchievementDef("all_board_sizes", "Board Master", "Win on 5x4, 6x5, 7x6, and 8x7 boards", "Mastery", 4, 300),
        AchievementDef("defeat_all_ai", "AI Conqueror", "Defeat all 5 AI difficulty levels", "Mastery", 5, 300),
        AchievementDef("expert_5x5", "Grand Master AI", "Defeat Expert AI on a 7x6 or larger board", "Mastery", 1, 400),

        // Tactics
        AchievementDef("double_capture_1", "Double Threat", "Form two 4-in-a-row lines with a single chip drop", "Tactics", 1, 50),
        AchievementDef("double_capture_25", "Strategic Mastermind", "Win with simultaneous double lines 25 times", "Tactics", 25, 200),
        AchievementDef("chain_5", "Vertical Striker", "Win with a 4-in-a-row vertical drop", "Tactics", 1, 100),
        AchievementDef("chain_10", "Diagonal Master", "Win with a 4-in-a-row diagonal connection", "Tactics", 1, 300),
        AchievementDef("no_hint_no_undo", "Pure Skill", "Win a match without using hints or undos", "Tactics", 1, 150),

        // Campaign
        AchievementDef("camp_chap_1", "First Chapter", "Complete Campaign Chapter 1", "Campaign", 1, 100),
        AchievementDef("camp_chap_2", "Tactical Chapter", "Complete Campaign Chapter 2", "Campaign", 1, 150),
        AchievementDef("stars_25", "Rising Star", "Earn 25 Campaign Stars", "Campaign", 25, 200),
        AchievementDef("stars_75", "Star Master", "Earn 75 Campaign Stars", "Campaign", 75, 400),
        AchievementDef("stars_all", "Galaxy Explorer", "Earn all Campaign Stars", "Campaign", 100, 800),
        AchievementDef("camp_perfect", "Perfectionist", "Earn 3 stars on 10 Campaign levels", "Campaign", 10, 300),

        // Dice Mode
        AchievementDef("dice_win_1", "High Roller", "Win your first Dice Mode match", "Dice Mode", 1, 100),
        AchievementDef("dice_six_all", "Maximum Efficiency", "Use all chip allowances from a roll", "Dice Mode", 1, 100),
        AchievementDef("dice_end_early_win", "Tactical Restraint", "Win after ending your turn early twice", "Dice Mode", 1, 150),
        AchievementDef("dice_wins_25", "Dice Champion", "Win 25 Dice Mode matches", "Dice Mode", 25, 300),
        AchievementDef("dice_three_sixes", "Lucky Streaks", "Roll maximum allowance three times in a single match", "Dice Mode", 3, 150),

        // Dedication
        AchievementDef("games_25", "Dedicated Player", "Complete 25 matches", "Dedication", 25, 100),
        AchievementDef("games_100", "Connect Four Enthusiast", "Complete 100 matches", "Dedication", 100, 300),
        AchievementDef("games_500", "Connect Four Devotee", "Complete 500 matches", "Dedication", 500, 1000),
        AchievementDef("active_streak_3", "Consistent", "Play at least 1 match on 3 consecutive days", "Dedication", 3, 100),
        AchievementDef("all_modes_played", "Explorer", "Complete a match in all 5 game modes", "Dedication", 5, 200),
        AchievementDef("all_themes_used", "Fashionista", "Play a match using every launch theme", "Dedication", 3, 150),

        // Collection & Power-Ups
        AchievementDef("first_joker_used", "Wild Card", "Use a joker for the first time", "Collection", 1, 50),
        AchievementDef("collect_10_relics", "Relic Hunter", "Collect 10 arcade relics", "Collection", 10, 200),
        AchievementDef("coins_1000", "Coin Hoarder", "Hold 1000 coins", "Collection", 1000, 200),
        AchievementDef("legendary_drop", "Legendary Find", "Find a legendary relic", "Collection", 1, 300),
        AchievementDef("power_up_win", "Power Player", "Win a Power-Up Mode match", "Collection", 1, 100)
    )
}
