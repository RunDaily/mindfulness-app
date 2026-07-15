package com.life.mindfulnessapp.ui.stats

import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.domain.model.OverviewDaySummary
import com.life.mindfulnessapp.overlay.formatSeconds
import com.life.mindfulnessapp.ui.applist.AppIcon
import com.life.mindfulnessapp.ui.theme.LogoGreen
import java.text.SimpleDateFormat
import java.util.*

// ── 颜色常量 ─────────────────────────────────────────────────────────────────
private val OvGray   = Color(0xFF8E8E93)
private val OvOrange = Color(0xFFFF9500)

// ── App 图标颜色调色板（用于多 App 横条色标） ─────────────────────────────────
private val appBarColors = listOf(
    Color(0xFF34C759), // 绿
    Color(0xFF007AFF), // 蓝
    Color(0xFFFF9500), // 橙
    Color(0xFFAF52DE), // 紫
    Color(0xFF5AC8FA), // 青
    Color(0xFFFF3B30), // 红
    Color(0xFFFFD166), // 黄
    Color(0xFF30B0C7), // 蓝绿
)

// ══════════════════════════════════════════════════════════════════════════════
//  总览主页
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun OverviewScreen(
    viewModel: StatsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToDayReport: (offset: Int) -> Unit = {}
) {
    val days       by viewModel.overviewDays.collectAsState()
    val loading    by viewModel.overviewLoading.collectAsState()
    val cs = MaterialTheme.colorScheme

    LaunchedEffect(Unit) {
        viewModel.loadOverview(30)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
            .statusBarsPadding()
    ) {
        // ── 顶部导航栏 ──────────────────────────────────────────────────────
        OverviewTopBar(onBack = onNavigateBack, cs = cs)

        when {
            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = LogoGreen,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            days.isEmpty() -> {
                OverviewEmptyState(cs = cs)
            }
            else -> {
                // 过滤掉全为0的尾部（最旧的若干天），但至少保留有数据的范围
                val activeDays = days.dropWhile { it.totalSeconds == 0L }
                val displayDays = if (activeDays.isEmpty()) days.takeLast(7) else activeDays

                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp,
                        top = 4.dp, bottom = 48.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // ── 趋势折线卡 ──────────────────────────────────────────
                    item(key = "trend_chart") {
                        OverviewTrendCard(days = displayDays, cs = cs)
                    }
                    // ── 汇总统计行 ──────────────────────────────────────────
                    item(key = "summary_chips") {
                        OverviewSummaryChips(days = displayDays, cs = cs)
                    }
                    // ── 日期列表（最新在前）──────────────────────────────────
                    item(key = "list_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = LogoGreen,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    "每日明细",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = cs.onBackground
                                )
                            }
                            Text(
                                "近 ${displayDays.size} 天",
                                fontSize = 11.sp,
                                color = OvGray
                            )
                        }
                    }
                    val reversedDays = displayDays.reversed()
                    itemsIndexed(
                        items = reversedDays,
                        key = { _, day -> day.dateKey }
                    ) { index, day ->
                        // offset: 今天=0, 昨天=1, ...
                        val offset = index
                        DayCard(
                            day = day,
                            allDaysMax = displayDays.maxOf { it.totalSeconds }.coerceAtLeast(1L),
                            isToday = (index == 0),
                            onClick = { onNavigateToDayReport(-offset) },
                            cs = cs
                        )
                    }
                }
            }
        }
    }
}

// ── 顶部栏 ───────────────────────────────────────────────────────────────────

@Composable
private fun OverviewTopBar(onBack: () -> Unit, cs: ColorScheme) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = cs.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            "使用总览",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = cs.onBackground,
            letterSpacing = (-0.5).sp,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp)
        )
        Text(
            "近30天",
            fontSize = 12.sp,
            color = OvGray,
            modifier = Modifier.padding(end = 16.dp)
        )
    }
}

// ── 总体趋势折线图卡片 ────────────────────────────────────────────────────────

@Composable
private fun OverviewTrendCard(days: List<OverviewDaySummary>, cs: ColorScheme) {
    val chartMax = days.maxOf { it.totalSeconds }.coerceAtLeast(1L)

    // 找最高点和最低点（有数据的天）
    val nonZeroDays = days.filter { it.totalSeconds > 0 }
    val peakDay    = nonZeroDays.maxByOrNull { it.totalSeconds }
    val troughDay  = nonZeroDays.minByOrNull { it.totalSeconds }

    val drawProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(800, easing = EaseOutCubic),
        label = "overviewTrend"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(cs.surface)
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.ShowChart,
                        contentDescription = null,
                        tint = LogoGreen,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        "使用走势",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface
                    )
                }
                // 今日总时长
                val todayDay = days.lastOrNull()
                if (todayDay != null && todayDay.totalSeconds > 0) {
                    Text(
                        "今日 ${formatSeconds(todayDay.totalSeconds)}",
                        fontSize = 12.sp,
                        color = LogoGreen,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 折线图 Canvas
            val chartH = 90.dp
            val n = days.size

            Column(modifier = Modifier.fillMaxWidth()) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartH)
                ) {
                    val w = size.width
                    val h = size.height
                    val padH = 10.dp.toPx()
                    val drawH = h - padH * 2
                    val padW = 8.dp.toPx()

                    val xs = if (n > 1) {
                        List(n) { i -> padW + (w - padW * 2) * i / (n - 1).toFloat() }
                    } else {
                        listOf(w / 2f)
                    }

                    val ys = days.map { day ->
                        if (day.totalSeconds <= 0L) padH + drawH
                        else padH + drawH * (1f - day.totalSeconds.toFloat() / chartMax)
                    }

                    // Catmull-Rom 控制点
                    fun ctrlPts(i: Int): Pair<Offset, Offset> {
                        val tension = 0.28f
                        val x0 = xs.getOrElse(i - 1) { xs[i] }
                        val y0 = ys.getOrElse(i - 1) { ys[i] }
                        val x1 = xs[i]; val y1 = ys[i]
                        val x2 = xs.getOrElse(i + 1) { xs[i] }
                        val y2 = ys.getOrElse(i + 1) { ys[i] }
                        val x3 = xs.getOrElse(i + 2) { xs.last() }
                        val y3 = ys.getOrElse(i + 2) { ys.last() }
                        return Offset(x1 + (x2 - x0) * tension, y1 + (y2 - y0) * tension) to
                               Offset(x2 - (x3 - x1) * tension, y2 - (y3 - y1) * tension)
                    }

                    val clipRight = w * drawProgress
                    drawContext.canvas.save()
                    drawContext.canvas.clipRect(
                        androidx.compose.ui.geometry.Rect(0f, 0f, clipRight, h)
                    )

                    if (n > 1) {
                        // 填充渐变
                        val fillPath = Path().apply {
                            moveTo(xs[0], ys[0])
                            for (i in 0 until n - 1) {
                                val (cp1, cp2) = ctrlPts(i)
                                cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, xs[i + 1], ys[i + 1])
                            }
                            lineTo(xs.last(), h)
                            lineTo(xs[0], h)
                            close()
                        }
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    LogoGreen.copy(alpha = 0.22f),
                                    LogoGreen.copy(alpha = 0f)
                                ),
                                startY = 0f, endY = h
                            )
                        )

                        // 折线本体
                        val linePath = Path().apply {
                            moveTo(xs[0], ys[0])
                            for (i in 0 until n - 1) {
                                val (cp1, cp2) = ctrlPts(i)
                                cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, xs[i + 1], ys[i + 1])
                            }
                        }
                        drawPath(
                            path = linePath,
                            color = LogoGreen.copy(alpha = 0.65f),
                            style = Stroke(
                                width = 2.2.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }

                    drawContext.canvas.restore()

                    // 数据点
                    days.forEachIndexed { i, day ->
                        val x = xs[i]; val y = ys[i]
                        if (x > clipRight) return@forEachIndexed
                        val isToday  = (i == n - 1)
                        val isPeak   = peakDay != null && day.dateKey == peakDay.dateKey
                        val isTrough = troughDay != null && day.dateKey == troughDay.dateKey &&
                                       troughDay.dateKey != peakDay?.dateKey

                        when {
                            isToday -> {
                                drawCircle(color = LogoGreen.copy(alpha = 0.15f), radius = 9.dp.toPx(), center = Offset(x, y))
                                drawCircle(color = LogoGreen, radius = 4.dp.toPx(), center = Offset(x, y))
                            }
                            isPeak -> {
                                drawCircle(color = OvOrange.copy(alpha = 0.18f), radius = 6.dp.toPx(), center = Offset(x, y))
                                drawCircle(color = OvOrange, radius = 3.dp.toPx(), center = Offset(x, y))
                            }
                            isTrough && day.totalSeconds > 0 -> {
                                drawCircle(color = LogoGreen.copy(alpha = 0.25f), radius = 3.dp.toPx(), center = Offset(x, y))
                            }
                            day.totalSeconds > 0 -> {
                                drawCircle(color = LogoGreen.copy(alpha = 0.30f), radius = 2.5.dp.toPx(), center = Offset(x, y))
                            }
                            else -> {
                                drawCircle(color = OvGray.copy(alpha = 0.15f), radius = 1.5.dp.toPx(), center = Offset(x, y))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // X 轴日期标签（仅显示首/中/末）
                if (n >= 2) {
                    val sdfLabel = SimpleDateFormat("M/d", Locale.getDefault())
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            sdfLabel.format(Date(days.first().dayStartMs)),
                            fontSize = 10.sp, color = OvGray
                        )
                        // 中间若有足够点则显示中间日期
                        if (n >= 7) {
                            val midIdx = n / 2
                            Text(
                                sdfLabel.format(Date(days[midIdx].dayStartMs)),
                                fontSize = 10.sp, color = OvGray
                            )
                        }
                        Text(
                            "今天",
                            fontSize = 10.sp,
                            color = LogoGreen,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // 峰值/谷值注解行
            if (peakDay != null || troughDay != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (peakDay != null) {
                        TrendAnnotation(
                            label = "最高",
                            value = formatSeconds(peakDay.totalSeconds),
                            dateStr = SimpleDateFormat("M月d日", Locale.CHINESE).format(Date(peakDay.dayStartMs)),
                            color = OvOrange,
                            modifier = Modifier.weight(1f),
                            cs = cs
                        )
                    }
                    if (troughDay != null && troughDay.dateKey != peakDay?.dateKey) {
                        TrendAnnotation(
                            label = "最低",
                            value = formatSeconds(troughDay.totalSeconds),
                            dateStr = SimpleDateFormat("M月d日", Locale.CHINESE).format(Date(troughDay.dayStartMs)),
                            color = LogoGreen,
                            modifier = Modifier.weight(1f),
                            cs = cs
                        )
                    }
                    // 平均
                    val nonZeroTotal = nonZeroDays.sumOf { it.totalSeconds }
                    if (nonZeroDays.isNotEmpty()) {
                        val avg = nonZeroTotal / nonZeroDays.size
                        TrendAnnotation(
                            label = "日均",
                            value = formatSeconds(avg),
                            dateStr = "${nonZeroDays.size} 天有记录",
                            color = OvGray,
                            modifier = Modifier.weight(1f),
                            cs = cs
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendAnnotation(
    label: String,
    value: String,
    dateStr: String,
    color: Color,
    modifier: Modifier = Modifier,
    cs: ColorScheme
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, fontSize = 10.sp, color = color.copy(alpha = 0.8f))
        Text(
            value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            letterSpacing = (-0.3).sp
        )
        Text(dateStr, fontSize = 9.sp, color = OvGray)
    }
}

// ── 汇总统计 Chips ────────────────────────────────────────────────────────────

@Composable
private fun OverviewSummaryChips(days: List<OverviewDaySummary>, cs: ColorScheme) {
    val nonZeroDays = days.filter { it.totalSeconds > 0 }
    val totalSec    = nonZeroDays.sumOf { it.totalSeconds }
    val totalDismiss= nonZeroDays.sumOf { it.dismissCount }
    val totalMindful= nonZeroDays.sumOf { it.mindfulCount }
    val totalOpens  = nonZeroDays.sumOf { it.totalOpenCount }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SummaryChip(
            value = formatSeconds(totalSec),
            label = "总时长",
            color = LogoGreen,
            modifier = Modifier.weight(1f),
            cs = cs
        )
        SummaryChip(
            value = "${totalOpens}次",
            label = "打开次数",
            color = cs.onSurface,
            modifier = Modifier.weight(1f),
            cs = cs
        )
        SummaryChip(
            value = "${totalDismiss}次",
            label = "克制",
            color = Color(0xFFFFD166),
            modifier = Modifier.weight(1f),
            cs = cs
        )
        SummaryChip(
            value = "${totalMindful}次",
            label = "有意识",
            color = Color(0xFF5AC8FA),
            modifier = Modifier.weight(1f),
            cs = cs
        )
    }
}

@Composable
private fun SummaryChip(
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    cs: ColorScheme
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(cs.surface)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            letterSpacing = (-0.3).sp,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
        Text(
            label,
            fontSize = 9.sp,
            color = OvGray,
            textAlign = TextAlign.Center
        )
    }
}

// ── 单日卡片 ──────────────────────────────────────────────────────────────────

@Composable
private fun DayCard(
    day: OverviewDaySummary,
    allDaysMax: Long,
    isToday: Boolean,
    onClick: () -> Unit,
    cs: ColorScheme
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current

    // 日期格式
    val dayLabel = remember(day.dateKey, isToday) {
        when {
            isToday -> "今天"
            else -> {
                val cal = Calendar.getInstance().apply { timeInMillis = day.dayStartMs }
                val today = Calendar.getInstance()
                val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                when {
                    cal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) &&
                    cal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) -> "昨天"
                    else -> SimpleDateFormat("M月d日", Locale.CHINESE).format(Date(day.dayStartMs))
                }
            }
        }
    }
    val weekDayLabel = remember(day.dateKey) {
        SimpleDateFormat("EEE", Locale.CHINESE).format(Date(day.dayStartMs))
    }

    // 整体进度（相对所有天的最大值）
    val barRatio by animateFloatAsState(
        targetValue = if (allDaysMax > 0) (day.totalSeconds.toFloat() / allDaysMax).coerceIn(0f, 1f)
                      else 0f,
        animationSpec = tween(500, easing = EaseOutCubic),
        label = "dayBar_${day.dateKey}"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cs.surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // ── 头部：日期 + 总时长 ─────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 左：日期标签
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 日期徽章
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isToday) LogoGreen.copy(alpha = 0.15f)
                                else cs.onSurface.copy(alpha = 0.06f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                SimpleDateFormat("d", Locale.getDefault())
                                    .format(Date(day.dayStartMs)),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isToday) LogoGreen else cs.onSurface.copy(alpha = 0.85f),
                                lineHeight = 16.sp
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            dayLabel,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isToday) LogoGreen else cs.onSurface
                        )
                        Text(
                            "$weekDayLabel · ${SimpleDateFormat("M/d", Locale.getDefault()).format(Date(day.dayStartMs))}",
                            fontSize = 11.sp,
                            color = OvGray
                        )
                    }
                }

                // 右：总时长 + 打开次数
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (day.totalSeconds > 0) {
                        Text(
                            formatSeconds(day.totalSeconds),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isToday) LogoGreen else cs.onSurface,
                            letterSpacing = (-0.4).sp
                        )
                        Text(
                            "打开 ${day.totalOpenCount} 次",
                            fontSize = 11.sp,
                            color = OvGray
                        )
                    } else {
                        Text(
                            "无记录",
                            fontSize = 14.sp,
                            color = cs.onSurface.copy(alpha = 0.2f)
                        )
                    }
                }
            }

            // 无数据时不展示后续内容
            if (day.totalSeconds <= 0L) return@Column

            // ── 整体时长占比进度条 ──────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(cs.onSurface.copy(alpha = 0.06f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(barRatio)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    LogoGreen.copy(alpha = 0.55f),
                                    if (isToday) LogoGreen else LogoGreen.copy(alpha = 0.80f)
                                )
                            )
                        )
                )
            }

            // ── 各 App 使用时长条 ──────────────────────────────────────────
            if (day.appBreakdown.isNotEmpty()) {
                val totalSec = day.totalSeconds.coerceAtLeast(1L)
                val topApps = day.appBreakdown.entries
                    .sortedByDescending { it.value }
                    .take(5)   // 最多显示5个 App

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    topApps.forEachIndexed { colorIdx, (pkg, sec) ->
                        val appName = day.appNames[pkg] ?: pkg.substringAfterLast(".")
                        val ratio   = sec.toFloat() / totalSec
                        val color   = appBarColors[colorIdx % appBarColors.size]

                        val icon = remember(pkg) {
                            try { ctx.packageManager.getApplicationIcon(pkg) }
                            catch (_: android.content.pm.PackageManager.NameNotFoundException) { null }
                        }

                        val animRatio by animateFloatAsState(
                            targetValue = ratio.coerceIn(0f, 1f),
                            animationSpec = tween(450 + colorIdx * 50, easing = EaseOutCubic),
                            label = "appBar_${day.dateKey}_$pkg"
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // App 图标（小）
                            AppIcon(
                                drawable = icon,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(5.dp))
                            )

                            // App 名
                            Text(
                                appName,
                                fontSize = 11.sp,
                                color = cs.onSurface.copy(alpha = 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(72.dp)
                            )

                            // 进度条
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(cs.onSurface.copy(alpha = 0.05f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(animRatio)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(color.copy(alpha = 0.65f), color)
                                            )
                                        )
                                )
                            }

                            // 时长
                            Text(
                                formatSeconds(sec),
                                fontSize = 11.sp,
                                color = color,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.width(44.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }

                    // 如果超过5个 App，显示"还有 N 个应用"
                    val extraCount = day.appBreakdown.size - topApps.size
                    if (extraCount > 0) {
                        Text(
                            "还有 $extraCount 个应用",
                            fontSize = 10.sp,
                            color = OvGray.copy(alpha = 0.7f),
                            modifier = Modifier.padding(start = 28.dp)
                        )
                    }
                }
            }

            // ── 底部小徽章（克制 / 有意识）──────────────────────────────────
            if (day.dismissCount > 0 || day.mindfulCount > 0 || day.peakHour != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (day.dismissCount > 0) {
                        DayBadge(
                            label = "克制 ${day.dismissCount}",
                            color = Color(0xFFFFD166)
                        )
                    }
                    if (day.mindfulCount > 0) {
                        DayBadge(
                            label = "有意识 ${day.mindfulCount}",
                            color = LogoGreen
                        )
                    }
                    if (day.peakHour != null) {
                        val peakLabel = when (day.peakHour) {
                            in 0..5  -> "凌晨活跃"
                            in 6..11 -> "上午 ${day.peakHour}点"
                            in 12..17 -> "下午 ${day.peakHour}点"
                            else     -> "晚上 ${day.peakHour}点"
                        }
                        DayBadge(label = peakLabel, color = OvGray)
                    }
                    Spacer(Modifier.weight(1f))
                    // 右箭头提示可点击
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "查看日报",
                        tint = cs.onSurface.copy(alpha = 0.15f),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DayBadge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            label,
            fontSize = 10.sp,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

// ── 空态 ─────────────────────────────────────────────────────────────────────

@Composable
private fun OverviewEmptyState(cs: ColorScheme) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 48.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(LogoGreen.copy(alpha = 0.07f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Timeline,
                    contentDescription = null,
                    tint = cs.onBackground.copy(alpha = 0.2f),
                    modifier = Modifier.size(38.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "暂无历史数据",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = cs.onBackground.copy(alpha = 0.5f)
            )
            Text(
                "使用一段时间后\n这里会显示每天的使用情况走势",
                fontSize = 13.sp,
                color = OvGray,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }
    }
}
