package com.life.mindfulnessapp.ui.settings

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.ui.theme.*

// ════════════════════════════════════════════════════════════════════════════
//  ThemeScreen  ·  独立主题设置页
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun ThemeScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    interceptThemeId: String = "default",
    onThemeSelected: (String) -> Unit = {},
    onNavigateToVip: () -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()

    // ── 动态颜色 ──────────────────────────────────────────────────────────
    val bgColor       = if (isDarkTheme) NightBg           else DayBg
    val cardColor     = if (isDarkTheme) NightCardBg        else DayCardBg
    val textPrimary   = if (isDarkTheme) NightTextPrimary   else DayTextPrimary
    val textSecondary = if (isDarkTheme) NightTextSecondary else DayTextSecondary
    val borderColor   = if (isDarkTheme) NightBorder        else DayBorder
    val dividerColor  = if (isDarkTheme) NightDivider       else DayDivider
    val accentGreen   = if (isDarkTheme) LogoGreen          else Color(0xFF27AE60)

    Scaffold(
        containerColor = bgColor
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // ── 顶部导航栏 ────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBackIosNew,
                        contentDescription = "返回",
                        tint = textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = "主题与外观",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            HorizontalDivider(
                color = dividerColor,
                thickness = 0.5.dp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ══════════════════════════════════════════════════
            //  1. 外观模式（日间 / 夜间）
            // ══════════════════════════════════════════════════
            ThemeSectionLabel(text = "外观模式", textColor = textSecondary)

            AppearanceSelectorCard(
                isDark = isDarkTheme,
                onToggle = { viewModel.setDarkTheme(it) },
                cardColor = cardColor,
                borderColor = borderColor,
                dividerColor = dividerColor,
                textPrimary = textPrimary,
                accentGreen = accentGreen
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ══════════════════════════════════════════════════
            //  2. 拦截风格（3 种，无 VIP 门禁）
            // ══════════════════════════════════════════════════
            ThemeSectionLabel(text = "拦截风格", textColor = textSecondary)

            InterceptModeSelector(
                selectedThemeId = interceptThemeId,
                cardColor = cardColor,
                borderColor = borderColor,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                accentGreen = accentGreen,
                onThemeSelected = onThemeSelected
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  外观模式选择卡片
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun AppearanceSelectorCard(
    isDark: Boolean,
    onToggle: (Boolean) -> Unit,
    cardColor: Color,
    borderColor: Color,
    dividerColor: Color,
    textPrimary: Color,
    accentGreen: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(cardColor)
            .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // 当前模式 icon + 标签
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(accentGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isDark) Icons.Default.NightlightRound else Icons.Default.LightMode,
                        contentDescription = null,
                        tint = accentGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isDark) "夜间模式" else "日间模式",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimary
                    )
                    Text(
                        text = if (isDark) "深蓝黑底，护眼沉浸" else "明亮清爽，清晰易读",
                        fontSize = 12.sp,
                        color = accentGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = dividerColor)
            Spacer(modifier = Modifier.height(14.dp))

            // 日 / 夜 两个大按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppearanceOptionButton(
                    modifier = Modifier.weight(1f),
                    label = "☀️ 日间",
                    description = "明亮清爽",
                    isSelected = !isDark,
                    bgColor = Color(0xFFF5F7F5),
                    highlightColor = Color(0xFF34C26A),
                    textColor = Color(0xFF1A1D1A),
                    onClick = { onToggle(false) }
                )
                AppearanceOptionButton(
                    modifier = Modifier.weight(1f),
                    label = "🌙 夜间",
                    description = "护眼沉浸",
                    isSelected = isDark,
                    bgColor = Color(0xFF0D1117),
                    highlightColor = Color(0xFF4CD980),
                    textColor = Color(0xFFE6EDF3),
                    onClick = { onToggle(true) }
                )
            }
        }
    }
}

@Composable
private fun AppearanceOptionButton(
    modifier: Modifier = Modifier,
    label: String,
    description: String,
    isSelected: Boolean,
    bgColor: Color,
    highlightColor: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    val borderW = if (isSelected) 2.dp else 1.dp
    val borderC = if (isSelected) highlightColor else highlightColor.copy(alpha = 0.18f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(borderW, borderC, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 预览色条
            Box(
                modifier = Modifier
                    .width(52.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(highlightColor.copy(alpha = if (isSelected) 0.95f else 0.28f))
            )
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) highlightColor else textColor.copy(alpha = 0.5f)
            )
            Text(
                text = description,
                fontSize = 11.sp,
                color = if (isSelected) highlightColor.copy(alpha = 0.65f) else textColor.copy(alpha = 0.3f)
            )
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(highlightColor)
                )
            }
        }
    }
}


// ════════════════════════════════════════════════════════════════════════════
//  拦截风格选择（3 种模式）
// ════════════════════════════════════════════════════════════════════════════

private data class InterceptMode(
    val id: String,
    val name: String,
    val emoji: String,
    val description: String,
    val accentColor: Color,
    val bgColor: Color
)

private val INTERCEPT_MODES = listOf(
    InterceptMode(
        id          = "simple",
        name        = "极简",
        emoji       = "◻",
        description = "iOS 风格，日/夜双模，纯色背景，无多余装饰",
        accentColor = Color(0xFF007AFF),
        bgColor     = Color(0xFFF2F2F7)
    ),
    InterceptMode(
        id          = "default",
        name        = "正念",
        emoji       = "🌿",
        description = "绿色引导，有意识地停下来思考",
        accentColor = Color(0xFF3DDC84),
        bgColor     = Color(0xFF0D1117)
    ),
    InterceptMode(
        id          = "zen",
        name        = "禅",
        emoji       = "◯",
        description = "极简黑白，一句话、一次呼吸",
        accentColor = Color(0xFFCCCCCC),
        bgColor     = Color(0xFF0A0A0A)
    ),
)

@Composable
private fun InterceptModeSelector(
    selectedThemeId: String,
    cardColor: Color,
    borderColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentGreen: Color,
    onThemeSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        INTERCEPT_MODES.forEach { mode ->
            val isSelected = mode.id == selectedThemeId
            val borderW = if (isSelected) 2.dp else 1.dp
            val borderC = if (isSelected) mode.accentColor else borderColor.copy(alpha = 0.4f)
            val bgAlpha = if (isSelected) 0.12f else 0.05f

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(mode.accentColor.copy(alpha = bgAlpha))
                    .border(borderW, borderC, RoundedCornerShape(14.dp))
                    .clickable { onThemeSelected(mode.id) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(text = mode.emoji, fontSize = 26.sp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mode.name,
                        fontSize = 15.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) mode.accentColor else textPrimary
                    )
                    Text(
                        text = mode.description,
                        fontSize = 12.sp,
                        color = textSecondary.copy(alpha = if (isSelected) 0.85f else 0.55f)
                    )
                }
                if (isSelected) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = mode.accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  小工具
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ThemeSectionLabel(text: String, textColor: Color) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = textColor.copy(alpha = 0.5f),
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}
