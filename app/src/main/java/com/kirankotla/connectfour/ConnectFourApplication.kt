package com.kirankotla.connectfour

import android.app.Application
import com.kirankotla.connectfour.config.FirebaseManager

class ConnectFourApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseManager.initialize(this)
    }
}
