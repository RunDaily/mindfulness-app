package com.life.mindfulnessapp.ui.stats

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.domain.model.AppDetailStats
import com.life.mindfulnessapp.overlay.formatSeconds
import com.life.mindfulnessapp.ui.applist.AppIcon
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.theme.MindfulGreen40

// ── 小组件页面主入口 ────────────────────────────────────────────────────────────

@Composable
fun StatsScreen(
    viewModel: StatsViewModel = hiltViewModel(),
    onNavigateToAppDetail: (packageName: String) -> Unit = {}
) {
    val todayAppDetails by viewModel.todayAppDetails.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val cs = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
            .statusBarsPadding()
    ) {
        // ── 顶部标题 + Toggle ──────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 14.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "组件",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = cs.onBackground
            )

            // 今日 / 本周 Toggle
            PillToggle(
                selected = selectedTab,
                options = listOf("今日", "本周"),
                onSelect = { viewModel.selectTab(it) },
                activeColor = MindfulGreen40,
                cs = cs
            )
        }

        if (todayAppDetails.isEmpty()) {
            // 空态
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(LogoGreen.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = null,
                            tint = cs.onBackground.copy(alpha = 0.25f),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Text(
                        "暂无监控应用",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onBackground.copy(alpha = 0.45f)
                    )
                    Text(
                        "在主页添加需要监控的应用\n这里会显示每个应用的使用时长",
                        fontSize = 13.sp,
                        color = cs.onBackground.copy(alpha = 0.28f),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            }
        } else {
            // 网格布局
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(
                    start = 14.dp, end = 14.dp,
                    top = 4.dp, bottom = 32.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(todayAppDetails, key = { it.packageName }) { detail ->
                    AppUsageWidget(
                        detail = detail,
                        showWeek = selectedTab == 1,
                        cs = cs,
                        onClick = { onNavigateToAppDetail(detail.packageName) }
                    )
                }
            }
        }
    }
}

// ── 胶囊式 Toggle ──────────────────────────────────────────────────────────────

@Composable
private fun PillToggle(
    selected: Int,
    options: List<String>,
    onSelect: (Int) -> Unit,
    activeColor: Color,
    cs: ColorScheme
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(cs.surfaceVariant.copy(alpha = 0.55f))
            .padding(3.dp)
    ) {
        Row {
            options.forEachIndexed { index, label ->
                val isSelected = selected == index
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) activeColor else Color.Transparent,
                    animationSpec = tween(200),
                    label = "toggleBg$index"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else cs.onSurface.copy(alpha = 0.5f),
                    animationSpec = tween(200),
                    label = "toggleText$index"
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(bgColor)
                        .clickable { onSelect(index) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = textColor
                    )
                }
            }
        }
    }
}

// ── 单个 App 使用时长小组件卡片 ────────────────────────────────────────────────

@Composable
private fun AppUsageWidget(
    detail: AppDetailStats,
    showWeek: Boolean,
    cs: ColorScheme,
    onClick: () -> Unit
) {
    val usedSeconds = if (showWeek) detail.weekSeconds else detail.todaySeconds
    val limitSeconds = if (showWeek) detail.weeklyLimitSeconds else detail.dailyLimitSeconds
    val progress = if (limitSeconds > 0) (usedSeconds.toFloat() / limitSeconds).coerceAtMost(1f) else 0f

    val progressColor = when {
        progress >= 1f -> Color(0xFFE74C3C)
        progress >= 0.8f -> Color(0xFFE8941A)
        usedSeconds > 0L -> MindfulGreen40
        else -> cs.outlineVariant.copy(alpha = 0.5f)
    }

    val animProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(600),
        label = "widgetProg"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)           // 保持正方形
            .clip(RoundedCornerShape(20.dp))
            .background(cs.surface)
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 顶部：App 图标 + 进度弧（右上角小圆形进度）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // App 图标（通过 packageName 在 Compose 侧直接加载）
                AppIconWidget(packageName = detail.packageName)

                // 右上角：进度环（仅有限额时显示）
                if (limitSeconds > 0) {
                    Box(
                        modifier = Modifier.size(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { 1f },
                            modifier = Modifier.size(32.dp),
                            color = cs.outlineVariant.copy(alpha = 0.3f),
                            strokeWidth = 2.5.dp
                        )
                        CircularProgressIndicator(
                            progress = { animProgress },
                            modifier = Modifier.size(32.dp),
                            color = progressColor,
                            strokeWidth = 2.5.dp
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = progressColor,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // 中部：已用时长（大字）
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (usedSeconds > 0L) formatSeconds(usedSeconds) else "—",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (usedSeconds > 0L) cs.onSurface else cs.onSurface.copy(alpha = 0.2f),
                    letterSpacing = (-0.5).sp,
                    maxLines = 1
                )
                Text(
                    text = if (showWeek) "本周已用" else "今日已用",
                    fontSize = 11.sp,
                    color = cs.onSurface.copy(alpha = 0.38f),
                    letterSpacing = 0.3.sp
                )
            }

            // 底部：App 名称 + 限额提示
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = detail.appName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = cs.onSurface.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (limitSeconds > 0) {
                    // 线性进度条
                    LinearProgressIndicator(
                        progress = { animProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = progressColor,
                        trackColor = cs.outlineVariant.copy(alpha = 0.3f)
                    )
                    Text(
                        text = "限额 ${formatSeconds(limitSeconds)}",
                        fontSize = 10.sp,
                        color = cs.onSurface.copy(alpha = 0.28f)
                    )
                } else {
                    Text(
                        text = "未设限额",
                        fontSize = 10.sp,
                        color = cs.onSurface.copy(alpha = 0.2f)
                    )
                }
            }
        }
    }
}

// ── App 图标 Composable（直接从 PackageManager 加载）─────────────────────────

@Composable
private fun AppIconWidget(packageName: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val icon = remember(packageName) {
        try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            null
        }
    }
    AppIcon(
        drawable = icon,
        modifier = Modifier.size(44.dp)
    )
}
