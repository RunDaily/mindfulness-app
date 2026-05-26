package com.life.mindfulnessapp.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ══════════════════════════════════════════════════════════════════════════════
//  夜间主题（Night Theme）
//  基调：深蓝灰底 #111318，翠绿高亮 #2ECC71
//  风格：现代胶囊计时UI，不压抑，蓝灰色系背景，翠绿点缀
// ══════════════════════════════════════════════════════════════════════════════
private val NightColorScheme = darkColorScheme(
    // 主色：Logo 翠绿（更饱和、高对比度）
    primary            = LogoGreen,
    onPrimary          = Color(0xFF001A0A),
    primaryContainer   = Color(0xFF0A1E35),
    onPrimaryContainer = LogoGreenBright,

    // 辅色
    secondary          = MindfulGreen40,
    onSecondary        = Color(0xFF001A0A),
    secondaryContainer = Color(0xFF152040),
    onSecondaryContainer = LogoGreenBright,

    // 第三色（蓝紫调，彰显夜晚感）
    tertiary           = Color(0xFF6C8EFF),
    onTertiary         = Color(0xFF001040),
    tertiaryContainer  = Color(0xFF1A2550),
    onTertiaryContainer = Color(0xFFB0C4FF),

    // 背景：深蓝黑
    background         = NightBg,
    onBackground       = NightTextPrimary,

    // 卡片/Surface（胶囊风格核心）
    surface            = NightCardBg,
    onSurface          = NightTextPrimary,
    surfaceVariant     = NightHeaderBg,         // 用于 Header 卡片背景（深绿）
    onSurfaceVariant   = Color.White,           // Header 卡片上的文字为白色

    outline            = NightBorder,
    outlineVariant     = NightDivider,

    error              = DangerColor,
    onError            = Color.White,
)

// ══════════════════════════════════════════════════════════════════════════════
//  日间主题（Day Theme）
//  基调：白/浅蓝灰底 #F5F7FA，翠绿强调 #27AE60
// ══════════════════════════════════════════════════════════════════════════════
private val DayColorScheme = lightColorScheme(
    // 主色：清新翠绿
    primary            = Color(0xFF1E8B4E),
    onPrimary          = Color(0xFFFFFFFF),
    primaryContainer   = Color(0xFFB8F0D0),
    onPrimaryContainer = Color(0xFF00391E),

    // 辅色
    secondary          = Color(0xFF27AE60),
    onSecondary        = Color.White,
    secondaryContainer = Color(0xFFC8F0DC),
    onSecondaryContainer = Color(0xFF00391E),

    // 第三色（蓝调）
    tertiary           = Color(0xFF3A7BD5),
    onTertiary         = Color.White,
    tertiaryContainer  = Color(0xFFD0E4FF),
    onTertiaryContainer = Color(0xFF00286E),

    // 背景：极淡蓝灰
    background         = DayBg,
    onBackground       = DayTextPrimary,

    // 卡片/Surface
    surface            = DayCardBg,
    onSurface          = DayTextPrimary,
    surfaceVariant     = DayHeaderBg,           // 用于 Header 卡片背景（翠绿）
    onSurfaceVariant   = Color.White,           // Header 卡片上的文字为白色

    outline            = DayBorder,
    outlineVariant     = DayDivider,

    error              = Color(0xFFBA1A1A),
    onError            = Color.White,
)

// ══════════════════════════════════════════════════════════════════════════════
//  MindfulnessAppTheme：统一入口，通过 darkTheme 参数切换日间/夜间
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun MindfulnessAppTheme(
    darkTheme: Boolean = true,          // true = 夜间主题，false = 日间主题
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) NightColorScheme else DayColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            window?.let {
                // 夜间主题 → 状态栏图标白色；日间主题 → 状态栏图标深色
                WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}
