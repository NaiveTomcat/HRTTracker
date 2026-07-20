package cn.naivetomcat.hrt_tracker.reminder

import cn.naivetomcat.hrt_tracker.data.MedicationPlan
import cn.naivetomcat.hrt_tracker.pk.DoseEvent
import kotlin.math.abs

internal const val DOSE_CHECK_IN_WINDOW_HOURS = 1.0
internal const val DOSE_CHECK_IN_WINDOW_MILLIS = 60 * 60 * 1000L
private const val DOSE_TOLERANCE_MG = 0.000_001

/**
 * Dose records do not currently store a plan ID, so use the stable medication
 * fields to determine whether a check-in belongs to a plan.
 */
internal fun DoseEvent.matchesPlanDose(plan: MedicationPlan): Boolean =
    route == plan.route &&
        ester == plan.ester &&
        abs(doseMG - plan.doseMG) <= DOSE_TOLERANCE_MG

internal fun hasPlanDoseCheckIn(
    plan: MedicationPlan,
    events: List<DoseEvent>,
    scheduledAtMillis: Long
): Boolean {
    val scheduledTimeH = scheduledAtMillis / 3_600_000.0
    return events.any { event ->
        event.matchesPlanDose(plan) &&
            abs(event.timeH - scheduledTimeH) <= DOSE_CHECK_IN_WINDOW_HOURS
    }
}

internal fun reminderEvaluationTimeMillis(scheduledAtMillis: Long): Long =
    scheduledAtMillis + DOSE_CHECK_IN_WINDOW_MILLIS
