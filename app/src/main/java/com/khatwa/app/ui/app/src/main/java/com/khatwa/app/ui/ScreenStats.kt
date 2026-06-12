package com.khatwa.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.khatwa.app.data.ActivityStore
import com.khatwa.app.data.ActivitySummary
import com.khatwa.app.data.ActivityType
import com.khatwa.app.data.Profile
import com.khatwa.app.tracking.Calories
import com.khatwa.app.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

@Composable
fun StatsScreen(profile: Profile) {
    val profileId = profile.id
    val ctx = LocalContext.current
    var all by remember { mutableStateOf<List<ActivitySummary>>(emptyList()) }
    LaunchedEffect(profileId) {
        all = withContext(Dispatchers.IO) { ActivityStore(ctx).list(profileId) }
    }
    var weekMode by rememberSaveable { mutableStateOf(true) }

    val now = System.currentTimeMillis()
    val days = if (weekMode) 7 else 30
    val dayMs = 24L * 3600 * 1000

    // start of today (local midnight)
    val cal = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val todayStart = cal.timeInMillis
    val windowStart = todayStart - (days - 1) * dayMs

    val inWindow = all.filter { it.startEpochMs >= windowStart }
    val totalKm = inWindow.sumOf { it.distanceM } / 1000.0
    val totalSec = inWindow.sumOf { it.movingSec.toLong() }
    val totalKcal = inWindow.sumOf { it.calories }

    // distance per day
    val perDay = FloatArray(days)
    inWindow.forEach { a ->
        val idx = ((a.startEpochMs - windowStart) / dayMs).toInt()
        if (idx in 0 until days) perDay[idx] = perDay[idx] + (a.distanceM / 1000.0).toFloat()
    }
    val labels: List<String>
    val values: List<Float>
    val highlight: Int
    if (weekMode) {
        labels = (0 until 7).map { Fmt.dayLabel(windowStart + it * dayMs) }
        values = perDay.toList()
        highlight = 6
    } else {
        // group 30 days into weekly bars (oldest -> newest)
        val weeks = FloatArray(5)
        for (i in 0 until days) weeks[(i / 7).coerceAtMost(4)] += perDay[i]
        values = weeks.toList()
        labels = listOf("4w ago", "3w", "2w", "last w", "now")
        highlight = 4
    }

    Column(
        Modifier.fillMaxSize().background(NightBg).statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
    ) {
        Row(
            Modifier.padding(vertical = 16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Stats", style = MaterialTheme.typography.headlineMedium, color = Sand)
            Spacer(Modifier.weight(1f))
            FilterChip(
                selected = weekMode, onClick = { weekMode = true },
                label = { Text("Week") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Ember, selectedLabelColor = Color(0xFF160B30)
                )
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = !weekMode, onClick = { weekMode = false },
                label = { Text("Month") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Ember, selectedLabelColor = Color(0xFF160B30)
                )
            )
        }

        Surface(shape = RoundedCornerShape(18.dp), color = Surface1, modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = EmberGlow, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Maintenance: ≈ ${Calories.maintenanceKcalPerDay(profile)} kcal/day to stay at ${if (profile.weightKg % 1.0 == 0.0) profile.weightKg.toInt().toString() else profile.weightKg.toString()} kg",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Sand
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        if (all.isEmpty()) {
            EmptyState(
                Icons.Filled.BarChart,
                "No data yet",
                "Record a first activity and your weekly totals will light up here."
            )
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatBlock(String.format(java.util.Locale.US, "%.1f", totalKm), "km")
                StatBlock(Fmt.duration(totalSec), "time")
                StatBlock("${totalKcal.toInt()}", "kcal")
                StatBlock("${inWindow.size}", if (inWindow.size == 1) "activity" else "activities")
            }
            Spacer(Modifier.height(22.dp))

            SectionCard(if (weekMode) "Distance · last 7 days" else "Distance · last 30 days") {
                BarsChart(values = values, labels = labels, highlight = highlight)
            }
            Spacer(Modifier.height(14.dp))

            SectionCard("By activity type") {
                ActivityType.entries.forEach { t ->
                    val ofType = inWindow.filter { it.type == t }
                    val km = ofType.sumOf { it.distanceM } / 1000.0
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(typeIcon(t), contentDescription = t.label, tint = if (ofType.isEmpty()) Muted else Ember, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(t.label, style = MaterialTheme.typography.bodyLarge, color = Sand)
                        Spacer(Modifier.weight(1f))
                        val kcalTxt = ofType.sumOf { a -> a.calories }.toInt()
                        val kmTxt = String.format(java.util.Locale.US, "%.1f", km)
                        Text(
                            if (ofType.isEmpty()) "—"
                            else "${ofType.size} · $kmTxt km · $kcalTxt kcal",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Muted
                        )
                    }
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}
