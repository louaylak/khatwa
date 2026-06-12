package com.khatwa.app.tracking

import com.khatwa.app.data.ActivityType
import com.khatwa.app.data.Split
import com.khatwa.app.data.TrackPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class TrackStatus { IDLE, TRACKING, AUTO_PAUSED, PAUSED }

data class LiveTrack(
    val status: TrackStatus = TrackStatus.IDLE,
    val type: ActivityType = ActivityType.WALK,
    val startEpochMs: Long = 0L,
    val elapsedMs: Long = 0L,
    val movingMs: Long = 0L,
    val distanceM: Double = 0.0,
    val calories: Double = 0.0,
    val speedMps: Double = 0.0,
    val maxSpeedMps: Double = 0.0,
    val elevGainM: Double = 0.0,
    val elevLossM: Double = 0.0,
    val currentEleM: Double = Double.NaN,
    val points: List<TrackPoint> = emptyList(),
    val splits: List<Split> = emptyList(),
    val gpsAccuracyM: Float? = null,
    /** Set once after a successful save; UI consumes it to navigate. */
    val savedId: String? = null,
    /** One-shot toast-style message (e.g. "Too short — not saved"). */
    val message: String? = null
)

/**
 * Single source of truth for the in-progress activity. The TrackingService is the
 * only writer; any number of composables read it. Living in the process means the
 * UI can be killed and reopened mid-walk without losing the live state.
 */
object TrackingManager {

    private val _state = MutableStateFlow(LiveTrack())
    val state: StateFlow<LiveTrack> = _state

    fun update(transform: (LiveTrack) -> LiveTrack) {
        _state.value = transform(_state.value)
    }

    fun resetToIdle() {
        _state.value = LiveTrack()
    }

    fun consumeSaved(): String? {
        val id = _state.value.savedId
        if (id != null) _state.value = _state.value.copy(savedId = null)
        return id
    }

    fun consumeMessage(): String? {
        val m = _state.value.message
        if (m != null) _state.value = _state.value.copy(message = null)
        return m
    }
}
