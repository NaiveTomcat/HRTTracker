package cn.naivetomcat.hrt_tracker.utils

import cn.naivetomcat.hrt_tracker.pk.DoseEvent
import cn.naivetomcat.hrt_tracker.pk.Ester
import cn.naivetomcat.hrt_tracker.pk.Route
import org.junit.Assert.*
import org.junit.Test

/**
 * MedicationPlanPredictor 单元测试
 * 重点测试 filterConflictingPredictions 方法
 */
class MedicationPlanPredictorTest {

    // 基准时间：某个任意绝对小时数
    private val baseTimeH = 10000.0

    private fun makeEvent(route: Route, ester: Ester, timeH: Double) = DoseEvent(
        route = route,
        timeH = timeH,
        doseMG = 2.0,
        ester = ester
    )

    // ---- filterConflictingPredictions ----

    @Test
    fun `filterConflictingPredictions - empty actual events returns all predicted`() {
        val predicted = listOf(
            makeEvent(Route.ORAL, Ester.E2, baseTimeH + 0.5),
            makeEvent(Route.ORAL, Ester.E2, baseTimeH + 1.0)
        )
        val result = MedicationPlanPredictor.filterConflictingPredictions(
            predictedEvents = predicted,
            actualEvents = emptyList()
        )
        assertEquals(predicted, result)
    }

    @Test
    fun `filterConflictingPredictions - empty predicted events returns empty`() {
        val actual = listOf(makeEvent(Route.ORAL, Ester.E2, baseTimeH))
        val result = MedicationPlanPredictor.filterConflictingPredictions(
            predictedEvents = emptyList(),
            actualEvents = actual
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `filterConflictingPredictions - predicted within window is removed`() {
        // Actual dose at baseTimeH; scheduled dose at baseTimeH + 0.5h (within 1h window)
        val actual = listOf(makeEvent(Route.ORAL, Ester.E2, baseTimeH))
        val predicted = listOf(makeEvent(Route.ORAL, Ester.E2, baseTimeH + 0.5))
        val result = MedicationPlanPredictor.filterConflictingPredictions(
            predictedEvents = predicted,
            actualEvents = actual
        )
        assertTrue("Predicted event within 1h window should be removed", result.isEmpty())
    }

    @Test
    fun `filterConflictingPredictions - predicted exactly at window boundary is removed`() {
        // Scheduled dose exactly at baseTimeH + 1.0h (boundary, inclusive)
        val actual = listOf(makeEvent(Route.ORAL, Ester.E2, baseTimeH))
        val predicted = listOf(makeEvent(Route.ORAL, Ester.E2, baseTimeH + 1.0))
        val result = MedicationPlanPredictor.filterConflictingPredictions(
            predictedEvents = predicted,
            actualEvents = actual
        )
        assertTrue("Predicted event at exact window boundary should be removed", result.isEmpty())
    }

    @Test
    fun `filterConflictingPredictions - predicted beyond window is kept`() {
        // Scheduled dose at baseTimeH + 1.5h (outside 1h window)
        val actual = listOf(makeEvent(Route.ORAL, Ester.E2, baseTimeH))
        val predicted = listOf(makeEvent(Route.ORAL, Ester.E2, baseTimeH + 1.5))
        val result = MedicationPlanPredictor.filterConflictingPredictions(
            predictedEvents = predicted,
            actualEvents = actual
        )
        assertEquals("Predicted event beyond 1h window should be kept", 1, result.size)
    }

    @Test
    fun `filterConflictingPredictions - predicted before actual is kept`() {
        // Scheduled dose BEFORE actual dose should not be filtered
        val actual = listOf(makeEvent(Route.ORAL, Ester.E2, baseTimeH + 1.0))
        val predicted = listOf(makeEvent(Route.ORAL, Ester.E2, baseTimeH))
        val result = MedicationPlanPredictor.filterConflictingPredictions(
            predictedEvents = predicted,
            actualEvents = actual
        )
        assertEquals("Predicted event before actual should be kept", 1, result.size)
    }

    @Test
    fun `filterConflictingPredictions - different route is not filtered`() {
        // Actual ORAL dose should not filter a SUBLINGUAL predicted dose
        val actual = listOf(makeEvent(Route.ORAL, Ester.E2, baseTimeH))
        val predicted = listOf(makeEvent(Route.SUBLINGUAL, Ester.E2, baseTimeH + 0.5))
        val result = MedicationPlanPredictor.filterConflictingPredictions(
            predictedEvents = predicted,
            actualEvents = actual
        )
        assertEquals("Different route should not be filtered", 1, result.size)
    }

    @Test
    fun `filterConflictingPredictions - different ester is not filtered`() {
        // Actual E2 dose should not filter an EV predicted dose
        val actual = listOf(makeEvent(Route.ORAL, Ester.E2, baseTimeH))
        val predicted = listOf(makeEvent(Route.ORAL, Ester.EV, baseTimeH + 0.5))
        val result = MedicationPlanPredictor.filterConflictingPredictions(
            predictedEvents = predicted,
            actualEvents = actual
        )
        assertEquals("Different ester should not be filtered", 1, result.size)
    }

    @Test
    fun `filterConflictingPredictions - only conflicting events are removed`() {
        // Two predicted events: one within window, one outside
        val actual = listOf(makeEvent(Route.ORAL, Ester.E2, baseTimeH))
        val predicted = listOf(
            makeEvent(Route.ORAL, Ester.E2, baseTimeH + 0.5),  // within window → removed
            makeEvent(Route.ORAL, Ester.E2, baseTimeH + 2.0)   // outside window → kept
        )
        val result = MedicationPlanPredictor.filterConflictingPredictions(
            predictedEvents = predicted,
            actualEvents = actual
        )
        assertEquals("Only the conflicting event should be removed", 1, result.size)
        assertEquals(baseTimeH + 2.0, result.first().timeH, 1e-9)
    }

    @Test
    fun `filterConflictingPredictions - custom conflict window is respected`() {
        // Use a 2-hour custom window
        val actual = listOf(makeEvent(Route.ORAL, Ester.E2, baseTimeH))
        val predicted = listOf(
            makeEvent(Route.ORAL, Ester.E2, baseTimeH + 1.5),  // within 2h → removed
            makeEvent(Route.ORAL, Ester.E2, baseTimeH + 2.5)   // outside 2h → kept
        )
        val result = MedicationPlanPredictor.filterConflictingPredictions(
            predictedEvents = predicted,
            actualEvents = actual,
            conflictWindowH = 2.0
        )
        assertEquals("Only event within 2h custom window should be removed", 1, result.size)
        assertEquals(baseTimeH + 2.5, result.first().timeH, 1e-9)
    }

    @Test
    fun `filterConflictingPredictions - multiple actuals each suppress their own window`() {
        // Two actual events at different times, each with their own window
        val actual = listOf(
            makeEvent(Route.ORAL, Ester.E2, baseTimeH),
            makeEvent(Route.ORAL, Ester.E2, baseTimeH + 24.0)
        )
        val predicted = listOf(
            makeEvent(Route.ORAL, Ester.E2, baseTimeH + 0.5),   // near first actual → removed
            makeEvent(Route.ORAL, Ester.E2, baseTimeH + 12.0),  // not near any actual → kept
            makeEvent(Route.ORAL, Ester.E2, baseTimeH + 24.5)   // near second actual → removed
        )
        val result = MedicationPlanPredictor.filterConflictingPredictions(
            predictedEvents = predicted,
            actualEvents = actual
        )
        assertEquals("Events near each actual should be removed", 1, result.size)
        assertEquals(baseTimeH + 12.0, result.first().timeH, 1e-9)
    }
}
