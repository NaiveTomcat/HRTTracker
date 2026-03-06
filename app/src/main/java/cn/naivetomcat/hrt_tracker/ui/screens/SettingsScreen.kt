package cn.naivetomcat.hrt_tracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cn.naivetomcat.hrt_tracker.R
import cn.naivetomcat.hrt_tracker.data.ColorTheme
import cn.naivetomcat.hrt_tracker.data.ThemeMode
import cn.naivetomcat.hrt_tracker.data.UserSettings
import cn.naivetomcat.hrt_tracker.ui.theme.HRTTrackerTheme
import cn.naivetomcat.hrt_tracker.viewmodel.UpdateCheckResult

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
    onAutoCheckUpdatesChange: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    updateCheckResult: UpdateCheckResult
) {
    var showCopyrightDialog by remember { mutableStateOf(false) }
    var showDisclaimerDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMediumEmphasized) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
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

            // 检查更新
            UpdateSection(
                autoCheckUpdates = settings.autoCheckUpdates,
                onAutoCheckUpdatesChange = onAutoCheckUpdatesChange,
                onCheckForUpdates = onCheckForUpdates,
                updateCheckResult = updateCheckResult
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
private fun UpdateSection(
    autoCheckUpdates: Boolean,
    onAutoCheckUpdatesChange: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    updateCheckResult: UpdateCheckResult
) {
    val isChecking = updateCheckResult is UpdateCheckResult.Checking
    val checkingStatusText = when (updateCheckResult) {
        is UpdateCheckResult.Checking -> stringResource(R.string.update_checking)
        is UpdateCheckResult.UpToDate -> stringResource(R.string.update_up_to_date)
        is UpdateCheckResult.Error -> stringResource(R.string.update_check_error)
        else -> null
    }

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
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2),
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
                        onCheckedChange = onAutoCheckUpdatesChange
                    )
                }
            ) {
                Text(stringResource(R.string.settings_auto_check_updates))
            }

            // 检查更新按钮
            SegmentedListItem(
                onClick = { if (!isChecking) onCheckForUpdates() },
                shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
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
            onAutoCheckUpdatesChange = {},
            onCheckForUpdates = {},
            updateCheckResult = UpdateCheckResult.Idle
        )
    }
}
