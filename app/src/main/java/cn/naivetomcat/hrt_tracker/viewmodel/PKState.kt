package cn.naivetomcat.hrt_tracker.viewmodel

import cn.naivetomcat.hrt_tracker.pk.SimulationResult

/**
 * 药代动力学UI状态
 */
data class PKState(
    val simulationResult: SimulationResult? = null,
    val currentTimeH: Double = System.currentTimeMillis() / 3600000.0,
    val currentConcentration: Double? = null,
    val isSimulating: Boolean = false,
    val error: String? = null
) {
    /**
     * 获取浓度等级描述（参考Oyama-s-HRT-Tracker医学标准）
     */
    fun getConcentrationLevel(): String? {
        if (currentConcentration == null) return null
        
        // 浓度判定优先级顺序（参考Oyama项目）
        return when {
            currentConcentration > 300 -> "高于参考范围"
            currentConcentration >= 100 && currentConcentration <= 200 -> "非针剂女性向 GAHT 目标"
            currentConcentration >= 70 && currentConcentration <= 300 -> "女性黄体期"
            currentConcentration >= 30 && currentConcentration < 70 -> "女性卵泡期"
            else -> "低于参考范围"
        }
    }

    /**
     * 获取浓度等级颜色（语义化，参考Oyama标准）
     */
    fun getConcentrationLevelColor(): ConcentrationLevel {
        if (currentConcentration == null) return ConcentrationLevel.UNKNOWN
        
        // 颜色分类与浓度范围对应
        return when {
            currentConcentration > 300 -> ConcentrationLevel.HIGH  // 高于参考范围（琥珀色）
            currentConcentration >= 100 && currentConcentration <= 200 -> ConcentrationLevel.MTF_TARGET  // GAHT目标（翠绿色）
            currentConcentration >= 70 && currentConcentration <= 300 -> ConcentrationLevel.LUTEAL  // 黄体期（蓝色）
            currentConcentration >= 30 && currentConcentration < 70 -> ConcentrationLevel.FOLLICULAR  // 卵泡期（靛蓝色）
            else -> ConcentrationLevel.LOW  // 低于参考范围（琥珀色）
        }
    }
}

/**
 * 浓度等级（参考医学标准）
 */
enum class ConcentrationLevel {
    UNKNOWN,         // 未知
    LOW,             // 低于参考范围 (<30 pg/mL)
    FOLLICULAR,      // 女性卵泡期 (30-70 pg/mL)
    LUTEAL,          // 女性黄体期 (70-300 pg/mL)
    MTF_TARGET,      // 非针剂女性向GAHT目标 (100-200 pg/mL)
    HIGH             // 高于参考范围 (>300 pg/mL)
}
