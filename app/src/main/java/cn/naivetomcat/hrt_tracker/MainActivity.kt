package cn.naivetomcat.hrt_tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.naivetomcat.hrt_tracker.data.AppDatabase
import cn.naivetomcat.hrt_tracker.data.DoseEventRepository
import cn.naivetomcat.hrt_tracker.navigation.AppNavigation
import cn.naivetomcat.hrt_tracker.ui.theme.HRTTrackerTheme
import cn.naivetomcat.hrt_tracker.viewmodel.HRTViewModel
import cn.naivetomcat.hrt_tracker.viewmodel.HRTViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // 初始化数据库和仓库
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = DoseEventRepository(database.doseEventDao())
        
        setContent {
            HRTTrackerTheme {
                // 创建 ViewModel
                val viewModel: HRTViewModel = viewModel(
                    factory = HRTViewModelFactory(
                        repository = repository,
                        bodyWeightKG = 55.0  // TODO: 从用户设置中获取
                    )
                )
                
                // 使用导航
                AppNavigation(viewModel = viewModel)
            }
        }
    }
}