package cn.naivetomcat.hrt_tracker.reminder

import cn.naivetomcat.hrt_tracker.data.MedicationPlan
import cn.naivetomcat.hrt_tracker.pk.DoseEvent
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Creates the dose record written by the notification's "confirm dose" action.
 *
 * The identifier is derived from the plan and the scheduled occurrence. This
 * makes the operation idempotent if Android delivers the action more than once.
 */
internal fun createReminderDoseEvent(
    plan: MedicationPlan,
    recordedAtMillis: Long,
    scheduledAtMillis: Long
): DoseEvent {
    require(recordedAtMillis > 0)
    require(scheduledAtMillis > 0)

    val occurrenceKey = "reminder:${plan.id}:$scheduledAtMillis"
    return DoseEvent(
        id = UUID.nameUUIDFromBytes(
            occurrenceKey.toByteArray(StandardCharsets.UTF_8)
        ),
        route = plan.route,
        timeH = recordedAtMillis / 3_600_000.0,
        doseMG = plan.doseMG,
        ester = plan.ester,
        extras = plan.extras
    )
}
