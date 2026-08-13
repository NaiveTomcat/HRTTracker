package cn.naivetomcat.hrt_tracker.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cn.naivetomcat.hrt_tracker.data.MedicationPlan
import cn.naivetomcat.hrt_tracker.data.MedicationPlanRepository
import cn.naivetomcat.hrt_tracker.reminder.ReminderManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 用药方案 ViewModel
 */
class MedicationPlanViewModel(
    private val repository: MedicationPlanRepository,
    private val reminderManager: ReminderManager
) : ViewModel() {

    /**
     * 所有用药方案
     */
    val plans: StateFlow<List<MedicationPlan>> = repository.getAllPlans()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * 启用的用药方案
     */
    val enabledPlans: StateFlow<List<MedicationPlan>> = repository.getEnabledPlans()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * 添加或更新用药方案
     */
    fun upsertPlan(plan: MedicationPlan) {
        viewModelScope.launch {
            repository.upsertPlan(plan)
            
            // 更新提醒
            if (plan.isEnabled) {
                reminderManager.scheduleReminder(plan)
            } else {
                reminderManager.cancelReminder(plan.id)
            }
        }
    }

    /**
     * 删除用药方案
     */
    fun deletePlan(id: UUID) {
        viewModelScope.launch {
            repository.deletePlan(id)
            
            // 取消提醒
            reminderManager.cancelReminder(id)
        }
    }

    /**
     * 启用/禁用用药方案
     */
    fun togglePlanEnabled(id: UUID, isEnabled: Boolean) {
        viewModelScope.launch {
            repository.updatePlanEnabled(id, isEnabled)
            
            // 更新提醒
            if (isEnabled) {
                repository.getPlanById(id)?.let { plan ->
                    reminderManager.scheduleReminder(plan)
                }
            } else {
                reminderManager.cancelReminder(id)
            }
        }
    }

    /**
     * 根据ID获取用药方案
     */
    suspend fun getPlanById(id: UUID): MedicationPlan? {
        return repository.getPlanById(id)
    }

    /**
     * 重新设置所有提醒（应用启动时调用）
     */
    fun rescheduleAllReminders() {
        viewModelScope.launch {
            val allPlans = repository.getAllPlans().first()
            reminderManager.rescheduleAllReminders(allPlans)
        }
    }
}

/**
 * MedicationPlanViewModel Factory
 */
class MedicationPlanViewModelFactory(
    private val repository: MedicationPlanRepository,
    private val reminderManager: ReminderManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MedicationPlanViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MedicationPlanViewModel(repository, reminderManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
