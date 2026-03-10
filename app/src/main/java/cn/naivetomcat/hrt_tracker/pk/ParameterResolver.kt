package cn.naivetomcat.hrt_tracker.pk

/**
 * 参数解析器
 * 根据给药事件解析出对应的药代动力学参数
 */
object ParameterResolver {

    /**
     * 解析给药事件的PK参数
     * 
     * @param event 给药事件
     * @param bodyWeightKG 体重（kg）
     * @return 解析后的PK参数
     */
    fun resolve(event: DoseEvent, bodyWeightKG: Double): PKParams {
        val k3 = if (event.route == Route.INJECTION) {
            CorePK.K_CLEAR_INJECTION
        } else {
            CorePK.K_CLEAR
        }

        return when (event.route) {
            Route.INJECTION -> resolveInjection(event, k3)
            Route.PATCH_APPLY -> resolvePatchApply(event, k3)
            Route.GEL -> resolveGel(event, k3)
            Route.ORAL -> resolveOral(event, k3)
            Route.SUBLINGUAL -> resolveSublingual(event, k3)
            Route.PATCH_REMOVE -> PKParams(
                fracFast = 0.0,
                k1Fast = 0.0,
                k1Slow = 0.0,
                k2 = 0.0,
                k3 = k3,
                F = 0.0,
                rateMGh = 0.0,
                fFast = 0.0,
                fSlow = 0.0
            )
            // 抗雄药物不参与药代动力学计算，应在调用前过滤，这里仅作防御性处理
            Route.ANTIANDROGEN -> PKParams(
                fracFast = 0.0,
                k1Fast = 0.0,
                k1Slow = 0.0,
                k2 = 0.0,
                k3 = k3,
                F = 0.0,
                rateMGh = 0.0,
                fFast = 0.0,
                fSlow = 0.0
            )
        }
    }

    /**
     * 解析注射参数
     */
    private fun resolveInjection(event: DoseEvent, k3: Double): PKParams {
        // 新的双部分depot参数 + formationFraction + 全局k1修正
        val k1Corr = CorePK.DEPOT_K1_CORR
        val k1Fast = (TwoPartDepotPK.k1Fast[event.ester] ?: 0.0) * k1Corr
        val k1Slow = (TwoPartDepotPK.k1Slow[event.ester] ?: 0.0) * k1Corr
        val fracFast = TwoPartDepotPK.fracFast[event.ester] ?: 1.0

        // F = formationFraction × 分子量换算 toE2Factor
        val form = InjectionPK.formationFraction[event.ester] ?: 0.08
        val toE2 = Ester.toE2Factor(event.ester)
        val F = form * toE2

        return PKParams(
            fracFast = fracFast,
            k1Fast = k1Fast,
            k1Slow = k1Slow,
            k2 = EsterPK.k2[event.ester] ?: 0.0,
            k3 = k3,
            F = F,
            rateMGh = 0.0,
            fFast = F,
            fSlow = F
        )
    }

    /**
     * 解析贴片应用参数
     */
    private fun resolvePatchApply(event: DoseEvent, k3: Double): PKParams {
        val releaseRateUG = event.extras[DoseEvent.ExtraKey.RELEASE_RATE_UG_PER_DAY]
        
        if (releaseRateUG != null) {
            // 零级释放模式
            val rateMGh = releaseRateUG / 24_000.0  // µg/day → mg/h
            return PKParams(
                fracFast = 1.0,
                k1Fast = 0.0,
                k1Slow = 0.0,
                k2 = 0.0,
                k3 = k3,
                F = 1.0,
                rateMGh = rateMGh,
                fFast = 1.0,
                fSlow = 1.0
            )
        } else {
            // 一阶释放模式
            val k1 = PatchPK.GENERIC_K1
            return PKParams(
                fracFast = 1.0,
                k1Fast = k1,
                k1Slow = 0.0,
                k2 = 0.0,
                k3 = k3,
                F = 1.0,
                rateMGh = 0.0,
                fFast = 1.0,
                fSlow = 1.0
            )
        }
    }

    /**
     * 解析凝胶参数
     */
    private fun resolveGel(event: DoseEvent, k3: Double): PKParams {
        val area = event.extras[DoseEvent.ExtraKey.AREA_CM2] ?: 750.0
        val (k1, F) = TransdermalGelPK.parameters(event.doseMG, area)
        
        return PKParams(
            fracFast = 1.0,
            k1Fast = k1,
            k1Slow = 0.0,
            k2 = 0.0,
            k3 = k3,
            F = F,
            rateMGh = 0.0,
            fFast = F,
            fSlow = F
        )
    }

    /**
     * 解析口服参数
     */
    private fun resolveOral(event: DoseEvent, k3: Double): PKParams {
        val k1Value = if (event.ester == Ester.EV) OralPK.K_ABS_EV else OralPK.K_ABS_E2
        val k2Value = if (event.ester == Ester.EV) (EsterPK.k2[Ester.EV] ?: 0.0) else 0.0
        
        return PKParams(
            fracFast = 1.0,
            k1Fast = k1Value,
            k1Slow = 0.0,
            k2 = k2Value,
            k3 = k3,
            F = OralPK.BIOAVAILABILITY,
            rateMGh = 0.0,
            fFast = OralPK.BIOAVAILABILITY,
            fSlow = OralPK.BIOAVAILABILITY
        )
    }

    /**
     * 解析舌下参数
     */
    private fun resolveSublingual(event: DoseEvent, k3: Double): PKParams {
        // θ解析器：优先使用显式theta；否则从UI档位代码映射
        val theta = resolveTheta(event)
        
        val k1Fast = OralPK.K_ABS_SL
        val k1Slow = if (event.ester == Ester.EV) OralPK.K_ABS_EV else OralPK.K_ABS_E2
        
        // 舌下快通路（黏膜）默认fFast = 1（剂量按E2当量输入）
        // 吞咽慢通路（相当于口服）fSlow = 口服生物利用度
        val fFast = 1.0
        val fSlow = OralPK.BIOAVAILABILITY
        
        // 若为EV，保留已标定的k2（供舌下快支路/其他3C路径使用）；E2则k2 = 0
        // 舌下慢支路（吞咽→胃肠）会按oral一室模型计算，不再额外水解
        val k2Value = if (event.ester == Ester.EV) (EsterPK.k2[Ester.EV] ?: 0.0) else 0.0
        
        return PKParams(
            fracFast = theta.coerceIn(0.0, 1.0),
            k1Fast = k1Fast,
            k1Slow = k1Slow,
            k2 = k2Value,
            k3 = k3,
            F = 1.0,
            rateMGh = 0.0,
            fFast = fFast,
            fSlow = fSlow
        )
    }

    /**
     * 解析舌下θ值
     * 优先使用extras中的显式theta，否则从tier映射
     */
    private fun resolveTheta(event: DoseEvent): Double {
        // 优先使用显式theta
        event.extras[DoseEvent.ExtraKey.SUBLINGUAL_THETA]?.let {
            return it.coerceIn(0.0, 1.0)
        }
        
        // 从tier代码映射
        event.extras[DoseEvent.ExtraKey.SUBLINGUAL_TIER]?.let { code ->
            val tier = when (code.toInt()) {
                0 -> SublingualTier.QUICK
                1 -> SublingualTier.CASUAL
                2 -> SublingualTier.STANDARD
                3 -> SublingualTier.STRICT
                else -> SublingualTier.STANDARD
            }
            return (SublingualTheta.recommended[tier] ?: 0.11).coerceIn(0.0, 1.0)
        }
        
        // 回退到标准档位
        return 0.11
    }
}
