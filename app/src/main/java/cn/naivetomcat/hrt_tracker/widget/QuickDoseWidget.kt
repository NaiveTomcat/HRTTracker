package cn.naivetomcat.hrt_tracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.Button
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentHeight
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import cn.naivetomcat.hrt_tracker.MainActivity
import cn.naivetomcat.hrt_tracker.R
import cn.naivetomcat.hrt_tracker.data.AppDatabase
import cn.naivetomcat.hrt_tracker.data.DoseEventEntity
import cn.naivetomcat.hrt_tracker.data.MedicationPlan
import cn.naivetomcat.hrt_tracker.data.displayName
import cn.naivetomcat.hrt_tracker.pk.DoseEvent
import cn.naivetomcat.hrt_tracker.widget.WidgetUtils.routeDisplayName
import kotlinx.coroutines.flow.first
import java.util.UUID

private val KEY_SELECTED_PLAN_ID = stringPreferencesKey("quick_dose_selected_plan_id")

/**
 * 快速用药记录微件
 *
 * 最小 2×1、可横向扩展的单行微件。
 * 空闲时显示首个启用方案的信息和「添加」按钮；
 * 点击后进入确认状态，可确认或取消；确认后立即将记录写入数据库。
 */
class QuickDoseWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(130.dp, 57.dp),
            DpSize(270.dp, 57.dp),
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = AppDatabase.getDatabase(context)
        val plans = db.medicationPlanDao().getEnabledPlans().first()
            .map { it.toMedicationPlan() }

        provideContent {
            val prefs = currentState<Preferences>()
            val selectedPlanId = prefs[KEY_SELECTED_PLAN_ID]
            QuickDoseWidgetContent(plans = plans, selectedPlanId = selectedPlanId)
        }
    }
}

@Composable
private fun QuickDoseWidgetContent(plans: List<MedicationPlan>, selectedPlanId: String?) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        when {
            plans.isEmpty() -> EmptyPlansRow()
            selectedPlanId != null -> {
                val plan = plans.find { it.id.toString() == selectedPlanId }
                if (plan != null) ConfirmingRow(plan) else IdleRow(plans)
            }
            else -> IdleRow(plans)
        }
    }
}

@Composable
private fun EmptyPlansRow() {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_add),
            contentDescription = null,
            modifier = GlanceModifier.size(20.dp),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant)
        )
        Spacer(modifier = GlanceModifier.width(8.dp))
        Text(
            text = "请先在应用内添加用药方案",
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 13.sp
            ),
            maxLines = 1
        )
        Spacer(modifier = GlanceModifier.defaultWeight())
        Button(
            text = "打开",
            onClick = actionStartActivity<MainActivity>()
        )
    }
}

@Composable
private fun IdleRow(plans: List<MedicationPlan>) {
    val plan = plans.first()
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_medication),
            contentDescription = null,
            modifier = GlanceModifier.size(22.dp),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary)
        )
        Spacer(modifier = GlanceModifier.width(10.dp))
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .wrapContentHeight(),
        ) {
            Text(
                text = plan.name,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
            Text(
                text = "${plan.doseMG}mg · ${plan.ester.displayName} · ${routeDisplayName(plan.route)}",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp
                ),
                maxLines = 1
            )
        }
        Spacer(modifier = GlanceModifier.width(8.dp))
        Button(
            text = "添加",
            onClick = actionRunCallback<SelectPlanAction>(
                actionParametersOf(SelectPlanAction.KEY_PLAN_ID to plan.id.toString())
            )
        )
    }
}

@Composable
private fun ConfirmingRow(plan: MedicationPlan) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .wrapContentHeight(),
        ) {
            Text(
                text = "确认添加用药记录？",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
            Text(
                text = "${plan.name} · ${plan.doseMG}mg · ${routeDisplayName(plan.route)}",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp
                ),
                maxLines = 1
            )
        }
        Spacer(modifier = GlanceModifier.width(4.dp))
        CircleIconButton(
            imageProvider = ImageProvider(R.drawable.ic_widget_close),
            contentDescription = "取消",
            onClick = actionRunCallback<CancelQuickDoseAction>(),
            backgroundColor = GlanceTheme.colors.errorContainer,
            contentColor = GlanceTheme.colors.onErrorContainer,
            modifier = GlanceModifier.size(36.dp)
        )
        Spacer(modifier = GlanceModifier.width(4.dp))
        CircleIconButton(
            imageProvider = ImageProvider(R.drawable.ic_widget_check),
            contentDescription = "确认添加",
            onClick = actionRunCallback<ConfirmQuickDoseAction>(),
            backgroundColor = GlanceTheme.colors.primaryContainer,
            contentColor = GlanceTheme.colors.onPrimaryContainer,
            modifier = GlanceModifier.size(36.dp)
        )
    }
}

// ─────────────── Action Callbacks ───────────────

/** 选中方案，进入确认状态 */
class SelectPlanAction : ActionCallback {
    companion object {
        val KEY_PLAN_ID = ActionParameters.Key<String>("plan_id")
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val planId = parameters[KEY_PLAN_ID] ?: return
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[KEY_SELECTED_PLAN_ID] = planId
        }
        QuickDoseWidget().update(context, glanceId)
    }
}

/** 确认并将用药记录写入数据库 */
class ConfirmQuickDoseAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        val planId = prefs[KEY_SELECTED_PLAN_ID] ?: run {
            QuickDoseWidget().update(context, glanceId)
            return
        }
        val db = AppDatabase.getDatabase(context)
        val planEntity = db.medicationPlanDao().getPlanById(UUID.fromString(planId))
        if (planEntity != null) {
            val plan = planEntity.toMedicationPlan()
            db.doseEventDao().upsertEvent(
                DoseEventEntity.fromDoseEvent(
                    DoseEvent(
                        route = plan.route,
                        timeH = System.currentTimeMillis() / 3600000.0,
                        doseMG = plan.doseMG,
                        ester = plan.ester,
                        extras = plan.extras
                    )
                )
            )
        }
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs.remove(KEY_SELECTED_PLAN_ID)
        }
        QuickDoseWidget().update(context, glanceId)
        // 同步刷新提醒微件
        val manager = GlanceAppWidgetManager(context)
        manager.getGlanceIds(MedicationReminderWidget::class.java).forEach { id ->
            MedicationReminderWidget().update(context, id)
        }
    }
}

/** 取消确认，回到空闲状态 */
class CancelQuickDoseAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs.remove(KEY_SELECTED_PLAN_ID)
        }
        QuickDoseWidget().update(context, glanceId)
    }
}
