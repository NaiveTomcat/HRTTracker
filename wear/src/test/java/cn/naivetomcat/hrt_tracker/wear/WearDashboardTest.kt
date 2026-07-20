package cn.naivetomcat.hrt_tracker.wear

import org.junit.Assert.assertEquals
import org.junit.Test

class WearDashboardTest {
    @Test
    fun `concentration advances along hourly curve`() {
        val dashboard = WearDashboard(
            plans = emptyList(),
            currentConcentration = 20.0,
            curveValues = listOf(0f, 10f, 20f, 30f, 40f),
            updatedAt = 1_000L
        )

        assertEquals(20.0, dashboard.concentrationAt(1_000L)!!, 0.001)
        assertEquals(
            25.0,
            dashboard.concentrationAt(1_000L + 30 * 60_000L)!!,
            0.001
        )
        assertEquals(
            30.0,
            dashboard.concentrationAt(1_000L + 60 * 60_000L)!!,
            0.001
        )
    }
}
