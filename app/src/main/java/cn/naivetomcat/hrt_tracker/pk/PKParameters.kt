package cn.naivetomcat.hrt_tracker.pk

/**
 * 核心药代动力学参数
 */
object CorePK {
    /**
     * 分布容积系数（L/kg）
     * 文献值：雌二醇的分布容积通常为10-15 L/kg（由于组织结合）
     */
    const val VD_PER_KG = 2.0

    /**
     * 自由雌二醇清除速率常数（h⁻¹）
     * 用于非注射途径
     */
    const val K_CLEAR = 0.41

    /**
     * 注射途径的清除速率常数（h⁻¹）
     * 这是一个有效参数，用于保持翻转动力学（吸收限制）
     * 以匹配油基肌肉注射酯类的文献Tmax/Cmax
     */
    const val K_CLEAR_INJECTION = 0.041

    /**
     * 注射储库k1的全局修正系数
     * 乘到注射k1_fast/k1_slow上
     */
    const val DEPOT_K1_CORR = 1.0
}

/**
 * 注射药代动力学参数：双部分储库模型
 * 将注射储库建模为两个平行的一阶室，以更好地控制峰值（Tmax/Cmax）和尾部曲线
 */
object TwoPartDepotPK {
    /**
     * 进入"快速"吸收储库的剂量分数（0-1）
     * 其余部分（1 - fracFast）进入"慢速"储库
     */
    val fracFast = mapOf(
        Ester.EB to 0.90,
        Ester.EV to 0.40,
        Ester.EC to 0.229164549,
        Ester.EN to 0.05
    )

    /**
     * 快速储库的吸收速率常数（h⁻¹）
     * 主要控制Tmax和Cmax
     */
    val k1Fast = mapOf(
        Ester.EB to 0.144,
        Ester.EV to 0.0216,
        Ester.EC to 0.005035046,
        Ester.EN to 0.0010
    )

    /**
     * 慢速储库的吸收速率常数（h⁻¹）
     * 主要控制终末半衰期（尾部）
     */
    val k1Slow = mapOf(
        Ester.EB to 0.114,
        Ester.EV to 0.0138,
        Ester.EC to 0.004510574,
        Ester.EN to 0.0050
    )
}

/**
 * 注射药代动力学：形成分数
 */
object InjectionPK {
    /**
     * 形成游离E2的经验分数
     * 本项目所有剂量已按E2当量输入
     * 最终F = formationFraction × toE2Factor
     */
    val formationFraction = mapOf(
        Ester.EB to 0.10922376473734707,
        Ester.EV to 0.062258288229969413,
        Ester.EC to 0.117255838,
        Ester.EN to 0.12
    )
}

/**
 * 酯类水解速率参数
 */
object EsterPK {
    /**
     * 血浆/肝酯酶水解速率常数 k₂（h⁻¹）
     */
    val k2 = mapOf(
        Ester.EB to 0.090,   // t½ ≈ 7.7 h
        Ester.EV to 0.070,   // t½ ≈ 9.9 h
        Ester.EC to 0.045,   // t½ ≈ 15.4 h
        Ester.EN to 0.015    // t½ ≈ 46.21 h
    )
}

/**
 * 口服药代动力学参数
 */
object OralPK {
    /**
     * 吸收速率常数（ka，h⁻¹）
     */
    const val K_ABS_E2 = 0.32   // 游离微粉化雌二醇（Tmax ≈ 2-3 h）
    const val K_ABS_EV = 0.05   // 戊酸雌二醇片（Tmax ≈ 6-7 h）

    /**
     * 系统生物利用度（首过效应）
     * E2和EV相似
     */
    const val BIOAVAILABILITY = 0.03

    /**
     * 舌下吸收速率（一阶）
     * 调整为在当前kClear下约1小时Tmax
     */
    const val K_ABS_SL = 1.8   // h⁻¹（与CorePK.K_CLEAR=0.41配合 → Tmax ≈ 1 h）
}

/**
 * 舌下行为档位到θ的映射
 * 基于"溶解 + 黏膜吸收 + 吞咽清除"的最小口腔模型数值积分
 */
object SublingualTheta {
    /**
     * 推荐θ值（中档场景）
     * θ表示舌下直接吸收的比例
     */
    val recommended = mapOf(
        SublingualTier.QUICK to 0.01,    // ≈ 2分钟含服
        SublingualTier.CASUAL to 0.04,   // ≈ 5分钟含服
        SublingualTier.STANDARD to 0.11, // ≈ 10分钟含服
        SublingualTier.STRICT to 0.18    // ≈ 15分钟含服
    )

    /**
     * 建议含服时长（分钟），用于UI提示
     */
    val holdMinutes = mapOf(
        SublingualTier.QUICK to 2.0,
        SublingualTier.CASUAL to 5.0,
        SublingualTier.STANDARD to 10.0,
        SublingualTier.STRICT to 15.0
    )

    /**
     * θ的参考下限（跨不同k_sw与k_diss的数值积分范围）
     */
    val thetaRangeLow = mapOf(
        SublingualTier.QUICK to 0.004,
        SublingualTier.CASUAL to 0.021,
        SublingualTier.STANDARD to 0.064,
        SublingualTier.STRICT to 0.115
    )

    /**
     * θ的参考上限
     */
    val thetaRangeHigh = mapOf(
        SublingualTier.QUICK to 0.012,
        SublingualTier.CASUAL to 0.057,
        SublingualTier.STANDARD to 0.156,
        SublingualTier.STRICT to 0.253
    )
}

/**
 * 透皮凝胶药代动力学参数
 */
object TransdermalGelPK {
    /**
     * 基线参数（来自EstroGel 0.75 mg在750 cm²）
     */
    private const val BASE_K1 = 0.022      // h⁻¹  (t½ ≈ 36 h)
    private const val SIGMA_SAT = 0.0080   // mg/cm²（略低于0.75/750）
    private const val F_MAX = 0.05

    /**
     * 计算给定每日剂量（mg）和涂抹面积（cm²）的k1和F
     * 注意：当前为简化模型，忽略涂抹面积
     * 始终返回基线k1和固定F（Fmax）
     */
    fun parameters(doseMG: Double, areaCM2: Double): Pair<Double, Double> {
        if (doseMG <= 0) return Pair(0.0, 0.0)
        
        val k1 = BASE_K1  // 恒定吸收速率
        val F = F_MAX     // 恒定系统分数
        return Pair(k1, F)
    }
}

/**
 * 贴片药代动力学参数
 */
object PatchPK {
    /**
     * 通用贴片：高载荷，用一阶释放近似
     */
    const val GENERIC_K1 = 0.0075   // k₁ ≈ 3.8 d t½
}
