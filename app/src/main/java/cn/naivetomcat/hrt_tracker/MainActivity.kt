package cn.naivetomcat.hrt_tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cn.naivetomcat.hrt_tracker.ui.screens.MedicationRecordsScreen
import cn.naivetomcat.hrt_tracker.ui.theme.HRTTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HRTTrackerTheme {
                // 使用新的 MedicationRecordsScreen，已内置状态管理和底部弹窗
                MedicationRecordsScreen()
            }
        }
    }
}