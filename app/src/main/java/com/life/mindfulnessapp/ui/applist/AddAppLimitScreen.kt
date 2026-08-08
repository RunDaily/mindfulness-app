package com.life.mindfulnessapp.ui.applist

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.domain.model.IntentBlockKeywords
import com.life.mindfulnessapp.domain.model.PeriodLockPolicy
import com.life.mindfulnessapp.domain.model.PeriodWindow
import com.life.mindfulnessapp.domain.model.PeriodWindowsCodec
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.vip.VipUpgradeDialog
import kotlinx.coroutines.launch

/**
 * 添加监控 · 配置页（全屏）
 * 主页：生效摘要 + 三能力轻配置；时段管理下沉子页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAppLimitScreen(
    packageName: String,
    viewModel: AppListViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onAddSuccess: () -> Unit,
    onNavigateToVip: () -> Unit = {}
) {
    val apps by viewModel.apps.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val showVipUpgradeDialog by viewModel.showVipUpgradeDialog.collectAsState()
    val monitoredCount by viewModel.monitoredCount.collectAsState()
    val cs = MaterialTheme.colorScheme
    val isDark = cs.background.red < 0.5f
    val showDesignPhilosophy = monitoredCount == 0

    LaunchedEffect(packageName) {
        viewModel.loadApp(packageName)
    }

    val appInfo = remember(apps, packageName) {
        apps.find { it.packageName == packageName }
    }
    val scope = rememberCoroutineScope()

    var requireIntent by remember { mutableStateOf(true) }
    var timeLimitOn by remember { mutableStateOf(true) }
    var sessionLimitOn by remember { mutableStateOf(true) }
    var intentQualityCheckOn by remember { mutableStateOf(false) }
    var intentBlockKeywords by remember { mutableStateOf(emptyList<String>()) }
    var dailyLimit by remember { mutableIntStateOf(30) }
    var periodLockOn by remember { mutableStateOf(false) }
    var periodWindows by remember {
        mutableStateOf(listOf(PeriodWindow.defaultSleep()))
    }
    var periodCommitment by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var showPeriodManage by remember { mutableStateOf(false) }

    val periodReady = !periodLockOn || (
        periodWindows.isNotEmpty() &&
            periodCommitment.trim().length >= PeriodLockPolicy.COMMITMENT_MIN_CHARS
        )
    val canConfirm =
        (requireIntent || timeLimitOn || periodLockOn) && periodReady && !isSaving

    fun confirm() {
        val info = appInfo ?: return
        if (!canConfirm) return
        scope.launch {
            isSaving = true
            val added = viewModel.saveMonitorConfig(
                appInfo = info,
                dailyLimitMinutes = dailyLimit,
                timeLimitEnabled = timeLimitOn,
                requireIntentOnOpen = requireIntent,
                sessionLimitEnabled = requireIntent && sessionLimitOn,
                intentQualityCheckEnabled = requireIntent && intentQualityCheckOn,
                intentBlockKeywordsJson = if (requireIntent && intentQualityCheckOn) {
                    IntentBlockKeywords.encode(intentBlockKeywords)
                } else {
                    ""
                },
                defaultSessionLimitMinutes = 15,
                intentReviewEnabled = false,
                overTimeMessage = "",
                periodLockEnabled = periodLockOn,
                periodWindowsJson = if (periodLockOn) PeriodWindowsCodec.encode(periodWindows) else "",
                periodLockCommitment = periodCommitment
            )
            isSaving = false
            if (added) onAddSuccess()
        }
    }

    if (showPeriodManage) {
        PeriodWindowsManageScreen(
            windows = periodWindows,
            onWindowsChange = { periodWindows = it },
            commitment = periodCommitment,
            onCommitmentChange = { periodCommitment = it },
            onBack = { showPeriodManage = false }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (appInfo != null) {
                        ConfigAppBarTitle(appInfo = appInfo, cs = cs)
                    } else {
                        Text(
                            "设定监控",
                            fontWeight = FontWeight.SemiBold,
                            color = cs.onSurface,
                            fontSize = 17.sp
                        )
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
                actions = {
                    TextButton(
                        onClick = { confirm() },
                        enabled = canConfirm && appInfo != null
                    ) {
                        Text(
                            text = "开始监控",
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
                MonitorConfigForm(
                    requireIntent = requireIntent,
                    onRequireIntentChange = { on ->
                        requireIntent = on
                        if (!on) {
                            sessionLimitOn = false
                            intentQualityCheckOn = false
                        }
                    },
                    timeLimitOn = timeLimitOn,
                    onTimeLimitChange = { timeLimitOn = it },
                    dailyLimit = dailyLimit,
                    onDailyLimitChange = { dailyLimit = it },
                    sessionLimitOn = sessionLimitOn,
                    onSessionLimitChange = { sessionLimitOn = it },
                    intentQualityCheckOn = intentQualityCheckOn,
                    onIntentQualityCheckChange = { intentQualityCheckOn = it },
                    intentBlockKeywords = intentBlockKeywords,
                    onIntentBlockKeywordsChange = { intentBlockKeywords = it },
                    periodLockOn = periodLockOn,
                    onPeriodLockChange = { periodLockOn = it },
                    periodWindows = periodWindows,
                    onPeriodWindowsChange = { periodWindows = it },
                    periodCommitment = periodCommitment,
                    onPeriodCommitmentChange = { periodCommitment = it },
                    onManagePeriods = { showPeriodManage = true },
                    bothOffHint = "请至少开启一项能力后，才能开始监控",
                    showDesignPhilosophy = showDesignPhilosophy,
                    modifier = Modifier
                        .padding(padding)
                        .navigationBarsPadding()
                )
            }
        }
    }

    if (showVipUpgradeDialog) {
        VipUpgradeDialog(
            isDarkTheme = isDark,
            cardColor = cs.surface,
            textPrimary = cs.onSurface,
            textSecondary = cs.onSurfaceVariant,
            borderColor = cs.outline,
            accentGreen = LogoGreen,
            onDismiss = { viewModel.dismissVipUpgradeDialog() },
            onUpgrade = {
                viewModel.dismissVipUpgradeDialog()
                onNavigateToVip()
            }
        )
    }
}
