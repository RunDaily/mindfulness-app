package com.life.mindfulnessapp.ui.explore

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.life.mindfulnessapp.overlay.ThemeBackground
import com.life.mindfulnessapp.ui.theme.*
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
//  拦截主题数据模型
// ─────────────────────────────────────────────────────────────────────────────

data class InterceptTheme(
    val id: String,
    val name: String,
    val emoji: String,
    val description: String,
    val previewBg: Brush,
    val accentColor: Color,
    val secondaryColor: Color,
    val unlockHint: String,     // 解锁条件说明
    val isLocked: Boolean = false
)

val ALL_INTERCEPT_THEMES = listOf(
    InterceptTheme(
        id = "default",
        name = "正念",
        emoji = "🌿",
        description = "深邃宁静的深色背景，绿色光晕呼吸动画，引导你放慢节奏",
        previewBg = Brush.radialGradient(
            listOf(Color(0xFF1A2038), Color(0xFF111318))
        ),
        accentColor = Color(0xFF2ECC71),
        secondaryColor = Color(0xFF4AE890),
        unlockHint = "默认解锁",
        isLocked = false
    ),
    InterceptTheme(
        id = "deep_sea",
        name = "深海",
        emoji = "🌊",
        description = "深蓝沉静，气泡漂浮，仿佛潜入深海，远离喧嚣",
        previewBg = Brush.radialGradient(
            listOf(Color(0xFF0A2540), Color(0xFF050F1E))
        ),
        accentColor = Color(0xFF00B4D8),
        secondaryColor = Color(0xFF90E0EF),
        unlockHint = "默认解锁",
        isLocked = false
    ),
    InterceptTheme(
        id = "cyberpunk",
        name = "赛博",
        emoji = "🤖",
        description = "黑底荧光绿，代码雨效果，终端字体，感受未来世界的警报",
        previewBg = Brush.linearGradient(
            listOf(Color(0xFF001100), Color(0xFF002200))
        ),
        accentColor = Color(0xFF00FF41),
        secondaryColor = Color(0xFF39FF14),
        unlockHint = "默认解锁",
        isLocked = false
    ),
    InterceptTheme(
        id = "lava",
        name = "熔岩",
        emoji = "🔥",
        description = "深红炽热，熔岩流动感，让冲动在高温中冷却",
        previewBg = Brush.radialGradient(
            listOf(Color(0xFF3D0A00), Color(0xFF1A0000))
        ),
        accentColor = Color(0xFFFF4500),
        secondaryColor = Color(0xFFFF6B35),
        unlockHint = "设置中开放",
        isLocked = true
    ),
    InterceptTheme(
        id = "sakura",
        name = "樱花",
        emoji = "🌸",
        description = "粉白渐变，花瓣飘落，温柔而坚定地提醒你放下手机",
        previewBg = Brush.linearGradient(
            listOf(Color(0xFF3D0A22), Color(0xFF1E0011))
        ),
        accentColor = Color(0xFFFF85A1),
        secondaryColor = Color(0xFFFFB3C6),
        unlockHint = "设置中开放",
        isLocked = true
    ),
    InterceptTheme(
        id = "moon",
        name = "月球",
        emoji = "🌙",
        description = "深紫星空，月相变换，让你感受宇宙的寂静与辽阔",
        previewBg = Brush.radialGradient(
            listOf(Color(0xFF1A0D3D), Color(0xFF080316))
        ),
        accentColor = Color(0xFFB39DDB),
        secondaryColor = Color(0xFFCE93D8),
        unlockHint = "设置中开放",
        isLocked = true
    ),
    InterceptTheme(
        id = "glitch",
        name = "故障",
        emoji = "😤",
        description = "类似404错误页，故障艺术风格，让打开 App 这件事显得荒诞",
        previewBg = Brush.linearGradient(
            listOf(Color(0xFF1A0022), Color(0xFF0A0011))
        ),
        accentColor = Color(0xFFFF0080),
        secondaryColor = Color(0xFF00FFFF),
        unlockHint = "设置中开放",
        isLocked = true
    ),
    InterceptTheme(
        id = "rpg",
        name = "勇者",
        emoji = "⚔️",
        description = "像素RPG风格，「你的精力值不足」，把克制手机变成一场冒险",
        previewBg = Brush.linearGradient(
            listOf(Color(0xFF0D1B2A), Color(0xFF05060A))
        ),
        accentColor = Color(0xFFFFD700),
        secondaryColor = Color(0xFFFF8C00),
        unlockHint = "设置中开放",
        isLocked = true
    )
)

// ─────────────────────────────────────────────────────────────────────────────
//  主题页主入口
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    isDarkTheme: Boolean = true,
    currentThemeId: String = "default",
    allThemesUnlocked: Boolean = false,
    onThemeSelect: (String) -> Unit = {}
) {
    val bgColor = if (isDarkTheme) NightBg else DayBg
    val textPrimary = if (isDarkTheme) NightTextPrimary else DayTextPrimary
    val textSecondary = if (isDarkTheme) NightTextSecondary else DayTextSecondary

    val unlockedCount = ALL_INTERCEPT_THEMES.count { !it.isLocked || allThemesUnlocked }
    val totalCount = ALL_INTERCEPT_THEMES.size

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // 脉冲动画
    val infiniteTransition = rememberInfiniteTransition(label = "theme_pulse")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )

    Scaffold(
        containerColor = bgColor,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = if (isDarkTheme) Color(0xFF252840) else Color(0xFFE8F5EE),
                    contentColor = textPrimary,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── 顶部标题区 ────────────────────────────────────────────────────
            ThemePageHeader(
                isDarkTheme = isDarkTheme,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                glowPulse = glowPulse,
                unlockedCount = unlockedCount,
                totalCount = totalCount
            )

            // ── 主题网格 ──────────────────────────────────────────────────────
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(
                    start = 14.dp, end = 14.dp,
                    top = 4.dp, bottom = 32.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(ALL_INTERCEPT_THEMES, key = { it.id }) { theme ->
                    val isSelected = theme.id == currentThemeId
                    val isUnlocked = !theme.isLocked || allThemesUnlocked
                    ThemeCard(
                        theme = theme,
                        isUnlocked = isUnlocked,
                        isSelected = isSelected,
                        isDarkTheme = isDarkTheme,
                        onClick = {
                            if (isUnlocked) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onThemeSelect(theme.id)
                            } else {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                coroutineScope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    snackbarHostState.showSnackbar(
                                        message = "🔒 前往设置页 → 拦截主题，开放全部主题",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  顶部 Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ThemePageHeader(
    isDarkTheme: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    glowPulse: Float,
    unlockedCount: Int = 3,
    totalCount: Int = 8
) {
    val accentGreen = if (isDarkTheme) LogoGreen else Color(0xFF27AE60)
    val headerGradient = if (isDarkTheme) {
        Brush.verticalGradient(listOf(Color(0xFF151A28), Color(0xFF111318)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFE8F5EE), Color(0xFFF5F7FA)))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(headerGradient)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 14.dp, bottom = 18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 主题图标（带脉冲光效）
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(accentGreen.copy(alpha = glowPulse * 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✦", fontSize = 14.sp, color = accentGreen.copy(alpha = glowPulse))
                    }
                    Text(
                        text = "拦截主题",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        letterSpacing = (-0.5).sp
                    )
                }
                // 解锁进度胶囊
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (unlockedCount == totalCount) accentGreen.copy(alpha = 0.15f)
                            else textSecondary.copy(alpha = 0.08f)
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "$unlockedCount / $totalCount",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (unlockedCount == totalCount) accentGreen
                                else textSecondary.copy(alpha = 0.5f)
                    )
                }
            }
            Text(
                text = "为拦截页面换上不同风格，让克制变得有趣",
                fontSize = 13.sp,
                color = textSecondary,
                lineHeight = 18.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  主题卡片
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ThemeCard(
    theme: InterceptTheme,
    isUnlocked: Boolean,
    isSelected: Boolean,
    isDarkTheme: Boolean,
    onClick: () -> Unit
) {
    val cardBg = if (isDarkTheme) NightCardBg else DayCardBg
    val borderColor = if (isDarkTheme) NightBorder else DayBorder
    val textPrimary = if (isDarkTheme) NightTextPrimary else DayTextPrimary
    val textSecondary = if (isDarkTheme) NightTextSecondary else DayTextSecondary

    // 选中缩放动画
    val scaleAnim by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.97f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "card_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scaleAnim)
            .clip(RoundedCornerShape(18.dp))
            .background(cardBg)
            .then(
                if (isSelected) {
                    Modifier.border(
                        2.dp,
                        Brush.linearGradient(listOf(theme.accentColor, theme.secondaryColor)),
                        RoundedCornerShape(18.dp)
                    )
                } else {
                    Modifier.border(
                        1.dp,
                        borderColor.copy(alpha = if (isDarkTheme) 0.5f else 0.6f),
                        RoundedCornerShape(18.dp)
                    )
                }
            )
            .clickable(onClick = onClick)
    ) {
        Column {
            // 预览区域（真实动效 + emoji）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(108.dp)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    .background(theme.previewBg),
                contentAlignment = Alignment.Center
            ) {
                // 真实动效层（等比缩放渲染，锁定主题才显示动效）
                if (isUnlocked) {
                    ThemeBackground(
                        themeId = theme.id,
                        modifier = Modifier.matchParentSize()
                    )
                } else {
                    // 未解锁：用半透明蒙层 + 模糊暗化代替
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.45f))
                    )
                }
                // Emoji（居中，带光晕底托）
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    theme.accentColor.copy(alpha = 0.35f),
                                    Color.Transparent
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = theme.emoji,
                        fontSize = 28.sp
                    )
                }
                // 锁标记（右上角）
                if (!isUnlocked) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "已锁定",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
                // 选中标记（右上角勾）
                if (isSelected && isUnlocked) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(theme.accentColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "已选中",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            // 信息区域
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 主题名
                Text(
                    text = theme.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isUnlocked) textPrimary else textPrimary.copy(alpha = 0.45f),
                    maxLines = 1
                )

                // 描述（2行）
                Text(
                    text = theme.description,
                    fontSize = 11.sp,
                    color = if (isUnlocked) textSecondary.copy(alpha = 0.75f) else textSecondary.copy(alpha = 0.35f),
                    lineHeight = 15.sp,
                    maxLines = 2
                )

                Spacer(Modifier.height(2.dp))

                // 底部状态标签
                if (isSelected && isUnlocked) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(theme.accentColor.copy(alpha = 0.18f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "✓ 使用中",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = theme.accentColor
                        )
                    }
                } else if (!isUnlocked) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = textSecondary.copy(alpha = 0.35f),
                            modifier = Modifier.size(9.dp)
                        )
                        Text(
                            theme.unlockHint,
                            fontSize = 9.5.sp,
                            color = textSecondary.copy(alpha = 0.35f),
                            lineHeight = 13.sp
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(textSecondary.copy(alpha = if (isDarkTheme) 0.08f else 0.06f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "点击切换",
                            fontSize = 10.sp,
                            color = textSecondary.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}
