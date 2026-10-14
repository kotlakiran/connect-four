package com.kirankotla.connectfour.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_game")
data class SavedGameEntity(
    @PrimaryKey val gameId: String,
    val rows: Int,
    val cols: Int,
    val mode: String,
    val turnRule: String,
    val aiDifficulty: String?,
    val stateJson: String,
    val updatedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "match_summary")
data class MatchSummaryEntity(
    @PrimaryKey val matchId: String,
    val mode: String,
    val rows: Int,
    val cols: Int,
    val turnRule: String,
    val aiDifficulty: String?,
    val result: String, // WIN, LOSS, DRAW
    val userScore: Int,
    val opponentScore: Int,
    val durationMs: Long,
    val movesCount: Int,
    val chainRecord: Int,
    val hintsUsed: Int,
    val undosUsed: Int,
    val starsEarned: Int,
    val timestampMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "match_replay")
data class MatchReplayEntity(
    @PrimaryKey val matchId: String,
    val moveLogJson: String,
    val checksum: String
)

@Entity(tableName = "player_profile")
data class PlayerProfileEntity(
    @PrimaryKey val profileId: String = "local_player",
    val name: String = "Player 1",
    val age: Int? = 18,
    val avatarId: String = "piko",
    val level: Int = 1,
    val lifetimeXp: Int = 0,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "player_stats")
data class PlayerStatsEntity(
    @PrimaryKey val profileId: String = "local_player",
    val gamesStarted: Int = 0,
    val gamesCompleted: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
    val boxesCaptured: Int = 0,
    val doubleCaptures: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val fastestWinMs: Long = 0L,
    val largestMargin: Int = 0,
    val coins: Int = 0,
    val gems: Int = 0,
    val ownedJokersJson: String = "[]",
    val collectiblesJson: String = "[]",
    val lastDailyClaimDay: Long = -1L,
    val dailyStreak: Int = 0,
    val bestDailyStreak: Int = 0
)

@Entity(tableName = "achievement")
data class AchievementEntity(
    @PrimaryKey val achievementId: String,
    val progress: Int = 0,
    val isUnlocked: Boolean = false,
    val unlockedAtMs: Long? = null,
    val rewardGrantedAtMs: Long? = null
)
