package cn.naivetomcat.hrt_tracker.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.Wearable

private const val REQUEST_PLANS_PATH = "/hrt/request-plans"

object WearSyncManager {
    fun requestPlansFromPhone(context: Context, force: Boolean = false) {
        val nowMillis = System.currentTimeMillis()
        if (!force && !WearPlanStore.shouldRequestPlans(context, nowMillis)) {
            return
        }
        if (force) {
            WearPlanStore.markPlansRequested(context, nowMillis)
        }

        Wearable.getNodeClient(context).connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    Log.w(TAG, "No connected phone node")
                }
                nodes.forEach { node ->
                    Wearable.getMessageClient(context)
                        .sendMessage(node.id, REQUEST_PLANS_PATH, byteArrayOf())
                        .addOnSuccessListener {
                            Log.d(TAG, "Requested plans from ${node.displayName}")
                        }
                        .addOnFailureListener { error ->
                            Log.w(TAG, "Unable to request plans", error)
                        }
                }
            }
            .addOnFailureListener { error ->
                Log.w(TAG, "Unable to find connected phone", error)
            }
    }

    private const val TAG = "HRTWearSync"
}

class WearSyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        WearSyncManager.requestPlansFromPhone(appContext, force = true)
        TileService.getUpdater(appContext)
            .requestUpdate(DoseTileService::class.java)
    }
}
