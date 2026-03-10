package cn.naivetomcat.hrt_tracker.data

import cn.naivetomcat.hrt_tracker.pk.DoseEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import kotlin.math.max

/**
 * 用药事件仓库
 */
class DoseEventRepository(private val dao: DoseEventDao) {
    
    /**
     * 获取所有用药事件（实时更新）
     */
    fun getAllEvents(): Flow<List<DoseEvent>> {
        return dao.getAllEvents().map { entities ->
            entities.map { it.toDoseEvent() }
        }
    }

    /**
     * 获取用于模拟的事件列表
     * 包含至少30天历史数据，且最少包含20次给药或全部给药
     */
    suspend fun getEventsForSimulation(currentTimeH: Double): List<DoseEvent> {
        val thirtyDaysAgo = currentTimeH - 24.0 * 30
        val recentEvents = dao.getEventsAfter(thirtyDaysAgo)
            .map { it.toDoseEvent() }

        val doseEventCount = recentEvents.count { it.route != cn.naivetomcat.hrt_tracker.pk.Route.PATCH_REMOVE }
        return if (doseEventCount < 20) {
            // 如果最近30天内不足20次给药，获取最近20次或全部
            dao.getRecentEvents(20)
                .map { it.toDoseEvent() }
        } else {
            recentEvents
        }
    }

    /**
     * 插入或更新用药事件
     */
    suspend fun upsertEvent(event: DoseEvent) {
        dao.upsertEvent(DoseEventEntity.fromDoseEvent(event))
    }

    /**
     * 删除用药事件
     */
    suspend fun deleteEvent(id: UUID) {
        dao.deleteEvent(id)
    }

    /**
     * 删除所有用药事件
     */
    suspend fun deleteAllEvents() {
        dao.deleteAllEvents()
    }
}
