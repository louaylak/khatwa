package com.khatwa.app.ui

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import com.khatwa.app.data.Gender
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * 2D game-style sport characters drawn 100% in code (no image assets):
 *  - Aiko  (female): coral top, black leggings with coral stripe, swaying ponytail
 *  - Kenji (male):   grey tank, black shorts with white stripe, spiky hair
 * The run cycle speed, stride and forward lean follow the REAL GPS speed.
 * sitting = true shows the exhausted finish pose (breathing + sweat drops).
 */

private val SkinTone = Color(0xFFF2C29B)
private val HairDark = Color(0xFF23222B)
private val Coral = Color(0xFFF87C6D)
private val CoralDeep = Color(0xFFE85B4B)
private val TankGrey = Color(0xFF8E9AA6)
private val ClothBlack = Color(0xFF1B1B22)
private val ShoeDark = Color(0xFF3A4250)

@Composable
fun SportCharacter(
    gender: Gender,
    speedMps: Float,
    modifier: Modifier = Modifier,
    sitting: Boolean = false,
    facingLeft: Boolean = false
) {
    val speed by rememberUpdatedState(speedMps.coerceIn(0f, 7f))
    var phase by remember { mutableFloatStateOf(0f) }

    // phase accumulator whose period shrinks with real speed
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = (now - last) / 1_000_000_000f
                    val period = if (speed < 0.25f) 2.6f else (1.15f - speed * 0.11f).coerceIn(0.34f, 1.15f)
                    phase = (phase + dt / period) % 1f
                }
                last = now
            }
        }
    }

    Canvas(modifier) {
        scale(scaleX = if (facingLeft) -1f else 1f, scaleY = 1f) {
            if (sitting) drawSitting(gender, phase)
            else drawRunner(gender, phase, speed)
        }
    }
}

// ------------------------------------------------------------------ helpers

private fun polar(from: Offset, angleDeg: Float, len: Float): Offset {
    val r = angleDeg * PI.toFloat() / 180f
    // 0° = straight down, positive swings forward (+x)
    return Offset(from.x + sin(r) * len, from.y + cos(r) * len)
}

private fun DrawScope.limb(a: Offset, b: Offset, width: Float, color: Color) {
    drawLine(color, a, b, strokeWidth = width, cap = StrokeCap.Round)
}

private fun DrawScope.shoe(at: Offset, s: Float, forward: Float, accent: Color) {
    drawOval(
        ShoeDark,
        topLeft = Offset(at.x - s * 0.055f + forward, at.y - s * 0.045f),
        size = androidx.compose.ui.geometry.Size(s * 0.14f, s * 0.07f)
    )
    drawOval(
        accent,
        topLeft = Offset(at.x - s * 0.055f + forward, at.y - s * 0.005f),
        size = androidx.compose.ui.geometry.Size(s * 0.14f, s * 0.028f)
    )
}

// ------------------------------------------------------------------ runner

private fun DrawScope.drawRunner(gender: Gender, phase: Float, speed: Float) {
    val s = size.minDimension
    val run = ((speed - 0.3f) / 3.2f).coerceIn(0f, 1f)   // 0 = idle/slow walk, 1 = fast run
    val moving = speed > 0.25f
    val w = phase * 2f * PI.toFloat()

    val legAmp = if (moving) 22f + 26f * run else 0f
    val armAmp = if (moving) 18f + 24f * run else 4f
    val lean = if (moving) 4f + 11f * run else 0f
    val bob = if (moving) sin(w * 2f) * s * (0.012f + 0.012f * run)
    else sin(phase * 2f * PI.toFloat()) * s * 0.006f   // gentle idle breathing

    val cx = size.width / 2f
    val hip = Offset(cx, size.height * 0.56f + bob)
    val torsoLen = s * 0.225f
    val leanR = lean * PI.toFloat() / 180f
    val shoulder = Offset(hip.x + sin(leanR) * torsoLen, hip.y - cos(leanR) * torsoLen)
    val headR = s * 0.105f
    val headC = Offset(shoulder.x + sin(leanR) * headR * 1.5f, shoulder.y - headR * 1.15f)

    val thigh = s * 0.165f
    val shin = s * 0.165f
    val arm = s * 0.13f
    val fore = s * 0.115f
    val limbW = s * 0.072f
    val legColor = if (gender == Gender.FEMALE) ClothBlack else SkinTone
    val accent = if (gender == Gender.FEMALE) Coral else Color(0xFFE8ECF2)

    val swing = sin(w)
    // back side first (slightly darker feel via same colors, drawn underneath)
    run {
        val a = -swing * legAmp
        val knee = polar(hip, a, thigh)
        val bend = max(0f, -swing) * (30f + 35f * run)
        val foot = polar(knee, a - bend, shin)
        limb(hip, knee, limbW, legColor)
        limb(knee, foot, limbW * 0.9f, legColor)
        if (gender == Gender.FEMALE) limb(hip, knee, limbW * 0.28f, accent)
        shoe(foot, s, -s * 0.02f, accent)
    }
    run {
        val elbow = polar(shoulder, -(-swing * armAmp), arm)
        val hand = polar(elbow, -(-swing * armAmp) + (45f + 30f * run), fore)
        limb(shoulder, elbow, limbW * 0.8f, SkinTone)
        limb(elbow, hand, limbW * 0.7f, SkinTone)
    }

    // hair behind head
    if (gender == Gender.FEMALE) {
        val sway = sin(w - 0.9f) * s * 0.03f * (0.3f + run)
        val tail = Path().apply {
            moveTo(headC.x - headR * 0.55f, headC.y - headR * 0.5f)
            quadraticBezierTo(
                headC.x - headR * 2.0f + sway, headC.y + headR * 0.4f,
                headC.x - headR * 1.5f + sway * 1.6f, headC.y + headR * 2.3f
            )
            quadraticBezierTo(
                headC.x - headR * 0.7f, headC.y + headR * 1.2f,
                headC.x - headR * 0.2f, headC.y + headR * 0.35f
            )
            close()
        }
        drawPath(tail, HairDark)
    }

    // torso
    if (gender == Gender.FEMALE) {
        // bare midriff + coral top
        limb(hip, shoulder, s * 0.115f, SkinTone)
        val chest = Offset(
            hip.x + (shoulder.x - hip.x) * 0.62f,
            hip.y + (shoulder.y - hip.y) * 0.62f
        )
        limb(chest, shoulder, s * 0.135f, Coral)
        limb(chest, Offset(chest.x, chest.y + s * 0.02f), s * 0.135f, CoralDeep)
        // leggings waist
        limb(hip, Offset(hip.x, hip.y + s * 0.02f), s * 0.13f, ClothBlack)
    } else {
        limb(hip, shoulder, s * 0.15f, TankGrey)
        limb(hip, Offset(hip.x, hip.y + s * 0.055f), s * 0.16f, ClothBlack) // shorts
        limb(
            Offset(hip.x, hip.y + s * 0.05f),
            Offset(hip.x + s * 0.01f, hip.y + s * 0.058f),
            s * 0.02f, Color(0xFFE8ECF2)
        )
    }

    // head + face hint + hair front
    drawCircle(SkinTone, headR, headC)
    if (gender == Gender.FEMALE) {
        drawPath(Path().apply {
            moveTo(headC.x - headR, headC.y)
            quadraticBezierTo(headC.x - headR * 0.4f, headC.y - headR * 1.5f, headC.x + headR * 0.95f, headC.y - headR * 0.35f)
            quadraticBezierTo(headC.x + headR * 0.2f, headC.y - headR * 0.75f, headC.x - headR * 0.55f, headC.y - headR * 0.35f)
            close()
        }, HairDark)
        drawCircle(HairDark, headR * 0.28f, Offset(headC.x - headR * 0.35f, headC.y - headR * 1.05f)) // bun
    } else {
        // spiky hair
        val p = Path()
        var x = headC.x - headR
        val top = headC.y - headR * 0.25f
        p.moveTo(x, top)
        var i = 0
        while (i < 5) {
            p.lineTo(x + headR * 0.25f, top - headR * (0.85f + (i % 2) * 0.35f))
            p.lineTo(x + headR * 0.5f, top - headR * 0.1f)
            x += headR * 0.45f
            i++
        }
        p.close()
        drawPath(p, HairDark)
    }
    drawCircle(HairDark, headR * 0.09f, Offset(headC.x + headR * 0.45f, headC.y - headR * 0.05f)) // eye

    // front side limbs (drawn over body)
    run {
        val a = swing * legAmp
        val knee = polar(hip, a, thigh)
        val bend = max(0f, swing) * (30f + 35f * run)
        val foot = polar(knee, a - bend, shin)
        limb(hip, knee, limbW, legColor)
        limb(knee, foot, limbW * 0.9f, legColor)
        if (gender == Gender.FEMALE) limb(hip, knee, limbW * 0.28f, accent)
        shoe(foot, s, s * 0.01f, accent)
    }
    run {
        val elbow = polar(shoulder, swing * armAmp, arm)
        val hand = polar(elbow, swing * armAmp + (45f + 30f * run), fore)
        limb(shoulder, elbow, limbW * 0.8f, SkinTone)
        limb(elbow, hand, limbW * 0.7f, SkinTone)
    }
}

// ------------------------------------------------------------------ exhausted

private fun DrawScope.drawSitting(gender: Gender, phase: Float) {
    val s = size.minDimension
    val breathe = sin(phase * 2f * PI.toFloat()) * s * 0.012f
    val cx = size.width / 2f
    val ground = size.height * 0.9f
    val hip = Offset(cx - s * 0.05f, ground - s * 0.13f)
    val shoulder = Offset(hip.x - s * 0.045f, hip.y - s * 0.21f - breathe)
    val headR = s * 0.105f
    val headC = Offset(shoulder.x + s * 0.035f, shoulder.y - headR * 1.05f) // head dropped forward
    val limbW = s * 0.072f
    val legColor = if (gender == Gender.FEMALE) ClothBlack else SkinTone
    val accent = if (gender == Gender.FEMALE) Coral else Color(0xFFE8ECF2)

    // legs: knees up, feet on ground
    val knee = Offset(cx + s * 0.13f, ground - s * 0.2f)
    val foot = Offset(cx + s * 0.2f, ground - s * 0.02f)
    limb(hip, knee, limbW, legColor)
    limb(knee, foot, limbW * 0.9f, legColor)
    if (gender == Gender.FEMALE) limb(hip, knee, limbW * 0.28f, accent)
    shoe(foot, s, 0f, accent)
    val knee2 = Offset(cx + s * 0.1f, ground - s * 0.16f)
    val foot2 = Offset(cx + s * 0.17f, ground - s * 0.015f)
    limb(Offset(hip.x + s * 0.01f, hip.y + s * 0.01f), knee2, limbW, legColor)
    limb(knee2, foot2, limbW * 0.9f, legColor)
    shoe(foot2, s, -s * 0.01f, accent)

    // torso leaning back slightly, breathing
    if (gender == Gender.FEMALE) {
        limb(hip, shoulder, s * 0.115f, SkinTone)
        val chest = Offset(hip.x + (shoulder.x - hip.x) * 0.6f, hip.y + (shoulder.y - hip.y) * 0.6f)
        limb(chest, shoulder, s * 0.135f, Coral)
    } else {
        limb(hip, shoulder, s * 0.15f, TankGrey)
    }
    // arm resting on knee
    val hand = Offset(knee.x, knee.y - s * 0.01f)
    val elbow = Offset((shoulder.x + hand.x) / 2f, (shoulder.y + hand.y) / 2f + s * 0.05f)
    limb(shoulder, elbow, limbW * 0.8f, SkinTone)
    limb(elbow, hand, limbW * 0.7f, SkinTone)

    // head + hair
    drawCircle(SkinTone, headR, headC)
    if (gender == Gender.FEMALE) {
        drawPath(Path().apply {
            moveTo(headC.x - headR, headC.y + headR * 0.1f)
            quadraticBezierTo(headC.x - headR * 0.3f, headC.y - headR * 1.5f, headC.x + headR * 0.95f, headC.y - headR * 0.25f)
            quadraticBezierTo(headC.x, headC.y - headR * 0.7f, headC.x - headR * 0.5f, headC.y - headR * 0.25f)
            close()
        }, HairDark)
        drawPath(Path().apply {
            moveTo(headC.x - headR * 0.7f, headC.y - headR * 0.2f)
            quadraticBezierTo(headC.x - headR * 1.9f, headC.y + headR * 0.9f, headC.x - headR * 1.1f, headC.y + headR * 2.1f)
            quadraticBezierTo(headC.x - headR * 0.5f, headC.y + headR, headC.x - headR * 0.3f, headC.y + headR * 0.3f)
            close()
        }, HairDark)
    } else {
        val p = Path()
        var x = headC.x - headR
        val top = headC.y - headR * 0.2f
        p.moveTo(x, top)
        var i = 0
        while (i < 5) {
            p.lineTo(x + headR * 0.25f, top - headR * (0.8f + (i % 2) * 0.3f))
            p.lineTo(x + headR * 0.5f, top - headR * 0.05f)
            x += headR * 0.45f
            i++
        }
        p.close()
        drawPath(p, HairDark)
    }
    // closed tired eye
    drawLine(HairDark, Offset(headC.x + headR * 0.25f, headC.y), Offset(headC.x + headR * 0.6f, headC.y), s * 0.012f, StrokeCap.Round)

    // sweat drops falling on a loop
    val dropT = phase
    val dy = dropT * s * 0.35f
    val alpha = (1f - dropT).coerceIn(0f, 1f)
    drawCircle(Teal.copy(alpha = alpha * 0.9f), s * 0.022f, Offset(headC.x + headR * 1.25f, headC.y - headR * 0.4f + dy))
    val d2 = (dropT + 0.5f) % 1f
    drawCircle(Teal.copy(alpha = (1f - d2) * 0.7f), s * 0.017f, Offset(headC.x - headR * 1.1f, headC.y - headR * 0.2f + d2 * s * 0.3f))
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
            val y = size.height * 0.86f
            drawLine(Outline, Offset(0f, y), Offset(size.width, y), 3f, StrokeCap.Round)
            // motion streaks
            val xHead = (-0.25f + t * 1.5f) * size.width
            for (i in 0..2) {
                val sx = xHead - size.width * (0.16f + i * 0.07f)
                drawLine(
                    EmberGlow.copy(alpha = 0.5f - i * 0.13f),
                    Offset(sx, size.height * (0.45f + i * 0.12f)),
                    Offset(sx - size.width * 0.07f, size.height * (0.45f + i * 0.12f)),
                    6f, StrokeCap.Round
                )
            }
        }
        val frac = -0.25f + t * 1.5f
        SportCharacter(
            gender = Gender.FEMALE, speedMps = 3.4f,
            modifier = Modifier
                .width(112.dp)
                .height(150.dp)
                .align(androidx.compose.ui.Alignment.BottomStart)
                .offset(x = w * frac)
        )
        SportCharacter(
            gender = Gender.MALE, speedMps = 3.4f,
            modifier = Modifier
                .width(112.dp)
                .height(150.dp)
                .align(androidx.compose.ui.Alignment.BottomStart)
                .offset(x = w * (frac - 0.2f))
        )
    }
}
