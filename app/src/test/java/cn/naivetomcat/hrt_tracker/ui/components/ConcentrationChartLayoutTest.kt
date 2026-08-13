package cn.naivetomcat.hrt_tracker.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ConcentrationChartLayoutTest {

    @Test
    fun narrowChartReducesXAxisLabels() {
        assertEquals(
            2,
            calculateXAxisLabelCount(
                chartWidthPx = 240f,
                labelWidthPx = 96f,
                minimumGapPx = 16f
            )
        )
    }

    @Test
    fun wideChartCapsXAxisLabels() {
        assertEquals(
            6,
            calculateXAxisLabelCount(
                chartWidthPx = 1200f,
                labelWidthPx = 80f,
                minimumGapPx = 16f
            )
        )
    }

    @Test
    fun invalidDimensionsFallBackToTwoLabels() {
        assertEquals(
            2,
            calculateXAxisLabelCount(
                chartWidthPx = Float.NaN,
                labelWidthPx = 80f,
                minimumGapPx = 16f
            )
        )
    }
}
