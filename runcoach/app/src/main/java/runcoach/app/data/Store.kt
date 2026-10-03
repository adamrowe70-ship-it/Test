package runcoach.app.data

import android.content.Context
import org.json.JSONObject
import runcoach.core.IntervalPlan
import runcoach.core.RunnerProfile

/** Small key-value storage for settings, the current plan and Spotify tokens. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("runcoach", Context.MODE_PRIVATE)

    var profile: RunnerProfile
        get() = RunnerProfile(
            age = prefs.getInt("age", 40),
            maxHr = prefs.getInt("maxHr", 0).takeIf { it > 0 },
        )
        set(v) = prefs.edit()
            .putInt("age", v.age)
            .putInt("maxHr", v.maxHr ?: 0)
            .apply()

    /** The session you are going to run next. Starts at 7 × 3 min run / 1 min walk. */
    var plan: IntervalPlan
        get() = readPlan("plan") ?: IntervalPlan(runSec = 180, walkSec = 60, reps = 7)
        set(v) = writePlan("plan", v)

    /** The plan the last analysed run was meant to follow. */
    var lastRunPlan: IntervalPlan?
        get() = readPlan("lastRunPlan")
        set(v) = writePlan("lastRunPlan", v)

    /** Step rates (run, walk) the next playlist should match. */
    var tempo: Pair<Int, Int>?
        get() = prefs.getString("tempo", null)?.split(",")?.let { it[0].toInt() to it[1].toInt() }
        set(v) = prefs.edit().putString("tempo", v?.let { "${it.first},${it.second}" }).apply()

    private fun readPlan(key: String): IntervalPlan? =
        prefs.getString(key, null)?.split(",")?.map { it.toInt() }?.let { IntervalPlan(it[0], it[1], it[2], it[3], it[4]) }

    private fun writePlan(key: String, p: IntervalPlan?) = prefs.edit()
        .putString(key, p?.let { "${it.runSec},${it.walkSec},${it.reps},${it.warmupSec},${it.cooldownSec}" })
        .apply()

    /** ±BPM a song may be from the target step rate. */
    var bpmTolerance: Int
        get() = prefs.getInt("bpmTolerance", 4)
        set(v) = prefs.edit().putInt("bpmTolerance", v).apply()

    /** Health Connect id of the last run turned into a recommendation. */
    var lastProcessedRunId: String?
        get() = prefs.getString("lastRunId", null)
        set(v) = prefs.edit().putString("lastRunId", v).apply()

    var lastReport: String?
        get() = prefs.getString("lastReport", null)
        set(v) = prefs.edit().putString("lastReport", v).apply()

    var lastPlaylistUrl: String?
        get() = prefs.getString("lastPlaylistUrl", null)
        set(v) = prefs.edit().putString("lastPlaylistUrl", v).apply()

    var spotifyAccessToken: String?
        get() = prefs.getString("sp.access", null)
        set(v) = prefs.edit().putString("sp.access", v).apply()

    var spotifyRefreshToken: String?
        get() = prefs.getString("sp.refresh", null)
        set(v) = prefs.edit().putString("sp.refresh", v).apply()

    var spotifyTokenExpiry: Long
        get() = prefs.getLong("sp.expiry", 0)
        set(v) = prefs.edit().putLong("sp.expiry", v).apply()

    var pkceVerifier: String?
        get() = prefs.getString("sp.verifier", null)
        set(v) = prefs.edit().putString("sp.verifier", v).apply()

    /** Spotify track id → "bpm,energy", or "" when the provider doesn't know the song. */
    var tempoCache: MutableMap<String, String>
        get() {
            val o = JSONObject(prefs.getString("tempoCache", "{}")!!)
            return o.keys().asSequence().associateWith { o.getString(it) }.toMutableMap()
        }
        set(v) = prefs.edit().putString("tempoCache", JSONObject(v as Map<*, *>).toString()).apply()
}
