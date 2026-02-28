package cn.naivetomcat.hrt_tracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import cn.naivetomcat.hrt_tracker.pk.DoseEvent
import cn.naivetomcat.hrt_tracker.pk.Ester
import cn.naivetomcat.hrt_tracker.pk.Route
import java.util.UUID

/**
 * 用药事件数据库实体
 */
@Entity(tableName = "dose_events")
data class DoseEventEntity(
    @PrimaryKey
    val id: UUID,
    val route: String,
    val timeH: Double,
    val doseMG: Double,
    val ester: String,
    val extras: Map<String, Double>
) {
    /**
     * 转换为领域模型
     */
    fun toDoseEvent(): DoseEvent {
        val extraMap = extras.mapKeys { (key, _) ->
            DoseEvent.ExtraKey.valueOf(key)
        }
        
        return DoseEvent(
            id = id,
            route = Route.valueOf(route),
            timeH = timeH,
            doseMG = doseMG,
            ester = Ester.valueOf(ester),
            extras = extraMap
        )
    }

    companion object {
        /**
         * 从领域模型创建实体
         */
        fun fromDoseEvent(event: DoseEvent): DoseEventEntity {
            val extraMap = event.extras.mapKeys { (key, _) ->
                key.name
            }
            
            return DoseEventEntity(
                id = event.id,
                route = event.route.name,
                timeH = event.timeH,
                doseMG = event.doseMG,
                ester = event.ester.name,
                extras = extraMap
            )
        }
    }
}
