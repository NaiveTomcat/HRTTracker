package cn.naivetomcat.hrt_tracker.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cn.naivetomcat.hrt_tracker.data.AppDatabase
import cn.naivetomcat.hrt_tracker.data.MedicationPlanRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Restores medication alarms after restart, app update, or clock changes.
 */
class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getDatabase(context)
                val plans = MedicationPlanRepository(database.medicationPlanDao())
                    .getAllPlans()
                    .first()
                ReminderManager(context).rescheduleAllReminders(plans)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
