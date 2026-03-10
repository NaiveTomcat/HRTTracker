package cn.naivetomcat.hrt_tracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cn.naivetomcat.hrt_tracker.R
import cn.naivetomcat.hrt_tracker.data.ColorTheme
import cn.naivetomcat.hrt_tracker.data.ThemeMode
import cn.naivetomcat.hrt_tracker.data.TimeFormat
import cn.naivetomcat.hrt_tracker.data.UserSettings
import cn.naivetomcat.hrt_tracker.ui.theme.HRTTrackerTheme
import cn.naivetomcat.hrt_tracker.viewmodel.ImportResult
import cn.naivetomcat.hrt_tracker.viewmodel.UpdateCheckResult
import kotlinx.coroutines.launch

private const val CLIPBOARD_LABEL_VERSION = "version"

/**
 * 设置页面
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    onBodyWeightChange: (Double) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onColorThemeChange: (ColorTheme) -> Unit,
    onTimeFormatChange: (TimeFormat) -> Unit,
    onAutoCheckUpdatesChange: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    updateCheckResult: UpdateCheckResult,
    onImportClick: () -> Unit = {},
    onImportFromClipboard: () -> Unit = {},
    onExportClick: () -> Unit = {},
    onExportToClipboard: () -> Unit = {},
    importResult: ImportResult = ImportResult.Idle,
    onDismissImportResult: () -> Unit = {},
    clipboardExportMessage: String? = null,
    onClipboardExportMessageShown: () -> Unit = {}
) {
    var showCopyrightDialog by remember { mutableStateOf(false) }
    var showDisclaimerDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(clipboardExportMessage) {
        if (clipboardExportMessage != null) {
            snackbarHostState.showSnackbar(clipboardExportMessage)
            onClipboardExportMessageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMediumEmphasized) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 体重输入
            BodyWeightSection(
                bodyWeight = settings.bodyWeight,
                onBodyWeightChange = onBodyWeightChange
            )

            // 夜间模式选择
            ThemeModeSection(
                currentMode = settings.themeMode,
                onModeChange = onThemeModeChange
            )

            // 颜色主题选择
            ColorThemeSection(
                currentTheme = settings.colorTheme,
                onThemeChange = onColorThemeChange
            )

            // 时间制式选择
            TimeFormatSection(
                currentFormat = settings.timeFormat,
                onFormatChange = onTimeFormatChange
            )

            // 检查更新
            UpdateSection(
                autoCheckUpdates = settings.autoCheckUpdates,
                onAutoCheckUpdatesChange = onAutoCheckUpdatesChange,
                onCheckForUpdates = onCheckForUpdates,
                updateCheckResult = updateCheckResult,
                snackbarHostState = snackbarHostState
            )

            // 数据导入/导出
            DataSection(
                onImportClick = onImportClick,
                onImportFromClipboard = onImportFromClipboard,
                onExportClick = onExportClick,
                onExportToClipboard = onExportToClipboard
            )

            AboutSection(
                onCopyrightClick = { showCopyrightDialog = true },
                onDisclaimerClick = { showDisclaimerDialog = true }
            )
        }

        if (showCopyrightDialog) {
            AlertDialog(
                onDismissRequest = { showCopyrightDialog = false },
                title = { Text(stringResource(R.string.about_copyright_title)) },
                text = { Text(stringResource(R.string.about_copyright_content)) },
                confirmButton = {
                    TextButton(onClick = { showCopyrightDialog = false }) {
                        Text(stringResource(R.string.dialog_close))
                    }
                }
            )
        }

        if (showDisclaimerDialog) {
            AlertDialog(
                onDismissRequest = { showDisclaimerDialog = false },
                title = { Text(stringResource(R.string.about_disclaimer_title)) },
                text = { Text(stringResource(R.string.about_disclaimer_content)) },
                confirmButton = {
                    TextButton(onClick = { showDisclaimerDialog = false }) {
                        Text(stringResource(R.string.dialog_close))
                    }
                }
            )
        }

        // 导入结果提示
        when (val result = importResult) {
            is ImportResult.Success -> {
                AlertDialog(
                    onDismissRequest = onDismissImportResult,
                    title = { Text(stringResource(R.string.settings_import_json)) },
                    text = {
                        Text(stringResource(R.string.import_success, result.importedCount))
                    },
                    confirmButton = {
                        TextButton(onClick = onDismissImportResult) {
                            Text(stringResource(R.string.common_confirm))
                        }
                    }
                )
            }
            is ImportResult.Error -> {
                AlertDialog(
                    onDismissRequest = onDismissImportResult,
                    title = { Text(stringResource(R.string.settings_import_json)) },
                    text = {
                        Text(stringResource(R.string.import_error, result.message))
                    },
                    confirmButton = {
                        TextButton(onClick = onDismissImportResult) {
                            Text(stringResource(R.string.common_confirm))
                        }
                    }
                )
            }
            else -> {}
        }
    }
}

/**
 * 数据导入/导出部分
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DataSection(
    onImportClick: () -> Unit,
    onImportFromClipboard: () -> Unit,
    onExportClick: () -> Unit,
    onExportToClipboard: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_data_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            SegmentedListItem(
                onClick = onImportClick,
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 4),
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                leadingContent = {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = null
                    )
                },
                supportingContent = {
                    Text(stringResource(R.string.settings_import_json_desc))
                }
            ) {
                Text(stringResource(R.string.settings_import_json))
            }

            SegmentedListItem(
                onClick = onImportFromClipboard,
                shapes = ListItemDefaults.segmentedShapes(index = 1, count = 4),
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                leadingContent = {
                    Icon(
                        imageVector = Icons.Outlined.ContentPaste,
                        contentDescription = null
                    )
                },
                supportingContent = {
                    Text(stringResource(R.string.settings_import_clipboard_desc))
                }
            ) {
                Text(stringResource(R.string.settings_import_clipboard))
            }

            SegmentedListItem(
                onClick = onExportClick,
                shapes = ListItemDefaults.segmentedShapes(index = 2, count = 4),
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                leadingContent = {
                    Icon(
                        imageVector = Icons.Outlined.Upload,
                        contentDescription = null
                    )
                },
                supportingContent = {
                    Text(stringResource(R.string.settings_export_json_desc))
                }
            ) {
                Text(stringResource(R.string.settings_export_json))
            }

            SegmentedListItem(
                onClick = onExportToClipboard,
                shapes = ListItemDefaults.segmentedShapes(index = 3, count = 4),
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                leadingContent = {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = null
                    )
                },
                supportingContent = {
                    Text(stringResource(R.string.settings_export_clipboard_desc))
                }
            ) {
                Text(stringResource(R.string.settings_export_clipboard))
            }
        }
    }
}

/**
 * 体重输入部分
 */
@Composable
private fun BodyWeightSection(
    bodyWeight: Double,
    onBodyWeightChange: (Double) -> Unit
) {
    var weightText by remember(bodyWeight) { mutableStateOf(bodyWeight.toString()) }
    var isError by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_weight_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            text = stringResource(R.string.settings_weight_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        OutlinedTextField(
            value = weightText,
            onValueChange = { newValue ->
                weightText = newValue
                val weight = newValue.toDoubleOrNull()
                if (weight != null && weight > 0 && weight <= 300) {
                    isError = false
                    onBodyWeightChange(weight)
                } else {
                    isError = true
                }
            },
            label = { Text(stringResource(R.string.settings_weight_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = isError,
            supportingText = {
                if (isError) {
                    Text(stringResource(R.string.settings_weight_error))
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 夜间模式选择部分
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThemeModeSection(
    currentMode: ThemeMode,
    onModeChange: (ThemeMode) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_theme_mode_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            ThemeMode.entries.forEachIndexed { index, mode ->
                val label = when (mode) {
                    ThemeMode.LIGHT -> stringResource(R.string.settings_theme_mode_light)
                    ThemeMode.DARK -> stringResource(R.string.settings_theme_mode_dark)
                    ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_mode_system)
                }
                val description = when (mode) {
                    ThemeMode.LIGHT -> stringResource(R.string.settings_theme_mode_light_desc)
                    ThemeMode.DARK -> stringResource(R.string.settings_theme_mode_dark_desc)
                    ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_mode_system_desc)
                }
                val icon = when (mode) {
                    ThemeMode.LIGHT -> Icons.Outlined.LightMode
                    ThemeMode.DARK -> Icons.Outlined.DarkMode
                    ThemeMode.SYSTEM -> Icons.Outlined.PhoneAndroid
                }

                SegmentedListItem(
                    selected = currentMode == mode,
                    onClick = { onModeChange(mode) },
                    shapes = ListItemDefaults.segmentedShapes(
                        index = index,
                        count = ThemeMode.entries.size
                    ),
                    colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    leadingContent = {
                        Icon(
                            imageVector = icon,
                            contentDescription = null
                        )
                    },
                    trailingContent = {
                        RadioButton(
                            selected = currentMode == mode,
                            onClick = null
                        )
                    },
                    supportingContent = { Text(description) }
                ) {
                    Text(label)
                }

            }
        }
    }
}

/**
 * 颜色主题选择部分
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ColorThemeSection(
    currentTheme: ColorTheme,
    onThemeChange: (ColorTheme) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_color_theme_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            ColorTheme.entries.forEachIndexed { index, theme ->
                val label = when (theme) {
                    ColorTheme.DYNAMIC -> stringResource(R.string.settings_color_theme_dynamic)
                    ColorTheme.BUILTIN -> stringResource(R.string.settings_color_theme_builtin)
                }
                val description = when (theme) {
                    ColorTheme.DYNAMIC -> stringResource(R.string.settings_color_theme_dynamic_desc)
                    ColorTheme.BUILTIN -> stringResource(R.string.settings_color_theme_builtin_desc)
                }
                val icon = when (theme) {
                    ColorTheme.DYNAMIC -> Icons.Outlined.ColorLens
                    ColorTheme.BUILTIN -> Icons.Outlined.Palette
                }

                SegmentedListItem(
                    selected = currentTheme == theme,
                    onClick = { onThemeChange(theme) },
                    shapes = ListItemDefaults.segmentedShapes(
                        index = index,
                        count = ColorTheme.entries.size
                    ),
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    leadingContent = {
                        Icon(
                            imageVector = icon,
                            contentDescription = null
                        )
                    },
                    trailingContent = {
                        RadioButton(
                            selected = currentTheme == theme,
                            onClick = null
                        )
                    },
                    supportingContent = { Text(description) }
                ) {
                    Text(label)
                }
            }
        }
    }
}

/**
 * 检查更新部分
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TimeFormatSection(
    currentFormat: TimeFormat,
    onFormatChange: (TimeFormat) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_time_format_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            TimeFormat.entries.forEachIndexed { index, format ->
                val label = when (format) {
                    TimeFormat.SYSTEM -> stringResource(R.string.settings_time_format_system)
                    TimeFormat.HOUR_12 -> stringResource(R.string.settings_time_format_12h)
                    TimeFormat.HOUR_24 -> stringResource(R.string.settings_time_format_24h)
                }
                val description = when (format) {
                    TimeFormat.SYSTEM -> stringResource(R.string.settings_time_format_system_desc)
                    TimeFormat.HOUR_12 -> stringResource(R.string.settings_time_format_12h_desc)
                    TimeFormat.HOUR_24 -> stringResource(R.string.settings_time_format_24h_desc)
                }
                val icon = when (format) {
                    TimeFormat.SYSTEM -> Icons.Outlined.PhoneAndroid
                    TimeFormat.HOUR_12 -> Icons.Outlined.Schedule
                    TimeFormat.HOUR_24 -> Icons.Outlined.Schedule
                }

                SegmentedListItem(
                    selected = currentFormat == format,
                    onClick = { onFormatChange(format) },
                    shapes = ListItemDefaults.segmentedShapes(
                        index = index,
                        count = TimeFormat.entries.size
                    ),
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    leadingContent = {
                        Icon(
                            imageVector = icon,
                            contentDescription = null
                        )
                    },
                    trailingContent = {
                        RadioButton(
                            selected = currentFormat == format,
                            onClick = null
                        )
                    },
                    supportingContent = { Text(description) }
                ) {
                    Text(label)
                }
            }
        }
    }
}

/**
 * 检查更新部分
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpdateSection(
    autoCheckUpdates: Boolean,
    onAutoCheckUpdatesChange: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    updateCheckResult: UpdateCheckResult,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val currentVersion = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
    val scope = rememberCoroutineScope()
    val isChecking = updateCheckResult is UpdateCheckResult.Checking
    val checkingStatusText = when (updateCheckResult) {
        is UpdateCheckResult.Checking -> stringResource(R.string.update_checking)
        is UpdateCheckResult.UpToDate -> stringResource(R.string.update_up_to_date)
        is UpdateCheckResult.Error -> stringResource(R.string.update_check_error)
        is UpdateCheckResult.UpdateAvailable -> stringResource(R.string.update_available_hint, updateCheckResult.tagName)
        is UpdateCheckResult.UpdateAvailableDismissed -> stringResource(R.string.update_available_hint, updateCheckResult.tagName)
        is UpdateCheckResult.DebugBuild -> stringResource(R.string.update_debug_hint, updateCheckResult.tagName)
        is UpdateCheckResult.DebugBuildDismissed -> stringResource(R.string.update_debug_hint, updateCheckResult.tagName)
        else -> stringResource(R.string.update_idle_hint)
    }
    val versionCopiedText = stringResource(R.string.version_copied)

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_update_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // 自动检查更新开关
            SegmentedListItem(
                onClick = { onAutoCheckUpdatesChange(!autoCheckUpdates) },
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 3),
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                leadingContent = {
                    Icon(
                        imageVector = Icons.Outlined.SystemUpdate,
                        contentDescription = null
                    )
                },
                trailingContent = {
                    Switch(
                        checked = autoCheckUpdates,
                        onCheckedChange = onAutoCheckUpdatesChange,
                        thumbContent = if (autoCheckUpdates) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        } else {
                            null
                        }
                    )
                }
            ) {
                Text(stringResource(R.string.settings_auto_check_updates))
            }

            // 检查更新按钮
            SegmentedListItem(
                onClick = { if (!isChecking) onCheckForUpdates() },
                shapes = ListItemDefaults.segmentedShapes(index = 1, count = 3),
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                leadingContent = {
                    if (isChecking) {
                        LoadingIndicator(
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null
                        )
                    }
                },
                supportingContent = checkingStatusText?.let { { Text(it) } }
            ) {
                Text(
                    text = stringResource(R.string.settings_check_updates_now),
                    color = if (isChecking) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    else MaterialTheme.colorScheme.onSurface
                )
            }

            SegmentedListItem(
                onClick = {
                    val clipboardManager =
                        context.getSystemService(android.content.ClipboardManager::class.java)
                    val clip = android.content.ClipData.newPlainText(CLIPBOARD_LABEL_VERSION, currentVersion)
                    clipboardManager?.setPrimaryClip(clip)
                    scope.launch { snackbarHostState.showSnackbar(versionCopiedText) }
                },
                shapes = ListItemDefaults.segmentedShapes(index = 2, count = 3),
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                leadingContent = {
                    Icon(
                        imageVector = Icons.Outlined.PhoneAndroid,
                        contentDescription = null
                    )
                },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                supportingContent = {
                    Text(currentVersion)
                }
            ) {
                Text(stringResource(R.string.settings_current_version))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AboutSection(
    onCopyrightClick: () -> Unit,
    onDisclaimerClick: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.about_section_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            SegmentedListItem(
                onClick = onCopyrightClick,
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2),
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                leadingContent = {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null
                    )
                }
            ) {
                Text(stringResource(R.string.about_copyright_button))
            }

            SegmentedListItem(
                onClick = onDisclaimerClick,
                shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                leadingContent = {
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = null
                    )
                }
            ) {
                Text(stringResource(R.string.about_disclaimer_button))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    HRTTrackerTheme {
        SettingsScreen(
            settings = UserSettings(
                bodyWeight = 55.0,
                themeMode = ThemeMode.SYSTEM,
                colorTheme = ColorTheme.DYNAMIC
            ),
            onBodyWeightChange = {},
            onThemeModeChange = {},
            onColorThemeChange = {},
            onTimeFormatChange = {},
            onAutoCheckUpdatesChange = {},
            onCheckForUpdates = {},
            updateCheckResult = UpdateCheckResult.Idle
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenDarkPreview() {
    HRTTrackerTheme(themeMode = ThemeMode.DARK) {
        SettingsScreen(
            settings = UserSettings(
                bodyWeight = 60.0,
                themeMode = ThemeMode.DARK,
                colorTheme = ColorTheme.BUILTIN
            ),
            onBodyWeightChange = {},
            onThemeModeChange = {},
            onColorThemeChange = {},
            onTimeFormatChange = {},
            onAutoCheckUpdatesChange = {},
            onCheckForUpdates = {},
            updateCheckResult = UpdateCheckResult.Idle
        )
    }
}
