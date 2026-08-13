package cn.naivetomcat.hrt_tracker.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cn.naivetomcat.hrt_tracker.R
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
    is24Hour: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val plans by viewModel.plans.collectAsState()
    var showBottomSheet by remember { mutableStateOf(false) }
    var planToEdit by remember { mutableStateOf<MedicationPlan?>(null) }
    var notificationPermissionGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var promotedNotificationsEnabled by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA ||
                NotificationManagerCompat.from(context).canPostPromotedNotifications()
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationPermissionGranted = granted
    }
    val promotedNotificationSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        promotedNotificationsEnabled =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA ||
                NotificationManagerCompat.from(context).canPostPromotedNotifications()
    }

    fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !notificationPermissionGranted
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun openPromotedNotificationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            promotedNotificationSettingsLauncher.launch(
                Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            )
        }
    }

    val hasEnabledPlan = plans.any { it.isEnabled }

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
            if (isEnabled) {
                requestNotificationPermissionIfNeeded()
            }
            viewModel.togglePlanEnabled(id, isEnabled)
        },
        showNotificationPermissionSetup = hasEnabledPlan && !notificationPermissionGranted,
        onNotificationPermissionSetup = ::requestNotificationPermissionIfNeeded,
        showPromotedNotificationSetup =
            hasEnabledPlan &&
                notificationPermissionGranted &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA &&
                !promotedNotificationsEnabled,
        onPromotedNotificationSetup = ::openPromotedNotificationSettings,
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
            if (plan.isEnabled) {
                requestNotificationPermissionIfNeeded()
            }
            viewModel.upsertPlan(plan)
            showBottomSheet = false
            planToEdit = null
        },
        onDelete = { id ->
            viewModel.deletePlan(id)
            showBottomSheet = false
            planToEdit = null
        },
        planToEdit = planToEdit,
        is24Hour = is24Hour
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
    showNotificationPermissionSetup: Boolean = false,
    onNotificationPermissionSetup: () -> Unit = {},
    showPromotedNotificationSetup: Boolean = false,
    onPromotedNotificationSetup: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.plans_title), style = MaterialTheme.typography.headlineMediumEmphasized) },
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
                    contentDescription = stringResource(R.string.plans_add_content_desc)
                )
            }
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showNotificationPermissionSetup) {
                item(key = "notification-permission") {
                    ReminderSetupCard(
                        title = stringResource(R.string.plans_notification_permission_title),
                        description = stringResource(R.string.plans_notification_permission_desc),
                        onClick = onNotificationPermissionSetup
                    )
                }
            }

            if (showPromotedNotificationSetup) {
                item(key = "promoted-notification-permission") {
                    ReminderSetupCard(
                        title = stringResource(R.string.plans_promoted_notification_title),
                        description = stringResource(R.string.plans_promoted_notification_desc),
                        onClick = onPromotedNotificationSetup
                    )
                }
            }

            if (plans.isEmpty()) {
                item(key = "empty") {
                    Box(
                        modifier = Modifier
                            .fillParentMaxHeight(0.7f)
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.plans_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
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

@Composable
private fun ReminderSetupCard(
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.NotificationsActive,
                contentDescription = null
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null
            )
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
