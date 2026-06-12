package com.khatwa.app.util

import com.khatwa.app.data.ActivityType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Fmt {

    /** meters -> "4.16" */
    fun km(meters: Double): String = String.format(Locale.US, "%.2f", meters / 1000.0)

    /** seconds -> "50:52" or "1:02:09" */
    fun duration(totalSec: Long): String {
        val s = if (totalSec < 0) 0 else totalSec
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
        else String.format(Locale.US, "%d:%02d", m, sec)
    }

    fun duration(totalSec: Int): String = duration(totalSec.toLong())

    /** seconds per km -> "12:14" (with /km suffix added by caller) */
    fun pace(secPerKm: Int): String {
        if (secPerKm <= 0 || secPerKm > 5400) return "--:--"
        return String.format(Locale.US, "%d:%02d", secPerKm / 60, secPerKm % 60)
    }

    /** m/s -> live pace string */
    fun paceFromSpeed(speedMps: Double): String {
        if (speedMps < 0.3) return "--:--"
        return pace((1000.0 / speedMps).toInt())
    }

    /** m/s -> "11.3" km/h */
    fun speedKmh(speedMps: Double): String = String.format(Locale.US, "%.1f", speedMps * 3.6)

    fun kmh(kmh: Double): String = String.format(Locale.US, "%.1f", kmh)

    /** "May 25, 2026 at 9:55 PM" */
    fun dateLine(epochMs: Long): String =
        SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.US).format(Date(epochMs))

    fun dayLabel(epochMs: Long): String =
        SimpleDateFormat("EEE", Locale.US).format(Date(epochMs))

    /** "Night Walk", "Morning Run", "Evening Ride" — from start hour. */
    fun autoTitle(epochMs: Long, type: ActivityType): String {
        val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val part = when {
            h < 5 -> "Late Night"
            h < 11 -> "Morning"
            h < 17 -> "Afternoon"
            h < 21 -> "Evening"
            else -> "Night"
        }
        val noun = when (type) {
            ActivityType.WALK -> "Walk"
            ActivityType.RUN -> "Run"
            ActivityType.BIKE -> "Ride"
        }
        return "$part $noun"
    }
}
