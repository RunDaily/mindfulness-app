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
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.onGloballyPositioned
import android.graphics.BlurMaskFilter
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.activity.ComponentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.R
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.data.repository.UsageRecordRepository.Companion.getDayRange
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.domain.model.AppUsageSummary
import com.life.mindfulnessapp.domain.model.IntentKind
import com.life.mindfulnessapp.domain.model.TimelineDisplayItem
import com.life.mindfulnessapp.domain.model.TimelineEvent
import com.life.mindfulnessapp.domain.model.WeeklyReportData
import com.life.mindfulnessapp.domain.model.collapseTimelineForDisplay
import com.life.mindfulnessapp.domain.usecase.PermissionStatus
import com.life.mindfulnessapp.overlay.formatSeconds
import com.life.mindfulnessapp.ui.theme.CapabilityForm
import com.life.mindfulnessapp.ui.theme.CapabilityKind
import com.life.mindfulnessapp.ui.theme.CapabilityMark
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.theme.LogoGreenBright
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

// ── 主入口 ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    // 必须用 Activity 作为 owner，确保与 MainActivity 里的 homeViewModel 是同一实例
    // 这样 MainActivity.handleNoteIntent 设置的 pendingHighlightId 才能被 HomeScreen 正确读到
    viewModel: HomeViewModel = hiltViewModel(LocalContext.current as ComponentActivity),
    onNavigateToAppDetail: (String) -> Unit = {},
    /** 管理列表：移除 / 管理已系着的锚 */
    onNavigateToManage: () -> Unit = {},
    /** 全机挑选器：系上新锚（不要经管理页中转） */
    onNavigateToAdd: () -> Unit = {},
    /** 本周觉察二级页 */
    onNavigateToWeekAwareness: () -> Unit = {}
) {
    val summaries by viewModel.usageSummaries.collectAsState()
    val permStatus by viewModel.permissionStatus.collectAsState()
    val monitoredAppsWithIcon by viewModel.monitoredAppsWithIcon.collectAsState()
    val timeline by viewModel.todayTimeline.collectAsState()
    val ongoingSessionSeconds by viewModel.ongoingSessionSeconds.collectAsState()
    val weekAwarenessPeek by viewModel.weekAwarenessPeek.collectAsState()
    val context = LocalContext.current
    val dayPulse = remember(timeline) { deriveDayPulse(timeline) }

    // HomeScreen 自身的备注/对照编辑弹窗状态
    var editingEvent by remember { mutableStateOf<TimelineEvent.UsageEvent?>(null) }
    var editFocus by remember { mutableStateOf(RecordEditFocus.Note) }
    // 是否是「结束使用后自动触发」的备注弹窗（影响弹窗文案）
    var isAutoNotePrompt by remember { mutableStateOf(false) }

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

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshWeekAwarenessPeek()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val cs = MaterialTheme.colorScheme

    // ── 折叠进度：判词滚出时渐显顶栏（监控轨仍在 item0，不计入阈值）────
    val listState = rememberLazyListState()
    var originHeightPx by remember { mutableFloatStateOf(0f) }

    val collapseProgress by remember {
        derivedStateOf {
            val firstVisible = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            when {
                firstVisible >= 1 -> 1f
                firstVisible == 0 -> {
                    if (originHeightPx > 0f) {
                        (offset.toFloat() / originHeightPx).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                }
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
            dayPulse = dayPulse,
            ongoingSessionSeconds = ongoingSessionSeconds,
            weekAwarenessPeek = weekAwarenessPeek,
            onOriginHeightMeasured = { originHeightPx = it },
            onRecordEdit = { event, focus ->
                viewModel.consumeOpenNoteEvent()
                editFocus = focus
                editingEvent = event
            },
            highlightRecordId = editingEvent?.recordId ?: guidedRecordId,
            onHighlightDone = { viewModel.consumeOpenNoteEvent() },
            onPermissionFix = { showPermissionDialog = true },
            onManageClick = onNavigateToManage,
            onAddAppClick = onNavigateToAdd,
            onAppClick = onNavigateToAppDetail,
            onWeekAwarenessClick = onNavigateToWeekAwareness,
            cardBg = cs.surface,
            onSurface = cs.onSurface,
            outline = cs.outlineVariant
        )

        // ── 浮层：坍缩态（只留一句今日判词）─────────────────────────────
        CollapsedHeaderOverlay(
            collapseProgress = collapseProgress,
            permissionStatus = permStatus,
            summaries = summaries,
            dayPulse = dayPulse,
            monitoredApps = monitoredAppsWithIcon,
            cs = cs
        )

        // 对照 / 备注编辑弹窗
        editingEvent?.let { event ->
            NoteEditDialog(
                event = event,
                cs = cs,
                focus = editFocus,
                isAutoPrompt = isAutoNotePrompt,
                onConfirm = { newNote, level ->
                    viewModel.updateRecordReview(event.recordId, newNote, level)
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
    dayPulse: DayPulse,
    /** (recordId, currentSessionSeconds) 进行中会话的实时有效秒数（已排除后台时间） */
    ongoingSessionSeconds: Pair<Long, Long>?,
    weekAwarenessPeek: WeeklyReportData?,
    onOriginHeightMeasured: (Float) -> Unit,
    onRecordEdit: (TimelineEvent.UsageEvent, RecordEditFocus) -> Unit,
    highlightRecordId: Long?,
    onHighlightDone: () -> Unit,
    onPermissionFix: () -> Unit,
    onManageClick: () -> Unit,
    onAddAppClick: () -> Unit,
    onAppClick: (String) -> Unit,
    onWeekAwarenessClick: () -> Unit,
    cardBg: Color,
    onSurface: Color,
    outline: Color
) {
    val iconMap = remember(monitoredAppsWithIcon) {
        monitoredAppsWithIcon.associateBy { it.packageName }
    }
    val displayItems = remember(timeline) { collapseTimelineForDisplay(timeline) }
    val dayVerdict = remember(monitoredAppsWithIcon, summaries, dayPulse) {
        computeDayVerdict(monitoredAppsWithIcon, summaries, dayPulse)
    }

    val isColdStart = monitoredAppsWithIcon.isEmpty()

    // 滚动到高亮条目（合并簇按簇头定位）
    LaunchedEffect(highlightRecordId, displayItems, timeline.isEmpty(), isColdStart) {
        if (highlightRecordId == null || isColdStart) return@LaunchedEffect
        val idx = displayItems.indexOfFirst { item ->
            when (item) {
                is TimelineDisplayItem.Single ->
                    item.event is TimelineEvent.UsageEvent &&
                        item.event.recordId == highlightRecordId
                is TimelineDisplayItem.MergedCluster ->
                    item.containsRecordId(highlightRecordId)
            }
        }
        if (idx >= 0) {
            // item0=lead；有记录时 item1=resume，事件从 index=2 起
            val base = if (timeline.isEmpty()) 1 else 2
            listState.animateScrollToItem(index = idx + base)
        }
    }

    val ongoingPackageName = remember(timeline) {
        timeline.filterIsInstance<TimelineEvent.UsageEvent>()
            .firstOrNull { it.isOngoing }
            ?.packageName
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        if (isColdStart) {
            // 冷启动：单次系锚仪式，不假装已是日志页
            item(key = "cold_start") {
                ColdStartMooringScreen(
                    permStatus = permStatus,
                    onPermissionFix = onPermissionFix,
                    onAddAppClick = onAddAppClick,
                    onSurface = onSurface,
                    onOriginHeightMeasured = onOriginHeightMeasured
                )
            }
        } else {
            // ── item 0：日原点 + 系着的锚 ──────────────────────────────────
            item(key = "home_lead") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    Column(
                        modifier = Modifier.onGloballyPositioned { coords ->
                            onOriginHeightMeasured(coords.size.height.toFloat())
                        }
                    ) {
                        if (!permStatus.allGranted) {
                            Spacer(modifier = Modifier.height(8.dp))
                            PermissionBreathStrip(
                                permissionStatus = permStatus,
                                onFix = onPermissionFix
                            )
                        } else {
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            DayOriginNode(
                                verdict = dayVerdict,
                                onSurface = onSurface,
                                outline = outline,
                                drawStemDown = false,
                                permissionOk = permStatus.allGranted
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    AppMonitorRow(
                        monitoredApps = monitoredAppsWithIcon,
                        summaries = summaries,
                        ongoingPackageName = ongoingPackageName,
                        onAppClick = onAppClick,
                        onManageClick = onManageClick,
                        onAddClick = onAddAppClick
                    )
                    when {
                        weekAwarenessPeek?.showHomeEntry == true -> {
                            Spacer(modifier = Modifier.height(10.dp))
                            WeekAwarenessEntryCard(
                                teaser = weekAwarenessPeek.homeTeaser,
                                onClick = onWeekAwarenessClick,
                                cardBg = cardBg,
                                onSurface = onSurface,
                                outline = outline,
                                emphasized = true
                            )
                        }
                        weekAwarenessPeek?.showQuietHomeEntry == true -> {
                            Spacer(modifier = Modifier.height(10.dp))
                            WeekAwarenessEntryCard(
                                teaser = weekAwarenessPeek.quietHomeTeaser,
                                onClick = onWeekAwarenessClick,
                                cardBg = cardBg,
                                onSurface = onSurface,
                                outline = outline,
                                emphasized = false
                            )
                        }
                    }
                }
            }

            if (timeline.isEmpty()) {
                item(key = "timeline_empty") {
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        EmptyTimelineContent(
                            onSurface = onSurface,
                            outline = outline
                        )
                    }
                }
            } else {
                item(key = "timeline_resume") {
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        TimelineResumeHeader(
                            onSurface = onSurface,
                            outline = outline
                        )
                    }
                }
                items(displayItems, key = { it.key }) { item ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        val highlightInCluster = highlightRecordId != null &&
                            item is TimelineDisplayItem.MergedCluster &&
                            item.containsRecordId(highlightRecordId)
                        val realtimeSeconds = when {
                            item is TimelineDisplayItem.Single &&
                                item.event is TimelineEvent.UsageEvent &&
                                item.event.isOngoing &&
                                ongoingSessionSeconds?.first == item.event.recordId ->
                                ongoingSessionSeconds.second
                            else -> null
                        }
                        TimelineDisplayNode(
                            item = item,
                            isLast = item == displayItems.last(),
                            iconMap = iconMap,
                            onRecordEdit = onRecordEdit,
                            highlightRecordId = highlightRecordId,
                            forceExpandMerged = highlightInCluster,
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
}

@Composable
private fun WeekAwarenessEntryCard(
    teaser: String,
    onClick: () -> Unit,
    cardBg: Color,
    onSurface: Color,
    outline: Color,
    emphasized: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .border(
                1.dp,
                outline.copy(alpha = if (emphasized) 0.45f else 0.28f),
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(LogoGreen.copy(alpha = if (emphasized) 1f else 0.45f))
        )
        Text(
            text = teaser,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            fontWeight = if (emphasized) FontWeight.Medium else FontWeight.Normal,
            color = onSurface.copy(alpha = if (emphasized) 1f else 0.72f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "查看",
            fontSize = 13.sp,
            color = LogoGreen.copy(alpha = if (emphasized) 1f else 0.7f),
            fontWeight = FontWeight.Medium
        )
    }
}

// ── 今日脉搏 & 判词 ───────────────────────────────────────────────────────────

/** 从时间轴推导的今日脉搏（排除种子） */
private data class DayPulse(
    val mindfulEnters: Int,
    val ungatedEnters: Int,
    val dismisses: Int,
    val enters: Int
)

private fun deriveDayPulse(timeline: List<TimelineEvent>): DayPulse {
    val usages = timeline.filterIsInstance<TimelineEvent.UsageEvent>().filter { !it.isSeed }
    val dismisses = usages.count { it.isGateQuit }
    val enters = usages.filter { !it.isGateQuit }
    val mindful = enters.count {
        !it.purpose.isNullOrBlank() && it.intentKind != IntentKind.PURPOSELESS
    }
    return DayPulse(
        mindfulEnters = mindful,
        ungatedEnters = enters.size - mindful,
        dismisses = dismisses,
        enters = enters.size
    )
}

private enum class VerdictTone { Still, Mindful, Drift, Bound, Alert, Unmoored }

/**
 * 今日判词：一句真话，不粉饰。
 * - 有意图门 → 以觉察 / 直进 / 守住叙事
 * - 仅时长锁 → 以额度叙事
 * - 未系锚 → 邀请，而非空仪表盘
 */
private data class DayVerdict(
    val kicker: String,
    val headline: String,
    val detail: String?,
    val tone: VerdictTone,
    val collapsedText: String
)

private fun computeDayVerdict(
    monitoredApps: List<AppInfo>,
    summaries: List<AppUsageSummary>,
    pulse: DayPulse
): DayVerdict {
    val hasIntentGate = monitoredApps.any { it.requireIntentOnOpen }
    val hasTimeLock = monitoredApps.any { it.timeLimitEnabled }
    val hasPeriodLock = monitoredApps.any { it.periodLockEnabled }
    val totalTodaySec = summaries.sumOf { it.todaySeconds }
    val timeText = if (totalTodaySec == 0L) null else formatSeconds(totalTodaySec)
    val overLimitCount = summaries.count { it.dailyUsagePercent >= 1f }

    if (monitoredApps.isEmpty()) {
        return DayVerdict(
            kicker = "今日",
            headline = "还没有系上锚",
            detail = "选一个常刷的 App，意图门让你想清楚再进",
            tone = VerdictTone.Unmoored,
            collapsedText = "未系锚"
        )
    }

    return when {
        hasIntentGate -> {
            val quietBits = buildList {
                if (pulse.dismisses > 0) add("守住 ${pulse.dismisses}")
                if (pulse.ungatedEnters > 0) add("直进 ${pulse.ungatedEnters}")
                if (timeText != null) add("用了 $timeText")
                if (overLimitCount > 0) add("${overLimitCount}触顶")
            }.joinToString(" · ").ifBlank { null }

            when {
                pulse.mindfulEnters > 0 -> DayVerdict(
                    kicker = "今日",
                    headline = "带着意图 · ${pulse.mindfulEnters}",
                    detail = quietBits,
                    tone = VerdictTone.Mindful,
                    collapsedText = "${pulse.mindfulEnters} 次意图"
                )
                pulse.ungatedEnters > 0 -> DayVerdict(
                    kicker = "今日",
                    headline = "直进了 ${pulse.ungatedEnters} 次",
                    detail = buildList {
                        add("还没有写下意图")
                        if (pulse.dismisses > 0) add("守住 ${pulse.dismisses}")
                        if (timeText != null) add(timeText)
                    }.joinToString(" · "),
                    tone = VerdictTone.Drift,
                    collapsedText = "直进 ${pulse.ungatedEnters}"
                )
                pulse.dismisses > 0 -> DayVerdict(
                    kicker = "今日",
                    headline = "守住了 ${pulse.dismisses} 次",
                    detail = "想打开，又在门外停住",
                    tone = VerdictTone.Bound,
                    collapsedText = "守住 ${pulse.dismisses}"
                )
                else -> DayVerdict(
                    kicker = "今日",
                    headline = "水面还静着",
                    detail = "还没打开过受监控的 App",
                    tone = VerdictTone.Still,
                    collapsedText = "水面静着"
                )
            }
        }
        hasTimeLock -> when {
            overLimitCount > 0 -> DayVerdict(
                kicker = "今日",
                headline = "$overLimitCount 个触顶",
                detail = timeText?.let { "已用 $it" },
                tone = VerdictTone.Alert,
                collapsedText = "${overLimitCount}触顶"
            )
            totalTodaySec == 0L -> DayVerdict(
                kicker = "今日",
                headline = "额度还完整",
                detail = "今天还没打开过",
                tone = VerdictTone.Still,
                collapsedText = "额度完整"
            )
            else -> DayVerdict(
                kicker = "今日",
                headline = "额度内",
                detail = timeText?.let { "已用 $it" },
                tone = VerdictTone.Bound,
                collapsedText = timeText ?: "额度内"
            )
        }
        hasPeriodLock -> DayVerdict(
            kicker = "今日",
            headline = if (pulse.dismisses > 0) "守住了 ${pulse.dismisses} 次" else "时段锁在场",
            detail = if (pulse.dismisses > 0) "在锁定时段停住了" else "指定时段会硬挡进入",
            tone = if (pulse.dismisses > 0) VerdictTone.Bound else VerdictTone.Still,
            collapsedText = if (pulse.dismisses > 0) "守住 ${pulse.dismisses}" else "时段锁"
        )
        else -> DayVerdict(
            kicker = "今日",
            headline = if (totalTodaySec == 0L) "只在看着" else "看着 · ${timeText ?: ""}",
            detail = "还没开意图门、时长锁或时段锁",
            tone = VerdictTone.Drift,
            collapsedText = timeText ?: "只在看着"
        )
    }
}

@Composable
private fun CollapsedHeaderOverlay(
    collapseProgress: Float,
    permissionStatus: PermissionStatus,
    summaries: List<AppUsageSummary>,
    dayPulse: DayPulse,
    monitoredApps: List<AppInfo>,
    cs: ColorScheme
) {
    if (collapseProgress <= 0f) return

    val alpha = collapseProgress.coerceIn(0f, 1f)
    val onBg = cs.onBackground
    val verdict = remember(monitoredApps, summaries, dayPulse) {
        computeDayVerdict(monitoredApps, summaries, dayPulse)
    }
    val toneColor = verdictToneColor(verdict.tone, onBg)

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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HomeBrandMark(
                    permissionOk = permissionStatus.allGranted,
                    pulseAlpha = dotAlpha,
                    size = 18.dp
                )
                Text(
                    text = stringResource(R.string.app_name),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = onBg.copy(alpha = 0.62f),
                    letterSpacing = 0.4.sp
                )
            }

            Text(
                text = verdict.collapsedText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = toneColor,
                letterSpacing = (-0.2).sp
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(0.5.dp)
                .background(cs.outlineVariant.copy(alpha = 0.4f * alpha))
        )
    }
}

private fun verdictToneColor(tone: VerdictTone, onBg: Color): Color = when (tone) {
    VerdictTone.Mindful, VerdictTone.Bound -> LogoGreen
    VerdictTone.Alert -> Color(0xFFE74C3C).copy(alpha = 0.90f)
    VerdictTone.Drift -> Color(0xFFE8941A).copy(alpha = 0.92f)
    VerdictTone.Still, VerdictTone.Unmoored -> onBg.copy(alpha = 0.55f)
}

// ── 品牌锚点（日原点 / 折叠顶栏）────────────────────────────────────────────

@Composable
private fun HomeBrandMark(
    permissionOk: Boolean,
    pulseAlpha: Float,
    size: Dp
) {
    val context = LocalContext.current
    val ringColor = if (permissionOk) {
        LogoGreen.copy(alpha = 0.40f + 0.50f * pulseAlpha)
    } else {
        Color(0xFFF39C12).copy(alpha = 0.40f + 0.50f * pulseAlpha)
    }
    val corner = size * 0.24f
    val iconBitmap = remember(context.packageName) {
        try {
            context.packageManager
                .getApplicationIcon(context.packageName)
                .toBitmap()
                .asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
    Box(
        modifier = Modifier
            .size(size)
            .border(1.5.dp, ringColor, RoundedCornerShape(corner))
            .padding(2.dp)
            .clip(RoundedCornerShape(corner * 0.85f))
            .background(Color(0xFF0A0E14)),
        contentAlignment = Alignment.Center
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap,
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}

/**
 * 脊梁栏宽：日原点徽标与事件点共用中线，保证一日一条轴对齐。
 * 徽标可略溢出栏宽，茎线始终居中。
 */
private val TimelineRailWidth = 28.dp
private val TimelineRailStroke = 1.5.dp
private val TimelineOriginMarkSize = 30.dp
private val TimelineOriginHaloSize = 42.dp

/**
 * 心锚日原点：品牌锚 + 今日判词。
 * 徽标加大一档，作「一日之始」而非状态小灯。
 */
@Composable
private fun DayOriginNode(
    verdict: DayVerdict,
    onSurface: Color,
    outline: Color,
    drawStemDown: Boolean,
    permissionOk: Boolean = true
) {
    val toneColor = verdictToneColor(verdict.tone, onSurface)
    val timeFormat = remember { SimpleDateFormat("M月d日", Locale.CHINESE) }
    var todayStr by remember { mutableStateOf(timeFormat.format(Date())) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            val (dayStart, dayEnd) = getDayRange(now)
            todayStr = timeFormat.format(Date(dayStart))
            kotlinx.coroutines.delay((dayEnd - now).coerceAtLeast(1_000L))
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "origin_pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "origin_ring"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (drawStemDown) 2.dp else 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(TimelineRailWidth)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(TimelineRailWidth)
                    .height(TimelineOriginHaloSize)
            ) {
                Box(
                    modifier = Modifier
                        .requiredSize(TimelineOriginHaloSize)
                        .clip(CircleShape)
                        .background(toneColor.copy(alpha = 0.08f + 0.10f * pulse))
                )
                HomeBrandMark(
                    permissionOk = permissionOk,
                    pulseAlpha = pulse,
                    size = TimelineOriginMarkSize
                )
            }
            if (drawStemDown) {
                Box(
                    modifier = Modifier
                        .width(TimelineRailStroke)
                        .height(14.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    outline.copy(alpha = 0.36f),
                                    outline.copy(alpha = 0.18f)
                                )
                            )
                        )
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp, top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = onSurface.copy(alpha = 0.72f),
                    letterSpacing = 0.3.sp
                )
                Text(
                    text = "·",
                    fontSize = 13.sp,
                    color = onSurface.copy(alpha = 0.22f)
                )
                Text(
                    text = verdict.kicker,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = toneColor.copy(alpha = 0.88f),
                    letterSpacing = 0.3.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = todayStr,
                    fontSize = 12.sp,
                    color = onSurface.copy(alpha = 0.34f),
                    fontWeight = FontWeight.Medium
                )
            }

            AnimatedContent(
                targetState = verdict.headline to verdict.detail,
                transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(200)) },
                label = "dayOriginVerdict"
            ) { (headline, detail) ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = headline,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = toneColor,
                        lineHeight = 31.sp,
                        letterSpacing = (-0.6).sp
                    )
                    if (detail != null) {
                        Text(
                            text = detail,
                            fontSize = 13.sp,
                            color = onSurface.copy(alpha = 0.38f),
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

/** 工具带之后：记录列表自己的轴起点（不从监控带穿过来） */
@Composable
private fun TimelineResumeHeader(
    onSurface: Color,
    outline: Color
) {
    val rail = outline.copy(alpha = 0.28f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(TimelineRailWidth)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .border(1.dp, rail.copy(alpha = 0.55f), CircleShape)
                    .padding(1.5.dp)
                    .clip(CircleShape)
                    .background(rail.copy(alpha = 0.45f))
            )
        }
        Text(
            text = "今天走过的",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = onSurface.copy(alpha = 0.32f),
            letterSpacing = 0.6.sp,
            modifier = Modifier.padding(start = 14.dp)
        )
    }
}

// 保留旧名给可能的外部引用；内部已迁到 DayOriginNode
@Composable
internal fun TimelineDayHeader(
    onSurface: Color,
    outline: Color = onSurface.copy(alpha = 0.25f),
    hasEvents: Boolean = true
) {
    DayOriginNode(
        verdict = DayVerdict(
            kicker = "今日",
            headline = "水面还静着",
            detail = null,
            tone = VerdictTone.Still,
            collapsedText = "水面静着"
        ),
        onSurface = onSurface,
        outline = outline,
        drawStemDown = hasEvents
    )
}

// ── 时间轴节点 ────────────────────────────────────────────────────────────────

@Composable
internal fun TimelineDisplayNode(
    item: TimelineDisplayItem,
    isLast: Boolean,
    iconMap: Map<String, AppInfo>,
    onRecordEdit: (TimelineEvent.UsageEvent, RecordEditFocus) -> Unit,
    highlightRecordId: Long? = null,
    forceExpandMerged: Boolean = false,
    onHighlightDone: () -> Unit = {},
    realtimeSeconds: Long? = null,
    /** false 时仅展示，不打开对照/备注编辑（历史日） */
    editable: Boolean = true,
    cardBg: Color,
    onSurface: Color,
    outline: Color
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeStr = remember(item.timeMs) { timeFormat.format(Date(item.timeMs)) }
    val quietOnRail = when (item) {
        is TimelineDisplayItem.MergedCluster -> true
        is TimelineDisplayItem.Single -> {
            val e = item.event
            e is TimelineEvent.UsageEvent && (e.isGateQuit || e.isSeed)
        }
    }
    val isOngoing = item is TimelineDisplayItem.Single &&
        (item.event as? TimelineEvent.UsageEvent)?.isOngoing == true

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top
    ) {
        TimelineConnector(
            item = item,
            isLast = isLast,
            outline = outline,
            quiet = quietOnRail,
            ongoing = isOngoing
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(
                    start = 12.dp,
                    bottom = when {
                        isLast -> 8.dp
                        quietOnRail -> 2.dp
                        else -> 14.dp
                    }
                )
        ) {
            if (quietOnRail) {
                when (item) {
                    is TimelineDisplayItem.Single -> when (val event = item.event) {
                        is TimelineEvent.UsageEvent -> {
                            if (event.isGateQuit) {
                                GateQuitLine(
                                    timeStr = timeStr,
                                    text = event.gateQuitLine ?: "离开了 · ${event.appName}",
                                    onSurface = onSurface,
                                    isHighlighted = event.recordId == highlightRecordId &&
                                        highlightRecordId != null,
                                    onHighlightDone = onHighlightDone
                                )
                            } else {
                                // 种子：时刻作前缀，本体仍走轻量卡
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TimelineTimeLabel(timeStr = timeStr, onSurface = onSurface, quiet = true)
                                    UsageEventCard(
                                        event = event,
                                        appInfo = iconMap[event.packageName],
                                        cardBg = cardBg,
                                        onSurface = onSurface,
                                        isHighlighted = event.recordId == highlightRecordId &&
                                            highlightRecordId != null,
                                        onHighlightDone = onHighlightDone,
                                        realtimeSeconds = realtimeSeconds,
                                        onClick = if (!editable) null else {
                                            {
                                                val focus = if (
                                                    UsageRecordEntity.MindfulnessLevel.isValid(
                                                        event.mindfulnessLevel
                                                    )
                                                ) RecordEditFocus.Note else RecordEditFocus.Compare
                                                onRecordEdit(event, focus)
                                            }
                                        },
                                        onCompareClick = if (!editable) null else {
                                            { onRecordEdit(event, RecordEditFocus.Compare) }
                                        },
                                        onNoteChipClick = if (!editable) null else {
                                            { onRecordEdit(event, RecordEditFocus.Note) }
                                        }
                                    )
                                }
                            }
                        }
                        is TimelineEvent.LimitResetEvent -> {
                            TimelineTimeLabel(timeStr = timeStr, onSurface = onSurface, quiet = false)
                            Spacer(modifier = Modifier.height(4.dp))
                            LimitResetEventCard(
                                event = event,
                                appInfo = iconMap[event.packageName],
                                cardBg = cardBg,
                                onSurface = onSurface
                            )
                        }
                    }
                    is TimelineDisplayItem.MergedCluster -> {
                        MergedClusterRow(
                            cluster = item,
                            timeStr = timeStr,
                            onSurface = onSurface,
                            forceExpand = forceExpandMerged,
                            highlightRecordId = highlightRecordId,
                            onHighlightDone = onHighlightDone
                        )
                    }
                }
            } else {
                TimelineTimeLabel(timeStr = timeStr, onSurface = onSurface, quiet = false)
                Spacer(modifier = Modifier.height(5.dp))
                when (item) {
                    is TimelineDisplayItem.Single -> when (val event = item.event) {
                        is TimelineEvent.UsageEvent -> UsageEventCard(
                            event = event,
                            appInfo = iconMap[event.packageName],
                            cardBg = cardBg,
                            onSurface = onSurface,
                            isHighlighted = event.recordId == highlightRecordId &&
                                highlightRecordId != null,
                            onHighlightDone = onHighlightDone,
                            realtimeSeconds = realtimeSeconds,
                            onClick = if (!editable) null else {
                                {
                                    val focus = if (
                                        UsageRecordEntity.MindfulnessLevel.isValid(event.mindfulnessLevel)
                                    ) RecordEditFocus.Note else RecordEditFocus.Compare
                                    onRecordEdit(event, focus)
                                }
                            },
                            onCompareClick = if (!editable) null else {
                                { onRecordEdit(event, RecordEditFocus.Compare) }
                            },
                            onNoteChipClick = if (!editable) null else {
                                { onRecordEdit(event, RecordEditFocus.Note) }
                            }
                        )
                        is TimelineEvent.LimitResetEvent -> LimitResetEventCard(
                            event = event,
                            appInfo = iconMap[event.packageName],
                            cardBg = cardBg,
                            onSurface = onSurface
                        )
                    }
                    is TimelineDisplayItem.MergedCluster -> MergedClusterRow(
                        cluster = item,
                        timeStr = timeStr,
                        onSurface = onSurface,
                        forceExpand = forceExpandMerged,
                        highlightRecordId = highlightRecordId,
                        onHighlightDone = onHighlightDone
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineTimeLabel(
    timeStr: String,
    onSurface: Color,
    quiet: Boolean
) {
    Text(
        text = timeStr,
        fontSize = if (quiet) 10.sp else 11.sp,
        color = onSurface.copy(alpha = if (quiet) 0.26f else 0.40f),
        fontWeight = if (quiet) FontWeight.Medium else FontWeight.SemiBold,
        letterSpacing = 0.2.sp
    )
}

/** 兼容旧调用 */
@Composable
internal fun TimelineEventNode(
    event: TimelineEvent,
    isLast: Boolean,
    iconMap: Map<String, AppInfo>,
    onRecordEdit: (TimelineEvent.UsageEvent, RecordEditFocus) -> Unit,
    isHighlighted: Boolean = false,
    onHighlightDone: () -> Unit = {},
    realtimeSeconds: Long? = null,
    cardBg: Color,
    onSurface: Color,
    outline: Color
) {
    TimelineDisplayNode(
        item = TimelineDisplayItem.Single(event),
        isLast = isLast,
        iconMap = iconMap,
        onRecordEdit = onRecordEdit,
        highlightRecordId = (event as? TimelineEvent.UsageEvent)?.recordId?.takeIf { isHighlighted },
        onHighlightDone = onHighlightDone,
        realtimeSeconds = realtimeSeconds,
        cardBg = cardBg,
        onSurface = onSurface,
        outline = outline
    )
}

@Composable
internal fun TimelineConnector(
    item: TimelineDisplayItem,
    isLast: Boolean,
    outline: Color,
    quiet: Boolean = false,
    ongoing: Boolean = false
) {
    val rail = outline.copy(alpha = 0.30f)
    val isReset = item is TimelineDisplayItem.Single &&
        item.event is TimelineEvent.LimitResetEvent
    val isMindful = item is TimelineDisplayItem.Single &&
        (item.event as? TimelineEvent.UsageEvent)?.let {
            it.hasIntentGate && it.isMindful && !it.isGateQuit && !it.isSeed
        } == true
    val isGated = item is TimelineDisplayItem.Single &&
        (item.event as? TimelineEvent.UsageEvent)?.let {
            it.hasIntentGate && !it.isGateQuit && !it.isSeed
        } == true

    val dotColor = when {
        ongoing -> LogoGreen
        isReset -> outline.copy(alpha = 0.50f)
        quiet -> outline.copy(alpha = 0.26f)
        isMindful -> LogoGreen.copy(alpha = 0.90f)
        isGated -> LogoGreen.copy(alpha = 0.55f)
        else -> outline.copy(alpha = 0.36f)
    }
    // 圆点与时刻/首行对齐
    val dotTopPad = if (quiet) 3.dp else 2.dp
    val coreSize = when {
        ongoing -> 8.dp
        isReset -> 7.dp
        quiet -> 4.dp
        isMindful -> 7.dp
        else -> 6.dp
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(TimelineRailWidth)
            .fillMaxHeight()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(top = dotTopPad)
                .size(if (ongoing) 14.dp else coreSize)
        ) {
            if (ongoing) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .border(1.5.dp, LogoGreen.copy(alpha = 0.35f), CircleShape)
                )
            }
            Box(
                modifier = Modifier
                    .size(coreSize)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
        if (!isLast) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .width(TimelineRailStroke)
                    .weight(1f)
                    .background(
                        Brush.verticalGradient(
                            listOf(rail, rail.copy(alpha = 0.18f))
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .width(TimelineRailStroke)
                    .height(14.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(rail, rail.copy(alpha = 0f))
                        )
                    )
            )
        }
    }
}

@Composable
internal fun TimelineConnector(
    event: TimelineEvent,
    isLast: Boolean,
    outline: Color
) {
    TimelineConnector(
        item = TimelineDisplayItem.Single(event),
        isLast = isLast,
        outline = outline
    )
}

// ── 使用记录：绿条表意图门；黄字仅用于单次超时 +xx:yy ─────────────────────

/** 意图门拦住后离开（兼容旧函数名） */
internal fun TimelineEvent.UsageEvent.isInterceptedAndQuit(): Boolean = isGateQuit

/** 兼容旧调用：门外/种子用更紧凑间距 */
internal fun TimelineEvent.UsageEvent.usesQuietDensity(): Boolean = isGateQuit || isSeed

/** 列表唯一非绿色强调：单次时长超出 */
private val OvertimeYellow = Color(0xFFE0A21A)

@Composable
internal fun UsageEventCard(
    event: TimelineEvent.UsageEvent,
    appInfo: AppInfo? = null,
    cardBg: Color,
    onSurface: Color,
    isHighlighted: Boolean = false,
    onHighlightDone: () -> Unit = {},
    realtimeSeconds: Long? = null,
    onClick: (() -> Unit)? = null,
    onCompareClick: (() -> Unit)? = null,
    onNoteChipClick: (() -> Unit)? = null
) {
    if (event.isGateQuit) {
        GateQuitLine(
            timeStr = "",
            text = event.gateQuitLine ?: "离开了 · ${event.appName}",
            onSurface = onSurface,
            isHighlighted = isHighlighted,
            onHighlightDone = onHighlightDone
        )
        return
    }

    if (event.hasIntentGate) {
        GatedUsageRecordRow(
            event = event,
            appInfo = appInfo,
            cardBg = cardBg,
            onSurface = onSurface,
            isHighlighted = isHighlighted,
            onHighlightDone = onHighlightDone,
            realtimeSeconds = realtimeSeconds,
            onClick = onClick?.takeUnless { event.isOngoing },
            onCompareClick = onCompareClick?.takeUnless { event.isOngoing },
            onNoteChipClick = onNoteChipClick?.takeUnless { event.isOngoing }
        )
    } else {
        UngatedUsageRecordRow(
            event = event,
            appInfo = appInfo,
            cardBg = cardBg,
            onSurface = onSurface,
            isHighlighted = isHighlighted,
            onHighlightDone = onHighlightDone,
            realtimeSeconds = realtimeSeconds,
            onClick = onClick?.takeUnless { event.isSeed || event.isOngoing }
        )
    }
}

private val TimelineCardShape = RoundedCornerShape(16.dp)
private val IntentBarWidth = 3.dp
private val TimelineLogoSize = 24.dp

/** 「离开了」：时刻与正文同行，不另占时间栏 */
@Composable
private fun GateQuitLine(
    timeStr: String,
    text: String,
    onSurface: Color,
    isHighlighted: Boolean,
    onHighlightDone: () -> Unit
) {
    LaunchedEffect(isHighlighted) {
        if (isHighlighted) {
            kotlinx.coroutines.delay(1200)
            onHighlightDone()
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (timeStr.isNotEmpty()) {
            Text(
                text = timeStr,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = onSurface.copy(alpha = if (isHighlighted) 0.40f else 0.26f),
                letterSpacing = 0.2.sp
            )
        }
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            color = if (isHighlighted) {
                LogoGreen.copy(alpha = 0.85f)
            } else {
                onSurface.copy(alpha = 0.34f)
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

// ── 共享：App 图标 ───────────────────────────────────────────────────────────

@Composable
private fun TimelineAppIcon(
    appInfo: AppInfo?,
    appName: String,
    dimmed: Boolean,
    accentFallback: Color,
    onSurface: Color,
    size: Dp = TimelineLogoSize
) {
    if (appInfo?.icon != null) {
        val bitmap = remember(appInfo.icon) { appInfo.icon.toBitmap().asImageBitmap() }
        Image(
            bitmap = bitmap,
            contentDescription = appName,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape((size.value * 0.25f).dp))
                .then(if (dimmed) Modifier.alpha(0.55f) else Modifier)
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape((size.value * 0.25f).dp))
                .background(accentFallback.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Android,
                contentDescription = null,
                tint = if (dimmed) onSurface.copy(alpha = 0.20f) else accentFallback,
                modifier = Modifier.size((size.value * 0.5f).dp)
            )
        }
    }
}

private fun TimelineEvent.UsageEvent.durationLabel(realtimeSeconds: Long?): String? = when {
    isOngoing -> formatDurationNarrative(
        realtimeSeconds ?: ((System.currentTimeMillis() - startTime) / 1000)
    )
    durationSeconds <= 0L -> null
    else -> formatDurationNarrative(durationSeconds)
}

/**
 * 时间轴时长：避免 `04:04` 被读成钟点。
 * 例：48秒 / 4分 / 1时5分
 */
private fun formatDurationNarrative(seconds: Long): String {
    if (seconds <= 0L) return "0分"
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

/** 时长 + 可选黄色超出后缀 */
@Composable
private fun DurationWithOvertime(
    durationText: String?,
    overtimeSeconds: Long?,
    durationColor: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight = FontWeight.SemiBold
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = durationText ?: "—",
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = durationColor,
            letterSpacing = (-0.2).sp
        )
        if (overtimeSeconds != null && overtimeSeconds > 0L) {
            Text(
                text = "+${formatDurationNarrative(overtimeSeconds)}",
                fontSize = (fontSize.value - 1f).sp,
                fontWeight = FontWeight.Medium,
                color = OvertimeYellow.copy(alpha = 0.95f),
                letterSpacing = (-0.2).sp
            )
        }
    }
}

@Composable
private fun OngoingBadge() {
    Text(
        text = "进行中",
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = LogoGreen.copy(alpha = 0.92f),
        letterSpacing = 0.2.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(LogoGreen.copy(alpha = 0.12f))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    )
}

/** 首页编辑入口聚焦维度 */
internal enum class RecordEditFocus {
    Compare,
    Note
}

/** 与弹窗三档一致的对照色 */
private fun mindfulnessTierAccent(level: Int): Color = when (level) {
    UsageRecordEntity.MindfulnessLevel.ALIGNED -> LogoGreen
    UsageRecordEntity.MindfulnessLevel.SLIGHT -> Color(0xFFD4A017)
    UsageRecordEntity.MindfulnessLevel.LARGE -> Color(0xFFC47A6A)
    else -> LogoGreen
}

/** 未填时的轻入口：克制边框，不抢意图（用于「对照」主催促） */
@Composable
private fun QuietAffordance(
    label: String,
    onSurface: Color,
    onClick: () -> Unit,
    accent: Color? = null,
    icon: ImageVector = Icons.Default.EditNote
) {
    val tone = accent?.copy(alpha = 0.72f) ?: onSurface.copy(alpha = 0.38f)
    val borderTone = accent?.copy(alpha = 0.28f) ?: onSurface.copy(alpha = 0.12f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .border(0.5.dp, borderTone, RoundedCornerShape(5.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tone,
            modifier = Modifier.size(11.dp)
        )
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = tone,
            letterSpacing = (-0.1).sp
        )
    }
}

/** 备注空态：纯文字入口，不做框按钮 */
@Composable
private fun SoftTextAffordance(
    label: String,
    onSurface: Color,
    onClick: () -> Unit
) {
    Text(
        text = label,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        color = onSurface.copy(alpha = 0.28f),
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
    )
}

/** 已填对照：档位色短标签，可点改档 */
@Composable
private fun CompareTierChip(
    level: Int,
    onClick: (() -> Unit)?
) {
    val accent = mindfulnessTierAccent(level)
    val label = UsageRecordEntity.MindfulnessLevel.tierLabel(level)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = 0.10f))
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.90f))
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = accent.copy(alpha = 0.88f),
            maxLines = 1
        )
    }
}

/**
 * 有意图门：左侧色条 + 叙事卡。
 * 主序：意图 → 对照 → 实际/备注；App 与时长降为顶栏元信息。
 */
@Composable
private fun GatedUsageRecordRow(
    event: TimelineEvent.UsageEvent,
    appInfo: AppInfo?,
    cardBg: Color,
    onSurface: Color,
    isHighlighted: Boolean,
    onHighlightDone: () -> Unit,
    realtimeSeconds: Long?,
    onClick: (() -> Unit)?,
    onCompareClick: (() -> Unit)? = null,
    onNoteChipClick: (() -> Unit)? = null
) {
    LaunchedEffect(isHighlighted) {
        if (isHighlighted) {
            kotlinx.coroutines.delay(1600)
            onHighlightDone()
        }
    }

    val mindfulnessTier = event.mindfulnessLevel
        ?.takeIf { UsageRecordEntity.MindfulnessLevel.isValid(it) }
    val compareLabel = mindfulnessTier?.let { UsageRecordEntity.MindfulnessLevel.tierLabel(it) }
        ?.takeIf { it.isNotEmpty() }
    val intentText = event.intentLine
    val noteText = event.note?.trim()?.takeIf { it.isNotEmpty() }
    val durationText = event.durationLabel(realtimeSeconds)
    val overtimeSeconds = event.sessionOvertimeSeconds
    val isPurposelessIntent = intentText == "没有目的"
    val canEdit = onClick != null && !event.isOngoing
    val showCompareChip = canEdit && compareLabel == null && onCompareClick != null
    val showNoteChip = canEdit && compareLabel != null && noteText == null && onNoteChipClick != null

    // 已对照：色条跟档位走，避免「跑偏」仍挂整条乐观绿
    val barColor = when {
        event.isOngoing -> LogoGreen
        isHighlighted -> LogoGreen.copy(alpha = 0.95f)
        mindfulnessTier != null -> mindfulnessTierAccent(mindfulnessTier).copy(alpha = 0.88f)
        isPurposelessIntent -> LogoGreen.copy(alpha = 0.35f)
        else -> LogoGreen.copy(alpha = 0.88f)
    }
    val borderColor = when {
        event.isOngoing -> LogoGreen.copy(alpha = 0.22f)
        isHighlighted -> LogoGreen.copy(alpha = 0.28f)
        mindfulnessTier == UsageRecordEntity.MindfulnessLevel.LARGE ->
            mindfulnessTierAccent(mindfulnessTier).copy(alpha = 0.18f)
        mindfulnessTier == UsageRecordEntity.MindfulnessLevel.SLIGHT ->
            mindfulnessTierAccent(mindfulnessTier).copy(alpha = 0.16f)
        else -> onSurface.copy(alpha = 0.07f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(TimelineCardShape)
            .background(cardBg)
            .border(1.dp, borderColor, TimelineCardShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Box(
            modifier = Modifier
                .width(IntentBarWidth)
                .fillMaxHeight()
                .background(barColor)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            // 元信息：App · 时长（降权，不抢意图）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                TimelineAppIcon(
                    appInfo = appInfo,
                    appName = event.appName,
                    dimmed = false,
                    accentFallback = LogoGreen,
                    onSurface = onSurface,
                    size = 18.dp
                )
                Text(
                    text = event.appName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = onSurface.copy(alpha = 0.42f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (event.isOngoing) {
                    OngoingBadge()
                    Spacer(modifier = Modifier.width(2.dp))
                }
                DurationWithOvertime(
                    durationText = durationText,
                    overtimeSeconds = overtimeSeconds,
                    durationColor = if (event.isOngoing) {
                        LogoGreen.copy(alpha = 0.85f)
                    } else {
                        onSurface.copy(alpha = 0.38f)
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // 意图：第一视线
            if (intentText != null) {
                Text(
                    text = intentText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isPurposelessIntent) {
                        onSurface.copy(alpha = 0.40f)
                    } else {
                        LogoGreen.copy(alpha = 0.92f)
                    },
                    lineHeight = 22.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (event.isOngoing) {
                Text(
                    text = "这一次还没写下意图",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = onSurface.copy(alpha = 0.36f)
                )
            }

            // 对照 → 实际
            if (mindfulnessTier != null || showCompareChip || noteText != null || showNoteChip) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    when {
                        mindfulnessTier != null -> CompareTierChip(
                            level = mindfulnessTier,
                            onClick = onCompareClick.takeIf { canEdit }
                        )
                        showCompareChip -> QuietAffordance(
                            label = "对照一下",
                            onSurface = onSurface,
                            onClick = onCompareClick!!,
                            accent = LogoGreen,
                            icon = Icons.Default.SelfImprovement
                        )
                    }
                    when {
                        noteText != null -> Text(
                            text = noteText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            color = onSurface.copy(alpha = 0.52f),
                            lineHeight = 18.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.then(
                                if (canEdit && onNoteChipClick != null) {
                                    Modifier.clickable(onClick = onNoteChipClick)
                                } else {
                                    Modifier
                                }
                            )
                        )
                        showNoteChip -> SoftTextAffordance(
                            label = UsageRecordEntity.MindfulnessLevel.cardNoteAffordance(
                                mindfulnessTier!!
                            ),
                            onSurface = onSurface,
                            onClick = onNoteChipClick!!
                        )
                    }
                }
            }
        }
    }
}

/**
 * 无意图门：无绿条的单行时长条。
 * 单次超时同样仅用黄色 +xx:yy；不使用红色。
 */
@Composable
private fun UngatedUsageRecordRow(
    event: TimelineEvent.UsageEvent,
    appInfo: AppInfo?,
    cardBg: Color,
    onSurface: Color,
    isHighlighted: Boolean,
    onHighlightDone: () -> Unit,
    realtimeSeconds: Long?,
    onClick: (() -> Unit)?
) {
    LaunchedEffect(isHighlighted) {
        if (isHighlighted) {
            kotlinx.coroutines.delay(1600)
            onHighlightDone()
        }
    }

    val durationText = event.durationLabel(realtimeSeconds)
    val overtimeSeconds = event.sessionOvertimeSeconds
    val durationColor = when {
        event.isOngoing -> LogoGreen
        else -> onSurface.copy(alpha = 0.58f)
    }
    val borderColor = when {
        event.isOngoing -> LogoGreen.copy(alpha = 0.18f)
        isHighlighted -> LogoGreen.copy(alpha = 0.20f)
        else -> onSurface.copy(alpha = 0.06f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TimelineCardShape)
            .background(cardBg.copy(alpha = if (event.isSeed) 0.55f else 0.78f))
            .border(1.dp, borderColor, TimelineCardShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TimelineAppIcon(
            appInfo = appInfo,
            appName = event.appName,
            dimmed = event.isSeed,
            accentFallback = onSurface,
            onSurface = onSurface,
            size = TimelineLogoSize
        )
        Text(
            text = event.appName,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            color = onSurface.copy(alpha = if (event.isSeed) 0.42f else 0.58f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (event.isOngoing) OngoingBadge()
            DurationWithOvertime(
                durationText = durationText,
                overtimeSeconds = overtimeSeconds,
                durationColor = durationColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ── 合并簇：连续门外停下 ─────────────────────────────────────────────────────

@Composable
private fun MergedClusterRow(
    cluster: TimelineDisplayItem.MergedCluster,
    timeStr: String,
    onSurface: Color,
    forceExpand: Boolean,
    highlightRecordId: Long?,
    onHighlightDone: () -> Unit
) {
    var expanded by remember(cluster.key) { mutableStateOf(false) }
    LaunchedEffect(forceExpand) {
        if (forceExpand) expanded = true
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = timeStr,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = onSurface.copy(alpha = 0.26f),
                letterSpacing = 0.2.sp
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cluster.titleLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = onSurface.copy(alpha = 0.38f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (cluster.isMixedApps && !expanded) {
                    val appsHint = remember(cluster.key) {
                        cluster.events
                            .map { it.appName }
                            .distinct()
                            .take(3)
                            .joinToString("、")
                            .let { names ->
                                val extra = cluster.events.map { it.packageName }.distinct().size - 3
                                if (extra > 0) "$names 等" else names
                            }
                    }
                    Text(
                        text = appsHint,
                        fontSize = 11.sp,
                        color = onSurface.copy(alpha = 0.24f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                tint = onSurface.copy(alpha = 0.22f),
                modifier = Modifier.size(16.dp)
            )
        }

        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 38.dp, top = 2.dp, bottom = 2.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                cluster.events.forEach { child ->
                    val childTime = remember(child.startTime) {
                        timeFormat.format(Date(child.startTime))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = childTime,
                            fontSize = 10.sp,
                            color = onSurface.copy(alpha = 0.24f),
                            letterSpacing = 0.2.sp
                        )
                        Text(
                            text = child.gateQuitLine ?: "离开了 · ${child.appName}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            color = onSurface.copy(alpha = 0.36f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (child.recordId == highlightRecordId && highlightRecordId != null) {
                        LaunchedEffect(highlightRecordId) { onHighlightDone() }
                    }
                }
            }
        }
    }
}

// 兼容旧调用名（若外部/预览仍引用）
@Composable
internal fun SeedUsageCard(
    event: TimelineEvent.UsageEvent,
    appInfo: AppInfo? = null,
    cardBg: Color,
    onSurface: Color
) = UsageEventCard(event, appInfo, cardBg, onSurface)

@Composable
internal fun InterceptedQuitCard(
    event: TimelineEvent.UsageEvent,
    appInfo: AppInfo? = null,
    cardBg: Color,
    onSurface: Color
) = UsageEventCard(event, appInfo, cardBg, onSurface)

@Composable
internal fun UsageDetailCard(
    event: TimelineEvent.UsageEvent,
    appInfo: AppInfo? = null,
    cardBg: Color,
    onSurface: Color,
    isHighlighted: Boolean = false,
    onHighlightDone: () -> Unit = {},
    realtimeSeconds: Long? = null,
    onClick: () -> Unit = {}
) = UsageEventCard(
    event = event,
    appInfo = appInfo,
    cardBg = cardBg,
    onSurface = onSurface,
    isHighlighted = isHighlighted,
    onHighlightDone = onHighlightDone,
    realtimeSeconds = realtimeSeconds,
    onClick = onClick
)

// ── 重新设定限额：轻量日志行（不再用重警告卡）──────────────────────────────

@Composable
private fun LimitResetEventCard(
    event: TimelineEvent.LimitResetEvent,
    appInfo: AppInfo? = null,
    cardBg: Color,
    onSurface: Color
) {
    val changeSummary = buildString {
        if (event.dailyChanged || !event.weeklyChanged) {
            append("每日 ${event.oldDailyLimitMinutes}→${event.newDailyLimitMinutes}分")
            if (event.extendedMinutes != 0) {
                append(
                    if (event.extendedMinutes > 0) " · +${event.extendedMinutes}分"
                    else " · ${event.extendedMinutes}分"
                )
            }
        }
        if (event.weeklyChanged) {
            if (isNotEmpty()) append("  ")
            append("每周 ${event.oldWeeklyLimitMinutes}→${event.newWeeklyLimitMinutes}分")
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(onSurface.copy(alpha = 0.04f))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TimelineAppIcon(
            appInfo = appInfo,
            appName = event.appName,
            dimmed = false,
            accentFallback = LogoGreen,
            onSurface = onSurface,
            size = 26.dp
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "调整了限制",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = onSurface.copy(alpha = 0.72f),
                maxLines = 1
            )
            Text(
                text = "${event.appName} · $changeSummary",
                fontSize = 11.sp,
                color = onSurface.copy(alpha = 0.42f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ── 空态 ──────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyTimelineContent(
    onSurface: Color,
    outline: Color
) {
    val rail = outline.copy(alpha = 0.20f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 28.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(TimelineRailWidth)
        ) {
            Box(
                modifier = Modifier
                    .width(TimelineRailStroke)
                    .height(28.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(rail, rail.copy(alpha = 0f))
                        )
                    )
            )
        }
        Text(
            text = "涟漪会落在这根轴上",
            fontSize = 13.sp,
            color = onSurface.copy(alpha = 0.30f),
            lineHeight = 19.sp,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, top = 4.dp, end = 8.dp)
        )
    }
}

// ── 对照 / 备注编辑弹窗 ──────────────────────────────────────────────────────

@Composable
internal fun NoteEditDialog(
    event: TimelineEvent.UsageEvent,
    cs: ColorScheme,
    focus: RecordEditFocus = RecordEditFocus.Note,
    isAutoPrompt: Boolean = false,
    onConfirm: (note: String?, mindfulnessLevel: Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var noteText by remember(event.recordId) { mutableStateOf(event.note ?: "") }
    var selectedLevel by remember(event.recordId) {
        mutableStateOf(
            event.mindfulnessLevel?.takeIf { UsageRecordEntity.MindfulnessLevel.isValid(it) }
        )
    }
    val focusRequester = remember { FocusRequester() }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val startStr = remember(event.startTime) { timeFormat.format(Date(event.startTime)) }
    val endStr = if (event.endTime > 0) timeFormat.format(Date(event.endTime)) else "进行中"
    val purposeText = event.purpose?.trim().orEmpty()
    val requireCompare = focus == RecordEditFocus.Compare
    val canSave = !requireCompare || UsageRecordEntity.MindfulnessLevel.isValid(selectedLevel)
    val durationNarrative = formatDurationNarrative(event.durationSeconds.coerceAtLeast(0L))
    val notePlaceholder = UsageRecordEntity.MindfulnessLevel.notePlaceholder(selectedLevel)
    val noteLabel = UsageRecordEntity.MindfulnessLevel.noteSectionLabel(selectedLevel)

    LaunchedEffect(focus) {
        if (focus == RecordEditFocus.Note) focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        titleContentColor = cs.onSurface,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = when {
                        focus == RecordEditFocus.Compare -> "对照一下"
                        purposeText.isNotEmpty() -> "备注"
                        else -> event.appName
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface
                )
                Text(
                    text = if (purposeText.isNotEmpty()) {
                        "带着意图用了 $durationNarrative"
                    } else {
                        "$startStr – $endStr · $durationNarrative"
                    },
                    fontSize = 12.sp,
                    color = cs.onSurface.copy(alpha = 0.45f)
                )
                if (purposeText.isNotEmpty()) {
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
                            text = purposeText,
                            fontSize = 12.sp,
                            color = LogoGreenBright,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = UsageRecordEntity.MindfulnessLevel.COMPARE_PROMPT,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = cs.onSurface.copy(alpha = 0.55f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            UsageRecordEntity.MindfulnessLevel.ALIGNED,
                            UsageRecordEntity.MindfulnessLevel.SLIGHT,
                            UsageRecordEntity.MindfulnessLevel.LARGE
                        ).forEach { level ->
                            val accent = mindfulnessTierAccent(level)
                            val selected = selectedLevel == level
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (selected) accent.copy(alpha = 0.16f)
                                        else cs.onSurface.copy(alpha = 0.04f)
                                    )
                                    .border(
                                        width = if (selected) 1.5.dp else 1.dp,
                                        color = if (selected) accent.copy(alpha = 0.85f)
                                        else cs.outlineVariant.copy(alpha = 0.7f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { selectedLevel = level }
                                    .padding(vertical = 10.dp)
                            ) {
                                Text(
                                    text = UsageRecordEntity.MindfulnessLevel.tierLabel(level),
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (selected) accent else cs.onSurface.copy(alpha = 0.50f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = noteLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = cs.onSurface.copy(alpha = 0.55f)
                    )
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { if (it.length <= 100) noteText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        placeholder = {
                            Text(
                                notePlaceholder,
                                fontSize = 14.sp,
                                color = cs.onSurface.copy(alpha = 0.28f)
                            )
                        },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = selectedLevel?.let { mindfulnessTierAccent(it) }
                                ?: LogoGreen,
                            unfocusedBorderColor = cs.outlineVariant,
                            focusedTextColor = cs.onSurface,
                            unfocusedTextColor = cs.onSurface,
                            cursorColor = selectedLevel?.let { mindfulnessTierAccent(it) }
                                ?: LogoGreen
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (!canSave) return@TextButton
                    onConfirm(
                        noteText.trim().ifBlank { null },
                        selectedLevel?.takeIf { UsageRecordEntity.MindfulnessLevel.isValid(it) }
                    )
                },
                enabled = canSave,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = LogoGreen,
                    disabledContentColor = cs.onSurface.copy(alpha = 0.28f)
                )
            ) {
                Text("保存", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = cs.onSurface.copy(alpha = 0.4f))
            ) {
                Text(if (isAutoPrompt) "稍后再说" else "取消")
            }
        }
    )
}

// ── 权限呼吸条（轻打断，不再用整张警告卡）──────────────────────────────────

@Composable
private fun PermissionBreathStrip(
    permissionStatus: PermissionStatus,
    onFix: () -> Unit = {}
) {
    val warningColor = Color(0xFFE8941A)
    val cs = MaterialTheme.colorScheme
    val missing = buildList {
        if (!permissionStatus.hasOverlay) add("悬浮窗")
        if (!permissionStatus.hasUsageStats) add("使用情况")
        if (!permissionStatus.hasBatteryOptimizationIgnored) add("电池")
        if (!permissionStatus.hasNotification) add("通知")
    }
    val label = if (missing.isEmpty()) {
        "部分权限未开启"
    } else {
        "待开 · ${missing.take(2).joinToString("、")}"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(warningColor.copy(alpha = if (cs.surface.red < 0.2f) 0.12f else 0.08f))
            .clickable(onClick = onFix)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(warningColor)
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = warningColor.copy(alpha = 0.92f),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "去处理",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = warningColor
        )
    }
}

@Composable
private fun PermissionWarningCard(
    permissionStatus: PermissionStatus,
    onFix: () -> Unit = {}
) {
    PermissionBreathStrip(permissionStatus = permissionStatus, onFix = onFix)
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

// ── 监控坑位轨：圆角槽卡片 · 图标→主信号→名称（借鉴统一槽形态）────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppMonitorRow(
    monitoredApps: List<AppInfo>,
    summaries: List<AppUsageSummary>,
    ongoingPackageName: String?,
    onAppClick: (String) -> Unit,
    onManageClick: () -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 未系锚由 ColdStartMooringScreen 承接
    if (monitoredApps.isEmpty()) return

    val cs = MaterialTheme.colorScheme
    val summaryMap = summaries.associateBy { it.packageName }
    val accentGreen = LogoGreen

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 2.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "系着 · ${monitoredApps.size}",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface.copy(alpha = 0.28f),
                letterSpacing = 0.6.sp
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onManageClick() }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        "管理",
                        fontSize = 11.sp,
                        color = accentGreen.copy(alpha = 0.65f),
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "管理",
                        tint = accentGreen.copy(alpha = 0.45f),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            monitoredApps.forEach { app ->
                AppMonitorSlotCard(
                    app = app,
                    summary = summaryMap[app.packageName],
                    isOngoing = app.packageName == ongoingPackageName,
                    cs = cs,
                    onClick = { onAppClick(app.packageName) },
                    onLongClick = onManageClick
                )
            }
            AddMonitorSlotCard(
                accentGreen = accentGreen,
                onClick = onAddClick
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 10.dp)
                .height(0.5.dp)
                .background(cs.outlineVariant.copy(alpha = 0.35f))
        )
    }
}

/**
 * 冷启动：单次系锚仪式屏。
 * 品牌 + 一句主文 + 一句说明 + 唯一 CTA；不预演空时间轴。
 */
@Composable
private fun ColdStartMooringScreen(
    permStatus: PermissionStatus,
    onPermissionFix: () -> Unit,
    onAddAppClick: () -> Unit,
    onSurface: Color,
    onOriginHeightMeasured: (Float) -> Unit
) {
    val accentGreen = LogoGreen
    val infiniteTransition = rememberInfiniteTransition(label = "cold_moor")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.40f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cold_pulse"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .onGloballyPositioned { coords ->
                onOriginHeightMeasured(coords.size.height.toFloat())
            }
            .padding(horizontal = 20.dp)
            .padding(bottom = 40.dp)
    ) {
        if (!permStatus.allGranted) {
            Spacer(modifier = Modifier.height(8.dp))
            PermissionBreathStrip(
                permissionStatus = permStatus,
                onFix = onPermissionFix
            )
            Spacer(modifier = Modifier.height(20.dp))
        } else {
            Spacer(modifier = Modifier.height(28.dp))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(accentGreen.copy(alpha = 0.08f + 0.08f * pulse))
                )
                HomeBrandMark(
                    permissionOk = permStatus.allGranted,
                    pulseAlpha = pulse,
                    size = 32.dp
                )
            }
            Text(
                text = stringResource(R.string.app_name),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = onSurface.copy(alpha = 0.70f),
                letterSpacing = 0.4.sp
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "系上第一只锚",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = onSurface.copy(alpha = 0.92f),
            lineHeight = 34.sp,
            letterSpacing = (-0.6).sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "选一个常刷的 App。打开前先问一句为什么，用完也能按时停。",
            fontSize = 14.sp,
            color = onSurface.copy(alpha = 0.42f),
            lineHeight = 21.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(accentGreen.copy(alpha = 0.14f))
                .border(
                    width = 1.dp,
                    color = accentGreen.copy(alpha = 0.28f + 0.22f * pulse),
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable(onClick = onAddAppClick)
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentGreen.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.95f),
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "去挑选应用",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = onSurface.copy(alpha = 0.92f)
                )
                Text(
                    text = "意图门 · 时长锁，可按需开启",
                    fontSize = 12.sp,
                    color = onSurface.copy(alpha = 0.40f)
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = accentGreen.copy(alpha = 0.70f),
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "系上之后，每一次打开都会落在今日轴上",
            fontSize = 12.sp,
            color = onSurface.copy(alpha = 0.26f),
            lineHeight = 17.sp
        )
    }
}

/** 统一槽位尺寸：图标 → 主信号 → 名称 */
private val MonitorSlotWidth = 72.dp
private val MonitorSlotHeight = 108.dp
private val MonitorSlotCorner = 20.dp
private val MonitorSlotIcon = 34.dp
private val MonitorSlotRing = 44.dp
private val MonitorAlertThreshold = 0.8f

/**
 * 槽卡用量文案：与时间轴同一套叙事时长。
 */
private fun formatSlotUsage(seconds: Long): String = formatDurationNarrative(seconds)

/**
 * 占用坑位：圆角槽卡片。
 * 环 + 用量始终在场（有冲击力的工具态）；告警/进行中只改颜色与脉动，不靠「藏起来」变安静。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppMonitorSlotCard(
    app: AppInfo,
    summary: AppUsageSummary?,
    isOngoing: Boolean,
    cs: ColorScheme,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val usedSeconds = summary?.todaySeconds ?: 0L
    val timeLockOn = app.timeLimitEnabled && !app.isUninstalled
    val intentOn = app.requireIntentOnOpen && !app.isUninstalled
    val periodOn = app.periodLockEnabled && !app.isUninstalled
    val limitSeconds = if (timeLockOn) {
        summary?.dailyLimitSeconds ?: (app.effectiveDailyLimitMinutes() * 60L)
    } else {
        0L
    }
    val progress = if (limitSeconds > 0) {
        (usedSeconds.toFloat() / limitSeconds).coerceAtMost(1f)
    } else {
        0f
    }
    val showAlert = timeLockOn && progress >= MonitorAlertThreshold
    val capped = timeLockOn && progress >= 1f
    // 有时长锁 → 真实进度；无锁但进行中 → 满环脉动；其余只露轨道
    val ringProgress = when {
        app.isUninstalled -> 0f
        timeLockOn -> progress
        isOngoing -> 1f
        else -> 0f
    }
    val ringColor = when {
        app.isUninstalled -> Color(0xFFB05A2A)
        capped -> Color(0xFFE74C3C)
        showAlert -> Color(0xFFE8941A)
        isOngoing -> LogoGreen
        timeLockOn && progress > 0f -> LogoGreen
        else -> LogoGreen.copy(alpha = 0.55f)
    }
    val statusText = when {
        app.isUninstalled -> "已卸"
        else -> formatSlotUsage(usedSeconds)
    }
    val statusColor = when {
        app.isUninstalled -> Color(0xFFB05A2A).copy(alpha = 0.90f)
        capped -> Color(0xFFE74C3C)
        showAlert -> Color(0xFFE8941A)
        isOngoing -> LogoGreen
        usedSeconds > 0L -> cs.onSurface.copy(alpha = 0.72f)
        else -> cs.onSurface.copy(alpha = 0.38f)
    }

    val animProgress by animateFloatAsState(
        targetValue = ringProgress,
        animationSpec = tween(600),
        label = "slotProg_${app.packageName}"
    )

    val slotShape = RoundedCornerShape(MonitorSlotCorner)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .width(MonitorSlotWidth)
            .height(MonitorSlotHeight)
            .clip(slotShape)
            .background(cs.surface.copy(alpha = 0.92f))
            .border(
                width = 1.dp,
                color = when {
                    capped -> Color(0xFFE74C3C).copy(alpha = 0.35f)
                    showAlert -> Color(0xFFE8941A).copy(alpha = 0.32f)
                    isOngoing -> LogoGreen.copy(alpha = 0.28f)
                    else -> cs.outlineVariant.copy(alpha = 0.16f)
                },
                shape = slotShape
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 6.dp, vertical = 10.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier.size(MonitorSlotRing),
                contentAlignment = Alignment.Center
            ) {
                if (!app.isUninstalled) {
                    MonitorProgressRing(
                        progress = animProgress,
                        trackColor = cs.onSurface.copy(
                            alpha = if (timeLockOn || isOngoing) 0.14f else 0.10f
                        ),
                        progressColor = ringColor.copy(alpha = 0.95f),
                        pulse = isOngoing
                    )
                }

                val icon = remember(app.packageName) {
                    try {
                        context.packageManager.getApplicationIcon(app.packageName)
                    } catch (_: Exception) {
                        app.icon
                    }
                }
                if (icon != null) {
                    val bitmap = remember(icon) { icon.toBitmap().asImageBitmap() }
                    Image(
                        bitmap = bitmap,
                        contentDescription = app.appName,
                        modifier = Modifier
                            .size(MonitorSlotIcon)
                            .clip(RoundedCornerShape(10.dp))
                            .alpha(if (app.isUninstalled) 0.40f else 1f)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(MonitorSlotIcon)
                            .clip(RoundedCornerShape(10.dp))
                            .background(cs.outlineVariant.copy(alpha = 0.20f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Android,
                            contentDescription = null,
                            tint = cs.onSurface.copy(alpha = 0.35f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 能力：图标下方，比角标更可读
            Box(
                modifier = Modifier.height(12.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    app.isUninstalled -> Unit
                    intentOn || timeLockOn || periodOn -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (intentOn) {
                                CapabilityMark(
                                    kind = CapabilityKind.IntentGate,
                                    form = CapabilityForm.Compact,
                                    active = true,
                                    tint = LogoGreen.copy(alpha = 0.85f),
                                    size = 11.dp
                                )
                            }
                            if (timeLockOn) {
                                CapabilityMark(
                                    kind = CapabilityKind.TimeLock,
                                    form = CapabilityForm.Compact,
                                    active = true,
                                    tint = if (showAlert || capped) ringColor else LogoGreen.copy(alpha = 0.85f),
                                    size = 11.dp
                                )
                            }
                            if (periodOn) {
                                CapabilityMark(
                                    kind = CapabilityKind.PeriodLock,
                                    form = CapabilityForm.Compact,
                                    active = true,
                                    tint = LogoGreen.copy(alpha = 0.85f),
                                    size = 11.dp
                                )
                            }
                        }
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .clip(CircleShape)
                                .background(cs.onSurface.copy(alpha = 0.16f))
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = statusText,
                fontSize = if (statusText.length > 4) 12.sp else 14.sp,
                fontWeight = FontWeight.Bold,
                color = statusColor,
                letterSpacing = (-0.2).sp,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }

        Text(
            text = app.appName,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = cs.onSurface.copy(alpha = if (app.isUninstalled) 0.32f else 0.45f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 添加坑：细绿描边 + 虚线圆 + */
@Composable
private fun AddMonitorSlotCard(
    accentGreen: Color,
    onClick: () -> Unit
) {
    val slotShape = RoundedCornerShape(MonitorSlotCorner)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(MonitorSlotWidth)
            .height(MonitorSlotHeight)
            .clip(slotShape)
            .border(1.dp, accentGreen.copy(alpha = 0.28f), slotShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier.size(MonitorSlotRing),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(5.dp.toPx(), 4.dp.toPx()),
                        0f
                    )
                )
                drawCircle(
                    color = accentGreen.copy(alpha = 0.55f),
                    radius = (size.minDimension / 2f) - 2.dp.toPx(),
                    style = stroke
                )
            }
            Icon(
                Icons.Default.Add,
                contentDescription = "添加监控应用",
                tint = accentGreen.copy(alpha = 0.75f),
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.height(22.dp))
        Text(
            text = "添加",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = accentGreen.copy(alpha = 0.55f)
        )
    }
}

@Composable
private fun MonitorProgressRing(
    progress: Float,
    trackColor: Color,
    progressColor: Color,
    pulse: Boolean
) {
    if (pulse) {
        val infiniteTransition = rememberInfiniteTransition(label = "slot_ring")
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "ring_pulse"
        )
        MonitorProgressRingCanvas(
            progress = progress,
            trackColor = trackColor,
            progressColor = progressColor.copy(alpha = progressColor.alpha * pulseAlpha)
        )
    } else {
        MonitorProgressRingCanvas(
            progress = progress,
            trackColor = trackColor,
            progressColor = progressColor
        )
    }
}

@Composable
private fun MonitorProgressRingCanvas(
    progress: Float,
    trackColor: Color,
    progressColor: Color
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeWidth = 3.dp.toPx()
        val diameter = size.minDimension - strokeWidth
        val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
        val arcSize = Size(diameter, diameter)
        if (trackColor.alpha > 0f) {
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        val sweep = (progress.coerceIn(0f, 1f) * 360f)
        if (sweep > 0.5f) {
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}
