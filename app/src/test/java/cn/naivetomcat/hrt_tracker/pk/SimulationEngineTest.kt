package cn.naivetomcat.hrt_tracker.pk

import org.junit.Assert.*
import org.junit.Test

/**
 * 模拟引擎测试
 */
class SimulationEngineTest {

    private val bodyWeight = 60.0  // kg
    private val epsilon = 1e-6

    @Test
    fun testEmptyEventList() {
        val result = SimulationEngine.runSimulation(
            events = emptyList(),
            bodyWeightKG = bodyWeight
        )
        
        assertTrue(result.timeH.isEmpty())
        assertTrue(result.concPGmL.isEmpty())
        assertEquals(0.0, result.auc, epsilon)
    }

    @Test
    fun testSingleOralE2Event() {
        val event = DoseEvent(
            route = Route.ORAL,
            timeH = 100.0,
            doseMG = 2.0,
            ester = Ester.E2
        )
        
        val result = SimulationEngine.runSimulation(
            events = listOf(event),
            bodyWeightKG = bodyWeight,
            numberOfSteps = 100
        )
        
        // 应该有100个时间点
        assertEquals(100, result.timeH.size)
        assertEquals(100, result.concPGmL.size)
        
        // 时间应该是升序的
        for (i in 1 until result.timeH.size) {
            assertTrue(result.timeH[i] > result.timeH[i - 1])
        }
        
        // 浓度应该都是非负的
        for (conc in result.concPGmL) {
            assertTrue(conc >= 0.0)
        }
        
        // AUC应该大于0
        assertTrue(result.auc > 0.0)
        
        // 第一个时间点（给药前24小时）浓度应该为0或接近0
        assertTrue(result.concPGmL.first() < 1.0)
    }

    @Test
    fun testSingleInjectionEVEvent() {
        val event = DoseEvent(
            route = Route.INJECTION,
            timeH = 100.0,
            doseMG = 5.0,
            ester = Ester.EV
        )
        
        val result = SimulationEngine.runSimulation(
            events = listOf(event),
            bodyWeightKG = bodyWeight,
            numberOfSteps = 200
        )
        
        assertEquals(200, result.timeH.size)
        assertEquals(200, result.concPGmL.size)
        
        // 注射应该产生持久的浓度曲线
        assertTrue(result.auc > 0.0)
        
        // 检查浓度是否在合理范围内
        val maxConc = result.concPGmL.maxOrNull()
        assertNotNull(maxConc)
        assertTrue(maxConc!! > 0.0)
    }

    @Test
    fun testMultipleOralEvents() {
        // 模拟每日2次口服
        val events = listOf(
            DoseEvent(
                route = Route.ORAL,
                timeH = 100.0,
                doseMG = 1.0,
                ester = Ester.E2
            ),
            DoseEvent(
                route = Route.ORAL,
                timeH = 112.0,  // 12小时后
                doseMG = 1.0,
                ester = Ester.E2
            ),
            DoseEvent(
                route = Route.ORAL,
                timeH = 124.0,  // 再12小时后
                doseMG = 1.0,
                ester = Ester.E2
            )
        )
        
        val result = SimulationEngine.runSimulation(
            events = events,
            bodyWeightKG = bodyWeight
        )
        
        assertTrue(result.timeH.isNotEmpty())
        assertTrue(result.concPGmL.isNotEmpty())
        assertTrue(result.auc > 0.0)
        
        // 多次给药应该产生比单次更高的AUC
        val singleResult = SimulationEngine.runSimulation(
            events = listOf(events.first()),
            bodyWeightKG = bodyWeight
        )
        assertTrue(result.auc > singleResult.auc)
    }

    @Test
    fun testPatchWithRemoval() {
        val events = listOf(
            DoseEvent(
                route = Route.PATCH_APPLY,
                timeH = 100.0,
                doseMG = 0.0,
                ester = Ester.E2,
                extras = mapOf(DoseEvent.ExtraKey.RELEASE_RATE_UG_PER_DAY to 50.0)
            ),
            DoseEvent(
                route = Route.PATCH_REMOVE,
                timeH = 268.0,  // 7天后移除
                doseMG = 0.0,
                ester = Ester.E2
            )
        )
        
        val result = SimulationEngine.runSimulation(
            events = events,
            bodyWeightKG = bodyWeight
        )
        
        assertTrue(result.timeH.isNotEmpty())
        assertTrue(result.concPGmL.isNotEmpty())
        assertTrue(result.auc > 0.0)
        
        // 找到移除时间点附近的索引
        val removeIdx = result.timeH.indexOfFirst { it >= 268.0 }
        if (removeIdx > 0 && removeIdx < result.timeH.size - 10) {
            // 移除后浓度应该开始下降
            val concAtRemoval = result.concPGmL[removeIdx]
            val concAfterRemoval = result.concPGmL[removeIdx + 10]
            assertTrue(concAfterRemoval <= concAtRemoval)
        }
    }

    @Test
    fun testSublingualE2() {
        val event = DoseEvent(
            route = Route.SUBLINGUAL,
            timeH = 100.0,
            doseMG = 1.0,
            ester = Ester.E2,
            extras = mapOf(DoseEvent.ExtraKey.SUBLINGUAL_TIER to 2.0)  // STANDARD
        )
        
        val result = SimulationEngine.runSimulation(
            events = listOf(event),
            bodyWeightKG = bodyWeight
        )
        
        assertTrue(result.timeH.isNotEmpty())
        assertTrue(result.concPGmL.isNotEmpty())
        assertTrue(result.auc > 0.0)
    }

    @Test
    fun testGelApplication() {
        val event = DoseEvent(
            route = Route.GEL,
            timeH = 100.0,
            doseMG = 0.75,
            ester = Ester.E2,
            extras = mapOf(DoseEvent.ExtraKey.AREA_CM2 to 750.0)
        )
        
        val result = SimulationEngine.runSimulation(
            events = listOf(event),
            bodyWeightKG = bodyWeight
        )
        
        assertTrue(result.timeH.isNotEmpty())
        assertTrue(result.concPGmL.isNotEmpty())
        assertTrue(result.auc > 0.0)
    }

    @Test
    fun testConcentrationInterpolation() {
        val event = DoseEvent(
            route = Route.ORAL,
            timeH = 100.0,
            doseMG = 2.0,
            ester = Ester.E2
        )
        
        val result = SimulationEngine.runSimulation(
            events = listOf(event),
            bodyWeightKG = bodyWeight,
            numberOfSteps = 50
        )
        
        // 测试在现有时间点的插值
        val firstTime = result.timeH.first()
        val firstConc = result.concentration(firstTime)
        assertNotNull(firstConc)
        assertEquals(result.concPGmL.first(), firstConc!!, epsilon)
        
        // 测试在两个时间点之间的插值
        if (result.timeH.size >= 2) {
            val t0 = result.timeH[0]
            val t1 = result.timeH[1]
            val midTime = (t0 + t1) / 2.0
            val midConc = result.concentration(midTime)
            assertNotNull(midConc)
            
            // 插值结果应该在两个端点之间
            val c0 = result.concPGmL[0]
            val c1 = result.concPGmL[1]
            val minConc = minOf(c0, c1)
            val maxConc = maxOf(c0, c1)
            assertTrue(midConc!! >= minConc - epsilon)
            assertTrue(midConc <= maxConc + epsilon)
        }
    }

    @Test
    fun testConcentrationInterpolationBounds() {
        val event = DoseEvent(
            route = Route.ORAL,
            timeH = 100.0,
            doseMG = 2.0,
            ester = Ester.E2
        )
        
        val result = SimulationEngine.runSimulation(
            events = listOf(event),
            bodyWeightKG = bodyWeight
        )
        
        // 测试时间范围外的插值
        val beforeStart = result.timeH.first() - 10.0
        val concBefore = result.concentration(beforeStart)
        assertNotNull(concBefore)
        assertEquals(result.concPGmL.first(), concBefore!!, epsilon)
        
        val afterEnd = result.timeH.last() + 10.0
        val concAfter = result.concentration(afterEnd)
        assertNotNull(concAfter)
        assertEquals(result.concPGmL.last(), concAfter!!, epsilon)
    }

    @Test
    fun testTimeRangeGeneration() {
        val event = DoseEvent(
            route = Route.ORAL,
            timeH = 100.0,
            doseMG = 2.0,
            ester = Ester.E2
        )
        
        val result = SimulationEngine.runSimulation(
            events = listOf(event),
            bodyWeightKG = bodyWeight
        )
        
        // 应该从给药前24小时开始
        val expectedStart = 100.0 - 24.0
        assertTrue(result.timeH.first() <= expectedStart + 0.1)
        
        // 应该延长到给药后14天
        val expectedEnd = 100.0 + 24.0 * 14
        assertTrue(result.timeH.last() >= expectedEnd - 0.1)
    }

    @Test
    fun testDifferentBodyWeights() {
        val event = DoseEvent(
            route = Route.ORAL,
            timeH = 100.0,
            doseMG = 2.0,
            ester = Ester.E2
        )
        
        // 较轻体重应该产生较高浓度
        val result50kg = SimulationEngine.runSimulation(
            events = listOf(event),
            bodyWeightKG = 50.0
        )
        
        val result80kg = SimulationEngine.runSimulation(
            events = listOf(event),
            bodyWeightKG = 80.0
        )
        
        // 相同剂量下，较轻体重的最大浓度应该更高
        val maxConc50 = result50kg.concPGmL.maxOrNull()!!
        val maxConc80 = result80kg.concPGmL.maxOrNull()!!
        assertTrue(maxConc50 > maxConc80)
    }

    @Test
    fun testInvalidParameters() {
        val event = DoseEvent(
            route = Route.ORAL,
            timeH = 100.0,
            doseMG = 2.0,
            ester = Ester.E2
        )
        
        // 测试无效步数
        val engine = SimulationEngine(
            events = listOf(event),
            bodyWeightKG = bodyWeight,
            startTimeH = 100.0,
            endTimeH = 90.0,  // 结束时间早于开始时间
            numberOfSteps = 100
        )
        
        val result = engine.run()
        assertTrue(result.timeH.isEmpty())
        assertTrue(result.concPGmL.isEmpty())
        assertEquals(0.0, result.auc, epsilon)
    }
}
