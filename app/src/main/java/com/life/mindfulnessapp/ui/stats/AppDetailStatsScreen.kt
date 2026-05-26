package com.life.mindfulnessapp.ui.stats

import android.graphics.drawable.Drawable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.domain.model.AppDetailStats
import com.life.mindfulnessapp.ui.applist.AppConfigBottomSheet
import com.life.mindfulnessapp.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

// ── 顶层入口 ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailStatsScreen(
    packageName: String,
    viewModel: StatsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    LaunchedEffect(packageName) { viewModel.selectAppForDetail(packageName) }

    val detail by viewModel.selectedAppDetail.collectAsState()
    val appIcon by viewModel.selectedAppIcon.collectAsState()
    val isUninstalled by viewModel.selectedAppIsUninstalled.collectAsState()
    val last30DayUsage by viewModel.last30DayUsage.collectAsState()

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showEditLimitDialog by remember { mutableStateOf(false) }

    val cs = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        detail?.appName ?: "",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        color = cs.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.clearSelectedApp(); onNavigateBack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = cs.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showEditLimitDialog = true }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "配置",
                            tint = cs.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cs.background,
                    titleContentColor = cs.onSurface,
                    navigationIconContentColor = cs.onSurface
                ),
                windowInsets = WindowInsets.statusBars
            )
        },
        containerColor = cs.background
    ) { padding ->

        if (detail == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MindfulGreen40, strokeWidth = 2.dp)
            }
            return@Scaffold
        }

        val d = detail!!
        val allRecords = d.weekRecords + d.todayRecords.filter { tr ->
            d.weekRecords.none { it.id == tr.id }
        }
        // 使用今日 + 本周记录合并（dedup）做瀑布流，按时间倒序
        val sortedRecords = (d.todayRecords + d.weekRecords)
            .distinctBy { it.id }
            .sortedByDescending { it.startTime }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 40.dp)
        ) {
            // ── 日历热力图头部 ────────────────────────────────────────────────
            item {
                CalendarHeatmapSection(
                    appIcon = appIcon,
                    appName = d.appName,
                    todaySeconds = d.todaySeconds,
                    weekSeconds = d.weekSeconds,
                    dailyLimitSeconds = d.dailyLimitSeconds,
                    last30DayUsage = last30DayUsage,
                    cs = cs
                )
            }

            // ── 分割标签 ─────────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "使用记录",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface.copy(alpha = 0.45f),
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        "${sortedRecords.size} 条",
                        fontSize = 12.sp,
                        color = cs.onSurface.copy(alpha = 0.28f)
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            // ── 瀑布流记录列表 ───────────────────────────────────────────────
            if (sortedRecords.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "暂无记录",
                            fontSize = 14.sp,
                            color = cs.onSurface.copy(alpha = 0.3f)
                        )
                    }
                }
            } else {
                items(sortedRecords, key = { it.id }) { record ->
                    DetailRecordItem(record = record, cs = cs)
                }
            }

            // ── 底部操作 ─────────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(24.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TextButton(
                        onClick = { showDeleteConfirmDialog = true },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = cs.onSurface.copy(alpha = 0.25f)
                        )
                    ) {
                        Text("移出监控", fontSize = 13.sp)
                    }
                }
            }
        }
    }

    if (showEditLimitDialog && detail != null) {
        val d = detail!!
        val fakeAppInfo = com.life.mindfulnessapp.domain.model.AppInfo(
            packageName = d.packageName,
            appName = d.appName,
            icon = appIcon,
            isMonitored = true,
            dailyLimitMinutes = (d.dailyLimitSeconds / 60).toInt().coerceAtLeast(5),
            weeklyLimitMinutes = (d.weeklyLimitSeconds / 60).toInt(),
            timeLimitEnabled = d.timeLimitEnabled,
            overTimeMessage = d.overTimeMessage
        )
        AppConfigBottomSheet(
            appInfo = fakeAppInfo,
            onConfirm = { daily, weekly, timeLimitOn, overMsg ->
                viewModel.updateLimitFromDetail(
                    packageName = packageName,
                    newDailyMinutes = daily,
                    newWeeklyMinutes = weekly,
                    timeLimitEnabled = timeLimitOn,
                    overTimeMessage = overMsg
                )
                showEditLimitDialog = false
            },
            onDismiss = { showEditLimitDialog = false }
        )
    }

    if (showDeleteConfirmDialog) {
        DeleteConfirmDialog(
            appName = detail?.appName ?: packageName,
            appIcon = appIcon,
            cs = MaterialTheme.colorScheme,
            onConfirm = {
                showDeleteConfirmDialog = false
                viewModel.removeFromMonitorAndNavigateBack(packageName) { onNavigateBack() }
            },
            onDismiss = { showDeleteConfirmDialog = false }
        )
    }
}

// ── 日历热力图区（头部）──────────────────────────────────────────────────────

@Composable
private fun CalendarHeatmapSection(
    appIcon: Drawable?,
    appName: String,
    todaySeconds: Long,
    weekSeconds: Long,
    dailyLimitSeconds: Long,
    last30DayUsage: Map<String, Long>,
    cs: ColorScheme
) {
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val today = remember { Calendar.getInstance() }
    val maxSec = last30DayUsage.values.maxOrNull()?.takeIf { it > 0 } ?: 1L

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(cs.surface)
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App 信息行
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AppIconOrPlaceholder(
                icon = appIcon,
                size = 56,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp))
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    appName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatChip(label = "今日", value = formatHourMin(todaySeconds))
                    StatChip(label = "本周", value = formatHourMin(weekSeconds))
                    if (dailyLimitSeconds > 0) {
                        StatChip(
                            label = "上限",
                            value = "${dailyLimitSeconds / 60}分",
                            valueColor = Color(0xFF2979FF)
                        )
                    }
                }
            }
        }

        // 日历热力格（近 30 天，7 列 × 5 行，最新在右下）
        HeatmapCalendar(
            today = today,
            last30DayUsage = last30DayUsage,
            maxSec = maxSec,
            dailyLimitSeconds = dailyLimitSeconds,
            sdf = sdf,
            cs = cs
        )
    }
}

@Composable
private fun StatChip(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val cs = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, fontSize = 11.sp, color = cs.onSurface.copy(alpha = 0.4f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

@Composable
private fun HeatmapCalendar(
    today: Calendar,
    last30DayUsage: Map<String, Long>,
    maxSec: Long,
    dailyLimitSeconds: Long,
    sdf: SimpleDateFormat,
    cs: ColorScheme
) {
    // 生成 5 行 × 7 列共 35 个格子，列对齐"周一～周日"
    // 今天落在哪列就固定在那列，该行右侧剩余格子留白（秒数为0显示灰色）
    // 这样星期标题行与实际日期能严格对齐
    val cells = remember(today) {
        // 今天是周几（Calendar.MONDAY=2 … SUNDAY=1，转换为 0=周一 … 6=周日）
        val todayDow = ((today.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7)
        // 整个网格最后一格（右下角）= 今天所在行的周日
        // 网格共 35 格，第 34 格（0-indexed）= 今天所在行的周日
        // 今天在第 (35 - 7 + todayDow) = (28 + todayDow) 格
        // 所以网格起点（第 0 格）距今天 = (28 + todayDow) 天前
        val gridStartOffset = 28 + todayDow
        (0 until 35).map { idx ->
            val daysAgo = gridStartOffset - idx
            Calendar.getInstance().apply {
                timeInMillis = today.timeInMillis
                add(Calendar.DAY_OF_YEAR, -daysAgo)
            }
        }
    }
    val weekDayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // 星期标题行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            weekDayLabels.forEach { d ->
                Text(
                    d,
                    fontSize = 9.sp,
                    color = cs.onSurface.copy(alpha = 0.3f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 5 行格子
        val rows = cells.chunked(7)
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row.forEach { cal ->
                    val dateKey = sdf.format(cal.time)
                    val isFuture = cal.timeInMillis > today.timeInMillis
                    val sec = if (isFuture) 0L else (last30DayUsage[dateKey] ?: 0L)
                    val ratio = sec.toFloat() / maxSec
                    val isToday = dateKey == sdf.format(today.time)

                    // 判断当天是否超限（有限额设置时才启用橙色警示）
                    val isOverLimit = dailyLimitSeconds > 0 && sec > dailyLimitSeconds
                    // 颜色色相：超限用暖橙，未超限用宁静蓝灰
                    val heatColor = if (isOverLimit) HeatmapWarn else HeatmapNeutral
                    // 今日边框颜色也跟随语义变化
                    val todayBorderColor = if (isOverLimit) HeatmapWarn else HeatmapNeutral

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                when {
                                    isFuture -> Color.Transparent
                                    sec > 0 -> heatColor.copy(alpha = (ratio * 0.80f + 0.20f).coerceIn(0.20f, 0.88f))
                                    else -> cs.onSurface.copy(alpha = 0.07f)
                                }
                            )
                            .then(
                                if (isToday) Modifier.border(1.5.dp, todayBorderColor, RoundedCornerShape(4.dp))
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // 未来格子不显示日期数字
                        if (!isFuture) {
                            Text(
                                text = cal.get(Calendar.DAY_OF_MONTH).toString(),
                                fontSize = 8.sp,
                                color = if (sec > 0 && ratio > 0.5f) Color.White.copy(alpha = 0.9f)
                                        else cs.onSurface.copy(alpha = if (sec > 0) 0.55f else 0.3f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // 图例
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 未超限示例格
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(HeatmapNeutral.copy(alpha = 0.55f))
            )
            Spacer(Modifier.width(3.dp))
            Text("正常", fontSize = 9.sp, color = cs.onSurface.copy(alpha = 0.35f))
            Spacer(Modifier.width(8.dp))
            // 超限示例格
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(HeatmapWarn.copy(alpha = 0.75f))
            )
            Spacer(Modifier.width(3.dp))
            Text("超出限额", fontSize = 9.sp, color = cs.onSurface.copy(alpha = 0.35f))
        }
    }
}

// ── 单条使用记录 Item（瀑布流风格）──────────────────────────────────────────

@Composable
private fun DetailRecordItem(record: UsageRecordEntity, cs: ColorScheme) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("M月d日", Locale.getDefault()) }

    val startStr = remember(record.startTime) { timeFormat.format(Date(record.startTime)) }
    val endStr = if (record.endTime > 0) remember(record.endTime) { timeFormat.format(Date(record.endTime)) } else "进行中"
    val dateStr = remember(record.startTime) { dateFormat.format(Date(record.startTime)) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(cs.surface)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 时间轴点
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(52.dp)
                ) {
                    Text(
                        dateStr,
                        fontSize = 10.sp,
                        color = cs.onSurface.copy(alpha = 0.35f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        startStr,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = cs.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        "↕",
                        fontSize = 9.sp,
                        color = cs.onSurface.copy(alpha = 0.2f)
                    )
                    Text(
                        endStr,
                        fontSize = 13.sp,
                        color = cs.onSurface.copy(alpha = 0.45f)
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(44.dp)
                        .background(cs.outlineVariant.copy(alpha = 0.5f))
                )

                // 目的与时长
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (record.purpose != null) {
                        Text(
                            record.purpose,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = cs.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            "无目的",
                            fontSize = 13.sp,
                            color = cs.onSurface.copy(alpha = 0.3f)
                        )
                    }
                    if (record.note != null) {
                        Text(
                            record.note,
                            fontSize = 11.sp,
                            color = cs.onSurface.copy(alpha = 0.45f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 时长徽章（中性蓝灰，不传递"用得多=好"的积极暗示）
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(HeatmapNeutral.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        formatHourMin(record.durationSeconds),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HeatmapNeutral
                    )
                }
            }
        }
    }
}

// ── App 图标占位 ──────────────────────────────────────────────────────────────

@Composable
internal fun AppIconOrPlaceholder(
    icon: Drawable?,
    size: Int,
    modifier: Modifier = Modifier,
    bgColor: Color = MaterialTheme.colorScheme.surface
) {
    if (icon != null) {
        val bitmap = remember(icon) { icon.toBitmap(size, size).asImageBitmap() }
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier)
    } else {
        Box(
            modifier = modifier.background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Android,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                modifier = Modifier.size((size * 0.55f).dp)
            )
        }
    }
}

// ── 移出监控确认对话框 ────────────────────────────────────────────────────────

@Composable
private fun DeleteConfirmDialog(
    appName: String,
    appIcon: Drawable?,
    cs: ColorScheme,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            AppIconOrPlaceholder(
                icon = appIcon, size = 48,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
                bgColor = cs.surfaceVariant
            )
        },
        title = {
            Text("移出监控？", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
        },
        text = {
            Text(
                "将「$appName」从监控列表中移除后，\n将不再拦截该应用并清除所有时限设置。",
                fontSize = 14.sp, color = cs.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = DangerColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("移出", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = cs.onSurface.copy(alpha = 0.4f))
            }
        },
        containerColor = cs.surface,
        shape = RoundedCornerShape(20.dp)
    )
}

// ── DetailSectionCard（复用供外部）──────────────────────────────────────────

@Composable
internal fun DetailSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    cs: ColorScheme,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(cs.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(icon, contentDescription = null, tint = MindfulGreen40, modifier = Modifier.size(16.dp))
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
            }
            content()
        }
    }
}

// ── 工具函数 ──────────────────────────────────────────────────────────────────

/** 格式化为 "Xh Ym" 样式 */
private fun formatHourMin(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        m > 0 -> "${m}m"
        seconds > 0 -> "${seconds}s"
        else -> "0m"
    }
}
