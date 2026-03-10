package cn.naivetomcat.hrt_tracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.util.Log
import cn.naivetomcat.hrt_tracker.data.ColorTheme
import cn.naivetomcat.hrt_tracker.data.SettingsDataStore
import cn.naivetomcat.hrt_tracker.data.ThemeMode
import cn.naivetomcat.hrt_tracker.data.TimeFormat
import cn.naivetomcat.hrt_tracker.data.UserSettings
import cn.naivetomcat.hrt_tracker.utils.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 更新检查结果
 */
sealed class UpdateCheckResult {
    data object Idle : UpdateCheckResult()
    data object Checking : UpdateCheckResult()
    data class UpdateAvailable(val tagName: String, val releaseUrl: String) : UpdateCheckResult()
    data class UpdateAvailableDismissed(val tagName: String) : UpdateCheckResult()
    data class DebugBuild(val tagName: String, val releaseUrl: String) : UpdateCheckResult()
    data class DebugBuildDismissed(val tagName: String) : UpdateCheckResult()
    data object UpToDate : UpdateCheckResult()
    data object Error : UpdateCheckResult()
}

/**
 * 设置 ViewModel
 * 管理用户设置和偏好
 */
class SettingsViewModel(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"
    }

    /**
     * 用户设置状态
     */
    val userSettings: StateFlow<UserSettings> = settingsDataStore.userSettings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserSettings()
        )

    /**
     * 更新检查结果状态
     */
    private val _updateCheckResult = MutableStateFlow<UpdateCheckResult>(UpdateCheckResult.Idle)
    val updateCheckResult: StateFlow<UpdateCheckResult> = _updateCheckResult.asStateFlow()

    /**
     * 更新体重
     */
    fun updateBodyWeight(weight: Double) {
        viewModelScope.launch {
            settingsDataStore.updateBodyWeight(weight)
        }
    }

    /**
     * 更新主题模式
     */
    fun updateThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsDataStore.updateThemeMode(mode)
        }
    }

    /**
     * 更新颜色主题
     */
    fun updateColorTheme(theme: ColorTheme) {
        viewModelScope.launch {
            settingsDataStore.updateColorTheme(theme)
        }
    }

    /**
     * 更新自动检查更新开关
     */
    fun updateAutoCheckUpdates(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.updateAutoCheckUpdates(enabled)
        }
    }

    /**
     * 更新时间制式
     */
    fun updateTimeFormat(format: TimeFormat) {
        viewModelScope.launch {
            settingsDataStore.updateTimeFormat(format)
        }
    }

    /**
     * 在应用启动时，若自动检查更新已开启则执行检查
     */
    fun triggerAutoCheckOnStartup(versionName: String) {
        viewModelScope.launch {
            val settings = settingsDataStore.userSettings.first()
            if (settings.autoCheckUpdates) {
                performUpdateCheck(versionName)
            }
        }
    }

    /**
     * 手动触发检查更新
     */
    fun checkForUpdates(versionName: String) {
        viewModelScope.launch {
            performUpdateCheck(versionName)
        }
    }

    /**
     * 关闭更新弹窗。若当前结果为 UpdateAvailable 或 DebugBuild，
     * 则保留版本信息（转为 Dismissed 状态），否则重置为 Idle。
     */
    fun dismissUpdateCheckResult() {
        _updateCheckResult.value = when (val current = _updateCheckResult.value) {
            is UpdateCheckResult.UpdateAvailable -> UpdateCheckResult.UpdateAvailableDismissed(current.tagName)
            is UpdateCheckResult.DebugBuild -> UpdateCheckResult.DebugBuildDismissed(current.tagName)
            else -> UpdateCheckResult.Idle
        }
    }

    private suspend fun performUpdateCheck(versionName: String) {
        _updateCheckResult.value = UpdateCheckResult.Checking
        try {
            val release = withContext(Dispatchers.IO) {
                UpdateChecker.fetchLatestRelease()
            }
            val isDebug = versionName.contains("debug", ignoreCase = true)
            _updateCheckResult.value = when {
                release == null -> UpdateCheckResult.Error
                UpdateChecker.isNewerVersion(release.tagName, versionName) ->
                    UpdateCheckResult.UpdateAvailable(release.tagName, release.releaseUrl)
                isDebug ->
                    UpdateCheckResult.DebugBuild(release.tagName, release.releaseUrl)
                else -> UpdateCheckResult.UpToDate
            }
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed", e)
            _updateCheckResult.value = UpdateCheckResult.Error
        }
    }
}

/**
 * SettingsViewModel 工厂类
 */
class SettingsViewModelFactory(
    private val settingsDataStore: SettingsDataStore
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(settingsDataStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
