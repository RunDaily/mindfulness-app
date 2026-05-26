package com.life.mindfulnessapp.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 受监控 App 的配置实体
 * @param packageName App 包名（唯一标识）
 * @param appName App 显示名称
 * @param dailyLimitMinutes 每日最大使用时长（分钟），0 表示不限制
 * @param weeklyLimitMinutes 每周累计最大使用时长（分钟），0 表示不限制
 * @param isEnabled 是否启用监控
 * @param createdAt 创建时间戳
 * @param dailyModifyCount 今日已修改限制次数（每天最多修改 [MAX_DAILY_MODIFY_COUNT] 次）
 * @param lastModifiedDate 上次修改限制的日期（格式 yyyyMMdd），用于判断是否跨天重置次数
 * @param timeLimitEnabled 是否启用时长监控
 * @param overTimeMessage 超时提醒文案
 */
@Entity(tableName = "app_limits")
data class AppLimitEntity(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val dailyLimitMinutes: Int = 60,
    val weeklyLimitMinutes: Int = 0,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val dailyModifyCount: Int = 0,
    val lastModifiedDate: String = "",
    val timeLimitEnabled: Boolean = true,
    val overTimeMessage: String = ""
) {
    companion object {
        /** 每天最多允许用户主动修改限制的次数 */
        const val MAX_DAILY_MODIFY_COUNT = 1
    }
}
