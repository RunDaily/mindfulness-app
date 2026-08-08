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
import kotlinx.coroutines.launch

/**
 * 编辑监控配置（全屏）
 * 主页：生效摘要 + 三能力轻配置；时段管理下沉子页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLimitEditScreen(
    packageName: String,
    viewModel: AppListViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToHistory: () -> Unit = {}
) {
    val apps by viewModel.apps.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val todayGlance by viewModel.todayGlance.collectAsState()
    val cs = MaterialTheme.colorScheme

    LaunchedEffect(packageName) {
        viewModel.loadApp(packageName)
    }

    val appInfo = remember(apps, packageName) {
        apps.find { it.packageName == packageName && it.isMonitored }
    }
    val scope = rememberCoroutineScope()

    val prevDailyLimit = appInfo?.dailyLimitMinutes?.coerceAtLeast(DAILY_LIMIT_MIN) ?: 30

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
    var seeded by remember(packageName) { mutableStateOf(false) }
    var showStopConfirm by remember { mutableStateOf(false) }
    var showPeriodManage by remember { mutableStateOf(false) }

    LaunchedEffect(appInfo) {
        val info = appInfo ?: return@LaunchedEffect
        if (seeded) return@LaunchedEffect
        requireIntent = info.requireIntentOnOpen
        timeLimitOn = info.timeLimitEnabled
        sessionLimitOn = info.requireIntentOnOpen && info.sessionLimitEnabled
        intentQualityCheckOn = info.requireIntentOnOpen && info.intentQualityCheckEnabled
        // 即使检验关着也保留词表，方便再次打开时不丢
        intentBlockKeywords = IntentBlockKeywords.decode(info.intentBlockKeywordsJson)
        dailyLimit = info.dailyLimitMinutes.coerceAtLeast(DAILY_LIMIT_MIN)
        periodLockOn = info.periodLockEnabled
        val decoded = PeriodWindowsCodec.decode(info.periodWindowsJson)
        periodWindows = decoded.ifEmpty { listOf(PeriodWindow.defaultSleep()) }
        periodCommitment = info.periodLockCommitment
        seeded = true
    }

    val periodReady = !periodLockOn || (
        periodWindows.isNotEmpty() &&
            periodCommitment.trim().length >= PeriodLockPolicy.COMMITMENT_MIN_CHARS
        )
    val canSave =
        (requireIntent || timeLimitOn || periodLockOn) && periodReady && !isSaving && appInfo != null
    val dailyHint = when {
        appInfo == null -> null
        dailyLimit < prevDailyLimit -> "收紧了"
        dailyLimit > prevDailyLimit -> "放宽了"
        else -> null
    }

    fun anyOtherOn(excluding: String): Boolean = when (excluding) {
        "intent" -> timeLimitOn || periodLockOn
        "time" -> requireIntent || periodLockOn
        "period" -> requireIntent || timeLimitOn
        else -> requireIntent || timeLimitOn || periodLockOn
    }

    fun requestIntentChange(on: Boolean) {
        if (!on && requireIntent && !anyOtherOn("intent")) {
            showStopConfirm = true
            return
        }
        requireIntent = on
        if (!on) {
            sessionLimitOn = false
            intentQualityCheckOn = false
        }
    }

    fun requestTimeLimitChange(on: Boolean) {
        if (!on && timeLimitOn && !anyOtherOn("time")) {
            showStopConfirm = true
            return
        }
        timeLimitOn = on
    }

    fun requestPeriodLockChange(on: Boolean) {
        if (!on && periodLockOn && !anyOtherOn("period")) {
            showStopConfirm = true
            return
        }
        periodLockOn = on
        if (on && periodWindows.isEmpty()) {
            periodWindows = listOf(PeriodWindow.defaultSleep())
        }
    }

    fun saveAndLeave() {
        val info = appInfo ?: return
        if (!canSave) return
        scope.launch {
            isSaving = true
            val ok = viewModel.saveMonitorConfig(
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
                overTimeMessage = info.overTimeMessage,
                periodLockEnabled = periodLockOn,
                periodWindowsJson = if (periodLockOn) PeriodWindowsCodec.encode(periodWindows) else "",
                periodLockCommitment = periodCommitment
            )
            isSaving = false
            if (ok) onNavigateBack()
        }
    }

    fun confirmStopMonitoring() {
        val info = appInfo ?: return
        showStopConfirm = false
        scope.launch {
            isSaving = true
            viewModel.stopMonitoring(info.packageName)
            isSaving = false
            onNavigateBack()
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
                            "调整监控",
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
                        onClick = { saveAndLeave() },
                        enabled = canSave
                    ) {
                        Text(
                            text = "完成",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (canSave) LogoGreen else cs.onSurface.copy(alpha = 0.28f)
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
                MonitorConfigForm(
                    requireIntent = requireIntent,
                    onRequireIntentChange = { requestIntentChange(it) },
                    timeLimitOn = timeLimitOn,
                    onTimeLimitChange = { requestTimeLimitChange(it) },
                    dailyLimit = dailyLimit,
                    onDailyLimitChange = { dailyLimit = it },
                    sessionLimitOn = sessionLimitOn,
                    onSessionLimitChange = { sessionLimitOn = it },
                    intentQualityCheckOn = intentQualityCheckOn,
                    onIntentQualityCheckChange = { intentQualityCheckOn = it },
                    intentBlockKeywords = intentBlockKeywords,
                    onIntentBlockKeywordsChange = { intentBlockKeywords = it },
                    periodLockOn = periodLockOn,
                    onPeriodLockChange = { requestPeriodLockChange(it) },
                    periodWindows = periodWindows,
                    onPeriodWindowsChange = { periodWindows = it },
                    periodCommitment = periodCommitment,
                    onPeriodCommitmentChange = { periodCommitment = it },
                    onManagePeriods = { showPeriodManage = true },
                    dailyHint = dailyHint,
                    bothOffHint = null,
                    todayGlance = todayGlance,
                    onTodayGlanceClick = onNavigateToHistory,
                    modifier = Modifier
                        .padding(padding)
                        .navigationBarsPadding()
                )
            }
        }
    }

    if (showStopConfirm && appInfo != null) {
        StopMonitoringConfirmDialog(
            appName = appInfo.appName,
            onConfirm = { confirmStopMonitoring() },
            onDismiss = { showStopConfirm = false }
        )
    }
}
