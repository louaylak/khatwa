package com.khatwa.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.khatwa.app.ui.Ads
import org.maplibre.android.MapLibre

class KhatwaApp : Application() {

    companion object {
        const val CHANNEL_TRACKING = "tracking"
    }

    override fun onCreate() {
        super.onCreate()

        // MapLibre must be initialized before any MapView is created
        MapLibre.getInstance(this)

        // AdMob: initialize early so the app-open interstitial is ready fast
        Ads.init(this)

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TRACKING,
                "Activity tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Live stats while recording an activity" }
        )
    }
}
