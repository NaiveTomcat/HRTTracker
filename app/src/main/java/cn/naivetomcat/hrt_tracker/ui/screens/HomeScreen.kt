package cn.naivetomcat.hrt_tracker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cn.naivetomcat.hrt_tracker.pk.SimulationResult
import cn.naivetomcat.hrt_tracker.ui.components.ConcentrationChart
import cn.naivetomcat.hrt_tracker.ui.theme.HRTTrackerTheme
import cn.naivetomcat.hrt_tracker.viewmodel.ConcentrationLevel
import cn.naivetomcat.hrt_tracker.viewmodel.HRTViewModel
import cn.naivetomcat.hrt_tracker.viewmodel.PKState

/**
 * 主页屏幕
 * 显示雌二醇血药浓度图表和当前浓度信息
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HRTViewModel,
    modifier: Modifier = Modifier
) {
    val pkState by viewModel.pkState.collectAsState()
    val events by viewModel.events.collectAsState()

    HomeScreenContent(
        pkState = pkState,
        doseTimePoints = events.map { it.timeH },
        onRefresh = { viewModel.runSimulation() },
        modifier = modifier
    )
}

/**
 * 主页屏幕内容（无状态）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreenContent(
    pkState: PKState,
    doseTimePoints: List<Double>,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("雌二醇血药浓度") },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "刷新"
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
                        Text("计算中...")
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
                            text = pkState.error ?: "未知错误",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(onClick = onRefresh) {
                            Text("重试")
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
                            text = "暂无数据",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "请先添加用药记录",
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
                    CurrentConcentrationCard(pkState = pkState)

                    // 图表卡片
                    ChartCard(
                        simulationResult = pkState.simulationResult!!,
                        currentTimeH = pkState.currentTimeH,
                        doseTimePoints = doseTimePoints
                    )

                    // 浓度等级说明
                    ConcentrationLevelGuide()
                }
            }
        }
    }
}

/**
 * 当前浓度卡片
 */
@Composable
private fun CurrentConcentrationCard(pkState: PKState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (pkState.getConcentrationLevelColor()) {
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
            Text(
                text = "当前浓度",
                style = MaterialTheme.typography.titleMedium
            )
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (pkState.currentConcentration != null) {
                        "%.1f pg/mL".format(pkState.currentConcentration)
                    } else {
                        "-- pg/mL"
                    },
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = pkState.getConcentrationLevel() ?: "--",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium
                )
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
    currentTimeH: Double,
    doseTimePoints: List<Double>
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
                text = "血药浓度曲线",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            ConcentrationChart(
                simulationResult = simulationResult,
                currentTimeH = currentTimeH,
                doseTimePoints = doseTimePoints,
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
                text = "浓度等级参考",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            
            ConcentrationLevelItem(
                level = "偏低",
                range = "< 50 pg/mL",
                color = MaterialTheme.colorScheme.error
            )
            ConcentrationLevelItem(
                level = "正常下限",
                range = "50-100 pg/mL",
                color = MaterialTheme.colorScheme.tertiary
            )
            ConcentrationLevelItem(
                level = "正常",
                range = "100-200 pg/mL",
                color = MaterialTheme.colorScheme.primary
            )
            ConcentrationLevelItem(
                level = "正常上限",
                range = "200-300 pg/mL",
                color = MaterialTheme.colorScheme.secondary
            )
            ConcentrationLevelItem(
                level = "偏高",
                range = "300-400 pg/mL",
                color = MaterialTheme.colorScheme.tertiary
            )
            ConcentrationLevelItem(
                level = "超高",
                range = "> 400 pg/mL",
                color = MaterialTheme.colorScheme.error
            )
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

@Preview(name = "空状态", showBackground = true, showSystemUi = true)
@Composable
private fun PreviewHomeScreenEmpty() {
    HRTTrackerTheme {
        HomeScreenContent(
            pkState = PKState(),
            doseTimePoints = emptyList(),
            onRefresh = {}
        )
    }
}

@Preview(name = "加载中", showBackground = true)
@Composable
private fun PreviewHomeScreenLoading() {
    HRTTrackerTheme {
        HomeScreenContent(
            pkState = PKState(isSimulating = true),
            doseTimePoints = emptyList(),
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
            onRefresh = {}
        )
    }
}
