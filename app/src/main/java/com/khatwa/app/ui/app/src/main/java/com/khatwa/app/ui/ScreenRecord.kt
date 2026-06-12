package com.khatwa.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.khatwa.app.data.ActivityType
import com.khatwa.app.data.Prefs
import com.khatwa.app.data.Profile
import com.khatwa.app.tracking.TrackStatus
import com.khatwa.app.tracking.TrackingManager
import com.khatwa.app.tracking.TrackingService
import com.khatwa.app.util.Fmt

@SuppressLint("MissingPermission")
@Composable
fun RecordScreen(
    profile: Profile,
    onOpenProfiles: () -> Unit,
    onSaved: (String) -> Unit
) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val live by TrackingManager.state.collectAsStateWithLifecycle()
    val idle = live.status == TrackStatus.IDLE
    val isRun = live.type != ActivityType.BIKE

    var typeName by rememberSaveable { mutableStateOf(ActivityType.WALK.name) }
    val selectedType = ActivityType.from(typeName)

    // ---------------- permissions + start flow ----------------
    fun fineGranted() = ContextCompat.checkSelfPermission(
        ctx, android.Manifest.permission.ACCESS_FINE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    var hasFine by remember { mutableStateOf(fineGranted()) }
    var showBatteryDialog by remember { mutableStateOf(false) }

    val launchService = {
        Ads.preload(ctx)   // get the finish-ad ready; never shows an ad on Start
        TrackingService.start(ctx, selectedType, profile.id)
    }
    val afterNotif = {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!prefs.batteryPromptShown && !pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
            showBatteryDialog = true
        } else {
            launchService()
        }
    }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { afterNotif() }

    val startFlowAfterLocation = {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            afterNotif()
        }
    }
    val locLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasFine = fineGranted()
        if (result.values.any { it }) startFlowAfterLocation()
        else Toast.makeText(ctx, "Location permission is required to track", Toast.LENGTH_LONG).show()
    }
    val onStartClick = {
        if (!hasFine) {
            locLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            startFlowAfterLocation()
        }
    }

    // ---------------- GPS preview while idle ----------------
    var previewLat by remember { mutableStateOf<Double?>(null) }
    var previewLon by remember { mutableStateOf<Double?>(null) }
    var previewAcc by remember { mutableStateOf<Float?>(null) }

    DisposableEffect(hasFine, idle) {
        if (hasFine && idle) {
            val client = LocationServices.getFusedLocationProviderClient(ctx)
            val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L).build()
            val cb = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let {
                        previewLat = it.latitude
                        previewLon = it.longitude
                        previewAcc = if (it.hasAccuracy()) it.accuracy else null
                    }
                }
            }
            try {
                client.requestLocationUpdates(req, cb, Looper.getMainLooper())
            } catch (_: SecurityException) { }
            onDispose { client.removeLocationUpdates(cb) }
        } else {
            onDispose { }
        }
    }

    // ---------------- Google Map + follow camera ----------------
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(MapDefaults.START, MapDefaults.START_ZOOM)
    }
    var follow by remember { mutableStateOf(true) }

    // panning / pinching by the user turns follow off
    LaunchedEffect(Unit) {
        snapshotFlow { cameraState.cameraMoveStartedReason }
            .collect { reason ->
                if (reason == CameraMoveStartedReason.GESTURE) follow = false
            }
    }

    val lastPoint = live.points.lastOrNull()
    val focusLat = lastPoint?.lat ?: previewLat
    val focusLon = lastPoint?.lon ?: previewLon

    // while follow is on, keep the camera on the runner at medium zoom
    LaunchedEffect(follow, focusLat, focusLon) {
        if (follow && focusLat != null && focusLon != null) {
            try {
                cameraState.animate(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(focusLat, focusLon), MapDefaults.FOLLOW_ZOOM
                    ),
                    700
                )
            } catch (_: Exception) { }
        }
    }

    // finished -> show interstitial, THEN open the summary
    LaunchedEffect(live.savedId) {
        if (live.savedId != null) {
            TrackingManager.consumeSaved()?.let { id ->
                Ads.showThen(ctx.findActivity()) { onSaved(id) }
            }
        }
    }
    LaunchedEffect(live.message) {
        if (live.message != null) {
            TrackingManager.consumeMessage()?.let {
                Toast.makeText(ctx, it, Toast.LENGTH_LONG).show()
            }
        }
    }

    var showStopDialog by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(NightBg)) {

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraState,
            properties = MapProperties(
                isMyLocationEnabled = hasFine,
                mapType = MapType.NORMAL
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                compassEnabled = false,
                mapToolbarEnabled = false
            )
        ) {
            if (live.points.size >= 2) {
                Polyline(
                    points = live.points.map { LatLng(it.lat, it.lon) },
                    color = Ember,
                    width = 14f,
                    startCap = RoundCap(),
                    endCap = RoundCap(),
                    jointType = JointType.ROUND
                )
            }
        }

        // ---------------- top overlay ----------------
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Surface1.copy(alpha = 0.94f),
                modifier = Modifier.clip(RoundedCornerShape(50))
                    .clickable(enabled = idle) { onOpenProfiles() }
            ) {
                Row(
                    Modifier.padding(start = 6.dp, end = 13.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(profile, 28)
                    Spacer(Modifier.width(8.dp))
                    Text(profile.name, style = MaterialTheme.typography.labelLarge, color = Sand)
                }
            }
            Spacer(Modifier.weight(1f))
            GpsPill(if (idle) previewAcc else live.gpsAccuracyM)
        }

        // ---------------- bottom ----------------
        Column(Modifier.align(Alignment.BottomCenter)) {

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = CircleShape,
                    color = if (follow) Ember else Surface1.copy(alpha = 0.94f),
                    modifier = Modifier.size(46.dp).clip(CircleShape).clickable {
                        follow = true
                        // LaunchedEffect above reacts and zooms back onto the runner
                    }
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.Filled.MyLocation, contentDescription = "Recenter",
                            tint = if (follow) Color(0xFF160B30) else Sand,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            if (idle) {
                Column(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TypeSelector(selectedType, enabled = true) { typeName = it.name }
                    PulseStartButton(enabled = true, onClick = onStartClick)
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = Surface1
                ) {
                    Column(
                        Modifier.fillMaxWidth().navigationBarsPadding()
                            .padding(horizontal = 22.dp, vertical = 18.dp)
                    ) {
                        if (live.status == TrackStatus.AUTO_PAUSED) {
                            TinyPill("AUTO-PAUSED — MOVE TO RESUME", Amber)
                            Spacer(Modifier.height(10.dp))
                        } else if (live.status == TrackStatus.PAUSED) {
                            TinyPill("PAUSED", Muted)
                            Spacer(Modifier.height(10.dp))
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatBlock(Fmt.duration(live.movingMs / 1000), "Time", big = true)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                AnimatedNumber(
                                    value = (live.distanceM / 1000.0).toFloat(),
                                    format = { String.format(java.util.Locale.US, "%.2f", it) },
                                    style = MaterialTheme.typography.displayMedium
                                )
                                Text("KM", style = MaterialTheme.typography.labelSmall, color = Muted)
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatBlock(
                                if (isRun) Fmt.paceFromSpeed(live.speedMps) else Fmt.speedKmh(live.speedMps),
                                if (isRun) "Pace /km" else "km/h"
                            )
                            StatBlock("${live.elevGainM.toInt()} m", "Elev gain")
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                AnimatedNumber(
                                    value = live.calories.toFloat(),
                                    format = { "${it.toInt()}" },
                                    style = MaterialTheme.typography.displaySmall,
                                    color = EmberGlow
                                )
                                Text("KCAL", style = MaterialTheme.typography.labelSmall, color = Muted)
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = {
                                    if (live.status == TrackStatus.PAUSED)
                                        TrackingService.send(ctx, TrackingService.ACTION_RESUME)
                                    else
                                        TrackingService.send(ctx, TrackingService.ACTION_PAUSE)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Surface2, contentColor = Sand
                                ),
                                modifier = Modifier.weight(1f).height(52.dp)
                            ) {
                                Icon(
                                    if (live.status == TrackStatus.PAUSED) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                    contentDescription = null
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(if (live.status == TrackStatus.PAUSED) "Resume" else "Pause")
                            }
                            Button(
                                onClick = { showStopDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Danger, contentColor = Color(0xFF2A0606)
                                ),
                                modifier = Modifier.weight(1f).height(52.dp)
                            ) {
                                Icon(Icons.Filled.Stop, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Stop")
                            }
                        }
                    }
                }
            }
        }
    }

    // ---------------- dialogs ----------------
    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            containerColor = Surface1,
            title = { Text("Finish activity?", color = Sand) },
            text = { Text("Save it to your history, or discard it completely.", color = Muted) },
            confirmButton = {
                TextButton(onClick = {
                    showStopDialog = false
                    TrackingService.send(ctx, TrackingService.ACTION_FINISH)
                }) { Text("Finish & Save", color = Ember) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showStopDialog = false
                        TrackingService.send(ctx, TrackingService.ACTION_DISCARD)
                    }) { Text("Discard", color = Danger) }
                    TextButton(onClick = { showStopDialog = false }) { Text("Cancel", color = Muted) }
                }
            }
        )
    }

    if (showBatteryDialog) {
        AlertDialog(
            onDismissRequest = { },
            containerColor = Surface1,
            title = { Text("Keep tracking alive", color = Sand) },
            text = {
                Text(
                    "Some phones (especially Samsung) kill background apps to save battery, which freezes GPS tracking when the screen is off. Allow Khatwa to ignore battery optimization so your full route is recorded.",
                    color = Muted
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.batteryPromptShown = true
                    showBatteryDialog = false
                    try {
                        ctx.startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${ctx.packageName}")
                            )
                        )
                    } catch (_: Exception) { }
                    launchService()
                }) { Text("Allow", color = Ember) }
            },
            dismissButton = {
                TextButton(onClick = {
                    prefs.batteryPromptShown = true
                    showBatteryDialog = false
                    launchService()
                }) { Text("Not now", color = Muted) }
            }
        )
    }
}
