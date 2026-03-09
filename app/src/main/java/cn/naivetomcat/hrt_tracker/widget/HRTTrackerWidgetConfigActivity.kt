package cn.naivetomcat.hrt_tracker.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Soap
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import cn.naivetomcat.hrt_tracker.data.AppDatabase
import cn.naivetomcat.hrt_tracker.data.MedicationPlan
import cn.naivetomcat.hrt_tracker.data.displayName
import cn.naivetomcat.hrt_tracker.pk.Route
import cn.naivetomcat.hrt_tracker.ui.theme.HRTTrackerTheme
import cn.naivetomcat.hrt_tracker.widget.WidgetUtils.routeDisplayName
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 微件配置 Activity
 *
 * 允许用户为每个微件实例选择对应的用药方案。
 * 选择后同时更新提醒行和快速添加行显示的内容。
 */
class HRTTrackerWidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Default: RESULT_CANCELED – if user presses back without selecting, widget is not placed
        setResult(RESULT_CANCELED)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            HRTTrackerTheme {
                WidgetConfigScreen(
                    onPlanSelected = { plan ->
                        lifecycleScope.launch {
                            val glanceId = GlanceAppWidgetManager(this@HRTTrackerWidgetConfigActivity)
                                .getGlanceIdBy(appWidgetId)
                            updateAppWidgetState(this@HRTTrackerWidgetConfigActivity, glanceId) { prefs ->
                                prefs[KEY_CONFIGURED_PLAN_ID] = plan.id.toString()
                                prefs[KEY_CONFIRMING] = false
                            }
                            HRTTrackerWidget().update(this@HRTTrackerWidgetConfigActivity, glanceId)
                            setResult(RESULT_OK, Intent().putExtra(
                                AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId
                            ))
                            finish()
                        }
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WidgetConfigScreen(
    onPlanSelected: (MedicationPlan) -> Unit,
    onDismiss: () -> Unit
) {
    // Load enabled plans from the database
    var plans by remember { mutableStateOf<List<MedicationPlan>>(emptyList()) }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        val db = AppDatabase.getDatabase(context)
        plans = db.medicationPlanDao().getEnabledPlans().first().map { it.toMedicationPlan() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("选择用药方案") },
                actions = {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (plans.isEmpty()) {
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "暂无启用的用药方案",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "请先在应用内添加并启用用药方案，然后重新添加微件",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(contentPadding = paddingValues) {
                items(plans) { plan ->
                    PlanConfigItem(
                        plan = plan,
                        onClick = { onPlanSelected(plan) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlanConfigItem(
    plan: MedicationPlan,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        leadingContent = {
            Icon(
                imageVector = routeIconForConfig(plan.route),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        headlineContent = {
            Text(
                text = plan.name,
                style = MaterialTheme.typography.bodyLarge
            )
        },
        supportingContent = {
            Text(
                text = "${plan.doseMG}mg · ${plan.ester.displayName} · ${routeDisplayName(plan.route)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}

private fun routeIconForConfig(route: Route) = when (route) {
    Route.INJECTION -> Icons.Filled.Vaccines
    Route.ORAL -> Icons.Filled.Medication
    Route.SUBLINGUAL -> Icons.Filled.WaterDrop
    Route.GEL -> Icons.Filled.Soap
    Route.PATCH_APPLY -> Icons.Filled.AddBox
    Route.PATCH_REMOVE -> Icons.Filled.RemoveCircle
}
