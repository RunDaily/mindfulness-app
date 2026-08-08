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
//  ThemeScreen  ·  独立主题设置页（MVP：仅日间 / 夜间）
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun ThemeScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()

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

            Spacer(Modifier.height(16.dp))

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

            Spacer(Modifier.height(12.dp))

            Text(
                text = "拦截页、胶囊与主界面会跟随外观模式切换日间 / 夜间配色。",
                fontSize = 12.sp,
                color = textSecondary.copy(alpha = 0.55f),
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

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

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = dividerColor)
            Spacer(Modifier.height(14.dp))

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

@Composable
private fun ThemeSectionLabel(text: String, textColor: Color) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = textColor.copy(alpha = 0.55f),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
    )
}
