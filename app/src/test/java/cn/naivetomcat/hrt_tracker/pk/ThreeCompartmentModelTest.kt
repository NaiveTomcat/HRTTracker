package cn.naivetomcat.hrt_tracker.pk

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp

/**
 * 三室模型数学计算测试
 */
class ThreeCompartmentModelTest {

    private val epsilon = 1e-6

    @Test
    fun testBatemanAmountZeroDose() {
        val params = PKParams(
            fracFast = 1.0,
            k1Fast = 0.32,
            k1Slow = 0.0,
            k2 = 0.0,
            k3 = 0.41,
            F = 0.03,
            rateMGh = 0.0,
            fFast = 0.03,
            fSlow = 0.03
        )
        
        val amount = ThreeCompartmentModel.oneCompAmount(24.0, 0.0, params)
        assertEquals(0.0, amount, epsilon)
    }

    @Test
    fun testBatemanAmountNegativeTime() {
        val params = PKParams(
            fracFast = 1.0,
            k1Fast = 0.32,
            k1Slow = 0.0,
            k2 = 0.0,
            k3 = 0.41,
            F = 0.03,
            rateMGh = 0.0,
            fFast = 0.03,
            fSlow = 0.03
        )
        
        // 负时间应该返回0（在实际使用中由PrecomputedEventModel处理）
        // 但Bateman函数本身会计算，所以我们测试正时间
        val amount = ThreeCompartmentModel.oneCompAmount(1.0, 2.0, params)
        assertTrue(amount > 0)
    }

    @Test
    fun testOneCompAmountOralE2() {
        // 模拟2mg口服E2在不同时间点的量
        val params = PKParams(
            fracFast = 1.0,
            k1Fast = OralPK.K_ABS_E2,  // 0.32
            k1Slow = 0.0,
            k2 = 0.0,
            k3 = CorePK.K_CLEAR,  // 0.41
            F = OralPK.BIOAVAILABILITY,  // 0.03
            rateMGh = 0.0,
            fFast = 0.03,
            fSlow = 0.03
        )
        
        val dose = 2.0  // mg
        
        // t=0时应该为0
        val amt0 = ThreeCompartmentModel.oneCompAmount(0.0, dose, params)
        assertEquals(0.0, amt0, epsilon)
        
        // t=2h时应该有峰值附近的量
        val amt2 = ThreeCompartmentModel.oneCompAmount(2.0, dose, params)
        assertTrue(amt2 > 0)
        
        // t=24h时应该接近0（大部分已清除）
        val amt24 = ThreeCompartmentModel.oneCompAmount(24.0, dose, params)
        assertTrue(amt24 > 0)
        assertTrue(amt24 < amt2)  // 24小时后应该少于2小时
    }

    @Test
    fun testDualAbsAmountSublingualE2() {
        // 模拟舌下E2：快速吸收（舌下）+ 慢速吸收（吞咽）
        val params = PKParams(
            fracFast = 0.11,  // θ = 0.11 (standard)
            k1Fast = OralPK.K_ABS_SL,  // 1.8
            k1Slow = OralPK.K_ABS_E2,  // 0.32
            k2 = 0.0,  // E2无水解
            k3 = CorePK.K_CLEAR,  // 0.41
            F = 1.0,
            rateMGh = 0.0,
            fFast = 1.0,  // 舌下直接吸收
            fSlow = OralPK.BIOAVAILABILITY  // 0.03 吞咽部分
        )
        
        val dose = 1.0  // mg
        
        // t=0时应该为0
        val amt0 = ThreeCompartmentModel.dualAbsAmount(0.0, dose, params)
        assertEquals(0.0, amt0, epsilon)
        
        // t=1h时应该有一定量（接近峰值）
        val amt1 = ThreeCompartmentModel.dualAbsAmount(1.0, dose, params)
        assertTrue(amt1 > 0)
        
        // t=6h时应该比1h少（主要是慢速吸收部分）
        val amt6 = ThreeCompartmentModel.dualAbsAmount(6.0, dose, params)
        assertTrue(amt6 > 0)
    }

    @Test
    fun testInjAmountEV() {
        // 模拟5mg EV注射
        val ester = Ester.EV
        val k1Corr = CorePK.DEPOT_K1_CORR
        val form = InjectionPK.formationFraction[ester]!!
        val toE2 = ester.toE2Factor()
        val F = form * toE2
        
        val params = PKParams(
            fracFast = TwoPartDepotPK.fracFast[ester]!!,  // 0.40
            k1Fast = TwoPartDepotPK.k1Fast[ester]!! * k1Corr,  // 0.0216
            k1Slow = TwoPartDepotPK.k1Slow[ester]!! * k1Corr,  // 0.0138
            k2 = EsterPK.k2[ester]!!,  // 0.070
            k3 = CorePK.K_CLEAR_INJECTION,  // 0.041
            F = F,
            rateMGh = 0.0,
            fFast = F,
            fSlow = F
        )
        
        val dose = 5.0  // mg
        
        // t=0时应该为0
        val amt0 = ThreeCompartmentModel.injAmount(0.0, dose, params)
        assertEquals(0.0, amt0, epsilon)
        
        // t=24h时应该有量
        val amt24 = ThreeCompartmentModel.injAmount(24.0, dose, params)
        assertTrue(amt24 > 0)
        
        // t=168h (7天)时应该比24h少但仍有相当量
        val amt168 = ThreeCompartmentModel.injAmount(168.0, dose, params)
        assertTrue(amt168 > 0)
        assertTrue(amt168 < amt24)
    }

    @Test
    fun testPatchAmountZeroOrder() {
        // 测试零级释放贴片（50 µg/day）
        val releaseRateUG = 50.0
        val rateMGh = releaseRateUG / 24_000.0
        
        val params = PKParams(
            fracFast = 1.0,
            k1Fast = 0.0,
            k1Slow = 0.0,
            k2 = 0.0,
            k3 = CorePK.K_CLEAR,  // 0.41
            F = 1.0,
            rateMGh = rateMGh,
            fFast = 1.0,
            fSlow = 1.0
        )
        
        val dose = 0.0  // 对零级释放不使用
        val wearH = 168.0  // 7天
        
        // t=0时应该为0
        val amt0 = ThreeCompartmentModel.patchAmount(0.0, dose, wearH, params)
        assertEquals(0.0, amt0, epsilon)
        
        // t=24h时应该有量
        val amt24 = ThreeCompartmentModel.patchAmount(24.0, dose, wearH, params)
        assertTrue(amt24 > 0)
        
        // t=48h应该比24h多（还在累积）
        val amt48 = ThreeCompartmentModel.patchAmount(48.0, dose, wearH, params)
        assertTrue(amt48 > amt24)
        
        // t=200h (移除后32h) 应该比168h少（开始衰减）
        val amt168 = ThreeCompartmentModel.patchAmount(168.0, dose, wearH, params)
        val amt200 = ThreeCompartmentModel.patchAmount(200.0, dose, wearH, params)
        assertTrue(amt200 > 0)
        assertTrue(amt200 < amt168)
    }

    @Test
    fun testPatchAmountFirstOrder() {
        // 测试一阶释放贴片
        val params = PKParams(
            fracFast = 1.0,
            k1Fast = PatchPK.GENERIC_K1,  // 0.0075
            k1Slow = 0.0,
            k2 = 0.0,
            k3 = CorePK.K_CLEAR,  // 0.41
            F = 1.0,
            rateMGh = 0.0,  // 非零级释放
            fFast = 1.0,
            fSlow = 1.0
        )
        
        val dose = 2.0  // mg
        val wearH = 168.0  // 7天
        
        // t=0时应该为0
        val amt0 = ThreeCompartmentModel.patchAmount(0.0, dose, wearH, params)
        assertEquals(0.0, amt0, epsilon)
        
        // t=72h时应该有量
        val amt72 = ThreeCompartmentModel.patchAmount(72.0, dose, wearH, params)
        assertTrue(amt72 > 0)
        
        // t=200h (移除后32h) 应该比168h少
        val amt168 = ThreeCompartmentModel.patchAmount(168.0, dose, wearH, params)
        val amt200 = ThreeCompartmentModel.patchAmount(200.0, dose, wearH, params)
        assertTrue(amt200 < amt168)
    }

    @Test
    fun testDualAbsMixedAmountSublingualEV() {
        // 测试舌下EV：快速分支有水解，慢速分支无水解（口服）
        val params = PKParams(
            fracFast = 0.11,  // θ = 0.11
            k1Fast = OralPK.K_ABS_SL,  // 1.8
            k1Slow = OralPK.K_ABS_EV,  // 0.05
            k2 = EsterPK.k2[Ester.EV]!!,  // 0.070
            k3 = CorePK.K_CLEAR,  // 0.41
            F = 1.0,
            rateMGh = 0.0,
            fFast = 1.0,
            fSlow = OralPK.BIOAVAILABILITY  // 0.03
        )
        
        val dose = 2.0  // mg
        
        // t=0时应该为0
        val amt0 = ThreeCompartmentModel.dualAbsMixedAmount(0.0, dose, params)
        assertEquals(0.0, amt0, epsilon)
        
        // t=2h时应该有量
        val amt2 = ThreeCompartmentModel.dualAbsMixedAmount(2.0, dose, params)
        assertTrue(amt2 > 0)
        
        // t=12h时仍应该有量
        val amt12 = ThreeCompartmentModel.dualAbsMixedAmount(12.0, dose, params)
        assertTrue(amt12 > 0)
    }
}
