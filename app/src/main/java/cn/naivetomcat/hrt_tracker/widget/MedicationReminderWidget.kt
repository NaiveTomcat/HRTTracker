package cn.naivetomcat.hrt_tracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import cn.naivetomcat.hrt_tracker.MainActivity
import cn.naivetomcat.hrt_tracker.R
import cn.naivetomcat.hrt_tracker.data.AppDatabase
import cn.naivetomcat.hrt_tracker.data.MedicationPlan
import cn.naivetomcat.hrt_tracker.data.displayName
import cn.naivetomcat.hrt_tracker.pk.DoseEvent
import cn.naivetomcat.hrt_tracker.widget.WidgetUtils.formatScheduledTime
import cn.naivetomcat.hrt_tracker.widget.WidgetUtils.routeDisplayName
import kotlinx.coroutines.flow.first

/**
 * 用药提醒微件
 *
 * 最小 2×1、可横向扩展的单行微件。
 * 显示最近一次计划用药的时间和内容，并判断是否已用药：
 * - 计划时间前后 1h 内有相同途径和酯类的用药记录 → 已用药
 * - 过期未用药 → 继续显示，直到距下一次用药时间更近才切换
 */
class MedicationReminderWidget : GlanceAppWidget() {

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

        // 获取过去 48h 内的实际用药记录，用于判断"已用药"状态
        val lookbackH = System.currentTimeMillis() / 3600000.0 - 48.0
        val recentEvents: List<DoseEvent> = db.doseEventDao()
            .getEventsByTimeRange(lookbackH, Double.MAX_VALUE)
            .map { it.toDoseEvent() }

        val relevantDose = WidgetUtils.findRelevantScheduledDose(plans, recentEvents)

        provideContent {
            MedicationReminderWidgetContent(plans = plans, info = relevantDose)
        }
    }
}

@Composable
private fun MedicationReminderWidgetContent(
    plans: List<MedicationPlan>,
    info: ScheduledDoseInfo?
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        when {
            plans.isEmpty() -> NoPlansRow()
            info == null -> NoScheduleRow()
            else -> ScheduledDoseRow(info)
        }
    }
}

@Composable
private fun NoPlansRow() {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_alarm),
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
private fun NoScheduleRow() {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_check_circle),
            contentDescription = null,
            modifier = GlanceModifier.size(20.dp),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary)
        )
        Spacer(modifier = GlanceModifier.width(8.dp))
        Text(
            text = "近期无计划用药",
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
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
private fun ScheduledDoseRow(info: ScheduledDoseInfo) {
    val plan = info.plan
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 状态图标
        val (statusIcon, statusTint) = when {
            info.isTaken -> Pair(R.drawable.ic_widget_check_circle, GlanceTheme.colors.primary)
            info.isOverdue -> Pair(R.drawable.ic_widget_alarm, GlanceTheme.colors.error)
            else -> Pair(R.drawable.ic_widget_alarm, GlanceTheme.colors.secondary)
        }
        Image(
            provider = ImageProvider(statusIcon),
            contentDescription = null,
            modifier = GlanceModifier.size(22.dp),
            colorFilter = ColorFilter.tint(statusTint)
        )
        Spacer(modifier = GlanceModifier.width(10.dp))

        // 主信息列
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .wrapContentHeight(),
        ) {
            val timeLabel = when {
                info.isTaken -> "已用药  ${formatScheduledTime(info.scheduledTime)}"
                info.isOverdue -> "过期未用药  ${formatScheduledTime(info.scheduledTime)}"
                else -> "下次用药  ${formatScheduledTime(info.scheduledTime)}"
            }
            Text(
                text = timeLabel,
                style = TextStyle(
                    color = if (info.isOverdue && !info.isTaken)
                        GlanceTheme.colors.error
                    else
                        GlanceTheme.colors.onSurface,
                    fontSize = 13.sp,
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
        // 右侧打开应用按钮
        Button(
            text = "打开",
            onClick = actionStartActivity<MainActivity>()
        )
    }
}
