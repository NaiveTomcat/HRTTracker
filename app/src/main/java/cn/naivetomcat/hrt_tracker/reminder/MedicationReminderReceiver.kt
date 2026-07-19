package cn.naivetomcat.hrt_tracker.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cn.naivetomcat.hrt_tracker.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 用药提醒广播接收器
 * 接收定时提醒的广播并显示通知
 */
class MedicationReminderReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_PLAN_ID = "plan_id"
        const val EXTRA_PLAN_NAME = "plan_name"
        const val EXTRA_PLAN_DESCRIPTION = "plan_description"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_SCHEDULED_AT_MILLIS = "scheduled_at_millis"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val planId = intent.getStringExtra(EXTRA_PLAN_ID) ?: return
        val planName = intent.getStringExtra(EXTRA_PLAN_NAME) ?: "用药提醒"
        val planDescription = intent.getStringExtra(EXTRA_PLAN_DESCRIPTION) ?: ""
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val scheduledAtMillis = intent.getLongExtra(
            EXTRA_SCHEDULED_AT_MILLIS,
            System.currentTimeMillis()
        )

        // 发送通知
        val notificationHelper = NotificationHelper(context)
        notificationHelper.sendMedicationReminder(
            planId = planId,
            planName = planName,
            description = planDescription,
            notificationId = notificationId,
            scheduledAtMillis = scheduledAtMillis
        )

        // Keep long-running plans alive beyond the pre-scheduled window. Each
        // delivered occurrence refreshes the next batch from the current plan.
        val planUuid = runCatching { UUID.fromString(planId) }.getOrNull() ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val plan = AppDatabase.getDatabase(context)
                    .medicationPlanDao()
                    .getPlanById(planUuid)
                    ?.toMedicationPlan()
                if (plan?.isEnabled == true) {
                    ReminderManager(context).scheduleReminder(plan)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
