package com.life.mindfulnessapp.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.domain.model.AppDayInsight
import com.life.mindfulnessapp.domain.model.formatInsightMinutes
import com.life.mindfulnessapp.domain.model.formatPeakHourLabel
import com.life.mindfulnessapp.ui.theme.HeatmapNeutral
import com.life.mindfulnessapp.ui.theme.LogoGreen

/** 时段空转峰提示 + 可选时段锁建议 */
@Composable
fun IdlePeakHintRow(
    peakHour: Int?,
    idleSeconds: Long,
    showPeriodLockSuggest: Boolean,
    onSuggestPeriodLock: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    if (peakHour == null || idleSeconds < 5 * 60L) return
    val cs = MaterialTheme.colorScheme
    val peakLabel = formatPeakHourLabel(peakHour) ?: return
    val endLabel = formatPeakHourLabel((peakHour + 1) % 24) ?: ""
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "空转高峰 $peakLabel–$endLabel · 约 ${formatInsightMinutes(idleSeconds)}",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFC4891A).copy(alpha = 0.92f)
        )
        if (showPeriodLockSuggest && onSuggestPeriodLock != null) {
            Text(
                text = "这段时间容易飘 · 可加时段锁",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = LogoGreen.copy(alpha = 0.90f),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(LogoGreen.copy(alpha = 0.08f))
                    .border(1.dp, LogoGreen.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                    .clickable(onClick = onSuggestPeriodLock)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
    }
}

/** 对照兑现：对照次数 + 三档 + 跑偏合计 */
@Composable
fun CompareFulfillmentCard(
    insight: AppDayInsight,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    if (insight.reviewedCount <= 0 && insight.mindfulEnterCount <= 0) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cs.surface.copy(alpha = 0.94f))
            .border(1.dp, cs.outlineVariant.copy(alpha = 0.16f), shape)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "对照兑现",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurface.copy(alpha = 0.42f)
        )
        if (insight.reviewedCount <= 0) {
            Text(
                text = "有 ${insight.mindfulEnterCount} 次带着意图，还没对照",
                fontSize = 13.sp,
                color = cs.onSurface.copy(alpha = 0.55f)
            )
        } else {
            Text(
                text = buildString {
                    append("对照过 ${insight.reviewedCount}")
                    append(" · 没跑偏 ${insight.alignedCount}")
                    append(" · 跑偏 ${insight.slightCount}")
                    append(" · 跑远 ${insight.largeCount}")
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = cs.onSurface.copy(alpha = 0.78f)
            )
            if (insight.driftSecondsTotal > 0L) {
                Text(
                    text = "其中跑偏约 ${formatInsightMinutes(insight.driftSecondsTotal)}（已计入空转）",
                    fontSize = 12.sp,
                    color = Color(0xFFC4891A).copy(alpha = 0.88f)
                )
            } else if (insight.alignedCount == insight.reviewedCount) {
                Text(
                    text = "这一天对照都没跑偏",
                    fontSize = 12.sp,
                    color = LogoGreen.copy(alpha = 0.88f)
                )
            }
        }
        if (insight.dismissCount.today > 0) {
            Text(
                text = "守住 ${insight.dismissCount.today.toInt()} 次",
                fontSize = 12.sp,
                color = HeatmapNeutral.copy(alpha = 0.88f)
            )
        }
    }
}

@Composable
fun EvidenceFoldHeader(
    totalCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (expanded) "收起证据" else "今日证据 · $totalCount",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface.copy(alpha = 0.45f)
            )
            if (!subtitle.isNullOrBlank() && !expanded) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = cs.onSurface.copy(alpha = 0.32f)
                )
            }
        }
        Text(
            text = if (expanded) "收起" else "展开",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = LogoGreen.copy(alpha = 0.85f)
        )
    }
}

@Composable
fun ExpandableEvidenceBlock(
    totalCount: Int,
    initiallyExpanded: Boolean = false,
    collapsedHint: String? = null,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Column(modifier = Modifier.fillMaxWidth()) {
        EvidenceFoldHeader(
            totalCount = totalCount,
            expanded = expanded,
            onToggle = { expanded = !expanded },
            subtitle = collapsedHint
        )
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                content()
            }
        }
    }
}
