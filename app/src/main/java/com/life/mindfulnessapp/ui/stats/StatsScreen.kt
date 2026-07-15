package com.life.mindfulnessapp.ui.stats

import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.domain.model.AppDetailStats
import com.life.mindfulnessapp.overlay.formatSeconds
import com.life.mindfulnessapp.ui.applist.AppIcon
import com.life.mindfulnessapp.ui.theme.LogoGreen

private val iosGray   = Color(0xFF8E8E93)
private val iosRed    = Color(0xFFFF3B30)
private val iosOrange = Color(0xFFFF9500)

// ── 统计主入口 ────────────────────────────────────────────────────────────────

@Composable
fun StatsScreen(
    viewModel: StatsViewModel = hiltViewModel(),
    onNavigateToAppDetail: (packageName: String) -> Unit = {},
    onNavigateToAppList: () -> Unit = {},
    onNavigateToDailyReport: () -> Unit = {},
    onNavigateToOverview: () -> Unit = {}
) {
    val todayAppDetails by viewModel.todayAppDetails.collectAsState()
    val cs = MaterialTheme.colorScheme

    val sortedDetails = remember(todayAppDetails) {
        todayAppDetails.sortedByDescending { it.todaySeconds }
    }

    // 0 = 周一 … 6 = 周日
    val todayIdx = remember {
        val cal = java.util.Calendar.getInstance()
        (cal.get(java.util.Calendar.DAY_OF_WEEK) - java.util.Calendar.MONDAY + 7) % 7
    }

    val todayTotal = remember(todayAppDetails) { todayAppDetails.sumOf { it.todaySeconds } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
            .statusBarsPadding()
    ) {
        // 标题行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "统计",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = cs.onBackground,
                letterSpacing = (-0.8).sp
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onNavigateToAppList() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text("管理", fontSize = 15.sp, color = LogoGreen, fontWeight = FontWeight.Medium)
            }
        }

        if (todayAppDetails.isEmpty()) {
            // 无数据时只显示报告入口 + 空态
            Column {
                // 日报 + 总览入口卡片
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    DailyReportEntryCard(
                        todayTotal = todayTotal,
                        onClick = onNavigateToDailyReport
                    )
                }
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    OverviewEntryCard(onClick = onNavigateToOverview)
                }
                EmptyState(onNavigateToAppList, cs)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = 4.dp, bottom = 40.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "summary") {
                    SummaryRow(sortedDetails, cs)
                }
                // 日报入口卡片
                item(key = "daily_report_entry") {
                    DailyReportEntryCard(
                        todayTotal = todayTotal,
                        onClick = onNavigateToDailyReport
                    )
                }
                // 总览入口卡片
                item(key = "overview_entry") {
                    OverviewEntryCard(onClick = onNavigateToOverview)
                }
                items(sortedDetails, key = { it.packageName }) { detail ->
                    AppBarCard(
                        detail      = detail,
                        todayIdx    = todayIdx,
                        cs          = cs,
                        onClick     = { onNavigateToAppDetail(detail.packageName) }
                    )
                }
            }
        }
    }
}

// ── 日报入口卡片 ──────────────────────────────────────────────────────────────

/**
 * 统计页内的「查看今日日报」入口卡片。
 * 展示今日总时长，点击后导航到完整日报页。
 */
@Composable
fun DailyReportEntryCard(
    todayTotal: Long,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val hasData = todayTotal > 0

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
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 左侧图标
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(LogoGreen.copy(alpha = if (hasData) 0.12f else 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Today,
                    contentDescription = null,
                    tint = if (hasData) LogoGreen else LogoGreen.copy(alpha = 0.4f),
                    modifier = Modifier.size(24.dp)
                )
            }

            // 中间文字
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "今日日报",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface
                )
                Text(
                    if (hasData) "今日已用 ${formatSeconds(todayTotal)}"
                    else "今天还没有使用记录",
                    fontSize = 12.sp,
                    color = iosGray
                )
            }

            // 右侧箭头
            Icon(
                androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "查看日报",
                tint = cs.onSurface.copy(alpha = 0.20f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ── 总览入口卡片 ──────────────────────────────────────────────────────────────

/**
 * 统计页内的「历史总览」入口卡片。
 * 点击后导航到总览页（按日期维度展示近30天走势）。
 */
@Composable
fun OverviewEntryCard(onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme

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
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 左侧图标
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(LogoGreen.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = null,
                    tint = LogoGreen,
                    modifier = Modifier.size(22.dp)
                )
            }

            // 中间文字
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "历史总览",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface
                )
                Text(
                    "查看近30天每日使用走势和明细",
                    fontSize = 12.sp,
                    color = iosGray
                )
            }

            // 右侧箭头
            Icon(
                androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "查看总览",
                tint = cs.onSurface.copy(alpha = 0.20f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ── 汇总行 ────────────────────────────────────────────────────────────────────

@Composable
private fun SummaryRow(details: List<AppDetailStats>, cs: ColorScheme) {
    val weekTotal  = remember(details) { details.sumOf { it.weekDailySeconds.sum() } }
    val todayTotal = remember(details) { details.sumOf { it.todaySeconds } }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SummaryItem("本周", if (weekTotal > 0) formatSeconds(weekTotal) else "—",
            if (weekTotal > 0) LogoGreen else cs.onBackground.copy(alpha = 0.2f))
        Box(Modifier.width(0.5.dp).height(22.dp).background(cs.onBackground.copy(alpha = 0.1f)))
        SummaryItem("今日", if (todayTotal > 0) formatSeconds(todayTotal) else "—",
            if (todayTotal > 0) cs.onBackground else cs.onBackground.copy(alpha = 0.2f))
    }
}

@Composable
private fun SummaryItem(label: String, value: String, valueColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 13.sp, color = iosGray)
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            color = valueColor, letterSpacing = (-0.3).sp)
    }
}

// ── App 折线图卡片 ────────────────────────────────────────────────────────────

@Composable
private fun AppBarCard(
    detail   : AppDetailStats,
    todayIdx : Int,
    cs       : ColorScheme,
    onClick  : () -> Unit
) {
    val weekData  = detail.weekDailySeconds          // 长度 7，idx 0 = 周一

    val todaySec  = detail.todaySeconds
    val limitSec  = detail.dailyLimitSeconds
    val progress  = if (limitSec > 0) (todaySec.toFloat() / limitSec).coerceAtMost(1f) else 0f
    val isOver    = limitSec > 0 && todaySec >= limitSec
    val isNear    = limitSec > 0 && progress >= 0.8f && !isOver

    val accent = when {
        isOver -> iosRed
        isNear -> iosOrange
        else   -> LogoGreen
    }

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
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

            // ── 头部：图标 / 名称 / 今日时长 ────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppIconWidget(detail.packageName, 40)

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        detail.appName,
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        when {
                            isOver       -> "已超出限额  ·  ${formatSeconds(limitSec)}"
                            isNear       -> "接近上限  ·  ${formatSeconds(limitSec)}"
                            limitSec > 0 -> "限额 ${formatSeconds(limitSec)}"
                            else         -> "暂无限额"
                        },
                        fontSize = 12.sp,
                        color = if (isOver || isNear) accent.copy(alpha = 0.9f) else iosGray
                    )
                }

                // 今日时长 + 箭头
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            if (todaySec > 0) formatSeconds(todaySec) else "—",
                            fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            color = if (todaySec > 0) accent else cs.onSurface.copy(alpha = 0.18f),
                            letterSpacing = (-0.3).sp
                        )
                        Text("今日", fontSize = 11.sp, color = iosGray)
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = cs.onSurface.copy(alpha = 0.18f),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            // ── 折线图区 ──────────────────────────────────────────────────────
            // 结构：[折线图 60dp] [间距 5dp] [标签区 14dp]
            // 数据重排：今日固定在最右（idx=6），历史数据在左
            val chartH = 60.dp
            val allDayShort = listOf("一", "二", "三", "四", "五", "六", "日")

            // 将 weekData（周一=0…周日=6）重排为「6天前 … 今日」顺序
            // 例：今日=周三(idx=2)，则顺序为：周四,周五,周六,周日,周一,周二,周三
            val orderedIndices = (0 until 7).map { offset ->
                (todayIdx - 6 + offset + 7) % 7
            }
            val orderedData   = orderedIndices.map { weekData[it] }
            val orderedLabels = orderedIndices.map { allDayShort[it] }

            // 有效最大值（仅历史有数据时才参与 Y 轴缩放）
            val chartMax = orderedData.maxOrNull()?.takeIf { it > 0 } ?: 1L

            // 入场动画：0f→1f，控制折线从左到右绘制进度
            val drawProgress by animateFloatAsState(
                targetValue   = 1f,
                animationSpec = tween(600, easing = EaseOutCubic),
                label         = "line_${detail.packageName}"
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                // 折线图 Canvas
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartH)
                ) {
                    val w = size.width
                    val h = size.height
                    val n = orderedData.size       // 7
                    val padH = 8.dp.toPx()         // 上下留白，防止点被裁剪
                    val drawH = h - padH * 2

                    // X 坐标：均匀分布，左边距=padH 防止首个圆点被裁
                    val padW = 8.dp.toPx()
                    val xs = List(n) { i -> padW + (w - padW * 2) * i / (n - 1).toFloat() }

                    // Y 坐标：0数据的天固定到底部基线，有数据按比例映射
                    val ys = orderedData.map { sec ->
                        if (sec <= 0L) padH + drawH          // 底部基线
                        else padH + drawH * (1f - sec.toFloat() / chartMax)
                    }

                    // 计算控制点（Catmull-Rom → 三次贝塞尔），平滑曲线
                    fun ctrlPts(i: Int): Pair<Offset, Offset> {
                        val tension = 0.30f
                        val x0 = xs.getOrElse(i - 1) { xs[i] }
                        val y0 = ys.getOrElse(i - 1) { ys[i] }
                        val x1 = xs[i];      val y1 = ys[i]
                        val x2 = xs.getOrElse(i + 1) { xs[i] }
                        val y2 = ys.getOrElse(i + 1) { ys[i] }
                        val x3 = xs.getOrElse(i + 2) { xs.last() }
                        val y3 = ys.getOrElse(i + 2) { ys.last() }
                        val cp1x = x1 + (x2 - x0) * tension
                        val cp1y = y1 + (y2 - y0) * tension
                        val cp2x = x2 - (x3 - x1) * tension
                        val cp2y = y2 - (y3 - y1) * tension
                        return Offset(cp1x, cp1y) to Offset(cp2x, cp2y)
                    }

                    // 用 clipRect 实现从左到右的入场动画
                    val clipRight = w * drawProgress
                    drawContext.canvas.save()
                    drawContext.canvas.clipRect(
                        androidx.compose.ui.geometry.Rect(0f, 0f, clipRight, h)
                    )

                    // ── 填充区域（渐变） ──────────────────────────────────────
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
                        path  = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(accent.copy(alpha = 0.20f), accent.copy(alpha = 0f)),
                            startY = 0f, endY = h
                        )
                    )

                    // ── 折线本体 ──────────────────────────────────────────────
                    val linePath = Path().apply {
                        moveTo(xs[0], ys[0])
                        for (i in 0 until n - 1) {
                            val (cp1, cp2) = ctrlPts(i)
                            cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, xs[i + 1], ys[i + 1])
                        }
                    }
                    drawPath(
                        path  = linePath,
                        color = accent.copy(alpha = 0.50f),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    drawContext.canvas.restore()

                    // ── 各数据点圆点 ──────────────────────────────────────────
                    orderedData.forEachIndexed { i, sec ->
                        val x = xs[i]; val y = ys[i]
                        if (x > clipRight) return@forEachIndexed
                        val isToday = (i == n - 1)   // 最右边永远是今日
                        when {
                            isToday -> {
                                // 今日：光晕 + 实心圆
                                drawCircle(color = accent.copy(alpha = 0.15f), radius = 8.dp.toPx(), center = Offset(x, y))
                                drawCircle(color = accent,                     radius = 3.5.dp.toPx(), center = Offset(x, y))
                            }
                            sec > 0 -> {
                                // 有数据的历史天：小实心点
                                drawCircle(color = accent.copy(alpha = 0.35f), radius = 2.5.dp.toPx(), center = Offset(x, y))
                            }
                            else -> {
                                // 无数据：底部灰色极小点
                                drawCircle(color = iosGray.copy(alpha = 0.18f), radius = 1.5.dp.toPx(), center = Offset(x, y))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                // 标签区（自适应高度，防止文字被裁剪）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    orderedLabels.forEachIndexed { i, label ->
                        val isToday = (i == orderedLabels.size - 1)
                        Text(
                            label,
                            modifier   = Modifier.weight(1f),
                            fontSize   = 10.sp,
                            color      = if (isToday) accent else iosGray,
                            fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                            textAlign  = TextAlign.Center
                        )
                    }
                }
            }

            // ── 限额进度条（仅有限额时显示）──────────────────────────────────
            if (limitSec > 0) {
                val animProg by animateFloatAsState(
                    targetValue   = progress,
                    animationSpec = tween(600, easing = EaseOutCubic),
                    label         = "prog_${detail.packageName}"
                )
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(cs.onSurface.copy(alpha = 0.07f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animProg)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(accent.copy(alpha = 0.65f), accent)
                                    )
                                )
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            if (isOver) "已超出限额"
                            else "${(progress * 100).toInt()}% 已用",
                            fontSize = 11.sp,
                            color = if (isOver || isNear) accent.copy(alpha = 0.85f) else iosGray
                        )
                        Text("限额 ${formatSeconds(limitSec)}", fontSize = 11.sp, color = iosGray)
                    }
                }
            }
        }
    }
}

// ── 空态 ──────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(onNavigateToAppList: () -> Unit, cs: ColorScheme) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(LogoGreen.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = null,
                    tint = cs.onBackground.copy(alpha = 0.2f),
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(Modifier.height(2.dp))
            Text("暂无监控应用", fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                color = cs.onBackground.copy(alpha = 0.5f))
            Text(
                "添加需要监控的应用后\n这里会显示每个应用的本周使用趋势",
                fontSize = 14.sp, color = iosGray,
                textAlign = TextAlign.Center, lineHeight = 20.sp
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(LogoGreen)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onNavigateToAppList() }
                    .padding(horizontal = 24.dp, vertical = 11.dp)
            ) {
                Text("添加应用", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}

// ── 工具 ──────────────────────────────────────────────────────────────────────

@Composable
private fun AppIconWidget(packageName: String, size: Int) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val icon = remember(packageName) {
        try { ctx.packageManager.getApplicationIcon(packageName) }
        catch (_: android.content.pm.PackageManager.NameNotFoundException) { null }
    }
    AppIcon(drawable = icon, modifier = Modifier.size(size.dp))
}
