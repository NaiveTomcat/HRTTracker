package cn.naivetomcat.hrt_tracker.pk

import kotlin.math.exp

/**
 * 预计算事件模型
 * 为单个给药事件提供高效的时间-浓度函数
 */
internal class PrecomputedEventModel(
    event: DoseEvent,
    allEvents: List<DoseEvent>,
    bodyWeightKG: Double
) {
    private val model: (Double) -> Double

    init {
        val params = ParameterResolver.resolve(event, bodyWeightKG)
        val startTime = event.timeH
        val dose = event.doseMG

        model = when (event.route) {
            Route.INJECTION -> { timeH ->
                val tau = timeH - startTime
                if (tau >= 0) {
                    ThreeCompartmentModel.injAmount(tau, dose, params)
                } else {
                    0.0
                }
            }

            Route.GEL, Route.ORAL -> { timeH ->
                val tau = timeH - startTime
                if (tau >= 0) {
                    ThreeCompartmentModel.oneCompAmount(tau, dose, params)
                } else {
                    0.0
                }
            }

            Route.SUBLINGUAL -> { timeH ->
                val tau = timeH - startTime
                if (tau >= 0) {
                    if (params.k2 > 0) {
                        // EV舌下：
                        // 快支路（黏膜）走3C：吸收→水解→清除
                        // 慢支路（吞咽/胃肠）与oral完全一致：一室Bateman（不再额外水解）
                        ThreeCompartmentModel.dualAbsMixedAmount(tau, dose, params)
                    } else {
                        // E2舌下：无水解，沿用一室解析式
                        ThreeCompartmentModel.dualAbsAmount(tau, dose, params)
                    }
                } else {
                    0.0
                }
            }

            Route.PATCH_APPLY -> {
                val remove = allEvents.firstOrNull {
                    it.route == Route.PATCH_REMOVE && it.timeH > startTime
                }
                val wearH = (remove?.timeH ?: Double.MAX_VALUE) - startTime

                { timeH ->
                    val tau = timeH - startTime
                    if (tau >= 0) {
                        ThreeCompartmentModel.patchAmount(tau, dose, wearH, params)
                    } else {
                        0.0
                    }
                }
            }

            Route.PATCH_REMOVE -> { _ -> 0.0 }

            // 抗雄药物不参与药代动力学计算，应在调用前过滤，这里仅作防御性处理
            Route.ANTIANDROGEN -> { _ -> 0.0 }
        }
    }

    /**
     * 计算在指定时间点的药物量
     * @param timeH 时间（小时）
     * @return 药物量（mg）
     */
    fun amount(timeH: Double): Double = model(timeH)
}

/**
 * 模拟结果
 * @param timeH 时间点数组（小时）
 * @param concPGmL 浓度数组（pg/mL）
 * @param auc 曲线下面积（pg·h/mL）
 */
data class SimulationResult(
    val timeH: List<Double>,
    val concPGmL: List<Double>,
    val auc: Double
) {
    /**
     * 在指定时间点插值浓度
     * @param hour 时间（小时）
     * @return 插值后的浓度（pg/mL），如果无法计算则返回null
     */
    fun concentration(hour: Double): Double? {
        if (timeH.isEmpty() || timeH.size != concPGmL.size) return null
        if (hour <= timeH.first()) return concPGmL.first()
        if (hour >= timeH.last()) return concPGmL.last()

        var low = 0
        var high = timeH.size - 1

        while (high - low > 1) {
            val mid = (low + high) / 2
            when {
                timeH[mid] == hour -> return concPGmL[mid]
                timeH[mid] < hour -> low = mid
                else -> high = mid
            }
        }

        val t0 = timeH[low]
        val t1 = timeH[high]
        val c0 = concPGmL[low]
        val c1 = concPGmL[high]
        
        if (t1 <= t0) return c0
        
        val ratio = (hour - t0) / (t1 - t0)
        return c0 + (c1 - c0) * ratio
    }
}

/**
 * 模拟引擎
 * 执行多给药事件的药代动力学模拟
 */
class SimulationEngine(
    events: List<DoseEvent>,
    bodyWeightKG: Double,
    private val startTimeH: Double,
    private val endTimeH: Double,
    private val numberOfSteps: Int
) {
    private val precomputedModels: List<PrecomputedEventModel>
    private val plasmaVolumeML: Double

    init {
        precomputedModels = events.mapNotNull { event ->
            if (event.route != Route.PATCH_REMOVE) {
                PrecomputedEventModel(event, events, bodyWeightKG)
            } else {
                null
            }
        }

        // 文献值：雌二醇的分布容积通常为10-15 L/kg（由于组织结合）
        plasmaVolumeML = CorePK.VD_PER_KG * bodyWeightKG * 1000
    }

    /**
     * 运行模拟
     * @return 模拟结果，包含时间序列、浓度序列和AUC
     */
    fun run(): SimulationResult {
        if (startTimeH >= endTimeH || numberOfSteps <= 1 || plasmaVolumeML <= 0) {
            return SimulationResult(emptyList(), emptyList(), 0.0)
        }

        val stepSize = (endTimeH - startTimeH) / (numberOfSteps - 1)
        val timeArr = mutableListOf<Double>()
        val concArr = mutableListOf<Double>()
        var auc = 0.0

        for (i in 0 until numberOfSteps) {
            val t = startTimeH + i * stepSize
            var totalAmountMG = 0.0

            for (model in precomputedModels) {
                totalAmountMG += model.amount(t)
            }

            // 将mg转换为pg，然后除以容积得到浓度
            val currentConc = totalAmountMG * 1e9 / plasmaVolumeML

            timeArr.add(t)
            concArr.add(currentConc)

            // 梯形法计算AUC
            if (i > 0) {
                auc += 0.5 * (currentConc + concArr[i - 1]) * stepSize
            }
        }

        return SimulationResult(timeArr, concArr, auc)
    }

    companion object {
        /**
         * 便捷方法：基于事件列表自动确定模拟时间范围
         * 
         * @param events 给药事件列表
         * @param bodyWeightKG 体重（kg）
         * @param numberOfSteps 时间步数（默认1000）
         * @return 模拟结果
         */
        fun runSimulation(
            events: List<DoseEvent>,
            bodyWeightKG: Double,
            numberOfSteps: Int = 1000
        ): SimulationResult {
            if (events.isEmpty()) {
                return SimulationResult(emptyList(), emptyList(), 0.0)
            }

            val sortedEvents = events.sortedBy { it.timeH }
            val tMin = sortedEvents.first().timeH
            val tMax = sortedEvents.last().timeH

            // 定义模拟时间范围
            val tStart = tMin - 24.0  // 提前24小时
            val tEnd = tMax + 24.0 * 14  // 延长14天

            val engine = SimulationEngine(
                events = sortedEvents,
                bodyWeightKG = bodyWeightKG,
                startTimeH = tStart,
                endTimeH = tEnd,
                numberOfSteps = numberOfSteps
            )

            return engine.run()
        }
    }
}
