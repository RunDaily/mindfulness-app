package com.life.mindfulnessapp.data.repository

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.life.mindfulnessapp.BuildConfig
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.FeedbackInboxStore
import com.life.mindfulnessapp.data.analytics.AnalyticsBuckets
import com.life.mindfulnessapp.data.analytics.AnalyticsEventStore
import com.life.mindfulnessapp.data.analytics.AppIconEncoder
import com.life.mindfulnessapp.data.analytics.HaEvents
import com.life.mindfulnessapp.data.network.AnalyticsAppIconDto
import com.life.mindfulnessapp.data.network.AnalyticsAppIconsRequest
import com.life.mindfulnessapp.data.network.AnalyticsConfigSnapshotRequest
import com.life.mindfulnessapp.data.network.AnalyticsEventDto
import com.life.mindfulnessapp.data.network.AnalyticsEventsRequest
import com.life.mindfulnessapp.data.network.AnalyticsMonitoredAppDto
import com.life.mindfulnessapp.data.network.AnalyticsPermissionSnapshotDto
import com.life.mindfulnessapp.data.network.AnalyticsPrefsSnapshotDto
import com.life.mindfulnessapp.data.network.ApiService
import com.life.mindfulnessapp.data.network.FeedbackRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 匿名埋点与意见反馈上报（设备级，不绑定账号）。
 * 事件入内存队列并落盘；批量 POST；失败回队重试，避免画像/漏斗静默丢失。
 */
@Singleton
class AnalyticsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: ApiService,
    private val feedbackInboxStore: FeedbackInboxStore,
    private val appPreferences: AppPreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val queue = ArrayDeque<AnalyticsEventDto>()
    private val eventStore = AnalyticsEventStore(context)
    private val iconQueue = LinkedHashMap<String, AnalyticsAppIconDto>()
    private val iconChecked = mutableSetOf<String>()
    private var flushScheduled = false
    private var diskHydrated = false
    private var snapshotInFlight = false
    private var pendingSnapshot: AnalyticsConfigSnapshotRequest? = null

    fun deviceId(): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        return androidId?.takeIf { it.isNotBlank() } ?: "unknown-${Build.MODEL}"
    }

    fun track(name: String, props: Map<String, Any?> = emptyMap()) {
        if (name.isBlank()) return
        val cleanProps = props
            .filterValues { it != null }
            .mapValues { (_, v) -> v.toString().take(240) }
        scope.launch {
            mutex.withLock {
                ensureDiskHydratedLocked()
                queue.addLast(AnalyticsEventDto(name = name.take(80), props = cleanProps))
                while (queue.size > AnalyticsEventStore.MAX_EVENTS) queue.removeFirst()
                persistQueueLocked()
                scheduleFlushLocked()
            }
        }
    }

    fun trackAppOpen() = track(HaEvents.APP_OPEN)

    fun trackBetaUnlock() = track(HaEvents.BETA_UNLOCK)

    fun trackPrivacyAccept() = track(HaEvents.PRIVACY_ACCEPT)

    fun trackOnboardingStep(step: String) =
        track(HaEvents.ONBOARDING_STEP, mapOf(HaEvents.Prop.STEP to step))

    fun trackOnboardingComplete(
        overlay: Boolean,
        usage: Boolean,
        battery: Boolean,
        notification: Boolean = true
    ) = track(
        HaEvents.ONBOARDING_COMPLETE,
        mapOf(
            HaEvents.Prop.OVERLAY to overlay,
            HaEvents.Prop.USAGE to usage,
            HaEvents.Prop.BATTERY to battery,
            HaEvents.Prop.NOTIFICATION to notification,
            HaEvents.Prop.SKIPPED to !(overlay && usage)
        )
    )

    fun trackPermissionGrant(permission: String, source: String = HaEvents.Source.ONBOARDING) =
        track(
            HaEvents.PERMISSION_GRANT,
            mapOf(
                HaEvents.Prop.PERMISSION to permission,
                HaEvents.Prop.SOURCE to source
            )
        )

    fun trackPermissionSkip(source: String = HaEvents.Source.ONBOARDING) =
        track(HaEvents.PERMISSION_SKIP, mapOf(HaEvents.Prop.SOURCE to source))

    fun trackPermissionPrompt(source: String) =
        track(HaEvents.PERMISSION_PROMPT, mapOf(HaEvents.Prop.SOURCE to source))

    fun trackFirstBindSkip() = track(HaEvents.FIRST_BIND_SKIP)

    fun trackCapabilityBind(
        app: String,
        pkg: String,
        intent: Boolean,
        time: Boolean,
        period: Boolean,
        session: Boolean = false,
        keywords: Boolean = false,
        dailyLimitMinutes: Int = 0,
        defaultSessionMin: Int = 0,
        periodWindowsJson: String? = null,
        keywordCount: Int = 0,
        isNew: Boolean,
        bindSlot: Int? = null,
        source: String = HaEvents.Source.BIND
    ) = track(
        HaEvents.CAPABILITY_BIND,
        buildMap {
            putAll(appIdentity(app, pkg))
            putAll(
                AnalyticsBuckets.capabilityDetailProps(
                    intent = intent,
                    time = time,
                    period = period,
                    session = session,
                    keywords = keywords,
                    dailyLimitMinutes = dailyLimitMinutes,
                    defaultSessionMin = defaultSessionMin,
                    periodWindowsJson = periodWindowsJson,
                    keywordCount = keywordCount,
                    source = source
                )
            )
            put(HaEvents.Prop.IS_NEW, isNew)
            if (bindSlot != null) put(HaEvents.Prop.BIND_SLOT, bindSlot)
            putAll(timeContext())
        }
    )

    fun trackCapabilityEdit(
        app: String,
        pkg: String,
        intent: Boolean,
        time: Boolean,
        period: Boolean,
        session: Boolean = false,
        keywords: Boolean = false,
        dailyLimitMinutes: Int = 0,
        defaultSessionMin: Int = 0,
        periodWindowsJson: String? = null,
        keywordCount: Int = 0,
        source: String = HaEvents.Source.EDIT
    ) = track(
        HaEvents.CAPABILITY_EDIT,
        buildMap {
            putAll(appIdentity(app, pkg))
            putAll(
                AnalyticsBuckets.capabilityDetailProps(
                    intent = intent,
                    time = time,
                    period = period,
                    session = session,
                    keywords = keywords,
                    dailyLimitMinutes = dailyLimitMinutes,
                    defaultSessionMin = defaultSessionMin,
                    periodWindowsJson = periodWindowsJson,
                    keywordCount = keywordCount,
                    source = source
                )
            )
        }
    )

    fun trackCapabilityUnbind(
        app: String = "",
        pkg: String = "",
        intent: Boolean? = null,
        time: Boolean? = null,
        period: Boolean? = null,
        session: Boolean? = null,
        monitoredLeft: Int? = null
    ) = track(
        HaEvents.CAPABILITY_UNBIND,
        buildMap {
            putAll(appIdentity(app, pkg))
            putAll(AnalyticsBuckets.appCaps(intent, time, period, session))
            if (monitoredLeft != null) put(HaEvents.Prop.MONITORED_LEFT, monitoredLeft)
        }
    )

    fun trackMonitorToggle(enabled: Boolean, monitoredCount: Int) =
        track(
            HaEvents.MONITOR_TOGGLE,
            mapOf(
                HaEvents.Prop.ENABLED to enabled,
                HaEvents.Prop.MONITORED_COUNT to monitoredCount
            )
        )

    fun trackMonitorReorder(monitoredCount: Int) =
        track(
            HaEvents.MONITOR_REORDER,
            mapOf(HaEvents.Prop.MONITORED_COUNT to monitoredCount)
        )

    fun trackInterceptShow(
        type: String,
        interceptId: String,
        app: String,
        pkg: String,
        capIntent: Boolean? = null,
        capTime: Boolean? = null,
        capPeriod: Boolean? = null,
        capSession: Boolean? = null
    ) = track(
        HaEvents.INTERCEPT_SHOW,
        buildMap {
            put(HaEvents.Prop.TYPE, type)
            put(HaEvents.Prop.INTERCEPT_ID, interceptId)
            putAll(appIdentity(app, pkg))
            putAll(AnalyticsBuckets.appCaps(capIntent, capTime, capPeriod, capSession))
            putAll(timeContext())
        }
    )

    fun trackGateEnter(
        interceptId: String,
        sessionId: Long,
        app: String,
        pkg: String,
        purpose: String?,
        intentKind: String? = null,
        sessionLimitMinutes: Int = 0,
        hasSessionLimit: Boolean = sessionLimitMinutes > 0
    ) = track(
        HaEvents.GATE_ENTER,
        buildMap {
            put(HaEvents.Prop.INTERCEPT_ID, interceptId)
            put(HaEvents.Prop.SESSION_ID, sessionId)
            putAll(appIdentity(app, pkg))
            putAll(purposeProps(purpose))
            if (!intentKind.isNullOrBlank()) put(HaEvents.Prop.INTENT_KIND, intentKind)
            put(HaEvents.Prop.SESSION_MIN, sessionLimitMinutes.coerceAtLeast(0))
            put(HaEvents.Prop.HAS_SESSION_LIMIT, hasSessionLimit)
        }
    )

    fun trackGateHold(
        interceptId: String,
        app: String,
        pkg: String,
        hadDraftPurpose: Boolean = false
    ) = track(
        HaEvents.GATE_HOLD,
        buildMap {
            put(HaEvents.Prop.INTERCEPT_ID, interceptId)
            putAll(appIdentity(app, pkg))
            put(HaEvents.Prop.HAD_DRAFT_PURPOSE, hadDraftPurpose)
        }
    )

    fun trackGateResume(
        sessionId: Long,
        app: String,
        pkg: String,
        priorEndReason: String?
    ) = track(
        HaEvents.GATE_RESUME,
        buildMap {
            put(HaEvents.Prop.SESSION_ID, sessionId)
            putAll(appIdentity(app, pkg))
            if (!priorEndReason.isNullOrBlank()) {
                put(HaEvents.Prop.PRIOR_END_REASON, priorEndReason)
            }
        }
    )

    fun trackGateBlockedKeyword(interceptId: String, app: String, pkg: String) =
        track(
            HaEvents.GATE_BLOCKED_KEYWORD,
            buildMap {
                put(HaEvents.Prop.INTERCEPT_ID, interceptId)
                putAll(appIdentity(app, pkg))
            }
        )

    fun trackLimitBlock(
        type: String,
        interceptId: String,
        app: String,
        pkg: String
    ) = track(
        HaEvents.LIMIT_BLOCK,
        buildMap {
            put(HaEvents.Prop.TYPE, type)
            put(HaEvents.Prop.INTERCEPT_ID, interceptId)
            putAll(appIdentity(app, pkg))
        }
    )

    fun trackLimitDecision(
        type: String,
        action: String,
        app: String,
        pkg: String,
        interceptId: String? = null,
        sessionId: Long? = null
    ) = track(
        HaEvents.LIMIT_DECISION,
        buildMap {
            put(HaEvents.Prop.TYPE, type)
            put(HaEvents.Prop.ACTION, action)
            putAll(appIdentity(app, pkg))
            if (!interceptId.isNullOrBlank()) put(HaEvents.Prop.INTERCEPT_ID, interceptId)
            if (sessionId != null && sessionId > 0) put(HaEvents.Prop.SESSION_ID, sessionId)
        }
    )

    fun trackSessionStart(
        sessionId: Long,
        app: String,
        pkg: String,
        entry: String,
        purpose: String? = null,
        sessionLimitMinutes: Int = 0
    ) = track(
        HaEvents.SESSION_START,
        buildMap {
            put(HaEvents.Prop.SESSION_ID, sessionId)
            putAll(appIdentity(app, pkg))
            put(HaEvents.Prop.ENTRY, entry)
            putAll(purposeProps(purpose))
            put(HaEvents.Prop.SESSION_MIN, sessionLimitMinutes.coerceAtLeast(0))
            putAll(timeContext())
        }
    )

    fun trackSessionExtend(
        sessionId: Long,
        app: String,
        pkg: String,
        extendMin: Int,
        remainSec: Long = 0L
    ) = track(
        HaEvents.SESSION_EXTEND,
        buildMap {
            put(HaEvents.Prop.SESSION_ID, sessionId)
            putAll(appIdentity(app, pkg))
            put(HaEvents.Prop.EXTEND_MIN, extendMin)
            put(HaEvents.Prop.REMAIN_SEC, remainSec)
        }
    )

    fun trackSessionEnd(
        sessionId: Long,
        app: String,
        pkg: String,
        endReason: String,
        durationSeconds: Long,
        extended: Boolean = false,
        hadPurpose: Boolean = false,
        level: Int? = null,
        purpose: String? = null
    ) = track(
        HaEvents.SESSION_END,
        buildMap {
            put(HaEvents.Prop.SESSION_ID, sessionId)
            putAll(appIdentity(app, pkg))
            put(HaEvents.Prop.END_REASON, endReason)
            put(HaEvents.Prop.DURATION_SEC, durationSeconds.coerceAtLeast(0))
            put(HaEvents.Prop.DUR_BUCKET, AnalyticsBuckets.durationBucket(durationSeconds))
            put(HaEvents.Prop.EXTENDED, extended)
            put(HaEvents.Prop.HAD_PURPOSE, hadPurpose)
            if (level != null) put(HaEvents.Prop.LEVEL, level)
            putAll(purposeProps(purpose))
            putAll(timeContext())
        }
    )

    fun trackCompareSave(
        sessionId: Long,
        source: String,
        hasNote: Boolean,
        hasLevel: Boolean,
        level: Int? = null,
        app: String = "",
        pkg: String = ""
    ) = track(
        HaEvents.COMPARE_SAVE,
        buildMap {
            put(HaEvents.Prop.SESSION_ID, sessionId)
            put(HaEvents.Prop.SOURCE, source)
            put(HaEvents.Prop.HAS_NOTE, hasNote)
            put(HaEvents.Prop.HAS_LEVEL, hasLevel)
            if (level != null) put(HaEvents.Prop.LEVEL, level)
            putAll(appIdentity(app, pkg))
        }
    )

    fun trackAwayBarAction(
        sessionId: Long,
        action: String,
        app: String,
        pkg: String
    ) = track(
        HaEvents.AWAY_BAR_ACTION,
        buildMap {
            put(HaEvents.Prop.SESSION_ID, sessionId)
            put(HaEvents.Prop.ACTION, action)
            putAll(appIdentity(app, pkg))
        }
    )

    fun trackMidCheckShow(
        sessionId: Long,
        checkIndex: Int,
        awarenessMode: String,
        app: String,
        pkg: String
    ) = track(
        HaEvents.MID_CHECK_SHOW,
        buildMap {
            put(HaEvents.Prop.SESSION_ID, sessionId)
            put(HaEvents.Prop.CHECK_INDEX, checkIndex)
            put(HaEvents.Prop.AWARENESS_MODE, awarenessMode)
            putAll(appIdentity(app, pkg))
        }
    )

    fun trackMidCheckAction(
        sessionId: Long,
        action: String,
        checkIndex: Int,
        awarenessMode: String,
        app: String,
        pkg: String
    ) = track(
        HaEvents.MID_CHECK_ACTION,
        buildMap {
            put(HaEvents.Prop.SESSION_ID, sessionId)
            put(HaEvents.Prop.ACTION, action)
            put(HaEvents.Prop.CHECK_INDEX, checkIndex)
            put(HaEvents.Prop.AWARENESS_MODE, awarenessMode)
            putAll(appIdentity(app, pkg))
        }
    )

    fun trackSoftExitAction(
        sessionId: Long,
        action: String,
        awarenessMode: String,
        app: String,
        pkg: String
    ) = track(
        HaEvents.SOFT_EXIT_ACTION,
        buildMap {
            put(HaEvents.Prop.SESSION_ID, sessionId)
            put(HaEvents.Prop.ACTION, action)
            put(HaEvents.Prop.AWARENESS_MODE, awarenessMode)
            putAll(appIdentity(app, pkg))
        }
    )

    fun trackFeedbackSubmit(category: String) =
        track(HaEvents.FEEDBACK_SUBMIT, mapOf(HaEvents.Prop.CATEGORY to category))

    fun trackUpdateCheck(hasUpdate: Boolean) =
        track(HaEvents.UPDATE_CHECK, mapOf(HaEvents.Prop.HAS_UPDATE to hasUpdate))

    fun trackExportRecords(
        range: String,
        action: String,
        format: String = HaEvents.ExportFormat.CSV
    ) =
        track(
            HaEvents.EXPORT_RECORDS,
            mapOf(
                HaEvents.Prop.RANGE to range,
                HaEvents.Prop.ACTION to action,
                HaEvents.Prop.FORMAT to format
            )
        )

    fun trackKeepAliveOpen() = track(HaEvents.KEEP_ALIVE_OPEN)

    fun trackKeepAliveToggle(enabled: Boolean) =
        track(HaEvents.KEEP_ALIVE_TOGGLE, mapOf(HaEvents.Prop.ENABLED to enabled))

    fun trackDesktopAnchorToggle(enabled: Boolean) =
        track(HaEvents.DESKTOP_ANCHOR_TOGGLE, mapOf(HaEvents.Prop.ENABLED to enabled))

    fun trackLookbackView(weekOffset: Int = 0) =
        track(HaEvents.LOOKBACK_VIEW, mapOf(HaEvents.Prop.WEEK_OFFSET to weekOffset))

    fun trackDayReportView() = track(HaEvents.DAY_REPORT_VIEW)

    fun trackDayTimelineView() = track(HaEvents.DAY_TIMELINE_VIEW)

    fun trackAppWeekRhythmView(pkg: String) =
        track(HaEvents.APP_WEEK_RHYTHM_VIEW, appIdentity(app = "", pkg = pkg))

    fun trackThemeChange(theme: String) =
        track(HaEvents.THEME_CHANGE, mapOf(HaEvents.Prop.THEME to theme))

    fun trackBreathGatePass(reason: String, app: String = "", pkg: String = "") =
        track(
            HaEvents.BREATH_GATE_PASS,
            buildMap {
                put(HaEvents.Prop.REASON, reason)
                putAll(appIdentity(app, pkg))
            }
        )

    fun trackVipPageView(isVip: Boolean) =
        track(HaEvents.VIP_PAGE_VIEW, mapOf(HaEvents.Prop.ALREADY to isVip))

    fun trackVipPlanSelect(planId: String) =
        track(HaEvents.VIP_PLAN_SELECT, mapOf(HaEvents.Prop.PLAN to planId))

    fun trackVipPayClick(planId: String) =
        track(HaEvents.VIP_PAY_CLICK, mapOf(HaEvents.Prop.PLAN to planId))

    fun trackVipIntentShow(planId: String, ref: String) =
        track(
            HaEvents.VIP_INTENT_SHOW,
            mapOf(HaEvents.Prop.PLAN to planId, HaEvents.Prop.REF to ref)
        )

    fun trackVipIntentSubmit(planId: String, channel: String, ref: String) =
        track(
            HaEvents.VIP_INTENT_SUBMIT,
            mapOf(
                HaEvents.Prop.PLAN to planId,
                HaEvents.Prop.CHANNEL to channel,
                HaEvents.Prop.REF to ref
            )
        )

    fun trackVipPayAbandon(planId: String, ref: String = "") =
        track(
            HaEvents.VIP_PAY_ABANDON,
            buildMap {
                put(HaEvents.Prop.PLAN, planId)
                if (ref.isNotBlank()) put(HaEvents.Prop.REF, ref)
            }
        )

    fun trackVipCodeRedeem(already: Boolean) =
        track(HaEvents.VIP_CODE_REDEEM, mapOf(HaEvents.Prop.ALREADY to already))

    fun trackVipCodeClaim(channel: String, alreadyIssued: Boolean) =
        track(
            HaEvents.VIP_CODE_CLAIM,
            mapOf(
                HaEvents.Prop.CHANNEL to channel,
                HaEvents.Prop.ALREADY to alreadyIssued
            )
        )

    fun trackVipPaidSuccess(planId: String, channel: String, ref: String = "") =
        track(
            HaEvents.VIP_PAID_SUCCESS,
            buildMap {
                put(HaEvents.Prop.PLAN, planId)
                put(HaEvents.Prop.CHANNEL, channel)
                if (ref.isNotBlank()) put(HaEvents.Prop.REF, ref)
            }
        )

    /**
     * 上报监控配置快照。失败时保留最近一次待重试。
     */
    fun pushConfigSnapshot(
        reason: String,
        monitorServiceOn: Boolean,
        vipActive: Boolean,
        monitoredCount: Int,
        permission: AnalyticsPermissionSnapshotDto,
        prefs: AnalyticsPrefsSnapshotDto,
        apps: List<AnalyticsMonitoredAppDto>
    ) {
        val request = AnalyticsConfigSnapshotRequest(
            device_id = deviceId(),
            app_version = BuildConfig.VERSION_NAME,
            os_version = Build.VERSION.RELEASE,
            device_model = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            channel = BuildConfig.DISTRIBUTION_CHANNEL,
            captured_at = System.currentTimeMillis(),
            reason = reason.take(40),
            monitor_service_on = monitorServiceOn,
            vip_active = vipActive,
            monitored_count = monitoredCount,
            permission = permission,
            prefs = prefs,
            apps = apps
        )
        scope.launch {
            mutex.withLock {
                pendingSnapshot = request
                if (snapshotInFlight) return@withLock
                snapshotInFlight = true
            }
            flushPendingSnapshot()
        }
    }

    /**
     * @param imageBase64List JPEG base64（无前缀），最多 3 张
     * @param localImagePaths 本机压缩文件路径，供记录页预览
     */
    suspend fun submitFeedback(
        content: String,
        contact: String = "",
        category: String = "general",
        diagnostic: String = "",
        imageBase64List: List<String> = emptyList(),
        localImagePaths: List<String> = emptyList()
    ): Result<Long?> {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("请填写反馈内容"))
        val normalizedCategory = when (category.trim().lowercase()) {
            "problem", "bug" -> "problem"
            "idea", "suggestion" -> "idea"
            else -> "general"
        }
        val images = imageBase64List.filter { it.isNotBlank() }.take(3)
        return try {
            val resp = apiService.submitFeedback(
                FeedbackRequest(
                    device_id = deviceId(),
                    contact = contact.trim(),
                    content = trimmed,
                    category = normalizedCategory,
                    app_version = BuildConfig.VERSION_NAME,
                    device_model = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                    os_version = Build.VERSION.RELEASE,
                    diagnostic = diagnostic,
                    images = images
                )
            )
            if (resp.success) {
                trackFeedbackSubmit(normalizedCategory)
                val id = resp.id
                if (id != null && id > 0L) {
                    feedbackInboxStore.upsertSubmitted(
                        id = id,
                        category = normalizedCategory,
                        content = trimmed,
                        contact = contact.trim(),
                        localImagePaths = localImagePaths.filter { it.isNotBlank() }
                    )
                }
                Result.success(id)
            } else {
                Result.failure(IllegalStateException(resp.error ?: "提交失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun appIdentity(app: String, pkg: String): Map<String, Any?> = buildMap {
        val a = AnalyticsBuckets.truncateApp(app)
        val p = AnalyticsBuckets.truncatePkg(pkg)
        if (a.isNotEmpty()) put(HaEvents.Prop.APP, a)
        if (p.isNotEmpty()) put(HaEvents.Prop.PKG, p)
        if (p.isNotEmpty()) rememberAppIcon(a, p)
    }

    private fun purposeProps(purpose: String?): Map<String, Any?> = buildMap {
        val raw = purpose?.trim().orEmpty()
        put(HaEvents.Prop.PURPOSE_LEN, raw.length)
        put(HaEvents.Prop.PURPOSE_CLARITY, AnalyticsBuckets.purposeClarity(raw))
        if (raw.isNotEmpty() && appPreferences.analyticsReportPurposeFullText) {
            put(HaEvents.Prop.PURPOSE, AnalyticsBuckets.truncatePurpose(raw))
        }
    }

    private fun timeContext(): Map<String, Any?> = mapOf(
        HaEvents.Prop.HOUR_BUCKET to AnalyticsBuckets.hourBucket(),
        HaEvents.Prop.WEEKDAY to AnalyticsBuckets.weekday()
    )

    private fun ensureDiskHydratedLocked() {
        if (diskHydrated) return
        diskHydrated = true
        eventStore.load().forEach { queue.addLast(it) }
        while (queue.size > AnalyticsEventStore.MAX_EVENTS) queue.removeFirst()
    }

    private fun persistQueueLocked() {
        eventStore.save(queue.toList())
    }

    private fun scheduleFlushLocked() {
        if (flushScheduled) return
        flushScheduled = true
        scope.launch {
            delay(1200)
            flushNow()
        }
    }

    private suspend fun flushNow() {
        val batch = mutex.withLock {
            ensureDiskHydratedLocked()
            flushScheduled = false
            if (queue.isEmpty()) {
                emptyList()
            } else {
                buildList {
                    repeat(minOf(40, queue.size)) { add(queue.removeFirst()) }
                }.also { persistQueueLocked() }
            }
        }
        if (batch.isNotEmpty()) {
            try {
                val resp = apiService.postAnalyticsEvents(
                    AnalyticsEventsRequest(
                        device_id = deviceId(),
                        app_version = BuildConfig.VERSION_NAME,
                        os_version = Build.VERSION.RELEASE,
                        device_model = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                        channel = BuildConfig.DISTRIBUTION_CHANNEL,
                        events = batch
                    )
                )
                if (!resp.success) {
                    requeueBatch(batch)
                }
            } catch (_: Exception) {
                requeueBatch(batch)
            }
        }
        flushAppIcons()
        flushPendingSnapshot()
        mutex.withLock {
            if ((queue.isNotEmpty() || iconQueue.isNotEmpty() || pendingSnapshot != null) &&
                !flushScheduled
            ) {
                scheduleFlushLocked()
            }
        }
    }

    private suspend fun requeueBatch(batch: List<AnalyticsEventDto>) {
        mutex.withLock {
            batch.asReversed().forEach { queue.addFirst(it) }
            while (queue.size > AnalyticsEventStore.MAX_EVENTS) queue.removeLast()
            persistQueueLocked()
        }
    }

    private suspend fun flushPendingSnapshot() {
        val request = mutex.withLock {
            val pending = pendingSnapshot ?: run {
                snapshotInFlight = false
                return
            }
            pending
        }
        try {
            val resp = apiService.postConfigSnapshot(request)
            val shouldRetryNewer = mutex.withLock {
                if (resp.success) {
                    val newer = pendingSnapshot
                    if (newer == null || newer.captured_at <= request.captured_at) {
                        pendingSnapshot = null
                        snapshotInFlight = false
                        false
                    } else {
                        // 期间已有更新快照，继续上报
                        snapshotInFlight = true
                        true
                    }
                } else {
                    snapshotInFlight = false
                    false
                }
            }
            if (shouldRetryNewer) flushPendingSnapshot()
        } catch (_: Exception) {
            mutex.withLock { snapshotInFlight = false }
        }
    }

    private fun rememberAppIcon(app: String, pkg: String) {
        if (pkg.isBlank() || pkg in iconChecked) return
        iconChecked.add(pkg)
        scope.launch {
            val encoded = AppIconEncoder.encode(context, pkg) ?: return@launch
            if (appPreferences.getAnalyticsAppIconHash(pkg) == encoded.hash) return@launch
            mutex.withLock {
                iconQueue[pkg] = AnalyticsAppIconDto(
                    pkg = pkg,
                    app = app.ifBlank { encoded.appName },
                    icon_base64 = encoded.base64,
                    hash = encoded.hash
                )
                scheduleFlushLocked()
            }
        }
    }

    private suspend fun flushAppIcons() {
        val batch = mutex.withLock {
            if (iconQueue.isEmpty()) return
            iconQueue.values.take(8).also { taken ->
                taken.forEach { iconQueue.remove(it.pkg) }
            }
        }
        try {
            val resp = apiService.postAppIcons(
                AnalyticsAppIconsRequest(
                    device_id = deviceId(),
                    icons = batch
                )
            )
            if (resp.success) {
                batch.forEach { dto ->
                    if (dto.hash.isNotBlank()) {
                        appPreferences.setAnalyticsAppIconHash(dto.pkg, dto.hash)
                    }
                }
            } else {
                mutex.withLock {
                    batch.forEach { iconQueue.putIfAbsent(it.pkg, it) }
                }
            }
        } catch (_: Exception) {
            mutex.withLock {
                batch.forEach { iconQueue.putIfAbsent(it.pkg, it) }
            }
        }
    }
}
