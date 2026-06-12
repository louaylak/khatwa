package com.khatwa.app.ui

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.khatwa.app.data.TrackPoint

object MapDefaults {
    /** Fallback camera before the first GPS fix (worldwide app, mid zoom). */
    val START = LatLng(36.191, 5.413)
    const val START_ZOOM = 5f

    /** "Not totally zoomed in, not totally zoomed out" — follow zoom while moving. */
    const val FOLLOW_ZOOM = 16f
}

fun List<TrackPoint>.toLatLngs(): List<LatLng> = map { LatLng(it.lat, it.lon) }

fun boundsOf(points: List<LatLng>): LatLngBounds? {
    if (points.size < 2) return null
    val b = LatLngBounds.Builder()
    points.forEach { b.include(it) }
    return try { b.build() } catch (_: Exception) { null }
}
