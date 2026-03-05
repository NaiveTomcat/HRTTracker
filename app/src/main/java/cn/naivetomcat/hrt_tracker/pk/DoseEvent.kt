package cn.naivetomcat.hrt_tracker.pk

import java.util.UUID

/**
 * 给药事件
 * @param id 唯一标识符
 * @param route 给药途径
 * @param timeH 时间（小时，相对于1970年起的绝对小时数）
 * @param doseMG 剂量（mg）
 * @param ester 酯类类型
 * @param extras 额外参数（如舌下θ、贴片释放速率等）
 */
data class DoseEvent(
    val id: UUID = UUID.randomUUID(),
    val route: Route,
    val timeH: Double,
    val doseMG: Double,
    val ester: Ester,
    val extras: Map<ExtraKey, Double> = emptyMap()
) {
    /**
     * 额外参数键枚举
     */
    enum class ExtraKey {
        CONCENTRATION_MG_ML,      // 浓度（mg/mL）
        AREA_CM2,                 // 涂抹面积（cm²）
        RELEASE_RATE_UG_PER_DAY, // 释放速率（µg/day），用于零级释放贴片
        SUBLINGUAL_THETA,         // 舌下直接吸收比例（0-1）
        SUBLINGUAL_TIER           // 舌下档位代码（0-3）
    }

}
