package com.life.mindfulnessapp

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.service.MonitorForegroundService
import com.life.mindfulnessapp.ui.applist.AppListScreen
import com.life.mindfulnessapp.ui.explore.ExploreScreen
import com.life.mindfulnessapp.ui.home.HomeScreen
import com.life.mindfulnessapp.ui.navigation.Screen
import com.life.mindfulnessapp.ui.onboarding.OnboardingScreen
import com.life.mindfulnessapp.ui.settings.SettingsScreen
import com.life.mindfulnessapp.ui.stats.AppDetailStatsScreen
import com.life.mindfulnessapp.ui.theme.*
import androidx.compose.runtime.collectAsState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

val Context.dataStore by preferencesDataStore(name = "settings")
val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appPreferences: AppPreferences

    // Android 13+ 通知运行时权限请求
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 用户选择后无需额外处理 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Android 13+ 请求通知运行时权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val isOnboardingCompleted = runBlocking {
            dataStore.data.map { it[ONBOARDING_COMPLETED] ?: false }.first()
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
            val allThemesUnlocked by appPreferences.allThemesUnlocked.collectAsState()

            MindfulnessAppTheme(darkTheme = isDarkTheme) {
                MindfulnessApp(
                    initialOnboardingDone = isOnboardingCompleted,
                    isDarkTheme = isDarkTheme,
                    interceptThemeId = interceptThemeId,
                    allThemesUnlocked = allThemesUnlocked,
                    onThemeSelected = { themeId ->
                        appPreferences.setInterceptThemeId(themeId)
                    },
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
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector
)

@Composable
fun MindfulnessApp(
    initialOnboardingDone: Boolean,
    isDarkTheme: Boolean = true,
    interceptThemeId: String = "default",
    allThemesUnlocked: Boolean = false,
    onThemeSelected: (String) -> Unit = {},
    onOnboardingComplete: () -> Unit
) {
    val navController = rememberNavController()

    // 根据主题动态调整底部导航栏颜色
    val dockBgColor     = if (isDarkTheme) NightDockBg    else DayDockBg
    val accentGreen     = if (isDarkTheme) LogoGreen       else Color(0xFF1E8B4E)
    val unselectedColor = if (isDarkTheme) Color(0xFF484F58) else Color(0xFFADB5AD)
    val indicatorColor  = accentGreen.copy(alpha = 0.14f)
    val bgColor         = if (isDarkTheme) NightBg         else DayBg

    // 新的 3-Tab 结构：今日 + 主题 + 设置
    val bottomNavItems = remember {
        listOf(
            BottomNavItem(Screen.Home,    "今日",   Icons.Default.Home),
            BottomNavItem(Screen.Explore, "主题",   Icons.Default.Style),
            BottomNavItem(Screen.Settings,"设置",   Icons.Default.Settings)
        )
    }

    val startDestination = if (initialOnboardingDone) Screen.Home.route else Screen.Onboarding.route

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = bottomNavItems.any {
        currentDestination?.hierarchy?.any { dest -> dest.route == it.screen.route } == true
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = bgColor,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = dockBgColor,
                    tonalElevation = 0.dp
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy
                            ?.any { it.route == item.screen.route } == true

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    // 使用 Screen.Home.route 作为 popUpTo 目标，而非 findStartDestination()。
                                    // 首次进入时 startDestination 是 Onboarding，Onboarding 完成后被清出 back stack，
                                    // 若仍用 findStartDestination()（即 Onboarding id），popUpTo 找不到目标节点，
                                    // 导致从 Settings 点 Home tab 后底部导航无反应。
                                    popUpTo(Screen.Home.route) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(item.icon, contentDescription = item.label)
                            },
                            label = {
                                Text(
                                    item.label,
                                    fontSize = 10.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = accentGreen,
                                selectedTextColor = accentGreen,
                                indicatorColor = indicatorColor,
                                unselectedIconColor = unselectedColor,
                                unselectedTextColor = unselectedColor
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
                    onNavigateToAppList = {
                        navController.navigate(Screen.AppList.route)
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route)
                    },
                    onNavigateToAppDetail = { packageName ->
                        navController.navigate(Screen.AppDetailStats.createRoute(packageName))
                    }
                )
            }

            composable(Screen.AppList.route) {
                AppListScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.AppDetailStats.route) { backStackEntry ->
                val packageName = backStackEntry.arguments?.getString("packageName") ?: return@composable
                AppDetailStatsScreen(
                    packageName = packageName,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen()
            }

            composable(
                route = Screen.Explore.route,
                enterTransition = { fadeIn() + slideInHorizontally { it / 4 } },
                exitTransition  = { fadeOut() + slideOutHorizontally { it / 4 } }
            ) {
                ExploreScreen(
                    isDarkTheme = isDarkTheme,
                    currentThemeId = interceptThemeId,
                    allThemesUnlocked = allThemesUnlocked,
                    onThemeSelect = onThemeSelected
                )
            }
        }
    }
}
