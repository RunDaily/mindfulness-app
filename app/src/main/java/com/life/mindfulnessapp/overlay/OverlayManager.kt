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

    /**
     * 由外部（Service）注册：当用户手动结束了一次「有目的的使用」时回调。
     * 参数为刚结束的 recordId，用于打开 App 并引导用户记录感受。
     */
    var onManualEndWithPurpose: ((recordId: Long) -> Unit)? = null

    private var interceptView: View? = null
    private var adView: View? = null          // 广告页浮窗（非 VIP 超限时插入）
    /** 广告被外部强制关闭（如用户按 Home 键）时置为 true，防止 onAdFinished 误触发超限页 */
    private var adCancelled: Boolean = false
    private var capsuleView: View? = null
    private var ceremonyView: View? = null   // 仪式感动画专属浮窗（居中、全屏透明）
    private var dismissCeremonyView: View? = null  // 退出仪式浮窗（"离开仪式"）

    /**
     * 退出仪式冷却：记录每个 App 上次「完整播放勋章动画」的时间戳（ms）。
     *
     * 设计原则：
     * - 冷却窗口内（2分钟）再次退出 → 跳过全屏动画，直接 pressHome，
     *   避免「刷」勋章和仪式变成噪音；
     * - 冷却计时从「上次完整仪式播放完毕」开始，而非从用户点击时开始。
     */
    private val dismissCeremonyCooldownMs = 2 * 60 * 1000L   // 2 分钟
    private val lastDismissCeremonyTime = mutableMapOf<String, Long>()  // packageName → timestamp

    private var capsuleUpdateJob: Job? = null
    private var capsuleSession: UsageSession? = null

    /** Compose 层注册的唤醒回调：触摸时调用，让胶囊从休眠态弹回活跃态 */
    private var capsuleWakeUp: (() -> Unit)? = null

    /** Compose 层注册的「显示结束确认弹窗」回调：后台超时时由 Service 调用，弹出确认弹窗 */
    private var capsuleShowConfirm: (() -> Unit)? = null

    /** Compose 层注册的「5分钟预警」回调：剩余恰好低于5分钟时触发一次 */
    private var capsuleWarnFiveMin: (() -> Unit)? = null

    /** Compose 层注册的「1分钟倒计时」回调：剩余恰好低于1分钟时触发，切换胶囊为倒计时形态 */
    private var capsuleStartCountdown: (() -> Unit)? = null

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
        onReset: (() -> Unit)? = null,
        themeIdOverride: String? = null
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
            // 获取系统数据：app 今日实际使用时长 + 全局屏幕时长

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
                val dailyLimitSeconds = currentLimit?.dailyLimitMinutes?.times(60L) ?: 0L
                val weeklyLimitSeconds = currentLimit?.weeklyLimitMinutes?.times(60L) ?: 0L
                val isAlreadyOverLimit = (dailyLimitSeconds > 0 && todayUsedSeconds >= dailyLimitSeconds)
                        || (weeklyLimitSeconds > 0 && weekUsedSeconds >= weeklyLimitSeconds)

                if (isAlreadyOverLimit) {
                    // 非 VIP：先插播广告，广告结束后展示超限页
                    // VIP：直接展示超限页（跳过广告）
                    // 两种路径最终都走 showLimitReachedInternal，不经过普通拦截页，
                    // 确保超限用户无法通过任何路径点到「继续使用」按钮。
                    if (!appPreferences.isVipActive()) {
                        showAdOverlay(packageName = packageName, onAdFinished = {
                            if (isInterceptVisible.get()) {
                                showLimitReachedInternal(packageName, onDismiss, onReset)
                            }
                        })
                    } else {
                        showLimitReachedInternal(packageName, onDismiss, onReset)
                    }
                    return@post
                }

                showInterceptInternal(
                    packageName, appName, dailyLimitMinutes, weeklyLimitMinutes,
                    todayUsedSeconds, weekUsedSeconds, todayRecords,
                    remainingModifyCount, themeIdOverride, onReset, onContinue, onDismiss
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
        themeIdOverride: String?,
        onReset: (() -> Unit)?,
        onContinue: (purpose: String?) -> Unit,
        onDismiss: () -> Unit
    ) {
        val capsuleTargetPos = CapsuleTargetPosition(x = 0f, y = 160f)
        val currentThemeId = themeIdOverride ?: appPreferences.getInterceptThemeId()

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

        // 禅/仪表盘等模式的 reset 回调（无内部弹窗，直接跳转设置页）
        val resetCallbackSimple: (() -> Unit)? =
            if (remainingModifyCount > 0 && onReset != null) {
                {
                    removeInterceptViewInternal()
                    isInterceptVisible.set(false)
                    onReset()
                }
            } else null

        val isDarkTheme = appPreferences.isDarkThemeEnabled()

        // ── 退出仪式回调（先展示"离开仪式"胶囊动画，动画结束后再执行真正的离开）──
        // 通用于禅模式和普通模式的 onDismiss 处理：
        //   1. 标记 isInterceptVisible = false（用户已做决定）
        //   2. 展示退出仪式浮窗（叠在拦截页上方，无缝衔接）
        //   3. 退出仪式弹出后（约 300ms），移除全屏拦截页（拦截页在仪式背后淡出）
        //   4. 仪式动画全部结束 → 执行 onDismiss（实际 pressHome）
        val dismissWithCeremony: () -> Unit = {
            isInterceptVisible.set(false)
            // 先展示退出仪式（叠加在拦截页上方，无缝过渡）
            showDismissCeremony(
                packageName = packageName,
                themeId = currentThemeId,
                onDismissCompleted = { onDismiss() }
            )
            // 稍后再移除拦截页（等退出仪式胶囊动画弹出后，此时用户只看到仪式胶囊）
            mainHandler.postDelayed({ removeInterceptViewInternal() }, 350)
            Unit
        }

        val composeView = createComposeView {
            if (currentThemeId == "zen") {
                // 禅模式：使用极简全屏拦截页（无内部调整弹窗，点击直接跳转设置页）
                ZenInterceptOverlayScreen(
                    appName = appName,
                    todayUsedSeconds = todayUsedSeconds,
                    dailyLimitMinutes = dailyLimitMinutes,
                    remainingModifyCount = remainingModifyCount,
                    onReset = resetCallbackSimple,
                    onContinue = { purpose ->
                        removeInterceptViewInternal()
                        onContinue(purpose)
                    },
                    onDismiss = dismissWithCeremony
                )
            } else {
                // simple / default 等主题：统一走 InterceptOverlayScreen
                // 内部有 ResetLimitDialog 弹窗，携带用户选择的新时长
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
                    themeId = currentThemeId,
                    isDarkTheme = isDarkTheme,
                    onReset = resetCallbackWithValue,
                    onContinue = { purpose ->
                        removeInterceptViewInternal()
                        onContinue(purpose)
                    },
                    onDismiss = dismissWithCeremony
                )
            }
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
                showLimitReachedInternal(packageName, onDismiss, onReset, onContinueOverLimit)
            })
            return
        }

        showLimitReachedInternal(packageName, onDismiss, onReset, onContinueOverLimit)
    }

    /**
     * 真正执行「显示超限浮窗」的内部方法，供 VIP 路径和广告结束后的回调共用。
     */
    private fun showLimitReachedInternal(
        packageName: String,
        onDismiss: () -> Unit,
        onReset: (() -> Unit)? = null,
        onContinueOverLimit: (() -> Unit)? = null
    ) {
        scope.launch {
            val now = System.currentTimeMillis()
            val todayUsed = usageRecordRepository.getDailyUsageSeconds(packageName, now)
            val remainingModifyCount = appLimitRepository.getRemainingModifyCount(packageName)
            val currentThemeId = appPreferences.getInterceptThemeId()

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
                    if (currentThemeId == "zen") {
                        // 禅模式：极简超限页
                        ZenLimitReachedOverlayScreen(
                            todayUsedSeconds = todayUsed,
                            remainingModifyCount = remainingModifyCount,
                            onReset = limitResetCallback,
                            onDismiss = {
                                isInterceptVisible.set(false)
                                onDismiss()
                                mainHandler.postDelayed({ removeInterceptViewInternal() }, 600)
                            }
                        )
                    } else {
                        // simple / default 等主题：统一走 LimitReachedOverlayScreen
                        LimitReachedOverlayScreen(
                            todayUsedSeconds = todayUsed,
                            remainingModifyCount = remainingModifyCount,
                            themeId = currentThemeId,
                            isDarkTheme = isDarkThemeLimit,
                            onReset = limitResetCallback,
                            onDismiss = {
                                // 先执行 onDismiss（pressHomeButton），让被拦截 App 先退出到后台，
                                // 再延迟移除拦截页 View，确保用户在视觉上不会看到 App 界面
                                isInterceptVisible.set(false)
                                onDismiss()
                                mainHandler.postDelayed({ removeInterceptViewInternal() }, 600)
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
                onEndSession = { effectScore, note ->
                    // 在结束前先读取 recordId，endSession 会清空 session
                    val endingRecordId = sessionManager.currentSession.value?.recordId
                    removeCapsuleViewInternal()
                    scope.launch {
                        sessionManager.endSession(
                            reason = UsageRecordEntity.EndReason.MANUAL,
                            note = note,
                            effectScore = effectScore
                        )
                        // 手动结束后，无论是否有目的，都触发 HomeScreen 高亮引导
                        if (endingRecordId != null) {
                            onManualEndWithPurpose?.invoke(endingRecordId)
                        }
                    }
                    onManualEndSession?.invoke()
                },
                onReturnToApp = {
                    returnToAppCallback?.invoke()
                },
                onRegisterWakeUp = { fn -> capsuleWakeUp = fn },
                onRegisterShowConfirm = { fn -> capsuleShowConfirm = fn },
                onRegisterWarnFiveMin = { fn -> capsuleWarnFiveMin = fn },
                onRegisterStartCountdown = { fn -> capsuleStartCountdown = fn },
                onExtendLimit = { extraMinutes ->
                    scope.launch { sessionManager.extendDailyLimit(extraMinutes) }
                },
                onConfirmDialogOpen = {
                    // 确认弹窗打开：暂停计时（仅前台场景，app 仍在前台但用户在考虑是否结束）
                    scope.launch { sessionManager.onAppGoBackground() }
                    // 切换 Window 为可聚焦模式，让备注 TextField 能获得输入焦点（弹出软键盘）
                    mainHandler.post {
                        capsuleView?.let { view ->
                            try {
                                val lp = view.layoutParams as? WindowManager.LayoutParams ?: return@post
                                lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                                windowManager.updateViewLayout(view, lp)
                            } catch (_: Exception) {}
                        }
                    }
                },
                onConfirmDialogClose = {
                    // 确认弹窗关闭（取消）：恢复计时
                    sessionManager.onAppReturnToForeground()
                    // 恢复 Window 为不可聚焦模式，让触摸事件穿透（拖动胶囊正常工作）
                    mainHandler.post {
                        capsuleView?.let { view ->
                            try {
                                val lp = view.layoutParams as? WindowManager.LayoutParams ?: return@post
                                lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                windowManager.updateViewLayout(view, lp)
                            } catch (_: Exception) {}
                        }
                    }
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
                    val remaining = (s.dailyLimitSeconds - s.dailyUsedSeconds - activeSeconds).coerceAtLeast(0)
                    capsuleDailyRemainingSeconds.value = remaining
                    capsuleDailyLimitSeconds.value = s.dailyLimitSeconds

                    // ── 预警触发（仅在有限制且非超限续记 session 时生效）──────────
                    if (s.dailyLimitSeconds > 0 && !s.isOverLimitSession) {
                        // 5 分钟预警：剩余首次降至 300 秒以下时触发
                        if (!fiveMinWarned && remaining in 1L..300L) {
                            fiveMinWarned = true
                            mainHandler.post { capsuleWarnFiveMin?.invoke() }
                        }
                        // 1 分钟倒计时：剩余首次降至 60 秒以下时触发
                        if (!countdownStarted && remaining in 1L..60L) {
                            countdownStarted = true
                            mainHandler.post { capsuleStartCountdown?.invoke() }
                        }
                        // 延长后剩余时间重新超过 60 秒：重置倒计时标记，允许下次再次触发
                        if (countdownStarted && remaining > 60L) {
                            countdownStarted = false
                        }
                        // 延长后剩余时间重新超过 300 秒：重置5分钟预警标记
                        if (fiveMinWarned && remaining > 300L) {
                            fiveMinWarned = false
                        }
                    }
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
        capsuleWarnFiveMin = null
        capsuleStartCountdown = null
        capsuleIsPaused.value = false
        returnToAppCallback = null
        capsuleView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) { }
            capsuleView = null
        }
    }

    fun dismissIntercept() {
        isInterceptVisible.set(false)
        interceptTargetPackage = null
        mainHandler.post {
            removeAdViewInternal(cancel = true)  // 用户主动离开，强制取消广告，不再展示超限页
            removeInterceptViewInternal()
        }
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

    /**
     * 展示退出仪式浮窗（「获得勋章」全屏动画）。
     *
     * 在用户点击「先不进去了」后调用：
     * 1. 若该 App 在 2 分钟冷却窗口内再次触发，跳过动画直接执行 [onDismissCompleted]，
     *    防止用户「刷」勋章 / 仪式动画变成噪音；
     * 2. 否则异步查询今日累计克制次数，展示完整全屏勋章动画（约 2s）；
     * 3. 动画播放完毕后更新冷却时间戳，再调用 [onDismissCompleted]（即 pressHome）。
     *
     * @param packageName         被拦截的 App 包名，用于冷却判断
     * @param themeId             当前拦截主题 ID，联动配色
     * @param onDismissCompleted  仪式动画结束后执行的回调（通常是 pressHomeButton）
     */
    fun showDismissCeremony(
        packageName: String,
        themeId: String,
        onDismissCompleted: () -> Unit
    ) {
        // ── 冷却判断：2 分钟内同一 App 再次退出 → 跳过动画 ──────────────
        val now = System.currentTimeMillis()
        val lastTime = lastDismissCeremonyTime[packageName] ?: 0L
        if (now - lastTime < dismissCeremonyCooldownMs) {
            // 冷却期内：静默跳过，直接 pressHome
            onDismissCompleted()
            return
        }

        // ── 正常流程：展示完整勋章动画 ──────────────────────────────────
        // 先查今日克制次数，然后在主线程展示动画（不阻塞 UI）
        scope.launch {
            // 数据库记录写入是在 onDismiss 之后（Service 写入极短记录），
            // 这里先读当前值，展示的是"本次之前"的次数+1（乐观显示）
            val countBefore = try {
                usageRecordRepository.getDayDismissCount()
            } catch (_: Exception) { 0 }
            val displayCount = countBefore + 1   // 本次也算进去

            mainHandler.post {
                removeDismissCeremonyViewInternal()

                val ceremonyComposeView = createComposeView {
                    DismissCeremonyOverlayView(
                        themeId = themeId,
                        dismissCount = displayCount,
                        onFinished = {
                            mainHandler.post {
                                removeDismissCeremonyViewInternal()
                                // 动画完整播放完毕 → 记录冷却时间戳
                                lastDismissCeremonyTime[packageName] = System.currentTimeMillis()
                                onDismissCompleted()
                            }
                        }
                    )
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
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
                    // 展示失败时直接执行 pressHome，不卡住流程
                    onDismissCompleted()
                }
            }
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
