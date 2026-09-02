package com.muzziq.mobile

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.muzziq.mobile.data.AppPrefs

class MuzziQApplication : Application() {
    lateinit var prefs: AppPrefs
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = AppPrefs(this)
        createPlaybackChannel()
    }

    private fun createPlaybackChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                PLAYBACK_CHANNEL_ID,
                "Lecture MuzziQ",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Notification de lecture en cours" }
        )
    }

    companion object {
        const val PLAYBACK_CHANNEL_ID = "muzziq_playback"
    }
}
