package com.life.mindfulnessapp.ui.applist

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.domain.model.BatchPickAppRow
import com.life.mindfulnessapp.domain.model.BatchPickSortMode
import com.life.mindfulnessapp.ui.applist.hasCapability
import com.life.mindfulnessapp.ui.theme.CapabilityForm
import com.life.mindfulnessapp.ui.theme.CapabilityKind
import com.life.mindfulnessapp.ui.theme.CapabilityMark
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.theme.MindfulGreen40
import com.life.mindfulnessapp.ui.theme.MonitorCapability
import com.life.mindfulnessapp.ui.vip.AccessGateDialog
import kotlinx.coroutines.launch

/**
 * 能力系锚：全量 App 4 列网格。
 * - 时长锁：多选后一键批量开启
 * - 其它能力：点一项弹出配置 Sheet，单独确认即写入
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapabilityBatchPickScreen(
    capability: CapabilityKind,
    viewModel: AppListViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onBindSuccess: () -> Unit = {},
    onNavigateToVip: () -> Unit = {}
) {
    val rows by viewModel.batchPickApps.collectAsState()
    val sortMode by viewModel.batchPickSortMode.collectAsState()
    val usageLoading by viewModel.batchPickUsageLoading.collectAsState()
    val permissionStatus by viewModel.permissionStatus.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val vipLevel by viewModel.vipLevel.collectAsState()
    val monitoredCount by viewModel.monitoredCount.collectAsState()
    val monitoredApps by viewModel.monitoredApps.collectAsState()
    val isAtFreeLimit by viewModel.isAtFreeLimit.collectAsState()
    val accessGate by viewModel.accessGate.collectAsState()
    val focusManager = LocalFocusManager.current
    val cs = MaterialTheme.colorScheme
    val label = MonitorCapability.label(capability)
    val accent = MonitorCapability.accent(capability)
    val boundCount = remember(monitoredApps, capability) {
        monitoredApps.count { it.hasCapability(capability) }
    }

    val isMultiSelect = capability == CapabilityKind.TimeLock
    val scope = rememberCoroutineScope()

    var editingApp by remember { mutableStateOf<AppInfo?>(null) }
    var selectedPackages by remember { mutableStateOf(setOf<String>()) }
    var isBatchSaving by remember { mutableStateOf(false) }

    LaunchedEffect(capability) {
        viewModel.setSearchQuery("")
        viewModel.setBatchPickSortMode(BatchPickSortMode.Duration)
        viewModel.refreshPermissions()
        viewModel.loadAppsAwait()
    }

    LaunchedEffect(permissionStatus.hasUsageStats) {
        if (permissionStatus.hasUsageStats) {
            viewModel.loadBatchPickUsageStats()
        }
    }

    val unlimitedSlots = AppPreferences.FREE_PERIOD_ENABLED || vipLevel > 0

    var unsuitableMessage by remember { mutableStateOf<String?>(null) }

    fun toggleSelection(app: AppInfo) {
        if (app.hasCapability(capability)) return
        com.life.mindfulnessapp.domain.model.MonitorSuitability
            .unsuitableReminder(app.packageName)
            ?.let {
                unsuitableMessage = it
                return
            }
        if (!app.isMonitored && !unlimitedSlots && isAtFreeLimit &&
            app.packageName !in selectedPackages
        ) {
            viewModel.requestVipUpgrade()
            return
        }
        val pkg = app.packageName
        selectedPackages = if (pkg in selectedPackages) {
            selectedPackages - pkg
        } else {
            selectedPackages + pkg
        }
    }

    fun openConfig(app: AppInfo) {
        if (isMultiSelect) {
            toggleSelection(app)
            return
        }
        com.life.mindfulnessapp.domain.model.MonitorSuitability
            .unsuitableReminder(app.packageName)
            ?.let {
                unsuitableMessage = it
                return
            }
        if (app.hasCapability(capability)) return
        if (!app.isMonitored && !unlimitedSlots && isAtFreeLimit) {
            viewModel.requestVipUpgrade()
            return
        }
        editingApp = app
    }

    unsuitableMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { unsuitableMessage = null },
            title = { Text("不适合加入") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { unsuitableMessage = null }) {
                    Text("知道了")
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CapabilityMark(
                            kind = capability,
                            form = CapabilityForm.Compact,
                            active = true,
                            tint = accent,
                            size = 22.dp
                        )
                        Column {
                            Text(
                                "添加 · $label",
                                fontWeight = FontWeight.SemiBold,
                                color = cs.onSurface,
                                fontSize = 17.sp
                            )
                            Text(
                                text = buildString {
                                    if (searchQuery.isNotBlank()) {
                                        append("匹配 ")
                                        append(rows.size)
                                        append(" 个")
                                    } else {
                                        append("已绑定 ")
                                        append(boundCount)
                                        append(" 个")
                                    }
                                    if (!unlimitedSlots) {
                                        append(" · 坑位 ")
                                        append(monitoredCount)
                                        append(" / ")
                                        append(AppPreferences.FREE_MONITOR_LIMIT)
                                    }
                                },
                                fontSize = 11.sp,
                                color = if (isAtFreeLimit && searchQuery.isBlank()) {
                                    Color(0xFFE8941A)
                                } else {
                                    cs.onSurface.copy(alpha = 0.40f)
                                }
                            )
                        }
                    }
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        containerColor = cs.background
    ) { padding ->
        BatchPickBody(
            isLoading = isLoading,
            rows = rows,
            targetCapability = capability,
            searchQuery = searchQuery,
            sortMode = sortMode,
            usageLoading = usageLoading,
            hasUsagePermission = permissionStatus.hasUsageStats,
            onSortModeChange = viewModel::setBatchPickSortMode,
            onSearchChange = viewModel::setSearchQuery,
            onClearSearch = { viewModel.setSearchQuery("") },
            onAppClick = { openConfig(it) },
            onSearchDone = { focusManager.clearFocus() },
            multiSelectMode = isMultiSelect,
            selectedPackages = selectedPackages,
            bottomInset = if (isMultiSelect && selectedPackages.isNotEmpty()) 88.dp else 24.dp,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
        )
    }

    if (isMultiSelect && selectedPackages.isNotEmpty()) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Button(
                onClick = {
                    if (isBatchSaving) return@Button
                    scope.launch {
                        isBatchSaving = true
                        val result = viewModel.batchBindCapability(
                            capability = capability,
                            packageNames = selectedPackages.toList()
                        )
                        isBatchSaving = false
                        if (result.successCount > 0) {
                            selectedPackages = emptySet()
                            onBindSuccess()
                        }
                    }
                },
                enabled = !isBatchSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = Color.White
                )
            ) {
                if (isBatchSaving) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        if (capability == CapabilityKind.TimeLock) {
                            "添加 ${selectedPackages.size} 个 App（中档）"
                        } else {
                            "为 ${selectedPackages.size} 个 App 开启$label"
                        },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }

    editingApp?.let { app ->
        key(app.packageName) {
            CapabilityBindConfigSheet(
                app = app,
                capability = capability,
                viewModel = viewModel,
                onDismiss = { editingApp = null },
                onSaved = { editingApp = null },
                onNavigateToVip = onNavigateToVip
            )
        }
    }

    if (accessGate.visible || accessGate.unlockToast != null) {
        AccessGateDialog(
            state = accessGate,
            cardColor = cs.surface,
            textPrimary = cs.onSurface,
            textSecondary = cs.onSurfaceVariant,
            borderColor = cs.outline,
            accentGreen = LogoGreen,
            onDismiss = { viewModel.dismissVipUpgradeDialog() },
            onCodeChange = viewModel::onAccessCodeChange,
            onRedeem = viewModel::redeemAccessCode,
            onOpenClaim = viewModel::openAccessClaimStep,
            onOpenRedeem = viewModel::openAccessRedeemStep,
            onClaimChannelChange = viewModel::onAccessClaimChannelChange,
            onClaimContactChange = viewModel::onAccessClaimContactChange,
            onSubmitClaim = viewModel::submitAccessClaim,
            onRedeemIssued = viewModel::redeemIssuedAccessCode,
            onViewMembership = {
                viewModel.dismissVipUpgradeDialog()
                onNavigateToVip()
            },
            onBackToGate = viewModel::backToAccessGate,
            onConsumeUnlockToast = viewModel::consumeAccessUnlockToast
        )
    }
    }
}

@Composable
private fun BatchPickBody(
    isLoading: Boolean,
    rows: List<BatchPickAppRow>,
    targetCapability: CapabilityKind,
    searchQuery: String,
    sortMode: BatchPickSortMode,
    usageLoading: Boolean,
    hasUsagePermission: Boolean,
    onSortModeChange: (BatchPickSortMode) -> Unit,
    onSearchChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onAppClick: (AppInfo) -> Unit,
    onSearchDone: () -> Unit,
    multiSelectMode: Boolean = false,
    selectedPackages: Set<String> = emptySet(),
    bottomInset: androidx.compose.ui.unit.Dp = 24.dp,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showBackToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 2 ||
                listState.firstVisibleItemScrollOffset > 400
        }
    }

    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomInset)
        ) {
            item(key = "search") {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 4.dp, bottom = 8.dp),
                    placeholder = {
                        Text(
                            "搜索应用名 / 拼音 / 简拼",
                            color = cs.onBackground.copy(alpha = 0.28f),
                            fontSize = 14.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = cs.onBackground.copy(alpha = 0.28f)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = onClearSearch) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "清除",
                                    tint = cs.onBackground.copy(alpha = 0.5f)
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LogoGreen,
                        unfocusedBorderColor = cs.outline.copy(alpha = 0.4f),
                        focusedContainerColor = cs.surface,
                        unfocusedContainerColor = cs.surface,
                        focusedTextColor = cs.onSurface,
                        unfocusedTextColor = cs.onSurface
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearchDone() })
                )
            }

            item(key = "sort") {
                AppUsageSortRow(
                    sortMode = sortMode,
                    usageLoading = usageLoading,
                    hasUsagePermission = hasUsagePermission,
                    caption = null,
                    onSortModeChange = onSortModeChange
                )
            }

            when {
                isLoading && rows.isEmpty() -> {
                    item(key = "loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MindfulGreen40, strokeWidth = 2.dp)
                        }
                    }
                }
                rows.isEmpty() -> {
                    item(key = "empty") {
                        Text(
                            text = if (searchQuery.isNotBlank()) "没有匹配的应用" else "没有可添加的应用",
                            fontSize = 14.sp,
                            color = cs.onSurface.copy(alpha = 0.38f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 40.dp, vertical = 36.dp)
                        )
                    }
                }
                else -> {
                    item(key = "grid_all") {
                        val coveredByPrimaryLock = remember(rows) {
                            rows.asSequence()
                                .map { it.app }
                                .filter { it.isMonitored && it.lockClonesEnabled && !it.isSystemDualRow }
                                .flatMap { app ->
                                    app.suspectedClonePackages +
                                        if (app.hasSystemDualInstance) {
                                            listOf(app.packageName)
                                        } else {
                                            emptyList()
                                        }
                                }
                                .toSet()
                        }
                        AppUsageGrid(
                            rows = rows,
                            usageLoading = usageLoading,
                            hasUsagePermission = hasUsagePermission,
                            sortMode = sortMode,
                            dimPredicate = { app ->
                                when {
                                    app.hasCapability(targetCapability) -> true
                                    // 系统分身行：主应用已监控且开启同锁 → 已覆盖
                                    app.isSystemDualRow &&
                                        app.isMonitored &&
                                        app.lockClonesEnabled -> true
                                    !app.isMonitored &&
                                        !app.isSystemDualRow &&
                                        app.packageName in coveredByPrimaryLock -> true
                                    else -> false
                                }
                            },
                            selectedPackages = selectedPackages,
                            multiSelectMode = multiSelectMode,
                            selectionAccent = MonitorCapability.accent(targetCapability),
                            onAppClick = { app ->
                                if (app.hasCapability(targetCapability)) return@AppUsageGrid
                                if (app.isSystemDualRow && app.isMonitored && app.lockClonesEnabled) {
                                    return@AppUsageGrid
                                }
                                if (!app.isMonitored &&
                                    !app.isSystemDualRow &&
                                    app.packageName in coveredByPrimaryLock
                                ) {
                                    return@AppUsageGrid
                                }
                                onAppClick(app)
                            }
                        )
                    }
                }
            }
        }

        if (showBackToTop) {
            FloatingActionButton(
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 20.dp, bottom = 24.dp),
                containerColor = cs.surface,
                contentColor = LogoGreen
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "回到顶部")
            }
        }
    }
}
