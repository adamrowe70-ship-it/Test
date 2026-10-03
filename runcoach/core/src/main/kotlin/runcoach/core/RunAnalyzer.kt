package runcoach.core

import kotlin.math.max
import kotlin.math.min

data class PhaseStats(
    val phase: Phase,
    /** Mean HR, skipping the first 20 % of the phase while HR catches up with effort. */
    val avgHr: Double?,
    val peakHr: Double?,
    val avgSpeedMps: Double?,
    val cadenceSpm: Double?,
    /** Fraction of a RUN phase actually spent running, 0–1; null when there is nothing to judge by. */
    val runningFraction: Double?,
) {
    /** Metres per step. */
    val strideLengthM: Double?
        get() = if (avgSpeedMps != null && cadenceSpm != null && cadenceSpm > 0) avgSpeedMps * 60 / cadenceSpm else null
}

data class RunAnalysis(
    val plan: IntervalPlan,
    val phases: List<PhaseStats>,
    /** Share of the planned run time that was actually run, 0–1. */
    val completion: Double,
    val avgRunHr: Double?,
    val avgRunHrPctMax: Double?,
    val avgRunSpeedMps: Double?,
    val runCadenceSpm: Double?,
    val walkCadenceSpm: Double?,
    val runStrideLengthM: Double?,
    /**
     * Rise in effort cost from the first to the last part of the running, as a fraction.
     * Uses HR per unit speed when speed is known (aerobic decoupling), otherwise plain HR.
     */
    val drift: Double?,
    /** Mean HR drop during walk breaks, bpm. */
    val walkRecoveryBpm: Double?,
    val maxHr: Int,
) {
    val runPhases: List<PhaseStats> get() = phases.filter { it.phase.kind == PhaseKind.RUN }
}

object RunAnalyzer {
    /** Steps/min: above this you are running, below it you are walking. */
    const val RUN_CADENCE_THRESHOLD = 140.0

    /** m/s (~9:15/km). Used only when no cadence is available. */
    const val RUN_SPEED_THRESHOLD = 1.8

    fun analyze(plan: IntervalPlan, data: RunData, profile: RunnerProfile): RunAnalysis {
        val phases = plan.timeline().map { statsFor(it, data) }
        val runs = phases.filter { it.phase.kind == PhaseKind.RUN }
        val walks = phases.filter { it.phase.kind == PhaseKind.WALK }

        val completion = runs.sumOf { s ->
            val ran = if (s.phase.startSec >= data.durationSec) 0.0 else (s.runningFraction ?: 1.0)
            // A session stopped early only gets credit for the part that was recorded.
            val recorded = (min(data.durationSec, s.phase.endSec.toDouble()) - s.phase.startSec)
                .coerceIn(0.0, s.phase.durationSec.toDouble()) / s.phase.durationSec
            ran * recorded * s.phase.durationSec
        } / plan.totalRunSec

        val maxHr = profile.effectiveMaxHr
        val avgRunHr = runs.weightedMean { it.avgHr }
        return RunAnalysis(
            plan = plan,
            phases = phases,
            completion = completion.coerceIn(0.0, 1.0),
            avgRunHr = avgRunHr,
            avgRunHrPctMax = avgRunHr?.let { it / maxHr },
            avgRunSpeedMps = runs.weightedMean { it.avgSpeedMps },
            runCadenceSpm = runs.weightedMean { it.cadenceSpm },
            walkCadenceSpm = (walks + phases.filter { it.phase.kind == PhaseKind.WARMUP })
                .weightedMean { it.cadenceSpm },
            runStrideLengthM = runs.weightedMean { it.strideLengthM },
            drift = drift(plan, data),
            walkRecoveryBpm = walkRecovery(walks, data),
            maxHr = maxHr,
        )
    }

    private fun statsFor(phase: Phase, data: RunData): PhaseStats {
        val start = phase.startSec.toDouble()
        val end = phase.endSec.toDouble()
        val settledStart = start + phase.durationSec * 0.2
        val hr = data.heartRate.within(settledStart, end)
        val speedSamples = data.speed.within(start, end)
        val cadenceSamples = data.cadence.within(start, end)

        val speed = speedSamples.meanOrNull()
            ?: overlapRate(data.distance, start, end)
        val cadence = cadenceSamples.meanOrNull()
            ?: overlapRate(data.steps, start, end)?.times(60)

        val runningFraction = if (phase.kind != PhaseKind.RUN) null else when {
            cadenceSamples.isNotEmpty() -> cadenceSamples.count { it.value >= RUN_CADENCE_THRESHOLD }.toDouble() / cadenceSamples.size
            speedSamples.isNotEmpty() -> speedSamples.count { it.value >= RUN_SPEED_THRESHOLD }.toDouble() / speedSamples.size
            // Only an average cadence: treat 110 spm as all walking and 140 as all running.
            cadence != null -> ((cadence - 110) / (RUN_CADENCE_THRESHOLD - 110)).coerceIn(0.0, 1.0)
            else -> null
        }
        return PhaseStats(phase, hr.meanOrNull(), hr.maxOfOrNull { it.value }, speed, cadence, runningFraction)
    }

    private fun drift(plan: IntervalPlan, data: RunData): Double? {
        // Compare the first and last third of the run reps; a single long rep is split in half.
        val runPhases = plan.timeline().filter { it.kind == PhaseKind.RUN && it.endSec <= data.durationSec }
        val windows: Pair<List<Phase>, List<Phase>> = when {
            runPhases.size >= 3 -> {
                val n = max(1, runPhases.size / 3)
                runPhases.take(n) to runPhases.takeLast(n)
            }
            runPhases.size == 1 && runPhases[0].durationSec >= 600 -> {
                val p = runPhases[0]
                val mid = (p.startSec + p.endSec) / 2
                listOf(p.copy(endSec = mid)) to listOf(p.copy(startSec = mid))
            }
            else -> return null
        }

        fun cost(ps: List<Phase>): Double? {
            val hrs = ps.flatMap { data.heartRate.within(it.startSec + it.durationSec * 0.2, it.endSec.toDouble()) }
            val hr = hrs.meanOrNull() ?: return null
            val speeds = ps.mapNotNull { p ->
                data.speed.within(p.startSec.toDouble(), p.endSec.toDouble()).meanOrNull()
                    ?: overlapRate(data.distance, p.startSec.toDouble(), p.endSec.toDouble())
            }
            return if (speeds.size == ps.size && speeds.all { it > 0 }) hr / speeds.average() else hr
        }

        val first = cost(windows.first) ?: return null
        val last = cost(windows.second) ?: return null
        return (last - first) / first
    }

    private fun walkRecovery(walks: List<PhaseStats>, data: RunData): Double? {
        val drops = walks.mapNotNull { w ->
            val s = w.phase.startSec.toDouble()
            val e = w.phase.endSec.toDouble()
            val atStart = data.heartRate.within(s - 15, s + 5).maxOfOrNull { it.value } ?: return@mapNotNull null
            val atEnd = data.heartRate.within(e - 15, e).meanOrNull() ?: return@mapNotNull null
            atStart - atEnd
        }
        return drops.takeIf { it.isNotEmpty() }?.average()
    }

    private fun List<TimedValue>.within(start: Double, end: Double) = filter { it.tSec >= start && it.tSec < end }

    private fun List<TimedValue>.meanOrNull(): Double? = if (isEmpty()) null else sumOf { it.value } / size

    /** Value per second inside [start, end), sharing each interval's value by time overlap. */
    private fun overlapRate(values: List<IntervalValue>, start: Double, end: Double): Double? {
        var total = 0.0
        var covered = 0.0
        for (v in values) {
            val len = v.endSec - v.startSec
            if (len <= 0) continue
            val overlap = min(end, v.endSec) - max(start, v.startSec)
            if (overlap > 0) {
                total += v.value * overlap / len
                covered += overlap
            }
        }
        // Need data for at least half the phase to trust the rate.
        return if (covered >= (end - start) * 0.5) total / covered else null
    }

    private fun List<PhaseStats>.weightedMean(f: (PhaseStats) -> Double?): Double? {
        var sum = 0.0
        var weight = 0.0
        for (s in this) {
            val v = f(s) ?: continue
            sum += v * s.phase.durationSec
            weight += s.phase.durationSec
        }
        return if (weight > 0) sum / weight else null
    }
}
