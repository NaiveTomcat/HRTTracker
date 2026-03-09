package cn.naivetomcat.hrt_tracker.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * HRT Tracker 组合微件 Receiver
 */
class HRTTrackerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HRTTrackerWidget()
}
