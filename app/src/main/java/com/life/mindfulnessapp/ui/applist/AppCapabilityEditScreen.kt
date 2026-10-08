package com.life.mindfulnessapp.ui.applist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.data.analytics.HaEvents
import com.life.mindfulnessapp.domain.model.ComparePolicy
import com.life.mindfulnessapp.domain.model.PeriodWindow
import com.life.mindfulnessapp.domain.model.PeriodWindowsCodec
import com.life.mindfulnessapp.ui.theme.CapabilityKind
import com.life.mindfulnessapp.ui.theme.LogoGreen
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

/**
 * 单能力配置页（从详情进入）。
 * 只承载 [capability] 一项；可开关；保存时写回整份监控配置（其它能力保持原值）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppCapabilityEditScreen(
    packageName: String,
    capability: CapabilityKind,
    viewModel: AppListViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onStoppedMonitoring: () -> Unit = onNavigateBack,
    onNavigateToIntentPoolManage: () -> Unit = {},
    onNavigateToQuickIntentTags: () -> Unit = {},
    onNavigateToDeepLinkGlance: () -> Unit = {}
) {
    val apps by viewModel.apps.collectAsState()
    val monitoredApps by viewModel.monitoredApps.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val copy = remember(capability) { CapabilityCopy.of(capability) }

    LaunchedEffect(packageName) {
        viewModel.loadApp(packageName)
    }

    val appInfo = remember(monitoredApps, apps, packageName) {
        monitoredApps.find { it.packageName == packageName }
            ?: apps.find { it.packageName == packageName && it.isMonitored }
    }

    val prevDailyLimit = appInfo?.dailyLimitMinutes?.coerceAtLeast(DAILY_LIMIT_MIN) ?: 30

    var requireIntent by remember { mutableStateOf(true) }
    var timeLimitOn by remember { mutableStateOf(true) }
    var sessionLimitOn by remember { mutableStateOf(true) }
    var dailyLimit by remember { mutableIntStateOf(30) }
    var periodLockOn by remember { mutableStateOf(false) }
    var periodWindows by remember {
        mutableStateOf(listOf(PeriodWindow.defaultSleep()))
    }
    var compareEnabled by remember { mutableStateOf(ComparePolicy.DEFAULT_ENABLED) }
    var compareMinMinutes by remember { mutableIntStateOf(ComparePolicy.DEFAULT_MIN_MINUTES) }
    var isSaving by remember { mutableStateOf(false) }
    var seeded by remember(packageName, capability) { mutableStateOf(false) }
    var showStopConfirm by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var showBreathForLoosen by remember { mutableStateOf(false) }
    var showBreathForStop by remember { mutableStateOf(false) }

    var baselineRequireIntent by remember { mutableStateOf(true) }
    var baselineTimeLimitOn by remember { mutableStateOf(true) }
    var baselineSessionLimitOn by remember { mutableStateOf(true) }
    var baselineDailyLimit by remember { mutableIntStateOf(30) }
    var baselinePeriodLockOn by remember { mutableStateOf(false) }
    var baselinePeriodWindows by remember { mutableStateOf(listOf(PeriodWindow.defaultSleep())) }
    var baselineCompareEnabled by remember { mutableStateOf(ComparePolicy.DEFAULT_ENABLED) }
    var baselineCompareMinMinutes by remember { mutableIntStateOf(ComparePolicy.DEFAULT_MIN_MINUTES) }

    LaunchedEffect(appInfo, capability) {
        val info = appInfo ?: return@LaunchedEffect
        if (seeded) return@LaunchedEffect
        requireIntent = info.requireIntentOnOpen
        timeLimitOn = info.timeLimitEnabled
        sessionLimitOn = info.requireIntentOnOpen && info.sessionLimitEnabled
        dailyLimit = info.dailyLimitMinutes.coerceAtLeast(DAILY_LIMIT_MIN)
        periodLockOn = info.periodLockEnabled
        val decoded = PeriodWindowsCodec.withLegacyCommitment(
            PeriodWindowsCodec.decode(info.periodWindowsJson),
            info.periodLockCommitment
        )
        periodWindows = decoded.ifEmpty { listOf(PeriodWindow.defaultSleep()) }
        compareEnabled = info.compareEnabled
        compareMinMinutes = ComparePolicy.sanitizeMinMinutes(info.compareMinMinutes)

        baselineRequireIntent = requireIntent
        baselineTimeLimitOn = timeLimitOn
        baselineSessionLimitOn = sessionLimitOn
        baselineDailyLimit = dailyLimit
        baselinePeriodLockOn = periodLockOn
        baselinePeriodWindows = periodWindows
        baselineCompareEnabled = compareEnabled
        baselineCompareMinMinutes = compareMinMinutes

        // 从能力区「叠加」进入时：该能力若尚未开，预开以便直接配置保存
        when (capability) {
            CapabilityKind.IntentGate -> if (!requireIntent) {
                requireIntent = true
                sessionLimitOn = true
            }
            CapabilityKind.TimeLock -> if (!timeLimitOn) {
                timeLimitOn = true
            }
            CapabilityKind.PeriodLock -> if (!periodLockOn) {
                periodLockOn = true
                if (periodWindows.isEmpty()) {
                    periodWindows = listOf(PeriodWindow.defaultSleep())
                }
            }
        }
        seeded = true
    }

    val isDirty = seeded && (
        requireIntent != baselineRequireIntent ||
            timeLimitOn != baselineTimeLimitOn ||
            sessionLimitOn != baselineSessionLimitOn ||
            dailyLimit != baselineDailyLimit ||
            periodLockOn != baselinePeriodLockOn ||
            periodWindows != baselinePeriodWindows ||
            compareEnabled != baselineCompareEnabled ||
            compareMinMinutes != baselineCompareMinMinutes
        )

    fun requestLeave() {
        if (isDirty) showDiscard = true else onNavigateBack()
    }

    BackHandler { requestLeave() }

    fun anyOtherOn(): Boolean = when (capability) {
        CapabilityKind.IntentGate -> timeLimitOn || periodLockOn
        CapabilityKind.TimeLock -> requireIntent || periodLockOn
        CapabilityKind.PeriodLock -> requireIntent || timeLimitOn
    }

    fun requestIntentChange(on: Boolean) {
        if (!on && requireIntent && !anyOtherOn()) {
            showStopConfirm = true
            return
        }
        requireIntent = on
        if (!on) sessionLimitOn = false
    }

    fun requestTimeLimitChange(on: Boolean) {
        if (!on && timeLimitOn && !anyOtherOn()) {
            showStopConfirm = true
            return
        }
        timeLimitOn = on
    }

    fun requestPeriodLockChange(on: Boolean) {
        if (!on && periodLockOn && !anyOtherOn()) {
            showStopConfirm = true
            return
        }
        periodLockOn = on
        if (on && periodWindows.isEmpty()) {
            periodWindows = listOf(PeriodWindow.defaultSleep())
        }
    }

    val periodReady = !periodLockOn || periodWindows.isNotEmpty()
    val canSave =
        (requireIntent || timeLimitOn || periodLockOn) &&
            periodReady &&
            !isSaving &&
            appInfo != null

    val dailyHint = when {
        appInfo == null -> null
        dailyLimit < prevDailyLimit -> "收紧了"
        dailyLimit > prevDailyLimit -> "放宽了"
        else -> null
    }

    fun persistAndLeave() {
        val info = appInfo ?: return
        if (!canSave) return
        scope.launch {
            isSaving = true
            val ok = viewModel.saveMonitorConfig(
                appInfo = info,
                dailyLimitMinutes = dailyLimit,
                timeAwarenessEnabled = false,
                timeLimitEnabled = timeLimitOn,
                requireIntentOnOpen = requireIntent,
                sessionLimitEnabled = requireIntent && sessionLimitOn,
                intentQualityCheckEnabled = false,
                intentBlockKeywordsJson = "",
                defaultSessionLimitMinutes = info.defaultSessionLimitMinutes,
                intentReviewEnabled = false,
                overTimeMessage = info.overTimeMessage,
                periodLockEnabled = periodLockOn,
                periodWindowsJson = if (periodLockOn) {
                    PeriodWindowsCodec.encode(periodWindows)
                } else {
                    info.periodWindowsJson
                },
                compareEnabled = compareEnabled,
                compareMinMinutes = compareMinMinutes
            )
            isSaving = false
            if (ok) onNavigateBack()
        }
    }

    fun saveAndLeave() {
        val info = appInfo ?: return
        if (!canSave || !isDirty) return
        // 触顶后放宽日限额 / 关闭时长锁 → 需呼吸代价。
        // 首次开启不算放宽：库里可能已有默认限额与今日用量，但锁尚未真正生效。
        val looseningDaily = capability == CapabilityKind.TimeLock &&
            baselineTimeLimitOn &&
            ((timeLimitOn && dailyLimit > baselineDailyLimit) || !timeLimitOn)
        if (!looseningDaily) {
            persistAndLeave()
            return
        }
        scope.launch {
            val hit = viewModel.isDailyLimitHitToday(info.packageName, baselineDailyLimit)
            if (hit) {
                showBreathForLoosen = true
            } else {
                persistAndLeave()
            }
        }
    }

    fun confirmStopMonitoring() {
        val info = appInfo ?: return
        showStopConfirm = false
        scope.launch {
            if (viewModel.isUnderActivePeriodHardLock(info.packageName)) {
                showBreathForStop = true
            } else {
                isSaving = true
                viewModel.stopMonitoring(info.packageName)
                isSaving = false
                onStoppedMonitoring()
            }
        }
    }

    fun executeStopMonitoring() {
        val info = appInfo ?: return
        scope.launch {
            isSaving = true
            viewModel.stopMonitoring(info.packageName)
            isSaving = false
            onStoppedMonitoring()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (appInfo != null) {
                        ConfigAppBarTitle(appInfo = appInfo, cs = cs)
                    } else {
                        Text(
                            text = copy.label,
                            fontWeight = FontWeight.SemiBold,
                            color = cs.onSurface,
                            fontSize = 17.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { requestLeave() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = cs.onSurface
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { saveAndLeave() },
                        enabled = canSave && isDirty
                    ) {
                        Text(
                            text = "保存",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (canSave && isDirty) LogoGreen
                            else cs.onSurface.copy(alpha = 0.28f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
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
                when (capability) {
                    CapabilityKind.IntentGate -> {
                        IntentGateConfigContent(
                            appName = appInfo.appName,
                            requireIntent = requireIntent,
                            onRequireIntentChange = { requestIntentChange(it) },
                            sessionLimitOn = sessionLimitOn,
                            onSessionLimitChange = { sessionLimitOn = it },
                            compareEnabled = compareEnabled,
                            onCompareEnabledChange = { compareEnabled = it },
                            compareMinMinutes = compareMinMinutes,
                            onCompareMinMinutesChange = {
                                compareMinMinutes = ComparePolicy.sanitizeMinMinutes(it)
                            },
                            allowMasterToggle = true,
                            onManageIntentPool = onNavigateToIntentPoolManage,
                            onManageQuickTags = onNavigateToQuickIntentTags,
                            onOpenDeepLinkGlance = onNavigateToDeepLinkGlance,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .navigationBarsPadding()
                        )
                    }
                    CapabilityKind.TimeLock -> {
                        TimeLockConfigContent(
                            appName = appInfo.appName,
                            timeLimitOn = timeLimitOn,
                            onTimeLimitChange = { requestTimeLimitChange(it) },
                            dailyLimit = dailyLimit,
                            onDailyLimitChange = { dailyLimit = it },
                            dailyHint = dailyHint,
                            allowMasterToggle = true,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .navigationBarsPadding()
                        )
                    }
                    CapabilityKind.PeriodLock -> {
                        PeriodLockConfigContent(
                            appName = appInfo.appName,
                            periodLockOn = periodLockOn,
                            onPeriodLockChange = { requestPeriodLockChange(it) },
                            periodWindows = periodWindows,
                            onPeriodWindowsChange = { periodWindows = it },
                            allowMasterToggle = true,
                            lockIsLive = baselinePeriodLockOn,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .navigationBarsPadding()
                        )
                    }
                }
            }
        }
    }

    if (showDiscard) {
        DiscardChangesDialog(
            onDiscard = {
                showDiscard = false
                onNavigateBack()
            },
            onStay = { showDiscard = false }
        )
    }

    if (showBreathForLoosen) {
        com.life.mindfulnessapp.ui.common.BreathCostGateDialog(
            title = "触顶后放宽限额？",
            subtitle = "今日额度已用完。把手指按在圆里，配合呼吸，再保存这次放宽。",
            confirmHint = "确认保存",
            onCompleted = {
                showBreathForLoosen = false
                val analytics = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    AnalyticsEntryPoint::class.java
                ).analyticsRepository()
                analytics.trackBreathGatePass(
                    reason = HaEvents.BreathReason.DAILY_LOOSEN,
                    app = appInfo?.appName.orEmpty(),
                    pkg = appInfo?.packageName.orEmpty()
                )
                persistAndLeave()
            },
            onDismiss = { showBreathForLoosen = false }
        )
    }

    if (showStopConfirm && appInfo != null) {
        StopMonitoringConfirmDialog(
            appName = appInfo.appName,
            onConfirm = { confirmStopMonitoring() },
            onDismiss = { showStopConfirm = false }
        )
    }

    if (showBreathForStop && appInfo != null) {
        PeriodLockDisableGateDialog(
            commitment = "",
            windowLabel = null,
            title = "锁定中停止监控？",
            confirmLabel = "确认停止",
            breathReason = HaEvents.BreathReason.STOP_MONITOR_LOCKED,
            appName = appInfo.appName,
            packageName = appInfo.packageName,
            onConfirm = {
                showBreathForStop = false
                executeStopMonitoring()
            },
            onDismiss = { showBreathForStop = false }
        )
    }
}
