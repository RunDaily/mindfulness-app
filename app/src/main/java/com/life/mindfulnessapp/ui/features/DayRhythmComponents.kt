package com.life.mindfulnessapp.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.domain.model.SystemForegroundSession
import com.life.mindfulnessapp.ui.theme.LogoGreen
import kotlin.math.roundToInt

/** 一天尺子相对可视轨宽的倍数（约 1.5 屏内容宽）。 */
const val DayRhythmWidthFactor = 1.5f

private val HourAxisHeight = 16.dp
private val TrackCorner = 3.dp

data class WeekRhythmDayUi(
    val dayStartMs: Long,
    val weekdayLetter: String,
    val dayOfMonth: String,
    val isToday: Boolean,
    val sessions: List<SystemForegroundSession>
)

/**
 * 整点数字轴：偶时显示 0 2 4 … 22，轨宽 [dayWidth]。
 */
@Composable
fun DayRhythmHourAxis(
    dayWidth: Dp,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val hourW = dayWidth / 24f
    Row(
        modifier = modifier
            .width(dayWidth)
            .height(HourAxisHeight),
        verticalAlignment = Alignment.Bottom
    ) {
        for (h in 0 until 24) {
            val label = if (h % 2 == 0) h.toString() else ""
            Text(
                text = label,
                fontSize = 8.sp,
                color = if (h % 6 == 0) {
                    cs.onSurface.copy(alpha = 0.42f)
                } else {
                    cs.onSurface.copy(alpha = 0.28f)
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.width(hourW)
            )
        }
    }
}

/**
 * 单日 24h 轨：整点竖细线 + 会话色块 + 可选「此刻」线。
 */
@Composable
fun DayRhythmTrack(
    dayStartMs: Long,
    sessions: List<SystemForegroundSession>,
    showNow: Boolean,
    modifier: Modifier = Modifier,
    trackHeight: Dp = 28.dp,
    selected: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier
            .height(trackHeight)
            .clip(RoundedCornerShape(TrackCorner))
            .background(
                if (selected) LogoGreen.copy(alpha = 0.06f)
                else cs.onSurface.copy(alpha = 0.04f)
            )
            .then(
                if (selected) {
                    Modifier.border(1.dp, LogoGreen.copy(alpha = 0.22f), RoundedCornerShape(TrackCorner))
                } else {
                    Modifier
                }
            )
    ) {
        val trackWidthPx = with(density) { maxWidth.toPx() }
        val hourPx = trackWidthPx / 24f
        val dayMs = AppWeekRhythmViewModel.DAY_MS.toFloat()
        for (h in 0 until 24) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = with(density) { (hourPx * h).toDp() })
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(
                        cs.onSurface.copy(alpha = if (h % 6 == 0) 0.14f else 0.08f)
                    )
            )
        }
        sessions.forEach { session ->
            val startFrac = ((session.startMs - dayStartMs) / dayMs).coerceIn(0f, 1f)
            val endFrac = ((session.endMs - dayStartMs) / dayMs).coerceIn(0f, 1f)
            val widthFrac = (endFrac - startFrac).coerceAtLeast(0f)
            if (widthFrac <= 0f) return@forEach
            val minWidthPx = with(density) { 2.dp.toPx() }
            val blockWidthPx = (trackWidthPx * widthFrac).coerceAtLeast(minWidthPx)
            val blockWidth = with(density) { blockWidthPx.toDp() }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = maxWidth * startFrac)
                    .width(blockWidth)
                    .fillMaxHeight(0.64f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(LogoGreen.copy(alpha = if (session.ongoing) 0.55f else 0.88f))
            )
        }
        if (showNow) {
            val nowFrac = AppWeekRhythmViewModel.hourNowFraction()
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = maxWidth * nowFrac)
                    .width(1.5.dp)
                    .fillMaxHeight()
                    .background(cs.onSurface.copy(alpha = 0.55f))
            )
        }
    }
}

/**
 * 详情页「今天」矮条：同尺可横滑，默认滚到此刻附近。
 */
@Composable
fun TodayRhythmStrip(
    dayStartMs: Long,
    sessions: List<SystemForegroundSession>,
    modifier: Modifier = Modifier,
    trackHeight: Dp = 36.dp
) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val dayWidth = maxWidth * DayRhythmWidthFactor
        LaunchedEffect(dayWidth) {
            val px = with(density) { dayWidth.toPx() }
            val now = AppWeekRhythmViewModel.hourNowFraction()
            val target = (px * (now - 0.25f).coerceIn(0f, 0.7f)).roundToInt()
            scroll.scrollTo(target.coerceAtLeast(0))
        }
        Column(modifier = Modifier.horizontalScroll(scroll)) {
            DayRhythmHourAxis(dayWidth = dayWidth)
            Spacer(Modifier.height(4.dp))
            DayRhythmTrack(
                dayStartMs = dayStartMs,
                sessions = sessions,
                showNow = true,
                trackHeight = trackHeight,
                modifier = Modifier.width(dayWidth)
            )
        }
    }
}

/**
 * 周节奏：左粘星期，右滑约 1.5 屏宽的 24h 轨。
 */
@Composable
fun WeekRhythmBoard(
    days: List<WeekRhythmDayUi>,
    selectedDayStartMs: Long?,
    onSelectDay: (Long) -> Unit,
    modifier: Modifier = Modifier,
    rowHeight: Dp = 34.dp,
    trackHeight: Dp = 28.dp
) {
    val cs = MaterialTheme.colorScheme
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val labelW = 32.dp
        val viewportW = (maxWidth - labelW).coerceAtLeast(1.dp)
        val dayWidth = viewportW * DayRhythmWidthFactor
        LaunchedEffect(dayWidth) {
            val px = with(density) { dayWidth.toPx() }
            scroll.scrollTo((px * 0.42f).roundToInt())
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.width(labelW)) {
                Spacer(Modifier.height(HourAxisHeight + 4.dp))
                days.forEach { day ->
                    val selected = day.dayStartMs == selectedDayStartMs
                    Column(
                        modifier = Modifier
                            .height(rowHeight)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelectDay(day.dayStartMs) },
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = day.weekdayLetter,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = when {
                                day.isToday -> LogoGreen
                                selected -> cs.onSurface
                                else -> cs.onSurface.copy(alpha = 0.72f)
                            }
                        )
                        Text(
                            text = day.dayOfMonth,
                            fontSize = 9.sp,
                            color = cs.onSurface.copy(alpha = 0.34f)
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scroll)
            ) {
                DayRhythmHourAxis(dayWidth = dayWidth)
                Spacer(Modifier.height(4.dp))
                days.forEach { day ->
                    val selected = day.dayStartMs == selectedDayStartMs
                    Box(
                        modifier = Modifier
                            .height(rowHeight)
                            .width(dayWidth)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelectDay(day.dayStartMs) },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        DayRhythmTrack(
                            dayStartMs = day.dayStartMs,
                            sessions = day.sessions,
                            showNow = day.isToday,
                            selected = selected,
                            trackHeight = trackHeight,
                            modifier = Modifier.width(dayWidth)
                        )
                    }
                }
            }
        }
    }
}
