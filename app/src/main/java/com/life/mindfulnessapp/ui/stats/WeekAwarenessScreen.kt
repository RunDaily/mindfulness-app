package com.life.mindfulnessapp.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.domain.model.WeeklyAppStat
import com.life.mindfulnessapp.domain.model.WeeklyReportData
import com.life.mindfulnessapp.overlay.formatSeconds
import com.life.mindfulnessapp.ui.theme.LogoGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val muted = Color(0xFF8E8E93)
private val weekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekAwarenessScreen(
    viewModel: WeekAwarenessViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToAppReview: (packageName: String) -> Unit,
    onNavigateToAddApps: () -> Unit = {}
) {
    val report by viewModel.report.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val cs = MaterialTheme.colorScheme

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("本周觉察", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        containerColor = cs.background
    ) { padding ->
        when {
            loading && report == null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = LogoGreen, strokeWidth = 2.dp)
                }
            }
            report == null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无数据", color = muted)
                }
            }
            else -> {
                WeekAwarenessContent(
                    report = report!!,
                    onAppClick = onNavigateToAppReview,
                    onAddApps = onNavigateToAddApps,
                    modifier = Modifier
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun WeekAwarenessContent(
    report: WeeklyReportData,
    onAppClick: (String) -> Unit,
    onAddApps: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val rangeFmt = SimpleDateFormat("M/d", Locale.getDefault())
    val rangeLabel =
        "${rangeFmt.format(Date(report.weekStartMs))} – ${rangeFmt.format(Date(report.weekEndMs))} · 到现在"

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Text(rangeLabel, fontSize = 13.sp, color = muted)

        Text(
            text = report.heroText,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurface,
            lineHeight = 32.sp
        )

        if (!report.hasMonitoredApps) {
            EmptyAnchorCard(onAddApps = onAddApps)
            return
        }

        // 主指标：守住 / 有意图；对照有样本才进三列，否则单独叙事
        if (report.alignmentRate != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    title = "守住",
                    value = "${report.dismissCount}",
                    subtitle = if (report.dismissCount > 0) "门外离开" else "尚无门前离开",
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "有意图",
                    value = report.mindfulRatio?.let { "${(it * 100).toInt()}%" } ?: "—",
                    subtitle = if (report.enterCount > 0)
                        "${report.mindfulEnterCount}/${report.enterCount} 进入"
                    else "本周无进入",
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "对照",
                    value = "${(report.alignmentRate * 100).toInt()}%",
                    subtitle = "${report.alignedCount}/${report.reviewedCount} 对齐",
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    title = "守住",
                    value = "${report.dismissCount}",
                    subtitle = if (report.dismissCount > 0) "门外离开" else "尚无门前离开",
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "有意图",
                    value = report.mindfulRatio?.let { "${(it * 100).toInt()}%" } ?: "—",
                    subtitle = if (report.enterCount > 0)
                        "${report.mindfulEnterCount}/${report.enterCount} 进入"
                    else "本周无进入",
                    modifier = Modifier.weight(1f)
                )
            }
            AlignmentHintCard(
                reviewedCount = report.reviewedCount,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 守住 vs 进入对照条
        if (report.dismissCount > 0 || report.enterCount > 0) {
            HoldEnterContrast(
                dismissCount = report.dismissCount,
                enterCount = report.enterCount
            )
        }

        // 7 日守住小柱（有意图门或已有守住时展示）
        if (report.hasIntentGateApps || report.dismissCount > 0) {
            DailyHoldBars(counts = report.dailyDismissCounts)
        }

        // App 清单
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "按应用",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface
            )
            Text(
                "点进看该 App 本周到现在",
                fontSize = 12.sp,
                color = muted
            )
            Spacer(modifier = Modifier.height(4.dp))
            report.appSummaries.forEach { app ->
                AppAwarenessRow(app = app, onClick = { onAppClick(app.packageName) })
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun AlignmentHintCard(
    reviewedCount: Int,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(cs.surface)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("对照", fontSize = 12.sp, color = muted)
        Text(
            if (reviewedCount == 0)
                "结束使用时回看那一句意图，对照会出现在这里。"
            else
                "再多几次结束对照，这里会显示对齐比例。",
            fontSize = 14.sp,
            color = cs.onSurface,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun EmptyAnchorCard(onAddApps: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cs.surface)
            .clickable(onClick = onAddApps)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("还没有系上锚", fontWeight = FontWeight.Medium, fontSize = 16.sp, color = cs.onSurface)
        Text(
            "选几个容易滑进去的 App，本周觉察才会留下痕迹。",
            fontSize = 13.sp,
            color = muted,
            lineHeight = 18.sp
        )
        Text("去系锚 →", fontSize = 14.sp, color = LogoGreen, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String?,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(cs.surface)
            .padding(horizontal = 12.dp, vertical = 14.dp)
    ) {
        Text(title, fontSize = 12.sp, color = muted)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            value,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurface
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, fontSize = 11.sp, color = muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HoldEnterContrast(dismissCount: Int, enterCount: Int) {
    val cs = MaterialTheme.colorScheme
    val total = (dismissCount + enterCount).coerceAtLeast(1)
    val holdWeight = dismissCount.toFloat() / total
    val enterWeight = enterCount.toFloat() / total

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cs.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("守住与进入", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = cs.onSurface)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(cs.outlineVariant.copy(alpha = 0.35f))
        ) {
            if (dismissCount > 0) {
                Box(
                    modifier = Modifier
                        .weight(holdWeight.coerceAtLeast(0.04f))
                        .fillMaxHeight()
                        .background(LogoGreen)
                )
            }
            if (enterCount > 0) {
                Box(
                    modifier = Modifier
                        .weight(enterWeight.coerceAtLeast(0.04f))
                        .fillMaxHeight()
                        .background(cs.onSurface.copy(alpha = 0.22f))
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("守住 $dismissCount", fontSize = 12.sp, color = LogoGreen)
            Text("进入 $enterCount", fontSize = 12.sp, color = muted)
        }
    }
}

@Composable
private fun DailyHoldBars(counts: List<Int>) {
    val cs = MaterialTheme.colorScheme
    val max = (counts.maxOrNull() ?: 0).coerceAtLeast(1)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cs.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("本周守住节奏", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = cs.onSurface)
        Text("纵轴是次数，不是时长", fontSize = 11.sp, color = muted)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            counts.forEachIndexed { index, count ->
                val fraction = if (count <= 0) 0.06f else (count.toFloat() / max).coerceIn(0.12f, 1f)
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .fillMaxHeight(fraction)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (count > 0) LogoGreen.copy(alpha = 0.85f)
                                else cs.outlineVariant.copy(alpha = 0.35f)
                            )
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            weekdayLabels.forEach { label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    fontSize = 11.sp,
                    color = muted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun AppAwarenessRow(app: WeeklyAppStat, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val intentLabel = when {
        !app.requireIntentOnOpen -> "未开意图门"
        app.enterCount == 0 && app.dismissCount == 0 -> "本周无记录"
        app.mindfulRatio != null -> "有意图 ${(app.mindfulRatio!! * 100).toInt()}%"
        app.enterCount > 0 -> "进入 ${app.enterCount}"
        else -> "守住 ${app.dismissCount}"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cs.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    app.appName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                app.limitReachedLabel?.let { label ->
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(label, fontSize = 11.sp, color = Color(0xFFE8941A))
                }
            }
            Text(
                "守住 ${app.dismissCount} · 进入 ${app.enterCount} · $intentLabel",
                fontSize = 12.sp,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (app.totalSeconds > 0) {
                Text(
                    "用了 ${formatSeconds(app.totalSeconds)}",
                    fontSize = 11.sp,
                    color = muted.copy(alpha = 0.8f)
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = muted,
            modifier = Modifier.size(20.dp)
        )
    }
}
