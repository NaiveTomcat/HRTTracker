package cn.naivetomcat.hrt_tracker.widget

import cn.naivetomcat.hrt_tracker.data.MedicationPlan
import cn.naivetomcat.hrt_tracker.pk.DoseEvent
import cn.naivetomcat.hrt_tracker.pk.Route
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs

/**
 * 已计划用药事件的状态信息
 */
data class ScheduledDoseInfo(
    val plan: MedicationPlan,
    val scheduledTime: LocalDateTime,
    val isTaken: Boolean,
    val isOverdue: Boolean
)

/**
 * Widget 共用工具函数
 */
object WidgetUtils {

    /** 计划用药的"已用药"判断时间窗口（±1小时） */
    const val TAKEN_WINDOW_HOURS = 1.0

    /**
     * 非 Composable 版本的给药途径显示名称
     */
    fun routeDisplayName(route: Route): String = when (route) {
        Route.INJECTION -> "注射"
        Route.ORAL -> "口服"
        Route.SUBLINGUAL -> "舌下"
        Route.GEL -> "凝胶"
        Route.PATCH_APPLY -> "贴片"
        Route.PATCH_REMOVE -> "移除贴"
    }

    /**
     * 将 LocalDateTime 转换为自 epoch 起的小时数
     */
    fun localDateTimeToHours(dateTime: LocalDateTime): Double =
        dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() / 3600000.0

    /**
     * 为提醒微件查找最相关的计划用药信息。
     *
     * 逻辑：
     * - 若计划用药时间前后 ±[TAKEN_WINDOW_HOURS] 小时内存在相同途径和酯类的实际记录，视为已用药
     * - 已过期未用药时：继续显示该次用药，直到距下一次计划用药时间更近才切换
     * - 优先展示最近的待用药事件
     */
    fun findRelevantScheduledDose(
        enabledPlans: List<MedicationPlan>,
        recentActualEvents: List<DoseEvent>
    ): ScheduledDoseInfo? {
        val now = LocalDateTime.now()
        val nowH = System.currentTimeMillis() / 3600000.0

        val relevantPerPlan = enabledPlans.mapNotNull { plan ->
            findRelevantForPlan(plan, now, nowH, recentActualEvents)
        }

        if (relevantPerPlan.isEmpty()) return null

        val overdueUntaken = relevantPerPlan.filter { it.isOverdue && !it.isTaken }
        val upcomingUntaken = relevantPerPlan.filter { !it.isOverdue && !it.isTaken }

        return when {
            overdueUntaken.isEmpty() && upcomingUntaken.isEmpty() -> {
                // 全部已用药，展示最近的下一次计划
                relevantPerPlan
                    .filter { !it.isOverdue }
                    .minByOrNull { localDateTimeToHours(it.scheduledTime) - nowH }
                    ?: relevantPerPlan.maxByOrNull { localDateTimeToHours(it.scheduledTime) }
            }
            overdueUntaken.isEmpty() -> {
                upcomingUntaken.minByOrNull { localDateTimeToHours(it.scheduledTime) - nowH }
            }
            upcomingUntaken.isEmpty() -> {
                overdueUntaken.maxByOrNull { localDateTimeToHours(it.scheduledTime) }
            }
            else -> {
                val closestOverdue =
                    overdueUntaken.maxByOrNull { localDateTimeToHours(it.scheduledTime) }!!
                val closestUpcoming =
                    upcomingUntaken.minByOrNull { localDateTimeToHours(it.scheduledTime) }!!
                val timeSince = nowH - localDateTimeToHours(closestOverdue.scheduledTime)
                val timeToNext = localDateTimeToHours(closestUpcoming.scheduledTime) - nowH
                // 过期事件仍更近时继续显示它
                if (timeSince <= timeToNext) closestOverdue else closestUpcoming
            }
        }
    }

    private fun findRelevantForPlan(
        plan: MedicationPlan,
        now: LocalDateTime,
        nowH: Double,
        recentActualEvents: List<DoseEvent>
    ): ScheduledDoseInfo? {
        val scheduledTimes = generateScheduledTimes(plan, now.minusHours(48), now.plusDays(7))
        if (scheduledTimes.isEmpty()) return null

        val lastPast = scheduledTimes.filter { it.isBefore(now) }.maxOrNull()
        val nextFuture = scheduledTimes.filter { !it.isBefore(now) }.minOrNull()

        /** Standard ±[TAKEN_WINDOW_HOURS] check for a given scheduled time. */
        fun isTakenAt(time: LocalDateTime): Boolean {
            val h = localDateTimeToHours(time)
            return recentActualEvents.any { actual ->
                actual.route == plan.route &&
                    actual.ester == plan.ester &&
                    abs(actual.timeH - h) <= TAKEN_WINDOW_HOURS
            }
        }

        /**
         * Extended check for whether the past scheduled dose has been fulfilled.
         * A catch-up dose taken at any point during the overdue display window
         * (from [fromH] − [TAKEN_WINDOW_HOURS] up to, but not overlapping, the next dose's own
         * window) is counted as satisfying the missed scheduled dose.
         *
         * The [TAKEN_WINDOW_HOURS] subtraction on [fromH] mirrors the ±window logic of
         * [isTakenAt], so a dose recorded slightly before the scheduled time is still counted.
         */
        fun isTakenBetween(fromH: Double, toExclusiveH: Double): Boolean {
            return recentActualEvents.any { actual ->
                actual.route == plan.route &&
                    actual.ester == plan.ester &&
                    actual.timeH >= fromH - TAKEN_WINDOW_HOURS &&
                    actual.timeH < toExclusiveH
            }
        }

        if (lastPast == null) {
            return nextFuture?.let { ScheduledDoseInfo(plan, it, isTakenAt(it), false) }
        }

        val lastPastH = localDateTimeToHours(lastPast)
        val nextFutureH = nextFuture?.let { localDateTimeToHours(it) }

        // Consider the past dose taken if any matching dose was recorded from the scheduled
        // time all the way up to (but not within the window of) the next scheduled dose.
        // This allows catch-up doses to clear the "漏服" state immediately.
        val lastPastTaken = isTakenBetween(
            fromH = lastPastH,
            toExclusiveH = nextFutureH?.minus(TAKEN_WINDOW_HOURS) ?: (nowH + TAKEN_WINDOW_HOURS)
        )

        return if (!lastPastTaken && nextFuture != null) {
            val timeSince = nowH - lastPastH
            val timeToNext = nextFutureH!! - nowH
            if (timeSince <= timeToNext) {
                ScheduledDoseInfo(plan, lastPast, false, isOverdue = true)
            } else {
                ScheduledDoseInfo(plan, nextFuture, isTakenAt(nextFuture), false)
            }
        } else if (!lastPastTaken) {
            ScheduledDoseInfo(plan, lastPast, false, isOverdue = true)
        } else {
            nextFuture?.let { ScheduledDoseInfo(plan, it, isTakenAt(it), false) }
        }
    }

    /**
     * 在给定时间窗口内生成某方案的全部计划时间点
     */
    private fun generateScheduledTimes(
        plan: MedicationPlan,
        fromDateTime: LocalDateTime,
        toDateTime: LocalDateTime
    ): List<LocalDateTime> {
        val result = mutableListOf<LocalDateTime>()
        val today = LocalDate.now()
        val fromDate = fromDateTime.toLocalDate()
        val toDate = toDateTime.toLocalDate()

        fun addIfInRange(dt: LocalDateTime) {
            if (!dt.isBefore(fromDateTime) && !dt.isAfter(toDateTime)) result.add(dt)
        }

        when (plan.scheduleType) {
            MedicationPlan.ScheduleType.DAILY -> {
                var date = fromDate
                while (!date.isAfter(toDate)) {
                    plan.timeOfDay.forEach { addIfInRange(LocalDateTime.of(date, it)) }
                    date = date.plusDays(1)
                }
            }
            MedicationPlan.ScheduleType.WEEKLY -> {
                var date = fromDate
                while (!date.isAfter(toDate)) {
                    if (plan.daysOfWeek.contains(date.dayOfWeek)) {
                        plan.timeOfDay.forEach { addIfInRange(LocalDateTime.of(date, it)) }
                    }
                    date = date.plusDays(1)
                }
            }
            MedicationPlan.ScheduleType.CUSTOM -> {
                // 从今天向前和向后按间隔枚举
                var offset = 0L
                var date = today
                while (!date.isAfter(toDate)) {
                    plan.timeOfDay.forEach { addIfInRange(LocalDateTime.of(date, it)) }
                    offset += plan.intervalDays
                    date = today.plusDays(offset)
                }
                offset = plan.intervalDays.toLong()
                date = today.minusDays(offset)
                while (!date.isBefore(fromDate)) {
                    plan.timeOfDay.forEach { addIfInRange(LocalDateTime.of(date, it)) }
                    offset += plan.intervalDays
                    date = today.minusDays(offset)
                }
            }
        }

        return result.sorted()
    }

    /**
     * 将计划时间格式化为友好字符串：今天/明天/昨天 HH:mm，其他显示 M/d HH:mm
     */
    fun formatScheduledTime(dateTime: LocalDateTime): String {
        val today = LocalDate.now()
        val date = dateTime.toLocalDate()
        val timeStr = "%02d:%02d".format(dateTime.hour, dateTime.minute)
        return when {
            date == today -> "今天 $timeStr"
            date == today.plusDays(1) -> "明天 $timeStr"
            date == today.minusDays(1) -> "昨天 $timeStr"
            else -> "${date.monthValue}/${date.dayOfMonth} $timeStr"
        }
    }
}
