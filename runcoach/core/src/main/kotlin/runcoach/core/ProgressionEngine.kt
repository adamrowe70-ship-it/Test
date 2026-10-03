package runcoach.core

import kotlin.math.abs
import kotlin.math.roundToInt

enum class Decision { PROGRESS, REPEAT, REGRESS }

data class Recommendation(
    val decision: Decision,
    val nextPlan: IntervalPlan,
    val reasons: List<String>,
    /** Pace to aim for on the run reps, m/s; null when there is no speed data. */
    val targetRunSpeedMps: Double?,
    /** Keep HR on run reps under this, bpm. */
    val hrCeiling: Int,
)

/**
 * Rule-based coach for general fitness. It moves you one step up or down a ladder of
 * run/walk sessions, from short intervals up to a 45-minute continuous run, based on
 * how hard the last session was.
 */
object ProgressionEngine {
    /**
     * Each step makes the run reps longer or the total run time bigger (by at most ~30 %).
     * Where reps get much longer, total run time can dip slightly to absorb the jump.
     */
    val LADDER: List<IntervalPlan> = listOf(
        IntervalPlan(60, 90, 8),
        IntervalPlan(90, 90, 7),
        IntervalPlan(120, 90, 7),
        IntervalPlan(120, 60, 8),
        IntervalPlan(180, 90, 6),
        IntervalPlan(180, 60, 7),
        IntervalPlan(240, 60, 6),
        IntervalPlan(300, 60, 5),
        IntervalPlan(360, 60, 5),
        IntervalPlan(480, 60, 4),
        IntervalPlan(600, 60, 3),
        IntervalPlan(720, 60, 3),
        IntervalPlan(900, 60, 2),
        IntervalPlan(1080, 60, 2),
        IntervalPlan(1800, 0, 1),
        IntervalPlan(2100, 0, 1),
        IntervalPlan(2400, 0, 1),
        IntervalPlan(2700, 0, 1),
    )

    /** Easy, conversational running stays under ~85 % of max HR. */
    const val EASY_HR_PCT = 0.85
    const val HARD_HR_PCT = 0.90
    const val EASY_DRIFT = 0.07
    const val HARD_DRIFT = 0.12

    /** The ladder step closest to a plan, by run time and rep length. */
    fun nearestLevel(plan: IntervalPlan): Int = LADDER.indices.minBy { i ->
        val l = LADDER[i]
        abs(l.totalRunSec - plan.totalRunSec) + abs(l.runSec - plan.runSec) * 2
    }

    fun recommend(analysis: RunAnalysis, rpe: Rpe? = null): Recommendation {
        val reasons = mutableListOf<String>()
        var hardSignals = 0
        var easySignals = 0
        var signalsAvailable = 0

        val c = analysis.completion
        when {
            c >= 0.95 -> { easySignals++; reasons += "You ran ${pct(c)} of the planned run time." }
            c >= 0.8 -> { hardSignals++; reasons += "You ran ${pct(c)} of the planned run time, so some reps turned into walks." }
            else -> { hardSignals += 2; reasons += "You ran only ${pct(c)} of the planned run time." }
        }
        signalsAvailable++

        analysis.avgRunHrPctMax?.let { p ->
            signalsAvailable++
            val hr = analysis.avgRunHr!!.roundToInt()
            when {
                p > HARD_HR_PCT -> { hardSignals++; reasons += "Average running HR $hr bpm (${pct(p)} of max) is in the hard zone." }
                p <= EASY_HR_PCT -> { easySignals++; reasons += "Average running HR $hr bpm (${pct(p)} of max) is comfortably aerobic." }
                else -> reasons += "Average running HR $hr bpm (${pct(p)} of max) is moderate to hard."
            }
        }

        analysis.drift?.let { d ->
            signalsAvailable++
            when {
                d > HARD_DRIFT -> { hardSignals++; reasons += "Effort rose ${pct(d)} from the first to the last reps. You were tiring." }
                d <= EASY_DRIFT -> { easySignals++; reasons += "Effort stayed steady from the first to the last reps (${signedPct(d)})." }
                else -> reasons += "Effort rose ${pct(d)} over the session. Some fatigue showed by the end."
            }
        }

        rpe?.let {
            signalsAvailable++
            when {
                it.value >= 8 -> { hardSignals++; reasons += "You rated it ${it.value}/10, which is hard." }
                it.value <= 6 -> { easySignals++; reasons += "You rated it ${it.value}/10, which is manageable." }
                else -> reasons += "You rated it ${it.value}/10."
            }
        }

        analysis.walkRecoveryBpm?.let { reasons += "HR dropped ~${it.roundToInt()} bpm in the walk breaks." }

        val decision = when {
            c < 0.8 || hardSignals >= 2 -> Decision.REGRESS
            hardSignals == 1 -> Decision.REPEAT
            // Move up only if every signal we have says easy, and there were at least two.
            easySignals == signalsAvailable && signalsAvailable >= 2 -> Decision.PROGRESS
            else -> Decision.REPEAT
        }

        val level = nearestLevel(analysis.plan)
        val nextLevel = when (decision) {
            Decision.PROGRESS -> (level + 1).coerceAtMost(LADDER.lastIndex)
            Decision.REPEAT -> level
            Decision.REGRESS -> (level - 1).coerceAtLeast(0)
        }
        val base = LADDER[nextLevel]
        // Keep the runner's own warm-up and cool-down lengths.
        val next = if (decision == Decision.REPEAT) analysis.plan
        else base.copy(warmupSec = analysis.plan.warmupSec, cooldownSec = analysis.plan.cooldownSec)

        if (decision == Decision.PROGRESS && level == LADDER.lastIndex) {
            reasons += "You're at the top of the ladder (45 min continuous). Keep this as your base run and add variety, such as one session a week with strides or hills."
        }

        val hrCeiling = (analysis.maxHr * EASY_HR_PCT).roundToInt()
        val targetSpeed = analysis.avgRunSpeedMps?.let { speed ->
            val hr = analysis.avgRunHr
            // HR rises roughly in step with speed at easy efforts, so ease off in proportion.
            if (hr != null && hr > hrCeiling) {
                val slower = speed * hrCeiling / hr
                reasons += "Run slower: about ${fmtPace(slower)} instead of ${fmtPace(speed)}, so you stay under $hrCeiling bpm."
                slower
            } else speed
        }

        return Recommendation(decision, next, reasons, targetSpeed, hrCeiling)
    }

    private fun pct(x: Double) = "${(x * 100).roundToInt()}%"
    private fun signedPct(x: Double) = (if (x >= 0) "+" else "") + pct(x)
}
