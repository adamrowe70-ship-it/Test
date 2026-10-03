package runcoach.core

/**
 * A run/walk session: an optional warm-up walk, [reps] × ([runSec] running + [walkSec] walking),
 * then an optional cool-down walk. A continuous run is `reps = 1, walkSec = 0`.
 */
data class IntervalPlan(
    val runSec: Int,
    val walkSec: Int,
    val reps: Int,
    val warmupSec: Int = 300,
    val cooldownSec: Int = 300,
) {
    init {
        require(runSec > 0 && reps > 0 && walkSec >= 0 && warmupSec >= 0 && cooldownSec >= 0)
    }

    val isContinuous: Boolean get() = reps == 1 && walkSec == 0
    val totalRunSec: Int get() = runSec * reps

    /** Walk breaks only sit *between* run reps; the last rep flows into the cool-down. */
    val totalSec: Int get() = warmupSec + totalRunSec + walkSec * (reps - 1) + cooldownSec

    fun describe(): String =
        if (isContinuous) "${fmtDuration(runSec)} continuous run"
        else "$reps × ${fmtDuration(runSec)} run / ${fmtDuration(walkSec)} walk"
}

enum class PhaseKind { WARMUP, RUN, WALK, COOLDOWN }

/** One block of the planned timeline, in seconds from the session start. */
data class Phase(val kind: PhaseKind, val index: Int, val startSec: Int, val endSec: Int) {
    val durationSec: Int get() = endSec - startSec
}

fun IntervalPlan.timeline(): List<Phase> {
    val phases = mutableListOf<Phase>()
    var t = 0
    fun add(kind: PhaseKind, index: Int, dur: Int) {
        if (dur > 0) phases += Phase(kind, index, t, t + dur)
        t += dur
    }
    add(PhaseKind.WARMUP, 0, warmupSec)
    for (i in 0 until reps) {
        add(PhaseKind.RUN, i, runSec)
        if (i < reps - 1) add(PhaseKind.WALK, i, walkSec)
    }
    add(PhaseKind.COOLDOWN, 0, cooldownSec)
    return phases
}

/** A point-in-time reading, e.g. heart rate (bpm), speed (m/s) or cadence (steps/min). */
data class TimedValue(val tSec: Double, val value: Double)

/** A value accumulated over an interval, e.g. steps or metres. */
data class IntervalValue(val startSec: Double, val endSec: Double, val value: Double)

/**
 * Raw data for one recorded run, with times in seconds from the session start.
 * Every series is optional: different watches/apps write different things into Health Connect.
 */
data class RunData(
    val durationSec: Double,
    val heartRate: List<TimedValue> = emptyList(),
    val speed: List<TimedValue> = emptyList(),
    val cadence: List<TimedValue> = emptyList(),
    val steps: List<IntervalValue> = emptyList(),
    val distance: List<IntervalValue> = emptyList(),
)

data class RunnerProfile(
    val age: Int,
    /** Measured max HR if known; otherwise estimated from age. */
    val maxHr: Int? = null,
) {
    /** Tanaka et al. (2001): 208 − 0.7 × age. */
    val effectiveMaxHr: Int get() = maxHr ?: (208 - 0.7 * age).toInt()
}

/** How the run felt, 1 (very easy) – 10 (maximal). Optional user input. */
@JvmInline
value class Rpe(val value: Int) {
    init {
        require(value in 1..10)
    }
}

internal fun fmtDuration(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return when {
        s == 0 -> "$m min"
        m == 0 -> "$s s"
        else -> "$m:${s.toString().padStart(2, '0')} min"
    }
}

/** Pace in min:ss per km from a speed in m/s. */
fun fmtPace(speedMps: Double): String {
    if (speedMps <= 0.0) return "–"
    val secPerKm = (1000.0 / speedMps).toInt()
    return "${secPerKm / 60}:${(secPerKm % 60).toString().padStart(2, '0')}/km"
}
