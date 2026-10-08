package com.life.mindfulnessapp.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.domain.model.BrowseCasualIntent
import com.life.mindfulnessapp.domain.model.DayEnterKind
import com.life.mindfulnessapp.domain.model.DayReportTimeline
import com.life.mindfulnessapp.domain.model.DayReportTimelineEntry
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 「今天」收据：一行摘要 + 决策脊线（守住 / 搜索 / 写下意图 / 随意浏览）。
 * 可窄到单 App；[onOpenFullDay] 从窄镜回到全日。
 */
@Composable
fun DayReportScreen(
    viewModel: DayReportViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onOpenFullDay: (() -> Unit)? = null,
    /** 回望总览（默认次数 · 按日 · 当天） */
    onOpenOverview: ((dateMs: Long) -> Unit)? = null
) {
    val report by viewModel.report.collectAsState()
    val selectedPage by viewModel.selectedPage.collectAsState()
    val isToday by viewModel.isToday.collectAsState()
    val colors = MaterialTheme.colorScheme
    val palette = rememberReceiptPalette()
    val narrow = report.isNarrow

    LaunchedEffect(Unit) {
        viewModel.trackViewIfNeeded()
    }

    val listItems = remember(report.entries) { buildSpineListItems(report.entries) }

    val dateLabel = remember(report.dateMs) {
        SimpleDateFormat("M 月 d 日", Locale.CHINESE).format(Date(report.dateMs))
    }
    /** 当日叫「今天」；翻日后标题跟日子走，不再假称今天。 */
    val titleLine = remember(report.filterAppName, narrow, isToday, dateLabel) {
        val dayTitle = if (isToday) "今天" else dateLabel
        if (narrow && report.filterAppName.isNotBlank()) "$dayTitle · ${report.filterAppName}"
        else dayTitle
    }
    /** 标题已是日期时，次级只给相对说法，避免同一行读两遍。 */
    val navLabel = remember(report.dateMs, isToday, dateLabel) {
        when {
            isToday -> dateLabel
            isYesterday(report.dateMs) -> "昨天"
            else -> SimpleDateFormat("EEEE", Locale.CHINESE).format(Date(report.dateMs))
        }
    }

    val canGoPrev = selectedPage > 0
    val canGoNext = selectedPage < viewModel.dayCount - 1
    val emptyHint = if (isToday) "还没有门口" else "这一天没有门口"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = colors.onBackground.copy(alpha = 0.55f)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = titleLine,
                color = colors.onBackground,
                fontSize = 22.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Medium
            )
            DayNavRow(
                dateLabel = navLabel,
                canGoPrev = canGoPrev,
                canGoNext = canGoNext,
                onPrevDay = viewModel::goPrevDay,
                onNextDay = viewModel::goNextDay
            )
            Spacer(modifier = Modifier.height(14.dp))
            SummaryLine(report = report, palette = palette)
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.onSurface.copy(alpha = 0.10f))
            )
        }

        if (report.entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emptyHint,
                    color = colors.onSurfaceVariant.copy(alpha = 0.55f),
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 12.dp)
            ) {
                itemsIndexed(
                    items = listItems,
                    key = { index, item ->
                        when (item) {
                            is SpineItem.Quiet -> "q_${item.range}_${item.label}_$index"
                            is SpineItem.Entry -> "e_${item.entry.recordId}"
                        }
                    }
                ) { _, item ->
                    when (item) {
                        is SpineItem.Quiet -> QuietSeamRow(item)
                        is SpineItem.Entry -> SpineEntryRow(
                            entry = item.entry,
                            showHour = item.showHour,
                            hideAppName = narrow,
                            palette = palette
                        )
                    }
                }
            }
        }

        if (onOpenOverview != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 4.dp, bottom = if (narrow && onOpenFullDay != null) 2.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "总览 ›",
                    color = colors.onSurfaceVariant.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    modifier = Modifier.clickable {
                        onOpenOverview(report.dateMs)
                    }
                )
            }
        }

        if (narrow && onOpenFullDay != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenFullDay)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "全部 App 的今天",
                    color = colors.onSurfaceVariant.copy(alpha = 0.55f),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "›",
                    color = colors.onSurfaceVariant.copy(alpha = 0.35f),
                    fontSize = 13.sp
                )
            }
        }
    }
}

// ── 调色 ─────────────────────────────────────────────────────────────────────

private data class ReceiptPalette(
    val hold: Color,
    val search: Color,
    val write: Color,
    val browse: Color,
    val lock: Color
)

@Composable
private fun rememberReceiptPalette(): ReceiptPalette {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return remember(dark) {
        if (dark) {
            ReceiptPalette(
                hold = Color(0xFFC4A35A),
                search = Color(0xFF7EB8C9),
                write = Color(0xFFA8B4C4),
                browse = Color(0xFFC47A9A),
                lock = Color(0xFF6A6E6B)
            )
        } else {
            ReceiptPalette(
                hold = Color(0xFF9A7B3C),
                search = Color(0xFF4A8FA3),
                write = Color(0xFF6B7380),
                browse = Color(0xFFA85A7A),
                lock = Color(0xFF8A8E8B)
            )
        }
    }
}

// ── 顶栏 ─────────────────────────────────────────────────────────────────────

@Composable
private fun DayNavRow(
    dateLabel: String,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit
) {
    val mute = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f)
    val faint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onPrevDay,
            enabled = canGoPrev,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "前一天",
                tint = if (canGoPrev) mute else faint,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = dateLabel,
            color = mute,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        IconButton(
            onClick = onNextDay,
            enabled = canGoNext,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "后一天",
                tint = if (canGoNext) mute else faint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SummaryLine(report: DayReportTimeline, palette: ReceiptPalette) {
    val fog = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
    val ink = MaterialTheme.colorScheme.onBackground
    val zero = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
    val sep = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)

    fun part(label: String, count: Int, accent: Color? = null): androidx.compose.ui.text.AnnotatedString {
        val active = count > 0
        val c = when {
            !active -> zero
            accent != null -> accent
            else -> ink
        }
        return buildAnnotatedString {
            withStyle(SpanStyle(color = if (active) fog else zero)) { append(label) }
            append(" ")
            withStyle(
                SpanStyle(
                    color = c,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                )
            ) { append(count.toString()) }
        }
    }

    Text(
        text = buildAnnotatedString {
            append(part("守住", report.heldCount, palette.hold))
            withStyle(SpanStyle(color = sep)) { append("  ·  ") }
            append(part("搜索", report.searchCount, palette.search))
            withStyle(SpanStyle(color = sep)) { append("  ·  ") }
            append(part("写下", report.writeCount))
            withStyle(SpanStyle(color = sep)) { append("  ·  ") }
            append(part(BrowseCasualIntent.DISPLAY_LABEL, report.browseCount, palette.browse))
        },
        fontSize = 12.sp,
        lineHeight = 18.sp
    )
}

// ── 脊线列表 ─────────────────────────────────────────────────────────────────

private sealed interface SpineItem {
    data class Quiet(val range: String, val label: String) : SpineItem
    data class Entry(val entry: DayReportTimelineEntry, val showHour: Boolean) : SpineItem
}

@Composable
private fun QuietSeamRow(item: SpineItem.Quiet) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.range,
            color = dim,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(68.dp),
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = item.label,
            color = dim,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun SpineEntryRow(
    entry: DayReportTimelineEntry,
    showHour: Boolean,
    hideAppName: Boolean,
    palette: ReceiptPalette
) {
    val colors = MaterialTheme.colorScheme
    val hour = remember(entry.timeMs) { hourOf(entry.timeMs) }
    val clock = remember(entry.timeMs) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(entry.timeMs))
    }
    val kindColor = kindColor(entry, palette)
    val (main, kind) = remember(entry, hideAppName) {
        sceneCopy(entry, hideAppName)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = if (showHour) hour.toString() else "",
            color = colors.onSurfaceVariant.copy(alpha = 0.40f),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .width(28.dp)
                .padding(top = 12.dp),
            maxLines = 1
        )
        Text(
            text = clock,
            color = colors.onSurfaceVariant.copy(alpha = 0.34f),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .width(40.dp)
                .padding(top = 13.dp),
            maxLines = 1
        )
        SpineDot(color = kindColor)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 6.dp, top = 9.dp, bottom = 11.dp)
        ) {
            Text(
                text = main,
                color = colors.onBackground,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 18.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = kind,
                color = kindColor,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
            outcomeLine(entry)?.let { outcome ->
                Text(
                    text = outcome,
                    color = colors.onSurfaceVariant.copy(alpha = 0.50f),
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SpineDot(color: Color) {
    val line = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val bg = MaterialTheme.colorScheme.background
    Box(
        modifier = Modifier
            .width(16.dp)
            .fillMaxHeight(),
        contentAlignment = Alignment.TopCenter
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val x = size.width / 2f
            drawLine(
                color = line,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1.dp.toPx()
            )
        }
        Box(
            modifier = Modifier
                .padding(top = 14.dp)
                .size(13.dp)
                .background(bg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(color, CircleShape)
            )
        }
    }
}

// ── 文案 ─────────────────────────────────────────────────────────────────────

private fun sceneCopy(
    entry: DayReportTimelineEntry,
    hideAppName: Boolean
): Pair<String, String> {
    val appPrefix = if (hideAppName) "" else "${entry.appName} · "
    return when (entry) {
        is DayReportTimelineEntry.HeldBack -> {
            val fact = if (entry.toOwnApp) "到了心锚" else "先不进去了"
            (appPrefix + fact) to "守住"
        }
        is DayReportTimelineEntry.MindfulUse -> {
            val duration = mindfulDurationLabel(entry)
            val fact = when (entry.enterKind) {
                DayEnterKind.SEARCH -> {
                    val q = entry.intentText.trim().trim('「', '」', '"', '“', '”')
                    if (q.isBlank()) "搜索" else "「$q」"
                }
                DayEnterKind.BROWSE -> duration ?: BrowseCasualIntent.DISPLAY_LABEL
                DayEnterKind.WRITE -> buildString {
                    val t = entry.intentText.trim()
                    if (t.isNotBlank()) append(t)
                    if (duration != null) {
                        if (isNotEmpty()) append(" · ")
                        append(duration)
                    }
                    if (isEmpty()) append("写下意图")
                }
            }
            (appPrefix + fact) to entry.enterKind.label
        }
        is DayReportTimelineEntry.PeriodBlocked ->
            (appPrefix + entry.windowLabel) to "时段锁"
        is DayReportTimelineEntry.TimeLockBlocked ->
            (appPrefix + entry.limitLabel) to "时长锁"
    }
}

private fun outcomeLine(entry: DayReportTimelineEntry): String? {
    val use = entry as? DayReportTimelineEntry.MindfulUse ?: return null
    return when {
        use.isOngoing && !use.note.isNullOrBlank() -> use.note
        use.compareLabel != null -> {
            if (!use.note.isNullOrBlank()) "${use.compareLabel}（${use.note}）"
            else use.compareLabel
        }
        !use.note.isNullOrBlank() -> use.note
        else -> null
    }
}

private fun mindfulDurationLabel(entry: DayReportTimelineEntry.MindfulUse): String? = when {
    entry.isOngoing -> "进行中"
    entry.durationSeconds > 0L -> formatReportDuration(entry.durationSeconds)
    else -> null
}

private fun kindColor(entry: DayReportTimelineEntry, palette: ReceiptPalette): Color = when (entry) {
    is DayReportTimelineEntry.HeldBack -> palette.hold
    is DayReportTimelineEntry.MindfulUse -> when (entry.enterKind) {
        DayEnterKind.SEARCH -> palette.search
        DayEnterKind.WRITE -> palette.write
        DayEnterKind.BROWSE -> palette.browse
    }
    is DayReportTimelineEntry.PeriodBlocked,
    is DayReportTimelineEntry.TimeLockBlocked -> palette.lock
}

// ── 列表构建 ─────────────────────────────────────────────────────────────────

private fun buildSpineListItems(entries: List<DayReportTimelineEntry>): List<SpineItem> {
    if (entries.isEmpty()) return emptyList()
    val result = ArrayList<SpineItem>(entries.size + 4)
    var lastPeriod: String? = null
    var lastHour: Int? = null
    var lastTime: Long? = null

    for (entry in entries) {
        val hour = hourOf(entry.timeMs)
        val period = periodLabel(hour)

        if (lastTime != null && entry.timeMs - lastTime >= 2 * 3_600_000L) {
            val from = (lastHour ?: 0) + 1
            val to = hour
            if (to > from) {
                result += SpineItem.Quiet(range = "$from–$to", label = "安静")
            }
        } else if (period != lastPeriod) {
            result += SpineItem.Quiet(range = periodRangeHint(hour), label = period)
        }

        result += SpineItem.Entry(entry, showHour = lastHour != hour)
        lastPeriod = period
        lastHour = hour
        lastTime = entry.timeMs
    }
    return result
}

private fun hourOf(timeMs: Long): Int =
    Calendar.getInstance().apply { timeInMillis = timeMs }.get(Calendar.HOUR_OF_DAY)

private fun isYesterday(dateMs: Long): Boolean {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -1)
    }
    val start = cal.timeInMillis
    return dateMs in start until (start + 24L * 60L * 60L * 1000L)
}

private fun periodLabel(hour: Int): String = when (hour) {
    in 0..4 -> "深夜"
    in 5..7 -> "清晨"
    in 8..11 -> "上午"
    in 12..17 -> "下午"
    else -> "晚上"
}

private fun periodRangeHint(hour: Int): String = when (hour) {
    in 0..4 -> "0–5"
    in 5..7 -> "5–8"
    in 8..11 -> "8–12"
    in 12..17 -> "12–18"
    else -> "18–24"
}

private fun formatReportDuration(seconds: Long): String {
    if (seconds <= 0L) return ""
    val totalMin = seconds / 60L
    return when {
        seconds < 60L -> "${seconds}秒"
        totalMin < 60L -> "${totalMin}分"
        else -> {
            val h = totalMin / 60L
            val m = totalMin % 60L
            if (m == 0L) "${h}时" else "${h}时${m}分"
        }
    }
}
