package cn.naivetomcat.hrt_tracker.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import cn.naivetomcat.hrt_tracker.MainActivity
import cn.naivetomcat.hrt_tracker.R

/**
 * 通知管理器
 * 负责创建和发送用药提醒通知
 */
class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "medication_reminder_channel"
        const val CHANNEL_NAME = "用药提醒"
        const val CHANNEL_DESCRIPTION = "用于提醒用户按时服药"
    }

    init {
        createNotificationChannel()
    }

    /**
     * 创建通知渠道（Android 8.0及以上需要）
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
                // 启用震动
                enableVibration(true)
                // 设置通知在锁屏上显示
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 发送用药提醒通知
     * @param planId 用药方案ID
     * @param planName 用药方案名称
     * @param description 用药方案描述
     * @param notificationId 通知ID
     * @param scheduledAtMillis 本次计划用药时间
     */
    fun sendMedicationReminder(
        planId: String,
        planName: String,
        description: String,
        notificationId: Int,
        scheduledAtMillis: Long
    ) {
        // 检查通知权限（Android 13及以上）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        // 创建点击通知后打开应用的Intent
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val confirmPendingIntent = actionPendingIntent(
            action = MedicationNotificationActionReceiver.ACTION_CONFIRM_DOSE,
            planId = planId,
            notificationId = notificationId,
            scheduledAtMillis = scheduledAtMillis
        )
        val skipPendingIntent = actionPendingIntent(
            action = MedicationNotificationActionReceiver.ACTION_SKIP_DOSE,
            planId = planId,
            notificationId = notificationId,
            scheduledAtMillis = scheduledAtMillis
        )

        // 构建通知
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_pill)
            .setContentTitle(context.getString(R.string.notification_medication_title, planName))
            .setContentText(description)
            .setStyle(NotificationCompat.BigTextStyle().bigText(description))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setWhen(scheduledAtMillis)
            .setShowWhen(true)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setRequestPromotedOngoing(true)
            .setAllowSystemGeneratedContextualActions(false)
            .addAction(
                R.drawable.ic_notification_pill,
                context.getString(R.string.notification_confirm_dose),
                confirmPendingIntent
            )
            .addAction(
                R.drawable.ic_notification_pill,
                context.getString(R.string.notification_skip_dose),
                skipPendingIntent
            )

        // 发送通知
        with(NotificationManagerCompat.from(context)) {
            notify(notificationId, builder.build())
        }
    }

    /**
     * 取消通知
     */
    fun cancelNotification(notificationId: Int) {
        with(NotificationManagerCompat.from(context)) {
            cancel(notificationId)
        }
    }

    private fun actionPendingIntent(
        action: String,
        planId: String,
        notificationId: Int,
        scheduledAtMillis: Long
    ): PendingIntent {
        val intent = Intent(context, MedicationNotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(MedicationNotificationActionReceiver.EXTRA_PLAN_ID, planId)
            putExtra(MedicationNotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(
                MedicationNotificationActionReceiver.EXTRA_SCHEDULED_AT_MILLIS,
                scheduledAtMillis
            )
        }
        return PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
