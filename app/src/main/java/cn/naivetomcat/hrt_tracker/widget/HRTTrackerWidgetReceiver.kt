package cn.naivetomcat.hrt_tracker.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * HRT Tracker 组合微件 Receiver
 */
class HRTTrackerWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = HRTTrackerWidget()

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive action=${intent.action}")
        super.onReceive(context, intent)
    }

    override fun onEnabled(context: Context) {
        Log.i(TAG, "onEnabled")
        super.onEnabled(context)
    }

    override fun onDisabled(context: Context) {
        Log.i(TAG, "onDisabled")
        super.onDisabled(context)
    }

    companion object {
        private const val TAG = "HRTWidgetReceiver"
    }
}
