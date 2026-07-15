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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.domain.model.DailyAppStat
import com.life.mindfulnessapp.domain.model.DailyReportData
import com.life.mindfulnessapp.overlay.formatSeconds
import com.life.mindfulnessapp.ui.applist.AppIcon
import com.life.mindfulnessapp.ui.theme.LogoGreen
import java.text.SimpleDateFormat
import java.util.*

// ── 颜色常量 ─────────────────────────────────────────────────────────────────
private val DrGray   = Color(0xFF8E8E93)
private val DrRed    = Color(0xFFFF3B30)
private val DrOrange = Color(0xFFFF9500)
private val DrBlue   = Color(0xFF007AFF)
private val DrTeal   = Color(0xFF5AC8FA)
private val DrGold   = Color(0xFFFFD166)

// ── 日报主页 ─────────────────────────────────────────────────────────────────

@Composable
fun DailyReportScreen(
    viewModel: StatsViewModel = hiltViewModel(),
    initialDayOffset: Int = 0,
    onNavigateBack: () -> Unit
) {
    val report  by viewModel.dailyReport.collectAsState()
    val loading by viewModel.dailyReportLoading.collectAsState()
    val offset  by viewModel.reportDayOffset.collectAsState()
    val cs = MaterialTheme.colorScheme

    LaunchedEffect(initialDayOffset) {
        viewModel.loadDailyReport(initialDayOffset)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
            .statusBarsPadding()
    ) {
        // ── 顶部导航栏（含日期标题 + 切换箭头）──────────────────────────────
        DailyTopBar(
            offset   = offset,
            report   = report,
            onBack   = onNavigateBack,
            onPrev   = { viewModel.dailyReportPrevDay() },
            onNext   = { viewModel.dailyReportNextDay() },
            cs       = cs
        )

        Spacer(Modifier.height(4.dp))

        // ── 内容区 ─────────────────────────────────────────────────────────
        when {
            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = LogoGreen,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            report == null || report!!.totalSeconds == 0L -> {
                DailyReportEmptyState(offset = offset, cs = cs)
            }
            else -> {
                val data = report!!
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp,
                        top = 0.dp, bottom = 52.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. 大数字 Hero
                    item(key = "hero") {
                        DailyHeroCard(data = data, cs = cs)
                    }
                    // 2. 四维快览
                    item(key = "overview") {
                        DailyOverviewRow(data = data, cs = cs)
                    }
                    // 3. App 排行
                    if (data.appSummaries.isNotEmpty()) {
                        item(key = "app_header") {
                            DailyAppSectionHeader(
                                total = data.totalSeconds,
                                count = data.appSummaries.size,
                                cs    = cs
                            )
                        }
                        items(data.appSummaries, key = { it.packageName }) { app ->
                            DailyAppRow(
                                app          = app,
                                totalSeconds = data.totalSeconds,
                                cs           = cs
                            )
                        }
                    }
                    // 5. 今日洞察（文字版，无多余图表）
                    item(key = "insights") {
                        DailyInsightCard(data = data, cs = cs)
                    }
                }
            }
        }
    }
}

// ── 顶部栏（日期标题 + 左右切换）─────────────────────────────────────────────

@Composable
private fun DailyTopBar(
    offset: Int,
    report: DailyReportData?,
    onBack: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    cs: ColorScheme
) {
    // 主标题：今天 / 昨天 / M月d日
    val mainTitle = remember(report, offset) {
        when (offset) {
            0    -> "今天"
            -1   -> "昨天"
            -2   -> "前天"
            else -> if (report != null) {
                SimpleDateFormat("M月d日", Locale.CHINESE).format(Date(report.dayStartMs))
            } else "${-offset} 天前"
        }
    }
    // 副标题：星期 + 完整日期
    val subTitle = remember(report, offset) {
        if (report != null) {
            SimpleDateFormat("yyyy年M月d日  EEEE", Locale.CHINESE).format(Date(report.dayStartMs))
        } else ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 返回按钮
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = cs.onBackground.copy(alpha = 0.65f),
                modifier = Modifier.size(22.dp)
            )
        }

        // 中间：日期标题（可点击区域，左右箭头夹住）
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // 左箭头
            IconButton(
                onClick  = onPrev,
                enabled  = offset > -29,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "前一天",
                    tint = if (offset > -29) cs.onBackground.copy(alpha = 0.55f)
                           else cs.onBackground.copy(alpha = 0.12f),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(6.dp))

            // 标题文字
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    mainTitle,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = cs.onBackground,
                    letterSpacing = (-0.4).sp
                )
                if (subTitle.isNotEmpty()) {
                    Text(
                        subTitle,
                        fontSize = 11.sp,
                        color = DrGray,
                        letterSpacing = 0.1.sp
                    )
                }
            }

            Spacer(Modifier.width(6.dp))

            // 右箭头
            IconButton(
                onClick  = onNext,
                enabled  = offset < 0,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "后一天",
                    tint = if (offset < 0) cs.onBackground.copy(alpha = 0.55f)
                           else cs.onBackground.copy(alpha = 0.12f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // 占位（与返回按钮对称）
        Spacer(Modifier.size(48.dp))
    }
}

// ── Hero 卡片（大数字 + 昨日对比）─────────────────────────────────────────────

@Composable
private fun DailyHeroCard(data: DailyReportData, cs: ColorScheme) {
    val pct    = data.vsPrevDayPercent
    val isUp   = (pct ?: 0f) > 0.02f
    val isDown = (pct ?: 0f) < -0.02f
    val trendColor = when {
        isUp   -> DrRed
        isDown -> LogoGreen
        else   -> DrGray
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        LogoGreen.copy(alpha = 0.10f),
                        LogoGreen.copy(alpha = 0.03f)
                    )
                )
            )
            .padding(horizontal = 22.dp, vertical = 22.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ── 第一行：总时长大字 + 趋势 ──────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        formatSeconds(data.totalSeconds),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        color = LogoGreen,
                        letterSpacing = (-1.5).sp,
                        lineHeight = 44.sp
                    )
                    Text(
                        "屏幕使用时长",
                        fontSize = 11.sp,
                        color = cs.onBackground.copy(alpha = 0.3f),
                        letterSpacing = 0.5.sp
                    )
                }

                // 趋势角标
                if (pct != null) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = when {
                                    isUp   -> Icons.AutoMirrored.Filled.TrendingUp
                                    isDown -> Icons.AutoMirrored.Filled.TrendingDown
                                    else   -> Icons.AutoMirrored.Filled.TrendingFlat
                                },
                                contentDescription = null,
                                tint = trendColor,
                                modifier = Modifier.size(16.dp)
                            )
                            val pctInt = (pct * 100).toInt()
                            val prefix = if (pctInt > 0) "+" else ""
                            Text(
                                "$prefix$pctInt%",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = trendColor,
                                letterSpacing = (-0.2).sp
                            )
                        }
                        Text(
                            "较前日",
                            fontSize = 10.sp,
                            color = DrGray
                        )
                    }
                }
            }

            // ── 分割线 ──────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(cs.onBackground.copy(alpha = 0.08f))
            )

            // ── 第二行：三个次要指标 ─────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeroStat(
                    value = "${data.totalOpenCount}",
                    label = "次打开",
                    color = cs.onBackground
                )
                HeroStatDivider(cs)
                HeroStat(
                    value = if (data.totalOpenCount > 0)
                        formatSeconds(data.totalSeconds / data.totalOpenCount)
                    else "—",
                    label = "平均时长",
                    color = cs.onBackground
                )
                HeroStatDivider(cs)
                HeroStat(
                    value = data.peakTimeLabel ?: "—",
                    label = "高峰时段",
                    color = cs.onBackground
                )
            }
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            value,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            letterSpacing = (-0.3).sp,
            maxLines = 1
        )
        Text(
            label,
            fontSize = 10.sp,
            color = DrGray,
            letterSpacing = 0.2.sp
        )
    }
}

@Composable
private fun HeroStatDivider(cs: ColorScheme) {
    Box(
        modifier = Modifier
            .width(0.5.dp)
            .height(28.dp)
            .background(cs.onBackground.copy(alpha = 0.10f))
    )
}

// ── 四维统计快览 ──────────────────────────────────────────────────────────────

@Composable
private fun DailyOverviewRow(data: DailyReportData, cs: ColorScheme) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        DailyOverviewChip(
            value      = if (data.mindfulCount > 0) "${data.mindfulCount}" else "—",
            label      = "有意识",
            valueColor = if (data.mindfulCount > 0) LogoGreen
                         else cs.onSurface.copy(alpha = 0.18f),
            iconRes    = Icons.Default.Psychology,
            iconColor  = if (data.mindfulCount > 0) LogoGreen.copy(alpha = 0.7f)
                         else cs.onSurface.copy(alpha = 0.15f),
            modifier   = Modifier.weight(1f),
            cs         = cs
        )
        DailyOverviewChip(
            value      = if (data.dismissCount > 0) "${data.dismissCount}" else "—",
            label      = "克制",
            valueColor = if (data.dismissCount > 0) DrGold
                         else cs.onSurface.copy(alpha = 0.18f),
            iconRes    = Icons.Default.Shield,
            iconColor  = if (data.dismissCount > 0) DrGold.copy(alpha = 0.8f)
                         else cs.onSurface.copy(alpha = 0.15f),
            modifier   = Modifier.weight(1f),
            cs         = cs
        )
        DailyOverviewChip(
            value      = "${data.appSummaries.size}",
            label      = "活跃应用",
            valueColor = cs.onSurface,
            iconRes    = Icons.Default.Apps,
            iconColor  = DrBlue.copy(alpha = 0.65f),
            modifier   = Modifier.weight(1f),
            cs         = cs
        )
    }
}

@Composable
private fun DailyOverviewChip(
    value: String,
    label: String,
    valueColor: Color,
    iconRes: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier,
    cs: ColorScheme
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(cs.surface)
            .padding(horizontal = 10.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(iconRes, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
        Text(
            value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            letterSpacing = (-0.3).sp
        )
        Text(label, fontSize = 10.sp, color = DrGray)
    }
}

// ── App 排行区头部 ────────────────────────────────────────────────────────────

@Composable
private fun DailyAppSectionHeader(
    total: Long,
    count: Int,
    cs: ColorScheme
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Default.Apps,
                contentDescription = null,
                tint = LogoGreen,
                modifier = Modifier.size(14.dp)
            )
            Text(
                "应用明细",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onBackground
            )
        }
        Text(
            "$count 个应用",
            fontSize = 11.sp,
            color = DrGray
        )
    }
}

// ── 单个 App 行 ───────────────────────────────────────────────────────────────

@Composable
private fun DailyAppRow(
    app: DailyAppStat,
    totalSeconds: Long,
    cs: ColorScheme
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val icon = remember(app.packageName) {
        try { ctx.packageManager.getApplicationIcon(app.packageName) }
        catch (_: android.content.pm.PackageManager.NameNotFoundException) { null }
    }
    val shareRatio = if (totalSeconds > 0) app.totalSeconds.toFloat() / totalSeconds else 0f
    val isOver = app.dailyLimitSeconds > 0 && app.totalSeconds >= app.dailyLimitSeconds
    val isNear = app.dailyLimitSeconds > 0 && app.usagePercent >= 0.8f && !isOver
    val accent = when {
        isOver -> DrRed
        isNear -> DrOrange
        else   -> LogoGreen
    }
    val barRatio by animateFloatAsState(
        targetValue   = shareRatio.coerceIn(0f, 1f),
        animationSpec = tween(500, easing = EaseOutCubic),
        label         = "dailyAppBar_${app.packageName}"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cs.surface)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // ── 主行 ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppIcon(drawable = icon, modifier = Modifier.size(38.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        app.appName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text("${app.openCount}次打开", fontSize = 11.sp, color = DrGray)
                        if (app.avgSessionSeconds > 0) {
                            Text("·", fontSize = 10.sp, color = DrGray.copy(alpha = 0.4f))
                            Text(
                                "均 ${formatSeconds(app.avgSessionSeconds)}",
                                fontSize = 11.sp,
                                color = DrGray
                            )
                        }
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        formatSeconds(app.totalSeconds),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        letterSpacing = (-0.4).sp
                    )
                    Text(
                        "${(shareRatio * 100).toInt()}%",
                        fontSize = 11.sp,
                        color = DrGray
                    )
                }
            }

            // ── 占比进度条 ────────────────────────────────────────────────
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
                            Brush.horizontalGradient(listOf(accent.copy(alpha = 0.6f), accent))
                        )
                )
            }

            // ── 徽章行（仅在有数据时显示）────────────────────────────────
            if (app.dismissCount > 0 || app.mindfulOpenCount > 0 || app.longestSessionSeconds > 60) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (app.dismissCount > 0) {
                        AppStatBadge("克制 ${app.dismissCount}次", DrGold, 0.12f)
                    }
                    if (app.mindfulOpenCount > 0) {
                        AppStatBadge("有意识 ${app.mindfulOpenCount}次", LogoGreen, 0.10f)
                    }
                    if (app.longestSessionSeconds > 60) {
                        AppStatBadge("最长 ${formatSeconds(app.longestSessionSeconds)}", DrGray, 0.08f)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppStatBadge(label: String, color: Color, bgAlpha: Float) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = bgAlpha))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(label, fontSize = 10.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

// ── 今日洞察卡片（纯文字，清晰易读）─────────────────────────────────────────

/**
 * 将克制、有意识、前日对比等信息整合为语义化的文字条目，
 * 不再重复用图表展示数字，而是提供有意义的解读文本。
 */
@Composable
private fun DailyInsightCard(data: DailyReportData, cs: ColorScheme) {
    val pct    = data.vsPrevDayPercent
    val isUp   = (pct ?: 0f) > 0.02f
    val isDown = (pct ?: 0f) < -0.02f

    // 构建洞察条目列表
    val insights = remember(data) {
        buildList {
            // 与前日对比
            if (pct != null) {
                val pctInt = kotlin.math.abs((pct * 100).toInt())
                val delta  = kotlin.math.abs(data.totalSeconds - data.prevDayTotalSeconds)
                when {
                    isDown -> add(
                        InsightItem(
                            icon  = "📉",
                            color = LogoGreen,
                            text  = "比昨天少用了 ${formatSeconds(delta)}，减少了 $pctInt%"
                        )
                    )
                    isUp -> add(
                        InsightItem(
                            icon  = "📈",
                            color = DrRed,
                            text  = "比昨天多用了 ${formatSeconds(delta)}，增加了 $pctInt%"
                        )
                    )
                    else -> add(
                        InsightItem(
                            icon  = "↔️",
                            color = DrGray,
                            text  = "与昨天使用时长相差不大"
                        )
                    )
                }
            }

            // 克制退出
            when {
                data.dismissCount == 0 -> add(
                    InsightItem(
                        icon  = "🛡️",
                        color = DrGray,
                        text  = "今天没有触发克制退出"
                    )
                )
                data.dismissCount in 1..2 -> add(
                    InsightItem(
                        icon  = "🛡️",
                        color = DrGold,
                        text  = "克制退出 ${data.dismissCount} 次，保持了一点自律"
                    )
                )
                else -> add(
                    InsightItem(
                        icon  = "🛡️",
                        color = DrGold,
                        text  = "克制退出 ${data.dismissCount} 次，自律表现出色！"
                    )
                )
            }

            // 有意识打开
            val mindfulRatioPct = if (data.totalOpenCount > 0)
                (data.mindfulCount.toFloat() / data.totalOpenCount * 100).toInt() else 0
            when {
                data.mindfulCount == 0 -> add(
                    InsightItem(
                        icon  = "🧠",
                        color = DrGray,
                        text  = "今天没有一次主动填写打开目的"
                    )
                )
                mindfulRatioPct >= 50 -> add(
                    InsightItem(
                        icon  = "🧠",
                        color = LogoGreen,
                        text  = "${data.mindfulCount} 次有意识打开，占比 $mindfulRatioPct%，非常专注"
                    )
                )
                else -> add(
                    InsightItem(
                        icon  = "🧠",
                        color = DrTeal,
                        text  = "${data.mindfulCount} 次有意识打开，占比 $mindfulRatioPct%"
                    )
                )
            }

            // 高峰时段
            if (data.peakHour != null) {
                val period = when (data.peakHour) {
                    in 0..5   -> "凌晨"
                    in 6..11  -> "上午"
                    in 12..17 -> "下午"
                    else      -> "晚上"
                }
                add(
                    InsightItem(
                        icon  = "⏰",
                        color = DrOrange,
                        text  = "手机使用高峰在${period} ${data.peakHour}:00—${data.peakHour + 1}:00"
                    )
                )
            }
        }
    }

    DailySectionCard(
        title = "今日洞察",
        icon  = Icons.Default.Lightbulb,
        cs    = cs
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            insights.forEachIndexed { idx, item ->
                InsightRow(item = item, cs = cs)
                if (idx < insights.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(cs.onSurface.copy(alpha = 0.05f))
                    )
                }
            }
        }
    }
}

private data class InsightItem(
    val icon: String,
    val color: Color,
    val text: String
)

@Composable
private fun InsightRow(item: InsightItem, cs: ColorScheme) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(item.icon, fontSize = 18.sp)
        Text(
            item.text,
            fontSize = 13.sp,
            color = item.color,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

// ── 通用 Section 卡片 ─────────────────────────────────────────────────────────

@Composable
private fun DailySectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    cs: ColorScheme,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cs.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 14.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = LogoGreen,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface
                )
            }
            content()
        }
    }
}

// ── 空态 ─────────────────────────────────────────────────────────────────────

@Composable
private fun DailyReportEmptyState(offset: Int, cs: ColorScheme) {
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
                    Icons.Default.EventNote,
                    contentDescription = null,
                    tint = cs.onBackground.copy(alpha = 0.2f),
                    modifier = Modifier.size(38.dp)
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                if (offset == 0) "今日清静" else "那天没有记录",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = cs.onBackground.copy(alpha = 0.55f),
                letterSpacing = 0.5.sp
            )
            Text(
                if (offset == 0) "今天还没有任何 App 使用记录"
                else "这天没有监控到任何使用记录",
                fontSize = 13.sp,
                color = DrGray,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }
    }
}
