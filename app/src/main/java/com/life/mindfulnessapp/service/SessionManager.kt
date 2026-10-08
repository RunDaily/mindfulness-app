package com.life.mindfulnessapp.service

import android.os.SystemClock
import android.util.Log
import com.life.mindfulnessapp.data.ActiveSessionCheckpointStore
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.PendingInterruptStore
import com.life.mindfulnessapp.data.analytics.HaEvents
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.data.repository.AnalyticsRepository
import com.life.mindfulnessapp.data.repository.AppLimitRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository
import com.life.mindfulnessapp.domain.model.BreathCostPolicy
import com.life.mindfulnessapp.domain.model.BrowseCasualCooldown
import com.life.mindfulnessapp.domain.model.BrowseCasualIntent
import com.life.mindfulnessapp.domain.model.CompanionPath
import com.life.mindfulnessapp.domain.model.ComparePolicy
import com.life.mindfulnessapp.domain.model.DailyCapFacts
import com.life.mindfulnessapp.domain.model.IntentKind
import com.life.mindfulnessapp.domain.model.PendingInterrupt
import com.life.mindfulnessapp.domain.model.SessionLimitPolicy
import com.life.mindfulnessapp.domain.model.UsageRecordCounts
import com.life.mindfulnessapp.domain.model.UsageSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 管理当前使用会话状态（单例，供 Service 和 Overlay 共享）
 *
 * 同一时刻前台只服务一个 [currentSession]；切到另一个被监控 App 时，可将原会话
 * [parkCurrentSessionForSwitch] 挂起，在宽限期内无缝 [restoreParkedSession]。
 */
@Singleton
class SessionManager @Inject constructor(
    private val appLimitRepository: AppLimitRepository,
    private val usageRecordRepository: UsageRecordRepository,
    private val pendingInterruptStore: PendingInterruptStore,
    private val activeSessionCheckpointStore: ActiveSessionCheckpointStore,
    private val appPreferences: AppPreferences,
    private val analyticsRepository: AnalyticsRepository
) {
    private val _currentSession = MutableStateFlow<UsageSession?>(null)
    val currentSession: StateFlow<UsageSession?> = _currentSession
    /** 防止胶囊连点导致同会话续两次 */
    private val extendMutex = Mutex()

    /**
     * 因切到另一个被监控 App 而挂起的会话（可多包并存，如 A→B→C）。
     * key = packageName。
     */
    private val parkedBySwitch = mutableMapOf<String, ParkedSwitchSession>()

    private data class ParkedSwitchSession(
        val session: UsageSession,
        /** [SystemClock.elapsedRealtime] 截止；超时按 SWITCHED_AWAY 落库 */
        val expireElapsedRealtimeMs: Long
    )

    /**
     * @return first = 生效日限额秒；second = 展示用延长秒（仅当生效限额高于基础时）；third = 基础日限额秒
     */
    private fun dailyLimitWithGraceSeconds(
        packageName: String,
        baseMinutes: Int
    ): Triple<Long, Long, Long> {
        if (baseMinutes <= 0) return Triple(0L, 0L, 0L)
        val baseSec = baseMinutes * 60L
        val limitSec = appPreferences.effectiveDailyLimitSeconds(packageName, baseMinutes)
        val bonusSec = (limitSec - baseSec).coerceAtLeast(0L)
        return Triple(limitSec, bonusSec, baseSec)
    }

    /**
     * 按最新配置与今日 DB 用量刷新内存会话的日限额 / 已用快照。
     * 用户中途改限额、或触顶延长后抬高基础限额时，胶囊必须跟上，否则会卡在旧天花板上。
     */
    suspend fun refreshLiveBudget() {
        rollToNewDayIfNeeded()
        val session = _currentSession.value ?: return
        val limit = appLimitRepository.getAppLimit(session.packageName) ?: return
        val now = System.currentTimeMillis()
        val dailyUsed = usageRecordRepository.getDailyUsageSeconds(session.packageName, now)
        val weeklyUsed = usageRecordRepository.getWeeklyUsageSeconds(session.packageName, now)

        if (!limit.timeLimitEnabled) {
            if (session.timeLimitEnabled ||
                session.dailyLimitSeconds != 0L ||
                session.dailyUsedSeconds != dailyUsed
            ) {
                _currentSession.value = session.copy(
                    timeLimitEnabled = false,
                    dailyLimitSeconds = 0L,
                    dailyBaseLimitSeconds = 0L,
                    dailyGraceBonusSeconds = 0L,
                    dailyUsedSeconds = dailyUsed,
                    weeklyLimitSeconds = 0L,
                    weeklyUsedSeconds = weeklyUsed
                )
                syncActiveSessionCheckpoint()
            }
            return
        }

        val (dailyLimitSec, graceBonusSec, dailyBaseSec) = dailyLimitWithGraceSeconds(
            session.packageName,
            limit.effectiveDailyLimitMinutes()
        )
        // 纯日锁会话续时只抬高内存天花板，不写回配置；刷新时保留已授予的续时秒数
        val sessionExtendBonus =
            if (!session.hasSessionLimit && session.sessionExtensionSeconds > 0L) {
                session.sessionExtensionSeconds
            } else {
                0L
            }
        val effectiveDailySec = dailyLimitSec + sessionExtendBonus
        // 展示用 +N：触顶延长 + 本会话续时（配置基础日限额不变）
        val displayGraceSec = graceBonusSec + sessionExtendBonus
        val weeklyLimitSec = limit.effectiveWeeklyLimitMinutes() * 60L
        if (session.dailyLimitSeconds != effectiveDailySec ||
            session.dailyBaseLimitSeconds != dailyBaseSec ||
            session.dailyGraceBonusSeconds != displayGraceSec ||
            session.dailyUsedSeconds != dailyUsed ||
            session.weeklyLimitSeconds != weeklyLimitSec ||
            session.weeklyUsedSeconds != weeklyUsed ||
            session.timeLimitEnabled != limit.timeLimitEnabled
        ) {
            _currentSession.value = session.copy(
                timeLimitEnabled = true,
                dailyLimitSeconds = effectiveDailySec,
                dailyBaseLimitSeconds = dailyBaseSec,
                dailyGraceBonusSeconds = displayGraceSec,
                dailyUsedSeconds = dailyUsed,
                weeklyLimitSeconds = weeklyLimitSec,
                weeklyUsedSeconds = weeklyUsed
            )
            syncActiveSessionCheckpoint()
        }
    }

    /**
     * 开始一个新的使用会话。
     *
     * @param purpose 使用意图文案；无意图门直进时可为 null
     * @param intentKind 意图类型；新路径多为 [IntentKind.PURPOSEFUL]
     * @param sessionLimitMinutes 本次会话上限（分钟）；0 = 不设单次上限
     * @param entry 埋点入口：gate / direct（默认按是否有意图推断）
     */
    suspend fun startSession(
        packageName: String,
        appName: String,
        purpose: String? = null,
        intentKind: IntentKind? = null,
        sessionLimitMinutes: Int = 0,
        entry: String? = null,
        /** 意图门再次放行：结束同包残留会话并重新计时（不复用内存会话） */
        restartIfExists: Boolean = false,
        /** 意图门展示到决策的停留毫秒 */
        gateDwellMs: Long = 0L
    ): UsageSession? {
        if (restartIfExists) {
            finalizeStaleSessionBeforeGateRestart(packageName)
        } else {
            val existing = _currentSession.value
            if (existing != null && existing.packageName == packageName) {
                return existing
            }
        }

        // 开新会话前先收口残留未结束记录，避免时间轴长期挂着「进行中」
        closeOrphanedOpenRecords()

        pendingInterruptStore.clear(packageName)

        val now = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val limit = appLimitRepository.getAppLimit(packageName) ?: return null

        val dailyUsed = usageRecordRepository.getDailyUsageSeconds(packageName, now)
        val weeklyUsed = usageRecordRepository.getWeeklyUsageSeconds(packageName, now)

        val resolvedKind = intentKind
            ?: if (!purpose.isNullOrBlank()) IntentKind.PURPOSEFUL else null
        val fromGate = entry == HaEvents.Entry.GATE
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
                sessionExtensionMinutes = 0,
                gateDwellMs = gateDwellMs.coerceAtLeast(0L)
            )
        )

        val (dailyLimitSec, graceBonusSec, dailyBaseSec) = dailyLimitWithGraceSeconds(
            packageName,
            limit.effectiveDailyLimitMinutes()
        )
        val todayEnterCount = resolveTodayEnterCount(packageName, now)
        val session = UsageSession(
            recordId = recordId,
            packageName = packageName,
            appName = appName,
            startTime = now,
            sessionOriginStartMs = now,
            segmentElapsedRealtimeMs = nowElapsed,
            dailyLimitSeconds = dailyLimitSec,
            dailyUsedSeconds = dailyUsed,
            weeklyLimitSeconds = limit.effectiveWeeklyLimitMinutes() * 60L,
            weeklyUsedSeconds = weeklyUsed,
            purpose = purpose,
            intentKind = resolvedKind,
            sessionLimitSeconds = clampedSessionMinutes * 60L,
            requireIntentOnOpen = limit.requireIntentOnOpen || fromGate,
            timeLimitEnabled = limit.timeLimitEnabled,
            todayEnterCount = todayEnterCount,
            intentReviewEnabled = limit.intentReviewEnabled,
            compareEnabled = limit.requireIntentOnOpen && limit.compareEnabled,
            compareMinMinutes = ComparePolicy.sanitizeMinMinutes(limit.compareMinMinutes),
            dailyBaseLimitSeconds = dailyBaseSec,
            dailyGraceBonusSeconds = graceBonusSec
        )
        _currentSession.value = session
        syncActiveSessionCheckpoint()
        val resolvedEntry = entry ?: if (!purpose.isNullOrBlank()) {
            HaEvents.Entry.GATE
        } else {
            HaEvents.Entry.DIRECT
        }
        analyticsRepository.trackSessionStart(
            sessionId = recordId,
            app = appName,
            pkg = packageName,
            entry = resolvedEntry,
            purpose = purpose,
            sessionLimitMinutes = clampedSessionMinutes
        )
        return session
    }

    /**
     * 会话跨过本地零点：昨天的有效时长收到昨天，今天从零重新计。
     * 意图与剩余会话额度沿用，不重新过门。
     */
    suspend fun rollToNewDayIfNeeded() {
        val session = _currentSession.value ?: return
        val now = System.currentTimeMillis()
        val (todayStart, _) = UsageRecordRepository.getDayRange(now)
        if (session.sessionOriginStartMs >= todayStart) return

        val segmentMs = if (session.isInBackground) {
            0L
        } else if (session.segmentElapsedRealtimeMs > 0L) {
            (SystemClock.elapsedRealtime() - session.segmentElapsedRealtimeMs).coerceAtLeast(0L)
        } else {
            (now - session.startTime).coerceAtLeast(0L)
        }
        val segmentStartedToday = !session.isInBackground && session.startTime >= todayStart
        val todayActiveMs = when {
            session.isInBackground -> 0L
            segmentStartedToday -> segmentMs
            else -> (now - todayStart).coerceAtLeast(0L).coerceAtMost(segmentMs)
        }
        val yesterdayMs = if (segmentStartedToday) {
            session.accumulatedActiveMs
        } else {
            session.accumulatedActiveMs + (segmentMs - todayActiveMs)
        }

        val existing = usageRecordRepository.getRecordById(session.recordId)
        usageRecordRepository.updateRecord(
            UsageRecordEntity(
                id = session.recordId,
                packageName = session.packageName,
                startTime = session.sessionOriginStartMs,
                endTime = todayStart,
                durationSeconds = yesterdayMs / 1000L,
                endReason = UsageRecordEntity.EndReason.DAY_ROLLOVER,
                purpose = session.purpose,
                intentKind = session.intentKind?.name,
                sessionLimitMinutes = (session.sessionLimitSeconds / 60L).toInt(),
                sessionExtensionMinutes = (session.sessionExtensionSeconds / 60L).toInt(),
                note = existing?.note,
                effectScore = existing?.effectScore,
                mindfulnessLevel = existing?.mindfulnessLevel,
                driftSeconds = existing?.driftSeconds,
                gateDwellMs = existing?.gateDwellMs ?: 0L
            )
        )
        val newId = usageRecordRepository.insertRecord(
            UsageRecordEntity(
                packageName = session.packageName,
                startTime = if (session.isInBackground) todayStart else now - todayActiveMs,
                endTime = -1L,
                purpose = session.purpose,
                intentKind = session.intentKind?.name,
                sessionLimitMinutes = (session.sessionLimitSeconds / 60L).toInt(),
                sessionExtensionMinutes = (session.sessionExtensionSeconds / 60L).toInt()
            )
        )
        val dailyUsed = usageRecordRepository.getDailyUsageSeconds(session.packageName, now)
        val continuedStart = if (session.isInBackground) todayStart else now - todayActiveMs
        _currentSession.value = session.copy(
            recordId = newId,
            startTime = continuedStart,
            sessionOriginStartMs = continuedStart,
            accumulatedActiveMs = 0L,
            segmentElapsedRealtimeMs = if (session.isInBackground) {
                0L
            } else {
                SystemClock.elapsedRealtime() - todayActiveMs
            },
            dailyUsedSeconds = dailyUsed
        )
        syncActiveSessionCheckpoint()
        Log.d(
            "SessionManager",
            "${session.packageName} 跨天重计：昨日 ${yesterdayMs / 1000L}s，今日已用 ${dailyUsed}s"
        )
    }

    /**
     * 意图门再次放行前：收口同包残留会话（含互切挂起），避免复用旧计时导致胶囊不出现。
     */
    private suspend fun finalizeStaleSessionBeforeGateRestart(packageName: String) {
        parkedBySwitch.remove(packageName)?.let { parked ->
            _currentSession.value = parked.session
            endSession(UsageRecordEntity.EndReason.GATE_REENTER)
        }
        val existing = _currentSession.value
        if (existing != null && existing.packageName == packageName) {
            endSession(UsageRecordEntity.EndReason.GATE_REENTER)
        }
        pendingInterruptStore.clear(packageName)
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

        val originStart = record?.startTime?.takeIf { it > 0 }
            ?: (pending.endedAt - accumulated * 1000L).coerceAtMost(pending.endedAt)

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
                    startTime = originStart,
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
        val (dailyLimitSec, graceBonusSec, dailyBaseSec) = dailyLimitWithGraceSeconds(
            pending.packageName,
            limit.effectiveDailyLimitMinutes()
        )
        val todayEnterCount = resolveTodayEnterCount(pending.packageName, now)

        val session = UsageSession(
            recordId = recordId,
            packageName = pending.packageName,
            appName = pending.appName,
            startTime = now,
            sessionOriginStartMs = originStart,
            segmentElapsedRealtimeMs = SystemClock.elapsedRealtime(),
            dailyLimitSeconds = dailyLimitSec,
            dailyUsedSeconds = dailyUsed,
            weeklyLimitSeconds = limit.effectiveWeeklyLimitMinutes() * 60L,
            weeklyUsedSeconds = weeklyUsed,
            accumulatedActiveMs = accumulated * 1000L,
            purpose = pending.purpose ?: record?.purpose,
            intentKind = resolvedKind,
            sessionLimitSeconds = sessionLimitMin * 60L,
            sessionExtensionSeconds = extensionMin * 60L,
            sessionExtensionUsed = extensionMin > 0,
            requireIntentOnOpen = limit.requireIntentOnOpen,
            timeLimitEnabled = limit.timeLimitEnabled,
            todayEnterCount = todayEnterCount,
            intentReviewEnabled = limit.intentReviewEnabled,
            compareEnabled = limit.requireIntentOnOpen && limit.compareEnabled,
            compareMinMinutes = ComparePolicy.sanitizeMinMinutes(limit.compareMinMinutes),
            dailyBaseLimitSeconds = dailyBaseSec,
            dailyGraceBonusSeconds = graceBonusSec
        )
        _currentSession.value = session
        syncActiveSessionCheckpoint()
        pendingInterruptStore.clear(pending.packageName)
        analyticsRepository.trackGateResume(
            sessionId = recordId,
            app = pending.appName,
            pkg = pending.packageName,
            priorEndReason = pending.endReason
        )
        analyticsRepository.trackSessionStart(
            sessionId = recordId,
            app = pending.appName,
            pkg = pending.packageName,
            entry = HaEvents.Entry.RESUME,
            purpose = session.purpose,
            sessionLimitMinutes = sessionLimitMin
        )
        return session
    }

    /** App 进入后台：快照当前已累计的有效前台时长，停止计时增长 */
    fun onAppGoBackground() {
        val session = _currentSession.value ?: return
        if (session.isInBackground) return
        val now = System.currentTimeMillis()
        val segmentMs = if (session.segmentElapsedRealtimeMs > 0L) {
            (SystemClock.elapsedRealtime() - session.segmentElapsedRealtimeMs).coerceAtLeast(0L)
        } else {
            (now - session.startTime).coerceAtLeast(0L)
        }
        _currentSession.value = session.copy(
            isInBackground = true,
            backgroundSinceMs = now,
            accumulatedActiveMs = session.accumulatedActiveMs + segmentMs,
            segmentElapsedRealtimeMs = 0L
        )
        syncActiveSessionCheckpoint()
    }

    /** App 回到前台：重置前台段起点，继续累计计时 */
    fun onAppReturnToForeground() {
        val session = _currentSession.value ?: return
        if (!session.isInBackground) return
        _currentSession.value = session.copy(
            isInBackground = false,
            backgroundSinceMs = 0L,
            startTime = System.currentTimeMillis(),
            segmentElapsedRealtimeMs = SystemClock.elapsedRealtime()
        )
        syncActiveSessionCheckpoint()
    }

    /**
     * 挂起当前会话，腾出槽位给另一个被监控 App。
     * 宽限期内 [restoreParkedSession] 可无缝续上；超时由 [finalizeExpiredParkedSessions] 落库。
     */
    fun parkCurrentSessionForSwitch(graceMs: Long): UsageSession? {
        val session = _currentSession.value ?: return null
        if (!session.isInBackground) {
            onAppGoBackground()
        }
        val frozen = _currentSession.value ?: return null
        val grace = graceMs.coerceAtLeast(0L)
        parkedBySwitch[frozen.packageName] = ParkedSwitchSession(
            session = frozen,
            expireElapsedRealtimeMs = SystemClock.elapsedRealtime() + grace
        )
        _currentSession.value = null
        syncActiveSessionCheckpoint()
        return frozen
    }

    /** 宽限期内取回挂起会话；已过期则返回 null（留给 finalize 落库） */
    fun restoreParkedSession(packageName: String): UsageSession? {
        val parked = parkedBySwitch[packageName] ?: return null
        if (SystemClock.elapsedRealtime() >= parked.expireElapsedRealtimeMs) {
            return null
        }
        // 恢复前若仍占着别的当前会话，调用方应先 park / end
        if (_currentSession.value != null) {
            return null
        }
        parkedBySwitch.remove(packageName)
        _currentSession.value = parked.session
        syncActiveSessionCheckpoint()
        return parked.session
    }

    fun hasParkedSession(packageName: String): Boolean =
        parkedBySwitch.containsKey(packageName)

    /** 最近一个挂起到期点（elapsedRealtime）；无挂起则 null */
    fun nextParkedExpiryElapsedMs(): Long? =
        parkedBySwitch.values.minOfOrNull { it.expireElapsedRealtimeMs }

    /**
     * 落库所有已过期的挂起会话。
     * 若当时已有前台会话，会暂存后写回，避免误清。
     */
    suspend fun finalizeExpiredParkedSessions(
        reason: String = UsageRecordEntity.EndReason.SWITCHED_AWAY
    ): Int {
        val nowElapsed = SystemClock.elapsedRealtime()
        val expiredPkgs = parkedBySwitch
            .filter { (_, p) -> nowElapsed >= p.expireElapsedRealtimeMs }
            .keys
            .toList()
        return finalizeParkedPackages(expiredPkgs, reason)
    }

    /** 服务销毁等场景：挂起中的会话一律落库，避免留下「进行中」脏记录 */
    suspend fun finalizeAllParkedSessions(
        reason: String = UsageRecordEntity.EndReason.APP_CLOSED
    ): Int = finalizeParkedPackages(parkedBySwitch.keys.toList(), reason)

    private suspend fun finalizeParkedPackages(pkgs: List<String>, reason: String): Int {
        if (pkgs.isEmpty()) return 0
        val held = _currentSession.value
        var count = 0
        for (pkg in pkgs) {
            val parked = parkedBySwitch.remove(pkg) ?: continue
            _currentSession.value = parked.session
            endSession(reason)
            count++
        }
        if (held != null && _currentSession.value == null) {
            _currentSession.value = held
        }
        return count
    }

    /**
     * 结束当前会话并持久化到本机数据库。
     */
    suspend fun endSession(
        reason: String,
        note: String? = null,
        mindfulnessLevel: Int? = null,
        effectScore: Int? = null,
        driftSeconds: Long? = null
    ) {
        val session = _currentSession.value ?: return
        val now = System.currentTimeMillis()
        val duration = session.currentSessionSeconds
        val existing = usageRecordRepository.getRecordById(session.recordId)
        val originStart = when {
            session.sessionOriginStartMs > 0L -> session.sessionOriginStartMs
            existing != null && existing.startTime > 0L -> existing.startTime
            else -> session.startTime
        }

        val resolvedLevel = mindfulnessLevel?.takeIf {
            UsageRecordEntity.MindfulnessLevel.isValid(it)
        } ?: existing?.mindfulnessLevel
        val resolvedDrift = com.life.mindfulnessapp.domain.model.DriftSecondsPolicy.resolveStored(
            level = resolvedLevel,
            driftSeconds = driftSeconds ?: existing?.driftSeconds,
            durationSeconds = duration
        )

        usageRecordRepository.updateRecord(
            UsageRecordEntity(
                id = session.recordId,
                packageName = session.packageName,
                startTime = originStart,
                endTime = now,
                durationSeconds = duration,
                endReason = reason,
                purpose = session.purpose,
                intentKind = session.intentKind?.name,
                sessionLimitMinutes = (session.sessionLimitSeconds / 60L).toInt(),
                sessionExtensionMinutes = (session.sessionExtensionSeconds / 60L).toInt(),
                note = note?.takeIf { it.isNotBlank() } ?: existing?.note,
                effectScore = effectScore ?: existing?.effectScore,
                mindfulnessLevel = resolvedLevel,
                driftSeconds = resolvedDrift
            )
        )
        analyticsRepository.trackSessionEnd(
            sessionId = session.recordId,
            app = session.appName,
            pkg = session.packageName,
            endReason = reason,
            durationSeconds = duration,
            extended = session.sessionExtensionUsed || session.sessionExtensionSeconds > 0L,
            hadPurpose = !session.purpose.isNullOrBlank(),
            level = resolvedLevel,
            purpose = session.purpose
        )
        if (resolvedLevel != null || !note.isNullOrBlank()) {
            val source = when (reason) {
                UsageRecordEntity.EndReason.SESSION_LIMIT_REACHED -> HaEvents.Source.SESSION_LIMIT
                UsageRecordEntity.EndReason.MANUAL -> HaEvents.Source.CAPSULE
                else -> HaEvents.Source.CAPSULE
            }
            analyticsRepository.trackCompareSave(
                sessionId = session.recordId,
                source = source,
                hasNote = !note.isNullOrBlank(),
                hasLevel = resolvedLevel != null,
                level = resolvedLevel,
                app = session.appName,
                pkg = session.packageName
            )
            appPreferences.recordCompareOutcome(resolvedLevel)
        }
        _currentSession.value = null
        activeSessionCheckpointStore.clear()
        applyPendingInterruptPolicy(session, reason, duration, now)
        armBrowseCasualCooldownIfNeeded(session, now)
    }

    /** 随意浏览会话收口后，按累计用量与连进武装下次冷却。 */
    private suspend fun armBrowseCasualCooldownIfNeeded(session: UsageSession, nowMs: Long) {
        val isBrowse = CompanionPath.resolve(
            session.intentKind,
            session.purpose,
            session.hasSessionLimit
        ) == CompanionPath.BROWSE ||
            BrowseCasualIntent.isBrowseLike(session.purpose.orEmpty())
        if (!isBrowse) return
        val todayKey = appPreferences.browseCasualCooldownTodayKey()
        val usedMin = DailyCapFacts.wholeMinutes(
            usageRecordRepository.getTodayBrowseCasualSeconds(session.packageName)
        )
        val browseLimit = appLimitRepository
            .getBrowseCasualPolicy(session.packageName)
            .effectiveDailyLimitMinutes()
        val next = BrowseCasualCooldown.afterSessionEnded(
            persisted = appPreferences.getBrowseCasualCooldown(session.packageName),
            todayKey = todayKey,
            nowMs = nowMs,
            usedBrowseMinutes = usedMin,
            browseDailyLimitMinutes = browseLimit
        )
        appPreferences.setBrowseCasualCooldown(session.packageName, next)
        if (next.cooldownUntilMs > nowMs) {
            val mins = BrowseCasualCooldown.remainingCooldownMinutes(next.cooldownUntilMs, nowMs)
            Log.d(
                "SessionManager",
                "随意浏览冷却已武装 [${session.packageName}] ${mins}分 " +
                    "(used=${usedMin} streak=${next.streakCount})"
            )
        }
    }

    /** 服务非主动停止前写入磁盘，便于同进程或进程重启后恢复。 */
    fun syncActiveSessionCheckpoint() {
        val session = _currentSession.value
        if (session != null) {
            activeSessionCheckpointStore.save(session)
        } else {
            activeSessionCheckpointStore.clear()
        }
    }

    fun peekCheckpointRecordId(): Long? = activeSessionCheckpointStore.peekRecordId()

    fun clearActiveSessionCheckpoint() {
        activeSessionCheckpointStore.clear()
    }

    /**
     * 从 checkpoint 恢复内存会话（不创建新 DB 记录）。
     * @return 恢复后的会话；校验失败时清除 checkpoint 并返回 null
     */
    suspend fun restoreSessionFromCheckpoint(): UsageSession? {
        if (_currentSession.value != null) return _currentSession.value
        val checkpoint = activeSessionCheckpointStore.load() ?: return null
        val record = usageRecordRepository.getRecordById(checkpoint.recordId)
        if (record == null || record.endTime > 0L || record.packageName != checkpoint.packageName) {
            activeSessionCheckpointStore.clear()
            return null
        }
        _currentSession.value = checkpoint
        return checkpoint
    }

    private fun applyPendingInterruptPolicy(
        session: UsageSession,
        reason: String,
        durationSeconds: Long,
        endedAt: Long
    ) {
        when {
            reason == UsageRecordEntity.EndReason.MANUAL ||
                reason == UsageRecordEntity.EndReason.LIMIT_REACHED ||
                reason == UsageRecordEntity.EndReason.SESSION_LIMIT_REACHED ||
                reason == UsageRecordEntity.EndReason.PERIOD_LOCK -> {
                pendingInterruptStore.clear(session.packageName)
            }
            session.requireIntentOnOpen &&
                UsageRecordEntity.EndReason.shouldOfferResumeConfirm(reason) &&
                durationSeconds >= MIN_DURATION_FOR_RESUME_CONFIRM_SEC &&
                PendingInterrupt.isNamedIntentOrSearch(
                    purpose = session.purpose,
                    intentKind = session.intentKind,
                    sessionLimitMinutes = (session.sessionLimitSeconds / 60L).toInt()
                ) -> {
                // 仅有名意图 / 搜索写入快照；随意浏览不进强续
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
                        durationSeconds = durationSeconds,
                        endedAt = endedAt
                    )
                )
            }
            else -> {
                pendingInterruptStore.clear(session.packageName)
            }
        }
    }

    private suspend fun maybeSavePendingFromOrphanRecord(
        record: UsageRecordEntity,
        endReason: String,
        endedAt: Long
    ) {
        val limit = appLimitRepository.getAppLimit(record.packageName) ?: return
        val duration = when {
            record.durationSeconds > 0L -> record.durationSeconds
            record.startTime > 0L -> ((endedAt - record.startTime) / 1000L).coerceAtLeast(0L)
            else -> 0L
        }
        val pseudoSession = UsageSession(
            recordId = record.id,
            packageName = record.packageName,
            appName = limit.appName,
            startTime = record.startTime,
            sessionOriginStartMs = record.startTime,
            dailyLimitSeconds = 0L,
            dailyUsedSeconds = 0L,
            weeklyLimitSeconds = 0L,
            weeklyUsedSeconds = 0L,
            purpose = record.purpose,
            intentKind = IntentKind.fromStorage(record.intentKind),
            sessionLimitSeconds = record.sessionLimitMinutes * 60L,
            sessionExtensionSeconds = record.sessionExtensionMinutes * 60L,
            requireIntentOnOpen = limit.requireIntentOnOpen,
            timeLimitEnabled = limit.timeLimitEnabled,
            intentReviewEnabled = limit.intentReviewEnabled,
            compareEnabled = limit.requireIntentOnOpen && limit.compareEnabled,
            compareMinMinutes = ComparePolicy.sanitizeMinMinutes(limit.compareMinMinutes)
        )
        applyPendingInterruptPolicy(pseudoSession, endReason, duration, endedAt)
    }

    /**
     * 收口库里残留的「未结束」记录（endTime ≤ 0）。
     * 进程被杀 / Service onDestroy 竞态时容易留下脏数据，导致首页与详情长期显示「进行中」。
     *
     * @param exceptRecordId 保留为进行中的当前会话记录（若有）
     */
    suspend fun closeOrphanedOpenRecords(
        exceptRecordId: Long? = _currentSession.value?.recordId
    ) {
        val now = System.currentTimeMillis()
        val open = usageRecordRepository.getOpenRecords()
        for (record in open) {
            if (exceptRecordId != null && record.id == exceptRecordId) continue
            val duration = record.durationSeconds.coerceAtLeast(0L)
            val end = when {
                record.endTime > 0L -> record.endTime
                duration > 0L -> (record.startTime + duration * 1000L).coerceAtMost(now)
                else -> now.coerceAtLeast(record.startTime)
            }
            val endReason = record.endReason
                .takeIf { it.isNotBlank() && it != UsageRecordEntity.EndReason.UNKNOWN }
                ?: UsageRecordEntity.EndReason.APP_CLOSED
            usageRecordRepository.updateRecord(
                record.copy(
                    endTime = end,
                    durationSeconds = duration,
                    endReason = endReason
                )
            )
            maybeSavePendingFromOrphanRecord(
                record = record.copy(
                    endTime = end,
                    durationSeconds = duration,
                    endReason = endReason
                ),
                endReason = endReason,
                endedAt = end
            )
        }
    }

    /**
     * 开启「日限触顶延长」会话：从当前已用起再给 N 分钟，并继续前台计时。
     *
     * - 须带意图；档位限定 5/10/15
     * - 胶囊展示基础限额后的彩色 +N，并以本次 N 分钟作为单次倒计时
     * - 延长本身已是当日一次机会，会话内不可再续
     */
    suspend fun startOverLimitSession(
        packageName: String,
        appName: String,
        purpose: String? = null,
        intentKind: IntentKind? = null,
        graceMinutes: Int = BreathCostPolicy.DEFAULT_DAILY_GRACE_MINUTES
    ): UsageSession? {
        val now = System.currentTimeMillis()
        val limit = appLimitRepository.getAppLimit(packageName) ?: return null
        val clampedGrace = BreathCostPolicy.clampGraceMinutes(graceMinutes)
        val trimmedPurpose = purpose?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        closeOrphanedOpenRecords()
        pendingInterruptStore.clear(packageName)

        val dailyUsed = usageRecordRepository.getDailyUsageSeconds(packageName, now)
        val weeklyUsed = usageRecordRepository.getWeeklyUsageSeconds(packageName, now)
        val baseMinutes = limit.effectiveDailyLimitMinutes()
        val baseSec = baseMinutes.coerceAtLeast(0) * 60L
        val graceSec = clampedGrace * 60L
        // 「延长 N 分钟」= 从现在起再给 N 分钟，而不是仅 base+N（已超额很久时 base+N 仍会立刻触顶）
        val ceilingSec = dailyUsed + graceSec

        appPreferences.markDailyGraceUsed(
            packageName = packageName,
            minutes = clampedGrace,
            ceilingSeconds = ceilingSec
        )
        // 与 effectiveDailyLimitSeconds 一致：max(基础, 天花板)
        val dailyLimitSec = maxOf(baseSec, ceilingSec)
        val graceBonusSec = (dailyLimitSec - baseSec).coerceAtLeast(0L)

        val recordId = usageRecordRepository.insertRecord(
            UsageRecordEntity(
                packageName = packageName,
                startTime = now,
                endTime = -1L,
                purpose = trimmedPurpose,
                intentKind = intentKind?.name,
                sessionLimitMinutes = clampedGrace
            )
        )

        val todayEnterCount = resolveTodayEnterCount(packageName, now)
        val session = UsageSession(
            recordId = recordId,
            packageName = packageName,
            appName = appName,
            startTime = now,
            sessionOriginStartMs = now,
            segmentElapsedRealtimeMs = SystemClock.elapsedRealtime(),
            dailyLimitSeconds = dailyLimitSec,
            dailyUsedSeconds = dailyUsed,
            weeklyLimitSeconds = limit.effectiveWeeklyLimitMinutes() * 60L,
            weeklyUsedSeconds = weeklyUsed,
            purpose = trimmedPurpose,
            intentKind = intentKind,
            sessionLimitSeconds = graceSec,
            sessionExtensionUsed = true,
            isOverLimitSession = false,
            requireIntentOnOpen = limit.requireIntentOnOpen,
            timeLimitEnabled = true,
            todayEnterCount = todayEnterCount,
            intentReviewEnabled = limit.intentReviewEnabled,
            compareEnabled = limit.requireIntentOnOpen && limit.compareEnabled,
            compareMinMinutes = ComparePolicy.sanitizeMinMinutes(limit.compareMinMinutes),
            dailyBaseLimitSeconds = baseSec,
            dailyGraceBonusSeconds = graceBonusSec
        )
        _currentSession.value = session
        syncActiveSessionCheckpoint()
        analyticsRepository.trackSessionStart(
            sessionId = recordId,
            app = appName,
            pkg = packageName,
            entry = HaEvents.Entry.GRACE,
            purpose = trimmedPurpose,
            sessionLimitMinutes = clampedGrace
        )
        return session
    }

    /**
     * 为当前会话授予一次续时（session grant，不改每日限额）。
     * 仅允许一次；胶囊临期或到点页「续一点时间」触发。
     * 上限：原时长 1/3；随意浏览另卡今日浏览剩余，并记入连进。
     *
     * 随意浏览到点续时须走 [extendBrowseSessionAsIntent]（先命名再续）。
     *
     * @param extraMinutes 要延长的分钟数
     * @return true = 成功
     */
    suspend fun extendSessionOnce(extraMinutes: Int): Boolean = extendMutex.withLock {
        val session = _currentSession.value ?: return false
        // 随意浏览禁止无锚续时
        if (session.isBrowseCompanion) return false
        return extendSessionOnceLocked(session, extraMinutes, namedPurpose = null)
    }

    /**
     * 随意浏览到点：命名升级为意图后续时。
     * 同会话 BROWSE → INTENT，不新建 record。
     */
    suspend fun extendBrowseSessionAsIntent(
        extraMinutes: Int,
        purpose: String,
    ): Boolean = extendMutex.withLock {
        val session = _currentSession.value ?: return false
        if (!session.isBrowseCompanion) return false
        val naming = purpose.trim()
        if (naming.isEmpty() || BrowseCasualIntent.isBrowseLike(naming)) return false
        return extendSessionOnceLocked(session, extraMinutes, namedPurpose = naming)
    }

    private suspend fun extendSessionOnceLocked(
        session: UsageSession,
        extraMinutes: Int,
        namedPurpose: String?,
    ): Boolean {
        if (session.sessionExtensionUsed || session.isOverLimitSession || !session.hasSessionLimit) {
            return false
        }
        if (namedPurpose == null) {
            if (!session.canOfferSessionExtension && !session.canOfferLimitReachedExtension) {
                return false
            }
        } else {
            if (!session.canOfferLimitReachedExtension) return false
        }

        val wasBrowse = session.isBrowseCompanion
        val baseMinutes = (session.sessionLimitSeconds / 60L).toInt()
        val remainingBrowse = if (wasBrowse) {
            remainingBrowseMinutesFor(session)
        } else {
            null
        }
        val maxExtra = SessionLimitPolicy.maxLimitReachedExtensionMinutes(
            sessionLimitMinutes = baseMinutes,
            remainingBrowseMinutes = remainingBrowse
        )
        if (extraMinutes <= 0 || extraMinutes > maxExtra) return false

        val newExtensionSeconds = extraMinutes * 60L
        val resolvedKind = if (namedPurpose != null) IntentKind.PURPOSEFUL else session.intentKind
        val record = usageRecordRepository.getRecordById(session.recordId)
        if (record != null) {
            usageRecordRepository.updateRecord(
                record.copy(
                    sessionExtensionMinutes = extraMinutes,
                    purpose = namedPurpose ?: record.purpose,
                    intentKind = resolvedKind?.name ?: record.intentKind
                )
            )
        }

        if (wasBrowse) {
            bumpBrowseCasualStreakOnExtend(session.packageName)
        }

        _currentSession.value = session.copy(
            sessionExtensionSeconds = newExtensionSeconds,
            sessionExtensionUsed = true,
            purpose = namedPurpose ?: session.purpose,
            intentKind = resolvedKind
        )
        val remain = (_currentSession.value?.let {
            (it.effectiveSessionLimitSeconds - it.currentSessionSeconds).coerceAtLeast(0L)
        } ?: 0L)
        analyticsRepository.trackSessionExtend(
            sessionId = session.recordId,
            app = session.appName,
            pkg = session.packageName,
            extendMin = extraMinutes,
            remainSec = remain
        )
        syncActiveSessionCheckpoint()
        return true
    }

    /** 今日随意浏览还剩几分（含当前会话已用）；不限 → [Int.MAX_VALUE]。 */
    private suspend fun remainingBrowseMinutesFor(session: UsageSession): Int {
        val limitMin = appLimitRepository
            .getBrowseCasualPolicy(session.packageName)
            .effectiveDailyLimitMinutes()
        if (limitMin <= 0) return Int.MAX_VALUE
        val closedSec = usageRecordRepository.getTodayBrowseCasualSeconds(session.packageName)
        val usedSec = closedSec + session.currentSessionSeconds.coerceAtLeast(0L)
        return DailyCapFacts.remainingMinutes(usedSec, limitMin * 60L) ?: 0
    }

    /** 到点续时计一次连刷。 */
    private fun bumpBrowseCasualStreakOnExtend(packageName: String) {
        val todayKey = appPreferences.browseCasualCooldownTodayKey()
        val nowMs = System.currentTimeMillis()
        val next = BrowseCasualCooldown.afterExtend(
            persisted = appPreferences.getBrowseCasualCooldown(packageName),
            todayKey = todayKey,
            nowMs = nowMs
        )
        appPreferences.setBrowseCasualCooldown(packageName, next)
        Log.d(
            "SessionManager",
            "随意浏览续时连进+1 [$packageName] streak=${next.streakCount}"
        )
    }

    /**
     * 统一续时入口：有单次上限走 [extendSessionOnce]；
     * 纯日锁则临时抬高本会话日限额天花板，同一会话只允许一次。
     * 不修改用户配置的每日限额（避免下次 1/3 越续越大）。
     */
    suspend fun extendBudgetOnce(extraMinutes: Int): Boolean {
        val session = _currentSession.value ?: return false
        if (session.hasSessionLimit) {
            return extendSessionOnce(extraMinutes)
        }
        return extendDailyLimit(extraMinutes)
    }

    /**
     * 纯日锁临近结束续时：仅抬高本会话内存中的今日限额，并标记已续过（不可再续）。
     * **不**写回配置里的每日限额。
     */
    suspend fun extendDailyLimit(extraMinutes: Int): Boolean = extendMutex.withLock {
        val session = _currentSession.value ?: return@withLock false
        if (session.hasSessionLimit || !session.canOfferSessionExtension) return@withLock false
        val baseMinutes = session.extensionBaseMinutes
        val maxExtra = SessionLimitPolicy.maxExtensionMinutes(baseMinutes)
        if (extraMinutes <= 0 || extraMinutes > maxExtra) return@withLock false

        val newExtensionSeconds = extraMinutes * 60L
        val record = usageRecordRepository.getRecordById(session.recordId)
        if (record != null) {
            usageRecordRepository.updateRecord(
                record.copy(sessionExtensionMinutes = extraMinutes)
            )
        }

        _currentSession.value = session.copy(
            dailyLimitSeconds = session.dailyLimitSeconds + newExtensionSeconds,
            dailyGraceBonusSeconds = session.dailyGraceBonusSeconds + newExtensionSeconds,
            sessionExtensionUsed = true,
            sessionExtensionSeconds = newExtensionSeconds
        )
        val remain = (_currentSession.value?.dailyRemainingSeconds?.let {
            if (it == Long.MAX_VALUE) 0L else it
        } ?: 0L)
        analyticsRepository.trackSessionExtend(
            sessionId = session.recordId,
            app = session.appName,
            pkg = session.packageName,
            extendMin = extraMinutes,
            remainSec = remain
        )
        syncActiveSessionCheckpoint()
        true
    }

    fun hasActiveSession(): Boolean = _currentSession.value != null

    fun getCurrentPackage(): String? = _currentSession.value?.packageName

    private suspend fun resolveTodayEnterCount(packageName: String, now: Long): Int {
        val dayRecords = usageRecordRepository.getDayRecordsForApp(packageName, now)
        return UsageRecordCounts.enterCount(dayRecords.filter { !it.isSeed }) + 1
    }

    companion object {
        /** 短于此时长的异常结束不弹出下次确认（避免误触噪音） */
        /** 不足此时长不写入续接快照，避免误触产生的极短会话提供「继续上次」。 */
        const val MIN_DURATION_FOR_RESUME_CONFIRM_SEC = 5L
    }
}
