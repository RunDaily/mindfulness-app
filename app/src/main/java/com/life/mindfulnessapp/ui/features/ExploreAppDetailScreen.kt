package com.life.mindfulnessapp.ui.features

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.domain.model.AppWeeklySystemUsage
import com.life.mindfulnessapp.domain.model.ExploreChartMetric
import com.life.mindfulnessapp.domain.model.SystemForegroundSession
import com.life.mindfulnessapp.domain.model.SystemUsageDayDetail
import com.life.mindfulnessapp.ui.features.TodayRhythmStrip
import com.life.mindfulnessapp.ui.applist.AppIcon
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.theme.LogoGreenDeep
import com.life.mindfulnessapp.ui.theme.WarningColor
import com.life.mindfulnessapp.util.AppUsageFormat
import java.util.Calendar

private data class DayPeriodDef(
    val label: String,
    val startHour: Int,
    val endHour: Int
)

private val DayPeriods = listOf(
    DayPeriodDef("早晨", 0, 6),
    DayPeriodDef("上午", 6, 12),
    DayPeriodDef("下午", 12, 18),
    DayPeriodDef("晚上", 18, 24)
)

private data class ExploreChartStyle(
    val barBrush: Brush,
    val trackColor: Color,
    val accent: Color,
    val selectedColumnBg: Color
)

@Composable
private fun exploreChartStyle(metric: ExploreChartMetric): ExploreChartStyle {
    return when (metric) {
        ExploreChartMetric.Duration -> ExploreChartStyle(
            barBrush = Brush.verticalGradient(
                listOf(LogoGreen.copy(alpha = 0.55f), LogoGreen, LogoGreenDeep)
            ),
            trackColor = LogoGreen.copy(alpha = 0.08f),
            accent = LogoGreen,
            selectedColumnBg = LogoGreen.copy(alpha = 0.08f)
        )
        ExploreChartMetric.Launches -> ExploreChartStyle(
            barBrush = Brush.verticalGradient(
                listOf(
                    WarningColor.copy(alpha = 0.5f),
                    WarningColor,
                    WarningColor.copy(red = 0.82f, green = 0.58f)
                )
            ),
            trackColor = WarningColor.copy(alpha = 0.10f),
            accent = WarningColor,
            selectedColumnBg = WarningColor.copy(alpha = 0.10f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreAppDetailScreen(
    viewModel: ExploreAppDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToWeekRhythm: () -> Unit = {}
) {
    val ui by viewModel.ui.collectAsState()
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = ui.app?.appName ?: "近 7 日",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "今日")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        containerColor = cs.background
    ) { padding ->
        when {
            !ui.hasUsagePermission -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("需要使用情况访问", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "授权后可查看近 7 日统计与每次前台记录。",
                        fontSize = 13.sp,
                        color = cs.onSurface.copy(alpha = 0.45f),
                        textAlign = TextAlign.Center
                    )
                    TextButton(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }
                    ) {
                        Text("去授权", color = LogoGreen, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            else -> {
                val chartDays = ui.completeDays
                val maxChartValue = when (ui.chartMetric) {
                    ExploreChartMetric.Duration ->
                        chartDays.maxOfOrNull { it.totalSeconds }?.coerceAtLeast(1L) ?: 1L
                    ExploreChartMetric.Launches ->
                        chartDays.maxOfOrNull { it.openCount }?.coerceAtLeast(1) ?: 1
                }.toLong()
                val selected = ui.selectedDay
                val chartStyle = exploreChartStyle(ui.chartMetric)

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(bottom = 40.dp)
                ) {
                    item(key = "hero") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 8.dp)
                        ) {
                            ExploreAppDetailHero(app = ui.app)
                            Spacer(modifier = Modifier.height(16.dp))
                            val todayDay = chartDays.lastOrNull { it.isToday } ?: chartDays.lastOrNull()
                            if (todayDay != null) {
                                Text(
                                    text = "今天 · 何时在用",
                                    fontSize = 11.sp,
                                    color = cs.onSurface.copy(alpha = 0.42f),
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                TodayRhythmStrip(
                                    dayStartMs = todayDay.dayStartMs,
                                    sessions = todayDay.sessions
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                ExploreWeekRhythmEntry(onClick = onNavigateToWeekRhythm)
                                Spacer(modifier = Modifier.height(14.dp))
                            }
                            ExploreMetricToggle(
                                metric = ui.chartMetric,
                                onSelect = viewModel::setChartMetric
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            if (ui.chartLoading) {
                                ExploreWeekChartSkeleton()
                            } else {
                                ExploreWeekBarChart(
                                    days = chartDays,
                                    weekUsage = ui.weekUsage,
                                    metric = ui.chartMetric,
                                    style = chartStyle,
                                    maxValue = maxChartValue,
                                    selectedDayStartMs = ui.selectedDayStartMs,
                                    onSelectDay = viewModel::selectDay
                                )
                                if (todayDay == null) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    ExploreWeekRhythmEntry(onClick = onNavigateToWeekRhythm)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "按系统用量统计 · 加入规则后改用心锚记录",
                                    fontSize = 11.sp,
                                    color = cs.onSurface.copy(alpha = 0.36f),
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }
                    }

                    if (selected != null) {
                        item(key = "day_${selected.dayStartMs}") {
                            ExploreSelectedDaySection(
                                day = selected,
                                metric = ui.chartMetric
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExploreAppDetailHero(app: com.life.mindfulnessapp.domain.model.AppInfo?) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppIcon(drawable = app?.icon, modifier = Modifier.size(48.dp))
        Text(
            text = app?.appName.orEmpty(),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = cs.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ExploreWeekRhythmEntry(onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "一周节奏",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = cs.onSurface.copy(alpha = 0.78f)
            )
            Text(
                text = "7 天 · 凌晨到晚上",
                fontSize = 11.sp,
                color = cs.onSurface.copy(alpha = 0.36f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = cs.onSurface.copy(alpha = 0.28f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ExploreMetricToggle(
    metric: ExploreChartMetric,
    onSelect: (ExploreChartMetric) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val durationStyle = exploreChartStyle(ExploreChartMetric.Duration)
    val launchesStyle = exploreChartStyle(ExploreChartMetric.Launches)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(cs.onSurface.copy(alpha = 0.05f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ExploreMetricChip(
            label = "使用时长",
            selected = metric == ExploreChartMetric.Duration,
            accent = durationStyle.accent,
            onClick = { onSelect(ExploreChartMetric.Duration) }
        )
        ExploreMetricChip(
            label = "打开次数",
            selected = metric == ExploreChartMetric.Launches,
            accent = launchesStyle.accent,
            onClick = { onSelect(ExploreChartMetric.Launches) }
        )
    }
}

@Composable
private fun ExploreMetricChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) accent.copy(alpha = 0.12f) else cs.surface.copy(alpha = 0f)
            )
            .then(
                if (selected) {
                    Modifier.border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(10.dp))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) accent else cs.onSurface.copy(alpha = 0.42f)
        )
    }
}

@Composable
private fun ExploreWeekBarChart(
    days: List<SystemUsageDayDetail>,
    weekUsage: AppWeeklySystemUsage,
    metric: ExploreChartMetric,
    style: ExploreChartStyle,
    maxValue: Long,
    selectedDayStartMs: Long?,
    onSelectDay: (Long) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val barAreaHeight = 128.dp
    val primary = when (metric) {
        ExploreChartMetric.Duration ->
            AppUsageFormat.totalDurationCompact(weekUsage.totalSeconds)
        ExploreChartMetric.Launches -> "${weekUsage.totalLaunches}次"
    }
    val secondary = when (metric) {
        ExploreChartMetric.Duration ->
            "打开 ${weekUsage.totalLaunches} 次 · 日均 ${AppUsageFormat.totalDurationCompact(weekUsage.avgDailySeconds)}"
        ExploreChartMetric.Launches ->
            "时长 ${AppUsageFormat.totalDurationCompact(weekUsage.totalSeconds)} · 日均 ${weekUsage.avgDailyLaunches} 次"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cs.surface)
            .border(1.dp, cs.onSurface.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = "近 7 日",
                    fontSize = 10.sp,
                    color = cs.onSurface.copy(alpha = 0.36f)
                )
                Text(
                    text = primary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = style.accent,
                    letterSpacing = (-0.4).sp,
                    lineHeight = 28.sp
                )
            }
            Text(
                text = secondary,
                fontSize = 11.sp,
                color = cs.onSurface.copy(alpha = 0.42f),
                textAlign = TextAlign.End,
                lineHeight = 15.sp,
                modifier = Modifier.widthIn(max = 160.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            days.forEach { day ->
                ExploreChartBarColumn(
                    day = day,
                    metric = metric,
                    style = style,
                    maxValue = maxValue,
                    barAreaHeight = barAreaHeight,
                    selected = day.dayStartMs == selectedDayStartMs,
                    modifier = Modifier.weight(1f),
                    onSelect = { onSelectDay(day.dayStartMs) }
                )
            }
        }
    }
}

@Composable
private fun ExploreChartBarColumn(
    day: SystemUsageDayDetail,
    metric: ExploreChartMetric,
    style: ExploreChartStyle,
    maxValue: Long,
    barAreaHeight: Dp,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val value = when (metric) {
        ExploreChartMetric.Duration -> day.totalSeconds.toFloat()
        ExploreChartMetric.Launches -> day.openCount.toFloat()
    }
    val ratio = if (maxValue <= 0L) 0f else (value / maxValue).coerceIn(0f, 1f)
    val barFraction by animateFloatAsState(
        targetValue = ratio,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "bar_${day.dayStartMs}_$metric"
    )
    val valueLabel = when (metric) {
        ExploreChartMetric.Duration -> {
            if (day.totalSeconds > 0L) {
                AppUsageFormat.totalDurationCompact(day.totalSeconds)
            } else {
                "0"
            }
        }
        ExploreChartMetric.Launches -> {
            if (day.openCount > 0) "${day.openCount}次" else "0"
        }
    }
    val columnBg = if (selected) style.selectedColumnBg else Color.Transparent
    val barShape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(columnBg)
            .clickable(onClick = onSelect)
            .padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = valueLabel,
            fontSize = 9.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) {
                style.accent
            } else {
                cs.onSurface.copy(alpha = if (value > 0f) 0.52f else 0.28f)
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = 11.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barAreaHeight),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .fillMaxHeight()
                    .clip(barShape)
                    .background(style.trackColor)
            )
            val barHeight = (barAreaHeight * barFraction)
                .coerceAtLeast(if (value > 0f) 4.dp else 0.dp)
            if (value > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .height(barHeight)
                        .clip(barShape)
                        .background(style.barBrush)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = day.chartDateLabel,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) {
                cs.onSurface.copy(alpha = 0.78f)
            } else {
                cs.onSurface.copy(alpha = 0.34f)
            }
        )
    }
}

@Composable
private fun ExploreWeekChartSkeleton() {
    val cs = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "chart_skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "chart_skeleton_alpha"
    )
    val bone = cs.onSurface.copy(alpha = 0.08f * alpha + 0.06f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cs.surface)
            .border(1.dp, cs.onSurface.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
            .padding(horizontal = 10.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            repeat(7) { i ->
                val h = listOf(0.35f, 0.55f, 0.42f, 0.68f, 0.5f, 0.38f, 0.62f)[i]
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .width(16.dp)
                            .height(8.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(bone)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.72f)
                            .height((128 * h).dp)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(bone)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .height(8.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(bone)
                    )
                }
            }
        }
    }
}

@Composable
private fun ExploreSelectedDaySection(
    day: SystemUsageDayDetail,
    metric: ExploreChartMetric
) {
    val cs = MaterialTheme.colorScheme
    val displaySessions = remember(day, metric) {
        when (metric) {
            ExploreChartMetric.Launches -> day.sessions.filter { it.countsAsOpen }
            ExploreChartMetric.Duration -> day.sessions
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = when (metric) {
                ExploreChartMetric.Duration -> {
                    val used = AppUsageFormat.totalDurationCompact(day.totalSeconds)
                    "$used · 打开 ${day.openCount} 次"
                }
                ExploreChartMetric.Launches ->
                    "打开 ${day.openCount} 次 · ${AppUsageFormat.totalDurationCompact(day.totalSeconds)}"
            },
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurface,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )
        Text(
            text = day.label,
            fontSize = 11.sp,
            color = cs.onSurface.copy(alpha = 0.36f),
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
        )
        DayPeriods.forEach { period ->
            val sessions = sessionsInPeriod(displaySessions, period.startHour, period.endHour)
            if (sessions.isEmpty()) return@forEach
            ExplorePeriodCard(
                periodLabel = period.label,
                sessions = sessions
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (displaySessions.isEmpty()) {
            Text(
                text = "这一天没有前台记录",
                fontSize = 13.sp,
                color = cs.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.padding(vertical = 24.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ExplorePeriodCard(
    periodLabel: String,
    sessions: List<SystemForegroundSession>
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(cs.surface)
            .border(1.dp, cs.onSurface.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
            .padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = periodLabel,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = cs.onSurface.copy(alpha = 0.38f),
            modifier = Modifier
                .width(28.dp)
                .padding(top = 4.dp),
            lineHeight = 14.sp
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sessions.forEach { session ->
                val start = AppUsageFormat.clockHm(session.startMs)
                val end = if (session.ongoing) "现在" else AppUsageFormat.clockHm(session.endMs)
                val duration = AppUsageFormat.sessionDuration(session.durationSeconds)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$start – $end",
                        fontSize = 13.sp,
                        color = cs.onSurface.copy(alpha = 0.78f)
                    )
                    Text(
                        text = duration,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (session.ongoing) LogoGreen else cs.onSurface.copy(alpha = 0.65f)
                    )
                }
            }
        }
    }
}

private fun sessionsInPeriod(
    sessions: List<SystemForegroundSession>,
    startHour: Int,
    endHour: Int
): List<SystemForegroundSession> {
    val cal = Calendar.getInstance()
    return sessions.filter { s ->
        cal.timeInMillis = s.startMs
        val h = cal.get(Calendar.HOUR_OF_DAY)
        h in startHour until endHour
    }.sortedBy { it.startMs }
}
