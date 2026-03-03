package cn.naivetomcat.hrt_tracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cn.naivetomcat.hrt_tracker.data.MedicationPlan
import cn.naivetomcat.hrt_tracker.pk.Ester
import cn.naivetomcat.hrt_tracker.pk.Route
import cn.naivetomcat.hrt_tracker.ui.components.MedicationPlanBottomSheet
import cn.naivetomcat.hrt_tracker.ui.components.MedicationPlanCard
import cn.naivetomcat.hrt_tracker.ui.theme.HRTTrackerTheme
import cn.naivetomcat.hrt_tracker.viewmodel.MedicationPlanViewModel
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID

/**
 * 用药方案屏幕（带状态管理）
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MedicationPlansScreen(
    viewModel: MedicationPlanViewModel,
    modifier: Modifier = Modifier
) {
    val plans by viewModel.plans.collectAsState()
    var showBottomSheet by remember { mutableStateOf(false) }
    var planToEdit by remember { mutableStateOf<MedicationPlan?>(null) }

    MedicationPlansScreenContent(
        plans = plans,
        onPlanClick = { plan ->
            planToEdit = plan
            showBottomSheet = true
        },
        onAddClick = {
            planToEdit = null
            showBottomSheet = true
        },
        onToggleEnabled = { id, isEnabled ->
            viewModel.togglePlanEnabled(id, isEnabled)
        },
        modifier = modifier
    )

    // 底部弹窗
    MedicationPlanBottomSheet(
        showBottomSheet = showBottomSheet,
        onDismiss = {
            showBottomSheet = false
            planToEdit = null
        },
        onSave = { plan ->
            viewModel.upsertPlan(plan)
            showBottomSheet = false
            planToEdit = null
        },
        onDelete = { id ->
            viewModel.deletePlan(id)
            showBottomSheet = false
            planToEdit = null
        },
        planToEdit = planToEdit
    )
}

/**
 * 用药方案屏幕内容（无状态）
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MedicationPlansScreenContent(
    plans: List<MedicationPlan>,
    onPlanClick: (MedicationPlan) -> Unit,
    onAddClick: () -> Unit,
    onToggleEnabled: (UUID, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("用药方案", style = MaterialTheme.typography.headlineMediumEmphasized) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            LargeFloatingActionButton(
                onClick = onAddClick,
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "添加用药方案"
                )
            }
        },
        modifier = modifier
    ) { paddingValues ->
        if (plans.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无用药方案",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(plans, key = { it.id }) { plan ->
                    MedicationPlanCard(
                        plan = plan,
                        onClick = { onPlanClick(plan) },
                        onToggleEnabled = { onToggleEnabled(plan.id, !plan.isEnabled) }
                    )
                }
            }
        }
    }
}

/**
 * 预览
 */
@Preview(showBackground = true)
@Composable
private fun MedicationPlansScreenPreview() {
    HRTTrackerTheme {
        val samplePlans = listOf(
            MedicationPlan(
                id = UUID.randomUUID(),
                name = "EV注射",
                route = Route.INJECTION,
                ester = Ester.EV,
                doseMG = 10.0,
                scheduleType = MedicationPlan.ScheduleType.WEEKLY,
                timeOfDay = listOf(LocalTime.of(9, 0)),
                daysOfWeek = setOf(DayOfWeek.MONDAY),
                isEnabled = true
            ),
            MedicationPlan(
                id = UUID.randomUUID(),
                name = "E2凝胶",
                route = Route.GEL,
                ester = Ester.E2,
                doseMG = 3.0,
                scheduleType = MedicationPlan.ScheduleType.DAILY,
                timeOfDay = listOf(LocalTime.of(23, 0)),
                isEnabled = true
            ),
            MedicationPlan(
                id = UUID.randomUUID(),
                name = "口服EV",
                route = Route.ORAL,
                ester = Ester.EV,
                doseMG = 2.0,
                scheduleType = MedicationPlan.ScheduleType.DAILY,
                timeOfDay = listOf(LocalTime.of(8, 0), LocalTime.of(23, 30)),
                isEnabled = false
            )
        )

        MedicationPlansScreenContent(
            plans = samplePlans,
            onPlanClick = {},
            onAddClick = {},
            onToggleEnabled = { _, _ -> }
        )
    }
}
