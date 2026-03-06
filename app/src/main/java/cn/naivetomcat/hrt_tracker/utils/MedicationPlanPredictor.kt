package cn.naivetomcat.hrt_tracker.utils

import cn.naivetomcat.hrt_tracker.data.MedicationPlan
import cn.naivetomcat.hrt_tracker.pk.DoseEvent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.*

/**
 * 用药方案预测工具
 * 根据用药方案生成未来的虚拟DoseEvent列表
 */
object MedicationPlanPredictor {

    /**
     * 实际用药记录与计划用药冲突的时间窗口（小时）
     * 实际用药后此窗口内的计划用药将被过滤，避免短时间内重复计算
     */
    const val PLAN_CONFLICT_WINDOW_H = 1.0

    /**
     * 根据用药方案生成未来的DoseEvent列表
     * @param plan 用药方案
     * @param fromDateTime 起始时间
     * @param daysAhead 预测多少天
     * @return 生成的DoseEvent列表
     */
    fun generateFutureEvents(
        plan: MedicationPlan,
        fromDateTime: LocalDateTime = LocalDateTime.now(),
        daysAhead: Int = 15
    ): List<DoseEvent> {
        if (!plan.isEnabled) {
            return emptyList()
        }

        val events = mutableListOf<DoseEvent>()
        val today = fromDateTime.toLocalDate()

        when (plan.scheduleType) {
            MedicationPlan.ScheduleType.DAILY -> {
                // 每天的每个时间点
                for (dayOffset in 0 until daysAhead) {
                    val date = today.plusDays(dayOffset.toLong())
                    
                    for (time in plan.timeOfDay) {
                        val dateTime = LocalDateTime.of(date, time)
                        
                        // 只添加未来的事件
                        if (dateTime.isAfter(fromDateTime)) {
                            events.add(createDoseEvent(plan, dateTime))
                        }
                    }
                }
            }

            MedicationPlan.ScheduleType.WEEKLY -> {
                // 每周特定几天的每个时间点
                for (dayOffset in 0 until daysAhead) {
                    val date = today.plusDays(dayOffset.toLong())
                    val dayOfWeek = date.dayOfWeek
                    
                    if (plan.daysOfWeek.contains(dayOfWeek)) {
                        for (time in plan.timeOfDay) {
                            val dateTime = LocalDateTime.of(date, time)
                            
                            // 只添加未来的事件
                            if (dateTime.isAfter(fromDateTime)) {
                                events.add(createDoseEvent(plan, dateTime))
                            }
                        }
                    }
                }
            }

            MedicationPlan.ScheduleType.CUSTOM -> {
                // 自定义间隔天数
                var dayOffset = 0
                
                while (dayOffset < daysAhead) {
                    val date = today.plusDays(dayOffset.toLong())
                    
                    for (time in plan.timeOfDay) {
                        val dateTime = LocalDateTime.of(date, time)
                        
                        // 只添加未来的事件
                        if (dateTime.isAfter(fromDateTime)) {
                            events.add(createDoseEvent(plan, dateTime))
                        }
                    }
                    
                    dayOffset += plan.intervalDays
                }
            }
        }

        return events.sortedBy { it.timeH }
    }

    /**
     * 根据用药方案和时间创建DoseEvent
     */
    private fun createDoseEvent(plan: MedicationPlan, dateTime: LocalDateTime): DoseEvent {
        val timeH = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() / 3600000.0
        
        return DoseEvent(
            id = UUID.randomUUID(),
            route = plan.route,
            timeH = timeH,
            doseMG = plan.doseMG,
            ester = plan.ester,
            extras = plan.extras
        )
    }

    /**
     * 为多个用药方案生成未来事件
     * @param plans 用药方案列表
     * @param fromDateTime 起始时间
     * @param daysAhead 预测多少天
     * @return 合并后的DoseEvent列表
     */
    fun generateFutureEventsForPlans(
        plans: List<MedicationPlan>,
        fromDateTime: LocalDateTime = LocalDateTime.now(),
        daysAhead: Int = 15
    ): List<DoseEvent> {
        return plans
            .filter { it.isEnabled }
            .flatMap { plan -> generateFutureEvents(plan, fromDateTime, daysAhead) }
            .sortedBy { it.timeH }
    }

    /**
     * 过滤与实际用药记录冲突的预测事件
     *
     * 若某个预测事件与实际用药记录具有相同的给药途径和酯类，
     * 且发生在实际用药时间点之后的 [conflictWindowH] 小时内，
     * 则认为该实际记录已覆盖此次计划用药，预测事件将被过滤掉，
     * 以避免在短时间内出现两次参与计算的用药事件。
     *
     * @param predictedEvents 预测事件列表
     * @param actualEvents 实际用药事件列表
     * @param conflictWindowH 冲突时间窗口（小时），默认 [PLAN_CONFLICT_WINDOW_H]
     * @return 过滤后的预测事件列表
     */
    fun filterConflictingPredictions(
        predictedEvents: List<DoseEvent>,
        actualEvents: List<DoseEvent>,
        conflictWindowH: Double = PLAN_CONFLICT_WINDOW_H
    ): List<DoseEvent> {
        if (actualEvents.isEmpty() || predictedEvents.isEmpty()) return predictedEvents

        return predictedEvents.filter { predicted ->
            actualEvents.none { actual ->
                actual.route == predicted.route &&
                    actual.ester == predicted.ester &&
                    predicted.timeH > actual.timeH &&
                    predicted.timeH <= actual.timeH + conflictWindowH
            }
        }
    }
}
