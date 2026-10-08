package com.life.mindfulnessapp.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.data.db.dao.HourlyUsage
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.data.repository.UsageRecordRepository
import com.life.mindfulnessapp.domain.model.SessionLimitPolicy
import com.life.mindfulnessapp.ui.applist.buildTodayGlance
import dagger.hilt.android.EntryPointAccessors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class InterceptUsageTab { Today, Last7 }

private data class DayUsagePoint(
    val dayStartMs: Long,
    val label: String,
    val seconds: Long,
    val isToday: Boolean,
)

private data class UsageEventRow(
    val timeLabel: String,
    val title: String,
    val meta: String,
)

private data class UsageLabel(val value: String, val unit: String)

/**
 * 拦截门内「使用量」二级页：今日 / 近七日。
 */
@Composable
internal fun InterceptUsagePanel(
    themeConfig: InterceptThemeConfig,
    appName: String,
    packageName: String,
    todayUsedSeconds: Long,
    dailyLimitMinutes: Int,
    impulseCount: Int,
    todayRecords: List<UsageRecordEntity>,
    onClose: () -> Unit,
) {
    var tab by remember { mutableStateOf(InterceptUsageTab.Today) }
    var loading by remember { mutableStateOf(true) }
    var hourly by remember { mutableStateOf<List<HourlyUsage>>(emptyList()) }
    var dayPoints by remember { mutableStateOf<List<DayUsagePoint>>(emptyList()) }
    var weekTotalSeconds by remember { mutableStateOf(0L) }

    val context = LocalContext.current
    val glance = remember(todayRecords, todayUsedSeconds) {
        val base = buildTodayGlance(
            records = todayRecords,
            requireIntentOnOpen = true,
            liveSessionSeconds = 0L
        )
        if (todayUsedSeconds > 0L) base.copy(totalSeconds = todayUsedSeconds) else base
    }
    val remainingMinutes = remember(dailyLimitMinutes, todayUsedSeconds) {
        if (dailyLimitMinutes <= 0) null
        else SessionLimitPolicy.dailyRemainingMinutes(dailyLimitMinutes, todayUsedSeconds)
    }
    val recentEvents = remember(todayRecords) {
        todayRecords
            .asReversed()
            .filterNot { it.isSeed }
            .take(5)
            .map { toUsageEventRow(it) }
    }

    LaunchedEffect(packageName) {
        loading = true
        val repo = EntryPointAccessors.fromApplication(
            context.applicationContext,
            InterceptOverlayEntryPoint::class.java
        ).usageRecordRepository()
        val loaded = withContext(Dispatchers.IO) {
            loadUsagePanelData(repo, packageName, todayUsedSeconds)
        }
        hourly = loaded.hourly
        dayPoints = loaded.days
        weekTotalSeconds = loaded.weekTotal
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(themeConfig.bgColor)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = themeConfig.textPrimary
                )
            }
            Text(
                text = "使用量",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = themeConfig.textPrimary,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.width(48.dp))
        }

        Text(
            text = appName,
            fontSize = 13.sp,
            color = themeConfig.textTertiary.copy(alpha = 0.85f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            textAlign = TextAlign.Center
        )

        UsageTabBar(
            themeConfig = themeConfig,
            selected = tab,
            onSelect = { tab = it }
        )

        if (loading && tab == InterceptUsageTab.Last7) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(48.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = themeConfig.accentColor,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(28.dp)
                )
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            when (tab) {
                InterceptUsageTab.Today -> TodayUsageBody(
                    themeConfig = themeConfig,
                    usedSeconds = glance.totalSeconds,
                    remainingMinutes = remainingMinutes,
                    dailyLimitMinutes = dailyLimitMinutes,
                    impulseCount = impulseCount.coerceAtLeast(0),
                    enterCount = glance.enterCount,
                    dismissCount = glance.dismissCount,
                    hourly = hourly,
                    events = recentEvents
                )
                InterceptUsageTab.Last7 -> Last7UsageBody(
                    themeConfig = themeConfig,
                    weekTotalSeconds = weekTotalSeconds,
                    days = dayPoints
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun UsageTabBar(
    themeConfig: InterceptThemeConfig,
    selected: InterceptUsageTab,
    onSelect: (InterceptUsageTab) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        listOf(
            InterceptUsageTab.Today to "今日",
            InterceptUsageTab.Last7 to "近七日"
        ).forEach { (id, label) ->
            val on = selected == id
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { onSelect(id) }
                    )
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = label,
                    fontSize = 15.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (on) themeConfig.textPrimary else themeConfig.textTertiary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(
                            if (on) themeConfig.accentColor
                            else themeConfig.dividerColor.copy(alpha = 0.35f)
                        )
                )
            }
        }
    }
}

@Composable
private fun TodayUsageBody(
    themeConfig: InterceptThemeConfig,
    usedSeconds: Long,
    remainingMinutes: Int?,
    dailyLimitMinutes: Int,
    impulseCount: Int,
    enterCount: Int,
    dismissCount: Int,
    hourly: List<HourlyUsage>,
    events: List<UsageEventRow>,
) {
    val usedLabel = formatUsageSeconds(usedSeconds)
    val sub = when {
        remainingMinutes == null && usedSeconds <= 0L -> "今日尚未使用"
        remainingMinutes == null -> "今日已用"
        remainingMinutes <= 0 -> "今日已用 · 额度已用完"
        else -> "今日已用 · 还剩 ${remainingMinutes} 分"
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = usedLabel.value,
                fontSize = 36.sp,
                fontWeight = FontWeight.SemiBold,
                color = themeConfig.textPrimary
            )
            Text(
                text = usedLabel.unit,
                fontSize = 13.sp,
                color = themeConfig.textTertiary,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
        }
        Text(
            text = sub,
            fontSize = 13.sp,
            color = themeConfig.textSecondary.copy(alpha = 0.9f),
            modifier = Modifier.padding(top = 8.dp)
        )

        if (dailyLimitMinutes > 0) {
            val p = (usedSeconds / 60f / dailyLimitMinutes).coerceIn(0f, 1f)
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(themeConfig.dividerColor.copy(alpha = 0.35f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(p)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(themeConfig.accentColor.copy(alpha = 0.9f))
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            UsageMiniStat(themeConfig, impulseCount.toString(), "想打开")
            UsageMiniStat(themeConfig, enterCount.toString(), "打开")
            UsageMiniStat(themeConfig, dismissCount.toString(), "守住")
        }

        if (hourly.any { it.totalSeconds > 0L }) {
            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = themeConfig.dividerColor.copy(alpha = 0.35f))
            Spacer(modifier = Modifier.height(14.dp))
            SectionLabel(themeConfig, "今日节奏")
            Spacer(modifier = Modifier.height(10.dp))
            HourlyBars(themeConfig, hourly)
        }

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(color = themeConfig.dividerColor.copy(alpha = 0.35f))
        Spacer(modifier = Modifier.height(14.dp))
        SectionLabel(themeConfig, "最近几次")
        if (events.isEmpty()) {
            Text(
                text = "今天还没有进出记录",
                fontSize = 13.sp,
                color = themeConfig.textTertiary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            )
        } else {
            events.forEach { ev ->
                EventRow(themeConfig, ev)
            }
        }
    }
}

@Composable
private fun Last7UsageBody(
    themeConfig: InterceptThemeConfig,
    weekTotalSeconds: Long,
    days: List<DayUsagePoint>,
) {
    val total = formatUsageSeconds(weekTotalSeconds)
    val avgSec = if (days.isEmpty()) 0L else weekTotalSeconds / days.size
    val avg = formatUsageSeconds(avgSec)
    val maxSec = days.maxOfOrNull { it.seconds }?.coerceAtLeast(1L) ?: 1L

    Column {
        Spacer(modifier = Modifier.height(12.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = total.value,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = themeConfig.textPrimary
                )
                Text(
                    text = total.unit,
                    fontSize = 13.sp,
                    color = themeConfig.textTertiary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                )
            }
            Text(
                text = "近七日合计 · 日均 ${formatUsageCompact(avgSec)}",
                fontSize = 13.sp,
                color = themeConfig.textSecondary.copy(alpha = 0.9f),
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            days.forEach { day ->
                val h = if (day.seconds <= 0L) 0.04f
                else (day.seconds.toFloat() / maxSec).coerceIn(0.08f, 1f)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(h)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (day.isToday) themeConfig.accentColor.copy(alpha = 0.9f)
                                else themeConfig.dividerColor.copy(alpha = 0.55f)
                            )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = day.label,
                        fontSize = 10.sp,
                        color = if (day.isToday) themeConfig.textSecondary
                        else themeConfig.textTertiary,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(color = themeConfig.dividerColor.copy(alpha = 0.35f))
        Spacer(modifier = Modifier.height(8.dp))
        days.asReversed().forEach { day ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (day.isToday) "今天" else day.fullLabel(),
                    fontSize = 14.sp,
                    color = themeConfig.textPrimary
                )
                Text(
                    text = formatUsageCompact(day.seconds),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = themeConfig.textSecondary
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.UsageMiniStat(
    themeConfig: InterceptThemeConfig,
    value: String,
    label: String,
) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = themeConfig.textPrimary
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = themeConfig.textTertiary,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun SectionLabel(themeConfig: InterceptThemeConfig, text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = themeConfig.textTertiary.copy(alpha = 0.8f),
        letterSpacing = 0.4.sp,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun HourlyBars(themeConfig: InterceptThemeConfig, hourly: List<HourlyUsage>) {
    val byHour = remember(hourly) {
        val map = hourly.associate { it.hour to it.totalSeconds }
        (0..23).map { h -> map[h] ?: 0L }
    }
    val max = byHour.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        byHour.forEach { sec ->
            val h = if (sec <= 0L) 0.06f else (sec.toFloat() / max).coerceIn(0.08f, 1f)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(h)
                    .clip(RoundedCornerShape(2.dp))
                    .background(themeConfig.dividerColor.copy(alpha = 0.5f))
            )
        }
    }
}

@Composable
private fun EventRow(themeConfig: InterceptThemeConfig, ev: UsageEventRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = ev.timeLabel,
            fontSize = 12.sp,
            color = themeConfig.textTertiary,
            modifier = Modifier.width(44.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ev.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = themeConfig.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = ev.meta,
                fontSize = 12.sp,
                color = themeConfig.textTertiary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

private fun formatUsageSeconds(seconds: Long): UsageLabel {
    val totalMinutes = (seconds / 60).coerceAtLeast(0)
    return when {
        totalMinutes < 60 -> UsageLabel(totalMinutes.toString(), "分")
        totalMinutes % 60 == 0L -> UsageLabel((totalMinutes / 60).toString(), "小时")
        else -> UsageLabel(
            "${totalMinutes / 60}小时${totalMinutes % 60}",
            "分"
        )
    }
}

private fun formatUsageCompact(seconds: Long): String {
    val label = formatUsageSeconds(seconds)
    return "${label.value}${label.unit}"
}

private fun DayUsagePoint.fullLabel(): String {
    val fmt = SimpleDateFormat("M月d日", Locale.getDefault())
    return fmt.format(Date(dayStartMs))
}

private fun toUsageEventRow(record: UsageRecordEntity): UsageEventRow {
    val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    val time = timeFmt.format(Date(record.startTime))
    return if (record.isGateQuit) {
        UsageEventRow(timeLabel = time, title = "门外离开", meta = "守住")
    } else {
        val purpose = record.purpose?.trim().orEmpty()
        val title = if (purpose.isNotEmpty()) purpose else "进入"
        UsageEventRow(
            timeLabel = time,
            title = title,
            meta = "进入 · ${formatUsageCompact(record.durationSeconds.coerceAtLeast(0L))}"
        )
    }
}

private data class UsagePanelLoaded(
    val hourly: List<HourlyUsage>,
    val days: List<DayUsagePoint>,
    val weekTotal: Long,
)

private suspend fun loadUsagePanelData(
    repo: UsageRecordRepository,
    packageName: String,
    todayUsedSeconds: Long,
): UsagePanelLoaded {
    val now = System.currentTimeMillis()
    val (todayStart, todayEnd) = UsageRecordRepository.getDayRange(now)
    val hourly = runCatching {
        repo.getHourlyDistribution(packageName, todayStart, todayEnd)
    }.getOrDefault(emptyList())

    val dayMs = 24L * 60 * 60 * 1000
    val cal = Calendar.getInstance().apply {
        timeInMillis = todayStart
        add(Calendar.DAY_OF_YEAR, -6)
    }
    val rangeStart = cal.timeInMillis
    val records = runCatching {
        repo.getCompletedRecordsForAppInRange(packageName, rangeStart, todayEnd)
    }.getOrDefault(emptyList())

    val shortFmt = SimpleDateFormat("E", Locale.CHINA)
    val points = ArrayList<DayUsagePoint>(7)
    var cursor = rangeStart
    while (cursor < todayEnd) {
        val next = cursor + dayMs
        val isToday = cursor == todayStart
        val sec = if (isToday) {
            todayUsedSeconds
        } else {
            records
                .filter { it.startTime in cursor until next }
                .sumOf { it.durationSeconds.coerceAtLeast(0L) }
        }
        val label = if (isToday) {
            "今"
        } else {
            shortFmt.format(Date(cursor)).removePrefix("周").take(1)
        }
        points.add(
            DayUsagePoint(
                dayStartMs = cursor,
                label = label,
                seconds = sec,
                isToday = isToday
            )
        )
        cursor = next
    }
    return UsagePanelLoaded(
        hourly = hourly,
        days = points,
        weekTotal = points.sumOf { it.seconds }
    )
}
