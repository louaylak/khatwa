package com.khatwa.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import org.osmdroid.config.Configuration
import org.osmdroid.mapsforge.MapsForgeTileSource
import java.io.File

class KhatwaApp : Application() {

    companion object {
        const val CHANNEL_TRACKING = "tracking"
    }

    override fun onCreate() {
        super.onCreate()

        // osmdroid: keep everything in app-private storage (no storage permission needed)
        val cfg = Configuration.getInstance()
        cfg.load(this, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        cfg.userAgentValue = packageName
        cfg.osmdroidBasePath = File(filesDir, "osmdroid").apply { mkdirs() }
        cfg.osmdroidTileCache = File(cacheDir, "tiles").apply { mkdirs() }

        // mapsforge offline renderer (must be initialized once before creating tile sources)
        try {
            MapsForgeTileSource.createInstance(this)
        } catch (_: Exception) {
            // online fallback still works if this ever fails
        }

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
