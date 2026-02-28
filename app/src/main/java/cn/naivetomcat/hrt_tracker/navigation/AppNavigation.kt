package cn.naivetomcat.hrt_tracker.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.List
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import cn.naivetomcat.hrt_tracker.ui.screens.HomeScreen
import cn.naivetomcat.hrt_tracker.ui.screens.MedicationRecordsScreen
import cn.naivetomcat.hrt_tracker.viewmodel.HRTViewModel

/**
 * 应用主导航
 */
@Composable
fun AppNavigation(viewModel: HRTViewModel) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            BottomNavigationBar(navController = navController)
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.HOME.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.HOME.route) {
                HomeScreen(viewModel = viewModel)
            }
            composable(Screen.RECORDS.route) {
                MedicationRecordsScreen(viewModel = viewModel)
            }
        }
    }
}

/**
 * 底部导航栏
 */
@Composable
private fun BottomNavigationBar(navController: NavHostController) {
    val items = listOf(
        BottomNavItem(
            screen = Screen.HOME,
            selectedIcon = Icons.Filled.Home,
            unselectedIcon = Icons.Outlined.Home,
            label = "主页"
        ),
        BottomNavItem(
            screen = Screen.RECORDS,
            selectedIcon = Icons.Filled.List,
            unselectedIcon = Icons.Outlined.List,
            label = "记录"
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
