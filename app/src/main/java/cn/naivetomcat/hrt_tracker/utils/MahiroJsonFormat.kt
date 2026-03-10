package cn.naivetomcat.hrt_tracker.utils

import cn.naivetomcat.hrt_tracker.pk.DoseEvent
import cn.naivetomcat.hrt_tracker.pk.Ester
import cn.naivetomcat.hrt_tracker.pk.Route
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.Instant
import java.util.UUID

/**
 * 从 hrt.mahiro.uk 导入时解析出的数据
 */
data class MahiroImportData(
    val weight: Double?,
    val events: List<DoseEvent>
)

/**
 * hrt.mahiro.uk JSON 格式解析与生成工具
 *
 * 对应 JSON 格式示例：
 * ```json
 * {
 *   "meta": { "version": 1, "exportedAt": "..." },
 *   "weight": 55,
 *   "events": [
 *     {
 *       "id": "uuid",
 *       "route": "sublingual",
 *       "ester": "E2",
 *       "timeH": 492244,
 *       "doseMG": 2,
 *       "extras": { "sublingualTier": 1 }
 *     }
 *   ],
 *   "labResults": [],
 *   "doseTemplates": []
 * }
 * ```
 */
object MahiroJsonFormat {

    private val prettyJson = Json { prettyPrint = true }

    private val ROUTE_FROM_JSON = mapOf(
        "injection" to Route.INJECTION,
        "oral" to Route.ORAL,
        "sublingual" to Route.SUBLINGUAL,
        "gel" to Route.GEL,
        "patch_apply" to Route.PATCH_APPLY,
        "patch_remove" to Route.PATCH_REMOVE,
        "antiandrogen" to Route.ANTIANDROGEN
    )

    /** 本应用的 Route 枚举 → mahiro JSON 中的 route 字符串 */
    private val ROUTE_TO_JSON = ROUTE_FROM_JSON.entries.associate { (k, v) -> v to k }

    /** mahiro JSON 中的 extras 键名 → 本应用的 ExtraKey 枚举 */
    private val EXTRA_KEY_FROM_JSON = mapOf(
        "sublingualTier" to DoseEvent.ExtraKey.SUBLINGUAL_TIER,
        "sublingualTheta" to DoseEvent.ExtraKey.SUBLINGUAL_THETA,
        "concentrationMgMl" to DoseEvent.ExtraKey.CONCENTRATION_MG_ML,
        "areaCm2" to DoseEvent.ExtraKey.AREA_CM2,
        "releaseRateUgPerDay" to DoseEvent.ExtraKey.RELEASE_RATE_UG_PER_DAY,
        "antiAndrogenType" to DoseEvent.ExtraKey.ANTI_ANDROGEN_TYPE
    )

    /** 本应用的 ExtraKey 枚举 → mahiro JSON 中的 extras 键名 */
    private val EXTRA_KEY_TO_JSON = EXTRA_KEY_FROM_JSON.entries.associate { (k, v) -> v to k }

    /**
     * 解析 mahiro JSON 格式的字符串，返回体重和用药事件列表。
     * 无法解析的事件会被跳过并记录警告日志。
     *
     * @throws Exception 若 JSON 格式不合法
     */
    fun parseImport(jsonContent: String): MahiroImportData {
        val root = Json.parseToJsonElement(jsonContent).jsonObject
        val weight = root["weight"]?.jsonPrimitive?.doubleOrNull
        val eventsArray = root["events"]?.jsonArray ?: JsonArray(emptyList())

        val events = eventsArray.mapNotNull { element ->
            parseEvent(element)
        }

        return MahiroImportData(weight = weight, events = events)
    }

    private fun parseEvent(element: JsonElement): DoseEvent? {
        return try {
            val obj = element.jsonObject

            val idStr = obj["id"]?.jsonPrimitive?.content
            val id = if (idStr != null) {
                try { UUID.fromString(idStr) } catch (e: IllegalArgumentException) { UUID.randomUUID() }
            } else {
                UUID.randomUUID()
            }

            val routeStr = obj["route"]?.jsonPrimitive?.content ?: return null
            val route = ROUTE_FROM_JSON[routeStr] ?: return null

            val esterStr = obj["ester"]?.jsonPrimitive?.content ?: return null
            val ester = try {
                Ester.valueOf(esterStr)
            } catch (e: IllegalArgumentException) {
                return null
            }

            val timeH = obj["timeH"]?.jsonPrimitive?.doubleOrNull ?: return null
            val doseMG = obj["doseMG"]?.jsonPrimitive?.doubleOrNull ?: return null

            val extrasObj = obj["extras"]?.jsonObject ?: JsonObject(emptyMap())
            val extras = extrasObj.entries.mapNotNull { (key, value) ->
                val extraKey = EXTRA_KEY_FROM_JSON[key] ?: return@mapNotNull null
                val extraValue = value.jsonPrimitive.doubleOrNull ?: return@mapNotNull null
                extraKey to extraValue
            }.toMap()

            DoseEvent(
                id = id,
                route = route,
                timeH = timeH,
                doseMG = doseMG,
                ester = ester,
                extras = extras
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 将体重和用药事件列表序列化为 mahiro JSON 格式字符串。
     */
    fun generateExport(weight: Double, events: List<DoseEvent>): String {
        val root = buildJsonObject {
            putJsonObject("meta") {
                put("version", 1)
                put("exportedAt", Instant.now().toString())
            }
            put("weight", weight)
            putJsonArray("events") {
                events.forEach { event ->
                    addJsonObject {
                        put("id", event.id.toString())
                        put("route", ROUTE_TO_JSON[event.route] ?: event.route.name.lowercase())
                        put("ester", event.ester.name)
                        put("timeH", event.timeH)
                        put("doseMG", event.doseMG)
                        putJsonObject("extras") {
                            event.extras.forEach { (key, value) ->
                                val jsonKey = EXTRA_KEY_TO_JSON[key] ?: key.name.lowercase()
                                put(jsonKey, value)
                            }
                        }
                    }
                }
            }
            putJsonArray("labResults") {}
            putJsonArray("doseTemplates") {}
        }
        return prettyJson.encodeToString(root)
    }
}
