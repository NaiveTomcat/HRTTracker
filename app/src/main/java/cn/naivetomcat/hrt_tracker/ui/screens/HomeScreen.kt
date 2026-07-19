package cn.naivetomcat.hrt_tracker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.naivetomcat.hrt_tracker.R
import cn.naivetomcat.hrt_tracker.data.MedicationPlan
import cn.naivetomcat.hrt_tracker.pk.SimulationResult
import cn.naivetomcat.hrt_tracker.ui.components.ConcentrationChart
import cn.naivetomcat.hrt_tracker.ui.theme.HRTTrackerTheme
import cn.naivetomcat.hrt_tracker.utils.MedicationPlanPredictor
import cn.naivetomcat.hrt_tracker.viewmodel.ConcentrationLevel
import cn.naivetomcat.hrt_tracker.viewmodel.HRTViewModel
import cn.naivetomcat.hrt_tracker.viewmodel.PKState
import java.time.LocalDateTime
import kotlin.math.abs

/**
 * 主页屏幕
 * 显示雌二醇血药浓度图表和当前浓度信息
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HRTViewModel,
    is24Hour: Boolean = true,
    modifier: Modifier = Modifier
) {
    val pkState by viewModel.pkState.collectAsState()
    val events by viewModel.events.collectAsState()
    val enabledPlans by viewModel.enabledPlans.collectAsState()
    val realtimeCurrentTimeH by viewModel.currentTimeH.collectAsState()

    HomeScreenContent(
        pkState = pkState,
        doseTimePoints = events.map { it.timeH },
        enabledPlans = enabledPlans,
        realtimeCurrentTimeH = realtimeCurrentTimeH,
        onRefresh = { viewModel.runSimulation() },
        is24Hour = is24Hour,
        modifier = modifier
    )
}

/**
 * 主页屏幕内容（无状态）
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeScreenContent(
    pkState: PKState,
    doseTimePoints: List<Double>,
    enabledPlans: List<MedicationPlan>,
    realtimeCurrentTimeH: Double,
    onRefresh: () -> Unit,
    is24Hour: Boolean = true,
    modifier: Modifier = Modifier
) {
    // 计算分叉点时间：未来第一次计划用药的时间
    val forkPointTimeH = if (enabledPlans.isNotEmpty()) {
        val now = LocalDateTime.now()
        val nextEvents = MedicationPlanPredictor.generateFutureEventsForPlans(
            plans = enabledPlans,
            fromDateTime = now,
            daysAhead = 7  // 检查接下来的7天，确保能找到周期性计划的下一次事件
        )
        nextEvents.minOfOrNull { it.timeH }
    } else {
        null
    }

    // 计算实时当前浓度值（通过线性插值）
    val realtimeCurrentConcentration = if (pkState.simulationResult != null) {
        calculateConcentrationAtTime(pkState.simulationResult!!, realtimeCurrentTimeH)
    } else {
        null
    }

    // 检查是否需要重新运行模拟
    // 当且仅当当前时刻晚于下一次计划用药时，触发重新模拟
    LaunchedEffect(realtimeCurrentTimeH, forkPointTimeH) {
        if (forkPointTimeH != null && realtimeCurrentTimeH >= forkPointTimeH && 
            realtimeCurrentTimeH - forkPointTimeH < 1.0 / 3600.0) { // 晚于分叉点不超过1秒，避免频繁刷新
            onRefresh()
        }
    }
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Top
        ),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMediumEmphasized) },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.home_refresh)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        when {
            pkState.isSimulating -> {
                // 加载状态
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.home_calculating))
                    }
                }
            }
            pkState.error != null -> {
                // 错误状态
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Error,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = pkState.error ?: stringResource(R.string.common_unknown_error),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(onClick = onRefresh) {
                            Text(stringResource(R.string.common_retry))
                        }
                    }
                }
            }
            pkState.simulationResult == null -> {
                // 空状态
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.common_no_data),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.home_need_add_record),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            else -> {
                // 正常显示
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 当前浓度卡片
                    CurrentConcentrationCard(
                        concentration = realtimeCurrentConcentration,
                        pkState = pkState
                    )

                    // 图表卡片
                    ChartCard(
                        simulationResult = pkState.simulationResult!!,
                        baselineSimulationResult = pkState.baselineSimulationResult,
                        currentTimeH = realtimeCurrentTimeH,
                        doseTimePoints = doseTimePoints,
                        forkPointTimeH = forkPointTimeH,
                        is24Hour = is24Hour
                    )

                    // 浓度等级说明
                    ConcentrationLevelGuide()

                    // 曲线说明
                    CurveExplanationGuide()
                }
            }
        }
    }
}

/**
 * 当前浓度卡片
 */
@Composable
private fun CurrentConcentrationCard(
    concentration: Double?,
    pkState: PKState
) {
    // 根据当前浓度值创建临时的 PKState 用于颜色判断
    val tempPkState = pkState.copy(currentConcentration = concentration)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (tempPkState.getConcentrationLevelColor()) {
                ConcentrationLevel.LOW -> MaterialTheme.colorScheme.errorContainer  // 低于参考范围（琥珀色）
                ConcentrationLevel.FOLLICULAR -> MaterialTheme.colorScheme.tertiaryContainer  // 女性卵泡期（靛蓝色）
                ConcentrationLevel.LUTEAL -> MaterialTheme.colorScheme.secondaryContainer  // 女性黄体期（蓝色）
                ConcentrationLevel.MTF_TARGET -> MaterialTheme.colorScheme.primaryContainer  // GAHT目标（翠绿色）
                ConcentrationLevel.HIGH -> MaterialTheme.colorScheme.errorContainer  // 高于参考范围（琥珀色）
                ConcentrationLevel.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant  // 未知
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val concentrationText = if (concentration != null) {
                "%.1f pg/mL".format(concentration)
            } else {
                stringResource(R.string.home_concentration_placeholder)
            }
            val levelText = getConcentrationLevelText(tempPkState.getConcentrationLevelColor())

            Text(
                text = stringResource(R.string.home_current_concentration),
                style = MaterialTheme.typography.titleMedium
            )

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val useStackedLayout =
                    maxWidth < 400.dp || LocalDensity.current.fontScale > 1.2f

                if (useStackedLayout) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = concentrationText,
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            autoSize = TextAutoSize.StepBased(
                                minFontSize = 28.sp,
                                maxFontSize = MaterialTheme.typography.displayMedium.fontSize,
                                stepSize = 1.sp
                            )
                        )
                        Text(
                            text = levelText,
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2
                        )
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = concentrationText,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            autoSize = TextAutoSize.StepBased(
                                minFontSize = 28.sp,
                                maxFontSize = MaterialTheme.typography.displayMedium.fontSize,
                                stepSize = 1.sp
                            )
                        )

                        Text(
                            text = levelText,
                            modifier = Modifier.widthIn(max = 220.dp),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.End,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}

/**
 * 图表卡片
 */
@Composable
private fun ChartCard(
    simulationResult: SimulationResult,
    baselineSimulationResult: SimulationResult?,
    currentTimeH: Double,
    doseTimePoints: List<Double>,
    forkPointTimeH: Double? = null,
    is24Hour: Boolean = true
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.home_chart_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            ConcentrationChart(
                simulationResult = simulationResult,
                baselineSimulationResult = baselineSimulationResult,
                currentTimeH = currentTimeH,
                doseTimePoints = doseTimePoints,
                forkPointTimeH = forkPointTimeH,
                is24Hour = is24Hour,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

/**
 * 浓度等级说明
 */
@Composable
private fun ConcentrationLevelGuide() {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.home_level_guide),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            
            ConcentrationLevelItem(
                level = stringResource(R.string.home_level_low),
                range = "< 30 pg/mL",
                color = MaterialTheme.colorScheme.error
            )
            ConcentrationLevelItem(
                level = stringResource(R.string.home_level_follicular),
                range = "30-70 pg/mL",
                color = MaterialTheme.colorScheme.tertiary
            )
            ConcentrationLevelItem(
                level = stringResource(R.string.home_level_luteal),
                range = "70-300 pg/mL",
                color = MaterialTheme.colorScheme.secondary
            )
            ConcentrationLevelItem(
                level = stringResource(R.string.home_level_target),
                range = "100-200 pg/mL",
                color = MaterialTheme.colorScheme.primary
            )
            ConcentrationLevelItem(
                level = stringResource(R.string.home_level_high),
                range = "> 300 pg/mL",
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * 曲线说明（解释历史曲线和预测曲线）
 */
@Composable
private fun CurveExplanationGuide() {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.home_curve_guide),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = stringResource(R.string.home_curve_dose_record),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            // 历史数据和早期预测
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height(3.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(1.5.dp)
                        )
                )
                Text(
                    text = stringResource(R.string.home_curve_history),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            // 无计划的未来预测
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height(3.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(1.5.dp)
                        )
                )
                Text(
                    text = stringResource(R.string.home_curve_no_plan),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            // 基于用药方案的预测
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height(3.dp)
                        .background(
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(1.5.dp)
                        )
                )
                Text(
                    text = stringResource(R.string.home_curve_with_plan),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * 浓度等级项
 */
@Composable
private fun ConcentrationLevelItem(
    level: String,
    range: String,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(color, shape = MaterialTheme.shapes.small)
            )
            Text(
                text = level,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        Text(
            text = range,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ============================================================================
// Previews
// ============================================================================

@Preview(
    name = "窄屏大字体浓度卡片",
    widthDp = 320,
    fontScale = 1.3f,
    showBackground = true
)
@Composable
private fun PreviewCurrentConcentrationCardNarrow() {
    HRTTrackerTheme {
        CurrentConcentrationCard(
            concentration = 147.4,
            pkState = PKState(currentConcentration = 147.4)
        )
    }
}

@Preview(name = "空状态", showBackground = true, showSystemUi = true)
@Composable
private fun PreviewHomeScreenEmpty() {
    HRTTrackerTheme {
        val currentTimeH = System.currentTimeMillis() / 3600000.0
        HomeScreenContent(
            pkState = PKState(),
            doseTimePoints = emptyList(),
            enabledPlans = emptyList(),
            realtimeCurrentTimeH = currentTimeH,
            onRefresh = {}
        )
    }
}

@Preview(name = "加载中", showBackground = true)
@Composable
private fun PreviewHomeScreenLoading() {
    HRTTrackerTheme {
        val currentTimeH = System.currentTimeMillis() / 3600000.0
        HomeScreenContent(
            pkState = PKState(isSimulating = true),
            doseTimePoints = emptyList(),
            enabledPlans = emptyList(),
            realtimeCurrentTimeH = currentTimeH,
            onRefresh = {}
        )
    }
}

@Preview(name = "有数据", showBackground = true, showSystemUi = true)
@Composable
private fun PreviewHomeScreenWithData() {
    HRTTrackerTheme {
        val currentTimeH = System.currentTimeMillis() / 3600000.0
        val mockResult = SimulationResult(
            timeH = List(100) { currentTimeH - 360 + it * 7.2 },
            concPGmL = List(100) { 100.0 + it * 2.0 - (it - 50) * (it - 50) * 0.1 },
            auc = 15000.0
        )
        
        HomeScreenContent(
            pkState = PKState(
                simulationResult = mockResult,
                currentConcentration = 150.0,
                currentTimeH = currentTimeH
            ),
            doseTimePoints = listOf(
                currentTimeH - 336,
                currentTimeH - 168,
                currentTimeH - 72,
                currentTimeH - 24
            ),
            enabledPlans = emptyList(),
            realtimeCurrentTimeH = currentTimeH,
            onRefresh = {}
        )
    }
}
/**
 * 通过线性插值计算指定时刻的血药浓度
 */
private fun calculateConcentrationAtTime(
    simulationResult: SimulationResult,
    targetTimeH: Double
): Double? {
    if (simulationResult.timeH.isEmpty() || simulationResult.concPGmL.isEmpty()) {
        return null
    }
    
    val minTime = simulationResult.timeH.minOrNull() ?: return null
    val maxTime = simulationResult.timeH.maxOrNull() ?: return null

    // 如果目标时刻在数据范围之外，返回 null
    if (targetTimeH < minTime || targetTimeH > maxTime) {
        return null
    }

    // 找到目标时刻在两个数据点之间的位置
    for (i in 0 until simulationResult.timeH.size - 1) {
        val time1 = simulationResult.timeH[i]
        val time2 = simulationResult.timeH[i + 1]

        if (time1 <= targetTimeH && targetTimeH <= time2) {
            // 线性插值
            val conc1 = simulationResult.concPGmL[i]
            val conc2 = simulationResult.concPGmL[i + 1]
            
            // 避免除以零
            if (time2 == time1) {
                return conc1
            }
            
            val ratio = (targetTimeH - time1) / (time2 - time1)
            return conc1 + (conc2 - conc1) * ratio
        }
    }

    return null
}

@Composable
private fun getConcentrationLevelText(level: ConcentrationLevel): String {
    return when (level) {
        ConcentrationLevel.LOW -> stringResource(R.string.home_level_low)
        ConcentrationLevel.FOLLICULAR -> stringResource(R.string.home_level_follicular)
        ConcentrationLevel.LUTEAL -> stringResource(R.string.home_level_luteal)
        ConcentrationLevel.MTF_TARGET -> stringResource(R.string.home_level_target)
        ConcentrationLevel.HIGH -> stringResource(R.string.home_level_high)
        ConcentrationLevel.UNKNOWN -> stringResource(R.string.home_level_placeholder)
    }
}
