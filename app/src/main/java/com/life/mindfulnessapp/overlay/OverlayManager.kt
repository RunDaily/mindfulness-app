package com.life.mindfulnessapp.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.data.repository.AppLimitRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository
import com.life.mindfulnessapp.domain.model.PendingInterrupt
import com.life.mindfulnessapp.domain.model.UsageRecordCounts
import com.life.mindfulnessapp.domain.model.UsageSession
import com.life.mindfulnessapp.domain.usecase.GetAppHistoryUsageUseCase
import com.life.mindfulnessapp.service.SessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OverlayManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val usageRecordRepository: UsageRecordRepository,
    private val appLimitRepository: AppLimitRepository,
    private val getAppHistoryUsageUseCase: GetAppHistoryUsageUseCase,
    private val sessionManager: SessionManager,
    private val appPreferences: com.life.mindfulnessapp.data.AppPreferences,
    private val pendingInterruptStore: com.life.mindfulnessapp.data.PendingInterruptStore,
    private val impulseStore: com.life.mindfulnessapp.data.ImpulseStore
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * 由外部（Service）注册：用户确认手动结束会话后回调。
     * @param recordId 刚结束的记录 ID
     * @param mindfulnessLevel 正念档位（未回顾时为 null）
     * @param destination 收口去向（回桌面对齐 / 回桌面跑偏可回看 / 进心锚）
     */
    var onManualEndSession: ((
        recordId: Long,
        mindfulnessLevel: Int?,
        destination: ManualEndDestination
    ) -> Unit)? = null

    /** 主动离开后轻条：打开正向 App */
    var onLaunchPositiveApp: ((packageName: String) -> Unit)? = null

    /** 主动离开后轻条：打开「想去的地方」配置页 */
    var onOpenPositiveDestinationSettings: (() -> Unit)? = null

    /** 由外部（Service）注册：胶囊临近结束时用户确认续时 */
    var onExtendSession: ((extraMinutes: Int) -> Unit)? = null

    private var interceptView: View? = null
    private var adView: View? = null          // 广告页浮窗（非 VIP 超限时插入）
    /** 广告被外部强制关闭（如用户按 Home 键）时置为 true，防止 onAdFinished 误触发超限页 */
    private var adCancelled: Boolean = false
    private var capsuleView: View? = null
    private var capsuleParams: WindowManager.LayoutParams? = null
    /**
     * 胶囊内结束确认 / 续时弹窗进行中。
     * 此时会故意把会话标成后台以冻结计时；监控循环若据此「回前台恢复」会拆掉弹窗。
     */
    val isCapsuleDialogBlocking = AtomicBoolean(false)
    private var capsuleSnapAnimator: ValueAnimator? = null
    /** 收起后回到偏好停靠点（左 / 中 / 右） */
    private val snapAfterCollapseRunnable = Runnable {
        val view = capsuleView ?: return@Runnable
        val params = capsuleParams ?: return@Runnable
        if (!capsuleExpanded.value) {
            snapCapsuleToDock(view, params, forceDock = appPreferences.getCapsuleDockPosition())
        }
    }
    private var ceremonyView: View? = null   // 仪式感动画专属浮窗（居中、全屏透明）
    private var dismissCeremonyView: View? = null  // 退出仪式浮窗（"离开仪式"）

    /**
     * 离开肯定冷却：记录每个 App 上次展示肯定（轻提示或全屏勋章）的时间戳（ms）。
     *
     * 设计原则：
     * - 冷却窗口内再次退出 → 静默离开，避免刷提示；
     * - 日常用轻提示；仅里程碑播全屏勋章。
     */
    private val dismissCeremonyCooldownMs = 2 * 60 * 1000L   // 2 分钟
    private val lastDismissCeremonyTime = mutableMapOf<String, Long>()  // packageName → timestamp

    private var capsuleUpdateJob: Job? = null
    private var capsuleSession: UsageSession? = null

    /** Compose 层注册的唤醒回调：触摸时调用，让胶囊从休眠态弹回活跃态 */
    private var capsuleWakeUp: (() -> Unit)? = null

    /** Compose 层注册的「显示结束确认弹窗」回调：后台超时时由 Service 调用，弹出确认弹窗 */
    private var capsuleShowConfirm: (() -> Unit)? = null

    /** 点「结束」：弹出结束确认（触摸由 Window 层命中，Compose clickable 收不到） */
    private var capsuleRequestEnd: (() -> Unit)? = null

    /** 「结束」在胶囊 View 内的命中区（含少量外扩），单位 px */
    private var capsuleStopHitRect: android.graphics.RectF? = null

    /** 结束确认打开时，把触摸交给 Compose，避免点按被拖拽监听吞掉 */
    private var capsuleEndDialogOpen: Boolean = false

    /** Compose 层注册的「5分钟预警」回调：剩余恰好低于5分钟时触发一次 */
    private var capsuleWarnFiveMin: (() -> Unit)? = null

    /** Compose 层注册的「1分钟临界」回调：剩余首次低于1分钟时触发，同形态加压（不换皮） */
    private var capsuleStartCountdown: (() -> Unit)? = null

    /** 意图门入场展开停留期间：点按/外侧点按提前收起 */
    private var capsuleSkipEntrance: (() -> Unit)? = null

    /**
     * 原子标志：当前是否正在展示拦截弹窗（或正在准备展示/创建会话）。
     * 防止监控循环在此期间重复触发。
     */
    val isInterceptVisible = AtomicBoolean(false)

    /**
     * 原子标志：广告页当前是否正在播放。
     * 广告播放期间用户按 Home 键不应关闭广告，监控服务检测到 Home 键时需检查此标志。
     */
    val isAdPlaying = AtomicBoolean(false)

    /**
     * 当前拦截/广告/超限页对应的被监控 App 包名。
     * 监控服务以此为标准判断是否应该关闭覆盖层：
     *   当前台从此包名切走时 → dismiss（用户按了 Home 键）
     *   广告结束后展示拦截页期间，用户仍在桌面，当前台未再次切驼 → 不 dismiss
     */
    @Volatile var interceptTargetPackage: String? = null
        private set

    // Capsule 状态（暴露给 Compose UI）
    val capsuleSessionSeconds = mutableStateOf(0L)
    val capsuleDailyRemainingSeconds = mutableStateOf(0L)
    val capsuleDailyLimitSeconds = mutableStateOf(0L)
    val capsuleAppName = mutableStateOf("")
    val capsuleAppPackageName = mutableStateOf("")  // app包名，用于加载图标
    val capsulePurpose = mutableStateOf<String?>(null)  // 用户在拦截页填写的使用目的
    val capsuleExpanded = mutableStateOf(false)

    /**
     * 胶囊暂停状态：app进入后台（且无活跃服务）时为true
     * 暂停时胶囊继续显示；点按胶囊主体返回 App，点「结束」弹确认
     */
    val capsuleIsPaused = mutableStateOf(false)

    /** 是否为超限续记会话（视觉与普通限额会话区分） */
    val capsuleIsOverLimit = mutableStateOf(false)

    /** 当前会话是否开启意图门 */
    val capsuleHasIntentGate = mutableStateOf(true)

    /** 当前会话是否开启时长锁（含超限续记） */
    val capsuleHasTimeLock = mutableStateOf(true)
    /** 是否存在单次会话上限（驱动胶囊按会话预算预警） */
    val capsuleHasSessionLimit = mutableStateOf(false)
    /** 单次时长临近结束时是否还可续一次 */
    val capsuleCanExtend = mutableStateOf(false)
    /** 本会话是否开启意图回顾 */
    /** 纯时长锁迷你态：已用侧是否显示到秒 */
    val capsuleShowUsedSeconds = mutableStateOf(false)
    /** true = 紧凑迷你；false = 标准迷你（默认） */
    val capsuleMiniCompact = mutableStateOf(false)

    /**
     * 含意图门切走后的「自动结束」剩余秒数。
     * -1 = 未处于离开倒计时；>=0 时暂停态展示倒计时。
     */
    val capsuleAwayCountdownSeconds = mutableStateOf(-1L)

    /**
     * 显示全屏拦截浮窗（始终要求用户写下使用目的）。
     *
     * 若该 App 存在「非标准闭环」待确认中断，则以「最近操作」条呈现，
     * 展示相对时刻并可一键继续，或重写意图开始新的一次。
     *
     * @param onContinue 用户确认继续时的回调，携带输入的使用目的
     * @param onSessionResumed 用户选择「继续上次」并成功恢复会话后的回调
     */
    fun showIntercept(
        packageName: String,
        appName: String,
        dailyLimitMinutes: Int,
        weeklyLimitMinutes: Int,
        onContinue: (com.life.mindfulnessapp.domain.model.InterceptEnterDecision) -> Unit,
        onDismiss: () -> Unit,
        onOpenOwnApp: (() -> Unit)? = null,
        onReset: (() -> Unit)? = null,
        onSessionResumed: ((UsageSession) -> Unit)? = null,
        /** false：同一进入尝试的静默重展（如拦截页期间息屏后解锁），不计新冲动 */
        countImpulse: Boolean = true
    ) {
        isInterceptVisible.set(true)
        interceptTargetPackage = packageName

        scope.launch {
            val now = System.currentTimeMillis()
            val dbTodayUsedSeconds = usageRecordRepository.getDailyUsageSeconds(packageName, now)
            val dbWeekUsedSeconds = usageRecordRepository.getWeeklyUsageSeconds(packageName, now)
            val todayRecords = usageRecordRepository.getDayRecordsForApp(packageName, now)
            val remainingModifyCount = appLimitRepository.getRemainingModifyCount(packageName)
            val currentLimit = appLimitRepository.getAppLimit(packageName)
            val pendingInterrupt = pendingInterruptStore.get(packageName)
            val intentGateOnEarly = currentLimit?.requireIntentOnOpen ?: true
            val recentPurposes = if (intentGateOnEarly) {
                usageRecordRepository.getRecentPurposes(packageName, limit = 5)
            } else {
                emptyList()
            }

            // 若当前有进行中的会话（同一个 App），需加上本次会话已累计的时长。
            // 数据库只存已完成的记录，正在进行的会话时长不在 DB 里。
            val activeSession = sessionManager.currentSession.value
            val activeExtraSeconds = if (activeSession != null && activeSession.packageName == packageName) {
                activeSession.currentSessionSeconds
            } else 0L
            val todayUsedSeconds = dbTodayUsedSeconds + activeExtraSeconds
            val weekUsedSeconds = dbWeekUsedSeconds + activeExtraSeconds

            mainHandler.post {
                // 如果在异步加载数据期间已被取消（如用户按 Home 键离开），放弃展示
                if (!isInterceptVisible.get()) return@post
                removeInterceptViewInternal()

                // 判断是否已超限：超限用户只能看超限页（离开 or 重设），不允许再「继续」
                val dailyLimitSeconds = currentLimit?.effectiveDailyLimitMinutes()?.times(60L) ?: 0L
                val weeklyLimitSeconds = currentLimit?.effectiveWeeklyLimitMinutes()?.times(60L) ?: 0L
                val isAlreadyOverLimit = (dailyLimitSeconds > 0 && todayUsedSeconds >= dailyLimitSeconds)
                        || (weeklyLimitSeconds > 0 && weekUsedSeconds >= weeklyLimitSeconds)

                if (isAlreadyOverLimit) {
                    // 超限优先：清掉待确认，避免与超限页叠加
                    pendingInterruptStore.clear(packageName)
                    if (!appPreferences.isVipActive()) {
                        showAdOverlay(packageName = packageName, onAdFinished = {
                            if (isInterceptVisible.get()) {
                                showLimitReachedInternal(packageName, onDismiss, onReset, onOpenOwnApp = onOpenOwnApp)
                            }
                        })
                    } else {
                        showLimitReachedInternal(packageName, onDismiss, onReset, onOpenOwnApp = onOpenOwnApp)
                    }
                    return@post
                }

                val enterCount = UsageRecordCounts.enterCount(todayRecords)
                val dismissCount = UsageRecordCounts.dismissCount(todayRecords)

                // 未闭环快照：仅意图门 + 有恢复回调时展示「最近操作」条；否则丢弃
                val intentGateOn = currentLimit?.requireIntentOnOpen ?: true
                val resumeCandidate = if (
                    pendingInterrupt != null &&
                    onSessionResumed != null &&
                    intentGateOn
                ) {
                    pendingInterrupt
                } else {
                    if (pendingInterrupt != null) {
                        pendingInterruptStore.clear(packageName)
                    }
                    null
                }

                val impulseCount = if (countImpulse) {
                    impulseStore.incrementImpulse(packageName)
                } else {
                    impulseStore.getImpulseCount(packageName).coerceAtLeast(1)
                }

                showInterceptInternal(
                    packageName, appName, dailyLimitMinutes, weeklyLimitMinutes,
                    todayUsedSeconds, weekUsedSeconds, todayRecords,
                    remainingModifyCount, onContinue, onDismiss,
                    onOpenOwnApp = onOpenOwnApp,
                    pendingInterrupt = resumeCandidate,
                    onSessionResumed = onSessionResumed,
                    sessionLimitEnabled = currentLimit?.sessionLimitEnabled ?: true,
                    // 进门时当场确认分钟数；配置页不再暴露默认值，统一预填 15
                    defaultSessionLimitMinutes = 15,
                    intentQualityCheckEnabled = currentLimit?.intentQualityCheckEnabled ?: false,
                    intentBlockKeywords = com.life.mindfulnessapp.domain.model.IntentBlockKeywords
                        .decode(currentLimit?.intentBlockKeywordsJson),
                    impulseCount = impulseCount,
                    enterCount = enterCount,
                    dismissCount = dismissCount,
                    recentPurposes = recentPurposes
                )
            }
        }
    }

    /** 真正创建并添加拦截页 View，供 showIntercept 和广告结束后的回调共用。 */
    private fun showInterceptInternal(
        packageName: String,
        appName: String,
        dailyLimitMinutes: Int,
        weeklyLimitMinutes: Int,
        todayUsedSeconds: Long,
        weekUsedSeconds: Long,
        todayRecords: List<com.life.mindfulnessapp.data.db.entity.UsageRecordEntity>,
        remainingModifyCount: Int,
        onContinue: (com.life.mindfulnessapp.domain.model.InterceptEnterDecision) -> Unit,
        onDismiss: () -> Unit,
        onOpenOwnApp: (() -> Unit)? = null,
        pendingInterrupt: PendingInterrupt? = null,
        onSessionResumed: ((UsageSession) -> Unit)? = null,
        sessionLimitEnabled: Boolean = true,
        defaultSessionLimitMinutes: Int = 15,
        intentQualityCheckEnabled: Boolean = false,
        intentBlockKeywords: List<String> = emptyList(),
        impulseCount: Int = 1,
        enterCount: Int = 0,
        dismissCount: Int = 0,
        recentPurposes: List<com.life.mindfulnessapp.domain.model.RecentPurpose> = emptyList()
    ) {
        val capsuleTargetPos = CapsuleTargetPosition(
            x = capsuleDockAbsoluteCenterX(),
            y = capsuleRestingYPx().toFloat()
        )

        // InterceptOverlayScreen 专用 reset 回调（内部有调整弹窗，携带用户选择的新时长）：
        // 用户在弹窗中确认新的时间目标后，先保存限额，再关闭浮窗，不再跳转设置页
        val resetCallbackWithValue: ((newDailyMinutes: Int, newWeeklyMinutes: Int) -> Unit)? =
            if (remainingModifyCount > 0) {
                { newDailyMinutes, newWeeklyMinutes ->
                    scope.launch {
                        appLimitRepository.resetAppLimit(
                            packageName = packageName,
                            newDailyLimitMinutes = newDailyMinutes,
                            newWeeklyLimitMinutes = newWeeklyMinutes
                        )
                        // 保存完成后关闭浮窗，由监控服务重新判断是否需要拦截
                        // 不调用外部 onReset（不跳设置页），限额已在此处直接更新
                        mainHandler.post {
                            removeInterceptViewInternal()
                            isInterceptVisible.set(false)
                        }
                    }
                }
            } else null

        val isDarkTheme = appPreferences.isDarkThemeEnabled()

        val isLimitTheme =
            (dailyLimitMinutes > 0 && todayUsedSeconds >= dailyLimitMinutes * 60L) ||
                (weeklyLimitMinutes > 0 && weekUsedSeconds >= weeklyLimitMinutes * 60L)

        // ── 离开肯定：日常轻提示（先离开再肯定）；里程碑才全屏勋章后再离开 ──
        // 拦截页会先播短退场，再调到此处
        val dismissWithCeremony: () -> Unit = {
            pendingInterruptStore.clear(packageName)
            isInterceptVisible.set(false)
            showDismissCeremony(
                packageName = packageName,
                destination = DismissDestination.HOME,
                isLimitTheme = isLimitTheme,
                offerPositiveDestination = true,
                onDismissCompleted = { onDismiss() }
            )
            mainHandler.postDelayed({ removeInterceptViewInternal() }, 300)
            Unit
        }

        val openOwnAppWithCeremony: (() -> Unit)? = onOpenOwnApp?.let { open ->
            {
                pendingInterruptStore.clear(packageName)
                isInterceptVisible.set(false)
                showDismissCeremony(
                    packageName = packageName,
                    destination = DismissDestination.OWN_APP,
                    isLimitTheme = isLimitTheme,
                    offerPositiveDestination = false,
                    onDismissCompleted = { open() }
                )
                mainHandler.postDelayed({ removeInterceptViewInternal() }, 300)
                Unit
            }
        }

        val resumeCallback: (() -> Unit)? =
            if (pendingInterrupt != null && onSessionResumed != null) {
                {
                    val interrupt = pendingInterrupt
                    scope.launch {
                        val session = sessionManager.resumeInterruptedSession(interrupt)
                        mainHandler.post {
                            if (session != null) {
                                removeInterceptViewInternal()
                                isInterceptVisible.set(false)
                                onSessionResumed(session)
                            } else {
                                // 恢复失败：清快照并重开标准拦截（无继续条）
                                pendingInterruptStore.clear(packageName)
                                removeInterceptViewInternal()
                                isInterceptVisible.set(true)
                                showInterceptInternal(
                                    packageName, appName, dailyLimitMinutes, weeklyLimitMinutes,
                                    todayUsedSeconds, weekUsedSeconds, todayRecords,
                                    remainingModifyCount, onContinue, onDismiss,
                                    onOpenOwnApp = onOpenOwnApp,
                                    pendingInterrupt = null,
                                    onSessionResumed = onSessionResumed,
                                    sessionLimitEnabled = sessionLimitEnabled,
                                    defaultSessionLimitMinutes = defaultSessionLimitMinutes,
                                    intentQualityCheckEnabled = intentQualityCheckEnabled,
                                    intentBlockKeywords = intentBlockKeywords,
                                    impulseCount = impulseCount,
                                    enterCount = enterCount,
                                    dismissCount = dismissCount,
                                    recentPurposes = recentPurposes
                                )
                            }
                        }
                    }
                }
            } else null

        val composeView = createComposeView {
            InterceptOverlayScreen(
                appName = appName,
                packageName = packageName,
                dailyLimitMinutes = dailyLimitMinutes,
                weeklyLimitMinutes = weeklyLimitMinutes,
                todayUsedSeconds = todayUsedSeconds,
                weekUsedSeconds = weekUsedSeconds,
                todayRecords = todayRecords,
                capsuleTargetPosition = capsuleTargetPos,
                remainingModifyCount = remainingModifyCount,
                themeId = "simple",
                isDarkTheme = isDarkTheme,
                sessionLimitEnabled = sessionLimitEnabled,
                defaultSessionLimitMinutes = defaultSessionLimitMinutes,
                intentQualityCheckEnabled = intentQualityCheckEnabled,
                intentBlockKeywords = intentBlockKeywords,
                pendingInterrupt = pendingInterrupt,
                onReset = resetCallbackWithValue,
                onContinue = { decision ->
                    // 开始新的一次：丢弃未闭环快照
                    pendingInterruptStore.clear(packageName)
                    removeInterceptViewInternal()
                    onContinue(decision)
                },
                onResumePrevious = resumeCallback,
                onDismiss = dismissWithCeremony,
                onOpenOwnApp = openOwnAppWithCeremony,
                impulseCount = impulseCount,
                enterCount = enterCount,
                dismissCount = dismissCount,
                recentPurposes = recentPurposes
            )
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            @Suppress("DEPRECATION")
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        try {
            windowManager.addView(composeView, params)
            interceptView = composeView
        } catch (e: Exception) {
            e.printStackTrace()
            isInterceptVisible.set(false)
        }
    }

    /**
     * 显示达到时限的浮窗。
     *
     * @param showAd 是否在超限页前插播广告（非 VIP 专属）。
     *               传 false 的场景：用户**正在使用 App 期间**自然到时，此时直接弹超限页，
     *               不插广告（体验突兀，且用户对「时间到了」的感知优先于广告）。
     *               传 true 的场景：用户**重新打开**一个已超限的 App，此时可以插播广告。
     */
    fun showLimitReached(
        packageName: String,
        onDismiss: () -> Unit,
        onReset: (() -> Unit)? = null,
        onContinueOverLimit: (() -> Unit)? = null,
        onOpenOwnApp: (() -> Unit)? = null,
        showAd: Boolean = false
    ) {
        isInterceptVisible.set(true)
        interceptTargetPackage = packageName

        // 仅在 showAd=true 且非 VIP 时才插播广告
        if (showAd && !appPreferences.isVipActive()) {
            showAdOverlay(packageName = packageName, onAdFinished = {
                // 广告播放期间 dismissIntercept() 可能被监控循环误触发（检测到 App 不在前台），
                // 将 isInterceptVisible 重新置为 true，确保超限页能够正常展示
                isInterceptVisible.set(true)
                showLimitReachedInternal(packageName, onDismiss, onReset, onContinueOverLimit, onOpenOwnApp)
            })
            return
        }

        showLimitReachedInternal(packageName, onDismiss, onReset, onContinueOverLimit, onOpenOwnApp)
    }

    /**
     * 时段锁硬挡页。优先级高于日限与意图门。
     * 不提供破界入口；解锁只能在配置里关闭时段（生效中关闭会过门槛）。
     */
    fun showPeriodLock(
        packageName: String,
        appName: String,
        windowLabel: String,
        daysLabel: String,
        commitment: String,
        remainingUnlockLabel: String,
        onDismiss: () -> Unit,
        onOpenOwnApp: (() -> Unit)? = null
    ) {
        isInterceptVisible.set(true)
        interceptTargetPackage = packageName

        mainHandler.post {
            if (!isInterceptVisible.get()) return@post
            removeInterceptViewInternal()

            val isDark = appPreferences.isDarkThemeEnabled()
            val composeView = createComposeView {
                PeriodLockOverlayScreen(
                    windowLabel = windowLabel,
                    daysLabel = daysLabel,
                    commitment = commitment,
                    remainingUnlockLabel = remainingUnlockLabel,
                    appName = appName,
                    isDarkTheme = isDark,
                    onDismiss = {
                        isInterceptVisible.set(false)
                        onDismiss()
                        mainHandler.postDelayed({ removeInterceptViewInternal() }, 600)
                    },
                    onOpenOwnApp = onOpenOwnApp?.let { open ->
                        {
                            isInterceptVisible.set(false)
                            open()
                            mainHandler.postDelayed({ removeInterceptViewInternal() }, 600)
                        }
                    }
                )
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            try {
                windowManager.addView(composeView, params)
                interceptView = composeView
            } catch (e: Exception) {
                e.printStackTrace()
                isInterceptVisible.set(false)
            }
        }
    }

    /**
     * 真正执行「显示超限浮窗」的内部方法，供 VIP 路径和广告结束后的回调共用。
     */
    private fun showLimitReachedInternal(
        packageName: String,
        onDismiss: () -> Unit,
        onReset: (() -> Unit)? = null,
        onContinueOverLimit: (() -> Unit)? = null,
        onOpenOwnApp: (() -> Unit)? = null
    ) {
        scope.launch {
            val now = System.currentTimeMillis()
            val todayUsed = usageRecordRepository.getDailyUsageSeconds(packageName, now)
            val remainingModifyCount = appLimitRepository.getRemainingModifyCount(packageName)

            mainHandler.post {
                // 如果在异步加载数据期间已被取消，放弃展示
                if (!isInterceptVisible.get()) return@post
                removeInterceptViewInternal()

                // 超限页共用的 reset 回调
                val limitResetCallback: (() -> Unit)? = if (remainingModifyCount > 0 && onReset != null) {
                    {
                        removeInterceptViewInternal()
                        if (!appPreferences.isVipActive()) {
                            isInterceptVisible.set(true)
                            showAdOverlay(packageName = packageName, onAdFinished = {
                                isInterceptVisible.set(false)
                                onReset()
                            })
                        } else {
                            isInterceptVisible.set(false)
                            onReset()
                        }
                    }
                } else null

                val isDarkThemeLimit = appPreferences.isDarkThemeEnabled()
                val composeView = createComposeView {
                    LimitReachedOverlayScreen(
                        todayUsedSeconds = todayUsed,
                        remainingModifyCount = remainingModifyCount,
                        themeId = "simple",
                        isDarkTheme = isDarkThemeLimit,
                        onReset = limitResetCallback,
                        onDismiss = {
                            // 先执行 onDismiss（pressHomeButton），让被拦截 App 先退出到后台，
                            // 再延迟移除拦截页 View，确保用户在视觉上不会看到 App 界面
                            isInterceptVisible.set(false)
                            onDismiss()
                            mainHandler.postDelayed({ removeInterceptViewInternal() }, 600)
                        },
                        onOpenOwnApp = onOpenOwnApp?.let { open ->
                            {
                                isInterceptVisible.set(false)
                                open()
                                mainHandler.postDelayed({ removeInterceptViewInternal() }, 600)
                            }
                        },
                        onContinueOverLimit = if (onContinueOverLimit != null) {
                            {
                                // 用户明确选择超限继续：立即关闭超限页，由外部 Service 开启续记 session
                                isInterceptVisible.set(false)
                                removeInterceptViewInternal()
                                onContinueOverLimit()
                            }
                        } else null
                    )
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                }
                try {
                    windowManager.addView(composeView, params)
                    interceptView = composeView
                } catch (e: Exception) {
                    e.printStackTrace()
                    isInterceptVisible.set(false)
                }
            }
        }
    }

    /**
     * 单次意图时长到点收口页。
     * 会话暂不结束：离开时再收口（可带可选正念对照）。续时已前移到胶囊。
     */
    fun showSessionLimitReached(
        packageName: String,
        appName: String,
        purpose: String?,
        committedMinutes: Int,
        onConfirm: (mindfulnessLevel: Int?, note: String?) -> Unit
    ) {
        isInterceptVisible.set(true)
        interceptTargetPackage = packageName
        mainHandler.post {
            if (!isInterceptVisible.get()) return@post
            removeInterceptViewInternal()
            val isDark = appPreferences.isDarkThemeEnabled()
            val composeView = createComposeView {
                SessionLimitReachedOverlayScreen(
                    appName = appName,
                    purpose = purpose,
                    committedMinutes = committedMinutes,
                    isDarkTheme = isDark,
                    onConfirm = { level, note ->
                        isInterceptVisible.set(false)
                        onConfirm(level, note)
                        mainHandler.postDelayed({ removeInterceptViewInternal() }, 600)
                    }
                )
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            try {
                windowManager.addView(composeView, params)
                interceptView = composeView
            } catch (e: Exception) {
                e.printStackTrace()
                isInterceptVisible.set(false)
            }
        }
    }

    /**
     * 展示广告全屏浮窗（非 VIP 超限时调用）。
     * 广告倒计时结束或用户跳过后，移除广告 View 并触发 [onAdFinished]。
     */
    private fun showAdOverlay(packageName: String, onAdFinished: () -> Unit) {
        mainHandler.post {
            removeAdViewInternal()
            adCancelled = false   // 重置取消标志
            isAdPlaying.set(true)  // 标记广告开始播放

            // ComposeView 是 final 类无法继承，用 FrameLayout 作为外层容器，
            // 在其上覆写按键拦截和焦点保持逻辑，ComposeView 作为子 View 填充其中。
            val adContainerView = object : android.widget.FrameLayout(context) {
                // 吃掉所有硬件按键（Back / Menu / Volume 等），防止用户绕过广告
                override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean = true

                // Home 键按下时系统会让当前窗口失焦；失焦后立刻重新请求焦点，
                // 使下一次按键事件仍由本 View 处理（间接阻止连续 Home 键操作后的焦点丢失）
                override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
                    super.onWindowFocusChanged(hasWindowFocus)
                    if (!hasWindowFocus) {
                        mainHandler.postDelayed({ requestFocus() }, 50)
                    }
                }
            }

            // ComposeView 从根 View 向上查找 ViewTreeLifecycleOwner，
            // 因此必须在容器（根 View）上也设置这三个属性，否则会抛出
            // "ViewTreeLifecycleOwner not found" 异常。
            // 同时对根容器也开启硬件加速层，确保整个 View 树都走 GPU 渲染，
            // 避免 "Software rendering doesn't support drawRenderNode" 崩溃。
            adContainerView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
            val adLifecycleOwner = OverlayLifecycleOwner().also { it.start() }
            adContainerView.setViewTreeLifecycleOwner(adLifecycleOwner)
            adContainerView.setViewTreeViewModelStoreOwner(object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            })
            adContainerView.setViewTreeSavedStateRegistryOwner(adLifecycleOwner)

            // 将 ComposeView（承载广告 Compose 内容）添加到容器中
            val adComposeView = createComposeView {
                AdOverlayScreen(
                    onAdFinished = {
                        mainHandler.post {
                            isAdPlaying.set(false)  // 广告播放结束
                            removeAdViewInternal()
                            // 仅在广告未被外部强制取消（如用户 Home 键离开）时，才展示超限页
                            if (!adCancelled) {
                                onAdFinished()
                            }
                        }
                    }
                )
            }
            adContainerView.addView(
                adComposeView,
                android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                )
            )

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // 不加 FLAG_NOT_FOCUSABLE：让窗口可获得焦点，才能接收按键事件
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }

            try {
                windowManager.addView(adContainerView, params)
                adView = adContainerView
            } catch (e: Exception) {
                e.printStackTrace()
                // 广告展示失败时直接跳过，不影响超限页展示
                onAdFinished()
            }
        }
    }

    private fun removeAdViewInternal(cancel: Boolean = false) {
        if (cancel) adCancelled = true
        isAdPlaying.set(false)  // 广告 View 被移除时同步清除标志
        adView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) { }
            adView = null
        }
    }

    /**
     * 显示小胶囊浮窗，同时清除"拦截展示中"标志。
     *
     * 入场策略：
     * - 意图门在场：窄条 → 展开横幅停留 → 收成迷你
     * - 纯时长锁：圆环种子（呼吸点）→ 气泡展开成迷你
     * - 超限续记：轻淡入（仪表亮起），不做仪式
     */
    fun showCapsule(session: UsageSession, playEnterAnimation: Boolean = true) {
        isInterceptVisible.set(false)
        mainHandler.post {
            removeCapsuleViewInternal()
            removeCeremonyViewInternal()

            capsuleAppName.value = session.appName
            capsuleAppPackageName.value = session.packageName
            capsuleSessionSeconds.value = session.currentSessionSeconds
            capsuleDailyRemainingSeconds.value = session.budgetRemainingSeconds.let {
                if (it == Long.MAX_VALUE) 0L else it
            }
            capsuleDailyLimitSeconds.value = when {
                session.hasSessionLimit -> session.effectiveSessionLimitSeconds
                else -> session.dailyLimitSeconds
            }
            capsulePurpose.value = session.purpose
            capsuleIsPaused.value = false  // 显示时默认非暂停
            capsuleIsOverLimit.value = session.isOverLimitSession
            capsuleHasIntentGate.value = session.hasIntentGate
            capsuleHasTimeLock.value = session.hasTimeLock || session.hasSessionLimit
            capsuleHasSessionLimit.value = session.hasSessionLimit
            capsuleCanExtend.value = session.canOfferSessionExtension
            capsuleShowUsedSeconds.value = appPreferences.isCapsuleUsedShowSeconds()
            capsuleMiniCompact.value = appPreferences.isCapsuleMiniCompact()

            capsuleWakeUp = null

            // 超限续记：轻淡入；纯时长锁走圆环气泡展开（由 CapsuleOverlayView 承接）
            val quietInstrumentEnter = session.isOverLimitSession
            val effectivePlayEnter = playEnterAnimation && !quietInstrumentEnter

            // 意图确认：意图门 + 有意图文案时短亮托底；常驻形态始终是文字锚点
            val playIntentSeal = effectivePlayEnter &&
                session.hasIntentGate &&
                !session.purpose.isNullOrBlank()
            capsuleExpanded.value = false

            addCapsuleView(
                session = session,
                playEnterAnimation = effectivePlayEnter,
                softReveal = quietInstrumentEnter && playEnterAnimation,
                playIntentSeal = playIntentSeal
            )
        }
    }

    /**
     * 将胶囊切换到暂停状态：
     * - 进度环变冷；离开倒计时默认收起
     * - [awayCountdownSeconds] != null 时进入离开倒计时（含意图门切走）
     * - 点按主体回到 App；「结束」由命中区单独处理
     */
    fun pauseCapsule(
        returnToAppAction: () -> Unit,
        awayCountdownSeconds: Long? = null
    ) {
        mainHandler.post {
            applyPausedState(returnToAppAction, awayCountdownSeconds)
        }
    }

    /**
     * 原子展示暂停胶囊（含离开倒计时），避免 showCapsule + pauseCapsule 两次 post 竞态：
     * show 末尾会把 isPaused 置 false / away 置 -1，若与 pause 交错会导致倒计时 UI 不更新。
     */
    fun showPausedCapsule(
        session: UsageSession,
        returnToAppAction: () -> Unit,
        awayCountdownSeconds: Long
    ) {
        isInterceptVisible.set(false)
        mainHandler.post {
            removeCapsuleViewInternal()
            removeCeremonyViewInternal()

            capsuleAppName.value = session.appName
            capsuleAppPackageName.value = session.packageName
            capsuleSessionSeconds.value = session.currentSessionSeconds
            capsuleDailyRemainingSeconds.value = session.budgetRemainingSeconds.let {
                if (it == Long.MAX_VALUE) 0L else it
            }
            capsuleDailyLimitSeconds.value = when {
                session.hasSessionLimit -> session.effectiveSessionLimitSeconds
                else -> session.dailyLimitSeconds
            }
            capsulePurpose.value = session.purpose
            capsuleIsOverLimit.value = session.isOverLimitSession
            capsuleHasIntentGate.value = session.hasIntentGate
            capsuleHasTimeLock.value = session.hasTimeLock || session.hasSessionLimit
            capsuleHasSessionLimit.value = session.hasSessionLimit
            capsuleCanExtend.value = session.canOfferSessionExtension
            capsuleShowUsedSeconds.value = appPreferences.isCapsuleUsedShowSeconds()
            capsuleMiniCompact.value = appPreferences.isCapsuleMiniCompact()
            capsuleWakeUp = null

            applyPausedState(returnToAppAction, awayCountdownSeconds)
            addCapsuleView(
                session = session,
                playEnterAnimation = false,
                softReveal = false,
                playIntentSeal = false
            )
        }
    }

    private fun applyPausedState(
        returnToAppAction: () -> Unit,
        awayCountdownSeconds: Long?
    ) {
        capsuleIsPaused.value = true
        returnToAppCallback = returnToAppAction
        capsuleExpanded.value = false
        if (awayCountdownSeconds != null) {
            capsuleAwayCountdownSeconds.value = awayCountdownSeconds.coerceAtLeast(0L)
        } else {
            capsuleAwayCountdownSeconds.value = -1L
        }
    }

    /** 更新离开倒计时剩余秒数（锁屏冻结期间由 Service 停更） */
    fun updateAwayCountdown(remainingSeconds: Long) {
        val sec = remainingSeconds.coerceAtLeast(0L)
        if (Looper.myLooper() == Looper.getMainLooper()) {
            capsuleAwayCountdownSeconds.value = sec
        } else {
            mainHandler.post { capsuleAwayCountdownSeconds.value = sec }
        }
    }

    /** 恢复胶囊到活跃状态（app回到前台时调用） */
    fun resumeCapsule() {
        mainHandler.post {
            capsuleIsPaused.value = false
            capsuleExpanded.value = false
            capsuleAwayCountdownSeconds.value = -1L
            returnToAppCallback = null
        }
    }

    /** 点击暂停胶囊时回到app的回调 */
    private var returnToAppCallback: (() -> Unit)? = null

    /**
     * 创建并添加普通胶囊 View 到 WindowManager。
     */
    private fun addCapsuleView(
        session: UsageSession,
        playEnterAnimation: Boolean,
        softReveal: Boolean = false,
        playIntentSeal: Boolean = false
    ) {
        val isDarkTheme = appPreferences.isDarkThemeEnabled()
        val composeView = createComposeView {
            CapsuleOverlayView(
                sessionManager = null,
                appName = capsuleAppName,
                appPackageName = capsuleAppPackageName,
                sessionSeconds = capsuleSessionSeconds,
                dailyRemainingSeconds = capsuleDailyRemainingSeconds,
                dailyLimitSeconds = capsuleDailyLimitSeconds,
                purpose = capsulePurpose,
                expanded = capsuleExpanded,
                isPaused = capsuleIsPaused,
                isOverLimit = capsuleIsOverLimit,
                hasIntentGate = capsuleHasIntentGate,
                hasTimeLock = capsuleHasTimeLock,
                hasSessionLimit = capsuleHasSessionLimit,
                canOfferExtension = capsuleCanExtend,
                showUsedSeconds = capsuleShowUsedSeconds,
                miniCompact = capsuleMiniCompact,
                awayCountdownSeconds = capsuleAwayCountdownSeconds,
                isDarkTheme = isDarkTheme,
                onToggleExpand = { toggleCapsuleExpanded() },
                onEndSession = { note, mindfulnessLevel, openToAnchor ->
                    finishManualEndSession(
                        note = note,
                        mindfulnessLevel = mindfulnessLevel,
                        openToAnchor = openToAnchor
                    )
                },
                onExtendSession = { minutes ->
                    onExtendSession?.invoke(minutes)
                },
                onReturnToApp = {
                    returnToAppCallback?.invoke()
                },
                onRegisterWakeUp = { fn -> capsuleWakeUp = fn },
                onRegisterShowConfirm = { fn -> capsuleShowConfirm = fn },
                onRegisterWarnFiveMin = { fn -> capsuleWarnFiveMin = fn },
                onRegisterStartCountdown = { fn -> capsuleStartCountdown = fn },
                onRegisterStopAction = { fn -> capsuleRequestEnd = fn },
                onRegisterSkipEntrance = { fn -> capsuleSkipEntrance = fn },
                onStopHitRectChanged = { rect -> capsuleStopHitRect = rect },
                onEndDialogVisibilityChanged = { open ->
                    capsuleEndDialogOpen = open
                    isCapsuleDialogBlocking.set(open)
                    setCapsuleFocusableForInput(open)
                    if (open) {
                        // 贴边收起时弹窗会随 WRAP_CONTENT 变宽，中心偏移导致大半跑出屏幕
                        centerCapsuleForDialog()
                    } else if (!capsuleExpanded.value) {
                        restoreCapsuleAfterDialog()
                    }
                },
                onConfirmDialogOpen = {
                    // 同步置位，避免 LaunchedEffect 与监控轮询之间的空窗被误 resume
                    isCapsuleDialogBlocking.set(true)
                    scope.launch { sessionManager.onAppGoBackground() }
                },
                onConfirmDialogClose = {
                    sessionManager.onAppReturnToForeground()
                },
                onMiniSettled = {
                    // 入场仪式从居中收起后，吸到偏好停靠点
                    if (!capsuleExpanded.value) {
                        mainHandler.postDelayed(snapAfterCollapseRunnable, 320L)
                    }
                },
                playEnterAnimation = playEnterAnimation,
                softReveal = softReveal,
                playIntentSeal = playIntentSeal
            )
        }

        val density = context.resources.displayMetrics.density
        val screenWidth = windowManager.currentWindowMetrics.bounds.width()
        // 收起壳随内容分档 + 水平 padding 16dp；CENTER_HORIZONTAL 下的停靠 x
        val compact = capsuleMiniCompact.value
        val collapsedShellDp = when {
            capsuleIsPaused.value -> if (compact) 176f else 200f
            capsuleHasIntentGate.value && !capsulePurpose.value.isNullOrBlank() ->
                if (compact) 192f else 216f
            capsuleHasIntentGate.value -> if (compact) 142f else 160f
            else -> if (compact) 130f else 148f
        }
        val collapsedViewW = ((collapsedShellDp + 16f) * density).toInt()
        val dock = appPreferences.getCapsuleDockPosition()
        val dockX = capsuleDockOffsetX(dock, screenWidth, collapsedViewW)
        // 意图门入场横幅从居中长出；其余直接落在停靠点
        val startCentered = playEnterAnimation && capsuleHasIntentGate.value

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            // CENTER_HORIZONTAL：x=0 即水平居中；宽度变化时系统保持居中（iOS 展开态）
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = if (startCentered) 0 else dockX
            // 贴状态栏下缘，视觉上像从系统区长出
            y = capsuleRestingYPx()
        }
        capsuleParams = params

        val capsuleHost = createCapsuleTouchHost(
            composeView = composeView,
            params = params,
            onClick = {
                // 入场展开中：点按提前收起
                // 暂停态：点按主体回来（「结束」由命中区单独处理）
                // 迷你态：点按展开；展开态点胶囊本身不收起（点外侧才收）
                when {
                    capsuleSkipEntrance != null -> capsuleSkipEntrance?.invoke()
                    capsuleIsPaused.value -> returnToAppCallback?.invoke()
                    !capsuleExpanded.value -> toggleCapsuleExpanded()
                }
            }
        )

        try {
            windowManager.addView(capsuleHost, params)
            capsuleView = capsuleHost
            if (capsuleUpdateJob == null || capsuleUpdateJob?.isActive == false) {
                startCapsuleTimer(session)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** 展开 ↔ 收起，并同步窗口水平位置（展开居中 / 收起贴边） */
    private fun toggleCapsuleExpanded() {
        val next = !capsuleExpanded.value
        capsuleExpanded.value = next
        syncCapsuleHorizontalForExpansion(next)
    }

    /** 结束确认 / 续时弹窗：窗口水平居中，避免贴边时弹窗大半出屏 */
    private fun centerCapsuleForDialog() {
        val view = capsuleView ?: return
        val params = capsuleParams ?: return
        mainHandler.removeCallbacks(snapAfterCollapseRunnable)
        capsuleSnapAnimator?.cancel()
        if (params.x != 0) {
            params.x = 0
            try {
                windowManager.updateViewLayout(view, params)
            } catch (_: Exception) { /* ignore */ }
        }
    }

    /** 弹窗关闭且仍为收起态：还原停靠点 */
    private fun restoreCapsuleAfterDialog() {
        val view = capsuleView ?: return
        val params = capsuleParams ?: return
        params.y = capsuleRestingYPx()
        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: Exception) { /* ignore */ }
        snapCapsuleToDock(view, params, forceDock = appPreferences.getCapsuleDockPosition())
    }

    /** 结束确认含备注输入时临时可聚焦，便于弹键盘；关闭后恢复不可聚焦。 */
    private fun setCapsuleFocusableForInput(focusable: Boolean) {
        val view = capsuleView ?: return
        val params = capsuleParams ?: return
        if (focusable) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            @Suppress("DEPRECATION")
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        } else {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
        }
        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: Exception) { /* ignore */ }
    }

    private fun syncCapsuleHorizontalForExpansion(expanded: Boolean) {
        val view = capsuleView ?: return
        val params = capsuleParams ?: return
        mainHandler.removeCallbacks(snapAfterCollapseRunnable)
        capsuleSnapAnimator?.cancel()
        if (expanded) {
            // 展开立刻归中；停靠点已在偏好中，收起后按偏好还原
            capsuleSnapAnimator?.cancel()
            if (params.x != 0) {
                params.x = 0
                try {
                    windowManager.updateViewLayout(view, params)
                } catch (_: Exception) { /* ignore */ }
            }
        } else {
            // 等壳宽弹簧大致落定再贴边，避免宽横幅先甩到边缘
            mainHandler.postDelayed(snapAfterCollapseRunnable, 360L)
        }
    }

    private fun animateCapsuleTo(
        view: View,
        params: WindowManager.LayoutParams,
        targetX: Int,
        targetY: Int,
        minDuration: Long = 80L,
        maxDuration: Long = 280L
    ) {
        capsuleSnapAnimator?.cancel()
        val startX = params.x
        val startY = params.y
        if (startX == targetX && startY == targetY) return
        val distance = maxOf(
            kotlin.math.abs(targetX - startX),
            kotlin.math.abs(targetY - startY)
        )
        val duration = (distance / 100f * 40f).toLong().coerceIn(minDuration, maxDuration)
        capsuleSnapAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator(1.55f)
            addUpdateListener { animator ->
                val t = animator.animatedValue as Float
                params.x = (startX + (targetX - startX) * t).toInt()
                params.y = (startY + (targetY - startY) * t).toInt()
                try {
                    windowManager.updateViewLayout(view, params)
                } catch (_: Exception) { cancel() }
            }
            start()
        }
    }

    private fun startCapsuleTimer(session: UsageSession) {
        capsuleUpdateJob?.cancel()
        capsuleSession = session
        // 预警触发标记：确保每个节点仅触发一次
        var fiveMinWarned = false
        var countdownStarted = false
        capsuleUpdateJob = scope.launch {
            while (true) {
                // 每次刷新时从 SessionManager 读取最新 session（含 isInBackground 状态），
                // 避免使用快照导致后台/暂停期间计时继续增长。
                val s = sessionManager.currentSession.value ?: capsuleSession
                if (s != null) {
                    val activeSeconds = s.currentSessionSeconds
                    capsuleSessionSeconds.value = activeSeconds
                    val remaining = s.budgetRemainingSeconds.let {
                        if (it == Long.MAX_VALUE) 0L else it
                    }
                    capsuleDailyRemainingSeconds.value = remaining
                    capsuleDailyLimitSeconds.value = when {
                        s.hasSessionLimit -> s.effectiveSessionLimitSeconds
                        else -> s.dailyLimitSeconds
                    }
                    capsuleHasSessionLimit.value = s.hasSessionLimit
                    capsuleCanExtend.value = s.canOfferSessionExtension
                    capsuleHasTimeLock.value = s.hasTimeLock || s.hasSessionLimit
                    capsuleShowUsedSeconds.value = appPreferences.isCapsuleUsedShowSeconds()
                    capsuleMiniCompact.value = appPreferences.isCapsuleMiniCompact()

                    // ── 预警：优先会话预算，否则日预算；非超限续记 ──────────
                    // 意图门单次时长：剩余 1 分钟且可续时，展开胶囊给出续时入口
                    val hasBudget = s.hasSessionLimit || s.dailyLimitSeconds > 0
                    val intentSessionBudget = s.hasIntentGate && s.hasSessionLimit
                    if (intentSessionBudget && !s.isOverLimitSession) {
                        if (!countdownStarted && remaining in 1L..60L && s.canOfferSessionExtension) {
                            countdownStarted = true
                            mainHandler.post {
                                capsuleExpanded.value = true
                                syncCapsuleHorizontalForExpansion(true)
                                capsuleStartCountdown?.invoke()
                            }
                        }
                        if (countdownStarted && remaining > 60L) {
                            countdownStarted = false
                        }
                    } else if (hasBudget && !s.isOverLimitSession && !intentSessionBudget) {
                        if (!fiveMinWarned && remaining in 1L..300L) {
                            fiveMinWarned = true
                            mainHandler.post {
                                capsuleExpanded.value = true
                                syncCapsuleHorizontalForExpansion(true)
                                capsuleWarnFiveMin?.invoke()
                            }
                        }
                        if (!countdownStarted && remaining in 1L..60L) {
                            countdownStarted = true
                            mainHandler.post {
                                capsuleExpanded.value = true
                                syncCapsuleHorizontalForExpansion(true)
                                capsuleStartCountdown?.invoke()
                            }
                        }
                        // 续时后剩余重新拉开：允许再次预警
                        if (countdownStarted && remaining > 60L) {
                            countdownStarted = false
                        }
                        if (fiveMinWarned && remaining > 300L) {
                            fiveMinWarned = false
                        }
                    }
                }
                delay(1000L)
            }
        }
    }

    /**
     * 悬浮窗拖拽必须用 [WindowManager.updateViewLayout] 跟手。
     *
     * 用 [FrameLayout.onInterceptTouchEvent] 在 Compose 子 View 之前抢走手势：
     * 仅收起态拦截（拖拽 + 「结束」命中区）；展开态 / 弹窗打开时不拦截，
     * 以便 Compose clickable（「结束」「续一会儿」）正常响应。
     *
     * 坐标系：gravity 含 [Gravity.CENTER_HORIZONTAL]，params.x 为相对屏幕水平中心的偏移。
     */
    private fun createCapsuleTouchHost(
        composeView: ComposeView,
        params: WindowManager.LayoutParams,
        onClick: () -> Unit
    ): View {
        val host = object : FrameLayout(context) {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDragging = false

            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    if (!capsuleEndDialogOpen) {
                        when {
                            capsuleSkipEntrance != null -> capsuleSkipEntrance?.invoke()
                            capsuleExpanded.value -> toggleCapsuleExpanded()
                        }
                    }
                    return true
                }
                return super.dispatchTouchEvent(ev)
            }

            override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
                // 确认 / 续时弹窗：把触摸交给 Compose
                if (capsuleEndDialogOpen) return false
                // 展开态：交给 Compose 处理「结束」「续一会儿」等 clickable；
                // 收起态仍由 Window 层拦截（拖拽 + 结束命中区）
                if (capsuleExpanded.value) return false
                return true
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (capsuleEndDialogOpen || capsuleExpanded.value) return false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        capsuleSnapAnimator?.cancel()
                        mainHandler.removeCallbacks(snapAfterCollapseRunnable)
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        translationX = 0f
                        translationY = 0f
                        isDragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (isDragging || kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) {
                            isDragging = true
                            params.x = initialX + dx
                            params.y = initialY + dy
                            try {
                                windowManager.updateViewLayout(this, params)
                            } catch (_: Exception) { /* ignore */ }
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val wasDragging = isDragging
                        isDragging = false
                        if (wasDragging) {
                            snapCapsuleToDock(this, params)
                        } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                            capsuleWakeUp?.invoke()
                            val stopHit = capsuleStopHitRect
                            if (stopHit != null && stopHit.contains(event.x, event.y)) {
                                capsuleRequestEnd?.invoke()
                            } else {
                                onClick()
                            }
                        }
                        return true
                    }
                    else -> return false
                }
            }
        }

        // Compose attach 时从窗口根 View（本 host）查找 ViewTreeLifecycleOwner。
        // 只设在子 ComposeView 上不够，会抛 "ViewTreeLifecycleOwner not found"。
        host.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        val hostLifecycleOwner = OverlayLifecycleOwner().also { it.start() }
        host.setViewTreeLifecycleOwner(hostLifecycleOwner)
        host.setViewTreeViewModelStoreOwner(object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        })
        host.setViewTreeSavedStateRegistryOwner(hostLifecycleOwner)

        host.addView(
            composeView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        return host
    }

    /**
     * 胶囊常驻 Y：贴状态栏下缘再留约 4dp。
     * 不用塞进状态栏内部，避免与系统图标抢位。
     */
    private fun capsuleRestingYPx(): Int {
        val density = context.resources.displayMetrics.density
        val statusBarH = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.windowInsets
                .getInsetsIgnoringVisibility(android.view.WindowInsets.Type.statusBars())
                .top
        } else {
            @Suppress("DEPRECATION")
            val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resId > 0) context.resources.getDimensionPixelSize(resId)
            else (24f * density).toInt()
        }
        return statusBarH + (4f * density).toInt()
    }

    /**
     * 收起态吸到顶部左 / 中 / 右停靠点，并回到状态栏下缘高度。
     * [forceDock] 非空时强制该侧（展开收起 / 弹窗还原）；否则按当前 x 就近判断并写回偏好。
     */
    private fun snapCapsuleToDock(
        view: View,
        params: WindowManager.LayoutParams,
        forceDock: String? = null
    ) {
        val screenWidth = windowManager.currentWindowMetrics.bounds.width()
        val viewWidth = view.width.takeIf { it > 0 } ?: 200
        val dock = forceDock
            ?: nearestCapsuleDock(params.x, screenWidth, viewWidth).also {
                appPreferences.setCapsuleDockPosition(it)
            }
        val targetX = capsuleDockOffsetX(dock, screenWidth, viewWidth)
        val targetY = capsuleRestingYPx()
        animateCapsuleTo(view, params, targetX, targetY)
    }

    private fun capsuleDockOffsetX(dock: String, screenWidth: Int, viewWidth: Int): Int {
        return when (AppPreferences.normalizeCapsuleDockPosition(dock)) {
            AppPreferences.CAPSULE_DOCK_CENTER -> 0
            AppPreferences.CAPSULE_DOCK_RIGHT -> (screenWidth - viewWidth) / 2
            else -> -(screenWidth - viewWidth) / 2
        }
    }

    private fun nearestCapsuleDock(offsetX: Int, screenWidth: Int, viewWidth: Int): String {
        val left = capsuleDockOffsetX(AppPreferences.CAPSULE_DOCK_LEFT, screenWidth, viewWidth)
        val center = 0
        val right = capsuleDockOffsetX(AppPreferences.CAPSULE_DOCK_RIGHT, screenWidth, viewWidth)
        return listOf(
            AppPreferences.CAPSULE_DOCK_LEFT to left,
            AppPreferences.CAPSULE_DOCK_CENTER to center,
            AppPreferences.CAPSULE_DOCK_RIGHT to right
        ).minBy { kotlin.math.abs(offsetX - it.second) }.first
    }

    /** 拦截退场动画用的停靠点绝对中心 X（屏幕坐标） */
    private fun capsuleDockAbsoluteCenterX(): Float {
        val screenWidth = windowManager.currentWindowMetrics.bounds.width()
        val density = context.resources.displayMetrics.density
        val approxW = ((160f + 16f) * density).toInt()
        val dock = appPreferences.getCapsuleDockPosition()
        val offsetX = capsuleDockOffsetX(dock, screenWidth, approxW)
        return screenWidth / 2f + offsetX
    }

    private fun removeInterceptViewInternal() {
        interceptView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) { }
            interceptView = null
        }
    }

    private fun removeCeremonyViewInternal() {
        ceremonyView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) { }
            ceremonyView = null
        }
    }

    private fun removeCapsuleViewInternal() {
        capsuleUpdateJob?.cancel()
        capsuleSession = null
        capsuleExpanded.value = false
        capsulePurpose.value = null
        capsuleWakeUp = null
        capsuleShowConfirm = null
        capsuleWarnFiveMin = null
        capsuleStartCountdown = null
        capsuleSkipEntrance = null
        capsuleIsPaused.value = false
        capsuleIsOverLimit.value = false
        capsuleHasIntentGate.value = true
        capsuleHasTimeLock.value = true
        capsuleHasSessionLimit.value = false
        capsuleCanExtend.value = false
        capsuleAwayCountdownSeconds.value = -1L
        returnToAppCallback = null
        mainHandler.removeCallbacks(snapAfterCollapseRunnable)
        capsuleSnapAnimator?.cancel()
        capsuleSnapAnimator = null
        capsuleParams = null
        capsuleRequestEnd = null
        capsuleStopHitRect = null
        capsuleEndDialogOpen = false
        isCapsuleDialogBlocking.set(false)
        capsuleView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) { }
            capsuleView = null
        }
    }

    /**
     * 手动结束会话的统一收口：写入 note / 正念程度（可选）、移除胶囊、回调 Service。
     * @param openToAnchor 为 true 时走「结束并去心锚」
     */
    private fun finishManualEndSession(
        note: String?,
        mindfulnessLevel: Int?,
        openToAnchor: Boolean = false
    ) {
        val session = sessionManager.currentSession.value
        val endingRecordId = session?.recordId
        val purposeHint = session?.purpose
        val packageName = session?.packageName.orEmpty()
        val reviewed = UsageRecordEntity.MindfulnessLevel.isValid(mindfulnessLevel)
        val destination = when {
            openToAnchor -> ManualEndDestination.OpenRecord
            !reviewed -> ManualEndDestination.LegacyUnreviewed
            mindfulnessLevel == UsageRecordEntity.MindfulnessLevel.ALIGNED ->
                ManualEndDestination.HomeAligned
            else -> ManualEndDestination.HomeDrifted
        }
        removeCapsuleViewInternal()
        if (endingRecordId != null && destination != ManualEndDestination.LegacyUnreviewed) {
            onManualEndSession?.invoke(endingRecordId, mindfulnessLevel, destination)
            when (destination) {
                ManualEndDestination.HomeAligned -> {
                    mainHandler.postDelayed({
                        playLeaveFeedback(
                            LeaveFeedbackRequest(
                                kind = LeaveFeedbackKind.SessionAligned,
                                packageName = packageName,
                                purposeHint = purposeHint
                            ),
                            onLeaveCompleted = {}
                        )
                    }, 420L)
                }
                ManualEndDestination.HomeDrifted -> {
                    mainHandler.postDelayed({
                        playLeaveFeedback(
                            LeaveFeedbackRequest(
                                kind = LeaveFeedbackKind.SessionDrifted,
                                packageName = packageName
                            ),
                            onLeaveCompleted = {},
                            onAction = {
                                onManualEndSession?.invoke(
                                    endingRecordId,
                                    mindfulnessLevel,
                                    ManualEndDestination.OpenRecord
                                )
                            }
                        )
                    }, 420L)
                }
                else -> Unit
            }
        }
        scope.launch {
            sessionManager.endSession(
                reason = UsageRecordEntity.EndReason.MANUAL,
                note = note,
                mindfulnessLevel = mindfulnessLevel
            )
            if (endingRecordId != null && destination == ManualEndDestination.LegacyUnreviewed) {
                onManualEndSession?.invoke(endingRecordId, null, destination)
            }
        }
    }

    fun dismissIntercept() {
        isInterceptVisible.set(false)
        interceptTargetPackage = null
        mainHandler.post {
            removeAdViewInternal(cancel = true)  // 用户主动离开，强制取消广告，不再展示超限页
            removeInterceptViewInternal()
            // 拦截期离开（Home / 强杀 / 切走）：门未进，不应残留该次相关胶囊
            removeCapsuleViewInternal()
        }
    }

    fun dismissCapsule() {
        capsuleUpdateJob?.cancel()
        mainHandler.post { removeCapsuleViewInternal() }
    }

    /**
     * 后台超时时触发胶囊内的「后台超时确认弹窗」（与手动结束文案不同）。
     * 若胶囊已关闭（回调为 null），则直接返回 false，
     * 调用方应在 false 时自行静默结束会话。
     *
     * @return true = 已成功触发弹窗；false = 胶囊不存在，弹窗未显示
     */
    fun triggerBackgroundTimeoutConfirm(): Boolean {
        val fn = capsuleShowConfirm ?: return false
        mainHandler.post { fn() }
        return true
    }

    /**
     * 已废弃：原暂停提示气泡，现在改为胶囊持续展示+暂停状态
     * 保留空方法以避免编译错误，调用方需改为调用 pauseCapsule()
     */
    @Deprecated("请改用 pauseCapsule() 方法", ReplaceWith("pauseCapsule(returnToAppAction)"))
    fun showPausedToast(appName: String, pauseMinutes: Int, timeoutMs: Long) {
        // 已废弃，不再显示toast气泡
    }

    /** 已废弃：原关闭暂停气泡方法，现在暂停是胶囊状态切换，通过 resumeCapsule() 恢复 */
    @Deprecated("请改用 resumeCapsule() 方法", ReplaceWith("resumeCapsule()"))
    fun dismissPausedToast() {
        // 已废弃
    }

    /**
     * 门外离开后的肯定反馈（兼容入口）。
     * 内部统一走 [playLeaveFeedback]。
     *
     * @param offerPositiveDestination 仅拦截页主动点「离开」为 true；Home 切走勿开
     */
    fun showDismissCeremony(
        packageName: String,
        destination: DismissDestination = DismissDestination.HOME,
        isLimitTheme: Boolean = false,
        offerPositiveDestination: Boolean = false,
        onDismissCompleted: () -> Unit
    ) {
        playLeaveFeedback(
            request = LeaveFeedbackRequest(
                kind = LeaveFeedbackKind.GateLight,
                packageName = packageName,
                destination = destination,
                applyGateCooldown = true,
                isLimitTheme = isLimitTheme,
                offerPositiveDestination = offerPositiveDestination &&
                    destination == DismissDestination.HOME
            ),
            onLeaveCompleted = onDismissCompleted
        )
    }

    /**
     * 统一离开反馈调度。
     *
     * Gate*：冷却 / 里程碑 / 轻条；Session*：轻条或直进心锚（由调用方先完成 leave）。
     */
    fun playLeaveFeedback(
        request: LeaveFeedbackRequest,
        onLeaveCompleted: () -> Unit,
        onAction: (() -> Unit)? = null
    ) {
        when (request.kind) {
            LeaveFeedbackKind.SessionToAnchor, LeaveFeedbackKind.GateSilent -> {
                onLeaveCompleted()
                return
            }
            LeaveFeedbackKind.SessionAligned, LeaveFeedbackKind.SessionDrifted -> {
                val isDarkTheme = appPreferences.isDarkThemeEnabled()
                val copy = leaveFeedbackCopy(request)
                mainHandler.post {
                    showLightLeaveAffirmation(
                        copy = copy,
                        isDarkTheme = isDarkTheme,
                        onAction = onAction.takeIf {
                            request.kind == LeaveFeedbackKind.SessionDrifted
                        }
                    )
                }
                onLeaveCompleted()
                return
            }
            LeaveFeedbackKind.GateLight, LeaveFeedbackKind.GateMilestone -> Unit
        }

        if (request.applyGateCooldown) {
            val now = System.currentTimeMillis()
            val lastTime = lastDismissCeremonyTime[request.packageName] ?: 0L
            if (now - lastTime < dismissCeremonyCooldownMs) {
                onLeaveCompleted()
                return
            }
        }

        scope.launch {
            if (request.offerPositiveDestination) {
                appPreferences.incrementExplicitGateLeaveCount()
            }

            val countBefore = try {
                usageRecordRepository.getDayDismissCount()
            } catch (_: Exception) {
                0
            }
            val displayCount = if (request.dismissCount > 0) {
                request.dismissCount
            } else {
                countBefore + 1
            }
            val resolved = request.copy(
                dismissCount = displayCount,
                kind = when {
                    request.kind == LeaveFeedbackKind.GateMilestone ->
                        LeaveFeedbackKind.GateMilestone
                    request.destination == DismissDestination.HOME &&
                        isDismissMilestone(displayCount) ->
                        LeaveFeedbackKind.GateMilestone
                    else -> LeaveFeedbackKind.GateLight
                }
            )
            val isDarkTheme = appPreferences.isDarkThemeEnabled()
            val enriched = buildGateAffirmationCopy(resolved)

            mainHandler.post {
                if (resolved.kind == LeaveFeedbackKind.GateMilestone) {
                    showFullDismissCeremony(
                        dismissCount = displayCount,
                        packageName = resolved.packageName,
                        onDismissCompleted = {
                            onLeaveCompleted()
                            // 里程碑后若有归属/引导，再挂轻条（不挡勋章）
                            if (enriched.actionLabel != null || enriched.moreLabel != null) {
                                mainHandler.postDelayed({
                                    showLightLeaveAffirmation(
                                        copy = enriched,
                                        isDarkTheme = isDarkTheme,
                                        onAction = gateAffirmationAction(enriched),
                                        onMoreChoice = gateAffirmationMoreAction(enriched),
                                        onManage = gateAffirmationManageAction(enriched)
                                    )
                                }, 360L)
                            }
                        }
                    )
                } else {
                    onLeaveCompleted()
                    lastDismissCeremonyTime[resolved.packageName] = System.currentTimeMillis()
                    mainHandler.postDelayed({
                        showLightLeaveAffirmation(
                            copy = enriched,
                            isDarkTheme = isDarkTheme,
                            onAction = gateAffirmationAction(enriched),
                            onMoreChoice = gateAffirmationMoreAction(enriched),
                            onManage = gateAffirmationManageAction(enriched)
                        )
                    }, 420L)
                }
            }
        }
    }

    private fun buildGateAffirmationCopy(request: LeaveFeedbackRequest): LeaveFeedbackCopy {
        val base = leaveFeedbackCopy(request)
        if (!request.offerPositiveDestination ||
            request.destination != DismissDestination.HOME
        ) {
            return base
        }
        val all = appPreferences.getPositiveDestinations()
        if (all.isEmpty()) {
            return if (appPreferences.shouldOfferPositiveSetupNudge()) {
                appPreferences.markPositiveSetupNudgeShown(
                    appPreferences.getExplicitGateLeaveCount()
                )
                enrichGateCopyWithDestination(
                    base = base,
                    primary = null,
                    displayChoices = emptyList(),
                    setupNudge = true
                )
            } else {
                base
            }
        }
        val display = appPreferences.getPositiveDestinationsForDisplay()
        val choices = display.mapNotNull { dest ->
            val systemName = resolveAppLabel(dest.packageName) ?: return@mapNotNull null
            LeaveDestinationChoice(
                packageName = dest.packageName,
                label = dest.displayLabel(systemName)
            )
        }
        if (choices.isEmpty()) return base
        val preferredPkg = appPreferences.getPreferredPositiveDestination()
        val primary = choices.firstOrNull { it.packageName == preferredPkg } ?: choices.first()
        return enrichGateCopyWithDestination(
            base = base,
            primary = primary,
            displayChoices = choices,
            setupNudge = false,
            totalConfigured = all.size
        )
    }

    private fun resolveAppLabel(packageName: String): String? {
        return try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun gateAffirmationAction(copy: LeaveFeedbackCopy): (() -> Unit)? {
        return when {
            copy.opensSettings -> {
                { onOpenPositiveDestinationSettings?.invoke() }
            }
            !copy.primaryPackageName.isNullOrBlank() -> {
                {
                    val pkg = copy.primaryPackageName
                    appPreferences.setPreferredPositiveDestination(pkg)
                    onLaunchPositiveApp?.invoke(pkg)
                }
            }
            else -> null
        }
    }

    private fun gateAffirmationMoreAction(
        copy: LeaveFeedbackCopy
    ): ((LeaveDestinationChoice) -> Unit)? {
        if (copy.moreChoices.isEmpty() && !copy.showManageLink) return null
        return { choice ->
            appPreferences.setPreferredPositiveDestination(choice.packageName)
            onLaunchPositiveApp?.invoke(choice.packageName)
        }
    }

    private fun gateAffirmationManageAction(copy: LeaveFeedbackCopy): (() -> Unit)? {
        if (!copy.showManageLink && !copy.opensSettings) return null
        return { onOpenPositiveDestinationSettings?.invoke() }
    }

    private fun showFullDismissCeremony(
        dismissCount: Int,
        packageName: String,
        onDismissCompleted: () -> Unit
    ) {
        removeDismissCeremonyViewInternal()

        val ceremonyComposeView = createComposeView {
            DismissCeremonyOverlayView(
                dismissCount = dismissCount,
                onFinished = {
                    mainHandler.post {
                        removeDismissCeremonyViewInternal()
                        lastDismissCeremonyTime[packageName] = System.currentTimeMillis()
                        onDismissCompleted()
                    }
                }
            )
        }

        // 可点跳过：不加 FLAG_NOT_TOUCHABLE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        try {
            windowManager.addView(ceremonyComposeView, params)
            dismissCeremonyView = ceremonyComposeView
        } catch (e: Exception) {
            e.printStackTrace()
            onDismissCompleted()
        }
    }

    private fun showLightLeaveAffirmation(
        copy: LeaveFeedbackCopy,
        isDarkTheme: Boolean,
        onAction: (() -> Unit)?,
        onMoreChoice: ((LeaveDestinationChoice) -> Unit)? = null,
        onManage: (() -> Unit)? = null
    ) {
        removeDismissCeremonyViewInternal()
        if (copy.title.isBlank()) return

        val touchable = onAction != null || onMoreChoice != null || onManage != null
        val composeView = createComposeView {
            DismissAffirmationOverlay(
                copy = copy,
                isDarkTheme = isDarkTheme,
                onAction = onAction?.let { action ->
                    {
                        mainHandler.post {
                            removeDismissCeremonyViewInternal()
                            action()
                        }
                    }
                },
                onMoreChoice = onMoreChoice?.let { more ->
                    { choice ->
                        mainHandler.post {
                            removeDismissCeremonyViewInternal()
                            more(choice)
                        }
                    }
                },
                onManage = onManage?.let { manage ->
                    {
                        mainHandler.post {
                            removeDismissCeremonyViewInternal()
                            manage()
                        }
                    }
                },
                onFinished = {
                    mainHandler.post { removeDismissCeremonyViewInternal() }
                }
            )
        }

        // 可点轻条：顶部 WRAP_CONTENT，避免全屏挡桌面触控；不可点仍全屏透传
        val flags = if (touchable) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            if (touchable) WindowManager.LayoutParams.WRAP_CONTENT
            else WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        try {
            windowManager.addView(composeView, params)
            dismissCeremonyView = composeView
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeDismissCeremonyViewInternal() {
        dismissCeremonyView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) { }
            dismissCeremonyView = null
        }
    }

    fun dismissAll() {
        isInterceptVisible.set(false)
        interceptTargetPackage = null
        capsuleUpdateJob?.cancel()
        mainHandler.post {
            removeAdViewInternal(cancel = true)
            removeInterceptViewInternal()
            removeCeremonyViewInternal()
            removeDismissCeremonyViewInternal()
            removeCapsuleViewInternal()
        }
    }

    /** 创建一个能承载 Compose 内容的 ComposeView，并正确设置 Lifecycle */
    private fun createComposeView(content: @androidx.compose.runtime.Composable () -> Unit): ComposeView {
        val lifecycleOwner = OverlayLifecycleOwner()
        lifecycleOwner.start()

        return ComposeView(context).apply {
            // 悬浮窗 View 在某些设备/场景下默认使用软件渲染，
            // 而 Compose 的 GraphicsLayer、LazyLayout 动画、OverscrollModifier 等
            // 内部会调用 drawRenderNode，软件渲染不支持此操作会崩溃。
            // 强制开启硬件加速层以规避 "Software rendering doesn't support drawRenderNode"。
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            })
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent { content() }
        }
    }
}
