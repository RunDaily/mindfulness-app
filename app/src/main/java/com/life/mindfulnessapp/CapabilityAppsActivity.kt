package com.life.mindfulnessapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.ui.applist.AppCapabilityEditScreen
import com.life.mindfulnessapp.ui.applist.AppHistoryScreen
import com.life.mindfulnessapp.ui.applist.AppLimitEditScreen
import com.life.mindfulnessapp.ui.features.CapabilityAppsScreen
import com.life.mindfulnessapp.ui.navigation.AppLimitTransitions
import com.life.mindfulnessapp.ui.navigation.Screen
import com.life.mindfulnessapp.ui.navigation.isNavigatingToAppLimit
import com.life.mindfulnessapp.ui.navigation.isNavigatingToCapabilityBind
import com.life.mindfulnessapp.ui.navigation.isPoppingFromAppLimit
import com.life.mindfulnessapp.ui.navigation.isPoppingFromCapabilityBind
import com.life.mindfulnessapp.ui.theme.CapabilityKind
import com.life.mindfulnessapp.ui.theme.parseCapabilityKind
import com.life.mindfulnessapp.ui.theme.MindfulnessAppTheme
import com.life.mindfulnessapp.ui.theme.chrome
import com.life.mindfulnessapp.ui.theme.rememberResolvedThemePack
import com.life.mindfulnessapp.ui.theme.rememberResolvedIsDark
import com.life.mindfulnessapp.ui.vip.VipScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 能力详情重量级页面：独立 Activity，系统进场动画整页压住主 Tab。
 * 内部再挂批量绑定 / 配置等二级流，返回时 finish 回「能力」Tab。
 */
@AndroidEntryPoint
class CapabilityAppsActivity : ComponentActivity() {

    @Inject
    lateinit var appPreferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val capabilityName = intent.getStringExtra(EXTRA_CAPABILITY).orEmpty()
        val capability = parseCapabilityKind(capabilityName)

        val bootPack = appPreferences.resolvedThemePack()
        window.setBackgroundDrawableResource(bootPack.chrome().windowBgRes)
        val darkTheme = bootPack.isDark
        val barStyle = if (darkTheme) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        }
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)

        setContent {
            val themePackPref by appPreferences.themePack.collectAsState()
            val themeFollow by appPreferences.themeFollowSystem.collectAsState()
            val themePack = rememberResolvedThemePack(themePackPref, themeFollow)
            val isDarkTheme = themePack.isDark

            SideEffect {
                window.setBackgroundDrawableResource(themePack.chrome().windowBgRes)
            }

            MindfulnessAppTheme(themePack = themePack) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CapabilityAppsNavHost(
                        capability = capability,
                        isDarkTheme = isDarkTheme,
                        onFinish = { finish() }
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_CAPABILITY = "capability"

        fun createIntent(context: Context, capability: CapabilityKind): Intent =
            Intent(context, CapabilityAppsActivity::class.java).putExtra(
                EXTRA_CAPABILITY,
                capability.name
            )
    }
}

@Composable
private fun CapabilityAppsNavHost(
    capability: CapabilityKind,
    isDarkTheme: Boolean,
    onFinish: () -> Unit
) {
    val navController = rememberNavController()
    val rootRoute = Screen.CapabilityApps.createRoute(capability)

    NavHost(
        navController = navController,
        startDestination = rootRoute
    ) {
        composable(
            route = Screen.CapabilityApps.route,
            arguments = listOf(
                navArgument("capability") { type = NavType.StringType }
            ),
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
            CapabilityAppsScreen(
                capability = capability,
                onNavigateBack = onFinish,
                onNavigateToAdd = {
                    context.startActivity(
                        CapabilityBatchPickActivity.createIntent(context, capability)
                    )
                },
                onNavigateToEdit = { packageName ->
                    navController.navigate(Screen.AppLimitEdit.createRoute(packageName))
                },
                onNavigateToVip = { navController.navigate(Screen.Vip.route) }
            )
        }

        composable(
            route = Screen.AppLimitEdit.route,
            arguments = listOf(
                navArgument("packageName") { type = NavType.StringType }
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
            val packageName = backStackEntry.arguments?.getString("packageName")
                ?: return@composable
            AppLimitEditScreen(
                packageName = packageName,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHistory = {
                    navController.navigate(Screen.AppHistory.createRoute(packageName))
                },
                onNavigateToCapability = { kind ->
                    navController.navigate(
                        Screen.AppCapabilityEdit.createRoute(packageName, kind.name)
                    )
                }
            )
        }

        composable(
            route = Screen.AppCapabilityEdit.route,
            arguments = listOf(
                navArgument("packageName") { type = NavType.StringType },
                navArgument("capability") { type = NavType.StringType }
            ),
            enterTransition = { AppLimitTransitions.enter() },
            exitTransition = { fadeOut(animationSpec = androidx.compose.animation.core.tween(160)) },
            popEnterTransition = { fadeIn(animationSpec = androidx.compose.animation.core.tween(160)) },
            popExitTransition = { AppLimitTransitions.popExit() }
        ) { backStackEntry ->
            val packageName = backStackEntry.arguments?.getString("packageName")
                ?: return@composable
            val capabilityName = backStackEntry.arguments?.getString("capability")
                ?: return@composable
            val kind = parseCapabilityKind(capabilityName)
            AppCapabilityEditScreen(
                packageName = packageName,
                capability = kind,
                onNavigateBack = { navController.popBackStack() },
                onStoppedMonitoring = {
                    navController.popBackStack(rootRoute, inclusive = false)
                }
            )
        }

        composable(
            route = Screen.AppHistory.route,
            arguments = listOf(
                navArgument("packageName") { type = NavType.StringType },
                navArgument("recordId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            ),
            enterTransition = { AppLimitTransitions.enter() },
            exitTransition = { fadeOut(animationSpec = androidx.compose.animation.core.tween(160)) },
            popEnterTransition = { fadeIn(animationSpec = androidx.compose.animation.core.tween(160)) },
            popExitTransition = { AppLimitTransitions.popExit() }
        ) { backStackEntry ->
            val packageName = backStackEntry.arguments?.getString("packageName")
                ?: return@composable
            val highlightRecordId = backStackEntry.arguments?.getLong("recordId") ?: -1L
            AppHistoryScreen(
                packageName = packageName,
                highlightRecordId = highlightRecordId,
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
