package cn.naivetomcat.hrt_tracker.data

import cn.naivetomcat.hrt_tracker.pk.AntiAndrogen
import cn.naivetomcat.hrt_tracker.pk.DoseEvent
import cn.naivetomcat.hrt_tracker.pk.Ester
import cn.naivetomcat.hrt_tracker.pk.Route
import cn.naivetomcat.hrt_tracker.pk.displayName as antiAndrogenDisplayName
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID

/**
 * 用药方案
 * @param id 唯一标识符
 * @param name 方案名称
 * @param route 给药途径
 * @param ester 药物类型
 * @param doseMG 剂量（mg）
 * @param scheduleType 给药周期类型
 * @param timeOfDay 给药时间（一天中的时刻）
 * @param daysOfWeek 一周中的哪几天（仅用于WEEKLY类型）
 * @param intervalDays 间隔天数（仅用于CUSTOM类型）
 * @param isEnabled 是否启用
 * @param extras 额外参数（如舌下θ、贴片释放速率等）
 * @param createdAt 创建时间戳
 */
data class MedicationPlan(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val route: Route,
    val ester: Ester,
    val doseMG: Double,
    val scheduleType: ScheduleType,
    val timeOfDay: List<LocalTime>, // 支持多个时间点（如每天8:00和23:30）
    val daysOfWeek: Set<DayOfWeek> = emptySet(), // 一周中的哪几天
    val intervalDays: Int = 1, // 间隔天数（用于CUSTOM类型）
    val isEnabled: Boolean = true,
    val extras: Map<DoseEvent.ExtraKey, Double> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * 给药周期类型
     */
    enum class ScheduleType {
        DAILY,      // 每天
        WEEKLY,     // 每周特定几天
        CUSTOM      // 自定义间隔天数
    }

    /**
     * 获取方案的显示描述
     */
    fun getDescription(): String {
        val routeStr = when (route) {
            Route.INJECTION -> "注射"
            Route.ORAL -> "口服"
            Route.SUBLINGUAL -> "舌下"
            Route.GEL -> "凝胶"
            Route.PATCH_APPLY -> "贴片"
            Route.PATCH_REMOVE -> "移除贴片"
            Route.ANTIANDROGEN -> "抗雄口服"
        }

        val scheduleStr = when (scheduleType) {
            ScheduleType.DAILY -> "每天"
            ScheduleType.WEEKLY -> {
                val days = daysOfWeek.sortedBy { it.value }.joinToString("、") { dayName(it) }
                "每周$days"
            }
            ScheduleType.CUSTOM -> "每${intervalDays}天"
        }

        val timeStr = timeOfDay.joinToString("、") { it.toString() }

        val medicationStr = if (route == Route.ANTIANDROGEN) {
            val aaType = extras[DoseEvent.ExtraKey.ANTI_ANDROGEN_TYPE]?.toInt()?.let {
                AntiAndrogen.values().getOrElse(it) { AntiAndrogen.CPA }
            } ?: AntiAndrogen.CPA
            aaType.antiAndrogenDisplayName
        } else {
            ester.displayName
        }

        return "$scheduleStr $timeStr $routeStr ${doseMG}mg $medicationStr"
    }

    private fun dayName(day: DayOfWeek): String {
        return when (day) {
            DayOfWeek.MONDAY -> "周一"
            DayOfWeek.TUESDAY -> "周二"
            DayOfWeek.WEDNESDAY -> "周三"
            DayOfWeek.THURSDAY -> "周四"
            DayOfWeek.FRIDAY -> "周五"
            DayOfWeek.SATURDAY -> "周六"
            DayOfWeek.SUNDAY -> "周日"
        }
    }
}

/**
 * Ester扩展属性：显示名称
 */
val Ester.displayName: String
    get() = when (this) {
        Ester.E2 -> "雌二醇"
        Ester.EB -> "苯甲酸雌二醇"
        Ester.EV -> "戊酸雌二醇"
        Ester.EC -> "环戊丙酸雌二醇"
        Ester.EN -> "庚酸雌二醇"
    }
