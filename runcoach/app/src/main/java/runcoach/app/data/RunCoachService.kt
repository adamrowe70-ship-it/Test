package runcoach.app.data

import android.content.Context
import runcoach.app.BuildConfig
import runcoach.core.CadencePlanner
import runcoach.core.Coach
import runcoach.core.CoachReport
import runcoach.core.IntervalPlan
import runcoach.core.PlaylistPlanner
import runcoach.core.Rpe
import runcoach.core.TempoTarget

data class RunResult(val report: CoachReport, val playlistUrl: String?, val playlistNote: String)

/** The whole flow: latest Garmin run → analysis → next plan → Spotify playlist. */
class RunCoachService(context: Context) {
    val store = Store(context)
    val health = HealthConnectSource(context)
    val spotify = Spotify(store, BuildConfig.SPOTIFY_CLIENT_ID)
    private val tempoProvider = TempoProvider(store)

    /**
     * Analyses the newest run. Returns null if it was already processed, unless you pass [rpe]
     * to re-rate it, which re-runs the analysis against the plan that run was meant to follow.
     */
    suspend fun processLatestRun(rpe: Rpe? = null, makePlaylist: Boolean = true): RunResult? {
        val run = health.latestRun() ?: error("No run found in Health Connect in the last 14 days.")
        val isNew = run.id != store.lastProcessedRunId
        if (!isNew && rpe == null) return null

        val runPlan = if (isNew) store.plan else (store.lastRunPlan ?: store.plan)
        val report = Coach.review(runPlan, run.data, store.profile, rpe)

        store.lastRunPlan = runPlan
        store.plan = report.recommendation.nextPlan
        store.tempo = report.tempo.runBpm to report.tempo.walkBpm
        store.lastProcessedRunId = run.id
        store.lastReport = report.summary()

        if (!makePlaylist || !spotify.isConnected) {
            return RunResult(report, null, if (spotify.isConnected) "" else "Connect Spotify to get a playlist.")
        }
        // The run is already recorded; a Spotify hiccup shouldn't lose the recommendation.
        val (url, note) = runCatching { makePlaylist(report.recommendation.nextPlan, report.tempo) }
            .getOrElse { null to "Couldn't make the playlist: ${it.message}. Tap New playlist to retry." }
        return RunResult(report, url, note)
    }

    /** Builds a playlist for [plan] from your own music. Returns (url or null, note). */
    suspend fun makePlaylist(
        plan: IntervalPlan = store.plan,
        tempo: TempoTarget = store.tempo?.let { TempoTarget(it.first, it.second, "") } ?: CadencePlanner.target(null),
    ): Pair<String?, String> {
        val pool = tempoProvider.withTempo(spotify.tastePool())
        val playlist = PlaylistPlanner.build(plan, tempo, pool, store.bpmTolerance.toDouble())
        val minutes = playlist.totalMs / 60_000
        val needed = plan.totalSec / 60
        if (playlist.tracks.isEmpty()) {
            return null to "None of your ${pool.size} songs match ${tempo.runBpm} BPM. Try a wider BPM tolerance."
        }
        val url = spotify.createPlaylist(playlist)
        store.lastPlaylistUrl = url
        val note = buildString {
            append("${playlist.tracks.size} songs, $minutes min, from ${pool.count { it.bpm != null }} of your songs with known tempo.")
            if (minutes < needed) append(" That's shorter than the $needed min session. Widen the BPM tolerance for more songs.")
        }
        return url to note
    }
}
