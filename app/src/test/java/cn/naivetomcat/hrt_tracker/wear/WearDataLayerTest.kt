package cn.naivetomcat.hrt_tracker.wear

import cn.naivetomcat.hrt_tracker.data.MedicationPlan
import cn.naivetomcat.hrt_tracker.pk.Ester
import cn.naivetomcat.hrt_tracker.pk.Route
import cn.naivetomcat.hrt_tracker.pk.SimulationResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime
import java.util.UUID

class WearDataLayerTest {
    @Test
    fun `wear curve samples twelve hours on each side`() {
        val result = SimulationResult(
            timeH = (0..24).map(Int::toDouble),
            concPGmL = (0..24).map { it * 10.0 },
            auc = 0.0
        )

        val samples = sampleWearCurve(result, 12.0)

        assertEquals(25, samples.size)
        assertEquals(0f, samples.first())
        assertEquals(120f, samples[12])
        assertEquals(240f, samples.last())
    }


    private fun plan(
        name: String,
        enabled: Boolean = true
    ) = MedicationPlan(
        name = name,
        route = Route.ORAL,
        ester = Ester.E2,
        doseMG = 2.0,
        scheduleType = MedicationPlan.ScheduleType.DAILY,
        timeOfDay = listOf(LocalTime.of(8, 0)),
        isEnabled = enabled
    )

    @Test
    fun `wear sync contains only first two enabled plans`() {
        val encoded = encodeWearPlans(
            listOf(
                plan("第一"),
                plan("已停用", enabled = false),
                plan("第二"),
                plan("第三")
            )
        )
        val array = Json.parseToJsonElement(encoded).jsonArray

        assertEquals(2, array.size)
        assertEquals("第一", array[0].jsonObject["name"]?.jsonPrimitive?.content)
        assertEquals("第二", array[1].jsonObject["name"]?.jsonPrimitive?.content)
    }

    @Test
    fun `wear action id and tap time are preserved in dose record`() {
        val sourcePlan = plan("手表方案")
        val actionId = UUID.fromString("dc206fed-7a62-4a9f-8911-22840a1152ef")
        val recordedAt = 1_800_000_000_000L

        val event = createWearDoseEvent(sourcePlan, actionId, recordedAt)

        assertEquals(actionId, event.id)
        assertEquals(recordedAt / 3_600_000.0, event.timeH, 0.0)
        assertEquals(sourcePlan.route, event.route)
        assertEquals(sourcePlan.doseMG, event.doseMG, 0.0)
        assertEquals(sourcePlan.ester, event.ester)
    }
}
