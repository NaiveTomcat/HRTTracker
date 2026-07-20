package cn.naivetomcat.hrt_tracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import cn.naivetomcat.hrt_tracker.MainActivity
import cn.naivetomcat.hrt_tracker.R
import cn.naivetomcat.hrt_tracker.data.AppDatabase
import cn.naivetomcat.hrt_tracker.data.DoseEventEntity
import cn.naivetomcat.hrt_tracker.data.DoseEventRepository
import cn.naivetomcat.hrt_tracker.data.MedicationPlan
import cn.naivetomcat.hrt_tracker.data.SettingsDataStore
import cn.naivetomcat.hrt_tracker.pk.DoseEvent
import cn.naivetomcat.hrt_tracker.pk.Route
import cn.naivetomcat.hrt_tracker.pk.SimulationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Traditional RemoteViews widget used for broad launcher compatibility,
 * including Honor foldable launchers.
 */
class HRTTrackerWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        updateAsync(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        super.onAppWidgetOptionsChanged(
            context,
            appWidgetManager,
            appWidgetId,
            newOptions
        )
        updateAsync(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_RECORD_PLAN) return

        val pendingResult = goAsync()
        WIDGET_SCOPE.launch {
            try {
                recordPlanDose(context.applicationContext, intent)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun updateAsync(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        WIDGET_SCOPE.launch {
            try {
                appWidgetIds.forEach { appWidgetId ->
                    updateWidget(
                        context.applicationContext,
                        appWidgetManager,
                        appWidgetId
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val ACTION_RECORD_PLAN =
            "cn.naivetomcat.hrt_tracker.widget.RECORD_PLAN"
        const val EXTRA_PLAN_ID = "plan_id"
        const val EXTRA_WIDGET_ID = "widget_id"
        val WIDGET_SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

private suspend fun updateWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    val plans = runCatching {
        AppDatabase.getDatabase(context)
            .medicationPlanDao()
            .getEnabledPlans()
            .first()
            .mapNotNull { entity ->
                runCatching { entity.toMedicationPlan() }.getOrNull()
            }
            .take(2)
    }.getOrDefault(emptyList())
    val concentration = runCatching {
        calculateWidgetConcentration(context)
    }.getOrNull()
    val minHeight = appWidgetManager
        .getAppWidgetOptions(appWidgetId)
        .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 100)
    val isTall = minHeight >= 180

    val views = RemoteViews(context.packageName, R.layout.widget_hrt_tracker)
    views.setTextViewText(
        R.id.widget_concentration,
        concentration?.let { "%.1f".format(it) } ?: "--"
    )
    views.setViewVisibility(
        R.id.widget_target_range,
        if (isTall) View.VISIBLE else View.GONE
    )
    views.setViewVisibility(
        R.id.widget_record_title,
        if (isTall) View.VISIBLE else View.GONE
    )
    views.setOnClickPendingIntent(
        R.id.widget_concentration_panel,
        openAppPendingIntent(context)
    )

    bindPlanButton(
        context = context,
        views = views,
        buttonId = R.id.widget_plan_one,
        plan = plans.getOrNull(0),
        appWidgetId = appWidgetId,
        emptyText = "打开 App 添加方案"
    )
    bindPlanButton(
        context = context,
        views = views,
        buttonId = R.id.widget_plan_two,
        plan = plans.getOrNull(1),
        appWidgetId = appWidgetId,
        emptyText = if (plans.isEmpty()) "添加用药方案" else "添加第二个方案"
    )
    appWidgetManager.updateAppWidget(appWidgetId, views)
}

private fun bindPlanButton(
    context: Context,
    views: RemoteViews,
    buttonId: Int,
    plan: MedicationPlan?,
    appWidgetId: Int,
    emptyText: String
) {
    if (plan == null) {
        views.setTextViewText(buttonId, emptyText)
        views.setOnClickPendingIntent(buttonId, openAppPendingIntent(context))
        return
    }

    views.setTextViewText(
        buttonId,
        "${plan.name}\n${formatDose(plan.doseMG)} mg"
    )
    val intent = Intent(context, HRTTrackerWidgetReceiver::class.java).apply {
        action = "cn.naivetomcat.hrt_tracker.widget.RECORD_PLAN"
        putExtra("plan_id", plan.id.toString())
        putExtra("widget_id", appWidgetId)
        data = Uri.parse(
            "hrttracker://widget/$appWidgetId/plan/${plan.id}"
        )
        addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
    }
    views.setOnClickPendingIntent(
        buttonId,
        PendingIntent.getBroadcast(
            context,
            appWidgetId xor plan.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    )
}

private fun openAppPendingIntent(context: Context): PendingIntent =
    PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

private suspend fun recordPlanDose(context: Context, intent: Intent) {
    val planId = intent.getStringExtra("plan_id")
        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        ?: return
    val database = AppDatabase.getDatabase(context)
    val plan = database.medicationPlanDao()
        .getPlanById(planId)
        ?.toMedicationPlan()
        ?.takeIf { it.isEnabled }
        ?: return
    val nowMillis = System.currentTimeMillis()
    val recordId = UUID.nameUUIDFromBytes(
        "widget:${plan.id}:${nowMillis / 60_000}"
            .toByteArray(StandardCharsets.UTF_8)
    )
    database.doseEventDao().upsertEvent(
        DoseEventEntity.fromDoseEvent(
            DoseEvent(
                id = recordId,
                route = plan.route,
                timeH = nowMillis / 3_600_000.0,
                doseMG = plan.doseMG,
                ester = plan.ester,
                extras = plan.extras
            )
        )
    )
    updateAllHRTTrackerWidgets(context)
    CoroutineScope(Dispatchers.Main).launch {
        Toast.makeText(context, "已记录：${plan.name}", Toast.LENGTH_SHORT)
            .show()
    }
}

internal suspend fun calculateWidgetConcentration(context: Context): Double? {
    val database = AppDatabase.getDatabase(context)
    val nowH = System.currentTimeMillis() / 3_600_000.0
    val events = DoseEventRepository(database.doseEventDao())
        .getEventsForSimulation(nowH)
        .filter { it.route != Route.ANTIANDROGEN && it.timeH <= nowH }
    if (events.isEmpty()) return null

    val bodyWeight = SettingsDataStore(context).userSettings.first().bodyWeight
    return SimulationEngine(
        events = events,
        bodyWeightKG = bodyWeight,
        startTimeH = nowH - 0.01,
        endTimeH = nowH,
        numberOfSteps = 2
    ).run().concPGmL.lastOrNull()
}

internal suspend fun updateAllHRTTrackerWidgets(context: Context) {
    val manager = AppWidgetManager.getInstance(context)
    val component = ComponentName(context, HRTTrackerWidgetReceiver::class.java)
    val ids = manager.getAppWidgetIds(component)
    ids.forEach { updateWidget(context, manager, it) }
}

private fun formatDose(doseMG: Double): String =
    if (doseMG % 1.0 == 0.0) {
        doseMG.toInt().toString()
    } else {
        "%.2f".format(doseMG)
    }
