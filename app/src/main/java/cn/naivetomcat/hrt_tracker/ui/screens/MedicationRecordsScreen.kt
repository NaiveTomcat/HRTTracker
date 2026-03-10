package cn.naivetomcat.hrt_tracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Wallpapers
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import cn.naivetomcat.hrt_tracker.R
import cn.naivetomcat.hrt_tracker.data.MedicationPlan
import cn.naivetomcat.hrt_tracker.data.ThemeMode
import cn.naivetomcat.hrt_tracker.pk.AntiAndrogen
import cn.naivetomcat.hrt_tracker.pk.DoseEvent
import cn.naivetomcat.hrt_tracker.pk.Ester
import cn.naivetomcat.hrt_tracker.pk.Route
import cn.naivetomcat.hrt_tracker.pk.SublingualTier
import cn.naivetomcat.hrt_tracker.ui.components.MedicationRecordBottomSheet
import cn.naivetomcat.hrt_tracker.ui.components.MedicationRecordItem
import cn.naivetomcat.hrt_tracker.ui.components.PatchMode
import cn.naivetomcat.hrt_tracker.ui.components.RecordDefaults
import cn.naivetomcat.hrt_tracker.ui.components.getAntiAndrogenDisplayName
import cn.naivetomcat.hrt_tracker.ui.theme.HRTTrackerTheme
import cn.naivetomcat.hrt_tracker.ui.utils.getRouteDisplayName
import cn.naivetomcat.hrt_tracker.ui.utils.getRouteIcon
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
    is24Hour: Boolean = true,
    modifier: Modifier = Modifier
) {
    val events by viewModel.events.collectAsState()
    val allPlans by viewModel.allPlans.collectAsState()
    var showBottomSheet by remember { mutableStateOf(false) }
    var eventToEdit by remember { mutableStateOf<DoseEvent?>(null) }
    var recordDefaults by remember { mutableStateOf<RecordDefaults?>(null) }

    MedicationRecordsScreenContent(
        events = events,
        allPlans = allPlans,
        onEventClick = { event ->
            eventToEdit = event
            showBottomSheet = true
        },
        onAddClick = {
            eventToEdit = null
            showBottomSheet = true
        },
        onQuickAddFromPlan = { plan ->
            val quickEvent = DoseEvent(
                route = plan.route,
                timeH = currentTimeHAtMinutePrecision(),
                doseMG = plan.doseMG,
                ester = plan.ester,
                extras = plan.extras
            )
            viewModel.upsertEvent(quickEvent)
            recordDefaults = quickEvent.toRecordDefaults()
        },
        is24Hour = is24Hour,
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
                recordDefaults = event.toRecordDefaults()
            }
        },
        onDelete = { id ->
            viewModel.deleteEvent(id)
            showBottomSheet = false
            eventToEdit = null
        },
        eventToEdit = eventToEdit,
        defaults = recordDefaults,
        is24Hour = is24Hour
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
    allPlans: List<MedicationPlan>,
    onEventClick: (DoseEvent) -> Unit,
    onAddClick: () -> Unit,
    onQuickAddFromPlan: (MedicationPlan) -> Unit,
    is24Hour: Boolean = true,
    modifier: Modifier = Modifier
) {
    var fabMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Top
        ),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.records_title), style = MaterialTheme.typography.headlineMediumEmphasized) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButtonMenu(
                expanded = fabMenuExpanded,
                button = {
                    ToggleFloatingActionButton(
                        checked = fabMenuExpanded,
                        onCheckedChange = { fabMenuExpanded = it },
                        containerSize = { 96.dp },
                        containerCornerRadius = { progress ->
                            lerp(28.dp, 48.dp, progress)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = if (fabMenuExpanded) {
                                stringResource(R.string.records_fab_close)
                            } else {
                                stringResource(R.string.records_fab_open)
                            }
                        )
                    }
                }
            ) {
                FloatingActionButtonMenuItem(
                    onClick = {
                        fabMenuExpanded = false
                        onAddClick()
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null
                        )
                    },
                    text = {
                        Text(stringResource(R.string.records_manual_add))
                    }
                )

                if (allPlans.isEmpty()) {
                    FloatingActionButtonMenuItem(
                        onClick = {},
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null
                            )
                        },
                        text = {
                            Text(stringResource(R.string.records_no_plan))
                        }
                    )
                } else {
                    allPlans.forEach { plan ->
                        FloatingActionButtonMenuItem(
                            onClick = {
                                fabMenuExpanded = false
                                onQuickAddFromPlan(plan)
                            },
                            icon = {
                                Icon(
                                    imageVector = getRouteIcon(plan.route),
                                    contentDescription = null
                                )
                            },
                            text = {
                                Text(
                                    stringResource(
                                        R.string.records_quick_add_format,
                                        getPlanMedicationDisplayName(plan),
                                        plan.doseMG,
                                        getRouteDisplayName(plan.route)
                                    )
                                )
                            }
                        )
                    }
                }
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
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.records_no_records),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.records_add_hint),
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
                        is24Hour = is24Hour,
                        onClick = { onEventClick(event) }
                    )
                }
            }
        }
    }
}

private fun DoseEvent.toRecordDefaults(): RecordDefaults {
    return RecordDefaults(
        route = route,
        ester = ester,
        doseMG = doseMG,
        patchMode = if (extras.containsKey(DoseEvent.ExtraKey.RELEASE_RATE_UG_PER_DAY)) {
            PatchMode.RATE
        } else {
            PatchMode.DOSE
        },
        patchRateUgPerDay = extras[DoseEvent.ExtraKey.RELEASE_RATE_UG_PER_DAY] ?: 0.0,
        sublingualTier = extras[DoseEvent.ExtraKey.SUBLINGUAL_TIER]?.toInt()?.let { tier ->
            SublingualTier.values().getOrElse(tier) { SublingualTier.STANDARD }
        } ?: SublingualTier.STANDARD,
        antiAndrogen = extras[DoseEvent.ExtraKey.ANTI_ANDROGEN_TYPE]?.toInt()?.let {
            AntiAndrogen.values().getOrElse(it) { AntiAndrogen.CPA }
        } ?: AntiAndrogen.CPA
    )
}

private fun currentTimeHAtMinutePrecision(): Double {
    val nowMs = System.currentTimeMillis()
    val minuteAlignedMs = (nowMs / 60000L) * 60000L
    return minuteAlignedMs / 3600000.0
}

@Composable
private fun getPlanMedicationDisplayName(plan: MedicationPlan): String {
    return if (plan.route == Route.ANTIANDROGEN) {
        val aaType = plan.extras[DoseEvent.ExtraKey.ANTI_ANDROGEN_TYPE]?.toInt()?.let {
            AntiAndrogen.values().getOrElse(it) { AntiAndrogen.CPA }
        } ?: AntiAndrogen.CPA
        getAntiAndrogenDisplayName(aaType)
    } else {
        getEsterDisplayName(plan.ester)
    }
}

@Composable
private fun getEsterDisplayName(ester: Ester): String {
    return when (ester) {
        Ester.E2 -> stringResource(R.string.ester_e2)
        Ester.EB -> stringResource(R.string.ester_eb)
        Ester.EV -> stringResource(R.string.ester_ev)
        Ester.EC -> stringResource(R.string.ester_ec)
        Ester.EN -> stringResource(R.string.ester_en)
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
            allPlans = emptyList(),
            onEventClick = {},
            onAddClick = {},
            onQuickAddFromPlan = {}
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

        val allPlans = remember {
            listOf(
                MedicationPlan(
                    name = "晚间口服",
                    route = Route.ORAL,
                    ester = Ester.E2,
                    doseMG = 2.0,
                    scheduleType = MedicationPlan.ScheduleType.DAILY,
                    timeOfDay = emptyList(),
                    isEnabled = true
                )
            )
        }

        MedicationRecordsScreenContent(
            events = events,
            allPlans = allPlans,
            onEventClick = {},
            onAddClick = {},
            onQuickAddFromPlan = {}
        )
    }
}

@Preview(name = "深色模式", showBackground = true, showSystemUi = true)
@Composable
private fun PreviewMedicationRecordsScreenDark() {
    HRTTrackerTheme(themeMode = ThemeMode.DARK) {
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
            allPlans = emptyList(),
            onEventClick = {},
            onAddClick = {},
            onQuickAddFromPlan = {}
        )
    }
}

@Preview(name = "系统浅色", showSystemUi = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "系统深色", showSystemUi = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "系统浅色绿色壁纸", showSystemUi = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO, wallpaper = Wallpapers.GREEN_DOMINATED_EXAMPLE)
@Composable
private fun PreviewMedicationRecordsScreenSystem() {
    HRTTrackerTheme() {
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
            allPlans = emptyList(),
            onEventClick = {},
            onAddClick = {},
            onQuickAddFromPlan = {}
        )
    }
}

// Note: 完整功能预览需要真实的 ViewModel，在实际设备上运行查看效果
