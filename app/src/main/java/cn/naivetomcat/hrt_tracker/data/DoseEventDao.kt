package cn.naivetomcat.hrt_tracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * 用药事件 DAO
 */
@Dao
interface DoseEventDao {
    /**
     * 获取所有用药事件（按时间排序）
     */
    @Query("SELECT * FROM dose_events ORDER BY timeH DESC")
    fun getAllEvents(): Flow<List<DoseEventEntity>>

    /**
     * 获取指定时间范围内的用药事件
     */
    @Query("SELECT * FROM dose_events WHERE timeH >= :startTimeH AND timeH <= :endTimeH ORDER BY timeH ASC")
    suspend fun getEventsByTimeRange(startTimeH: Double, endTimeH: Double): List<DoseEventEntity>

    /**
     * 获取最近N条用药记录
     */
    @Query("SELECT * FROM dose_events ORDER BY timeH DESC LIMIT :limit")
    suspend fun getRecentEvents(limit: Int): List<DoseEventEntity>

    /**
     * 获取指定时间之后的所有记录
     */
    @Query("SELECT * FROM dose_events WHERE timeH >= :startTimeH ORDER BY timeH ASC")
    suspend fun getEventsAfter(startTimeH: Double): List<DoseEventEntity>

    /**
     * 插入或更新用药事件
     */
    @Upsert
    suspend fun upsertEvent(event: DoseEventEntity)

    /**
     * 删除用药事件
     */
    @Query("DELETE FROM dose_events WHERE id = :id")
    suspend fun deleteEvent(id: UUID)

    /**
     * 删除所有用药事件
     */
    @Query("DELETE FROM dose_events")
    suspend fun deleteAllEvents()
}
