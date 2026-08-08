package com.life.mindfulnessapp.domain.model

/**
 * 离开后可去的正向 App。
 * [alias] 为空时展示系统 App 名；有别名时轻条用别名（如「晨读」）。
 */
data class PositiveDestination(
    val packageName: String,
    val alias: String? = null
) {
    fun displayLabel(fallbackAppName: String): String {
        val a = alias?.trim().orEmpty()
        return a.ifEmpty { fallbackAppName }
    }
}
