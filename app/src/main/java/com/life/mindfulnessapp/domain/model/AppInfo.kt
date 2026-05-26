package com.life.mindfulnessapp.domain.model

import android.graphics.drawable.Drawable

/**
 * 已安装 App 的展示信息
 */
data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isMonitored: Boolean = false,
    val dailyLimitMinutes: Int = 60,
    val weeklyLimitMinutes: Int = 0,
    /** 是否单独开启时长监控 */
    val timeLimitEnabled: Boolean = true,
    /** 超时提醒文案 */
    val overTimeMessage: String = "",
    /** 该 App 是否已从设备卸载（但仍保留在监控列表中） */
    val isUninstalled: Boolean = false
)
