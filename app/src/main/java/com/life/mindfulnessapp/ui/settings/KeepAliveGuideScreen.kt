package com.life.mindfulnessapp.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.life.mindfulnessapp.domain.usecase.PermissionStatus
import com.life.mindfulnessapp.service.KeepAliveWallpaperService
import com.life.mindfulnessapp.ui.common.PermissionSettingsIntents
import com.life.mindfulnessapp.ui.theme.themeChrome
import com.life.mindfulnessapp.util.OemKeepAliveGuide
import com.life.mindfulnessapp.util.RecentsHider
import com.life.mindfulnessapp.util.RestrictedSettingsGuide

private val WarnCoral = Color(0xFFC47A6A)

/**
 * 后台保活：两段故事（能工作 / 防清理）+ 加强最轻。
 * 严苛机防清理置顶；温和机能工作置顶。进度只报可检测项。
 * OriginOS 六步见 [VivoOriginOsKeepAliveGuideScreen]。
 */
@Composable
fun KeepAliveGuideScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToVivoOriginOs: () -> Unit = {},
    onNavigateToWallpaperStudio: () -> Unit = {}
) {
    val context = LocalContext.current
    val chrome = themeChrome()
    val permStatus by viewModel.permissionStatus.collectAsState()
    val hideFromRecents by viewModel.hideFromRecents.collectAsState()
    val oem = remember { OemKeepAliveGuide.contentForDevice(context) }

    KeepAliveTwoTrackBody(
        title = "后台保活",
        deviceLine = OemKeepAliveGuide.deviceSubtitle(oem),
        tip = null,
        footer = "强行停止后需手动打开。锁定与白名单决定清不清理得到你。",
        switches = oem.switches,
        prioritizeAntiClean = OemKeepAliveGuide.prioritizeAntiClean(oem.family),
        showHardening = true,
        showVivoOriginOsEntry = OemKeepAliveGuide.shouldShowVivoOriginOsEntry(),
        collapseWorkWhenReady = true,
        hideFromRecents = hideFromRecents,
        onHideFromRecentsChange = { enabled ->
            viewModel.setHideFromRecents(enabled)
            (context as? android.app.Activity)?.let {
                RecentsHider.applyFromActivity(it, enabled)
            } ?: RecentsHider.apply(context, enabled)
        },
        onOpenWallpaperStudio = onNavigateToWallpaperStudio,
        bgColor = chrome.bg,
        textPrimary = chrome.textPrimary,
        textSecondary = chrome.textSecondary,
        hairline = chrome.border.copy(alpha = if (chrome.isDark) 0.45f else 0.55f),
        accentGreen = chrome.accent,
        permStatus = permStatus,
        onNavigateBack = onNavigateBack,
        onNavigateToVivoOriginOs = onNavigateToVivoOriginOs,
        onRefreshPermissions = {
            viewModel.refreshPermissions()
            viewModel.refreshServiceRunning()
        },
        onTrackOpen = { viewModel.trackKeepAliveOpen() }
    )
}

@Composable
fun VivoOriginOsKeepAliveGuideScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val chrome = themeChrome()
    val permStatus by viewModel.permissionStatus.collectAsState()
    val oem = remember { OemKeepAliveGuide.vivoOriginOsContent(context) }

    KeepAliveTwoTrackBody(
        title = "OriginOS 六步",
        deviceLine = "vivo · 完整路径",
        tip = oem.tip,
        footer = "第 6 步后需重启一次。强行停止后只能手动打开心锚。",
        switches = oem.switches,
        prioritizeAntiClean = true,
        showHardening = false,
        showVivoOriginOsEntry = false,
        collapseWorkWhenReady = true,
        hideFromRecents = false,
        onHideFromRecentsChange = {},
        onOpenWallpaperStudio = {},
        bgColor = chrome.bg,
        textPrimary = chrome.textPrimary,
        textSecondary = chrome.textSecondary,
        hairline = chrome.border.copy(alpha = if (chrome.isDark) 0.45f else 0.55f),
        accentGreen = chrome.accent,
        permStatus = permStatus,
        onNavigateBack = onNavigateBack,
        onRefreshPermissions = {
            viewModel.refreshPermissions()
            viewModel.refreshServiceRunning()
        },
        onTrackOpen = { viewModel.trackKeepAliveOpen() }
    )
}

@Composable
private fun KeepAliveTwoTrackBody(
    title: String,
    deviceLine: String,
    tip: String?,
    footer: String,
    switches: List<OemKeepAliveGuide.SwitchItem>,
    prioritizeAntiClean: Boolean,
    showHardening: Boolean,
    showVivoOriginOsEntry: Boolean,
    collapseWorkWhenReady: Boolean,
    hideFromRecents: Boolean,
    onHideFromRecentsChange: (Boolean) -> Unit,
    onOpenWallpaperStudio: () -> Unit,
    bgColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    hairline: Color,
    accentGreen: Color,
    permStatus: PermissionStatus,
    onNavigateBack: () -> Unit,
    onNavigateToVivoOriginOs: () -> Unit = {},
    onRefreshPermissions: () -> Unit,
    onTrackOpen: () -> Unit
) {
    val context = LocalContext.current

    val antiCleanSwitches = remember(switches) {
        switches.filter { it.detectKind == null }
    }
    val oemDetectSwitches = remember(switches) {
        switches.filter { it.detectKind != null }
    }

    val manualDone = remember(antiCleanSwitches) {
        mutableStateMapOf<String, Boolean>().apply {
            antiCleanSwitches.forEach { put(it.id, OemKeepAliveGuide.isMarkedDone(context, it.id)) }
        }
    }

    var wallpaperOn by remember {
        mutableStateOf(KeepAliveWallpaperService.isActive(context))
    }

    fun markManual(id: String, done: Boolean) {
        manualDone[id] = done
        OemKeepAliveGuide.setMarkedDone(context, id, done)
    }

    LaunchedEffect(Unit) {
        onTrackOpen()
        onRefreshPermissions()
        wallpaperOn = KeepAliveWallpaperService.isActive(context)
    }

    DisposableEffect(context) {
        val owner = context as? LifecycleOwner ?: return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefreshPermissions()
                wallpaperOn = KeepAliveWallpaperService.isActive(context)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { onRefreshPermissions() }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onRefreshPermissions() }

    data class WorkRow(
        val id: String,
        val title: String,
        val how: String? = null,
        val on: Boolean,
        val onOpen: () -> Unit
    )

    fun openOemOrFallback(item: OemKeepAliveGuide.SwitchItem, fallback: () -> Unit) {
        if (item.intents.isNotEmpty()) {
            OemKeepAliveGuide.launchFirstAvailable(context, item.intents)
        } else {
            fallback()
        }
    }

    val coveredKinds = oemDetectSwitches.mapNotNull { it.detectKind }.toSet()

    val workRows = buildList {
        fun addSystem(
            id: String,
            title: String,
            kind: OemKeepAliveGuide.DetectKind,
            how: String? = null,
            on: Boolean,
            onOpen: () -> Unit
        ) {
            if (kind in coveredKinds) return
            add(WorkRow(id, title, how, on, onOpen))
        }
        addSystem(
            id = "overlay",
            title = "悬浮窗",
            kind = OemKeepAliveGuide.DetectKind.Overlay,
            on = permStatus.hasOverlay,
            onOpen = { settingsLauncher.launch(PermissionSettingsIntents.overlay(context)) }
        )
        addSystem(
            id = "usage",
            title = "用量访问",
            kind = OemKeepAliveGuide.DetectKind.UsageStats,
            on = permStatus.hasUsageStats,
            onOpen = { settingsLauncher.launch(PermissionSettingsIntents.usageAccess()) }
        )
        addSystem(
            id = "notif",
            title = "通知",
            kind = OemKeepAliveGuide.DetectKind.Notification,
            on = permStatus.hasNotification,
            onOpen = {
                val needRuntime = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                if (needRuntime) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    settingsLauncher.launch(PermissionSettingsIntents.appNotification(context))
                }
            }
        )
        addSystem(
            id = "battery",
            title = "忽略电池优化",
            kind = OemKeepAliveGuide.DetectKind.BatteryIgnore,
            on = permStatus.hasBatteryOptimizationIgnored,
            onOpen = { settingsLauncher.launch(PermissionSettingsIntents.ignoreBattery(context)) }
        )
        addSystem(
            id = "a11y",
            title = "无障碍",
            kind = OemKeepAliveGuide.DetectKind.Accessibility,
            how = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                RestrictedSettingsGuide.accessibilityHow
            } else {
                null
            },
            on = permStatus.hasAccessibilityKeepAlive,
            onOpen = {
                settingsLauncher.launch(PermissionSettingsIntents.accessibilityKeepAlive())
            }
        )
        oemDetectSwitches.forEach { item ->
            val kind = item.detectKind ?: return@forEach
            add(
                WorkRow(
                    id = item.id,
                    title = item.title,
                    how = item.how,
                    on = OemKeepAliveGuide.isDetectOn(kind, permStatus),
                    onOpen = {
                        openOemOrFallback(item) {
                            when (kind) {
                                OemKeepAliveGuide.DetectKind.Overlay ->
                                    settingsLauncher.launch(PermissionSettingsIntents.overlay(context))
                                OemKeepAliveGuide.DetectKind.UsageStats ->
                                    settingsLauncher.launch(PermissionSettingsIntents.usageAccess())
                                OemKeepAliveGuide.DetectKind.Notification ->
                                    settingsLauncher.launch(
                                        PermissionSettingsIntents.appNotification(context)
                                    )
                                OemKeepAliveGuide.DetectKind.BatteryIgnore ->
                                    settingsLauncher.launch(
                                        PermissionSettingsIntents.ignoreBattery(context)
                                    )
                                OemKeepAliveGuide.DetectKind.Accessibility ->
                                    settingsLauncher.launch(
                                        PermissionSettingsIntents.accessibilityKeepAlive()
                                    )
                            }
                        }
                    }
                )
            )
        }
    }

    val workDone = workRows.count { it.on }
    val workTotal = workRows.size
    val workReady = workTotal > 0 && workDone >= workTotal

    val antiDone = antiCleanSwitches.count { item ->
        if (item.detectKind != null) {
            OemKeepAliveGuide.isDetectOn(item.detectKind, permStatus)
        } else {
            manualDone[item.id] == true
        }
    }
    val antiTotal = antiCleanSwitches.size

    val progressTitle: String
    val progressSub: String
    val progressOk: Boolean
    if (prioritizeAntiClean) {
        progressTitle = when {
            antiTotal == 0 -> if (workReady) "能工作已齐" else "能工作 $workDone / $workTotal"
            antiDone >= antiTotal -> "防清理就绪"
            else -> "防清理 $antiDone / $antiTotal"
        }
        progressSub = when {
            !workReady -> "能工作 $workDone / $workTotal"
            antiDone < antiTotal -> "能工作已齐 · 先做未完成项"
            else -> "能工作已齐 · 加强可选"
        }
        progressOk = workReady && (antiTotal == 0 || antiDone >= antiTotal)
    } else {
        progressTitle = if (workReady) "能工作已齐" else "能工作 $workDone / $workTotal"
        progressSub = when {
            !workReady -> "先开权限"
            antiTotal > 0 && antiDone < antiTotal -> "防清理建议完成"
            else -> "本机清理较温和"
        }
        progressOk = workReady
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = bgColor,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = textPrimary
                    )
                }
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimary
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = deviceLine,
                fontSize = 11.sp,
                color = textSecondary.copy(alpha = 0.55f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = progressTitle,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (progressOk) accentGreen.copy(alpha = 0.9f) else textPrimary
            )
            Text(
                text = progressSub,
                fontSize = 11.sp,
                color = textSecondary.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 3.dp)
            )
            if (!tip.isNullOrBlank()) {
                Text(
                    text = tip,
                    fontSize = 12.sp,
                    color = textSecondary.copy(alpha = 0.65f),
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            val antiBlock: @Composable () -> Unit = {
                if (antiCleanSwitches.isNotEmpty()) {
                    SectionLab(
                        text = "防清理",
                        hint = if (prioritizeAntiClean) "本机一键清理会杀进程" else "建议完成",
                        textSecondary = textSecondary
                    )
                    antiCleanSwitches.forEach { item ->
                        val done = manualDone[item.id] == true
                        SpineActionRow(
                            title = item.title,
                            how = if (done) null else item.how,
                            right = if (done) "完成" else "去做",
                            rightBad = !done,
                            faint = done,
                            soft = false,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            hairline = hairline,
                            onOpen = if (!done && item.intents.isNotEmpty()) {
                                {
                                    OemKeepAliveGuide.launchFirstAvailable(context, item.intents)
                                }
                            } else null,
                            onToggle = { markManual(item.id, !done) }
                        )
                    }
                }
            }

            val workBlock: @Composable () -> Unit = {
                if (workRows.isNotEmpty()) {
                    SectionLab(
                        text = if (workReady && collapseWorkWhenReady) "能工作 · 已齐" else "能工作",
                        hint = null,
                        textSecondary = textSecondary
                    )
                    if (workReady && collapseWorkWhenReady) {
                        SpineActionRow(
                            title = "权限",
                            how = null,
                            right = "已齐",
                            rightBad = false,
                            faint = true,
                            soft = false,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            hairline = hairline,
                            onOpen = null,
                            onToggle = null
                        )
                    } else {
                        workRows.forEach { row ->
                            SpineActionRow(
                                title = row.title,
                                how = if (row.on) null else row.how,
                                right = if (row.on) "已开" else "去开",
                                rightBad = !row.on,
                                faint = row.on,
                                soft = false,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                hairline = hairline,
                                onOpen = if (!row.on) row.onOpen else null,
                                onToggle = null
                            )
                        }
                    }
                }
            }

            if (prioritizeAntiClean) {
                antiBlock()
                workBlock()
            } else {
                workBlock()
                antiBlock()
            }

            if (showHardening) {
                SectionLab(text = "加强", hint = null, textSecondary = textSecondary)
                SpineActionRow(
                    title = "桌面壁纸",
                    how = if (wallpaperOn) {
                        "守护中 · 与发现页同一套"
                    } else {
                        "去制作并设为桌面，即开启守护"
                    },
                    right = if (wallpaperOn) "已开" else "去设",
                    rightBad = false,
                    faint = wallpaperOn,
                    soft = true,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    hairline = hairline,
                    onOpen = onOpenWallpaperStudio,
                    onToggle = null
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "隐藏最近任务",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            color = textSecondary.copy(alpha = 0.85f)
                        )
                        Text(
                            text = "多任务里不显示心锚",
                            fontSize = 10.5.sp,
                            color = textSecondary.copy(alpha = 0.45f),
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                    Switch(
                        checked = hideFromRecents,
                        onCheckedChange = onHideFromRecentsChange,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = accentGreen.copy(alpha = 0.55f),
                            checkedThumbColor = accentGreen
                        )
                    )
                }
                HorizontalDivider(color = hairline.copy(alpha = 0.55f))
            }

            if (showVivoOriginOsEntry) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToVivoOriginOs)
                        .padding(vertical = 14.dp)
                ) {
                    Text(
                        text = "OriginOS 完整六步",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = textPrimary.copy(alpha = 0.9f)
                    )
                    Text(
                        text = "白名单 · 冻结例外 · 需重启",
                        fontSize = 11.sp,
                        color = textSecondary.copy(alpha = 0.45f),
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                HorizontalDivider(color = hairline.copy(alpha = 0.55f))
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = footer,
                fontSize = 11.sp,
                color = textSecondary.copy(alpha = 0.38f),
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}

@Composable
private fun SectionLab(text: String, hint: String?, textSecondary: Color) {
    Spacer(modifier = Modifier.height(18.dp))
    Text(
        text = text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        color = textSecondary.copy(alpha = 0.5f),
        letterSpacing = 0.8.sp
    )
    if (!hint.isNullOrBlank()) {
        Text(
            text = hint,
            fontSize = 10.5.sp,
            color = textSecondary.copy(alpha = 0.42f),
            modifier = Modifier.padding(top = 3.dp, bottom = 2.dp)
        )
    } else {
        Spacer(modifier = Modifier.height(2.dp))
    }
}

@Composable
private fun SpineActionRow(
    title: String,
    how: String?,
    right: String,
    rightBad: Boolean,
    faint: Boolean,
    soft: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    hairline: Color,
    onOpen: (() -> Unit)?,
    onToggle: (() -> Unit)?
) {
    val titleColor = when {
        faint -> textPrimary.copy(alpha = 0.38f)
        soft -> textSecondary.copy(alpha = 0.85f)
        else -> textPrimary
    }
    val rightColor = when {
        rightBad -> WarnCoral
        faint -> textPrimary.copy(alpha = 0.4f)
        else -> textSecondary.copy(alpha = 0.65f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                when {
                    onOpen != null -> Modifier.clickable(onClick = onOpen)
                    onToggle != null -> Modifier.clickable(onClick = onToggle)
                    else -> Modifier
                }
            )
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = if (soft) 14.sp else 15.sp,
                fontWeight = if (soft) FontWeight.Normal else FontWeight.Medium,
                color = titleColor
            )
            if (!how.isNullOrBlank()) {
                Text(
                    text = how,
                    fontSize = if (soft) 10.5.sp else 11.sp,
                    color = textSecondary.copy(alpha = if (faint) 0.35f else 0.5f),
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
        Text(
            text = right,
            fontSize = 12.sp,
            fontWeight = if (rightBad) FontWeight.SemiBold else FontWeight.Normal,
            color = rightColor,
            modifier = if (onToggle != null) {
                Modifier.clickable(onClick = onToggle)
            } else {
                Modifier
            }
        )
    }
    HorizontalDivider(color = hairline.copy(alpha = 0.55f))
}
