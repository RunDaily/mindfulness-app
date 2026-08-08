package com.life.mindfulnessapp.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 按日、按包名的冲动次数：每次展示拦截页 +1。
 * 用于拦截页「今天第 N 次」与周报守住率。
 */
@Singleton
class ImpulseStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** 今日该 App 冲动次数（含当前，若刚 increment） */
    fun getImpulseCount(packageName: String, dateKey: String = todayKey()): Int =
        prefs.getInt(impulseKey(packageName, dateKey), 0)

    /** 展示拦截页时调用，返回自增后的次数 */
    fun incrementImpulse(packageName: String, dateKey: String = todayKey()): Int {
        val key = impulseKey(packageName, dateKey)
        val next = prefs.getInt(key, 0) + 1
        prefs.edit { putInt(key, next) }
        return next
    }

    /**
     * 周报用：汇总 [dateKeys] 内各日冲动次数之和。
     */
    fun sumImpulseCounts(dateKeys: List<String>, packageName: String? = null): Int {
        var total = 0
        val dateSet = dateKeys.toSet()
        for ((key, value) in prefs.all) {
            if (!key.startsWith("impulse_")) continue
            val datePart = key.substringAfterLast('_')
            if (datePart !in dateSet) continue
            if (packageName != null) {
                val expected = impulseKey(packageName, datePart)
                if (key != expected) continue
            }
            total += when (value) {
                is Int -> value
                is String -> value.toIntOrNull() ?: 0
                else -> 0
            }
        }
        return total
    }

    private fun impulseKey(packageName: String, dateKey: String) =
        "impulse_${packageName}_$dateKey"

    companion object {
        private const val PREFS_NAME = "impulse_counts"
    }
}
