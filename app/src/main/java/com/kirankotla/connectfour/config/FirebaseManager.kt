package com.kirankotla.connectfour.config

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.analytics.FirebaseAnalytics

object FirebaseManager {
    private const val TAG = "FirebaseManager"
    var isInitialized: Boolean = false
        private set

    var auth: FirebaseAuth? = null
        private set
    var firestore: FirebaseFirestore? = null
        private set
    var realtimeDatabase: FirebaseDatabase? = null
        private set
    var analytics: FirebaseAnalytics? = null
        private set

    fun initialize(context: Context, environment: Environment = Environment.current) {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                try {
                    FirebaseApp.initializeApp(context)
                } catch (_: Exception) {
                    val options = com.google.firebase.FirebaseOptions.Builder()
                        .setApplicationId("1:756945363546:android:ea60c418e059b1784c2e63")
                        .setApiKey("AIzaSyCj0ZvrzOmUsMC3v79wLrVCA8-Y6bXBKCQ")
                        .setProjectId("connectfour-game-app")
                        .setStorageBucket("connectfour-game-app.firebasestorage.app")
                        .setDatabaseUrl("https://connectfour-game-app-default-rtdb.firebaseio.com")
                        .build()
                    FirebaseApp.initializeApp(context, options)
                }
            }
            auth = FirebaseAuth.getInstance()
            firestore = FirebaseFirestore.getInstance().apply {
                firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .build()
            }
            realtimeDatabase = FirebaseDatabase.getInstance().apply {
                setPersistenceEnabled(true)
            }
            analytics = try {
                FirebaseAnalytics.getInstance(context)
            } catch (e: Exception) {
                Log.w(TAG, "Analytics init skipped: ${e.message}")
                null
            }

            if (environment.useEmulators) {
                auth?.useEmulator("10.0.2.2", 9099)
                firestore?.useEmulator("10.0.2.2", 8080)
                realtimeDatabase?.useEmulator("10.0.2.2", 9000)
            }

            isInitialized = true
            Log.i(TAG, "Firebase initialized successfully for env ${environment.name}")
        } catch (e: Exception) {
            Log.w(TAG, "Firebase initialization failed or unconfigured, running in offline/mock mode: ${e.message}")
            isInitialized = false
        }
    }
}
