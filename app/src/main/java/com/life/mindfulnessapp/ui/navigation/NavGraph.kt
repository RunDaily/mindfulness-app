package com.life.mindfulnessapp.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    object AppList : Screen("app_list")
    object AppLimitEdit : Screen("app_limit_edit/{packageName}") {
        fun createRoute(packageName: String) = "app_limit_edit/$packageName"
    }
    object Stats : Screen("stats")
    object AppDetailStats : Screen("app_detail_stats/{packageName}?autoEdit={autoEdit}") {
        fun createRoute(packageName: String, autoEdit: Boolean = false) =
            if (autoEdit) "app_detail_stats/$packageName?autoEdit=true"
            else "app_detail_stats/$packageName"
    }
    /** 第3个 Tab：我的页面 */
    object Profile : Screen("profile")
    /** 日报完整页（从统计页入口进入，可携带 dayOffset 参数） */
    object DailyReport : Screen("daily_report?dayOffset={dayOffset}") {
        fun createRoute(dayOffset: Int = 0) = "daily_report?dayOffset=$dayOffset"
    }
    /** 历史总览页（按日期走势，从统计页入口进入） */
    object Overview : Screen("overview")
    /** 二级设置页（从「我」进入） */
    object Settings : Screen("settings")
    object Theme : Screen("theme")
    object Vip : Screen("vip")
    object Invite : Screen("invite")
    object FavoriteQuotes : Screen("favorite_quotes")
}

/** 底部导航的三个 Tab */
sealed class BottomTab(
    val screen: Screen,
    val label: String,
    val icon: ImageVector
) {
    object Home : BottomTab(Screen.Home, "今日", Icons.Default.Home)
    object Stats : BottomTab(Screen.Stats, "统计", Icons.Default.BarChart)
    object Profile : BottomTab(Screen.Profile, "我", Icons.Default.Person)

    companion object {
        val all = listOf(Home, Stats, Profile)
    }
}
