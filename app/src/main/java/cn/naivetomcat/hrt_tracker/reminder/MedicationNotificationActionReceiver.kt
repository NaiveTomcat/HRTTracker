package cn.naivetomcat.hrt_tracker.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cn.naivetomcat.hrt_tracker.data.AppDatabase
import cn.naivetomcat.hrt_tracker.data.DoseEventEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Handles the system notification's dose actions without opening the app.
 */
class MedicationNotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CONFIRM_DOSE =
            "cn.naivetomcat.hrt_tracker.action.CONFIRM_DOSE"
        const val ACTION_SKIP_DOSE =
            "cn.naivetomcat.hrt_tracker.action.SKIP_DOSE"
        const val EXTRA_PLAN_ID = "plan_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_SCHEDULED_AT_MILLIS = "scheduled_at_millis"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val notificationHelper = NotificationHelper(context)

        if (intent.action == ACTION_SKIP_DOSE) {
            notificationHelper.cancelNotification(notificationId)
            return
        }
        if (intent.action != ACTION_CONFIRM_DOSE) {
            return
        }

        val planId = intent.getStringExtra(EXTRA_PLAN_ID)
            ?.let { value -> runCatching { UUID.fromString(value) }.getOrNull() }
            ?: return
        val scheduledAtMillis = intent.getLongExtra(
            EXTRA_SCHEDULED_AT_MILLIS,
            System.currentTimeMillis()
        )
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getDatabase(context)
                val plan = database.medicationPlanDao()
                    .getPlanById(planId)
                    ?.toMedicationPlan()

                if (plan != null) {
                    val event = createReminderDoseEvent(
                        plan = plan,
                        recordedAtMillis = System.currentTimeMillis(),
                        scheduledAtMillis = scheduledAtMillis
                    )
                    database.doseEventDao().upsertEvent(
                        DoseEventEntity.fromDoseEvent(event)
                    )
                }

                // A deleted plan is also a stale reminder, so dismiss it.
                notificationHelper.cancelNotification(notificationId)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
