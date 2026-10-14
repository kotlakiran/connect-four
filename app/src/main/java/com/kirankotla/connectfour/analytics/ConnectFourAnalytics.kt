package com.kirankotla.connectfour.analytics

import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.kirankotla.connectfour.config.FirebaseManager

object ConnectFourAnalytics {
    private const val TAG = "ConnectFourAnalytics"

    private fun getAnalytics(): FirebaseAnalytics? = FirebaseManager.analytics

    fun logLogin(method: String, userId: String) {
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.METHOD, method)
            putString("player_uid", userId)
        }
        getAnalytics()?.logEvent(FirebaseAnalytics.Event.LOGIN, bundle)
        Log.d(TAG, "Event: login ($method, $userId)")
    }

    fun logGameStarted(gameId: String, mode: String, isMultiplayer: Boolean, opponentType: String) {
        val bundle = Bundle().apply {
            putString("game_id", gameId)
            putString("game_mode", mode)
            putBoolean("is_multiplayer", isMultiplayer)
            putString("opponent_type", opponentType)
            putLong("timestamp", System.currentTimeMillis())
        }
        getAnalytics()?.logEvent("game_started", bundle)
        Log.d(TAG, "Event: game_started ($gameId, $mode)")
    }

    fun logMatchFound(gameId: String, queueDurationMs: Long, mode: String) {
        val bundle = Bundle().apply {
            putString("game_id", gameId)
            putLong("queue_duration_ms", queueDurationMs)
            putString("mode", mode)
        }
        getAnalytics()?.logEvent("match_found", bundle)
        Log.d(TAG, "Event: match_found ($gameId, ${queueDurationMs}ms)")
    }

    fun logGameCompleted(
        gameId: String,
        durationMs: Long,
        userScore: Int,
        opponentScore: Int,
        result: String,
        mode: String
    ) {
        val bundle = Bundle().apply {
            putString("game_id", gameId)
            putLong("duration_ms", durationMs)
            putInt("user_score", userScore)
            putInt("opponent_score", opponentScore)
            putString("result", result)
            putString("mode", mode)
        }
        getAnalytics()?.logEvent("game_completed", bundle)
        Log.d(TAG, "Event: game_completed ($gameId, $result)")
    }

    fun logAchievementUnlocked(achievementId: String, title: String) {
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.ACHIEVEMENT_ID, achievementId)
            putString("title", title)
        }
        getAnalytics()?.logEvent(FirebaseAnalytics.Event.UNLOCK_ACHIEVEMENT, bundle)
        Log.d(TAG, "Event: unlock_achievement ($achievementId, $title)")
    }

    fun logDailyRewardClaimed(day: Int, coins: Int) {
        val bundle = Bundle().apply {
            putInt("day_number", day)
            putInt("coins_awarded", coins)
        }
        getAnalytics()?.logEvent("daily_reward_claimed", bundle)
        Log.d(TAG, "Event: daily_reward_claimed ($day, +$coins coins)")
    }

    fun logJokerUsed(jokerId: String, gameMode: String) {
        val bundle = Bundle().apply {
            putString("joker_id", jokerId)
            putString("game_mode", gameMode)
        }
        getAnalytics()?.logEvent("joker_used", bundle)
        Log.d(TAG, "Event: joker_used ($jokerId, $gameMode)")
    }

    fun logCampaignLevelCompleted(chapterId: Int, levelId: Int, stars: Int, movesCount: Int) {
        val bundle = Bundle().apply {
            putInt("chapter_id", chapterId)
            putInt("level_id", levelId)
            putInt("stars_earned", stars)
            putInt("moves_count", movesCount)
        }
        getAnalytics()?.logEvent("campaign_level_completed", bundle)
        Log.d(TAG, "Event: campaign_level_completed (ch$chapterId-lvl$levelId, $stars stars)")
    }
}
