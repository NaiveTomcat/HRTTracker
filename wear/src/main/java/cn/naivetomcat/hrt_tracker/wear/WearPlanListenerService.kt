package cn.naivetomcat.hrt_tracker.wear

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import androidx.wear.tiles.TileService

class WearPlanListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents
            .filter {
                it.type == DataEvent.TYPE_CHANGED &&
                    it.dataItem.uri.path == PLANS_PATH
            }
            .forEach { event ->
                val plansJson = DataMapItem.fromDataItem(event.dataItem)
                    .dataMap
                    .getString(KEY_PLANS_JSON)
                    ?: "[]"
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val current = dataMap.getDouble(
                    KEY_CURRENT_CONCENTRATION,
                    Double.NaN
                ).takeIf { it.isFinite() }
                val curveValues = dataMap.getFloatArray(KEY_CURVE_VALUES)
                    ?: floatArrayOf()
                val updatedAt = dataMap.getLong(KEY_UPDATED_AT, 0L)
                WearPlanStore.saveDashboard(
                    this,
                    plansJson,
                    current,
                    curveValues,
                    updatedAt
                )
                Log.d(TAG, "Received plans from phone: $plansJson")
            }

        TileService.getUpdater(this)
            .requestUpdate(DoseTileService::class.java)
    }

    private companion object {
        const val TAG = "HRTWearPlanListener"
        const val PLANS_PATH = "/hrt/plans"
        const val KEY_PLANS_JSON = "plans_json"
        const val KEY_CURRENT_CONCENTRATION = "current_concentration"
        const val KEY_CURVE_VALUES = "curve_values"
        const val KEY_UPDATED_AT = "dashboard_updated_at"
    }
}
