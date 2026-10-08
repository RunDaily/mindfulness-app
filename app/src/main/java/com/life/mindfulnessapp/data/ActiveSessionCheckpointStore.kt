package com.life.mindfulnessapp.data

import android.content.Context
import androidx.core.content.edit
import com.life.mindfulnessapp.domain.model.ComparePolicy
import com.life.mindfulnessapp.domain.model.IntentKind
import com.life.mindfulnessapp.domain.model.UsageSession
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 持久化进行中的 [UsageSession]，供监控服务被系统回收后无感恢复。
 * 仅在非用户主动停止监控时写入；[SessionManager.endSession] 会清除。
 */
@Singleton
class ActiveSessionCheckpointStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(session: UsageSession) {
        val json = JSONObject().apply {
            put(KEY_RECORD_ID, session.recordId)
            put(KEY_PACKAGE, session.packageName)
            put(KEY_APP_NAME, session.appName)
            put(KEY_START_TIME, session.startTime)
            put(KEY_ORIGIN_START, session.sessionOriginStartMs)
            put(KEY_DAILY_LIMIT, session.dailyLimitSeconds)
            put(KEY_DAILY_USED, session.dailyUsedSeconds)
            put(KEY_WEEKLY_LIMIT, session.weeklyLimitSeconds)
            put(KEY_WEEKLY_USED, session.weeklyUsedSeconds)
            put(KEY_ACCUMULATED_MS, session.accumulatedActiveMs)
            // 兼容旧版只读秒字段的客户端
            put(KEY_ACCUMULATED, session.accumulatedActiveSeconds)
            put(KEY_PURPOSE, session.purpose ?: JSONObject.NULL)
            put(KEY_INTENT_KIND, session.intentKind?.name ?: JSONObject.NULL)
            put(KEY_SESSION_LIMIT, session.sessionLimitSeconds)
            put(KEY_SESSION_EXT, session.sessionExtensionSeconds)
            put(KEY_SESSION_EXT_USED, session.sessionExtensionUsed)
            put(KEY_IN_BACKGROUND, session.isInBackground)
            put(KEY_BACKGROUND_SINCE, session.backgroundSinceMs)
            put(KEY_OVER_LIMIT, session.isOverLimitSession)
            put(KEY_REQUIRE_INTENT, session.requireIntentOnOpen)
            put(KEY_TIME_LOCK, session.timeLimitEnabled)
            put(KEY_TIME_AWARENESS, false)
            put(KEY_TODAY_ENTER_COUNT, session.todayEnterCount)
            put(KEY_INTENT_REVIEW, session.intentReviewEnabled)
            put(KEY_COMPARE_ENABLED, session.compareEnabled)
            put(KEY_COMPARE_MIN_MINUTES, session.compareMinMinutes)
            put(KEY_DAILY_BASE, session.dailyBaseLimitSeconds)
            put(KEY_DAILY_GRACE, session.dailyGraceBonusSeconds)
            put(KEY_SAVED_AT, System.currentTimeMillis())
        }
        prefs.edit { putString(KEY_JSON, json.toString()) }
    }

    fun peekRecordId(): Long? {
        val raw = prefs.getString(KEY_JSON, null) ?: return null
        return runCatching { JSONObject(raw).getLong(KEY_RECORD_ID) }.getOrNull()
    }

    fun load(): UsageSession? {
        val raw = prefs.getString(KEY_JSON, null) ?: return null
        return try {
            val json = JSONObject(raw)
            val savedAt = json.optLong(KEY_SAVED_AT, 0L)
            if (savedAt > 0L && System.currentTimeMillis() - savedAt > MAX_AGE_MS) {
                clear()
                return null
            }
            val (compareEnabled, compareMinMinutes) = resolveCompareSettings(json)
            val accumulatedMs = when {
                json.has(KEY_ACCUMULATED_MS) -> json.getLong(KEY_ACCUMULATED_MS)
                json.has(KEY_ACCUMULATED) -> json.getLong(KEY_ACCUMULATED) * 1000L
                else -> 0L
            }
            val startTime = json.getLong(KEY_START_TIME)
            val wasInBackground = json.optBoolean(KEY_IN_BACKGROUND, false)
            // 不恢复 segmentElapsedRealtime（进程重启后失效）。
            // 若 checkpoint 仍标「前台」，绝不能用墙钟 startTime→now 续算——会把锁屏/杀进程空档吞进时长。
            // 只把「保存当时」已走过的前台段并入累计，并强制挂起，回 App 再开新段。
            val segmentAtSaveMs = if (!wasInBackground && savedAt > 0L && startTime > 0L) {
                (savedAt - startTime).coerceAtLeast(0L)
            } else {
                0L
            }
            val restoredAccumulated = accumulatedMs + segmentAtSaveMs
            val forceBackground = !wasInBackground
            UsageSession(
                recordId = json.getLong(KEY_RECORD_ID),
                packageName = json.getString(KEY_PACKAGE),
                appName = json.optString(KEY_APP_NAME, json.getString(KEY_PACKAGE)),
                startTime = startTime,
                sessionOriginStartMs = json.optLong(KEY_ORIGIN_START, startTime),
                dailyLimitSeconds = json.getLong(KEY_DAILY_LIMIT),
                dailyUsedSeconds = json.getLong(KEY_DAILY_USED),
                weeklyLimitSeconds = json.getLong(KEY_WEEKLY_LIMIT),
                weeklyUsedSeconds = json.getLong(KEY_WEEKLY_USED),
                accumulatedActiveMs = restoredAccumulated,
                segmentElapsedRealtimeMs = 0L,
                purpose = if (json.isNull(KEY_PURPOSE)) null
                else json.optString(KEY_PURPOSE).takeIf { it.isNotBlank() },
                intentKind = if (json.isNull(KEY_INTENT_KIND)) null
                else IntentKind.fromStorage(json.optString(KEY_INTENT_KIND)),
                sessionLimitSeconds = json.optLong(KEY_SESSION_LIMIT, 0L),
                sessionExtensionSeconds = json.optLong(KEY_SESSION_EXT, 0L),
                sessionExtensionUsed = json.optBoolean(KEY_SESSION_EXT_USED, false),
                isInBackground = wasInBackground || forceBackground,
                backgroundSinceMs = when {
                    forceBackground -> savedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
                    else -> json.optLong(KEY_BACKGROUND_SINCE, 0L)
                },
                isOverLimitSession = json.optBoolean(KEY_OVER_LIMIT, false),
                requireIntentOnOpen = json.optBoolean(KEY_REQUIRE_INTENT, true),
                timeLimitEnabled = json.optBoolean(KEY_TIME_LOCK, true),
                todayEnterCount = json.optInt(KEY_TODAY_ENTER_COUNT, 0),
                intentReviewEnabled = json.optBoolean(KEY_INTENT_REVIEW, false),
                compareEnabled = compareEnabled,
                compareMinMinutes = compareMinMinutes,
                dailyBaseLimitSeconds = json.optLong(KEY_DAILY_BASE, 0L),
                dailyGraceBonusSeconds = json.optLong(KEY_DAILY_GRACE, 0L)
            )
        } catch (_: Exception) {
            clear()
            null
        }
    }

    fun clear() {
        prefs.edit { remove(KEY_JSON) }
    }

    companion object {
        private const val PREFS_NAME = "active_session_checkpoint"
        private const val KEY_JSON = "session_json"
        private const val KEY_RECORD_ID = "recordId"
        private const val KEY_PACKAGE = "packageName"
        private const val KEY_APP_NAME = "appName"
        private const val KEY_START_TIME = "startTime"
        private const val KEY_ORIGIN_START = "sessionOriginStartMs"
        private const val KEY_DAILY_LIMIT = "dailyLimitSeconds"
        private const val KEY_DAILY_USED = "dailyUsedSeconds"
        private const val KEY_WEEKLY_LIMIT = "weeklyLimitSeconds"
        private const val KEY_WEEKLY_USED = "weeklyUsedSeconds"
        private const val KEY_ACCUMULATED = "accumulatedActiveSeconds"
        private const val KEY_ACCUMULATED_MS = "accumulatedActiveMs"
        private const val KEY_PURPOSE = "purpose"
        private const val KEY_INTENT_KIND = "intentKind"
        private const val KEY_SESSION_LIMIT = "sessionLimitSeconds"
        private const val KEY_SESSION_EXT = "sessionExtensionSeconds"
        private const val KEY_SESSION_EXT_USED = "sessionExtensionUsed"
        private const val KEY_IN_BACKGROUND = "isInBackground"
        private const val KEY_BACKGROUND_SINCE = "backgroundSinceMs"
        private const val KEY_OVER_LIMIT = "isOverLimitSession"
        private const val KEY_REQUIRE_INTENT = "requireIntentOnOpen"
        private const val KEY_TIME_LOCK = "timeLimitEnabled"
        private const val KEY_TIME_AWARENESS = "timeAwarenessEnabled"
        private const val KEY_TODAY_ENTER_COUNT = "todayEnterCount"
        private const val KEY_INTENT_REVIEW = "intentReviewEnabled"
        private const val KEY_COMPARE_ENABLED = "compareEnabled"
        private const val KEY_COMPARE_MIN_MINUTES = "compareMinMinutes"
        /** 旧 checkpoint：短时对照开关 → 迁为 compareMinMinutes=5 */
        private const val KEY_SHORT_COMPARE_LEGACY = "shortSessionCompareEnabled"
        private const val KEY_DAILY_BASE = "dailyBaseLimitSeconds"
        private const val KEY_DAILY_GRACE = "dailyGraceBonusSeconds"
        private const val KEY_SAVED_AT = "savedAt"

        /** 超过此时长的 checkpoint 视为过期，避免隔天误恢复 */
        const val MAX_AGE_MS = 6 * 60 * 60 * 1000L

        private fun resolveCompareSettings(json: JSONObject): Pair<Boolean, Int> {
            if (json.has(KEY_COMPARE_ENABLED) || json.has(KEY_COMPARE_MIN_MINUTES)) {
                return json.optBoolean(KEY_COMPARE_ENABLED, ComparePolicy.DEFAULT_ENABLED) to
                    ComparePolicy.sanitizeMinMinutes(
                        json.optInt(KEY_COMPARE_MIN_MINUTES, ComparePolicy.DEFAULT_MIN_MINUTES)
                    )
            }
            // 旧版 shortSessionCompareEnabled：开 → 门槛 5；关 → 门槛 10（仍启用对照）
            val shortOn = json.optBoolean(KEY_SHORT_COMPARE_LEGACY, false)
            return true to if (shortOn) ComparePolicy.FLOOR_MIN_MINUTES else ComparePolicy.DEFAULT_MIN_MINUTES
        }
    }
}
