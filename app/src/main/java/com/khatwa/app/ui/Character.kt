package com.khatwa.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.khatwa.app.R
import com.khatwa.app.data.Gender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/**
 * Sprite-sheet runner: artist-drawn 10-frame run cycle (transparent strip).
 * The bitmap is decoded OFF the main thread (produceState) so the first frame
 * never blocks or comes up blank. Frame timing is smooth (fractional, no integer
 * skipping). Frame rate follows live GPS speed; the sprite flips to face travel.
 */
private const val FRAME_COUNT = 10

private class Sheet(val image: ImageBitmap, val frameW: Int, val frameH: Int)

private val cache = HashMap<Gender, Sheet>()

private suspend fun loadSheet(ctx: Context, gender: Gender): Sheet? = withContext(Dispatchers.IO) {
    cache[gender]?.let { return@withContext it }
    val res = if (gender == Gender.FEMALE) R.drawable.runner_female else R.drawable.runner_male
    val opt = BitmapFactory.Options().apply {
        inScaled = false
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val bmp = try { BitmapFactory.decodeResource(ctx.resources, res, opt) } catch (_: Throwable) { null }
        ?: return@withContext null
    // downscale: full strip is ~3000px wide; 1200px is plenty for a thumb-sized sprite
    val target = 1200
    val scaled = if (bmp.width > target) {
        val s = target.toFloat() / bmp.width
        Bitmap.createScaledBitmap(bmp, target, (bmp.height * s).toInt(), true)
    } else bmp
    val sheet = Sheet(scaled.asImageBitmap(), scaled.width / FRAME_COUNT, scaled.height)
    cache[gender] = sheet
    sheet
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
    val sheet by produceState<Sheet?>(initialValue = cache[gender], gender) {
        if (value == null) value = loadSheet(ctx, gender)
    }
    val speed by rememberUpdatedState(speedMps.coerceIn(0f, 7f))

    // continuous fractional frame index -> smooth, no jitter
    var frameF by remember { mutableFloatStateOf(0f) }
    var bob by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(sitting) {
        var last = 0L
        var bobPhase = 0f
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = (now - last) / 1_000_000_000f
                    if (sitting) {
                        bobPhase = (bobPhase + dt / 2.2f) % 1f
                        bob = sin(bobPhase * 2f * PI.toFloat())
                        frameF = 0f
                    } else {
                        val fps = if (speed < 0.3f) 7f else (9f + speed * 2.4f).coerceIn(9f, 20f)
                        frameF = (frameF + dt * fps) % FRAME_COUNT
                        bobPhase = (bobPhase + dt * fps / FRAME_COUNT * 2f) % 1f
                        bob = sin(bobPhase * 2f * PI.toFloat()) * (if (speed < 0.3f) 0.2f else 1f)
                    }
                }
                last = now
            }
        }
    }

    Canvas(modifier) {
        val s = sheet
        if (s == null) return@Canvas   // nothing yet; show nothing (not a broken box)
        val fw = s.frameW
        val fh = s.frameH
        val frame = floor(frameF).toInt().coerceIn(0, FRAME_COUNT - 1)
        val scaleF = minOf(size.width / fw, size.height / fh)
        val drawW = fw * scaleF
        val drawH = fh * scaleF
        val left = (size.width - drawW) / 2f
        val bobPx = if (sitting) bob * size.height * 0.012f else -abs(bob) * size.height * 0.035f
        val top = size.height - drawH + bobPx

        scale(scaleX = if (facingLeft) -1f else 1f, scaleY = 1f,
            pivot = Offset(size.width / 2f, size.height / 2f)) {
            drawImage(
                image = s.image,
                srcOffset = IntOffset(frame * fw, 0),
                srcSize = IntSize(fw, fh),
                dstOffset = IntOffset(left.toInt(), top.toInt()),
                dstSize = IntSize(drawW.toInt().coerceAtLeast(1), drawH.toInt().coerceAtLeast(1))
            )
        }
    }
}

// ------------------------------------------------------------------ welcome intro

/** Aiko + Kenji sprint across the welcome splash. */
@Composable
fun RunningIntro(modifier: Modifier = Modifier) {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) t = (t + (now - last) / 1_000_000_000f / 3.0f) % 1f
                last = now
            }
        }
    }
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth().height(160.dp)) {
        val w = maxWidth
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height * 0.92f
            drawLine(Outline, Offset(0f, y), Offset(size.width, y), 3f, StrokeCap.Round)
        }
        val frac = -0.22f + t * 1.5f
        SportCharacter(
            gender = Gender.MALE, speedMps = 3.6f,
            modifier = Modifier.width(120.dp).height(150.dp)
                .align(Alignment.BottomStart).offset(x = w * (frac - 0.16f))
        )
        SportCharacter(
            gender = Gender.FEMALE, speedMps = 3.6f,
            modifier = Modifier.width(120.dp).height(150.dp)
                .align(Alignment.BottomStart).offset(x = w * frac)
        )
    }
}
