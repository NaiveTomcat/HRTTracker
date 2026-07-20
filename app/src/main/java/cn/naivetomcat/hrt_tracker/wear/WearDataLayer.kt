package cn.naivetomcat.hrt_tracker.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import cn.naivetomcat.hrt_tracker.data.AppDatabase
import cn.naivetomcat.hrt_tracker.data.DoseEventEntity
import cn.naivetomcat.hrt_tracker.data.MedicationPlan
import cn.naivetomcat.hrt_tracker.pk.DoseEvent
import cn.naivetomcat.hrt_tracker.pk.SimulationResult
import cn.naivetomcat.hrt_tracker.widget.updateAllHRTTrackerWidgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

object WearDataLayer {
    const val PLANS_PATH = "/hrt/plans"
    const val REQUEST_PLANS_PATH = "/hrt/request-plans"
    const val DOSE_ACTIONS_PATH_PREFIX = "/hrt/dose-actions"
    const val KEY_PLANS_JSON = "plans_json"
    const val KEY_PLAN_ID = "plan_id"
    const val KEY_ACTION_ID = "action_id"
    const val KEY_RECORDED_AT = "recorded_at"
    const val KEY_CURRENT_CONCENTRATION = "current_concentration"
    const val KEY_CURVE_VALUES = "curve_values"
    const val KEY_DASHBOARD_UPDATED_AT = "dashboard_updated_at"
    private const val PREFERENCES_NAME = "wear_dashboard_cache"
    private const val KEY_CACHED_CURRENT = "cached_current"
    private const val KEY_CACHED_CURVE = "cached_curve"

    fun syncPlans(context: Context, plans: List<MedicationPlan>) {
        val preferences = context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
        val current = preferences.getString(KEY_CACHED_CURRENT, null)
            ?.toDoubleOrNull()
        val curve = preferences.getString(KEY_CACHED_CURVE, null)
            ?.split(',')
            ?.mapNotNull { it.toFloatOrNull() }
            .orEmpty()
        syncDashboard(context, plans, current, curve)
    }

    fun syncDashboard(
        context: Context,
        plans: List<MedicationPlan>,
        currentConcentration: Double?,
        curveValues: List<Float>
    ) {
        val plansJson = encodeWearPlans(plans)
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .apply {
                if (currentConcentration != null) {
                    putString(KEY_CACHED_CURRENT, currentConcentration.toString())
                } else {
                    remove(KEY_CACHED_CURRENT)
                }
                putString(KEY_CACHED_CURVE, curveValues.joinToString(","))
            }
            .apply()

        runCatching {
            val updatedAt = System.currentTimeMillis()
            val request = PutDataMapRequest.create(PLANS_PATH).apply {
                dataMap.putString(KEY_PLANS_JSON, plansJson)
                dataMap.putDouble(
                    KEY_CURRENT_CONCENTRATION,
                    currentConcentration ?: Double.NaN
                )
                dataMap.putFloatArray(
                    KEY_CURVE_VALUES,
                    curveValues.toFloatArray()
                )
                dataMap.putLong(KEY_DASHBOARD_UPDATED_AT, updatedAt)
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context)
                .putDataItem(request)
                .addOnSuccessListener {
                    Log.d(TAG, "Synced ${plans.size} plan(s) to Wear OS")
                }
                .addOnFailureListener { error ->
                    Log.w(TAG, "Unable to sync plans to Wear OS", error)
                }
        }
    }

    private const val TAG = "HRTWearDataLayer"
}

internal fun sampleWearCurve(
    simulationResult: SimulationResult?,
    currentTimeH: Double
): List<Float> {
    if (simulationResult == null) return emptyList()
    return (-12..12).mapNotNull { offsetHours ->
        simulationResult.concentration(currentTimeH + offsetHours)
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?.toFloat()
    }
}

/**
 * Receives reliable, buffered one-tap dose actions from the paired Wear OS app.
 */
class WearDoseListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != WearDataLayer.REQUEST_PLANS_PATH) return

        runBlocking(Dispatchers.IO) {
            val plans = AppDatabase.getDatabase(this@WearDoseListenerService)
                .medicationPlanDao()
                .getEnabledPlans()
                .first()
                .mapNotNull { runCatching { it.toMedicationPlan() }.getOrNull() }
                .take(2)
            WearDataLayer.syncPlans(this@WearDoseListenerService, plans)
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val changedActions = dataEvents
            .filter {
                it.type == DataEvent.TYPE_CHANGED &&
                    it.dataItem.uri.path?.startsWith(
                        WearDataLayer.DOSE_ACTIONS_PATH_PREFIX
                    ) == true
            }
            .map { it.dataItem }

        if (changedActions.isEmpty()) return

        runBlocking(Dispatchers.IO) {
            val database = AppDatabase.getDatabase(this@WearDoseListenerService)
            changedActions.forEach { item ->
                val data = DataMapItem.fromDataItem(item).dataMap
                val planId = data.getString(WearDataLayer.KEY_PLAN_ID)
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                val actionId = data.getString(WearDataLayer.KEY_ACTION_ID)
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                val recordedAt = data.getLong(
                    WearDataLayer.KEY_RECORDED_AT,
                    System.currentTimeMillis()
                )
                val plan = planId
                    ?.let { database.medicationPlanDao().getPlanById(it) }
                    ?.toMedicationPlan()
                    ?.takeIf { it.isEnabled }

                if (plan != null && actionId != null && recordedAt > 0L) {
                    database.doseEventDao().upsertEvent(
                        DoseEventEntity.fromDoseEvent(
                            createWearDoseEvent(plan, actionId, recordedAt)
                        )
                    )
                    updateAllHRTTrackerWidgets(this@WearDoseListenerService)
                }

                // The action ID is also the dose record ID, so processing is
                // idempotent even if deletion is delayed or delivery repeats.
                Wearable.getDataClient(this@WearDoseListenerService)
                    .deleteDataItems(item.uri)
            }
        }
    }
}

internal fun encodeWearPlans(plans: List<MedicationPlan>): String =
    buildJsonArray {
        plans.filter { it.isEnabled }.take(2).forEach { plan ->
            add(
                buildJsonObject {
                    put("id", plan.id.toString())
                    put("name", plan.name)
                    put("doseMG", plan.doseMG)
                }
            )
        }
    }.toString()

internal fun createWearDoseEvent(
    plan: MedicationPlan,
    actionId: UUID,
    recordedAtMillis: Long
): DoseEvent = DoseEvent(
    id = actionId,
    route = plan.route,
    timeH = recordedAtMillis / 3_600_000.0,
    doseMG = plan.doseMG,
    ester = plan.ester,
    extras = plan.extras
)
