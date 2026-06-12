package com.khatwa.app.map

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages the offline Algeria map (OpenStreetMap data in mapsforge .map format,
 * rendered fully offline including side streets and footpaths).
 *
 * Sources: the Esslingen university mirror first (the mapsforge project asks that
 * bulk traffic use mirrors), then the main mapsforge server. The download resumes
 * automatically if interrupted (HTTP Range), and the user can also sideload the
 * file manually via the import button.
 */
object MapStore {

    private val URLS = listOf(
        "https://ftp-stud.hs-esslingen.de/pub/Mirrors/download.mapsforge.org/maps/v5/africa/algeria.map",
        "https://download.mapsforge.org/maps/v5/africa/algeria.map"
    )

    const val APPROX_MB = 277
    private const val MIN_VALID_BYTES = 10L * 1024 * 1024

    fun mapFile(ctx: Context): File =
        File(File(ctx.filesDir, "maps").apply { mkdirs() }, "algeria.map")

    fun isReady(ctx: Context): Boolean =
        mapFile(ctx).let { it.exists() && it.length() > MIN_VALID_BYTES }

    sealed class DL {
        data object Idle : DL()
        data class Running(val pct: Int, val doneMb: Int, val totalMb: Int) : DL()
        data object Done : DL()
        data class Error(val msg: String) : DL()
    }

    fun download(ctx: Context): Flow<DL> = flow {
        val target = mapFile(ctx)
        val part = File(target.parentFile, "algeria.map.part")
        var lastError = "Download failed"
        for (url in URLS) {
            try {
                downloadOne(url, part) { pct, mb, total -> emit(DL.Running(pct, mb, total)) }
                if (part.length() > MIN_VALID_BYTES) {
                    if (target.exists()) target.delete()
                    if (part.renameTo(target)) {
                        emit(DL.Done)
                        return@flow
                    }
                    lastError = "Could not finalize file"
                }
            } catch (e: Exception) {
                lastError = e.message ?: "Network error"
            }
        }
        emit(DL.Error(lastError))
    }.flowOn(Dispatchers.IO)

    private suspend fun downloadOne(
        urlStr: String,
        part: File,
        onProgress: suspend (Int, Int, Int) -> Unit
    ) {
        val existing = if (part.exists()) part.length() else 0L
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
        }
        try {
            val code = conn.responseCode
            val append: Boolean
            var total: Long
            when (code) {
                206 -> { append = true; total = existing + conn.contentLengthLong }
                200 -> { append = false; total = conn.contentLengthLong }
                else -> throw RuntimeException("HTTP $code")
            }
            if (total <= 0) total = APPROX_MB * 1024L * 1024L
            var done = if (append) existing else 0L
            conn.inputStream.use { input ->
                FileOutputStream(part, append).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var lastEmit = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - lastEmit > 300) {
                            lastEmit = now
                            val pct = ((done * 100) / total).toInt().coerceIn(0, 100)
                            onProgress(pct, (done / 1_048_576).toInt(), (total / 1_048_576).toInt())
                        }
                    }
                }
            }
            onProgress(100, (done / 1_048_576).toInt(), (done.coerceAtLeast(total) / 1_048_576).toInt())
        } finally {
            conn.disconnect()
        }
    }

    /** Copies a user-picked algeria.map into app storage. Returns true on success. */
    suspend fun importFrom(ctx: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val target = mapFile(ctx)
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { out -> input.copyTo(out, 64 * 1024) }
            } ?: return@withContext false
            target.length() > MIN_VALID_BYTES
        } catch (e: Exception) {
            false
        }
    }
}
