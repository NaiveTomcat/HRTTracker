package cn.naivetomcat.hrt_tracker.pk

/**
 * 药代动力学参数
 * 用于描述单个给药事件的动力学行为
 * 
 * @param fracFast 快速吸收库的比例（0-1）
 * @param k1Fast 快速吸收库的速率常数（h⁻¹）
 * @param k1Slow 慢速吸收库的速率常数（h⁻¹）
 * @param k2 酯类水解速率常数（h⁻¹）
 * @param k3 雌二醇消除速率常数（h⁻¹）
 * @param F 生物利用度（整体）
 * @param rateMGh 零级释放速率（mg/h），用于贴片
 * @param fFast 快速通路的生物利用度
 * @param fSlow 慢速通路的生物利用度
 */
data class PKParams(
    val fracFast: Double,
    val k1Fast: Double,
    val k1Slow: Double,
    val k2: Double,
    val k3: Double,
    val F: Double,
    val rateMGh: Double,
    val fFast: Double,
    val fSlow: Double
)
