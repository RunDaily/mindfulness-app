package com.life.mindfulnessapp.ui.schedule

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.data.analytics.HaEvents
import com.life.mindfulnessapp.domain.model.PeriodDays
import com.life.mindfulnessapp.domain.model.PeriodWindow
import com.life.mindfulnessapp.ui.applist.PeriodLockDisableGateDialog
import com.life.mindfulnessapp.ui.common.SecondaryPageScaffold
import com.life.mindfulnessapp.ui.theme.LogoGreen

/** 日程锁 · 添加/编辑时段（Activity 二级页）。 */
@Composable
fun ScheduleLockEditScreen(
    planId: String?,
    onFinished: () -> Unit,
    viewModel: ScheduleLockEditViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(planId) { viewModel.load(planId) }
    LaunchedEffect(state.finished) {
        if (state.finished) onFinished()
    }

    BackHandler {
        if (state.pickingApps) viewModel.closeAppPicker() else onFinished()
    }

    when {
        !state.ready -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = LogoGreen)
            }
        }
        state.pickingApps -> {
            ScheduleLockAppPickerPage(
                apps = state.allApps,
                monitoredPackages = state.monitoredApps.map { it.packageName }.toSet(),
                followMonitored = state.editor.includeMonitored,
                selected = state.editor.packageNames.toSet(),
                onToggle = viewModel::togglePackage,
                onBack = viewModel::closeAppPicker,
                onDone = viewModel::closeAppPicker
            )
        }
        else -> {
            ScheduleLockEditorPage(
                editor = state.editor,
                monitoredApps = state.monitoredApps,
                appLabels = state.appLabels,
                toast = state.toast,
                onConsumeToast = viewModel::consumeToast,
                onTitleChange = viewModel::updateTitle,
                onStartChange = { viewModel.updateRange(it, state.editor.endMinute) },
                onEndChange = { viewModel.updateRange(state.editor.startMinute, it) },
                onDaysChange = viewModel::updateDaysMask,
                onIncludeMonitoredChange = viewModel::setIncludeMonitored,
                onPickApps = viewModel::openAppPicker,
                onSave = viewModel::requestSave,
                onDelete = viewModel::requestDelete,
                onCancel = onFinished
            )
        }
    }

    state.pendingBreath?.let { action ->
        val original = state.original
        PeriodLockDisableGateDialog(
            commitment = original?.title.orEmpty(),
            windowLabel = original?.label(),
            title = when (action) {
                ScheduleLockBreathAction.Delete -> "删除日程锁？"
                ScheduleLockBreathAction.Save -> "改动日程锁？"
            },
            confirmLabel = when (action) {
                ScheduleLockBreathAction.Delete -> "确认删除"
                ScheduleLockBreathAction.Save -> "确认改动"
            },
            breathReason = HaEvents.BreathReason.SCHEDULE_WEAKEN,
            onConfirm = viewModel::confirmBreath,
            onDismiss = viewModel::dismissBreath
        )
    }

    if (state.pendingSoftDelete) {
        val colors = MaterialTheme.colorScheme
        val title = state.original?.title?.trim().orEmpty()
            .ifEmpty { state.editor.title.trim() }
        AlertDialog(
            onDismissRequest = viewModel::dismissSoftDelete,
            title = {
                Text(
                    if (title.isNotEmpty()) "删除「$title」？" else "删除日程锁？"
                )
            },
            text = {
                Text(
                    "删除后不再按此时段执行",
                    color = colors.onSurfaceVariant,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmSoftDelete) {
                    Text("删除", color = colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissSoftDelete) {
                    Text("取消", color = colors.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
internal fun ScheduleLockEditorPage(
    editor: ScheduleLockEditorState,
    monitoredApps: List<com.life.mindfulnessapp.domain.model.AppInfo>,
    appLabels: Map<String, String>,
    toast: String?,
    onConsumeToast: () -> Unit,
    onTitleChange: (String) -> Unit,
    onStartChange: (Int) -> Unit,
    onEndChange: (Int) -> Unit,
    onDaysChange: (Int) -> Unit,
    onIncludeMonitoredChange: (Boolean) -> Unit,
    onPickApps: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val mask = editor.daysMask and PeriodDays.EVERY_DAY
    val isEveryDay = mask == PeriodDays.EVERY_DAY
    val isWeekdays = mask == PeriodDays.WEEKDAYS
    val isPresetCustom = !isEveryDay && !isWeekdays
    var customOpen by remember(editor.itemId, editor.isNew) {
        mutableStateOf(isPresetCustom)
    }
    val showCustom = customOpen || isPresetCustom
    val monitoredNames = monitoredApps.map { it.appName }
    val followLabel = when {
        monitoredNames.isEmpty() -> "跟随监控中的"
        else -> "跟随监控中的 · ${formatNameList(monitoredNames, max = 2)}"
    }
    val extraNames = editor.packageNames.map { pkg -> appLabels[pkg] ?: pkg }
    val extraLine = when {
        extraNames.isEmpty() -> "选择 ›"
        else -> "${formatNameList(extraNames)} ›"
    }

    fun pickTime(current: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(
            context,
            { _, h, m -> onPicked(h * 60 + m) },
            current / 60,
            current % 60,
            true
        ).show()
    }

    SecondaryPageScaffold(
        title = if (editor.isNew) "添加时段" else "编辑时段",
        onBack = onCancel
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            toast?.let { msg ->
                Text(
                    msg,
                    color = colors.error,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .clickable(onClick = onConsumeToast)
                )
            }

            FieldRow(label = "名称") {
                BasicTextField(
                    value = editor.title,
                    onValueChange = onTitleChange,
                    singleLine = true,
                    textStyle = TextStyle(color = colors.onBackground, fontSize = 15.sp),
                    cursorBrush = SolidColor(LogoGreen),
                    decorationBox = { inner ->
                        if (editor.title.isEmpty()) {
                            Text(
                                "工作专注",
                                color = colors.onSurfaceVariant.copy(alpha = 0.35f),
                                fontSize = 15.sp
                            )
                        }
                        inner()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            FieldRow(
                label = "开始",
                value = PeriodWindow.formatHm(editor.startMinute),
                onClick = { pickTime(editor.startMinute, onStartChange) }
            )
            FieldRow(
                label = "结束",
                value = PeriodWindow.formatHm(editor.endMinute),
                onClick = { pickTime(editor.endMinute, onEndChange) }
            )

            Text(
                "重复",
                color = colors.onSurfaceVariant.copy(alpha = 0.55f),
                fontSize = 11.sp,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(top = 18.dp, bottom = 10.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                QuietChip("每天", isEveryDay && !showCustom) {
                    customOpen = false
                    onDaysChange(PeriodDays.EVERY_DAY)
                }
                QuietChip("工作日", isWeekdays && !showCustom) {
                    customOpen = false
                    onDaysChange(PeriodDays.WEEKDAYS)
                }
                QuietChip("自定义", showCustom) { customOpen = true }
            }
            if (showCustom) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    listOf(
                        "一" to PeriodDays.MON,
                        "二" to PeriodDays.TUE,
                        "三" to PeriodDays.WED,
                        "四" to PeriodDays.THU,
                        "五" to PeriodDays.FRI,
                        "六" to PeriodDays.SAT,
                        "日" to PeriodDays.SUN
                    ).forEach { (label, bit) ->
                        val on = (mask and bit) != 0
                        Text(
                            label,
                            color = if (on) LogoGreen else colors.onSurfaceVariant.copy(alpha = 0.4f),
                            fontSize = 13.sp,
                            modifier = Modifier.clickable {
                                val next = if (on) mask and bit.inv() else mask or bit
                                onDaysChange(if (next == 0) bit else next)
                            }
                        )
                    }
                }
            }

            Text(
                "锁定范围",
                color = colors.onSurfaceVariant.copy(alpha = 0.55f),
                fontSize = 11.sp,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
            )
            FieldRow(
                label = followLabel,
                value = if (editor.includeMonitored) "开" else "关",
                emphasizeValue = editor.includeMonitored,
                valueMuted = !editor.includeMonitored,
                onClick = { onIncludeMonitoredChange(!editor.includeMonitored) }
            )
            if (monitoredNames.isEmpty()) {
                Text(
                    if (editor.includeMonitored) {
                        "还没有监控 App · 先去方案添加，或另选 App"
                    } else {
                        "还没有监控 App · 用下方另选 App"
                    },
                    color = colors.onSurfaceVariant.copy(alpha = 0.45f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                )
            }
            FieldRow(
                label = "另选 App",
                value = extraLine,
                valueMuted = editor.packageNames.isEmpty(),
                onClick = onPickApps
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text("保存", color = LogoGreen, fontSize = 15.sp, modifier = Modifier.clickable(onClick = onSave))
                Text(
                    "取消",
                    color = colors.onSurfaceVariant.copy(alpha = 0.65f),
                    fontSize = 15.sp,
                    modifier = Modifier.clickable(onClick = onCancel)
                )
                if (!editor.isNew) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        "删除",
                        color = colors.error.copy(alpha = 0.8f),
                        fontSize = 15.sp,
                        modifier = Modifier.clickable(onClick = onDelete)
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
internal fun ScheduleLockAppPickerPage(
    apps: List<com.life.mindfulnessapp.domain.model.AppInfo>,
    monitoredPackages: Set<String>,
    followMonitored: Boolean,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter {
            com.life.mindfulnessapp.util.AppNameSearch.matches(it.appName, it.packageName, query)
        }
    }
    // 跟随开：监控中的已含在主开关，选单只出其他应用
    val selectable = remember(filtered, monitoredPackages, followMonitored) {
        if (followMonitored) filtered.filter { it.packageName !in monitoredPackages }
        else filtered
    }
    val monitoredSection = remember(selectable, monitoredPackages, followMonitored) {
        if (followMonitored) emptyList()
        else selectable.filter { it.packageName in monitoredPackages }
            .sortedWith(pickerOrder(selected))
    }
    val otherSection = remember(selectable, monitoredPackages, followMonitored, selected) {
        val raw = if (followMonitored) {
            selectable
        } else {
            selectable.filter { it.packageName !in monitoredPackages }
        }
        raw.sortedWith(pickerOrder(selected))
    }
    val subtitle = if (followMonitored) {
        "另选未在监控里的"
    } else {
        "可从全部应用里选"
    }

    SecondaryPageScaffold(
        title = "另选 App",
        onBack = onBack,
        subtitle = subtitle
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = colors.onBackground, fontSize = 15.sp),
                cursorBrush = SolidColor(LogoGreen),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            "搜索应用",
                            color = colors.onSurfaceVariant.copy(alpha = 0.35f),
                            fontSize = 15.sp
                        )
                    }
                    inner()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            )
            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.28f))

            if (apps.isEmpty()) {
                Text(
                    "没有可选取的 App",
                    color = colors.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 16.dp)
                )
            } else if (selectable.isEmpty()) {
                Text(
                    if (query.isNotBlank()) "没有匹配的 App"
                    else if (followMonitored) "监控外没有可另选的 App"
                    else "没有可选取的 App",
                    color = colors.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 16.dp)
                )
            } else {
                if (monitoredSection.isNotEmpty()) {
                    PickerSectionLabel("监控中的")
                    monitoredSection.forEach { app ->
                        PickerAppRow(app = app, selected = selected, onToggle = onToggle)
                    }
                }
                if (otherSection.isNotEmpty()) {
                    PickerSectionLabel(
                        when {
                            followMonitored -> "可另选"
                            monitoredSection.isEmpty() -> "全部应用"
                            else -> "其他应用"
                        },
                        top = if (monitoredSection.isEmpty()) 8.dp else 18.dp
                    )
                    otherSection.forEach { app ->
                        PickerAppRow(app = app, selected = selected, onToggle = onToggle)
                    }
                }
            }
            Text(
                "完成",
                color = LogoGreen,
                fontSize = 15.sp,
                modifier = Modifier
                    .padding(top = 22.dp, bottom = 24.dp)
                    .clickable(onClick = onDone)
            )
        }
    }
}

private fun pickerOrder(selected: Set<String>): Comparator<com.life.mindfulnessapp.domain.model.AppInfo> =
    compareBy<com.life.mindfulnessapp.domain.model.AppInfo> { it.packageName !in selected }
        .thenBy { com.life.mindfulnessapp.util.AppNameSearch.sortKey(it.appName) }

@Composable
private fun PickerAppRow(
    app: com.life.mindfulnessapp.domain.model.AppInfo,
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    FieldRow(
        label = app.appName,
        value = if (app.packageName in selected) "已选" else "选取",
        valueMuted = app.packageName !in selected,
        emphasizeValue = app.packageName in selected,
        onClick = { onToggle(app.packageName) }
    )
}

private fun formatNameList(names: List<String>, max: Int = 3): String = when {
    names.isEmpty() -> ""
    names.size <= max -> names.joinToString("、")
    else -> "${names.take(max).joinToString("、")} 等"
}

@Composable
private fun PickerSectionLabel(text: String, top: androidx.compose.ui.unit.Dp = 0.dp) {
    val colors = MaterialTheme.colorScheme
    Text(
        text,
        color = colors.onSurfaceVariant.copy(alpha = 0.55f),
        fontSize = 11.sp,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(top = top, bottom = 6.dp)
    )
}

@Composable
private fun QuietChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(
        label,
        color = if (selected) LogoGreen else colors.onSurfaceVariant.copy(alpha = 0.45f),
        fontSize = 13.sp,
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun FieldRow(
    label: String,
    value: String? = null,
    valueMuted: Boolean = false,
    emphasizeValue: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: (@Composable () -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                color = colors.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(end = 16.dp)
            )
            when {
                content != null -> Box(modifier = Modifier.weight(1f)) { content() }
                value != null -> Text(
                    value,
                    color = when {
                        emphasizeValue -> LogoGreen
                        valueMuted -> colors.onSurfaceVariant.copy(alpha = 0.4f)
                        else -> colors.onBackground
                    },
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.28f))
    }
}
