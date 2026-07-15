package com.life.mindfulnessapp.ui.stats

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.domain.model.TimelineEvent
import com.life.mindfulnessapp.ui.applist.AppConfigBottomSheet
import com.life.mindfulnessapp.ui.home.TimelineEventNode
import com.life.mindfulnessapp.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

// ── 顶层入口 ──────────────────────────────────────────────────────────────────

@Composable
fun AppDetailStatsScreen(
    packageName: String,
    autoShowEditDialog: Boolean = false,
    viewModel: StatsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    LaunchedEffect(packageName) { viewModel.selectAppForDetail(packageName) }

    val detail by viewModel.selectedAppDetail.collectAsState()
    val appIcon by viewModel.selectedAppIcon.collectAsState()
    val calendarMonth by viewModel.calendarMonth.collectAsState()
    val calendarMonthDbUsage by viewModel.calendarMonthDbUsage.collectAsState()
    val addedDateKey by viewModel.addedDateKey.collectAsState()
    val selectedDate by viewModel.selectedCalendarDate.collectAsState()
    val selectedDayRecords by viewModel.selectedDayRecords.collectAsState()

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showEditLimitDialog by remember { mutableStateOf(autoShowEditDialog) }

    val cs = MaterialTheme.colorScheme
    val todayKey = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    val todayMonth = remember { SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date()) }

    // ── 月历折叠状态：点击标题行切换展开/折叠 ──────────────────────────────
    var calendarCollapsed by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    Scaffold(
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

        // 把 records 转为 TimelineEvent.UsageEvent，复用首页组件
        val timelineEvents = remember(selectedDayRecords) {
            selectedDayRecords.map { rec ->
                TimelineEvent.UsageEvent(
                    packageName   = rec.packageName,
                    appName       = rec.packageName.substringAfterLast("."),
                    startTime     = rec.startTime,
                    endTime       = rec.endTime,
                    durationSeconds = rec.durationSeconds,
                    endReason     = rec.endReason,
                    purpose       = rec.purpose,
                    recordId      = rec.id,
                    note          = rec.note
                )
            }
        }
        // appIcon 供首页卡片使用
        val iconMap = remember(appIcon, d.packageName) {
            val appInfoEntry = AppInfo(
                packageName       = d.packageName,
                appName           = d.appName,
                icon              = appIcon,
                isMonitored       = true,
                dailyLimitMinutes = (d.dailyLimitSeconds / 60).toInt(),
                weeklyLimitMinutes = (d.weeklyLimitSeconds / 60).toInt(),
                timeLimitEnabled  = d.timeLimitEnabled,
                overTimeMessage   = d.overTimeMessage
            )
            mapOf(d.packageName to appInfoEntry)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 48.dp)
        ) {
            // ── item 0：顶部栏 ─────────────────────────────────────────────
            item(key = "topbar") {
                DetailTopBar(
                    appIcon = appIcon,
                    appName = d.appName,
                    addedDateKey = addedDateKey,
                    todaySeconds = d.todaySeconds,
                    weekSeconds = d.weekSeconds,
                    dailyLimitSeconds = d.dailyLimitSeconds,
                    cs = cs,
                    onNavigateBack = { viewModel.clearSelectedApp(); onNavigateBack() },
                    onSettings = { showEditLimitDialog = true }
                )
            }

            // ── item 1：可折叠月历 ─────────────────────────────────────────
            item(key = "calendar") {
                Spacer(Modifier.height(10.dp))
                CollapsibleMonthCalendar(
                    monthKey = calendarMonth,
                    dbUsageMap = calendarMonthDbUsage,
                    selectedDate = selectedDate,
                    todayKey = todayKey,
                    todayMonth = todayMonth,
                    dailyLimitSeconds = d.dailyLimitSeconds,
                    addedDateKey = addedDateKey,
                    collapsed = calendarCollapsed,
                    cs = cs,
                    onPrevMonth = { viewModel.calendarPrevMonth() },
                    onNextMonth = { viewModel.calendarNextMonth() },
                    onDateSelected = { dateKey ->
                        viewModel.selectCalendarDate(dateKey)
                        // 选中日期后自动折叠，方便查看下方记录
                        calendarCollapsed = true
                    },
                    onToggleCollapse = { calendarCollapsed = !calendarCollapsed }
                )
            }

            // ── item 2：选中日期标题行 ─────────────────────────────────────
            item(key = "day_header") {
                Spacer(Modifier.height(16.dp))
                DetailDayHeader(
                    selectedDate = selectedDate,
                    recordCount = selectedDayRecords.size,
                    cs = cs
                )
            }

            // ── items 3+：首页风格时间轴记录 ──────────────────────────────
            if (timelineEvents.isEmpty()) {
                item(key = "empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MindfulGreen40.copy(alpha = 0.28f),
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                "这天没有使用记录",
                                fontSize = 13.sp,
                                color = cs.onSurface.copy(alpha = 0.28f)
                            )
                        }
                    }
                }
            } else {
                items(timelineEvents, key = { "record_${it.recordId}" }) { event ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        TimelineEventNode(
                            event = event,
                            isLast = event == timelineEvents.last(),
                            iconMap = iconMap,
                            onNoteClick = { /* 详情页暂不支持备注编辑 */ },
                            cardBg = cs.surface,
                            onSurface = cs.onSurface,
                            outline = cs.outlineVariant
                        )
                    }
                }
            }

            // ── 底部操作 ──────────────────────────────────────────────────────
            item(key = "remove") {
                Spacer(Modifier.height(24.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TextButton(
                        onClick = { showDeleteConfirmDialog = true },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = cs.onSurface.copy(alpha = 0.22f)
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

// ── 详情页顶部栏 ──────────────────────────────────────────────────────────────
//
// 将「返回按钮 + App图标 + App名/副标题 + 数据芯片 + 设置按钮」统一设计为一行
// 彻底消除原来 TopAppBar title 与 CompactAppHeader 标题重复的问题
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DetailTopBar(
    appIcon: Drawable?,
    appName: String,
    addedDateKey: String?,
    todaySeconds: Long,
    weekSeconds: Long,
    dailyLimitSeconds: Long,
    cs: ColorScheme,
    onNavigateBack: () -> Unit,
    onSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(cs.surface)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 返回按钮
        IconButton(onClick = onNavigateBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = cs.onSurface.copy(alpha = 0.75f),
                modifier = Modifier.size(22.dp)
            )
        }

        // App 图标
        AppIconOrPlaceholder(
            icon = appIcon,
            size = 40,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(11.dp))
        )

        Spacer(Modifier.width(10.dp))

        // App 名称 + 副标题（弹性占位）
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                appName,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (addedDateKey != null) {
                Text(
                    "监控自 $addedDateKey",
                    fontSize = 10.sp,
                    color = cs.onSurface.copy(alpha = 0.35f)
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // 数据芯片组
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MiniStatChip(label = "今日", value = formatHourMin(todaySeconds), cs = cs)
            MiniStatChip(label = "本周", value = formatHourMin(weekSeconds), cs = cs)
            if (dailyLimitSeconds > 0) {
                val isOver = todaySeconds >= dailyLimitSeconds
                MiniStatChip(
                    label = "限",
                    value = "${dailyLimitSeconds / 60}m",
                    valueColor = if (isOver) WarningColor else cs.onSurface.copy(alpha = 0.55f),
                    cs = cs
                )
            }
        }

        // 设置按钮
        IconButton(onClick = onSettings) {
            Icon(
                Icons.Default.Settings,
                contentDescription = "配置",
                tint = cs.onSurface.copy(alpha = 0.45f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun MiniStatChip(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
    cs: ColorScheme
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(label, fontSize = 9.sp, color = cs.onSurface.copy(alpha = 0.35f))
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

// ── 可折叠月历组件 ────────────────────────────────────────────────────────────
// 展开态：显示完整月份所有日期行
// 折叠态（collapsed=true）：只显示含选中日期的那一行（单周）
// 点击月份标题行可切换折叠/展开，选中日期后自动折叠
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CollapsibleMonthCalendar(
    monthKey: String,
    dbUsageMap: Map<String, Long>,
    selectedDate: String?,
    todayKey: String,
    todayMonth: String,
    dailyLimitSeconds: Long,
    addedDateKey: String?,
    collapsed: Boolean,                // true=折叠为单周, false=展开全月
    cs: ColorScheme,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDateSelected: (String) -> Unit,
    onToggleCollapse: () -> Unit       // 点击标题行切换折叠/展开
) {
    val calendarCells = remember(monthKey) { buildCalendarCells(monthKey) }
    val weeks = remember(calendarCells) { calendarCells.chunked(7) }

    val titleText = remember(monthKey) {
        try {
            val titleSdf = SimpleDateFormat("yyyy年M月", Locale.getDefault())
            titleSdf.format(SimpleDateFormat("yyyy-MM", Locale.getDefault()).parse(monthKey)!!)
        } catch (e: Exception) { monthKey }
    }

    val isCurrentMonth = monthKey == todayMonth
    val weekLabels = listOf("一", "二", "三", "四", "五", "六", "日")

    // 找到选中日期所在的周 index（折叠时只渲染该行）
    val selectedWeekIndex = remember(selectedDate, weeks) {
        if (selectedDate == null) weeks.size - 1
        else weeks.indexOfFirst { week -> week.any { it.dateKey == selectedDate } }
            .takeIf { it >= 0 } ?: weeks.size - 1
    }

    // 动态测量格子区域实际高度（px），避免硬编码行高出错
    val density = androidx.compose.ui.platform.LocalDensity.current
    var gridTotalHeightPx by remember { mutableFloatStateOf(0f) }
    // 切换月份时重置测量值，待下次展开时重新测量
    LaunchedEffect(monthKey) { gridTotalHeightPx = 0f }

    // 单行高度 = 整体高度 / 总行数（测量完成前用 0，动画不会错乱）
    val rowHeightPx = if (weeks.isNotEmpty() && gridTotalHeightPx > 0f)
        gridTotalHeightPx / weeks.size else 0f

    // 动画驱动：折叠进度 0f=展开, 1f=折叠
    val animProgress by animateFloatAsState(
        targetValue = if (collapsed) 1f else 0f,
        animationSpec = tween(300),
        label = "calendarCollapse"
    )

    // 格子区域高度随动画插值（px → Dp，用标准 Float 线性插值）
    val currentBodyHeightPx = gridTotalHeightPx + (rowHeightPx - gridTotalHeightPx) * animProgress
    val currentBodyHeight: Dp = with(density) { currentBodyHeightPx.toDp() }

    // 折叠时向上偏移，把选中周"滚"到顶部（px → Dp）
    val scrollOffsetPx = rowHeightPx * selectedWeekIndex * animProgress
    val scrollOffsetDp: Dp = with(density) { scrollOffsetPx.toDp() }

    // 箭头旋转：展开时 0°，折叠时 180°（朝上变朝下，提示可展开）
    val arrowRotation by animateFloatAsState(
        targetValue = if (collapsed) 180f else 0f,
        animationSpec = tween(300),
        label = "arrowRotation"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(cs.surface)
            .padding(top = 4.dp, bottom = 12.dp)
    ) {
        // ── 月份导航栏（点击中间区域切换折叠）──────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 上一月（折叠时禁用）
            IconButton(
                onClick = onPrevMonth,
                modifier = Modifier.size(36.dp),
                enabled = !collapsed
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "上一月",
                    tint = cs.onSurface.copy(alpha = if (collapsed) 0.15f else 0.6f),
                    modifier = Modifier.size(22.dp)
                )
            }

            // 月份标题 + 折叠指示器（可点击区域）
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onToggleCollapse)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                AnimatedContent(
                    targetState = titleText,
                    transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                    label = "monthTitle"
                ) { title ->
                    Text(
                        title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface
                    )
                }
                Spacer(Modifier.width(4.dp))
                // 折叠/展开箭头指示器
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = if (collapsed) "展开月历" else "折叠月历",
                    tint = cs.onSurface.copy(alpha = 0.35f),
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(arrowRotation)
                )
            }

            // 下一月（折叠时禁用）
            IconButton(
                onClick = onNextMonth,
                enabled = !isCurrentMonth && !collapsed,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "下一月",
                    tint = if (isCurrentMonth || collapsed) cs.onSurface.copy(alpha = 0.15f)
                    else cs.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }


        // ── 星期标题行 ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            weekLabels.forEach { label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = cs.onSurface.copy(alpha = 0.3f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // ── 日期格子区域（折叠时裁剪到单行高度，并平移偏移量）─────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (currentBodyHeight > 0.dp) currentBodyHeight else Dp.Unspecified)
                .clipToBounds()
        ) {
            Column(
                modifier = Modifier
                    .offset(y = -scrollOffsetDp)
                    // 用 onSizeChanged 动态测量整个格子列的实际高度（展开状态）
                    .onSizeChanged { size ->
                        // 仅在未折叠（进度接近 0）时更新展开高度，避免折叠过程中覆盖真实值
                        if (animProgress < 0.05f) {
                            gridTotalHeightPx = size.height.toFloat()
                        }
                    }
            ) {
                weeks.forEach { week ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        week.forEach { cell ->
                            val isCurrentMonthDay = cell.month == monthKey
                            val isFuture = !isCurrentMonthDay || cell.dateKey > todayKey
                            val isBeforeMonitoringDb = addedDateKey != null && cell.dateKey < addedDateKey
                            val dbSec = if (isFuture || !isCurrentMonthDay || isBeforeMonitoringDb) 0L
                            else (dbUsageMap[cell.dateKey] ?: 0L)
                            val isOverLimit = dailyLimitSeconds > 0 && dbSec > dailyLimitSeconds
                            val isToday = cell.dateKey == todayKey
                            val isSelected = cell.dateKey == selectedDate && isCurrentMonthDay
                            val isAddedDay = cell.dateKey == addedDateKey && isCurrentMonthDay

                            CalendarCell(
                                modifier = Modifier.weight(1f),
                                day = cell.dayOfMonth,
                                dbSec = dbSec,
                                isCurrentMonth = isCurrentMonthDay,
                                isToday = isToday,
                                isSelected = isSelected,
                                isFuture = isFuture,
                                isOverLimit = isOverLimit,
                                isAddedDay = isAddedDay,
                                cs = cs,
                                onClick = {
                                    if (isCurrentMonthDay && !isFuture) onDateSelected(cell.dateKey)
                                }
                            )
                        }
                        repeat(7 - week.size) {
                            Box(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
}

// ── 选中日期标题行（时间轴区域上方）────────────────────────────────────────────

@Composable
private fun DetailDayHeader(
    selectedDate: String?,
    recordCount: Int,
    cs: ColorScheme
) {
    val displayDate = remember(selectedDate) {
        if (selectedDate == null) return@remember "今日"
        try {
            val displaySdf = SimpleDateFormat("M月d日 EEE", Locale.CHINESE)
            displaySdf.format(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(selectedDate)!!)
        } catch (e: Exception) { selectedDate }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(3.dp, 14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MindfulGreen40)
            )
            AnimatedContent(
                targetState = displayDate,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                label = "detailDayHeader"
            ) { date ->
                Text(date, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface.copy(alpha = 0.75f))
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "${recordCount}条",
                fontSize = 11.sp,
                color = cs.onSurface.copy(alpha = 0.28f)
            )
        }
    }
}

// ── 单个日历格子 ──────────────────────────────────────────────────────────────
//
// 布局（从上到下）：
//   日期数字（大）
//   记录时长（小，绿色）
//   右上角：加入监控标记（小圆点）/ 超限标记
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CalendarCell(
    modifier: Modifier = Modifier,
    day: Int,
    dbSec: Long,      // 本地记录统计时长
    isCurrentMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    isFuture: Boolean,
    isOverLimit: Boolean,
    isAddedDay: Boolean,  // 加入监控的那天
    cs: ColorScheme,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = when {
            !isCurrentMonth -> Color.Transparent
            isSelected && isOverLimit -> HeatmapWarn
            isSelected -> MindfulGreen40
            else -> Color.Transparent
        },
        animationSpec = tween(180),
        label = "cellBg"
    )

    val dayColor = when {
        !isCurrentMonth -> cs.onSurface.copy(alpha = 0.18f)
        isSelected -> Color.White
        isToday -> MindfulGreen40
        isFuture -> cs.onSurface.copy(alpha = 0.28f)
        else -> cs.onSurface.copy(alpha = 0.82f)
    }

    Box(modifier = modifier.padding(horizontal = 1.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(bgColor)
                .then(
                    if (isToday && !isSelected && isCurrentMonth) Modifier.border(
                        1.5.dp,
                        if (isOverLimit) HeatmapWarn else MindfulGreen40,
                        RoundedCornerShape(10.dp)
                    ) else Modifier
                )
                .clickable(enabled = isCurrentMonth && !isFuture) { onClick() }
                .padding(top = 7.dp, bottom = 8.dp, start = 1.dp, end = 1.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // 日期数字
            Text(
                text = day.toString(),
                fontSize = 13.sp,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
                color = dayColor,
                textAlign = TextAlign.Center
            )

            // 记录时长行
            val dbText = formatDurationCompact(dbSec, isCurrentMonth && !isFuture)
            Text(
                text = dbText,
                fontSize = 8.sp,
                fontWeight = FontWeight.Normal,
                color = when {
                    dbText.isEmpty() -> Color.Transparent
                    isSelected -> Color.White.copy(alpha = 0.80f)
                    isOverLimit -> HeatmapWarn.copy(alpha = 0.9f)
                    else -> MindfulGreen40.copy(alpha = 0.75f)
                },
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.defaultMinSize(minHeight = 10.dp)
            )
        }

        // 右上角：加入监控标记（黄色小圆点）
        if (isAddedDay && isCurrentMonth) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(WarningColor)
                    .align(Alignment.TopEnd)
                    .offset(x = (-2).dp, y = 3.dp)
            )
        }
    }
}

// ── 格式化辅助：日历格子用的紧凑格式 ─────────────────────────────────────────

private fun formatDurationCompact(seconds: Long, shouldShow: Boolean): String {
    if (!shouldShow || seconds <= 0L) return ""
    return when {
        seconds >= 3600 -> {
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            if (m > 0) "${h}h${m}m" else "${h}h"
        }
        seconds >= 60 -> "${seconds / 60}m"
        else -> "${seconds}s"
    }
}

// ── 月历数据结构 ──────────────────────────────────────────────────────────────

private data class CalendarCellData(
    val dateKey: String,
    val dayOfMonth: Int,
    val month: String
)

private fun buildCalendarCells(monthKey: String): List<CalendarCellData> {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val monthSdf = SimpleDateFormat("yyyy-MM", Locale.getDefault())

    val firstDayCal = Calendar.getInstance().apply {
        time = monthSdf.parse(monthKey)!!
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val daysInMonth = firstDayCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDow = ((firstDayCal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7)

    val cells = mutableListOf<CalendarCellData>()

    if (firstDow > 0) {
        val prevCal = firstDayCal.clone() as Calendar
        prevCal.add(Calendar.DAY_OF_MONTH, -firstDow)
        repeat(firstDow) { i ->
            val c = (prevCal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, i) }
            cells.add(CalendarCellData(sdf.format(c.time), c.get(Calendar.DAY_OF_MONTH), monthSdf.format(c.time)))
        }
    }

    for (d in 1..daysInMonth) {
        val c = (firstDayCal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, d) }
        cells.add(CalendarCellData(sdf.format(c.time), d, monthKey))
    }

    val remainder = if (cells.size % 7 == 0) 0 else 7 - (cells.size % 7)
    val lastDayCal = (firstDayCal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, daysInMonth) }
    repeat(remainder) { i ->
        val c = (lastDayCal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, i + 1) }
        cells.add(CalendarCellData(sdf.format(c.time), c.get(Calendar.DAY_OF_MONTH), monthSdf.format(c.time)))
    }

    return cells
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
            ) { Text("移出", color = Color.White) }
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

// ── DetailSectionCard（供外部复用）───────────────────────────────────────────

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


