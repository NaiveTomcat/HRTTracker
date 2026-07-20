package cn.naivetomcat.hrt_tracker.reminder

import cn.naivetomcat.hrt_tracker.data.MedicationPlan
import cn.naivetomcat.hrt_tracker.pk.DoseEvent
import cn.naivetomcat.hrt_tracker.pk.Ester
import cn.naivetomcat.hrt_tracker.pk.Route
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class DoseCheckInMatcherTest {

    private val plan = MedicationPlan(
        name = "晚间用药",
        route = Route.ORAL,
        ester = Ester.E2,
        doseMG = 2.0,
        scheduleType = MedicationPlan.ScheduleType.DAILY,
        timeOfDay = listOf(LocalTime.of(22, 0))
    )
    private val scheduledAtMillis = 1_800_000_000_000L
    private val scheduledTimeH = scheduledAtMillis / 3_600_000.0

    private fun event(
        timeOffsetH: Double,
        route: Route = plan.route,
        doseMG: Double = plan.doseMG
    ) = DoseEvent(
        route = route,
        timeH = scheduledTimeH + timeOffsetH,
        doseMG = doseMG,
        ester = plan.ester
    )

    @Test
    fun `check-ins at both one-hour boundaries suppress the reminder`() {
        assertTrue(
            hasPlanDoseCheckIn(
                plan,
                listOf(event(-1.0), event(1.0)),
                scheduledAtMillis
            )
        )
    }

    @Test
    fun `check-in outside the window does not suppress the reminder`() {
        assertFalse(
            hasPlanDoseCheckIn(
                plan,
                listOf(event(1.01)),
                scheduledAtMillis
            )
        )
    }

    @Test
    fun `different route or dose does not count as this plan`() {
        assertFalse(
            hasPlanDoseCheckIn(
                plan,
                listOf(
                    event(0.0, route = Route.SUBLINGUAL),
                    event(0.0, doseMG = 1.0)
                ),
                scheduledAtMillis
            )
        )
    }

    @Test
    fun `reminder is evaluated after the positive one-hour boundary`() {
        assertTrue(
            reminderEvaluationTimeMillis(scheduledAtMillis) ==
                scheduledAtMillis + 3_600_000L
        )
    }
}
