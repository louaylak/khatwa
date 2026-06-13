package com.khatwa.app.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

// ----------------------------- Models -----------------------------

enum class ActivityType(val label: String) {
    WALK("Walk"), RUN("Run"), BIKE("Bike");

    companion object {
        fun from(name: String?): ActivityType =
            entries.firstOrNull { it.name == name } ?: WALK
    }
}

enum class Gender { MALE, FEMALE }

data class Profile(
    val id: String,
    val name: String,
    val gender: Gender,
    val age: Int,
    val heightCm: Double,
    val activityLevel: Double = 1.375,   // BMR multiplier: 1.2 sedentary .. 1.725 very active
    val weightKg: Double,
    val avatarPath: String? = null
)

/** One GPS sample kept for the route. t = ms offset from activity start. */
data class TrackPoint(
    val lat: Double,
    val lon: Double,
    val ele: Double,      // smoothed elevation, meters
    val t: Long,          // ms since start
    val speed: Float,     // m/s (smoothed)
    val dist: Float       // cumulative meters at this point
)

data class Split(val km: Int, val seconds: Int)

data class ActivitySummary(
    val id: String,
    val profileId: String,
    val type: ActivityType,
    val title: String,
    val startEpochMs: Long,
    val distanceM: Double,
    val movingSec: Int,
    val elapsedSec: Int,
    val calories: Double,
    val elevGainM: Double,
    val elevLossM: Double,
    val maxElevM: Double,
    val avgSpeedKmh: Double,
    val maxSpeedKmh: Double,
    val fastestSplitSec: Int,       // moving seconds of fastest full km, 0 if none
    val splits: List<Split>
)

// ----------------------------- JSON -----------------------------

object Json {
    fun profileToJson(p: Profile): JSONObject = JSONObject().apply {
        put("id", p.id); put("name", p.name); put("gender", p.gender.name)
        put("age", p.age); put("heightCm", p.heightCm); put("weightKg", p.weightKg)
        put("activityLevel", p.activityLevel)
        put("avatarPath", p.avatarPath ?: JSONObject.NULL)
    }

    fun profileFromJson(o: JSONObject): Profile = Profile(
        id = o.getString("id"),
        name = o.getString("name"),
        gender = if (o.optString("gender") == "FEMALE") Gender.FEMALE else Gender.MALE,
        age = o.optInt("age", 25),
        heightCm = o.optDouble("heightCm", 170.0),
        weightKg = o.optDouble("weightKg", 70.0),
        activityLevel = o.optDouble("activityLevel", 1.375),
        avatarPath = if (o.isNull("avatarPath")) null else o.optString("avatarPath")
    )

    fun summaryToJson(a: ActivitySummary): JSONObject = JSONObject().apply {
        put("id", a.id); put("profileId", a.profileId); put("type", a.type.name)
        put("title", a.title); put("startEpochMs", a.startEpochMs)
        put("distanceM", a.distanceM); put("movingSec", a.movingSec); put("elapsedSec", a.elapsedSec)
        put("calories", a.calories); put("elevGainM", a.elevGainM); put("elevLossM", a.elevLossM)
        put("maxElevM", a.maxElevM); put("avgSpeedKmh", a.avgSpeedKmh); put("maxSpeedKmh", a.maxSpeedKmh)
        put("fastestSplitSec", a.fastestSplitSec)
        put("splits", JSONArray().also { arr ->
            a.splits.forEach { s -> arr.put(JSONObject().put("km", s.km).put("seconds", s.seconds)) }
        })
    }

    fun summaryFromJson(o: JSONObject): ActivitySummary {
        val splits = mutableListOf<Split>()
        val arr = o.optJSONArray("splits") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val s = arr.getJSONObject(i)
            splits.add(Split(s.getInt("km"), s.getInt("seconds")))
        }
        return ActivitySummary(
            id = o.getString("id"),
            profileId = o.getString("profileId"),
            type = ActivityType.from(o.optString("type")),
            title = o.optString("title", "Activity"),
            startEpochMs = o.getLong("startEpochMs"),
            distanceM = o.getDouble("distanceM"),
            movingSec = o.getInt("movingSec"),
            elapsedSec = o.getInt("elapsedSec"),
            calories = o.getDouble("calories"),
            elevGainM = o.optDouble("elevGainM", 0.0),
            elevLossM = o.optDouble("elevLossM", 0.0),
            maxElevM = o.optDouble("maxElevM", 0.0),
            avgSpeedKmh = o.optDouble("avgSpeedKmh", 0.0),
            maxSpeedKmh = o.optDouble("maxSpeedKmh", 0.0),
            fastestSplitSec = o.optInt("fastestSplitSec", 0),
            splits = splits
        )
    }

    fun routeToJson(points: List<TrackPoint>): JSONArray = JSONArray().also { arr ->
        points.forEach { p ->
            arr.put(JSONArray().apply {
                put(p.lat); put(p.lon); put(p.ele); put(p.t); put(p.speed.toDouble()); put(p.dist.toDouble())
            })
        }
    }

    fun routeFromJson(arr: JSONArray): List<TrackPoint> {
        val out = ArrayList<TrackPoint>(arr.length())
        for (i in 0 until arr.length()) {
            val a = arr.getJSONArray(i)
            out.add(
                TrackPoint(
                    lat = a.getDouble(0), lon = a.getDouble(1), ele = a.getDouble(2),
                    t = a.getLong(3), speed = a.getDouble(4).toFloat(), dist = a.getDouble(5).toFloat()
                )
            )
        }
        return out
    }
}

// ----------------------------- Profile store -----------------------------

class ProfileStore(private val ctx: Context) {

    private val file: File get() = File(ctx.filesDir, "profiles.json")
    private val avatarsDir: File get() = File(ctx.filesDir, "avatars").apply { mkdirs() }

    fun list(): List<Profile> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { Json.profileFromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun get(id: String): Profile? = list().firstOrNull { it.id == id }

    fun save(p: Profile) {
        val all = list().filter { it.id != p.id } + p
        writeAll(all)
    }

    fun delete(id: String) {
        writeAll(list().filter { it.id != id })
        File(avatarsDir, "$id.jpg").delete()
    }

    private fun writeAll(all: List<Profile>) {
        val arr = JSONArray().also { a -> all.forEach { a.put(Json.profileToJson(it)) } }
        file.writeText(arr.toString())
    }

    /** Copies a picked image into app storage; returns the stored path. */
    fun saveAvatar(profileId: String, uri: Uri): String? = try {
        val dst = File(avatarsDir, "$profileId.jpg")
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            dst.outputStream().use { output -> input.copyTo(output) }
        }
        dst.absolutePath
    } catch (e: Exception) {
        null
    }

    companion object {
        fun newId(): String = UUID.randomUUID().toString().substring(0, 8)
    }
}

// ----------------------------- Activity store -----------------------------

class ActivityStore(private val ctx: Context) {

    private val dir: File get() = File(ctx.filesDir, "activities").apply { mkdirs() }

    fun save(summary: ActivitySummary, route: List<TrackPoint>) {
        File(dir, "${summary.id}.meta.json").writeText(Json.summaryToJson(summary).toString())
        File(dir, "${summary.id}.route.json").writeText(Json.routeToJson(route).toString())
    }

    fun list(profileId: String?): List<ActivitySummary> {
        val files = dir.listFiles { f -> f.name.endsWith(".meta.json") } ?: return emptyList()
        return files.mapNotNull { f ->
            try { Json.summaryFromJson(JSONObject(f.readText())) } catch (e: Exception) { null }
        }.filter { profileId == null || it.profileId == profileId }
            .sortedByDescending { it.startEpochMs }
    }

    fun get(id: String): ActivitySummary? {
        val f = File(dir, "$id.meta.json")
        if (!f.exists()) return null
        return try { Json.summaryFromJson(JSONObject(f.readText())) } catch (e: Exception) { null }
    }

    fun loadRoute(id: String): List<TrackPoint> {
        val f = File(dir, "$id.route.json")
        if (!f.exists()) return emptyList()
        return try { Json.routeFromJson(JSONArray(f.readText())) } catch (e: Exception) { emptyList() }
    }

    fun updateTitle(id: String, title: String) {
        val a = get(id) ?: return
        File(dir, "$id.meta.json").writeText(Json.summaryToJson(a.copy(title = title)).toString())
    }

    fun delete(id: String) {
        File(dir, "$id.meta.json").delete()
        File(dir, "$id.route.json").delete()
    }

    fun deleteAllFor(profileId: String) {
        list(profileId).forEach { delete(it.id) }
    }
}

// ----------------------------- Prefs -----------------------------

class Prefs(ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("khatwa", Context.MODE_PRIVATE)

    var activeProfileId: String?
        get() = sp.getString("activeProfileId", null)
        set(v) = sp.edit().putString("activeProfileId", v).apply()

    /** True once the user has either downloaded the map or chosen online mode. */
    var mapPromptDone: Boolean
        get() = sp.getBoolean("mapPromptDone", false)
        set(v) = sp.edit().putBoolean("mapPromptDone", v).apply()

    var country: String?
        get() = sp.getString("country", null)
        set(v) { sp.edit().putString("country", v).apply() }

    var batteryPromptShown: Boolean
        get() = sp.getBoolean("batteryPromptShown", false)
        set(v) = sp.edit().putBoolean("batteryPromptShown", v).apply()
}
