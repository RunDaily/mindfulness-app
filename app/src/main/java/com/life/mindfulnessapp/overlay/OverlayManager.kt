package com.life.mindfulnessapp.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnAttach
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.life.mindfulnessapp.BuildConfig
import com.life.mindfulnessapp.DualSpaceGateActivity
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.analytics.HaEvents
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.data.repository.AnalyticsRepository
import com.life.mindfulnessapp.data.repository.AppLimitRepository
import com.life.mindfulnessapp.data.repository.PlanBlockRepository
import com.life.mindfulnessapp.data.repository.SystemUsageRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository
import com.life.mindfulnessapp.display.DisplayGrayscaleController
import com.life.mindfulnessapp.domain.model.CapsuleCompanionPrefs
import com.life.mindfulnessapp.domain.model.CompanionAppMode
import com.life.mindfulnessapp.domain.model.CompanionBarForm
import com.life.mindfulnessapp.domain.model.CompanionPath
import com.life.mindfulnessapp.domain.model.CompanionScene
import com.life.mindfulnessapp.domain.model.DailyCapFacts
import com.life.mindfulnessapp.domain.model.DailyLimitGrayWashPolicy
import com.life.mindfulnessapp.domain.model.MidSessionCheckPolicy
import com.life.mindfulnessapp.domain.model.MonitorSuitability
import com.life.mindfulnessapp.domain.model.LeaveRitual
import com.life.mindfulnessapp.domain.model.PendingInterrupt
import com.life.mindfulnessapp.domain.model.ThemePack
import com.life.mindfulnessapp.domain.model.PositiveExitChoice
import com.life.mindfulnessapp.domain.model.PlanBlock
import com.life.mindfulnessapp.domain.model.ScheduleOrbPolicy
import com.life.mindfulnessapp.domain.model.SessionAwarenessCopy
import com.life.mindfulnessapp.domain.model.SessionAwarenessMode
import com.life.mindfulnessapp.domain.model.SessionLimitPolicy
import com.life.mindfulnessapp.domain.model.UsageRecordCounts
import com.life.mindfulnessapp.domain.model.UsageSession
import com.life.mindfulnessapp.domain.model.WalkAwarenessLevel
import com.life.mindfulnessapp.PlanAddAppActivity
import com.life.mindfulnessapp.domain.usecase.GetAppHistoryUsageUseCase
import com.life.mindfulnessapp.service.SessionCompareReminderWorker
import com.life.mindfulnessapp.service.SessionManager
import com.life.mindfulnessapp.service.MonitorForegroundService
import android.provider.Settings
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
    private val systemUsageRepository: SystemUsageRepository,
    private val appLimitRepository: AppLimitRepository,
    private val planBlockRepository: PlanBlockRepository,
    private val getAppHistoryUsageUseCase: GetAppHistoryUsageUseCase,
    private val sessionManager: SessionManager,
    private val appPreferences: com.life.mindfulnessapp.data.AppPreferences,
    private val pendingInterruptStore: com.life.mindfulnessapp.data.PendingInterruptStore,
    private val impulseStore: com.life.mindfulnessapp.data.ImpulseStore,
    private val displayGrayscaleController: DisplayGrayscaleController,
    private val analyticsRepository: AnalyticsRepository
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val TAG = "OverlayManager"
    /** 与 [InterceptOverlayScreen] 离开退场时长对齐：App 已切走后再卸层 */
    private val interceptLeaveDismissMs = 180L

    /**
     * 已从拦截门播过入场的会话 recordId。
     * 只有门口进入显式请求才播；锁屏重建 / 回前台 / 重试一律不播。
     */
    private val enterAnimPlayedRecordIds = LinkedHashSet<Long>()

    /**
     * 入场动效：仅「拦截页进入」那一次。
     * - true：门口进入请求播（同 recordId 仍只播一次）
     * - false / null：不播
     */
    private fun resolveCapsuleEnterAnimation(recordId: Long, override: Boolean?): Boolean {
        if (override != true) return false
        if (recordId > 0L && enterAnimPlayedRecordIds.contains(recordId)) return false
        claimCapsuleEnterAnim(recordId)
        return true
    }

    private fun claimCapsuleEnterAnim(recordId: Long) {
        if (recordId <= 0L) return
        enterAnimPlayedRecordIds.add(recordId)
        while (enterAnimPlayedRecordIds.size > 48) {
            val first = enterAnimPlayedRecordIds.firstOrNull() ?: break
            enterAnimPlayedRecordIds.remove(first)
        }
    }

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

    /**
     * 结束胶囊 / 离开目标 App 时立刻回桌面（不要等落库）。
     * 未对照的主动结束也必须走这里，否则目标 App 会留在前台。
     */
    var onLeaveTargetToHome: ((packageName: String) -> Unit)? = null

    /** 拦截页收到系统返回键：按守住离开 */
    var onInterceptBackPressed: (() -> Unit)? = null

    /**
     * Compose 拦截页二级页优先消费返回键。
     * 返回 true 表示已回到拦截首页（或上一级），勿再走守住离开。
     */
    @Volatile
    var onInterceptComposeBack: (() -> Boolean)? = null

    /** 拦截页收到最近任务键：先卸层露出系统进程列表 */
    var onInterceptRecentsPressed: (() -> Unit)? = null

    /** 拦截层失焦（可能是最近任务手势）：交给 Service 判定 */
    var onInterceptLostFocus: (() -> Unit)? = null

    /** 拦截层真正拿到窗口焦点：进程列表会话结束，门已盖稳 */
    var onInterceptGainedFocus: (() -> Unit)? = null

    /** 占位层盖上后：把目标 App 推回桌面，这段不计入系统使用时长 */
    var onInterceptCoverPrepared: ((packageName: String) -> Unit)? = null

    /**
     * 拦截层真正从 WindowManager 卸下。
     * 占位→正式页的替换不走这里；Service 用来结束「盖层压播放」。
     */
    var onInterceptLayerRemoved: (() -> Unit)? = null

    /** 主动离开后轻条：打开正向 App */
    var onLaunchPositiveApp: ((packageName: String) -> Unit)? = null

    /**
     * 门上正向出口（去做了）。
     * Overlay 卸门并播轻条后由 Service 记 GATE_POSITIVE_EXIT，再按需拉起目标 App。
     */
    var onPositiveExit: ((packageName: String, choice: PositiveExitChoice) -> Unit)? = null

    /** 主动离开后轻条：打开「想去的地方」配置页 */
    var onOpenPositiveDestinationSettings: (() -> Unit)? = null

    /** 离开轻条「详细」：打开该 App 的记录页；recordId>0 高亮该条，0 高亮今日最新 */
    var onOpenAppHistory: ((packageName: String, recordId: Long) -> Unit)? = null

    /** 拦截页「打开监控配置」→ App 监控详情 */
    var onOpenAppLimitEdit: ((packageName: String) -> Unit)? = null

    /** 写意图页「调整」快捷标签 → 管理页 */
    var onOpenQuickIntentTags: ((packageName: String, appName: String) -> Unit)? = null

    /**
     * 离开倒计时归零横条：条内完成轻量对照 → 闭环。
     * Service 负责写入档位/备注、清可续。
     */
    var onAwayEndedCompareSaved: (
        (recordId: Long, packageName: String, level: Int, note: String?, driftSeconds: Long?) -> Unit
    )? = null

    /**
     * 离开倒计时归零横条：环尽未点 / 展开后点「先不了」→ 保持未闭环。
     * Service 可补发「可续」静默通知。
     */
    var onAwayEndedDismissedWithoutCompare: ((recordId: Long, packageName: String) -> Unit)? = null

    /** 回顾横条挂接失败（解锁瞬间系统常拒绝 overlay）→ Service 稍后重试 */
    var onAwayEndedBarAttachFailed: (() -> Unit)? = null

    /** 桌面岛「打开心锚」→ Service 拉起主界面 */
    var onOpenHeartAnchorFromDesktop: (() -> Unit)? = null

    /** 桌面 Hub → 使用日志页 */
    var onOpenUsageLogFromDesktop: (() -> Unit)? = null

    /** 桌面 Hub / 到场岛 → 日程表 Tab */
    var onOpenScheduleFromDesktop: (() -> Unit)? = null

    /** 使用中系统账 Pulse → 方案添加（可带预选包名） */
    var onOpenPlanAddFromDesktop: ((packageName: String) -> Unit)? = null

    /** 由外部（Service）注册：胶囊临近结束时用户确认续时 */
    var onExtendSession: ((extraMinutes: Int) -> Unit)? = null

    /** 意图命中屏蔽词（一次命中一次回调） */
    var onKeywordBlocked: ((packageName: String, appName: String) -> Unit)? = null

    /**
     * 当前意图门草稿是否非空。门外离开时由 Service 读取并写入 gate_hold.had_draft_purpose。
     */
    @Volatile
    var interceptHadDraftPurpose: Boolean = false
        private set

    /** 点离开瞬间快照，避免仪式拆层后 Compose dispose 清掉草稿标记 */
    @Volatile
    private var pendingGateHoldHadDraft: Boolean = false

    /** 读取并清零意图草稿标记（含离开瞬间快照） */
    fun consumeInterceptHadDraftPurpose(): Boolean {
        val had = pendingGateHoldHadDraft || interceptHadDraftPurpose
        pendingGateHoldHadDraft = false
        interceptHadDraftPurpose = false
        return had
    }

    /** Home 切走等路径：在拆层前先快照草稿，供 gate_hold 上报 */
    fun snapshotGateHoldDraftForAnalytics() {
        pendingGateHoldHadDraft = interceptHadDraftPurpose
    }

    private var interceptView: View? = null
    private var interceptParams: WindowManager.LayoutParams? = null
    private var interceptLayerRefreshJob: Job? = null
    private var adView: View? = null          // 广告页浮窗（非 VIP 超限时插入）
    /** 广告被外部强制关闭（如用户按 Home 键）时置为 true，防止 onAdFinished 误触发超限页 */
    private var adCancelled: Boolean = false
    private var capsuleView: View? = null
    private var capsuleParams: WindowManager.LayoutParams? = null
    /** 独立意图跑道（不占用胶囊/灵动岛壳） */
    private var intentRunwayView: View? = null
    private var capsuleRequestCheckFocus: (() -> Unit)? = null
    /** 面板轻问作答写入胶囊运行态；返回是否已接入当前胶囊 */
    private var capsuleApplyExternalAwareness: ((still: Boolean) -> Boolean)? = null
    /** 贴条锚提示占用的顶部高度；窗口 y 已上移同等像素，保陪伴条视觉位置 */
    private var capsuleAttachedTopInsetPx: Int = 0
    /**
     * 开面板时会 remove+add 把条置顶；此时 LayoutChange 的 clamp 会把 y 微微顶上去。
     * 在此时间戳前跳过 clamp，钉住原位。
     */
    private var suppressCapsuleLayoutClampUntilElapsed: Long = 0L
    /** 拖拽引导层（先于胶囊 add，保证在胶囊下方） */
    private var dockGuideView: View? = null
    private var dockGuideParams: WindowManager.LayoutParams? = null
    private val dockGuideVisible = mutableStateOf(false)
    private val dockGuideHighlight = mutableStateOf<String?>(null)
    private val dockGuideBandTopY = mutableStateOf(0)
    private val dockGuideRowPitch = mutableStateOf(0)
    /**
     * 胶囊内结束确认 / 续时弹窗进行中。
     * 此时会故意把会话标成后台以冻结计时；监控循环若据此「回前台恢复」会拆掉弹窗。
     */
    val isCapsuleDialogBlocking = AtomicBoolean(false)
    private var capsuleSnapAnimator: ValueAnimator? = null
    /**
     * 收起形变未落定：壳仍接近灵动岛宽幅，必须禁止拖拽，
     * 否则会出现「展开态还能拖」的错位体验。
     */
    private var capsuleCollapseSettling: Boolean = false
    /**
     * 圆球入场已落定。限额预警自动展开须等此标志，
     * 否则会与入场仪式抢态。
     */
    private var capsuleMiniSettled: Boolean = false
    /** 入场未完成时积压的限额自动展开 */
    private var pendingLimitAutoExpand: (() -> Unit)? = null
    /** 记忆的自由悬浮坐标（CENTER_HORIZONTAL 下的 x 偏移 + 距顶 y） */
    private var capsuleFloatOffsetX: Int = 0
    private var capsuleFloatOffsetY: Int = 0
    /** 收起态实测壳尺寸；展开中仍用这份，避免岛宽把悬浮 X 夹死 */
    private var capsuleCollapsedMeasuredW: Int = 0
    private var capsuleCollapsedMeasuredH: Int = 0
    private var capsuleFloatDragging: Boolean = false
    /**
     * 灵动岛记忆坐标（与悬浮各自独立）。
     * 展开时回到这里；默认顶栏居中。不与悬浮坐标互相覆盖。
     */
    private var capsuleIslandOffsetX: Int = 0
    private var capsuleIslandOffsetY: Int = 0
    /** 跑马灯 / 对照 / 展开岛：窗口钉在顶部中央 */
    private var capsuleTopPinned: Boolean = false
    /** 形变落定：收起态回到记忆悬浮点；展开态归岛记忆点 */
    private val snapAfterCollapseRunnable = Runnable {
        capsuleCollapseSettling = false
        val view = capsuleView ?: return@Runnable
        val params = capsuleParams ?: return@Runnable
        if (!capsuleExpanded.value && !capsuleTopPinned) {
            restoreCapsuleFloatPosition(view, params, animate = true)
        } else {
            restoreCapsuleIslandPosition(view, params, animate = false)
        }
    }
    private val finishExpandAnchorRunnable = Runnable {
        val view = capsuleView ?: return@Runnable
        val params = capsuleParams ?: return@Runnable
        if (capsuleExpanded.value || capsuleTopPinned) {
            restoreCapsuleIslandPosition(view, params, animate = false)
        }
    }

    /** 灵动岛展开 / 入场 / 收起落定前 / 顶部钉住 / Session Hub：不可拖拽 */
    private fun isCapsuleDragLocked(): Boolean =
        !capsuleMiniSettled ||
            capsuleExpanded.value ||
            capsuleCollapseSettling ||
            capsuleTopPinned ||
            capsuleSkipEntrance != null ||
            capsuleEndDialogOpen ||
            intentSessionHub.value != null ||
            companionInspectOpen.value
    private var ceremonyView: View? = null   // 仪式感动画专属浮窗（居中、全屏透明）
    private var dismissCeremonyView: View? = null  // 退出仪式浮窗（"离开仪式"）
    @Volatile private var awayEndedBarShowing: Boolean = false
    private var dismissCeremonyParams: WindowManager.LayoutParams? = null

    // ── 步行觉察 · 路况锚点（独立于仪式/胶囊层）──────────────────────────────
    private var walkAwareView: View? = null
    private var walkAwareParams: WindowManager.LayoutParams? = null
    @Volatile private var walkAwareShowing: Boolean = false
    private val walkAwareLevelState = mutableStateOf(WalkAwarenessLevel.L0)
    private val walkAwareLabelState = mutableStateOf("")
    private var walkAwareHideAction: (() -> Unit)? = null

    // ── 桌面心锚微粒（监测开启时常驻；意图门会话期隐藏微粒，点陪伴条开同一套 Hub）────────
    private var desktopAnchorView: View? = null
    private var desktopAnchorParams: WindowManager.LayoutParams? = null
    private var desktopAnchorPanelView: View? = null
    private var desktopAnchorPanelParams: WindowManager.LayoutParams? = null
    private val desktopAnchorPanelOpen = mutableStateOf(false)
    private val desktopAnchorTopApps = mutableStateOf<List<DesktopAnchorTopApp>>(emptyList())
    private val desktopAnchorHourlySeconds = mutableStateOf(LongArray(24))
    private val desktopAnchorTodayTotalSeconds = mutableStateOf(0L)
    private val desktopAnchorTodayEnterCount = mutableStateOf(0)
    private val desktopAnchorTodayDismissCount = mutableStateOf(0)
    private val desktopAnchorActiveLock =
        mutableStateOf<ScheduleOrbPolicy.ActiveGlance?>(null)
    private val desktopAnchorInApp = mutableStateOf<DesktopAnchorInAppGlance?>(null)
    private val desktopAnchorPulse = mutableStateOf<DesktopAnchorPulseGlance?>(null)
    private val intentSessionHub = mutableStateOf<IntentSessionHubGlance?>(null)
    private var desktopAnchorPulseJob: Job? = null
    private var desktopAnchorPanelRemoveRunnable: Runnable? = null
    private var desktopAnchorFloatX: Int = 0
    private var desktopAnchorFloatY: Int = 0
    /** 拦截门 / 锁屏类场景强制隐藏（设置开关仍开） */
    private var desktopAnchorSceneHidden: Boolean = false
    /** 意图门胶囊期间隐藏桌面微粒（一体胶囊内嵌转圈图标） */
    private var desktopAnchorSessionHidden: Boolean = false
    private var desktopAnchorRefreshJob: Job? = null
    private var desktopAnchorInAppJob: Job? = null
    private var desktopAnchorSnapAnimator: ValueAnimator? = null
    private val desktopAnchorDragging = mutableStateOf(false)
    private val desktopAnchorWakeSeq = mutableStateOf(0)
    private val desktopAnchorOrbAnchor = mutableStateOf(DesktopAnchorOrbAnchor())
    private var desktopAnchorPanelBackHandler: (() -> Boolean)? = null
    @Volatile private var desktopAnchorDesiredVisible: Boolean = false
    /** 心锚自己在前台时不必再挂桌面小球 */
    @Volatile private var desktopAnchorHostAppHidden: Boolean = false

    // ── 使用中点开：陪伴小卡片（不是 Hub / 岛）─────────────────────────────
    private var companionInspectView: View? = null
    private var companionInspectParams: WindowManager.LayoutParams? = null
    private val companionInspectOpen = mutableStateOf(false)
    private val companionInspectGlance = mutableStateOf<CompanionInspectGlance?>(null)
    private var companionInspectRemoveRunnable: Runnable? = null

    // ── 日限额临近：系统真灰度（非整屏灰幕）──────────────────────────────────
    /** 避免无权限时每秒刷日志 */
    private var grayScaleMissingPermLogged: Boolean = false

    /**
     * 离开肯定冷却：记录每个 App 上次展示肯定（轻提示或全屏勋章）的时间戳（ms）。
     *
     * 设计原则（见 LeaveRitual / .design/leave-ritual-sketch.html）：
     * - 冷却内再离开 → 静默（门内极短淡出，无余韵/勋章）；
     * - 日常仪式在门内呼气完成，门外不挂轻条；
     * - 仅里程碑全屏勋章；冷却撞上则推迟到下次合格离开。
     */
    private val dismissCeremonyCooldownMs = LeaveRitual.COOLDOWN_MS
    private val lastDismissCeremonyTime = mutableMapOf<String, Long>()
    /** 冷却内吞掉的里程碑次数；下次合格离开补播 */
    private val deferredMilestoneCount = mutableMapOf<String, Int>()

    private var capsuleUpdateJob: Job? = null
    private var capsuleSession: UsageSession? = null
    /**
     * 入场动效开关（State）：门口进入时置 true，开播即清 false。
     * 避免开面板 restack（remove+add）导致 Compose 重建后用旧 Boolean 再播一遍。
     */
    private val capsulePlayEnterAnimation = mutableStateOf(false)
    private val capsuleSoftReveal = mutableStateOf(false)

    /**
     * 会话级觉察状态：锁屏 dismissAll 会拆 Compose，remember 会丢。
     * 按 recordId 保住「锚提示已播 / 轻问次数」。
     */
    private var capsuleAwarenessRecordId: Long = -1L
    private var capsuleEnterAnchorHintShown: Boolean = false
    private var capsuleBrowseNearEndHintShown: Boolean = false
    private var capsuleFocusChecksCompleted: Int = 0
    private var capsuleLastCheckAtSec: Long = 0L

    private fun syncCapsuleAwarenessForSession(recordId: Long) {
        if (recordId == capsuleAwarenessRecordId) return
        capsuleAwarenessRecordId = recordId
        capsuleEnterAnchorHintShown = false
        capsuleBrowseNearEndHintShown = false
        capsuleFocusChecksCompleted = 0
        capsuleLastCheckAtSec = 0L
    }

    private fun clearCapsuleAwarenessState() {
        capsuleAwarenessRecordId = -1L
        capsuleEnterAnchorHintShown = false
        capsuleBrowseNearEndHintShown = false
        capsuleFocusChecksCompleted = 0
        capsuleLastCheckAtSec = 0L
    }

    /** Compose 层注册的唤醒回调：触摸时调用，让胶囊从休眠态弹回活跃态 */
    private var capsuleWakeUp: (() -> Unit)? = null

    /** Compose 层注册的「显示结束确认弹窗」回调：后台超时时由 Service 调用，弹出确认弹窗 */
    private var capsuleShowConfirm: (() -> Unit)? = null

    /** 点「结束」：迷你态命中区未拦到时的 Window 层兜底 */
    private var capsuleRequestEnd: (() -> Unit)? = null

    /** 「结束」在屏幕上的命中区（含少量外扩），单位 px */
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

    /**
     * 用户刚在拦截页确认进入：拆页到胶囊挂上之间的空窗。
     * 此期间不得走「Home 离开」拆胶囊。
     */
    @Volatile var gateEnteringPackage: String? = null
        private set

    private var pendingCapsuleSession: UsageSession? = null
    private var pendingCapsulePlayEnter: Boolean = true
    private val ensureCapsuleVisibleRunnable = object : Runnable {
        override fun run() {
            val session = pendingCapsuleSession ?: return
            val live = sessionManager.currentSession.value
            // 已切后台：勿用活跃态重试冲掉暂停胶囊
            if (live != null &&
                live.packageName == session.packageName &&
                live.isInBackground
            ) {
                pendingCapsuleSession = null
                return
            }
            if (isSessionPresenceAttached(session)) {
                pendingCapsuleSession = null
                return
            }
            Log.w(TAG, "胶囊未挂上，重试 showCapsule ${session.packageName}")
            // 重试绝不重播进场：否则第一次失败后会再光点落成一次，观感「跳来跳去」
            showCapsuleNow(session, playEnterAnimation = false)
            if (isSessionPresenceAttached(session)) {
                pendingCapsuleSession = null
                pendingCapsulePlayEnter = false
                if (gateEnteringPackage == session.packageName) {
                    finishGateEnterDismiss(session.packageName)
                }
            } else {
                mainHandler.postDelayed(this, 280L)
            }
        }
    }

    /**
     * 当前全屏拦截层种类。Home 键策略按种类分流：
     * 意图门 → 关页并记守住；日限/时段锁 → 静默关页；意图时长到点 → 稍后回顾。
     */
    @Volatile var interceptKind: InterceptOverlayKind? = null
        private set

    /** 拦截 View 仍挂载时的种类快照，供息屏/屏保误拆后恢复。 */
    @Volatile private var attachedInterceptKind: InterceptOverlayKind? = null

    /** 递增以作废尚未执行的 [scheduleRemoveInterceptView]。 */
    private var interceptRemoveGeneration = 0

    /** 本次拦截层开始展示的墙钟时间，供 Home 推断排除「打开 App 时的桌面事件」。 */
    @Volatile var interceptShownAtWall: Long = 0L
        private set

    /** 静默重展拦截页时续上门时钟（熄屏前的首次出现时刻） */
    fun restoreInterceptShownAtWall(shownAtWall: Long) {
        if (shownAtWall > 0L) {
            interceptShownAtWall = shownAtWall
        }
    }

    /** 最近任务打开时卸层保留拦截态，回来再重挂 */
    @Volatile var interceptParkedForRecents: Boolean = false
        private set

    /** 当前拦截层内容是否为正式 Compose 门（false = 纯色占位） */
    @Volatile var interceptContentIsCompose: Boolean = false
        private set

    /**
     * 当前拦截页是否为日限触顶页。
     * Home 离开时用于走「时间到了」文案，避免误提示「守住了」。
     */
    @Volatile var interceptLimitTheme: Boolean = false
        private set

    // Capsule 状态（暴露给 Compose UI）
    val capsuleSessionSeconds = mutableStateOf(0L)
    val capsuleDailyRemainingSeconds = mutableStateOf(0L)
    val capsuleDailyLimitSeconds = mutableStateOf(0L)
    val capsuleAppName = mutableStateOf("")
    val capsuleAppPackageName = mutableStateOf("")  // app包名，用于加载图标
    val capsulePurpose = mutableStateOf<String?>(null)  // 用户在拦截页填写的使用目的
    val capsuleIntentKind = mutableStateOf<com.life.mindfulnessapp.domain.model.IntentKind?>(null)
    val capsuleExpanded = mutableStateOf(false)

    /**
     * 胶囊暂停状态：含意图门切到后台时为 true。
     * 暂停时点按主体回 App；仅时长锁切走不挂暂停胶囊。
     */
    val capsuleIsPaused = mutableStateOf(false)

    /** 是否为超限续记会话（视觉与普通限额会话区分） */
    val capsuleIsOverLimit = mutableStateOf(false)

    /** 当日触顶延长秒数（胶囊在限额后展示彩色 +N） */
    val capsuleDailyGraceBonusSeconds = mutableStateOf(0L)
    /** 配置的基础日限额秒（胶囊分母；与生效天花板 [capsuleDailyLimitSeconds] 区分） */
    val capsuleDailyBaseLimitSeconds = mutableStateOf(0L)

    /** 当前会话是否开启意图门 */
    val capsuleHasIntentGate = mutableStateOf(true)
    /** 当前会话是否启用对照（App 配置；仍须觉察练习总闸） */
    val capsuleCompareEnabled = mutableStateOf(true)
    /** 当前会话对照最低时长（分钟） */
    val capsuleCompareMinMinutes = mutableStateOf(10)
    /** 觉察练习总闸（发现认领；默认关） */
    val capsuleAwarenessPracticeEnabled = mutableStateOf(false)
    /** 中途轻问间隔秒 */
    val capsuleAwarenessPracticeGapSec = mutableStateOf(
        com.life.mindfulnessapp.domain.model.MidSessionCheckPolicy.GAP_NORMAL_SEC
    )

    /** 当前会话是否开启时长锁（含超限续记） */
    val capsuleHasTimeLock = mutableStateOf(true)
    val capsuleTodayEnterCount = mutableStateOf(0)
    val capsuleSessionOriginStartMs = mutableStateOf(0L)
    val capsuleTodayTotalSeconds = mutableStateOf(0L)
    /** 是否存在单次会话上限（驱动胶囊按会话预算预警） */
    val capsuleHasSessionLimit = mutableStateOf(false)
    /** 单次时长临近结束时是否还可续一次 */
    val capsuleCanExtend = mutableStateOf(false)
    /** 本次会话基础时长上限（分钟，不含续时）；0 = 无单次上限 */
    val capsuleSessionLimitMinutes = mutableStateOf(0)
    /** 本会话是否开启意图回顾 */
    /** 纯时长锁迷你态：已用侧是否显示到秒 */
    val capsuleShowUsedSeconds = mutableStateOf(false)
    /** true = 紧凑迷你；false = 标准迷你（默认） */
    val capsuleMiniCompact = mutableStateOf(false)
    /** 收起态壳透明度：跟气质套走 */
    val capsuleShellOpacity = mutableStateOf(appPreferences.getCapsuleShellOpacity())
    /** 门 / 陪伴 / 桌面圆球共用解析后的气质 */
    val overlayThemePack = mutableStateOf(appPreferences.resolvedThemePack())
    val capsuleCompanionPrefs = mutableStateOf(
        CapsuleCompanionPrefs(
            barEnabled = appPreferences.isCompanionBarEnabled(),
            form = appPreferences.getCompanionBarForm(),
        )
    )

    /**
     * 含意图门切走后的「自动结束」剩余秒数。
     * -1 = 未处于离开倒计时；>=0 时暂停态用环边耗尽表达进度。
     */
    val capsuleAwayCountdownSeconds = mutableStateOf(-1L)

    /**
     * 本轮离开倒计时总量（秒），与 [capsuleAwayCountdownSeconds] 配对算进度。
     * 0 = 未知；展示暂停时钉住，避免还原中途时进度被重置成满格。
     */
    val capsuleAwayCountdownTotalSeconds = mutableStateOf(0L)

    /**
     * 异步拉数前先盖住目标 App（主线程优先队列），对齐「拦截先于 App 露脸」。
     * 可与 [showIntercept] / [showPeriodLock] 等叠加，占位层会被正式 UI 替换。
     */
    fun prepareInterceptCover(
        packageName: String,
        kind: InterceptOverlayKind = InterceptOverlayKind.IntentGate
    ) {
        // 标志位可任意线程写：监测轮询需立刻看见「门已在路上」。
        // 碰 View 必须主线程——调用方常在 Dispatchers.Default（如 eager cover / showBreathDoor）。
        val switchingFromOther =
            interceptView != null &&
                interceptTargetPackage != null &&
                interceptTargetPackage != packageName
        isInterceptVisible.set(true)
        interceptTargetPackage = packageName
        interceptKind = kind
        attachedInterceptKind = kind
        interceptShownAtWall = System.currentTimeMillis()
        val coverUi = Runnable {
            setDesktopAnchorSceneHidden(true)
            forceCloseCompanionInspect()
            if (!isInterceptVisible.get()) return@Runnable
            if (interceptTargetPackage != packageName) return@Runnable
            if (interceptView == null || switchingFromOther) {
                showInterceptPlaceholder()
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            coverUi.run()
        } else {
            mainHandler.postAtFrontOfQueue(coverUi)
        }
        onInterceptCoverPrepared?.invoke(packageName)
    }

    /**
     * 显示全屏拦截浮窗（始终要求用户写下使用目的）。
     *
     * 若该 App 存在「非标准闭环」待确认中断，则以「最近操作」条呈现，
     * 展示相对时刻并可一键继续，或重写意图开始新的一次。
     *
     * @param onContinue 用户确认继续时的回调，携带输入的使用目的
     * @param onSessionResumed 用户点「接着上次意图进入」并成功开始新会话后的回调
     */
    fun showIntercept(
        packageName: String,
        appName: String,
        dailyLimitMinutes: Int,
        weeklyLimitMinutes: Int,
        onContinue: (com.life.mindfulnessapp.domain.model.InterceptEnterDecision) -> Unit,
        onDismiss: () -> Unit,
        onReset: (() -> Unit)? = null,
        onSessionResumed: ((UsageSession) -> Unit)? = null,
        onContinueWithGrace: ((purpose: String, graceMinutes: Int) -> Unit)? = null,
        /** false：同一进入尝试的静默重展（如拦截页期间息屏后解锁），不计新冲动 */
        countImpulse: Boolean = true,
        /** 用户确认进入、退场动画开始时（早于 onContinue） */
        onEnterAnimating: (() -> Unit)? = null
    ) {
        prepareInterceptCover(packageName, InterceptOverlayKind.IntentGate)
        if (!countImpulse) {
            // 静默重展：清掉息屏竞态可能挂上的离开轻条
            suppressTransientLeaveFeedback()
        }

        scope.launch {
            val now = System.currentTimeMillis()
            val dbTodayUsedSeconds = usageRecordRepository.getDailyUsageSeconds(packageName, now)
            val dbWeekUsedSeconds = usageRecordRepository.getWeeklyUsageSeconds(packageName, now)
            val todayRecords = usageRecordRepository.getDayRecordsForApp(packageName, now)
            val remainingModifyCount = appLimitRepository.getRemainingModifyCount(packageName)
            val currentLimit = appLimitRepository.getAppLimit(packageName)
            val pendingInterrupt = pendingInterruptStore.get(packageName)

            // 若当前有进行中的会话（同一个 App），需加上本次会话已累计的时长。
            // 数据库只存已完成的记录，正在进行的会话时长不在 DB 里。
            val activeSession = sessionManager.currentSession.value
            val activeExtraSeconds = if (activeSession != null && activeSession.packageName == packageName) {
                activeSession.currentSessionSeconds
            } else 0L
            // 监控后只用心锚（含进行中）；门口盖层不采系统
            val todayUsedSeconds = DailyCapFacts.usedSeconds(dbTodayUsedSeconds + activeExtraSeconds)
            val weekUsedSeconds = dbWeekUsedSeconds + activeExtraSeconds

            mainHandler.post {
                // 如果在异步加载数据期间已被取消（如用户按 Home 键离开），放弃展示
                if (!isInterceptVisible.get()) return@post
                if (interceptTargetPackage != packageName) return@post
                // 正式门已在：不要再拆重建，否则会整页跳动
                if (hasInterceptUiAttached()) return@post
                val liveSession = sessionManager.currentSession.value
                if (liveSession != null && liveSession.packageName == packageName) {
                    android.util.Log.d(
                        "OverlayManager",
                        "[$packageName] 已有活跃会话，放弃迟到的拦截页"
                    )
                    isInterceptVisible.set(false)
                    interceptTargetPackage = null
                    interceptKind = null
                    removeInterceptViewInternal()
                    return@post
                }
                // 不先拆占位层：由 addInterceptOverlayView 先加后卸，避免露底

                // 判断是否已超限：须与 UsageStrip 使用同一套「生效日限」（含触顶延长天花板）。
                // 若只用基础日限做展示、用生效日限做分流，会出现「今日已用完」却仍停在意图门。
                val baseDaily = currentLimit?.effectiveDailyLimitMinutes() ?: dailyLimitMinutes
                val dailyLimitSeconds =
                    appPreferences.effectiveDailyLimitSeconds(packageName, baseDaily)
                val weeklyLimitSeconds = currentLimit?.effectiveWeeklyLimitMinutes()?.times(60L)
                    ?: (weeklyLimitMinutes * 60L)
                val isAlreadyOverLimit = DailyCapFacts.exhausted(todayUsedSeconds, dailyLimitSeconds) ||
                    DailyCapFacts.exhausted(weekUsedSeconds, weeklyLimitSeconds)
                // 意图门上的日限文案：用生效秒数换算，避免宽限期内仍显示「今日已用完」
                val displayDailyLimitMinutes = when {
                    dailyLimitSeconds > 0L ->
                        ((dailyLimitSeconds + 59L) / 60L).toInt().coerceAtLeast(1)
                    else -> 0
                }
                val displayWeeklyLimitMinutes =
                    currentLimit?.effectiveWeeklyLimitMinutes() ?: weeklyLimitMinutes

                if (isAlreadyOverLimit) {
                    // 超限优先：清掉待确认，避免与超限页叠加
                    pendingInterruptStore.clear(packageName)
                    android.util.Log.d(
                        "OverlayManager",
                        "[$packageName] 已超限，改展时长锁页 " +
                            "(used=$todayUsedSeconds limitSec=$dailyLimitSeconds)"
                    )
                    if (!appPreferences.isVipActive()) {
                        showAdOverlay(packageName = packageName, onAdFinished = {
                            if (isInterceptVisible.get()) {
                                showLimitReachedInternal(
                                    packageName, onDismiss, onReset,
                                    onContinueWithGrace = onContinueWithGrace
                                )
                            }
                        })
                    } else {
                        showLimitReachedInternal(
                            packageName, onDismiss, onReset,
                            onContinueWithGrace = onContinueWithGrace
                        )
                    }
                    return@post
                }

                val enterCount = UsageRecordCounts.enterCount(todayRecords)
                val dismissCount = UsageRecordCounts.dismissCount(todayRecords)

                // 未闭环快照：仅意图门 + 有名意图/搜索才出「刚刚 · 意图」弱链
                val intentGateOn = currentLimit?.requireIntentOnOpen ?: true
                val resumeCandidate = if (
                    pendingInterrupt != null &&
                    onSessionResumed != null &&
                    intentGateOn &&
                    pendingInterrupt.isStrongResumeEligible()
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

                // 新鲜打开播「停住」拍；静默重展（息屏解锁等）直达命名，避免重复打断
                showInterceptInternal(
                    packageName, appName, displayDailyLimitMinutes, displayWeeklyLimitMinutes,
                    todayUsedSeconds, weekUsedSeconds, todayRecords,
                    remainingModifyCount, onContinue, onDismiss,
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
                    playPauseBeat = countImpulse,
                    onEnterAnimating = onEnterAnimating
                )
            }
        }
    }

    /** 数据未就绪时先盖纯色全屏，避免目标 App 先露脸再弹门 */
    private fun showInterceptPlaceholder() {
        val androidColor = interceptSolidAndroidColor()
        val view = View(context).apply {
            setBackgroundColor(androidColor)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val params = fullscreenImmersiveOverlayParams(
            extraFlags = WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        )
        try {
            addInterceptOverlayView(view, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** 与拦截门面墨色一致的实心底色（跟 [ThemePack.gateInk]）。 */
    private fun interceptSolidAndroidColor(): Int {
        val pack = appPreferences.resolvedThemePack()
        val bgCompose = pack.gateInk().bg
        return android.graphics.Color.argb(
            (bgCompose.alpha * 255).toInt().coerceIn(0, 255),
            (bgCompose.red * 255).toInt().coerceIn(0, 255),
            (bgCompose.green * 255).toInt().coerceIn(0, 255),
            (bgCompose.blue * 255).toInt().coerceIn(0, 255)
        )
    }

    private fun refreshOverlayThemePack() {
        overlayThemePack.value = appPreferences.resolvedThemePack()
        capsuleShellOpacity.value = appPreferences.getCapsuleShellOpacity()
    }

    /** Compose 内容自己画底；View 底用同色实底。 */
    private fun interceptComposeBackdropColor(): Int = interceptSolidAndroidColor()

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
        pendingInterrupt: PendingInterrupt? = null,
        onSessionResumed: ((UsageSession) -> Unit)? = null,
        sessionLimitEnabled: Boolean = true,
        defaultSessionLimitMinutes: Int = 15,
        intentQualityCheckEnabled: Boolean = false,
        intentBlockKeywords: List<String> = emptyList(),
        impulseCount: Int = 1,
        enterCount: Int = 0,
        dismissCount: Int = 0,
        playPauseBeat: Boolean = true,
        onEnterAnimating: (() -> Unit)? = null
    ) {
        val capsuleTargetPos = CapsuleTargetPosition(
            x = capsuleFloatAbsoluteCenterX(packageName),
            y = capsuleFloatOffsetY.toFloat()
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

        refreshOverlayThemePack()
        val themePack = overlayThemePack.value
        val isDarkTheme = themePack.isDark

        val isLimitTheme =
            (dailyLimitMinutes > 0 && todayUsedSeconds >= dailyLimitMinutes * 60L) ||
                (weeklyLimitMinutes > 0 && weekUsedSeconds >= weeklyLimitMinutes * 60L)

        // ── 离开肯定：日常轻提示（先离开再肯定）；里程碑才全屏勋章后再离开 ──
        // 拦截页会先播短退场，再调到此处
        val dismissWithCeremony: () -> Unit = {
            pendingGateHoldHadDraft = interceptHadDraftPurpose
            pendingInterruptStore.clear(packageName)
            isInterceptVisible.set(false)
            // 离开退场动画已在 InterceptOverlayScreen 播完；稍延卸层，避免 App 仍在前台时先露出界面
            scheduleRemoveInterceptView(interceptLeaveDismissMs)
            showDismissCeremony(
                packageName = packageName,
                destination = DismissDestination.HOME,
                isLimitTheme = isLimitTheme,
                offerPositiveDestination = true,
                onDismissCompleted = { onDismiss() }
            )
            Unit
        }

        /** 「有意义的事」：卸门 + 记录离开；有绑定则直达 App，跳过正向去处仪式 */
        val dismissToMeaningfulApp: (String?) -> Unit = { launchPkg ->
            pendingGateHoldHadDraft = interceptHadDraftPurpose
            pendingInterruptStore.clear(packageName)
            isInterceptVisible.set(false)
            scheduleRemoveInterceptView(interceptLeaveDismissMs)
            showDismissCeremony(
                packageName = packageName,
                destination = DismissDestination.HOME,
                isLimitTheme = isLimitTheme,
                offerPositiveDestination = false,
                onDismissCompleted = {
                    onDismiss()
                    val pkg = launchPkg?.trim().orEmpty()
                    if (pkg.isNotEmpty()) {
                        onLaunchPositiveApp?.invoke(pkg)
                    }
                }
            )
            Unit
        }

        val dismissToPositiveExit: (PositiveExitChoice) -> Unit = { choice ->
            pendingGateHoldHadDraft = interceptHadDraftPurpose
            pendingInterruptStore.clear(packageName)
            isInterceptVisible.set(false)
            scheduleRemoveInterceptView(interceptLeaveDismissMs)
            showPositiveExitCeremony(
                packageName = packageName,
                choice = choice,
                onDismissCompleted = {
                    onPositiveExit?.invoke(packageName, choice)
                        ?: run {
                            onDismiss()
                            val pkg = choice.launchPackageName?.trim().orEmpty()
                            if (pkg.isNotEmpty()) onLaunchPositiveApp?.invoke(pkg)
                        }
                }
            )
            Unit
        }

        val resumeCallback: (() -> Unit)? =
            if (pendingInterrupt != null && onSessionResumed != null) {
                {
                    val interrupt = pendingInterrupt
                    scope.launch {
                        SessionCompareReminderWorker.cancel(context, interrupt.recordId)
                        val session = sessionManager.resumeInterruptedSession(interrupt)
                        mainHandler.post {
                            if (session != null) {
                                beginGateEnter(packageName)
                                removeInterceptViewInternal()
                                onSessionResumed(session)
                            } else {
                                // 进入失败：清快照并重开标准拦截（无继续条）
                                pendingInterruptStore.clear(packageName)
                                removeInterceptViewInternal()
                                isInterceptVisible.set(true)
                                showInterceptInternal(
                                    packageName, appName, dailyLimitMinutes, weeklyLimitMinutes,
                                    todayUsedSeconds, weekUsedSeconds, todayRecords,
                                    remainingModifyCount, onContinue, onDismiss,
                                    pendingInterrupt = null,
                                    onSessionResumed = onSessionResumed,
                                    sessionLimitEnabled = sessionLimitEnabled,
                                    defaultSessionLimitMinutes = defaultSessionLimitMinutes,
                                    intentQualityCheckEnabled = intentQualityCheckEnabled,
                                    intentBlockKeywords = intentBlockKeywords,
                                    impulseCount = impulseCount,
                                    enterCount = enterCount,
                                    dismissCount = dismissCount,
                                    playPauseBeat = false
                                )
                            }
                        }
                    }
                }
            } else null

        val composeView = createComposeView(
            solidBackgroundColor = interceptComposeBackdropColor()
        ) {
            interceptHadDraftPurpose = false
            val livePack = overlayThemePack.value
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
                themePack = livePack,
                isDarkTheme = livePack.isDark,
                sessionLimitEnabled = sessionLimitEnabled,
                defaultSessionLimitMinutes = defaultSessionLimitMinutes,
                intentQualityCheckEnabled = intentQualityCheckEnabled,
                intentBlockKeywords = intentBlockKeywords,
                pendingInterrupt = pendingInterrupt,
                onReset = resetCallbackWithValue,
                onEnterAnimating = onEnterAnimating,
                onContinue = { decision ->
                    // 开始新的一次：丢弃未闭环快照
                    pendingInterruptStore.clear(packageName)
                    interceptHadDraftPurpose = false
                    removeInterceptViewInternal()
                    onContinue(decision)
                },
                onResumePrevious = resumeCallback,
                leaveRitualInCooldown = isLeaveRitualInCooldown(packageName),
                onDismiss = dismissWithCeremony,
                onLaunchMeaningfulApp = { pkg ->
                    dismissToMeaningfulApp(pkg)
                },
                onPositiveExit = { choice ->
                    dismissToPositiveExit(choice)
                },
                onOpenAppSettings = {
                    // IconButton 波纹在下一帧绑 RenderNode；卸窗后 draw 会
                    // IllegalStateException: Cannot start this animator on a detached view
                    isInterceptVisible.set(false)
                    interceptView?.let { v ->
                        v.isEnabled = false
                        v.isClickable = false
                        v.cancelPendingInputEvents()
                        runCatching { v.jumpDrawablesToCurrentState() }
                        v.visibility = View.INVISIBLE
                        v.alpha = 0f
                        v.setLayerType(View.LAYER_TYPE_NONE, null)
                    }
                    val pkg = packageName
                    mainHandler.postDelayed({
                        removeInterceptViewInternal()
                        onOpenAppLimitEdit?.invoke(pkg)
                    }, 280L)
                },
                impulseCount = impulseCount,
                enterCount = enterCount,
                dismissCount = dismissCount,
                playPauseBeat = playPauseBeat,
                onKeywordBlocked = {
                    onKeywordBlocked?.invoke(packageName, appName)
                },
                onIntentDraftChanged = { had ->
                    interceptHadDraftPurpose = had
                },
                onComposeBackHandlerChange = { handler ->
                    onInterceptComposeBack = handler
                }
            )
        }

        val params = fullscreenImmersiveOverlayParams(
            extraFlags = WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        ).apply {
            @Suppress("DEPRECATION")
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        try {
            addInterceptOverlayView(composeView, params)
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
        onContinueWithGrace: ((purpose: String, graceMinutes: Int) -> Unit)? = null,
        showAd: Boolean = false
    ) {
        prepareInterceptCover(packageName, InterceptOverlayKind.DailyLimit)

        // 仅在 showAd=true 且非 VIP 时才插播广告
        if (showAd && !appPreferences.isVipActive()) {
            showAdOverlay(packageName = packageName, onAdFinished = {
                // 广告播放期间 dismissIntercept() 可能被监控循环误触发（检测到 App 不在前台），
                // 将 isInterceptVisible 重新置为 true，确保超限页能够正常展示
                isInterceptVisible.set(true)
                showLimitReachedInternal(packageName, onDismiss, onReset, onContinueWithGrace)
            })
            return
        }

        showLimitReachedInternal(packageName, onDismiss, onReset, onContinueWithGrace)
    }

    /**
     * 时段锁硬挡页。优先级高于日限与意图门。
     * 可有「紧急进入」计次（[onBreakthrough]，需选时长）；
     * 解锁整段只能在配置里关闭时段（生效中关闭会过门槛）。
     * [scheduleTitle] / [scheduleWhy] 仅日程锁传入，门面露出段名与可选「为了」。
     */
    fun showPeriodLock(
        packageName: String,
        appName: String,
        window: com.life.mindfulnessapp.domain.model.PeriodWindow,
        scheduleTitle: String? = null,
        scheduleWhy: String? = null,
        exemptionLabel: String? = null,
        onBreakthrough: ((minutes: Int) -> Unit)? = null,
        onDismiss: () -> Unit
    ) {
        prepareInterceptCover(packageName, InterceptOverlayKind.PeriodLock)
        // 硬挡优先：避免残留暂停胶囊叠在时段锁之上
        dismissCapsule()

        mainHandler.post {
            if (!isInterceptVisible.get()) return@post
            // 保留占位层直到正式页挂上（addInterceptOverlayView 原子替换）

            refreshOverlayThemePack()
            val livePack = overlayThemePack.value
            val composeView = createComposeView(
                solidBackgroundColor = interceptComposeBackdropColor()
            ) {
                PeriodLockOverlayScreen(
                    window = window,
                    packageName = packageName,
                    appName = appName,
                    scheduleTitle = scheduleTitle,
                    scheduleWhy = scheduleWhy,
                    exemptionLabel = exemptionLabel,
                    themePack = livePack,
                    onBreakthrough = onBreakthrough?.let { breakthrough ->
                        { minutes ->
                            isInterceptVisible.set(false)
                            breakthrough(minutes)
                            mainHandler.postDelayed({ removeInterceptViewInternal() }, 600)
                        }
                    },
                    onDismiss = {
                        isInterceptVisible.set(false)
                        onDismiss()
                        mainHandler.postDelayed({ removeInterceptViewInternal() }, 600)
                    }
                )
            }

            val params = fullscreenImmersiveOverlayParams(
                extraFlags = WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            )
            try {
                addInterceptOverlayView(composeView, params)
            } catch (e: Exception) {
                e.printStackTrace()
                isInterceptVisible.set(false)
            }
        }
    }

    /** 刷逛软门：总门分流；搜 / 写意图不定时长，随意浏览才认本次多久。搜索失败不卸门。 */
    fun showBreathGate(
        packageName: String,
        appName: String,
        remainingLabel: String,
        canSearch: Boolean,
        /** 有可靠搜索深链才出「随意浏览」 */
        offerBrowse: Boolean = false,
        intentTags: List<com.life.mindfulnessapp.domain.model.IntentGateAction> = emptyList(),
        browseUsedMinutes: Int = 0,
        browseLimitMinutes: Int? = null,
        maxSessionMinutes: Int = SessionLimitPolicy.MAX_SESSION_MINUTES,
        browseCooldownMinutes: Int = 0,
        onAdmit: (SoftDoorChoice, (Boolean) -> Unit) -> Unit,
        onLeave: () -> Unit
    ) {
        prepareInterceptCover(packageName, InterceptOverlayKind.Breath)
        mainHandler.post {
            if (!isInterceptVisible.get()) return@post
            refreshOverlayThemePack()
            val livePack = overlayThemePack.value
            val composeView = createComposeView(
                solidBackgroundColor = interceptComposeBackdropColor()
            ) {
                BreathGateOverlayScreen(
                    appName = appName,
                    remainingLabel = remainingLabel,
                    canSearch = canSearch,
                    offerBrowse = offerBrowse,
                    intentTags = intentTags,
                    browseUsedMinutes = browseUsedMinutes,
                    browseLimitMinutes = browseLimitMinutes,
                    maxSessionMinutes = maxSessionMinutes,
                    browseCooldownMinutes = browseCooldownMinutes,
                    packageName = packageName,
                    themePack = livePack,
                    onAdmit = { choice, done ->
                        onAdmit(choice) { ok ->
                            mainHandler.post {
                                if (ok) {
                                    isInterceptVisible.set(false)
                                    mainHandler.postDelayed({ removeInterceptViewInternal() }, 280)
                                }
                                done(ok)
                            }
                        }
                    },
                    onLeave = {
                        isInterceptVisible.set(false)
                        onLeave()
                        mainHandler.postDelayed({ removeInterceptViewInternal() }, 400)
                    }
                )
            }
            val params = fullscreenImmersiveOverlayParams(
                extraFlags = WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            ).apply {
                @Suppress("DEPRECATION")
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }
            try {
                addInterceptOverlayView(composeView, params)
            } catch (e: Exception) {
                e.printStackTrace()
                isInterceptVisible.set(false)
            }
        }
    }

    /** 事务软门：无呼吸；选时长后打开。 */
    fun showTaskGate(
        packageName: String,
        appName: String,
        factsLabel: String,
        onAdmit: (SoftDoorChoice, (Boolean) -> Unit) -> Unit,
        onLeave: () -> Unit
    ) {
        prepareInterceptCover(packageName, InterceptOverlayKind.Breath)
        mainHandler.post {
            if (!isInterceptVisible.get()) return@post
            refreshOverlayThemePack()
            val livePack = overlayThemePack.value
            val composeView = createComposeView(
                solidBackgroundColor = interceptComposeBackdropColor()
            ) {
                TaskGateOverlayScreen(
                    appName = appName,
                    factsLabel = factsLabel,
                    themePack = livePack,
                    onAdmit = { choice, done ->
                        onAdmit(choice) { ok ->
                            mainHandler.post {
                                if (ok) {
                                    isInterceptVisible.set(false)
                                    mainHandler.postDelayed({ removeInterceptViewInternal() }, 280)
                                }
                                done(ok)
                            }
                        }
                    },
                    onLeave = {
                        isInterceptVisible.set(false)
                        onLeave()
                        mainHandler.postDelayed({ removeInterceptViewInternal() }, 400)
                    }
                )
            }
            val params = fullscreenImmersiveOverlayParams(
                extraFlags = WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            )
            try {
                addInterceptOverlayView(composeView, params)
            } catch (e: Exception) {
                e.printStackTrace()
                isInterceptVisible.set(false)
            }
        }
    }

    /** 次数已尽硬挡。 */
    fun showOpenLimitBlock(
        packageName: String,
        appName: String,
        usedOpens: Int,
        openCap: Int,
        remainingMinutes: Int? = null,
        onLeave: () -> Unit
    ) {
        prepareInterceptCover(packageName, InterceptOverlayKind.OpenLimit)
        dismissCapsule()
        mainHandler.post {
            if (!isInterceptVisible.get()) return@post
            refreshOverlayThemePack()
            val livePack = overlayThemePack.value
            val composeView = createComposeView(
                solidBackgroundColor = interceptComposeBackdropColor()
            ) {
                HardBlockOverlayScreen(
                    packageName = packageName,
                    appName = appName,
                    title = "今天的次数\n用完了",
                    hero = "${usedOpens.coerceAtLeast(0)} / ${openCap.coerceAtLeast(0)}",
                    heroIsFraction = true,
                    unit = "次",
                    extraLine = remainingMinutes
                        ?.takeIf { it > 0 }
                        ?.let { "时长还剩 $it 分" }
                        .orEmpty(),
                    whenLine = "明天再开",
                    themePack = livePack,
                    onLeave = {
                        isInterceptVisible.set(false)
                        onLeave()
                        mainHandler.postDelayed({ removeInterceptViewInternal() }, 400)
                    }
                )
            }
            val params = fullscreenImmersiveOverlayParams(
                extraFlags = WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            )
            try {
                addInterceptOverlayView(composeView, params)
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
        onContinueWithGrace: ((purpose: String, graceMinutes: Int) -> Unit)? = null
    ) {
        scope.launch {
            val now = System.currentTimeMillis()
            val recordToday = usageRecordRepository.getDailyUsageSeconds(packageName, now)
            val todayUsed = DailyCapFacts.usedSeconds(recordToday)
            val weekUsed = usageRecordRepository.getWeeklyUsageSeconds(packageName, now)
            val limit = appLimitRepository.getAppLimit(packageName)
            val dailyLimitSeconds = appPreferences.effectiveDailyLimitSeconds(
                packageName,
                limit?.effectiveDailyLimitMinutes() ?: 0
            )
            val weeklyLimitSeconds = (limit?.effectiveWeeklyLimitMinutes() ?: 0) * 60L
            val dailyHit = DailyCapFacts.exhausted(todayUsed, dailyLimitSeconds)
            val weeklyHit = DailyCapFacts.exhausted(weekUsed, weeklyLimitSeconds)
            val showWeek = !dailyHit && weeklyHit
            val appName = limit?.appName ?: packageName

            mainHandler.post {
                if (!isInterceptVisible.get()) return@post
                interceptKind = InterceptOverlayKind.DailyLimit
                attachedInterceptKind = InterceptOverlayKind.DailyLimit
                interceptShownAtWall = System.currentTimeMillis()
                interceptLimitTheme = true

                refreshOverlayThemePack()
                val livePack = overlayThemePack.value
                val composeView = createComposeView(
                    solidBackgroundColor = interceptComposeBackdropColor()
                ) {
                    LimitReachedOverlayScreen(
                        usedSeconds = if (showWeek) weekUsed else todayUsed,
                        limitSeconds = if (showWeek) weeklyLimitSeconds else dailyLimitSeconds,
                        packageName = packageName,
                        appName = appName,
                        scope = if (showWeek) LimitReachedScope.Week else LimitReachedScope.Today,
                        themePack = livePack,
                        onDismiss = {
                            isInterceptVisible.set(false)
                            onDismiss()
                            mainHandler.postDelayed({ removeInterceptViewInternal() }, 600)
                        }
                    )
                }

                val params = fullscreenImmersiveOverlayParams(
                    extraFlags = WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                )
                try {
                    addInterceptOverlayView(composeView, params)
                } catch (e: Exception) {
                    e.printStackTrace()
                    isInterceptVisible.set(false)
                    interceptLimitTheme = false
                }
            }
        }
    }

    /**
     * 单次意图时长到点收口页。
     * 离开 / 意图·搜索「续一点时间」/ 随意浏览「再需要一点时间」半门命名后续段。
     * 与胶囊共用一次续时额度。Home = 稍后。
     */
    fun showSessionLimitReached(
        packageName: String,
        appName: String,
        purpose: String?,
        committedMinutes: Int,
        durationSeconds: Long,
        canExtend: Boolean,
        maxExtendMinutes: Int,
        intentKind: com.life.mindfulnessapp.domain.model.IntentKind? = null,
        compareEnabled: Boolean = true,
        compareMinMinutes: Int = 10,
        onConfirm: (mindfulnessLevel: Int?, note: String?, driftSeconds: Long?) -> Unit,
        /** namedPurpose 非空 = 随意浏览命名升级后续时 */
        onExtend: (extraMinutes: Int, namedPurpose: String?) -> Unit,
        onReviewLater: () -> Unit
    ) {
        prepareInterceptCover(packageName, InterceptOverlayKind.SessionLimit)
        mainHandler.post {
            if (!isInterceptVisible.get()) return@post
            refreshOverlayThemePack()
            val livePack = overlayThemePack.value
            val composeView = createComposeView(
                solidBackgroundColor = interceptComposeBackdropColor()
            ) {
                SessionLimitReachedOverlayScreen(
                    appName = appName,
                    packageName = packageName,
                    purpose = purpose,
                    committedMinutes = committedMinutes,
                    durationSeconds = durationSeconds,
                    canExtend = canExtend,
                    maxExtendMinutes = maxExtendMinutes,
                    intentKind = intentKind,
                    compareEnabled = compareEnabled,
                    compareMinMinutes = compareMinMinutes,
                    themePack = livePack,
                    isDarkTheme = livePack.isDark,
                    onConfirm = { level, note, drift ->
                        isInterceptVisible.set(false)
                        onConfirm(level, note, drift)
                        mainHandler.postDelayed({ removeInterceptViewInternal() }, 600)
                    },
                    onExtend = { minutes, namedPurpose ->
                        isInterceptVisible.set(false)
                        onExtend(minutes, namedPurpose)
                        mainHandler.postDelayed({ removeInterceptViewInternal() }, 280)
                    },
                    onReviewLater = {
                        isInterceptVisible.set(false)
                        onReviewLater()
                        mainHandler.postDelayed({ removeInterceptViewInternal() }, 280)
                    }
                )
            }
            val params = fullscreenImmersiveOverlayParams()
            try {
                addInterceptOverlayView(composeView, params)
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

            // 不加 FLAG_NOT_FOCUSABLE：让窗口可获得焦点，才能接收按键事件
            val params = fullscreenImmersiveOverlayParams()

            try {
                windowManager.addView(adContainerView, params)
                applyImmersiveStatusBarHide(adContainerView)
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
     * 拦截页确认进入：标记进门中，避免监控误判 Home / 重弹时长锁。
     * interceptTargetPackage 保留到 [finishGateEnterDismiss]，供 Home 探测与层状态判断。
     */
    fun beginGateEnter(packageName: String) {
        gateEnteringPackage = packageName
        isInterceptVisible.set(false)
    }

    /** 会话与胶囊就绪后，统一卸拦截层并清 metadata */
    fun finishGateEnterDismiss(packageName: String) {
        if (gateEnteringPackage == packageName) {
            gateEnteringPackage = null
        }
        interceptTargetPackage = null
        interceptKind = null
        interceptLimitTheme = false
        removeInterceptViewInternal()
    }

    fun clearGateEnter(packageName: String? = null) {
        if (packageName == null || gateEnteringPackage == packageName) {
            gateEnteringPackage = null
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
    fun showCapsule(session: UsageSession, playEnterAnimation: Boolean? = null) {
        isInterceptVisible.set(false)
        pendingCapsuleSession = session
        val animate = resolveCapsuleEnterAnimation(session.recordId, playEnterAnimation)
        pendingCapsulePlayEnter = animate
        mainHandler.removeCallbacks(ensureCapsuleVisibleRunnable)
        mainHandler.post {
            showCapsuleNow(session, animate)
            if (isSessionPresenceAttached(session)) {
                pendingCapsuleSession = null
                pendingCapsulePlayEnter = false
                if (gateEnteringPackage == session.packageName) {
                    finishGateEnterDismiss(session.packageName)
                }
            } else if (session.hasIntentGate || !appPreferences.isDesktopAnchorEnabled()) {
                // 未挂上才重试；重试一律不进场（见 ensureCapsuleVisibleRunnable）
                mainHandler.postDelayed(ensureCapsuleVisibleRunnable, 160L)
            } else {
                pendingCapsuleSession = null
                pendingCapsulePlayEnter = false
            }
        }
    }

    private fun isSessionPresenceAttached(session: UsageSession): Boolean {
        if (session.hasIntentGate || !session.purpose.isNullOrBlank()) return capsuleView != null
        if (desktopAnchorInApp.value?.packageName == session.packageName) return true
        // 桌面心锚关闭时回退胶囊
        return !appPreferences.isDesktopAnchorEnabled() && capsuleView != null
    }

    private fun showCapsuleNow(session: UsageSession, playEnterAnimation: Boolean) {
        // 后台快照勿挂活跃态：否则会冲掉暂停环 / 让桌面胶囊继续转圈计时观感
        val live = sessionManager.currentSession.value
        if (session.isInBackground ||
            (live != null && live.packageName == session.packageName && live.isInBackground)
        ) {
            Log.d(TAG, "showCapsuleNow 跳过活跃态（会话已在后台）${session.packageName}")
            return
        }
        // 拦截页进来的命名会话走陪伴条；桌面圆球只留给没有这次意图的在场。
        val namedSession = session.hasIntentGate || !session.purpose.isNullOrBlank()
        if (!namedSession &&
            appPreferences.isDesktopAnchorEnabled() &&
            Settings.canDrawOverlays(context)
        ) {
            if (capsuleView != null || capsuleSession != null) {
                removeCapsuleViewInternal()
            }
            presentDesktopAnchorInApp(session)
            return
        }

        // 同会话已挂上：只同步状态，勿拆 Compose（拆了会重播光点落成 / 窗位跳动）
        val sameLiveCapsule = capsuleView != null &&
            capsuleAppPackageName.value == session.packageName &&
            (capsuleSession?.recordId == session.recordId || capsuleSession == null)
        if (sameLiveCapsule) {
            syncLiveCapsuleState(session, namedSession = namedSession, paused = false)
            if (capsuleUpdateJob == null || capsuleUpdateJob?.isActive == false) {
                startCapsuleTimer(session)
            }
            syncDailyLimitGrayWash(session)
            return
        }

        clearDesktopAnchorInApp()
        removeCapsuleViewInternal()
        removeCeremonyViewInternal()

        syncCapsuleAwarenessForSession(session.recordId)
        syncLiveCapsuleState(session, namedSession = namedSession, paused = false)
        syncAwarenessPracticePrefs()
        capsuleShowUsedSeconds.value = appPreferences.isCapsuleUsedShowSeconds()
        capsuleMiniCompact.value = appPreferences.isCapsuleMiniCompact()
        capsuleShellOpacity.value = appPreferences.getCapsuleShellOpacity()
        refreshCapsuleCompanionPrefs()

        capsuleWakeUp = null

        // 超限续记：轻淡入；纯时长锁走圆环气泡展开（由 CapsuleOverlayView 承接）
        val quietInstrumentEnter = session.isOverLimitSession
        val effectivePlayEnter = playEnterAnimation && !quietInstrumentEnter
        capsulePlayEnterAnimation.value = effectivePlayEnter
        capsuleSoftReveal.value = quietInstrumentEnter && playEnterAnimation

        capsuleExpanded.value = false

        addCapsuleView(session = session)
    }

    /** 同步活跃胶囊字段；不触碰 View 生命周期。 */
    private fun syncLiveCapsuleState(
        session: UsageSession,
        namedSession: Boolean,
        paused: Boolean,
    ) {
        capsuleSession = session
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
        capsuleDailyBaseLimitSeconds.value =
            if (session.hasSessionLimit) 0L else session.displayDailyBaseLimitSeconds
        capsuleDailyGraceBonusSeconds.value =
            if (session.hasSessionLimit) 0L else session.dailyGraceBonusSeconds
        capsulePurpose.value = session.purpose
        capsuleIntentKind.value = session.intentKind
        capsuleIsPaused.value = paused
        capsuleIsOverLimit.value = session.isOverLimitSession
        capsuleHasIntentGate.value = namedSession
        capsuleCompareEnabled.value = session.compareEnabled
        capsuleCompareMinMinutes.value = session.compareMinMinutes
        capsuleHasTimeLock.value = session.hasTimeLock || session.hasSessionLimit
        capsuleSessionOriginStartMs.value = session.sessionOriginStartMs
        bindCapsuleTodayUsage(session)
        capsuleHasSessionLimit.value = session.hasSessionLimit
        capsuleCanExtend.value = session.canOfferSessionExtension
        capsuleSessionLimitMinutes.value = session.extensionBaseMinutes
        capsuleTodayEnterCount.value = session.todayEnterCount
    }

    /**
     * 将胶囊切换到暂停状态：
     * - 含意图门：冷色环边耗尽离开倒计时；点按主体回 App
     * - [awayCountdownSeconds] / [awayCountdownTotalSeconds] 驱动环边进度
     */
    fun pauseCapsule(
        returnToAppAction: () -> Unit,
        awayCountdownSeconds: Long? = null,
        awayCountdownTotalSeconds: Long? = null
    ) {
        mainHandler.post {
            applyPausedState(
                returnToAppAction,
                awayCountdownSeconds,
                awayCountdownTotalSeconds
            )
        }
    }

    /**
     * 原子展示暂停胶囊（含离开倒计时），避免 showCapsule + pauseCapsule 两次 post 竞态：
     * show 末尾会把 isPaused 置 false / away 置 -1，若与 pause 交错会导致倒计时 UI 不更新。
     */
    fun showPausedCapsule(
        session: UsageSession,
        returnToAppAction: () -> Unit,
        awayCountdownSeconds: Long,
        awayCountdownTotalSeconds: Long = awayCountdownSeconds
    ) {
        isInterceptVisible.set(false)
        // 作废尚未执行的活跃态 showCapsule 重试，避免冲掉暂停环
        pendingCapsuleSession = null
        mainHandler.removeCallbacks(ensureCapsuleVisibleRunnable)
        mainHandler.post {
            // 同包已挂胶囊：只切暂停态，避免拆建 View 导致计时数字跳动
            if (capsuleView != null && capsuleAppPackageName.value == session.packageName) {
                capsuleSession = session
                capsuleSessionSeconds.value = session.currentSessionSeconds
                applyPausedState(
                    returnToAppAction,
                    awayCountdownSeconds,
                    awayCountdownTotalSeconds
                )
                if (capsuleUpdateJob == null || capsuleUpdateJob?.isActive == false) {
                    startCapsuleTimer(session)
                }
                return@post
            }
            removeCapsuleViewInternal()
            removeCeremonyViewInternal()

            syncCapsuleAwarenessForSession(session.recordId)

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
            capsuleDailyBaseLimitSeconds.value =
                if (session.hasSessionLimit) 0L else session.displayDailyBaseLimitSeconds
            capsuleDailyGraceBonusSeconds.value =
                if (session.hasSessionLimit) 0L else session.dailyGraceBonusSeconds
            capsulePurpose.value = session.purpose
        capsuleIntentKind.value = session.intentKind
            capsuleIsOverLimit.value = session.isOverLimitSession
            capsuleHasIntentGate.value = session.hasIntentGate
            capsuleCompareEnabled.value = session.compareEnabled
            capsuleCompareMinMinutes.value = session.compareMinMinutes
            capsuleHasTimeLock.value = session.hasTimeLock || session.hasSessionLimit
            capsuleSessionOriginStartMs.value = session.sessionOriginStartMs
            capsuleTodayEnterCount.value = session.todayEnterCount
            capsuleTodayTotalSeconds.value = session.todayTotalSeconds
            capsuleHasSessionLimit.value = session.hasSessionLimit
            capsuleCanExtend.value = session.canOfferSessionExtension
            capsuleSessionLimitMinutes.value = session.extensionBaseMinutes
            capsuleShowUsedSeconds.value = appPreferences.isCapsuleUsedShowSeconds()
            capsuleMiniCompact.value = appPreferences.isCapsuleMiniCompact()
            capsuleShellOpacity.value = appPreferences.getCapsuleShellOpacity()
            syncAwarenessPracticePrefs()
            refreshCapsuleCompanionPrefs()
            capsuleWakeUp = null

            applyPausedState(
                returnToAppAction,
                awayCountdownSeconds,
                awayCountdownTotalSeconds
            )
            capsulePlayEnterAnimation.value = false
            capsuleSoftReveal.value = false
            addCapsuleView(session = session)
        }
    }

    /**
     * 回前台恢复展示：同包已挂上时不重建 View，只同步状态，避免计时跳动。
     */
    fun resumeOrShowCapsule(session: UsageSession) {
        isInterceptVisible.set(false)
        mainHandler.post {
            val live = sessionManager.currentSession.value
            if (session.isInBackground ||
                (live != null && live.packageName == session.packageName && live.isInBackground)
            ) {
                Log.d(TAG, "resumeOrShowCapsule 拒绝活跃态（仍在后台）${session.packageName}")
                return@post
            }
            val sameCapsule =
                capsuleView != null && capsuleAppPackageName.value == session.packageName
            val sameDesktop =
                !session.hasIntentGate &&
                    session.purpose.isNullOrBlank() &&
                    desktopAnchorInApp.value?.packageName == session.packageName
            if (sameCapsule) {
                capsuleIsPaused.value = false
                capsuleExpanded.value = false
                capsuleAwayCountdownSeconds.value = -1L
                capsuleAwayCountdownTotalSeconds.value = 0L
                returnToAppCallback = null
                capsuleSession = session
                capsuleSessionSeconds.value = session.currentSessionSeconds
                val remaining = session.budgetRemainingSeconds.let {
                    if (it == Long.MAX_VALUE) 0L else it
                }
                capsuleDailyRemainingSeconds.value = remaining
                capsuleDailyLimitSeconds.value = when {
                    session.hasSessionLimit -> session.effectiveSessionLimitSeconds
                    else -> session.dailyLimitSeconds
                }
                capsuleDailyBaseLimitSeconds.value =
                    if (session.hasSessionLimit) 0L else session.displayDailyBaseLimitSeconds
                capsuleDailyGraceBonusSeconds.value =
                    if (session.hasSessionLimit) 0L else session.dailyGraceBonusSeconds
                capsuleHasSessionLimit.value = session.hasSessionLimit
                capsuleCanExtend.value = session.canOfferSessionExtension
                capsuleSessionLimitMinutes.value = session.extensionBaseMinutes
                capsuleHasTimeLock.value = session.hasTimeLock || session.hasSessionLimit
                capsuleSessionOriginStartMs.value = session.sessionOriginStartMs
                capsuleTodayEnterCount.value = session.todayEnterCount
                capsuleTodayTotalSeconds.value = session.todayTotalSeconds
                capsuleIsOverLimit.value = session.isOverLimitSession
                capsulePurpose.value = session.purpose
        capsuleIntentKind.value = session.intentKind
                if (capsuleUpdateJob == null || capsuleUpdateJob?.isActive == false) {
                    startCapsuleTimer(session)
                }
                syncDailyLimitGrayWash(session)
                return@post
            }
            if (sameDesktop) {
                presentDesktopAnchorInApp(session)
                return@post
            }
            showCapsuleNow(session, playEnterAnimation = false)
        }
    }

    private fun applyPausedState(
        returnToAppAction: () -> Unit,
        awayCountdownSeconds: Long?,
        awayCountdownTotalSeconds: Long? = null
    ) {
        capsuleIsPaused.value = true
        returnToAppCallback = returnToAppAction
        capsuleExpanded.value = false
        closeIntentSessionHub()
        // 离开目标 App：关掉系统灰度（今日武装不清除，再进仍灰）
        displayGrayscaleController.setGrayscaleActive(false)
        if (awayCountdownSeconds != null) {
            val remain = awayCountdownSeconds.coerceAtLeast(0L)
            capsuleAwayCountdownSeconds.value = remain
            val totalHint = awayCountdownTotalSeconds?.coerceAtLeast(0L) ?: 0L
            when {
                totalHint > 0L ->
                    capsuleAwayCountdownTotalSeconds.value =
                        maxOf(totalHint, remain, 1L)
                capsuleAwayCountdownTotalSeconds.value < remain ->
                    capsuleAwayCountdownTotalSeconds.value = remain.coerceAtLeast(1L)
                capsuleAwayCountdownTotalSeconds.value <= 0L ->
                    capsuleAwayCountdownTotalSeconds.value = remain.coerceAtLeast(1L)
            }
        } else {
            capsuleAwayCountdownSeconds.value = -1L
            capsuleAwayCountdownTotalSeconds.value = 0L
        }
    }

    /** 更新离开倒计时剩余秒数（锁屏冻结 UI 期间由 Service 停更；墙钟超时仍会收口） */
    fun updateAwayCountdown(remainingSeconds: Long) {
        val sec = remainingSeconds.coerceAtLeast(0L)
        if (Looper.myLooper() == Looper.getMainLooper()) {
            capsuleAwayCountdownSeconds.value = sec
            if (capsuleAwayCountdownTotalSeconds.value < sec) {
                capsuleAwayCountdownTotalSeconds.value = sec.coerceAtLeast(1L)
            }
        } else {
            mainHandler.post {
                capsuleAwayCountdownSeconds.value = sec
                if (capsuleAwayCountdownTotalSeconds.value < sec) {
                    capsuleAwayCountdownTotalSeconds.value = sec.coerceAtLeast(1L)
                }
            }
        }
    }

    /** 恢复胶囊到活跃状态（app回到前台时调用） */
    fun resumeCapsule() {
        mainHandler.post {
            capsuleIsPaused.value = false
            capsuleExpanded.value = false
            capsuleAwayCountdownSeconds.value = -1L
            capsuleAwayCountdownTotalSeconds.value = 0L
            returnToAppCallback = null
            capsuleSession?.let { syncDailyLimitGrayWash(it) }
        }
    }

    /** 续时后同步胶囊展示状态（不重建 View） */
    fun syncCapsuleSessionState(session: UsageSession) {
        mainHandler.post {
            capsuleSession = session
            capsuleSessionSeconds.value = session.currentSessionSeconds
            val remaining = session.budgetRemainingSeconds.let {
                if (it == Long.MAX_VALUE) 0L else it
            }
            capsuleDailyRemainingSeconds.value = remaining
            capsuleDailyLimitSeconds.value = when {
                session.hasSessionLimit -> session.effectiveSessionLimitSeconds
                else -> session.dailyLimitSeconds
            }
            capsuleDailyBaseLimitSeconds.value =
                if (session.hasSessionLimit) 0L else session.displayDailyBaseLimitSeconds
            capsuleDailyGraceBonusSeconds.value =
                if (session.hasSessionLimit) 0L else session.dailyGraceBonusSeconds
            capsuleHasSessionLimit.value = session.hasSessionLimit
            capsuleCanExtend.value = session.canOfferSessionExtension
            capsuleSessionLimitMinutes.value = session.extensionBaseMinutes
            capsuleHasTimeLock.value = session.hasTimeLock || session.hasSessionLimit
            capsuleSessionOriginStartMs.value = session.sessionOriginStartMs
            bindCapsuleTodayUsage(session)
            capsuleIsOverLimit.value = session.isOverLimitSession
            capsulePurpose.value = session.purpose
        capsuleIntentKind.value = session.intentKind
            syncDailyLimitGrayWash(session)
        }
    }

    /** 点击暂停胶囊时回到app的回调 */
    private var returnToAppCallback: (() -> Unit)? = null

    private fun bindCapsuleTodayUsage(session: UsageSession) {
        capsuleTodayEnterCount.value = session.todayEnterCount
        capsuleTodayTotalSeconds.value = session.todayTotalSeconds
        scope.launch(Dispatchers.IO) {
            val snap = systemUsageRepository.getTodaySystemUsageSnapshot(
                session.packageName,
                session.sessionOriginStartMs
            )
            mainHandler.post {
                if (capsuleAppPackageName.value != session.packageName) return@post
                if (snap.totalSeconds > capsuleTodayTotalSeconds.value) {
                    capsuleTodayTotalSeconds.value = snap.totalSeconds
                }
                if (snap.openCount > capsuleTodayEnterCount.value) {
                    capsuleTodayEnterCount.value = snap.openCount
                }
            }
        }
    }

    /**
     * 创建并添加普通胶囊 View 到 WindowManager。
     */
    private fun addCapsuleView(session: UsageSession) {
        // 日限额真灰度：按会话对齐系统色彩校正
        syncDailyLimitGrayWash(session)
        refreshOverlayThemePack()
        val isDarkTheme = overlayThemePack.value.isDark
        val composeView = createComposeView {
            val livePack = overlayThemePack.value
            CapsuleOverlayView(
                sessionManager = null,
                appName = capsuleAppName,
                appPackageName = capsuleAppPackageName,
                sessionSeconds = capsuleSessionSeconds,
                dailyRemainingSeconds = capsuleDailyRemainingSeconds,
                dailyLimitSeconds = capsuleDailyLimitSeconds,
                dailyBaseLimitSeconds = capsuleDailyBaseLimitSeconds,
                dailyGraceBonusSeconds = capsuleDailyGraceBonusSeconds,
                purpose = capsulePurpose,
                intentKind = capsuleIntentKind,
                expanded = capsuleExpanded,
                isPaused = capsuleIsPaused,
                isOverLimit = capsuleIsOverLimit,
                hasIntentGate = capsuleHasIntentGate,
                compareEnabled = capsuleCompareEnabled,
                compareMinMinutes = capsuleCompareMinMinutes,
                awarenessPracticeEnabled = capsuleAwarenessPracticeEnabled,
                awarenessPracticeGapSec = capsuleAwarenessPracticeGapSec,
                hasTimeLock = capsuleHasTimeLock,
                todayEnterCount = capsuleTodayEnterCount,
                sessionOriginStartMs = capsuleSessionOriginStartMs,
                todayTotalSeconds = capsuleTodayTotalSeconds,
                hasSessionLimit = capsuleHasSessionLimit,
                canOfferExtension = capsuleCanExtend,
                sessionLimitMinutes = capsuleSessionLimitMinutes,
                showUsedSeconds = capsuleShowUsedSeconds,
                miniCompact = capsuleMiniCompact,
                shellOpacity = capsuleShellOpacity,
                companionPrefs = capsuleCompanionPrefs,
                awayCountdownSeconds = capsuleAwayCountdownSeconds,
                awayCountdownTotalSeconds = capsuleAwayCountdownTotalSeconds,
                themePack = overlayThemePack,
                isDarkTheme = livePack.isDark,
                onToggleExpand = { toggleCapsuleExpanded() },
                onCompanionActivate = { onCompanionActivate() },
                onEndSession = { note, mindfulnessLevel, driftSeconds, openToAnchor ->
                    finishManualEndSession(
                        note = note,
                        mindfulnessLevel = mindfulnessLevel,
                        driftSeconds = driftSeconds,
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
                        // 贴边收起时弹窗会随 WRAP_CONTENT 变宽；缓动居中，避免瞬移抖一下
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
                onEnterAnimationConsumed = {
                    // 开播即清，防止 restack 重建后再播
                    capsulePlayEnterAnimation.value = false
                    capsuleSoftReveal.value = false
                },
                onMiniSettled = {
                    capsulePlayEnterAnimation.value = false
                    capsuleSoftReveal.value = false
                    capsuleMiniSettled = true
                    // 入场落定后再补跑积压的限额预警展开，避免与仪式抢态
                    val pending = pendingLimitAutoExpand
                    pendingLimitAutoExpand = null
                    if (pending != null) {
                        pending.invoke()
                    } else if (!capsuleExpanded.value && !capsuleTopPinned) {
                        mainHandler.postDelayed(snapAfterCollapseRunnable, 320L)
                    }
                },
                onTopPinChanged = { pin ->
                    setCapsuleTopPinned(pin)
                },
                onAttachedTopInsetChanged = { insetPx ->
                    applyCapsuleAttachedTopInset(insetPx)
                },
                onRegisterRequestCheckFocus = { fn ->
                    capsuleRequestCheckFocus = fn
                },
                onRegisterApplyExternalAwareness = { fn ->
                    capsuleApplyExternalAwareness = fn
                },
                showOrbDiscoverHint = !appPreferences.hasSeenFocusOrbDiscoverHint(),
                onOrbDiscoverHintShown = {
                    appPreferences.markFocusOrbDiscoverHintSeen()
                },
                enterAnchorHintAlreadyShown = capsuleEnterAnchorHintShown,
                browseNearEndHintAlreadyShown = capsuleBrowseNearEndHintShown,
                initialFocusChecksCompleted = capsuleFocusChecksCompleted,
                initialLastCheckAtSec = capsuleLastCheckAtSec,
                onAwarenessSessionStateChanged = { checks, lastCheck, enterHint, browseHint ->
                    capsuleFocusChecksCompleted = checks
                    capsuleLastCheckAtSec = lastCheck
                    capsuleEnterAnchorHintShown = enterHint
                    capsuleBrowseNearEndHintShown = browseHint
                },
                onMidSessionAwareness = { kind, action, checkIndex, mode ->
                    val session = sessionManager.currentSession.value
                    val sessionId = session?.recordId ?: 0L
                    val app = capsuleAppName.value
                    val pkg = capsuleAppPackageName.value
                    when (kind) {
                        "show" -> analyticsRepository.trackMidCheckShow(
                            sessionId = sessionId,
                            checkIndex = checkIndex,
                            awarenessMode = mode,
                            app = app,
                            pkg = pkg
                        )
                        "action" -> if (!action.isNullOrBlank()) {
                            analyticsRepository.trackMidCheckAction(
                                sessionId = sessionId,
                                action = action,
                                checkIndex = checkIndex,
                                awarenessMode = mode,
                                app = app,
                                pkg = pkg
                            )
                        }
                        "soft_exit" -> if (!action.isNullOrBlank()) {
                            analyticsRepository.trackSoftExitAction(
                                sessionId = sessionId,
                                action = action,
                                awarenessMode = mode,
                                app = app,
                                pkg = pkg
                            )
                        }
                    }
                },
                playEnterAnimation = capsulePlayEnterAnimation,
                softReveal = capsuleSoftReveal
            )
        }

        loadCapsuleFloatPosition(session.packageName)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            // CENTER_HORIZONTAL：x=0 即水平居中；展开岛时归 0
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = capsuleFloatOffsetX
            y = capsuleFloatOffsetY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        capsuleParams = params
        capsuleTopPinned = false
        // 岛记忆默认顶栏居中；与悬浮记忆分离
        capsuleIslandOffsetX = 0
        capsuleIslandOffsetY = capsuleTopRowYPx()

        val capsuleHost = createCapsuleTouchHost(
            composeView = composeView,
            params = params,
            onClick = {
                onCompanionActivate()
            }
        )

        try {
            windowManager.addView(capsuleHost, params)
            capsuleView = capsuleHost
            if (capsuleUpdateJob == null || capsuleUpdateJob?.isActive == false) {
                startCapsuleTimer(session)
            }
            onCapsulePresentedForDesktopAnchor(session)
        } catch (e: Exception) {
            Log.e(TAG, "挂胶囊失败 ${session.packageName}", e)
            capsuleView = null
        }
    }

    /** 用户点圆球 / 陪伴条：使用中打开小卡片；预警岛仍走展开。 */
    private fun onCompanionActivate() {
        when {
            capsuleSkipEntrance != null -> capsuleSkipEntrance?.invoke()
            capsuleIsPaused.value -> returnToAppCallback?.invoke()
            capsuleExpanded.value || capsuleTopPinned -> toggleCapsuleExpanded()
            companionInspectOpen.value -> collapseCompanionInspect()
            else -> openCompanionInspect()
        }
    }

    private fun openCompanionInspect() {
        closeIntentSessionHub()
        companionInspectRemoveRunnable?.let { mainHandler.removeCallbacks(it) }
        companionInspectRemoveRunnable = null
        refreshDesktopAnchorGlance()
        refreshCompanionInspectGlance()
        companionInspectOpen.value = true
        ensureCompanionInspectView()
        restackCapsuleAboveInspect()
        val view = companionInspectView ?: capsuleView
        view?.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    private fun collapseCompanionInspect() {
        if (!companionInspectOpen.value && companionInspectView == null) return
        if (companionInspectOpen.value) {
            companionInspectView?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        companionInspectOpen.value = false
        companionInspectRemoveRunnable?.let { mainHandler.removeCallbacks(it) }
        val remove = Runnable {
            companionInspectRemoveRunnable = null
            removeCompanionInspectView()
        }
        companionInspectRemoveRunnable = remove
        mainHandler.postDelayed(remove, COMPANION_INSPECT_REMOVE_MS)
    }

    private fun forceCloseCompanionInspect() {
        companionInspectRemoveRunnable?.let { mainHandler.removeCallbacks(it) }
        companionInspectRemoveRunnable = null
        companionInspectOpen.value = false
        companionInspectGlance.value = null
        removeCompanionInspectView()
    }

    /** 面板轻问作答：还在 / 偏了；偏了走结束确认 */
    private fun markInspectAwarenessAnswered(still: Boolean) {
        val purpose = capsulePurpose.value
        val mode = SessionAwarenessMode.from(capsuleIntentKind.value, purpose)
        val checkIndex = capsuleFocusChecksCompleted
        analyticsRepository.trackMidCheckAction(
            sessionId = sessionManager.currentSession.value?.recordId ?: 0L,
            action = if (still) "still" else "drift",
            checkIndex = checkIndex,
            awarenessMode = mode.name.lowercase(),
            app = capsuleAppName.value,
            pkg = capsuleAppPackageName.value,
        )
        // 优先写进当前胶囊运行态，避免 5 分钟内再弹中场问
        val applied = capsuleApplyExternalAwareness?.invoke(still) == true
        if (!applied) {
            capsuleFocusChecksCompleted = (capsuleFocusChecksCompleted + 1).coerceAtMost(99)
            capsuleLastCheckAtSec = capsuleSessionSeconds.value
        }
    }

    private fun refreshCompanionInspectGlance() {
        val view = capsuleView
        val loc = IntArray(2)
        view?.getLocationOnScreen(loc)
        val density = context.resources.displayMetrics.density
        val bottomInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.windowInsets
                .getInsetsIgnoringVisibility(android.view.WindowInsets.Type.navigationBars())
                .bottom
        } else {
            (24f * density).toInt()
        }
        val prefs = capsuleCompanionPrefs.value
        val resolved = resolvedCompanion()
        val purpose = capsulePurpose.value?.trim().orEmpty()
        val path = CompanionPath.resolve(
            intentKind = capsuleIntentKind.value,
            purpose = purpose,
            hasSessionLimit = capsuleHasSessionLimit.value,
        )
        val mode = SessionAwarenessMode.from(capsuleIntentKind.value, purpose)
        // 觉察练习关 / 随意浏览不问；搜索安静窗内点开只看时间
        val showAsk = MidSessionCheckPolicy.shouldOfferInspectAsk(
            intentGate = capsuleHasIntentGate.value,
            intentKind = capsuleIntentKind.value,
            purpose = purpose,
            hasSessionLimit = capsuleHasSessionLimit.value,
            sessionSeconds = capsuleSessionSeconds.value,
            completedChecks = capsuleFocusChecksCompleted,
            sessionLimitMinutes = capsuleSessionLimitMinutes.value,
            practiceEnabled = capsuleAwarenessPracticeEnabled.value,
            gapSec = capsuleAwarenessPracticeGapSec.value,
        )
        val variant = capsuleFocusChecksCompleted
        companionInspectGlance.value = CompanionInspectGlance(
            packageName = capsuleAppPackageName.value,
            appName = capsuleAppName.value,
            purpose = purpose.takeIf { it.isNotEmpty() },
            sessionSeconds = capsuleSessionSeconds.value,
            todayTotalSeconds = capsuleTodayTotalSeconds.value,
            todayEnterCount = capsuleTodayEnterCount.value,
            sessionLimitMinutes = capsuleSessionLimitMinutes.value,
            hasSessionLimit = capsuleHasSessionLimit.value,
            sessionRemainingSeconds = capsuleDailyRemainingSeconds.value,
            hasIntentGate = capsuleHasIntentGate.value,
            showEnd = capsuleHasIntentGate.value || resolved.isPureMusic,
            endIsMusicExit = resolved.isPureMusic && !capsuleHasIntentGate.value,
            awarenessPrompt = if (showAsk) {
                SessionAwarenessCopy.midCheckPrompt(
                    mode = mode,
                    naming = purpose.takeIf { it.isNotEmpty() },
                    variant = variant,
                    path = path,
                )
            } else null,
            awarenessYes = if (showAsk) SessionAwarenessCopy.midCheckYes(mode, path) else null,
            awarenessNo = if (showAsk) SessionAwarenessCopy.midCheckNo(mode, path) else null,
            selectedMode = CompanionScene.inspectMode(prefs).storageKey,
            opacity = capsuleShellOpacity.value,
            miniCompact = capsuleMiniCompact.value,
            capsuleLeft = loc.getOrElse(0) { 0 },
            capsuleTop = loc.getOrElse(1) { 0 },
            capsuleWidth = view?.width?.takeIf { it > 0 } ?: collapsedCapsuleApproxWidthPx(),
            capsuleHeight = view?.height?.takeIf { it > 0 } ?: collapsedCapsuleApproxHeightPx(),
            screenWidth = overlayScreenWidthPx(),
            screenHeight = overlayScreenHeightPx(),
            topInset = capsuleTopRowYPx(),
            bottomInset = bottomInset,
        )
    }

    private fun ensureCompanionInspectView() {
        if (companionInspectView != null) {
            companionInspectView?.visibility = View.VISIBLE
            return
        }
        refreshOverlayThemePack()
        val composeView = createComposeView {
            val livePack = overlayThemePack.value
            CompanionInspectOverlay(
                open = companionInspectOpen,
                glance = companionInspectGlance,
                topApps = desktopAnchorTopApps,
                todayMonitorTotalSeconds = desktopAnchorTodayTotalSeconds,
                todayEnterCount = desktopAnchorTodayEnterCount,
                todayDismissCount = desktopAnchorTodayDismissCount,
                themePack = livePack,
                isDarkTheme = livePack.isDark,
                onDismiss = { collapseCompanionInspect() },
                onOpacityChange = { opacity -> applyCompanionInspectOpacity(opacity) },
                onMiniCompactChange = { compact ->
                    setCapsuleMiniCompactFromPanel(compact)
                    refreshCompanionInspectGlance()
                },
                onEndSession = {
                    val musicExit = companionInspectGlance.value?.endIsMusicExit == true
                    forceCloseCompanionInspect()
                    if (musicExit) {
                        finishManualEndSession(null, null, null, false)
                    } else {
                        capsuleRequestEnd?.invoke()
                    }
                },
                onOpenTodayReceipt = {
                    forceCloseCompanionInspect()
                    onOpenUsageLogFromDesktop?.invoke()
                },
                onOpenHeartAnchor = {
                    forceCloseCompanionInspect()
                    onOpenHeartAnchorFromDesktop?.invoke()
                },
                onAwarenessStill = {
                    markInspectAwarenessAnswered(still = true)
                    collapseCompanionInspect()
                },
                onAwarenessDrift = {
                    markInspectAwarenessAnswered(still = false)
                    forceCloseCompanionInspect()
                    capsuleRequestEnd?.invoke()
                },
            )
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        companionInspectParams = params
        val host = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK ||
                    event.keyCode == KeyEvent.KEYCODE_ESCAPE
                ) {
                    if (event.action == KeyEvent.ACTION_UP) collapseCompanionInspect()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
        val hostLifecycleOwner = OverlayLifecycleOwner().also { it.start() }
        host.setViewTreeLifecycleOwner(hostLifecycleOwner)
        host.setViewTreeViewModelStoreOwner(object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        })
        host.setViewTreeSavedStateRegistryOwner(hostLifecycleOwner)
        host.addView(
            composeView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        try {
            windowManager.addView(host, params)
            companionInspectView = host
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            windowManager.updateViewLayout(host, params)
            host.requestFocus()
        } catch (e: Exception) {
            Log.e(TAG, "挂陪伴卡片失败", e)
            companionInspectView = null
            companionInspectParams = null
            companionInspectOpen.value = false
        }
    }

    private fun removeCompanionInspectView() {
        companionInspectView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) { /* ignore */ }
        }
        companionInspectView = null
        companionInspectParams = null
    }

    private fun restackCapsuleAboveInspect() {
        val cap = capsuleView ?: return
        val params = capsuleParams ?: return
        val keepX = params.x
        val keepY = params.y
        // 拦掉紧随其后的 LayoutChange clamp，避免开面板时条上跳
        suppressCapsuleLayoutClampUntilElapsed =
            android.os.SystemClock.elapsedRealtime() + 480L
        try {
            windowManager.removeView(cap)
            params.x = keepX
            params.y = keepY
            windowManager.addView(cap, params)
            // 再钉一次：部分机型 add 后会按 gravity 重算一帧
            mainHandler.post {
                val view = capsuleView ?: return@post
                val lp = capsuleParams ?: return@post
                if (lp.x == keepX && lp.y == keepY) return@post
                lp.x = keepX
                lp.y = keepY
                try {
                    windowManager.updateViewLayout(view, lp)
                } catch (_: Exception) { /* ignore */ }
            }
        } catch (e: Exception) {
            Log.w(TAG, "陪伴条置顶失败", e)
        }
    }

    private fun applyCompanionInspectMode(mode: CompanionAppMode) {
        val pkg = capsuleAppPackageName.value
        if (pkg.isBlank()) return
        appPreferences.setCompanionAppMode(pkg, mode.storageKey)
        refreshCapsuleCompanionPrefs()
        refreshCompanionInspectGlance()
        mainHandler.post {
            val view = capsuleView ?: return@post
            rememberCollapsedCapsuleSize(view)
            val (w, h) = collapsedCapsuleSizePx(view)
            val params = capsuleParams ?: return@post
            val next = clampCapsuleFloat(params.x, params.y, w, h)
            if (params.x != next.first || params.y != next.second) {
                params.x = next.first
                params.y = next.second
                try { windowManager.updateViewLayout(view, params) } catch (_: Exception) { }
            }
            refreshCompanionInspectGlance()
        }
    }

    private fun applyCompanionInspectOpacity(opacity: Float) {
        appPreferences.setCapsuleShellOpacity(opacity)
        capsuleShellOpacity.value = appPreferences.getCapsuleShellOpacity()
        val g = companionInspectGlance.value ?: return
        companionInspectGlance.value = g.copy(opacity = capsuleShellOpacity.value)
    }

    /** 展开 ↔ 收起，并同步窗口位置（展开钉顶 / 收起回悬浮点） */
    private fun toggleCapsuleExpanded() {
        forceCloseCompanionInspect()
        closeIntentSessionHub()
        val next = !capsuleExpanded.value
        capsuleExpanded.value = next
        syncCapsuleHorizontalForExpansion(next)
    }

    /** 跑马灯 / 对照岛请求钉顶（不经 expanded 亦可） */
    private fun setCapsuleTopPinned(pin: Boolean) {
        val view = capsuleView ?: return
        val params = capsuleParams ?: return
        if (capsuleTopPinned == pin) {
            if (pin) restoreCapsuleIslandPosition(view, params, animate = false)
            return
        }
        capsuleTopPinned = pin
        if (pin) closeIntentSessionHub()
        if (pin || capsuleExpanded.value) {
            // 钉顶前若仍在悬浮态，先记下悬浮点
            if (pin && !capsuleExpanded.value) {
                captureFloatPositionFromWindow(params)
            }
            restoreCapsuleIslandPosition(view, params, animate = true)
        } else {
            captureIslandPositionFromWindow(params)
            restoreCapsuleFloatPosition(view, params, animate = true)
        }
    }

    /**
     * 贴条锚提示出现 / 消失时调整窗口 y，让陪伴条屏幕位置不变。
     */
    private fun applyCapsuleAttachedTopInset(insetPx: Int) {
        val next = insetPx.coerceAtLeast(0)
        val delta = next - capsuleAttachedTopInsetPx
        capsuleAttachedTopInsetPx = next
        if (delta == 0) return
        val view = capsuleView ?: return
        val params = capsuleParams ?: return
        params.y = (params.y - delta).coerceAtLeast(0)
        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: Exception) { /* ignore */ }
    }

    private fun removeIntentRunwayInternal() {
        intentRunwayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) { }
            intentRunwayView = null
        }
    }

    /** 结束确认 / 续时弹窗：窗口水平居中并回到上排 */
    private fun centerCapsuleForDialog() {
        val view = capsuleView ?: return
        val params = capsuleParams ?: return
        mainHandler.removeCallbacks(snapAfterCollapseRunnable)
        mainHandler.removeCallbacks(finishExpandAnchorRunnable)
        capsuleSnapAnimator?.cancel()
        // 弹窗用临时顶中，不覆盖岛/悬浮记忆；缓动过去，避免贴边瞬移抖一下
        animateOrSetCapsule(view, params, 0, capsuleTopRowYPx(), animate = true)
    }

    /** 弹窗关闭且仍为收起态：还原自由悬浮点 */
    private fun restoreCapsuleAfterDialog() {
        val view = capsuleView ?: return
        val params = capsuleParams ?: return
        if (!capsuleExpanded.value && !capsuleTopPinned) {
            restoreCapsuleFloatPosition(view, params, animate = true)
        } else if (capsuleExpanded.value || capsuleTopPinned) {
            restoreCapsuleIslandPosition(view, params, animate = true)
        }
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

    /**
     * 展开 / 收起时窗口位置：
     * - 展开：回到记忆的灵动岛位置（默认顶栏居中）
     * - 收起：回到记忆的自由悬浮坐标
     * 两套坐标互不覆盖。
     */
    private fun syncCapsuleHorizontalForExpansion(expanded: Boolean) {
        val view = capsuleView ?: return
        val params = capsuleParams ?: return
        mainHandler.removeCallbacks(snapAfterCollapseRunnable)
        mainHandler.removeCallbacks(finishExpandAnchorRunnable)
        capsuleSnapAnimator?.cancel()
        if (expanded) {
            captureFloatPositionFromWindow(params)
            capsuleCollapseSettling = false
            restoreCapsuleIslandPosition(view, params, animate = true)
            mainHandler.postDelayed(finishExpandAnchorRunnable, 420L)
        } else {
            captureIslandPositionFromWindow(params)
            capsuleCollapseSettling = true
            if (!capsuleTopPinned) {
                restoreCapsuleFloatPosition(view, params, animate = true)
            }
            mainHandler.postDelayed(snapAfterCollapseRunnable, 420L)
        }
    }

    /** 收起态近似宽：clamp 悬浮坐标时绝不用展开岛宽，否则会把记忆 X 挤回 0 */
    private fun collapsedCapsuleApproxWidthPx(): Int {
        val density = context.resources.displayMetrics.density
        if (!resolvedCompanion().showBar) {
            val shell = floatingOrbMetrics(appPreferences.isCapsuleMiniCompact()).shell.value
            return ((shell + CapsuleOuterPadH.value * 2f) * density).toInt()
        }
        val typicalW =
            (CompanionCollapsedWidthMin.value + CompanionCollapsedWidthMax.value) / 2f
        return ((typicalW + CapsuleOuterPadH.value * 2f) * density).toInt()
    }

    private fun collapsedCapsuleApproxHeightPx(): Int {
        val density = context.resources.displayMetrics.density
        if (!resolvedCompanion().showBar) {
            val shell = floatingOrbMetrics(appPreferences.isCapsuleMiniCompact()).shell.value
            return ((shell + CapsuleOuterPadTop.value + CapsuleOuterPadBottom.value) * density).toInt()
        }
        val barH = companionBarMetrics(
            compact = appPreferences.isCapsuleMiniCompact(),
            light = resolvedCompanion().form == CompanionBarForm.LIGHT
        ).height.value
        return ((barH + CapsuleOuterPadTop.value + CapsuleOuterPadBottom.value) * density).toInt()
    }

    private fun rememberCollapsedCapsuleSize(view: View) {
        if (capsuleExpanded.value || capsuleTopPinned || capsuleCollapseSettling) return
        if (view.width > 0) capsuleCollapsedMeasuredW = view.width
        if (view.height > 0) capsuleCollapsedMeasuredH = view.height
    }

    /** 收起壳尺寸：悬浮态用实测，展开中用缓存，避免岛宽污染 X */
    private fun collapsedCapsuleSizePx(view: View? = capsuleView): Pair<Int, Int> {
        val live = view != null &&
            !capsuleExpanded.value &&
            !capsuleTopPinned &&
            !capsuleCollapseSettling
        val w = view?.width?.takeIf { live && it > 0 }
            ?: capsuleCollapsedMeasuredW.takeIf { it > 0 }
            ?: collapsedCapsuleApproxWidthPx()
        val h = view?.height?.takeIf { live && it > 0 }
            ?: capsuleCollapsedMeasuredH.takeIf { it > 0 }
            ?: collapsedCapsuleApproxHeightPx()
        return w to h
    }

    private fun islandCapsuleApproxWidthPx(): Int {
        val density = context.resources.displayMetrics.density
        val screenW = overlayScreenWidthPx()
        // 与 expandedIslandWidth 同量级，用于岛坐标 clamp
        return ((screenW / density) * 0.92f * density).toInt().coerceAtLeast(
            ((280f + 16f) * density).toInt()
        )
    }

    private fun captureFloatPositionFromWindow(params: WindowManager.LayoutParams) {
        val (w, h) = collapsedCapsuleSizePx()
        val inset = capsuleAttachedTopInsetPx
        val barH = (h - inset).coerceAtLeast(1)
        val barY = params.y + inset
        val (x, y) = clampCapsuleFloat(params.x, barY, w, barH)
        capsuleFloatOffsetX = x
        capsuleFloatOffsetY = y
        val pkg = capsuleAppPackageName.value
        if (pkg.isNotBlank()) {
            appPreferences.setCapsuleFloatOffsetForPackage(pkg, x, y)
        }
    }

    private fun captureIslandPositionFromWindow(params: WindowManager.LayoutParams) {
        val topMin = capsuleTopRowYPx()
        val (x, y) = clampCapsuleFloat(
            params.x,
            params.y.coerceAtLeast(topMin),
            islandCapsuleApproxWidthPx(),
            collapsedCapsuleApproxHeightPx()
        )
        capsuleIslandOffsetX = x
        capsuleIslandOffsetY = y
    }

    private fun restoreCapsuleIslandPosition(
        view: View,
        params: WindowManager.LayoutParams,
        animate: Boolean
    ) {
        val topMin = capsuleTopRowYPx()
        val x = capsuleIslandOffsetX
        val y = capsuleIslandOffsetY.coerceAtLeast(topMin)
        animateOrSetCapsule(view, params, x, y, animate)
    }

    private fun pinCapsuleToTopCenter(
        view: View,
        params: WindowManager.LayoutParams,
        animate: Boolean
    ) {
        // 兼容旧调用：重置岛记忆为顶栏居中并落到该点
        capsuleIslandOffsetX = 0
        capsuleIslandOffsetY = capsuleTopRowYPx()
        restoreCapsuleIslandPosition(view, params, animate)
    }

    private fun restoreCapsuleFloatPosition(
        view: View,
        params: WindowManager.LayoutParams,
        animate: Boolean
    ) {
        val (w, h) = collapsedCapsuleSizePx(view)
        val inset = capsuleAttachedTopInsetPx
        val barH = (h - inset).coerceAtLeast(1)
        val (x, y) = clampCapsuleFloat(capsuleFloatOffsetX, capsuleFloatOffsetY, w, barH)
        // 仅用收起宽度做展示 clamp；写回记忆也用收起宽，避免被岛宽污染
        capsuleFloatOffsetX = x
        capsuleFloatOffsetY = y
        // 窗口顶 = 条顶 - 贴条锚提示高度
        animateOrSetCapsule(view, params, x, (y - inset).coerceAtLeast(0), animate)
    }

    private fun animateOrSetCapsule(
        view: View,
        params: WindowManager.LayoutParams,
        x: Int,
        y: Int,
        animate: Boolean
    ) {
        if (animate) {
            animateCapsuleTo(view, params, x, y, minDuration = 220L, maxDuration = 380L)
        } else {
            params.x = x
            params.y = y
            try {
                windowManager.updateViewLayout(view, params)
            } catch (_: Exception) { /* ignore */ }
        }
    }

    private fun loadCapsuleFloatPosition(packageName: String) {
        val (approxW, approxH) = collapsedCapsuleSizePx()
        val stored = appPreferences.getCapsuleFloatOffsetForPackage(packageName)
        if (stored != null) {
            val (x, y) = clampCapsuleFloat(stored.first, stored.second, approxW, approxH)
            capsuleFloatOffsetX = x
            capsuleFloatOffsetY = y
            return
        }
        // 兼容旧六格停靠：映射为初始悬浮点
        val dock = appPreferences.getCapsuleDockPositionForPackage(packageName)
        val screenWidth = overlayScreenWidthPx()
        val x = when (AppPreferences.capsuleDockColumn(dock)) {
            AppPreferences.CAPSULE_DOCK_CENTER -> 0
            AppPreferences.CAPSULE_DOCK_RIGHT -> (screenWidth - approxW) / 2
            else -> -(screenWidth - approxW) / 2
        }
        val top = capsuleTopRowYPx()
        val y = if (AppPreferences.isCapsuleDockLowerRow(dock)) {
            top + capsuleLowerRowOffsetPx()
        } else {
            top
        }
        val clamped = clampCapsuleFloat(x, y, approxW, approxH)
        capsuleFloatOffsetX = clamped.first
        capsuleFloatOffsetY = clamped.second
    }

    private fun saveCapsuleFloatPosition(
        offsetX: Int,
        offsetY: Int,
        viewWidth: Int,
        viewHeight: Int
    ) {
        val (x, y) = clampCapsuleFloat(
            offsetX,
            offsetY,
            viewWidth.coerceAtLeast(1),
            viewHeight.coerceAtLeast(1)
        )
        capsuleFloatOffsetX = x
        capsuleFloatOffsetY = y
        appPreferences.setCapsuleFloatOffsetForPackage(capsuleAppPackageName.value, x, y)
    }

    private data class OverlaySafeInsets(
        val navBottom: Int,
        val gestureLeft: Int,
        val gestureRight: Int,
        val gestureBottom: Int,
        val cutoutLeft: Int,
        val cutoutRight: Int,
        val cutoutBottom: Int
    )

    private fun overlaySafeInsets(): OverlaySafeInsets {
        val density = context.resources.displayMetrics.density
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wi = windowManager.currentWindowMetrics.windowInsets
            val nav = wi.getInsetsIgnoringVisibility(
                android.view.WindowInsets.Type.navigationBars()
            )
            val gestures = wi.getInsetsIgnoringVisibility(
                android.view.WindowInsets.Type.systemGestures()
            )
            val cutout = wi.getInsetsIgnoringVisibility(
                android.view.WindowInsets.Type.displayCutout()
            )
            return OverlaySafeInsets(
                navBottom = nav.bottom,
                gestureLeft = gestures.left,
                gestureRight = gestures.right,
                gestureBottom = gestures.bottom,
                cutoutLeft = cutout.left,
                cutoutRight = cutout.right,
                cutoutBottom = cutout.bottom
            )
        }
        return OverlaySafeInsets(
            navBottom = (24f * density).toInt(),
            gestureLeft = (20f * density).toInt(),
            gestureRight = (20f * density).toInt(),
            gestureBottom = 0,
            cutoutLeft = 0,
            cutoutRight = 0,
            cutoutBottom = 0
        )
    }

    /**
     * 任意区域悬停：按真实壳尺寸夹到安全区。
     * 左右至少 16dp，且不小于系统返回手势 / 刘海 inset，避免贴边抢返回。
     * 底边只用 nav/gesture/cutout + 16dp，不再叠死 56dp 底栏避让。
     */
    private fun clampCapsuleFloat(
        offsetX: Int,
        offsetY: Int,
        viewWidth: Int,
        viewHeight: Int
    ): Pair<Int, Int> {
        val density = context.resources.displayMetrics.density
        val screenWidth = overlayScreenWidthPx()
        val screenHeight = overlayScreenHeightPx()
        val insets = overlaySafeInsets()
        val edgeX = maxOf(
            (16f * density).toInt(),
            insets.gestureLeft,
            insets.gestureRight,
            insets.cutoutLeft,
            insets.cutoutRight
        )
        val topMin = capsuleTopRowYPx()
        val bottomClearance =
            maxOf(insets.navBottom, insets.gestureBottom, insets.cutoutBottom) +
                (16f * density).toInt()
        val viewW = viewWidth.coerceAtLeast(1)
        val viewH = viewHeight.coerceAtLeast(1)
        val yMax = (screenHeight - bottomClearance - viewH).coerceAtLeast(topMin)
        val maxAbsX = ((screenWidth - viewW) / 2 - edgeX).coerceAtLeast(0)
        val x = offsetX.coerceIn(-maxAbsX, maxAbsX)
        val y = offsetY.coerceIn(topMin, yMax)
        return x to y
    }

    private fun overlayScreenHeightPx(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.height()
        } else {
            @Suppress("DEPRECATION")
            context.resources.displayMetrics.heightPixels
        }
    }

        /** 只动 Y，X 交给停靠列跟随，避免两套动画抢 params.x */
    private fun animateCapsuleYTo(
        view: View,
        params: WindowManager.LayoutParams,
        targetY: Int,
        minDuration: Long = 80L,
        maxDuration: Long = 280L
    ) {
        capsuleSnapAnimator?.cancel()
        val startY = params.y
        if (startY == targetY) return
        val distance = kotlin.math.abs(targetY - startY)
        val duration = (distance / 100f * 40f).toLong().coerceIn(minDuration, maxDuration)
        capsuleSnapAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator(1.55f)
            addUpdateListener { animator ->
                val t = animator.animatedValue as Float
                params.y = (startY + (targetY - startY) * t).toInt()
                try {
                    windowManager.updateViewLayout(view, params)
                } catch (_: Exception) { cancel() }
            }
            start()
        }
    }

    /** 入场未落定则排队，避免时长锁临近时一进 App 就与迷你入场叠出错位 */
    private fun runOrDeferLimitAutoExpand(action: () -> Unit) {
        if (capsuleMiniSettled) {
            action()
        } else {
            pendingLimitAutoExpand = action
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
        var budgetTick = 0
        capsuleUpdateJob = scope.launch {
            while (true) {
                // 限额刷新走低频，避免每次 tick 被 DB IO 拉长导致秒数连跳
                budgetTick++
                if (budgetTick % 5 == 1) {
                    runCatching { sessionManager.refreshLiveBudget() }
                }
                // 每次刷新时从 SessionManager 读取最新 session（含 isInBackground 状态），
                // 避免使用快照导致后台/暂停期间计时继续增长。
                val s = sessionManager.currentSession.value ?: capsuleSession
                if (s != null) {
                    capsuleSession = s
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
                    capsuleDailyBaseLimitSeconds.value =
                        if (s.hasSessionLimit) 0L else s.displayDailyBaseLimitSeconds
                    capsuleDailyGraceBonusSeconds.value =
                        if (s.hasSessionLimit) 0L else s.dailyGraceBonusSeconds
                    capsuleHasSessionLimit.value = s.hasSessionLimit
                    capsuleCanExtend.value = s.canOfferSessionExtension
                    capsuleSessionLimitMinutes.value = s.extensionBaseMinutes
                    capsuleHasTimeLock.value = s.hasTimeLock || s.hasSessionLimit
                    capsuleTodayEnterCount.value = s.todayEnterCount
                    capsuleTodayTotalSeconds.value = s.todayTotalSeconds
                    capsuleSessionOriginStartMs.value = s.sessionOriginStartMs
                    capsuleShowUsedSeconds.value = appPreferences.isCapsuleUsedShowSeconds()
                    capsuleMiniCompact.value = appPreferences.isCapsuleMiniCompact()
                    capsuleShellOpacity.value = appPreferences.getCapsuleShellOpacity()
                    syncAwarenessPracticePrefs()
                    refreshCapsuleCompanionPrefs()
                    if (companionInspectOpen.value) {
                        refreshCompanionInspectGlance()
                    }
                    if (intentSessionHub.value != null) {
                        refreshIntentSessionHubGlance()
                        val pulse = desktopAnchorPulse.value
                        if (pulse != null && pulse.packageName == s.packageName && pulse.live) {
                            desktopAnchorPulse.value = pulse.copy(
                                sessionSeconds = activeSeconds,
                                todayTotalSeconds = capsuleTodayTotalSeconds.value
                            )
                        }
                    }

                    // 日限额临近洗灰（仅日预算；暂停 / 超限续记不刷）
                    syncDailyLimitGrayWash(s)

                    // ── 预警：优先会话预算，否则日预算；非超限续记 ──────────
                    // 意图门单次时长：剩余 1 分钟且可续时，展开胶囊给出续时入口
                    val hasBudget = s.hasSessionLimit || s.dailyLimitSeconds > 0
                    val intentSessionBudget = s.hasIntentGate && s.hasSessionLimit
                    if (intentSessionBudget && !s.isOverLimitSession) {
                        if (!countdownStarted && remaining in 1L..60L && s.canOfferSessionExtension) {
                            countdownStarted = true
                            mainHandler.post {
                                runOrDeferLimitAutoExpand {
                                    closeIntentSessionHub()
                                    capsuleExpanded.value = true
                                    syncCapsuleHorizontalForExpansion(true)
                                    capsuleStartCountdown?.invoke()
                                }
                            }
                        }
                        if (countdownStarted && remaining > 60L) {
                            countdownStarted = false
                        }
                    } else if (hasBudget && !s.isOverLimitSession && !intentSessionBudget) {
                        // 5 分钟：迷你态提醒一次，不再展开日常大条
                        if (!fiveMinWarned && remaining in 1L..300L) {
                            fiveMinWarned = true
                            mainHandler.post {
                                closeIntentSessionHub()
                                capsuleWarnFiveMin?.invoke()
                            }
                        }
                        // 最后 1 分钟：迷你态变色，点开后再给续时入口
                        if (fiveMinWarned && remaining > 300L) {
                            fiveMinWarned = false
                        }
                    }
                    // 对齐到下一整秒边界，避免 delay(1000)+IO 导致 5→7 连跳
                    val msIntoSecond = (s.currentActiveMs % 1000L).toInt()
                    delay((1000L - msIntoSecond).coerceIn(50L, 1000L))
                } else {
                    delay(1000L)
                }
            }
        }
    }

    /**
     * 悬浮窗拖拽必须用 [WindowManager.updateViewLayout] 跟手。
     *
     * 用 [FrameLayout.onInterceptTouchEvent] 在 Compose 子 View 之前抢走手势。
     * 迷你态默认拦截（拖拽 + 点按展开）；落在「结束」命中区的 DOWN 不拦截，
     * 交给 Compose clickable。展开态 / 弹窗 / 入场未落定：全程不拦截。
     *
     * 坐标系：gravity 含 [Gravity.CENTER_HORIZONTAL]，params.x 为相对屏幕水平中心的偏移。
     */
    private fun createCapsuleTouchHost(
        composeView: ComposeView,
        params: WindowManager.LayoutParams,
        onClick: () -> Unit
    ): View {
        val host = object : FrameLayout(context) {
            private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDragging = false
            private var downOnStop = false

            private fun rememberTouchDown(ev: MotionEvent) {
                capsuleSnapAnimator?.cancel()
                mainHandler.removeCallbacks(snapAfterCollapseRunnable)
                mainHandler.removeCallbacks(finishExpandAnchorRunnable)
                capsuleCollapseSettling = false
                capsuleFloatDragging = false
                initialX = params.x
                initialY = params.y
                initialTouchX = ev.rawX
                initialTouchY = ev.rawY
                translationX = 0f
                translationY = 0f
                isDragging = false
                downOnStop = false
            }

            private fun isOnStop(ev: MotionEvent): Boolean {
                val stopHit = capsuleStopHitRect ?: return false
                return stopHit.contains(ev.rawX, ev.rawY)
            }

            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    if (!capsuleEndDialogOpen) {
                        when {
                            capsuleSkipEntrance != null -> capsuleSkipEntrance?.invoke()
                            // Session Hub 在独立全屏层上操作，不能当「点外面」收掉
                            intentSessionHub.value != null -> Unit
                            capsuleExpanded.value -> toggleCapsuleExpanded()
                        }
                    }
                    return true
                }
                return super.dispatchTouchEvent(ev)
            }

            override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
                // 确认 / 续时弹窗 / 灵动岛（含收起落定前）：交给 Compose，禁止 Window 拖拽
                if (isCapsuleDragLocked()) return false
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        rememberTouchDown(ev)
                        downOnStop = isOnStop(ev)
                        // 结束方块交给 Compose clickable；其余仍由 Window 拖拽 / 点按
                        return !downOnStop
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!downOnStop) return true
                        val dx = ev.rawX - initialTouchX
                        val dy = ev.rawY - initialTouchY
                        if (kotlin.math.abs(dx) > touchSlop ||
                            kotlin.math.abs(dy) > touchSlop
                        ) {
                            downOnStop = false
                            return true
                        }
                        return false
                    }
                    else -> return !downOnStop
                }
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (isCapsuleDragLocked()) return false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        rememberTouchDown(event)
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (isDragging ||
                            kotlin.math.abs(dx) > touchSlop ||
                            kotlin.math.abs(dy) > touchSlop
                        ) {
                            if (!isDragging) {
                                isDragging = true
                                capsuleFloatDragging = true
                            }
                            rememberCollapsedCapsuleSize(this)
                            val (w, h) = collapsedCapsuleSizePx(this)
                            val next = clampCapsuleFloat(initialX + dx, initialY + dy, w, h)
                            params.x = next.first
                            params.y = next.second
                            try {
                                windowManager.updateViewLayout(this, params)
                            } catch (_: Exception) { /* ignore */ }
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val wasDragging = isDragging
                        isDragging = false
                        capsuleFloatDragging = false
                        if (wasDragging) {
                            rememberCollapsedCapsuleSize(this)
                            val (w, h) = collapsedCapsuleSizePx(this)
                            saveCapsuleFloatPosition(params.x, params.y, w, h)
                            if (params.x != capsuleFloatOffsetX || params.y != capsuleFloatOffsetY) {
                                animateOrSetCapsule(
                                    this,
                                    params,
                                    capsuleFloatOffsetX,
                                    capsuleFloatOffsetY,
                                    animate = true
                                )
                            }
                        } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                            capsuleWakeUp?.invoke()
                            val stopHit = capsuleStopHitRect
                            if (stopHit != null && stopHit.contains(event.rawX, event.rawY)) {
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
        host.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            rememberCollapsedCapsuleSize(v)
            if (capsuleFloatDragging || isCapsuleDragLocked()) return@addOnLayoutChangeListener
            if (android.os.SystemClock.elapsedRealtime() < suppressCapsuleLayoutClampUntilElapsed) {
                return@addOnLayoutChangeListener
            }
            val lp = capsuleParams ?: return@addOnLayoutChangeListener
            val (w, h) = collapsedCapsuleSizePx(v)
            val (x, y) = clampCapsuleFloat(lp.x, lp.y, w, h)
            if (x != lp.x || y != lp.y) {
                saveCapsuleFloatPosition(x, y, w, h)
                lp.x = x
                lp.y = y
                try {
                    windowManager.updateViewLayout(v, lp)
                } catch (_: Exception) { /* ignore */ }
            }
        }
        return host
    }

    /**
     * 上排常驻 Y：贴状态栏下缘再留约 4dp。
     * 不用塞进状态栏内部，避免与系统图标抢位。
     */
    private fun capsuleTopRowYPx(): Int {
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

    /** 下排相对上排的额外下移（略紧凑，少挡标题区） */
    private fun capsuleLowerRowOffsetPx(): Int {
        val density = context.resources.displayMetrics.density
        return (46f * density).toInt()
    }

    /** 与浮窗共用的屏宽 */
    private fun capsuleDockScreenWidthPx(): Int = overlayScreenWidthPx()

    /** API 30+ 用 WindowMetrics；更低系统回退 displayMetrics */
    private fun overlayScreenWidthPx(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.width()
        } else {
            @Suppress("DEPRECATION")
            context.resources.displayMetrics.widthPixels
        }
    }

    /** 拦截退场动画用的圆球默认落点绝对中心 X（屏幕坐标） */
    private fun capsuleFloatAbsoluteCenterX(packageName: String = capsuleAppPackageName.value): Float {
        val screenWidth = overlayScreenWidthPx()
        loadCapsuleFloatPosition(packageName)
        return screenWidth / 2f + capsuleFloatOffsetX
    }

    private fun removeDockGuideViewInternal() {
        dockGuideView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) { }
            dockGuideView = null
        }
        dockGuideParams = null
        dockGuideVisible.value = false
        dockGuideHighlight.value = null
    }

    private fun scheduleRemoveInterceptView(delayMs: Long) {
        val generation = ++interceptRemoveGeneration
        val remove = Runnable {
            if (generation != interceptRemoveGeneration) return@Runnable
            removeInterceptViewInternal()
        }
        if (delayMs <= 0L) {
            mainHandler.post(remove)
        } else {
            mainHandler.postDelayed(remove, delayMs)
        }
    }

    private fun removeInterceptViewInternal() {
        stopInterceptLayerRefresh()
        val hadView = interceptView != null
        interceptView?.let {
            // 卸窗前清掉硬件层缓冲，减轻部分 OEM 上的透明残影
            it.alpha = 0f
            it.setLayerType(android.view.View.LAYER_TYPE_NONE, null)
            try { windowManager.removeView(it) } catch (e: Exception) { }
            interceptView = null
        }
        interceptParams = null
        interceptContentIsCompose = false
        interceptLimitTheme = false
        attachedInterceptKind = null
        onInterceptComposeBack = null
        setDesktopAnchorSceneHidden(false)
        if (hadView) {
            onInterceptLayerRemoved?.invoke()
        }
    }

    private fun removeCeremonyViewInternal() {
        ceremonyView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) { }
            ceremonyView = null
        }
    }

    private fun removeCapsuleViewInternal() {
        forceCloseCompanionInspect()
        closeIntentSessionHub()
        capsuleUpdateJob?.cancel()
        capsuleSession = null
        capsulePlayEnterAnimation.value = false
        capsuleSoftReveal.value = false
        capsuleExpanded.value = false
        capsulePurpose.value = null
        capsuleIntentKind.value = null
        capsuleWakeUp = null
        capsuleShowConfirm = null
        capsuleWarnFiveMin = null
        capsuleStartCountdown = null
        capsuleSkipEntrance = null
        capsuleIsPaused.value = false
        capsuleIsOverLimit.value = false
        capsuleDailyGraceBonusSeconds.value = 0L
        capsuleDailyBaseLimitSeconds.value = 0L
        capsuleHasIntentGate.value = true
        capsuleCompareEnabled.value = true
        capsuleCompareMinMinutes.value = 10
        capsuleHasTimeLock.value = true
        capsuleHasSessionLimit.value = false
        capsuleCanExtend.value = false
        capsuleSessionLimitMinutes.value = 0
        capsuleAwayCountdownSeconds.value = -1L
        capsuleAwayCountdownTotalSeconds.value = 0L
        returnToAppCallback = null
        mainHandler.removeCallbacks(snapAfterCollapseRunnable)
        mainHandler.removeCallbacks(finishExpandAnchorRunnable)
        capsuleCollapseSettling = false
        capsuleMiniSettled = false
        pendingLimitAutoExpand = null
        capsuleSnapAnimator?.cancel()
        capsuleSnapAnimator = null
        capsuleTopPinned = false
        capsuleIslandOffsetX = 0
        capsuleIslandOffsetY = 0
        capsuleCollapsedMeasuredW = 0
        capsuleCollapsedMeasuredH = 0
        capsuleFloatDragging = false
        capsuleParams = null
        capsuleRequestEnd = null
        capsuleRequestCheckFocus = null
        capsuleApplyExternalAwareness = null
        capsuleAttachedTopInsetPx = 0
        capsuleStopHitRect = null
        capsuleEndDialogOpen = false
        isCapsuleDialogBlocking.set(false)
        removeIntentRunwayInternal()
        removeDockGuideViewInternal()
        releaseDailyLimitGrayscale()
        capsuleView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) { }
            capsuleView = null
        }
        onCapsuleRemovedForDesktopAnchor()
    }

    /**
     * 日限额临近 → 系统真灰度（整屏去饱和）。
     * 半透明 overlay 去不掉底下彩色，故不走灰幕。
     */
    private fun syncDailyLimitGrayWash(session: UsageSession) {
        val forceDebug = BuildConfig.DEBUG && appPreferences.isDebugForceGrayWashEnabled()
        val wantGray = when {
            capsuleIsPaused.value -> false
            forceDebug -> true
            session.dailyLimitSeconds <= 0L -> false
            else -> {
                val pkg = session.packageName
                if (appPreferences.hasDailyLimitGrayWashToday(pkg)) {
                    true
                } else {
                    val baseLimit = session.displayDailyBaseLimitSeconds
                        .takeIf { it > 0L }
                        ?: session.dailyLimitSeconds
                    val remaining = session.dailyRemainingSeconds.let {
                        if (it == Long.MAX_VALUE) Long.MAX_VALUE else it.coerceAtLeast(0L)
                    }
                    if (remaining == Long.MAX_VALUE ||
                        !DailyLimitGrayWashPolicy.shouldTrigger(baseLimit, remaining)
                    ) {
                        false
                    } else {
                        appPreferences.markDailyLimitGrayWashStarted(pkg)
                        true
                    }
                }
            }
        }

        if (!wantGray) {
            displayGrayscaleController.setGrayscaleActive(false)
            return
        }

        val ok = displayGrayscaleController.setGrayscaleActive(true)
        if (!ok && !grayScaleMissingPermLogged) {
            grayScaleMissingPermLogged = true
            Log.w(
                TAG,
                "系统真灰度需要 WRITE_SECURE_SETTINGS。" +
                    " Debug: adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
            )
        }
        if (ok) grayScaleMissingPermLogged = false
    }

    /** 设置页切换 Debug 强制灰度后立刻对齐当前会话 */
    fun resyncDailyLimitGrayWash() {
        mainHandler.post {
            val session = capsuleSession ?: sessionManager.currentSession.value
            if (session == null) {
                displayGrayscaleController.setGrayscaleActive(false)
                return@post
            }
            syncDailyLimitGrayWash(session)
        }
    }

    private fun releaseDailyLimitGrayscale() {
        displayGrayscaleController.setGrayscaleActive(false)
    }

    /**
     * 手动结束会话的统一收口：写入 note / 正念程度（可选）、移除胶囊、回调 Service。
     * @param openToAnchor 为 true 时走「结束并去心锚」
     */
    private fun finishManualEndSession(
        note: String?,
        mindfulnessLevel: Int?,
        driftSeconds: Long? = null,
        openToAnchor: Boolean = false
    ) {
        val session = sessionManager.currentSession.value
        val endingRecordId = session?.recordId
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
        clearCapsuleAwarenessState()
        // 回桌面必须先于落库：未对照 / recordId 为空时旧路径会跳过 Home，目标 App 留在前台
        if (destination != ManualEndDestination.OpenRecord) {
            mainHandler.post { onLeaveTargetToHome?.invoke(packageName) }
        }
        scope.launch {
            sessionManager.endSession(
                reason = UsageRecordEntity.EndReason.MANUAL,
                note = note,
                mindfulnessLevel = mindfulnessLevel,
                driftSeconds = driftSeconds
            )
            if (endingRecordId == null) return@launch
            // 先落库再回调：进心锚时时间轴已是终态，可直接定位高亮
            mainHandler.post {
                onManualEndSession?.invoke(endingRecordId, mindfulnessLevel, destination)
                // 仅跑偏挂可回看轻条；对齐回桌面静默
                if (destination == ManualEndDestination.HomeDrifted) {
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
            }
        }
    }

    fun dismissIntercept(keepCapsule: Boolean = false) {
        interceptParkedForRecents = false
        isInterceptVisible.set(false)
        interceptTargetPackage = null
        interceptKind = null
        DualSpaceGateActivity.finishIfShowing()
        mainHandler.post {
            removeAdViewInternal(cancel = true)  // 用户主动离开，强制取消广告，不再展示超限页
            removeInterceptViewInternal()
            // 门未进才拆胶囊；点「继续」后会话已开，不能被 Home 误判拆掉
            if (!keepCapsule && gateEnteringPackage == null) {
                removeCapsuleViewInternal()
            }
        }
    }

    /** 同 App 是否处于离开仪式冷却（门内应极短淡出）。 */
    fun isLeaveRitualInCooldown(packageName: String): Boolean {
        val last = lastDismissCeremonyTime[packageName] ?: return false
        return System.currentTimeMillis() - last < dismissCeremonyCooldownMs
    }

    /**
     * 用户主动离开拦截页（页上「先不进去了」）：卸层 + 守住反馈。
     * Home / 多任务不是离开路径（拉回门口）。与 [showInterceptInternal] 内 dismissWithCeremony 对齐。
     */
    fun dismissInterceptForUserLeave(
        packageName: String,
        isLimitTheme: Boolean,
        onDismissCompleted: () -> Unit
    ) {
        pendingGateHoldHadDraft = interceptHadDraftPurpose
        pendingInterruptStore.clear(packageName)
        interceptParkedForRecents = false
        isInterceptVisible.set(false)
        interceptTargetPackage = null
        interceptKind = null
        gateEnteringPackage = null
        DualSpaceGateActivity.finishIfShowing()
        scheduleRemoveInterceptView(interceptLeaveDismissMs)
        showDismissCeremony(
            packageName = packageName,
            destination = DismissDestination.HOME,
            isLimitTheme = isLimitTheme,
            offerPositiveDestination = true,
            onDismissCompleted = onDismissCompleted
        )
    }

    /**
     * 用户已离开目标 App 后再卸拦截层（Home / 点离开），避免先拆层再露出 App。
     */
    fun dismissInterceptAfterLeave(keepCapsule: Boolean = false) {
        interceptParkedForRecents = false
        isInterceptVisible.set(false)
        interceptTargetPackage = null
        interceptKind = null
        DualSpaceGateActivity.finishIfShowing()
        mainHandler.postDelayed({
            removeAdViewInternal(cancel = true)
            removeInterceptViewInternal()
            if (!keepCapsule && gateEnteringPackage == null) {
                removeCapsuleViewInternal()
            }
        }, interceptLeaveDismissMs)
    }

    fun dismissCapsule() {
        capsuleUpdateJob?.cancel()
        mainHandler.post {
            clearDesktopAnchorInApp()
            removeCapsuleViewInternal()
        }
    }

    /** 拦截页 Compose 内容是否已挂上（非纯色占位层） */
    fun hasInterceptUiAttached(): Boolean {
        if (!interceptContentIsCompose) return false
        val v = interceptView ?: return false
        return if (Looper.myLooper() == Looper.getMainLooper()) {
            v.isAttachedToWindow
        } else {
            isInterceptLayerWindowAttached()
        }
    }

    /** 拦截层（占位或正式 UI）是否仍挂在 WindowManager */
    fun hasInterceptLayerAttached(): Boolean = interceptView != null

    /** 拦截 metadata 仍在（层可能被系统在息屏时卸掉） */
    fun hasActiveInterceptTarget(): Boolean =
        interceptTargetPackage != null || gateEnteringPackage != null

    /** 拦截层是否仍实际贴在窗口上（须区分「标志位仍在」与「View 已被系统卸掉」） */
    fun isInterceptLayerWindowAttached(): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return interceptView?.isAttachedToWindow == true
        }
        var attached = false
        val latch = java.util.concurrent.CountDownLatch(1)
        mainHandler.post {
            attached = interceptView?.isAttachedToWindow == true
            latch.countDown()
        }
        latch.await(200L, java.util.concurrent.TimeUnit.MILLISECONDS)
        return attached
    }

    /** 拦截层是否仍占屏（View 须仍贴在窗口上；仅标志位不算） */
    fun isInterceptLayerOnScreen(targetPackage: String? = interceptTargetPackage): Boolean {
        if (!isInterceptLayerWindowAttached()) return false
        if (isInterceptVisible.get()) return true
        if (interceptView == null) return false
        if (targetPackage == null) return true
        val layerPkg = interceptTargetPackage ?: gateEnteringPackage
        return layerPkg == null || layerPkg == targetPackage
    }

    /** 暂停/活跃胶囊是否仍挂在 WindowManager；无意图门时含桌面转圈态 */
    fun isCapsuleAttached(): Boolean =
        if (Looper.myLooper() == Looper.getMainLooper()) {
            capsuleView != null || desktopAnchorInApp.value != null
        } else {
            var attached = false
            val latch = java.util.concurrent.CountDownLatch(1)
            mainHandler.post {
                attached = capsuleView != null || desktopAnchorInApp.value != null
                latch.countDown()
            }
            latch.await(200L, java.util.concurrent.TimeUnit.MILLISECONDS)
            attached
        }

    /**     * 息屏 / 屏保 / 解锁竞态下：静默拆掉误触发的离开轻条/勋章，
     * 避免拦截页场景冒出「守住了」等无关横条。
     */
    fun suppressTransientLeaveFeedback() {
        interceptRemoveGeneration++
        mainHandler.post {
            removeDismissCeremonyViewInternal()
        }
    }

    /**
     * 拦截页被息屏/屏保遮住或误走离开流程时：撤销待卸层、恢复可见，
     * 避免亮屏后整页重建。
     */
    fun reconcileInterceptVisibilityAfterObscured(packageName: String) {
        interceptRemoveGeneration++
        mainHandler.post {
            removeDismissCeremonyViewInternal()
            if (interceptView == null) return@post
            isInterceptVisible.set(true)
            if (interceptTargetPackage == null) {
                interceptTargetPackage = packageName
            }
            if (interceptKind == null) {
                interceptKind = attachedInterceptKind ?: InterceptOverlayKind.IntentGate
            }
        }
    }

    fun isAwayEndedBarShowing(): Boolean = awayEndedBarShowing

    fun isWalkAwareShowing(): Boolean = walkAwareShowing

    /**
     * 全屏门/仪式期间压制步行锚点，避免叠层抢戏。
     * 用量迷你胶囊可与之并存（锚点靠右上，胶囊多在左下）。
     */
    fun isWalkAwareSuppressedByFullscreen(): Boolean {
        return isInterceptLayerWindowAttached() ||
            dismissCeremonyView != null ||
            adView != null
    }

    /**
     * 展示或更新路况锚点。已挂载时只改 level/文案，避免反复 addView。
     * 点击锚点 → [onHideThisWalk]（本次步行隐藏）。
     */
    fun showOrUpdateWalkAware(
        level: WalkAwarenessLevel,
        label: String,
        onHideThisWalk: () -> Unit
    ) {
        mainHandler.post {
            walkAwareHideAction = onHideThisWalk
            walkAwareLevelState.value = level
            walkAwareLabelState.value = label
            if (walkAwareView != null && walkAwareShowing) return@post
            addWalkAwareView(retryLeft = 2)
        }
    }

    fun dismissWalkAware() {
        mainHandler.post { removeWalkAwareViewInternal() }
    }

    private fun addWalkAwareView(retryLeft: Int) {
        if (walkAwareView != null) return
        val isDarkTheme = appPreferences.isDarkThemeEnabled()
        val composeView = createComposeView {
            WalkAwareOverlay(
                level = walkAwareLevelState,
                label = walkAwareLabelState,
                isDarkTheme = isDarkTheme,
                onHideThisWalk = {
                    mainHandler.post {
                        walkAwareHideAction?.invoke()
                    }
                }
            )
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 0
            y = 0
        }
        try {
            windowManager.addView(composeView, params)
            walkAwareView = composeView
            walkAwareParams = params
            walkAwareShowing = true
        } catch (e: Exception) {
            Log.e(TAG, "挂路况锚点失败 retryLeft=$retryLeft", e)
            walkAwareShowing = false
            if (retryLeft > 0) {
                mainHandler.postDelayed({ addWalkAwareView(retryLeft - 1) }, 320L)
            }
        }
    }

    private fun removeWalkAwareViewInternal() {
        walkAwareView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) { }
            walkAwareView = null
            walkAwareParams = null
            walkAwareShowing = false
            walkAwareHideAction = null
        }
    }

    /** 息屏时收起回顾横条但不触发 onFinished；由 Service 保留 pending 待亮屏再弹。 */
    fun dismissAwayEndedBarSilently() {
        mainHandler.post {
            if (!awayEndedBarShowing) return@post
            awayEndedBarShowing = false
            removeDismissCeremonyViewInternal()
        }
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
     * @param offerPositiveDestination 仅页上主动点「离开」为 true
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
                offerPositiveDestination = offerPositiveDestination
            ),
            onLeaveCompleted = onDismissCompleted
        )
    }

    /** 正向出口轻条：去做了 · {标题} */
    fun showPositiveExitCeremony(
        packageName: String,
        choice: PositiveExitChoice,
        onDismissCompleted: () -> Unit
    ) {
        scope.launch {
            val todayCount = try {
                usageRecordRepository.getDayPositiveExitCount() + 1
            } catch (_: Exception) {
                1
            }
            playLeaveFeedback(
                request = LeaveFeedbackRequest(
                    kind = LeaveFeedbackKind.GatePositiveExit,
                    packageName = packageName,
                    purposeHint = choice.title,
                    positiveExitCount = todayCount,
                    applyGateCooldown = false,
                    offerPositiveDestination = false
                ),
                onLeaveCompleted = onDismissCompleted
            )
        }
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
            LeaveFeedbackKind.SessionToAnchor,
            LeaveFeedbackKind.SessionAligned,
            LeaveFeedbackKind.GateSilent -> {
                onLeaveCompleted()
                return
            }
            LeaveFeedbackKind.SessionDrifted -> {
                val isDarkTheme = appPreferences.isDarkThemeEnabled()
                val copy = leaveFeedbackCopy(request)
                mainHandler.post {
                    showLightLeaveAffirmation(
                        copy = copy,
                        isDarkTheme = isDarkTheme,
                        onAction = onAction
                    )
                }
                onLeaveCompleted()
                return
            }
            LeaveFeedbackKind.GateLight, LeaveFeedbackKind.GateMilestone,
            LeaveFeedbackKind.GatePositiveExit -> Unit
        }

        if (request.kind == LeaveFeedbackKind.GatePositiveExit) {
            val isDarkTheme = appPreferences.isDarkThemeEnabled()
            val copy = leaveFeedbackCopy(
                request.copy(
                    appLabel = request.appLabel?.trim()?.takeIf { it.isNotEmpty() }
                        ?: resolveAppLabel(request.packageName)
                )
            )
            val detailAction = gateAffirmationDetailAction(copy, request.packageName)
            mainHandler.post {
                onLeaveCompleted()
                mainHandler.postDelayed({
                    showLightLeaveAffirmation(
                        copy = copy,
                        isDarkTheme = isDarkTheme,
                        onAction = null,
                        onDetail = detailAction
                    )
                }, 420L)
            }
            return
        }

        scope.launch {
            if (AppPreferences.POSITIVE_DESTINATIONS_ENABLED && request.offerPositiveDestination) {
                appPreferences.incrementExplicitGateLeaveCount()
            }

            val countBefore = try {
                usageRecordRepository.getDayDismissCountForApp(request.packageName)
            } catch (_: Exception) {
                0
            }
            val displayCount = if (request.dismissCount > 0) {
                request.dismissCount
            } else {
                countBefore + 1
            }
            val now = System.currentTimeMillis()
            val lastTime = lastDismissCeremonyTime[request.packageName] ?: 0L
            val inCooldown = request.applyGateCooldown &&
                now - lastTime < dismissCeremonyCooldownMs

            // 冷却内：静默；里程碑推迟到下次合格离开（防刷全屏）
            if (inCooldown) {
                if (LeaveRitual.isMilestone(displayCount) ||
                    request.kind == LeaveFeedbackKind.GateMilestone
                ) {
                    deferredMilestoneCount[request.packageName] = displayCount
                }
                mainHandler.post { onLeaveCompleted() }
                return@launch
            }

            val deferred = deferredMilestoneCount[request.packageName]
            val isMilestone = request.kind == LeaveFeedbackKind.GateMilestone ||
                deferred != null ||
                LeaveRitual.isMilestone(displayCount)
            val milestoneCount = deferred ?: displayCount
            val resolved = request.copy(
                dismissCount = if (isMilestone) milestoneCount else displayCount,
                appLabel = request.appLabel?.trim()?.takeIf { it.isNotEmpty() }
                    ?: resolveAppLabel(request.packageName),
                kind = if (isMilestone) {
                    LeaveFeedbackKind.GateMilestone
                } else {
                    LeaveFeedbackKind.GateLight
                }
            )
            val isDarkTheme = appPreferences.isDarkThemeEnabled()
            val enriched = buildGateAffirmationCopy(resolved)
            val detailAction = gateAffirmationDetailAction(enriched, resolved.packageName)

            mainHandler.post {
                lastDismissCeremonyTime[resolved.packageName] = System.currentTimeMillis()
                if (resolved.kind == LeaveFeedbackKind.GateMilestone) {
                    deferredMilestoneCount.remove(resolved.packageName)
                    showFullDismissCeremony(
                        dismissCount = resolved.dismissCount,
                        packageName = resolved.packageName,
                        onDismissCompleted = {
                            onLeaveCompleted()
                            if (enriched.actionLabel != null ||
                                enriched.moreLabel != null ||
                                detailAction != null
                            ) {
                                mainHandler.postDelayed({
                                    showLightLeaveAffirmation(
                                        copy = enriched,
                                        isDarkTheme = isDarkTheme,
                                        onAction = gateAffirmationAction(enriched),
                                        onMoreChoice = gateAffirmationMoreAction(enriched),
                                        onManage = gateAffirmationManageAction(enriched),
                                        onDetail = detailAction
                                    )
                                }, 360L)
                            }
                        }
                    )
                } else {
                    // 日常：门内已呼气；门外不挂轻条（防双仪式、防刷）
                    onLeaveCompleted()
                }
            }
        }
    }

    private fun buildGateAffirmationCopy(request: LeaveFeedbackRequest): LeaveFeedbackCopy {
        val base = leaveFeedbackCopy(request)
        if (!AppPreferences.POSITIVE_DESTINATIONS_ENABLED || !request.offerPositiveDestination) {
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

    private fun gateAffirmationDetailAction(
        copy: LeaveFeedbackCopy,
        packageName: String
    ): (() -> Unit)? {
        if (copy.detailLabel.isNullOrBlank() || packageName.isBlank()) return null
        return {
            scope.launch {
                val id = runCatching {
                    usageRecordRepository.getDayRecordsForApp(packageName)
                        .firstOrNull()
                        ?.id
                }.getOrNull() ?: 0L
                onOpenAppHistory?.invoke(packageName, id)
            }
        }
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
        onManage: (() -> Unit)? = null,
        onDetail: (() -> Unit)? = null
    ) {
        removeDismissCeremonyViewInternal()
        if (copy.title.isBlank()) return

        val touchable =
            onAction != null || onMoreChoice != null || onManage != null || onDetail != null
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
                onDetail = onDetail?.let { detail ->
                    {
                        mainHandler.post {
                            removeDismissCeremonyViewInternal()
                            detail()
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
            if (!touchable) applyTrustedPassThroughOpacity(this)
        }

        try {
            windowManager.addView(composeView, params)
            dismissCeremonyView = composeView
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 离开倒计时归零 / 进程死后：回顾横条（默认展开三档对照 + 底部进度条）。
     * 保存即闭环；进度走完且未选档则保持未闭环（可续）。
     */
    fun showAwayEndedBar(
        appName: String,
        packageName: String,
        recordId: Long,
        timeAgoLabel: String,
        durationSeconds: Long,
        purpose: String? = null,
        intentKind: com.life.mindfulnessapp.domain.model.IntentKind? = null
    ) {
        mainHandler.post {
            addAwayEndedBarView(
                appName = appName,
                packageName = packageName,
                recordId = recordId,
                timeAgoLabel = timeAgoLabel,
                durationSeconds = durationSeconds,
                purpose = purpose,
                intentKind = intentKind,
                retryLeft = 2
            )
        }
    }

    private fun addAwayEndedBarView(
        appName: String,
        packageName: String,
        recordId: Long,
        timeAgoLabel: String,
        durationSeconds: Long,
        purpose: String?,
        intentKind: com.life.mindfulnessapp.domain.model.IntentKind?,
        retryLeft: Int
    ) {
        if (awayEndedBarShowing && dismissCeremonyView != null) return
        removeDismissCeremonyViewInternal()
        val isDarkTheme = appPreferences.isDarkThemeEnabled()
        val composeView = createComposeView {
            AwayEndedBarOverlay(
                appName = appName,
                packageName = packageName,
                timeAgoLabel = timeAgoLabel,
                durationSeconds = durationSeconds,
                purpose = purpose,
                intentKind = intentKind,
                isDarkTheme = isDarkTheme,
                showFastCompareHint = appPreferences.qualifiesFastCompareHint(),
                onCompareSaved = { level, note, drift ->
                    mainHandler.post {
                        removeDismissCeremonyViewInternal()
                        onAwayEndedCompareSaved?.invoke(
                            recordId,
                            packageName,
                            level,
                            note,
                            drift
                        )
                    }
                },
                onFinished = {
                    mainHandler.post {
                        removeDismissCeremonyViewInternal()
                        onAwayEndedDismissedWithoutCompare?.invoke(recordId, packageName)
                    }
                },
                onInputFocusChanged = { focusable ->
                    mainHandler.post { setAwayEndedBarFocusable(focusable) }
                }
            )
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        try {
            windowManager.addView(composeView, params)
            dismissCeremonyView = composeView
            dismissCeremonyParams = params
            awayEndedBarShowing = true
            applyDesktopAnchorVisibility()
        } catch (e: Exception) {
            Log.e(TAG, "挂回顾横条失败 $packageName retryLeft=$retryLeft", e)
            awayEndedBarShowing = false
            if (retryLeft > 0) {
                mainHandler.postDelayed({
                    addAwayEndedBarView(
                        appName = appName,
                        packageName = packageName,
                        recordId = recordId,
                        timeAgoLabel = timeAgoLabel,
                        durationSeconds = durationSeconds,
                        purpose = purpose,
                        intentKind = intentKind,
                        retryLeft = retryLeft - 1
                    )
                }, 320L)
            } else {
                onAwayEndedBarAttachFailed?.invoke()
            }
        }
    }

    /** 展开备注输入时临时可聚焦；收起/关闭后恢复不可聚焦。 */
    private fun setAwayEndedBarFocusable(focusable: Boolean) {
        val view = dismissCeremonyView ?: return
        val params = dismissCeremonyParams ?: return
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

    private fun removeDismissCeremonyViewInternal() {
        dismissCeremonyView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) { }
            dismissCeremonyView = null
            dismissCeremonyParams = null
            awayEndedBarShowing = false
        }
        setDesktopAnchorSceneHidden(false)
    }

    /**
     * 监测服务启动/设置开关变更时调用：按规则显示或拆除桌面心锚。
     */
    fun syncDesktopAnchor(monitoringActive: Boolean = MonitorForegroundService.isRunning) {
        desktopAnchorDesiredVisible = monitoringActive
        mainHandler.post { applyDesktopAnchorVisibility() }
    }

    /** 设置页切换「桌面心锚」后立刻对齐 */
    fun onDesktopAnchorPreferenceChanged() {
        mainHandler.post { applyDesktopAnchorVisibility() }
    }

    fun setHostAppInForeground(inForeground: Boolean) {
        if (desktopAnchorHostAppHidden == inForeground) return
        desktopAnchorHostAppHidden = inForeground
        mainHandler.post {
            if (inForeground) forceCloseDesktopAnchorPanel()
            applyDesktopAnchorVisibility()
        }
    }

    /** 设置「收起态大小」变更：同步圆球 / 时间条尺寸档 */
    fun onCapsuleMiniSizeChanged() {
        mainHandler.post {
            capsuleMiniCompact.value = appPreferences.isCapsuleMiniCompact()
            if (desktopAnchorPanelOpen.value) {
                captureDesktopAnchorOrbAnchor()
            }
        }
    }

    /** 设置页改气质后立刻对齐门 / 陪伴壳色与透明度 */
    fun onCapsuleShellOpacityChanged() {
        mainHandler.post {
            refreshOverlayThemePack()
            if (companionInspectOpen.value) refreshCompanionInspectGlance()
        }
    }

    /** 设置页或单 App 改陪伴形态后立刻对齐 */
    fun onCompanionAppearanceChanged() {
        mainHandler.post {
            refreshCapsuleCompanionPrefs()
            if (companionInspectOpen.value) refreshCompanionInspectGlance()
        }
    }

    private fun refreshCapsuleCompanionPrefs() {
        capsuleCompanionPrefs.value = CapsuleCompanionPrefs(
            barEnabled = appPreferences.isCompanionBarEnabled(),
            form = appPreferences.getCompanionBarForm(),
            appMode = appPreferences.getCompanionAppMode(capsuleAppPackageName.value),
        )
    }

    private fun syncAwarenessPracticePrefs() {
        capsuleAwarenessPracticeEnabled.value = appPreferences.isAwarenessPracticeEnabled()
        capsuleAwarenessPracticeGapSec.value =
            appPreferences.getAwarenessPracticeRhythm().gapSec
    }

    private fun resolvedCompanion() = CompanionScene.resolve(
        packageName = capsuleAppPackageName.value,
        prefs = capsuleCompanionPrefs.value,
        hasIntentGate = capsuleHasIntentGate.value,
    )

    private fun setCapsuleMiniCompactFromPanel(compact: Boolean) {
        val size = if (compact) {
            AppPreferences.CAPSULE_MINI_SIZE_COMPACT
        } else {
            AppPreferences.CAPSULE_MINI_SIZE_STANDARD
        }
        appPreferences.setCapsuleMiniSize(size)
        capsuleMiniCompact.value = compact
        if (desktopAnchorPanelOpen.value) {
            captureDesktopAnchorOrbAnchor()
        }
        analyticsRepository.track(
            HaEvents.DESKTOP_PANEL_TOOL,
            mapOf(HaEvents.Prop.ACTION to if (compact) "mini_compact" else "mini_standard")
        )
    }

    private fun desktopAnchorShellWidthPx(): Int {
        val density = context.resources.displayMetrics.density
        return (floatingOrbMetrics(appPreferences.isCapsuleMiniCompact()).desktopHit.value * density).toInt()
    }

    fun dismissAll() {
        isInterceptVisible.set(false)
        interceptTargetPackage = null
        interceptKind = null
        gateEnteringPackage = null
        pendingCapsuleSession = null
        pendingCapsulePlayEnter = false
        mainHandler.removeCallbacks(ensureCapsuleVisibleRunnable)
        capsuleUpdateJob?.cancel()
        mainHandler.post {
            removeAdViewInternal(cancel = true)
            removeInterceptViewInternal()
            removeCeremonyViewInternal()
            removeDismissCeremonyViewInternal()
            removeCapsuleViewInternal()
            removeWalkAwareViewInternal()
            removeDesktopAnchorInternal()
            // removeCapsuleViewInternal 已释放系统灰度；此处再兜底一次
            releaseDailyLimitGrayscale()
        }
    }

    // ── 桌面心锚微粒（仅桌面；意图门会话期隐藏，锚标在胶囊同壳转圈）──────────

    private fun onCapsulePresentedForDesktopAnchor(@Suppress("UNUSED_PARAMETER") session: UsageSession) {
        clearDesktopAnchorInApp()
        desktopAnchorSessionHidden = true
        forceCloseDesktopAnchorPanel()
        applyDesktopAnchorVisibility()
    }

    private fun onCapsuleRemovedForDesktopAnchor() {
        desktopAnchorSessionHidden = false
        applyDesktopAnchorVisibility()
        if (desktopAnchorView != null) {
            restoreDesktopAnchorFloatPosition(animate = true)
        }
    }

    private fun presentDesktopAnchorInApp(session: UsageSession) {
        if (session.hasIntentGate || !session.purpose.isNullOrBlank()) {
            clearDesktopAnchorInApp()
            desktopAnchorSessionHidden = true
            forceCloseDesktopAnchorPanel()
            applyDesktopAnchorVisibility()
            return
        }
        desktopAnchorSessionHidden = false
        val existing = desktopAnchorInApp.value
        if (existing?.packageName == session.packageName) {
            // 同包回前台：只同步秒数，避免重建 ticker / UsageStats 回写造成跳动
            desktopAnchorInApp.value = existing.copy(
                appName = session.appName,
                sessionSeconds = session.currentSessionSeconds,
                todayTotalSeconds = maxOf(existing.todayTotalSeconds, session.todayTotalSeconds)
            )
            applyDesktopAnchorVisibility()
            if (desktopAnchorInAppJob?.isActive != true) {
                startDesktopAnchorInAppTicker(session.packageName)
            }
            val pulse = desktopAnchorPulse.value
            if (pulse != null && pulse.packageName == session.packageName && pulse.live) {
                desktopAnchorPulse.value = pulse.copy(
                    sessionSeconds = session.currentSessionSeconds
                )
            }
            return
        }
        desktopAnchorInApp.value = DesktopAnchorInAppGlance(
            packageName = session.packageName,
            appName = session.appName,
            todayTotalSeconds = session.todayTotalSeconds,
            sessionSeconds = session.currentSessionSeconds,
            sessions = emptyList(),
            source = DesktopInAppSource.HeartAnchor,
            canAddToPlan = false,
        )
        applyDesktopAnchorVisibility()
        startDesktopAnchorInAppTicker(session.packageName)
        refreshDesktopAnchorInAppGlance()
    }

    /**
     * 前台是还没加进规则的普通 App：桌面球切成转圈，点球看系统用量。
     * 由 [MonitorForegroundService] 在前台稳定后调用。
     */
    fun presentUnmonitoredDesktopInApp(packageName: String) {
        if (!appPreferences.isDesktopAnchorEnabled()) return
        val pkg = packageName.trim()
        if (pkg.isEmpty() || pkg == context.packageName) return
        val run = Runnable {
            if (desktopAnchorSessionHidden || desktopAnchorSceneHidden) return@Runnable
            val live = sessionManager.currentSession.value
            if (live != null && !live.isInBackground && live.packageName == pkg) {
                return@Runnable
            }
            val existing = desktopAnchorInApp.value
            if (existing?.packageName == pkg &&
                existing.source == DesktopInAppSource.SystemUsage
            ) {
                if (desktopAnchorInAppJob?.isActive != true) {
                    startUnmonitoredDesktopInAppTicker(pkg)
                }
                applyDesktopAnchorVisibility()
                return@Runnable
            }
            desktopAnchorSessionHidden = false
            val label = resolveAppLabel(pkg) ?: pkg
            desktopAnchorInApp.value = DesktopAnchorInAppGlance(
                packageName = pkg,
                appName = label,
                todayTotalSeconds = 0L,
                sessionSeconds = 0L,
                sessions = emptyList(),
                source = DesktopInAppSource.SystemUsage,
                canAddToPlan = !MonitorSuitability.isUnsuitable(pkg),
            )
            applyDesktopAnchorVisibility()
            startUnmonitoredDesktopInAppTicker(pkg)
            refreshUnmonitoredDesktopInAppGlance()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) run.run() else mainHandler.post(run)
    }

    /** 回到桌面 / 系统页 / 已进规则的 App：拆掉系统账使用中态。 */
    fun clearUnmonitoredDesktopInApp() {
        val run = Runnable {
            val cur = desktopAnchorInApp.value ?: return@Runnable
            if (cur.source != DesktopInAppSource.SystemUsage) return@Runnable
            clearDesktopAnchorInApp()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) run.run() else mainHandler.post(run)
    }

    /** 同包轮询时若系统账态丢了，补挂。 */
    fun ensureUnmonitoredDesktopInApp(packageName: String) {
        val pkg = packageName.trim()
        if (pkg.isEmpty()) return
        val cur = desktopAnchorInApp.value
        if (cur?.packageName == pkg && cur.source == DesktopInAppSource.SystemUsage) {
            if (desktopAnchorInAppJob?.isActive != true) {
                startUnmonitoredDesktopInAppTicker(pkg)
            }
            return
        }
        presentUnmonitoredDesktopInApp(pkg)
    }

    private fun clearDesktopAnchorInApp() {
        desktopAnchorInAppJob?.cancel()
        desktopAnchorInAppJob = null
        if (desktopAnchorInApp.value != null) {
            desktopAnchorInApp.value = null
            clearDesktopAnchorPulse()
            if (desktopAnchorPanelOpen.value || desktopAnchorPanelView != null) {
                forceCloseDesktopAnchorPanel()
            }
        }
    }

    private fun startUnmonitoredDesktopInAppTicker(packageName: String) {
        desktopAnchorInAppJob?.cancel()
        desktopAnchorInAppJob = scope.launch {
            var tick = 0
            while (true) {
                val cur = desktopAnchorInApp.value
                if (cur == null ||
                    cur.packageName != packageName ||
                    cur.source != DesktopInAppSource.SystemUsage
                ) {
                    break
                }
                if (cur.sessionStartedAtMs > 0L) {
                    val liveSec = ((System.currentTimeMillis() - cur.sessionStartedAtMs) / 1000L)
                        .coerceAtLeast(0L)
                    if (liveSec != cur.sessionSeconds) {
                        val next = cur.copy(sessionSeconds = liveSec)
                        desktopAnchorInApp.value = next
                        val pulse = desktopAnchorPulse.value
                        if (pulse != null && pulse.packageName == packageName && pulse.live) {
                            desktopAnchorPulse.value = pulse.copy(sessionSeconds = liveSec)
                        }
                    }
                }
                tick++
                if (tick % 5 == 0 || desktopAnchorPanelOpen.value) {
                    refreshUnmonitoredDesktopInAppGlance()
                }
                delay(1_000L)
            }
        }
    }

    private fun refreshUnmonitoredDesktopInAppGlance() {
        val pkg = desktopAnchorInApp.value
            ?.takeIf { it.source == DesktopInAppSource.SystemUsage }
            ?.packageName
            ?: return
        scope.launch(Dispatchers.IO) {
            val curCheck = desktopAnchorInApp.value
            if (curCheck?.packageName != pkg ||
                curCheck.source != DesktopInAppSource.SystemUsage
            ) {
                return@launch
            }
            val snap = systemUsageRepository.getTodaySystemUsageSnapshot(pkg)
            val sessions = systemUsageRepository.getTodayForegroundSessions(pkg)
            val pulseSessions = systemForegroundSessionsToPulseSessions(sessions)
            val ongoing = sessions.firstOrNull { it.ongoing }
            val startedAt = ongoing?.startMs ?: 0L
            val sessionSecs = if (ongoing != null) {
                ongoing.durationSeconds.coerceAtLeast(0L)
            } else {
                0L
            }
            val (dayStart, dayEnd) = UsageRecordRepository.getDayRange(System.currentTimeMillis())
            val hourly = hourlyListToArray(
                systemUsageRepository.getHourlyDistribution(pkg, dayStart, dayEnd)
            )
            val label = resolveAppLabel(pkg) ?: pkg
            val canAdd = !MonitorSuitability.isUnsuitable(pkg)
            mainHandler.post {
                val cur = desktopAnchorInApp.value ?: return@post
                if (cur.packageName != pkg || cur.source != DesktopInAppSource.SystemUsage) {
                    return@post
                }
                val next = cur.copy(
                    appName = label,
                    todayTotalSeconds = snap.totalSeconds,
                    openCount = snap.openCount,
                    sessionSeconds = sessionSecs,
                    sessions = pulseSessions,
                    dismissCount = 0,
                    hourlySeconds = hourly,
                    canAddToPlan = canAdd,
                    sessionStartedAtMs = startedAt,
                )
                desktopAnchorInApp.value = next
                val pulse = desktopAnchorPulse.value
                if (pulse?.packageName == pkg) {
                    desktopAnchorPulse.value = pulse.copy(
                        appName = label,
                        todayTotalSeconds = snap.totalSeconds,
                        openCount = snap.openCount,
                        sessionSeconds = sessionSecs,
                        live = ongoing != null,
                        sessions = pulseSessions,
                        dismissCount = 0,
                        source = DesktopInAppSource.SystemUsage,
                        hourlySeconds = hourly,
                        canAddToPlan = canAdd,
                        inSlot = false,
                    )
                }
            }
        }
    }

    private fun startDesktopAnchorInAppTicker(packageName: String) {
        desktopAnchorInAppJob?.cancel()
        desktopAnchorInAppJob = scope.launch {
            var tick = 0
            while (true) {
                if (tick % 5 == 0) {
                    runCatching { sessionManager.refreshLiveBudget() }
                }
                val s = sessionManager.currentSession.value
                if (s == null ||
                    s.packageName != packageName ||
                    s.hasIntentGate ||
                    s.isInBackground
                ) {
                    mainHandler.post {
                        releaseDailyLimitGrayscale()
                        clearDesktopAnchorInApp()
                    }
                    break
                }
                syncDailyLimitGrayWash(s)
                val liveSec = s.currentSessionSeconds
                val cur = desktopAnchorInApp.value
                if (cur != null && cur.packageName == packageName) {
                    desktopAnchorInApp.value = cur.copy(
                        sessionSeconds = liveSec
                    )
                }
                val pulse = desktopAnchorPulse.value
                if (pulse != null && pulse.packageName == packageName && pulse.live) {
                    desktopAnchorPulse.value = pulse.copy(
                        sessionSeconds = liveSec
                    )
                }
                tick++
                if (tick % 5 == 0 || desktopAnchorPanelOpen.value) {
                    refreshDesktopAnchorInAppGlance()
                }
                val msIntoSecond = (s.currentActiveMs % 1000L).toInt()
                delay((1000L - msIntoSecond).coerceIn(50L, 1000L))
            }
        }
    }

    private fun refreshDesktopAnchorInAppGlance() {
        val pkg = desktopAnchorInApp.value?.packageName ?: return
        scope.launch(Dispatchers.IO) {
            val s = sessionManager.currentSession.value
            if (s == null || s.packageName != pkg || s.hasIntentGate) return@launch
            val records = usageRecordRepository.getDayRecordsForAppIncludingOpen(pkg)
            val sessionSecs = s.currentSessionSeconds
            val sessions = usageRecordsToPulseSessions(records, liveSessionSeconds = sessionSecs)
            val todaySecs = maxOf(
                usageRecordsTodaySeconds(records, liveSessionSeconds = sessionSecs),
                s.todayTotalSeconds
            )
            val openCount = UsageRecordCounts.enterCount(records)
            val dismissCount = UsageRecordCounts.dismissCount(records)
            // 进行中会话秒数以 SessionManager 为准；列表与汇总来自心锚记录
            mainHandler.post {
                val cur = desktopAnchorInApp.value ?: return@post
                if (cur.packageName != pkg) return@post
                val liveNow = sessionManager.currentSession.value
                    ?.takeIf { it.packageName == pkg }
                    ?.currentSessionSeconds
                    ?: sessionSecs
                val next = cur.copy(
                    todayTotalSeconds = todaySecs,
                    sessionSeconds = liveNow,
                    sessions = sessions,
                    openCount = openCount,
                    dismissCount = dismissCount
                )
                desktopAnchorInApp.value = next
                val pulse = desktopAnchorPulse.value
                if (pulse?.packageName == pkg) {
                    desktopAnchorPulse.value = pulse.copy(
                        todayTotalSeconds = todaySecs,
                        sessionSeconds = liveNow,
                        openCount = openCount,
                        dismissCount = dismissCount,
                        live = true,
                        sessions = sessions
                    )
                }
            }
        }
    }

    private fun setDesktopAnchorSceneHidden(hidden: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { setDesktopAnchorSceneHidden(hidden) }
            return
        }
        if (desktopAnchorSceneHidden == hidden) {
            if (!hidden) applyDesktopAnchorVisibility()
            return
        }
        desktopAnchorSceneHidden = hidden
        if (hidden) {
            forceCloseDesktopAnchorPanel()
            desktopAnchorView?.visibility = View.GONE
        } else {
            applyDesktopAnchorVisibility()
        }
    }

    private fun applyDesktopAnchorVisibility() {
        val prefOn = appPreferences.isDesktopAnchorEnabled()
        val want = desktopAnchorDesiredVisible &&
            prefOn &&
            Settings.canDrawOverlays(context) &&
            !desktopAnchorSceneHidden &&
            !desktopAnchorSessionHidden &&
            !desktopAnchorHostAppHidden &&
            !awayEndedBarShowing

        if (!prefOn || !desktopAnchorDesiredVisible) {
            removeDesktopAnchorInternal()
            return
        }
        if (!want) {
            if (intentSessionHub.value == null) {
                forceCloseDesktopAnchorPanel()
            }
            desktopAnchorView?.visibility = View.GONE
            return
        }
        if (desktopAnchorView == null) {
            addDesktopAnchorView()
        } else {
            desktopAnchorView?.visibility = View.VISIBLE
        }
        refreshDesktopAnchorGlance()
    }

    private fun addDesktopAnchorView() {
        if (desktopAnchorView != null) return
        capsuleMiniCompact.value = appPreferences.isCapsuleMiniCompact()
        loadDesktopAnchorFloatPosition()
        refreshOverlayThemePack()
        val composeView = createComposeView {
            val livePack = overlayThemePack.value
            DesktopAnchorOverlay(
                panelOpen = desktopAnchorPanelOpen,
                inAppGlance = desktopAnchorInApp,
                miniCompact = capsuleMiniCompact,
                themePack = livePack,
                isDarkTheme = livePack.isDark,
                dragging = desktopAnchorDragging,
                wakeSeq = desktopAnchorWakeSeq,
                onTogglePanel = { toggleDesktopAnchorPanel() },
            )
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = desktopAnchorFloatX
            y = desktopAnchorFloatY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        desktopAnchorParams = params

        val host = createDesktopAnchorTouchHost(composeView, params)
        try {
            windowManager.addView(host, params)
            desktopAnchorView = host
        } catch (e: Exception) {
            Log.e(TAG, "挂桌面心锚失败", e)
            desktopAnchorView = null
            desktopAnchorParams = null
        }
    }

    private fun createDesktopAnchorTouchHost(
        composeView: ComposeView,
        params: WindowManager.LayoutParams
    ): View {
        val host = object : FrameLayout(context) {
            private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDragging = false

            override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
                if (desktopAnchorPanelOpen.value) return false
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        desktopAnchorSnapAnimator?.cancel()
                        wakeDesktopAnchor()
                        desktopAnchorDragging.value = false
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = ev.rawX
                        initialTouchY = ev.rawY
                        isDragging = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = ev.rawX - initialTouchX
                        val dy = ev.rawY - initialTouchY
                        if (!isDragging &&
                            (dx * dx + dy * dy) > touchSlop * touchSlop
                        ) {
                            isDragging = true
                            desktopAnchorDragging.value = true
                        }
                        if (isDragging) {
                            val viewW = width.takeIf { it > 0 }
                                ?: desktopAnchorShellWidthPx()
                            val next = clampDesktopAnchorFloat(
                                initialX + dx.toInt(),
                                initialY + dy.toInt(),
                                viewW
                            )
                            params.x = next.first
                            params.y = next.second
                            try {
                                windowManager.updateViewLayout(this, params)
                            } catch (_: Exception) { /* ignore */ }
                            return true
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isDragging) {
                            finishDesktopAnchorDrag(this, params)
                            isDragging = false
                            return true
                        }
                    }
                }
                return isDragging
            }

            override fun onTouchEvent(ev: MotionEvent): Boolean {
                if (!isDragging) return false
                when (ev.actionMasked) {
                    MotionEvent.ACTION_MOVE -> {
                        val dx = ev.rawX - initialTouchX
                        val dy = ev.rawY - initialTouchY
                        val viewW = width.takeIf { it > 0 }
                            ?: desktopAnchorShellWidthPx()
                        val next = clampDesktopAnchorFloat(
                            initialX + dx.toInt(),
                            initialY + dy.toInt(),
                            viewW
                        )
                        params.x = next.first
                        params.y = next.second
                        try {
                            windowManager.updateViewLayout(this, params)
                        } catch (_: Exception) { /* ignore */ }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        finishDesktopAnchorDrag(this, params)
                        isDragging = false
                    }
                }
                return true
            }

            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                // 面板在独立全屏窗处理外点；圆球窗不必再吃 OUTSIDE
                return super.dispatchTouchEvent(ev)
            }
        }
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

    private fun toggleIntentSessionHub() {
        if (intentSessionHub.value != null && desktopAnchorPanelOpen.value) {
            collapseDesktopAnchorPanel()
            return
        }
        refreshIntentSessionHubGlance()
        refreshDesktopAnchorGlance()
        desktopAnchorPanelRemoveRunnable?.let { mainHandler.removeCallbacks(it) }
        desktopAnchorPanelRemoveRunnable = null
        captureDesktopAnchorOrbAnchor()
        tickDesktopAnchorHaptic(open = true)
        desktopAnchorPanelOpen.value = true
        ensureDesktopAnchorPanelView()
        captureDesktopAnchorOrbAnchor()
        setDesktopAnchorPanelFocusable(true)
        desktopAnchorPanelView?.requestFocus()
        analyticsRepository.track(
            HaEvents.DESKTOP_ANCHOR_OPEN,
            mapOf(HaEvents.Prop.SOURCE to "session_hub")
        )
    }

    private fun closeIntentSessionHub() {
        if (intentSessionHub.value == null &&
            !(desktopAnchorPanelOpen.value && capsuleHasIntentGate.value)
        ) {
            return
        }
        forceCloseDesktopAnchorPanel()
    }

    private fun refreshIntentSessionHubGlance() {
        val purpose = capsulePurpose.value?.trim().orEmpty()
            .ifBlank { capsuleAppName.value.ifBlank { "这一次" } }
        val pkg = capsuleAppPackageName.value
        intentSessionHub.value = IntentSessionHubGlance(
            packageName = pkg,
            appName = capsuleAppName.value,
            purpose = purpose,
            sessionSeconds = capsuleSessionSeconds.value,
            todayTotalSeconds = capsuleTodayTotalSeconds.value,
            todayEnterCount = capsuleTodayEnterCount.value,
            remainSeconds = capsuleDailyRemainingSeconds.value,
            hasSessionLimit = capsuleHasSessionLimit.value,
            sessionLimitMinutes = capsuleSessionLimitMinutes.value,
            overLimit = capsuleIsOverLimit.value,
        )
        val prior = desktopAnchorInApp.value?.takeIf { it.packageName == pkg }
        desktopAnchorInApp.value = DesktopAnchorInAppGlance(
            packageName = pkg,
            appName = capsuleAppName.value,
            todayTotalSeconds = capsuleTodayTotalSeconds.value,
            sessionSeconds = capsuleSessionSeconds.value,
            sessions = prior?.sessions.orEmpty()
        )
    }

    private fun tickDesktopAnchorHaptic(open: Boolean) {
        val view = desktopAnchorView ?: desktopAnchorPanelView ?: return
        val code = if (open) {
            HapticFeedbackConstants.CONTEXT_CLICK
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        }
        view.performHapticFeedback(code)
    }

    private fun tickDesktopAnchorSnapHaptic(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    private fun captureDesktopAnchorOrbAnchor() {
        val orb = desktopAnchorView
        val useOrb = orb != null && orb.visibility == View.VISIBLE
        val source = if (useOrb) orb else (capsuleView ?: orb)
        if (source == null) return
        val orbLoc = IntArray(2)
        source.getLocationOnScreen(orbLoc)
        val panelLoc = IntArray(2)
        desktopAnchorPanelView?.getLocationOnScreen(panelLoc)
        val density = context.resources.displayMetrics.density
        val bottomInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.windowInsets
                .getInsetsIgnoringVisibility(android.view.WindowInsets.Type.navigationBars())
                .bottom
        } else {
            (24f * density).toInt()
        }
        val minSize = if (useOrb) desktopAnchorShellWidthPx() else (36f * density).toInt()
        desktopAnchorOrbAnchor.value = DesktopAnchorOrbAnchor(
            x = orbLoc[0] - panelLoc[0],
            y = orbLoc[1] - panelLoc[1],
            width = source.width.coerceAtLeast(minSize),
            height = source.height.coerceAtLeast(minSize),
            parentWidth = overlayScreenWidthPx(),
            parentHeight = overlayScreenHeightPx(),
            topInset = capsuleTopRowYPx(),
            bottomInset = bottomInset + (24f * density).toInt()
        )
    }

    private fun toggleDesktopAnchorPanel() {
        val live = sessionManager.currentSession.value
        if (live != null && !live.isInBackground &&
            (live.hasIntentGate || !live.purpose.isNullOrBlank())
        ) {
            forceCloseDesktopAnchorPanel()
            showCapsuleNow(live, playEnterAnimation = false)
            openCompanionInspect()
            return
        }
        if (desktopAnchorPanelOpen.value) {
            collapseDesktopAnchorPanel()
            return
        }
        desktopAnchorPanelRemoveRunnable?.let { mainHandler.removeCallbacks(it) }
        desktopAnchorPanelRemoveRunnable = null
        if (intentSessionHub.value != null) {
            intentSessionHub.value = null
        }
        captureDesktopAnchorOrbAnchor()
        tickDesktopAnchorHaptic(open = true)
        desktopAnchorPanelOpen.value = true
        ensureDesktopAnchorPanelView()
        captureDesktopAnchorOrbAnchor()
        setDesktopAnchorPanelFocusable(true)
        desktopAnchorPanelView?.requestFocus()
        analyticsRepository.track(HaEvents.DESKTOP_ANCHOR_OPEN, emptyMap())
        // Hub 与日程锁态始终刷新；使用中再补当前 App 账
        refreshDesktopAnchorGlance()
        when (desktopAnchorInApp.value?.source) {
            DesktopInAppSource.SystemUsage -> refreshUnmonitoredDesktopInAppGlance()
            DesktopInAppSource.HeartAnchor -> refreshDesktopAnchorInAppGlance()
            null -> Unit
        }
    }

    private fun collapseDesktopAnchorPanel(haptic: Boolean = true) {
        if (!desktopAnchorPanelOpen.value && desktopAnchorPanelView == null) return
        if (haptic && desktopAnchorPanelOpen.value) {
            tickDesktopAnchorHaptic(open = false)
        }
        desktopAnchorPanelOpen.value = false
        clearDesktopAnchorPulse()
        setDesktopAnchorPanelFocusable(false)
        desktopAnchorPanelRemoveRunnable?.let { mainHandler.removeCallbacks(it) }
        val remove = Runnable {
            desktopAnchorPanelRemoveRunnable = null
            removeDesktopAnchorPanelView()
        }
        desktopAnchorPanelRemoveRunnable = remove
        mainHandler.postDelayed(remove, 300L)
    }

    private fun forceCloseDesktopAnchorPanel() {
        desktopAnchorPanelRemoveRunnable?.let { mainHandler.removeCallbacks(it) }
        desktopAnchorPanelRemoveRunnable = null
        desktopAnchorPanelOpen.value = false
        clearDesktopAnchorPulse()
        desktopAnchorPanelBackHandler = null
        removeDesktopAnchorPanelView()
    }

    private fun ensureDesktopAnchorPanelView() {
        if (desktopAnchorPanelView != null) {
            desktopAnchorPanelView?.visibility = View.VISIBLE
            restackForegroundAbovePanel()
            captureDesktopAnchorOrbAnchor()
            setDesktopAnchorPanelFocusable(true)
            desktopAnchorPanelView?.requestFocus()
            return
        }
        refreshOverlayThemePack()
        val composeView = createComposeView {
            val livePack = overlayThemePack.value
            DesktopAnchorPanelOverlay(
                panelOpen = desktopAnchorPanelOpen,
                topApps = desktopAnchorTopApps,
                hourlySeconds = desktopAnchorHourlySeconds,
                todayTotalSeconds = desktopAnchorTodayTotalSeconds,
                todayEnterCount = desktopAnchorTodayEnterCount,
                todayDismissCount = desktopAnchorTodayDismissCount,
                activeLock = desktopAnchorActiveLock,
                inAppGlance = desktopAnchorInApp,
                pulseGlance = desktopAnchorPulse,
                orbAnchor = desktopAnchorOrbAnchor,
                intentSession = intentSessionHub,
                themePack = livePack,
                isDarkTheme = livePack.isDark,
                onDismiss = { collapseDesktopAnchorPanel() },
                onOpenApp = {
                    collapseDesktopAnchorPanel()
                    onOpenHeartAnchorFromDesktop?.invoke()
                    analyticsRepository.track(HaEvents.DESKTOP_ANCHOR_OPEN_APP, emptyMap())
                },
                onOpenSchedule = {
                    collapseDesktopAnchorPanel()
                    onOpenScheduleFromDesktop?.invoke()
                },
                onOpenUsageLog = {
                    collapseDesktopAnchorPanel()
                    onOpenUsageLogFromDesktop?.invoke()
                    analyticsRepository.track(
                        HaEvents.DESKTOP_PANEL_TOOL,
                        mapOf(HaEvents.Prop.ACTION to "open_usage_log")
                    )
                },
                onRequestPulse = { pkg ->
                    loadDesktopAnchorPulse(pkg)
                    analyticsRepository.track(
                        HaEvents.DESKTOP_PANEL_TOOL,
                        mapOf(HaEvents.Prop.ACTION to "open_pulse", HaEvents.Prop.PKG to pkg)
                    )
                },
                onClearPulse = { clearDesktopAnchorPulse() },
                onOpenFullReport = { pkg ->
                    collapseDesktopAnchorPanel()
                    onOpenAppHistory?.invoke(pkg, 0L)
                    analyticsRepository.track(
                        HaEvents.DESKTOP_PANEL_TOOL,
                        mapOf(HaEvents.Prop.ACTION to "open_history", HaEvents.Prop.PKG to pkg)
                    )
                },
                onAddToPlan = { pkg ->
                    collapseDesktopAnchorPanel()
                    val open = onOpenPlanAddFromDesktop
                    if (open != null) {
                        open(pkg)
                    } else {
                        runCatching {
                            context.startActivity(
                                PlanAddAppActivity.createIntent(context, pkg).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        }
                    }
                    analyticsRepository.track(
                        HaEvents.DESKTOP_PANEL_TOOL,
                        mapOf(HaEvents.Prop.ACTION to "add_to_plan", HaEvents.Prop.PKG to pkg)
                    )
                },
                onLaunchSearch = { entryId, query ->
                    collapseDesktopAnchorPanel()
                    launchDesktopAnchorSearch(entryId, query)
                },
                onNeedImeFocus = { ime ->
                    mainHandler.post { setDesktopAnchorPanelIme(ime) }
                },
                onNeedClipboardFocus = {
                    mainHandler.post {
                        val view = desktopAnchorPanelView ?: return@post
                        view.isFocusable = true
                        view.isFocusableInTouchMode = true
                        setDesktopAnchorPanelFocusable(true)
                        view.requestFocus()
                    }
                },
                onEndSession = {
                    forceCloseDesktopAnchorPanel()
                    capsuleRequestEnd?.invoke()
                },
                onBindBackHandler = { handler ->
                    desktopAnchorPanelBackHandler = handler
                },
                miniCompact = capsuleMiniCompact,
                onSetMiniCompact = { compact ->
                    setCapsuleMiniCompactFromPanel(compact)
                },
            )
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        desktopAnchorPanelParams = params
        val host = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK ||
                    event.keyCode == KeyEvent.KEYCODE_ESCAPE
                ) {
                    if (event.action == KeyEvent.ACTION_UP) {
                        val handled = desktopAnchorPanelBackHandler?.invoke() == true
                        if (!handled) collapseDesktopAnchorPanel()
                    }
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
        val hostLifecycleOwner = OverlayLifecycleOwner().also { it.start() }
        host.setViewTreeLifecycleOwner(hostLifecycleOwner)
        host.setViewTreeViewModelStoreOwner(object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        })
        host.setViewTreeSavedStateRegistryOwner(hostLifecycleOwner)
        host.addView(
            composeView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        try {
            windowManager.addView(host, params)
            desktopAnchorPanelView = host
            // 圆球或陪伴条置顶，仍可点按收起；面板全屏层在其下吃外点
            restackForegroundAbovePanel()
            captureDesktopAnchorOrbAnchor()
            setDesktopAnchorPanelFocusable(true)
            host.requestFocus()
        } catch (e: Exception) {
            Log.e(TAG, "挂桌面心锚面板失败", e)
            desktopAnchorPanelView = null
            desktopAnchorPanelParams = null
            desktopAnchorPanelOpen.value = false
        }
    }

    /** 面板全屏窗盖住后，把圆球 / 意图门陪伴条重新挂到最前 */
    private fun restackForegroundAbovePanel() {
        restackDesktopAnchorOrbAbovePanel()
        restackCapsuleAbovePanel()
    }

    private fun restackDesktopAnchorOrbAbovePanel() {
        val orb = desktopAnchorView ?: return
        val params = desktopAnchorParams ?: return
        try {
            windowManager.removeView(orb)
            windowManager.addView(orb, params)
        } catch (e: Exception) {
            Log.w(TAG, "桌面心锚圆球置顶失败", e)
        }
    }

    private fun restackCapsuleAbovePanel() {
        val cap = capsuleView ?: return
        val params = capsuleParams ?: return
        try {
            windowManager.removeView(cap)
            windowManager.addView(cap, params)
        } catch (e: Exception) {
            Log.w(TAG, "意图门陪伴条置顶失败", e)
        }
    }

    private fun removeDesktopAnchorPanelView() {
        setDesktopAnchorPanelFocusable(false)
        desktopAnchorPanelView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) { /* ignore */ }
        }
        desktopAnchorPanelView = null
        desktopAnchorPanelParams = null
        if (intentSessionHub.value != null) {
            intentSessionHub.value = null
            desktopAnchorInApp.value = null
        }
    }

    private fun setDesktopAnchorPanelFocusable(focusable: Boolean) {
        val view = desktopAnchorPanelView ?: return
        val params = desktopAnchorPanelParams ?: return
        if (focusable) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
        }
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL.inv()
        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: Exception) { /* ignore */ }
    }

    /** 搜索页需要键盘时改 softInput；面板开着时窗口保持可聚焦，以便返回键收起。 */
    private fun setDesktopAnchorPanelIme(ime: Boolean) {
        val view = desktopAnchorPanelView ?: return
        val params = desktopAnchorPanelParams ?: return
        if (ime) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            @Suppress("DEPRECATION")
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        } else if (desktopAnchorPanelOpen.value) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
        } else {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
        }
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL.inv()
        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: Exception) { /* ignore */ }
    }

    private fun launchDesktopAnchorSearch(entryId: String, query: String) {
        val entry = com.life.mindfulnessapp.data.deeplink.SearchDeepLinkCatalog.all
            .firstOrNull { it.id == entryId }
        if (entry == null) {
            Log.w(TAG, "搜索直达未找到 entry=$entryId")
            return
        }
        val err = com.life.mindfulnessapp.data.deeplink.SearchDeepLinkLauncher.launch(
            context,
            entry,
            query
        )
        analyticsRepository.track(
            HaEvents.DESKTOP_PANEL_TOOL,
            mapOf(
                HaEvents.Prop.ACTION to "search_launch",
                HaEvents.Prop.PKG to entry.packageName
            )
        )
        if (err != null) Log.w(TAG, "搜索直达失败: $err")
    }

    private fun clearDesktopAnchorPulse() {
        desktopAnchorPulseJob?.cancel()
        desktopAnchorPulseJob = null
        desktopAnchorPulse.value = null
    }

    private fun loadDesktopAnchorPulse(packageName: String) {
        val pkg = packageName.trim()
        if (pkg.isEmpty()) return
        // 若正是当前无意图门会话，先用会话快照占位，再异步补全
        val inApp = desktopAnchorInApp.value
        val hub = intentSessionHub.value
        val cachedInSlot = desktopAnchorTopApps.value.find { it.packageName == pkg }?.inSlot
        if (inApp != null && inApp.packageName == pkg) {
            desktopAnchorPulse.value = DesktopAnchorPulseGlance(
                packageName = inApp.packageName,
                appName = inApp.appName,
                todayTotalSeconds = inApp.todayTotalSeconds,
                openCount = inApp.openCount.coerceAtLeast(hub?.todayEnterCount ?: 0),
                sessionSeconds = inApp.sessionSeconds,
                live = true,
                sessions = inApp.sessions,
                dismissCount = inApp.dismissCount,
                inSlot = cachedInSlot ?: (inApp.source == DesktopInAppSource.HeartAnchor),
                source = inApp.source,
                hourlySeconds = inApp.hourlySeconds.copyOf(),
                canAddToPlan = inApp.canAddToPlan,
            )
            if (inApp.source == DesktopInAppSource.SystemUsage) {
                refreshUnmonitoredDesktopInAppGlance()
                return
            }
        } else if (hub != null && hub.packageName == pkg) {
            desktopAnchorPulse.value = DesktopAnchorPulseGlance(
                packageName = hub.packageName,
                appName = hub.appName,
                todayTotalSeconds = hub.todayTotalSeconds,
                openCount = hub.todayEnterCount,
                sessionSeconds = hub.sessionSeconds,
                live = true,
                sessions = emptyList(),
                dismissCount = 0,
                inSlot = true,
                source = DesktopInAppSource.HeartAnchor,
                canAddToPlan = false,
            )
        } else {
            desktopAnchorPulse.value = null
        }
        desktopAnchorPulseJob?.cancel()
        desktopAnchorPulseJob = scope.launch(Dispatchers.IO) {
            val inSlot = pkg in appLimitRepository.getEnabledPackageNames()
            // Hub 点未进规则的芯片：走系统账
            if (!inSlot && (inApp == null || inApp.packageName != pkg)) {
                val snap = systemUsageRepository.getTodaySystemUsageSnapshot(pkg)
                val sysSessions = systemUsageRepository.getTodayForegroundSessions(pkg)
                val pulseSessions = systemForegroundSessionsToPulseSessions(sysSessions)
                val ongoing = sysSessions.firstOrNull { it.ongoing }
                val (dayStart, dayEnd) = UsageRecordRepository.getDayRange(System.currentTimeMillis())
                val hourly = hourlyListToArray(
                    systemUsageRepository.getHourlyDistribution(pkg, dayStart, dayEnd)
                )
                val label = resolveAppLabel(pkg) ?: pkg
                val canAdd = !MonitorSuitability.isUnsuitable(pkg)
                mainHandler.post {
                    desktopAnchorPulse.value = DesktopAnchorPulseGlance(
                        packageName = pkg,
                        appName = label,
                        todayTotalSeconds = snap.totalSeconds,
                        openCount = snap.openCount,
                        sessionSeconds = ongoing?.durationSeconds?.coerceAtLeast(0L) ?: 0L,
                        live = ongoing != null,
                        sessions = pulseSessions,
                        dismissCount = 0,
                        inSlot = false,
                        source = DesktopInAppSource.SystemUsage,
                        hourlySeconds = hourly,
                        canAddToPlan = canAdd,
                    )
                }
                return@launch
            }
            val records = usageRecordRepository.getDayRecordsForAppIncludingOpen(pkg)
            val sessionLive = sessionManager.currentSession.value
                ?.takeIf { it.packageName == pkg }
            val live = sessionLive != null ||
                (inApp?.packageName == pkg) ||
                (hub?.packageName == pkg) ||
                records.any { it.endTime <= 0L && UsageRecordCounts.isEnter(it) }
            val sessionSecs = when {
                sessionLive != null -> sessionLive.currentSessionSeconds
                inApp?.packageName == pkg -> inApp.sessionSeconds
                hub?.packageName == pkg -> hub.sessionSeconds
                else -> records.firstOrNull { it.endTime <= 0L && UsageRecordCounts.isEnter(it) }
                    ?.durationSeconds?.coerceAtLeast(0L) ?: 0L
            }
            val liveSecs = if (live) sessionSecs else null
            val sessions = usageRecordsToPulseSessions(records, liveSessionSeconds = liveSecs)
            val todaySecs = maxOf(
                usageRecordsTodaySeconds(records, liveSessionSeconds = liveSecs),
                inApp?.takeIf { it.packageName == pkg }?.todayTotalSeconds ?: 0L,
                hub?.takeIf { it.packageName == pkg }?.todayTotalSeconds ?: 0L,
                sessionLive?.todayTotalSeconds ?: 0L
            )
            val openCount = UsageRecordCounts.enterCount(records)
            val dismissCount = UsageRecordCounts.dismissCount(records)
            val label = resolveAppLabel(pkg) ?: pkg
            mainHandler.post {
                desktopAnchorPulse.value = DesktopAnchorPulseGlance(
                    packageName = pkg,
                    appName = label,
                    todayTotalSeconds = todaySecs,
                    openCount = openCount,
                    sessionSeconds = sessionSecs,
                    live = live,
                    sessions = sessions,
                    dismissCount = dismissCount,
                    inSlot = inSlot,
                    source = DesktopInAppSource.HeartAnchor,
                    canAddToPlan = false,
                )
            }
        }
    }

    private fun setDesktopAnchorFocusable(focusable: Boolean) {
        val view = desktopAnchorView ?: return
        val params = desktopAnchorParams ?: return
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

    private fun refreshDesktopAnchorGlance() {
        desktopAnchorRefreshJob?.cancel()
        desktopAnchorRefreshJob = scope.launch(Dispatchers.IO) {
            val monitored = appLimitRepository.getEnabledPackageNames().toSet()
            val (start, end) = UsageRecordRepository.getDayRange(System.currentTimeMillis())
            val hourly = LongArray(24)
            val tops: List<DesktopAnchorTopApp>
            val total: Long
            var enterTotal = 0
            var dismissTotal = 0
            if (monitored.isEmpty()) {
                tops = emptyList()
                total = 0L
            } else {
                val secondsByPkg = usageRecordRepository.getAppTotalByPeriod(start, end)
                    .filter { it.packageName in monitored }
                    .associate { it.packageName to it.totalSeconds }
                    .toMutableMap()
                val openByPkg = mutableMapOf<String, Int>()
                for (pkg in monitored) {
                    val records = usageRecordRepository.getDayRecordsForAppIncludingOpen(pkg)
                    val enters = UsageRecordCounts.enterCount(records)
                    openByPkg[pkg] = enters
                    enterTotal += enters
                    dismissTotal += UsageRecordCounts.dismissCount(records)
                    for (r in records) {
                        if (!UsageRecordCounts.isEnter(r)) continue
                        if (r.endTime <= 0L) {
                            val liveSec = sessionManager.currentSession.value
                                ?.takeIf { it.packageName == pkg }
                                ?.currentSessionSeconds
                                ?: r.durationSeconds.coerceAtLeast(0L)
                            secondsByPkg[pkg] = (secondsByPkg[pkg] ?: 0L) + liveSec
                            accumulateLocalHourly(hourly, r.startTime, liveSec)
                        } else {
                            accumulateLocalHourly(
                                hourly,
                                r.startTime,
                                r.durationSeconds.coerceAtLeast(0L)
                            )
                        }
                    }
                }
                total = secondsByPkg.values.sum()
                tops = secondsByPkg.entries
                    .sortedByDescending { it.value }
                    .take(5)
                    .map { (pkg, sec) ->
                        DesktopAnchorTopApp(
                            packageName = pkg,
                            appName = resolveAppLabel(pkg) ?: pkg,
                            totalSeconds = sec,
                            openCount = openByPkg[pkg] ?: 0,
                            inSlot = true
                        )
                    }
                    .filter { it.totalSeconds > 0L || it.openCount > 0 }
            }
            val activeLock = ScheduleOrbPolicy.activeGlance(
                plans = planBlockRepository.getAllOnce(),
                monitoredPackages = monitored,
                appLabel = { pkg -> resolveAppLabel(pkg) ?: pkg },
            )
            mainHandler.post {
                desktopAnchorTopApps.value = tops
                desktopAnchorHourlySeconds.value = hourly
                desktopAnchorTodayTotalSeconds.value = total
                desktopAnchorTodayEnterCount.value = enterTotal
                desktopAnchorTodayDismissCount.value = dismissTotal
                desktopAnchorActiveLock.value = activeLock
            }
        }
    }

    /**
     * 按本地小时把时长摊进 0–23 桶。
     * 不能用 `(startMs/3600000)%24`：那是 UTC，中国早上会落到晚上时段。
     */
    private fun accumulateLocalHourly(hourly: LongArray, startMs: Long, durationSeconds: Long) {
        if (durationSeconds <= 0L || startMs <= 0L) return
        val endMs = startMs + durationSeconds * 1000L
        var cur = startMs
        val cal = java.util.Calendar.getInstance()
        while (cur < endMs) {
            cal.timeInMillis = cur
            val hour = cal.get(java.util.Calendar.HOUR_OF_DAY).coerceIn(0, 23)
            cal.add(java.util.Calendar.HOUR_OF_DAY, 1)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            val hourEnd = cal.timeInMillis.coerceAtMost(endMs)
            hourly[hour] = hourly[hour] + ((hourEnd - cur) / 1000L).coerceAtLeast(0L)
            cur = hourEnd
        }
    }

    private fun loadDesktopAnchorFloatPosition() {
        val density = context.resources.displayMetrics.density
        val viewW = desktopAnchorShellWidthPx()
        val stored = appPreferences.getDesktopAnchorFloatOffset()
        if (stored != null) {
            val snapped = snapDesktopAnchorToNearestEdge(stored.first, stored.second, viewW)
            desktopAnchorFloatX = snapped.first
            desktopAnchorFloatY = snapped.second
            if (snapped.first != stored.first || snapped.second != stored.second) {
                appPreferences.setDesktopAnchorFloatOffset(snapped.first, snapped.second)
            }
            return
        }
        val screenH = overlayScreenHeightPx()
        val y = (screenH - (128f * density).toInt()).coerceAtLeast(capsuleTopRowYPx())
        val snapped = snapDesktopAnchorToNearestEdge(1, y, viewW)
        desktopAnchorFloatX = snapped.first
        desktopAnchorFloatY = snapped.second
    }

    private fun saveDesktopAnchorFloatPosition(offsetX: Int, offsetY: Int, viewWidth: Int) {
        val (x, y) = snapDesktopAnchorToNearestEdge(offsetX, offsetY, viewWidth)
        desktopAnchorFloatX = x
        desktopAnchorFloatY = y
        appPreferences.setDesktopAnchorFloatOffset(x, y)
    }

    /** 贴边内距：略大于手势条，避免吸死左右缘抢返回手势 */
    private fun desktopAnchorEdgePx(): Int {
        val density = context.resources.displayMetrics.density
        return (10f * density).toInt()
    }

    private fun desktopAnchorMaxAbsX(viewWidth: Int): Int {
        val screenWidth = overlayScreenWidthPx()
        return ((screenWidth - viewWidth) / 2 - desktopAnchorEdgePx()).coerceAtLeast(0)
    }

    /**
     * 松手吸左右边（Y 保持）。水平居中偏右则靠右，与 iOS 辅助触控一致。
     */
    private fun snapDesktopAnchorToNearestEdge(
        offsetX: Int,
        offsetY: Int,
        viewWidth: Int
    ): Pair<Int, Int> {
        val (x, y) = clampDesktopAnchorFloat(offsetX, offsetY, viewWidth)
        val maxAbsX = desktopAnchorMaxAbsX(viewWidth)
        val snappedX = if (x >= 0) maxAbsX else -maxAbsX
        return snappedX to y
    }

    private fun clampDesktopAnchorFloat(offsetX: Int, offsetY: Int, viewWidth: Int): Pair<Int, Int> {
        val density = context.resources.displayMetrics.density
        val screenHeight = overlayScreenHeightPx()
        val edge = desktopAnchorEdgePx()
        val topMin = capsuleTopRowYPx()
        val bottomInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.windowInsets
                .getInsetsIgnoringVisibility(android.view.WindowInsets.Type.navigationBars())
                .bottom
        } else {
            (24f * density).toInt()
        }
        val bottomPad = (56f * density).toInt()
        val viewH = desktopAnchorShellWidthPx() + (6f * density).toInt()
        val yMax = (screenHeight - bottomInset - viewH - edge - bottomPad).coerceAtLeast(topMin)
        val maxAbsX = desktopAnchorMaxAbsX(viewWidth)
        val x = offsetX.coerceIn(-maxAbsX, maxAbsX)
        val y = offsetY.coerceIn(topMin, yMax)
        return x to y
    }

    private fun restoreDesktopAnchorFloatPosition(animate: Boolean) {
        val view = desktopAnchorView ?: return
        val params = desktopAnchorParams ?: return
        val viewW = view.width.takeIf { it > 0 } ?: desktopAnchorShellWidthPx()
        val (x, y) = snapDesktopAnchorToNearestEdge(desktopAnchorFloatX, desktopAnchorFloatY, viewW)
        desktopAnchorFloatX = x
        desktopAnchorFloatY = y
        animateDesktopAnchorTo(view, params, x, y, animate)
    }

    private fun wakeDesktopAnchor() {
        desktopAnchorWakeSeq.value = desktopAnchorWakeSeq.value + 1
    }

    private fun finishDesktopAnchorDrag(view: View, params: WindowManager.LayoutParams) {
        val viewW = view.width.takeIf { it > 0 } ?: desktopAnchorShellWidthPx()
        val snapped = snapDesktopAnchorToNearestEdge(params.x, params.y, viewW)
        saveDesktopAnchorFloatPosition(snapped.first, snapped.second, viewW)
        val moved = params.x != snapped.first || params.y != snapped.second
        animateDesktopAnchorTo(view, params, snapped.first, snapped.second, animate = true)
        desktopAnchorDragging.value = false
        wakeDesktopAnchor()
        if (moved) tickDesktopAnchorSnapHaptic(view)
    }

    private fun animateDesktopAnchorTo(
        view: View,
        params: WindowManager.LayoutParams,
        targetX: Int,
        targetY: Int,
        animate: Boolean
    ) {
        desktopAnchorSnapAnimator?.cancel()
        if (!animate) {
            params.x = targetX
            params.y = targetY
            try {
                windowManager.updateViewLayout(view, params)
            } catch (_: Exception) { /* ignore */ }
            return
        }
        val startX = params.x
        val startY = params.y
        if (startX == targetX && startY == targetY) {
            params.x = targetX
            params.y = targetY
            return
        }
        desktopAnchorSnapAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 260L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val f = anim.animatedValue as Float
                params.x = (startX + (targetX - startX) * f).toInt()
                params.y = (startY + (targetY - startY) * f).toInt()
                try {
                    windowManager.updateViewLayout(view, params)
                } catch (_: Exception) { /* ignore */ }
            }
            start()
        }
    }

    private fun removeDesktopAnchorInternal() {
        desktopAnchorPanelRemoveRunnable?.let { mainHandler.removeCallbacks(it) }
        desktopAnchorPanelRemoveRunnable = null
        desktopAnchorRefreshJob?.cancel()
        desktopAnchorRefreshJob = null
        desktopAnchorInAppJob?.cancel()
        desktopAnchorInAppJob = null
        desktopAnchorPulseJob?.cancel()
        desktopAnchorPulseJob = null
        desktopAnchorSnapAnimator?.cancel()
        desktopAnchorSnapAnimator = null
        desktopAnchorDragging.value = false
        desktopAnchorPanelOpen.value = false
        desktopAnchorPulse.value = null
        desktopAnchorInApp.value = null
        desktopAnchorActiveLock.value = null
        removeDesktopAnchorPanelView()
        desktopAnchorView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) { /* ignore */ }
            desktopAnchorView = null
        }
        desktopAnchorParams = null
    }

    /** 创建一个能承载 Compose 内容的 ComposeView，并正确设置 Lifecycle */
    private fun createComposeView(
        solidBackgroundColor: Int? = null,
        content: @androidx.compose.runtime.Composable () -> Unit
    ): ComposeView {
        val lifecycleOwner = OverlayLifecycleOwner()
        lifecycleOwner.start()

        return ComposeView(context).apply {
            // 悬浮窗 View 在某些设备/场景下默认使用软件渲染，
            // 而 Compose 的 GraphicsLayer、LazyLayout 动画、OverscrollModifier 等
            // 内部会调用 drawRenderNode，软件渲染不支持此操作会崩溃。
            // 强制开启硬件加速层以规避 "Software rendering doesn't support drawRenderNode"。
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
            if (solidBackgroundColor != null) {
                setBackgroundColor(solidBackgroundColor)
            }
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            })
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent { content() }
        }
    }

    /**
     * Android 12+ 对「别的 App 全屏遮挡 + 触摸穿透」做了安全过滤：
     * 窗口 alpha 超过 [InputManager.getMaximumObscuringOpacityForTouch]（通常 0.8）时，
     * 会丢弃下层触摸并弹出「未针对最新版本 Android 优化 / 触摸可能延迟」类提示。
     * 仅用于 FLAG_NOT_TOUCHABLE 的氛围/引导层，不影响可点击拦截页。
     */
    private fun applyTrustedPassThroughOpacity(params: WindowManager.LayoutParams) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val maxOpacity = try {
            context.getSystemService(InputManager::class.java)
                ?.maximumObscuringOpacityForTouch
                ?: 0.8f
        } catch (_: Exception) {
            0.8f
        }
        params.alpha = (maxOpacity - 0.05f).coerceIn(0.01f, 0.75f)
    }

    /**
     * 全屏沉浸拦截层 Window：铺满状态栏/刘海，并请求隐藏状态栏。
     * @param extraFlags 额外 flags（如 FLAG_WATCH_OUTSIDE_TOUCH）
     */
    private fun fullscreenImmersiveOverlayParams(
        extraFlags: Int = 0
    ): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                extraFlags,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    /**
     * 包一层以接收系统返回 / 最近任务键。
     * Overlay 没有 OnBackPressedDispatcher，必须在 View 上吃按键。
     */
    private fun wrapInterceptKeyHost(content: View): View {
        if (content is InterceptKeyHost) return content
        val host = InterceptKeyHost(context)
        // Compose 从窗口根查找 ViewTreeLifecycleOwner；只设在子 ComposeView 上会崩
        val lifecycleOwner = content.findViewTreeLifecycleOwner()
            ?: OverlayLifecycleOwner().also { it.start() }
        host.setViewTreeLifecycleOwner(lifecycleOwner)
        host.setViewTreeViewModelStoreOwner(
            content.findViewTreeViewModelStoreOwner()
                ?: object : ViewModelStoreOwner {
                    override val viewModelStore = ViewModelStore()
                }
        )
        val savedOwner = content.findViewTreeSavedStateRegistryOwner()
            ?: lifecycleOwner as? SavedStateRegistryOwner
        if (savedOwner != null) {
            host.setViewTreeSavedStateRegistryOwner(savedOwner)
        }
        host.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        host.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        return host
    }

    private inner class InterceptKeyHost(context: Context) : FrameLayout(context) {
        private var attachedAtElapsed: Long = 0L

        init {
            isFocusable = true
            isFocusableInTouchMode = true
            descendantFocusability = FOCUS_AFTER_DESCENDANTS
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_ESCAPE,
                KeyEvent.KEYCODE_HOME,
                KeyEvent.KEYCODE_APP_SWITCH -> return true
            }
            return super.dispatchKeyEvent(event)
        }

        override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
            super.onWindowFocusChanged(hasWindowFocus)
            if (hasWindowFocus) {
                onInterceptGainedFocus?.invoke()
                return
            }
            // 刚挂上时 requestFocus 会闪一次失焦，不能当 Home / 最近任务
            if (attachedAtElapsed > 0L &&
                android.os.SystemClock.elapsedRealtime() - attachedAtElapsed < 800L
            ) {
                return
            }
            onInterceptLostFocus?.invoke()
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            attachedAtElapsed = android.os.SystemClock.elapsedRealtime()
            post { requestFocus() }
        }
    }

    /** 最近任务：卸掉拦截层但保留目标，避免挡住系统进程列表。 */
    fun parkInterceptForRecents() {
        val target = interceptTargetPackage ?: return
        interceptParkedForRecents = true
        isInterceptVisible.set(true)
        interceptTargetPackage = target
        if (Looper.myLooper() == Looper.getMainLooper()) {
            detachInterceptViewKeepingTarget()
        } else {
            mainHandler.post { detachInterceptViewKeepingTarget() }
        }
    }

    fun clearRecentsPark() {
        interceptParkedForRecents = false
    }

    fun interceptWindowHasFocus(): Boolean {
        val view = interceptView ?: return false
        return if (Looper.myLooper() == Looper.getMainLooper()) {
            view.hasWindowFocus()
        } else {
            var focused = false
            val latch = java.util.concurrent.CountDownLatch(1)
            mainHandler.post {
                focused = interceptView?.hasWindowFocus() == true
                latch.countDown()
            }
            latch.await(120L, java.util.concurrent.TimeUnit.MILLISECONDS)
            focused
        }
    }

    fun requestInterceptFocus() {
        mainHandler.post { interceptView?.requestFocus() }
    }

    private fun detachInterceptViewKeepingTarget() {
        stopInterceptLayerRefresh()
        interceptView?.let {
            it.alpha = 0f
            it.setLayerType(View.LAYER_TYPE_NONE, null)
            try { windowManager.removeView(it) } catch (_: Exception) { }
            interceptView = null
        }
        interceptParams = null
        interceptContentIsCompose = false
    }

    /** 挂上拦截层并做沉浸 + 层刷新；先加新层再卸旧层，避免中间露底。 */
    private fun addInterceptOverlayView(view: View, params: WindowManager.LayoutParams) {
        interceptParkedForRecents = false
        interceptContentIsCompose = view is ComposeView
        val hosted = wrapInterceptKeyHost(view)
        val previous = interceptView
        hosted.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                if (interceptView !== v) return
                stopInterceptLayerRefresh()
                interceptView = null
                interceptParams = null
                interceptContentIsCompose = false
            }
        })
        windowManager.addView(hosted, params)
        interceptView = hosted
        interceptParams = params
        if (previous != null && previous !== hosted) {
            previous.alpha = 0f
            previous.setLayerType(android.view.View.LAYER_TYPE_NONE, null)
            try {
                windowManager.removeView(previous)
            } catch (_: Exception) {
            }
        }
        applyImmersiveStatusBarHide(hosted)
        attachInterceptOutsideTouchRecovery(hosted)
        scheduleInterceptOverlayBumps()
        startInterceptLayerRefresh()
        // 正式页挂上后再压一拍：短视频常在占位层之后才起播
        val pkg = interceptTargetPackage
        if (!pkg.isNullOrBlank() && isInterceptVisible.get()) {
            onInterceptCoverPrepared?.invoke(pkg)
        }
    }

    private fun attachInterceptOutsideTouchRecovery(view: View) {
        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                bumpInterceptOverlayLayer()
                MonitorForegroundService.requestImmediateForegroundCheck()
                true
            } else {
                false
            }
        }
    }

    /** @param relayout true 时强制 requestLayout + updateViewLayout（仅首帧露出用）；日常刷新只 invalidate，避免打断 Compose 动效 */
    private fun bumpInterceptOverlayLayer(relayout: Boolean = false) {
        val view = interceptView ?: return
        try {
            view.invalidate()
            if (!relayout) return
            val params = interceptParams ?: return
            view.requestLayout()
            windowManager.updateViewLayout(view, params)
        } catch (_: Exception) {
            // ignore
        }
    }

    private fun scheduleInterceptOverlayBumps() {
        val view = interceptView ?: return
        val params = interceptParams ?: return
        fun bump() {
            try {
                bumpInterceptOverlayLayer(relayout = true)
            } catch (_: Exception) {
                // ignore
            }
        }
        view.post { bump() }
        mainHandler.postDelayed({ bump() }, 32L)
        mainHandler.postDelayed({ bump() }, 120L)
    }

    /** 拦截展示初期轻量刷新，缓解「层已加但未绘制」；避免周期性全量 relayout 造成动效掉帧。 */
    private fun startInterceptLayerRefresh() {
        interceptLayerRefreshJob?.cancel()
        interceptLayerRefreshJob = scope.launch {
            var ticks = 0
            while (isInterceptVisible.get() && interceptView != null && ticks < 5) {
                delay(480L)
                mainHandler.post { bumpInterceptOverlayLayer(relayout = false) }
                ticks++
            }
        }
    }

    private fun stopInterceptLayerRefresh() {
        interceptLayerRefreshJob?.cancel()
        interceptLayerRefreshJob = null
    }

    /**
     * 隐藏状态栏，让拦截页更沉浸。
     * 部分 OEM 对悬浮窗限制较大；失败时至少已铺满状态栏区域由背景盖住。
     */
    private fun applyImmersiveStatusBarHide(view: View) {
        fun hide() {
            @Suppress("DEPRECATION")
            view.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
            ViewCompat.getWindowInsetsController(view)?.let { controller ->
                controller.hide(WindowInsetsCompat.Type.statusBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        hide()
        view.doOnAttach { hide() }
        @Suppress("DEPRECATION")
        view.setOnSystemUiVisibilityChangeListener { visibility ->
            if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                hide()
            }
        }
    }
}
