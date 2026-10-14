package com.kirankotla.connectfour.achievements

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.kirankotla.connectfour.analytics.ConnectFourAnalytics
import com.kirankotla.connectfour.config.FirebaseManager
import com.kirankotla.connectfour.data.AchievementEntity
import com.kirankotla.connectfour.data.ConnectFourDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class AchievementSyncManager(
    private val dao: ConnectFourDao
) {
    private val TAG = "AchievementSyncManager"
    private val firestore: FirebaseFirestore? get() = FirebaseManager.firestore

    suspend fun unlockAchievement(achievementId: String, title: String) = withContext(Dispatchers.IO) {
        // Unlock locally in Room
        val existing = dao.getAchievement(achievementId)
        val updated = (existing ?: AchievementEntity(achievementId)).copy(
            isUnlocked = true,
            unlockedAtMs = System.currentTimeMillis()
        )
        dao.insertOrUpdateAchievement(updated)

        // Log analytics
        ConnectFourAnalytics.logAchievementUnlocked(achievementId, title)

        // Sync with Firestore if user is authenticated
        val uid = FirebaseManager.auth?.currentUser?.uid
        val db = firestore
        if (db != null && uid != null && uid != "local_player") {
            try {
                db.collection("users").document(uid).update(
                    "achievements", FieldValue.arrayUnion(achievementId)
                ).await()
            } catch (e: Exception) {
                Log.w(TAG, "Error syncing achievement $achievementId to cloud: ${e.message}")
            }
        }
    }

    suspend fun syncFromCloud(uid: String) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            val doc = db.collection("users").document(uid).get().await()
            val cloudAchievements = (doc.get("achievements") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            for (achId in cloudAchievements) {
                val local = dao.getAchievement(achId)
                if (local == null || !local.isUnlocked) {
                    dao.insertOrUpdateAchievement(
                        AchievementEntity(
                            achievementId = achId,
                            isUnlocked = true,
                            unlockedAtMs = System.currentTimeMillis()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to restore cloud achievements: ${e.message}")
        }
    }
}
