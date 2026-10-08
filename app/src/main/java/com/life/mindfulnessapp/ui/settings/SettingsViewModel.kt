package com.life.mindfulnessapp.ui.settings

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.BuildConfig
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.CachedAuthor
import com.life.mindfulnessapp.data.network.AuthorRequestResult
import com.life.mindfulnessapp.data.repository.QuoteRepository
import com.life.mindfulnessapp.domain.model.ThemeMode
import com.life.mindfulnessapp.domain.model.ThemePack
import com.life.mindfulnessapp.data.analytics.FeedbackCategory
import com.life.mindfulnessapp.data.analytics.FeedbackThread
import com.life.mindfulnessapp.data.analytics.HaEvents
import com.life.mindfulnessapp.data.repository.AnalyticsRepository
import com.life.mindfulnessapp.data.repository.FeedbackInboxRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository
import com.life.mindfulnessapp.data.repository.VipRepository
import com.life.mindfulnessapp.debug.DebugMockUsageSeeder
import com.life.mindfulnessapp.display.DisplayGrayscaleController
import com.life.mindfulnessapp.domain.usecase.CheckPermissionsUseCase
import com.life.mindfulnessapp.domain.usecase.PermissionStatus
import com.life.mindfulnessapp.overlay.OverlayManager
import com.life.mindfulnessapp.service.MonitorForegroundService
import com.life.mindfulnessapp.util.FeedbackImageHelper
import com.life.mindfulnessapp.util.QuotePushScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val checkPermissionsUseCase: CheckPermissionsUseCase,
    private val appPreferences: AppPreferences,
    private val usageRecordRepository: UsageRecordRepository,
    private val vipRepository: VipRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val monitorProfileReporter: com.life.mindfulnessapp.data.repository.MonitorProfileReporter,
    private val feedbackInboxRepository: FeedbackInboxRepository,
    private val quoteRepository: QuoteRepository,
    private val debugMockUsageSeeder: DebugMockUsageSeeder,
    private val overlayManager: OverlayManager,
    private val displayGrayscaleController: DisplayGrayscaleController
) : ViewModel() {

    init {
        syncQuotes()
    }

    private val _permissionStatus = MutableStateFlow(PermissionStatus(false, false, false))
    val permissionStatus: StateFlow<PermissionStatus> = _permissionStatus

    private val _isServiceRunning = MutableStateFlow(MonitorForegroundService.isRunning)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning

    val themeMode: StateFlow<ThemeMode> = appPreferences.themeMode

    val themePack: StateFlow<ThemePack> = appPreferences.themePack

    val themeFollowSystem: StateFlow<Boolean> = appPreferences.themeFollowSystem

    /** 实时 VIP 等级，供 UI 判断是否显示 VIP 门禁提示 */
    val vipLevel: StateFlow<Int> = vipRepository.vipLevel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val betaUnlocked: StateFlow<Boolean> = appPreferences.betaUnlocked
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val claimedInviteCode: StateFlow<String> = appPreferences.claimedInviteCode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private val _betaCode = MutableStateFlow(appPreferences.getBetaCode())
    val betaCode: StateFlow<String> = _betaCode

    fun refreshInviteCodeCache() {
        _betaCode.value = appPreferences.getBetaCode()
    }

    /** 是否是 VIP 用户 */
    fun isVip(): Boolean = vipRepository.isVip()

    /** 加强保活开关：开启后额外运行一个独立守护前台服务 */
    val enhancedKeepAlive: StateFlow<Boolean> = appPreferences.enhancedKeepAlive
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** 多任务界面隐藏心锚（国产机默认开） */
    val hideFromRecents: StateFlow<Boolean> = appPreferences.hideFromRecents
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            appPreferences.isHideFromRecentsEnabled()
        )

    fun setHideFromRecents(enabled: Boolean) {
        appPreferences.setHideFromRecents(enabled)
    }

    /** 胶囊已用时长是否显示到秒 */
    val capsuleUsedShowSeconds: StateFlow<Boolean> = appPreferences.capsuleUsedShowSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** 桌面心锚微粒开关 */
    val desktopAnchorEnabled: StateFlow<Boolean> = appPreferences.desktopAnchorEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** 迷你胶囊尺寸档：standard / compact，默认 standard */
    val capsuleMiniSize: StateFlow<String> = appPreferences.capsuleMiniSize
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            AppPreferences.CAPSULE_MINI_SIZE_STANDARD
        )

    /** 意图门离开倒计时秒数（60 / 120 / 300） */
    val awayCountdownSeconds: StateFlow<Int> = appPreferences.awayCountdownSeconds
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            AppPreferences.DEFAULT_AWAY_COUNTDOWN_SECONDS
        )

    fun refreshPermissions() {
        viewModelScope.launch {
            val prev = _permissionStatus.value
            val next = checkPermissionsUseCase()
            trackPermissionGrants(prev, next)
            _permissionStatus.value = next
        }
    }

    private fun trackPermissionGrants(prev: PermissionStatus, next: PermissionStatus) {
        var changed = false
        if (!prev.hasOverlay && next.hasOverlay) {
            analyticsRepository.trackPermissionGrant(
                HaEvents.Permission.OVERLAY,
                HaEvents.Source.SETTINGS
            )
            changed = true
        }
        if (!prev.hasUsageStats && next.hasUsageStats) {
            analyticsRepository.trackPermissionGrant(
                HaEvents.Permission.USAGE,
                HaEvents.Source.SETTINGS
            )
            changed = true
        }
        if (!prev.hasBatteryOptimizationIgnored && next.hasBatteryOptimizationIgnored) {
            analyticsRepository.trackPermissionGrant(
                HaEvents.Permission.BATTERY,
                HaEvents.Source.SETTINGS
            )
            changed = true
        }
        if (!prev.hasNotification && next.hasNotification) {
            analyticsRepository.trackPermissionGrant(
                HaEvents.Permission.NOTIFICATION,
                HaEvents.Source.SETTINGS
            )
            changed = true
        }
        if (!prev.hasAccessibilityKeepAlive && next.hasAccessibilityKeepAlive) {
            analyticsRepository.trackPermissionGrant(
                HaEvents.Permission.ACCESSIBILITY,
                HaEvents.Source.SETTINGS
            )
            changed = true
        }
        if (changed) {
            monitorProfileReporter.scheduleSnapshot(HaEvents.SnapshotReason.SYNC)
        }
    }

    fun refreshServiceRunning() {
        _isServiceRunning.value = MonitorForegroundService.isRunning
    }

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
        monitorProfileReporter.trackMonitorToggle(
            enabled = running,
            monitoredCount = 0
        )
    }

    fun setMonitorEnabled(enabled: Boolean, monitoredCount: Int = 0) {
        _isServiceRunning.value = enabled
        monitorProfileReporter.trackMonitorToggle(
            enabled = enabled,
            monitoredCount = monitoredCount
        )
    }

    /**
     * 切换加强保活开关（Alarm / 无障碍等通道；不再单独起第二条前台通知）。
     */
    fun setEnhancedKeepAlive(enabled: Boolean) {
        appPreferences.setEnhancedKeepAlive(enabled)
        analyticsRepository.trackKeepAliveToggle(enabled)
        monitorProfileReporter.scheduleSnapshot(HaEvents.SnapshotReason.SYNC)
    }

    fun setCapsuleUsedShowSeconds(enabled: Boolean) {
        appPreferences.setCapsuleUsedShowSeconds(enabled)
    }

    fun setDesktopAnchorEnabled(enabled: Boolean) {
        appPreferences.setDesktopAnchorEnabled(enabled)
        analyticsRepository.trackDesktopAnchorToggle(enabled)
        overlayManager.onDesktopAnchorPreferenceChanged()
    }

    fun setCapsuleMiniSize(size: String) {
        appPreferences.setCapsuleMiniSize(size)
        overlayManager.onCapsuleMiniSizeChanged()
        monitorProfileReporter.scheduleSnapshot(HaEvents.SnapshotReason.SYNC)
    }

    fun setAwayCountdownSeconds(seconds: Int) {
        appPreferences.setAwayCountdownSeconds(seconds)
    }

    val scheduledQuotePushEnabled: StateFlow<Boolean> = appPreferences.scheduledQuotePushEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val scheduledQuotePushStart: StateFlow<Int> = appPreferences.scheduledQuotePushStart
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            AppPreferences.DEFAULT_SCHEDULED_QUOTE_PUSH_START
        )

    val scheduledQuotePushEnd: StateFlow<Int> = appPreferences.scheduledQuotePushEnd
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            AppPreferences.DEFAULT_SCHEDULED_QUOTE_PUSH_END
        )

    val scheduledQuotePushInterval: StateFlow<Int> = appPreferences.scheduledQuotePushInterval
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            AppPreferences.DEFAULT_SCHEDULED_QUOTE_PUSH_INTERVAL
        )

    val quoteLibrary: StateFlow<List<CachedAuthor>> = quoteRepository.authors
        .map { list -> list.filter { it.subscribed } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val authorCatalog: StateFlow<List<CachedAuthor>> = quoteRepository.authors
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setScheduledQuotePushEnabled(enabled: Boolean) {
        appPreferences.setScheduledQuotePushEnabled(enabled)
        QuotePushScheduler.reschedule(appContext)
        monitorProfileReporter.scheduleSnapshot(HaEvents.SnapshotReason.SYNC)
    }

    fun setScheduledQuotePushWindow(startMinute: Int, endMinute: Int) {
        appPreferences.setScheduledQuotePushWindow(startMinute, endMinute)
        QuotePushScheduler.reschedule(appContext)
    }

    fun setScheduledQuotePushInterval(minutes: Int) {
        appPreferences.setScheduledQuotePushInterval(minutes)
        QuotePushScheduler.reschedule(appContext)
    }

    fun previewQuotePushSlots(): List<Int> = appPreferences.expandScheduledQuotePushSlots()

    fun resolveQuoteMoment(
        id: Int,
        content: String,
        author: String,
        authorId: Int
    ): com.life.mindfulnessapp.data.CachedPushedQuote {
        if (content.isNotBlank()) {
            return com.life.mindfulnessapp.data.CachedPushedQuote(
                id = id,
                content = content.trim(),
                author = author,
                authorId = authorId
            )
        }
        return appPreferences.getLastPushedQuote()
            ?: com.life.mindfulnessapp.data.CachedPushedQuote(content = "")
    }

    fun isQuoteFavorited(id: Int, content: String): Boolean =
        appPreferences.isQuoteFavorited(id, content)

    fun toggleFavoriteQuote(
        id: Int,
        content: String,
        author: String,
        authorId: Int
    ): Boolean = appPreferences.toggleFavoriteQuote(id, content, author, authorId)

    val stopQuoteEnabled: StateFlow<Boolean> = appPreferences.stopQuoteEnabled

    fun setStopQuoteEnabled(enabled: Boolean) {
        appPreferences.setStopQuoteEnabled(enabled)
    }

    /** @deprecated 改用 [setScheduledQuotePushEnabled] */
    @Deprecated("Use setScheduledQuotePushEnabled")
    fun setInterceptQuoteEnabled(enabled: Boolean) = setScheduledQuotePushEnabled(enabled)

    fun refreshAuthorCatalog() {
        viewModelScope.launch { quoteRepository.refreshCatalog() }
    }

    suspend fun toggleAuthorSubscription(authorId: Int): Boolean =
        quoteRepository.toggleAuthorSubscription(authorId)

    suspend fun requestAuthor(name: String, note: String): AuthorRequestResult =
        quoteRepository.requestAuthor(name, note)

    fun syncQuotes() {
        viewModelScope.launch {
            quoteRepository.syncAll()
        }
    }

    fun trackKeepAliveOpen() {
        analyticsRepository.trackKeepAliveOpen()
    }

    fun setThemeMode(mode: ThemeMode) {
        appPreferences.setThemeMode(mode)
        analyticsRepository.trackThemeChange(mode.storageValue)
        overlayManager.onCapsuleShellOpacityChanged()
        monitorProfileReporter.scheduleSnapshot(HaEvents.SnapshotReason.SYNC)
    }

    fun setThemePack(pack: ThemePack) {
        appPreferences.setThemePack(pack)
        analyticsRepository.trackThemeChange(pack.storageKey)
        overlayManager.onCapsuleShellOpacityChanged()
        monitorProfileReporter.scheduleSnapshot(HaEvents.SnapshotReason.SYNC)
    }

    fun setThemeFollowSystem(follow: Boolean) {
        appPreferences.setThemeFollowSystem(follow)
        val label = if (follow) "system" else appPreferences.getThemePack().storageKey
        analyticsRepository.trackThemeChange(label)
        overlayManager.onCapsuleShellOpacityChanged()
        monitorProfileReporter.scheduleSnapshot(HaEvents.SnapshotReason.SYNC)
    }

    fun buildDiagnosticFeedbackBody(): String {
        val perm = _permissionStatus.value
        return buildString {
            appendLine("版本: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("渠道: ${BuildConfig.DISTRIBUTION_CHANNEL}")
            appendLine("设备: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("系统: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("监控服务: ${if (MonitorForegroundService.isRunning) "运行中" else "未运行"}")
            appendLine("悬浮窗: ${if (perm.hasOverlay) "已开" else "未开"}")
            appendLine("使用访问: ${if (perm.hasUsageStats) "已开" else "未开"}")
            appendLine("电池优化忽略: ${if (perm.hasBatteryOptimizationIgnored) "是" else "否"}")
            appendLine("通知: ${if (perm.hasNotification) "已开" else "未开"}")
            appendLine("会员码已兑: ${if (appPreferences.isBetaUnlocked()) "是" else "否"}")
            val code = appPreferences.getBetaCode()
            if (code.isNotBlank()) appendLine("会员码: $code")
            appendLine("VIP 等级: ${appPreferences.getVipLevel()}")
        }
    }

    fun submitFeedback(
        content: String,
        contact: String = "",
        category: String = FeedbackCategory.PROBLEM.apiValue,
        imageUris: List<Uri> = emptyList(),
        onDone: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val resolved = FeedbackCategory.fromApi(category) ?: FeedbackCategory.PROBLEM
            val prepared = if (imageUris.isEmpty()) {
                emptyList()
            } else {
                withContext(Dispatchers.IO) {
                    FeedbackImageHelper.prepareForUpload(appContext, imageUris)
                }
            }
            val result = analyticsRepository.submitFeedback(
                content = content,
                contact = contact,
                category = resolved.apiValue,
                diagnostic = buildDiagnosticFeedbackBody(),
                imageBase64List = prepared.map { it.base64Jpeg },
                localImagePaths = prepared.map { it.localPath }
            )
            result.fold(
                onSuccess = { onDone(true, resolved.successMessage) },
                onFailure = { e ->
                    if (prepared.isNotEmpty()) {
                        FeedbackImageHelper.deleteLocal(prepared.map { it.localPath })
                    }
                    onDone(false, e.message?.takeIf { it.isNotBlank() } ?: "提交失败，请稍后重试")
                }
            )
        }
    }

    // ── 反馈收件箱（开发者回复）──────────────────────────────────────────────

    val feedbackThreads: StateFlow<List<FeedbackThread>> = feedbackInboxRepository.threads

    val feedbackUnreadCount: StateFlow<Int> = feedbackInboxRepository.threads
        .map { list -> list.count { it.isUnreadReply } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun syncFeedbackReplies(notify: Boolean = false) {
        viewModelScope.launch {
            syncFeedbackRepliesAwait(notify)
        }
    }

    suspend fun syncFeedbackRepliesAwait(notify: Boolean = false) {
        feedbackInboxRepository.syncReplies(notify = notify, minIntervalMs = 8_000L)
    }

    fun markFeedbackReplyRead(id: Long) {
        feedbackInboxRepository.markReplyRead(id)
    }

    fun feedbackThread(id: Long): FeedbackThread? = feedbackInboxRepository.getThread(id)

    // ── 清除本地数据 ────────────────────────────────────────────────────────

    private val _isClearingData = MutableStateFlow(false)
    val isClearingData: StateFlow<Boolean> = _isClearingData

    /** 清除本地全部使用记录（不影响限额设置）*/
    fun clearLocalUsageData(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _isClearingData.value = true
            usageRecordRepository.deleteAllRecords()
            _isClearingData.value = false
            onDone()
        }
    }

    // ── Debug：注入演示使用记录 ─────────────────────────────────────────────

    private val _isSeedingMockUsage = MutableStateFlow(false)
    val isSeedingMockUsage: StateFlow<Boolean> = _isSeedingMockUsage.asStateFlow()

    val debugForceGrayWash: StateFlow<Boolean> = appPreferences.debugForceGrayWash

    fun canWriteSecureSettingsForGrayscale(): Boolean =
        displayGrayscaleController.canWriteSecureSettings()

    fun setDebugForceGrayWash(enabled: Boolean) {
        if (!BuildConfig.DEBUG) return
        appPreferences.setDebugForceGrayWash(enabled)
        overlayManager.resyncDailyLimitGrayWash()
    }

    /**
     * 仅 Debug：写入微信 / 小红书 / 王者荣耀 近一周演示记录，并确保对应监控配置。
     * 会先清掉这三个包在「上周+本周」窗内的旧演示数据，再注入。
     */
    fun seedDebugMockUsage(onDone: (ok: Boolean, message: String) -> Unit) {
        if (!BuildConfig.DEBUG) {
            onDone(false, "仅 Debug 可用")
            return
        }
        if (_isSeedingMockUsage.value) return
        viewModelScope.launch {
            _isSeedingMockUsage.value = true
            val result = runCatching {
                withContext(Dispatchers.IO) { debugMockUsageSeeder.seedCurrentWeek() }
            }
            _isSeedingMockUsage.value = false
            result.fold(
                onSuccess = { r ->
                    onDone(
                        true,
                        "已注入 ${r.recordCount} 条 · ${r.limitCount} 个演示 App"
                    )
                },
                onFailure = { e ->
                    onDone(false, e.message?.ifBlank { null } ?: "注入失败")
                }
            )
        }
    }
}
