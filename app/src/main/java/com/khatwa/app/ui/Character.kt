package com.khatwa.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.khatwa.app.R
import com.khatwa.app.data.Gender
import kotlin.math.PI
import kotlin.math.sin

/**
 * Sprite-sheet runner: real artist-drawn run cycle (10 frames in a horizontal strip),
 * background already cut to transparent. Frame rate follows the live GPS speed, so the
 * legs cycle faster as you speed up. Flips horizontally to face the travel direction.
 *
 * Strips live in res/drawable-nodpi: runner_male.png / runner_female.png
 */
private const val FRAME_COUNT = 10

private data class Strip(val bmp: Bitmap, val frameW: Int, val frameH: Int)

private val stripCache = HashMap<Gender, Strip?>()

private fun loadStrip(ctx: android.content.Context, gender: Gender): Strip? {
    stripCache[gender]?.let { return it }
    val res = if (gender == Gender.FEMALE) R.drawable.runner_female else R.drawable.runner_male
    val opt = BitmapFactory.Options().apply { inScaled = false }
    val bmp = try { BitmapFactory.decodeResource(ctx.resources, res, opt) } catch (_: Exception) { null }
    val strip = bmp?.let { Strip(it, it.width / FRAME_COUNT, it.height) }
    stripCache[gender] = strip
    return strip
}

@Composable
fun SportCharacter(
    gender: Gender,
    speedMps: Float,
    modifier: Modifier = Modifier,
    sitting: Boolean = false,
    facingLeft: Boolean = false
) {
    val ctx = LocalContext.current
    val strip = remember(gender) { loadStrip(ctx, gender) }
    val image = remember(strip) { strip?.bmp?.asImageBitmap() }
    val speed by rememberUpdatedState(speedMps.coerceIn(0f, 7f))

    var frame by remember { mutableIntStateOf(0) }
    var bob by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(gender, sitting) {
        var acc = 0f
        var bobPhase = 0f
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = (now - last) / 1_000_000_000f
                    if (sitting) {
                        // exhausted: hold a frame, gentle breathing bob
                        bobPhase = (bobPhase + dt / 2.2f) % 1f
                        bob = sin(bobPhase * 2f * PI.toFloat())
                        frame = 0
                    } else {
                        // frames per second scales with speed (idle ticks slowly)
                        val fps = if (speed < 0.25f) 6f else (7f + speed * 3.2f).coerceIn(7f, 26f)
                        acc += dt * fps
                        if (acc >= 1f) {
                            frame = (frame + acc.toInt()) % FRAME_COUNT
                            acc -= acc.toInt()
                        }
                        bobPhase = (bobPhase + dt * fps / FRAME_COUNT) % 1f
                        bob = sin(bobPhase * 2f * PI.toFloat() * 2f) * (if (speed < 0.25f) 0.15f else 1f)
                    }
                }
                last = now
            }
        }
    }

    Canvas(modifier) {
        if (strip == null || image == null) {
            drawFallback(facingLeft); return@Canvas
        }
        val img = image
        val fw = strip.frameW
        val fh = strip.frameH
        // fit the frame into the canvas, feet at bottom, with a little vertical bob
        val scale = minOf(size.width / fw, size.height / fh)
        val drawW = fw * scale
        val drawH = fh * scale
        val left = (size.width - drawW) / 2f
        val bobPx = if (sitting) bob * size.height * 0.015f else -kotlin.math.abs(bob) * size.height * 0.04f
        val top = size.height - drawH + bobPx

        scale(scaleX = if (facingLeft) -1f else 1f, scaleY = 1f, pivot = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)) {
            drawImage(
                image = img,
                srcOffset = androidx.compose.ui.unit.IntOffset(frame * fw, 0),
                srcSize = androidx.compose.ui.unit.IntSize(fw, fh),
                dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()),
                dstSize = androidx.compose.ui.unit.IntSize(drawW.toInt(), drawH.toInt())
            )
        }
    }
}

private fun DrawScope.drawFallback(facingLeft: Boolean) {
    // simple ember triangle if the sprite ever fails to load (never blocks the app)
    val s = size.minDimension
    scale(scaleX = if (facingLeft) -1f else 1f, scaleY = 1f) {
        drawCircle(Ember, s * 0.18f, Offset(size.width / 2f, size.height / 2f))
    }
}

// ------------------------------------------------------------------ welcome intro

/** Aiko and Kenji sprint across the screen — used on the welcome splash. */
@Composable
fun RunningIntro(modifier: Modifier = Modifier) {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) t = (t + (now - last) / 1_000_000_000f / 2.8f) % 1f
                last = now
            }
        }
    }
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth().height(170.dp)) {
        val w = maxWidth
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height * 0.9f
            drawLine(Outline, Offset(0f, y), Offset(size.width, y), 3f, StrokeCap.Round)
            val xHead = (-0.2f + t * 1.45f) * size.width
            for (i in 0..2) {
                val sx = xHead - size.width * (0.14f + i * 0.06f)
                drawLine(
                    EmberGlow.copy(alpha = 0.5f - i * 0.13f),
                    Offset(sx, size.height * (0.42f + i * 0.11f)),
                    Offset(sx - size.width * 0.06f, size.height * (0.42f + i * 0.11f)),
                    6f, StrokeCap.Round
                )
            }
        }
        val frac = -0.2f + t * 1.45f
        SportCharacter(
            gender = Gender.FEMALE, speedMps = 3.6f,
            modifier = Modifier
                .width(120.dp).height(155.dp)
                .align(androidx.compose.ui.Alignment.BottomStart)
                .offset(x = w * frac)
        )
        SportCharacter(
            gender = Gender.MALE, speedMps = 3.6f,
            modifier = Modifier
                .width(120.dp).height(155.dp)
                .align(androidx.compose.ui.Alignment.BottomStart)
                .offset(x = w * (frac - 0.2f))
        )
    }
}
