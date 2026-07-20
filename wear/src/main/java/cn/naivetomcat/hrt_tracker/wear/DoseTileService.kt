package cn.naivetomcat.hrt_tracker.wear

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.StateBuilders
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.expression.dynamicDataMapOf
import androidx.wear.protolayout.expression.mapTo
import androidx.wear.protolayout.expression.stringAppDataKey
import androidx.wear.protolayout.material3.Typography.BODY_MEDIUM
import androidx.wear.protolayout.material3.Typography.TITLE_SMALL
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textButton
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.modifiers.loadAction
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.RequestBuilders.ResourcesRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.common.util.concurrent.Futures
import java.nio.ByteBuffer
import java.util.UUID

private const val RESOURCES_VERSION_PREFIX = "6"
private const val DOSE_ACTIONS_PATH_PREFIX = "/hrt/dose-actions"
private const val CHART_RESOURCE_ID = "concentration_chart"
private const val CHART_WIDTH_PX = 240
private const val CHART_HEIGHT_PX = 72
private const val SENT_FEEDBACK_MILLIS = 1_000L
private val SELECTED_PLAN_KEY = stringAppDataKey("selected_plan_id")

class DoseTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ) = Futures.immediateFuture(
        buildTile(requestParams)
    )

    override fun onTileResourcesRequest(
        requestParams: ResourcesRequest
    ) = Futures.immediateFuture(buildResources())

    private fun buildTile(requestParams: RequestBuilders.TileRequest): Tile {
        WearSyncManager.requestPlansFromPhone(this)
        val selectedPlanId = requestParams.currentState.stateMap[SELECTED_PLAN_KEY]
        if (!selectedPlanId.isNullOrBlank()) {
            enqueueDoseAction(selectedPlanId)
            WearPlanStore.markSent(
                this,
                selectedPlanId,
                System.currentTimeMillis()
            )
            scheduleSentFeedbackClear()
        }

        val dashboard = WearPlanStore.getDashboard(this)
        val plans = dashboard.plans
        val nowMillis = System.currentTimeMillis()
        val lastSentPlanId = WearPlanStore.recentSentPlanId(
            this,
            nowMillis,
            SENT_FEEDBACK_MILLIS
        )
        val layout = materialScope(this, requestParams.deviceConfiguration) {
            primaryLayout(
                titleSlot = {
                    text(
                        dashboard.concentrationAt(nowMillis)?.let {
                            "雌二醇 %.1f pg/mL".format(it)
                        }?.layoutString ?: "雌二醇浓度".layoutString,
                        typography = TITLE_SMALL
                    )
                },
                mainSlot = {
                    val column = LayoutElementBuilders.Column.Builder()
                        .setWidth(DimensionBuilders.expand())
                        .setHorizontalAlignment(
                            LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
                        )
                    if (dashboard.curveValues.isNotEmpty()) {
                        column.addContent(
                            LayoutElementBuilders.Image.Builder()
                                .setResourceId(CHART_RESOURCE_ID)
                                .setWidth(DimensionBuilders.dp(150f))
                                .setHeight(DimensionBuilders.dp(45f))
                                .setContentScaleMode(
                                    LayoutElementBuilders.CONTENT_SCALE_MODE_FIT
                                )
                                .build()
                        )
                        column.addContent(
                            LayoutElementBuilders.Spacer.Builder()
                                .setHeight(DimensionBuilders.dp(4f))
                                .build()
                        )
                    }
                    if (plans.isEmpty()) {
                        column.addContent(
                            text(
                                "请先在手机端启用用药方案".layoutString,
                                typography = BODY_MEDIUM,
                                maxLines = 2
                            )
                        )
                    } else {
                        val buttonWidth = 80f
                        val buttonSpacing = 6f
                        val rowWidth =
                            plans.size * buttonWidth +
                                (plans.size - 1) * buttonSpacing
                        val buttonRow = LayoutElementBuilders.Row.Builder()
                            .setWidth(DimensionBuilders.dp(rowWidth))
                            .setHeight(DimensionBuilders.dp(52f))
                            .setVerticalAlignment(
                                LayoutElementBuilders.VERTICAL_ALIGN_CENTER
                            )
                        plans.forEachIndexed { index, plan ->
                            buttonRow.addContent(
                                textButton(
                                    onClick = clickable(
                                        action = loadAction(
                                            dynamicDataMapOf(
                                                SELECTED_PLAN_KEY mapTo plan.id
                                            )
                                        )
                                    ),
                                    width = DimensionBuilders.dp(buttonWidth),
                                    height = DimensionBuilders.dp(52f),
                                    labelContent = {
                                        val prefix =
                                            if (lastSentPlanId == plan.id) "✓ " else ""
                                        text(
                                            "$prefix${plan.name}\n${formatDose(plan.doseMG)}mg"
                                                .layoutString,
                                            typography = BODY_MEDIUM,
                                            maxLines = 2
                                        )
                                    }
                                )
                            )
                            if (index < plans.lastIndex) {
                                buttonRow.addContent(
                                    LayoutElementBuilders.Spacer.Builder()
                                        .setWidth(
                                            DimensionBuilders.dp(buttonSpacing)
                                        )
                                        .build()
                                )
                            }
                        }
                        column.addContent(buttonRow.build())
                    }
                    column.build()
                }
            )
        }

        return Tile.Builder()
            .setResourcesVersion(resourcesVersion(dashboard, nowMillis))
            .setFreshnessIntervalMillis(5 * 60 * 1000L)
            .setTileTimeline(Timeline.fromLayoutElement(layout))
            .setState(StateBuilders.State.Builder().build())
            .build()
    }

    private fun buildResources(): Resources {
        val dashboard = WearPlanStore.getDashboard(this)
        val nowMillis = System.currentTimeMillis()
        val builder = Resources.Builder()
            .setVersion(resourcesVersion(dashboard, nowMillis))
        if (dashboard.curveValues.isNotEmpty()) {
            builder.addIdToImageMapping(
                CHART_RESOURCE_ID,
                ResourceBuilders.ImageResource.Builder()
                    .setInlineResource(
                        ResourceBuilders.InlineImageResource.Builder()
                            .setData(
                                renderChart(
                                    dashboard.curveValues,
                                    dashboard.currentCurvePosition(nowMillis)
                                )
                            )
                            .setWidthPx(CHART_WIDTH_PX)
                            .setHeightPx(CHART_HEIGHT_PX)
                            .setFormat(ResourceBuilders.IMAGE_FORMAT_ARGB_8888)
                            .build()
                    )
                    .build()
            )
        }
        return builder.build()
    }

    private fun enqueueDoseAction(planId: String) {
        val actionId = UUID.randomUUID().toString()
        val request = PutDataMapRequest.create(
            "$DOSE_ACTIONS_PATH_PREFIX/$actionId"
        ).apply {
            dataMap.putString("plan_id", planId)
            dataMap.putString("action_id", actionId)
            dataMap.putLong("recorded_at", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        Wearable.getDataClient(this).putDataItem(request)
    }

    private fun scheduleSentFeedbackClear() {
        Handler(Looper.getMainLooper()).postDelayed(
            {
                WearPlanStore.clearSentFeedback(applicationContext)
                TileService.getUpdater(applicationContext)
                    .requestUpdate(DoseTileService::class.java)
            },
            SENT_FEEDBACK_MILLIS
        )
    }

    private fun formatDose(doseMG: Double): String =
        if (doseMG % 1.0 == 0.0) {
            doseMG.toInt().toString()
        } else {
            "%.2f".format(doseMG)
        }
}

private fun resourcesVersion(
    dashboard: WearDashboard,
    nowMillis: Long
): String =
    "$RESOURCES_VERSION_PREFIX-${dashboard.updatedAt}-${nowMillis / 300_000L}"

private fun renderChart(
    values: List<Float>,
    currentPosition: Float
): ByteArray {
    val bitmap = Bitmap.createBitmap(
        CHART_WIDTH_PX,
        CHART_HEIGHT_PX,
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.TRANSPARENT)
    val maxValue = maxOf(250f, (values.maxOrNull() ?: 0f) * 1.1f)
    val plotLeft = 30f
    val plotRight = CHART_WIDTH_PX - plotLeft
    val plotTop = 3f
    val plotBottom = CHART_HEIGHT_PX - 16f
    val plotWidth = plotRight - plotLeft
    val plotHeight = plotBottom - plotTop
    fun y(value: Float): Float =
        plotBottom -
            value.coerceIn(0f, maxValue) / maxValue * plotHeight

    val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(75, 190, 195, 215)
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }
    val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 220, 222, 235)
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(205, 207, 220)
        textSize = 9f
    }
    listOf(0f, 100f, 200f).forEach { tick ->
        val tickY = y(tick)
        canvas.drawLine(plotLeft, tickY, plotRight, tickY, gridPaint)
        labelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(
            tick.toInt().toString(),
            plotLeft - 4f,
            tickY + 3f,
            labelPaint
        )
    }
    canvas.drawLine(
        plotLeft,
        plotTop,
        plotLeft,
        plotBottom,
        axisPaint
    )
    canvas.drawLine(
        plotLeft,
        plotBottom,
        plotRight,
        plotBottom,
        axisPaint
    )

    val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(55, 155, 165, 255)
        style = Paint.Style.FILL
    }
    canvas.drawRect(
        plotLeft,
        y(200f),
        plotRight,
        y(100f),
        targetPaint
    )

    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(199, 210, 254)
        strokeWidth = 3f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val path = Path()
    values.forEachIndexed { index, value ->
        val x = if (values.size <= 1) {
            plotLeft + plotWidth / 2f
        } else {
            plotLeft + index.toFloat() / (values.size - 1) * plotWidth
        }
        if (index == 0) path.moveTo(x, y(value)) else path.lineTo(x, y(value))
    }
    canvas.drawPath(path, linePaint)

    val pointPosition = currentPosition.coerceIn(0f, 1f)
    val valuePosition = pointPosition * values.lastIndex
    val lowerIndex = valuePosition.toInt()
    val upperIndex = minOf(lowerIndex + 1, values.lastIndex)
    val ratio = valuePosition - lowerIndex
    val currentValue = values[lowerIndex] +
        (values[upperIndex] - values[lowerIndex]) * ratio
    val currentX = if (values.size <= 1) {
        plotLeft + plotWidth / 2f
    } else {
        plotLeft + pointPosition * plotWidth
    }
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(244, 180, 220)
        style = Paint.Style.FILL
        canvas.drawCircle(currentX, y(currentValue), 4.5f, this)
    }

    labelPaint.textSize = 8f
    labelPaint.textAlign = Paint.Align.LEFT
    canvas.drawText("-12h", plotLeft, CHART_HEIGHT_PX - 3f, labelPaint)
    labelPaint.textAlign = Paint.Align.CENTER
    canvas.drawText(
        "现在",
        plotLeft + plotWidth / 2f,
        CHART_HEIGHT_PX - 3f,
        labelPaint
    )
    labelPaint.textAlign = Paint.Align.RIGHT
    canvas.drawText("+12h", plotRight, CHART_HEIGHT_PX - 3f, labelPaint)

    val buffer = ByteBuffer.allocate(bitmap.byteCount)
    bitmap.copyPixelsToBuffer(buffer)
    bitmap.recycle()
    return buffer.array()
}
