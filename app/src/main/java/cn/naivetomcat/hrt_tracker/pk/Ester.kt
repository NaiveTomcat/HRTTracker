package cn.naivetomcat.hrt_tracker.pk

/**
 * 雌激素酯类枚举
 */
enum class Ester {
    E2,  // 雌二醇
    EB,  // 苯甲酸雌二醇
    EV,  // 戊酸雌二醇
    EC,  // 环戊丙酸雌二醇
    EN;  // 庚酸雌二醇

    companion object {
        /**
         * 雌二醇（E2）的分子量
         */
        const val E2_MOLECULAR_WEIGHT = 272.38

        /**
         * 各酯类的完整名称
         */
        val fullNames = mapOf(
            E2 to "Estradiol",
            EB to "Estradiol Benzoate",
            EV to "Estradiol Valerate",
            EC to "Estradiol Cypionate",
            EN to "Estradiol Enanthate"
        )

        /**
         * 各酯类的分子量（g/mol）
         */
        val molecularWeights = mapOf(
            E2 to 272.38,
            EB to 376.50,  // C25H28O2
            EV to 356.50,  // C23H32O3
            EC to 396.58,  // C26H36O3
            EN to 384.56   // C25H36O3
        )

        /**
         * 获取酯类到E2的转换因子
         * 用于将酯类剂量折算为等效的雌二醇剂量
         */
        fun toE2Factor(ester: Ester): Double {
            if (ester == E2) return 1.0
            return E2_MOLECULAR_WEIGHT / (molecularWeights[ester] ?: E2_MOLECULAR_WEIGHT)
        }
    }

    /**
     * 获取完整名称
     */
    fun fullName(): String = fullNames[this] ?: name

    /**
     * 获取分子量
     */
    fun molecularWeight(): Double = molecularWeights[this] ?: E2_MOLECULAR_WEIGHT

    /**
     * 获取到E2的转换因子
     */
    fun toE2Factor(): Double = toE2Factor(this)
}
