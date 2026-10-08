package com.life.mindfulnessapp.ui.home

import android.Manifest
import android.os.Build
import androidx.compose.animation.core.*
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.activity.ComponentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.life.mindfulnessapp.R
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.data.repository.UsageRecordRepository.Companion.getDayRange
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.domain.model.AppUsageSummary
import com.life.mindfulnessapp.domain.model.IntentKind
import com.life.mindfulnessapp.domain.model.TimelineDisplayItem
import com.life.mindfulnessapp.domain.model.TimelineEvent
import com.life.mindfulnessapp.domain.model.TimelineSectionItem
import com.life.mindfulnessapp.domain.model.buildTimelineSections
import com.life.mindfulnessapp.domain.model.collapseTimelineForDisplay
import com.life.mindfulnessapp.domain.model.buildHeldAwayOrdinalIndex
import com.life.mindfulnessapp.domain.model.groupTimelineSections
import com.life.mindfulnessapp.domain.model.HeldAwayOrdinalIndex
import com.life.mindfulnessapp.domain.usecase.PermissionStatus
import com.life.mindfulnessapp.ui.common.CompareSectionColors
import com.life.mindfulnessapp.ui.common.IntentCompareSection
import com.life.mindfulnessapp.ui.common.PermissionGateDialog
import com.life.mindfulnessapp.ui.common.compareCanConfirm
import com.life.mindfulnessapp.ui.common.PermissionGateMode
import com.life.mindfulnessapp.ui.common.PermissionSettingsIntents
import com.life.mindfulnessapp.ui.common.CompareTierColors
import com.life.mindfulnessapp.ui.features.UsageLogPeriodHeader
import com.life.mindfulnessapp.ui.features.UsageLogTransitionRow
import com.life.mindfulnessapp.ui.features.resolveUsageLogActiveHour
import com.life.mindfulnessapp.ui.features.usageLogAppShade
import kotlinx.coroutines.launch
import com.life.mindfulnessapp.domain.model.ComparePolicy
import com.life.mindfulnessapp.domain.model.UsageLogListItem
import com.life.mindfulnessapp.domain.model.UsageLogPeriodGroup
import com.life.mindfulnessapp.domain.model.UsageTransitionScene
import com.life.mindfulnessapp.ui.theme.HeatmapNeutral
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.theme.LogoGreenBright
import com.life.mindfulnessapp.ui.vip.AccessGateDialog
import com.life.mindfulnessapp.ui.vip.AccessGateViewModel
import java.text.SimpleDateFormat
import java.util.*

// ── 主入口 ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    // 必须用 Activity 作为 owner，确保与 MainActivity 里的 homeViewModel 是同一实例
    // 这样 MainActivity.handleNoteIntent 设置的 pendingHighlightId 才能被 HomeScreen 正确读到
    viewModel: HomeViewModel = hiltViewModel(LocalContext.current as ComponentActivity),
    accessGateViewModel: AccessGateViewModel = hiltViewModel(),
    weekLookbackTipViewModel: com.life.mindfulnessapp.ui.lookback.WeekLookbackTipViewModel = hiltViewModel(),
    onNavigateToAppDetail: (String) -> Unit = {},
    /** 坑位排序（原「管理」；按能力坑位视图在「能力」Tab） */
    onNavigateToManage: () -> Unit = {},
    /** 坑位「+」：跳到能力 Tab · 管理 */
    onNavigateToAdd: () -> Unit = {},
    onNavigateToVip: () -> Unit = {},
    /** 全部记录（月历 + 历史日流水） */
    onNavigateToRecordHistory: () -> Unit = {},
    /** 周回望 */
    onNavigateToWeekLookback: () -> Unit = {},
    /** 守计划列表 */
    onNavigateToPlanBlocks: (() -> Unit)? = null,
    /** 后台保活指南（监控曾中断时） */
    onNavigateToKeepAliveGuide: () -> Unit = {}
) {
    val context = LocalContext.current
    val openPlanBlocks = onNavigateToPlanBlocks ?: {
        context.startActivity(
            com.life.mindfulnessapp.PlanBlockListActivity.createIntent(context)
        )
    }
    val summaries by viewModel.usageSummaries.collectAsState()
    val permStatus by viewModel.permissionStatus.collectAsState()
    val homeReady by viewModel.homeReady.collectAsState()
    val monitoredAppsWithIcon by viewModel.monitoredAppsWithIcon.collectAsState()
    val monitorInterruptPending by viewModel.monitorInterruptPending.collectAsState()
    val activePlanBlock by viewModel.activePlanBlock.collectAsState()
    val isAtFreeLimit by viewModel.isAtFreeLimit.collectAsState()
    val accessGate by accessGateViewModel.accessGate.collectAsState()
    val showSundayLookbackTip by weekLookbackTipViewModel.showSundayTip.collectAsState()
    val timeline by viewModel.todayTimeline.collectAsState()
    val homeLogGroups by viewModel.homeLogGroups.collectAsState()
    val dayInsight by viewModel.dayInsight.collectAsState()
    val ongoingSessionSeconds by viewModel.ongoingSessionSeconds.collectAsState()
    val activeSessionPackageName by viewModel.activeSessionPackageName.collectAsState()
    val dayPulse = remember(timeline) { deriveDayPulse(timeline) }
    val cs = MaterialTheme.colorScheme

    var pendingAddAfterUnlock by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isAtFreeLimit, pendingAddAfterUnlock, accessGate.visible) {
        if (pendingAddAfterUnlock && !isAtFreeLimit && !accessGate.visible) {
            pendingAddAfterUnlock = false
            onNavigateToAdd()
        }
    }

    fun tryAddMonitor() {
        if (isAtFreeLimit) {
            pendingAddAfterUnlock = true
            accessGateViewModel.request()
        } else {
            onNavigateToAdd()
        }
    }

    // HomeScreen 自身的备注/对照编辑弹窗状态
    var editingEvent by remember { mutableStateOf<TimelineEvent.UsageEvent?>(null) }
    var editFocus by remember { mutableStateOf(RecordEditFocus.Note) }
    // 是否是「结束使用后自动触发」的备注弹窗（影响弹窗文案）
    var isAutoNotePrompt by remember { mutableStateOf(false) }

    // 使用结束后高亮引导的 recordId（只滚动+高亮，不自动弹窗）
    // 直接用 ViewModel StateFlow，不经过本地 remember —— 冷热启动都能即时生效
    val guidedRecordId by viewModel.pendingHighlightId.collectAsState()
    val pendingOpenCompareId by viewModel.pendingOpenCompareId.collectAsState()
    // 权限处理弹窗：已有监控但推荐权限不完整时自动提醒（空态交给系锚场景门）
    var showPermissionDialog by remember { mutableStateOf(false) }
    var autoPermissionPrompted by rememberSaveable { mutableStateOf(false) }

    var notificationRuntimeAsked by rememberSaveable { mutableStateOf(false) }
    var notificationRuntimeFinished by rememberSaveable {
        mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
    }

    val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.refreshPermissions() }
    val usageLauncher   = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.refreshPermissions() }
    val batteryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.refreshPermissions() }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshPermissions()
        notificationRuntimeFinished = true
    }

    LaunchedEffect(homeReady, permStatus.hasNotification) {
        if (!homeReady) return@LaunchedEffect
        when {
            permStatus.hasNotification ||
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> {
                notificationRuntimeFinished = true
            }
            !notificationRuntimeAsked -> {
                notificationRuntimeAsked = true
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(
        homeReady,
        notificationRuntimeFinished,
        permStatus.recommendedGranted,
        monitoredAppsWithIcon.isNotEmpty()
    ) {
        if (!homeReady || !notificationRuntimeFinished) return@LaunchedEffect
        if (!permStatus.recommendedGranted &&
            monitoredAppsWithIcon.isNotEmpty() &&
            !autoPermissionPrompted
        ) {
            autoPermissionPrompted = true
            showPermissionDialog = true
            viewModel.onPermissionPromptShown()
        }
        if (permStatus.recommendedGranted && showPermissionDialog) {
            showPermissionDialog = false
        }
    }

    // 离开超时横条「对照一下」：时间轴出现目标条后自动打开对照编辑
    LaunchedEffect(pendingOpenCompareId, timeline) {
        val targetId = pendingOpenCompareId ?: return@LaunchedEffect
        val event = timeline
            .filterIsInstance<TimelineEvent.UsageEvent>()
            .firstOrNull { it.recordId == targetId }
            ?: return@LaunchedEffect
        if (event.hasIntentGate && !event.isGateQuit && !event.isSeed) {
            editFocus = RecordEditFocus.Compare
            editingEvent = event
            isAutoNotePrompt = true
            viewModel.consumeOpenCompareEvent()
            viewModel.consumeOpenNoteEvent()
        } else {
            viewModel.consumeOpenCompareEvent()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadData()
        viewModel.refreshMonitorHealth()
    }

    val lifecycleOwner = LocalContext.current as? LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        val owner = lifecycleOwner ?: return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshMonitorHealth()
                viewModel.refreshPermissions()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }


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

        // 数据未齐时不画首页，避免「未系锚 / 权限不完整」假状态闪一帧
        if (homeReady) {
            // ── 下区域：以 LazyColumn 统一承载 header + 时间轴 ─────────────────
            HomeContentList(
                listState = listState,
                collapseProgress = collapseProgress,
                summaries = summaries,
                permStatus = permStatus,
                monitoredAppsWithIcon = monitoredAppsWithIcon,
                timeline = timeline,
                homeLogGroups = homeLogGroups,
                dayPulse = dayPulse,
                dayInsight = dayInsight,
                showSundayLookbackTip = showSundayLookbackTip,
                onDismissSundayTip = weekLookbackTipViewModel::dismiss,
                ongoingSessionSeconds = ongoingSessionSeconds,
                activeSessionPackageName = activeSessionPackageName,
                onOriginHeightMeasured = { originHeightPx = it },
                onRecordEdit = { event, focus ->
                    // 回顾备注仅意图门真实进入条目（守住离开 / 种子 / 纯时长锁不含）
                    if (event.hasIntentGate && !event.isGateQuit && !event.isSeed) {
                        viewModel.consumeOpenNoteEvent()
                        editFocus = focus
                        editingEvent = event
                    }
                },
                highlightRecordId = editingEvent?.recordId ?: guidedRecordId,
                onHighlightDone = { viewModel.consumeOpenNoteEvent() },
                onPermissionFix = { showPermissionDialog = true },
                monitorInterruptPending = monitorInterruptPending,
                onMonitorInterruptFix = {
                    viewModel.dismissMonitorInterruptBanner()
                    onNavigateToKeepAliveGuide()
                },
                onManageClick = onNavigateToManage,
                onAddAppClick = { tryAddMonitor() },
                onAppClick = onNavigateToAppDetail,
                onNavigateToRecordHistory = onNavigateToRecordHistory,
                onNavigateToWeekLookback = onNavigateToWeekLookback,
                activePlanBlock = activePlanBlock,
                onNavigateToPlanBlocks = openPlanBlocks,
                cardBg = cs.surface,
                pageBg = cs.background,
                onSurface = cs.onSurface,
                outline = cs.outlineVariant
            )

            // ── 浮层：坍缩态（日期 + 一句今日脉搏）─────────────────────────────
            CollapsedHeaderOverlay(
                collapseProgress = collapseProgress,
                permissionStatus = permStatus,
                dayInsight = dayInsight,
                monitoredApps = monitoredAppsWithIcon,
                cs = cs
            )
        }

        // 对照 / 备注编辑弹窗
        editingEvent?.let { event ->
            NoteEditDialog(
                event = event,
                cs = cs,
                focus = editFocus,
                isAutoPrompt = isAutoNotePrompt,
                onConfirm = { newNote, level, drift ->
                    viewModel.updateRecordReview(event.recordId, newNote, level, drift)
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
        if (homeReady && showPermissionDialog) {
            PermissionGateDialog(
                permissionStatus = permStatus,
                mode = PermissionGateMode.Soft,
                onDismiss = { showPermissionDialog = false },
                onGrantOverlay = {
                    overlayLauncher.launch(PermissionSettingsIntents.overlay(context))
                },
                onGrantUsage = {
                    usageLauncher.launch(PermissionSettingsIntents.usageAccess())
                },
                onGrantBattery = {
                    batteryLauncher.launch(PermissionSettingsIntents.ignoreBattery(context))
                },
                onGrantNotification = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.startActivity(PermissionSettingsIntents.appNotification(context))
                    }
                    viewModel.refreshPermissions()
                },
                onGrantAccessibility = {
                    context.startActivity(PermissionSettingsIntents.accessibilityKeepAlive())
                    viewModel.refreshPermissions()
                }
            )
        }

        if (accessGate.visible || accessGate.unlockToast != null) {
            AccessGateDialog(
                state = accessGate,
                cardColor = cs.surface,
                textPrimary = cs.onSurface,
                textSecondary = cs.onSurfaceVariant,
                borderColor = cs.outline,
                accentGreen = LogoGreen,
                onDismiss = {
                    pendingAddAfterUnlock = false
                    accessGateViewModel.dismiss()
                },
                onCodeChange = accessGateViewModel::onCodeChange,
                onRedeem = accessGateViewModel::redeem,
                onOpenClaim = accessGateViewModel::openClaimStep,
                onOpenRedeem = accessGateViewModel::openRedeemStep,
                onClaimChannelChange = accessGateViewModel::onClaimChannelChange,
                onClaimContactChange = accessGateViewModel::onClaimContactChange,
                onSubmitClaim = accessGateViewModel::submitClaim,
                onRedeemIssued = accessGateViewModel::redeemIssued,
                onViewMembership = {
                    pendingAddAfterUnlock = false
                    accessGateViewModel.dismiss()
                    onNavigateToVip()
                },
                onBackToGate = accessGateViewModel::backToGate,
                onConsumeUnlockToast = accessGateViewModel::consumeUnlockToast
            )
        }
    } // end Box
}

// ── 整体内容列表（上区域 header + 下区域时间轴，统一在一个 LazyColumn）────────

/** 坍缩顶栏内容高度（不含状态栏），吸顶时段头需让出 */
private val HomeCollapsedBarContent = 40.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeContentList(
    listState: androidx.compose.foundation.lazy.LazyListState,
    collapseProgress: Float,
    summaries: List<AppUsageSummary>,
    permStatus: PermissionStatus,
    monitoredAppsWithIcon: List<AppInfo>,
    timeline: List<TimelineEvent>,
    homeLogGroups: List<UsageLogPeriodGroup>,
    dayPulse: DayPulse,
    dayInsight: com.life.mindfulnessapp.domain.model.DayInsightSnapshot,
    showSundayLookbackTip: Boolean = false,
    onDismissSundayTip: () -> Unit = {},
    /** (recordId, currentSessionSeconds) 进行中会话的实时有效秒数（已排除后台时间） */
    ongoingSessionSeconds: Pair<Long, Long>?,
    /** 监控轨高亮包名（任意活跃会话，含前台） */
    activeSessionPackageName: String?,
    onOriginHeightMeasured: (Float) -> Unit,
    onRecordEdit: (TimelineEvent.UsageEvent, RecordEditFocus) -> Unit,
    highlightRecordId: Long?,
    onHighlightDone: () -> Unit,
    onPermissionFix: () -> Unit,
    monitorInterruptPending: Boolean = false,
    onMonitorInterruptFix: () -> Unit = {},
    onManageClick: () -> Unit,
    onAddAppClick: () -> Unit,
    onAppClick: (String) -> Unit,
    onNavigateToRecordHistory: () -> Unit,
    onNavigateToWeekLookback: () -> Unit = {},
    activePlanBlock: com.life.mindfulnessapp.domain.model.PlanBlock? = null,
    onNavigateToPlanBlocks: () -> Unit = {},
    cardBg: Color,
    pageBg: Color,
    onSurface: Color,
    outline: Color
) {
    val eventsById = remember(timeline) {
        timeline.filterIsInstance<TimelineEvent.UsageEvent>().associateBy { it.recordId }
    }
    val scope = rememberCoroutineScope()
    val logKeyIndex = remember(homeLogGroups) { homeLogKeyIndex(homeLogGroups) }
    val appShade = remember(homeLogGroups) { usageLogAppShade(homeLogGroups) }
    val logRowByKey = remember(homeLogGroups) {
        homeLogGroups.asSequence()
            .flatMap { it.rows.asSequence() }
            .filterIsInstance<UsageLogListItem.Row>()
            .associateBy { it.key }
    }
    val activeHour by remember(logRowByKey) {
        derivedStateOf { resolveUsageLogActiveHour(listState, logRowByKey) }
    }
    val unmoored = monitoredAppsWithIcon.isEmpty()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // 仅完全坍缩后下移列表顶边，避免与滚动插值互搏；时段 sticky 贴在浮层下沿
    val listTopInset =
        if (collapseProgress >= 1f) statusTop + HomeCollapsedBarContent else 0.dp

    LaunchedEffect(highlightRecordId) {
        if (highlightRecordId == null) return@LaunchedEffect
        kotlinx.coroutines.delay(2400)
        onHighlightDone()
    }
    LaunchedEffect(highlightRecordId, homeLogGroups, unmoored) {
        if (highlightRecordId == null || unmoored) return@LaunchedEffect
        repeat(8) { attempt ->
            val idx = logKeyIndex.entries.firstOrNull { (key, _) ->
                key.endsWith("_$highlightRecordId")
            }?.value
            if (idx != null) {
                listState.animateScrollToItem(index = idx)
                return@LaunchedEffect
            }
            if (attempt < 7) kotlinx.coroutines.delay(120)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(top = listTopInset),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // ── item 0：今日日期 + 坑位 + 简要数据（未系锚也展示空坑骨架）────
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
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        TodayDateHerald(
                            onSurface = onSurface,
                            permissionCue = permStatus.takeUnless { it.recommendedGranted },
                            onPermissionFix = onPermissionFix,
                            onOpenHistory = onNavigateToRecordHistory,
                            onOpenWeekLookback = onNavigateToWeekLookback
                        )
                    }
                    if (showSundayLookbackTip && !unmoored) {
                        Spacer(modifier = Modifier.height(10.dp))
                        com.life.mindfulnessapp.ui.lookback.SundayLookbackTipBar(
                            onOpen = onNavigateToWeekLookback,
                            onDismiss = onDismissSundayTip,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                    if (monitorInterruptPending) {
                        Spacer(modifier = Modifier.height(10.dp))
                        MonitorInterruptStrip(
                            onFix = onMonitorInterruptFix,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                    if (activePlanBlock != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        ActivePlanHomeStrip(
                            plan = activePlanBlock,
                            onClick = onNavigateToPlanBlocks,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                AppMonitorRow(
                    monitoredApps = monitoredAppsWithIcon,
                    summaries = summaries,
                    pitInsights = dayInsight.pitByPackage,
                    ongoingPackageName = activeSessionPackageName,
                    onAppClick = onAppClick,
                    onManageClick = onManageClick,
                    onAddClick = onAddAppClick
                )
                Spacer(modifier = Modifier.height(12.dp))
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    com.life.mindfulnessapp.ui.common.DayInsightCard(
                        snapshot = dayInsight,
                        unmoored = unmoored
                    )
                }
            }
        }

        item(key = "timeline_resume") {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                TimelineResumeHeader(
                    dayPulse = dayPulse,
                    usageSeconds = summaries.sumOf { it.todaySeconds },
                    unmoored = unmoored,
                    onSurface = onSurface,
                    outline = outline,
                    onOpenHistory = onNavigateToRecordHistory,
                    showMetrics = false
                )
            }
        }

        if (homeLogGroups.isEmpty()) {
            item(key = "timeline_empty") {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    EmptyTimelineContent(
                        onSurface = onSurface,
                        outline = outline,
                        unmoored = unmoored
                    )
                }
            }
        } else {
            homeLogGroups.forEach { group ->
                stickyHeader(key = group.header.key) {
                    UsageLogPeriodHeader(
                        item = group.header,
                        hourChips = group.hourChips,
                        activeHour = activeHour,
                        onHourClick = { chip ->
                            val index = logKeyIndex[chip.anchorKey] ?: return@UsageLogPeriodHeader
                            scope.launch { listState.animateScrollToItem(index) }
                        }
                    )
                }
                items(group.rows, key = { it.key }) { row ->
                    when (row) {
                        is UsageLogListItem.Row -> {
                            val event = row.transition.recordId?.let { eventsById[it] }
                            val showTrail = event != null && (
                                row.transition.scene == UsageTransitionScene.ENTER ||
                                    row.transition.scene == UsageTransitionScene.END
                                )
                            UsageLogTransitionRow(
                                item = row,
                                highlighted = highlightRecordId != null &&
                                    row.transition.recordId == highlightRecordId,
                                shaded = appShade[row.key] == true,
                                trailing = if (showTrail && event != null) {
                                    {
                                        HomeLogRecordTrail(
                                            event = event,
                                            realtimeSeconds = ongoingSessionSeconds
                                                ?.takeIf { it.first == event.recordId }
                                                ?.second,
                                            onSurface = onSurface,
                                            onRecordEdit = onRecordEdit
                                        )
                                    }
                                } else {
                                    null
                                }
                            )
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
}

/** 首页 LazyColumn：item0 顶栏、item1 摘要，日志从 index 2 起 */
private fun homeLogKeyIndex(groups: List<UsageLogPeriodGroup>): Map<String, Int> {
    val map = HashMap<String, Int>()
    var index = 2
    for (group in groups) {
        map[group.header.key] = index
        index++
        for (row in group.rows) {
            map[row.key] = index
            index++
        }
    }
    return map
}

@Composable
private fun HomeLogRecordTrail(
    event: TimelineEvent.UsageEvent,
    realtimeSeconds: Long?,
    onSurface: Color,
    onRecordEdit: (TimelineEvent.UsageEvent, RecordEditFocus) -> Unit
) {
    val effectiveDuration = when {
        event.isOngoing ->
            realtimeSeconds ?: ((System.currentTimeMillis() - event.startTime) / 1000L)
        else -> event.durationSeconds
    }
    val canEdit = event.hasIntentGate && !event.isGateQuit &&
        !event.isPositiveExit && !event.isSeed && !event.isOngoing
    val tier = event.mindfulnessLevel
        ?.takeIf { UsageRecordEntity.MindfulnessLevel.isValid(it) }
    val meetsCompare = ComparePolicy.shouldOfferInlineCompare(
        hasIntentGate = event.hasIntentGate,
        purpose = event.purpose,
        durationSeconds = effectiveDuration,
        compareEnabled = event.compareEnabled,
        compareMinMinutes = event.compareMinMinutes,
        intentKind = event.intentKind
    )
    val note = event.note?.trim()?.takeIf { it.isNotEmpty() }
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        when {
            canEdit && tier == null && meetsCompare -> {
                Text(
                    text = "对照",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LogoGreen.copy(alpha = 0.92f),
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        onRecordEdit(event, RecordEditFocus.Compare)
                    }
                )
            }
            tier != null -> {
                val isDark = onSurface.luminance() > 0.5f
                val accent = CompareTierColors.accent(tier, isDark)
                val label = UsageRecordEntity.MindfulnessLevel.tierLabel(
                    tier,
                    intentKind = event.intentKind
                )
                if (label.isNotEmpty()) {
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(accent.copy(alpha = 0.14f))
                            .clickable { onRecordEdit(event, RecordEditFocus.Note) }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        if (note != null) {
            Text(
                text = note,
                fontSize = 10.sp,
                color = onSurface.copy(alpha = 0.36f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = 88.dp)
                    .clickable { onRecordEdit(event, RecordEditFocus.Note) }
            )
        } else if (canEdit && tier != null) {
            Text(
                text = "记一句",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = onSurface.copy(alpha = 0.45f),
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable {
                    onRecordEdit(event, RecordEditFocus.Note)
                }
            )
        }
    }
}

// ── 今日脉搏 & 判词 ───────────────────────────────────────────────────────────

/** 从时间轴推导的今日脉搏（排除种子） */
private data class DayPulse(
    val mindfulEnters: Int,
    val ungatedEnters: Int,
    val dismisses: Int,
    val positiveExits: Int,
    val enters: Int
)

private fun deriveDayPulse(timeline: List<TimelineEvent>): DayPulse {
    val usages = timeline.filterIsInstance<TimelineEvent.UsageEvent>().filter { !it.isSeed }
    val dismisses = usages.count { it.isGateQuit }
    val positiveExits = usages.count { it.isPositiveExit }
    val enters = usages.filter { !it.isGateQuit && !it.isPositiveExit }
    val mindful = enters.count {
        !it.purpose.isNullOrBlank() && it.intentKind != IntentKind.PURPOSELESS
    }
    return DayPulse(
        mindfulEnters = mindful,
        ungatedEnters = enters.size - mindful,
        dismisses = dismisses,
        positiveExits = positiveExits,
        enters = enters.size
    )
}

@Composable
private fun CollapsedHeaderOverlay(
    collapseProgress: Float,
    permissionStatus: PermissionStatus,
    dayInsight: com.life.mindfulnessapp.domain.model.DayInsightSnapshot,
    monitoredApps: List<AppInfo>,
    cs: ColorScheme
) {
    if (collapseProgress <= 0f) return

    val alpha = collapseProgress.coerceIn(0f, 1f)
    val onBg = cs.onBackground
    val dateLabel = remember {
        SimpleDateFormat("M月d日", Locale.CHINESE).format(Date())
    }
    val pulseLabel = remember(dayInsight, monitoredApps) {
        when {
            monitoredApps.isEmpty() -> "未系锚"
            dayInsight.hasSignal -> dayInsight.headline
            else -> "水面静着"
        }
    }

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
                    permissionOk = permissionStatus.coreGranted,
                    pulseAlpha = dotAlpha,
                    size = 18.dp
                )
                Text(
                    text = dateLabel,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = onBg.copy(alpha = 0.62f),
                    letterSpacing = 0.2.sp
                )
            }

            Text(
                text = pulseLabel,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = onBg.copy(alpha = 0.55f),
                letterSpacing = (-0.2).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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

private fun collapsedPulseLabel(
    pulse: DayPulse,
    summaries: List<AppUsageSummary>,
    unmoored: Boolean
): String {
    if (unmoored) return "未系锚"
    val bits = buildList {
        if (pulse.mindfulEnters > 0) add("意图 ${pulse.mindfulEnters}")
        if (pulse.dismisses > 0) add("守住 ${pulse.dismisses}")
        if (pulse.positiveExits > 0) add("去做 ${pulse.positiveExits}")
        val sec = summaries.sumOf { it.todaySeconds }
        if (sec > 0L) add(formatDurationNarrative(sec))
    }
    return bits.joinToString(" · ").ifBlank { "水面静着" }
}

// ── 品牌锚点（折叠顶栏）──────────────────────────────────────────────────────

@Composable
private fun HomeBrandMark(
    permissionOk: Boolean,
    pulseAlpha: Float,
    size: Dp
) {
    val tint = if (permissionOk) {
        LogoGreen.copy(alpha = 0.72f + 0.28f * pulseAlpha)
    } else {
        Color(0xFFF39C12).copy(alpha = 0.72f + 0.28f * pulseAlpha)
    }
    Icon(
        painter = painterResource(id = R.drawable.ic_stat_anchor),
        contentDescription = stringResource(R.string.app_name),
        modifier = Modifier.size(size),
        tint = tint
    )
}

/**
 * 首页日头：一行日期 + 进历史；权限提示压在下面。
 */
@Composable
private fun TodayDateHerald(
    onSurface: Color,
    permissionCue: PermissionStatus? = null,
    onPermissionFix: (() -> Unit)? = null,
    onOpenHistory: (() -> Unit)? = null,
    onOpenWeekLookback: (() -> Unit)? = null
) {
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            tick++
            val now = System.currentTimeMillis()
            val (_, dayEnd) = getDayRange(now)
            kotlinx.coroutines.delay((dayEnd - now).coerceAtLeast(1_000L))
        }
    }
    val dateLabel = remember(tick) {
        SimpleDateFormat("M月d日", Locale.CHINESE).format(Date())
    }
    val weekdayLabel = remember(tick) {
        SimpleDateFormat("EEEE", Locale.CHINESE).format(Date())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = if (onOpenHistory != null) {
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenHistory)
                } else Modifier
            ) {
                Text(
                    text = dateLabel,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = onSurface.copy(alpha = 0.90f),
                    letterSpacing = (-0.4).sp,
                    lineHeight = 26.sp
                )
                Text(
                    text = weekdayLabel,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = onSurface.copy(alpha = 0.40f),
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (onOpenWeekLookback != null) {
                    Text(
                        text = "周回望",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = LogoGreen.copy(alpha = 0.88f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onOpenWeekLookback)
                            .padding(horizontal = 2.dp, vertical = 2.dp)
                    )
                }
                if (onOpenHistory != null) {
                    Text(
                        text = "历史",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = onSurface.copy(alpha = 0.40f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onOpenHistory)
                            .padding(horizontal = 2.dp, vertical = 2.dp)
                    )
                }
            }
        }
        if (permissionCue != null && onPermissionFix != null) {
            PermissionOriginCue(
                permissionStatus = permissionCue,
                onFix = onPermissionFix
            )
        }
    }
}

/** 日原点旁的权限轻提示：一行可点，不抢主视线 */
@Composable
private fun PermissionOriginCue(
    permissionStatus: PermissionStatus,
    onFix: () -> Unit
) {
    val warningColor = Color(0xFFE8941A)
    val label = when {
        !permissionStatus.coreGranted -> "监控未生效 · 去处理"
        else -> "权限不完整 · 去处理"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onFix)
            .padding(vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(warningColor.copy(alpha = 0.92f))
        )
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = warningColor.copy(alpha = 0.88f),
            letterSpacing = (-0.1).sp
        )
    }
}

/** 后台监控曾被清理中断：引导去保活指南 */
@Composable
private fun MonitorInterruptStrip(
    onFix: () -> Unit,
    modifier: Modifier = Modifier
) {
    val warningColor = Color(0xFFE8941A)
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(warningColor.copy(alpha = if (cs.surface.red < 0.2f) 0.14f else 0.10f))
            .clickable(onClick = onFix)
            .padding(horizontal = 12.dp, vertical = 10.dp),
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
            text = "监控曾中断 · 可能被一键清理",
            fontSize = 12.sp,
            color = warningColor.copy(alpha = 0.92f),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "去保活",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = warningColor
        )
    }
}

/** 记录列表区起点：今日关键指标 */
@Composable
private fun TimelineResumeHeader(
    dayPulse: DayPulse,
    usageSeconds: Long,
    unmoored: Boolean,
    onSurface: Color,
    outline: Color,
    onOpenHistory: (() -> Unit)? = null,
    showMetrics: Boolean = true
) {
    val title = when {
        unmoored -> "系锚后会出现在这里"
        else -> "今日明细"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = onSurface.copy(alpha = 0.45f),
                letterSpacing = 0.2.sp
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(outline.copy(alpha = 0.28f))
            )
            if (onOpenHistory != null && !unmoored) {
                Text(
                    text = "历史",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = onSurface.copy(alpha = 0.36f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onOpenHistory)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
        if (showMetrics && !unmoored &&
            (dayPulse.enters > 0 || dayPulse.dismisses > 0 || dayPulse.positiveExits > 0 ||
                usageSeconds > 0L)
        ) {
            TodayMetricsRow(
                dayPulse = dayPulse,
                usageSeconds = usageSeconds,
                onSurface = onSurface
            )
        }
    }
}

@Composable
private fun TodayMetricsRow(
    dayPulse: DayPulse,
    usageSeconds: Long,
    onSurface: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (dayPulse.enters > 0) {
            TodayMetricCell(
                kind = LogMotionKind.Enter,
                value = dayPulse.enters.toString(),
                label = "进入使用",
                onSurface = onSurface
            )
        }
        if (dayPulse.dismisses > 0) {
            TodayMetricCell(
                kind = LogMotionKind.Leave,
                value = dayPulse.dismisses.toString(),
                label = "守住",
                onSurface = onSurface
            )
        }
        if (dayPulse.positiveExits > 0) {
            TodayMetricCell(
                kind = null,
                value = dayPulse.positiveExits.toString(),
                label = "去做了",
                onSurface = onSurface,
                accent = Color(0xFF64D2FF)
            )
        }
        if (usageSeconds > 0L) {
            TodayMetricCell(
                kind = null,
                value = formatDurationNarrative(usageSeconds),
                label = "使用时长",
                onSurface = onSurface
            )
        }
        if (dayPulse.mindfulEnters > 0) {
            TodayMetricCell(
                kind = null,
                value = dayPulse.mindfulEnters.toString(),
                label = "带着意图",
                onSurface = onSurface,
                accent = LogoGreen
            )
        }
    }
}

@Composable
private fun TodayMetricCell(
    kind: LogMotionKind?,
    value: String,
    label: String,
    onSurface: Color,
    accent: Color? = null
) {
    val valueColor = accent?.copy(alpha = 0.88f)
        ?: when (kind) {
            LogMotionKind.Enter -> LogoGreen.copy(alpha = 0.88f)
            LogMotionKind.Leave -> HeatmapNeutral.copy(alpha = 0.90f)
            else -> onSurface.copy(alpha = 0.78f)
        }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (kind != null) {
                MotionCountMark(
                    kind = kind,
                    count = value.toIntOrNull() ?: 0,
                    onSurface = onSurface,
                    compact = false
                )
            } else {
                Text(
                    text = value,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = valueColor,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        Text(
            text = label,
            fontSize = 10.sp,
            color = onSurface.copy(alpha = 0.32f),
            letterSpacing = 0.2.sp
        )
    }
}

// 保留旧名给可能的外部引用
@Composable
internal fun TimelineDayHeader(
    onSurface: Color,
    outline: Color = onSurface.copy(alpha = 0.25f),
    hasEvents: Boolean = true
) {
    TodayDateHerald(onSurface = onSurface)
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

/** 首页编辑入口聚焦维度 */
internal enum class RecordEditFocus {
    Compare,
    Note
}

/** 与弹窗三档一致的对照色 */
internal fun mindfulnessTierAccent(level: Int): Color =
    com.life.mindfulnessapp.ui.common.CompareTierColors.accent(level, isDark = true)

// ── 空态 ──────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyTimelineContent(
    onSurface: Color,
    @Suppress("UNUSED_PARAMETER") outline: Color,
    unmoored: Boolean = false
) {
    Text(
        text = if (unmoored) {
            "系上锚后，打开与守住会记在这里"
        } else {
            "今天还没有记录"
        },
        fontSize = 13.sp,
        color = onSurface.copy(alpha = 0.30f),
        lineHeight = 19.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 28.dp, start = 2.dp, end = 8.dp)
    )
}

// ── 对照 / 备注编辑弹窗 ──────────────────────────────────────────────────────

@Composable
internal fun NoteEditDialog(
    event: TimelineEvent.UsageEvent,
    cs: ColorScheme,
    focus: RecordEditFocus = RecordEditFocus.Note,
    isAutoPrompt: Boolean = false,
    onConfirm: (note: String?, mindfulnessLevel: Int?, driftSeconds: Long?) -> Unit,
    onDismiss: () -> Unit
) {
    var noteText by remember(event.recordId) { mutableStateOf(event.note ?: "") }
    var selectedLevel by remember(event.recordId) {
        mutableStateOf(
            event.mindfulnessLevel?.takeIf { UsageRecordEntity.MindfulnessLevel.isValid(it) }
        )
    }
    var driftSeconds by remember(event.recordId) {
        mutableStateOf<Long?>(null)
    }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val startStr = remember(event.startTime) { timeFormat.format(Date(event.startTime)) }
    val endStr = if (!event.isOngoing && event.endTime > 0) {
        timeFormat.format(Date(event.endTime))
    } else if (event.isOngoing) {
        "进行中"
    } else {
        timeFormat.format(Date(event.startTime))
    }
    val purposeText = event.purpose?.trim().orEmpty()
    val awarenessMode = remember(event.intentKind, purposeText) {
        com.life.mindfulnessapp.domain.model.SessionAwarenessMode.from(
            intentKind = event.intentKind,
            purpose = purposeText
        )
    }
    val requireCompare = focus == RecordEditFocus.Compare
    val canSave = if (requireCompare) {
        compareCanConfirm(
            enableCompare = true,
            selectedLevel = selectedLevel,
            driftSeconds = driftSeconds,
            durationSeconds = event.durationSeconds.coerceAtLeast(0L)
        )
    } else {
        true
    }
    val compareColors = CompareSectionColors.fromMaterial(
        onSurface = cs.onSurface,
        outlineVariant = cs.outlineVariant,
        isDark = cs.background.luminance() < 0.5f
    )
    val durationNarrative = formatDurationNarrative(event.durationSeconds.coerceAtLeast(0L))

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
                        if (awarenessMode == com.life.mindfulnessapp.domain.model.SessionAwarenessMode.URGE) {
                            "带着看见用了 $durationNarrative"
                        } else {
                            "带着命名用了 $durationNarrative"
                        }
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
            IntentCompareSection(
                selectedLevel = selectedLevel,
                onLevelSelected = { selectedLevel = it },
                noteText = noteText,
                onNoteChange = { noteText = it },
                colors = compareColors,
                useOutlinedNoteField = true,
                showNoteCharCount = true,
                awarenessMode = awarenessMode,
                modifier = Modifier.fillMaxWidth(),
                durationSeconds = event.durationSeconds.coerceAtLeast(0L),
                driftSeconds = driftSeconds,
                onDriftSecondsChange = { driftSeconds = it }
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (!canSave) return@TextButton
                    onConfirm(
                        noteText.trim().ifBlank { null },
                        selectedLevel?.takeIf { UsageRecordEntity.MindfulnessLevel.isValid(it) },
                        driftSeconds
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
private fun ActivePlanHomeStrip(
    plan: com.life.mindfulnessapp.domain.model.PlanBlock,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(LogoGreen.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "进行中 · ${plan.title}",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface
            )
            Text(
                text = "${plan.label()} · ${com.life.mindfulnessapp.domain.model.PlanBlockPolicy.remainingUnlockLabel(plan)}",
                fontSize = 12.sp,
                color = cs.onSurface.copy(alpha = 0.42f)
            )
        }
        Text(
            text = "守计划",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = LogoGreen
        )
    }
}

@Composable
private fun PermissionBreathStrip(
    permissionStatus: PermissionStatus,
    onFix: () -> Unit = {}
) {
    val warningColor = Color(0xFFE8941A)
    val cs = MaterialTheme.colorScheme
    val missing = permissionStatus.missingRecommendedLabels
    val label = when {
        !permissionStatus.coreGranted ->
            "监控未生效 · 待开${missing.joinToString("、")}"
        missing.isNotEmpty() ->
            "权限不完整 · 待开${missing.joinToString("、")}，影响后台保活"
        else -> "部分权限未开启"
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

// ── 监控坑位轨：圆角槽卡片 · 图标→时长→意图条数→名称 ────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppMonitorRow(
    monitoredApps: List<AppInfo>,
    summaries: List<AppUsageSummary>,
    pitInsights: Map<String, com.life.mindfulnessapp.domain.model.AppPitInsight> = emptyMap(),
    ongoingPackageName: String?,
    onAppClick: (String) -> Unit,
    onManageClick: () -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val summaryMap = summaries.associateBy { it.packageName }
    val accentGreen = LogoGreen
    val unmoored = monitoredApps.isEmpty()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (unmoored) "坑位" else "坑位 · ${monitoredApps.size}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = cs.onSurface.copy(alpha = 0.38f)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "排序",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = accentGreen.copy(alpha = 0.90f),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onManageClick)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            monitoredApps.forEach { app ->
                AppMonitorSlotCard(
                    app = app,
                    summary = summaryMap[app.packageName],
                    pitInsight = pitInsights[app.packageName],
                    isOngoing = app.packageName == ongoingPackageName,
                    cs = cs,
                    onClick = { onAppClick(app.packageName) },
                    onLongClick = onManageClick
                )
            }
            AddMonitorSlotCard(
                accentGreen = accentGreen,
                emphasize = unmoored,
                onClick = onAddClick
            )
        }
    }
}

/** 统一槽位尺寸：图标 → 时长 → 意图条数 → 名称 */
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

private fun formatSlotIntentCount(count: Int): String = "${count}条"

/**
 * 占用坑位：圆角槽卡片。
 * 主信号是今日时长与意图条数；能力配置放到详情页。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppMonitorSlotCard(
    app: AppInfo,
    summary: AppUsageSummary?,
    pitInsight: com.life.mindfulnessapp.domain.model.AppPitInsight? = null,
    isOngoing: Boolean,
    cs: ColorScheme,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val usedSeconds = summary?.todaySeconds ?: 0L
    val intentCount = pitInsight?.mindfulEnterCount ?: 0
    val timeLockOn = app.timeLimitEnabled && !app.isUninstalled
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
    val durationText = when {
        app.isUninstalled -> "已卸"
        else -> formatSlotUsage(usedSeconds)
    }
    val durationColor = when {
        app.isUninstalled -> Color(0xFFB05A2A).copy(alpha = 0.90f)
        capped -> Color(0xFFE74C3C)
        showAlert -> Color(0xFFE8941A)
        isOngoing -> LogoGreen
        usedSeconds > 0L -> cs.onSurface.copy(alpha = 0.78f)
        else -> cs.onSurface.copy(alpha = 0.38f)
    }
    val intentText = if (app.isUninstalled) "—" else formatSlotIntentCount(intentCount)
    val intentColor = when {
        app.isUninstalled -> cs.onSurface.copy(alpha = 0.28f)
        intentCount > 0 -> cs.onSurface.copy(alpha = 0.55f)
        else -> cs.onSurface.copy(alpha = 0.32f)
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
            .padding(horizontal = 6.dp, vertical = 9.dp)
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

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = durationText,
                fontSize = if (durationText.length > 4) 12.sp else 14.sp,
                fontWeight = FontWeight.Bold,
                color = durationColor,
                letterSpacing = (-0.2).sp,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
            Text(
                text = intentText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = intentColor,
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

/** 添加坑：细绿描边 + 虚线圆 +；未系锚时轻脉动，提示这是主入口 */
@Composable
private fun AddMonitorSlotCard(
    accentGreen: Color,
    onClick: () -> Unit,
    emphasize: Boolean = false
) {
    val slotShape = RoundedCornerShape(MonitorSlotCorner)
    val infiniteTransition = rememberInfiniteTransition(label = "add_slot")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "add_slot_border"
    )
    val borderAlpha = if (emphasize) pulse else 0.28f
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(MonitorSlotWidth)
            .height(MonitorSlotHeight)
            .clip(slotShape)
            .border(1.dp, accentGreen.copy(alpha = borderAlpha), slotShape)
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
                    color = accentGreen.copy(alpha = if (emphasize) 0.70f else 0.55f),
                    radius = (size.minDimension / 2f) - 2.dp.toPx(),
                    style = stroke
                )
            }
            Icon(
                Icons.Default.Add,
                contentDescription = if (emphasize) "系上第一只锚" else "添加监控应用",
                tint = accentGreen.copy(alpha = if (emphasize) 0.88f else 0.75f),
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.height(22.dp))
        Text(
            text = if (emphasize) "系锚" else "添加",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = accentGreen.copy(alpha = if (emphasize) 0.72f else 0.55f)
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
