package cn.naivetomcat.hrt_tracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import cn.naivetomcat.hrt_tracker.R
import cn.naivetomcat.hrt_tracker.pk.SimulationResult
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.compose.ui.platform.LocalLocale

private const val MAX_X_AXIS_LABEL_COUNT = 6
private const val GAHT_TARGET_MIN_PG_ML = 100.0
private const val GAHT_TARGET_MAX_PG_ML = 200.0

/**
 * Returns the number of X-axis labels that fit without overlapping.
 *
 * Keeping this calculation independent from Compose drawing makes the narrow-screen
 * behavior deterministic and easy to cover with unit tests.
 */
internal fun calculateXAxisLabelCount(
    chartWidthPx: Float,
    labelWidthPx: Float,
    minimumGapPx: Float
): Int {
    if (!chartWidthPx.isFinite() || !labelWidthPx.isFinite() || !minimumGapPx.isFinite() ||
        chartWidthPx <= 0f || labelWidthPx < 0f || minimumGapPx < 0f
    ) {
        return 2
    }

    val slotWidth = (labelWidthPx + minimumGapPx).coerceAtLeast(1f)
    return (chartWidthPx / slotWidth)
        .toInt()
        .coerceIn(2, MAX_X_AXIS_LABEL_COUNT)
}

private fun createStarPath(
    center: Offset,
    outerRadius: Float,
    innerRadius: Float
): Path = Path().apply {
    repeat(10) { index ->
        val radius = if (index % 2 == 0) outerRadius else innerRadius
        val angle = -PI / 2.0 + index * PI / 5.0
        val point = Offset(
            x = center.x + (cos(angle) * radius).toFloat(),
            y = center.y + (sin(angle) * radius).toFloat()
        )
        if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
    }
    close()
}

/**
 * 雌二醇浓度图表组件
 * 使用 Canvas 绘制交互式折线图
 * 
 * 功能：
 * - 支持手势缩放和拖动
 * - 显示坐标轴和网格线
 * - 标记给药时间点和当前时刻
 * - 同时显示基线曲线（无计划）和计划曲线（有计划）
 *
 * @param simulationResult 完整模拟结果（历史+未来计划）
 * @param baselineSimulationResult 基线模拟结果（仅历史，不考虑未来计划）
 * @param currentTimeH 当前时刻（小时）
 * @param doseTimePoints 给药时间点列表（小时）
 * @param forkPointTimeH 分叉点时间（未来第一次计划用药时间），此时刻后主曲线转为计划曲线
 * @param modifier Modifier
 */
@Composable
fun ConcentrationChart(
    simulationResult: SimulationResult,
    baselineSimulationResult: SimulationResult?,
    currentTimeH: Double,
    doseTimePoints: List<Double>,
    forkPointTimeH: Double? = null,
    is24Hour: Boolean = true,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val surfaceContainerColor = MaterialTheme.colorScheme.surfaceContainer
    val errorColor = MaterialTheme.colorScheme.error
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val textMeasurer = rememberTextMeasurer()

    // 触摸交互状态
    var touchPosition by remember { mutableStateOf<Offset?>(null) }
    var selectedPoint by remember { mutableStateOf<Pair<Double, Double>?>(null) } // (time, conc)

    // 数据范围
    val timeMin = simulationResult.timeH.minOrNull() ?: 0.0
    val timeMax = simulationResult.timeH.maxOrNull() ?: 1.0
    val totalTimeRange = timeMax - timeMin
    
    // 计算默认显示范围：当前时刻-24小时到+12小时（共36小时）
    val defaultViewStart = currentTimeH - 24.0
    val defaultViewEnd = currentTimeH + 12.0
    val defaultViewRange = defaultViewEnd - defaultViewStart // 36小时
    
    // 计算默认缩放级别
    val initialScale = if (totalTimeRange > 0 && defaultViewRange > 0) {
        (totalTimeRange / defaultViewRange).toFloat().coerceIn(1f, 50f)
    } else 1f

    // 缩放和平移状态（仅针对时间轴）
    var scaleX by remember { mutableFloatStateOf(initialScale) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var isInitialized by remember { mutableStateOf(false) }
    
    // 仅当模拟结果改变时重置初始化状态（不包括 currentTimeH 变化）
    LaunchedEffect(simulationResult) {
        scaleX = initialScale
        offsetX = 0f
        isInitialized = false
    }

    if (simulationResult.timeH.isEmpty() || simulationResult.concPGmL.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.chart_no_data), color = onSurfaceColor)
        }
        return
    }

    // Y轴刻度使用25的倍数
    val rawConcMax = maxOf(
        simulationResult.concPGmL.maxOrNull() ?: 100.0,
        GAHT_TARGET_MAX_PG_ML
    ) * 1.1
    val concMin = 0.0
    val concMax = kotlin.math.ceil(rawConcMax / 25.0) * 25.0

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val paddingLeft = 60.dp.toPx()
                        val paddingRight = 20.dp.toPx()
                        val paddingBottom = 50.dp.toPx()
                        val chartWidth = size.width - paddingLeft - paddingRight
                        val chartLeft = paddingLeft
                        val chartBottom = size.height - paddingBottom - 20.dp.toPx()
                        
                        // 检查手势是否在X轴区域（底部坐标轴区域）
                        val isInXAxisArea = centroid.y >= chartBottom
                        
                        // 只允许 X 轴方向的缩放（最大50倍）
                        val oldScaleX = scaleX
                        val newScaleX = (scaleX * zoom).coerceIn(1f, 50f)
                        
                        // 如果发生了缩放，调整 offsetX 使得缩放中心保持不变
                        if (newScaleX != oldScaleX && zoom != 1f) {
                            // 缩放中心相对于图表区域的位置
                            val focusX = (centroid.x - chartLeft).coerceIn(0f, chartWidth)
                            // 缩放前，焦点对应的归一化位置
                            val normalizedPos = (focusX - offsetX) / (chartWidth * oldScaleX)
                            // 缩放后，调整 offset 使得相同归一化位置仍在焦点处
                            offsetX = focusX - normalizedPos * chartWidth * newScaleX
                        }
                        
                        scaleX = newScaleX
                        
                        // 只有在X轴区域才允许平移
                        if (isInXAxisArea) {
                            offsetX += pan.x
                        }
                        
                        // 限制 offsetX 范围
                        val maxOffset = chartWidth * (scaleX - 1f)
                        offsetX = offsetX.coerceIn(-maxOffset, 0f)
                        
                        // 清除触摸选中状态
                        touchPosition = null
                        selectedPoint = null
                    }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val paddingLeft = 60.dp.toPx()
                        val paddingRight = 20.dp.toPx()
                        val paddingTop = 20.dp.toPx()
                        val paddingBottom = 50.dp.toPx()
                        val chartWidth = size.width - paddingLeft - paddingRight
                        val chartHeight = size.height - paddingTop - paddingBottom
                        val chartLeft = paddingLeft
                        val chartTop = paddingTop
                        val chartRight = chartLeft + chartWidth
                        val chartBottom = chartTop + chartHeight
                        
                        // 检查是否在图表区域内
                        if (down.position.x >= chartLeft && down.position.x <= chartRight &&
                            down.position.y >= chartTop && down.position.y <= chartBottom) {
                            
                            // 更新选中点的函数
                            fun updateSelectedPoint(offset: Offset) {
                                touchPosition = offset
                                
                                // 找到最近的数据点
                                val touchX = offset.x
                                val normalizedX = (touchX - chartLeft - offsetX) / (chartWidth * scaleX)
                                val touchTime = timeMin + normalizedX * (timeMax - timeMin)
                                
                                var closestIndex = 0
                                var minDistance = Double.MAX_VALUE
                                
                                simulationResult.timeH.forEachIndexed { index, time ->
                                    val distance = abs(time - touchTime)
                                    if (distance < minDistance) {
                                        minDistance = distance
                                        closestIndex = index
                                    }
                                }
                                
                                if (closestIndex < simulationResult.timeH.size) {
                                    selectedPoint = Pair(
                                        simulationResult.timeH[closestIndex],
                                        simulationResult.concPGmL[closestIndex]
                                    )
                                }
                            }
                            
                            // 初始选中点
                            updateSelectedPoint(down.position)
                            
                            // 跟踪拖动
                            drag(down.id) { change ->
                                val currentPos = change.position
                                // 限制在图表区域内
                                if (currentPos.x >= chartLeft && currentPos.x <= chartRight &&
                                    currentPos.y >= chartTop && currentPos.y <= chartBottom) {
                                    updateSelectedPoint(currentPos)
                                    change.consume()
                                }
                            }
                            
                            // 松开后清除选中
                            touchPosition = null
                            selectedPoint = null
                        }
                    }
                }
        ) {
        val paddingLeft = 60.dp.toPx()
        val paddingRight = 20.dp.toPx()
        val paddingTop = 20.dp.toPx()
        val paddingBottom = 50.dp.toPx()
        val chartWidth = size.width - paddingLeft - paddingRight
        val chartHeight = size.height - paddingTop - paddingBottom
        val chartLeft = paddingLeft
        val chartTop = paddingTop
        val chartRight = chartLeft + chartWidth
        val chartBottom = chartTop + chartHeight

        // 首次初始化偏移量，使得默认显示范围居中
        if (!isInitialized && totalTimeRange > 0) {
            val normalizedStart = ((defaultViewStart - timeMin) / totalTimeRange).toFloat()
            offsetX = -normalizedStart * chartWidth * scaleX
            // 限制在有效范围内
            val maxOffset = chartWidth * (scaleX - 1f)
            offsetX = offsetX.coerceIn(-maxOffset, 0f)
            isInitialized = true
        }

        // 限制绘制区域在图表框内
        clipRect(
            left = chartLeft,
            top = chartTop,
            right = chartRight,
            bottom = chartBottom
        ) {
            val targetBandTop = chartBottom -
                chartHeight * ((GAHT_TARGET_MAX_PG_ML - concMin) / (concMax - concMin)).toFloat()
            val targetBandBottom = chartBottom -
                chartHeight * ((GAHT_TARGET_MIN_PG_ML - concMin) / (concMax - concMin)).toFloat()

            drawRect(
                color = primaryColor.copy(alpha = 0.12f),
                topLeft = Offset(chartLeft, targetBandTop),
                size = Size(chartWidth, targetBandBottom - targetBandTop)
            )
            drawLine(
                color = primaryColor.copy(alpha = 0.38f),
                start = Offset(chartLeft, targetBandTop),
                end = Offset(chartRight, targetBandTop),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = primaryColor.copy(alpha = 0.38f),
                start = Offset(chartLeft, targetBandBottom),
                end = Offset(chartRight, targetBandBottom),
                strokeWidth = 1.dp.toPx()
            )

            // 确定分叉点时间（优先使用传入的 forkPointTimeH，否则使用当前时刻）
            val forkTime = forkPointTimeH ?: currentTimeH
            
            // 计算分叉点处的浓度值（线性插值）
            var forkPointConc = 0.0
            var forkPointFound = false
            for (i in 0 until simulationResult.timeH.size - 1) {
                val time1 = simulationResult.timeH[i]
                val time2 = simulationResult.timeH[i + 1]
                
                if (time1 <= forkTime && time2 >= forkTime) {
                    // 线性插值计算分叉点处的浓度
                    val conc1 = simulationResult.concPGmL[i]
                    val conc2 = simulationResult.concPGmL[i + 1]
                    val ratio = if (time2 != time1) (forkTime - time1) / (time2 - time1) else 0.0
                    forkPointConc = conc1 + (conc2 - conc1) * ratio
                    forkPointFound = true
                    break
                }
            }
            
            // 如果没找到插值点，使用最近的点
            if (!forkPointFound) {
                simulationResult.timeH.forEachIndexed { index, time ->
                    if ((index == 0 || abs(time - forkTime) < abs(simulationResult.timeH[index - 1] - forkTime)) &&
                        index < simulationResult.concPGmL.size) {
                        forkPointConc = simulationResult.concPGmL[index]
                    }
                }
            }
            
            // 计算分叉点的屏幕坐标
            val forkNormalizedTime = ((forkTime - timeMin) / (timeMax - timeMin)).toFloat()
            val forkScreenX = chartLeft + forkNormalizedTime * chartWidth * scaleX + offsetX
            val forkScreenY = chartBottom - chartHeight * ((forkPointConc - concMin) / (concMax - concMin)).toFloat()
            
            // 绘制基线曲线（分叉点之后未用药）- primary虚线
            baselineSimulationResult?.let { baseline ->
                val baselinePath = Path()
                var isFirst = true
                baseline.timeH.forEachIndexed { index, time ->
                    // 只绘制分叉点之后的部分
                    if (time >= forkTime) {
                        val conc = baseline.concPGmL[index]
                        val normalizedTime = ((time - timeMin) / (timeMax - timeMin)).toFloat()
                        val x = chartLeft + normalizedTime * chartWidth * scaleX + offsetX
                        val y = chartBottom - chartHeight * ((conc - concMin) / (concMax - concMin)).toFloat()
                        
                        if (isFirst) {
                            // 从分叉点开始
                            baselinePath.moveTo(forkScreenX, forkScreenY)
                            baselinePath.lineTo(x, y)
                            isFirst = false
                        } else {
                            baselinePath.lineTo(x, y)
                        }
                    }
                }
                
                // 绘制基线未来曲线（使用 primary color 50% 不透明实线）
                drawPath(
                    path = baselinePath,
                    color = primaryColor.copy(alpha = 0.5f),
                    style = Stroke(
                        width = 2.5.dp.toPx()
                    )
                )
            }
            
            // 绘制主曲线 - 分段绘制（分叉点前后使用不同颜色）
            // 1. 分叉点之前的部分（历史 + 第一次未来用药之前）- primary实线
            val pathBefore = Path()
            var hasMovedBefore = false
            simulationResult.timeH.forEachIndexed { index, time ->
                if (time < forkTime) {  // 严格小于分叉点时间
                    val conc = simulationResult.concPGmL[index]
                    val normalizedTime = ((time - timeMin) / (timeMax - timeMin)).toFloat()
                    val x = chartLeft + normalizedTime * chartWidth * scaleX + offsetX
                    val y = chartBottom - chartHeight * ((conc - concMin) / (concMax - concMin)).toFloat()
                    
                    if (!hasMovedBefore) {
                        pathBefore.moveTo(x, y)
                        hasMovedBefore = true
                    } else {
                        pathBefore.lineTo(x, y)
                    }
                }
            }
            
            // 线段延伸到分叉点
            if (forkTime > timeMin && forkTime < timeMax) {
                if (hasMovedBefore) {
                    pathBefore.lineTo(forkScreenX, forkScreenY)
                } else {
                    // 如果没有数据点在分叉点之前，从分叉点开始
                    pathBefore.moveTo(forkScreenX, forkScreenY)
                }
            }
            
            drawPath(
                path = pathBefore,
                color = primaryColor,
                style = Stroke(width = 2.5.dp.toPx())
            )
            
            // 2. 分叉点之后的部分（按计划用药）- tertiary虚线
            val pathAfter = Path()
            var startedAfter = false
            
            // 首先从分叉点开始
            if (forkTime > timeMin && forkTime < timeMax) {
                pathAfter.moveTo(forkScreenX, forkScreenY)
                startedAfter = true
            }
            
            simulationResult.timeH.forEachIndexed { index, time ->
                if (time > forkTime) {  // 严格大于分叉点时间
                    val conc = simulationResult.concPGmL[index]
                    val normalizedTime = ((time - timeMin) / (timeMax - timeMin)).toFloat()
                    val x = chartLeft + normalizedTime * chartWidth * scaleX + offsetX
                    val y = chartBottom - chartHeight * ((conc - concMin) / (concMax - concMin)).toFloat()
                    
                    if (!startedAfter) {
                        pathAfter.moveTo(x, y)
                        startedAfter = true
                    } else {
                        pathAfter.lineTo(x, y)
                    }
                }
            }
            
            drawPath(
                path = pathAfter,
                color = tertiaryColor.copy(alpha = 0.5f),
                style = Stroke(
                    width = 2.5.dp.toPx()
                )
            )

            // 在曲线上用星形标记实际给药记录。
            doseTimePoints.forEach { doseTime ->
                if (doseTime >= timeMin && doseTime <= timeMax) {
                    val normalizedTime = ((doseTime - timeMin) / (timeMax - timeMin)).toFloat()
                    val x = chartLeft + normalizedTime * chartWidth * scaleX + offsetX
                    val concentration = simulationResult.concentration(doseTime)
                    if (x >= chartLeft && x <= chartRight && concentration != null) {
                        val y = chartBottom -
                            chartHeight * ((concentration - concMin) / (concMax - concMin)).toFloat()
                        val starPath = createStarPath(
                            center = Offset(x, y),
                            outerRadius = 8.dp.toPx(),
                            innerRadius = 3.6.dp.toPx()
                        )
                        drawPath(
                            path = starPath,
                            color = errorColor.copy(alpha = 0.95f)
                        )
                        drawPath(
                            path = starPath,
                            color = surfaceColor,
                            style = Stroke(
                                width = 1.5.dp.toPx(),
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }

            // 标记当前时刻（用大圆点）
            if (currentTimeH >= timeMin && currentTimeH <= timeMax) {
                val normalizedTime = ((currentTimeH - timeMin) / (timeMax - timeMin)).toFloat()
                val x = chartLeft + normalizedTime * chartWidth * scaleX + offsetX
                
                // 找到当前时刾的浓度
                var currentConc = 0.0
                var minTimeDiff = Double.MAX_VALUE
                simulationResult.timeH.forEachIndexed { index, time ->
                    val diff = abs(time - currentTimeH)
                    if (diff < minTimeDiff) {
                        minTimeDiff = diff
                        currentConc = simulationResult.concPGmL[index]
                    }
                }
                
                val y = chartBottom - chartHeight * ((currentConc - concMin) / (concMax - concMin)).toFloat()
                
                if (x >= chartLeft && x <= chartRight) {
                    // 绘制外圆（白色边框）
                    drawCircle(
                        color = surfaceColor,
                        radius = 8.dp.toPx(),
                        center = Offset(x, y)
                    )
                    // 绘制内圆（tertiary 颜色）
                    drawCircle(
                        color = tertiaryColor,
                        radius = 6.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }
            
            // 绘制选中点的标记
            selectedPoint?.let { (time, conc) ->
                val normalizedTime = ((time - timeMin) / (timeMax - timeMin)).toFloat()
                val x = chartLeft + normalizedTime * chartWidth * scaleX + offsetX
                val y = chartBottom - chartHeight * ((conc - concMin) / (concMax - concMin)).toFloat()
                
                if (x >= chartLeft && x <= chartRight) {
                    // 绘制高亮圆点
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.3f),
                        radius = 12.dp.toPx(),
                        center = Offset(x, y)
                    )
                    drawCircle(
                        color = primaryColor,
                        radius = 5.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }
        }
        
        // 绘制网格和坐标轴（不受clipRect限制）
        val gridColor = onSurfaceColor.copy(alpha = 0.15f)
        
        // 垂直网格线（时间）
        for (i in 0..10) {
            val x = chartLeft + chartWidth * i / 10
            drawLine(
                color = gridColor,
                start = Offset(x, chartTop),
                end = Offset(x, chartBottom),
                strokeWidth = 1.5.dp.toPx()
            )
        }
        
        // 水平网格线（浓度）- 只使用25的倍数，最多6个刻度
        val idealStep = concMax / 5.0  // 确保最多6个刻度（包括0）
        val yStep = kotlin.math.ceil(idealStep / 25.0) * 25.0  // 向上取整到25的倍数
        val yStepCount = (concMax / yStep).toInt()
        
        for (i in 0..yStepCount) {
            val concValue = i * yStep
            if (concValue > concMax) break
            
            val normalizedY = (concValue - concMin) / (concMax - concMin)
            val y = chartBottom - chartHeight * normalizedY.toFloat()
            
            drawLine(
                color = gridColor,
                start = Offset(chartLeft, y),
                end = Offset(chartRight, y),
                strokeWidth = 1.5.dp.toPx()
            )
            
            // Y轴刻度标签
            val text = "%.0f".format(concValue)
            val textLayoutResult = textMeasurer.measure(
                text = text,
                style = TextStyle(
                    color = onSurfaceColor,
                    fontSize = 11.sp
                )
            )
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(
                    chartLeft - textLayoutResult.size.width - 8.dp.toPx(),
                    y - textLayoutResult.size.height / 2
                )
            )
        }

        // 绘制坐标轴
        drawLine(
            color = onSurfaceColor,
            start = Offset(chartLeft, chartTop),
            end = Offset(chartLeft, chartBottom),
            strokeWidth = 2.dp.toPx()
        )
        drawLine(
            color = onSurfaceColor,
            start = Offset(chartLeft, chartBottom),
            end = Offset(chartRight, chartBottom),
            strokeWidth = 2.dp.toPx()
        )

        // X轴标签
        val dateFormat = SimpleDateFormat(
            if (is24Hour) "MM/dd HH:mm" else "MM/dd hh:mm a",
            Locale.getDefault()
        )
        // 计算可见时间范围
        val visibleTimeStart = timeMin - (offsetX / (chartWidth * scaleX)) * (timeMax - timeMin)
        val visibleTimeEnd = visibleTimeStart + (timeMax - timeMin) / scaleX

        fun formatXAxisLabel(timeH: Double): String {
            val timeMillis = (timeH * 3600000).toLong()
            return dateFormat.format(Date(timeMillis)).replaceFirst(" ", "\n")
        }

        val xAxisTextStyle = TextStyle(
            color = onSurfaceColor,
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        )
        val widestEdgeLabel = listOf(visibleTimeStart, visibleTimeEnd)
            .map { time ->
                textMeasurer.measure(
                    text = formatXAxisLabel(time),
                    style = xAxisTextStyle
                )
            }
            .maxBy { it.size.width }
        val xAxisLabelCount = calculateXAxisLabelCount(
            chartWidthPx = chartWidth,
            labelWidthPx = widestEdgeLabel.size.width.toFloat(),
            minimumGapPx = 12.dp.toPx()
        )

        for (i in 0 until xAxisLabelCount) {
            val fraction = i.toDouble() / (xAxisLabelCount - 1)
            val timeValue = visibleTimeStart + (visibleTimeEnd - visibleTimeStart) * fraction
            // 从时间值计算屏幕坐标
            val normalizedPos = ((timeValue - timeMin) / (timeMax - timeMin)).toFloat()
            val x = chartLeft + normalizedPos * chartWidth * scaleX + offsetX
            
            if (x >= chartLeft && x <= chartRight) {
                val textLayoutResult = textMeasurer.measure(
                    text = formatXAxisLabel(timeValue),
                    style = xAxisTextStyle
                )
                val maxLabelLeft = (size.width - textLayoutResult.size.width).coerceAtLeast(0f)
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(
                        (x - textLayoutResult.size.width / 2).coerceIn(0f, maxLabelLeft),
                        chartBottom + 8.dp.toPx()
                    )
                )
            }
        }
        }
        
        // 显示浮动信息窗口
        selectedPoint?.let { (time, conc) ->
            touchPosition?.let { pos ->
                val dateFormat = SimpleDateFormat(
                    if (is24Hour) "MM/dd HH:mm" else "MM/dd hh:mm a",
                    LocalLocale.current.platformLocale
                )
                val timeMillis = (time * 3600000).toLong()
                val timeText = dateFormat.format(Date(timeMillis))
                val concText = "%.1f pg/mL".format(conc)
                
                Surface(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (pos.x + 16.dp.toPx()).toInt(),
                                y = (pos.y - 60.dp.toPx()).toInt()
                            )
                        }
                        .wrapContentSize(),
                    color = surfaceColor,
                    shadowElevation = 4.dp,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(
                            text = timeText,
                            style = TextStyle(
                                fontSize = 11.sp,
                                color = onSurfaceColor
                            )
                        )
                        Text(
                            text = concText,
                            style = TextStyle(
                                fontSize = 12.sp,
                                color = primaryColor
                            )
                        )
                    }
                }
            }
        }
    }
}
