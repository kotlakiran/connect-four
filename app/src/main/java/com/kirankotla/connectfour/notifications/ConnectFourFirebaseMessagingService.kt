package com.kirankotla.connectfour.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.kirankotla.connectfour.MainActivity
import com.kirankotla.connectfour.config.FirebaseManager

class ConnectFourFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token generated: $token")
        val uid = FirebaseManager.auth?.currentUser?.uid ?: return
        val firestore = FirebaseManager.firestore ?: return
        firestore.collection("users").document(uid)
            .update("fcmToken", token)
            .addOnFailureListener {
                Log.w(TAG, "Failed to update FCM token in user document: ${it.message}")
            }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "From: ${remoteMessage.from}")

        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "Connect Four Match Update"
        val message = remoteMessage.notification?.body ?: remoteMessage.data["message"] ?: "Your turn or game invitation is waiting."
        val gameId = remoteMessage.data["gameId"]

        showNotification(title, message, gameId)
    }

    private fun showNotification(title: String, message: String, gameId: String?) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Game & Matchmaking Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Invites, opponent ready status, and game start notifications"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (gameId != null) {
                putExtra("nav_game_id", gameId)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    companion object {
        private const val TAG = "ConnectFourFCM"
        const val CHANNEL_ID = "connectfour_game_alerts"
    }
}
