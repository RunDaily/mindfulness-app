package com.life.mindfulnessapp.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.domain.model.PendingInterrupt
import com.life.mindfulnessapp.domain.model.RecentPurposeStat
import com.life.mindfulnessapp.overlay.InterceptThemeConfig

/**
 * 「刚刚 · 意图」弱入口（单行，不抢主路径；不展示中断原因）。
 */
@Composable
fun IntentSoftResumeLink(
    interrupt: PendingInterrupt,
    themeConfig: InterceptThemeConfig,
    enabled: Boolean,
    onResume: () -> Unit,
    modifier: Modifier = Modifier
) {
    val summary = interrupt.gateResumePurposeLabel().ifBlank {
        interrupt.purpose?.trim().orEmpty()
    }
    if (summary.isBlank()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onResume)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "刚刚 · $summary",
            fontSize = 13.sp,
            color = themeConfig.textSecondary.copy(alpha = if (enabled) 0.88f else 0.4f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "  继续",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = themeConfig.accentColor.copy(alpha = if (enabled) 0.9f else 0.4f)
        )
    }
}

/** 去重后的今日意图（今日有用量优先；否则回退近期有记录的意图） */
fun todayIntentStats(recentPurposes: List<RecentPurposeStat>): List<RecentPurposeStat> {
    val cleaned = recentPurposes
        .map { it.copy(purpose = it.purpose.trim()) }
        .filter { it.purpose.isNotEmpty() }
        .distinctBy { it.purpose }
    val today = cleaned.filter { it.todaySeconds > 0L }
        .sortedByDescending { it.todaySeconds }
    if (today.isNotEmpty()) return today
    return cleaned.sortedByDescending { it.lastUsedAt }
}

/**
 * 头区语境层的「今日意图」弱链：与「今日已用」同级字号，
 * 仅文字略亮 + › 暗示可点，无描边/底色，不抢主路径。
 */
@Composable
fun TodayIntentContextLink(
    count: Int,
    themeConfig: InterceptThemeConfig,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (count <= 0) return
    Text(
        text = "今日意图 $count ›",
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = themeConfig.textSecondary.copy(alpha = if (enabled) 0.78f else 0.35f),
        letterSpacing = 0.15.sp,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 1.dp)
    )
}

/**
 * 今日意图详情：层内遮罩列表（不用 Dialog，避免悬浮窗二次 Window）。
 * 点选填入，不直接进门。
 */
@Composable
fun TodayIntentDetailOverlay(
    stats: List<RecentPurposeStat>,
    themeConfig: InterceptThemeConfig,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetShape = RoundedCornerShape(20.dp)
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(themeConfig.bgColor.copy(alpha = 0.72f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.9f)
                .heightIn(max = 520.dp)
                .clip(sheetShape)
                .background(themeConfig.surfaceColor)
                .border(1.dp, themeConfig.dividerColor.copy(alpha = 0.4f), sheetShape)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {}
                )
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Text(
                text = "今日意图",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = themeConfig.textPrimary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Text(
                text = "共 ${stats.size} 条 · 点选填入，再确认时长",
                fontSize = 13.sp,
                color = themeConfig.textSecondary.copy(alpha = 0.75f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 14.dp),
                textAlign = TextAlign.Center
            )
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                stats.forEach { stat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = enabled) { onSelect(stat.purpose.trim()) }
                            .padding(vertical = 14.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stat.purpose.trim(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = themeConfig.textPrimary.copy(alpha = if (enabled) 0.92f else 0.4f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (stat.todaySeconds > 0L) {
                            Text(
                                text = formatCompactDuration(stat.todaySeconds),
                                fontSize = 13.sp,
                                color = themeConfig.textSecondary.copy(alpha = 0.8f)
                            )
                        } else if (stat.useCount > 1) {
                            Text(
                                text = "${stat.useCount}次",
                                fontSize = 13.sp,
                                color = themeConfig.textSecondary.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "关闭",
                fontSize = 15.sp,
                color = themeConfig.textSecondary,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            )
        }
    }
}

/** 拦截页紧凑时长：`32分` / `1时20分` / `45秒` */
fun formatCompactDuration(seconds: Long): String {
    if (seconds <= 0L) return "0分"
    if (seconds < 60L) return "${seconds}秒"
    val minutes = seconds / 60L
    return when {
        minutes < 60L -> "${minutes}分"
        minutes % 60L == 0L -> "${minutes / 60}时"
        else -> "${minutes / 60}时${minutes % 60}分"
    }
}
