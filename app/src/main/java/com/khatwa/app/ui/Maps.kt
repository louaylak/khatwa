package com.khatwa.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.khatwa.app.map.MapStore
import org.osmdroid.mapsforge.MapsForgeTileProvider
import org.osmdroid.mapsforge.MapsForgeTileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

object MapDefaults {
    /** Sétif city center — sensible default before the first GPS fix. */
    val SETIF: GeoPoint = GeoPoint(36.191, 5.413)
    const val ZOOM = 13.5
}

/**
 * Switches the map to the offline mapsforge renderer when algeria.map is present,
 * otherwise falls back to online OpenStreetMap tiles.
 * @return true when the offline map is active.
 */
fun applyTileSource(map: MapView, ctx: Context): Boolean {
    val f = MapStore.mapFile(ctx)
    return try {
        if (f.exists() && f.length() > 10L * 1024 * 1024) {
            val source = MapsForgeTileSource.createFromFiles(arrayOf(f))
            val provider = MapsForgeTileProvider(
                SimpleRegisterReceiver(ctx.applicationContext), source, null
            )
            map.tileProvider = provider
            map.setUseDataConnection(false)
            map.setMaxZoomLevel(20.0)
            true
        } else {
            map.setTileSource(TileSourceFactory.MAPNIK)
            map.setUseDataConnection(true)
            false
        }
    } catch (e: Exception) {
        // corrupt file / renderer issue: never crash, just go online
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setUseDataConnection(true)
        false
    }
}

/** MapView with Compose lifecycle handling (resume/pause/detach). */
@Composable
fun rememberMapView(): MapView {
    val ctx = LocalContext.current
    val map = remember {
        MapView(ctx).apply {
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            setTilesScaledToDpi(true)
            controller.setZoom(MapDefaults.ZOOM)
            controller.setCenter(MapDefaults.SETIF)
        }
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> {}
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            map.onDetach()
        }
    }
    return map
}

/** Small white-ringed dot used as the "you are here" marker. */
fun dotDrawable(ctx: Context, argb: Int): BitmapDrawable {
    val density = ctx.resources.displayMetrics.density
    val size = (density * 22).toInt().coerceAtLeast(16)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    paint.color = argb
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - density * 3.5f, paint)
    return BitmapDrawable(ctx.resources, bmp)
}

fun newLocationMarker(map: MapView, ctx: Context, argb: Int): Marker =
    Marker(map).apply {
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        icon = dotDrawable(ctx, argb)
        setInfoWindow(null)
    }

fun newRouteLine(argb: Int, widthPx: Float): Polyline =
    Polyline().apply {
        outlinePaint.color = argb
        outlinePaint.strokeWidth = widthPx
        outlinePaint.strokeCap = Paint.Cap.ROUND
        outlinePaint.strokeJoin = Paint.Join.ROUND
        outlinePaint.isAntiAlias = true
        setInfoWindow(null)
    }

fun zoomToRoute(map: MapView, pts: List<GeoPoint>) {
    when {
        pts.isEmpty() -> {}
        pts.size == 1 -> {
            map.controller.setCenter(pts.first())
            map.controller.setZoom(16.0)
        }
        else -> {
            val bb = BoundingBox.fromGeoPoints(pts)
            map.post {
                try {
                    map.zoomToBoundingBox(bb, false, 90)
                } catch (_: Exception) {
                    map.controller.setCenter(pts.first())
                }
            }
        }
    }
}
