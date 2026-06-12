package com.khatwa.app.tracking

import com.khatwa.app.data.ActivityType
import com.khatwa.app.data.Gender
import com.khatwa.app.data.Profile
import kotlin.math.abs
import kotlin.math.max

/**
 * Calorie model — documented so every number is defensible.
 *
 * 1) Personal resting rate: Mifflin-St Jeor (1990) basal metabolic rate, kcal/day:
 *      male:   10*kg + 6.25*cm - 5*age + 5
 *      female: 10*kg + 6.25*cm - 5*age - 161
 *    The standard MET convention (1 MET = 3.5 ml O2/kg/min ≈ 1 kcal/kg/h) over- or
 *    under-estimates individuals; using "corrected METs" anchored to the personal BMR
 *    (kcal/min = MET * BMR/1440) makes age, height and sex actually matter.
 *
 * 2) Walking / running intensity: ACSM metabolic equations (VO2 in ml/kg/min,
 *    S = speed in m/min, G = fractional grade):
 *      walking: VO2 = 3.5 + 0.1*S + 1.8*S*G      (valid ~3–6.5 km/h)
 *      running: VO2 = 3.5 + 0.2*S + 0.9*S*G      (valid above ~8 km/h)
 *    Between 6.5 and 8.5 km/h the two are linearly blended (the jog transition zone).
 *    MET = VO2 / 3.5. Grade is clamped to [-8 %, +15 %] — outside that range the
 *    equations are not validated and GPS grade is noise anyway. VO2 floored at 4.0
 *    so steep downhill never drops below light activity.
 *
 * 3) Cycling: speed-banded METs from the Compendium of Physical Activities
 *    (Ainsworth et al., 2011 codes 01010–01050), since the ACSM cycle equation is
 *    for ergometer work rate, not road speed. Climbing is added separately as
 *    mechanical work: kcal = m*g*Δh / (0.22 gross efficiency) / 4184. Descents add
 *    nothing (coasting).
 */
object Calories {

    fun bmrKcalPerDay(p: Profile): Double {
        val base = 10.0 * p.weightKg + 6.25 * p.heightCm - 5.0 * p.age
        return if (p.gender == Gender.MALE) base + 5.0 else base - 161.0
    }

    fun met(type: ActivityType, speedMps: Double, grade: Double): Double {
        val kmh = speedMps * 3.6
        return when (type) {
            ActivityType.WALK, ActivityType.RUN -> {
                val s = speedMps * 60.0                     // m/min
                if (s < 15.0) return 1.3                    // barely moving
                val g = grade.coerceIn(-0.08, 0.15)
                val vo2Walk = 3.5 + 0.1 * s + 1.8 * s * g
                val vo2Run = 3.5 + 0.2 * s + 0.9 * s * g
                val vo2 = when {
                    kmh <= 6.5 -> vo2Walk
                    kmh >= 8.5 -> vo2Run
                    else -> {
                        val f = (kmh - 6.5) / 2.0
                        vo2Walk * (1 - f) + vo2Run * f
                    }
                }
                max(vo2, 4.0) / 3.5
            }
            ActivityType.BIKE -> when {
                kmh < 8.0 -> 3.5
                kmh < 12.0 -> 4.0
                kmh < 16.0 -> 5.8
                kmh < 19.0 -> 6.8
                kmh < 22.0 -> 8.0
                kmh < 25.5 -> 10.0
                kmh < 30.0 -> 12.0
                else -> 14.0
            }
        }
    }

    /**
     * Kcal for one accepted GPS segment.
     * @param dtMin segment duration in minutes (already capped by the caller)
     * @param climbM committed positive elevation in this segment (bike extra work only)
     */
    fun kcalForSegment(
        profile: Profile,
        type: ActivityType,
        speedMps: Double,
        grade: Double,
        dtMin: Double,
        climbM: Double
    ): Double {
        if (dtMin <= 0) return 0.0
        val bmrPerMin = bmrKcalPerDay(profile) / 1440.0
        val base = met(type, speedMps, grade) * bmrPerMin * dtMin
        val climb = if (type == ActivityType.BIKE && climbM > 0)
            profile.weightKg * 9.81 * climbM / 0.22 / 4184.0
        else 0.0
        return base + climb
    }
}

/** Exponential moving average for noisy GPS speed. */
class SpeedEma(private val alpha: Double = 0.35) {
    var value: Double = -1.0
        private set

    fun update(x: Double): Double {
        value = if (value < 0) x else alpha * x + (1 - alpha) * value
        return value
    }

    fun reset() { value = -1.0 }
}

/**
 * Elevation pipeline for phone GPS altitude (raw noise is ±10–20 m):
 *  - median over a sliding 7-sample window kills single-sample spikes
 *  - gain/loss is only committed once the smoothed altitude has moved more than
 *    [hysteresisM] in one direction (prevents noise from accumulating into fake climb)
 *  - grade is measured over the trailing >=30 m of horizontal distance, never
 *    point-to-point
 */
class ElevationFilter(
    private val hysteresisM: Double = 2.5,
    private val gradeWindowM: Double = 30.0
) {
    private val window = ArrayDeque<Double>()
    private val track = ArrayDeque<Pair<Double, Double>>()   // (cumDist, smoothedEle)

    var gain: Double = 0.0; private set
    var loss: Double = 0.0; private set
    var current: Double = Double.NaN; private set
    var maxEle: Double = -Double.MAX_VALUE; private set
    /** Positive elevation committed by the most recent feed() — for bike climb work. */
    var lastClimbDelta: Double = 0.0; private set

    private var anchor: Double = Double.NaN   // last committed altitude

    fun feed(rawAltitude: Double?, cumDist: Double): Double {
        lastClimbDelta = 0.0
        if (rawAltitude == null || rawAltitude.isNaN()) return current

        window.addLast(rawAltitude)
        if (window.size > 7) window.removeFirst()
        val smoothed = window.sorted()[window.size / 2]
        current = smoothed
        if (smoothed > maxEle) maxEle = smoothed

        if (anchor.isNaN()) {
            anchor = smoothed
        } else {
            val diff = smoothed - anchor
            if (abs(diff) >= hysteresisM) {
                if (diff > 0) { gain += diff; lastClimbDelta = diff } else loss += -diff
                anchor = smoothed
            }
        }

        track.addLast(cumDist to smoothed)
        while (track.size > 2 && cumDist - track.first().first > gradeWindowM * 4) {
            track.removeFirst()
        }
        return current
    }

    /** Fractional grade over the trailing window; 0 until enough distance exists. */
    fun grade(): Double {
        if (track.size < 2) return 0.0
        val newest = track.last()
        var oldest = track.first()
        for (p in track) {
            if (newest.first - p.first >= gradeWindowM) oldest = p else break
        }
        val run = newest.first - oldest.first
        if (run < gradeWindowM * 0.8) return 0.0
        return ((newest.second - oldest.second) / run).coerceIn(-0.30, 0.30)
    }

    fun reset() {
        window.clear(); track.clear()
        gain = 0.0; loss = 0.0; current = Double.NaN
        maxEle = -Double.MAX_VALUE; anchor = Double.NaN; lastClimbDelta = 0.0
    }
}

/** Per-type GPS sanity thresholds. */
object GpsRules {
    fun accuracyLimitM(type: ActivityType): Float =
        if (type == ActivityType.BIKE) 30f else 25f

    /** Reject teleports faster than this (m/s). */
    fun maxPlausibleSpeed(type: ActivityType): Double = when (type) {
        ActivityType.WALK -> 4.5      // 16 km/h
        ActivityType.RUN -> 8.5       // 30 km/h
        ActivityType.BIKE -> 22.0     // 79 km/h
    }

    fun autoPauseBelow(type: ActivityType): Double =
        if (type == ActivityType.BIKE) 0.8 else 0.4

    fun autoResumeAbove(type: ActivityType): Double =
        if (type == ActivityType.BIKE) 1.4 else 0.7
}
