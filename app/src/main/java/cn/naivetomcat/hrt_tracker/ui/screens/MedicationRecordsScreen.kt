package cn.naivetomcat.hrt_tracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cn.naivetomcat.hrt_tracker.pk.DoseEvent
import cn.naivetomcat.hrt_tracker.pk.Ester
import cn.naivetomcat.hrt_tracker.pk.Route
import cn.naivetomcat.hrt_tracker.pk.SublingualTier
import cn.naivetomcat.hrt_tracker.ui.components.MedicationRecordBottomSheet
import cn.naivetomcat.hrt_tracker.ui.components.MedicationRecordItem
import cn.naivetomcat.hrt_tracker.ui.components.PatchMode
import cn.naivetomcat.hrt_tracker.ui.components.RecordDefaults
import cn.naivetomcat.hrt_tracker.ui.theme.HRTTrackerTheme
import cn.naivetomcat.hrt_tracker.viewmodel.HRTViewModel

/**
 * 用药记录列表屏幕（带状态管理）
 * 
 * @param viewModel HRT ViewModel
 * @param modifier Modifier
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MedicationRecordsScreen(
    viewModel: HRTViewModel,
    modifier: Modifier = Modifier
) {
    val events by viewModel.events.collectAsState()
    var showBottomSheet by remember { mutableStateOf(false) }
    var eventToEdit by remember { mutableStateOf<DoseEvent?>(null) }
    var recordDefaults by remember { mutableStateOf<RecordDefaults?>(null) }

    MedicationRecordsScreenContent(
        events = events,
        onEventClick = { event ->
            eventToEdit = event
            showBottomSheet = true
        },
        onAddClick = {
            eventToEdit = null
            showBottomSheet = true
        },
        modifier = modifier
    )

    // 底部弹窗
    MedicationRecordBottomSheet(
        showBottomSheet = showBottomSheet,
        onDismiss = {
            showBottomSheet = false
            eventToEdit = null
        },
        onSave = { event ->
            // 保存前判断是否为新记录（用于更新默认值）
            val isNewRecord = eventToEdit == null
            
            viewModel.upsertEvent(event)
            showBottomSheet = false
            eventToEdit = null
            
            // 如果是新记录，保存默认值（除时间外）以供下次使用
            if (isNewRecord) {
                recordDefaults = RecordDefaults(
                    route = event.route,
                    ester = event.ester,
                    doseMG = event.doseMG,
                    patchMode = if (event.extras.containsKey(DoseEvent.ExtraKey.RELEASE_RATE_UG_PER_DAY)) 
                        PatchMode.RATE 
                    else 
                        PatchMode.DOSE,
                    patchRateUgPerDay = event.extras[DoseEvent.ExtraKey.RELEASE_RATE_UG_PER_DAY] ?: 0.0,
                    sublingualTier = event.extras[DoseEvent.ExtraKey.SUBLINGUAL_TIER]?.toInt()?.let { tier ->
                        SublingualTier.values().getOrElse(tier) { SublingualTier.STANDARD }
                    } ?: SublingualTier.STANDARD
                )
            }
        },
        onDelete = { id ->
            viewModel.deleteEvent(id)
            showBottomSheet = false
            eventToEdit = null
        },
        eventToEdit = eventToEdit,
        defaults = recordDefaults
    )
}

/**
 * 用药记录列表屏幕内容（无状态）
 * 
 * @param events 用药事件列表
 * @param onEventClick 点击事件回调
 * @param onAddClick 添加按钮点击回调
 * @param modifier Modifier
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MedicationRecordsScreenContent(
    events: List<DoseEvent>,
    onEventClick: (DoseEvent) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("用药记录") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            MediumFloatingActionButton(
                onClick = onAddClick,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "添加用药记录"
                )
            }
        },
        modifier = modifier
    ) { paddingValues ->
        if (events.isEmpty()) {
            // 空状态
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(32.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Column(
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "暂无用药记录",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "点击右下角按钮添加用药记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // 用药记录列表（按时间倒序排列，最新的在前面）
            val sortedEvents = remember(events) {
                events.sortedByDescending { it.timeH }
            }
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = sortedEvents,
                    key = { it.id }
                ) { event ->
                    MedicationRecordItem(
                        event = event,
                        onClick = { onEventClick(event) }
                    )
                }
            }
        }
    }
}

// ============================================================================
// Previews
// ============================================================================

@Preview(name = "空列表", showBackground = true)
@Composable
private fun PreviewMedicationRecordsScreenEmpty() {
    HRTTrackerTheme {
        MedicationRecordsScreenContent(
            events = emptyList(),
            onEventClick = {},
            onAddClick = {}
        )
    }
}

@Preview(name = "有记录列表", showBackground = true, showSystemUi = true,
    backgroundColor = 0xFF00E5FF
)
@Composable
private fun PreviewMedicationRecordsScreenWithData() {
    HRTTrackerTheme {
        val currentTime = System.currentTimeMillis() / 3600000.0
        val events = remember {
            listOf(
                DoseEvent(
                    route = Route.INJECTION,
                    timeH = currentTime - 168.0,
                    doseMG = 5.0,
                    ester = Ester.EV
                ),
                DoseEvent(
                    route = Route.ORAL,
                    timeH = currentTime - 24.0,
                    doseMG = 2.0,
                    ester = Ester.E2
                ),
                DoseEvent(
                    route = Route.ORAL,
                    timeH = currentTime - 12.0,
                    doseMG = 2.0,
                    ester = Ester.E2
                ),
                DoseEvent(
                    route = Route.SUBLINGUAL,
                    timeH = currentTime - 6.0,
                    doseMG = 1.0,
                    ester = Ester.E2
                ),
                DoseEvent(
                    route = Route.GEL,
                    timeH = currentTime - 2.0,
                    doseMG = 0.75,
                    ester = Ester.E2,
                    extras = mapOf(DoseEvent.ExtraKey.AREA_CM2 to 750.0)
                ),
                DoseEvent(
                    route = Route.PATCH_APPLY,
                    timeH = currentTime - 72.0,
                    doseMG = 0.0,
                    ester = Ester.E2,
                    extras = mapOf(DoseEvent.ExtraKey.RELEASE_RATE_UG_PER_DAY to 50.0)
                )
            )
        }

        MedicationRecordsScreenContent(
            events = events,
            onEventClick = {},
            onAddClick = {}
        )
    }
}

@Preview(name = "深色模式", showBackground = true, showSystemUi = true)
@Composable
private fun PreviewMedicationRecordsScreenDark() {
    HRTTrackerTheme(darkTheme = true) {
        val currentTime = System.currentTimeMillis() / 3600000.0
        val events = remember {
            listOf(
                DoseEvent(
                    route = Route.INJECTION,
                    timeH = currentTime - 168.0,
                    doseMG = 5.0,
                    ester = Ester.EV
                ),
                DoseEvent(
                    route = Route.ORAL,
                    timeH = currentTime - 12.0,
                    doseMG = 2.0,
                    ester = Ester.E2
                ),
                DoseEvent(
                    route = Route.SUBLINGUAL,
                    timeH = currentTime - 2.0,
                    doseMG = 1.0,
                    ester = Ester.E2
                )
            )
        }

        MedicationRecordsScreenContent(
            events = events,
            onEventClick = {},
            onAddClick = {}
        )
    }
}

// Note: 完整功能预览需要真实的 ViewModel，在实际设备上运行查看效果
