package com.life.mindfulnessapp.ui.applist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.domain.model.AppUsageOverview
import com.life.mindfulnessapp.domain.model.GuardEvent
import com.life.mindfulnessapp.domain.model.GuardEventKind
import com.life.mindfulnessapp.domain.model.GuardOverview
import com.life.mindfulnessapp.domain.model.PeriodWindowsCodec
import com.life.mindfulnessapp.ui.theme.DangerColor
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.theme.WarningColor
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 概览主数字：`42m` / `1h20m`，与设计稿一致 */
fun formatDashboardMinutes(seconds: Long): String {
    if (seconds <= 0L) return "0m"
    val totalMin = seconds / 60L
    return when {
        totalMin < 60L -> "${totalMin}m"
        totalMin % 60L == 0L -> "${totalMin / 60}h"
        else -> "${totalMin / 60}h${totalMin % 60}m"
    }
}

private val TimeLockOrange = WarningColor
private val PeriodLockRed = DangerColor
private val GuardGreen = LogoGreen
private val DriftOrange = Color(0xFFE8A03A)
private val FarBlue = Color(0xFF5B9BD8)

@Composable
fun AppDetailLockPills(
    appInfo: AppInfo,
    modifier: Modifier = Modifier
) {
    val periodWindows = remember(appInfo.periodWindowsJson) {
        PeriodWindowsCodec.decode(appInfo.periodWindowsJson).filter { it.enabled }
    }
    val pills = buildList {
        if (appInfo.timeLimitEnabled) {
            add(
                LockPillSpec(
                    icon = Icons.Outlined.Timer,
                    text = "时长锁 · ${appInfo.dailyLimitMinutes}m/日",
                    color = TimeLockOrange
                )
            )
        }
        if (appInfo.periodLockEnabled) {
            val range = when {
                periodWindows.isEmpty() -> "已开启"
                periodWindows.size == 1 -> periodWindows.first().label().replace(" – ", "—")
                else -> "${periodWindows.size}段"
            }
            add(
                LockPillSpec(
                    icon = Icons.Outlined.Lock,
                    text = "时段锁 · $range",
                    color = PeriodLockRed
                )
            )
        }
    }
    if (pills.isEmpty()) return
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        pills.forEach { pill ->
            LockPill(spec = pill)
        }
    }
}

private data class LockPillSpec(
    val icon: ImageVector,
    val text: String,
    val color: Color
)

@Composable
private fun LockPill(
    spec: LockPillSpec
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .border(1.dp, spec.color.copy(alpha = 0.55f), shape)
            .background(spec.color.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = spec.icon,
            contentDescription = null,
            tint = spec.color,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = spec.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = spec.color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AppDetailStatGrid(
    overview: AppUsageOverview,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                title = "今日使用",
                value = formatDashboardMinutes(overview.todaySeconds),
                subtitle = "${overview.todayEnterCount} 次会话",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "本周使用",
                value = formatDashboardMinutes(overview.weekSeconds),
                subtitle = "${overview.weekEnterCount} 次会话",
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                title = "累计总时长",
                value = formatDashboardMinutes(overview.totalSeconds),
                subtitle = "共 ${overview.totalEnterCount} 次",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "平均单次",
                value = formatDashboardMinutes(overview.avgSessionSeconds),
                subtitle = "每次会话均值",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(cs.surface)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            color = cs.onSurface.copy(alpha = 0.42f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = cs.onSurface.copy(alpha = 0.94f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            fontSize = 11.sp,
            color = cs.onSurface.copy(alpha = 0.38f)
        )
    }
}

@Composable
fun AppDetailMindfulnessDistribution(
    overview: AppUsageOverview,
    modifier: Modifier = Modifier
) {
    if (overview.reviewedCount <= 0) return
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    val total = overview.reviewedCount.coerceAtLeast(1)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cs.surface)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Text(
            text = "正念评价分布",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurface.copy(alpha = 0.82f)
        )
        Spacer(modifier = Modifier.height(14.dp))
        MindfulnessBarRow(
            icon = Icons.Outlined.CheckCircle,
            label = "没跑偏",
            count = overview.alignedCount,
            total = total,
            color = GuardGreen
        )
        Spacer(modifier = Modifier.height(12.dp))
        MindfulnessBarRow(
            icon = Icons.Outlined.WaterDrop,
            label = "跑偏了",
            count = overview.slightCount,
            total = total,
            color = DriftOrange
        )
        Spacer(modifier = Modifier.height(12.dp))
        MindfulnessBarRow(
            icon = Icons.Outlined.Timer,
            label = "跑远了",
            count = overview.largeCount,
            total = total,
            color = FarBlue
        )
    }
}

@Composable
private fun MindfulnessBarRow(
    icon: ImageVector,
    label: String,
    count: Int,
    total: Int,
    color: Color
) {
    val cs = MaterialTheme.colorScheme
    val ratio = (count.toFloat() / total).coerceIn(0f, 1f)
    val pct = ((count.toFloat() / total) * 100f).toInt()
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = cs.onSurface.copy(alpha = 0.78f),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$count 次 · $pct%",
                fontSize = 12.sp,
                color = cs.onSurface.copy(alpha = 0.42f)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(cs.onSurface.copy(alpha = 0.08f))
        ) {
            if (ratio > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(ratio)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(color)
                )
            }
        }
    }
}

@Composable
fun AppDetailUsageCalendar(
    daySecondsByDayStart: Map<Long, Long>,
    selectedDayStartMs: Long,
    monthStartMs: Long,
    onSelectDay: (Long) -> Unit,
    onShiftMonth: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    val monthLabel = remember(monthStartMs) {
        SimpleDateFormat("yyyy年 M月", Locale.CHINESE).format(Date(monthStartMs))
    }
    val cells = remember(monthStartMs) { buildSundayFirstMonthCells(monthStartMs) }
    val weekdays = listOf("日", "一", "二", "三", "四", "五", "六")
    val todayStart = remember {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }
    val canNextMonth = remember(monthStartMs, todayStart) {
        val next = Calendar.getInstance().apply {
            timeInMillis = monthStartMs
            add(Calendar.MONTH, 1)
        }.timeInMillis
        next <= todayStart
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cs.surface)
            .padding(horizontal = 10.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { onShiftMonth(-1) }) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "上个月",
                    tint = cs.onSurface.copy(alpha = 0.55f)
                )
            }
            Text(
                text = monthLabel,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface.copy(alpha = 0.85f)
            )
            IconButton(
                onClick = { onShiftMonth(1) },
                enabled = canNextMonth
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "下个月",
                    tint = cs.onSurface.copy(alpha = if (canNextMonth) 0.55f else 0.18f)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            weekdays.forEach { w ->
                Text(
                    text = w,
                    fontSize = 11.sp,
                    color = cs.onSurface.copy(alpha = 0.32f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        cells.chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                week.forEach { cell ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(0.92f)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (cell != null) {
                            val seconds = daySecondsByDayStart[cell] ?: 0L
                            val hasUsage = seconds > 0L
                            val selected = cell == selectedDayStartMs
                            val enabled = cell <= todayStart
                            val cellShape = RoundedCornerShape(10.dp)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(cellShape)
                                    .then(
                                        when {
                                            selected && hasUsage ->
                                                Modifier.background(LogoGreen)
                                            selected ->
                                                Modifier.border(1.5.dp, LogoGreen, cellShape)
                                            else -> Modifier
                                        }
                                    )
                                    .then(
                                        if (enabled) Modifier.clickable { onSelectDay(cell) }
                                        else Modifier
                                    )
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = dayOfMonth(cell).toString(),
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = when {
                                        !enabled -> cs.onSurface.copy(alpha = 0.18f)
                                        selected && hasUsage -> Color.White
                                        selected -> LogoGreen
                                        else -> cs.onSurface.copy(alpha = 0.72f)
                                    }
                                )
                                if (hasUsage) {
                                    Text(
                                        text = formatDashboardMinutes(seconds),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = when {
                                            selected -> Color.White.copy(alpha = 0.92f)
                                            else -> LogoGreen.copy(alpha = 0.75f)
                                        },
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 月初对齐到周日的格子；空位为 null */
private fun buildSundayFirstMonthCells(monthStartMs: Long): List<Long?> {
    val cal = Calendar.getInstance().apply { timeInMillis = monthStartMs }
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    // Calendar: SUNDAY=1 … SATURDAY=7 → 转成周日=0
    val firstDow = cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
    val cells = ArrayList<Long?>(42)
    repeat(firstDow) { cells += null }
    repeat(daysInMonth) { i ->
        cells += Calendar.getInstance().apply {
            timeInMillis = monthStartMs
            add(Calendar.DAY_OF_MONTH, i)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    while (cells.size % 7 != 0) cells += null
    return cells
}

private fun dayOfMonth(dayStartMs: Long): Int {
    val cal = Calendar.getInstance().apply { timeInMillis = dayStartMs }
    return cal.get(Calendar.DAY_OF_MONTH)
}

fun startOfMonth(dayStartMs: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = dayStartMs
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

fun startOfToday(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

@Composable
fun AppDetailGuardSummary(
    overview: GuardOverview,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        GuardMetricCard(
            count = overview.gateQuitCount,
            label = "守住次数",
            color = GuardGreen,
            modifier = Modifier.weight(1f)
        )
        GuardMetricCard(
            count = overview.timeLockCount,
            label = "时长锁拦截",
            color = TimeLockOrange,
            modifier = Modifier.weight(1f)
        )
        GuardMetricCard(
            count = overview.periodLockCount,
            label = "时段锁拦截",
            color = PeriodLockRed,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun GuardMetricCard(
    count: Int,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(cs.surface)
            .padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = count.toString(),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = cs.onSurface.copy(alpha = 0.42f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AppDetailGuardEventCard(
    event: GuardEvent,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val (accent, icon) = when (event.kind) {
        GuardEventKind.GateQuit -> GuardGreen to Icons.Outlined.Shield
        GuardEventKind.TimeLock -> TimeLockOrange to Icons.Outlined.Timer
        GuardEventKind.PeriodLock -> PeriodLockRed to Icons.Outlined.Lock
    }
    val time = remember(event.startTime) {
        SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
            .format(Date(event.startTime))
    }
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.18f), shape)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.label,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = time,
                fontSize = 12.sp,
                color = cs.onSurface.copy(alpha = 0.40f)
            )
        }
    }
}
