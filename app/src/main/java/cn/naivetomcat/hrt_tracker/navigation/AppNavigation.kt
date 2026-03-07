package cn.naivetomcat.hrt_tracker.navigation

import android.os.SystemClock
import android.text.format.DateFormat
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlin.math.abs
import cn.naivetomcat.hrt_tracker.R
import cn.naivetomcat.hrt_tracker.data.TimeFormat
import cn.naivetomcat.hrt_tracker.ui.screens.HomeScreen
import cn.naivetomcat.hrt_tracker.ui.screens.MedicationPlansScreen
import cn.naivetomcat.hrt_tracker.ui.screens.MedicationRecordsScreen
import cn.naivetomcat.hrt_tracker.ui.screens.SettingsScreen
import cn.naivetomcat.hrt_tracker.viewmodel.HRTViewModel
import cn.naivetomcat.hrt_tracker.viewmodel.MedicationPlanViewModel
import cn.naivetomcat.hrt_tracker.viewmodel.SettingsViewModel
import cn.naivetomcat.hrt_tracker.viewmodel.UpdateCheckResult

private const val NAV_SPRING_DAMPING_RATIO = 0.72f
private const val NAV_SPRING_STIFFNESS = Spring.StiffnessLow
private const val NAV_CLICK_THROTTLE_MS = 200L
private const val NAV_SWIPE_THRESHOLD_DP = 60

/**
 * 应用主导航
 */
@Composable
fun AppNavigation(
    hrtViewModel: HRTViewModel,
    settingsViewModel: SettingsViewModel,
    medicationPlanViewModel: MedicationPlanViewModel
) {
    val navController = rememberNavController()
    var navDirection by remember { mutableIntStateOf(1) }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val updateCheckResult by settingsViewModel.updateCheckResult.collectAsState()
    val userSettings by settingsViewModel.userSettings.collectAsState()

    // 根据用户设置和设备语言区域计算是否使用24小时制
    val is24Hour = when (userSettings.timeFormat) {
        TimeFormat.SYSTEM -> DateFormat.is24HourFormat(context)
        TimeFormat.HOUR_12 -> false
        TimeFormat.HOUR_24 -> true
    }

    @Suppress("DEPRECATION")
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: Exception) { "" }
    }

    // 应用启动时自动检查更新
    LaunchedEffect(Unit) {
        settingsViewModel.triggerAutoCheckOnStartup(versionName)
    }

    // 有新版本时显示更新弹窗
    if (updateCheckResult is UpdateCheckResult.UpdateAvailable) {
        val result = updateCheckResult as UpdateCheckResult.UpdateAvailable
        AlertDialog(
            onDismissRequest = { settingsViewModel.dismissUpdateCheckResult() },
            title = { Text(stringResource(R.string.update_available_title)) },
            text = {
                Text(stringResource(R.string.update_available_content, result.tagName))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        uriHandler.openUri(result.releaseUrl)
                        settingsViewModel.dismissUpdateCheckResult()
                    }
                ) {
                    Text(stringResource(R.string.update_go_to_release))
                }
            },
            dismissButton = {
                TextButton(onClick = { settingsViewModel.dismissUpdateCheckResult() }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
        ),
        bottomBar = {
            BottomNavigationBar(
                navController = navController,
                onDirectionChange = { navDirection = it }
            )
        }
    ) { innerPadding ->
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRouteState = rememberUpdatedState(navBackStackEntry?.destination?.route)
        var swipeDelta by remember { mutableFloatStateOf(0f) }
        val swipeThresholdPx = with(LocalDensity.current) { NAV_SWIPE_THRESHOLD_DP.dp.toPx() }

        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { swipeDelta = 0f },
                        onDragCancel = { swipeDelta = 0f },
                        onDragEnd = {
                            if (abs(swipeDelta) > swipeThresholdPx) {
                                // swipeDelta > 0 means finger moved right -> go to previous tab
                                val direction = if (swipeDelta > 0) -1 else 1
                                val currentIndex = screenIndex(currentRouteState.value)
                                val targetIndex = currentIndex + direction
                                if (currentIndex != -1 && targetIndex in Screen.entries.indices) {
                                    navDirection = direction
                                    navController.navigate(Screen.entries[targetIndex].route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        }
                    ) { _, dragAmount ->
                        swipeDelta += dragAmount
                    }
                }
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.HOME.route,
                modifier = Modifier.fillMaxSize(),
            enterTransition = {
                val initialIndex = screenIndex(initialState.destination.route)
                val targetIndex = screenIndex(targetState.destination.route)
                if (initialIndex == -1 || targetIndex == -1 || initialIndex == targetIndex) {
                    EnterTransition.None
                } else if (navDirection > 0) {
                    slideInHorizontally(
                        animationSpec = spring(
                            dampingRatio = NAV_SPRING_DAMPING_RATIO,
                            stiffness = NAV_SPRING_STIFFNESS
                        ),
                        initialOffsetX = { it }
                    )
                } else {
                    slideInHorizontally(
                        animationSpec = spring(
                            dampingRatio = NAV_SPRING_DAMPING_RATIO,
                            stiffness = NAV_SPRING_STIFFNESS
                        ),
                        initialOffsetX = { -it }
                    )
                }
            },
            exitTransition = {
                val initialIndex = screenIndex(initialState.destination.route)
                val targetIndex = screenIndex(targetState.destination.route)
                if (initialIndex == -1 || targetIndex == -1 || initialIndex == targetIndex) {
                    ExitTransition.None
                } else if (navDirection > 0) {
                    slideOutHorizontally(
                        animationSpec = spring(
                            dampingRatio = NAV_SPRING_DAMPING_RATIO,
                            stiffness = NAV_SPRING_STIFFNESS
                        ),
                        targetOffsetX = { -it }
                    )
                } else {
                    slideOutHorizontally(
                        animationSpec = spring(
                            dampingRatio = NAV_SPRING_DAMPING_RATIO,
                            stiffness = NAV_SPRING_STIFFNESS
                        ),
                        targetOffsetX = { it }
                    )
                }
            },
            popEnterTransition = {
                val initialIndex = screenIndex(initialState.destination.route)
                val targetIndex = screenIndex(targetState.destination.route)
                if (initialIndex == -1 || targetIndex == -1 || initialIndex == targetIndex) {
                    EnterTransition.None
                } else if (navDirection > 0) {
                    slideInHorizontally(
                        animationSpec = spring(
                            dampingRatio = NAV_SPRING_DAMPING_RATIO,
                            stiffness = NAV_SPRING_STIFFNESS
                        ),
                        initialOffsetX = { it }
                    )
                } else {
                    slideInHorizontally(
                        animationSpec = spring(
                            dampingRatio = NAV_SPRING_DAMPING_RATIO,
                            stiffness = NAV_SPRING_STIFFNESS
                        ),
                        initialOffsetX = { -it }
                    )
                }
            },
            popExitTransition = {
                val initialIndex = screenIndex(initialState.destination.route)
                val targetIndex = screenIndex(targetState.destination.route)
                if (initialIndex == -1 || targetIndex == -1 || initialIndex == targetIndex) {
                    ExitTransition.None
                } else if (navDirection > 0) {
                    slideOutHorizontally(
                        animationSpec = spring(
                            dampingRatio = NAV_SPRING_DAMPING_RATIO,
                            stiffness = NAV_SPRING_STIFFNESS
                        ),
                        targetOffsetX = { -it }
                    )
                } else {
                    slideOutHorizontally(
                        animationSpec = spring(
                            dampingRatio = NAV_SPRING_DAMPING_RATIO,
                            stiffness = NAV_SPRING_STIFFNESS
                        ),
                        targetOffsetX = { it }
                    )
                }
            },
        ) {
            composable(Screen.HOME.route) {
                HomeScreen(viewModel = hrtViewModel, is24Hour = is24Hour)
            }
            composable(Screen.RECORDS.route) {
                MedicationRecordsScreen(viewModel = hrtViewModel, is24Hour = is24Hour)
            }
            composable(Screen.MEDICATION_PLANS.route) {
                MedicationPlansScreen(viewModel = medicationPlanViewModel, is24Hour = is24Hour)
            }
            composable(Screen.SETTINGS.route) {
                SettingsScreen(
                    settings = userSettings,
                    onBodyWeightChange = settingsViewModel::updateBodyWeight,
                    onThemeModeChange = settingsViewModel::updateThemeMode,
                    onColorThemeChange = settingsViewModel::updateColorTheme,
                    onTimeFormatChange = settingsViewModel::updateTimeFormat,
                    onAutoCheckUpdatesChange = settingsViewModel::updateAutoCheckUpdates,
                    onCheckForUpdates = { settingsViewModel.checkForUpdates(versionName) },
                    updateCheckResult = updateCheckResult
                )
            }
        }
        } // Box
    }
}

/**
 * 底部导航栏
 */
@Composable
private fun BottomNavigationBar(
    navController: NavHostController,
    onDirectionChange: (Int) -> Unit
) {
    var lastNavigateAt by remember { mutableLongStateOf(0L) }

    val items = listOf(
        BottomNavItem(
            screen = Screen.HOME,
            selectedIcon = Icons.Filled.Home,
            unselectedIcon = Icons.Outlined.Home,
            label = stringResource(R.string.nav_home)
        ),
        BottomNavItem(
            screen = Screen.RECORDS,
            selectedIcon = Icons.Filled.List,
            unselectedIcon = Icons.Outlined.List,
            label = stringResource(R.string.nav_records)
        ),
        BottomNavItem(
            screen = Screen.MEDICATION_PLANS,
            selectedIcon = Icons.Filled.MedicalServices,
            unselectedIcon = Icons.Outlined.MedicalServices,
            label = stringResource(R.string.nav_plans)
        ),
        BottomNavItem(
            screen = Screen.SETTINGS,
            selectedIcon = Icons.Filled.Settings,
            unselectedIcon = Icons.Outlined.Settings,
            label = stringResource(R.string.nav_settings)
        )
    )

    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        items.forEach { item ->
            val selected = currentDestination?.hierarchy?.any {
                it.route == item.screen.route
            } == true

            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label
                    )
                },
                label = { Text(item.label) },
                selected = selected,
                onClick = {
                    if (selected) return@NavigationBarItem

                    val currentIndex = screenIndex(currentDestination?.route)
                    val targetIndex = screenIndex(item.screen.route)
                    if (currentIndex != -1 && targetIndex != -1) {
                        onDirectionChange(if (targetIndex > currentIndex) 1 else -1)
                    }

                    val now = SystemClock.elapsedRealtime()
                    if (now - lastNavigateAt < NAV_CLICK_THROTTLE_MS) {
                        return@NavigationBarItem
                    }
                    lastNavigateAt = now

                    navController.navigate(item.screen.route) {
                        // 弹出到导航图的起始目的地
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        // 避免在重新选择同一项时创建多个副本
                        launchSingleTop = true
                        // 在重新选择之前选择的项时恢复状态
                        restoreState = true
                    }
                }
            )
        }
    }
}

/**
 * 底部导航项
 */
private data class BottomNavItem(
    val screen: Screen,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val label: String
)

private fun screenIndex(route: String?): Int {
    return Screen.entries.indexOfFirst { it.route == route }
}
