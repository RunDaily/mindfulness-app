package com.life.mindfulnessapp.domain.model

/**
 * 不适合加入少名单监控的 App。
 * 微信真有事时门口碍事，无意识打开又拦不住——产品上不鼓励加入。
 */
object MonitorSuitability {

    const val WECHAT = "com.tencent.mm"

    fun isUnsuitable(packageName: String): Boolean =
        packageName.trim() == WECHAT

    /** 用户可见提醒；适合则 null。 */
    fun unsuitableReminder(packageName: String): String? =
        if (isUnsuitable(packageName)) "微信不适合加入监控" else null
}
