package cn.naivetomcat.hrt_tracker.widget

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.Button
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.PreviewSizeMode
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
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
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
import cn.naivetomcat.hrt_tracker.pk.DoseEvent
import cn.naivetomcat.hrt_tracker.pk.Ester
import cn.naivetomcat.hrt_tracker.pk.Route
import cn.naivetomcat.hrt_tracker.widget.WidgetUtils.formatScheduledTime
import cn.naivetomcat.hrt_tracker.widget.WidgetUtils.routeDisplayName
import cn.naivetomcat.hrt_tracker.widget.WidgetUtils.drugDisplayName
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

// State preference keys (per widget instance via PreferencesGlanceStateDefinition)
internal val KEY_CONFIGURED_PLAN_ID = stringPreferencesKey("widget_configured_plan_id")
internal val KEY_CONFIRMING = booleanPreferencesKey("widget_confirming")
internal val KEY_ADDING = booleanPreferencesKey("widget_adding")

// Width threshold separating narrow (2-col) from wide (3+-col) layouts
private val WIDE_WIDTH_THRESHOLD = 170.dp
private const val TAG = "HRTTrackerWidget"

/**
 * HRT Tracker 组合微件（用药提醒 + 快速添加记录）
 *
 * 两行竖向布局，最小 2×1（仅可横向扩展）：
 * - 第一行（tertiary container 背景）：用药提醒
 * - 第二行（widget background）：快速添加当前方案的用药记录
 *
 * 宽 ≥ 3格（≥ 220dp）：每行显示图标 → 内容 → 独立按钮
 * 宽 = 2格（< 220dp）：去掉独立按钮，整行可点击；
 *   确认状态下整个微件仅显示「确认」「取消」两个按钮
 */
class HRTTrackerWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(130.dp, 58.dp),  // 2 cols × 1 row (narrow)
            DpSize(200.dp, 58.dp),  // 3+ cols × 1 row (wide)
        )
    )

    // Android 15+ generated preview: render at the wide size so all content is visible.
    @get:RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override val previewSizeMode: PreviewSizeMode =
        SizeMode.Responsive(setOf(DpSize(200.dp, 58.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Log.d(TAG, "provideGlance start id=$id")
        val db = AppDatabase.getDatabase(context)

        // Load all data outside provideContent (suspend functions only allowed here)
        val allPlans = db.medicationPlanDao().getEnabledPlans().first()
            .mapNotNull { entity ->
                runCatching { entity.toMedicationPlan() }
                    .onFailure { e -> Log.e(TAG, "plan parse failed, skip id=${entity.id}", e) }
                    .getOrNull()
            }
        Log.d(TAG, "provideGlance loaded plans count=${allPlans.size} id=$id")

        val lookbackH = System.currentTimeMillis() / 3600000.0 - 48.0
        val recentEvents: List<DoseEvent> = db.doseEventDao()
            .getEventsByTimeRange(lookbackH, Double.MAX_VALUE)
            .mapNotNull { entity ->
                runCatching { entity.toDoseEvent() }
                    .onFailure { e -> Log.e(TAG, "event parse failed, skip id=${entity.id}", e) }
                    .getOrNull()
            }
        Log.d(TAG, "provideGlance loaded recentEvents count=${recentEvents.size} id=$id")

        val state = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val configuredPlanIdInState = state[KEY_CONFIGURED_PLAN_ID]
        val resolvedConfiguredPlan = if (configuredPlanIdInState != null) {
            allPlans.find { it.id.toString() == configuredPlanIdInState } ?: allPlans.firstOrNull()
        } else {
            allPlans.firstOrNull()
        }

        // Persist fallback plan so the widget won't stay in an unconfigured state.
        if (resolvedConfiguredPlan != null && configuredPlanIdInState != resolvedConfiguredPlan.id.toString()) {
            updateAppWidgetState(context, id) { prefs ->
                prefs[KEY_CONFIGURED_PLAN_ID] = resolvedConfiguredPlan.id.toString()
            }
            Log.i(
                TAG,
                "provideGlance normalized configuredPlanId from=$configuredPlanIdInState to=${resolvedConfiguredPlan.id} id=$id"
            )
        }

        val reminderInfo = runCatching {
            resolvedConfiguredPlan?.let {
                WidgetUtils.findRelevantScheduledDose(listOf(it), recentEvents)
            }
        }.onFailure { e ->
            Log.e(TAG, "findRelevantScheduledDose failed id=$id", e)
        }.getOrNull()

        provideContent {
            // Read per-widget state inside composable (only accessible here via currentState)
            val prefs = currentState<Preferences>()
            val isConfirming = prefs[KEY_CONFIRMING] == true
            val isAdding = prefs[KEY_ADDING] == true

            Log.d(
                TAG,
                "provideGlance render configuredPlanId=${resolvedConfiguredPlan?.id} confirming=$isConfirming adding=$isAdding hasReminder=${reminderInfo != null} id=$id"
            )

            HRTTrackerWidgetContent(
                configuredPlan = resolvedConfiguredPlan,
                allPlansEmpty = allPlans.isEmpty(),
                reminderInfo = reminderInfo,
                isConfirming = isConfirming,
                isAdding = isAdding
            )
        }
    }

    /**
     * Android 15+ 生成式预览（widget picker 动态缩略图）。
     * 使用静态假数据，不访问数据库；一次性合成，无重组。
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val previewPlan = MedicationPlan(
            name = "戊酸雌二醇",
            route = Route.ORAL,
            ester = Ester.EV,
            doseMG = 2.0,
            scheduleType = MedicationPlan.ScheduleType.DAILY,
            timeOfDay = listOf(LocalTime.of(8, 0))
        )
        val previewInfo = ScheduledDoseInfo(
            plan = previewPlan,
            scheduledTime = LocalDate.now().atTime(8, 0),
            isTaken = false,
            isOverdue = false
        )
        provideContent {
            GlanceTheme {
                HRTTrackerWidgetContent(
                    configuredPlan = previewPlan,
                    allPlansEmpty = false,
                    reminderInfo = previewInfo,
                    isConfirming = false,
                    isAdding = false
                )
            }
        }
    }
}

@Composable
private fun HRTTrackerWidgetContent(
    configuredPlan: MedicationPlan?,
    allPlansEmpty: Boolean,
    reminderInfo: ScheduledDoseInfo?,
    isConfirming: Boolean,
    isAdding: Boolean
) {
    val isWide = LocalSize.current.width >= WIDE_WIDTH_THRESHOLD
    // Use a smart-castable local variable for confirming state
    val confirmingPlan = if (isConfirming || isAdding) configuredPlan else null

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
    ) {
        if (confirmingPlan != null && !isWide) {
            // Narrow + confirming: entire widget becomes two confirm/cancel buttons
            NarrowConfirmingContent(isAdding)
        } else {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                // ── Row 1: Medication Reminder (tertiary container) ────────────────
                val reminderModifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .background(GlanceTheme.colors.tertiaryContainer)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .let { mod ->
                        // Narrow mode: whole row opens app when tapped
                        if (!isWide) mod.clickable(actionStartActivity<MainActivity>()) else mod
                    }
                Box(
                    modifier = reminderModifier,
                    contentAlignment = Alignment.CenterStart
                ) {
                    ReminderRowContent(
                        plan = configuredPlan,
                        info = reminderInfo,
                        allPlansEmpty = allPlansEmpty,
                        isWide = isWide
                    )
                }

                Spacer(modifier = GlanceModifier.height(4.dp))

                // ── Row 2: Quick Add (widget background) ──────────────────────────
                val quickAddModifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .let { mod ->
                        // Narrow + not confirming: whole row starts the confirm flow
                        if (!isWide && configuredPlan != null && confirmingPlan == null)
                            mod.clickable(actionRunCallback<StartConfirmAction>())
                        else mod
                    }
                Box(
                    modifier = quickAddModifier,
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (confirmingPlan != null) {
                        // Wide + confirming: confirm/cancel inline in this row
                        WideConfirmingRow(confirmingPlan, isAdding)
                    } else {
                        QuickAddRowContent(
                            plan = configuredPlan,
                            allPlansEmpty = allPlansEmpty,
                            isWide = isWide
                        )
                    }
                }
            }
        }
    }
}

// ── Reminder row ──────────────────────────────────────────────────────────────

@Composable
private fun ReminderRowContent(
    plan: MedicationPlan?,
    info: ScheduledDoseInfo?,
    allPlansEmpty: Boolean,
    isWide: Boolean
) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            allPlansEmpty || plan == null -> {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_alarm),
                    contentDescription = null,
                    modifier = GlanceModifier.size(18.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onTertiaryContainer)
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                Text(
                    text = "无用药方案",
                    style = TextStyle(
                        color = GlanceTheme.colors.onTertiaryContainer,
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight()
                )
                if (isWide) {
                    Button(text = "打开", onClick = actionStartActivity<MainActivity>())
                }
            }

            info == null -> {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_check_circle),
                    contentDescription = null,
                    modifier = GlanceModifier.size(18.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onTertiaryContainer)
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                Text(
                    text = "近期无计划用药",
                    style = TextStyle(
                        color = GlanceTheme.colors.onTertiaryContainer,
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight()
                )
                if (isWide) {
                    Button(text = "打开", onClick = actionStartActivity<MainActivity>())
                }
            }

            else -> {
                val statusIcon = if (info.isTaken) R.drawable.ic_widget_check_circle
                else R.drawable.ic_widget_alarm
                val iconTint = if (info.isOverdue && !info.isTaken)
                    GlanceTheme.colors.error else GlanceTheme.colors.onTertiaryContainer
                Image(
                    provider = ImageProvider(statusIcon),
                    contentDescription = null,
                    modifier = GlanceModifier.size(18.dp),
                    colorFilter = ColorFilter.tint(iconTint)
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                Column(
                    modifier = GlanceModifier.defaultWeight().wrapContentHeight()
                ) {
                    val timeLabel = when {
                        info.isTaken -> "${formatScheduledTime(info.scheduledTime)}  已用药"
                        info.isOverdue -> "${formatScheduledTime(info.scheduledTime)}  漏服"
                        else -> "${formatScheduledTime(info.scheduledTime)}  下次用药"
                    }
                    Text(
                        text = timeLabel,
                        style = TextStyle(
                            color = if (info.isOverdue && !info.isTaken)
                                GlanceTheme.colors.error
                            else GlanceTheme.colors.onTertiaryContainer,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                    Text(
                        text = "${plan.doseMG}mg · ${plan.drugDisplayName()} · ${routeDisplayName(plan.route)}",
                        style = TextStyle(
                            color = GlanceTheme.colors.onTertiaryContainer,
                            fontSize = 10.sp
                        ),
                        maxLines = 1
                    )
                }
                if (isWide) {
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Button(text = "打开", onClick = actionStartActivity<MainActivity>())
                }
            }
        }
    }
}

// ── Quick Add row ─────────────────────────────────────────────────────────────

@Composable
private fun QuickAddRowContent(
    plan: MedicationPlan?,
    allPlansEmpty: Boolean,
    isWide: Boolean
) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (allPlansEmpty || plan == null) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_add),
                contentDescription = null,
                modifier = GlanceModifier.size(18.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant)
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = "无可用方案",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp
                ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight()
            )
            if (isWide) {
                Button(text = "打开", onClick = actionStartActivity<MainActivity>())
            }
        } else {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_medication),
                contentDescription = null,
                modifier = GlanceModifier.size(18.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary)
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Column(
                modifier = GlanceModifier.defaultWeight().wrapContentHeight()
            ) {
                Text(
                    text = plan.drugDisplayName(),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
                Text(
                    text = "${routeDisplayName(plan.route)} · ${plan.doseMG}mg",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 10.sp
                    ),
                    maxLines = 1
                )
            }
            if (isWide) {
                Spacer(modifier = GlanceModifier.width(4.dp))
                Button(
                    text = "添加",
                    onClick = actionRunCallback<StartConfirmAction>()
                )
            }
        }
    }
}

// ── Confirming states ─────────────────────────────────────────────────────────

/** 宽模式：确认行替换 Quick Add 区内容，提醒行保持不变 */
@Composable
private fun WideConfirmingRow(plan: MedicationPlan, isAdding: Boolean) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = GlanceModifier.defaultWeight().wrapContentHeight()
        ) {
            Text(
                text = if (isAdding) "添加中..." else "确认添加用药记录？",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
            Text(
                text = "${plan.drugDisplayName()} · ${routeDisplayName(plan.route)} · ${plan.doseMG}mg",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 10.sp
                ),
                maxLines = 1
            )
        }
        Spacer(modifier = GlanceModifier.width(4.dp))
        CircleIconButton(
            imageProvider = ImageProvider(R.drawable.ic_widget_close),
            contentDescription = "取消",
            onClick = actionRunCallback<CancelConfirmAction>(),
            backgroundColor = GlanceTheme.colors.errorContainer,
            contentColor = GlanceTheme.colors.onErrorContainer,
            modifier = GlanceModifier.size(32.dp),
            enabled = !isAdding
        )
        Spacer(modifier = GlanceModifier.width(4.dp))
        CircleIconButton(
            imageProvider = ImageProvider(R.drawable.ic_widget_check),
            contentDescription = "确认添加",
            onClick = actionRunCallback<ConfirmDoseAction>(),
            backgroundColor = GlanceTheme.colors.primaryContainer,
            contentColor = GlanceTheme.colors.onPrimaryContainer,
            modifier = GlanceModifier.size(32.dp),
            enabled = !isAdding
        )
    }
}

/** 窄模式：确认状态下整个微件仅显示两个按钮，其余内容全部隐藏 */
@Composable
private fun NarrowConfirmingContent(isAdding: Boolean) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        Button(
            text = if (isAdding) "添加中..." else "✓ 确认添加",
            onClick = actionRunCallback<ConfirmDoseAction>(),
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            enabled = !isAdding
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Button(
            text = "✕ 取消",
            onClick = actionRunCallback<CancelConfirmAction>(),
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            enabled = !isAdding
        )
    }
}

// ── Action Callbacks ──────────────────────────────────────────────────────────

/** 进入确认状态 */
class StartConfirmAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        Log.d(TAG, "StartConfirmAction id=$glanceId")
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[KEY_CONFIRMING] = true
        }
        HRTTrackerWidget().update(context, glanceId)
    }
}

/** 确认添加记录，然后立即退回主界面（防止用户误以为未响应而多次点击） */
class ConfirmDoseAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        Log.d(TAG, "ConfirmDoseAction start id=$glanceId")
        // Read the configured plan ID once before any state mutation.
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        val configuredPlanId = prefs[KEY_CONFIGURED_PLAN_ID]
        Log.d(TAG, "ConfirmDoseAction configuredPlanId=$configuredPlanId id=$glanceId")

        // Show "添加中..." immediately to give the user instant feedback and prevent
        // duplicate taps while the DB write is in progress.
        updateAppWidgetState(context, glanceId) { it[KEY_ADDING] = true }
        HRTTrackerWidget().update(context, glanceId)

        val db = AppDatabase.getDatabase(context)
        val allPlans = db.medicationPlanDao().getEnabledPlans().first()
            .map { it.toMedicationPlan() }
        val plan = if (configuredPlanId != null) {
            allPlans.find { it.id.toString() == configuredPlanId } ?: allPlans.firstOrNull()
        } else {
            allPlans.firstOrNull()
        }
        Log.d(TAG, "ConfirmDoseAction resolvedPlan=${plan?.id} allPlans=${allPlans.size} id=$glanceId")
        if (plan != null) {
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
            Log.i(TAG, "ConfirmDoseAction upsert success planId=${plan.id} id=$glanceId")
        } else {
            Log.w(TAG, "ConfirmDoseAction skipped upsert: no available plan id=$glanceId")
        }

        // Return to main widget view and clear loading state.
        updateAppWidgetState(context, glanceId) { it[KEY_CONFIRMING] = false; it[KEY_ADDING] = false }
        HRTTrackerWidget().update(context, glanceId)
        Log.d(TAG, "ConfirmDoseAction finish id=$glanceId")
    }
}

/** 取消确认，回到主界面 */
class CancelConfirmAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        Log.d(TAG, "CancelConfirmAction id=$glanceId")
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[KEY_CONFIRMING] = false
        }
        HRTTrackerWidget().update(context, glanceId)
    }
}
