package com.life.mindfulnessapp.ui.applist

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.data.analytics.HaEvents
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.domain.model.PeriodWindow
import com.life.mindfulnessapp.domain.model.PeriodWindowsCodec
import com.life.mindfulnessapp.ui.common.BottomInsetSheetOverlay
import com.life.mindfulnessapp.ui.common.PermissionGateDialog
import com.life.mindfulnessapp.ui.common.PermissionGateMode
import com.life.mindfulnessapp.ui.common.PermissionSettingsIntents
import com.life.mindfulnessapp.ui.theme.CapabilityKind
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.theme.MonitorCapability
import kotlinx.coroutines.launch

/**
 * 网格内点 App：Activity 内底栏面板，确认即写入。
 */
@Composable
fun CapabilityBindConfigSheet(
    app: AppInfo,
    capability: CapabilityKind,
    viewModel: AppListViewModel,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    onNavigateToVip: () -> Unit = {},
    /** 首启等场景：使用情况已在前面授权，绑定时只检查悬浮窗 */
    usageAlreadyGranted: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val permStatus by viewModel.permissionStatus.collectAsState()
    val copy = remember(capability) { CapabilityCopy.of(capability) }
    val label = copy.label
    val accent = MonitorCapability.accent(capability)
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val bodyMaxHeight = screenHeight * 0.52f

    var dailyLimit by remember(app.packageName, capability) { mutableIntStateOf(30) }
    var periodWindows by remember(app.packageName, capability) {
        mutableStateOf(listOf(PeriodWindow.defaultSleep()))
    }
    var isSaving by remember { mutableStateOf(false) }
    var showPermissionGate by remember { mutableStateOf(false) }
    var pendingConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(app, capability) {
        dailyLimit = if (app.isMonitored && app.timeLimitEnabled) {
            app.dailyLimitMinutes.coerceAtLeast(DAILY_LIMIT_MIN)
        } else {
            30
        }
        periodWindows = when {
            app.isMonitored && app.periodWindowsJson.isNotBlank() ->
                PeriodWindowsCodec.decode(app.periodWindowsJson)
            capability == CapabilityKind.PeriodLock ->
                listOf(PeriodWindow.defaultSleep())
            else -> periodWindows
        }
    }

    val permissionGateMode = if (usageAlreadyGranted) {
        PermissionGateMode.OverlayOnly
    } else {
        PermissionGateMode.Required
    }
    val bindPermissionsReady = permStatus.hasOverlay &&
        (usageAlreadyGranted || permStatus.hasUsageStats)

    val canConfirm = !isSaving && when (capability) {
        CapabilityKind.PeriodLock -> periodWindows.isNotEmpty()
        else -> true
    }

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshPermissions(HaEvents.Source.BIND) }
    val usageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshPermissions(HaEvents.Source.BIND) }

    fun performSave() {
        if (!canConfirm) return
        scope.launch {
            isSaving = true
            val ok = saveCapabilityBind(
                viewModel = viewModel,
                app = app,
                capability = capability,
                dailyLimit = dailyLimit,
                periodWindowsJson = if (capability == CapabilityKind.PeriodLock) {
                    PeriodWindowsCodec.encode(periodWindows)
                } else {
                    null
                }
            )
            isSaving = false
            if (ok) {
                Toast.makeText(context, "已开启", Toast.LENGTH_SHORT).show()
                onSaved()
            }
        }
    }

    fun confirm() {
        if (isSaving) return
        if (!bindPermissionsReady) {
            pendingConfirm = true
            showPermissionGate = true
            viewModel.onPermissionPromptShown(HaEvents.Source.BIND)
            return
        }
        performSave()
    }

    LaunchedEffect(bindPermissionsReady, pendingConfirm, showPermissionGate) {
        if (bindPermissionsReady && pendingConfirm && showPermissionGate) {
            showPermissionGate = false
            pendingConfirm = false
            performSave()
        }
    }

    val dismissOnOutside = capability == CapabilityKind.IntentGate
    val scrollState = rememberScrollState()

    BottomInsetSheetOverlay(
        onDismissRequest = onDismiss,
        dismissOnBackPress = true,
        dismissOnClickOutside = dismissOnOutside,
        scrimAlpha = if (dismissOnOutside) 0.36f else 0.42f
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(drawable = app.icon, modifier = Modifier.size(40.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = app.appName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = accent.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Medium
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = cs.onSurface.copy(alpha = 0.50f)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = bodyMaxHeight)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = copy.description,
                fontSize = 14.sp,
                color = cs.onSurface.copy(alpha = 0.62f),
                lineHeight = 21.sp
            )

            if (app.suspectedClonePackages.isNotEmpty()) {
                Text(
                    text = "将同时锁定 ${app.suspectedClonePackages.size} 个疑似分身（可在详情里关闭）",
                    fontSize = 12.sp,
                    color = LogoGreen.copy(alpha = 0.90f),
                    lineHeight = 18.sp
                )
            } else if (app.isSystemDualRow || app.hasSystemDualInstance) {
                Text(
                    text = if (app.isSystemDualRow) {
                        "系统分身与主应用同包名；开启主应用即可一并拦截。建议同时打开「无障碍保活」，分身拦截更稳。"
                    } else {
                        "已检测到系统分身，开启后会一并拦截（可在详情里关闭）。建议打开「无障碍保活」。"
                    },
                    fontSize = 12.sp,
                    color = LogoGreen.copy(alpha = 0.90f),
                    lineHeight = 18.sp
                )
            } else if (app.suspectedCloneOfPackage != null && !app.hasSystemDualInstance) {
                Text(
                    text = "此应用疑似为分身；若主应用已开启「分身一并锁定」，通常无需单独添加",
                    fontSize = 12.sp,
                    color = cs.onSurface.copy(alpha = 0.48f),
                    lineHeight = 18.sp
                )
            }

            when (capability) {
                CapabilityKind.TimeLock -> {
                    CapabilityNestedCard(emphasized = true) {
                        DurationLimitSettings(
                            dailyMinutes = dailyLimit,
                            onDailyMinutesChange = { dailyLimit = it }
                        )
                    }
                }
                CapabilityKind.PeriodLock -> {
                    CapabilityNestedCard(emphasized = true) {
                        PeriodWindowSettings(
                            windows = periodWindows,
                            onWindowsChange = { periodWindows = it },
                            onRequestDisableWindow = { id ->
                                periodWindows = periodWindows.map {
                                    if (it.id == id) it.copy(enabled = false) else it
                                }
                            },
                            onRequestDeleteWindow = { id ->
                                periodWindows = periodWindows.filter { it.id != id }
                            },
                            lockIsLive = false
                        )
                    }
                    if (periodWindows.isEmpty()) {
                        Text(
                            text = "请至少添加一个时段",
                            fontSize = 12.sp,
                            color = Color(0xFFE8941A)
                        )
                    }
                }
                CapabilityKind.IntentGate -> Unit
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = cs.onSurface.copy(alpha = 0.08f),
                    contentColor = cs.onSurface.copy(alpha = 0.72f)
                )
            ) {
                Text("取消", fontWeight = FontWeight.Medium, fontSize = 15.sp)
            }
            Button(
                onClick = { confirm() },
                enabled = canConfirm,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LogoGreen,
                    contentColor = Color.White,
                    disabledContainerColor = LogoGreen.copy(alpha = 0.28f),
                    disabledContentColor = Color.White.copy(alpha = 0.70f)
                )
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text("开启", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }
        }
    }

    if (showPermissionGate) {
        PermissionGateDialog(
            permissionStatus = permStatus,
            mode = permissionGateMode,
            onDismiss = {
                showPermissionGate = false
                pendingConfirm = false
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

internal suspend fun saveCapabilityBind(
    viewModel: AppListViewModel,
    app: AppInfo,
    capability: CapabilityKind,
    dailyLimit: Int = 30,
    periodWindowsJson: String? = null
): Boolean {
    val intentOn = when {
        capability == CapabilityKind.IntentGate -> true
        app.isMonitored -> app.requireIntentOnOpen
        else -> false
    }
    val timeOn = when {
        capability == CapabilityKind.TimeLock -> true
        app.isMonitored -> app.timeLimitEnabled
        else -> false
    }
    val periodOn = when {
        capability == CapabilityKind.PeriodLock -> true
        app.isMonitored -> app.periodLockEnabled
        else -> false
    }
    val windowsJson = when {
        capability == CapabilityKind.PeriodLock && periodWindowsJson != null -> periodWindowsJson
        !periodOn -> if (app.isMonitored && app.periodLockEnabled) app.periodWindowsJson else ""
        app.isMonitored && app.periodWindowsJson.isNotBlank() -> app.periodWindowsJson
        else -> PeriodWindowsCodec.encode(listOf(PeriodWindow.defaultSleep()))
    }
    val daily = when {
        capability == CapabilityKind.TimeLock -> dailyLimit
        app.isMonitored -> app.dailyLimitMinutes.coerceAtLeast(DAILY_LIMIT_MIN)
        else -> 30
    }
    return viewModel.saveMonitorConfig(
        appInfo = app,
        dailyLimitMinutes = daily,
        timeAwarenessEnabled = false,
        timeLimitEnabled = timeOn,
        requireIntentOnOpen = intentOn,
        sessionLimitEnabled = false,
        intentQualityCheckEnabled = false,
        intentBlockKeywordsJson = app.intentBlockKeywordsJson,
        defaultSessionLimitMinutes = app.defaultSessionLimitMinutes.coerceIn(1, 60),
        intentReviewEnabled = false,
        overTimeMessage = app.overTimeMessage,
        periodLockEnabled = periodOn,
        periodWindowsJson = windowsJson,
        periodLockCommitment = ""
    )
}
