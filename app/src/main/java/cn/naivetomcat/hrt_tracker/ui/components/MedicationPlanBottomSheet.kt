package cn.naivetomcat.hrt_tracker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cn.naivetomcat.hrt_tracker.R
import cn.naivetomcat.hrt_tracker.data.MedicationPlan
import cn.naivetomcat.hrt_tracker.pk.*
import cn.naivetomcat.hrt_tracker.ui.theme.HRTTrackerTheme
import cn.naivetomcat.hrt_tracker.ui.utils.getRouteDisplayName
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * 添加或编辑用药方案的底部弹窗
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MedicationPlanBottomSheet(
    showBottomSheet: Boolean,
    onDismiss: () -> Unit,
    onSave: (MedicationPlan) -> Unit,
    onDelete: ((UUID) -> Unit)? = null,
    planToEdit: MedicationPlan? = null
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    // 表单状态
    var name by remember(planToEdit, showBottomSheet) {
        mutableStateOf(planToEdit?.name ?: "")
    }

    var selectedRoute by remember(planToEdit, showBottomSheet) {
        mutableStateOf(planToEdit?.route ?: Route.INJECTION)
    }

    var selectedEster by remember(planToEdit, showBottomSheet) {
        mutableStateOf(planToEdit?.ester ?: Ester.EV)
    }

    var scheduleType by remember(planToEdit, showBottomSheet) {
        mutableStateOf(planToEdit?.scheduleType ?: MedicationPlan.ScheduleType.DAILY)
    }

    var doseMGText by remember(planToEdit, showBottomSheet) {
        mutableStateOf(planToEdit?.doseMG?.toString() ?: "")
    }

    var timeOfDay by remember(planToEdit, showBottomSheet) {
        mutableStateOf(planToEdit?.timeOfDay ?: listOf(LocalTime.of(9, 0)))
    }

    var daysOfWeek by remember(planToEdit, showBottomSheet) {
        mutableStateOf(planToEdit?.daysOfWeek ?: emptySet())
    }

    var intervalDays by remember(planToEdit, showBottomSheet) {
        mutableStateOf(planToEdit?.intervalDays?.toString() ?: "1")
    }

    // 舌下吸收等级
    var sublingualTier by remember(planToEdit, showBottomSheet) {
        mutableStateOf(
            planToEdit?.extras?.get(DoseEvent.ExtraKey.SUBLINGUAL_TIER)?.toInt()?.let { tier ->
                SublingualTier.values().getOrElse(tier) { SublingualTier.STANDARD }
            } ?: SublingualTier.STANDARD
        )
    }

    var showTimePicker by remember { mutableStateOf(false) }
    var timeIndexToEdit by remember { mutableStateOf(0) }

    // 根据给药途径过滤可用的酯类
    val availableEsters = remember(selectedRoute) {
        getAvailableEstersForRoute(selectedRoute)
    }

    // 如果当前选择的酯类不在可用列表中，自动切换到第一个
    LaunchedEffect(selectedRoute, availableEsters) {
        if (selectedEster !in availableEsters) {
            selectedEster = availableEsters.firstOrNull() ?: Ester.E2
        }
    }

    // 验证表单
    val isValid = remember(name, doseMGText, timeOfDay, scheduleType, daysOfWeek, intervalDays) {
        name.isNotBlank() &&
                doseMGText.toDoubleOrNull() != null &&
                doseMGText.toDouble() > 0 &&
                timeOfDay.isNotEmpty() &&
                (scheduleType != MedicationPlan.ScheduleType.WEEKLY || daysOfWeek.isNotEmpty()) &&
                (scheduleType != MedicationPlan.ScheduleType.CUSTOM || (intervalDays.toIntOrNull() ?: 0) > 0)
    }

    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 标题
                Text(
                    text = if (planToEdit != null) stringResource(R.string.plan_sheet_edit_title) else stringResource(R.string.plan_sheet_add_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // 方案名称
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.plan_sheet_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 给药途径选择
                RouteSelectionSection(
                    selectedRoute = selectedRoute,
                    onRouteSelected = { selectedRoute = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 药物类型选择
                EsterSelectionSection(
                    selectedEster = selectedEster,
                    availableEsters = availableEsters,
                    onEsterSelected = { selectedEster = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 剂量输入
                OutlinedTextField(
                    value = doseMGText,
                    onValueChange = { doseMGText = it },
                    label = { Text(stringResource(R.string.plan_sheet_dose_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 舌下吸收等级（仅舌下途径显示）
                AnimatedVisibility(visible = selectedRoute == Route.SUBLINGUAL) {
                    Column {
                        SublingualTierSelector(
                            selectedTier = sublingualTier,
                            onTierSelected = { sublingualTier = it }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                Spacer(modifier = Modifier.height(0.dp))

                // 给药周期类型选择
                ScheduleTypeSection(
                    selectedType = scheduleType,
                    onTypeSelected = { scheduleType = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 根据周期类型显示不同的配置
                when (scheduleType) {
                    MedicationPlan.ScheduleType.DAILY -> {
                        // 每天：只需要选择时间
                    }
                    MedicationPlan.ScheduleType.WEEKLY -> {
                        // 每周：选择星期几
                        DaysOfWeekSection(
                            selectedDays = daysOfWeek,
                            onDayToggled = { day ->
                                daysOfWeek = if (daysOfWeek.contains(day)) {
                                    daysOfWeek - day
                                } else {
                                    daysOfWeek + day
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    MedicationPlan.ScheduleType.CUSTOM -> {
                        // 自定义：输入间隔天数
                        OutlinedTextField(
                            value = intervalDays,
                            onValueChange = { intervalDays = it },
                            label = { Text(stringResource(R.string.plan_sheet_interval_days_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // 时间选择
                TimeOfDaySection(
                    times = timeOfDay,
                    onAddTime = {
                        timeOfDay = timeOfDay + LocalTime.of(9, 0)
                    },
                    onRemoveTime = { index ->
                        if (timeOfDay.size > 1) {
                            timeOfDay = timeOfDay.filterIndexed { i, _ -> i != index }
                        }
                    },
                    onEditTime = { index ->
                        timeIndexToEdit = index
                        showTimePicker = true
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 删除按钮（仅编辑时显示）
                    if (planToEdit != null && onDelete != null) {
                        OutlinedButton(
                            onClick = {
                                onDelete(planToEdit.id)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.common_delete))
                        }
                    }

                    // 取消按钮
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.common_cancel))
                    }

                    // 保存按钮
                    Button(
                        onClick = {
                            val doseMG = doseMGText.toDoubleOrNull() ?: 0.0
                            
                            // 构建extras
                            val extras = mutableMapOf<DoseEvent.ExtraKey, Double>()
                            if (selectedRoute == Route.SUBLINGUAL) {
                                extras[DoseEvent.ExtraKey.SUBLINGUAL_TIER] = sublingualTier.ordinal.toDouble()
                            }
                            
                            val plan = MedicationPlan(
                                id = planToEdit?.id ?: UUID.randomUUID(),
                                name = name,
                                route = selectedRoute,
                                ester = selectedEster,
                                doseMG = doseMG,
                                scheduleType = scheduleType,
                                timeOfDay = timeOfDay,
                                daysOfWeek = daysOfWeek,
                                intervalDays = intervalDays.toIntOrNull() ?: 1,
                                isEnabled = planToEdit?.isEnabled ?: true,
                                extras = extras,
                                createdAt = planToEdit?.createdAt ?: System.currentTimeMillis()
                            )
                            onSave(plan)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = isValid
                    ) {
                        Text(stringResource(R.string.common_save))
                    }
                }
            }
        }
    }

    // 时间选择器
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = timeOfDay.getOrNull(timeIndexToEdit)?.hour ?: 9,
            initialMinute = timeOfDay.getOrNull(timeIndexToEdit)?.minute ?: 0,
            is24Hour = true
        )

        TimePickerDialog(
            onDismiss = { showTimePicker = false },
            onConfirm = {
                val newTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                timeOfDay = timeOfDay.mapIndexed { index, time ->
                    if (index == timeIndexToEdit) newTime else time
                }
                showTimePicker = false
            }
        ) {
            TimePicker(state = timePickerState)
        }
    }
}

/**
 * 给药途径选择组件
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RouteSelectionSection(
    selectedRoute: Route,
    onRouteSelected: (Route) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.plan_sheet_route_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        val routes = Route.values().filter { it != Route.PATCH_REMOVE && it != Route.PATCH_APPLY }
        ButtonGroup(modifier = Modifier.fillMaxWidth()) {
            routes.forEachIndexed { index, route ->
                val interactionSource = remember(route) { MutableInteractionSource() }
                ToggleButton(
                    checked = selectedRoute == route,
                    onCheckedChange = { onRouteSelected(route) },
                    modifier = Modifier
                        .weight(1f)
                        .animateWidth(interactionSource),
                    interactionSource = interactionSource,
                    shapes = when {
                        index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        index == routes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    }
                ) {
                    val routeText = getRouteDisplayName(route)
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        // Invisible anchor text forces Box to be exactly 2 lines tall
                        Text(
                            text = routeText,
                            style = MaterialTheme.typography.bodySmall,
                            minLines = 2,
                            color = Color.Transparent
                        )
                        // Visible text is centered within the 2-line Box
                        Text(
                            text = routeText,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/**
 * 药物类型选择组件
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EsterSelectionSection(
    selectedEster: Ester,
    availableEsters: List<Ester>,
    onEsterSelected: (Ester) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.plan_sheet_ester_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        ButtonGroup(modifier = Modifier.fillMaxWidth()) {
            availableEsters.forEachIndexed { index, ester ->
                val interactionSource = remember(ester) { MutableInteractionSource() }
                ToggleButton(
                    checked = selectedEster == ester,
                    onCheckedChange = { onEsterSelected(ester) },
                    modifier = Modifier
                        .weight(1f)
                        .animateWidth(interactionSource),
                    interactionSource = interactionSource,
                    shapes = when {
                        index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        index == availableEsters.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    }
                ) {
                    val esterText = ester.name
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        // Invisible anchor text forces Box to be exactly 2 lines tall
                        Text(
                            text = esterText,
                            style = MaterialTheme.typography.bodySmall,
                            minLines = 2,
                            color = Color.Transparent
                        )
                        // Visible text is centered within the 2-line Box
                        Text(
                            text = esterText,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/**
 * 给药周期类型选择组件
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScheduleTypeSection(
    selectedType: MedicationPlan.ScheduleType,
    onTypeSelected: (MedicationPlan.ScheduleType) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.plan_sheet_schedule_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        val types = MedicationPlan.ScheduleType.values()
        ButtonGroup(modifier = Modifier.fillMaxWidth()) {
            types.forEachIndexed { index, type ->
                val interactionSource = remember(type) { MutableInteractionSource() }
                ToggleButton(
                    checked = selectedType == type,
                    onCheckedChange = { onTypeSelected(type) },
                    modifier = Modifier
                        .weight(1f)
                        .animateWidth(interactionSource),
                    interactionSource = interactionSource,
                    shapes = when {
                        index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        index == types.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    }
                ) {
                    val scheduleText = when (type) {
                        MedicationPlan.ScheduleType.DAILY -> stringResource(R.string.plan_sheet_schedule_daily)
                        MedicationPlan.ScheduleType.WEEKLY -> stringResource(R.string.plan_sheet_schedule_weekly)
                        MedicationPlan.ScheduleType.CUSTOM -> stringResource(R.string.plan_sheet_schedule_custom)
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        // Invisible anchor text forces Box to be exactly 2 lines tall
                        Text(
                            text = scheduleText,
                            style = MaterialTheme.typography.bodySmall,
                            minLines = 2,
                            color = Color.Transparent
                        )
                        // Visible text is centered within the 2-line Box
                        Text(
                            text = scheduleText,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/**
 * 星期选择组件
 */
@Composable
private fun DaysOfWeekSection(
    selectedDays: Set<DayOfWeek>,
    onDayToggled: (DayOfWeek) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.plan_sheet_weekday_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val days = listOf(
                DayOfWeek.MONDAY to stringResource(R.string.weekday_mon_short),
                DayOfWeek.TUESDAY to stringResource(R.string.weekday_tue_short),
                DayOfWeek.WEDNESDAY to stringResource(R.string.weekday_wed_short),
                DayOfWeek.THURSDAY to stringResource(R.string.weekday_thu_short),
                DayOfWeek.FRIDAY to stringResource(R.string.weekday_fri_short),
                DayOfWeek.SATURDAY to stringResource(R.string.weekday_sat_short),
                DayOfWeek.SUNDAY to stringResource(R.string.weekday_sun_short)
            )

            days.forEach { (day, label) ->
                FilterChip(
                    selected = selectedDays.contains(day),
                    onClick = { onDayToggled(day) },
                    label = { Text(label) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * 时间选择组件
 */
@Composable
private fun TimeOfDaySection(
    times: List<LocalTime>,
    onAddTime: () -> Unit,
    onRemoveTime: (Int) -> Unit,
    onEditTime: (Int) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.plan_sheet_time_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onAddTime) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.plan_sheet_time_add)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        times.forEachIndexed { index, time ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedCard(
                    onClick = { onEditTime(index) },
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = time.format(DateTimeFormatter.ofPattern("HH:mm")),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                if (times.size > 1) {
                    IconButton(onClick = { onRemoveTime(index) }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.plan_sheet_time_remove)
                        )
                    }
                }
            }

            if (index < times.size - 1) {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/**
 * 时间选择器对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.common_confirm))
            }
        },
        text = { content() }
    )
}

/**
 * 舌下吸收等级选择器
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SublingualTierSelector(
    selectedTier: SublingualTier,
    onTierSelected: (SublingualTier) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.plan_sheet_sublingual_tier_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        val tiers = SublingualTier.values()
        ButtonGroup(modifier = Modifier.fillMaxWidth()) {
            tiers.forEachIndexed { index, tier ->
                val interactionSource = remember(tier) { MutableInteractionSource() }
                ToggleButton(
                    checked = selectedTier == tier,
                    onCheckedChange = { onTierSelected(tier) },
                    modifier = Modifier
                        .weight(1f)
                        .animateWidth(interactionSource),
                    interactionSource = interactionSource,
                    shapes = when {
                        index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        index == tiers.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = getSublingualTierName(tier),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selectedTier == tier) FontWeight.Bold else FontWeight.Normal
                        )
                        Text(
                            text = getSublingualTierDescription(tier),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

/**
 * 获取舌下吸收等级名称
 */
@Composable
private fun getSublingualTierName(tier: SublingualTier): String {
    return when (tier) {
        SublingualTier.QUICK -> stringResource(R.string.sublingual_tier_quick)
        SublingualTier.CASUAL -> stringResource(R.string.sublingual_tier_casual)
        SublingualTier.STANDARD -> stringResource(R.string.sublingual_tier_standard)
        SublingualTier.STRICT -> stringResource(R.string.sublingual_tier_strict)
    }
}

/**
 * 获取舌下吸收等级描述
 */
@Composable
private fun getSublingualTierDescription(tier: SublingualTier): String {
    return when (tier) {
        SublingualTier.QUICK -> stringResource(R.string.sublingual_tier_quick_desc)
        SublingualTier.CASUAL -> stringResource(R.string.sublingual_tier_casual_desc)
        SublingualTier.STANDARD -> stringResource(R.string.sublingual_tier_standard_desc)
        SublingualTier.STRICT -> stringResource(R.string.sublingual_tier_strict_desc)
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

/**
 * 根据给药途径获取可用的酯类列表
 */
private fun getAvailableEstersForRoute(route: Route): List<Ester> {
    return when (route) {
        Route.INJECTION -> listOf(Ester.E2, Ester.EB, Ester.EV, Ester.EC, Ester.EN)
        Route.ORAL -> listOf(Ester.E2, Ester.EV)
        Route.SUBLINGUAL -> listOf(Ester.E2, Ester.EV)
        Route.GEL -> listOf(Ester.E2)
        Route.PATCH_APPLY -> listOf(Ester.E2)
        Route.PATCH_REMOVE -> listOf(Ester.E2)
    }
}

/**
 * 预览
 */
@Preview(showBackground = true)
@Composable
private fun MedicationPlanBottomSheetPreview() {
    HRTTrackerTheme {
        // 预览占位
        Box(modifier = Modifier.fillMaxSize()) {
            Text("用药方案底部弹窗预览")
        }
    }
}
