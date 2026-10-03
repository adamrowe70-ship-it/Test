package runcoach.core

import kotlin.math.roundToInt

/** Everything the app shows after a run. */
data class CoachReport(
    val analysis: RunAnalysis,
    val recommendation: Recommendation,
    val tempo: TempoTarget,
) {
    fun summary(): String = buildString {
        val a = analysis
        val r = recommendation
        appendLine("Last run: ${a.plan.describe()}")
        a.avgRunSpeedMps?.let { appendLine("  Run pace: ${fmtPace(it)}") }
        a.avgRunHr?.let { appendLine("  Run HR: ${it.roundToInt()} bpm (max ${a.maxHr})") }
        a.runCadenceSpm?.let { appendLine("  Cadence: ${it.roundToInt()} spm") }
        a.runStrideLengthM?.let { appendLine("  Stride length: ${"%.2f".format(it)} m") }
        appendLine()
        appendLine(
            when (r.decision) {
                Decision.PROGRESS -> "Next: step up → ${r.nextPlan.describe()}"
                Decision.REPEAT -> "Next: repeat → ${r.nextPlan.describe()}"
                Decision.REGRESS -> "Next: step back → ${r.nextPlan.describe()}"
            }
        )
        r.reasons.forEach { appendLine("  • $it") }
        appendLine("  • Keep run HR under ${r.hrCeiling} bpm.")
        appendLine()
        appendLine("Music: ${tempo.note}")
    }
}

object Coach {
    fun review(plan: IntervalPlan, data: RunData, profile: RunnerProfile, rpe: Rpe? = null): CoachReport {
        val analysis = RunAnalyzer.analyze(plan, data, profile)
        return CoachReport(analysis, ProgressionEngine.recommend(analysis, rpe), CadencePlanner.target(analysis))
    }
}
