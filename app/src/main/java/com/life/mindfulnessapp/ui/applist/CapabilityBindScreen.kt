package com.life.mindfulnessapp.ui.applist

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.analytics.HaEvents
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.ui.common.PermissionGateDialog
import com.life.mindfulnessapp.ui.common.PermissionGateMode
import com.life.mindfulnessapp.ui.common.PermissionSettingsIntents
import com.life.mindfulnessapp.ui.theme.CapabilityForm
import com.life.mindfulnessapp.ui.theme.CapabilityKind
import com.life.mindfulnessapp.ui.theme.CapabilityMark
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.theme.MindfulGreen40
import com.life.mindfulnessapp.ui.theme.MonitorCapability
import com.life.mindfulnessapp.ui.vip.AccessGateDialog
import kotlinx.coroutines.launch

private enum class BindPhase {
    PickApp,
    PickCapability,
    Intro
}

/**
 * 能力 × App 绑定。
 *
 * - [firstBind]：只选能力，再交给 [onPickCapability] 进批量网格
 * - 其余入口请直接使用 [CapabilityBatchPickScreen]
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapabilityBindScreen(
    viewModel: AppListViewModel = hiltViewModel(),
    firstBind: Boolean = false,
    presetCapability: CapabilityKind? = null,
    onNavigateBack: () -> Unit,
    onBind: (packageName: String, primary: CapabilityKind) -> Unit,
    /** 已监控 App 叠加 [presetCapability]：进入该能力配置，不占新坑位 */
    onEnableOnExisting: (packageName: String, capability: CapabilityKind) -> Unit = { _, _ -> },
    /** 首绑：选完能力后进批量网格 */
    onPickCapability: (CapabilityKind) -> Unit = {},
    onNavigateToVip: () -> Unit = {}
) {
    val apps by viewModel.apps.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isAtFreeLimit by viewModel.isAtFreeLimit.collectAsState()
    val vipLevel by viewModel.vipLevel.collectAsState()
    val monitoredCount by viewModel.monitoredCount.collectAsState()
    val permStatus by viewModel.permissionStatus.collectAsState()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val isDark = isSystemInDarkTheme()

    // 首绑：直接选能力，不再走单 App 步进
    if (firstBind) {
        FirstBindCapabilityPick(
            onNavigateBack = onNavigateBack,
            onPick = onPickCapability
        )
        return
    }

    var introDecided by rememberSaveable { mutableStateOf(false) }
    var showIntro by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshPermissions()
        val count = viewModel.loadAppsAwait()
        showIntro = count == 0
        introDecided = true
    }

    val phases = remember(showIntro, presetCapability) {
        buildList {
            add(BindPhase.PickApp)
            if (presetCapability == null) add(BindPhase.PickCapability)
            if (showIntro) add(BindPhase.Intro)
        }
    }

    var phaseIndex by rememberSaveable(showIntro, presetCapability?.name) { mutableStateOf(0) }
    var selectedKindName by rememberSaveable(presetCapability?.name) {
        mutableStateOf(presetCapability?.name)
    }
    var selectedPackage by rememberSaveable { mutableStateOf<String?>(null) }

    val primary = remember(selectedKindName) {
        selectedKindName?.let { com.life.mindfulnessapp.ui.theme.parseCapabilityKind(it) }
    }
    val selectedApp = remember(apps, selectedPackage) {
        apps.find { it.packageName == selectedPackage }
    }
    val phase = phases.getOrElse(phaseIndex) { phases.first() }

    var showPermissionGate by remember { mutableStateOf(false) }
    var pendingBind by remember { mutableStateOf<Pair<String, CapabilityKind>?>(null) }

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshPermissions(HaEvents.Source.BIND) }
    val usageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshPermissions(HaEvents.Source.BIND) }

    LaunchedEffect(permStatus.coreGranted, pendingBind, showPermissionGate) {
        val pending = pendingBind ?: return@LaunchedEffect
        if (permStatus.coreGranted && showPermissionGate) {
            showPermissionGate = false
            pendingBind = null
            onBind(pending.first, pending.second)
        }
    }

    val candidates = remember(apps, presetCapability) {
        when (presetCapability) {
            null -> apps.filter { !it.isMonitored }
            else -> apps.filter { app ->
                // 未系锚：可新增；已系锚：仅尚未开启该能力的可叠加
                !app.isMonitored || !app.hasCapability(presetCapability)
            }
        }
    }
    val stackableCandidates = remember(candidates, presetCapability) {
        if (presetCapability == null) emptyList()
        else candidates.filter { it.isMonitored }
    }
    val freshCandidates = remember(candidates, presetCapability) {
        if (presetCapability == null) candidates
        else candidates.filter { !it.isMonitored }
    }

    fun attemptBind(pkg: String, kind: CapabilityKind) {
        if (isAtFreeLimit) {
            viewModel.requestVipUpgrade()
            return
        }
        if (!permStatus.coreGranted) {
            pendingBind = pkg to kind
            showPermissionGate = true
            viewModel.onPermissionPromptShown(HaEvents.Source.BIND)
            return
        }
        onBind(pkg, kind)
    }

    fun goBack() {
        if (phaseIndex > 0) {
            val leaving = phases[phaseIndex]
            phaseIndex -= 1
            when (leaving) {
                BindPhase.Intro -> {
                    // 预选能力时保留 kind，仅清空「了解」进度
                    if (presetCapability == null) selectedKindName = null
                }
                BindPhase.PickCapability -> {
                    selectedKindName = null
                    // 保留已选 App
                }
                BindPhase.PickApp -> Unit
            }
            focusManager.clearFocus()
        } else {
            onNavigateBack()
        }
    }

    BackHandler { goBack() }

    fun onAppPicked(app: AppInfo) {
        selectedPackage = app.packageName
        if (presetCapability != null) {
            selectedKindName = presetCapability.name
            // 已系锚：直接叠加该能力，不占新坑、不走首绑介绍
            if (app.isMonitored) {
                onEnableOnExisting(app.packageName, presetCapability)
                return
            }
        }
        val next = phaseIndex + 1
        if (next < phases.size) {
            phaseIndex = next
        } else {
            val kind = primary ?: presetCapability ?: return
            attemptBind(app.packageName, kind)
        }
    }

    fun onCapabilityPicked(kind: CapabilityKind) {
        val pkg = selectedPackage ?: return
        selectedKindName = kind.name
        if (showIntro) {
            val next = phaseIndex + 1
            if (next < phases.size) phaseIndex = next
        } else {
            attemptBind(pkg, kind)
        }
    }

    fun onIntroContinue() {
        val pkg = selectedPackage ?: return
        val kind = primary ?: return
        attemptBind(pkg, kind)
    }

    val title = when (phase) {
        BindPhase.PickApp -> when {
            presetCapability != null -> "添加 · ${MonitorCapability.label(presetCapability)}"
            else -> "添加应用"
        }
        BindPhase.PickCapability -> "选择能力"
        BindPhase.Intro -> primary?.let { MonitorCapability.label(it) } ?: "了解形态"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            title,
                            fontWeight = FontWeight.SemiBold,
                            color = cs.onSurface,
                            fontSize = 17.sp
                        )
                        if (vipLevel <= 0) {
                            Text(
                                text = "$monitoredCount / ${AppPreferences.FREE_MONITOR_LIMIT}",
                                fontSize = 11.sp,
                                color = if (isAtFreeLimit) Color(0xFFE8941A)
                                else cs.onSurface.copy(alpha = 0.40f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { goBack() }) {
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
        bottomBar = {
            if (phase == BindPhase.Intro && primary != null) {
                val label = MonitorCapability.label(primary)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(cs.background)
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Button(
                        onClick = { onIntroContinue() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LogoGreen,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            "用${label}继续",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        },
        containerColor = cs.background
    ) { padding ->
        if (!introDecided) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MindfulGreen40, strokeWidth = 2.dp)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            BindFlowStepper(
                labels = phases.map { p ->
                    when (p) {
                        BindPhase.PickApp -> "选择应用"
                        BindPhase.PickCapability -> "选择能力"
                        BindPhase.Intro -> "了解形态"
                    }
                },
                currentIndex = phaseIndex,
                onStepClick = { target ->
                    if (target < phaseIndex) {
                        phaseIndex = target
                        when (phases[target]) {
                            BindPhase.PickApp -> {
                                selectedPackage = null
                                if (presetCapability == null) selectedKindName = null
                                else selectedKindName = presetCapability.name
                            }
                            BindPhase.PickCapability -> selectedKindName = null
                            BindPhase.Intro -> Unit
                        }
                        focusManager.clearFocus()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            AnimatedContent(
                targetState = phase,
                transitionSpec = {
                    val forward = phases.indexOf(targetState) >= phases.indexOf(initialState)
                    val enter = slideInHorizontally(
                        animationSpec = tween(280),
                        initialOffsetX = { if (forward) it / 5 else -it / 5 }
                    ) + fadeIn(tween(220))
                    val exit = slideOutHorizontally(
                        animationSpec = tween(240),
                        targetOffsetX = { if (forward) -it / 6 else it / 6 }
                    ) + fadeOut(tween(180))
                    enter togetherWith exit
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                label = "bind_flow_phase"
            ) { current ->
                when (current) {
                    BindPhase.PickApp -> AppStepContent(
                        isLoading = isLoading,
                        stackable = stackableCandidates,
                        fresh = freshCandidates,
                        presetLabel = presetCapability?.let { MonitorCapability.label(it) },
                        searchQuery = searchQuery,
                        onSearchChange = { viewModel.setSearchQuery(it) },
                        onClearSearch = { viewModel.setSearchQuery("") },
                        onAppClick = { onAppPicked(it) },
                        onSearchDone = { focusManager.clearFocus() }
                    )
                    BindPhase.PickCapability -> CapabilityStepContent(
                        selected = primary,
                        headline = "先用哪种方式管「${selectedApp?.appName ?: "该应用"}」？",
                        subline = if (showIntro) {
                            "点一项，先看它打开时的样子。之后还能叠加。"
                        } else {
                            "点一项开始配置。之后还能叠加其他能力。"
                        },
                        onPick = { onCapabilityPicked(it) }
                    )
                    BindPhase.Intro -> {
                        val kind = primary
                        if (kind != null) {
                            CapabilityIntroBody(
                                kind = kind,
                                isDarkTheme = isDark,
                                subject = selectedApp?.let {
                                    ExperiencePreviewSubject(
                                        appName = it.appName,
                                        packageName = it.packageName,
                                        icon = it.icon
                                    )
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }

    val accessGate by viewModel.accessGate.collectAsState()
    if (accessGate.visible) {
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
            onClaimChannelChange = viewModel::onAccessClaimChannelChange,
            onClaimContactChange = viewModel::onAccessClaimContactChange,
            onSubmitClaim = viewModel::submitAccessClaim,
            onRedeemIssued = viewModel::redeemIssuedAccessCode,
            onViewMembership = {
                viewModel.dismissVipUpgradeDialog()
                onNavigateToVip()
            },
            onOpenRedeem = viewModel::openAccessRedeemStep,
            onBackToGate = viewModel::backToAccessGate,
            onConsumeUnlockToast = viewModel::consumeAccessUnlockToast
        )
    }

    if (showPermissionGate) {
        PermissionGateDialog(
            permissionStatus = permStatus,
            mode = PermissionGateMode.Required,
            onDismiss = {
                showPermissionGate = false
                pendingBind = null
                viewModel.trackPermissionSkip(HaEvents.Source.BIND)
            },
            onGrantOverlay = {
                overlayLauncher.launch(PermissionSettingsIntents.overlay(context))
            },
            onGrantUsage = {
                usageLauncher.launch(PermissionSettingsIntents.usageAccess())
            }
        )
    }
}

/** 引导后首绑：只选一种能力，随后进入批量网格。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FirstBindCapabilityPick(
    onNavigateBack: () -> Unit,
    onPick: (CapabilityKind) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "系上第一枚锚",
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface,
                        fontSize = 17.sp
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
                    TextButton(onClick = onNavigateBack) {
                        Text(
                            "稍后",
                            color = cs.onSurface.copy(alpha = 0.55f),
                            fontSize = 14.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        containerColor = cs.background
    ) { padding ->
        CapabilityStepContent(
            selected = null,
            headline = "先用哪种方式开始？",
            subline = "选一项后，批量挑选要系上的应用。之后还能叠加其他能力。",
            onPick = onPick,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}

@Composable
private fun BindFlowStepper(
    labels: List<String>,
    currentIndex: Int,
    onStepClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        labels.forEachIndexed { index, label ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .width(if (labels.size > 2) 20.dp else 32.dp)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(
                            if (currentIndex >= index) LogoGreen.copy(alpha = 0.55f)
                            else cs.onSurface.copy(alpha = 0.12f)
                        )
                )
            }
            BindFlowStepTab(
                index = index + 1,
                label = label,
                state = when {
                    index < currentIndex -> BindStepState.Done
                    index == currentIndex -> BindStepState.Current
                    else -> BindStepState.Upcoming
                },
                compact = labels.size > 2,
                onClick = { onStepClick(index) }
            )
        }
    }
}

private enum class BindStepState { Current, Done, Upcoming }

@Composable
private fun BindFlowStepTab(
    index: Int,
    label: String,
    state: BindStepState,
    compact: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val clickable = state != BindStepState.Upcoming
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp, horizontal = if (compact) 2.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(
                    when (state) {
                        BindStepState.Current -> LogoGreen
                        BindStepState.Done -> LogoGreen.copy(alpha = 0.18f)
                        BindStepState.Upcoming -> cs.onSurface.copy(alpha = 0.08f)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (state == BindStepState.Done) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = LogoGreen
                )
            } else {
                Text(
                    text = index.toString(),
                    modifier = Modifier.offset(y = 0.5.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 11.sp,
                    textAlign = TextAlign.Center,
                    color = when (state) {
                        BindStepState.Current -> Color.White
                        BindStepState.Upcoming -> cs.onSurface.copy(alpha = 0.35f)
                        BindStepState.Done -> LogoGreen
                    },
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both
                        )
                    )
                )
            }
        }
        Text(
            text = label,
            fontSize = if (compact) 12.sp else 14.sp,
            fontWeight = if (state == BindStepState.Current) FontWeight.SemiBold
            else FontWeight.Medium,
            color = when (state) {
                BindStepState.Current -> cs.onSurface.copy(alpha = 0.92f)
                BindStepState.Done -> LogoGreen.copy(alpha = 0.90f)
                BindStepState.Upcoming -> cs.onSurface.copy(alpha = 0.35f)
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CapabilityStepContent(
    selected: CapabilityKind?,
    headline: String,
    subline: String,
    onPick: (CapabilityKind) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "capability_headline") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = headline,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
                    lineHeight = 22.sp
                )
                Text(
                    text = subline,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    lineHeight = 18.sp
                )
            }
        }
        items(CapabilityCopy.All, key = { it.kind.name }) { spec ->
            CapabilityChoiceCard(
                spec = spec,
                selected = selected == spec.kind,
                onClick = { onPick(spec.kind) }
            )
        }
    }
}

/** 介绍正文（无顶栏/底栏）：嵌入绑定同页「了解形态」步。 */
@Composable
fun CapabilityIntroBody(
    kind: CapabilityKind,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier,
    subject: ExperiencePreviewSubject? = null
) {
    val cs = MaterialTheme.colorScheme
    val copy = CapabilityCopy.of(kind)
    val scroll = rememberScrollState()

    Column(
        modifier = modifier
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp)
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(LogoGreen.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                CapabilityMark(
                    kind = kind,
                    form = CapabilityForm.Emphasis,
                    size = 24.dp
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = copy.slogan,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface.copy(alpha = 0.92f),
                    lineHeight = 24.sp
                )
                Text(
                    text = copy.description,
                    fontSize = 13.sp,
                    color = cs.onSurface.copy(alpha = 0.48f),
                    lineHeight = 18.sp
                )
            }
        }

        CapabilityExperiencePreview(
            kind = kind,
            isDarkTheme = isDarkTheme,
            tall = true,
            subject = subject
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "它会怎样帮你",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface.copy(alpha = 0.88f)
            )
            Text(
                text = copy.introBody,
                fontSize = 14.sp,
                color = cs.onSurface.copy(alpha = 0.62f),
                lineHeight = 22.sp
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "适合这些时刻",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface.copy(alpha = 0.88f)
            )
            copy.scenes.forEach { scene ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 7.dp)
                            .size(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(LogoGreen.copy(alpha = 0.75f))
                    )
                    Text(
                        text = scene,
                        fontSize = 14.sp,
                        color = cs.onSurface.copy(alpha = 0.62f),
                        lineHeight = 21.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, cs.outline.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
                .background(cs.surface)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text = "这里先选一种管法即可；之后还可在详情里叠加其他能力。",
                fontSize = 12.sp,
                color = cs.onSurface.copy(alpha = 0.45f),
                lineHeight = 18.sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun AppStepContent(
    isLoading: Boolean,
    stackable: List<AppInfo>,
    fresh: List<AppInfo>,
    presetLabel: String?,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onAppClick: (AppInfo) -> Unit,
    onSearchDone: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showBackToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 4 ||
                listState.firstVisibleItemScrollOffset > 400
        }
    }
    val isEmpty = stackable.isEmpty() && fresh.isEmpty()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            item {
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
            when {
                isLoading -> {
                    item {
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
                isEmpty -> {
                    item {
                        Text(
                            text = when {
                                searchQuery.isNotBlank() -> "没有匹配的应用"
                                presetLabel != null -> "没有可叠加「$presetLabel」的应用"
                                else -> "没有可添加的应用"
                            },
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
                    if (stackable.isNotEmpty()) {
                        item(key = "hdr_stack") {
                            BindSectionLabel(
                                text = if (presetLabel != null) "已系锚 · 叠加$presetLabel"
                                else "已系锚",
                                cs = cs
                            )
                        }
                        items(stackable, key = { "stack_${it.listKey}" }) { app ->
                            BindAppListItem(
                                appInfo = app,
                                subtitle = "已在其他能力中",
                                onClick = { onAppClick(app) }
                            )
                        }
                    }
                    if (fresh.isNotEmpty()) {
                        if (stackable.isNotEmpty()) {
                            item(key = "hdr_fresh") {
                                BindSectionLabel(
                                    text = "尚未系锚",
                                    cs = cs,
                                    topGap = true
                                )
                            }
                        }
                        items(fresh, key = { "fresh_${it.listKey}" }) { app ->
                            BindAppListItem(
                                appInfo = app,
                                onClick = { onAppClick(app) }
                            )
                        }
                    }
                }
            }
        }

        if (showBackToTop) {
            FloatingActionButton(
                onClick = {
                    scope.launch { listState.animateScrollToItem(0) }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 24.dp),
                containerColor = cs.surface,
                contentColor = LogoGreen
            ) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = "回到顶部"
                )
            }
        }
    }
}

@Composable
private fun BindSectionLabel(
    text: String,
    cs: ColorScheme,
    topGap: Boolean = false
) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = cs.onSurface.copy(alpha = 0.40f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = if (topGap) 16.dp else 8.dp,
                bottom = 6.dp
            )
    )
}

@Composable
private fun CapabilityChoiceCard(
    spec: CapabilityCopy,
    selected: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (selected) LogoGreen.copy(alpha = 0.10f) else cs.surface
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) LogoGreen.copy(alpha = 0.55f)
                else cs.outline.copy(alpha = 0.22f),
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (selected) LogoGreen.copy(alpha = 0.16f)
                    else cs.onSurface.copy(alpha = 0.05f)
                ),
            contentAlignment = Alignment.Center
        ) {
            CapabilityMark(
                kind = spec.kind,
                form = CapabilityForm.Standard,
                tint = if (selected) {
                    MonitorCapability.accent(spec.kind)
                } else {
                    cs.onSurface.copy(alpha = 0.62f)
                },
                size = 22.dp
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = spec.label,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface.copy(alpha = 0.92f)
            )
            Text(
                text = spec.description,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = cs.onSurface.copy(alpha = 0.78f),
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = "已选",
                tint = LogoGreen,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun BindAppListItem(
    appInfo: AppInfo,
    onClick: () -> Unit,
    subtitle: String? = null
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cs.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AppIcon(drawable = appInfo.icon, modifier = Modifier.size(44.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = appInfo.appName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = cs.onSurface,
                maxLines = 1
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = LogoGreen.copy(alpha = 0.75f),
                    maxLines = 1
                )
            }
        }
    }
}
