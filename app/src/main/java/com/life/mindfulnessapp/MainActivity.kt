package com.life.mindfulnessapp

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.service.MonitorForegroundService
import com.life.mindfulnessapp.ui.applist.AddAppLimitScreen
import com.life.mindfulnessapp.ui.applist.AppHistoryScreen
import com.life.mindfulnessapp.ui.applist.AppLimitEditScreen
import com.life.mindfulnessapp.ui.applist.AppListScreen
import com.life.mindfulnessapp.ui.applist.MonitorManageScreen
import com.life.mindfulnessapp.ui.home.HomeViewModel
import androidx.activity.viewModels
import com.life.mindfulnessapp.ui.home.HomeScreen
import com.life.mindfulnessapp.ui.navigation.AppLimitTransitions
import com.life.mindfulnessapp.ui.navigation.BottomTab
import com.life.mindfulnessapp.ui.navigation.Screen
import com.life.mindfulnessapp.ui.navigation.isNavigatingToAppLimit
import com.life.mindfulnessapp.ui.navigation.isPoppingFromAppLimit
import com.life.mindfulnessapp.ui.onboarding.OnboardingScreen
import com.life.mindfulnessapp.ui.profile.ProfileScreen
import com.life.mindfulnessapp.ui.settings.PositiveDestinationsScreen
import com.life.mindfulnessapp.ui.settings.SettingsScreen
import com.life.mindfulnessapp.ui.settings.ThemeScreen
import com.life.mindfulnessapp.ui.stats.WeekAwarenessScreen
import com.life.mindfulnessapp.ui.theme.*
import com.life.mindfulnessapp.ui.vip.VipScreen
import androidx.compose.runtime.collectAsState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

val Context.dataStore by preferencesDataStore(name = "settings")
val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
val PRIVACY_ACCEPTED = booleanPreferencesKey("privacy_policy_accepted")

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appPreferences: AppPreferences

    // 用 by viewModels() 确保与 HomeScreen 里的 hiltViewModel() 是同一个实例
    private val homeViewModel: HomeViewModel by viewModels()

    // Android 13+ 通知运行时权限请求
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 用户选择后无需额外处理 */ }

    /** 来自 Service 的「打开 App 限制编辑」请求，存储待跳转的 packageName（State，改变时触发重组） */
    private var pendingAppLimitEditPackage by mutableStateOf<String?>(null)

    /** 来自拦截页「打开心锚」：落到今日 Tab */
    private var pendingNavigateHome by mutableStateOf(false)

    /** 来自离开轻条：打开「想去的地方」配置 */
    private var pendingNavigatePositiveDestinations by mutableStateOf(false)

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
                    homeViewModel.requestOpenNote(recordId)
                    pendingNavigateHomeHighlightId = recordId
                }
            }
            MonitorForegroundService.ACTION_OPEN_APP_LIMIT_EDIT -> {
                val pkg = intent.getStringExtra(MonitorForegroundService.EXTRA_APP_PACKAGE_NAME)
                if (!pkg.isNullOrEmpty()) {
                    pendingAppLimitEditPackage = pkg
                }
            }
            MonitorForegroundService.ACTION_OPEN_HOME -> {
                pendingNavigateHome = true
            }
            MonitorForegroundService.ACTION_OPEN_POSITIVE_DESTINATIONS -> {
                pendingNavigatePositiveDestinations = true
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 处理冷启动时携带的 Intent
        handleIncomingIntent(intent)

        // Android 13+ 请求通知运行时权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

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

        // 如果已完成引导，直接启动监控服务
        if (isOnboardingCompleted) {
            MonitorForegroundService.start(this)
        }

        setContent {
            // 实时监听主题偏好，支持设置页切换后立即生效
            val isDarkTheme by appPreferences.isDarkTheme.collectAsState()

            MindfulnessAppTheme(darkTheme = isDarkTheme) {
                MindfulnessApp(
                    initialOnboardingDone = isOnboardingCompleted,
                    isPrivacyAccepted = isPrivacyAccepted,
                    isDarkTheme = isDarkTheme,
                    pendingAppLimitEditPackage = pendingAppLimitEditPackage,
                    onAppLimitEditHandled = { pendingAppLimitEditPackage = null },
                    pendingNavigateHome = pendingNavigateHome,
                    onNavigateHomeHandled = { pendingNavigateHome = false },
                    pendingNavigatePositiveDestinations = pendingNavigatePositiveDestinations,
                    onNavigatePositiveDestinationsHandled = {
                        pendingNavigatePositiveDestinations = false
                    },
                    pendingNavigateHomeHighlightId = pendingNavigateHomeHighlightId,
                    onNavigateHomeForHighlightHandled = { onNavigateHomeForHighlightHandled() },
                    showSessionEndedSnackbar = showSessionEndedSnackbar,
                    sessionEndedSnackbarMessage = sessionEndedSnackbarMessage,
                    onSessionEndedSnackbarShown = { onSessionEndedSnackbarShown() },
                    onPrivacyAccept = {
                        runBlocking { dataStore.edit { it[PRIVACY_ACCEPTED] = true } }
                    },
                    onPrivacyDecline = { finish() },
                    onOnboardingComplete = {
                        runBlocking {
                            dataStore.edit { it[ONBOARDING_COMPLETED] = true }
                        }
                        MonitorForegroundService.start(this)
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(sessionEndedReceiver)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MindfulnessApp(
    initialOnboardingDone: Boolean,
    isPrivacyAccepted: Boolean = false,
    isDarkTheme: Boolean = true,
    pendingAppLimitEditPackage: String? = null,
    onAppLimitEditHandled: () -> Unit = {},
    pendingNavigateHome: Boolean = false,
    onNavigateHomeHandled: () -> Unit = {},
    pendingNavigatePositiveDestinations: Boolean = false,
    onNavigatePositiveDestinationsHandled: () -> Unit = {},
    pendingNavigateHomeHighlightId: Long? = null,
    onNavigateHomeForHighlightHandled: () -> Unit = {},
    showSessionEndedSnackbar: Boolean = false,
    sessionEndedSnackbarMessage: String = "计时已结束 ✓",
    onSessionEndedSnackbarShown: () -> Unit = {},
    onPrivacyAccept: () -> Unit = {},
    onPrivacyDecline: () -> Unit = {},
    onOnboardingComplete: () -> Unit
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

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

    // 手动结束后强制落到「今日」Tab（弹出其上的二级页 / 切回 Home）
    LaunchedEffect(pendingNavigateHomeHighlightId) {
        if (pendingNavigateHomeHighlightId == null) return@LaunchedEffect
        navController.navigate(Screen.Home.route) {
            popUpTo(Screen.Home.route) { inclusive = false }
            launchSingleTop = true
        }
        onNavigateHomeForHighlightHandled()
    }

    // 拦截页「打开心锚」：落到今日 Tab
    LaunchedEffect(pendingNavigateHome) {
        if (!pendingNavigateHome) return@LaunchedEffect
        navController.navigate(Screen.Home.route) {
            popUpTo(Screen.Home.route) { inclusive = false }
            launchSingleTop = true
        }
        onNavigateHomeHandled()
    }

    // 离开轻条「下次可设一个想去的地方」
    LaunchedEffect(pendingNavigatePositiveDestinations) {
        if (!pendingNavigatePositiveDestinations) return@LaunchedEffect
        navController.navigate(Screen.PositiveDestinations.route) {
            launchSingleTop = true
        }
        onNavigatePositiveDestinationsHandled()
    }

    // 当从浮窗「重新设定今日目标」跳转过来时，直接进入该 App 的监控配置页
    LaunchedEffect(pendingAppLimitEditPackage) {
        val pkg = pendingAppLimitEditPackage ?: return@LaunchedEffect
        navController.navigate(Screen.AppLimitEdit.createRoute(pkg)) {
            launchSingleTop = true
        }
        onAppLimitEditHandled()
    }

    val accentGreen     = if (isDarkTheme) LogoGreen else Color(0xFF1E8B4E)
    val bgColor         = if (isDarkTheme) NightBg   else DayBg

    val startDestination = if (initialOnboardingDone) Screen.Home.route else Screen.Onboarding.route

    // 计算 Onboarding 初始页：
    //  - 隐私未接受 → 第 0 页（隐私政策）
    //  - 隐私已接受但未完成 onboarding → 第 1 页（功能介绍）
    val onboardingInitialPage = if (!isPrivacyAccepted) 0 else 1

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    // 只在 Tab 根页面显示底部导航栏；Onboarding 及二级页面不显示
    val tabRoutes = setOf(Screen.Home.route, Screen.Profile.route)
    val showBottomBar = currentRoute in tabRoutes

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
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label
                                )
                            },
                            label = { Text(tab.label, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = accentGreen,
                                selectedTextColor = accentGreen,
                                indicatorColor = accentGreen.copy(alpha = 0.14f),
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
                    onPrivacyAccept = onPrivacyAccept,
                    onPrivacyDecline = onPrivacyDecline,
                    onComplete = {
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
                    if (isNavigatingToAppLimit()) AppLimitTransitions.holdExit() else null
                },
                popEnterTransition = {
                    if (isPoppingFromAppLimit()) AppLimitTransitions.holdPopEnter() else null
                }
            ) {
                HomeScreen(
                    onNavigateToAppDetail = { packageName ->
                        navController.navigate(Screen.AppLimitEdit.createRoute(packageName))
                    },
                    onNavigateToManage = {
                        navController.navigate(Screen.MonitorManage.route)
                    },
                    onNavigateToAdd = {
                        navController.navigate(Screen.AppList.route)
                    },
                    onNavigateToWeekAwareness = {
                        navController.navigate(Screen.WeekAwareness.route)
                    }
                )
            }

            composable(
                route = Screen.MonitorManage.route,
                exitTransition = {
                    if (isNavigatingToAppLimit()) AppLimitTransitions.holdExit() else null
                },
                popEnterTransition = {
                    if (isPoppingFromAppLimit()) AppLimitTransitions.holdPopEnter() else null
                }
            ) {
                MonitorManageScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToAdd = {
                        navController.navigate(Screen.AppList.route)
                    },
                    onNavigateToEdit = { packageName ->
                        navController.navigate(Screen.AppLimitEdit.createRoute(packageName))
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
                AppListScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToAddLimit = { packageName ->
                        navController.navigate(Screen.AppLimitAdd.createRoute(packageName))
                    },
                    onNavigateToVip = { navController.navigate(Screen.Vip.route) }
                )
            }

            composable(
                route = Screen.AppLimitAdd.route,
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
                AddAppLimitScreen(
                    packageName = packageName,
                    onNavigateBack = { navController.popBackStack() },
                    onAddSuccess = {
                        // 按来源落点：管理页发起 → 回管理；首页「+」发起 → 回首页看新坑
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
                exitTransition = { fadeOut(animationSpec = androidx.compose.animation.core.tween(160)) },
                popEnterTransition = { fadeIn(animationSpec = androidx.compose.animation.core.tween(160)) },
                popExitTransition = { AppLimitTransitions.popExit() }
            ) { backStackEntry ->
                val packageName = backStackEntry.arguments?.getString("packageName") ?: return@composable
                AppLimitEditScreen(
                    packageName = packageName,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToHistory = {
                        navController.navigate(Screen.AppHistory.createRoute(packageName))
                    }
                )
            }

            composable(
                route = Screen.AppHistory.route,
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
                AppHistoryScreen(
                    packageName = packageName,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.WeekAwareness.route) {
                WeekAwarenessScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToAppReview = { packageName ->
                        navController.navigate(Screen.AppHistory.createRoute(packageName))
                    },
                    onNavigateToAddApps = {
                        navController.navigate(Screen.AppList.route)
                    }
                )
            }

            // ── 「我」Tab ───────────────────────────────────────────────────
            composable(Screen.Profile.route) {
                ProfileScreen(
                    onNavigateToVip = {
                        navController.navigate(Screen.Vip.route)
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route)
                    },
                    onNavigateToWeekAwareness = {
                        navController.navigate(Screen.WeekAwareness.route)
                    }
                )
            }

            // ── 二级设置页（从「我」进入）──────────────────────────────────
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToTheme = {
                        navController.navigate(Screen.Theme.route)
                    },
                    onNavigateToPositiveDestinations = {
                        navController.navigate(Screen.PositiveDestinations.route)
                    },
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(Screen.Theme.route) {
                ThemeScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.PositiveDestinations.route) {
                PositiveDestinationsScreen(
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
}


