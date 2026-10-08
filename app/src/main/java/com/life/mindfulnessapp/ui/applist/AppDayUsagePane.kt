package com.life.mindfulnessapp.ui.applist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.domain.model.AppMapEvent
import com.life.mindfulnessapp.domain.model.AppMapEventKind
import com.life.mindfulnessapp.domain.model.AppRelationInsight
import com.life.mindfulnessapp.domain.model.formatRelationClock
import com.life.mindfulnessapp.domain.model.formatRelationDuration
import com.life.mindfulnessapp.domain.model.hourUsageFillRatio
import com.life.mindfulnessapp.domain.model.overlapsHour
import com.life.mindfulnessapp.ui.theme.HeatmapNeutral
import com.life.mindfulnessapp.ui.theme.LogoGreen

private val ChartBarAreaH = 112.dp
private val ChartLabelH = 18.dp

/**
 * 关系页主体：日期切换 + 24h 触顶柱图 + 空转峰 / 对照兑现 + 可折叠证据。
 */
@Composable
fun AppDayUsageRelationSection(
    insight: AppRelationInsight,
    onShiftDay: (Int) -> Unit,
    onEventClick: (AppMapEvent) -> Unit,
    dayInsight: com.life.mindfulnessapp.domain.model.AppDayInsight? = null,
    showPeriodLockSuggest: Boolean = false,
    onSuggestPeriodLock: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedHour by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(insight.dayStartMs) {
        selectedHour = null
    }

    val visibleEvents = remember(insight.dayEvents, selectedHour, insight.dayStartMs) {
        val hour = selectedHour
        if (hour == null) insight.dayEvents
        else insight.dayEvents.filter { it.overlapsHour(insight.dayStartMs, hour) }
    }

    Column(modifier.fillMaxWidth()) {
        DayNavRow(
            label = insight.dayLabel,
            canGoNext = insight.canGoNext,
            onPrev = { onShiftDay(-1) },
            onNext = { onShiftDay(1) }
        )
        Spacer(modifier = Modifier.height(6.dp))
        DayUsageSummaryLine(
            usageSeconds = insight.dayUsageSeconds,
            eventCount = insight.dayEvents.size,
            selectedHour = selectedHour,
            loading = insight.loadingHourly
        )
        Spacer(modifier = Modifier.height(12.dp))
        AppDayHourlyChart(
            hourlySeconds = insight.hourlySeconds,
            selectedHour = selectedHour,
            isToday = insight.isToday,
            nowMs = insight.nowMs,
            dayStartMs = insight.dayStartMs,
            highlightHour = dayInsight?.idlePeakHour,
            onHourClick = { hour ->
                selectedHour = if (selectedHour == hour) null else hour
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "柱高为该小时使用量 · 触顶 = 用满 60 分钟",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f),
            modifier = Modifier.padding(horizontal = 2.dp)
        )
        if (dayInsight != null) {
            Spacer(modifier = Modifier.height(12.dp))
            com.life.mindfulnessapp.ui.common.IdlePeakHintRow(
                peakHour = dayInsight.idlePeakHour,
                idleSeconds = dayInsight.idleSeconds.today,
                showPeriodLockSuggest = showPeriodLockSuggest,
                onSuggestPeriodLock = onSuggestPeriodLock
            )
            Spacer(modifier = Modifier.height(12.dp))
            com.life.mindfulnessapp.ui.common.CompareFulfillmentCard(insight = dayInsight)
        }
        Spacer(modifier = Modifier.height(18.dp))
        if (visibleEvents.isEmpty()) {
            NarrativeHeader(
                selectedHour = selectedHour,
                visibleCount = 0,
                onClearHour = { selectedHour = null }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when {
                    selectedHour != null -> "这一小时没有意图记录"
                    else -> "这一天还没有意图进入或守住"
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.36f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                textAlign = TextAlign.Center
            )
        } else {
            com.life.mindfulnessapp.ui.common.ExpandableEvidenceBlock(
                totalCount = visibleEvents.size,
                initiallyExpanded = selectedHour != null || visibleEvents.size <= 3,
                collapsedHint = when {
                    selectedHour != null -> "${selectedHour} 点 · ${visibleEvents.size} 条"
                    else -> "带着意图 / 守住 / 直进"
                }
            ) {
                if (selectedHour != null) {
                    NarrativeHeader(
                        selectedHour = selectedHour,
                        visibleCount = visibleEvents.size,
                        onClearHour = { selectedHour = null }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    visibleEvents.forEach { event ->
                        IntentNarrativeRow(
                            event = event,
                            onClick = { onEventClick(event) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayNavRow(
    label: String,
    canGoNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "前一天",
                tint = cs.onSurface.copy(alpha = 0.50f)
            )
        }
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurface.copy(alpha = 0.88f),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        IconButton(onClick = onNext, enabled = canGoNext, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "后一天",
                tint = cs.onSurface.copy(alpha = if (canGoNext) 0.50f else 0.18f)
            )
        }
    }
}

@Composable
private fun DayUsageSummaryLine(
    usageSeconds: Long,
    eventCount: Int,
    selectedHour: Int?,
    loading: Boolean
) {
    val cs = MaterialTheme.colorScheme
    val usageText = if (loading && usageSeconds <= 0L) {
        "读取用量…"
    } else {
        formatRelationDuration(usageSeconds)
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            usageText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = cs.onSurface.copy(alpha = 0.82f)
        )
        Text(
            "  ·  意图 ${eventCount} 次",
            fontSize = 13.sp,
            color = cs.onSurface.copy(alpha = 0.40f)
        )
        if (selectedHour != null) {
            Text(
                "  ·  ${selectedHour}时",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = LogoGreen.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
fun AppDayHourlyChart(
    hourlySeconds: LongArray,
    selectedHour: Int?,
    isToday: Boolean,
    nowMs: Long,
    dayStartMs: Long,
    onHourClick: (Int) -> Unit,
    highlightHour: Int? = null,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val nowHour = remember(nowMs, dayStartMs, isToday) {
        if (!isToday) null
        else {
            val elapsed = (nowMs - dayStartMs).coerceAtLeast(0L)
            (elapsed / 3_600_000L).toInt().coerceIn(0, 23)
        }
    }
    val hours = remember(hourlySeconds) {
        LongArray(24) { i -> hourlySeconds.getOrElse(i) { 0L } }
    }
    val peakAccent = Color(0xFFC4891A)

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(ChartBarAreaH),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            for (hour in 0..23) {
                val seconds = hours[hour]
                val fill = hourUsageFillRatio(seconds)
                val selected = selectedHour == hour
                val isPeak = highlightHour == hour
                val dimOthers = selectedHour != null && !selected
                val top = when {
                    isPeak -> peakAccent.copy(alpha = if (dimOthers) 0.40f else 0.95f)
                    else -> LogoGreen.copy(alpha = if (dimOthers) 0.35f else 0.95f)
                }
                val bottom = when {
                    isPeak -> peakAccent.copy(alpha = if (dimOthers) 0.20f else 0.55f)
                    else -> LogoGreen.copy(alpha = if (dimOthers) 0.18f else 0.55f)
                }
                val barBrush = Brush.verticalGradient(colors = listOf(top, bottom))
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onHourClick(hour) },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    // 触顶参考线
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .align(Alignment.TopCenter)
                            .background(cs.onSurface.copy(alpha = 0.06f))
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(0.86f)
                            .fillMaxHeight(fill.coerceAtLeast(if (seconds > 0L) 0.04f else 0.015f))
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(barBrush)
                            .then(
                                if (selected || isPeak) {
                                    Modifier.border(
                                        1.dp,
                                        if (isPeak && !selected) peakAccent.copy(alpha = 0.55f)
                                        else Color.White.copy(alpha = 0.45f),
                                        RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                                    )
                                } else Modifier
                            )
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(ChartLabelH),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            for (hour in 0..23) {
                val emphasize = hour % 6 == 0 || hour == nowHour || hour == selectedHour
                Text(
                    text = hour.toString(),
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal,
                    color = when {
                        hour == selectedHour -> LogoGreen.copy(alpha = 0.90f)
                        hour == nowHour -> LogoGreen.copy(alpha = 0.70f)
                        hour % 6 == 0 -> cs.onSurface.copy(alpha = 0.42f)
                        else -> cs.onSurface.copy(alpha = 0.22f)
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun NarrativeHeader(
    selectedHour: Int?,
    visibleCount: Int,
    onClearHour: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (selectedHour != null) "${selectedHour}时的意图" else "这一天的意图",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurface.copy(alpha = 0.72f),
            modifier = Modifier.weight(1f)
        )
        if (selectedHour != null) {
            Text(
                text = "看全天",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = LogoGreen.copy(alpha = 0.90f),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onClearHour)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        } else if (visibleCount > 0) {
            Text(
                text = "${visibleCount} 条",
                fontSize = 12.sp,
                color = cs.onSurface.copy(alpha = 0.34f)
            )
        }
    }
}

@Composable
private fun IntentNarrativeRow(
    event: AppMapEvent,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val accent = when (event.kind) {
        AppMapEventKind.GateQuit -> HeatmapNeutral
        AppMapEventKind.MindfulEnter -> LogoGreen
        AppMapEventKind.Enter -> LogoGreen.copy(alpha = 0.55f)
    }
    val title = when {
        event.isGateQuit -> "守住离开"
        !event.purpose.isNullOrBlank() -> "「${event.purpose}」"
        else -> "进入"
    }
    val sub = buildString {
        append(formatRelationClock(event.startTime))
        if (!event.isGateQuit && event.durationSeconds > 0L) {
            append(" · ")
            append(formatRelationDuration(event.durationSeconds))
        }
        when (event.kind) {
            AppMapEventKind.MindfulEnter -> append(" · 意图进入")
            AppMapEventKind.Enter -> append(" · 进入")
            AppMapEventKind.GateQuit -> Unit
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(cs.onSurface.copy(alpha = 0.035f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent.copy(alpha = 0.90f))
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface.copy(alpha = 0.90f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                sub,
                fontSize = 12.sp,
                color = cs.onSurface.copy(alpha = 0.38f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun MapEventDetailSheet(
    event: AppMapEvent,
    onOpenHistory: () -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val kindLabel = when (event.kind) {
        AppMapEventKind.MindfulEnter -> "意图进入"
        AppMapEventKind.GateQuit -> "守住离开"
        AppMapEventKind.Enter -> "进入"
    }
    val accent = when (event.kind) {
        AppMapEventKind.GateQuit -> HeatmapNeutral
        else -> LogoGreen
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 28.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(36.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(cs.onSurface.copy(alpha = 0.12f))
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = kindLabel,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = accent
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (!event.purpose.isNullOrBlank()) {
            Text(
                text = event.purpose,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface.copy(alpha = 0.92f),
                lineHeight = 26.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
        } else if (event.isGateQuit) {
            Text(
                text = "在门外停住了",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface.copy(alpha = 0.88f)
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
        Text(
            text = buildString {
                append(formatRelationClock(event.startTime))
                if (!event.isGateQuit && event.durationSeconds > 0L) {
                    append(" · ")
                    append(formatRelationDuration(event.durationSeconds))
                }
            },
            fontSize = 14.sp,
            color = cs.onSurface.copy(alpha = 0.48f)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "在使用记录中查看",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = LogoGreen,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, LogoGreen.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                .clickable(onClick = onOpenHistory)
                .padding(vertical = 12.dp),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "关闭",
            fontSize = 14.sp,
            color = cs.onSurface.copy(alpha = 0.40f),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onDismiss)
                .padding(vertical = 10.dp),
            textAlign = TextAlign.Center
        )
    }
}
