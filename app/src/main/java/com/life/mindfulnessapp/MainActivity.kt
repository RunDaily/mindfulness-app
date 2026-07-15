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
import com.life.mindfulnessapp.ui.applist.AppListScreen
import com.life.mindfulnessapp.ui.home.HomeViewModel
import androidx.activity.viewModels
import com.life.mindfulnessapp.ui.home.HomeScreen
import com.life.mindfulnessapp.ui.navigation.BottomTab
import com.life.mindfulnessapp.ui.navigation.Screen
import com.life.mindfulnessapp.ui.onboarding.OnboardingScreen
import com.life.mindfulnessapp.ui.profile.ProfileScreen
import com.life.mindfulnessapp.ui.settings.SettingsScreen
import com.life.mindfulnessapp.ui.settings.ThemeScreen
import com.life.mindfulnessapp.ui.stats.AppDetailStatsScreen
import com.life.mindfulnessapp.ui.stats.DailyReportScreen
import com.life.mindfulnessapp.ui.stats.OverviewScreen
import com.life.mindfulnessapp.ui.stats.StatsScreen
import com.life.mindfulnessapp.ui.theme.*
import com.life.mindfulnessapp.ui.favorite.FavoriteQuotesScreen
import com.life.mindfulnessapp.ui.invite.InviteScreen
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

    /**
     * 用户在 Anchor App 内手动结束计时时，触发 Snackbar 提示的标志。
     * true = 需要显示 Snackbar，显示后由 UI 重置为 false。
     */
    var showSessionEndedSnackbar by mutableStateOf(false)
        private set

    /** 接收来自 MonitorForegroundService 的「会话在 App 内结束」LocalBroadcast */
    private val sessionEndedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == MonitorForegroundService.ACTION_SESSION_ENDED_IN_APP) {
                showSessionEndedSnackbar = true
            }
        }
    }

    /** 供 UI 层在 Snackbar 展示完毕后调用，重置标志 */
    fun onSessionEndedSnackbarShown() {
        showSessionEndedSnackbar = false
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
                }
            }
            MonitorForegroundService.ACTION_OPEN_APP_LIMIT_EDIT -> {
                val pkg = intent.getStringExtra(MonitorForegroundService.EXTRA_APP_PACKAGE_NAME)
                if (!pkg.isNullOrEmpty()) {
                    pendingAppLimitEditPackage = pkg
                }
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
            // 实时监听拦截主题 ID
            val interceptThemeId by appPreferences.interceptThemeId.collectAsState()

            MindfulnessAppTheme(darkTheme = isDarkTheme) {
                MindfulnessApp(
                    initialOnboardingDone = isOnboardingCompleted,
                    isPrivacyAccepted = isPrivacyAccepted,
                    isDarkTheme = isDarkTheme,
                    interceptThemeId = interceptThemeId,
                    pendingAppLimitEditPackage = pendingAppLimitEditPackage,
                    onAppLimitEditHandled = { pendingAppLimitEditPackage = null },
                    showSessionEndedSnackbar = showSessionEndedSnackbar,
                    onSessionEndedSnackbarShown = { onSessionEndedSnackbarShown() },
                    onThemeSelected = { themeId ->
                        appPreferences.setInterceptThemeId(themeId)
                    },
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
    interceptThemeId: String = "default",
    pendingAppLimitEditPackage: String? = null,
    onAppLimitEditHandled: () -> Unit = {},
    showSessionEndedSnackbar: Boolean = false,
    onSessionEndedSnackbarShown: () -> Unit = {},
    onThemeSelected: (String) -> Unit = {},
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
                message = "计时已结束，记录已保存 ✓",
                duration = SnackbarDuration.Short
            )
            onSessionEndedSnackbarShown()
        }
    }

    // 当从浮窗「重新设定今日目标」跳转过来时，直接导航到该 App 的详情页并自动弹出编辑对话框
    LaunchedEffect(pendingAppLimitEditPackage) {
        val pkg = pendingAppLimitEditPackage ?: return@LaunchedEffect
        navController.navigate(Screen.AppDetailStats.createRoute(pkg, autoEdit = true)) {
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

    // 只在三个 Tab 根页面显示底部导航栏；Onboarding 及二级页面不显示
    val tabRoutes = setOf(Screen.Home.route, Screen.Stats.route, Screen.Profile.route)
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

            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToAppDetail = { packageName ->
                        navController.navigate(Screen.AppDetailStats.createRoute(packageName))
                    },
                    onNavigateToAppList = {
                        navController.navigate(Screen.AppList.route)
                    }
                )
            }

            composable(Screen.AppList.route) {
                AppListScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToVip = { navController.navigate(Screen.Vip.route) }
                )
            }

            composable(Screen.Stats.route) {
                StatsScreen(
                    onNavigateToAppDetail = { packageName ->
                        navController.navigate(Screen.AppDetailStats.createRoute(packageName))
                    },
                    onNavigateToAppList = {
                        navController.navigate(Screen.AppList.route)
                    },
                    onNavigateToDailyReport = {
                        navController.navigate(Screen.DailyReport.createRoute(0))
                    },
                    onNavigateToOverview = {
                        navController.navigate(Screen.Overview.route)
                    }
                )
            }

            composable(
                route = Screen.DailyReport.route,
                arguments = listOf(
                    androidx.navigation.navArgument("dayOffset") {
                        type = androidx.navigation.NavType.IntType
                        defaultValue = 0
                    }
                )
            ) { backStackEntry ->
                val dayOffset = backStackEntry.arguments?.getInt("dayOffset") ?: 0
                DailyReportScreen(
                    initialDayOffset = dayOffset,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Overview.route) {
                OverviewScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDayReport = { offset ->
                        navController.navigate(Screen.DailyReport.createRoute(offset))
                    }
                )
            }

            composable(
                route = Screen.AppDetailStats.route,
                arguments = listOf(
                    androidx.navigation.navArgument("packageName") {
                        type = androidx.navigation.NavType.StringType
                    },
                    androidx.navigation.navArgument("autoEdit") {
                        type = androidx.navigation.NavType.BoolType
                        defaultValue = false
                    }
                )
            ) { backStackEntry ->
                val packageName = backStackEntry.arguments?.getString("packageName") ?: return@composable
                val autoEdit = backStackEntry.arguments?.getBoolean("autoEdit") ?: false
                AppDetailStatsScreen(
                    packageName = packageName,
                    autoShowEditDialog = autoEdit,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // ── 第3个 Tab：我的页面 ─────────────────────────────────────────
            composable(Screen.Profile.route) {
                ProfileScreen(
                    interceptThemeId = interceptThemeId,
                    onNavigateToVip = {
                        navController.navigate(Screen.Vip.route)
                    },
                    onNavigateToInvite = {
                        navController.navigate(Screen.Invite.route)
                    },
                    onNavigateToAppList = {
                        navController.navigate(Screen.AppList.route)
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route)
                    },
                    onNavigateToFavorites = {
                        navController.navigate(Screen.FavoriteQuotes.route)
                    }
                )
            }

            // ── 二级设置页（从「我」进入）──────────────────────────────────
            composable(Screen.Settings.route) {
                SettingsScreen(
                    interceptThemeId = interceptThemeId,
                    onThemeSelected = onThemeSelected,
                    onNavigateToTheme = {
                        navController.navigate(Screen.Theme.route)
                    },
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(Screen.Theme.route) {
                ThemeScreen(
                    interceptThemeId = interceptThemeId,
                    onThemeSelected = onThemeSelected,
                    onNavigateToVip = {
                        navController.navigate(Screen.Vip.route)
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Vip.route) {
                VipScreen(
                    isDarkTheme = isDarkTheme,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Invite.route) {
                InviteScreen(
                    isDarkTheme = isDarkTheme,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // ── 收藏格言列表 ───────────────────────────────────────────────
            composable(Screen.FavoriteQuotes.route) {
                FavoriteQuotesScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}


