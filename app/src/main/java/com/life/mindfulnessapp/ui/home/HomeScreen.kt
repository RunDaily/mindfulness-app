package com.life.mindfulnessapp.ui.home

import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.domain.model.AppUsageSummary
import com.life.mindfulnessapp.domain.model.TimelineEvent
import com.life.mindfulnessapp.domain.usecase.PermissionStatus
import com.life.mindfulnessapp.overlay.formatSeconds
import com.life.mindfulnessapp.ui.applist.AppIcon
import com.life.mindfulnessapp.ui.applist.LimitSlider
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.theme.LogoGreenBright
import com.life.mindfulnessapp.ui.theme.MindfulGreen40
import java.text.SimpleDateFormat
import java.util.*

// ── 坞栏常量 ─────────────────────────────────────────────────────────────────
private const val DOCK_SLOTS = 5
private const val DOCK_MAX = 20

// ── 主入口 ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToAppList: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAppDetail: (String) -> Unit = {}
) {
    val summaries by viewModel.usageSummaries.collectAsState()
    val permStatus by viewModel.permissionStatus.collectAsState()
    val monitoredApps by viewModel.monitoredAppCount.collectAsState()
    val monitoredAppsWithIcon by viewModel.monitoredAppsWithIcon.collectAsState()
    val timeline by viewModel.todayTimeline.collectAsState()
    val todayMindfulCount by viewModel.todayMindfulCount.collectAsState()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showAddSheet by remember { mutableStateOf(false) }
    // 备注编辑弹窗状态
    var editingEvent by remember { mutableStateOf<TimelineEvent.UsageEvent?>(null) }

    LaunchedEffect(showAddSheet) { if (showAddSheet) viewModel.loadInstalledApps() }
    LaunchedEffect(Unit) { viewModel.loadData() }

    val cs = MaterialTheme.colorScheme
    val bottomBarHeight = 72.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            HomeHeroCard(
                permissionStatus = permStatus,
                summaries = summaries,
                todayMindfulCount = todayMindfulCount,
                onNavigateToSettings = onNavigateToSettings
            )

            if (!permStatus.allGranted) {
                PermissionWarningCard(
                    permissionStatus = permStatus,
                    onNavigateToSettings = onNavigateToSettings
                )
            }

            // App 用量横向滑动卡片区（仅有监控 App 时显示）
            if (monitoredAppsWithIcon.isNotEmpty()) {
                AppUsageRow(
                    monitoredApps = monitoredAppsWithIcon,
                    summaries = summaries,
                    onAppClick = onNavigateToAppDetail
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                TodayTimeline(
                    timeline = timeline,
                    summaries = summaries,
                    monitoredAppsWithIcon = monitoredAppsWithIcon,
                    bottomPadding = bottomBarHeight,
                    onNavigateToAppList = onNavigateToAppList,
                    onNoteClick = { editingEvent = it },
                    cardBg = cs.surface,
                    onSurface = cs.onSurface,
                    outline = cs.outlineVariant
                )
            }
        }

        AppDockBar(
            monitoredApps = monitoredAppsWithIcon,
            summaries = summaries,
            onAppClick = onNavigateToAppDetail,
            onAppLongClick = { _ -> viewModel.loadInstalledApps(); showAddSheet = true },
            onEmptySlotClick = { showAddSheet = true },
            onOverflowClick = { viewModel.loadInstalledApps(); showAddSheet = true },
            modifier = Modifier.align(Alignment.BottomCenter),
            dockBg = cs.background
        )
    }

    // 备注编辑弹窗
    editingEvent?.let { event ->
        NoteEditDialog(
            event = event,
            cs = cs,
            onConfirm = { newNote ->
                viewModel.updateRecordNote(event.recordId, newNote)
                editingEvent = null
            },
            onDismiss = { editingEvent = null }
        )
    }

    if (showAddSheet) {
        AppPickerSheet(
            viewModel = viewModel,
            sheetState = sheetState,
            onDismiss = { showAddSheet = false; viewModel.clearSheetSearch() },
            sheetBg = cs.surface,
            onSurface = cs.onSurface
        )
    }
}

// ── 顶部紧凑 Header ───────────────────────────────────────────────────────────

@Composable
private fun HomeHeroCard(
    permissionStatus: PermissionStatus,
    summaries: List<AppUsageSummary>,
    todayMindfulCount: Int,
    onNavigateToSettings: () -> Unit
) {
    val onBg = MaterialTheme.colorScheme.onBackground
    val totalTodaySec = summaries.sumOf { it.todaySeconds }
    val timeText = if (totalTodaySec == 0L) "—" else formatSeconds(totalTodaySec)
    val timeColor = if (totalTodaySec == 0L) onBg.copy(alpha = 0.2f) else LogoGreen

    // 超时 App 数量
    val overLimitCount = summaries.count { it.dailyUsagePercent >= 1f }

    // 数字数变化时的跳动动画
    val infiniteTransition = rememberInfiniteTransition(label = "hero_pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_pulse"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── 第一行：标题 + 设置入口 ──────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 状态指示灯
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (permissionStatus.allGranted) LogoGreen.copy(alpha = dotAlpha)
                            else Color(0xFFF39C12).copy(alpha = dotAlpha)
                        )
                )
                Text(
                    text = "今天",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = onBg.copy(alpha = 0.55f),
                    letterSpacing = 0.3.sp
                )
            }
            // 设置入口
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(onBg.copy(alpha = 0.06f))
                    .clickable { onNavigateToSettings() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "设置",
                    tint = onBg.copy(alpha = 0.45f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // ── 第二行：大数字 + 右侧统计 ────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            // 左：今日使用时长（大字体）
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                AnimatedContent(
                    targetState = timeText,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                    label = "timeText"
                ) { text ->
                    Text(
                        text = text,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        color = timeColor,
                        lineHeight = 38.sp,
                        letterSpacing = (-1).sp
                    )
                }
                Text(
                    "屏幕使用时长",
                    fontSize = 11.sp,
                    color = onBg.copy(alpha = 0.3f),
                    letterSpacing = 0.5.sp
                )
            }

            // 右：竖排统计小块
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 觉察次数
                HeroStatChip(
                    value = if (todayMindfulCount == 0) "—" else "$todayMindfulCount",
                    label = "觉察",
                    color = if (todayMindfulCount == 0) onBg.copy(alpha = 0.18f) else LogoGreen,
                    onBg = onBg
                )
                // 超限 App
                HeroStatChip(
                    value = if (overLimitCount == 0) "—" else "$overLimitCount",
                    label = "超限",
                    color = if (overLimitCount == 0) onBg.copy(alpha = 0.18f) else Color(0xFFE74C3C),
                    onBg = onBg
                )
            }
        }
    }
}

@Composable
private fun HeroStatChip(
    value: String,
    label: String,
    color: Color,
    onBg: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = value,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = onBg.copy(alpha = 0.28f),
            letterSpacing = 0.3.sp
        )
    }
}

// ── 今日时间轴 ────────────────────────────────────────────────────────────────

@Composable
private fun TodayTimeline(
    timeline: List<TimelineEvent>,
    summaries: List<AppUsageSummary>,
    monitoredAppsWithIcon: List<AppInfo>,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onNavigateToAppList: () -> Unit,
    onNoteClick: (TimelineEvent.UsageEvent) -> Unit,
    cardBg: Color,
    onSurface: Color,
    outline: Color
) {
    val iconMap = remember(monitoredAppsWithIcon) {
        monitoredAppsWithIcon.associateBy { it.packageName }
    }
    if (timeline.isEmpty()) {
        EmptyTimelineContent(onNavigateToAppList = onNavigateToAppList, onSurface = onSurface)
        return
    }

    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = 8.dp, bottom = bottomPadding + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item { TimelineDayHeader(onSurface = onSurface) }

        items(timeline, key = { event ->
            when (event) {
                is TimelineEvent.UsageEvent -> "usage_${event.recordId}"
                is TimelineEvent.LimitResetEvent -> "reset_${event.resetId}"
            }
        }) { event ->
            TimelineEventNode(
                event = event,
                isLast = event == timeline.last(),
                iconMap = iconMap,
                onNoteClick = onNoteClick,
                cardBg = cardBg,
                onSurface = onSurface,
                outline = outline
            )
        }
    }
}

@Composable
private fun TimelineDayHeader(onSurface: Color) {
    val timeFormat = remember { SimpleDateFormat("MM月dd日", Locale.CHINESE) }
    val todayStr = remember { timeFormat.format(Date()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            todayStr,
            fontSize = 12.sp,
            color = onSurface.copy(alpha = 0.4f),
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.8.sp
        )
    }
}

// ── 时间轴节点 ────────────────────────────────────────────────────────────────

@Composable
private fun TimelineEventNode(
    event: TimelineEvent,
    isLast: Boolean,
    iconMap: Map<String, AppInfo>,
    onNoteClick: (TimelineEvent.UsageEvent) -> Unit,
    cardBg: Color,
    onSurface: Color,
    outline: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TimelineConnector(event = event, isLast = isLast, outline = outline)
        Box(modifier = Modifier.weight(1f).padding(bottom = 10.dp)) {
            when (event) {
                is TimelineEvent.UsageEvent -> UsageEventCard(
                    event = event,
                    appInfo = iconMap[event.packageName],
                    cardBg = cardBg,
                    onSurface = onSurface,
                    onClick = { onNoteClick(event) }
                )
                is TimelineEvent.LimitResetEvent -> LimitResetEventCard(event, iconMap[event.packageName], cardBg = cardBg, onSurface = onSurface)
            }
        }
    }
}

@Composable
private fun TimelineConnector(event: TimelineEvent, isLast: Boolean, outline: Color) {
    val dotColor = when (event) {
        is TimelineEvent.LimitResetEvent -> Color(0xFFE74C3C)
        is TimelineEvent.UsageEvent -> when {
            event.isLimitReached -> Color(0xFFF39C12)
            event.isOngoing      -> LogoGreen
            else                 -> outline.copy(alpha = 0.6f)
        }
    }
    val isReset = event is TimelineEvent.LimitResetEvent

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(20.dp)
    ) {
        // 连接点
        Box(
            modifier = Modifier
                .size(if (isReset) 12.dp else 8.dp)
                .clip(CircleShape)
                .background(dotColor),
            contentAlignment = Alignment.Center
        ) {
            if (isReset) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(7.dp))
            }
        }
        // 连接线
        if (!isLast) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(if (isReset) 42.dp else 54.dp)
                    .background(outline.copy(alpha = 0.3f))
            )
        }
    }
}

// ── 使用记录卡片（胶囊风格）──────────────────────────────────────────────────

@Composable
private fun UsageEventCard(
    event: TimelineEvent.UsageEvent,
    appInfo: AppInfo? = null,
    cardBg: Color,
    onSurface: Color,
    onClick: () -> Unit = {}
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val startStr = remember(event.startTime) { timeFormat.format(Date(event.startTime)) }
    val endStr = if (event.isOngoing) "进行中" else remember(event.endTime) { timeFormat.format(Date(event.endTime)) }

    val isMindful = event.purpose != null
    val accentColor = when {
        event.isLimitReached -> Color(0xFFE74C3C)
        event.isOngoing      -> LogoGreen
        else                 -> onSurface.copy(alpha = 0.25f)
    }
    val endReasonLabel = when (event.endReason) {
        UsageRecordEntity.EndReason.LIMIT_REACHED -> "⏰ 超时被拦截"
        UsageRecordEntity.EndReason.MANUAL        -> "手动结束"
        UsageRecordEntity.EndReason.AUTO_TIMEOUT  -> "后台超时结束"
        UsageRecordEntity.EndReason.APP_CLOSED    -> "关闭 App"
        else -> if (event.isOngoing) "使用中…" else "已结束"
    }

    // 胶囊卡片
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .then(
                if (event.isOngoing)
                    Modifier.border(1.dp, LogoGreen.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                else if (isMindful)
                    Modifier.border(1.dp, accentColor.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                else Modifier
            )
            .clickable(enabled = !event.isOngoing) { onClick() }
    ) {
        // 左侧彩色指示条
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                .background(
                    if (isMindful || event.isOngoing) accentColor
                    else onSurface.copy(alpha = 0.1f)
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // 主行：应用图标 + 名称 + 时长
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // 应用图标
                    if (appInfo?.icon != null) {
                        val bitmap = remember(appInfo.icon) { appInfo.icon.toBitmap().asImageBitmap() }
                        Image(
                            bitmap = bitmap,
                            contentDescription = event.appName,
                            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                                .background(accentColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Android, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                event.appName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isMindful || event.isOngoing) onSurface else onSurface.copy(alpha = 0.6f)
                            )
                            if (event.isOngoing) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(LogoGreen.copy(alpha = 0.2f))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        "进行中",
                                        fontSize = 9.sp,
                                        color = LogoGreen,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        Text(
                            "$startStr → $endStr",
                            fontSize = 11.sp,
                            color = onSurface.copy(alpha = if (isMindful) 0.4f else 0.28f)
                        )
                    }
                }

                // 右侧：时长 + 结束原因
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (event.note != null) {
                            Icon(
                                Icons.Default.EditNote,
                                contentDescription = "有备注",
                                tint = LogoGreen.copy(alpha = 0.5f),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        Text(
                            if (event.isOngoing)
                                formatSeconds((System.currentTimeMillis() - event.startTime) / 1000)
                            else formatSeconds(event.durationSeconds),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isMindful || event.isOngoing) accentColor else onSurface.copy(alpha = 0.3f),
                            letterSpacing = (-0.3).sp
                        )
                    }
                    Text(
                        endReasonLabel,
                        fontSize = 10.sp,
                        color = onSurface.copy(alpha = if (isMindful) 0.38f else 0.22f)
                    )
                }
            }

            // 使用目的行
            if (isMindful) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(LogoGreen.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(Icons.Default.SelfImprovement, contentDescription = null, tint = LogoGreen.copy(alpha = 0.8f), modifier = Modifier.size(11.dp))
                    Text(event.purpose!!, fontSize = 11.sp, color = LogoGreenBright, fontWeight = FontWeight.Medium)
                }
            }

            // 效果备注行
            if (event.note != null) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(onSurface.copy(alpha = 0.05f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        Icons.Default.EditNote,
                        contentDescription = null,
                        tint = onSurface.copy(alpha = 0.3f),
                        modifier = Modifier.size(11.dp).padding(top = 1.dp)
                    )
                    Text(
                        event.note,
                        fontSize = 11.sp,
                        color = onSurface.copy(alpha = 0.5f),
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

// ── 重新设定限额事件卡片 ──────────────────────────────────────────────────────

@Composable
private fun LimitResetEventCard(
    event: TimelineEvent.LimitResetEvent,
    appInfo: AppInfo? = null,
    cardBg: Color,
    onSurface: Color
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeStr = remember(event.resetTime) { timeFormat.format(Date(event.resetTime)) }
    val dangerColor = Color(0xFFE74C3C)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(dangerColor.copy(alpha = 0.08f))
            .border(1.dp, dangerColor.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                .background(dangerColor)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.BottomEnd) {
                if (appInfo?.icon != null) {
                    val bitmap = remember(appInfo.icon) { appInfo.icon.toBitmap().asImageBitmap() }
                    Image(bitmap = bitmap, contentDescription = event.appName,
                        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)))
                } else {
                    Box(
                        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                            .background(dangerColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Android, contentDescription = null, tint = dangerColor, modifier = Modifier.size(16.dp))
                    }
                }
                Box(
                    modifier = Modifier.size(12.dp).clip(CircleShape).background(dangerColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(7.dp))
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("又给自己放宽了", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = dangerColor.copy(alpha = 0.9f))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(dangerColor.copy(alpha = 0.15f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(event.appName, fontSize = 10.sp, color = dangerColor.copy(alpha = 0.85f))
                    }
                }
                Text(
                    "${event.oldDailyLimitMinutes}分钟 → ${event.newDailyLimitMinutes}分钟  (+${event.extendedMinutes}分钟)",
                    fontSize = 12.sp, color = dangerColor.copy(alpha = 0.65f)
                )
                Text(timeStr, fontSize = 10.sp, color = onSurface.copy(alpha = 0.28f))
            }
        }
    }
}

// ── 底部坞栏 ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppDockBar(
    monitoredApps: List<AppInfo>,
    summaries: List<AppUsageSummary>,
    onAppClick: (String) -> Unit,
    onAppLongClick: (AppInfo) -> Unit,
    onEmptySlotClick: () -> Unit,
    onOverflowClick: () -> Unit,
    modifier: Modifier = Modifier,
    dockBg: Color
) {
    val summaryMap = summaries.associateBy { it.packageName }
    val hasOverflow = monitoredApps.size >= DOCK_SLOTS
    val displayApps = monitoredApps.take(DOCK_SLOTS - 1)
    val overflowCount = monitoredApps.size - (DOCK_SLOTS - 1)
    val appSlots: List<AppInfo?> = List(DOCK_SLOTS - 1) { index -> displayApps.getOrNull(index) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = dockBg,
        shadowElevation = 32.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Box {
            Column {
                // 顶部绿色细分割线
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(LogoGreen.copy(alpha = 0.3f))
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    appSlots.forEach { app ->
                        if (app != null) {
                            DockAppItem(
                                app = app,
                                summary = summaryMap[app.packageName],
                                onClick = { onAppClick(app.packageName) },
                                onLongClick = { onAppLongClick(app) },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            DockEmptySlot(onClick = onEmptySlotClick, modifier = Modifier.weight(1f))
                        }
                    }
                    if (hasOverflow) {
                        DockOverflowSlot(count = overflowCount, onClick = onOverflowClick, modifier = Modifier.weight(1f))
                    } else {
                        DockEmptySlot(onClick = onEmptySlotClick, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DockOverflowSlot(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(modifier = Modifier.size(44.dp)) {
                drawCircle(
                    color = LogoGreen.copy(alpha = 0.3f),
                    radius = size.minDimension / 2f - 2.dp.toPx(),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f)
                    )
                )
            }
            Text(text = "+$count", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = LogoGreen)
        }
        Text(text = "更多", fontSize = 9.sp, color = LogoGreen.copy(alpha = 0.55f), maxLines = 1, textAlign = TextAlign.Center)
    }
}

@Composable
private fun DockEmptySlot(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(modifier = Modifier.size(44.dp)) {
                drawCircle(
                    color = LogoGreen.copy(alpha = 0.2f),
                    radius = size.minDimension / 2f - 2.dp.toPx(),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f)
                    )
                )
            }
            Icon(Icons.Default.Add, contentDescription = "添加", tint = LogoGreen.copy(alpha = 0.55f), modifier = Modifier.size(18.dp))
        }
        Text(text = "添加", fontSize = 9.sp, color = LogoGreen.copy(alpha = 0.45f), maxLines = 1, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockAppItem(
    app: AppInfo,
    summary: AppUsageSummary?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = summary?.dailyUsagePercent ?: 0f
    val arcColor = when {
        app.isUninstalled -> Color(0xFFB05A2A)
        progress >= 1f    -> Color(0xFFE74C3C)
        progress >= 0.8f  -> Color(0xFFF39C12)
        else              -> LogoGreen
    }
    val trackColor = if (app.isUninstalled) Color(0xFFB05A2A).copy(alpha = 0.15f)
                     else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)

    Column(
        modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.size(44.dp),
                color = trackColor,
                strokeWidth = 2.dp,
                strokeCap = StrokeCap.Round
            )
            if (progress > 0f) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(44.dp),
                    color = arcColor,
                    strokeWidth = 2.5.dp,
                    strokeCap = StrokeCap.Round
                )
            }
            if (app.icon != null) {
                val bitmap = remember(app.icon) { app.icon.toBitmap().asImageBitmap() }
                Image(bitmap = bitmap, contentDescription = app.appName, modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)))
            } else {
                Box(
                    modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(arcColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Android, contentDescription = app.appName, tint = arcColor, modifier = Modifier.size(16.dp))
                }
            }
        }
        Text(
            text = app.appName.take(4),
            fontSize = 9.sp,
            color = if (app.isUninstalled) Color(0xFFB05A2A).copy(alpha = 0.45f)
                    else LogoGreen.copy(alpha = 0.65f),
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

// ── App 选择器 Sheet ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerSheet(
    viewModel: HomeViewModel,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    sheetBg: Color,
    onSurface: Color
) {
    val installedApps by viewModel.filteredInstalledApps.collectAsState()
    val searchQuery by viewModel.sheetSearchQuery.collectAsState()
    val isLoading by viewModel.isLoadingApps.collectAsState()
    val focusManager = LocalFocusManager.current
    var pendingApp by remember { mutableStateOf<AppInfo?>(null) }
    val cs = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("添加监控应用", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = onSurface)
                val monitoredApps by viewModel.monitoredAppsWithIcon.collectAsState()
                val count = monitoredApps.size
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (count >= DOCK_MAX) Color(0xFF4A2800) else LogoGreen.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "$count / $DOCK_MAX",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (count >= DOCK_MAX) Color(0xFFFFB74D) else LogoGreenBright,
                    )
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSheetSearchQuery(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
                placeholder = { Text("搜索应用名称", color = onSurface.copy(alpha = 0.28f), fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = onSurface.copy(alpha = 0.28f)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearSheetSearch() }) {
                            Icon(Icons.Default.Close, contentDescription = "清除", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LogoGreen,
                    unfocusedBorderColor = cs.outline.copy(alpha = 0.5f),
                    focusedContainerColor = cs.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = cs.surfaceVariant.copy(alpha = 0.3f),
                    focusedTextColor = onSurface,
                    unfocusedTextColor = onSurface
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
            )

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LogoGreen, strokeWidth = 2.dp)
                }
            } else {
                val monitoredApps by viewModel.monitoredAppsWithIcon.collectAsState()
                val monitoredCount = monitoredApps.size
                val unmonitored = installedApps.filter { !it.isMonitored }
                val monitored = installedApps.filter { it.isMonitored }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    if (monitored.isNotEmpty()) {
                        item { SheetSectionHeader("已监控 (${monitored.size})", onSurface = onSurface) }
                        items(monitored, key = { it.packageName }) { app ->
                            SheetAppRow(appInfo = app, isAtLimit = false, onToggle = { viewModel.removeFromMonitor(app.packageName) }, onAdd = null, onSurface = onSurface)
                        }
                    }
                    if (unmonitored.isNotEmpty()) {
                        item {
                            SheetSectionHeader(
                                if (monitoredCount >= DOCK_MAX) "已达上限" else "所有应用 (${unmonitored.size})",
                                onSurface = onSurface
                            )
                        }
                        items(unmonitored, key = { it.packageName }) { app ->
                            SheetAppRow(
                                appInfo = app,
                                isAtLimit = monitoredCount >= DOCK_MAX,
                                onToggle = null,
                                onAdd = { pendingApp = app; focusManager.clearFocus() },
                                onSurface = onSurface
                            )
                        }
                    }
                }
            }
        }
    }

    pendingApp?.let { app ->
        QuickAddSheet(
            appInfo = app,
            sheetBg = sheetBg,
            onSurface = onSurface,
            onConfirm = { dailyMinutes, weeklyMinutes ->
                viewModel.addToMonitor(app, dailyMinutes, weeklyMinutes)
                pendingApp = null
            },
            onDismiss = { pendingApp = null }
        )
    }
}

@Composable
private fun SheetSectionHeader(title: String, onSurface: Color) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = onSurface.copy(alpha = 0.35f),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        letterSpacing = 0.8.sp
    )
}

@Composable
private fun SheetAppRow(
    appInfo: AppInfo,
    isAtLimit: Boolean,
    onToggle: (() -> Unit)?,
    onAdd: (() -> Unit)?,
    onSurface: Color
) {
    val isMonitored = appInfo.isMonitored
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isAtLimit || isMonitored) {
                if (isMonitored) onToggle?.invoke() else onAdd?.invoke()
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppIcon(drawable = appInfo.icon, modifier = Modifier.size(42.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = appInfo.appName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = when {
                    appInfo.isUninstalled -> onSurface.copy(alpha = 0.4f)
                    isAtLimit && !isMonitored -> onSurface.copy(alpha = 0.28f)
                    else -> onSurface
                }
            )
            if (isMonitored && !appInfo.isUninstalled) {
                Text("每日 ${appInfo.dailyLimitMinutes} 分钟", fontSize = 12.sp, color = LogoGreen)
            }
        }
        if (isMonitored) {
            IconButton(onClick = { onToggle?.invoke() }) {
                Icon(Icons.Default.RemoveCircleOutline, contentDescription = "移除", tint = Color(0xFFE74C3C), modifier = Modifier.size(22.dp))
            }
        } else {
            IconButton(onClick = { onAdd?.invoke() }, enabled = !isAtLimit) {
                Icon(Icons.Default.AddCircleOutline, contentDescription = "添加",
                    tint = if (isAtLimit) onSurface.copy(alpha = 0.18f) else LogoGreen, modifier = Modifier.size(22.dp))
            }
        }
    }
}

// ── 快速添加配置 Sheet ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddSheet(
    appInfo: AppInfo,
    sheetBg: Color,
    onSurface: Color,
    onConfirm: (dailyMinutes: Int, weeklyMinutes: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedPreset by remember { mutableIntStateOf(60) }
    var customDaily by remember { mutableIntStateOf(60) }
    var useCustom by remember { mutableStateOf(false) }
    val presets = listOf(15, 30, 60, 90, 120)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val cs = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppIcon(drawable = appInfo.icon, modifier = Modifier.size(48.dp))
                Column {
                    Text(appInfo.appName, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = onSurface)
                    Text("设置每日使用时限", fontSize = 13.sp, color = onSurface.copy(alpha = 0.38f))
                }
            }

            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.5f))

            Text("快速选择", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = onSurface.copy(alpha = 0.35f), letterSpacing = 0.8.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { preset ->
                    val isSelected = !useCustom && selectedPreset == preset
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) MindfulGreen40
                                else cs.surfaceVariant.copy(alpha = 0.6f)
                            )
                            .clickable { selectedPreset = preset; useCustom = false }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (preset >= 60) "${preset / 60}h" else "${preset}m",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else onSurface.copy(alpha = 0.45f)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().clickable { useCustom = !useCustom },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "自定义时限",
                    fontSize = 14.sp,
                    color = if (useCustom) LogoGreen else onSurface.copy(alpha = 0.38f),
                    fontWeight = if (useCustom) FontWeight.SemiBold else FontWeight.Normal
                )
                Icon(
                    if (useCustom) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = onSurface.copy(alpha = 0.35f),
                    modifier = Modifier.size(20.dp)
                )
            }
            if (useCustom) {
                LimitSlider(label = "每日最大使用", value = customDaily, range = 5..480, onValueChange = { customDaily = it })
            }

            // 确认按钮（胶囊风格）
            Button(
                onClick = {
                    val daily = if (useCustom) customDaily else selectedPreset
                    onConfirm(daily, 0)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MindfulGreen40)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                val daily = if (useCustom) customDaily else selectedPreset
                Text(
                    "添加监控 · ${if (daily >= 60) "${daily / 60}小时${if (daily % 60 > 0) "${daily % 60}分" else ""}" else "${daily}分钟"}/天",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ── 空态 ──────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyTimelineContent(onNavigateToAppList: () -> Unit, onSurface: Color) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(horizontal = 40.dp)
        ) {
            // 动态日出图标
            val infiniteTransition = rememberInfiniteTransition(label = "empty_anim")
            val sunGlow by infiniteTransition.animateFloat(
                initialValue = 0.06f, targetValue = 0.16f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "sun_glow"
            )
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF39C12).copy(alpha = sunGlow)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF39C12).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.WbSunny,
                        contentDescription = null,
                        tint = Color(0xFFF39C12).copy(alpha = 0.85f),
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
            Text(
                "今日清净如初",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = onSurface.copy(alpha = 0.75f),
                letterSpacing = 1.sp
            )
            Text(
                "添加你想要管控的 App\n每次打开都会在这里留下记录",
                fontSize = 13.sp,
                color = onSurface.copy(alpha = 0.32f),
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            // 引导按钮
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(LogoGreen.copy(alpha = 0.12f))
                    .border(1.dp, LogoGreen.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                    .clickable { onNavigateToAppList() }
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = LogoGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        "添加监控应用",
                        fontSize = 13.sp,
                        color = LogoGreen,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ── 备注编辑弹窗 ──────────────────────────────────────────────────────────────

@Composable
private fun NoteEditDialog(
    event: TimelineEvent.UsageEvent,
    cs: ColorScheme,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var noteText by remember(event.recordId) { mutableStateOf(event.note ?: "") }
    val focusRequester = remember { FocusRequester() }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val startStr = remember(event.startTime) { timeFormat.format(Date(event.startTime)) }
    val endStr = if (event.endTime > 0) timeFormat.format(Date(event.endTime)) else "进行中"

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        titleContentColor = cs.onSurface,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = event.appName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface
                )
                Text(
                    text = "$startStr – $endStr · ${formatSeconds(event.durationSeconds)}",
                    fontSize = 12.sp,
                    color = cs.onSurface.copy(alpha = 0.38f)
                )
                if (event.purpose != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(LogoGreen.copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.SelfImprovement,
                            contentDescription = null,
                            tint = LogoGreen,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = event.purpose,
                            fontSize = 12.sp,
                            color = LogoGreenBright,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "效果备注",
                    fontSize = 13.sp,
                    color = cs.onSurface.copy(alpha = 0.5f)
                )
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { if (it.length <= 100) noteText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = {
                        Text(
                            "这次使用感觉怎么样？",
                            fontSize = 14.sp,
                            color = cs.onSurface.copy(alpha = 0.28f)
                        )
                    },
                    minLines = 3,
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LogoGreen,
                        unfocusedBorderColor = cs.outlineVariant,
                        focusedTextColor = cs.onSurface,
                        unfocusedTextColor = cs.onSurface,
                        cursorColor = LogoGreen
                    )
                )
                Text(
                    text = "${noteText.length} / 100",
                    fontSize = 11.sp,
                    color = cs.onSurface.copy(alpha = 0.28f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(noteText.trim().ifBlank { null }) },
                colors = ButtonDefaults.textButtonColors(contentColor = LogoGreen)
            ) {
                Text("保存", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = cs.onSurface.copy(alpha = 0.4f))
            ) {
                Text("取消")
            }
        }
    )
}

// ── 权限警告 ──────────────────────────────────────────────────────────────────

@Composable
private fun PermissionWarningCard(
    permissionStatus: PermissionStatus,
    onNavigateToSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF2D1800).copy(alpha = 0.8f))
            .border(1.dp, Color(0xFFF39C12).copy(alpha = 0.25f), RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFF39C12), modifier = Modifier.size(18.dp))
            Text(
                "部分权限未开启，可能影响正常使用",
                fontSize = 13.sp,
                color = Color(0xFFFFB74D),
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onNavigateToSettings, contentPadding = PaddingValues(4.dp)) {
                Text("处理", fontSize = 12.sp, color = Color(0xFFF39C12), fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ── App 用量横向滑动区 ────────────────────────────────────────────────────────

@Composable
private fun AppUsageRow(
    monitoredApps: List<AppInfo>,
    summaries: List<AppUsageSummary>,
    onAppClick: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val summaryMap = summaries.associateBy { it.packageName }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 小标题
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "今日用量",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface.copy(alpha = 0.35f),
                letterSpacing = 0.8.sp
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            monitoredApps.forEach { app ->
                val summary = summaryMap[app.packageName]
                AppUsageMiniCard(
                    app = app,
                    summary = summary,
                    cs = cs,
                    onClick = { onAppClick(app.packageName) }
                )
            }
        }

        // 底部分隔线
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp)
                .height(1.dp)
                .background(cs.outlineVariant.copy(alpha = 0.3f))
        )
    }
}

@Composable
private fun AppUsageMiniCard(
    app: AppInfo,
    summary: AppUsageSummary?,
    cs: ColorScheme,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val usedSeconds = summary?.todaySeconds ?: 0L
    val limitSeconds = summary?.dailyLimitSeconds ?: (app.dailyLimitMinutes * 60L)
    val progress = if (limitSeconds > 0) (usedSeconds.toFloat() / limitSeconds).coerceAtMost(1f) else 0f

    val progressColor = when {
        app.isUninstalled -> Color(0xFFB05A2A)
        progress >= 1f    -> Color(0xFFE74C3C)
        progress >= 0.8f  -> Color(0xFFE8941A)
        usedSeconds > 0L  -> MindfulGreen40
        else              -> cs.outlineVariant.copy(alpha = 0.5f)
    }

    val animProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(600),
        label = "miniCardProg"
    )

    Box(
        modifier = Modifier
            .width(80.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(cs.surface)
            .clickable { onClick() }
            .padding(10.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // App图标 + 进度环
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.size(40.dp),
                    color = cs.outlineVariant.copy(alpha = 0.3f),
                    strokeWidth = 2.dp
                )
                if (progress > 0f) {
                    CircularProgressIndicator(
                        progress = { animProgress },
                        modifier = Modifier.size(40.dp),
                        color = progressColor,
                        strokeWidth = 2.5.dp,
                        strokeCap = StrokeCap.Round
                    )
                }
                // App 图标
                val icon = remember(app.packageName) {
                    try { context.packageManager.getApplicationIcon(app.packageName) }
                    catch (e: Exception) { app.icon }
                }
                if (icon != null) {
                    val bitmap = remember(icon) { icon.toBitmap().asImageBitmap() }
                    Image(bitmap = bitmap, contentDescription = app.appName, modifier = Modifier.size(26.dp).clip(RoundedCornerShape(6.dp)))
                } else {
                    Box(modifier = Modifier.size(26.dp).clip(RoundedCornerShape(6.dp)).background(progressColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Android, contentDescription = null, tint = progressColor, modifier = Modifier.size(14.dp))
                    }
                }
            }

            // 已用时长
            Text(
                text = if (usedSeconds > 0L) formatSeconds(usedSeconds) else "—",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (usedSeconds > 0L) cs.onSurface else cs.onSurface.copy(alpha = 0.2f),
                maxLines = 1,
                textAlign = TextAlign.Center,
                letterSpacing = (-0.3).sp
            )

            // App 名（截短）
            Text(
                text = app.appName.take(4),
                fontSize = 9.5.sp,
                color = cs.onSurface.copy(alpha = 0.38f),
                maxLines = 1,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}