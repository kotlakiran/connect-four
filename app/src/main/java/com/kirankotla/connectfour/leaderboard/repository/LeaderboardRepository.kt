package com.kirankotla.connectfour.leaderboard.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.kirankotla.connectfour.config.FirebaseManager
import com.kirankotla.connectfour.leaderboard.model.LeaderboardEntry
import com.kirankotla.connectfour.leaderboard.model.LeaderboardTimeframe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class LeaderboardRepository {
    private val TAG = "LeaderboardRepo"
    private val firestore: FirebaseFirestore? get() = FirebaseManager.firestore

    suspend fun getLeaderboard(
        timeframe: LeaderboardTimeframe,
        currentUid: String,
        currentUserScore: Int = 8450,
        currentUserName: String = "You",
        currentUserAvatar: String = "piko"
    ): List<LeaderboardEntry> = withContext(Dispatchers.IO) {
        val db = firestore
        var remoteEntries = emptyList<LeaderboardEntry>()
        if (db != null) {
            try {
                val collectionName = when (timeframe) {
                    LeaderboardTimeframe.DAILY -> "leaderboard_daily"
                    LeaderboardTimeframe.WEEKLY -> "leaderboard_weekly"
                    LeaderboardTimeframe.MONTHLY -> "leaderboard_monthly"
                    LeaderboardTimeframe.ALL_TIME -> "public_profiles"
                }

                val snap = db.collection(collectionName)
                    .orderBy("score", Query.Direction.DESCENDING)
                    .limit(50)
                    .get()
                    .await()

                if (!snap.isEmpty) {
                    var rank = 1
                    remoteEntries = snap.documents.map { doc ->
                        val uid = doc.id
                        LeaderboardEntry(
                            rank = rank++,
                            uid = uid,
                            displayName = doc.getString("displayName") ?: "Player",
                            avatarId = doc.getString("avatarId") ?: "piko",
                            score = doc.getLong("score")?.toInt() ?: 0,
                            wins = doc.getLong("gamesWon")?.toInt() ?: 0,
                            isCurrentUser = uid == currentUid
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching Firestore leaderboard: ${e.message}")
            }
        }

        // Keep the launch-event seeded roster visible until it is manually removed.
        val (scoreMultiplier, winsMultiplier) = when (timeframe) {
            LeaderboardTimeframe.DAILY -> 0.12f to 0.14f
            LeaderboardTimeframe.WEEKLY -> 0.32f to 0.35f
            LeaderboardTimeframe.MONTHLY -> 0.68f to 0.70f
            LeaderboardTimeframe.ALL_TIME -> 1.0f to 1.0f
        }

        val scaledUserScore = (currentUserScore * scoreMultiplier).toInt().coerceAtLeast(100)
        val scaledUserWins = (52 * winsMultiplier).toInt().coerceAtLeast(1)

        val seedProfiles = listOf(
            SeedProfile("Alex", 15450, 98, "bolt"),
            SeedProfile("Rahul", 14890, 92, "sprout"),
            SeedProfile("Maya", 14120, 87, "luna"),
            SeedProfile("Marcus", 13650, 83, "ember"),
            SeedProfile("Priya", 13100, 79, "mavi"),
            SeedProfile("Elena", 12550, 75, "koko"),
            SeedProfile("Sora", 11980, 71, "pixel"),
            SeedProfile("Liam", 11420, 67, "bolt"),
            SeedProfile("Zara", 10890, 64, "sprout"),
            SeedProfile("David", 10320, 60, "ember"),
            SeedProfile("Chloe", 9810, 57, "luna"),
            SeedProfile("Ethan", 9340, 54, "mavi"),
            SeedProfile("Anya", 8890, 51, "koko"),
            SeedProfile("Lucas", 8450, 48, "pixel"),
            SeedProfile("Nina", 7980, 45, "bolt"),
            SeedProfile("Kai", 7530, 42, "sprout"),
            SeedProfile("Mia", 7120, 40, "luna"),
            SeedProfile("Oliver", 6710, 37, "ember"),
            SeedProfile("Sophia", 6320, 35, "mavi"),
            SeedProfile("Aryan", 5940, 32, "koko"),
            SeedProfile("Emma", 5580, 30, "pixel"),
            SeedProfile("Mason", 5210, 28, "bolt"),
            SeedProfile("Isabella", 4870, 26, "sprout"),
            SeedProfile("Jack", 4520, 24, "ember"),
            SeedProfile("Ava", 4210, 22, "luna"),
            SeedProfile("Logan", 3890, 20, "mavi"),
            SeedProfile("Harper", 3580, 18, "koko"),
            SeedProfile("Owen", 3290, 16, "pixel"),
            SeedProfile("Zoe", 2980, 14, "bolt"),
            SeedProfile("Leo", 2690, 12, "sprout")
        )

        val seedEntries = seedProfiles.map { profile ->
            val score = (profile.baseScore * scoreMultiplier).toInt()
            val wins = (profile.baseWins * winsMultiplier).toInt().coerceAtLeast(1)
            LeaderboardEntry(
                rank = 0,
                uid = "sim_uid_${profile.name.lowercase()}",
                displayName = profile.name,
                avatarId = profile.avatarId,
                score = score,
                wins = wins,
                isCurrentUser = false
            )
        }

        val allCandidates = remoteEntries.filter { it.uid != currentUid } + seedEntries + LeaderboardEntry(
            rank = 0,
            uid = currentUid,
            displayName = currentUserName,
            avatarId = currentUserAvatar,
            score = scaledUserScore,
            wins = scaledUserWins,
            isCurrentUser = true
        )

        var currentRank = 1
        allCandidates.distinctBy { it.uid }.sortedByDescending { it.score }.map { entry ->
            entry.copy(rank = currentRank++)
        }
    }

    private data class SeedProfile(
        val name: String,
        val baseScore: Int,
        val baseWins: Int,
        val avatarId: String
    )
}
