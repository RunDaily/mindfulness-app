package com.life.mindfulnessapp.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.data.repository.AppLimitRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository
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
    private val appPreferences: com.life.mindfulnessapp.data.AppPreferences
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** 由外部（Service）注册，在用户手动结束会话时执行（如按 Home 键） */
    var onManualEndSession: (() -> Unit)? = null

    private var interceptView: View? = null
    private var capsuleView: View? = null
    private var ceremonyView: View? = null   // 仪式感动画专属浮窗（居中、全屏透明）
    private var capsuleUpdateJob: Job? = null
    private var capsuleSession: UsageSession? = null

    /** Compose 层注册的唤醒回调：触摸时调用，让胶囊从休眠态弹回活跃态 */
    private var capsuleWakeUp: (() -> Unit)? = null

    /** Compose 层注册的「显示结束确认弹窗」回调：后台超时时由 Service 调用，弹出确认弹窗 */
    private var capsuleShowConfirm: (() -> Unit)? = null

    /**
     * 原子标志：当前是否正在展示拦截弹窗（或正在准备展示/创建会话）。
     * 防止监控循环在此期间重复触发。
     */
    val isInterceptVisible = AtomicBoolean(false)

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
     * 暂停时胶囊继续显示，但光盘停转，且点击可回到app
     */
    val capsuleIsPaused = mutableStateOf(false)

    /**
     * 显示全屏拦截浮窗（始终要求用户写下使用目的）
     * @param onContinue 用户确认继续时的回调，携带输入的使用目的
     */
    fun showIntercept(
        packageName: String,
        appName: String,
        dailyLimitMinutes: Int,
        weeklyLimitMinutes: Int,
        onContinue: (purpose: String?) -> Unit,
        onDismiss: () -> Unit,
        onReset: ((newDailyMinutes: Int, newWeeklyMinutes: Int) -> Unit)? = null,
        themeIdOverride: String? = null
    ) {
        isInterceptVisible.set(true)

        scope.launch {
            val now = System.currentTimeMillis()
            val dbTodayUsedSeconds = usageRecordRepository.getDailyUsageSeconds(packageName, now)
            val dbWeekUsedSeconds = usageRecordRepository.getWeeklyUsageSeconds(packageName, now)
            val todayRecords = usageRecordRepository.getDayRecordsForApp(packageName, now)
            val remainingModifyCount = appLimitRepository.getRemainingModifyCount(packageName)
            val currentLimit = appLimitRepository.getAppLimit(packageName)
            val historyUsage = if (remainingModifyCount > 0) {
                getAppHistoryUsageUseCase(packageName)
            } else null

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

                val capsuleTargetPos = CapsuleTargetPosition(
                    x = 0f,
                    y = 160f
                )

                val currentThemeId = themeIdOverride ?: appPreferences.getInterceptThemeId()

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
                        currentWeeklyLimitMinutes = currentLimit?.weeklyLimitMinutes ?: 0,
                        historyUsage = historyUsage,
                        themeId = currentThemeId,
                        onReset = if (remainingModifyCount > 0 && onReset != null) {
                            { newDaily, newWeekly ->
                                removeInterceptViewInternal()
                                isInterceptVisible.set(false)
                                onReset(newDaily, newWeekly)
                            }
                        } else null,
                        onContinue = { purpose ->
                            removeInterceptViewInternal()
                            onContinue(purpose)
                        },
                        onDismiss = {
                            // 先执行 onDismiss（pressHomeButton），让被拦截 App 先退出到后台，
                            // 再延迟移除拦截页 View，确保用户在视觉上不会看到 App 界面
                            isInterceptVisible.set(false)
                            onDismiss()
                            mainHandler.postDelayed({
                                removeInterceptViewInternal()
                            }, 400)
                        }
                    )
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    flags = flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                    flags = flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
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
     * 显示达到时限的浮窗。
     */
    fun showLimitReached(
        packageName: String,
        onDismiss: () -> Unit,
        onReset: ((newDailyMinutes: Int, newWeeklyMinutes: Int) -> Unit)? = null
    ) {
        isInterceptVisible.set(true)
        scope.launch {
            val now = System.currentTimeMillis()
            val todayUsed = usageRecordRepository.getDailyUsageSeconds(packageName, now)
            val remainingModifyCount = appLimitRepository.getRemainingModifyCount(packageName)
            val currentLimit = appLimitRepository.getAppLimit(packageName)
            val historyUsage = if (remainingModifyCount > 0) {
                getAppHistoryUsageUseCase(packageName)
            } else null
            val currentThemeId = appPreferences.getInterceptThemeId()

            mainHandler.post {
                // 如果在异步加载数据期间已被取消，放弃展示
                if (!isInterceptVisible.get()) return@post
                removeInterceptViewInternal()

                val composeView = createComposeView {
                    LimitReachedOverlayScreen(
                        todayUsedSeconds = todayUsed,
                        remainingModifyCount = remainingModifyCount,
                        currentDailyLimitMinutes = currentLimit?.dailyLimitMinutes ?: 60,
                        currentWeeklyLimitMinutes = currentLimit?.weeklyLimitMinutes ?: 0,
                        historyUsage = historyUsage,
                        themeId = currentThemeId,
                        onReset = if (remainingModifyCount > 0 && onReset != null) {
                            { newDaily, newWeekly ->
                                removeInterceptViewInternal()
                                isInterceptVisible.set(false)
                                onReset(newDaily, newWeekly)
                            }
                        } else null,
                        onDismiss = {
                            // 先执行 onDismiss（pressHomeButton），让被拦截 App 先退出到后台，
                            // 再延迟移除拦截页 View，确保用户在视觉上不会看到 App 界面
                            isInterceptVisible.set(false)
                            onDismiss()
                            mainHandler.postDelayed({
                                removeInterceptViewInternal()
                            }, 400)
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
    }

    /**
     * 显示小胶囊浮窗，同时清除"拦截展示中"标志
     */
    fun showCapsule(session: UsageSession, playEnterAnimation: Boolean = true) {
        isInterceptVisible.set(false)
        mainHandler.post {
            removeCapsuleViewInternal()
            removeCeremonyViewInternal()

            capsuleAppName.value = session.appName
            capsuleAppPackageName.value = session.packageName
            capsuleSessionSeconds.value = 0L
            capsuleDailyRemainingSeconds.value = session.dailyRemainingSeconds
            capsuleDailyLimitSeconds.value = session.dailyLimitSeconds
            capsulePurpose.value = session.purpose
            capsuleIsPaused.value = false  // 显示时默认非暂停

            capsuleWakeUp = null

            val hasCeremony = playEnterAnimation && !session.purpose.isNullOrBlank()
            val currentThemeId = appPreferences.getInterceptThemeId()

            if (hasCeremony) {
                val ceremonyComposeView = createComposeView {
                    CeremonyOverlayView(
                        purposeText = session.purpose ?: "",
                        themeId = currentThemeId,
                        onFinished = {
                            mainHandler.post {
                                removeCeremonyViewInternal()
                                addCapsuleView(session, playEnterAnimation = true)
                            }
                        }
                    )
                }

                val ceremonyParams = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                }

                try {
                    windowManager.addView(ceremonyComposeView, ceremonyParams)
                    ceremonyView = ceremonyComposeView
                    startCapsuleTimer(session)
                } catch (e: Exception) {
                    e.printStackTrace()
                    addCapsuleView(session, playEnterAnimation = playEnterAnimation)
                }
            } else {
                addCapsuleView(session, playEnterAnimation = playEnterAnimation)
            }
        }
    }

    /**
     * 将胶囊切换到暂停状态：
     * - 光盘停止旋转
     * - 显示"已暂停"文字
     * - 点击胶囊可回到app
     *
     * 注意：此方法不移除胶囊View，只改变状态。
     *
     * @param returnToAppAction 点击胶囊时执行的"回到app"动作
     */
    fun pauseCapsule(returnToAppAction: () -> Unit) {
        mainHandler.post {
            capsuleIsPaused.value = true
            returnToAppCallback = returnToAppAction
        }
    }

    /** 恢复胶囊到活跃状态（app回到前台时调用） */
    fun resumeCapsule() {
        mainHandler.post {
            capsuleIsPaused.value = false
            returnToAppCallback = null
        }
    }

    /** 点击暂停胶囊时回到app的回调 */
    private var returnToAppCallback: (() -> Unit)? = null

    /**
     * 创建并添加普通胶囊 View 到 WindowManager。
     */
    private fun addCapsuleView(session: UsageSession, playEnterAnimation: Boolean) {
        val currentThemeId = appPreferences.getInterceptThemeId()
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
                themeId = currentThemeId,
                onToggleExpand = {
                    if (capsuleIsPaused.value) {
                        // 暂停状态下点击：回到app
                        returnToAppCallback?.invoke()
                    } else {
                        capsuleExpanded.value = !capsuleExpanded.value
                    }
                },
                onEndSession = {
                    removeCapsuleViewInternal()
                    scope.launch {
                        sessionManager.endSession(UsageRecordEntity.EndReason.MANUAL)
                    }
                    onManualEndSession?.invoke()
                },
                onReturnToApp = {
                    returnToAppCallback?.invoke()
                },
                onRegisterWakeUp = { fn -> capsuleWakeUp = fn },
                onRegisterShowConfirm = { fn -> capsuleShowConfirm = fn },
                onConfirmDialogOpen = {
                    // 确认弹窗打开：暂停计时（仅前台场景，app 仍在前台但用户在考虑是否结束）
                    scope.launch { sessionManager.onAppGoBackground() }
                },
                onConfirmDialogClose = {
                    // 确认弹窗关闭（取消）：恢复计时
                    sessionManager.onAppReturnToForeground()
                },
                playEnterAnimation = playEnterAnimation
            )
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 160
        }

        setupDragAndDrop(composeView, params) {
            if (capsuleIsPaused.value) {
                // 暂停态点击：回到app
                returnToAppCallback?.invoke()
            } else {
                capsuleExpanded.value = !capsuleExpanded.value
            }
        }

        try {
            windowManager.addView(composeView, params)
            capsuleView = composeView
            if (capsuleUpdateJob == null || capsuleUpdateJob?.isActive == false) {
                startCapsuleTimer(session)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startCapsuleTimer(session: UsageSession) {
        capsuleUpdateJob?.cancel()
        capsuleSession = session
        capsuleUpdateJob = scope.launch {
            while (true) {
                val s = capsuleSession
                if (s != null) {
                    val activeSeconds = s.currentSessionSeconds
                    capsuleSessionSeconds.value = activeSeconds
                    capsuleDailyRemainingSeconds.value =
                        (s.dailyLimitSeconds - s.dailyUsedSeconds - activeSeconds).coerceAtLeast(0)
                }
                delay(1000L)
            }
        }
    }

    private fun setupDragAndDrop(view: View, params: WindowManager.LayoutParams, onClick: () -> Unit) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    capsuleWakeUp?.invoke()
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (isDragging || Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isDragging = true
                        params.x = initialX + dx
                        params.y = initialY + dy
                        try {
                            windowManager.updateViewLayout(v, params)
                        } catch (e: Exception) { /* ignore */ }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        onClick()
                    } else {
                        snapToEdge(v, params)
                    }
                    isDragging = false
                    true
                }
                else -> false
            }
        }
    }

    private fun snapToEdge(view: View, params: WindowManager.LayoutParams) {
        val screenWidth = windowManager.currentWindowMetrics.bounds.width()
        val viewWidth = view.width.takeIf { it > 0 } ?: 200
        val centerX = params.x + viewWidth / 2
        val targetX = if (centerX < screenWidth / 2) 0 else screenWidth - viewWidth

        val startX = params.x
        if (startX == targetX) return

        val distance = Math.abs(targetX - startX)
        val duration = (distance / 100f * 40f).toLong().coerceIn(80L, 260L)

        ValueAnimator.ofInt(startX, targetX).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { animator ->
                params.x = animator.animatedValue as Int
                try {
                    windowManager.updateViewLayout(view, params)
                } catch (e: Exception) { cancel() }
            }
            start()
        }
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
        capsuleIsPaused.value = false
        returnToAppCallback = null
        capsuleView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) { }
            capsuleView = null
        }
    }

    fun dismissIntercept() {
        isInterceptVisible.set(false)
        mainHandler.post { removeInterceptViewInternal() }
    }

    fun dismissCapsule() {
        capsuleUpdateJob?.cancel()
        mainHandler.post { removeCapsuleViewInternal() }
    }

    /**
     * 后台超时时触发胶囊内的「结束确认弹窗」。
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

    fun dismissAll() {
        isInterceptVisible.set(false)
        capsuleUpdateJob?.cancel()
        mainHandler.post {
            removeInterceptViewInternal()
            removeCeremonyViewInternal()
            removeCapsuleViewInternal()
        }
    }

    /** 创建一个能承载 Compose 内容的 ComposeView，并正确设置 Lifecycle */
    private fun createComposeView(content: @androidx.compose.runtime.Composable () -> Unit): ComposeView {
        val lifecycleOwner = OverlayLifecycleOwner()
        lifecycleOwner.start()

        return ComposeView(context).apply {
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
