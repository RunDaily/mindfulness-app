package com.life.mindfulnessapp.ui.home

import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onGloballyPositioned
import android.graphics.BlurMaskFilter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.activity.ComponentActivity
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

// ── 常量 ─────────────────────────────────────────────────────────────────────
private const val DOCK_MAX = 20

// ── 主入口 ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    // 必须用 Activity 作为 owner，确保与 MainActivity 里的 homeViewModel 是同一实例
    // 这样 MainActivity.handleNoteIntent 设置的 pendingHighlightId 才能被 HomeScreen 正确读到
    viewModel: HomeViewModel = hiltViewModel(LocalContext.current as ComponentActivity),
    onNavigateToAppDetail: (String) -> Unit = {},
    onNavigateToAppList: () -> Unit = {}
) {
    val summaries by viewModel.usageSummaries.collectAsState()
    val permStatus by viewModel.permissionStatus.collectAsState()
    val monitoredAppsWithIcon by viewModel.monitoredAppsWithIcon.collectAsState()
    val timeline by viewModel.todayTimeline.collectAsState()
    val todayMindfulCount by viewModel.todayMindfulCount.collectAsState()
    val ongoingSessionSeconds by viewModel.ongoingSessionSeconds.collectAsState()
    val context = LocalContext.current

    // HomeScreen 自身的备注编辑弹窗状态
    var editingEvent by remember { mutableStateOf<TimelineEvent.UsageEvent?>(null) }
    // 是否是「结束使用后自动触发」的备注弹窗（影响弹窗文案）
    var isAutoNotePrompt by remember { mutableStateOf(false) }
    // HomeScreen 内触发添加 Sheet 的状态（时间轴空态引导按钮）
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 使用结束后高亮引导的 recordId（只滚动+高亮，不自动弹窗）
    // 直接用 ViewModel StateFlow，不经过本地 remember —— 冷热启动都能即时生效
    val guidedRecordId by viewModel.pendingHighlightId.collectAsState()
    // 权限处理弹窗
    var showPermissionDialog by remember { mutableStateOf(false) }

    // 权限跳转 Launcher（返回后刷新权限状态）
    val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.refreshPermissions() }
    val usageLauncher   = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.refreshPermissions() }
    val batteryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.refreshPermissions() }

    LaunchedEffect(Unit) { viewModel.loadData() }

    val cs = MaterialTheme.colorScheme

    // ── 折叠进度：由列表滚动状态驱动 ─────────────────────────────────────
    // listState 提升到此处，同时传给 TodayTimeline 和浮层 header
    val listState = rememberLazyListState()

    // 上区域高度测量（用 px 来计算折叠进度）
    var headerHeightPx by remember { mutableFloatStateOf(0f) }

    // collapseProgress: 0f = 完全展开，1f = 完全折叠
    // 当列表第一个 item（header）还可见时，根据其滚动偏移量计算；
    // 一旦 header 完全滑出，progress 固定为 1f
    val collapseProgress by remember {
        derivedStateOf {
            val firstVisible = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            when {
                // 当前第一个可见 item 不是 header（index 0），说明已完全折叠
                firstVisible > 0 -> 1f
                // header 正在滚动中，按偏移量计算折叠进度
                headerHeightPx > 0f -> (offset.toFloat() / headerHeightPx).coerceIn(0f, 1f)
                else -> 0f
            }
        }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(cs.background)
    ) {

        // ── 下区域：以 LazyColumn 统一承载 header + 时间轴 ─────────────────
        HomeContentList(
            listState = listState,
            summaries = summaries,
            permStatus = permStatus,
            monitoredAppsWithIcon = monitoredAppsWithIcon,
            timeline = timeline,
            todayMindfulCount = todayMindfulCount,
            ongoingSessionSeconds = ongoingSessionSeconds,
            onHeaderHeightMeasured = { headerHeightPx = it },
            onNoteClick = {
                viewModel.consumeOpenNoteEvent()
                editingEvent = it
            },
            highlightRecordId = editingEvent?.recordId ?: guidedRecordId,
            onHighlightDone = { viewModel.consumeOpenNoteEvent() },
            onPermissionFix = { showPermissionDialog = true },
            onAddAppClick = {
                onNavigateToAppList()
            },
            onAppClick = onNavigateToAppDetail,
            cardBg = cs.surface,
            onSurface = cs.onSurface,
            outline = cs.outlineVariant
        )

        // ── 浮层：坍缩态 header（随折叠进度渐显）─────────────────────────
        CollapsedHeaderOverlay(
            collapseProgress = collapseProgress,
            permissionStatus = permStatus,
            summaries = summaries,
            todayMindfulCount = todayMindfulCount,
            cs = cs
        )

        // 备注编辑弹窗
        editingEvent?.let { event ->
            NoteEditDialog(
                event = event,
                cs = cs,
                isAutoPrompt = isAutoNotePrompt,
                onConfirm = { newNote ->
                    viewModel.updateRecordNote(event.recordId, newNote)
                    editingEvent = null
                    isAutoNotePrompt = false
                    viewModel.consumeOpenNoteEvent()
                },
                onDismiss = {
                    editingEvent = null
                    isAutoNotePrompt = false
                    viewModel.consumeOpenNoteEvent()
                }
            )
        }

        // 权限处理弹窗
        if (showPermissionDialog) {
            PermissionFixDialog(
                permissionStatus = permStatus,
                onDismiss = { showPermissionDialog = false },
                onGrantOverlay = {
                    overlayLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"))
                    )
                },
                onGrantUsage = {
                    usageLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                },
                onGrantBattery = {
                    batteryLauncher.launch(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}"))
                    )
                },
                onGrantNotification = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                    )
                }
            )
        }
    } // end Box
}

// ── 整体内容列表（上区域 header + 下区域时间轴，统一在一个 LazyColumn）────────

@Composable
private fun HomeContentList(
    listState: androidx.compose.foundation.lazy.LazyListState,
    summaries: List<AppUsageSummary>,
    permStatus: PermissionStatus,
    monitoredAppsWithIcon: List<AppInfo>,
    timeline: List<TimelineEvent>,
    todayMindfulCount: Int,
    /** (recordId, currentSessionSeconds) 进行中会话的实时有效秒数（已排除后台时间） */
    ongoingSessionSeconds: Pair<Long, Long>?,
    onHeaderHeightMeasured: (Float) -> Unit,
    onNoteClick: (TimelineEvent.UsageEvent) -> Unit,
    highlightRecordId: Long?,
    onHighlightDone: () -> Unit,
    onPermissionFix: () -> Unit,
    onAddAppClick: () -> Unit,
    onAppClick: (String) -> Unit,
    cardBg: Color,
    onSurface: Color,
    outline: Color
) {
    val iconMap = remember(monitoredAppsWithIcon) {
        monitoredAppsWithIcon.associateBy { it.packageName }
    }

    // 滚动到高亮条目
    LaunchedEffect(highlightRecordId) {
        if (highlightRecordId != null) {
            // header 占 index=0，时间轴 header 占 index=1，timeline items 从 index=2 开始
            val idx = timeline.indexOfFirst {
                it is TimelineEvent.UsageEvent && it.recordId == highlightRecordId
            }
            if (idx >= 0) {
                listState.animateScrollToItem(index = idx + 2)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // ── item 0：上区域（展开态 header）────────────────────────────────
        item(key = "home_header") {
            HomeExpandedHeader(
                permStatus = permStatus,
                summaries = summaries,
                todayMindfulCount = todayMindfulCount,
                monitoredAppsWithIcon = monitoredAppsWithIcon,
                onPermissionFix = onPermissionFix,
                onAddAppClick = onAddAppClick,
                onAppClick = onAppClick,
                onHeightMeasured = onHeaderHeightMeasured
            )
        }

        // ── item 1：时间轴日期 header ─────────────────────────────────────
        item(key = "timeline_day_header") {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                TimelineDayHeader(onSurface = onSurface)
            }
        }

        if (timeline.isEmpty()) {
            // 空态
            item(key = "timeline_empty") {
                EmptyTimelineContent(
                    hasMonitoredApps = monitoredAppsWithIcon.isNotEmpty(),
                    onSurface = onSurface
                )
            }
        } else {
            // ── items 2+：时间轴事件 ─────────────────────────────────────
            items(timeline, key = { event ->
                when (event) {
                    is TimelineEvent.UsageEvent -> "usage_${event.recordId}"
                    is TimelineEvent.LimitResetEvent -> "reset_${event.resetId}"
                }
            }) { event ->
                // 时间轴节点需要左侧 padding
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    // 若该条目正在进行中，将 ViewModel 的实时有效秒数注入，避免用 now-startTime 误算后台时间
                    val realtimeSeconds = if (event is TimelineEvent.UsageEvent && event.isOngoing &&
                        ongoingSessionSeconds?.first == event.recordId) {
                        ongoingSessionSeconds.second
                    } else null
                    TimelineEventNode(
                        event = event,
                        isLast = event == timeline.last(),
                        iconMap = iconMap,
                        onNoteClick = onNoteClick,
                        isHighlighted = (event as? TimelineEvent.UsageEvent)?.recordId == highlightRecordId && highlightRecordId != null,
                        onHighlightDone = onHighlightDone,
                        realtimeSeconds = realtimeSeconds,
                        cardBg = cardBg,
                        onSurface = onSurface,
                        outline = outline
                    )
                }
            }
        }
    }
}

// ── 上区域：展开态 header（作为 LazyColumn 的第一个 item）───────────────────

@Composable
private fun HomeExpandedHeader(
    permStatus: PermissionStatus,
    summaries: List<AppUsageSummary>,
    todayMindfulCount: Int,
    monitoredAppsWithIcon: List<AppInfo>,
    onPermissionFix: () -> Unit,
    onAddAppClick: () -> Unit,
    onAppClick: (String) -> Unit,
    onHeightMeasured: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                // 上报真实高度，用于计算折叠进度
                onHeightMeasured(coords.size.height.toFloat())
            }
    ) {
        HomeHeroCard(
            permissionStatus = permStatus,
            summaries = summaries,
            todayMindfulCount = todayMindfulCount
        )

        if (!permStatus.allGranted) {
            PermissionWarningCard(
                permissionStatus = permStatus,
                onFix = onPermissionFix
            )
        }

        AppMonitorRow(
            monitoredApps = monitoredAppsWithIcon,
            summaries = summaries,
            onAppClick = onAppClick,
            onAddClick = onAddAppClick
        )

        // 底部额外留白，让列表初始状态时上区域下方有自然过渡
        Spacer(modifier = Modifier.height(4.dp))
    }
}

// ── 浮层：坍缩态 header（随折叠进度渐显，吸附在屏幕顶部）─────────────────────

@Composable
private fun CollapsedHeaderOverlay(
    collapseProgress: Float,
    permissionStatus: PermissionStatus,
    summaries: List<AppUsageSummary>,
    todayMindfulCount: Int,
    cs: ColorScheme
) {
    // 只有折叠进度 > 0 才渲染，节省性能
    if (collapseProgress <= 0f) return

    val alpha = collapseProgress.coerceIn(0f, 1f)
    val onBg = cs.onBackground
    val totalTodaySec = summaries.sumOf { it.todaySeconds }
    val timeText = if (totalTodaySec == 0L) "—" else formatSeconds(totalTodaySec)
    val timeColor = if (totalTodaySec == 0L) onBg.copy(alpha = 0.2f) else LogoGreen
    val overLimitCount = summaries.count { it.dailyUsagePercent >= 1f }

    // 状态灯脉冲动画
    val infiniteTransition = rememberInfiniteTransition(label = "collapsed_pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "collapsed_dot_pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            // 毛玻璃感：用半透明背景模拟，Android 原生较难真正做磨砂，用主题色高透明度替代
            .background(cs.background.copy(alpha = 0.96f))
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 左：状态指示灯 + "今天"标签
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            if (permissionStatus.allGranted) LogoGreen.copy(alpha = dotAlpha)
                            else Color(0xFFF39C12).copy(alpha = dotAlpha)
                        )
                )
                Text(
                    text = "今天",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = onBg.copy(alpha = 0.45f),
                    letterSpacing = 0.3.sp
                )
            }

            // 中：今日使用时长（大字）
            Text(
                text = timeText,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = timeColor,
                letterSpacing = (-0.5).sp
            )

            // 右：觉察 + 超限统计
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CollapsedStatChip(
                    value = if (todayMindfulCount == 0) "—" else "$todayMindfulCount",
                    label = "觉察",
                    color = if (todayMindfulCount == 0) onBg.copy(alpha = 0.18f) else LogoGreen,
                    onBg = onBg
                )
                CollapsedStatChip(
                    value = if (overLimitCount == 0) "—" else "$overLimitCount",
                    label = "超限",
                    color = if (overLimitCount == 0) onBg.copy(alpha = 0.18f) else Color(0xFFE74C3C),
                    onBg = onBg
                )
            }
        }

        // 底部细分割线
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(0.5.dp)
                .background(cs.outlineVariant.copy(alpha = 0.4f * alpha))
        )
    }
}

// ── 顶部紧凑 Header ───────────────────────────────────────────────────────────

@Composable
private fun HomeHeroCard(
    permissionStatus: PermissionStatus,
    summaries: List<AppUsageSummary>,
    todayMindfulCount: Int
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
        // ── 第一行：标题行 ──────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
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

/** 坍缩态 header 中使用的精简统计 chip */
@Composable
private fun CollapsedStatChip(
    value: String,
    label: String,
    color: Color,
    onBg: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 9.sp,
            color = onBg.copy(alpha = 0.25f),
            letterSpacing = 0.3.sp
        )
    }
}

@Composable
internal fun TimelineDayHeader(onSurface: Color) {
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
internal fun TimelineEventNode(
    event: TimelineEvent,
    isLast: Boolean,
    iconMap: Map<String, AppInfo>,
    onNoteClick: (TimelineEvent.UsageEvent) -> Unit,
    isHighlighted: Boolean = false,
    onHighlightDone: () -> Unit = {},
    /** 进行中会话的实时有效秒数（已排除后台时间），null 表示非进行中或数据未就绪 */
    realtimeSeconds: Long? = null,
    cardBg: Color,
    onSurface: Color,
    outline: Color
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeStr = remember(event.timeMs) { timeFormat.format(Date(event.timeMs)) }

    // 参照拦截页结构：左侧纯轴列（点+线），右侧内容（时间+卡片）
    // IntrinsicSize.Min 让左侧轴列能通过 fillMaxHeight 获取右侧真实高度
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top
    ) {
        // 左侧：时间轴列，只负责圆点 + 固定高度连接线
        TimelineConnector(
            event = event,
            isLast = isLast,
            outline = outline
        )
        // 右侧：时间标签 + 卡片，统一在内容区
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 4.dp else 16.dp)
        ) {
            // 时间标签（内容区顶部，与圆点天然对齐）
            Text(
                text = timeStr,
                fontSize = 11.sp,
                color = onSurface.copy(alpha = 0.35f),
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.2).sp,
                modifier = Modifier.padding(bottom = 5.dp)
            )
            when (event) {
                is TimelineEvent.UsageEvent -> UsageEventCard(
                    event = event,
                    appInfo = iconMap[event.packageName],
                    cardBg = cardBg,
                    onSurface = onSurface,
                    isHighlighted = isHighlighted,
                    onHighlightDone = onHighlightDone,
                    realtimeSeconds = realtimeSeconds,
                    onClick = { onNoteClick(event) }
                )
                is TimelineEvent.LimitResetEvent -> LimitResetEventCard(event, iconMap[event.packageName], cardBg = cardBg, onSurface = onSurface)
            }
        }
    }
}

@Composable
internal fun TimelineConnector(
    event: TimelineEvent,
    isLast: Boolean,
    outline: Color
) {
    val dotColor = when (event) {
        is TimelineEvent.LimitResetEvent -> Color(0xFFE74C3C)
        is TimelineEvent.UsageEvent -> when {
            event.isLimitReached          -> Color(0xFFF39C12)
            event.isOngoing               -> LogoGreen
            event.isInterceptedAndQuit()  -> LogoGreen.copy(alpha = 0.5f)
            event.purpose != null         -> LogoGreen
            else                          -> outline.copy(alpha = 0.55f)
        }
    }
    val isReset = event is TimelineEvent.LimitResetEvent

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(24.dp)
            .fillMaxHeight()
    ) {
        // 圆点，偏移 5dp 与右侧时间文字首行基线对齐
        if (isReset) {
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(dotColor)
                    .offset(y = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(6.dp))
            }
        } else {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor)
                    .offset(y = 5.dp)
            )
        }
        // 连接线：配合 fillMaxHeight + IntrinsicSize.Min 自动适应卡片高度
        if (!isLast) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .weight(1f)
                    .offset(y = 5.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                outline.copy(alpha = 0.65f),
                                outline.copy(alpha = 0.30f)
                            )
                        )
                    )
            )
        }
    }
}

// ── 使用记录卡片（分场景调度） ───────────────────────────────────────────────

/**
 * 判断是否为「拦截成功退出」场景：
 * - 没有填写使用目的（purpose == null）
 * - 不是进行中
 * - 不是超时拦截（那是另一种场景）
 * - 使用时长极短（≤ 20秒），说明用户看到拦截页后迅速退出了
 */
internal fun TimelineEvent.UsageEvent.isInterceptedAndQuit(): Boolean {
    if (isOngoing) return false
    if (isLimitReached) return false
    if (purpose != null) return false
    return durationSeconds <= 20L
}

@Composable
internal fun UsageEventCard(
    event: TimelineEvent.UsageEvent,
    appInfo: AppInfo? = null,
    cardBg: Color,
    onSurface: Color,
    isHighlighted: Boolean = false,
    onHighlightDone: () -> Unit = {},
    /** 进行中会话的实时有效秒数（已排除后台时间），null 时回退到旧逻辑 */
    realtimeSeconds: Long? = null,
    onClick: () -> Unit = {}
) {
    if (event.isInterceptedAndQuit()) {
        // 场景1：拦截后退出 → 简洁自律风格卡片
        InterceptedQuitCard(
            event = event,
            appInfo = appInfo,
            cardBg = cardBg,
            onSurface = onSurface
        )
    } else {
        // 场景2：有目的使用 / 进行中 / 超时拦截 / 普通使用 → 标准详情卡片
        UsageDetailCard(
            event = event,
            appInfo = appInfo,
            cardBg = cardBg,
            onSurface = onSurface,
            isHighlighted = isHighlighted,
            onHighlightDone = onHighlightDone,
            realtimeSeconds = realtimeSeconds,
            onClick = onClick
        )
    }
}

// ── 场景1：拦截成功退出卡片（勋章风格·金色·成就感）────────────────────────

// 勋章金色常量（与 DismissCeremonyOverlayView 保持一致）
private val MedalGold      = Color(0xFFFFD166)
private val MedalGoldLight = Color(0xFFFFF0A0)
private val MedalGoldDim   = Color(0xFFB8860B)

@Composable
internal fun InterceptedQuitCard(
    event: TimelineEvent.UsageEvent,
    appInfo: AppInfo? = null,
    cardBg: Color,
    onSurface: Color
) {
    // 卡片：金色左侧条 + 极淡金色边框 + 背景极淡金色晕
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .border(1.dp, MedalGold.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
    ) {
        // 左侧金色指示条
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(MedalGoldLight.copy(alpha = 0.80f), MedalGold.copy(alpha = 0.55f))
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 12.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 应用图标（小号，低调，带金色光晕）
            Box(contentAlignment = Alignment.Center) {
                if (appInfo?.icon != null) {
                    val bitmap = remember(appInfo.icon) { appInfo.icon.toBitmap().asImageBitmap() }
                    Image(
                        bitmap = bitmap,
                        contentDescription = event.appName,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .alpha(0.55f)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(onSurface.copy(alpha = 0.05f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Android,
                            contentDescription = null,
                            tint = onSurface.copy(alpha = 0.20f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // 中间：App名 + 时间
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.appName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = onSurface.copy(alpha = 0.45f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "没有进去",
                    fontSize = 11.sp,
                    color = onSurface.copy(alpha = 0.28f)
                )
            }

            // 右侧：圆形勋章图标（与退出仪式视觉一致）+ 文字
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 小圆形勋章 Canvas
                androidx.compose.foundation.Canvas(modifier = Modifier.size(28.dp)) {
                    val w = size.width
                    val h = size.height
                    val cx = w / 2f
                    val cy = h / 2f
                    val medalR = w * 0.40f

                    // 外层光晕
                    drawCircle(
                        brush = androidx.compose.ui.graphics.Brush.radialGradient(
                            colors = listOf(
                                MedalGold.copy(alpha = 0.22f),
                                MedalGold.copy(alpha = 0.06f),
                                Color.Transparent
                            ),
                            center = androidx.compose.ui.geometry.Offset(cx, cy),
                            radius = medalR * 1.5f
                        ),
                        radius = medalR * 1.5f,
                        center = androidx.compose.ui.geometry.Offset(cx, cy)
                    )

                    // 勋章圆形主体（径向渐变模拟金属质感）
                    drawCircle(
                        brush = androidx.compose.ui.graphics.Brush.radialGradient(
                            colors = listOf(
                                MedalGoldLight,
                                MedalGold,
                                MedalGoldDim.copy(alpha = 0.90f)
                            ),
                            center = androidx.compose.ui.geometry.Offset(cx * 0.75f, cy * 0.72f),
                            radius = medalR * 2f
                        ),
                        radius = medalR,
                        center = androidx.compose.ui.geometry.Offset(cx, cy)
                    )

                    // 亮边描边
                    drawCircle(
                        color = MedalGoldLight.copy(alpha = 0.65f),
                        radius = medalR - 0.8f.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(cx, cy),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.8f.dp.toPx())
                    )

                    // 对勾（三层）
                    val checkPath = Path().apply {
                        moveTo(cx - w * 0.185f, cy + h * 0.025f)
                        lineTo(cx - w * 0.010f, cy + h * 0.185f)
                        lineTo(cx + w * 0.240f, cy - h * 0.145f)
                    }
                    // 底层深色
                    drawPath(
                        checkPath,
                        color = MedalGoldDim.copy(alpha = 0.85f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 2.6f.dp.toPx(), cap = StrokeCap.Round,
                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                        )
                    )
                    // 主层亮金
                    drawPath(
                        checkPath,
                        color = MedalGoldLight,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 1.6f.dp.toPx(), cap = StrokeCap.Round,
                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                        )
                    )
                }

                // 文字
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "克制",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MedalGold.copy(alpha = 0.90f),
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "已解锁",
                        fontSize = 9.5.sp,
                        color = MedalGold.copy(alpha = 0.55f),
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

// ── 场景2：有目的使用 / 普通使用 / 进行中 / 超时拦截卡片（详细信息）─────────

@Composable
internal fun UsageDetailCard(
    event: TimelineEvent.UsageEvent,
    appInfo: AppInfo? = null,
    cardBg: Color,
    onSurface: Color,
    isHighlighted: Boolean = false,
    onHighlightDone: () -> Unit = {},
    /** 进行中会话的实时有效秒数（已排除后台时间），null 时回退到 durationSeconds */
    realtimeSeconds: Long? = null,
    onClick: () -> Unit = {}
) {
    // ── 高亮动画：光沿边框跑一圈，终点落在左侧绿条位置 ──────────────
    // 用 Animatable 确保从 0 开始播放，而不是瞬间跳到终态
    val sweepAnim = remember { Animatable(0f) }
    LaunchedEffect(isHighlighted) {
        if (isHighlighted) {
            sweepAnim.snapTo(0f)           // 确保从头开始
            sweepAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 2000, easing = LinearEasing)
            )
            // 动画完成后通知 ViewModel 消费高亮，防止滑动回来重播
            onHighlightDone()
        } else {
            sweepAnim.snapTo(0f)           // 高亮取消立即归零
        }
    }
    val sweepProgress = sweepAnim.value
    // 动画是否已完成（> 0.98 认为跑完了）
    val sweepDone = sweepProgress > 0.98f
    // 绿条在最后 20% 进度淡入（光快到终点时绿条渐显）
    val indicatorAlpha = if (!isHighlighted) 1f
    else if (sweepDone) 1f
    else ((sweepProgress - 0.80f) / 0.20f).coerceIn(0f, 1f)
    // pill 闪光：动画完成后常亮
    val highlightAlpha = if (!isHighlighted) 0f
    else if (sweepDone) 1f
    else ((sweepProgress - 0.80f) / 0.20f).coerceIn(0f, 1f)

    val isMindful = event.purpose != null
    val accentColor = when {
        event.isLimitReached -> Color(0xFFE74C3C)
        event.isOngoing      -> LogoGreen
        isMindful            -> LogoGreen
        else                 -> onSurface.copy(alpha = 0.22f)
    }

    // 结束原因 badge 文字 + 颜色
    val endReasonLabel = when (event.endReason) {
        UsageRecordEntity.EndReason.LIMIT_REACHED -> "⏰ 超时拦截"
        UsageRecordEntity.EndReason.MANUAL        -> "手动离开"
        UsageRecordEntity.EndReason.AUTO_TIMEOUT  -> "后台超时"
        UsageRecordEntity.EndReason.APP_CLOSED    -> "已关闭"
        else -> if (event.isOngoing) "使用中" else "已结束"
    }
    val endReasonColor = when {
        event.isLimitReached -> Color(0xFFF39C12)
        event.isOngoing      -> LogoGreen
        else                 -> onSurface.copy(alpha = 0.30f)
    }

    // 进行中状态：左侧彩条更宽以突出
    val indicatorWidth = if (event.isOngoing || isMindful) 4.dp else 3.dp
    val indicatorColor = when {
        event.isOngoing      -> LogoGreen
        event.isLimitReached -> Color(0xFFE74C3C)
        isMindful            -> LogoGreen.copy(alpha = 0.7f)
        else                 -> onSurface.copy(alpha = 0.08f)
    }

    // 胶囊卡片：单层 Box，用 drawWithContent 在内容之上叠加边框光效
    // 卡片本身完全静止，光只在边框线上流动
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .then(
                when {
                    event.isOngoing      -> Modifier.border(1.dp, LogoGreen.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    event.isLimitReached -> Modifier.border(1.dp, Color(0xFFE74C3C).copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                    isMindful            -> Modifier.border(1.dp, LogoGreen.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
                    else                 -> Modifier
                }
            )
            // 高亮时：在内容之上叠加流光描边（drawWithContent 最后绘制）
            .then(if (isHighlighted) Modifier.drawWithContent {
                drawContent()   // 先画所有卡片内容

                // 动画完成后停止绘制光效（绿条已经亮起，不需要再画描边）
                if (sweepDone) return@drawWithContent

                val cornerRadius = 14.dp.toPx()
                val strokeW      = 2f.dp.toPx()
                val inset        = strokeW / 2f

                // ── 路径：从左边缘中点出发，顺时针跑一整圈回到原点 ──
                val fullPath = android.graphics.Path().apply {
                    // 从左边缘中点开始，顺时针
                    val midY = size.height / 2f
                    moveTo(inset, midY)
                    // 上半段：从左中 → 左上角弧 → 顶边 → 右上角弧
                    arcTo(
                        android.graphics.RectF(inset, inset, inset + cornerRadius * 2, inset + cornerRadius * 2),
                        180f, 90f, false
                    )
                    lineTo(size.width - inset - cornerRadius, inset)
                    arcTo(
                        android.graphics.RectF(size.width - inset - cornerRadius * 2, inset, size.width - inset, inset + cornerRadius * 2),
                        270f, 90f, false
                    )
                    // 右边：右上 → 右下
                    lineTo(size.width - inset, size.height - inset - cornerRadius)
                    arcTo(
                        android.graphics.RectF(size.width - inset - cornerRadius * 2, size.height - inset - cornerRadius * 2, size.width - inset, size.height - inset),
                        0f, 90f, false
                    )
                    // 底边：右下 → 左下
                    lineTo(inset + cornerRadius, size.height - inset)
                    arcTo(
                        android.graphics.RectF(inset, size.height - inset - cornerRadius * 2, inset + cornerRadius * 2, size.height - inset),
                        90f, 90f, false
                    )
                    // 回到起点：左下角弧 → 左边中点
                    lineTo(inset, midY)
                }
                val pm      = android.graphics.PathMeasure(fullPath, false)
                val pathLen = pm.length

                // 1. 轨道（暗绿色，光跑过的轨迹）
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawPath(fullPath, android.graphics.Paint().apply {
                        isAntiAlias = true
                        style       = android.graphics.Paint.Style.STROKE
                        strokeWidth = strokeW
                        color       = LogoGreen.copy(alpha = 0.18f).toArgb()
                    })
                }

                // 2. 流光（带拖尾，沿路径匀速推进）
                if (sweepProgress > 0f) {
                    val tailLen  = pathLen * 0.18f
                    val headDist = sweepProgress * pathLen
                    val tailPath = android.graphics.Path()
                    pm.getSegment((headDist - tailLen).coerceAtLeast(0f), headDist, tailPath, true)

                    drawIntoCanvas { canvas ->
                        // 外层光晕
                        canvas.nativeCanvas.drawPath(tailPath, android.graphics.Paint().apply {
                            isAntiAlias = true
                            style       = android.graphics.Paint.Style.STROKE
                            strokeWidth = strokeW * 5f
                            strokeCap   = android.graphics.Paint.Cap.ROUND
                            maskFilter  = BlurMaskFilter(strokeW * 4f, BlurMaskFilter.Blur.NORMAL)
                            color       = LogoGreen.copy(alpha = 0.55f).toArgb()
                        })
                        // 亮绿芯
                        canvas.nativeCanvas.drawPath(tailPath, android.graphics.Paint().apply {
                            isAntiAlias = true
                            style       = android.graphics.Paint.Style.STROKE
                            strokeWidth = strokeW * 1.4f
                            strokeCap   = android.graphics.Paint.Cap.ROUND
                            color       = LogoGreen.copy(alpha = 1f).toArgb()
                        })
                        // 白色高光点
                        canvas.nativeCanvas.drawPath(tailPath, android.graphics.Paint().apply {
                            isAntiAlias = true
                            style       = android.graphics.Paint.Style.STROKE
                            strokeWidth = strokeW * 0.6f
                            strokeCap   = android.graphics.Paint.Cap.ROUND
                            color       = Color.White.copy(alpha = 0.9f).toArgb()
                        })
                    }
                }
            } else Modifier)
            .clickable(enabled = !event.isOngoing) { onClick() }
    ) {
        // 左侧彩色指示条
        // 高亮动画期间：随光点接近左边缘而淡入（indicatorAlpha 0→1）
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(indicatorWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                .background(indicatorColor.copy(alpha = indicatorColor.alpha * indicatorAlpha))
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // ── 主行：图标 + 名称/时间段 + 时长大字 ────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左：图标 + App名 + 时间段
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
                            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(accentColor.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Android, contentDescription = null, tint = accentColor, modifier = Modifier.size(17.dp))
                        }
                    }

                    // App 名 + 进行中 badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = event.appName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isMindful || event.isOngoing || event.isLimitReached)
                                onSurface else onSurface.copy(alpha = 0.55f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (event.isOngoing) {
                            // 进行中 badge：带绿色背景、更显眼
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(LogoGreen.copy(alpha = 0.18f))
                                    .border(1.dp, LogoGreen.copy(alpha = 0.35f), RoundedCornerShape(5.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "进行中",
                                    fontSize = 9.5.sp,
                                    color = LogoGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // 右：时长大字（核心数据，最显眼）+ 结束原因小字
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    // 时长：核心数字，字号调大
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        // 有备注时显示备注角标
                        if (event.note != null) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(LogoGreen.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.EditNote,
                                    contentDescription = "有备注",
                                    tint = LogoGreen.copy(alpha = 0.7f),
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                        Text(
                            text = if (event.isOngoing)
                                formatSeconds(realtimeSeconds ?: ((System.currentTimeMillis() - event.startTime) / 1000))
                            else formatSeconds(event.durationSeconds),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (event.isOngoing || event.isLimitReached || isMindful)
                                accentColor else onSurface.copy(alpha = 0.28f),
                            letterSpacing = (-0.5).sp
                        )
                    }
                    // 结束原因：小 badge 形式，颜色语义化
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(endReasonColor.copy(alpha = 0.1f))
                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                    ) {
                        Text(
                            text = endReasonLabel,
                            fontSize = 9.5.sp,
                            color = endReasonColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // ── 目的 pill（绿色填充，语义：主动·有意识） ────────────────────────
            if (isMindful) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(LogoGreen.copy(alpha = 0.12f))
                        .border(1.dp, LogoGreen.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        Icons.Default.SelfImprovement,
                        contentDescription = null,
                        tint = LogoGreen.copy(alpha = 0.85f),
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = event.purpose!!,
                        fontSize = 11.sp,
                        color = LogoGreenBright,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // ── 备注 pill（中性背景 + 斜体文字，语义：事后反思） ─────────────────
            if (event.note != null) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(onSurface.copy(alpha = 0.06f))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        Icons.Default.EditNote,
                        contentDescription = null,
                        tint = onSurface.copy(alpha = 0.35f),
                        modifier = Modifier.size(11.dp).padding(top = 1.dp)
                    )
                    Text(
                        text = event.note,
                        fontSize = 11.sp,
                        color = onSurface.copy(alpha = 0.55f),
                        lineHeight = 16.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            } else if (!event.isOngoing) {
                // ── 无备注时：虚线边框 pill 引导 ────────────────────────────────
                // isHighlighted 时：绿色填充背景 + 白色文字，呼吸脉动；否则：低调灰色
                val pillBg = if (isHighlighted)
                    LogoGreen.copy(alpha = 0.12f + 0.08f * highlightAlpha)
                else
                    Color.Transparent
                val pillBorderColor = if (isHighlighted)
                    LogoGreen.copy(alpha = 0.45f + 0.35f * highlightAlpha)
                else
                    onSurface.copy(alpha = 0.14f)
                val pillTextColor = if (isHighlighted)
                    LogoGreen.copy(alpha = 0.75f + 0.25f * highlightAlpha)
                else
                    onSurface.copy(alpha = 0.28f)
                val pillIconColor = if (isHighlighted)
                    LogoGreen.copy(alpha = 0.7f + 0.3f * highlightAlpha)
                else
                    onSurface.copy(alpha = 0.22f)

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(pillBg)
                        .border(
                            width = if (isHighlighted) 1.dp else 1.dp,
                            color = pillBorderColor,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = pillIconColor,
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        text = "记录此刻感受",
                        fontSize = 10.5.sp,
                        color = pillTextColor,
                        fontWeight = if (isHighlighted) FontWeight.Medium else FontWeight.Normal
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
    val dangerColor = Color(0xFFE74C3C)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(14.dp))
            .background(dangerColor.copy(alpha = 0.07f))
            .border(1.dp, dangerColor.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
    ) {
        // 左侧指示条（与 UsageEventCard 对齐）
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(4.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                .background(dangerColor.copy(alpha = 0.8f))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App 图标 + 警告角标
            Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.BottomEnd) {
                if (appInfo?.icon != null) {
                    val bitmap = remember(appInfo.icon) { appInfo.icon.toBitmap().asImageBitmap() }
                    Image(
                        bitmap = bitmap,
                        contentDescription = event.appName,
                        modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(dangerColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Android,
                            contentDescription = null,
                            tint = dangerColor,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(dangerColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(8.dp))
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // 标题行：「调整了限制」+ App 名 chip
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "调整了限制",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = dangerColor.copy(alpha = 0.9f)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(dangerColor.copy(alpha = 0.12f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = event.appName,
                            fontSize = 10.sp,
                            color = dangerColor.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                // 限制变化：数值对比更直观
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "${event.oldDailyLimitMinutes}分",
                        fontSize = 12.sp,
                        color = dangerColor.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "→",
                        fontSize = 10.sp,
                        color = dangerColor.copy(alpha = 0.3f)
                    )
                    Text(
                        text = "${event.newDailyLimitMinutes}分",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = dangerColor.copy(alpha = 0.75f)
                    )
                    // 延长了多少分钟 badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(dangerColor.copy(alpha = 0.1f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "+${event.extendedMinutes}分",
                            fontSize = 10.sp,
                            color = dangerColor.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// ── 空态 ──────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyTimelineContent(hasMonitoredApps: Boolean, onSurface: Color) {
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
                text = if (hasMonitoredApps)
                    "今天还没有打开任何受监控的 App"
                else
                    "前往「设置 → 管理监控应用」\n添加想要管控的 App",
                fontSize = 13.sp,
                color = onSurface.copy(alpha = 0.32f),
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── 备注编辑弹窗 ──────────────────────────────────────────────────────────────

@Composable
private fun NoteEditDialog(
    event: TimelineEvent.UsageEvent,
    cs: ColorScheme,
    isAutoPrompt: Boolean = false,
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
                // 自动触发时显示引导性标题
                if (isAutoPrompt) {
                    Text(
                        text = "记录此刻感受",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface
                    )
                    Text(
                        text = "使用结束了，花一秒写下感受",
                        fontSize = 12.sp,
                        color = cs.onSurface.copy(alpha = 0.45f)
                    )
                } else {
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
                }
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
                if (!isAutoPrompt) {
                    Text(
                        text = "效果备注",
                        fontSize = 13.sp,
                        color = cs.onSurface.copy(alpha = 0.5f)
                    )
                }
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
    onFix: () -> Unit = {}
) {
    // 使用 MaterialTheme 警告色语义，跟随日间/夜间主题
    val warningColor = Color(0xFFE8941A)   // 琥珀橙：夜间饱和度适中，日间同样清晰
    val cs = MaterialTheme.colorScheme
    // 背景：主题 errorContainer 色调模仿（夜间=深棕调，日间=浅橙调）
    // 这里用 warningColor 混合 surface，兼容两套主题
    val warnBg = cs.surface.copy(alpha = 0f).let {
        // 夜间模式 surface 是深蓝灰 → 混入琥珀橙得深棕；日间 surface 是白色 → 混入得浅橙
        warningColor.copy(alpha = if (cs.surface.red < 0.2f) 0.15f else 0.1f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(cs.surface)  // 底色跟随主题
            .background(warnBg)      // 警告色叠加
            .border(1.dp, warningColor.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = warningColor,
                modifier = Modifier.size(18.dp)
            )
            Text(
                "部分权限未开启，可能影响正常使用",
                fontSize = 13.sp,
                color = warningColor.copy(alpha = 0.9f),
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onFix, contentPadding = PaddingValues(4.dp)) {
                Text("处理", fontSize = 12.sp, color = warningColor, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ── 权限处理弹窗 ──────────────────────────────────────────────────────────────

@Composable
private fun PermissionFixDialog(
    permissionStatus: PermissionStatus,
    onDismiss: () -> Unit,
    onGrantOverlay: () -> Unit,
    onGrantUsage: () -> Unit,
    onGrantBattery: () -> Unit,
    onGrantNotification: () -> Unit
) {
    val warningColor = Color(0xFFE8941A)
    val cs = MaterialTheme.colorScheme

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(cs.surface)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 标题
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(warningColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = warningColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            "权限未开启",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = cs.onSurface
                        )
                        Text(
                            "请开启以下权限以保证功能正常",
                            fontSize = 12.sp,
                            color = cs.onSurface.copy(alpha = 0.45f)
                        )
                    }
                }

                HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.4f))

                // 权限列表
                if (!permissionStatus.hasOverlay) {
                    PermissionFixRow(
                        icon = Icons.Default.Layers,
                        title = "悬浮窗权限",
                        desc = "用于显示心锚拦截浮层",
                        accentColor = warningColor,
                        cs = cs,
                        onGrant = onGrantOverlay
                    )
                }
                if (!permissionStatus.hasUsageStats) {
                    PermissionFixRow(
                        icon = Icons.Default.QueryStats,
                        title = "使用情况访问",
                        desc = "用于统计各 App 使用时长",
                        accentColor = warningColor,
                        cs = cs,
                        onGrant = onGrantUsage
                    )
                }
                if (!permissionStatus.hasBatteryOptimizationIgnored) {
                    PermissionFixRow(
                        icon = Icons.Default.BatteryFull,
                        title = "忽略电池优化（可选）",
                        desc = "保证后台服务持续运行",
                        accentColor = warningColor,
                        cs = cs,
                        onGrant = onGrantBattery
                    )
                }
                if (!permissionStatus.hasNotification) {
                    PermissionFixRow(
                        icon = Icons.Default.Notifications,
                        title = "通知权限（可选）",
                        desc = "用于发送使用提醒通知",
                        accentColor = warningColor,
                        cs = cs,
                        onGrant = onGrantNotification
                    )
                }

                // 关闭按钮
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.textButtonColors(contentColor = cs.onSurface.copy(alpha = 0.45f))
                ) {
                    Text("关闭", fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun PermissionFixRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String,
    accentColor: Color,
    cs: ColorScheme,
    onGrant: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accentColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = cs.onSurface)
            Text(desc, fontSize = 11.sp, color = cs.onSurface.copy(alpha = 0.4f))
        }
        TextButton(
            onClick = onGrant,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text("去开启", fontSize = 12.sp, color = accentColor, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── 监控 App 槽位区（融合了原 Dock 的槽位 + 今日用量卡片）─────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppMonitorRow(
    monitoredApps: List<AppInfo>,
    summaries: List<AppUsageSummary>,
    onAppClick: (String) -> Unit,
    onAddClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val summaryMap = summaries.associateBy { it.packageName }
    val accentGreen = LogoGreen

    Column(modifier = Modifier.fillMaxWidth()) {
        // 标题行：「监控应用」+ 右侧「管理 →」按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (monitoredApps.isEmpty()) "监控应用" else "监控应用 · ${monitoredApps.size}",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface.copy(alpha = 0.35f),
                letterSpacing = 0.8.sp
            )
            // 点击「管理 →」，跳转到应用管理页
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onAddClick() }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        "管理",
                        fontSize = 11.sp,
                        color = accentGreen.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "管理",
                        tint = accentGreen.copy(alpha = 0.55f),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 已有的监控 App 卡片（长按触发添加 Sheet）
            monitoredApps.forEach { app ->
                val summary = summaryMap[app.packageName]
                AppMonitorSlotCard(
                    app = app,
                    summary = summary,
                    cs = cs,
                    accentGreen = accentGreen,
                    onClick = { onAppClick(app.packageName) },
                    onLongClick = onAddClick
                )
            }

            // 末尾「+ 添加槽位」卡片
            AddSlotCard(
                cs = cs,
                accentGreen = accentGreen,
                isEmpty = monitoredApps.isEmpty(),
                onClick = onAddClick
            )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppMonitorSlotCard(
    app: AppInfo,
    summary: AppUsageSummary?,
    cs: ColorScheme,
    accentGreen: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit
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
        label = "slotProg_${app.packageName}"
    )

    Box(
        modifier = Modifier
            .width(80.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(cs.surface)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(10.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // App 图标 + 进度环
            Box(contentAlignment = Alignment.Center) {
                // 底层轨道环
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.size(40.dp),
                    color = cs.outlineVariant.copy(alpha = 0.3f),
                    strokeWidth = 2.dp
                )
                // 进度弧
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
                    Image(
                        bitmap = bitmap,
                        contentDescription = app.appName,
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(progressColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Android,
                            contentDescription = null,
                            tint = progressColor,
                            modifier = Modifier.size(14.dp)
                        )
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

            // App 名（截短，用 Ellipsis 优雅截断，兼容中英文）
            Text(
                text = app.appName,
                fontSize = 9.5.sp,
                color = cs.onSurface.copy(alpha = 0.38f),
                maxLines = 1,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AddSlotCard(
    cs: ColorScheme,
    accentGreen: Color,
    isEmpty: Boolean,
    onClick: () -> Unit
) {
    // 空状态时：脉冲光圈动画，吸引注意力
    val infiniteTransition = rememberInfiniteTransition(label = "add_slot_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.12f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        modifier = Modifier
            .width(80.dp)
            .scale(if (isEmpty) pulseScale else 1f)
            .clip(RoundedCornerShape(14.dp))
            .background(accentGreen.copy(alpha = if (isEmpty) pulseAlpha else 0.04f))
            .border(
                width = if (isEmpty) 1.5.dp else 1.dp,
                color = accentGreen.copy(alpha = if (isEmpty) 0.5f else 0.15f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // 虚线圆圈 + 加号
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.size(40.dp)) {
                    drawCircle(
                        color = accentGreen.copy(alpha = if (isEmpty) 0.45f else 0.25f),
                        radius = size.minDimension / 2f - 1.dp.toPx(),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = if (isEmpty) 2.dp.toPx() else 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(4.dp.toPx(), 3.dp.toPx()), 0f
                            )
                        )
                    )
                }
                Icon(
                    Icons.Default.Add,
                    contentDescription = "添加监控应用",
                    tint = accentGreen.copy(alpha = if (isEmpty) 0.85f else 0.55f),
                    modifier = Modifier.size(if (isEmpty) 18.dp else 16.dp)
                )
            }
            Text(
                text = if (isEmpty) "添加" else "+",
                fontSize = if (isEmpty) 9.5.sp else 12.sp,
                fontWeight = if (isEmpty) FontWeight.Bold else FontWeight.Medium,
                color = accentGreen.copy(alpha = if (isEmpty) 0.9f else 0.6f),
                textAlign = TextAlign.Center
            )
            if (isEmpty) {
                Text(
                    text = "第一个 App",
                    fontSize = 9.sp,
                    color = accentGreen.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
                )
            } else {
                // 占位保持高度一致
                Text(
                    text = "",
                    fontSize = 9.5.sp
                )
            }
        }
    }
}
