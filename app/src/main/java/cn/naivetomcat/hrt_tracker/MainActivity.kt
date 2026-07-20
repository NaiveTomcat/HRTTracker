package cn.naivetomcat.hrt_tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.naivetomcat.hrt_tracker.data.AppDatabase
import cn.naivetomcat.hrt_tracker.data.ThemeMode
import cn.naivetomcat.hrt_tracker.data.DoseEventRepository
import cn.naivetomcat.hrt_tracker.data.MedicationPlanRepository
import cn.naivetomcat.hrt_tracker.data.SettingsDataStore
import cn.naivetomcat.hrt_tracker.navigation.AppNavigation
import cn.naivetomcat.hrt_tracker.reminder.ReminderManager
import cn.naivetomcat.hrt_tracker.ui.theme.HRTTrackerTheme
import cn.naivetomcat.hrt_tracker.viewmodel.HRTViewModel
import cn.naivetomcat.hrt_tracker.viewmodel.HRTViewModelFactory
import cn.naivetomcat.hrt_tracker.viewmodel.MedicationPlanViewModel
import cn.naivetomcat.hrt_tracker.viewmodel.MedicationPlanViewModelFactory
import cn.naivetomcat.hrt_tracker.viewmodel.SettingsViewModel
import cn.naivetomcat.hrt_tracker.viewmodel.SettingsViewModelFactory
import cn.naivetomcat.hrt_tracker.widget.updateAllHRTTrackerWidgets
import cn.naivetomcat.hrt_tracker.wear.WearDataLayer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 初始化数据库和仓库
        val database = AppDatabase.getDatabase(applicationContext)
        val doseEventRepository = DoseEventRepository(database.doseEventDao())
        val medicationPlanRepository = MedicationPlanRepository(database.medicationPlanDao())
        
        // 初始化设置数据存储
        val settingsDataStore = SettingsDataStore(applicationContext)
        
        setContent {
            // 创建 SettingsViewModel
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModelFactory(settingsDataStore)
            )
            
            // 获取用户设置
            val userSettings by settingsViewModel.userSettings.collectAsState()
            val systemInDarkTheme = isSystemInDarkTheme()
            val isDarkTheme = when (userSettings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemInDarkTheme
            }

            SideEffect {
                val transparent = Color.Transparent.toArgb()
                this@MainActivity.enableEdgeToEdge(
                    statusBarStyle = if (isDarkTheme) {
                        SystemBarStyle.dark(transparent)
                    } else {
                        SystemBarStyle.light(transparent, transparent)
                    },
                    navigationBarStyle = if (isDarkTheme) {
                        SystemBarStyle.dark(transparent)
                    } else {
                        SystemBarStyle.light(transparent, transparent)
                    }
                )
            }
            
            // 应用主题
            HRTTrackerTheme(
                themeMode = userSettings.themeMode,
                colorTheme = userSettings.colorTheme
            ) {
                // 创建 HRTViewModel，使用用户设置的体重
                val hrtViewModel: HRTViewModel = viewModel(
                    factory = HRTViewModelFactory(
                        repository = doseEventRepository,
                        medicationPlanRepository = medicationPlanRepository,
                        bodyWeightKG = userSettings.bodyWeight
                    )
                )
                val doseEvents by hrtViewModel.events.collectAsState()
                val pkState by hrtViewModel.pkState.collectAsState()
                LaunchedEffect(doseEvents) {
                    updateAllHRTTrackerWidgets(applicationContext)
                }
                
                // 创建 MedicationPlanViewModel
                val reminderManager = ReminderManager(applicationContext)
                val medicationPlanViewModel: MedicationPlanViewModel = viewModel(
                    factory = MedicationPlanViewModelFactory(medicationPlanRepository, reminderManager)
                )
                val medicationPlans by medicationPlanViewModel.plans.collectAsState()
                LaunchedEffect(
                    medicationPlans,
                    pkState.simulationResult,
                    pkState.currentConcentration
                ) {
                    WearDataLayer.syncDashboard(
                        applicationContext,
                        medicationPlans.filter { it.isEnabled }.take(2),
                        pkState.currentConcentration,
                        cn.naivetomcat.hrt_tracker.wear.sampleWearCurve(
                            pkState.simulationResult,
                            pkState.currentTimeH
                        )
                    )
                }
                
                // 应用启动时重新设置所有提醒
                LaunchedEffect(medicationPlanViewModel) {
                    medicationPlanViewModel.rescheduleAllReminders()
                }
                
                // 使用导航
                AppNavigation(
                    hrtViewModel = hrtViewModel,
                    settingsViewModel = settingsViewModel,
                    medicationPlanViewModel = medicationPlanViewModel
                )
            }
        }
    }

}
