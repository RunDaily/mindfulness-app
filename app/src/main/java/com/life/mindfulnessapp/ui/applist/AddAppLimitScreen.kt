package com.life.mindfulnessapp.ui.applist

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.life.mindfulnessapp.domain.model.PeriodWindow
import com.life.mindfulnessapp.domain.model.PeriodWindowsCodec
import com.life.mindfulnessapp.ui.common.PermissionGateDialog
import com.life.mindfulnessapp.ui.common.PermissionGateMode
import com.life.mindfulnessapp.ui.common.PermissionSettingsIntents
import com.life.mindfulnessapp.ui.navigation.Screen
import com.life.mindfulnessapp.ui.theme.CapabilityKind
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.theme.MonitorCapability
import com.life.mindfulnessapp.ui.vip.AccessGateDialog
import kotlinx.coroutines.launch

/**
 * 添加监控 · 单能力配置页。
 * 主能力进页即开，只调这一项的参数；顶栏「开启××」写入。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAppLimitScreen(
    packageName: String,
    primaryCapability: CapabilityKind? = null,
    viewModel: AppListViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onAddSuccess: () -> Unit,
    onNavigateToVip: () -> Unit = {}
) {
    val apps by viewModel.apps.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val permStatus by viewModel.permissionStatus.collectAsState()
    val cs = MaterialTheme.colorScheme
    val isDark = cs.background.red < 0.5f
    val primary = primaryCapability ?: CapabilityKind.IntentGate
    val context = LocalContext.current

    var showPermissionGate by remember { mutableStateOf(false) }
    var pendingConfirm by remember { mutableStateOf(false) }

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshPermissions(HaEvents.Source.ENABLE_MONITOR) }
    val usageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshPermissions(HaEvents.Source.ENABLE_MONITOR) }

    LaunchedEffect(packageName) {
        viewModel.loadApp(packageName)
        viewModel.refreshPermissions()
    }

    val appInfo = remember(apps, packageName) {
        apps.find { it.packageName == packageName }
    }
    val scope = rememberCoroutineScope()

    val seeded = remember(primary) {
        // 绑定已选定主能力：进页即开，配置页不再二次拨总开关
        SeedDefaults(
            requireIntent = primary == CapabilityKind.IntentGate,
            timeLimitOn = primary == CapabilityKind.TimeLock,
            sessionLimitOn = false,
            periodLockOn = primary == CapabilityKind.PeriodLock
        )
    }

    var requireIntent by remember(primary) { mutableStateOf(seeded.requireIntent) }
    var timeLimitOn by remember(primary) { mutableStateOf(seeded.timeLimitOn) }
    var sessionLimitOn by remember(primary) { mutableStateOf(seeded.sessionLimitOn) }
    var dailyLimit by remember { mutableIntStateOf(30) }
    var periodLockOn by remember(primary) { mutableStateOf(seeded.periodLockOn) }
    var periodWindows by remember {
        mutableStateOf(listOf(PeriodWindow.defaultSleep()))
    }
    var isSaving by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    val initialWindows = remember { periodWindows }

    val isDirty = remember(
        requireIntent, timeLimitOn, sessionLimitOn, dailyLimit,
        periodLockOn, periodWindows, seeded, initialWindows
    ) {
        requireIntent != seeded.requireIntent ||
            timeLimitOn != seeded.timeLimitOn ||
            sessionLimitOn != seeded.sessionLimitOn ||
            dailyLimit != 30 ||
            periodLockOn != seeded.periodLockOn ||
            periodWindows != initialWindows
    }

    fun requestLeave() {
        if (isDirty) showDiscard = true else onNavigateBack()
    }

    BackHandler { requestLeave() }

    val periodReady = !periodLockOn || periodWindows.isNotEmpty()
    val canConfirm =
        (requireIntent || timeLimitOn || periodLockOn) &&
            periodReady && !isSaving

    fun performSave() {
        val info = appInfo ?: return
        scope.launch {
            isSaving = true
            val added = viewModel.saveMonitorConfig(
                appInfo = info,
                dailyLimitMinutes = dailyLimit,
                timeAwarenessEnabled = false,
                timeLimitEnabled = timeLimitOn,
                requireIntentOnOpen = requireIntent,
                sessionLimitEnabled = requireIntent && sessionLimitOn,
                intentQualityCheckEnabled = false,
                intentBlockKeywordsJson = "",
                defaultSessionLimitMinutes = 15,
                intentReviewEnabled = false,
                overTimeMessage = "",
                periodLockEnabled = periodLockOn,
                periodWindowsJson = if (periodLockOn) PeriodWindowsCodec.encode(periodWindows) else ""
            )
            isSaving = false
            if (added) onAddSuccess()
        }
    }

    fun confirm() {
        if (!canConfirm || appInfo == null) return
        if (!permStatus.coreGranted) {
            pendingConfirm = true
            showPermissionGate = true
            viewModel.onPermissionPromptShown(HaEvents.Source.ENABLE_MONITOR)
            return
        }
        performSave()
    }

    // 权限补齐后自动继续开启
    LaunchedEffect(permStatus.coreGranted, pendingConfirm, showPermissionGate) {
        if (permStatus.coreGranted && pendingConfirm && showPermissionGate) {
            showPermissionGate = false
            pendingConfirm = false
            performSave()
        }
    }

    val confirmLabel = "开启${MonitorCapability.label(primary)}"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (appInfo != null) {
                        ConfigAppBarTitle(appInfo = appInfo, cs = cs)
                    } else {
                        Text(
                            "设定${MonitorCapability.label(primary)}",
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
                        onClick = { confirm() },
                        enabled = canConfirm && appInfo != null
                    ) {
                        Text(
                            text = confirmLabel,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (canConfirm && appInfo != null) LogoGreen
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
                        "未找到该应用",
                        fontSize = 14.sp,
                        color = cs.onSurface.copy(alpha = 0.45f),
                        textAlign = TextAlign.Center
                    )
                }
            }
            else -> {
                when (primary) {
                    CapabilityKind.IntentGate -> {
                        IntentGateConfigContent(
                            appName = appInfo.appName,
                            requireIntent = requireIntent,
                            onRequireIntentChange = { on ->
                                requireIntent = on
                                if (!on) sessionLimitOn = false
                            },
                            sessionLimitOn = sessionLimitOn,
                            onSessionLimitChange = { sessionLimitOn = it },
                            allowMasterToggle = false,
                            modifier = Modifier
                                .padding(padding)
                                .navigationBarsPadding()
                        )
                    }
                    CapabilityKind.TimeLock -> {
                        TimeLockConfigContent(
                            appName = appInfo.appName,
                            timeLimitOn = timeLimitOn,
                            onTimeLimitChange = { timeLimitOn = it },
                            dailyLimit = dailyLimit,
                            onDailyLimitChange = { dailyLimit = it },
                            allowMasterToggle = false,
                            modifier = Modifier
                                .padding(padding)
                                .navigationBarsPadding()
                        )
                    }
                    CapabilityKind.PeriodLock -> {
                        PeriodLockConfigContent(
                            appName = appInfo.appName,
                            periodLockOn = periodLockOn,
                            onPeriodLockChange = { periodLockOn = it },
                            periodWindows = periodWindows,
                            onPeriodWindowsChange = { periodWindows = it },
                            allowMasterToggle = false,
                            lockIsLive = false,
                            modifier = Modifier
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

    val accessGate by viewModel.accessGate.collectAsState()
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

    if (showPermissionGate) {
        PermissionGateDialog(
            permissionStatus = permStatus,
            mode = PermissionGateMode.Required,
            onDismiss = {
                showPermissionGate = false
                pendingConfirm = false
                viewModel.trackPermissionSkip(HaEvents.Source.ENABLE_MONITOR)
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

private data class SeedDefaults(
    val requireIntent: Boolean,
    val timeLimitOn: Boolean,
    val sessionLimitOn: Boolean,
    val periodLockOn: Boolean
)

/** 路由 seed → 主能力；`all` / 未知 → 默认意图门 */
fun parsePrimaryCapabilitySeed(seed: String?): CapabilityKind {
    if (seed.isNullOrBlank() || seed == Screen.AppLimitAdd.SeedAll) {
        return CapabilityKind.IntentGate
    }
    return com.life.mindfulnessapp.ui.theme.parseCapabilityKind(seed)
}
