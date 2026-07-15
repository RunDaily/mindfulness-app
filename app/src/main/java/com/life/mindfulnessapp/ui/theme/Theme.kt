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
//  夜间主题（Night Theme）v2
//  ─ 背景三层拉开：0F1117 → 1A1E2C → 222840（各差 ~10 亮度单位）
//  ─ 护眼绿 #26BB68 替代高饱和 #2ECC71
//  ─ 第三色改为冷蓝信息色（语义：链接/时间/中性标注）
//  ─ secondaryContainer 加深，与 surface 拉开明显对比
// ══════════════════════════════════════════════════════════════════════════════
private val NightColorScheme = darkColorScheme(
    // 主色：护眼翠绿（降饱和度，长期使用不疲劳）
    primary            = LogoGreen,               // #26BB68
    onPrimary          = Color(0xFF00180A),
    primaryContainer   = Color(0xFF0D2235),        // 深蓝调，避免暗绿压抑
    onPrimaryContainer = LogoGreenBright,          // #43CC7E，高对比显示文字

    // 辅色（稍深于 primary，形成层次）
    secondary          = LogoGreenDeep,            // #1A9E55 —— 比 primary 暗一档
    onSecondary        = Color(0xFF00180A),
    secondaryContainer = Color(0xFF152435),        // 深蓝绿调容器（比旧 152040 稍绿）
    onSecondaryContainer = LogoGreenBright,

    // 第三色：冷蓝信息色（语义：时间、数据、中性标注）
    // 替换旧蓝紫 6C8EFF，改为更沉稳的天蓝调
    tertiary           = Color(0xFF5096D8),        // 天蓝，信息语义
    onTertiary         = Color(0xFF001840),
    tertiaryContainer  = Color(0xFF182845),
    onTertiaryContainer = Color(0xFFA8C8F0),

    // 背景：L1 深黑蓝（AMOLED 友好）
    background         = NightBg,                  // #0F1117
    onBackground       = NightTextPrimary,          // #EDF0F7

    // 卡片/Surface：L2 深蓝灰（比背景明显亮，卡片有浮起感）
    surface            = NightCardBg,              // #1A1E2C
    onSurface          = NightTextPrimary,
    // surfaceVariant：用于次级容器，如 BottomSheet/折叠区背景
    surfaceVariant     = NightCardElevated,        // #222840（L3，弹层级别）
    onSurfaceVariant   = NightTextSecondary,       // #8A96B0，次级文字

    outline            = NightBorder,              // #273045
    outlineVariant     = NightDivider,             // #1D2438

    error              = DangerColor,              // #CF4040（更内敛）
    onError            = Color.White,

    // scrim：遮罩层（BottomSheet 半透黑）
    scrim              = Color(0xFF000000),
)

// ══════════════════════════════════════════════════════════════════════════════
//  日间主题（Day Theme）v2
//  ─ 背景改用 F4F6F9（偏冷暖中性，替代偏冷 F5F7FA）
//  ─ primary #1B9E55 / secondary #26BB68，拉开明度差，选中态清晰
//  ─ secondaryContainer 改为极淡薄荷 #EAF6EF（旧 C8F0DC 过重）
//  ─ surfaceVariant 改为 DayCardGreen，用于已监控/高亮卡片容器
//  ─ tertiary 改为 #3577CC（深蓝），日间信息标注，对比度 ≥ 4.5:1
// ══════════════════════════════════════════════════════════════════════════════
private val DayColorScheme = lightColorScheme(
    // 主色：深翠绿（更深，日间强对比，WCAG AA ≥ 4.5:1 on white）
    primary            = Color(0xFF1B9E55),        // 比旧 1E8B4E 稍亮，更清新
    onPrimary          = Color(0xFFFFFFFF),
    primaryContainer   = Color(0xFFCCF0DE),        // 比旧 B8F0D0 略饱和，呼应品牌绿
    onPrimaryContainer = Color(0xFF003D20),

    // 辅色：品牌绿（比 primary 浅一档）
    secondary          = Color(0xFF26BB68),        // 与 LogoGreen 一致，形成层次
    onSecondary        = Color.White,
    // secondaryContainer：极淡薄荷（旧 C8F0DC 过重，改为 EAF6EF 更轻盈）
    secondaryContainer = Color(0xFFEAF6EF),
    onSecondaryContainer = Color(0xFF003D20),

    // 第三色：深蓝（日间信息标注、时间显示）
    // 替换旧 3A7BD5，使用更偏靛蓝的 3577CC，对比度更高
    tertiary           = Color(0xFF3577CC),
    onTertiary         = Color.White,
    tertiaryContainer  = Color(0xFFD4E7FF),
    onTertiaryContainer = Color(0xFF003070),

    // 背景：暖冷中性灰（比纯冷灰有温度感）
    background         = DayBg,                   // #F4F6F9
    onBackground       = DayTextPrimary,           // #191D2B

    // 卡片/Surface：纯白（通过 1dp outline 与背景区分）
    surface            = DayCardBg,               // #FFFFFF
    onSurface          = DayTextPrimary,
    // surfaceVariant：用于高亮/已监控卡片容器（薄荷绿，轻盈不沉重）
    surfaceVariant     = DayCardGreen,             // #EAF6EF
    onSurfaceVariant   = DayTextSecondary,         // #556070

    outline            = DayBorder,               // #CBD4E2（卡片边框，模拟阴影）
    outlineVariant     = DayDivider,              // #E0E6EF（细分割线）

    error              = Color(0xFFC0392B),        // 深红，日间更醒目
    onError            = Color.White,

    // scrim：遮罩层
    scrim              = Color(0xFF000000),
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
