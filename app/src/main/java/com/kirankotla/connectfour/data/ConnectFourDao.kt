package com.kirankotla.connectfour.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectFourDao {

    // Saved Game
    @Query("SELECT * FROM saved_game WHERE gameId = :gameId LIMIT 1")
    suspend fun getSavedGame(gameId: String): SavedGameEntity?

    @Query("SELECT * FROM saved_game ORDER BY updatedAtMs DESC LIMIT 1")
    suspend fun getLatestSavedGame(): SavedGameEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveGame(entity: SavedGameEntity)

    @Query("DELETE FROM saved_game WHERE gameId = :gameId")
    suspend fun deleteSavedGame(gameId: String)

    // Match Summary
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatchSummary(entity: MatchSummaryEntity)

    @Query("SELECT * FROM match_summary ORDER BY timestampMs DESC LIMIT 50")
    fun getRecentMatchSummariesFlow(): Flow<List<MatchSummaryEntity>>

    @Query("SELECT * FROM match_summary WHERE matchId = :matchId LIMIT 1")
    suspend fun getMatchSummary(matchId: String): MatchSummaryEntity?

    // Match Replay
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatchReplay(entity: MatchReplayEntity)

    @Query("SELECT * FROM match_replay WHERE matchId = :matchId LIMIT 1")
    suspend fun getMatchReplay(matchId: String): MatchReplayEntity?

    // Player Profile
    @Query("SELECT * FROM player_profile WHERE profileId = :profileId LIMIT 1")
    fun getPlayerProfileFlow(profileId: String = "local_player"): Flow<PlayerProfileEntity?>

    @Query("SELECT * FROM player_profile WHERE profileId = :profileId LIMIT 1")
    suspend fun getPlayerProfile(profileId: String = "local_player"): PlayerProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: PlayerProfileEntity)

    // Player Stats
    @Query("SELECT * FROM player_stats WHERE profileId = :profileId LIMIT 1")
    fun getPlayerStatsFlow(profileId: String = "local_player"): Flow<PlayerStatsEntity?>

    @Query("SELECT * FROM player_stats WHERE profileId = :profileId LIMIT 1")
    suspend fun getPlayerStats(profileId: String = "local_player"): PlayerStatsEntity?

    @Query("SELECT coins, gems FROM player_stats WHERE profileId = :profileId LIMIT 1")
    fun getWalletFlow(profileId: String = "local_player"): Flow<WalletBalance?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateStats(stats: PlayerStatsEntity)

    // Achievements
    @Query("SELECT * FROM achievement")
    fun getAllAchievementsFlow(): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievement WHERE achievementId = :id LIMIT 1")
    suspend fun getAchievement(id: String): AchievementEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAchievement(achievement: AchievementEntity)

    @Query("DELETE FROM match_summary")
    suspend fun clearHistory()

    @Query("DELETE FROM saved_game")
    suspend fun clearSavedGames()
}
