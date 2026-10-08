package com.life.mindfulnessapp.ui.applist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.domain.model.AppRelationInsight
import com.life.mindfulnessapp.domain.model.AppTodayGlance
import com.life.mindfulnessapp.domain.model.CompanionAppMode
import com.life.mindfulnessapp.domain.model.PeriodLockPolicy
import com.life.mindfulnessapp.domain.model.PeriodWindow
import com.life.mindfulnessapp.domain.model.PeriodWindowsCodec
import com.life.mindfulnessapp.service.KeepAliveAccessibilityService
import com.life.mindfulnessapp.ui.theme.CapabilityKind
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.theme.MonitorCapability
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
/**
 * 已监控 App 详情：概览 / 记录 / 意图 / 守护 四 Tab 仪表盘。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLimitEditScreen(
    packageName: String,
    viewModel: AppListViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToHistory: () -> Unit = {},
    onNavigateToWeekRhythm: () -> Unit = {},
    onNavigateToCapability: (CapabilityKind) -> Unit = {},
    onNavigateToIntentDetail: (String) -> Unit = {},
    onNavigateToIntentManage: () -> Unit = {}
) {
    val apps by viewModel.apps.collectAsState()
    val monitoredApps by viewModel.monitoredApps.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val todayGlance by viewModel.todayGlance.collectAsState()
    val relationInsight by viewModel.relationInsight.collectAsState()
    val usageOverview by viewModel.usageOverview.collectAsState()
    val guardOverview by viewModel.guardOverview.collectAsState()
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    LaunchedEffect(packageName) {
        viewModel.loadApp(packageName)
    }

    val appInfo = remember(monitoredApps, apps, packageName) {
        monitoredApps.find { it.packageName == packageName }
            ?: apps.find { it.packageName == packageName && it.isMonitored }
    }

    var showStopConfirm by remember { mutableStateOf(false) }
    var showBreathForStop by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    var showExpectationEditor by remember { mutableStateOf(false) }
    var selectedRelationTab by remember(packageName) { mutableStateOf(AppRelationTab.Overview) }
    val rulesSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = appInfo?.appName ?: "",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = cs.onSurface
                        )
                    }
                },
                actions = {
                    if (appInfo != null) {
                        IconButton(onClick = { showRules = true }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "能力与规则",
                                tint = cs.onSurface.copy(alpha = 0.55f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cs.background,
                    scrolledContainerColor = cs.background
                )
            )
        },
        containerColor = cs.background
    ) { padding ->
        when {
            isLoading && appInfo == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = LogoGreen, strokeWidth = 2.dp)
                }
            }
            appInfo == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "该应用尚未在监控中",
                        fontSize = 14.sp,
                        color = cs.onSurface.copy(alpha = 0.45f),
                        textAlign = TextAlign.Center
                    )
                }
            }
            else -> {
                val info = appInfo
                val tabs = remember(info.requireIntentOnOpen) {
                    if (info.requireIntentOnOpen) {
                        listOf(
                            AppRelationTab.Overview,
                            AppRelationTab.Records,
                            AppRelationTab.Intent,
                            AppRelationTab.Guard
                        )
                    } else {
                        listOf(
                            AppRelationTab.Overview,
                            AppRelationTab.Records,
                            AppRelationTab.Guard
                        )
                    }
                }
                LaunchedEffect(tabs) {
                    if (selectedRelationTab !in tabs) {
                        selectedRelationTab = AppRelationTab.Overview
                    }
                }
                AppRelationDetailBody(
                    appInfo = info,
                    insight = relationInsight,
                    usageOverview = usageOverview,
                    guardOverview = guardOverview,
                    visibleTabs = tabs,
                    selectedTab = selectedRelationTab,
                    onTabSelected = { selectedRelationTab = it },
                    contentPadding = padding,
                    onIntentItemClick = onNavigateToIntentDetail,
                    onIntentManageClick = onNavigateToIntentManage,
                    onHistoryClick = onNavigateToHistory,
                    onWeekRhythmClick = onNavigateToWeekRhythm,
                    onShiftRelationDay = viewModel::shiftRelationDay,
                    onSetRelationDay = { dayStart ->
                        viewModel.setRelationDay(dayStart)
                        selectedRelationTab = AppRelationTab.Records
                    },
                    packageName = packageName
                )
            }
        }
    }

    if (showRules && appInfo != null) {
        ModalBottomSheet(
            onDismissRequest = { showRules = false },
            sheetState = rulesSheetState,
            containerColor = cs.surface,
            dragHandle = null
        ) {
            RulesAndMoreSheet(
                appInfo = appInfo,
                todayGlance = todayGlance,
                onCapabilityClick = { kind ->
                    scope.launch {
                        rulesSheetState.hide()
                        showRules = false
                        onNavigateToCapability(kind)
                    }
                },
                onLockClonesChange = { enabled ->
                    viewModel.setLockClonesEnabled(appInfo.packageName, enabled)
                },
                companionAppMode = viewModel.getCompanionAppMode(appInfo.packageName),
                onCompanionAppModeChange = { mode ->
                    viewModel.setCompanionAppMode(appInfo.packageName, mode)
                },
                onEditExpectation = {
                    scope.launch {
                        rulesSheetState.hide()
                        showRules = false
                        showExpectationEditor = true
                    }
                },
                onHistory = {
                    scope.launch {
                        rulesSheetState.hide()
                        showRules = false
                        onNavigateToHistory()
                    }
                },
                onStop = {
                    scope.launch {
                        rulesSheetState.hide()
                        showRules = false
                        showStopConfirm = true
                    }
                },
                onCancel = {
                    scope.launch {
                        rulesSheetState.hide()
                        showRules = false
                    }
                }
            )
        }
    }

    if (showStopConfirm && appInfo != null) {
        StopMonitoringConfirmDialog(
            appName = appInfo.appName,
            onConfirm = {
                showStopConfirm = false
                scope.launch {
                    if (viewModel.isUnderActivePeriodHardLock(appInfo.packageName)) {
                        showBreathForStop = true
                    } else {
                        viewModel.stopMonitoring(appInfo.packageName)
                        onNavigateBack()
                    }
                }
            },
            onDismiss = { showStopConfirm = false }
        )
    }

    if (showBreathForStop && appInfo != null) {
        PeriodLockDisableGateDialog(
            commitment = "",
            windowLabel = null,
            title = "锁定中停止监控？",
            confirmLabel = "确认停止",
            breathReason = com.life.mindfulnessapp.data.analytics.HaEvents.BreathReason.STOP_MONITOR_LOCKED,
            appName = appInfo.appName,
            packageName = appInfo.packageName,
            onConfirm = {
                showBreathForStop = false
                scope.launch {
                    viewModel.stopMonitoring(appInfo.packageName)
                    onNavigateBack()
                }
            },
            onDismiss = { showBreathForStop = false }
        )
    }

    if (showExpectationEditor && appInfo != null) {
        ExpectationEditDialog(
            appName = appInfo.appName,
            initial = appInfo.usageCovenant,
            onDismiss = { showExpectationEditor = false },
            onSave = { text ->
                showExpectationEditor = false
                viewModel.updateUsageCovenant(appInfo.packageName, text)
            }
        )
    }
}

// ── App 详情关系页（四 Tab 仪表盘 · 整页一体滚动）─────────────────────────

@Composable
private fun AppRelationDetailBody(
    appInfo: AppInfo,
    insight: AppRelationInsight?,
    usageOverview: com.life.mindfulnessapp.domain.model.AppUsageOverview?,
    guardOverview: com.life.mindfulnessapp.domain.model.GuardOverview,
    visibleTabs: List<AppRelationTab>,
    selectedTab: AppRelationTab,
    onTabSelected: (AppRelationTab) -> Unit,
    contentPadding: PaddingValues,
    onIntentItemClick: (String) -> Unit = {},
    onIntentManageClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    onWeekRhythmClick: () -> Unit = {},
    onShiftRelationDay: (Int) -> Unit = {},
    onSetRelationDay: (Long) -> Unit = {},
    packageName: String
) {
    val listState = rememberLazyListState()
    var selectedDayStartMs by remember(packageName) { mutableStateOf(startOfToday()) }
    var monthStartMs by remember(packageName) { mutableStateOf(startOfMonth(startOfToday())) }

    val intentPoolVm: IntentPoolViewModel = hiltViewModel()
    LaunchedEffect(packageName) {
        intentPoolVm.load(packageName, trackView = selectedTab == AppRelationTab.Intent)
    }
    LaunchedEffect(selectedTab) {
        if (selectedTab == AppRelationTab.Intent) {
            intentPoolVm.load(packageName, trackView = true)
        }
        listState.scrollToItem(0)
    }
    val intentSnapshot by intentPoolVm.snapshot.collectAsState()
    val intentLoading by intentPoolVm.isLoading.collectAsState()
    val intentFilterId by intentPoolVm.categoryFilterId.collectAsState()

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding())
            .navigationBarsPadding(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 28.dp
        )
    ) {
        item(key = "tabs") {
            AppRelationTabSwitcher(
                selected = selectedTab,
                onSelected = onTabSelected,
                visibleTabs = visibleTabs,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        when (selectedTab) {
            AppRelationTab.Overview -> {
                overviewDashboardItems(
                    appInfo = appInfo,
                    overview = usageOverview,
                    selectedDayStartMs = selectedDayStartMs,
                    monthStartMs = monthStartMs,
                    onSelectDay = { day ->
                        selectedDayStartMs = day
                        monthStartMs = startOfMonth(day)
                    },
                    onShiftMonth = { delta ->
                        val cal = java.util.Calendar.getInstance().apply {
                            timeInMillis = monthStartMs
                            add(java.util.Calendar.MONTH, delta)
                        }
                        monthStartMs = startOfMonth(cal.timeInMillis)
                    },
                    onOpenDayRecords = onSetRelationDay,
                    onWeekRhythmClick = onWeekRhythmClick
                )
            }
            AppRelationTab.Records -> {
                recordsTabItems(
                    insight = insight,
                    onShiftDay = onShiftRelationDay,
                    onHistoryClick = onHistoryClick
                )
            }
            AppRelationTab.Intent -> {
                intentPoolItems(
                    snapshot = intentSnapshot,
                    loading = intentLoading,
                    categoryFilterId = intentFilterId,
                    onFilterSelected = intentPoolVm::setCategoryFilter,
                    onItemClick = onIntentItemClick,
                    onManageClick = onIntentManageClick
                )
            }
            AppRelationTab.Guard -> {
                guardOverviewItems(overview = guardOverview)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.overviewDashboardItems(
    appInfo: AppInfo,
    overview: com.life.mindfulnessapp.domain.model.AppUsageOverview?,
    selectedDayStartMs: Long,
    monthStartMs: Long,
    onSelectDay: (Long) -> Unit,
    onShiftMonth: (Int) -> Unit,
    onOpenDayRecords: (Long) -> Unit,
    onWeekRhythmClick: () -> Unit
) {
    if (overview == null) {
        item(key = "overview_loading") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = LogoGreen, strokeWidth = 2.dp)
            }
        }
        return
    }
    item(key = "overview_locks") {
        AppDetailLockPills(appInfo = appInfo)
        Spacer(modifier = Modifier.height(12.dp))
    }
    item(key = "overview_stats") {
        AppDetailStatGrid(overview = overview)
        Spacer(modifier = Modifier.height(12.dp))
    }
    if (appInfo.requireIntentOnOpen && overview.reviewedCount > 0) {
        item(key = "overview_mindfulness") {
            AppDetailMindfulnessDistribution(overview = overview)
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
    item(key = "overview_calendar") {
        AppDetailUsageCalendar(
            daySecondsByDayStart = overview.daySecondsByDayStart,
            selectedDayStartMs = selectedDayStartMs,
            monthStartMs = monthStartMs,
            onSelectDay = { day ->
                val hasUsage = (overview.daySecondsByDayStart[day] ?: 0L) > 0L
                // 再次点选已选中的有用量日 → 进入记录 Tab 看当日
                if (day == selectedDayStartMs && hasUsage) {
                    onOpenDayRecords(day)
                } else {
                    onSelectDay(day)
                }
            },
            onShiftMonth = onShiftMonth
        )
    }
    item(key = "overview_week_rhythm") {
        Spacer(modifier = Modifier.height(4.dp))
        WeekRhythmNavRow(onClick = onWeekRhythmClick)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.recordsTabItems(
    insight: AppRelationInsight?,
    onShiftDay: (Int) -> Unit,
    onHistoryClick: () -> Unit
) {
    if (insight == null) {
        item(key = "records_loading") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = LogoGreen, strokeWidth = 2.dp)
            }
        }
        return
    }
    item(key = "records_day") {
        AppDayUsageRelationSection(
            insight = insight,
            onShiftDay = onShiftDay,
            onEventClick = { }
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
    item(key = "records_full_history") {
        val cs = MaterialTheme.colorScheme
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onHistoryClick)
                .padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "查看完整时间轴",
                fontSize = 14.sp,
                color = cs.onSurface.copy(alpha = 0.55f),
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = cs.onSurface.copy(alpha = 0.28f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.intentPoolItems(
    snapshot: com.life.mindfulnessapp.domain.model.IntentPoolSnapshot?,
    loading: Boolean,
    categoryFilterId: String?,
    onFilterSelected: (String?) -> Unit,
    onItemClick: (String) -> Unit,
    onManageClick: () -> Unit
) {
    when {
        loading && snapshot == null -> {
            item(key = "intent_loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = LogoGreen, strokeWidth = 2.dp)
                }
            }
        }
        snapshot == null || (snapshot.totalEntryCount == 0 && snapshot.hiddenCount == 0) -> {
            item(key = "intent_empty") {
                IntentPoolEmptyState()
            }
        }
        else -> {
            item(key = "intent_meta") {
                Text(
                    text = "${snapshot.totalEntryCount} 种意图 · ${formatIntentPoolDuration(snapshot.totalMindfulSeconds)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (snapshot.categoryBreakdown.isNotEmpty()) {
                    IntentPoolCategoryChips(
                        breakdown = snapshot.categoryBreakdown,
                        selectedFilterId = categoryFilterId,
                        onFilterSelected = onFilterSelected
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
                Text(
                    text = "整理分类 · 合并意图",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onManageClick)
                        .padding(vertical = 8.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            items(snapshot.items, key = { "intent_${it.entry.id}" }) { item ->
                IntentPoolItemRow(
                    item = item,
                    timeScope = snapshot.timeScope,
                    onClick = { onItemClick(item.entry.id) },
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            if (snapshot.hiddenCount > 0) {
                item(key = "intent_hidden") {
                    Text(
                        text = "另有 ${snapshot.hiddenCount} 条已隐藏",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.36f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.guardOverviewItems(
    overview: com.life.mindfulnessapp.domain.model.GuardOverview
) {
    item(key = "guard_counts") {
        AppDetailGuardSummary(overview = overview)
        Spacer(modifier = Modifier.height(16.dp))
    }
    if (overview.recent.isEmpty()) {
        item(key = "guard_empty") {
            Text(
                text = "还没有守护记录",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.40f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 28.dp),
                textAlign = TextAlign.Center
            )
        }
    } else {
        items(overview.recent.take(20), key = { "guard_${it.recordId}" }) { event ->
            AppDetailGuardEventCard(
                event = event,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}

@Composable
private fun ExpectationEditDialog(
    appName: String,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "对「$appName」的期望",
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                Text(
                    text = "写下你希望用它来做什么——意义和价值的约定。",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    lineHeight = 19.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(120) },
                    placeholder = {
                        Text("例如：只用来查攻略，不刷推荐流", fontSize = 14.sp)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onSave(draft.trim()) }) {
                Text("保存", color = LogoGreen)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun AppIdentityHeader(
    appInfo: AppInfo,
    compact: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    val iconSize = if (compact) 40.dp else 64.dp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 0.dp, bottom = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .size(iconSize)
                .clip(CircleShape)
                .background(cs.surfaceVariant.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(
                drawable = appInfo.icon,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(if (compact) 6.dp else 10.dp))
        Text(
            text = appInfo.appName,
            fontSize = if (compact) 16.sp else 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurface.copy(alpha = 0.92f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ── 非意图门：配置向详情 ──────────────────────────────────────────────────

@Composable
private fun ConfigOrientedBody(
    appInfo: AppInfo,
    todayGlance: AppTodayGlance?,
    dayInsight: com.life.mindfulnessapp.domain.model.AppDayInsight?,
    contentPadding: PaddingValues,
    onCapabilityClick: (CapabilityKind) -> Unit,
    onHistoryClick: () -> Unit
) {
    val listState = rememberLazyListState()
    val periodWindows = remember(appInfo.periodWindowsJson) {
        PeriodWindowsCodec.decode(appInfo.periodWindowsJson)
    }
    val glance = todayGlance ?: AppTodayGlance(
        openCount = 0,
        dismissCount = 0,
        mindfulEnterCount = 0,
        totalSeconds = 0L,
        requireIntentOnOpen = false
    )
    val enabledCaps = remember(appInfo) {
        CapabilityCopy.All.filter { copy ->
            when (copy.kind) {
                CapabilityKind.IntentGate -> appInfo.requireIntentOnOpen
                CapabilityKind.TimeLock -> appInfo.timeLimitEnabled
                CapabilityKind.PeriodLock -> appInfo.periodLockEnabled
            }
        }
    }
    val disabledCaps = remember(appInfo) {
        CapabilityCopy.All.filter { copy ->
            when (copy.kind) {
                CapabilityKind.IntentGate -> !appInfo.requireIntentOnOpen
                CapabilityKind.TimeLock -> !appInfo.timeLimitEnabled
                CapabilityKind.PeriodLock -> !appInfo.periodLockEnabled
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 40.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { AppIdentityHeader(appInfo = appInfo) }
        item {
            if (dayInsight != null) {
                com.life.mindfulnessapp.ui.common.DayInsightCard(
                    headline = dayInsight.headline,
                    mindful = dayInsight.mindfulSeconds,
                    dismiss = dayInsight.dismissCount,
                    idle = dayInsight.idleSeconds,
                    total = dayInsight.totalSeconds,
                    showYesterdayDelta = dayInsight.isToday && dayInsight.hasSignal,
                    compact = true
                )
            } else {
                Text(
                    text = "今日已用 ${formatCapabilityDuration(glance.totalSeconds)}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                )
            }
        }
        item {
            HistoryNavRow(onClick = onHistoryClick)
        }
        items(enabledCaps.size) { index ->
            val copy = enabledCaps[index]
            UnifiedCapabilityCard(
                kind = copy.kind,
                enabled = true,
                description = copy.description,
                glance = glance,
                dailyLimitMinutes = appInfo.dailyLimitMinutes,
                sessionLimitOn = false,
                periodWindows = periodWindows,
                onClick = { onCapabilityClick(copy.kind) }
            )
        }
        if (disabledCaps.isNotEmpty()) {
            item {
                Text(
                    text = "还可叠加",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                )
            }
            items(disabledCaps.size) { index ->
                val copy = disabledCaps[index]
                UnifiedCapabilityCard(
                    kind = copy.kind,
                    enabled = false,
                    description = copy.description,
                    glance = glance,
                    dailyLimitMinutes = appInfo.dailyLimitMinutes,
                    sessionLimitOn = false,
                    periodWindows = periodWindows,
                    onClick = { onCapabilityClick(copy.kind) }
                )
            }
        }
    }
}

@Composable
private fun HistoryNavRow(onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cs.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "使用记录",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = cs.onSurface.copy(alpha = 0.78f),
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = cs.onSurface.copy(alpha = 0.28f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun WeekRhythmNavRow(onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "一周节奏",
                fontSize = 14.sp,
                color = cs.onSurface.copy(alpha = 0.55f)
            )
            Text(
                text = "7 天 · 凌晨到晚上",
                fontSize = 11.sp,
                color = cs.onSurface.copy(alpha = 0.32f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = cs.onSurface.copy(alpha = 0.28f),
            modifier = Modifier.size(18.dp)
        )
    }
}

// ── 齿轮：能力与更多 ──────────────────────────────────────────────────────

@Composable
private fun RulesAndMoreSheet(
    appInfo: AppInfo,
    todayGlance: AppTodayGlance?,
    onCapabilityClick: (CapabilityKind) -> Unit,
    onLockClonesChange: (Boolean) -> Unit,
    companionAppMode: String,
    onCompanionAppModeChange: (String) -> Unit,
    onEditExpectation: () -> Unit = {},
    onHistory: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val periodWindows = remember(appInfo.periodWindowsJson) {
        PeriodWindowsCodec.decode(appInfo.periodWindowsJson)
    }
    val glance = todayGlance ?: AppTodayGlance(
        openCount = 0,
        dismissCount = 0,
        mindfulEnterCount = 0,
        totalSeconds = 0L,
        requireIntentOnOpen = appInfo.requireIntentOnOpen
    )
    val cloneCount = appInfo.suspectedClonePackages.size
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var a11yOn by remember { mutableStateOf(KeepAliveAccessibilityService.isEnabled(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                a11yOn = KeepAliveAccessibilityService.isEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val cloneSubtitle = when {
        appInfo.hasSystemDualInstance && appInfo.lockClonesEnabled && !a11yOn ->
            "已检测到系统分身。请开启「无障碍保活」，分身打开时才能稳定拦截"
        appInfo.hasSystemDualInstance && appInfo.lockClonesEnabled ->
            "已检测到系统分身（同包双开），打开时一并拦截"
        appInfo.hasSystemDualInstance ->
            "已检测到系统分身；关闭后仅锁定主应用"
        cloneCount > 0 && appInfo.lockClonesEnabled ->
            "已发现 $cloneCount 个疑似分身，打开时一并拦截"
        cloneCount > 0 ->
            "已发现 $cloneCount 个疑似分身，关闭后需单独添加"
        appInfo.lockClonesEnabled ->
            "检测到分身时自动按本配置拦截"
        else ->
            "仅锁定本应用；分身需单独添加"
    }
    var companionMode by remember(appInfo.packageName, companionAppMode) {
        mutableStateOf(CompanionAppMode.fromStorage(companionAppMode))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 8.dp)
    ) {
        Text(
            text = "能力与规则",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = cs.onSurface.copy(alpha = 0.40f),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )

        CapabilityCopy.All.forEach { copy ->
            val enabled = when (copy.kind) {
                CapabilityKind.IntentGate -> appInfo.requireIntentOnOpen
                CapabilityKind.TimeLock -> appInfo.timeLimitEnabled
                CapabilityKind.PeriodLock -> appInfo.periodLockEnabled
            }
            val status = when (copy.kind) {
                CapabilityKind.IntentGate -> if (enabled) {
                    "开启 · 今日守住 ${glance.dismissCount} · 打开 ${glance.enterCount}"
                } else "未开启"
                CapabilityKind.TimeLock -> if (enabled) {
                    "开启 · ${formatCapabilityLimitMinutes(appInfo.dailyLimitMinutes)}/日"
                } else "未开启"
                CapabilityKind.PeriodLock -> if (enabled) {
                    val n = periodWindows.count { it.enabled }
                    "开启 · $n 个时段"
                } else "未开启"
            }
            SheetCapabilityRow(
                title = MonitorCapability.label(copy.kind),
                status = status,
                enabled = enabled,
                onClick = { onCapabilityClick(copy.kind) }
            )
        }

        HorizontalDivider(
            color = cs.outline.copy(alpha = 0.12f),
            modifier = Modifier.padding(vertical = 4.dp)
        )
        SheetRow(
            title = "期望描述",
            subtitle = appInfo.usageCovenant.trim().ifEmpty { "我和这个 App 的意义约定" },
            onClick = onEditExpectation
        )
        HorizontalDivider(color = cs.outline.copy(alpha = 0.12f))
        SheetToggleRow(
            title = "分身一并锁定",
            subtitle = cloneSubtitle,
            checked = appInfo.lockClonesEnabled,
            onCheckedChange = onLockClonesChange
        )
        HorizontalDivider(color = cs.outline.copy(alpha = 0.12f))
        SheetRow(
            title = "使用中陪伴",
            subtitle = "${companionMode.title} · ${companionMode.hint}",
            onClick = {
                val next = CompanionAppMode.next(companionMode)
                companionMode = next
                onCompanionAppModeChange(next.storageKey)
            }
        )
        HorizontalDivider(color = cs.outline.copy(alpha = 0.12f))
        SheetRow(
            title = "使用记录",
            subtitle = "打开、守住与每次时长",
            onClick = onHistory
        )
        HorizontalDivider(color = cs.outline.copy(alpha = 0.12f))
        SheetRow(
            title = "停止监控",
            subtitle = "历史记录会保留",
            destructive = true,
            onClick = onStop
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "取消",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = cs.onSurface.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onCancel)
                .padding(vertical = 14.dp)
        )
    }
}

@Composable
private fun SheetToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface.copy(alpha = 0.92f)
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = cs.onSurface.copy(alpha = 0.42f),
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = LogoGreen,
                checkedThumbColor = Color.White
            )
        )
    }
}

@Composable
private fun SheetCapabilityRow(
    title: String,
    status: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface.copy(alpha = 0.90f)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = status,
                fontSize = 12.sp,
                color = if (enabled) LogoGreen.copy(alpha = 0.85f)
                else cs.onSurface.copy(alpha = 0.40f)
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = cs.onSurface.copy(alpha = 0.28f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SheetRow(
    title: String,
    subtitle: String,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (destructive) Color(0xFFE74C3C).copy(alpha = 0.90f)
            else cs.onSurface.copy(alpha = 0.92f)
        )
        Text(
            text = subtitle,
            fontSize = 12.sp,
            color = cs.onSurface.copy(alpha = 0.42f),
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

// ── 能力卡（非意图门详情） ────────────────────────────────────────────────

@Composable
private fun UnifiedCapabilityCard(
    kind: CapabilityKind,
    enabled: Boolean,
    description: String,
    glance: AppTodayGlance,
    dailyLimitMinutes: Int,
    sessionLimitOn: Boolean,
    periodWindows: List<PeriodWindow>,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cs.surface)
            .border(
                width = 1.dp,
                color = if (enabled) LogoGreen.copy(alpha = 0.22f)
                else cs.outline.copy(alpha = 0.12f),
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = MonitorCapability.label(kind),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface.copy(alpha = 0.92f),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (enabled) "开启中" else "未开启",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) LogoGreen else cs.onSurface.copy(alpha = 0.36f)
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (enabled) LogoGreen.copy(alpha = 0.75f)
                else cs.onSurface.copy(alpha = 0.28f),
                modifier = Modifier.size(20.dp)
            )
        }

        if (!enabled) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                fontSize = 13.sp,
                color = cs.onSurface.copy(alpha = 0.40f),
                lineHeight = 18.sp
            )
        } else {
            Spacer(modifier = Modifier.height(12.dp))
            when (kind) {
                CapabilityKind.IntentGate -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        CompactMetric(value = glance.enterCount.toString(), label = "打开")
                        CompactMetric(value = glance.dismissCount.toString(), label = "守住")
                        CompactMetric(value = glance.mindfulEnterCount.toString(), label = "目的")
                    }
                    if (sessionLimitOn) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "进门先定时长",
                            fontSize = 12.sp,
                            color = LogoGreen.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                CapabilityKind.TimeLock -> {
                    val usedMin = (glance.totalSeconds / 60f).coerceAtLeast(0f)
                    val progress = if (dailyLimitMinutes > 0) {
                        (usedMin / dailyLimitMinutes).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    Text(
                        text = "今日已用 ${formatCapabilityDuration(glance.totalSeconds)}",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface.copy(alpha = 0.92f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "每日限制 ${formatCapabilityLimitMinutes(dailyLimitMinutes)}",
                        fontSize = 13.sp,
                        color = cs.onSurface.copy(alpha = 0.42f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = LogoGreen,
                        trackColor = cs.onSurface.copy(alpha = 0.08f),
                        strokeCap = StrokeCap.Round
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${(progress * 100).roundToInt()}%",
                        fontSize = 12.sp,
                        color = cs.onSurface.copy(alpha = 0.38f)
                    )
                }
                CapabilityKind.PeriodLock -> {
                    val active = PeriodLockPolicy.activeWindow(periodWindows.filter { it.enabled })
                    if (periodWindows.isEmpty()) {
                        Text(
                            text = "尚未设置时段",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = cs.onSurface.copy(alpha = 0.72f)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            periodWindows.forEach { window ->
                                val isActive = active?.id == window.id
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = window.label().replace(" – ", "-"),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (window.enabled) cs.onSurface.copy(alpha = 0.92f)
                                            else cs.onSurface.copy(alpha = 0.40f),
                                            modifier = Modifier.weight(1f)
                                        )
                                        when {
                                            !window.enabled -> Text(
                                                text = "已关闭",
                                                fontSize = 12.sp,
                                                color = cs.onSurface.copy(alpha = 0.32f)
                                            )
                                            isActive -> Text(
                                                text = "生效中",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = LogoGreen
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = buildString {
                                            append(window.daysLabel())
                                            window.rangeHint()?.let { append(" · "); append(it) }
                                        },
                                        fontSize = 12.sp,
                                        color = cs.onSurface.copy(alpha = 0.40f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactMetric(value: String, label: String) {
    val cs = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = cs.onSurface.copy(alpha = 0.92f)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = cs.onSurface.copy(alpha = 0.42f)
        )
    }
}
