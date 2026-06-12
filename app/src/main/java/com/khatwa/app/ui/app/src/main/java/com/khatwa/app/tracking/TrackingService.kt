package com.khatwa.app.tracking

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.khatwa.app.KhatwaApp
import com.khatwa.app.MainActivity
import com.khatwa.app.data.ActivityStore
import com.khatwa.app.data.ActivitySummary
import com.khatwa.app.data.ActivityType
import com.khatwa.app.data.Profile
import com.khatwa.app.data.ProfileStore
import com.khatwa.app.data.Split
import com.khatwa.app.data.TrackPoint
import com.khatwa.app.util.Fmt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

class TrackingService : LifecycleService() {

    companion object {
        const val ACTION_START = "com.khatwa.app.START"
        const val ACTION_PAUSE = "com.khatwa.app.PAUSE"
        const val ACTION_RESUME = "com.khatwa.app.RESUME"
        const val ACTION_FINISH = "com.khatwa.app.FINISH"
        const val ACTION_DISCARD = "com.khatwa.app.DISCARD"
        const val EXTRA_TYPE = "type"
        const val EXTRA_PROFILE = "profileId"
        const val NOTIF_ID = 41

        fun start(ctx: Context, type: ActivityType, profileId: String) {
            val i = Intent(ctx, TrackingService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_TYPE, type.name)
                .putExtra(EXTRA_PROFILE, profileId)
            ContextCompat.startForegroundService(ctx, i)
        }

        fun send(ctx: Context, action: String) {
            ctx.startService(Intent(ctx, TrackingService::class.java).setAction(action))
        }
    }

    private var fused: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var ticker: Job? = null

    // --- per-activity state (service is the only writer) ---
    private var profile: Profile? = null
    private var type: ActivityType = ActivityType.WALK
    private var startWallMs = 0L
    private var lastTickMs = 0L
    private var movingMs = 0L
    private var distanceM = 0.0
    private var calories = 0.0
    private var maxSpeedMps = 0.0
    private var lastAccepted: Location? = null
    private var lastAcceptedRtMs = 0L
    private var rejectStreak = 0
    private var stillSinceMs: Long? = null
    private var tickCount = 0
    private val speedEma = SpeedEma()
    private val elevation = ElevationFilter()
    private val points = ArrayList<TrackPoint>(4096)
    private var lastStoredDist = -10.0
    private var lastStoredT = -10_000L
    private val splits = ArrayList<Split>()
    private var movingMsAtLastSplit = 0L
    private var nextSplitKm = 1

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> startTracking(intent)
            ACTION_PAUSE -> setManualPause(true)
            ACTION_RESUME -> setManualPause(false)
            ACTION_FINISH -> finish(save = true)
            ACTION_DISCARD -> finish(save = false)
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    // ------------------------------------------------------------- start

    private fun startTracking(intent: Intent) {
        val profileId = intent.getStringExtra(EXTRA_PROFILE) ?: return stopSelf()
        profile = ProfileStore(this).get(profileId) ?: return stopSelf()
        type = ActivityType.from(intent.getStringExtra(EXTRA_TYPE))

        resetState()
        startWallMs = System.currentTimeMillis()
        lastTickMs = SystemClock.elapsedRealtime()

        TrackingManager.update {
            LiveTrack(status = TrackStatus.TRACKING, type = type, startEpochMs = startWallMs)
        }

        ServiceCompat.startForeground(
            this, NOTIF_ID, buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )

        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "khatwa:tracking")
            .apply { acquire(6 * 60 * 60 * 1000L) }

        requestLocationUpdates()

        ticker = lifecycleScope.launch {
            while (isActive) {
                delay(1000)
                tick()
            }
        }
    }

    private fun resetState() {
        movingMs = 0L; distanceM = 0.0; calories = 0.0; maxSpeedMps = 0.0
        lastAccepted = null; lastAcceptedRtMs = 0L; rejectStreak = 0
        stillSinceMs = null; tickCount = 0
        speedEma.reset(); elevation.reset()
        points.clear(); lastStoredDist = -10.0; lastStoredT = -10_000L
        splits.clear(); movingMsAtLastSplit = 0L; nextSplitKm = 1
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf(); return
        }
        fused = LocationServices.getFusedLocationProviderClient(this)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(600L)
            .setWaitForAccurateLocation(false)
            .build()
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { onPoint(it) }
            }
        }
        fused?.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
    }

    // ------------------------------------------------------------- ticker

    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        val dt = now - lastTickMs
        lastTickMs = now
        val status = TrackingManager.state.value.status
        if (status == TrackStatus.TRACKING) movingMs += dt
        if (status == TrackStatus.IDLE) return

        TrackingManager.update {
            it.copy(
                elapsedMs = System.currentTimeMillis() - startWallMs,
                movingMs = movingMs
            )
        }
        tickCount++
        if (tickCount % 3 == 0) notifyUpdate()
    }

    // ------------------------------------------------------------- GPS pipeline

    private fun onPoint(loc: Location) {
        val status = TrackingManager.state.value.status
        if (status == TrackStatus.IDLE) return

        val accuracy = if (loc.hasAccuracy()) loc.accuracy else 999f
        if (accuracy > GpsRules.accuracyLimitM(type)) {
            TrackingManager.update { it.copy(gpsAccuracyM = accuracy) }
            return
        }

        val nowRt = SystemClock.elapsedRealtime()
        val last = lastAccepted

        // Derived speed for sanity checks
        val dtSec = if (last != null) ((nowRt - lastAcceptedRtMs) / 1000.0).coerceIn(0.05, 30.0) else 0.0
        val rawDist = if (last != null) last.distanceTo(loc).toDouble() else 0.0
        val derived = if (dtSec > 0) rawDist / dtSec else 0.0

        // Teleport rejection (GPS jump through a building, tunnel re-acquire, etc.)
        if (last != null && derived > GpsRules.maxPlausibleSpeed(type)) {
            rejectStreak++
            if (rejectStreak >= 3) {           // GPS really moved: re-anchor, add nothing
                lastAccepted = loc; lastAcceptedRtMs = nowRt; rejectStreak = 0
            }
            return
        }
        rejectStreak = 0

        val chipSpeed = if (loc.hasSpeed() && loc.speed >= 0f) loc.speed.toDouble() else derived
        val smSpeed = speedEma.update(chipSpeed)
        if (smSpeed > maxSpeedMps) maxSpeedMps = smSpeed

        // ---- auto-pause state machine (manual pause has priority) ----
        var newStatus = status
        if (status != TrackStatus.PAUSED) {
            if (smSpeed < GpsRules.autoPauseBelow(type)) {
                if (stillSinceMs == null) stillSinceMs = nowRt
                if (nowRt - (stillSinceMs ?: nowRt) > 4000 && status == TrackStatus.TRACKING) {
                    newStatus = TrackStatus.AUTO_PAUSED
                }
            } else if (smSpeed > GpsRules.autoResumeAbove(type)) {
                stillSinceMs = null
                if (status == TrackStatus.AUTO_PAUSED) {
                    newStatus = TrackStatus.TRACKING
                    lastAccepted = loc; lastAcceptedRtMs = nowRt   // anchor: paused drift not counted
                }
            }
        }

        var ele = elevation.current

        if (newStatus == TrackStatus.TRACKING) {
            if (last == null) {
                lastAccepted = loc; lastAcceptedRtMs = nowRt
                ele = elevation.feed(if (loc.hasAltitude()) loc.altitude else null, distanceM)
                appendPoint(loc, ele, smSpeed)
            } else {
                val jitter = rawDist < 1.5 && smSpeed < 0.5
                if (!jitter) {
                    distanceM += rawDist
                    ele = elevation.feed(if (loc.hasAltitude()) loc.altitude else null, distanceM)
                    val p = profile
                    if (p != null) {
                        calories += Calories.kcalForSegment(
                            p, type, smSpeed, elevation.grade(),
                            dtMin = (dtSec / 60.0).coerceAtMost(0.25),
                            climbM = elevation.lastClimbDelta
                        )
                    }
                    checkSplit()
                    appendPoint(loc, ele, smSpeed)
                    lastAccepted = loc; lastAcceptedRtMs = nowRt
                } else {
                    ele = elevation.feed(if (loc.hasAltitude()) loc.altitude else null, distanceM)
                }
            }
        }

        val snapshot = points.toList()
        TrackingManager.update {
            it.copy(
                status = newStatus,
                distanceM = distanceM,
                calories = calories,
                speedMps = if (newStatus == TrackStatus.TRACKING) smSpeed else 0.0,
                maxSpeedMps = maxSpeedMps,
                elevGainM = elevation.gain,
                elevLossM = elevation.loss,
                currentEleM = ele,
                points = snapshot,
                splits = splits.toList(),
                gpsAccuracyM = accuracy
            )
        }
    }

    private fun appendPoint(loc: Location, ele: Double, speed: Double) {
        val t = System.currentTimeMillis() - startWallMs
        if (distanceM - lastStoredDist >= 2.0 || t - lastStoredT >= 5000) {
            points.add(
                TrackPoint(
                    lat = loc.latitude, lon = loc.longitude,
                    ele = if (ele.isNaN()) 0.0 else ele,
                    t = t, speed = speed.toFloat(), dist = distanceM.toFloat()
                )
            )
            lastStoredDist = distanceM
            lastStoredT = t
        }
    }

    private fun checkSplit() {
        while (distanceM >= nextSplitKm * 1000.0) {
            val sec = ((movingMs - movingMsAtLastSplit) / 1000.0).toInt()
            splits.add(Split(nextSplitKm, sec))
            movingMsAtLastSplit = movingMs
            nextSplitKm++
        }
    }

    // ------------------------------------------------------------- pause / finish

    private fun setManualPause(paused: Boolean) {
        TrackingManager.update {
            if (it.status == TrackStatus.IDLE) it
            else it.copy(status = if (paused) TrackStatus.PAUSED else TrackStatus.TRACKING, speedMps = 0.0)
        }
        if (!paused) {
            // re-anchor so the gap walked while paused is not counted
            lastAccepted = null
            speedEma.reset()
            stillSinceMs = null
        }
        notifyUpdate()
    }

    private fun finish(save: Boolean) {
        ticker?.cancel()
        locationCallback?.let { fused?.removeLocationUpdates(it) }

        val p = profile
        val movingSec = (movingMs / 1000).toInt()

        if (save && p != null && distanceM >= 30.0 && movingSec >= 30) {
            val id = UUID.randomUUID().toString().substring(0, 12)
            val fastest = splits.minOfOrNull { it.seconds } ?: 0
            val summary = ActivitySummary(
                id = id,
                profileId = p.id,
                type = type,
                title = Fmt.autoTitle(startWallMs, type),
                startEpochMs = startWallMs,
                distanceM = distanceM,
                movingSec = movingSec,
                elapsedSec = ((System.currentTimeMillis() - startWallMs) / 1000).toInt(),
                calories = calories,
                elevGainM = elevation.gain,
                elevLossM = elevation.loss,
                maxElevM = if (elevation.maxEle == -Double.MAX_VALUE) 0.0 else elevation.maxEle,
                avgSpeedKmh = if (movingSec > 0) distanceM / movingSec * 3.6 else 0.0,
                maxSpeedKmh = maxSpeedMps * 3.6,
                fastestSplitSec = fastest,
                splits = splits.toList()
            )
            ActivityStore(this).save(summary, points.toList())
            TrackingManager.update { LiveTrack(savedId = id) }
        } else {
            val msg = if (!save) "Activity discarded"
            else "Too short to save (under 30 m / 30 s)"
            TrackingManager.update { LiveTrack(message = msg) }
        }

        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        ticker?.cancel()
        locationCallback?.let { fused?.removeLocationUpdates(it) }
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    // ------------------------------------------------------------- notification

    private fun action(label: String, act: String, code: Int): NotificationCompat.Action {
        val pi = PendingIntent.getService(
            this, code,
            Intent(this, TrackingService::class.java).setAction(act),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Action(0, label, pi)
    }

    private fun buildNotification(): Notification {
        val live = TrackingManager.state.value
        val contentPi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val statusTxt = when (live.status) {
            TrackStatus.PAUSED -> " · paused"
            TrackStatus.AUTO_PAUSED -> " · auto-paused"
            else -> ""
        }
        val text = "${Fmt.km(live.distanceM)} km · ${Fmt.duration(live.movingMs / 1000)} · ${live.calories.toInt()} kcal$statusTxt"
        val b = NotificationCompat.Builder(this, KhatwaApp.CHANNEL_TRACKING)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("${type.label} in progress")
            .setContentText(text)
            .setContentIntent(contentPi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (live.status == TrackStatus.PAUSED) {
            b.addAction(action("Resume", ACTION_RESUME, 2))
        } else {
            b.addAction(action("Pause", ACTION_PAUSE, 1))
        }
        b.addAction(action("Finish", ACTION_FINISH, 3))
        return b.build()
    }

    private fun notifyUpdate() {
        val nm = androidx.core.app.NotificationManagerCompat.from(this)
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED || android.os.Build.VERSION.SDK_INT < 33
        ) {
            try { nm.notify(NOTIF_ID, buildNotification()) } catch (_: SecurityException) { }
        }
    }
}
