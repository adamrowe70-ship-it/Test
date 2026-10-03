package runcoach.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoachTest {
    private val profile = RunnerProfile(age = 45) // est. max HR 176
    private val sevenByThree = IntervalPlan(runSec = 180, walkSec = 60, reps = 7)

    /** Synthetic run following the plan, one sample every 5 s. */
    private fun simulate(
        plan: IntervalPlan,
        runHr: Double,
        walkHr: Double = runHr - 25,
        hrRisePerRep: Double = 0.0,
        runSpeed: Double = 2.4,
        runCadence: Double = 158.0,
        walkedReps: Set<Int> = emptySet(),
        stopAfterSec: Int? = null,
        withSpeedAndCadenceSeries: Boolean = true,
    ): RunData {
        val hr = mutableListOf<TimedValue>()
        val speed = mutableListOf<TimedValue>()
        val cadence = mutableListOf<TimedValue>()
        val steps = mutableListOf<IntervalValue>()
        val end = stopAfterSec ?: plan.totalSec
        for (p in plan.timeline()) {
            var t = p.startSec
            while (t < p.endSec && t < end) {
                val running = p.kind == PhaseKind.RUN && p.index !in walkedReps
                val h = if (running) runHr + hrRisePerRep * p.index else walkHr
                val sp = if (running) runSpeed else 1.4
                val cad = if (running) runCadence else 112.0
                hr += TimedValue(t.toDouble(), h)
                speed += TimedValue(t.toDouble(), sp)
                cadence += TimedValue(t.toDouble(), cad)
                steps += IntervalValue(t.toDouble(), t + 5.0, cad / 12)
                t += 5
            }
        }
        return if (withSpeedAndCadenceSeries) RunData(end.toDouble(), hr, speed, cadence)
        else RunData(end.toDouble(), heartRate = hr, steps = steps)
    }

    @Test
    fun `timeline puts walks only between reps`() {
        val phases = sevenByThree.timeline()
        assertEquals(7, phases.count { it.kind == PhaseKind.RUN })
        assertEquals(6, phases.count { it.kind == PhaseKind.WALK })
        assertEquals(sevenByThree.totalSec, phases.last().endSec)
        assertEquals(300 + 7 * 180 + 6 * 60 + 300, sevenByThree.totalSec)
    }

    @Test
    fun `easy complete session progresses to the next ladder step`() {
        val report = Coach.review(sevenByThree, simulate(sevenByThree, runHr = 140.0), profile, Rpe(5))
        assertEquals(1.0, report.analysis.completion, 1e-9)
        assertEquals(Decision.PROGRESS, report.recommendation.decision)
        assertEquals(IntervalPlan(240, 60, 6), report.recommendation.nextPlan)
        assertTrue(report.recommendation.nextPlan.totalRunSec > sevenByThree.totalRunSec)
    }

    @Test
    fun `high heart rate repeats the session and suggests a slower pace`() {
        val report = Coach.review(sevenByThree, simulate(sevenByThree, runHr = 162.0), profile)
        assertEquals(Decision.REPEAT, report.recommendation.decision)
        assertEquals(sevenByThree, report.recommendation.nextPlan)
        val target = assertNotNull(report.recommendation.targetRunSpeedMps)
        assertTrue(target < 2.4)
    }

    @Test
    fun `strong drift plus hard RPE steps back`() {
        val data = simulate(sevenByThree, runHr = 135.0, hrRisePerRep = 4.0)
        val report = Coach.review(sevenByThree, data, profile, Rpe(9))
        assertTrue(report.analysis.drift!! > ProgressionEngine.HARD_DRIFT)
        assertEquals(Decision.REGRESS, report.recommendation.decision)
        assertEquals(IntervalPlan(180, 90, 6), report.recommendation.nextPlan)
    }

    @Test
    fun `walking two reps lowers completion and repeats`() {
        val report = Coach.review(sevenByThree, simulate(sevenByThree, runHr = 140.0, walkedReps = setOf(5, 6)), profile)
        assertEquals(5.0 / 7, report.analysis.completion, 1e-9)
        assertEquals(Decision.REGRESS, report.recommendation.decision)
    }

    @Test
    fun `stopping early counts as incomplete`() {
        val stop = 300 + 4 * 240 // after four reps
        val report = Coach.review(sevenByThree, simulate(sevenByThree, runHr = 140.0, stopAfterSec = stop), profile)
        assertEquals(4.0 / 7, report.analysis.completion, 0.01)
        assertEquals(Decision.REGRESS, report.recommendation.decision)
    }

    @Test
    fun `cadence and speed fall back to step and distance totals`() {
        val data = simulate(sevenByThree, runHr = 140.0, withSpeedAndCadenceSeries = false)
        val a = RunAnalyzer.analyze(sevenByThree, data, profile)
        assertEquals(158.0, a.runCadenceSpm!!, 0.5)
        assertEquals(112.0, a.walkCadenceSpm!!, 0.5)
        assertEquals(1.0, a.completion, 1e-9)
        assertNull(a.avgRunSpeedMps)
    }

    @Test
    fun `stride length is speed over step rate`() {
        val a = RunAnalyzer.analyze(sevenByThree, simulate(sevenByThree, runHr = 140.0), profile)
        assertEquals(2.4 * 60 / 158, a.runStrideLengthM!!, 1e-6)
    }

    @Test
    fun `top of the ladder stays put`() {
        val top = ProgressionEngine.LADDER.last()
        val report = Coach.review(top, simulate(top, runHr = 140.0), profile, Rpe(4))
        assertEquals(Decision.PROGRESS, report.recommendation.decision)
        assertEquals(top.runSec, report.recommendation.nextPlan.runSec)
    }

    @Test
    fun `ladder grows run time without big jumps`() {
        ProgressionEngine.LADDER.zipWithNext().forEach { (a, b) ->
            val growth = b.totalRunSec.toDouble() / a.totalRunSec
            assertTrue(growth in 0.8..1.35, "$a -> $b grows ${growth}x")
            assertTrue(b.runSec >= a.runSec && (b.runSec > a.runSec || growth > 1.0), "$a -> $b is not harder")
        }
    }

    @Test
    fun `low cadence is nudged up three percent`() {
        val a = RunAnalyzer.analyze(sevenByThree, simulate(sevenByThree, runHr = 140.0, runCadence = 150.0), profile)
        val t = CadencePlanner.target(a)
        assertEquals(155, t.runBpm)
        assertEquals(112, t.walkBpm)
    }
}

class PlaylistPlannerTest {
    @Test
    fun `half and double time songs fit the step rate`() {
        assertEquals(170.0, PlaylistPlanner.stepTempo(85.0, 170, 3.0))
        assertEquals(168.0, PlaylistPlanner.stepTempo(168.0, 170, 3.0))
        assertNull(PlaylistPlanner.stepTempo(120.0, 170, 3.0))
    }

    @Test
    fun `playlist has walk songs around run songs and covers the session`() {
        val plan = IntervalPlan(180, 60, 7)
        val tempo = TempoTarget(runBpm = 165, walkBpm = 115, note = "")
        val pool = (0 until 40).map { i ->
            val bpm = if (i % 3 == 0) 116.0 else 82.0 + (i % 4) // walk songs and half-time run songs
            Track("spotify:track:$i", "Song $i", "Artist", 210_000, bpm, tasteRank = i)
        } + Track("spotify:track:x", "Too slow", "Artist", 200_000, 100.0)

        val p = PlaylistPlanner.build(plan, tempo, pool)
        assertEquals(PhaseKind.WARMUP, p.tracks.first().section)
        assertEquals(PhaseKind.COOLDOWN, p.tracks.last().section)
        assertTrue(p.tracks.none { it.track.name == "Too slow" })
        assertTrue(p.totalMs >= plan.totalSec * 1000L, "playlist ${p.totalMs} ms shorter than session")
        assertEquals(p.tracks.size, p.tracks.map { it.track.uri }.toSet().size)
        p.tracks.filter { it.section == PhaseKind.RUN }.forEach {
            assertTrue(kotlin.math.abs(it.stepBpm - 165) <= 4)
        }
    }
}
