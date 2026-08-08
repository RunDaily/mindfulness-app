package com.life.mindfulnessapp.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavBackStackEntry

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    /** 已监控应用管理（短列表） */
    object MonitorManage : Screen("monitor_manage")
    /** 添加监控应用（全机挑选器） */
    object AppList : Screen("app_list")
    /** 新增监控 · 配置全屏页 */
    object AppLimitAdd : Screen("app_limit_add/{packageName}") {
        fun createRoute(packageName: String) = "app_limit_add/$packageName"
    }
    /** 编辑已有监控配置 / 限额 */
    object AppLimitEdit : Screen("app_limit_edit/{packageName}") {
        fun createRoute(packageName: String) = "app_limit_edit/$packageName"
    }
    /** 单 App 历史记录（按日倒序） */
    object AppHistory : Screen("app_history/{packageName}") {
        fun createRoute(packageName: String) = "app_history/$packageName"
    }
    /** 本周觉察：跨 App 周级汇总（从今日轻入口 /「我」进入） */
    object WeekAwareness : Screen("week_awareness")
    /** 「我」Tab */
    object Profile : Screen("profile")
    /** 二级设置页（从「我」进入） */
    object Settings : Screen("settings")
    object Theme : Screen("theme")
    /** 想去的地方：离开后的正向 App 归属 */
    object PositiveDestinations : Screen("positive_destinations")
    object Vip : Screen("vip")
}

/** 底部导航：今日 + 我（无总览 Tab；周级觉察另作轻入口） */
sealed class BottomTab(
    val screen: Screen,
    val label: String,
    val icon: ImageVector
) {
    object Home : BottomTab(Screen.Home, "今日", Icons.Default.Home)
    object Profile : BottomTab(Screen.Profile, "我", Icons.Default.Person)

    companion object {
        val all = listOf(Home, Profile)
    }
}

/** 监控配置页：自底部升起 / 向下收起，接近全屏 Sheet 的沉稳进场 */
object AppLimitTransitions {
    private const val EnterMs = 480
    private const val ExitMs = 380

    fun enter(): EnterTransition =
        slideInVertically(
            animationSpec = tween(EnterMs, easing = FastOutSlowInEasing),
            initialOffsetY = { it }
        ) + fadeIn(animationSpec = tween(300))

    fun popExit(): ExitTransition =
        slideOutVertically(
            animationSpec = tween(ExitMs, easing = FastOutSlowInEasing),
            targetOffsetY = { it }
        ) + fadeOut(animationSpec = tween(260))

    /** 下层页保持不动，避免和上滑叠成「双滑」 */
    fun holdExit(): ExitTransition = ExitTransition.None

    fun holdPopEnter(): EnterTransition = EnterTransition.None
}

fun AnimatedContentTransitionScope<NavBackStackEntry>.isNavigatingToAppLimit(): Boolean {
    val route = targetState.destination.route ?: return false
    return route.startsWith("app_limit_add") ||
        route.startsWith("app_limit_edit") ||
        route.startsWith("app_history")
}

fun AnimatedContentTransitionScope<NavBackStackEntry>.isPoppingFromAppLimit(): Boolean {
    val route = initialState.destination.route ?: return false
    return route.startsWith("app_limit_add") ||
        route.startsWith("app_limit_edit") ||
        route.startsWith("app_history")
}
