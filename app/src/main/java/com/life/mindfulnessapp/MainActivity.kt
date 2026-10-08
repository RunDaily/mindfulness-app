package com.life.mindfulnessapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.PlanAddAppActivity
import com.life.mindfulnessapp.InstrumentConfigActivity
import com.life.mindfulnessapp.PlanBlockListActivity
import com.life.mindfulnessapp.ScheduleLockEditActivity
import com.life.mindfulnessapp.service.MonitorForegroundService
import com.life.mindfulnessapp.ui.applist.AddAppLimitScreen
import com.life.mindfulnessapp.ui.applist.AppCapabilityEditScreen
import com.life.mindfulnessapp.ui.applist.AppHistoryScreen
import com.life.mindfulnessapp.ui.applist.AppLimitEditScreen
import com.life.mindfulnessapp.ui.applist.IntentPoolDetailScreen
import com.life.mindfulnessapp.ui.applist.IntentPoolManageScreen
import com.life.mindfulnessapp.ui.applist.DeepLinkGlanceScreen
import com.life.mindfulnessapp.ui.applist.QuickIntentTagsScreen
import com.life.mindfulnessapp.ui.applist.CapabilityBindScreen
import com.life.mindfulnessapp.ui.applist.MonitorManageScreen
import com.life.mindfulnessapp.ui.applist.MonitorReorderScreen
import com.life.mindfulnessapp.ui.applist.parsePrimaryCapabilitySeed
import com.life.mindfulnessapp.ui.features.AppWeekRhythmScreen
import com.life.mindfulnessapp.ui.features.ExploreAppDetailScreen
import com.life.mindfulnessapp.ui.features.ExploreUsageRankScreen
import com.life.mindfulnessapp.ui.features.TimeRulerScreen
import com.life.mindfulnessapp.ui.features.UsageLogScreen
import com.life.mindfulnessapp.ui.features.WalkAwarenessScreen
import com.life.mindfulnessapp.ui.plan.AppTrendScreen
import com.life.mindfulnessapp.ui.plan.PlanScreen
import com.life.mindfulnessapp.ui.plan.TodayAppDetailScreen
import com.life.mindfulnessapp.ui.plan.TodayRulesScreen
import com.life.mindfulnessapp.ui.theme.CapabilityKind
import com.life.mindfulnessapp.ui.home.HomeViewModel
import androidx.activity.viewModels
import com.life.mindfulnessapp.ui.discover.DiscoverScreen
import com.life.mindfulnessapp.ui.discover.OverviewScreen
import com.life.mindfulnessapp.ui.home.DayReportScreen
import com.life.mindfulnessapp.ui.home.RecordHistoryScreen
import com.life.mindfulnessapp.ui.navigation.AppLimitTransitions
import com.life.mindfulnessapp.ui.navigation.BottomTab
import com.life.mindfulnessapp.ui.navigation.Screen
import com.life.mindfulnessapp.ui.navigation.isNavigatingToAppLimit
import com.life.mindfulnessapp.ui.navigation.isNavigatingToCapabilityBind
import com.life.mindfulnessapp.ui.navigation.isPoppingFromAppLimit
import com.life.mindfulnessapp.ui.navigation.isPoppingFromCapabilityBind
import com.life.mindfulnessapp.ui.navigation.navigateDayReportBridge
import com.life.mindfulnessapp.ui.navigation.navigateOverviewBridge
import com.life.mindfulnessapp.ui.onboarding.OnboardingScreen
import com.life.mindfulnessapp.ui.profile.AboutScreen
import com.life.mindfulnessapp.ui.profile.ProductManualScreen
import com.life.mindfulnessapp.ui.profile.AppUpdateScreen
import com.life.mindfulnessapp.ui.profile.FeedbackScreen
import com.life.mindfulnessapp.ui.profile.PlaygroundScreen
import com.life.mindfulnessapp.ui.profile.QuoteBrowseScreen
import com.life.mindfulnessapp.ui.profile.QuotePlayScreen
import com.life.mindfulnessapp.ui.profile.WallpaperStudioScreen
import com.life.mindfulnessapp.ui.profile.ProfileScreen
import com.life.mindfulnessapp.ui.schedule.ScheduleScreen
import com.life.mindfulnessapp.ui.settings.KeepAliveGuideScreen
import com.life.mindfulnessapp.ui.settings.VivoOriginOsKeepAliveGuideScreen
import com.life.mindfulnessapp.ui.settings.PositiveDestinationsScreen
import com.life.mindfulnessapp.ui.settings.AuthorCatalogScreen
import com.life.mindfulnessapp.ui.settings.QuoteMomentScreen
import com.life.mindfulnessapp.ui.settings.QuotePushScheduleScreen
import com.life.mindfulnessapp.ui.settings.SettingsScreen
import com.life.mindfulnessapp.ui.settings.ThemeScreen
import com.life.mindfulnessapp.ui.debug.SearchDeepLinkTestScreen
import com.life.mindfulnessapp.ui.theme.*
import com.life.mindfulnessapp.ui.vip.VipScreen
import androidx.compose.runtime.collectAsState
import com.life.mindfulnessapp.ui.update.AppUpdateHost
import com.life.mindfulnessapp.ui.update.AppUpdateViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import androidx.lifecycle.lifecycleScope
import javax.inject.Inject

val Context.dataStore by preferencesDataStore(name = "settings")
val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
val PRIVACY_ACCEPTED = booleanPreferencesKey("privacy_policy_accepted")

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var analyticsRepository: com.life.mindfulnessapp.data.repository.AnalyticsRepository

    @Inject
    lateinit var monitorProfileReporter: com.life.mindfulnessapp.data.repository.MonitorProfileReporter

    @Inject
    lateinit var feedbackInboxRepository: com.life.mindfulnessapp.data.repository.FeedbackInboxRepository

    // 用 by viewModels() 确保与 HomeScreen 里的 hiltViewModel() 是同一个实例
    private val homeViewModel: HomeViewModel by viewModels()

    private val appUpdateViewModel: AppUpdateViewModel by viewModels()

    /** 来自 Service 的「打开 App 限制编辑」请求，存储待跳转的 packageName（State，改变时触发重组） */
    private var pendingAppLimitEditPackage by mutableStateOf<String?>(null)
    /** 非空时直达单能力配置页（[CapabilityKind.name]） */
    private var pendingAppLimitEditCapability by mutableStateOf<String?>(null)
    /** 来自门口「调整」：快捷标签管理 */
    private var pendingQuickIntentTagsPackage by mutableStateOf<String?>(null)
    private var pendingQuickIntentTagsAppName by mutableStateOf("")

    /** 来自离开轻条：打开「想去的地方」配置 */
    private var pendingNavigatePositiveDestinations by mutableStateOf(false)

    /** 来自离开轻条「详细」：打开某 App 的记录页 */
    private var pendingAppHistoryPackage by mutableStateOf<String?>(null)
    private var pendingAppHistoryRecordId by mutableStateOf(-1L)

    /** 来自开发者回复通知：打开反馈详情 */
    private var pendingFeedbackReplyId by mutableStateOf<Long?>(null)

    /** 来自「监控曾中断」恢复通知：打开后台保活指南 */
    private var pendingNavigateKeepAliveGuide by mutableStateOf(false)
    private var pendingNavigateSchedule by mutableStateOf(false)

    /** 收据回望：打开发现 · 总览（带按日） */
    private var pendingOverviewRoute by mutableStateOf<String?>(null)

    /** 来自格言推送通知：打开格言时刻页 */
    private var pendingQuoteMoment by mutableStateOf<PendingQuoteMoment?>(null)

    data class PendingQuoteMoment(
        val id: Int,
        val content: String,
        val author: String,
        val authorId: Int
    )

    /**
     * 用户在心锚内手动结束计时时，触发 Snackbar 的标志。
     * true = 需要显示 Snackbar，显示后由 UI 重置为 false。
     */
    var showSessionEndedSnackbar by mutableStateOf(false)
        private set

    /** Snackbar 文案：已回顾 / 可去回顾 */
    var sessionEndedSnackbarMessage by mutableStateOf("计时已结束 ✓")
        private set

    /**
     * 需要导航到「今日」并高亮的 recordId。
     * 来自：被监控 App 内结束 / 心锚内结束广播 / 通知点击。
     * UI 消费后应调用 [onNavigateHomeForHighlightHandled]。
     */
    var pendingNavigateHomeHighlightId by mutableStateOf<Long?>(null)
        private set

    /** 接收来自 MonitorForegroundService 的「会话在 App 内结束」LocalBroadcast */
    private val sessionEndedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == MonitorForegroundService.ACTION_SESSION_ENDED_IN_APP) {
                val recordId = intent.getLongExtra(MonitorForegroundService.EXTRA_NOTE_RECORD_ID, -1L)
                val reviewed = intent.getBooleanExtra(
                    MonitorForegroundService.EXTRA_SESSION_REVIEWED,
                    false
                )
                if (recordId != -1L) {
                    homeViewModel.requestOpenNote(recordId)
                    pendingNavigateHomeHighlightId = recordId
                }
                sessionEndedSnackbarMessage = if (reviewed) {
                    "计时已结束，对照已保存 ✓"
                } else {
                    "计时已结束 ✓"
                }
                showSessionEndedSnackbar = true
            }
        }
    }

    /** 供 UI 层在 Snackbar 展示完毕后调用，重置标志 */
    fun onSessionEndedSnackbarShown() {
        showSessionEndedSnackbar = false
    }

    fun onNavigateHomeForHighlightHandled() {
        pendingNavigateHomeHighlightId = null
    }

    /**
     * 处理来自 Service 的各类导航 Intent。
     */
    private fun handleIncomingIntent(intent: Intent?) {
        when (intent?.action) {
            MonitorForegroundService.ACTION_OPEN_NOTE -> {
                val recordId = intent.getLongExtra(MonitorForegroundService.EXTRA_NOTE_RECORD_ID, -1L)
                if (recordId != -1L) {
                    val openCompare = intent.getBooleanExtra(
                        MonitorForegroundService.EXTRA_OPEN_COMPARE,
                        false
                    )
                    if (openCompare) {
                        homeViewModel.requestOpenCompare(recordId)
                    } else {
                        homeViewModel.requestOpenNote(recordId)
                    }
                    pendingNavigateHomeHighlightId = recordId
                }
            }
            MonitorForegroundService.ACTION_OPEN_APP_LIMIT_EDIT -> {
                val pkg = intent.getStringExtra(MonitorForegroundService.EXTRA_APP_PACKAGE_NAME)
                if (!pkg.isNullOrEmpty()) {
                    pendingAppLimitEditCapability = intent.getStringExtra(
                        MonitorForegroundService.EXTRA_OPEN_CAPABILITY
                    )
                    pendingAppLimitEditPackage = pkg
                }
            }
            MonitorForegroundService.ACTION_OPEN_QUICK_INTENT_TAGS -> {
                val pkg = intent.getStringExtra(MonitorForegroundService.EXTRA_APP_PACKAGE_NAME)
                if (!pkg.isNullOrEmpty()) {
                    pendingQuickIntentTagsAppName =
                        intent.getStringExtra(MonitorForegroundService.EXTRA_APP_NAME).orEmpty()
                    pendingQuickIntentTagsPackage = pkg
                }
            }
            MonitorForegroundService.ACTION_OPEN_POSITIVE_DESTINATIONS -> {
                pendingNavigatePositiveDestinations = true
            }
            MonitorForegroundService.ACTION_OPEN_APP_HISTORY -> {
                val pkg = intent.getStringExtra(MonitorForegroundService.EXTRA_APP_PACKAGE_NAME)
                if (!pkg.isNullOrEmpty()) {
                    pendingAppHistoryRecordId = intent.getLongExtra(
                        MonitorForegroundService.EXTRA_NOTE_RECORD_ID,
                        0L
                    )
                    pendingAppHistoryPackage = pkg
                }
            }
            MonitorForegroundService.ACTION_OPEN_FEEDBACK_REPLY -> {
                val id = intent.getLongExtra(MonitorForegroundService.EXTRA_FEEDBACK_ID, -1L)
                if (id > 0L) {
                    pendingFeedbackReplyId = id
                }
            }
            MonitorForegroundService.ACTION_OPEN_SCHEDULE -> {
                pendingNavigateSchedule = true
            }
            ACTION_OPEN_WEEK_OVERVIEW,
            ACTION_OPEN_OVERVIEW -> {
                val dateMs = intent.getLongExtra(EXTRA_OVERVIEW_DATE_MS, 0L)
                val lens = intent.getStringExtra(EXTRA_OVERVIEW_LENS).orEmpty().ifBlank { "gate" }
                pendingOverviewRoute = Screen.Overview.createRoute(
                    lens = lens,
                    cut = "day",
                    dateMs = dateMs
                )
            }
            ACTION_OPEN_USAGE_OVERVIEW -> {
                val dateMs = intent.getLongExtra(EXTRA_OVERVIEW_DATE_MS, 0L)
                pendingOverviewRoute = Screen.Overview.createUsageRoute(cut = "day", dateMs = dateMs)
            }
            com.life.mindfulnessapp.util.MonitorHealthStore.ACTION_OPEN_KEEP_ALIVE_GUIDE -> {
                pendingNavigateKeepAliveGuide = true
                homeViewModel.dismissMonitorInterruptBanner()
            }
        }
        // 格言推送通知：不依赖 action（默认 MAIN）
        if (intent?.getBooleanExtra(
                com.life.mindfulnessapp.util.QuotePushNotifier.EXTRA_OPEN_QUOTE_MOMENT,
                false
            ) == true
        ) {
            pendingQuoteMoment = PendingQuoteMoment(
                id = intent.getIntExtra(
                    com.life.mindfulnessapp.util.QuotePushNotifier.EXTRA_QUOTE_ID,
                    0
                ),
                content = intent.getStringExtra(
                    com.life.mindfulnessapp.util.QuotePushNotifier.EXTRA_QUOTE_CONTENT
                ).orEmpty(),
                author = intent.getStringExtra(
                    com.life.mindfulnessapp.util.QuotePushNotifier.EXTRA_QUOTE_AUTHOR
                ).orEmpty(),
                authorId = intent.getIntExtra(
                    com.life.mindfulnessapp.util.QuotePushNotifier.EXTRA_QUOTE_AUTHOR_ID,
                    0
                )
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // 国产机默认隐藏最近任务，降低一键清理点名概率
        com.life.mindfulnessapp.util.RecentsHider.applyFromActivity(
            this,
            appPreferences.isHideFromRecentsEnabled()
        )
        // 回到前台时拉取开发者回复：仅更新 App 内红点，不重复发系统通知
        lifecycleScope.launch {
            feedbackInboxRepository.syncReplies(notify = false, minIntervalMs = 8_000L)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val darkTheme = appPreferences.isDarkThemeEnabled()
        window.setBackgroundDrawableResource(
            if (darkTheme) R.color.app_window_bg_night else R.color.app_window_bg_day
        )
        val barStyle = if (darkTheme) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        }
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
        // 处理冷启动时携带的 Intent
        handleIncomingIntent(intent)

        // 注册「会话在 App 内结束」LocalBroadcast 接收器，用于显示 Snackbar 轻提示
        LocalBroadcastManager.getInstance(this).registerReceiver(
            sessionEndedReceiver,
            IntentFilter(MonitorForegroundService.ACTION_SESSION_ENDED_IN_APP)
        )

        val isOnboardingCompleted = runBlocking {
            dataStore.data.map { it[ONBOARDING_COMPLETED] ?: false }.first()
        }
        val isPrivacyAccepted = runBlocking {
            dataStore.data.map { it[PRIVACY_ACCEPTED] ?: false }.first()
        }
        appPreferences.migrateInviteUnlockToVipIfNeeded()
        analyticsRepository.trackAppOpen()
        monitorProfileReporter.pushSnapshotNow(
            com.life.mindfulnessapp.data.analytics.HaEvents.SnapshotReason.COLD_START
        )

        // 引导完成后默认拉起监控 + 加强保活（设置页不再暴露开关）
        if (isOnboardingCompleted) {
            MonitorForegroundService.start(this)
            appPreferences.setEnhancedKeepAlive(true)
        }

        setContent {
            // 实时监听主题偏好，支持设置页切换后立即生效
            val themePackPref by appPreferences.themePack.collectAsState()
            val themeFollow by appPreferences.themeFollowSystem.collectAsState()
            val themePack = rememberResolvedThemePack(themePackPref, themeFollow)
            val isDarkTheme = themePack.isDark

            SideEffect {
                window.setBackgroundDrawableResource(themePack.chrome().windowBgRes)
            }

            MindfulnessAppTheme(themePack = themePack) {
                MindfulnessApp(
                    initialOnboardingDone = isOnboardingCompleted,
                    isPrivacyAccepted = isPrivacyAccepted,
                    isDarkTheme = isDarkTheme,
                    appUpdateViewModel = appUpdateViewModel,
                    pendingAppLimitEditPackage = pendingAppLimitEditPackage,
                    pendingAppLimitEditCapability = pendingAppLimitEditCapability,
                    onAppLimitEditHandled = {
                        pendingAppLimitEditPackage = null
                        pendingAppLimitEditCapability = null
                    },
                    pendingQuickIntentTagsPackage = pendingQuickIntentTagsPackage,
                    pendingQuickIntentTagsAppName = pendingQuickIntentTagsAppName,
                    onQuickIntentTagsHandled = {
                        pendingQuickIntentTagsPackage = null
                        pendingQuickIntentTagsAppName = ""
                    },
                    pendingNavigatePositiveDestinations = pendingNavigatePositiveDestinations,
                    onNavigatePositiveDestinationsHandled = {
                        pendingNavigatePositiveDestinations = false
                    },
                    pendingAppHistoryPackage = pendingAppHistoryPackage,
                    pendingAppHistoryRecordId = pendingAppHistoryRecordId,
                    onAppHistoryHandled = {
                        pendingAppHistoryPackage = null
                        pendingAppHistoryRecordId = -1L
                    },
                    pendingFeedbackReplyId = pendingFeedbackReplyId,
                    onFeedbackReplyHandled = { pendingFeedbackReplyId = null },
                    pendingNavigateKeepAliveGuide = pendingNavigateKeepAliveGuide,
                    onNavigateKeepAliveGuideHandled = { pendingNavigateKeepAliveGuide = false },
                    pendingNavigateSchedule = pendingNavigateSchedule,
                    onNavigateScheduleHandled = { pendingNavigateSchedule = false },
                    pendingOverviewRoute = pendingOverviewRoute,
                    onOverviewRouteHandled = { pendingOverviewRoute = null },
                    pendingQuoteMoment = pendingQuoteMoment,
                    onQuoteMomentHandled = { pendingQuoteMoment = null },
                    pendingNavigateHomeHighlightId = pendingNavigateHomeHighlightId,
                    onNavigateHomeForHighlightHandled = { onNavigateHomeForHighlightHandled() },
                    showSessionEndedSnackbar = showSessionEndedSnackbar,
                    sessionEndedSnackbarMessage = sessionEndedSnackbarMessage,
                    onSessionEndedSnackbarShown = { onSessionEndedSnackbarShown() },
                    onPrivacyAccept = {
                        runBlocking { dataStore.edit { it[PRIVACY_ACCEPTED] = true } }
                        analyticsRepository.trackPrivacyAccept()
                    },
                    onPrivacyDecline = { finish() },
                    onOnboardingComplete = {
                        runBlocking {
                            dataStore.edit { it[ONBOARDING_COMPLETED] = true }
                        }
                        // 埋点已在 OnboardingViewModel.completeOnboarding 上报
                        MonitorForegroundService.start(this)
                        appPreferences.setEnhancedKeepAlive(true)
                    },
                    onFirstBindSkip = { analyticsRepository.trackFirstBindSkip() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(sessionEndedReceiver)
    }

    companion object {
        const val ACTION_OPEN_OVERVIEW =
            "com.life.mindfulnessapp.action.OPEN_OVERVIEW"
        /** @deprecated 用 [ACTION_OPEN_OVERVIEW] */
        const val ACTION_OPEN_WEEK_OVERVIEW =
            "com.life.mindfulnessapp.action.OPEN_WEEK_OVERVIEW"
        /** @deprecated 用 [ACTION_OPEN_OVERVIEW] + lens=usage */
        const val ACTION_OPEN_USAGE_OVERVIEW =
            "com.life.mindfulnessapp.action.OPEN_USAGE_OVERVIEW"
        const val EXTRA_OVERVIEW_DATE_MS = "overview_date_ms"
        const val EXTRA_OVERVIEW_LENS = "overview_lens"

        fun openOverviewIntent(
            context: Context,
            dateMs: Long = 0L,
            lens: String = "gate"
        ): Intent =
            Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_OVERVIEW
                putExtra(EXTRA_OVERVIEW_DATE_MS, dateMs)
                putExtra(EXTRA_OVERVIEW_LENS, lens)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }

        fun openWeekOverviewIntent(context: Context, dateMs: Long = 0L): Intent =
            openOverviewIntent(context, dateMs, lens = "gate")

        fun openUsageOverviewIntent(context: Context, dateMs: Long = 0L): Intent =
            openOverviewIntent(context, dateMs, lens = "usage")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MindfulnessApp(
    initialOnboardingDone: Boolean,
    isPrivacyAccepted: Boolean = false,
    isDarkTheme: Boolean = true,
    appUpdateViewModel: AppUpdateViewModel,
    pendingAppLimitEditPackage: String? = null,
    pendingAppLimitEditCapability: String? = null,
    onAppLimitEditHandled: () -> Unit = {},
    pendingQuickIntentTagsPackage: String? = null,
    pendingQuickIntentTagsAppName: String = "",
    onQuickIntentTagsHandled: () -> Unit = {},
    pendingNavigatePositiveDestinations: Boolean = false,
    onNavigatePositiveDestinationsHandled: () -> Unit = {},
    pendingAppHistoryPackage: String? = null,
    pendingAppHistoryRecordId: Long = -1L,
    onAppHistoryHandled: () -> Unit = {},
    pendingFeedbackReplyId: Long? = null,
    onFeedbackReplyHandled: () -> Unit = {},
    pendingNavigateKeepAliveGuide: Boolean = false,
    onNavigateKeepAliveGuideHandled: () -> Unit = {},
    pendingNavigateSchedule: Boolean = false,
    onNavigateScheduleHandled: () -> Unit = {},
    pendingOverviewRoute: String? = null,
    onOverviewRouteHandled: () -> Unit = {},
    pendingQuoteMoment: MainActivity.PendingQuoteMoment? = null,
    onQuoteMomentHandled: () -> Unit = {},
    pendingNavigateHomeHighlightId: Long? = null,
    onNavigateHomeForHighlightHandled: () -> Unit = {},
    showSessionEndedSnackbar: Boolean = false,
    sessionEndedSnackbarMessage: String = "计时已结束 ✓",
    onSessionEndedSnackbarShown: () -> Unit = {},
    onPrivacyAccept: () -> Unit = {},
    onPrivacyDecline: () -> Unit = {},
    onOnboardingComplete: () -> Unit,
    onFirstBindSkip: () -> Unit = {}
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    /** 引导结束后弹出「系上第一枚锚」；返回/稍后清除 */
    var firstBindAfterOnboarding by remember { mutableStateOf(false) }
    /** 首页坑位「+」：切到能力 Tab 的「管理」 */
    var openFeaturesManage by remember { mutableStateOf(false) }
    var onboardingDone by remember { mutableStateOf(initialOnboardingDone) }
    var privacyAccepted by remember { mutableStateOf(isPrivacyAccepted) }

    // 引导与隐私完成后，自动检查官网更新
    LaunchedEffect(onboardingDone, privacyAccepted) {
        if (onboardingDone && privacyAccepted) {
            appUpdateViewModel.checkOnLaunch()
        }
    }

    // 监听 showSessionEndedSnackbar 标志，触发时展示 Snackbar
    LaunchedEffect(showSessionEndedSnackbar) {
        if (showSessionEndedSnackbar) {
            snackbarHostState.showSnackbar(
                message = sessionEndedSnackbarMessage,
                duration = SnackbarDuration.Short
            )
            onSessionEndedSnackbarShown()
        }
    }

    // 到点收口 / 结束并去心锚：强制落到「今日」并高亮该条
    LaunchedEffect(pendingNavigateHomeHighlightId) {
        if (pendingNavigateHomeHighlightId == null) return@LaunchedEffect
        navController.navigate(Screen.Home.route) {
            popUpTo(Screen.Home.route) { inclusive = false }
            launchSingleTop = true
        }
        onNavigateHomeForHighlightHandled()
    }

    // 离开轻条「下次可设一个想去的地方」
    LaunchedEffect(pendingNavigatePositiveDestinations) {
        if (!pendingNavigatePositiveDestinations) return@LaunchedEffect
        if (AppPreferences.POSITIVE_DESTINATIONS_ENABLED) {
            navController.navigate(Screen.PositiveDestinations.route) {
                launchSingleTop = true
            }
        }
        onNavigatePositiveDestinationsHandled()
    }

    // 离开轻条「详细」：直达该 App 的记录页并高亮刚结束的那一条
    LaunchedEffect(pendingAppHistoryPackage, pendingAppHistoryRecordId) {
        val pkg = pendingAppHistoryPackage ?: return@LaunchedEffect
        navController.navigate(
            Screen.AppHistory.createRoute(pkg, pendingAppHistoryRecordId)
        ) {
            launchSingleTop = true
        }
        onAppHistoryHandled()
    }

    // 开发者回复通知：打开反馈页并定位到该条
    var openFeedbackReplyId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(pendingFeedbackReplyId) {
        val id = pendingFeedbackReplyId ?: return@LaunchedEffect
        openFeedbackReplyId = id
        navController.navigate(Screen.Feedback.route) {
            launchSingleTop = true
        }
        onFeedbackReplyHandled()
    }

    // 监控恢复通知：打开后台保活指南
    LaunchedEffect(pendingNavigateKeepAliveGuide) {
        if (!pendingNavigateKeepAliveGuide) return@LaunchedEffect
        navController.navigate(Screen.KeepAliveGuide.route) {
            launchSingleTop = true
        }
        onNavigateKeepAliveGuideHandled()
    }

    // 桌面心锚 → 发现 · 日程锁
    LaunchedEffect(pendingNavigateSchedule) {
        if (!pendingNavigateSchedule) return@LaunchedEffect
        navController.navigate(Screen.Discover.route) {
            popUpTo(Screen.Home.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        navController.navigate(Screen.Schedule.route) {
            launchSingleTop = true
        }
        onNavigateScheduleHandled()
    }

    // 收据回望 → 发现 · 总览
    LaunchedEffect(pendingOverviewRoute) {
        val route = pendingOverviewRoute ?: return@LaunchedEffect
        navController.navigate(Screen.Discover.route) {
            popUpTo(Screen.Home.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        navController.navigateOverviewBridge(route)
        onOverviewRouteHandled()
    }

    // 格言推送通知：打开格言时刻页
    LaunchedEffect(pendingQuoteMoment) {
        val q = pendingQuoteMoment ?: return@LaunchedEffect
        navController.navigate(
            Screen.QuoteMoment.create(
                id = q.id,
                content = q.content,
                author = q.author,
                authorId = q.authorId
            )
        ) {
            launchSingleTop = true
        }
        onQuoteMomentHandled()
    }

    // 当从浮窗「重新设定今日目标」/监控配置跳转过来时
    LaunchedEffect(pendingAppLimitEditPackage, pendingAppLimitEditCapability) {
        val pkg = pendingAppLimitEditPackage ?: return@LaunchedEffect
        val capability = pendingAppLimitEditCapability
        val route = if (!capability.isNullOrBlank()) {
            Screen.AppCapabilityEdit.createRoute(pkg, capability)
        } else {
            Screen.AppLimitEdit.createRoute(pkg)
        }
        // 等一帧，避免冷启动时 NavHost 尚未就绪
        kotlinx.coroutines.yield()
        runCatching {
            navController.navigate(route) {
                launchSingleTop = true
                // 从拦截层进来时，尽量落在首页之上，避免无返回栈
                if (onboardingDone) {
                    popUpTo(Screen.Home.route) { inclusive = false }
                }
            }
        }
        onAppLimitEditHandled()
    }

    LaunchedEffect(pendingQuickIntentTagsPackage) {
        val pkg = pendingQuickIntentTagsPackage ?: return@LaunchedEffect
        kotlinx.coroutines.yield()
        runCatching {
            navController.navigate(
                Screen.QuickIntentTags.createRoute(pkg, pendingQuickIntentTagsAppName)
            ) {
                launchSingleTop = true
                if (onboardingDone) {
                    popUpTo(Screen.Home.route) { inclusive = false }
                }
            }
        }
        onQuickIntentTagsHandled()
    }

    val chrome = themeChrome()
    val accentGreen     = chrome.accent
    val bgColor         = chrome.bg

    Box(modifier = Modifier.fillMaxSize()) {
    val startDestination = when {
        onboardingDone -> Screen.Home.route
        else -> Screen.Onboarding.route
    }

    // 计算 Onboarding 初始页：
    //  - 隐私未接受 → 第 0 页（一扇门 + 轻量隐私同意）
    //  - 隐私已接受但未完成 onboarding → 第 1 页（权限）
    val onboardingInitialPage = if (!privacyAccepted) 0 else 1

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route
    val feedbackSettingsVm: com.life.mindfulnessapp.ui.settings.SettingsViewModel = hiltViewModel()
    val feedbackUnread by feedbackSettingsVm.feedbackUnreadCount.collectAsState()
    val pendingUpdate by appUpdateViewModel.pendingUpdate.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var warmResumeNonce by remember { mutableIntStateOf(0) }
    var pendingWarmHomeUpdateCheck by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        var wasStopped = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> wasStopped = true
                Lifecycle.Event.ON_START -> {
                    if (wasStopped) {
                        warmResumeNonce++
                    }
                    wasStopped = false
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 暖启动：同步反馈回复（App 内红点）；回到首页时检测更新（每个新版本弹窗一次）
    LaunchedEffect(warmResumeNonce, onboardingDone, privacyAccepted) {
        if (warmResumeNonce == 0 || !onboardingDone || !privacyAccepted) return@LaunchedEffect
        feedbackSettingsVm.syncFeedbackReplies(notify = false)
        pendingWarmHomeUpdateCheck = true
    }

    LaunchedEffect(pendingWarmHomeUpdateCheck, currentRoute, onboardingDone, privacyAccepted) {
        if (!pendingWarmHomeUpdateCheck || !onboardingDone || !privacyAccepted) return@LaunchedEffect
        if (currentRoute != Screen.Home.route) return@LaunchedEffect
        pendingWarmHomeUpdateCheck = false
        appUpdateViewModel.checkOnHomeResume()
    }

    // 每次切到「我」Tab 时后台刷新待更新状态（带节流）
    LaunchedEffect(currentRoute, onboardingDone, privacyAccepted) {
        if (onboardingDone && privacyAccepted && currentRoute == Screen.Profile.route) {
            appUpdateViewModel.checkOnProfileVisible()
        }
    }

    // 只在 Tab 根页面显示底部导航栏；Onboarding 及二级页面不显示
    val tabRoutes = setOf(
        Screen.Home.route,
        Screen.Features.route,
        Screen.Discover.route,
        Screen.Profile.route
    )
    val showBottomBar = (currentRoute ?: startDestination) in tabRoutes

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = bgColor,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = if (isDarkTheme) NightDockBg else DayDockBg,
                    tonalElevation = 0.dp
                ) {
                    BottomTab.all.forEach { tab ->
                        val selected = currentDestination
                            ?.hierarchy
                            ?.any { it.route == tab.screen.route } == true
                        val showProfileDot =
                            tab == BottomTab.Profile &&
                                (feedbackUnread > 0 || pendingUpdate != null) &&
                                !selected
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.screen.route) {
                                    // 弹回栈到 Home，避免重复堆叠
                                    popUpTo(Screen.Home.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (showProfileDot) {
                                            Badge(
                                                containerColor = Color(0xFFE74C3C),
                                                contentColor = Color.White
                                            )
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.icon,
                                        contentDescription = tab.label
                                    )
                                }
                            },
                            label = {
                                Text(
                                    tab.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = accentGreen,
                                selectedTextColor = accentGreen,
                                indicatorColor = Color.Transparent,
                                unselectedIconColor = if (isDarkTheme) Color(0xFF484F58) else Color(0xFFADB5AD),
                                unselectedTextColor = if (isDarkTheme) Color(0xFF484F58) else Color(0xFFADB5AD)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    initialPage = onboardingInitialPage,
                    isDarkTheme = isDarkTheme,
                    onPrivacyAccept = {
                        privacyAccepted = true
                        onPrivacyAccept()
                    },
                    onPrivacyDecline = onPrivacyDecline,
                    onComplete = {
                        onboardingDone = true
                        onOnboardingComplete()
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(
                route = Screen.Home.route,
                exitTransition = {
                    when {
                        isNavigatingToAppLimit() -> AppLimitTransitions.holdExit()
                        isNavigatingToCapabilityBind() -> AppLimitTransitions.holdExit()
                        else -> null
                    }
                },
                popEnterTransition = {
                    when {
                        isPoppingFromAppLimit() -> AppLimitTransitions.holdPopEnter()
                        isPoppingFromCapabilityBind() -> AppLimitTransitions.holdPopEnter()
                        else -> null
                    }
                }
            ) {
                TodayRulesScreen(
                    onOpenPlan = {
                        navController.navigate(Screen.Features.route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenTodayReceipt = {
                        navController.navigate(Screen.DayReport.createRoute())
                    },
                    onOpenApp = { pkg ->
                        navController.navigate(Screen.TodayAppDetail.createRoute(pkg))
                    },
                    onOpenPhoneApp = { pkg ->
                        navController.navigate(Screen.ExploreAppDetail.createRoute(pkg))
                    }
                )
            }

            composable(
                route = Screen.TodayAppDetail.route,
                arguments = listOf(navArgument("packageName") { type = NavType.StringType })
            ) { backStackEntry ->
                val todayPkg = backStackEntry.arguments?.getString("packageName")
                    ?: return@composable
                val todayContext = LocalContext.current
                TodayAppDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEditRules = {
                        todayContext.startActivity(
                            InstrumentConfigActivity.createIntent(todayContext, todayPkg)
                        )
                    },
                    onOpenTrend = {
                        navController.navigate(Screen.AppTrend.createRoute(todayPkg))
                    },
                    onWeekRhythm = {
                        navController.navigate(Screen.AppWeekRhythm.createRoute(todayPkg))
                    },
                    onOpenTodayReceipt = {
                        navController.navigate(Screen.DayReport.createRoute(todayPkg))
                    }
                )
            }

            composable(
                route = Screen.Features.route,
                exitTransition = {
                    if (isNavigatingToAppLimit()) AppLimitTransitions.holdExit()
                    else null
                },
                popEnterTransition = {
                    if (isPoppingFromAppLimit()) AppLimitTransitions.holdPopEnter()
                    else null
                }
            ) {
                val context = LocalContext.current
                PlanScreen(
                    onAddApp = {
                        context.startActivity(PlanAddAppActivity.createIntent(context))
                    }
                )
            }

            composable(
                route = Screen.ExploreUsageRank.route,
                enterTransition = { AppLimitTransitions.enter() },
                exitTransition = { fadeOut(animationSpec = androidx.compose.animation.core.tween(160)) },
                popEnterTransition = { fadeIn(animationSpec = androidx.compose.animation.core.tween(160)) },
                popExitTransition = { AppLimitTransitions.popExit() }
            ) {
                ExploreUsageRankScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToAppDetail = { packageName ->
                        navController.navigate(Screen.ExploreAppDetail.createRoute(packageName))
                    }
                )
            }

            composable(
                route = Screen.ExploreTimeRuler.route,
                enterTransition = { AppLimitTransitions.enter() },
                exitTransition = { fadeOut(animationSpec = androidx.compose.animation.core.tween(160)) },
                popEnterTransition = { fadeIn(animationSpec = androidx.compose.animation.core.tween(160)) },
                popExitTransition = { AppLimitTransitions.popExit() }
            ) {
                TimeRulerScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.WalkAwareness.route,
                enterTransition = { AppLimitTransitions.enter() },
                exitTransition = { fadeOut(animationSpec = androidx.compose.animation.core.tween(160)) },
                popEnterTransition = { fadeIn(animationSpec = androidx.compose.animation.core.tween(160)) },
                popExitTransition = { AppLimitTransitions.popExit() }
            ) {
                WalkAwarenessScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.MeaningfulThings.route,
                enterTransition = { AppLimitTransitions.enter() },
                exitTransition = { fadeOut(animationSpec = androidx.compose.animation.core.tween(160)) },
                popEnterTransition = { fadeIn(animationSpec = androidx.compose.animation.core.tween(160)) },
                popExitTransition = { AppLimitTransitions.popExit() }
            ) {
                com.life.mindfulnessapp.ui.features.MeaningfulThingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(route = Screen.PlanBlocks.route) {
                val ctx = LocalContext.current
                LaunchedEffect(Unit) {
                    ctx.startActivity(PlanBlockListActivity.createIntent(ctx))
                    navController.popBackStack()
                }
            }

            composable(
                route = Screen.PlanBlockEdit.route,
                arguments = listOf(
                    navArgument("planId") { type = NavType.StringType }
                )
            ) {
                val ctx = LocalContext.current
                val planId = it.arguments?.getString("planId")
                LaunchedEffect(planId) {
                    ctx.startActivity(ScheduleLockEditActivity.createIntent(ctx, planId))
                    navController.popBackStack()
                }
            }

            composable(
                route = Screen.MonitorManage.route,
                exitTransition = {
                    when {
                        isNavigatingToAppLimit() -> AppLimitTransitions.holdExit()
                        isNavigatingToCapabilityBind() -> AppLimitTransitions.holdExit()
                        else -> null
                    }
                },
                popEnterTransition = {
                    when {
                        isPoppingFromAppLimit() -> AppLimitTransitions.holdPopEnter()
                        isPoppingFromCapabilityBind() -> AppLimitTransitions.holdPopEnter()
                        else -> null
                    }
                }
            ) {
                val context = LocalContext.current
                MonitorManageScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToAdd = { kind ->
                        if (kind != null) {
                            context.startActivity(
                                CapabilityBatchPickActivity.createIntent(context, kind)
                            )
                        }
                    },
                    onNavigateToEdit = { packageName ->
                        navController.navigate(Screen.AppLimitEdit.createRoute(packageName))
                    },
                    onNavigateToReorder = {
                        navController.navigate(Screen.MonitorReorder.route)
                    },
                    onNavigateToVip = { navController.navigate(Screen.Vip.route) }
                )
            }

            composable(route = Screen.MonitorReorder.route) {
                MonitorReorderScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEdit = { packageName ->
                        navController.navigate(Screen.AppLimitEdit.createRoute(packageName))
                    }
                )
            }

            composable(
                route = "capability_bind?seed={seed}",
                arguments = listOf(
                    androidx.navigation.navArgument("seed") {
                        type = androidx.navigation.NavType.StringType
                        defaultValue = ""
                    }
                ),
                enterTransition = { AppLimitTransitions.enter() },
                exitTransition = {
                    when {
                        isNavigatingToAppLimit() -> AppLimitTransitions.holdExit()
                        isNavigatingToCapabilityBind() -> AppLimitTransitions.holdExit()
                        else -> fadeOut(animationSpec = androidx.compose.animation.core.tween(160))
                    }
                },
                popEnterTransition = {
                    when {
                        isPoppingFromAppLimit() -> AppLimitTransitions.holdPopEnter()
                        isPoppingFromCapabilityBind() -> AppLimitTransitions.holdPopEnter()
                        else -> fadeIn(animationSpec = androidx.compose.animation.core.tween(160))
                    }
                },
                popExitTransition = { AppLimitTransitions.popExit() }
            ) { backStackEntry ->
                val context = LocalContext.current
                val seedName = backStackEntry.arguments?.getString("seed").orEmpty()
                val preset = seedName.takeIf { it.isNotBlank() }
                    ?.let { com.life.mindfulnessapp.ui.theme.parseCapabilityKind(it) }
                if (preset != null && !firstBindAfterOnboarding) {
                    LaunchedEffect(preset) {
                        context.startActivity(
                            CapabilityBatchPickActivity.createIntent(context, preset)
                        )
                        navController.popBackStack()
                    }
                    return@composable
                }
                CapabilityBindScreen(
                    firstBind = firstBindAfterOnboarding || preset == null,
                    presetCapability = null,
                    onNavigateBack = {
                        if (firstBindAfterOnboarding) {
                            onFirstBindSkip()
                            firstBindAfterOnboarding = false
                        }
                        navController.popBackStack()
                    },
                    onBind = { _, _ -> },
                    onPickCapability = { kind ->
                        firstBindAfterOnboarding = false
                        context.startActivity(
                            CapabilityBatchPickActivity.createIntent(context, kind)
                        )
                        navController.popBackStack()
                    },
                    onNavigateToVip = { navController.navigate(Screen.Vip.route) }
                )
            }

            composable(
                route = Screen.AppList.route,
                exitTransition = {
                    if (isNavigatingToAppLimit()) AppLimitTransitions.holdExit() else null
                },
                popEnterTransition = {
                    if (isPoppingFromAppLimit()) AppLimitTransitions.holdPopEnter() else null
                }
            ) {
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    openFeaturesManage = true
                    navController.navigate(Screen.Features.route) {
                        popUpTo(Screen.AppList.route) { inclusive = true }
                    }
                }
            }

            composable(
                route = Screen.AppLimitAdd.route,
                arguments = listOf(
                    androidx.navigation.navArgument("packageName") {
                        type = androidx.navigation.NavType.StringType
                    },
                    androidx.navigation.navArgument("seed") {
                        type = androidx.navigation.NavType.StringType
                        defaultValue = Screen.AppLimitAdd.SeedAll
                    }
                ),
                enterTransition = { AppLimitTransitions.enter() },
                exitTransition = { fadeOut(animationSpec = androidx.compose.animation.core.tween(160)) },
                popEnterTransition = { fadeIn(animationSpec = androidx.compose.animation.core.tween(160)) },
                popExitTransition = { AppLimitTransitions.popExit() }
            ) { backStackEntry ->
                val packageName = backStackEntry.arguments?.getString("packageName") ?: return@composable
                val seed = backStackEntry.arguments?.getString("seed")
                AddAppLimitScreen(
                    packageName = packageName,
                    primaryCapability = parsePrimaryCapabilitySeed(seed),
                    onNavigateBack = { navController.popBackStack() },
                    onAddSuccess = {
                        // 按来源落点：管理页 → 回管理；否则回首页
                        val backToManage = navController.popBackStack(
                            Screen.MonitorManage.route,
                            inclusive = false
                        )
                        if (!backToManage) {
                            navController.popBackStack(Screen.Home.route, inclusive = false)
                        }
                    },
                    onNavigateToVip = { navController.navigate(Screen.Vip.route) }
                )
            }

            composable(
                route = Screen.AppLimitEdit.route,
                arguments = listOf(
                    androidx.navigation.navArgument("packageName") {
                        type = androidx.navigation.NavType.StringType
                    }
                ),
                enterTransition = { AppLimitTransitions.enter() },
                exitTransition = {
                    if (isNavigatingToAppLimit()) AppLimitTransitions.holdExit()
                    else fadeOut(animationSpec = androidx.compose.animation.core.tween(160))
                },
                popEnterTransition = {
                    if (isPoppingFromAppLimit()) AppLimitTransitions.holdPopEnter()
                    else fadeIn(animationSpec = androidx.compose.animation.core.tween(160))
                },
                popExitTransition = { AppLimitTransitions.popExit() }
            ) { backStackEntry ->
                val packageName = backStackEntry.arguments?.getString("packageName") ?: return@composable
                AppLimitEditScreen(
                    packageName = packageName,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToHistory = {
                        navController.navigate(Screen.AppHistory.createRoute(packageName))
                    },
                    onNavigateToWeekRhythm = {
                        navController.navigate(Screen.AppWeekRhythm.createRoute(packageName))
                    },
                    onNavigateToCapability = { kind ->
                        navController.navigate(
                            Screen.AppCapabilityEdit.createRoute(packageName, kind.name)
                        )
                    },
                    onNavigateToIntentDetail = { entryId ->
                        navController.navigate(
                            Screen.IntentPoolDetail.createRoute(packageName, entryId)
                        )
                    },
                    onNavigateToIntentManage = {
                        navController.navigate(
                            Screen.IntentPoolManage.createRoute(packageName)
                        )
                    }
                )
            }

            composable(
                route = Screen.AppCapabilityEdit.route,
                arguments = listOf(
                    androidx.navigation.navArgument("packageName") {
                        type = androidx.navigation.NavType.StringType
                    },
                    androidx.navigation.navArgument("capability") {
                        type = androidx.navigation.NavType.StringType
                    }
                ),
                enterTransition = { AppLimitTransitions.enter() },
                exitTransition = { fadeOut(animationSpec = androidx.compose.animation.core.tween(160)) },
                popEnterTransition = { fadeIn(animationSpec = androidx.compose.animation.core.tween(160)) },
                popExitTransition = { AppLimitTransitions.popExit() }
            ) { backStackEntry ->
                val packageName = backStackEntry.arguments?.getString("packageName") ?: return@composable
                val capabilityName = backStackEntry.arguments?.getString("capability") ?: return@composable
                val capability = com.life.mindfulnessapp.ui.theme.parseCapabilityKind(capabilityName)
                AppCapabilityEditScreen(
                    packageName = packageName,
                    capability = capability,
                    onNavigateBack = { navController.popBackStack() },
                    onStoppedMonitoring = {
                        val backToManage = navController.popBackStack(
                            Screen.MonitorManage.route,
                            inclusive = false
                        )
                        if (!backToManage) {
                            navController.popBackStack(Screen.Home.route, inclusive = false)
                        }
                    },
                    onNavigateToIntentPoolManage = {
                        navController.navigate(
                            Screen.IntentPoolManage.createRoute(packageName)
                        )
                    },
                    onNavigateToQuickIntentTags = {
                        navController.navigate(
                            Screen.QuickIntentTags.createRoute(packageName)
                        )
                    },
                    onNavigateToDeepLinkGlance = {
                        navController.navigate(
                            Screen.DeepLinkGlance.createRoute(packageName)
                        )
                    }
                )
            }

            composable(
                route = Screen.QuickIntentTags.route,
                arguments = listOf(
                    navArgument("packageName") { type = NavType.StringType },
                    navArgument("appName") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                ),
                enterTransition = { AppLimitTransitions.enter() },
                exitTransition = { fadeOut(animationSpec = androidx.compose.animation.core.tween(160)) },
                popEnterTransition = { fadeIn(animationSpec = androidx.compose.animation.core.tween(160)) },
                popExitTransition = { AppLimitTransitions.popExit() }
            ) { backStackEntry ->
                val packageName = backStackEntry.arguments?.getString("packageName") ?: return@composable
                val appNameArg = backStackEntry.arguments?.getString("appName").orEmpty()
                QuickIntentTagsScreen(
                    packageName = packageName,
                    appName = appNameArg,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.DeepLinkGlance.route,
                arguments = listOf(
                    navArgument("packageName") { type = NavType.StringType },
                    navArgument("appName") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                ),
                enterTransition = { AppLimitTransitions.enter() },
                exitTransition = { fadeOut(animationSpec = androidx.compose.animation.core.tween(160)) },
                popEnterTransition = { fadeIn(animationSpec = androidx.compose.animation.core.tween(160)) },
                popExitTransition = { AppLimitTransitions.popExit() }
            ) { backStackEntry ->
                val packageName = backStackEntry.arguments?.getString("packageName") ?: return@composable
                val appNameArg = backStackEntry.arguments?.getString("appName").orEmpty()
                DeepLinkGlanceScreen(
                    packageName = packageName,
                    appName = appNameArg,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.AppHistory.route,
                arguments = listOf(
                    androidx.navigation.navArgument("packageName") {
                        type = androidx.navigation.NavType.StringType
                    },
                    androidx.navigation.navArgument("recordId") {
                        type = androidx.navigation.NavType.LongType
                        defaultValue = -1L
                    }
                ),
                enterTransition = { AppLimitTransitions.enter() },
                exitTransition = { fadeOut(animationSpec = androidx.compose.animation.core.tween(160)) },
                popEnterTransition = { fadeIn(animationSpec = androidx.compose.animation.core.tween(160)) },
                popExitTransition = { AppLimitTransitions.popExit() }
            ) { backStackEntry ->
                val packageName = backStackEntry.arguments?.getString("packageName") ?: return@composable
                val highlightRecordId = backStackEntry.arguments?.getLong("recordId") ?: -1L
                AppHistoryScreen(
                    packageName = packageName,
                    highlightRecordId = highlightRecordId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.IntentPoolDetail.route,
                arguments = listOf(
                    navArgument("packageName") { type = NavType.StringType },
                    navArgument("entryId") { type = NavType.StringType }
                ),
                enterTransition = { AppLimitTransitions.enter() },
                exitTransition = { fadeOut(animationSpec = androidx.compose.animation.core.tween(160)) },
                popEnterTransition = { fadeIn(animationSpec = androidx.compose.animation.core.tween(160)) },
                popExitTransition = { AppLimitTransitions.popExit() }
            ) { backStackEntry ->
                val packageName = backStackEntry.arguments?.getString("packageName") ?: return@composable
                val entryId = backStackEntry.arguments?.getString("entryId") ?: return@composable
                IntentPoolDetailScreen(
                    packageName = packageName,
                    entryId = entryId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToHistory = { recordId ->
                        navController.navigate(
                            Screen.AppHistory.createRoute(packageName, recordId)
                        )
                    }
                )
            }

            composable(
                route = Screen.IntentPoolManage.route,
                arguments = listOf(
                    navArgument("packageName") { type = NavType.StringType }
                ),
                enterTransition = { AppLimitTransitions.enter() },
                exitTransition = { fadeOut(animationSpec = androidx.compose.animation.core.tween(160)) },
                popEnterTransition = { fadeIn(animationSpec = androidx.compose.animation.core.tween(160)) },
                popExitTransition = { AppLimitTransitions.popExit() }
            ) { backStackEntry ->
                val packageName = backStackEntry.arguments?.getString("packageName") ?: return@composable
                IntentPoolManageScreen(
                    packageName = packageName,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.ExploreAppDetail.route,
                arguments = listOf(
                    androidx.navigation.navArgument("packageName") {
                        type = androidx.navigation.NavType.StringType
                    }
                ),
                enterTransition = { AppLimitTransitions.enter() },
                exitTransition = { fadeOut(animationSpec = androidx.compose.animation.core.tween(160)) },
                popEnterTransition = { fadeIn(animationSpec = androidx.compose.animation.core.tween(160)) },
                popExitTransition = { AppLimitTransitions.popExit() }
            ) { backStackEntry ->
                val packageName = backStackEntry.arguments?.getString("packageName") ?: return@composable
                ExploreAppDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToWeekRhythm = {
                        navController.navigate(Screen.AppWeekRhythm.createRoute(packageName))
                    }
                )
            }

            composable(
                route = Screen.AppWeekRhythm.route,
                arguments = listOf(
                    androidx.navigation.navArgument("packageName") {
                        type = androidx.navigation.NavType.StringType
                    }
                ),
                enterTransition = { AppLimitTransitions.enter() },
                exitTransition = { fadeOut(animationSpec = androidx.compose.animation.core.tween(160)) },
                popEnterTransition = { fadeIn(animationSpec = androidx.compose.animation.core.tween(160)) },
                popExitTransition = { AppLimitTransitions.popExit() }
            ) { backStackEntry ->
                backStackEntry.arguments?.getString("packageName") ?: return@composable
                AppWeekRhythmScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.AppTrend.route,
                arguments = listOf(
                    androidx.navigation.navArgument("packageName") {
                        type = androidx.navigation.NavType.StringType
                    }
                ),
                enterTransition = { AppLimitTransitions.enter() },
                exitTransition = { fadeOut(animationSpec = androidx.compose.animation.core.tween(160)) },
                popEnterTransition = { fadeIn(animationSpec = androidx.compose.animation.core.tween(160)) },
                popExitTransition = { AppLimitTransitions.popExit() }
            ) { backStackEntry ->
                backStackEntry.arguments?.getString("packageName") ?: return@composable
                AppTrendScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            // ── 「发现」Tab ─────────────────────────────────────────────────
            composable(Screen.Discover.route) {
                DiscoverScreen(
                    onOpenOverview = {
                        navController.navigate(Screen.Overview.createRoute()) {
                            launchSingleTop = true
                        }
                    },
                    onOpenSchedule = {
                        navController.navigate(Screen.Schedule.route)
                    },
                    onOpenAwarenessPractice = {
                        navController.navigate(Screen.AwarenessPractice.route)
                    },
                    onOpenQuotePlay = {
                        navController.navigate(Screen.QuotePlay.route)
                    },
                    onOpenWallpaperStudio = {
                        navController.navigate(Screen.WallpaperStudio.route)
                    }
                )
            }

            composable(
                route = Screen.Overview.route,
                arguments = listOf(
                    navArgument("lens") {
                        type = NavType.StringType
                        defaultValue = "gate"
                    },
                    navArgument("cut") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("dateMs") {
                        type = NavType.LongType
                        defaultValue = 0L
                    }
                )
            ) {
                OverviewScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDayReport = { dateMs ->
                        navController.navigateDayReportBridge(
                            Screen.DayReport.createRoute(dateMs = dateMs)
                        )
                    },
                    onOpenAppDayReport = { pkg ->
                        navController.navigateDayReportBridge(
                            Screen.DayReport.createRoute(packageName = pkg)
                        )
                    },
                    onOpenAppDetail = { pkg ->
                        navController.navigate(Screen.TodayAppDetail.createRoute(pkg))
                    }
                )
            }

            composable(Screen.Schedule.route) {
                ScheduleScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.AwarenessPractice.route,
                enterTransition = { AppLimitTransitions.enter() },
                exitTransition = { fadeOut(animationSpec = androidx.compose.animation.core.tween(160)) },
                popEnterTransition = { fadeIn(animationSpec = androidx.compose.animation.core.tween(160)) },
                popExitTransition = { AppLimitTransitions.popExit() }
            ) {
                com.life.mindfulnessapp.ui.discover.AwarenessPracticeScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // ── 「我」Tab ───────────────────────────────────────────────────
            composable(Screen.Profile.route) {
                ProfileScreen(
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route)
                    },
                    onNavigateToKeepAliveGuide = {
                        navController.navigate(Screen.KeepAliveGuide.route)
                    },
                    onNavigateToProductManual = {
                        navController.navigate(Screen.ProductManual.route)
                    },
                    onNavigateToAbout = {
                        navController.navigate(Screen.About.route)
                    },
                    onNavigateToFeedback = {
                        navController.navigate(Screen.Feedback.route)
                    }
                )
            }

            composable(Screen.AppUpdate.route) {
                AppUpdateScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.DayReport.route,
                arguments = listOf(
                    navArgument("packageName") {
                        type = NavType.StringType
                        defaultValue = ""
                        nullable = true
                    },
                    navArgument("dateMs") {
                        type = NavType.LongType
                        defaultValue = 0L
                    }
                )
            ) {
                DayReportScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onOpenFullDay = {
                        navController.navigateDayReportBridge(Screen.DayReport.createRoute())
                    },
                    onOpenOverview = { dateMs ->
                        navController.navigateOverviewBridge(
                            Screen.Overview.createGateRoute(cut = "day", dateMs = dateMs)
                        )
                    }
                )
            }

            composable(Screen.WeekLookback.route) {
                com.life.mindfulnessapp.ui.lookback.WeekLookbackScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToHome = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Screen.UsageLog.route) {
                UsageLogScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.RecordHistory.route) {
                RecordHistoryScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDayReport = {
                        navController.navigate(Screen.DayReport.createRoute())
                    }
                )
            }

            // ── 二级设置页（从「我」进入）──────────────────────────────────
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToTheme = {
                        navController.navigate(Screen.Theme.route)
                    },
                    onNavigateToPlayground = {
                        navController.navigate(Screen.Playground.route)
                    },
                    onNavigateToSearchDeepLinkTest = {
                        navController.navigate(Screen.SearchDeepLinkTest.route)
                    },
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(Screen.Playground.route) {
                PlaygroundScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToWallpaperStudio = {
                        navController.navigate(Screen.WallpaperStudio.route)
                    },
                    onNavigateToQuotePlay = {
                        navController.navigate(Screen.QuotePlay.route)
                    }
                )
            }

            composable(Screen.QuotePlay.route) {
                QuotePlayScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToBrowse = {
                        navController.navigate(Screen.QuoteBrowse.route)
                    },
                    onNavigateToAuthors = {
                        navController.navigate(Screen.QuoteLibrary.route)
                    }
                )
            }

            composable(Screen.QuoteBrowse.route) {
                QuoteBrowseScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.WallpaperStudio.route) {
                WallpaperStudioScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            if (BuildConfig.DEBUG) {
                composable(Screen.SearchDeepLinkTest.route) {
                    SearchDeepLinkTestScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }

            composable(Screen.KeepAliveGuide.route) {
                KeepAliveGuideScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToVivoOriginOs = {
                        navController.navigate(Screen.VivoOriginOsKeepAliveGuide.route)
                    },
                    onNavigateToWallpaperStudio = {
                        navController.navigate(Screen.WallpaperStudio.route)
                    }
                )
            }

            composable(Screen.VivoOriginOsKeepAliveGuide.route) {
                VivoOriginOsKeepAliveGuideScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Theme.route) {
                ThemeScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.QuoteLibrary.route) {
                AuthorCatalogScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.QuotePushSchedule.route) {
                QuotePushScheduleScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.QuoteMoment.route,
                arguments = listOf(
                    navArgument("id") {
                        type = androidx.navigation.NavType.IntType
                        defaultValue = 0
                    },
                    navArgument("content") {
                        type = androidx.navigation.NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("author") {
                        type = androidx.navigation.NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("authorId") {
                        type = androidx.navigation.NavType.IntType
                        defaultValue = 0
                    }
                )
            ) { entry ->
                QuoteMomentScreen(
                    quoteId = entry.arguments?.getInt("id") ?: 0,
                    quoteContent = entry.arguments?.getString("content").orEmpty(),
                    quoteAuthor = entry.arguments?.getString("author").orEmpty(),
                    quoteAuthorId = entry.arguments?.getInt("authorId") ?: 0,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // 路由保留；入口由 POSITIVE_DESTINATIONS_ENABLED 屏蔽
            composable(Screen.PositiveDestinations.route) {
                PositiveDestinationsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.About.route) {
                AboutScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToAppUpdate = {
                        navController.navigate(Screen.AppUpdate.route)
                    },
                    onNavigateToProductManual = {
                        navController.navigate(Screen.ProductManual.route)
                    }
                )
            }

            composable(Screen.ProductManual.route) {
                ProductManualScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Feedback.route) {
                FeedbackScreen(
                    openReplyId = openFeedbackReplyId,
                    onOpenReplyHandled = { openFeedbackReplyId = null },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Vip.route) {
                VipScreen(
                    isDarkTheme = isDarkTheme,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }

    AppUpdateHost(
        viewModel = appUpdateViewModel,
        isDarkTheme = isDarkTheme
    )
    }
}


