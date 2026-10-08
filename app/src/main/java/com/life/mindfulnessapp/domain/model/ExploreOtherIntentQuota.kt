package com.life.mindfulnessapp.domain.model

/**
 * 搜索型意图门 ·「其他意图」日次数限额。
 * 搜索直达不占次数；预设 / 手写 / 随便看看共用配额。
 */
object ExploreOtherIntentQuota {
    const val DEFAULT_DAILY_LIMIT = 3

    fun remaining(used: Int, limit: Int = DEFAULT_DAILY_LIMIT): Int =
        (limit - used).coerceAtLeast(0)

    fun isExhausted(used: Int, limit: Int = DEFAULT_DAILY_LIMIT): Boolean =
        remaining(used, limit) <= 0
}
