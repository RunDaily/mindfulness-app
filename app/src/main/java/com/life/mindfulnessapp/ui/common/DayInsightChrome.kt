package com.life.mindfulnessapp.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.domain.model.DayInsightSnapshot
import com.life.mindfulnessapp.domain.model.MetricDelta
import com.life.mindfulnessapp.domain.model.formatInsightDeltaArrow
import com.life.mindfulnessapp.domain.model.formatInsightMinutes
import com.life.mindfulnessapp.ui.theme.HeatmapNeutral
import com.life.mindfulnessapp.ui.theme.LogoGreen

/**
 * 今日洞见卡：一句结论 + 四指标（有目的 / 守住 / 空转 / 总时长）。
 */
@Composable
fun DayInsightCard(
    headline: String,
    mindful: MetricDelta,
    dismiss: MetricDelta,
    idle: MetricDelta,
    total: MetricDelta,
    showYesterdayDelta: Boolean = true,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(if (compact) 14.dp else 16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cs.surface.copy(alpha = 0.94f))
            .border(1.dp, cs.outlineVariant.copy(alpha = 0.18f), shape)
            .padding(
                horizontal = if (compact) 12.dp else 14.dp,
                vertical = if (compact) 12.dp else 14.dp
            ),
        verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp)
    ) {
        Text(
            text = headline,
            fontSize = if (compact) 15.sp else 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurface.copy(alpha = 0.90f),
            lineHeight = if (compact) 21.sp else 22.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            InsightMetricCell(
                label = "有目的",
                value = formatInsightMinutes(mindful.today),
                delta = if (showYesterdayDelta) formatInsightDeltaArrow(mindful.delta) else null,
                accent = LogoGreen,
                modifier = Modifier.weight(1f)
            )
            InsightMetricCell(
                label = "守住",
                value = dismiss.today.toInt().toString(),
                delta = if (showYesterdayDelta && dismiss.delta != 0L) {
                    val d = dismiss.delta.toInt()
                    if (d > 0) "+$d" else "$d"
                } else null,
                accent = HeatmapNeutral,
                modifier = Modifier.weight(1f)
            )
            InsightMetricCell(
                label = "空转",
                value = formatInsightMinutes(idle.today),
                delta = if (showYesterdayDelta) formatInsightDeltaArrow(idle.delta) else null,
                accent = Color(0xFFC4891A),
                modifier = Modifier.weight(1f)
            )
            InsightMetricCell(
                label = "总时长",
                value = formatInsightMinutes(total.today),
                delta = if (showYesterdayDelta) formatInsightDeltaArrow(total.delta) else null,
                accent = cs.onSurface.copy(alpha = 0.55f),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun DayInsightCard(
    snapshot: DayInsightSnapshot,
    unmoored: Boolean = false,
    modifier: Modifier = Modifier
) {
    DayInsightCard(
        headline = when {
            unmoored -> "系锚后，这里会看见今天"
            else -> snapshot.headline
        },
        mindful = snapshot.mindfulSeconds,
        dismiss = snapshot.dismissCount,
        idle = snapshot.idleSeconds,
        total = snapshot.totalSeconds,
        showYesterdayDelta = !unmoored && snapshot.hasSignal,
        modifier = modifier
    )
}

@Composable
private fun InsightMetricCell(
    label: String,
    value: String,
    delta: String?,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = cs.onSurface.copy(alpha = 0.40f),
            maxLines = 1
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = accent.copy(alpha = 0.92f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!delta.isNullOrBlank()) {
            Text(
                text = delta,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = cs.onSurface.copy(alpha = 0.36f),
                maxLines = 1
            )
        } else {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/** 坑位底部：有目的 / 空转 结构条 */
@Composable
fun PitStructureBar(
    mindfulSeconds: Long,
    idleSeconds: Long,
    modifier: Modifier = Modifier
) {
    val total = (mindfulSeconds + idleSeconds).coerceAtLeast(0L)
    val mindfulW = if (total > 0L) mindfulSeconds.toFloat() / total else 0f
    val idleW = if (total > 0L) idleSeconds.toFloat() / total else 0f
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(track)
    ) {
        if (mindfulW > 0f) {
            Box(
                modifier = Modifier
                    .weight(mindfulW.coerceAtLeast(0.04f))
                    .height(3.dp)
                    .background(LogoGreen.copy(alpha = 0.85f))
            )
        }
        if (idleW > 0f) {
            Box(
                modifier = Modifier
                    .weight(idleW.coerceAtLeast(0.04f))
                    .height(3.dp)
                    .background(Color(0xFFC4891A).copy(alpha = 0.80f))
            )
        }
        if (total <= 0L) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
