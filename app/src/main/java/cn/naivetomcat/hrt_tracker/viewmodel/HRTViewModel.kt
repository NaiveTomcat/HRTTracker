package cn.naivetomcat.hrt_tracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cn.naivetomcat.hrt_tracker.data.DoseEventRepository
import cn.naivetomcat.hrt_tracker.data.MedicationPlanRepository
import cn.naivetomcat.hrt_tracker.pk.DoseEvent
import cn.naivetomcat.hrt_tracker.pk.Route
import cn.naivetomcat.hrt_tracker.pk.SimulationEngine
import cn.naivetomcat.hrt_tracker.utils.MahiroJsonFormat
import cn.naivetomcat.hrt_tracker.utils.MedicationPlanPredictor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * JSON 导入结果
 */
sealed class ImportResult {
    data object Idle : ImportResult()
    data object Importing : ImportResult()
    data class Success(val importedCount: Int) : ImportResult()
    data class Error(val message: String) : ImportResult()
}

/**
 * HRT Tracker ViewModel
 * 管理用药记录和药代动力学模拟
 */
class HRTViewModel(
    private val repository: DoseEventRepository,
    private val medicationPlanRepository: MedicationPlanRepository,
    private val bodyWeightKG: Double = 55.0 // 默认体重，后续可以从用户设置中获取
) : ViewModel() {

    private companion object {
        const val SIMULATION_POINTS_PER_HOUR = 12.0 // 5分钟一个数据点
    }

    // 用药事件列表
    val events: StateFlow<List<DoseEvent>> = repository.getAllEvents()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // 所有用药计划列表
    val allPlans: StateFlow<List<cn.naivetomcat.hrt_tracker.data.MedicationPlan>> = medicationPlanRepository.getAllPlans()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // 启用的用药计划列表
    val enabledPlans: StateFlow<List<cn.naivetomcat.hrt_tracker.data.MedicationPlan>> = medicationPlanRepository.getEnabledPlans()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // PK 状态
    private val _pkState = MutableStateFlow(PKState())
    val pkState: StateFlow<PKState> = _pkState.asStateFlow()

    // 实时当前时刻流（每秒更新一次）
    val currentTimeH: StateFlow<Double> = flow {
        while (true) {
            emit(System.currentTimeMillis() / 3600000.0)
            delay(1000) // 每秒更新一次
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = System.currentTimeMillis() / 3600000.0
    )

    init {
        // 监听事件变化，自动重新运行模拟
        viewModelScope.launch {
            events.collect { eventList ->
                // 无论列表是否为空，都运行模拟以更新状态
                runSimulation()
            }
        }
        
        // 监听用药方案变化，自动重新运行模拟
        viewModelScope.launch {
            medicationPlanRepository.getEnabledPlans().collect {
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

    // JSON 导入结果状态
    private val _importResult = MutableStateFlow<ImportResult>(ImportResult.Idle)
    val importResult: StateFlow<ImportResult> = _importResult.asStateFlow()

    /**
     * 从 mahiro JSON 格式字符串导入用药事件
     * @param jsonContent JSON 字符串
     * @param onWeightImport 若 JSON 中包含体重数据，回调以更新体重设置
     */
    fun importFromMahiroJson(jsonContent: String, onWeightImport: ((Double) -> Unit)? = null) {
        viewModelScope.launch {
            _importResult.value = ImportResult.Importing
            try {
                val data = withContext(Dispatchers.Default) {
                    MahiroJsonFormat.parseImport(jsonContent)
                }
                data.events.forEach { event ->
                    repository.upsertEvent(event)
                }
                data.weight?.let { onWeightImport?.invoke(it) }
                _importResult.value = ImportResult.Success(data.events.size)
            } catch (e: Exception) {
                _importResult.value = ImportResult.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * 重置导入结果状态为 Idle
     */
    fun dismissImportResult() {
        _importResult.value = ImportResult.Idle
    }

    /**
     * 直接将导入结果设为 Error（例如剪贴板为空时）
     */
    fun reportClipboardImportError(message: String) {
        _importResult.value = ImportResult.Error(message)
    }

    /**
     * 将当前所有用药事件导出为 mahiro JSON 格式字符串
     * @param weight 用户体重（kg），用于写入 JSON 的 weight 字段
     */
    fun exportToMahiroJson(weight: Double): String {
        return MahiroJsonFormat.generateExport(weight, events.value)
    }

    /**
     * 运行药代动力学模拟
     */
    fun runSimulation() {
        viewModelScope.launch {
            try {
                _pkState.update { it.copy(isSimulating = true, error = null) }

                val currentTimeH = System.currentTimeMillis() / 3600000.0
                
                // 获取历史用药事件
                val historicalEvents = repository.getEventsForSimulation(currentTimeH)
                
                // 获取启用的用药方案
                val enabledPlans = medicationPlanRepository.getEnabledPlans().first()
                
                // 根据用药方案生成未来15天的预测事件
                val futureEvents = if (enabledPlans.isNotEmpty()) {
                    val now = LocalDateTime.now()
                    val predicted = MedicationPlanPredictor.generateFutureEventsForPlans(
                        plans = enabledPlans,
                        fromDateTime = now,
                        daysAhead = 15
                    )
                    // 过滤与历史用药记录冲突的预测事件：
                    // 若实际用药后1小时内存在同类计划用药，则该计划用药不参与曲线计算
                    MedicationPlanPredictor.filterConflictingPredictions(
                        predictedEvents = predicted,
                        actualEvents = historicalEvents
                    )
                } else {
                    emptyList()
                }
                
                if (historicalEvents.isEmpty() && futureEvents.isEmpty()) {
                    _pkState.update {
                        it.copy(
                            simulationResult = null,
                            baselineSimulationResult = null,
                            currentConcentration = null,
                            isSimulating = false,
                            currentTimeH = currentTimeH
                        )
                    }
                    return@launch
                }

                // 计算时间范围：当前时刻往前15天到往后15天
                val startTimeH = currentTimeH - 24.0 * 15  // 当前时刻往前15天
                val endTimeH = currentTimeH + 24.0 * 15    // 当前时刻往后15天

                // 计算步数：至少5分钟一步
                val totalHours = endTimeH - startTimeH
                val stepsNeeded = ceil(totalHours * SIMULATION_POINTS_PER_HOUR).toInt() + 1
                val numberOfSteps = maxOf(stepsNeeded, 1000) // 至少1000步

                // 运行基线仿真（仅历史事件，不考虑未来计划）
                val baselineResult = if (historicalEvents.isNotEmpty()) {
                    val baselineEngine = SimulationEngine(
                        events = historicalEvents,
                        bodyWeightKG = bodyWeightKG,
                        startTimeH = startTimeH,
                        endTimeH = endTimeH,
                        numberOfSteps = numberOfSteps
                    )
                    baselineEngine.run()
                } else {
                    null
                }
                
                // 运行完整仿真（历史事件 + 未来计划）
                val allEvents = historicalEvents + futureEvents
                val fullResult = if (allEvents.isNotEmpty()) {
                    val fullEngine = SimulationEngine(
                        events = allEvents,
                        bodyWeightKG = bodyWeightKG,
                        startTimeH = startTimeH,
                        endTimeH = endTimeH,
                        numberOfSteps = numberOfSteps
                    )
                    fullEngine.run()
                } else {
                    null
                }
                
                // 计算当前时刻的浓度（使用完整仿真结果）
                val currentConc = fullResult?.concentration(currentTimeH) 
                    ?: baselineResult?.concentration(currentTimeH)

                _pkState.update {
                    it.copy(
                        simulationResult = fullResult,
                        baselineSimulationResult = baselineResult,
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
    private val medicationPlanRepository: MedicationPlanRepository,
    private val bodyWeightKG: Double = 65.0
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HRTViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HRTViewModel(repository, medicationPlanRepository, bodyWeightKG) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
