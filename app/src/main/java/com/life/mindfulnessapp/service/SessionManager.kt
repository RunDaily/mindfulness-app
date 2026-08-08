package com.life.mindfulnessapp.service

import com.life.mindfulnessapp.data.PendingInterruptStore
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.data.repository.AppLimitRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository
import com.life.mindfulnessapp.domain.model.IntentKind
import com.life.mindfulnessapp.domain.model.PendingInterrupt
import com.life.mindfulnessapp.domain.model.SessionLimitPolicy
import com.life.mindfulnessapp.domain.model.UsageSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 管理当前使用会话状态（单例，供 Service 和 Overlay 共享）
 */
@Singleton
class SessionManager @Inject constructor(
    private val appLimitRepository: AppLimitRepository,
    private val usageRecordRepository: UsageRecordRepository,
    private val pendingInterruptStore: PendingInterruptStore
) {
    private val _currentSession = MutableStateFlow<UsageSession?>(null)
    val currentSession: StateFlow<UsageSession?> = _currentSession

    /**
     * 开始一个新的使用会话。
     *
     * @param purpose 使用意图文案；无意图门直进时可为 null
     * @param intentKind 意图类型；新路径多为 [IntentKind.PURPOSEFUL]
     * @param sessionLimitMinutes 本次会话上限（分钟）；0 = 不设单次上限
     */
    suspend fun startSession(
        packageName: String,
        appName: String,
        purpose: String? = null,
        intentKind: IntentKind? = null,
        sessionLimitMinutes: Int = 0
    ): UsageSession? {
        val existing = _currentSession.value
        if (existing != null && existing.packageName == packageName) {
            return existing
        }

        pendingInterruptStore.clear(packageName)

        val now = System.currentTimeMillis()
        val limit = appLimitRepository.getAppLimit(packageName) ?: return null

        val dailyUsed = usageRecordRepository.getDailyUsageSeconds(packageName, now)
        val weeklyUsed = usageRecordRepository.getWeeklyUsageSeconds(packageName, now)

        val resolvedKind = intentKind
            ?: if (!purpose.isNullOrBlank()) IntentKind.PURPOSEFUL else null
        val clampedSessionMinutes = if (sessionLimitMinutes > 0) {
            val dailyRemaining = SessionLimitPolicy.dailyRemainingMinutes(
                limit.effectiveDailyLimitMinutes(),
                dailyUsed
            )
            SessionLimitPolicy.clampSessionMinutes(sessionLimitMinutes, dailyRemaining)
        } else {
            0
        }

        val recordId = usageRecordRepository.insertRecord(
            UsageRecordEntity(
                packageName = packageName,
                startTime = now,
                endTime = -1L,
                purpose = purpose,
                intentKind = resolvedKind?.name,
                sessionLimitMinutes = clampedSessionMinutes,
                sessionExtensionMinutes = 0
            )
        )

        val session = UsageSession(
            recordId = recordId,
            packageName = packageName,
            appName = appName,
            startTime = now,
            dailyLimitSeconds = limit.effectiveDailyLimitMinutes() * 60L,
            dailyUsedSeconds = dailyUsed,
            weeklyLimitSeconds = limit.effectiveWeeklyLimitMinutes() * 60L,
            weeklyUsedSeconds = weeklyUsed,
            purpose = purpose,
            intentKind = resolvedKind,
            sessionLimitSeconds = clampedSessionMinutes * 60L,
            requireIntentOnOpen = limit.requireIntentOnOpen,
            timeLimitEnabled = limit.timeLimitEnabled,
            intentReviewEnabled = limit.intentReviewEnabled
        )
        _currentSession.value = session
        return session
    }

    /**
     * 恢复一次未标准闭环的会话：重新打开原记录，接着累计时长，保留原目的与会话契约。
     */
    suspend fun resumeInterruptedSession(pending: PendingInterrupt): UsageSession? {
        val existing = _currentSession.value
        if (existing != null && existing.packageName == pending.packageName) {
            pendingInterruptStore.clear(pending.packageName)
            return existing
        }

        val limit = appLimitRepository.getAppLimit(pending.packageName) ?: return null
        val record = usageRecordRepository.getRecordById(pending.recordId)
        val now = System.currentTimeMillis()

        val accumulated = if (record != null) {
            maxOf(record.durationSeconds, pending.durationSeconds)
        } else {
            pending.durationSeconds
        }

        val resolvedKind = pending.intentKind
            ?: IntentKind.fromStorage(record?.intentKind)
            ?: if (!(pending.purpose ?: record?.purpose).isNullOrBlank()) IntentKind.PURPOSEFUL else null
        val sessionLimitMin = when {
            pending.sessionLimitMinutes > 0 -> pending.sessionLimitMinutes
            (record?.sessionLimitMinutes ?: 0) > 0 -> record!!.sessionLimitMinutes
            else -> 0
        }
        val extensionMin = when {
            pending.sessionExtensionMinutes > 0 -> pending.sessionExtensionMinutes
            (record?.sessionExtensionMinutes ?: 0) > 0 -> record!!.sessionExtensionMinutes
            else -> 0
        }

        val recordId = if (record != null) {
            usageRecordRepository.updateRecord(
                record.copy(
                    endTime = -1L,
                    durationSeconds = 0L,
                    endReason = UsageRecordEntity.EndReason.UNKNOWN,
                    intentKind = resolvedKind?.name ?: record.intentKind,
                    sessionLimitMinutes = sessionLimitMin,
                    sessionExtensionMinutes = extensionMin
                )
            )
            record.id
        } else {
            usageRecordRepository.insertRecord(
                UsageRecordEntity(
                    packageName = pending.packageName,
                    startTime = (pending.endedAt - accumulated * 1000L).coerceAtMost(pending.endedAt),
                    endTime = -1L,
                    purpose = pending.purpose,
                    intentKind = resolvedKind?.name,
                    sessionLimitMinutes = sessionLimitMin,
                    sessionExtensionMinutes = extensionMin
                )
            )
        }

        val dailyUsed = usageRecordRepository.getDailyUsageSeconds(pending.packageName, now)
        val weeklyUsed = usageRecordRepository.getWeeklyUsageSeconds(pending.packageName, now)

        val session = UsageSession(
            recordId = recordId,
            packageName = pending.packageName,
            appName = pending.appName,
            startTime = now,
            dailyLimitSeconds = limit.effectiveDailyLimitMinutes() * 60L,
            dailyUsedSeconds = dailyUsed,
            weeklyLimitSeconds = limit.effectiveWeeklyLimitMinutes() * 60L,
            weeklyUsedSeconds = weeklyUsed,
            accumulatedActiveSeconds = accumulated,
            purpose = pending.purpose ?: record?.purpose,
            intentKind = resolvedKind,
            sessionLimitSeconds = sessionLimitMin * 60L,
            sessionExtensionSeconds = extensionMin * 60L,
            sessionExtensionUsed = extensionMin > 0,
            requireIntentOnOpen = limit.requireIntentOnOpen,
            timeLimitEnabled = limit.timeLimitEnabled,
            intentReviewEnabled = limit.intentReviewEnabled
        )
        _currentSession.value = session
        pendingInterruptStore.clear(pending.packageName)
        return session
    }

    /** App 进入后台：快照当前已累计的有效前台时长，停止计时增长 */
    fun onAppGoBackground() {
        val session = _currentSession.value ?: return
        val now = System.currentTimeMillis()
        val currentSegmentSeconds = (now - session.startTime) / 1000
        _currentSession.value = session.copy(
            isInBackground = true,
            backgroundSinceMs = now,
            accumulatedActiveSeconds = session.accumulatedActiveSeconds + currentSegmentSeconds
        )
    }

    /** App 回到前台：重置 startTime 为当前时刻，继续累计计时 */
    fun onAppReturnToForeground() {
        val session = _currentSession.value ?: return
        _currentSession.value = session.copy(
            isInBackground = false,
            backgroundSinceMs = 0L,
            startTime = System.currentTimeMillis()
        )
    }

    /**
     * 结束当前会话并持久化到本机数据库。
     */
    suspend fun endSession(
        reason: String,
        note: String? = null,
        mindfulnessLevel: Int? = null,
        effectScore: Int? = null
    ) {
        val session = _currentSession.value ?: return
        val now = System.currentTimeMillis()
        val duration = session.currentSessionSeconds

        usageRecordRepository.updateRecord(
            UsageRecordEntity(
                id = session.recordId,
                packageName = session.packageName,
                startTime = session.startTime,
                endTime = now,
                durationSeconds = duration,
                endReason = reason,
                purpose = session.purpose,
                intentKind = session.intentKind?.name,
                sessionLimitMinutes = (session.sessionLimitSeconds / 60L).toInt(),
                sessionExtensionMinutes = (session.sessionExtensionSeconds / 60L).toInt(),
                note = note?.takeIf { it.isNotBlank() },
                effectScore = effectScore,
                mindfulnessLevel = mindfulnessLevel?.takeIf {
                    UsageRecordEntity.MindfulnessLevel.isValid(it)
                }
            )
        )
        _currentSession.value = null

        when {
            reason == UsageRecordEntity.EndReason.MANUAL ||
                reason == UsageRecordEntity.EndReason.LIMIT_REACHED ||
                reason == UsageRecordEntity.EndReason.SESSION_LIMIT_REACHED -> {
                pendingInterruptStore.clear(session.packageName)
            }
            session.requireIntentOnOpen &&
                UsageRecordEntity.EndReason.shouldOfferResumeConfirm(reason) &&
                duration >= MIN_DURATION_FOR_RESUME_CONFIRM_SEC -> {
                pendingInterruptStore.save(
                    PendingInterrupt(
                        packageName = session.packageName,
                        recordId = session.recordId,
                        appName = session.appName,
                        endReason = reason,
                        purpose = session.purpose,
                        intentKind = session.intentKind,
                        sessionLimitMinutes = (session.sessionLimitSeconds / 60L).toInt(),
                        sessionExtensionMinutes = (session.sessionExtensionSeconds / 60L).toInt(),
                        durationSeconds = duration,
                        endedAt = now
                    )
                )
            }
            else -> {
                pendingInterruptStore.clear(session.packageName)
            }
        }
    }

    /**
     * 开启「超限续记」会话：用户在超限页关闭后 App 仍在前台时调用。
     * 保留原意图文案，避免时间轴意图叙事断层。
     */
    suspend fun startOverLimitSession(
        packageName: String,
        appName: String,
        purpose: String? = null,
        intentKind: IntentKind? = null
    ): UsageSession? {
        val now = System.currentTimeMillis()
        val limit = appLimitRepository.getAppLimit(packageName) ?: return null

        pendingInterruptStore.clear(packageName)

        val dailyUsed = usageRecordRepository.getDailyUsageSeconds(packageName, now)
        val weeklyUsed = usageRecordRepository.getWeeklyUsageSeconds(packageName, now)
        val trimmedPurpose = purpose?.trim()?.takeIf { it.isNotEmpty() }

        val recordId = usageRecordRepository.insertRecord(
            UsageRecordEntity(
                packageName = packageName,
                startTime = now,
                endTime = -1L,
                purpose = trimmedPurpose,
                intentKind = intentKind?.name,
                sessionLimitMinutes = 0
            )
        )

        val session = UsageSession(
            recordId = recordId,
            packageName = packageName,
            appName = appName,
            startTime = now,
            dailyLimitSeconds = limit.effectiveDailyLimitMinutes() * 60L,
            dailyUsedSeconds = dailyUsed,
            weeklyLimitSeconds = limit.effectiveWeeklyLimitMinutes() * 60L,
            weeklyUsedSeconds = weeklyUsed,
            purpose = trimmedPurpose,
            intentKind = intentKind,
            isOverLimitSession = true,
            requireIntentOnOpen = limit.requireIntentOnOpen,
            timeLimitEnabled = true,
            intentReviewEnabled = limit.intentReviewEnabled
        )
        _currentSession.value = session
        return session
    }

    /** 强制清除会话（不写记录，如切换到其他被监控App） */
    suspend fun clearSession(reason: String = UsageRecordEntity.EndReason.APP_CLOSED) {
        endSession(reason)
    }

    /**
     * 为当前会话授予一次续时（session grant，不改每日限额）。
     * 仅允许一次；由胶囊临近结束时用户手输分钟触发。
     *
     * @param extraMinutes 要延长的分钟数
     * @return true = 成功
     */
    suspend fun extendSessionOnce(extraMinutes: Int): Boolean {
        val session = _currentSession.value ?: return false
        if (!session.canOfferSessionExtension) return false
        if (extraMinutes <= 0) return false

        val newExtensionSeconds = extraMinutes * 60L
        val record = usageRecordRepository.getRecordById(session.recordId)
        if (record != null) {
            usageRecordRepository.updateRecord(
                record.copy(sessionExtensionMinutes = extraMinutes)
            )
        }

        _currentSession.value = session.copy(
            sessionExtensionSeconds = newExtensionSeconds,
            sessionExtensionUsed = true
        )
        return true
    }

    /**
     * 旧路径：永久延长每日限额。新续时请用 [extendSessionOnce]。
     * 保留给无会话上限、仅日锁预警的兜底场景。
     */
    suspend fun extendDailyLimit(extraMinutes: Int): Boolean {
        val session = _currentSession.value ?: return false
        // 有会话上限时走 session grant
        if (session.hasSessionLimit) {
            return extendSessionOnce(extraMinutes)
        }
        val limit = appLimitRepository.getAppLimit(session.packageName) ?: return false

        val newDailyLimitMinutes = limit.dailyLimitMinutes + extraMinutes
        appLimitRepository.saveAppLimit(
            limit.copy(dailyLimitMinutes = newDailyLimitMinutes)
        )
        _currentSession.value = session.copy(
            dailyLimitSeconds = newDailyLimitMinutes * 60L
        )
        return true
    }

    fun hasActiveSession(): Boolean = _currentSession.value != null

    fun getCurrentPackage(): String? = _currentSession.value?.packageName

    companion object {
        /** 短于此时长的异常结束不弹出下次确认（避免误触噪音） */
        const val MIN_DURATION_FOR_RESUME_CONFIRM_SEC = 5L
    }
}
