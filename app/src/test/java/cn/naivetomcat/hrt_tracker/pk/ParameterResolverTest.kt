package cn.naivetomcat.hrt_tracker.pk

import org.junit.Assert.*
import org.junit.Test

/**
 * 参数解析器测试
 */
class ParameterResolverTest {

    private val bodyWeight = 60.0  // kg
    private val epsilon = 1e-6

    @Test
    fun testResolveInjectionEB() {
        val event = DoseEvent(
            route = Route.INJECTION,
            timeH = 100.0,
            doseMG = 5.0,
            ester = Ester.EB
        )
        
        val params = ParameterResolver.resolve(event, bodyWeight)
        
        // 验证基本结构
        assertEquals(CorePK.K_CLEAR_INJECTION, params.k3, epsilon)
        assertEquals(TwoPartDepotPK.fracFast[Ester.EB]!!, params.fracFast, epsilon)
        
        // 验证k1值（应用了修正系数）
        val expectedK1Fast = TwoPartDepotPK.k1Fast[Ester.EB]!! * CorePK.DEPOT_K1_CORR
        val expectedK1Slow = TwoPartDepotPK.k1Slow[Ester.EB]!! * CorePK.DEPOT_K1_CORR
        assertEquals(expectedK1Fast, params.k1Fast, epsilon)
        assertEquals(expectedK1Slow, params.k1Slow, epsilon)
        
        // 验证k2
        assertEquals(EsterPK.k2[Ester.EB]!!, params.k2, epsilon)
        
        // 验证F（formation fraction × toE2Factor）
        val expectedF = InjectionPK.formationFraction[Ester.EB]!! * Ester.EB.toE2Factor()
        assertEquals(expectedF, params.F, epsilon)
        assertEquals(expectedF, params.fFast, epsilon)
        assertEquals(expectedF, params.fSlow, epsilon)
        
        // rateMGh应该为0（非贴片）
        assertEquals(0.0, params.rateMGh, epsilon)
    }

    @Test
    fun testResolveInjectionEV() {
        val event = DoseEvent(
            route = Route.INJECTION,
            timeH = 100.0,
            doseMG = 5.0,
            ester = Ester.EV
        )
        
        val params = ParameterResolver.resolve(event, bodyWeight)
        
        assertEquals(CorePK.K_CLEAR_INJECTION, params.k3, epsilon)
        assertEquals(TwoPartDepotPK.fracFast[Ester.EV]!!, params.fracFast, epsilon)
        assertEquals(EsterPK.k2[Ester.EV]!!, params.k2, epsilon)
    }

    @Test
    fun testResolveOralE2() {
        val event = DoseEvent(
            route = Route.ORAL,
            timeH = 100.0,
            doseMG = 2.0,
            ester = Ester.E2
        )
        
        val params = ParameterResolver.resolve(event, bodyWeight)
        
        // 口服使用非注射清除率
        assertEquals(CorePK.K_CLEAR, params.k3, epsilon)
        
        // E2口服使用K_ABS_E2
        assertEquals(OralPK.K_ABS_E2, params.k1Fast, epsilon)
        assertEquals(0.0, params.k1Slow, epsilon)
        
        // E2没有水解步骤
        assertEquals(0.0, params.k2, epsilon)
        
        // 生物利用度
        assertEquals(OralPK.BIOAVAILABILITY, params.F, epsilon)
        
        // 单一通路
        assertEquals(1.0, params.fracFast, epsilon)
    }

    @Test
    fun testResolveOralEV() {
        val event = DoseEvent(
            route = Route.ORAL,
            timeH = 100.0,
            doseMG = 2.0,
            ester = Ester.EV
        )
        
        val params = ParameterResolver.resolve(event, bodyWeight)
        
        assertEquals(CorePK.K_CLEAR, params.k3, epsilon)
        
        // EV口服使用K_ABS_EV
        assertEquals(OralPK.K_ABS_EV, params.k1Fast, epsilon)
        
        // EV有水解步骤
        assertEquals(EsterPK.k2[Ester.EV]!!, params.k2, epsilon)
        
        assertEquals(OralPK.BIOAVAILABILITY, params.F, epsilon)
    }

    @Test
    fun testResolveSublingualE2WithExplicitTheta() {
        val theta = 0.15
        val event = DoseEvent(
            route = Route.SUBLINGUAL,
            timeH = 100.0,
            doseMG = 1.0,
            ester = Ester.E2,
            extras = mapOf(DoseEvent.ExtraKey.SUBLINGUAL_THETA to theta)
        )
        
        val params = ParameterResolver.resolve(event, bodyWeight)
        
        assertEquals(CorePK.K_CLEAR, params.k3, epsilon)
        
        // 验证theta
        assertEquals(theta, params.fracFast, epsilon)
        
        // 快速吸收：舌下
        assertEquals(OralPK.K_ABS_SL, params.k1Fast, epsilon)
        
        // 慢速吸收：吞咽后口服
        assertEquals(OralPK.K_ABS_E2, params.k1Slow, epsilon)
        
        // E2无水解
        assertEquals(0.0, params.k2, epsilon)
        
        // 快速通路：完全生物利用度
        assertEquals(1.0, params.fFast, epsilon)
        
        // 慢速通路：口服生物利用度
        assertEquals(OralPK.BIOAVAILABILITY, params.fSlow, epsilon)
    }

    @Test
    fun testResolveSublingualE2WithTier() {
        val event = DoseEvent(
            route = Route.SUBLINGUAL,
            timeH = 100.0,
            doseMG = 1.0,
            ester = Ester.E2,
            extras = mapOf(DoseEvent.ExtraKey.SUBLINGUAL_TIER to 2.0)  // STANDARD
        )
        
        val params = ParameterResolver.resolve(event, bodyWeight)
        
        // 应该使用STANDARD的theta值
        val expectedTheta = SublingualTheta.recommended[SublingualTier.STANDARD]!!
        assertEquals(expectedTheta, params.fracFast, epsilon)
    }

    @Test
    fun testResolveSublingualEV() {
        val theta = 0.11
        val event = DoseEvent(
            route = Route.SUBLINGUAL,
            timeH = 100.0,
            doseMG = 2.0,
            ester = Ester.EV,
            extras = mapOf(DoseEvent.ExtraKey.SUBLINGUAL_THETA to theta)
        )
        
        val params = ParameterResolver.resolve(event, bodyWeight)
        
        assertEquals(theta, params.fracFast, epsilon)
        assertEquals(OralPK.K_ABS_SL, params.k1Fast, epsilon)
        assertEquals(OralPK.K_ABS_EV, params.k1Slow, epsilon)
        
        // EV有水解
        assertEquals(EsterPK.k2[Ester.EV]!!, params.k2, epsilon)
        
        assertEquals(1.0, params.fFast, epsilon)
        assertEquals(OralPK.BIOAVAILABILITY, params.fSlow, epsilon)
    }

    @Test
    fun testResolveGel() {
        val area = 750.0
        val event = DoseEvent(
            route = Route.GEL,
            timeH = 100.0,
            doseMG = 0.75,
            ester = Ester.E2,
            extras = mapOf(DoseEvent.ExtraKey.AREA_CM2 to area)
        )
        
        val params = ParameterResolver.resolve(event, bodyWeight)
        
        assertEquals(CorePK.K_CLEAR, params.k3, epsilon)
        
        val (expectedK1, expectedF) = TransdermalGelPK.parameters(0.75, area)
        assertEquals(expectedK1, params.k1Fast, epsilon)
        assertEquals(expectedF, params.F, epsilon)
        
        assertEquals(1.0, params.fracFast, epsilon)
        assertEquals(0.0, params.rateMGh, epsilon)
    }

    @Test
    fun testResolvePatchApplyZeroOrder() {
        val releaseRate = 50.0  // µg/day
        val event = DoseEvent(
            route = Route.PATCH_APPLY,
            timeH = 100.0,
            doseMG = 0.0,  // 对零级释放不使用
            ester = Ester.E2,
            extras = mapOf(DoseEvent.ExtraKey.RELEASE_RATE_UG_PER_DAY to releaseRate)
        )
        
        val params = ParameterResolver.resolve(event, bodyWeight)
        
        assertEquals(CorePK.K_CLEAR, params.k3, epsilon)
        
        // 验证释放速率转换
        val expectedRate = releaseRate / 24_000.0
        assertEquals(expectedRate, params.rateMGh, epsilon)
        
        assertEquals(1.0, params.F, epsilon)
        assertEquals(1.0, params.fracFast, epsilon)
    }

    @Test
    fun testResolvePatchApplyFirstOrder() {
        val event = DoseEvent(
            route = Route.PATCH_APPLY,
            timeH = 100.0,
            doseMG = 2.0,
            ester = Ester.E2
            // 没有释放速率，使用一阶模型
        )
        
        val params = ParameterResolver.resolve(event, bodyWeight)
        
        assertEquals(CorePK.K_CLEAR, params.k3, epsilon)
        assertEquals(PatchPK.GENERIC_K1, params.k1Fast, epsilon)
        assertEquals(0.0, params.rateMGh, epsilon)
        assertEquals(1.0, params.F, epsilon)
    }

    @Test
    fun testResolvePatchRemove() {
        val event = DoseEvent(
            route = Route.PATCH_REMOVE,
            timeH = 200.0,
            doseMG = 0.0,
            ester = Ester.E2
        )
        
        val params = ParameterResolver.resolve(event, bodyWeight)
        
        // 移除贴片不产生新药物
        assertEquals(0.0, params.F, epsilon)
        assertEquals(0.0, params.fracFast, epsilon)
        assertEquals(0.0, params.rateMGh, epsilon)
    }

    @Test
    fun testThetaBoundsEnforcement() {
        // 测试theta超出[0,1]范围的处理
        val event1 = DoseEvent(
            route = Route.SUBLINGUAL,
            timeH = 100.0,
            doseMG = 1.0,
            ester = Ester.E2,
            extras = mapOf(DoseEvent.ExtraKey.SUBLINGUAL_THETA to -0.5)
        )
        
        val params1 = ParameterResolver.resolve(event1, bodyWeight)
        assertTrue(params1.fracFast >= 0.0)
        assertTrue(params1.fracFast <= 1.0)
        
        val event2 = DoseEvent(
            route = Route.SUBLINGUAL,
            timeH = 100.0,
            doseMG = 1.0,
            ester = Ester.E2,
            extras = mapOf(DoseEvent.ExtraKey.SUBLINGUAL_THETA to 1.5)
        )
        
        val params2 = ParameterResolver.resolve(event2, bodyWeight)
        assertTrue(params2.fracFast >= 0.0)
        assertTrue(params2.fracFast <= 1.0)
    }
}
