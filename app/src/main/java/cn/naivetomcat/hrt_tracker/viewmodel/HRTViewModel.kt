package cn.naivetomcat.hrt_tracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cn.naivetomcat.hrt_tracker.data.DoseEventRepository
import cn.naivetomcat.hrt_tracker.pk.DoseEvent
import cn.naivetomcat.hrt_tracker.pk.Route
import cn.naivetomcat.hrt_tracker.pk.SimulationEngine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * HRT Tracker ViewModel
 * 管理用药记录和药代动力学模拟
 */
class HRTViewModel(
    private val repository: DoseEventRepository,
    private val bodyWeightKG: Double = 55.0 // 默认体重，后续可以从用户设置中获取
) : ViewModel() {

    // 用药事件列表
    val events: StateFlow<List<DoseEvent>> = repository.getAllEvents()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // PK 状态
    private val _pkState = MutableStateFlow(PKState())
    val pkState: StateFlow<PKState> = _pkState.asStateFlow()

    init {
        // 监听事件变化，自动重新运行模拟
        viewModelScope.launch {
            events.collect { eventList ->
                // 无论列表是否为空，都运行模拟以更新状态
                runSimulation()
            }
        }
    }

    /**
     * 添加或更新用药事件
     */
    fun upsertEvent(event: DoseEvent) {
        viewModelScope.launch {
            repository.upsertEvent(event)
        }
    }

    /**
     * 删除用药事件
     */
    fun deleteEvent(id: UUID) {
        viewModelScope.launch {
            repository.deleteEvent(id)
        }
    }

    /**
     * 运行药代动力学模拟
     */
    fun runSimulation() {
        viewModelScope.launch {
            try {
                _pkState.update { it.copy(isSimulating = true, error = null) }

                val currentTimeH = System.currentTimeMillis() / 3600000.0
                
                // 获取用于模拟的事件
                val simulationEvents = repository.getEventsForSimulation(currentTimeH)
                
                if (simulationEvents.isEmpty()) {
                    _pkState.update {
                        it.copy(
                            simulationResult = null,
                            currentConcentration = null,
                            isSimulating = false,
                            currentTimeH = currentTimeH
                        )
                    }
                    return@launch
                }

                // 计算时间范围：当前时刻向后15天
                val startTimeH = currentTimeH - 24.0 * 15  // 当前时刻往前15天
                val endTimeH = currentTimeH + 24.0 * 15    // 当前时刻往后15天

                // 计算步数：至少15分钟一步
                val totalHours = endTimeH - startTimeH
                val stepsNeeded = (totalHours * 4).toInt() // 15分钟 = 0.25小时，所以每小时4步
                val numberOfSteps = maxOf(stepsNeeded, 1000) // 至少1000步

                // 运行模拟
                val engine = SimulationEngine(
                    events = simulationEvents,
                    bodyWeightKG = bodyWeightKG,
                    startTimeH = startTimeH,
                    endTimeH = endTimeH,
                    numberOfSteps = numberOfSteps
                )

                val result = engine.run()
                
                // 计算当前时刻的浓度
                val currentConc = result.concentration(currentTimeH)

                _pkState.update {
                    it.copy(
                        simulationResult = result,
                        currentConcentration = currentConc,
                        currentTimeH = currentTimeH,
                        isSimulating = false
                    )
                }
            } catch (e: Exception) {
                _pkState.update {
                    it.copy(
                        isSimulating = false,
                        error = "模拟失败: ${e.message}"
                    )
                }
            }
        }
    }
}

/**
 * ViewModel Factory
 */
class HRTViewModelFactory(
    private val repository: DoseEventRepository,
    private val bodyWeightKG: Double = 65.0
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HRTViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HRTViewModel(repository, bodyWeightKG) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
