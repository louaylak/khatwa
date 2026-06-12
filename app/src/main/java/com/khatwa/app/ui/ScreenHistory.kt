package com.khatwa.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.toArgb
import com.khatwa.app.data.ActivityStore
import com.khatwa.app.data.ActivitySummary
import com.khatwa.app.data.ActivityType
import com.khatwa.app.data.TrackPoint
import com.khatwa.app.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import kotlin.math.max

// ================================================================ history list

@Composable
fun HistoryScreen(profileId: String, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    var activities by remember { mutableStateOf<List<ActivitySummary>>(emptyList()) }
    LaunchedEffect(profileId) {
        activities = withContext(Dispatchers.IO) { ActivityStore(ctx).list(profileId) }
    }

    Column(
        Modifier.fillMaxSize().background(NightBg).statusBarsPadding()
    ) {
        Row(
            Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("History", style = MaterialTheme.typography.headlineMedium, color = Sand)
            Spacer(Modifier.weight(1f))
            if (activities.isNotEmpty()) {
                TinyPill("${activities.size} ${if (activities.size == 1) "ACTIVITY" else "ACTIVITIES"}", Muted)
            }
        }

        if (activities.isEmpty()) {
            EmptyState(
                Icons.Filled.History,
                "Nothing recorded yet",
                "Hit the ember button on the Record tab and your activities will appear here."
            )
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, bottom = 20.dp
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(activities, key = { it.id }) { a ->
                    HistoryCard(a, onOpen)
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(a: ActivitySummary, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    val preview by produceState(initialValue = emptyList<Pair<Double, Double>>(), a.id) {
        value = withContext(Dispatchers.IO) {
            val r = ActivityStore(ctx).loadRoute(a.id)
            val step = (r.size / 240).coerceAtLeast(1)
            r.filterIndexed { i, _ -> i % step == 0 }.map { it.lat to it.lon }
        }
    }
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Surface1,
        modifier = Modifier.fillMaxWidth().clickable { onOpen(a.id) }
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Surface2),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(typeIcon(a.type), contentDescription = a.type.label, tint = Ember, modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(a.title, style = MaterialTheme.typography.titleMedium, color = Sand)
                    Text(Fmt.dateLine(a.startEpochMs), style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (preview.size >= 2) {
                RoutePreview(
                    preview,
                    modifier = Modifier.fillMaxWidth().height(140.dp)
                )
                Spacer(Modifier.height(12.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MiniStat(Fmt.km(a.distanceM), "km")
                MiniStat(Fmt.duration(a.movingSec), "time")
                if (a.type == ActivityType.BIKE) {
                    MiniStat(Fmt.kmh(a.avgSpeedKmh), "km/h avg")
                } else {
                    MiniStat(Fmt.pace(paceSecPerKm(a)), "/km")
                }
                MiniStat("${a.calories.toInt()}", "kcal")
            }
        }
    }
}

@Composable
private fun MiniStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = Sand)
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Muted)
    }
}

private fun paceSecPerKm(a: ActivitySummary): Int =
    if (a.distanceM < 1.0) 0 else (a.movingSec / (a.distanceM / 1000.0)).toInt()

// ================================================================ detail

private class Series(
    val xs: List<Float>,
    val ele: List<Float>,
    val paceXs: List<Float>,
    val pace: List<Float>,
    val speed: List<Float>
)

private fun buildSeries(route: List<TrackPoint>): Series {
    if (route.size < 3) return Series(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    val totalM = route.last().dist
    val bucketM = max(40f, totalM / 110f)
    val xs = ArrayList<Float>()
    val ele = ArrayList<Float>()
    val paceXs = ArrayList<Float>()
    val pace = ArrayList<Float>()
    val speed = ArrayList<Float>()

    var prev = route.first()
    xs.add(prev.dist / 1000f); ele.add(prev.ele.toFloat())
    var nextEdge = prev.dist + bucketM
    for (p in route) {
        if (p.dist >= nextEdge) {
            xs.add(p.dist / 1000f)
            ele.add(p.ele.toFloat())
            val dd = (p.dist - prev.dist).toDouble()
            val dt = (p.t - prev.t) / 1000.0
            if (dd > 1.0 && dt > 0.5) {
                paceXs.add(p.dist / 1000f)
                pace.add((dt / (dd / 1000.0)).toFloat())
                speed.add((dd / dt * 3.6).toFloat())
            }
            prev = p
            nextEdge = p.dist + bucketM
        }
    }
    // cap pace spikes so one GPS hiccup does not flatten the chart
    if (pace.isNotEmpty()) {
        val sorted = pace.sorted()
        val p90 = sorted[((sorted.size - 1) * 0.9f).toInt()]
        val cap = (p90 * 1.35f).coerceAtMost(2700f)
        for (i in pace.indices) if (pace[i] > cap) pace[i] = cap
    }
    return Series(xs, ele, paceXs, pace, speed)
}

@Composable
fun DetailScreen(activityId: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { ActivityStore(ctx) }
    var summary by remember { mutableStateOf(store.get(activityId)) }
    val route by produceState(initialValue = emptyList<TrackPoint>(), activityId) {
        value = withContext(Dispatchers.IO) { store.loadRoute(activityId) }
    }
    val series = remember(route) { buildSeries(route) }

    var showRename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val a = summary
    if (a == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val isBike = a.type == ActivityType.BIKE
    val avgPace = paceSecPerKm(a)
    val avgElapsedPace = if (a.distanceM < 1.0) 0 else (a.elapsedSec / (a.distanceM / 1000.0)).toInt()

    Column(
        Modifier.fillMaxSize().background(NightBg).statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Sand)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showRename = true }) {
                Icon(Icons.Filled.Edit, contentDescription = "Rename", tint = Muted)
            }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Muted)
            }
        }

        Column(Modifier.padding(horizontal = 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(typeIcon(a.type), contentDescription = null, tint = Ember, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(a.title, style = MaterialTheme.typography.headlineSmall, color = Sand)
            }
            Text(Fmt.dateLine(a.startEpochMs), style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Spacer(Modifier.height(14.dp))

        // ------------- map with route draw-in -------------
        DetailMap(route)

        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatBlock(Fmt.km(a.distanceM), "Distance", big = true)
            StatBlock(Fmt.duration(a.movingSec), "Moving time", big = true)
        }
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (isBike) StatBlock("${Fmt.kmh(a.avgSpeedKmh)}", "Avg km/h")
            else StatBlock(Fmt.pace(avgPace), "Avg pace /km")
            StatBlock("${a.elevGainM.toInt()} m", "Elev gain")
            StatBlock("${a.calories.toInt()}", "Calories")
        }
        Spacer(Modifier.height(20.dp))

        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

            if (series.xs.size >= 2) {
                SectionCard("Elevation") {
                    LineChart(
                        xs = series.xs, ys = series.ele,
                        lineColor = Teal,
                        yLabel = { "${it.toInt()} m" }
                    )
                    Spacer(Modifier.height(8.dp))
                    KeyRow("Elevation Gain", "${a.elevGainM.toInt()} m")
                    KeyRow("Elevation Loss", "${a.elevLossM.toInt()} m")
                    KeyRow("Max Elevation", "${a.maxElevM.toInt()} m", divider = false)
                }
            }

            if (!isBike && series.pace.size >= 2) {
                SectionCard("Pace") {
                    LineChart(
                        xs = series.paceXs, ys = series.pace,
                        lineColor = Ember,
                        inverted = true,
                        avgValue = avgPace.toFloat(),
                        yLabel = { Fmt.pace(it.toInt()) }
                    )
                    Spacer(Modifier.height(8.dp))
                    KeyRow("Avg Pace", "${Fmt.pace(avgPace)} /km")
                    KeyRow("Moving Time", Fmt.duration(a.movingSec))
                    KeyRow("Avg Elapsed Pace", "${Fmt.pace(avgElapsedPace)} /km")
                    KeyRow("Elapsed Time", Fmt.duration(a.elapsedSec))
                    KeyRow(
                        "Fastest Split",
                        if (a.fastestSplitSec > 0) "${Fmt.pace(a.fastestSplitSec)} /km" else "—",
                        divider = false
                    )
                }
            }

            if (isBike && series.speed.size >= 2) {
                SectionCard("Speed") {
                    LineChart(
                        xs = series.paceXs, ys = series.speed,
                        lineColor = Ember,
                        avgValue = a.avgSpeedKmh.toFloat(),
                        yLabel = { "${Fmt.kmh(it.toDouble())} km/h" }
                    )
                    Spacer(Modifier.height(8.dp))
                    KeyRow("Avg Speed", "${Fmt.kmh(a.avgSpeedKmh)} km/h")
                    KeyRow("Max Speed", "${Fmt.kmh(a.maxSpeedKmh)} km/h")
                    KeyRow("Elapsed Time", Fmt.duration(a.elapsedSec), divider = false)
                }
            }

            if (a.splits.isNotEmpty()) {
                SectionCard("Splits") {
                    SplitBars(a.splits)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.navigationBarsPadding())
    }

    if (showRename) {
        var title by remember { mutableStateOf(a.title) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            containerColor = Surface1,
            title = { Text("Rename activity", color = Sand) },
            text = {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Ember, unfocusedBorderColor = Outline,
                        cursorColor = Ember, focusedTextColor = Sand, unfocusedTextColor = Sand
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (title.isNotBlank()) {
                        store.updateTitle(a.id, title.trim())
                        summary = store.get(a.id)
                    }
                    showRename = false
                }) { Text("Save", color = Ember) }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("Cancel", color = Muted) }
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Surface1,
            title = { Text("Delete this activity?", color = Sand) },
            text = { Text("This cannot be undone.", color = Muted) },
            confirmButton = {
                TextButton(onClick = {
                    store.delete(a.id)
                    confirmDelete = false
                    onBack()
                }) { Text("Delete", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = Muted) }
            }
        )
    }
}

@Composable
private fun DetailMap(route: List<TrackPoint>) {
    val ctx = LocalContext.current
    val mapView = rememberMapView()
    val lineWidthPx = with(LocalDensity.current) { 5.dp.toPx() }
    val routeLine = remember { newRouteLine(Ember.toArgb(), lineWidthPx) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        applyTileSource(mapView, ctx)
        if (!mapView.overlays.contains(routeLine)) mapView.overlays.add(routeLine)
    }
    LaunchedEffect(route) {
        if (route.size >= 2) {
            val geo = route.map { GeoPoint(it.lat, it.lon) }
            zoomToRoute(mapView, geo)
            progress.snapTo(0f)
            progress.animateTo(1f, tween(1600)) {
                val n = max(2, (geo.size * value).toInt())
                routeLine.setPoints(geo.take(n))
                mapView.invalidate()
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Surface1,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(260.dp)
    ) {
        Box {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
            OsmAttribution(Modifier.align(Alignment.BottomStart).padding(6.dp))
        }
    }
}
