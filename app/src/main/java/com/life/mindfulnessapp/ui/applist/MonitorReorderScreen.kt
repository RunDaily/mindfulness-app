package com.life.mindfulnessapp.ui.applist

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.data.analytics.HaEvents
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.domain.model.PeriodWindowsCodec
import com.life.mindfulnessapp.ui.theme.CapabilityPairMarks
import com.life.mindfulnessapp.ui.theme.LogoGreen
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 坑位排序二级页：拖动手柄调整顺序（与首页坑位轨同步）；可顺手移除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorReorderScreen(
    viewModel: AppListViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (packageName: String) -> Unit
) {
    val monitored by viewModel.monitoredApps.collectAsState()
    val haptic = LocalHapticFeedback.current

    var removingApp by remember { mutableStateOf<AppInfo?>(null) }
    var breathStopApp by remember { mutableStateOf<AppInfo?>(null) }
    var list by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isDragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(monitored) {
        if (!isDragging) list = monitored
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        list = list.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    val cs = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "调整顺序",
                            fontWeight = FontWeight.SemiBold,
                            color = cs.onSurface,
                            fontSize = 17.sp
                        )
                        Text(
                            "拖动手柄 · 与首页坑位同步",
                            fontSize = 11.sp,
                            color = cs.onSurface.copy(alpha = 0.40f)
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        containerColor = cs.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            state = lazyListState,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(list, key = { it.packageName }) { app ->
                ReorderableItem(reorderableState, key = app.packageName) { dragging ->
                    val elevation by animateDpAsState(
                        targetValue = if (dragging) 6.dp else 0.dp,
                        label = "reorder_drag_elev"
                    )
                    val handleInteraction = remember { MutableInteractionSource() }
                    Surface(
                        shadowElevation = elevation,
                        shape = RoundedCornerShape(14.dp),
                        color = cs.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ReorderManageRow(
                            app = app,
                            cs = cs,
                            onEdit = { onNavigateToEdit(app.packageName) },
                            onRemove = { removingApp = app },
                            dragHandleModifier = Modifier.draggableHandle(
                                interactionSource = handleInteraction,
                                onDragStarted = {
                                    isDragging = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragStopped = {
                                    isDragging = false
                                    viewModel.reorderMonitored(list.map { it.packageName })
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            )
                        )
                    }
                }
            }
        }
    }

    removingApp?.let { app ->
        AlertDialog(
            onDismissRequest = { removingApp = null },
            containerColor = cs.surface,
            title = {
                Text("停止监控「${app.appName}」？", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Text(
                    "之后打开该应用将不再拦截，历史记录仍会保留。",
                    fontSize = 13.sp,
                    color = cs.onSurface.copy(alpha = 0.55f)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = app
                        removingApp = null
                        scope.launch {
                            if (viewModel.isUnderActivePeriodHardLock(target.packageName)) {
                                breathStopApp = target
                            } else {
                                viewModel.removeFromMonitor(target.packageName)
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFE74C3C))
                ) { Text("移除", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(
                    onClick = { removingApp = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = cs.onSurface.copy(alpha = 0.45f))
                ) { Text("取消") }
            }
        )
    }

    breathStopApp?.let { app ->
        PeriodLockDisableGateDialog(
            commitment = "",
            windowLabel = null,
            title = "锁定中停止监控？",
            confirmLabel = "确认停止",
            breathReason = HaEvents.BreathReason.STOP_MONITOR_LOCKED,
            appName = app.appName,
            packageName = app.packageName,
            onConfirm = {
                viewModel.removeFromMonitor(app.packageName)
                breathStopApp = null
            },
            onDismiss = { breathStopApp = null }
        )
    }
}

@Composable
private fun ReorderManageRow(
    app: AppInfo,
    cs: ColorScheme,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    dragHandleModifier: Modifier
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onEdit)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(
            onClick = {},
            modifier = Modifier
                .size(40.dp)
                .then(dragHandleModifier)
        ) {
            Icon(
                Icons.Default.DragHandle,
                contentDescription = "拖动排序",
                tint = cs.onSurface.copy(alpha = 0.36f),
                modifier = Modifier.size(22.dp)
            )
        }

        AppIcon(drawable = app.icon, modifier = Modifier.size(40.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = app.appName,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (app.isUninstalled) cs.onSurface.copy(alpha = 0.40f) else cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = when {
                    app.isUninstalled -> "已卸载"
                    else -> buildString {
                        val parts = mutableListOf<String>()
                        if (app.periodLockEnabled) {
                            parts.add(
                                PeriodWindowsCodec.summaryLabel(
                                    PeriodWindowsCodec.decode(app.periodWindowsJson)
                                )
                            )
                        }
                        if (app.timeLimitEnabled) {
                            parts.add("每日 ${app.dailyLimitMinutes} 分钟")
                        }
                        if (app.requireIntentOnOpen) {
                            val intentParts = buildList {
                                add("意图门")
                                if (app.intentQualityCheckEnabled) {
                                    val n = com.life.mindfulnessapp.domain.model.IntentBlockKeywords
                                        .decode(app.intentBlockKeywordsJson).size
                                    add(if (n > 0) "限制词$n" else "限制词")
                                }
                                if (app.sessionLimitEnabled) add("单次")
                            }
                            parts.add(intentParts.joinToString(" · "))
                        }
                        if (parts.isEmpty()) append("点击设置")
                        else append(parts.joinToString(" · "))
                    }
                },
                fontSize = 12.sp,
                color = when {
                    app.isUninstalled -> cs.onSurface.copy(alpha = 0.30f)
                    else -> LogoGreen.copy(alpha = 0.80f)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (!app.isUninstalled) {
            CapabilityPairMarks(
                intentOn = app.requireIntentOnOpen,
                timeOn = app.timeLimitEnabled,
                periodOn = app.periodLockEnabled
            )
        }

        IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Default.RemoveCircleOutline,
                contentDescription = "移除监控",
                tint = cs.onSurface.copy(alpha = 0.28f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
