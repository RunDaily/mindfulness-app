package com.life.mindfulnessapp.service

import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.data.repository.AccountRepository
import com.life.mindfulnessapp.data.repository.AppLimitRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository
import com.life.mindfulnessapp.domain.model.UsageSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 管理当前使用会话状态（单例，供 Service 和 Overlay 共享）
 */
@Singleton
class SessionManager @Inject constructor(
    private val appLimitRepository: AppLimitRepository,
    private val usageRecordRepository: UsageRecordRepository,
    private val accountRepository: AccountRepository
) {
    // 用于后台同步的独立 CoroutineScope（不绑定任何 Lifecycle）
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _currentSession = MutableStateFlow<UsageSession?>(null)
    val currentSession: StateFlow<UsageSession?> = _currentSession

    /**
     * 开始一个新的使用会话
     * @param purpose 使用目的（由拦截页用户属入的目的，null 表示未填写）
     */
    suspend fun startSession(
        packageName: String,
        appName: String,
        purpose: String? = null
    ): UsageSession? {
        // 已有相同 App 的会话，不重复创建
        val existing = _currentSession.value
        if (existing != null && existing.packageName == packageName) {
            return existing
        }

        val now = System.currentTimeMillis()
        val limit = appLimitRepository.getAppLimit(packageName) ?: return null

        val dailyUsed = usageRecordRepository.getDailyUsageSeconds(packageName, now)
        val weeklyUsed = usageRecordRepository.getWeeklyUsageSeconds(packageName, now)

        // 创建数据库记录（进行中状态），同时写入使用目的
        val recordId = usageRecordRepository.insertRecord(
            UsageRecordEntity(
                packageName = packageName,
                startTime = now,
                endTime = -1L,
                purpose = purpose
            )
        )

        val session = UsageSession(
            recordId = recordId,
            packageName = packageName,
            appName = appName,
            startTime = now,
            dailyLimitSeconds = limit.dailyLimitMinutes * 60L,
            dailyUsedSeconds = dailyUsed,
            weeklyLimitSeconds = limit.weeklyLimitMinutes * 60L,
            weeklyUsedSeconds = weeklyUsed,
            purpose = purpose
        )
        _currentSession.value = session
        return session
    }

    /** App 进入后台：快照当前已累计的有效前台时长，停止计时增长 */
    fun onAppGoBackground() {
        val session = _currentSession.value ?: return
        val now = System.currentTimeMillis()
        // 将当前段的时长加入累计值，并标记进入后台
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
            startTime = System.currentTimeMillis()  // 重置「当前段」起点
        )
    }

    /**
     * 结束当前会话并持久化记录，若已登录则后台静默同步到云端
     * @param reason 结束原因
     * @param note 用户在结束弹框填写的备注（null 表示未填写）
     * @param effectScore 用户对本次使用效果的自评分 0-10（null 表示未评分）
     */
    suspend fun endSession(
        reason: String,
        note: String? = null,
        effectScore: Int? = null
    ) {
        val session = _currentSession.value ?: return
        val now = System.currentTimeMillis()
        // 使用有效前台时长（排除后台时间），而非原始时间差
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
                note = note?.takeIf { it.isNotBlank() },
                effectScore = effectScore
            )
        )
        _currentSession.value = null

        // 若用户已登录，后台静默上传本次会话数据到云端（失败不影响正常使用）
        if (accountRepository.isLoggedIn) {
            syncScope.launch {
                accountRepository.syncSessions()
            }
        }
    }

    /**
     * 开启「超限续记」会话：用户在超限页关闭后 App 仍在前台时调用。
     *
     * 与普通 [startSession] 的区别：
     *   - 不校验是否已有相同 App 的会话（调用方已先 endSession）
     *   - 写入数据库记录时 purpose 固定为 null（超限继续，不需要用户填写目的）
     *   - 返回的 [UsageSession.isOverLimitSession] = true，
     *     监控循环检测到超限时不会再次弹出超限页
     *
     * @param packageName 被监控 App 包名
     * @param appName     App 显示名称
     * @return 创建成功的会话，若无法获取限额则返回 null
     */
    suspend fun startOverLimitSession(
        packageName: String,
        appName: String
    ): UsageSession? {
        val now = System.currentTimeMillis()
        val limit = appLimitRepository.getAppLimit(packageName) ?: return null

        val dailyUsed = usageRecordRepository.getDailyUsageSeconds(packageName, now)
        val weeklyUsed = usageRecordRepository.getWeeklyUsageSeconds(packageName, now)

        val recordId = usageRecordRepository.insertRecord(
            UsageRecordEntity(
                packageName = packageName,
                startTime = now,
                endTime = -1L,
                purpose = null
            )
        )

        val session = UsageSession(
            recordId = recordId,
            packageName = packageName,
            appName = appName,
            startTime = now,
            dailyLimitSeconds = limit.dailyLimitMinutes * 60L,
            dailyUsedSeconds = dailyUsed,
            weeklyLimitSeconds = limit.weeklyLimitMinutes * 60L,
            weeklyUsedSeconds = weeklyUsed,
            purpose = null,
            isOverLimitSession = true
        )
        _currentSession.value = session
        return session
    }

    /** 强制清除会话（不写记录，如切换到其他被监控App） */
    suspend fun clearSession(reason: String = UsageRecordEntity.EndReason.APP_CLOSED) {
        endSession(reason)
    }

    /**
     * 延长当前会话的每日限额（用户在倒计时胶囊中主动申请延时时调用）。
     *
     * 同时更新：
     *   1. 内存中的 [UsageSession.dailyLimitSeconds]，让胶囊计时立即反映新剩余时长
     *   2. 数据库中的 App 限制记录，确保本次延时对 Service 监控循环也生效
     *
     * 注意：此操作**不消耗**今日修改机会（修改机会仅用于用户主动「重新设定目标」场景）。
     *
     * @param extraMinutes 要延长的分钟数（如 5、10、15）
     * @return true = 成功；false = 无活跃会话或无法获取限额
     */
    suspend fun extendDailyLimit(extraMinutes: Int): Boolean {
        val session = _currentSession.value ?: return false
        val limit = appLimitRepository.getAppLimit(session.packageName) ?: return false

        val newDailyLimitMinutes = limit.dailyLimitMinutes + extraMinutes
        // 更新数据库（不消耗 modifyCount，使用原始 insert/update 路径）
        appLimitRepository.saveAppLimit(
            limit.copy(dailyLimitMinutes = newDailyLimitMinutes)
        )
        // 更新内存 session，让胶囊剩余时长立即刷新
        _currentSession.value = session.copy(
            dailyLimitSeconds = newDailyLimitMinutes * 60L
        )
        return true
    }

    fun hasActiveSession(): Boolean = _currentSession.value != null

    fun getCurrentPackage(): String? = _currentSession.value?.packageName
}
