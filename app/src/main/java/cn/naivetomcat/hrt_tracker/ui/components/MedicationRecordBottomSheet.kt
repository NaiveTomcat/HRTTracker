package cn.naivetomcat.hrt_tracker.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cn.naivetomcat.hrt_tracker.R
import cn.naivetomcat.hrt_tracker.pk.*
import cn.naivetomcat.hrt_tracker.ui.theme.HRTTrackerTheme
import cn.naivetomcat.hrt_tracker.ui.utils.getRouteDisplayName
import cn.naivetomcat.hrt_tracker.ui.utils.getRouteIcon
import java.text.SimpleDateFormat
import java.util.*

/**
 * 用于记住上次添加记录时的默认值（除时间外）
 */
data class RecordDefaults(
    val route: Route = Route.INJECTION,
    val ester: Ester = Ester.EV,
    val doseMG: Double = 0.0,
    val patchMode: PatchMode = PatchMode.DOSE,
    val patchRateUgPerDay: Double = 0.0,
    val sublingualTier: SublingualTier = SublingualTier.STANDARD
)

/**
 * 添加或编辑用药记录的底部弹窗
 *
 * @param showBottomSheet 是否显示底部弹窗
 * @param onDismiss 关闭回调
 * @param onSave 保存回调
 * @param onDelete 删除回调（仅编辑时）
 * @param eventToEdit 要编辑的事件（null表示新增）
 * @param defaults 添加新记录时的默认值（从上次记录继承）
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MedicationRecordBottomSheet(
    showBottomSheet: Boolean,
    onDismiss: () -> Unit,
    onSave: (DoseEvent) -> Unit,
    onDelete: ((UUID) -> Unit)? = null,
    eventToEdit: DoseEvent? = null,
    defaults: RecordDefaults? = null
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    // 表单状态 - 使用 eventToEdit 作为 key，确保每次打开时都重新初始化
    // 新建时使用默认值（如果有），编辑时使用记录的值
    var selectedRoute by remember(eventToEdit, showBottomSheet) { 
        mutableStateOf(eventToEdit?.route ?: defaults?.route ?: Route.INJECTION) 
    }
    var selectedEster by remember(eventToEdit, showBottomSheet) { 
        mutableStateOf(eventToEdit?.ester ?: defaults?.ester ?: Ester.EV) 
    }
    var selectedDateTime by remember(eventToEdit, showBottomSheet) { 
        mutableStateOf(
            if (eventToEdit != null) {
                Date((eventToEdit.timeH * 3600000).toLong())
            } else {
                Date() // 新建时使用当前时间
            }
        )
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // 剂量相关状态
    var rawDoseText by remember(eventToEdit, showBottomSheet) { 
        mutableStateOf(
            if (eventToEdit != null && eventToEdit.route == Route.PATCH_APPLY && 
                eventToEdit.extras.containsKey(DoseEvent.ExtraKey.RELEASE_RATE_UG_PER_DAY)) {
                "" // 贴片释放速率模式下，不显示剂量
            } else if (eventToEdit != null) {
                eventToEdit.doseMG.toString()
            } else if (defaults != null && defaults.doseMG > 0) {
                defaults.doseMG.toString()
            } else {
                ""
            }
        )
    }
    var e2DoseText by remember(eventToEdit, showBottomSheet) { 
        mutableStateOf(
            if (eventToEdit != null && eventToEdit.route != Route.PATCH_APPLY) {
                val e2 = eventToEdit.doseMG * Ester.toE2Factor(eventToEdit.ester)
                String.format("%.3f", e2)
            } else if (defaults != null && defaults.doseMG > 0) {
                val e2 = defaults.doseMG * Ester.toE2Factor(defaults.ester)
                String.format("%.3f", e2)
            } else {
                ""
            }
        )
    }
    var lastEditedField by remember(eventToEdit, showBottomSheet) { 
        // When editing an existing record, treat raw dose as the source of truth to avoid
        // floating-point precision loss from the E2-equivalence round-trip.
        mutableStateOf<DoseField>(if (eventToEdit != null) DoseField.RAW else DoseField.E2) 
    }

    // 贴片相关状态
    var patchMode by remember(eventToEdit, showBottomSheet) { 
        mutableStateOf(
            if (eventToEdit?.extras?.containsKey(DoseEvent.ExtraKey.RELEASE_RATE_UG_PER_DAY) == true) 
                PatchMode.RATE 
            else if (eventToEdit == null && defaults != null)
                defaults.patchMode
            else 
                PatchMode.DOSE
        ) 
    }
    var patchRateText by remember(eventToEdit, showBottomSheet) { 
        mutableStateOf(
            eventToEdit?.extras?.get(DoseEvent.ExtraKey.RELEASE_RATE_UG_PER_DAY)?.toString() 
                ?: (if (defaults != null && defaults.patchRateUgPerDay > 0) defaults.patchRateUgPerDay.toString() else "")
        ) 
    }

    // 舌下相关状态
    var sublingualTier by remember(eventToEdit, showBottomSheet) { 
        mutableStateOf(
            eventToEdit?.extras?.get(DoseEvent.ExtraKey.SUBLINGUAL_TIER)?.toInt()?.let { tier ->
                SublingualTier.values().getOrElse(tier) { SublingualTier.STANDARD }
            } ?: defaults?.sublingualTier ?: SublingualTier.STANDARD
        )
    }

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

    // 剂量互相转换
    LaunchedEffect(rawDoseText, selectedEster, lastEditedField) {
        if (lastEditedField == DoseField.RAW && rawDoseText.isNotEmpty()) {
            rawDoseText.toDoubleOrNull()?.let { raw ->
                val e2Equiv = raw * Ester.toE2Factor(selectedEster)
                e2DoseText = String.format("%.3f", e2Equiv)
            }
        }
    }

    LaunchedEffect(e2DoseText, selectedEster, lastEditedField) {
        if (lastEditedField == DoseField.E2 && e2DoseText.isNotEmpty()) {
            e2DoseText.toDoubleOrNull()?.let { e2 ->
                val raw = e2 / Ester.toE2Factor(selectedEster)
                rawDoseText = String.format("%.3f", raw)
            }
        }
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
                    text = if (eventToEdit != null) stringResource(R.string.record_sheet_edit_title) else stringResource(R.string.record_sheet_add_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // 时间选择
                DateTimeSection(
                    selectedDateTime = selectedDateTime,
                    onDateClick = { showDatePicker = true },
                    onTimeClick = { showTimePicker = true }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 给药途径选择
                RouteSelector(
                    selectedRoute = selectedRoute,
                    onRouteSelected = { selectedRoute = it }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 药物类型选择
                EsterSelector(
                    selectedEster = selectedEster,
                    availableEsters = availableEsters,
                    onEsterSelected = { selectedEster = it }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 剂量输入（根据给药途径和模式显示不同内容）
                // 贴片移除不需要剂量输入
                when {
                    selectedRoute == Route.PATCH_REMOVE -> {
                        // 不显示剂量输入
                    }
                    selectedRoute == Route.PATCH_APPLY && patchMode == PatchMode.RATE -> {
                        PatchRateInput(
                            rateText = patchRateText,
                            onRateChange = { patchRateText = it },
                            onModeChange = { patchMode = PatchMode.DOSE }
                        )
                    }
                    selectedRoute == Route.PATCH_APPLY -> {
                        PatchDoseInput(
                            rawDoseText = rawDoseText,
                            selectedEster = selectedEster,
                            onRawDoseChange = {
                                rawDoseText = it
                                lastEditedField = DoseField.RAW
                            },
                            onModeChange = { patchMode = PatchMode.RATE }
                        )
                    }
                    else -> {
                        DoseInputSection(
                            rawDoseText = rawDoseText,
                            e2DoseText = e2DoseText,
                            selectedEster = selectedEster,
                            onRawDoseChange = {
                                rawDoseText = it
                                lastEditedField = DoseField.RAW
                            },
                            onE2DoseChange = {
                                e2DoseText = it
                                lastEditedField = DoseField.E2
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 舌下含服特殊选项
                AnimatedVisibility(visible = selectedRoute == Route.SUBLINGUAL) {
                    Column {
                        SublingualTierSelector(
                            selectedTier = sublingualTier,
                            onTierSelected = { sublingualTier = it }
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }

                // 按钮区域
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 删除按钮（仅编辑时显示）
                    if (eventToEdit != null && onDelete != null) {
                        OutlinedButton(
                            onClick = {
                                onDelete(eventToEdit.id)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
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
                            val timeH = selectedDateTime.time / 3600000.0
                            val doseMG = when {
                                selectedRoute == Route.PATCH_REMOVE -> 0.0
                                selectedRoute == Route.PATCH_APPLY && patchMode == PatchMode.RATE -> 0.0
                                else -> rawDoseText.toDoubleOrNull() ?: 0.0
                            }

                            val extras = mutableMapOf<DoseEvent.ExtraKey, Double>()
                            
                            // 添加贴片释放速率
                            if (selectedRoute == Route.PATCH_APPLY && patchMode == PatchMode.RATE) {
                                patchRateText.toDoubleOrNull()?.let {
                                    extras[DoseEvent.ExtraKey.RELEASE_RATE_UG_PER_DAY] = it
                                }
                            }
                            
                            // 添加舌下档位
                            if (selectedRoute == Route.SUBLINGUAL) {
                                extras[DoseEvent.ExtraKey.SUBLINGUAL_TIER] = sublingualTier.ordinal.toDouble()
                            }

                            val event = DoseEvent(
                                id = eventToEdit?.id ?: UUID.randomUUID(),
                                route = selectedRoute,
                                timeH = timeH,
                                doseMG = doseMG,
                                ester = selectedEster,
                                extras = extras
                            )
                            onSave(event)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = when {
                            selectedRoute == Route.PATCH_REMOVE -> true
                            selectedRoute == Route.PATCH_APPLY && patchMode == PatchMode.RATE -> patchRateText.toDoubleOrNull() != null && patchRateText.toDoubleOrNull()!! > 0
                            else -> rawDoseText.toDoubleOrNull() != null && rawDoseText.toDoubleOrNull()!! > 0
                        }
                    ) {
                        Text(stringResource(R.string.common_save))
                    }
                }
            }
        }
    }

    // Date Picker
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDateTime.time
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val calendar = Calendar.getInstance().apply {
                            timeInMillis = millis
                            set(Calendar.HOUR_OF_DAY, selectedDateTime.hours)
                            set(Calendar.MINUTE, selectedDateTime.minutes)
                        }
                        selectedDateTime = calendar.time
                    }
                    showDatePicker = false
                }) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Time Picker
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedDateTime.hours,
            initialMinute = selectedDateTime.minutes,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val calendar = Calendar.getInstance().apply {
                        time = selectedDateTime
                        set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                        set(Calendar.MINUTE, timePickerState.minute)
                    }
                    selectedDateTime = calendar.time
                    showTimePicker = false
                }) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
            text = {
                TimePicker(state = timePickerState)
            }
        )
    }
}

/**
 * 日期时间选择部分
 */
@Composable
private fun DateTimeSection(
    selectedDateTime: Date,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.record_sheet_time_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 日期选择
            ElevatedCard(
                onClick = onDateClick,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = SimpleDateFormat("yyyy-MM-dd", LocalLocale.current.platformLocale).format(selectedDateTime),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // 时间选择
            ElevatedCard(
                onClick = onTimeClick,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = SimpleDateFormat("HH:mm", LocalLocale.current.platformLocale).format(selectedDateTime),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

/**
 * 给药途径选择器
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RouteSelector(
    selectedRoute: Route,
    onRouteSelected: (Route) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.record_sheet_route_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        var expanded by remember { mutableStateOf(false) }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = getRouteDisplayName(selectedRoute),
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                leadingIcon = {
                    Icon(getRouteIcon(selectedRoute), contentDescription = null)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                Route.values().forEach { route ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(getRouteIcon(route), contentDescription = null)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(getRouteDisplayName(route))
                            }
                        },
                        onClick = {
                            onRouteSelected(route)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * 药物类型选择器
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EsterSelector(
    selectedEster: Ester,
    availableEsters: List<Ester>,
    onEsterSelected: (Ester) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.record_sheet_ester_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        var expanded by remember { mutableStateOf(false) }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = getEsterDisplayName(selectedEster),
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                availableEsters.forEach { ester ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    getEsterDisplayName(ester),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    ester.fullName(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            onEsterSelected(ester)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * 剂量输入部分
 */
@Composable
private fun DoseInputSection(
    rawDoseText: String,
    e2DoseText: String,
    selectedEster: Ester,
    onRawDoseChange: (String) -> Unit,
    onE2DoseChange: (String) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.record_sheet_dose_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 原始剂量输入
            OutlinedTextField(
                value = rawDoseText,
                onValueChange = onRawDoseChange,
                label = {
                    Text(
                        if (selectedEster == Ester.E2) {
                            stringResource(R.string.record_sheet_dose_label)
                        } else {
                            stringResource(R.string.record_sheet_dose_label_with_ester, selectedEster.name)
                        }
                    )
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
                trailingIcon = {
                    Text(stringResource(R.string.unit_mg), style = MaterialTheme.typography.bodySmall)
                }
            )

            // E2等效剂量（仅非E2时显示）
            if (selectedEster != Ester.E2) {
                OutlinedTextField(
                    value = e2DoseText,
                    onValueChange = onE2DoseChange,
                    label = { Text(stringResource(R.string.record_sheet_e2_equivalent_label)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    trailingIcon = {
                        Text(stringResource(R.string.unit_mg), style = MaterialTheme.typography.bodySmall)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(
                            alpha = 0.3f
                        )
                    )
                )
            }
        }

        // 显示转换因子提示
        if (selectedEster != Ester.E2) {
            Text(
                text = stringResource(R.string.record_sheet_conversion_factor, String.format("%.4f", Ester.toE2Factor(selectedEster))),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    }
}

/**
 * 贴片剂量输入
 */
@Composable
private fun PatchDoseInput(
    rawDoseText: String,
    selectedEster: Ester,
    onRawDoseChange: (String) -> Unit,
    onModeChange: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.record_sheet_patch_total_dose),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onModeChange) {
                Text(stringResource(R.string.record_sheet_switch_to_rate))
            }
        }

        OutlinedTextField(
            value = rawDoseText,
            onValueChange = onRawDoseChange,
            label = { Text(stringResource(R.string.record_sheet_total_dose_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                Text(stringResource(R.string.unit_mg), style = MaterialTheme.typography.bodySmall)
            }
        )
    }
}

/**
 * 贴片释放速率输入
 */
@Composable
private fun PatchRateInput(
    rateText: String,
    onRateChange: (String) -> Unit,
    onModeChange: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.record_sheet_patch_rate),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onModeChange) {
                Text(stringResource(R.string.record_sheet_switch_to_total_dose))
            }
        }

        OutlinedTextField(
            value = rateText,
            onValueChange = onRateChange,
            label = { Text(stringResource(R.string.record_sheet_rate_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                Text(stringResource(R.string.unit_ug_per_day), style = MaterialTheme.typography.bodySmall)
            }
        )

        Text(
            text = stringResource(R.string.record_sheet_rate_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp)
        )
    }
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
            text = stringResource(R.string.record_sheet_sublingual_tier_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

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
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = getSublingualTierName(tier),
                            style = MaterialTheme.typography.labelMedium,
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

// ===== 辅助函数 =====

private enum class DoseField {
    RAW, E2
}

enum class PatchMode {
    DOSE, RATE
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
        Route.PATCH_APPLY, Route.PATCH_REMOVE -> listOf(Ester.E2)
    }
}

/**
 * 获取酯类的显示名称
 */
@Composable
private fun getEsterDisplayName(ester: Ester): String {
    return when (ester) {
        Ester.E2 -> stringResource(R.string.record_sheet_ester_e2)
        Ester.EB -> stringResource(R.string.record_sheet_ester_eb)
        Ester.EV -> stringResource(R.string.record_sheet_ester_ev)
        Ester.EC -> stringResource(R.string.record_sheet_ester_ec)
        Ester.EN -> stringResource(R.string.record_sheet_ester_en)
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

// ============================================================================
// Previews
// ============================================================================

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(name = "新增用药记录", showBackground = true)
@Composable
private fun PreviewMedicationRecordBottomSheetAdd() {
    HRTTrackerTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                // 模拟表单内容
                Text(
                    text = "添加用药记录",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // 日期时间部分
                Text(
                    text = "给药时间",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ElevatedCard(
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(SimpleDateFormat("yyyy-MM-dd", LocalLocale.current.platformLocale).format(Date()))
                        }
                    }
                    ElevatedCard(
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(SimpleDateFormat("HH:mm", LocalLocale.current.platformLocale).format(Date()))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 给药途径
                Text(
                    text = "给药途径",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = "肌肉注射",
                    onValueChange = {},
                    readOnly = true,
                    leadingIcon = { Icon(getRouteIcon(Route.INJECTION), contentDescription = null) },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 药物类型
                Text(
                    text = "药物类型",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = "EV - 戊酸雌二醇",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 剂量输入
                Text(
                    text = "药物剂量",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = "5.0",
                        onValueChange = {},
                        label = { Text("EV 剂量 (mg)") },
                        trailingIcon = { Text("mg", style = MaterialTheme.typography.bodySmall) },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = "3.82",
                        onValueChange = {},
                        label = { Text("等效 E2 (mg)") },
                        trailingIcon = { Text("mg", style = MaterialTheme.typography.bodySmall) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {},
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消")
                    }
                    Button(
                        onClick = {},
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(name = "编辑注射记录", showBackground = true)
@Composable
private fun PreviewMedicationRecordBottomSheetEditInjection() {
    HRTTrackerTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                Text(
                    text = "编辑用药记录",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Text(
                    text = "给药时间",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ElevatedCard(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(SimpleDateFormat("yyyy-MM-dd", LocalLocale.current.platformLocale).format(Date(System.currentTimeMillis() - 7 * 24 * 3600000)))
                        }
                    }
                    ElevatedCard(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("14:30")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = "肌肉注射",
                    onValueChange = {},
                    label = { Text("给药途径") },
                    readOnly = true,
                    leadingIcon = { Icon(getRouteIcon(Route.INJECTION), contentDescription = null) },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = "EV - 戊酸雌二醇",
                    onValueChange = {},
                    label = { Text("药物类型") },
                    readOnly = true,
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = "5.0",
                        onValueChange = {},
                        label = { Text("EV 剂量 (mg)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = "3.82",
                        onValueChange = {},
                        label = { Text("等效 E2 (mg)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("删除")
                    }
                    OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) {
                        Text("取消")
                    }
                    Button(onClick = {}, modifier = Modifier.weight(1f)) {
                        Text("保存")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(name = "编辑舌下记录", showBackground = true)
@Composable
private fun PreviewMedicationRecordBottomSheetEditSublingual() {
    HRTTrackerTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                Text(
                    text = "编辑用药记录",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                OutlinedTextField(
                    value = "舌下含服",
                    onValueChange = {},
                    label = { Text("给药途径") },
                    readOnly = true,
                    leadingIcon = { Icon(getRouteIcon(Route.SUBLINGUAL), contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = "E2 - 雌二醇",
                    onValueChange = {},
                    label = { Text("药物类型") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = "1.0",
                    onValueChange = {},
                    label = { Text("剂量 (mg)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "舌下吸收等级",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val previewTiers = listOf("快速" to "~2分钟", "随意" to "~5分钟", "标准" to "~10分钟", "严格" to "~15分钟")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                ) {
                    previewTiers.forEachIndexed { index, (name, desc) ->
                        ToggleButton(
                            checked = index == 2,
                            onCheckedChange = {},
                            modifier = Modifier.weight(1f),
                            shapes = when {
                                index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                index == previewTiers.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            }
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                Text(name, style = MaterialTheme.typography.labelMedium)
                                Text(desc, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("删除")
                    }
                    OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) {
                        Text("取消")
                    }
                    Button(onClick = {}, modifier = Modifier.weight(1f)) {
                        Text("保存")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(name = "编辑贴片记录（释放速率）", showBackground = true)
@Composable
private fun PreviewMedicationRecordBottomSheetEditPatchRate() {
    HRTTrackerTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                Text(
                    text = "编辑用药记录",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                OutlinedTextField(
                    value = "贴片贴上",
                    onValueChange = {},
                    label = { Text("给药途径") },
                    readOnly = true,
                    leadingIcon = { Icon(getRouteIcon(Route.PATCH_APPLY), contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = "E2 - 雌二醇",
                    onValueChange = {},
                    label = { Text("药物类型") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "贴片释放速率",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = {}) {
                        Text("切换到总剂量")
                    }
                }

                OutlinedTextField(
                    value = "50",
                    onValueChange = {},
                    label = { Text("释放速率") },
                    trailingIcon = { Text("µg/天", style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "输入贴片标称的释放速率（如 50, 100 µg/天）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("删除")
                    }
                    OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) {
                        Text("取消")
                    }
                    Button(onClick = {}, modifier = Modifier.weight(1f)) {
                        Text("保存")
                    }
                }
            }
        }
    }
}
