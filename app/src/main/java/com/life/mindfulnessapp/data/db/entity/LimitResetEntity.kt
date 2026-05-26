package com.life.mindfulnessapp.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 「超时后重新设定限额」事件记录
 *
 * 每当用户在达到上限后选择重新设定目标时，写入一条此记录。
 * 用于首页时间轴特殊标注「又破防了」这个行为节点。
 *
 * @param id          自增主键
 * @param packageName App 包名
 * @param appName     App 显示名称（冗余存储，避免后续查表）
 * @param resetTime   重设时间戳（毫秒）
 * @param oldDailyLimitMinutes 修改前每日限额（分钟）
 * @param newDailyLimitMinutes 修改后每日限额（分钟）
 * @param oldWeeklyLimitMinutes 修改前每周限额（分钟），0 表示无限制
 * @param newWeeklyLimitMinutes 修改后每周限额（分钟），0 表示无限制
 */
@Entity(tableName = "limit_resets")
data class LimitResetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val resetTime: Long = System.currentTimeMillis(),
    val oldDailyLimitMinutes: Int,
    val newDailyLimitMinutes: Int,
    val oldWeeklyLimitMinutes: Int = 0,
    val newWeeklyLimitMinutes: Int = 0
)
