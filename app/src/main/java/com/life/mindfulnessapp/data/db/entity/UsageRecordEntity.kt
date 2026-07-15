package com.life.mindfulnessapp.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 单次使用记录实体
 * @param id 自增主键
 * @param packageName App 包名
 * @param startTime 开始时间戳（毫秒）
 * @param endTime 结束时间戳（毫秒），-1 表示进行中
 * @param durationSeconds 使用时长（秒）
 * @param endReason 结束原因
 * @param purpose 使用目的（用户进入 App 前填写，null 表示未填写）
 * @param note 用户事后添加的效果备注（可随时编辑，null 表示未填写）
 * @param effectScore 本次使用效果自评分（0-10，结束时填写，null 表示未评分）
 */
@Entity(tableName = "usage_records")
data class UsageRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val startTime: Long,
    val endTime: Long = -1L,
    val durationSeconds: Long = 0L,
    val endReason: String = EndReason.UNKNOWN,
    val purpose: String? = null,
    val note: String? = null,
    val effectScore: Int? = null
) {
    object EndReason {
        const val MANUAL = "MANUAL"           // 用户手动点击胶囊结束
        const val AUTO_TIMEOUT = "AUTO_TIMEOUT" // 切换后台10分钟自动结束
        const val LIMIT_REACHED = "LIMIT_REACHED" // 达到时长上限
        const val APP_CLOSED = "APP_CLOSED"   // App 被关闭
        const val UNKNOWN = "UNKNOWN"
    }
}
