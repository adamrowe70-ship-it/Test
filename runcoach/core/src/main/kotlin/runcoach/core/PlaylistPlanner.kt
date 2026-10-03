package runcoach.core

import kotlin.math.abs
import kotlin.math.roundToInt

/** Song tempo targets for the next session, in beats per minute (1 beat = 1 step). */
data class TempoTarget(
    val runBpm: Int,
    val walkBpm: Int,
    val note: String,
)

object CadencePlanner {
    const val DEFAULT_RUN_CADENCE = 160
    const val DEFAULT_WALK_CADENCE = 115

    /**
     * Matches songs to the cadence you actually run at. If your cadence is low (under 165 spm),
     * it nudges it up ~3 % per session. Small cadence increases shorten the stride and lower the
     * impact on knees and hips, and 3 % is small enough to feel natural.
     */
    fun target(analysis: RunAnalysis?): TempoTarget {
        val run = analysis?.runCadenceSpm
        val walk = analysis?.walkCadenceSpm
        val runBpm: Int
        val note: String
        when {
            run == null -> {
                runBpm = DEFAULT_RUN_CADENCE
                note = "No cadence data yet, so using $DEFAULT_RUN_CADENCE spm."
            }
            run < 165 -> {
                runBpm = (run * 1.03).roundToInt().coerceAtMost(170)
                note = "Your cadence was ${run.roundToInt()} spm. Songs are at $runBpm BPM to nudge it up a little and shorten your stride."
            }
            else -> {
                runBpm = run.roundToInt().coerceAtMost(185)
                note = "Your cadence of ${run.roundToInt()} spm is in a good range, so the songs match it."
            }
        }
        return TempoTarget(runBpm, walk?.roundToInt() ?: DEFAULT_WALK_CADENCE, note)
    }
}

data class Track(
    val uri: String,
    val name: String,
    val artist: String,
    val durationMs: Long,
    /** Tempo from a BPM provider; null when unknown. */
    val bpm: Double? = null,
    /** 0–1 from the provider, if available. */
    val energy: Double? = null,
    /** Lower is more loved, e.g. position in your top tracks. */
    val tasteRank: Int = 0,
)

data class PlannedTrack(val track: Track, val section: PhaseKind, val stepBpm: Double)

data class PlaylistPlan(
    val name: String,
    val description: String,
    val tracks: List<PlannedTrack>,
    /** Tracks in the pool that had a tempo and matched a target. */
    val matchedPoolSize: Int,
) {
    val totalMs: Long get() = tracks.sumOf { it.track.durationMs }
}

object PlaylistPlanner {
    /**
     * A song fits a step rate if its beat, half-time or double-time is close to it.
     * For example, an 85 BPM song fits 170 spm. Returns the effective step BPM or null.
     */
    fun stepTempo(songBpm: Double, target: Int, tolerance: Double): Double? =
        listOf(songBpm, songBpm * 2, songBpm / 2)
            .filter { abs(it - target) <= tolerance }
            .minByOrNull { abs(it - target) }

    /**
     * Builds an ordered playlist for the session. It opens with walk-tempo songs for the
     * warm-up, then run-tempo songs for the run/walk block, and ends with walk-tempo songs
     * for the cool-down. Songs can't line up with 1–3 min intervals, so the middle block
     * uses run tempo throughout. The walk breaks are short enough to walk over it.
     */
    fun build(
        plan: IntervalPlan,
        tempo: TempoTarget,
        pool: List<Track>,
        tolerance: Double = 4.0,
        name: String = "RunCoach · ${plan.describe()}",
    ): PlaylistPlan {
        val distinct = pool.distinctBy { it.uri }
        val runFits = distinct.mapNotNull { t -> t.bpm?.let { stepTempo(it, tempo.runBpm, tolerance) }?.let { t to it } }
        val walkFits = distinct.mapNotNull { t -> t.bpm?.let { stepTempo(it, tempo.walkBpm, tolerance) }?.let { t to it } }

        val used = mutableSetOf<String>()
        val out = mutableListOf<PlannedTrack>()

        fun fill(section: PhaseKind, fits: List<Pair<Track, Double>>, target: Int, seconds: Int, highEnergyFirst: Boolean) {
            var remainingMs = seconds * 1000L
            // Prefer songs you love most and whose tempo is closest. For the run block, put higher energy first.
            val ranked = fits
                .filter { it.first.uri !in used }
                .sortedWith(
                    compareBy<Pair<Track, Double>> { it.first.tasteRank + abs(it.second - target) * 5 }
                        .thenBy { if (highEnergyFirst) -(it.first.energy ?: 0.5) else 0.0 }
                )
            for ((track, step) in ranked) {
                if (remainingMs <= 0) break
                out += PlannedTrack(track, section, step)
                used += track.uri
                remainingMs -= track.durationMs
            }
        }

        val mainSec = plan.totalSec - plan.warmupSec - plan.cooldownSec
        fill(PhaseKind.WARMUP, walkFits, tempo.walkBpm, plan.warmupSec, highEnergyFirst = false)
        fill(PhaseKind.RUN, runFits, tempo.runBpm, mainSec, highEnergyFirst = true)
        fill(PhaseKind.COOLDOWN, walkFits, tempo.walkBpm, plan.cooldownSec, highEnergyFirst = false)

        val description = "${plan.describe()} · run ${tempo.runBpm} BPM, walk ${tempo.walkBpm} BPM · made by RunCoach"
        return PlaylistPlan(name, description, out, (runFits + walkFits).map { it.first.uri }.toSet().size)
    }
}
