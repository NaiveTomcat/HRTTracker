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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import cn.naivetomcat.hrt_tracker.pk.SimulationResult
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.sqrt


/**
 * 雌二醇浓度图表组件
 * 使用 Canvas 绘制交互式折线图
 * 
 * 功能：
 * - 支持手势缩放和拖动
 * - 显示坐标轴和网格线
 * - 标记给药时间点和当前时刻
 *
 * @param simulationResult 模拟结果
 * @param currentTimeH 当前时刻（小时）
 * @param doseTimePoints 给药时间点列表（小时）
 * @param modifier Modifier
 */
@Composable
fun ConcentrationChart(
    simulationResult: SimulationResult,
    currentTimeH: Double,
    doseTimePoints: List<Double>,
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
    
    // 当数据改变时重置初始化状态
    LaunchedEffect(simulationResult, currentTimeH) {
        scaleX = initialScale
        offsetX = 0f
        isInitialized = false
    }

    if (simulationResult.timeH.isEmpty() || simulationResult.concPGmL.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("暂无数据", color = onSurfaceColor)
        }
        return
    }

    // Y轴刻度使用25的倍数
    val rawConcMax = (simulationResult.concPGmL.maxOrNull() ?: 100.0) * 1.1
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
            // 绘制浓度曲线（应用缩放和平移）
            val path = Path()
            simulationResult.timeH.forEachIndexed { index, time ->
                val conc = simulationResult.concPGmL[index]
                val normalizedTime = ((time - timeMin) / (timeMax - timeMin)).toFloat()
                val x = chartLeft + normalizedTime * chartWidth * scaleX + offsetX
                val y = chartBottom - chartHeight * ((conc - concMin) / (concMax - concMin)).toFloat()
                
                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            
            drawPath(
                path = path,
                color = primaryColor,
                style = Stroke(width = 2.5.dp.toPx())
            )

            // 标记给药时间点（应用缩放和平移）
            doseTimePoints.forEach { doseTime ->
                if (doseTime >= timeMin && doseTime <= timeMax) {
                    val normalizedTime = ((doseTime - timeMin) / (timeMax - timeMin)).toFloat()
                    val x = chartLeft + normalizedTime * chartWidth * scaleX + offsetX
                    if (x >= chartLeft && x <= chartRight) {
                        drawLine(
                            color = errorColor.copy(alpha = 0.7f),
                            start = Offset(x, chartTop),
                            end = Offset(x, chartBottom),
                            strokeWidth = 2.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f))
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
        val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
        // 计算可见时间范围
        val visibleTimeStart = timeMin - (offsetX / (chartWidth * scaleX)) * (timeMax - timeMin)
        val visibleTimeEnd = visibleTimeStart + (timeMax - timeMin) / scaleX
        
        for (i in 0..5) {
            val timeValue = visibleTimeStart + (visibleTimeEnd - visibleTimeStart) * i / 5
            // 从时间值计算屏幕坐标
            val normalizedPos = ((timeValue - timeMin) / (timeMax - timeMin)).toFloat()
            val x = chartLeft + normalizedPos * chartWidth * scaleX + offsetX
            
            if (x >= chartLeft && x <= chartRight) {
                val timeMillis = (timeValue * 3600000).toLong()
                val text = dateFormat.format(Date(timeMillis))
                val textLayoutResult = textMeasurer.measure(
                    text = text,
                    style = TextStyle(
                        color = onSurfaceColor,
                        fontSize = 10.sp
                    )
                )
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(
                        x - textLayoutResult.size.width / 2,
                        chartBottom + 8.dp.toPx()
                    )
                )
            }
        }
        }
        
        // 显示浮动信息窗口
        selectedPoint?.let { (time, conc) ->
            touchPosition?.let { pos ->
                val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
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

