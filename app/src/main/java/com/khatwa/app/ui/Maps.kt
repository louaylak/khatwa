package com.khatwa.app.ui

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.khatwa.app.data.TrackPoint
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.max

/**
 * Map stack: MapLibre (open source) + OpenFreeMap tiles.
 * 100% free — no API key, no billing, no quotas, worldwide,
 * with a clean modern look (OSM data, "liberty" style).
 * Style alternatives: .../styles/bright , .../styles/positron
 */
const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

object MapDefaults {
    const val START_LAT = 36.191      // fallback before first GPS fix
    const val START_LON = 5.413
    const val START_ZOOM = 4.5
    /** "Not totally zoomed in, not totally zoomed out" — follow zoom. */
    const val FOLLOW_ZOOM = 16.0
}

/** Live references to a ready map: the map itself, its style, the route source. */
class MapHandles(
    val map: MapLibreMap,
    val style: Style,
    private val routeSource: GeoJsonSource
) {
    /** Draws the first [count] points of the route (full route by default). */
    fun setRoute(points: List<TrackPoint>, count: Int = points.size) {
        try {
            if (points.size < 2) {
                routeSource.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
                return
            }
            val pts = points.take(max(2, count)).map { Point.fromLngLat(it.lon, it.lat) }
            routeSource.setGeoJson(LineString.fromLngLats(pts))
        } catch (_: Exception) { }
    }

    fun followTo(lat: Double, lon: Double, zoom: Double = MapDefaults.FOLLOW_ZOOM, durationMs: Int = 700) {
        try {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), zoom), durationMs)
        } catch (_: Exception) { }
    }

    fun fitRoute(points: List<TrackPoint>, paddingPx: Int = 90) {
        if (points.size < 2) return
        try {
            val b = LatLngBounds.Builder()
            points.forEach { b.include(LatLng(it.lat, it.lon)) }
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(b.build(), paddingPx))
        } catch (_: Exception) { }
    }

    /** Lat/lon -> screen pixels (for the animated character overlay). */
    fun project(lat: Double, lon: Double): android.graphics.PointF? =
        try { map.projection.toScreenLocation(LatLng(lat, lon)) } catch (_: Exception) { null }

    /** Fires on every camera frame (pan, zoom, our follow animation). */
    fun onCameraMove(cb: () -> Unit) {
        map.addOnCameraMoveListener { cb() }
    }

    fun setPuckVisible(visible: Boolean) {
        try {
            if (map.locationComponent.isLocationComponentActivated)
                map.locationComponent.isLocationComponentEnabled = visible
        } catch (_: Exception) { }
    }

    /** follow-mode breaker: fires when the USER pans/pinches (not our animations). */
    fun onUserGesture(onGesture: () -> Unit) {
        map.addOnCameraMoveStartedListener { reason ->
            if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) onGesture()
        }
    }

    /** Google-style pulsing blue location puck. Call only with location permission. */
    @SuppressLint("MissingPermission")
    fun enableLocationPuck(ctx: Context) {
        try {
            val lc = map.locationComponent
            lc.activateLocationComponent(
                LocationComponentActivationOptions.builder(ctx, style).build()
            )
            lc.isLocationComponentEnabled = true
            lc.cameraMode = CameraMode.NONE
            lc.renderMode = RenderMode.NORMAL
        } catch (_: Exception) { }
    }
}

/** MapView wired to the Compose lifecycle (MapLibre requires all callbacks). */
@Composable
fun rememberLifecycleMapView(): MapView {
    val ctx = LocalContext.current
    val mapView = remember { MapView(ctx).apply { onCreate(null) } }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }
    return mapView
}

/** Loads the free style, adds the purple route layer, hands back live handles. */
fun setupKhatwaMap(
    ctx: Context,
    mapView: MapView,
    routeColorArgb: Int,
    onReady: (MapHandles) -> Unit
) {
    mapView.getMapAsync { map ->
        map.cameraPosition = CameraPosition.Builder()
            .target(LatLng(MapDefaults.START_LAT, MapDefaults.START_LON))
            .zoom(MapDefaults.START_ZOOM)
            .build()
        map.uiSettings.apply {
            isCompassEnabled = false
            isRotateGesturesEnabled = true
            isTiltGesturesEnabled = false
        }
        map.setStyle(Style.Builder().fromUri(MAP_STYLE_URL)) { style ->
            val source = GeoJsonSource("khatwa-route-src")
            style.addSource(source)
            style.addLayer(
                LineLayer("khatwa-route-line", "khatwa-route-src").withProperties(
                    PropertyFactory.lineColor(routeColorArgb),
                    PropertyFactory.lineWidth(5f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                )
            )
            onReady(MapHandles(map, style, source))
        }
    }
}
