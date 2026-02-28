package cn.naivetomcat.hrt_tracker.pk

import org.junit.Assert.*
import org.junit.Test

/**
 * PK参数库测试
 */
class PKParametersTest {

    @Test
    fun testCorePKConstants() {
        assertEquals(2.0, CorePK.VD_PER_KG, 0.0001)
        assertEquals(0.41, CorePK.K_CLEAR, 0.0001)
        assertEquals(0.041, CorePK.K_CLEAR_INJECTION, 0.0001)
        assertEquals(1.0, CorePK.DEPOT_K1_CORR, 0.0001)
    }

    @Test
    fun testTwoPartDepotPKFracFast() {
        assertEquals(0.90, TwoPartDepotPK.fracFast[Ester.EB]!!, 0.0001)
        assertEquals(0.40, TwoPartDepotPK.fracFast[Ester.EV]!!, 0.0001)
        assertEquals(0.229164549, TwoPartDepotPK.fracFast[Ester.EC]!!, 0.000001)
        assertEquals(0.05, TwoPartDepotPK.fracFast[Ester.EN]!!, 0.0001)
    }

    @Test
    fun testTwoPartDepotPKK1Fast() {
        assertEquals(0.144, TwoPartDepotPK.k1Fast[Ester.EB]!!, 0.0001)
        assertEquals(0.0216, TwoPartDepotPK.k1Fast[Ester.EV]!!, 0.00001)
        assertEquals(0.005035046, TwoPartDepotPK.k1Fast[Ester.EC]!!, 0.000000001)
        assertEquals(0.0010, TwoPartDepotPK.k1Fast[Ester.EN]!!, 0.00001)
    }

    @Test
    fun testTwoPartDepotPKK1Slow() {
        assertEquals(0.114, TwoPartDepotPK.k1Slow[Ester.EB]!!, 0.0001)
        assertEquals(0.0138, TwoPartDepotPK.k1Slow[Ester.EV]!!, 0.00001)
        assertEquals(0.004510574, TwoPartDepotPK.k1Slow[Ester.EC]!!, 0.000000001)
        assertEquals(0.0050, TwoPartDepotPK.k1Slow[Ester.EN]!!, 0.00001)
    }

    @Test
    fun testInjectionPKFormationFraction() {
        assertEquals(0.10922376473734707, InjectionPK.formationFraction[Ester.EB]!!, 0.000000000001)
        assertEquals(0.062258288229969413, InjectionPK.formationFraction[Ester.EV]!!, 0.000000000001)
        assertEquals(0.117255838, InjectionPK.formationFraction[Ester.EC]!!, 0.000000001)
        assertEquals(0.12, InjectionPK.formationFraction[Ester.EN]!!, 0.0001)
    }

    @Test
    fun testEsterPKK2() {
        assertEquals(0.090, EsterPK.k2[Ester.EB]!!, 0.0001)
        assertEquals(0.070, EsterPK.k2[Ester.EV]!!, 0.0001)
        assertEquals(0.045, EsterPK.k2[Ester.EC]!!, 0.0001)
        assertEquals(0.015, EsterPK.k2[Ester.EN]!!, 0.0001)
    }

    @Test
    fun testOralPKConstants() {
        assertEquals(0.32, OralPK.K_ABS_E2, 0.0001)
        assertEquals(0.05, OralPK.K_ABS_EV, 0.0001)
        assertEquals(0.03, OralPK.BIOAVAILABILITY, 0.0001)
        assertEquals(1.8, OralPK.K_ABS_SL, 0.0001)
    }

    @Test
    fun testSublingualThetaRecommended() {
        assertEquals(0.01, SublingualTheta.recommended[SublingualTier.QUICK]!!, 0.0001)
        assertEquals(0.04, SublingualTheta.recommended[SublingualTier.CASUAL]!!, 0.0001)
        assertEquals(0.11, SublingualTheta.recommended[SublingualTier.STANDARD]!!, 0.0001)
        assertEquals(0.18, SublingualTheta.recommended[SublingualTier.STRICT]!!, 0.0001)
    }

    @Test
    fun testSublingualThetaHoldMinutes() {
        assertEquals(2.0, SublingualTheta.holdMinutes[SublingualTier.QUICK]!!, 0.0001)
        assertEquals(5.0, SublingualTheta.holdMinutes[SublingualTier.CASUAL]!!, 0.0001)
        assertEquals(10.0, SublingualTheta.holdMinutes[SublingualTier.STANDARD]!!, 0.0001)
        assertEquals(15.0, SublingualTheta.holdMinutes[SublingualTier.STRICT]!!, 0.0001)
    }

    @Test
    fun testTransdermalGelPKParameters() {
        val (k1, F) = TransdermalGelPK.parameters(0.75, 750.0)
        assertEquals(0.022, k1, 0.0001)
        assertEquals(0.05, F, 0.0001)
    }

    @Test
    fun testTransdermalGelPKZeroDose() {
        val (k1, F) = TransdermalGelPK.parameters(0.0, 750.0)
        assertEquals(0.0, k1, 0.0001)
        assertEquals(0.0, F, 0.0001)
    }

    @Test
    fun testPatchPKGenericK1() {
        assertEquals(0.0075, PatchPK.GENERIC_K1, 0.0001)
    }
}
