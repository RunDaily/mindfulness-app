package com.life.mindfulnessapp.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.life.mindfulnessapp.R
import com.life.mindfulnessapp.domain.model.ThemePack

/**
 * 当前解析后的气质色板，供硬编码 Day/Night 的界面统一取色。
 */
data class ThemeChrome(
    val pack: ThemePack,
    val bg: Color,
    val card: Color,
    val cardElevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textHint: Color,
    val border: Color,
    val divider: Color,
    val accent: Color,
    val dockBg: Color,
    val isDark: Boolean,
    val windowBgRes: Int,
    val shellOpacity: Float,
)

fun ThemePack.chrome(): ThemeChrome {
    val opacity = shellOpacity
    return when (this) {
        ThemePack.Night -> ThemeChrome(
            pack = this,
            bg = NightBg,
            card = NightCardBg,
            cardElevated = NightCardElevated,
            textPrimary = NightTextPrimary,
            textSecondary = NightTextSecondary,
            textHint = NightTextHint,
            border = NightBorder,
            divider = NightDivider,
            accent = LogoGreen,
            dockBg = NightDockBg,
            isDark = true,
            windowBgRes = R.color.app_window_bg_night,
            shellOpacity = opacity,
        )
        ThemePack.Day -> ThemeChrome(
            pack = this,
            bg = DayBg,
            card = DayCardBg,
            cardElevated = DayCardElevated,
            textPrimary = DayTextPrimary,
            textSecondary = DayTextSecondary,
            textHint = DayTextHint,
            border = DayBorder,
            divider = DayDivider,
            accent = Color(0xFF1B9E55),
            dockBg = DayDockBg,
            isDark = false,
            windowBgRes = R.color.app_window_bg_day,
            shellOpacity = opacity,
        )
        ThemePack.Mist -> ThemeChrome(
            pack = this,
            bg = MistBg,
            card = MistCardBg,
            cardElevated = MistCardElevated,
            textPrimary = MistTextPrimary,
            textSecondary = MistTextSecondary,
            textHint = MistTextHint,
            border = MistBorder,
            divider = MistDivider,
            accent = MistAccent,
            dockBg = MistDockBg,
            isDark = false,
            windowBgRes = R.color.app_window_bg_mist,
            shellOpacity = opacity,
        )
    }
}

val LocalThemeChrome = staticCompositionLocalOf { ThemePack.Night.chrome() }

val LocalThemePack = staticCompositionLocalOf { ThemePack.Night }

@Composable
@ReadOnlyComposable
fun themeChrome(): ThemeChrome = LocalThemeChrome.current
