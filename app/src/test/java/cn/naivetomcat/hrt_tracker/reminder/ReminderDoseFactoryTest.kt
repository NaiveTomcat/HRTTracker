package cn.naivetomcat.hrt_tracker.reminder

import cn.naivetomcat.hrt_tracker.data.MedicationPlan
import cn.naivetomcat.hrt_tracker.pk.DoseEvent
import cn.naivetomcat.hrt_tracker.pk.Ester
import cn.naivetomcat.hrt_tracker.pk.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalTime
import java.util.UUID

class ReminderDoseFactoryTest {

    private val plan = MedicationPlan(
        id = UUID.fromString("5f99f21a-4fc4-4457-8d2b-967b25c77541"),
        name = "晚间用药",
        route = Route.SUBLINGUAL,
        ester = Ester.E2,
        doseMG = 2.0,
        scheduleType = MedicationPlan.ScheduleType.DAILY,
        timeOfDay = listOf(LocalTime.of(22, 0)),
        extras = mapOf(DoseEvent.ExtraKey.SUBLINGUAL_TIER to 2.0)
    )

    @Test
    fun `same occurrence creates the same id`() {
        val scheduledAt = 1_800_000_000_000L

        val first = createReminderDoseEvent(plan, scheduledAt + 1_000L, scheduledAt)
        val second = createReminderDoseEvent(plan, scheduledAt + 5_000L, scheduledAt)

        assertEquals(first.id, second.id)
    }

    @Test
    fun `different occurrences create different ids`() {
        val first = createReminderDoseEvent(plan, 1_800_000_000_000L, 1_800_000_000_000L)
        val second = createReminderDoseEvent(plan, 1_800_086_400_000L, 1_800_086_400_000L)

        assertNotEquals(first.id, second.id)
    }

    @Test
    fun `confirmed record copies plan details and confirmation time`() {
        val recordedAt = 1_800_000_123_000L
        val event = createReminderDoseEvent(plan, recordedAt, 1_800_000_000_000L)

        assertEquals(plan.route, event.route)
        assertEquals(plan.ester, event.ester)
        assertEquals(plan.doseMG, event.doseMG, 0.0)
        assertEquals(plan.extras, event.extras)
        assertEquals(recordedAt / 3_600_000.0, event.timeH, 0.0)
    }
}
