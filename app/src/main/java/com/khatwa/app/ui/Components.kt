package com.khatwa.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khatwa.app.data.ActivityType
import com.khatwa.app.data.Split
import com.khatwa.app.util.Fmt
import kotlin.math.max
import kotlin.math.min

// ---------------------------------------------------------------- icons

fun typeIcon(t: ActivityType): ImageVector = when (t) {
    ActivityType.WALK -> Icons.AutoMirrored.Filled.DirectionsWalk
    ActivityType.RUN -> Icons.AutoMirrored.Filled.DirectionsRun
    ActivityType.BIKE -> Icons.AutoMirrored.Filled.DirectionsBike
}

// ---------------------------------------------------------------- record button

/** Signature element: ember record button with expanding sonar rings. */
@Composable
fun PulseStartButton(enabled: Boolean, onClick: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "pulse")
    val p1 by inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2100, easing = LinearEasing)),
        label = "ring"
    )
    val breathe by inf.animateFloat(
        0.97f, 1.04f,
        infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe"
    )
    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            fun ring(p: Float) {
                val r = size.minDimension / 2f * (0.40f + 0.60f * p)
                drawCircle(
                    color = Ember.copy(alpha = (1f - p) * 0.45f),
                    radius = r,
                    style = Stroke(width = 2.dp.toPx() + 4.dp.toPx() * (1f - p))
                )
            }
            if (enabled) {
                ring(p1)
                ring((p1 + 0.5f) % 1f)
            }
        }
        Box(
            modifier = Modifier
                .size(88.dp)
                .scale(if (enabled) breathe else 1f)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(EmberGlow, Ember, EmberDeep)))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Start",
                tint = Color(0xFF1A0D04),
                modifier = Modifier.size(46.dp)
            )
        }
    }
}

// ---------------------------------------------------------------- GPS pill

@Composable
fun GpsPill(accuracyM: Float?) {
    val inf = rememberInfiniteTransition(label = "gps")
    val blink by inf.animateFloat(
        0.35f, 1f,
        infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "blink"
    )
    val good = accuracyM != null && accuracyM <= 25f
    val color = if (good) Teal else Amber
    val label = when {
        accuracyM == null -> "Searching GPS"
        good -> "GPS Acquired"
        else -> "Weak GPS ±${accuracyM.toInt()} m"
    }
    Surface(shape = RoundedCornerShape(50), color = Surface1.copy(alpha = 0.94f)) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = if (good) 1f else blink))
            )
            Spacer(Modifier.width(7.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = color)
        }
    }
}

@Composable
fun TinyPill(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.14f)) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

// ---------------------------------------------------------------- numbers & stats

@Composable
fun AnimatedNumber(
    value: Float,
    format: (Float) -> String,
    style: TextStyle,
    color: Color = Sand
) {
    val anim by animateFloatAsState(value, tween(800), label = "num")
    Text(format(anim), style = style, color = color)
}

@Composable
fun StatBlock(value: String, label: String, modifier: Modifier = Modifier, big: Boolean = false) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = if (big) MaterialTheme.typography.displayMedium
            else MaterialTheme.typography.displaySmall,
            color = Sand
        )
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Muted)
    }
}

// ---------------------------------------------------------------- type selector

@Composable
fun TypeSelector(selected: ActivityType, enabled: Boolean, onSelect: (ActivityType) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ActivityType.entries.forEach { t ->
            val isSel = t == selected
            val bg by animateColorAsState(
                if (isSel) Ember else Surface1.copy(alpha = 0.94f),
                tween(250), label = "chip"
            )
            val fg by animateColorAsState(
                if (isSel) Color(0xFF1A0D04) else Muted,
                tween(250), label = "chipFg"
            )
            Surface(
                shape = RoundedCornerShape(50),
                color = bg,
                modifier = Modifier.clip(RoundedCornerShape(50))
                    .clickable(enabled = enabled) { onSelect(t) }
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(typeIcon(t), contentDescription = t.label, tint = fg, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t.label, style = MaterialTheme.typography.labelLarge, color = fg)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- cards & rows

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable Column.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Surface1,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = Muted)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun KeyRow(label: String, value: String, divider: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Muted)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = Sand, fontWeight = FontWeight.SemiBold)
    }
    if (divider) HorizontalDivider(color = Outline.copy(alpha = 0.5f))
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = Muted, modifier = Modifier.size(54.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = Sand)
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

// ---------------------------------------------------------------- line chart

/**
 * Canvas line/area chart used for elevation (normal), pace (inverted: faster on top)
 * and speed. No chart library — full control over the ember styling.
 */
@Composable
fun LineChart(
    xs: List<Float>,
    ys: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = Ember,
    inverted: Boolean = false,
    avgValue: Float? = null,
    yLabel: (Float) -> String = { String.format(java.util.Locale.US, "%.0f", it) },
    xUnit: String = "km"
) {
    val tm = rememberTextMeasurer()
    Canvas(modifier.fillMaxWidth().height(170.dp)) {
        if (xs.size < 2 || ys.size != xs.size) return@Canvas
        val labelStyle = TextStyle(color = Muted, fontSize = 10.sp)

        var minY = ys.min()
        var maxY = ys.max()
        if (maxY - minY < 1e-3f) { maxY += 1f; minY -= 1f }
        val padRange = (maxY - minY) * 0.12f
        minY -= padRange; maxY += padRange

        val minX = xs.first()
        val maxX = max(xs.last(), minX + 0.01f)

        val left = 6.dp.toPx()
        val right = size.width - 6.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 18.dp.toPx()
        val w = right - left
        val h = bottom - top

        fun px(x: Float) = left + (x - minX) / (maxX - minX) * w
        fun py(v: Float): Float {
            val f = (v - minY) / (maxY - minY)
            return if (inverted) top + f * h else top + (1f - f) * h
        }

        // grid
        val grid = 3
        for (i in 0..grid) {
            val y = top + h * i / grid
            drawLine(Outline.copy(alpha = 0.45f), Offset(left, y), Offset(right, y), strokeWidth = 1f)
        }

        // area + line
        val line = Path()
        val area = Path()
        xs.forEachIndexed { i, x ->
            val p = Offset(px(x), py(ys[i]))
            if (i == 0) {
                line.moveTo(p.x, p.y); area.moveTo(p.x, bottom); area.lineTo(p.x, p.y)
            } else {
                line.lineTo(p.x, p.y); area.lineTo(p.x, p.y)
            }
        }
        area.lineTo(px(xs.last()), bottom)
        area.close()
        drawPath(
            area,
            brush = Brush.verticalGradient(
                listOf(lineColor.copy(alpha = 0.28f), lineColor.copy(alpha = 0.0f)),
                startY = top, endY = bottom
            )
        )
        drawPath(line, color = lineColor, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // average dashed line
        avgValue?.let { avg ->
            val y = py(avg.coerceIn(minY, maxY))
            drawLine(
                EmberGlow.copy(alpha = 0.85f),
                Offset(left, y), Offset(right, y),
                strokeWidth = 1.6.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
            )
        }

        // y labels: best (top) and worst (bottom) actual values
        val topVal = if (inverted) ys.min() else ys.max()
        val botVal = if (inverted) ys.max() else ys.min()
        drawText(tm.measure(AnnotatedString(yLabel(topVal)), labelStyle), topLeft = Offset(left + 2f, top + 2f))
        val botLayout = tm.measure(AnnotatedString(yLabel(botVal)), labelStyle)
        drawText(botLayout, topLeft = Offset(left + 2f, bottom - botLayout.size.height - 2f))

        // x label
        val xTxt = String.format(java.util.Locale.US, "%.1f %s", maxX, xUnit)
        val xLayout = tm.measure(AnnotatedString(xTxt), labelStyle)
        drawText(xLayout, topLeft = Offset(right - xLayout.size.width, size.height - xLayout.size.height.toFloat()))
    }
}

// ---------------------------------------------------------------- bars chart

@Composable
fun BarsChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    highlight: Int = -1
) {
    val tm = rememberTextMeasurer()
    Canvas(modifier.fillMaxWidth().height(150.dp)) {
        if (values.isEmpty()) return@Canvas
        val labelStyle = TextStyle(color = Muted, fontSize = 9.sp)
        val maxV = max(values.max(), 0.001f)
        val bottom = size.height - 16.dp.toPx()
        val slot = size.width / values.size
        val barW = min(slot * 0.55f, 26.dp.toPx())
        values.forEachIndexed { i, v ->
            val hPx = (v / maxV) * (bottom - 10.dp.toPx())
            val x = slot * i + (slot - barW) / 2f
            val color = if (i == highlight) Ember else Surface2
            drawRoundRect(
                brush = if (i == highlight)
                    Brush.verticalGradient(listOf(EmberGlow, Ember))
                else Brush.verticalGradient(listOf(color, color)),
                topLeft = Offset(x, bottom - hPx),
                size = Size(barW, hPx.coerceAtLeast(2.dp.toPx())),
                cornerRadius = CornerRadius(barW / 2.4f, barW / 2.4f)
            )
            if (i < labels.size) {
                val l = tm.measure(AnnotatedString(labels[i]), labelStyle)
                drawText(l, topLeft = Offset(slot * i + (slot - l.size.width) / 2f, bottom + 3.dp.toPx()))
            }
        }
    }
}

// ---------------------------------------------------------------- route preview

/** Pure-canvas route silhouette (no map tiles): fast, offline, used on cards. */
@Composable
fun RoutePreview(
    points: List<Pair<Double, Double>>,   // (lat, lon)
    modifier: Modifier = Modifier,
    strokeDp: Float = 3.5f,
    progress: Float = 1f,
    background: Color = Surface2
) {
    Canvas(modifier.clip(RoundedCornerShape(16.dp)).background(background)) {
        if (points.size < 2) return@Canvas
        var minLat = Double.MAX_VALUE; var maxLat = -Double.MAX_VALUE
        var minLon = Double.MAX_VALUE; var maxLon = -Double.MAX_VALUE
        points.forEach { (la, lo) ->
            if (la < minLat) minLat = la; if (la > maxLat) maxLat = la
            if (lo < minLon) minLon = lo; if (lo > maxLon) maxLon = lo
        }
        val latSpan = max(maxLat - minLat, 1e-6)
        val lonSpan = max(maxLon - minLon, 1e-6)
        val pad = 0.14f
        val availW = size.width * (1 - 2 * pad)
        val availH = size.height * (1 - 2 * pad)
        val scale = min(availW / lonSpan.toFloat(), availH / latSpan.toFloat())
        val offX = (size.width - lonSpan.toFloat() * scale) / 2f
        val offY = (size.height - latSpan.toFloat() * scale) / 2f

        fun toOffset(la: Double, lo: Double) = Offset(
            offX + ((lo - minLon).toFloat() * scale),
            offY + ((maxLat - la).toFloat() * scale)   // lat grows upward
        )

        val count = max(2, (points.size * progress.coerceIn(0f, 1f)).toInt())
        val path = Path()
        points.take(count).forEachIndexed { i, (la, lo) ->
            val p = toOffset(la, lo)
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(
            path,
            brush = Brush.linearGradient(listOf(EmberDeep, Ember, EmberGlow)),
            style = Stroke(strokeDp.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        val start = toOffset(points.first().first, points.first().second)
        drawCircle(Teal, radius = 3.5.dp.toPx(), center = start)
        if (progress >= 1f) {
            val end = toOffset(points.last().first, points.last().second)
            drawCircle(Color.White, radius = 3.dp.toPx(), center = end)
        }
    }
}

// ---------------------------------------------------------------- splits

@Composable
fun SplitBars(splits: List<Split>) {
    if (splits.isEmpty()) return
    val fastest = splits.minOf { it.seconds }.coerceAtLeast(1)
    Column {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Text("KM", style = MaterialTheme.typography.labelSmall, color = Muted, modifier = Modifier.width(34.dp))
            Text("PACE", style = MaterialTheme.typography.labelSmall, color = Muted)
        }
        splits.forEach { s ->
            val frac = (fastest.toFloat() / s.seconds.toFloat()).coerceIn(0.08f, 1f)
            val isFastest = s.seconds == fastest
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${s.km}", style = MaterialTheme.typography.bodyMedium, color = Sand, modifier = Modifier.width(34.dp))
                Text(
                    Fmt.pace(s.seconds),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isFastest) Teal else Sand,
                    modifier = Modifier.width(56.dp)
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(10.dp)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(frac)
                            .height(10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (isFastest) Brush.horizontalGradient(listOf(Teal, Teal))
                                else Brush.horizontalGradient(listOf(EmberDeep, Ember))
                            )
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- attribution

@Composable
fun OsmAttribution(modifier: Modifier = Modifier) {
    Text(
        "© OpenStreetMap contributors",
        style = MaterialTheme.typography.labelSmall,
        color = Muted.copy(alpha = 0.9f),
        modifier = modifier
            .background(NightBg.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
