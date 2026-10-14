package com.kirankotla.connectfour.model

enum class GameMode(val displayName: String) {
    QUICK_PLAY("Quick Play"),
    VS_AI("Player vs AI"),
    PASS_AND_PLAY("Pass & Play"),
    CAMPAIGN("Campaign Map"),
    DICE_MODE("Dice Mode")
}

enum class TurnRule(val displayName: String) {
    STANDARD("Classic Sudden Death"),
    MAX_LINES("Max Lines Battle")
}

enum class AiDifficulty(val displayName: String) {
    BEGINNER("Beginner"),
    EASY("Easy"),
    MEDIUM("Medium"),
    HARD("Hard"),
    EXPERT("Expert")
}
