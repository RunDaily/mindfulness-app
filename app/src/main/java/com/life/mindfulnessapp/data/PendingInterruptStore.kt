package com.life.mindfulnessapp.data

import android.content.Context
import androidx.core.content.edit
import com.life.mindfulnessapp.domain.model.IntentKind
import com.life.mindfulnessapp.domain.model.PendingInterrupt
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 按包名持久化「待确认中断」快照。
 * 使用独立 SharedPreferences，避免与业务偏好混杂。
 *
 * 读取时若已过期（见 [PendingInterrupt.isExpired]），清除快照并返回 null，
 * 下次进入走普通拦截，不再在意图门区提供「继续上次」。
 */
@Singleton
class PendingInterruptStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(interrupt: PendingInterrupt) {
        val json = JSONObject().apply {
            put(KEY_PACKAGE, interrupt.packageName)
            put(KEY_RECORD_ID, interrupt.recordId)
            put(KEY_APP_NAME, interrupt.appName)
            put(KEY_END_REASON, interrupt.endReason)
            put(KEY_PURPOSE, interrupt.purpose ?: JSONObject.NULL)
            put(KEY_INTENT_KIND, interrupt.intentKind?.name ?: JSONObject.NULL)
            put(KEY_SESSION_LIMIT, interrupt.sessionLimitMinutes)
            put(KEY_SESSION_EXT, interrupt.sessionExtensionMinutes)
            put(KEY_DURATION, interrupt.durationSeconds)
            put(KEY_ENDED_AT, interrupt.endedAt)
        }
        prefs.edit { putString(keyFor(interrupt.packageName), json.toString()) }
    }

    fun get(packageName: String): PendingInterrupt? {
        val raw = prefs.getString(keyFor(packageName), null) ?: return null
        return try {
            val json = JSONObject(raw)
            val interrupt = PendingInterrupt(
                packageName = json.getString(KEY_PACKAGE),
                recordId = json.getLong(KEY_RECORD_ID),
                appName = json.optString(KEY_APP_NAME, packageName),
                endReason = json.getString(KEY_END_REASON),
                purpose = if (json.isNull(KEY_PURPOSE)) null
                else json.optString(KEY_PURPOSE).takeIf { it.isNotBlank() },
                intentKind = if (json.isNull(KEY_INTENT_KIND)) null
                else IntentKind.fromStorage(json.optString(KEY_INTENT_KIND)),
                sessionLimitMinutes = json.optInt(KEY_SESSION_LIMIT, 0),
                sessionExtensionMinutes = json.optInt(KEY_SESSION_EXT, 0),
                durationSeconds = json.getLong(KEY_DURATION),
                endedAt = json.getLong(KEY_ENDED_AT)
            )
            if (interrupt.isExpired()) {
                clear(packageName)
                null
            } else {
                interrupt
            }
        } catch (_: Exception) {
            clear(packageName)
            null
        }
    }

    fun clear(packageName: String) {
        prefs.edit { remove(keyFor(packageName)) }
    }

    private fun keyFor(packageName: String) = "pending_$packageName"

    companion object {
        private const val PREFS_NAME = "pending_interrupts"
        private const val KEY_PACKAGE = "packageName"
        private const val KEY_RECORD_ID = "recordId"
        private const val KEY_APP_NAME = "appName"
        private const val KEY_END_REASON = "endReason"
        private const val KEY_PURPOSE = "purpose"
        private const val KEY_INTENT_KIND = "intentKind"
        private const val KEY_SESSION_LIMIT = "sessionLimitMinutes"
        private const val KEY_SESSION_EXT = "sessionExtensionMinutes"
        private const val KEY_DURATION = "durationSeconds"
        private const val KEY_ENDED_AT = "endedAt"
    }
}
