package cn.naivetomcat.hrt_tracker.pk

import org.junit.Assert.*
import org.junit.Test

/**
 * 酯类枚举测试
 */
class EsterTest {

    @Test
    fun testE2MolecularWeight() {
        assertEquals(272.38, Ester.E2_MOLECULAR_WEIGHT, 0.01)
    }

    @Test
    fun testMolecularWeights() {
        assertEquals(272.38, Ester.E2.molecularWeight(), 0.01)
        assertEquals(376.50, Ester.EB.molecularWeight(), 0.01)
        assertEquals(356.50, Ester.EV.molecularWeight(), 0.01)
        assertEquals(396.58, Ester.EC.molecularWeight(), 0.01)
        assertEquals(384.56, Ester.EN.molecularWeight(), 0.01)
    }

    @Test
    fun testToE2Factor() {
        // E2到E2的转换因子应该为1
        assertEquals(1.0, Ester.E2.toE2Factor(), 0.0001)
        
        // 验证其他酯类的转换因子
        assertEquals(272.38 / 376.50, Ester.EB.toE2Factor(), 0.0001)
        assertEquals(272.38 / 356.50, Ester.EV.toE2Factor(), 0.0001)
        assertEquals(272.38 / 396.58, Ester.EC.toE2Factor(), 0.0001)
        assertEquals(272.38 / 384.56, Ester.EN.toE2Factor(), 0.0001)
    }

    @Test
    fun testFullNames() {
        assertEquals("Estradiol", Ester.E2.fullName())
        assertEquals("Estradiol Benzoate", Ester.EB.fullName())
        assertEquals("Estradiol Valerate", Ester.EV.fullName())
        assertEquals("Estradiol Cypionate", Ester.EC.fullName())
        assertEquals("Estradiol Enanthate", Ester.EN.fullName())
    }

    @Test
    fun testAllEstersPresent() {
        val esters = Ester.values()
        assertEquals(5, esters.size)
        assertTrue(esters.contains(Ester.E2))
        assertTrue(esters.contains(Ester.EB))
        assertTrue(esters.contains(Ester.EV))
        assertTrue(esters.contains(Ester.EC))
        assertTrue(esters.contains(Ester.EN))
    }
}
