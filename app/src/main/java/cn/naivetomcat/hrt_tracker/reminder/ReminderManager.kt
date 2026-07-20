package cn.naivetomcat.hrt_tracker.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import cn.naivetomcat.hrt_tracker.data.MedicationPlan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.*

/**
 * 用药提醒管理器
 * 负责管理用药方案的提醒
 */
class ReminderManager(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private companion object {
        const val MAX_PLAN_TIME_POINTS = 10
        const val SCHEDULED_OCCURRENCES_PER_TIME = 30
    }

    /**
     * 为用药方案设置提醒
     */
    fun scheduleReminder(plan: MedicationPlan) {
        if (!plan.isEnabled) {
            return
        }

        // Editing a plan can remove or move times. Clear old PendingIntents first
        // so stale alarms from the previous definition cannot fire.
        cancelReminder(plan.id)

        // 为每个时间点设置提醒
        plan.timeOfDay.forEachIndexed { index, time ->
            val nextReminderTimes = calculateNextReminderTimes(plan, time)
            
            nextReminderTimes.take(SCHEDULED_OCCURRENCES_PER_TIME)
                .forEachIndexed { dayIndex, dateTime ->
                scheduleAlarm(plan, dateTime, index * 1000 + dayIndex)
            }
        }
    }

    /**
     * 取消用药方案的所有提醒
     */
    fun cancelReminder(planId: UUID) {
        for (timeIndex in 0 until MAX_PLAN_TIME_POINTS) {
            for (dayIndex in 0 until SCHEDULED_OCCURRENCES_PER_TIME) {
                val requestCode = planId.hashCode() + timeIndex * 1000 + dayIndex
                val intent = Intent(context, MedicationReminderReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                pendingIntent?.let {
                    alarmManager.cancel(it)
                    it.cancel()
                }
            }
        }
    }

    /**
     * 重新设置所有启用方案的提醒
     */
    suspend fun rescheduleAllReminders(plans: List<MedicationPlan>) {
        // 取消所有现有提醒
        plans.forEach { cancelReminder(it.id) }
        
        // 重新设置启用的方案
        plans.filter { it.isEnabled }.forEach { scheduleReminder(it) }
    }

    /**
     * 设置单次提醒
     */
    private fun scheduleAlarm(plan: MedicationPlan, dateTime: LocalDateTime, timeIndex: Int) {
        val scheduledAtMillis =
            dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Wait until the end of the ±1 hour check-in window before deciding
        // whether a reminder is still needed.
        val triggerTime = reminderEvaluationTimeMillis(scheduledAtMillis)
        
        // 如果时间已经过去，不设置提醒
        if (triggerTime < System.currentTimeMillis()) {
            return
        }

        val intent = Intent(context, MedicationReminderReceiver::class.java).apply {
            putExtra(MedicationReminderReceiver.EXTRA_PLAN_ID, plan.id.toString())
            putExtra(MedicationReminderReceiver.EXTRA_PLAN_NAME, plan.name)
            putExtra(MedicationReminderReceiver.EXTRA_PLAN_DESCRIPTION, plan.getDescription())
            putExtra(MedicationReminderReceiver.EXTRA_NOTIFICATION_ID, plan.id.hashCode() + timeIndex)
            putExtra(MedicationReminderReceiver.EXTRA_SCHEDULED_AT_MILLIS, scheduledAtMillis)
        }

        val requestCode = plan.id.hashCode() + timeIndex
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 设置精确提醒
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12及以上，检查是否有精确闹钟权限
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                // 如果没有权限，使用非精确提醒
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }

    /**
     * 计算下次提醒时间列表
     */
    private fun calculateNextReminderTimes(plan: MedicationPlan, time: LocalTime): List<LocalDateTime> {
        val now = LocalDateTime.now()
        val today = LocalDate.now()
        val reminderTimes = mutableListOf<LocalDateTime>()

        when (plan.scheduleType) {
            MedicationPlan.ScheduleType.DAILY -> {
                // 每天提醒
                for (i in 0 until 30) { // 计算接下来30天
                    val reminderDate = today.plusDays(i.toLong())
                    val reminderTime = LocalDateTime.of(reminderDate, time)
                    
                    if (reminderTime.plusHours(1).isAfter(now)) {
                        reminderTimes.add(reminderTime)
                    }
                }
            }
            
            MedicationPlan.ScheduleType.WEEKLY -> {
                // 每周特定几天提醒
                for (i in 0 until 60) { // 计算接下来60天
                    val reminderDate = today.plusDays(i.toLong())
                    val dayOfWeek = reminderDate.dayOfWeek
                    
                    if (plan.daysOfWeek.contains(dayOfWeek)) {
                        val reminderTime = LocalDateTime.of(reminderDate, time)
                        
                        if (reminderTime.plusHours(1).isAfter(now)) {
                            reminderTimes.add(reminderTime)
                        }
                    }
                }
            }
            
            MedicationPlan.ScheduleType.CUSTOM -> {
                // 自定义间隔天数
                var dayOffset = 0
                while (reminderTimes.size < 30) { // 最多计算30次
                    val reminderDate = today.plusDays(dayOffset.toLong())
                    val reminderTime = LocalDateTime.of(reminderDate, time)
                    
                    if (reminderTime.plusHours(1).isAfter(now)) {
                        reminderTimes.add(reminderTime)
                    }
                    
                    dayOffset += plan.intervalDays
                }
            }
        }

        return reminderTimes.sortedBy { it }
    }
}
