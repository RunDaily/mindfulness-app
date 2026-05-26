package com.life.mindfulnessapp.ui.navigation

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    object AppList : Screen("app_list")
    object AppLimitEdit : Screen("app_limit_edit/{packageName}") {
        fun createRoute(packageName: String) = "app_limit_edit/$packageName"
    }
    object Stats : Screen("stats")
    object AppDetailStats : Screen("app_detail_stats/{packageName}") {
        fun createRoute(packageName: String) = "app_detail_stats/$packageName"
    }
    object Settings : Screen("settings")
    object Explore : Screen("explore")   // 现在用作「主题」Tab
}
