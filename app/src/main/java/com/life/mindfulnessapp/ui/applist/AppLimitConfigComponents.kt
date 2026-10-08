package com.life.mindfulnessapp.ui.applist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.domain.model.AppTodayGlance
import com.life.mindfulnessapp.domain.model.IntentBlockKeywords
import com.life.mindfulnessapp.ui.theme.CapabilityForm
import com.life.mindfulnessapp.ui.theme.CapabilityKind
import com.life.mindfulnessapp.ui.theme.CapabilityMark
import com.life.mindfulnessapp.ui.theme.LogoGreen

/**
 * 监控配置页 · iOS Settings 风格共享件。
 *
 * 设计原则：
 * 1. 分组列表：灰底 + 圆角白组，无描边卡片。
 * 2. 行式决策：左标题、右开关 / 数值 / chevron。
 * 3. 组头组尾用安静 caption，不在行内堆说明。
 * 4. 呈现意图门、时长锁与时段锁；提醒 / 仪式不上位。
 * 5. 主操作落在导航栏「完成 / 开始监控」，不钉大按钮。
 */

internal val ConfigPagePadding = 16.dp
internal val ConfigGroupShape = RoundedCornerShape(12.dp)
internal val ConfigGroupGap = 28.dp
internal val ConfigRowMinHeight = 48.dp
internal val ConfigDividerInset = 16.dp

/** TopAppBar 标题：图标 + App 名称 */
@Composable
internal fun ConfigAppBarTitle(
    appInfo: AppInfo,
    cs: ColorScheme = MaterialTheme.colorScheme
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AppIcon(drawable = appInfo.icon, modifier = Modifier.size(28.dp))
        Text(
            text = appInfo.appName,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurface,
            fontSize = 17.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 完整监控配置表单：顶部生效摘要 + 三能力轻配置。
 * 多时段编辑通过 [onManagePeriods] 下沉到子页。
 *
 * @param bothOffHint 三项都关时的底部提示（添加页用）；编辑页关最后一项会先走「停止监控」确认，通常不传。
 * @param showDesignPhilosophy 首次添加：简短说明后再进开关。
 * @param todayUsedLabel 可选轻状态，如「12 分钟」
 * @param onManagePeriods 进入时段管理子页
 * @param primaryOnly 非空时只展示该主能力的配置。
 * @param allowMasterToggle 单能力页是否显示主开关；添加流为 false（视为已开），编辑流为 true。
 */
@Composable
internal fun MonitorConfigForm(
    requireIntent: Boolean,
    onRequireIntentChange: (Boolean) -> Unit,
    timeLimitOn: Boolean,
    onTimeLimitChange: (Boolean) -> Unit,
    dailyLimit: Int,
    onDailyLimitChange: (Int) -> Unit,
    sessionLimitOn: Boolean = true,
    onSessionLimitChange: (Boolean) -> Unit = {},
    intentQualityCheckOn: Boolean = false,
    onIntentQualityCheckChange: (Boolean) -> Unit = {},
    intentBlockKeywords: List<String> = emptyList(),
    onIntentBlockKeywordsChange: (List<String>) -> Unit = {},
    periodLockOn: Boolean = false,
    onPeriodLockChange: (Boolean) -> Unit = {},
    periodWindows: List<com.life.mindfulnessapp.domain.model.PeriodWindow> =
        listOf(com.life.mindfulnessapp.domain.model.PeriodWindow.DEFAULT_SLEEP),
    onPeriodWindowsChange: (List<com.life.mindfulnessapp.domain.model.PeriodWindow>) -> Unit = {},
    periodCommitment: String = "",
    onPeriodCommitmentChange: (String) -> Unit = {},
    onManagePeriods: () -> Unit = {},
    todayUsedLabel: String? = null,
    todayGlance: AppTodayGlance? = null,
    onTodayGlanceClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    dailyHint: String? = null,
    bothOffHint: String? = "请至少开启一项能力",
    showDesignPhilosophy: Boolean = false,
    primaryOnly: CapabilityKind? = null,
    allowMasterToggle: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    val bothOff = !requireIntent && !timeLimitOn && !periodLockOn
    val windowsOk = periodWindows.isNotEmpty()
    val policy = com.life.mindfulnessapp.domain.model.PeriodLockPolicy
    val showIntent = primaryOnly == null || primaryOnly == CapabilityKind.IntentGate
    val showTime = primaryOnly == null || primaryOnly == CapabilityKind.TimeLock
    val showPeriod = primaryOnly == null || primaryOnly == CapabilityKind.PeriodLock
    val activeNote = remember(periodWindows) {
        policy.activeWindow(periodWindows)?.message?.trim().orEmpty().ifBlank {
            periodWindows.firstOrNull { it.message.isNotBlank() }?.message?.trim().orEmpty()
        }
    }
    val effect = remember(
        requireIntent, sessionLimitOn, intentQualityCheckOn, intentBlockKeywords,
        timeLimitOn, dailyLimit, periodLockOn, periodWindows, todayUsedLabel
    ) {
        buildMonitorEffectSummary(
            intentOn = requireIntent,
            sessionLimitOn = sessionLimitOn,
            intentQualityCheckOn = intentQualityCheckOn,
            intentBlockKeywordCount = intentBlockKeywords.size,
            timeOn = timeLimitOn,
            dailyLimitMinutes = dailyLimit,
            periodOn = periodLockOn,
            windows = periodWindows,
            commitment = activeNote,
            todayUsedLabel = todayUsedLabel
        )
    }

    var pendingMasterOff by remember { mutableStateOf(false) }

    fun requestPeriodMaster(on: Boolean) {
        when {
            !on && periodLockOn -> pendingMasterOff = true
            else -> {
                onPeriodLockChange(on)
                if (on && periodWindows.isEmpty()) {
                    onPeriodWindowsChange(
                        listOf(com.life.mindfulnessapp.domain.model.PeriodWindow.defaultSleep())
                    )
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ConfigPagePadding)
            .padding(top = 12.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(ConfigGroupGap)
    ) {
        if (showDesignPhilosophy && primaryOnly == null) {
            FirstMonitorTeachBlock(cs = cs)
        }

        if (todayGlance != null && onTodayGlanceClick != null && primaryOnly == null) {
            TodayGlanceCard(
                glance = todayGlance.copy(requireIntentOnOpen = requireIntent),
                onClick = onTodayGlanceClick,
                cs = cs
            )
        }

        if (primaryOnly == null) {
            MonitorEffectSummaryCard(summary = effect, cs = cs)
        }

        if (showIntent) {
            SettingsSection(
                header = "意图门",
                headerIcon = {
                    CapabilityMark(
                        kind = CapabilityKind.IntentGate,
                        form = CapabilityForm.Standard,
                        size = 15.dp
                    )
                },
                footer = when {
                    primaryOnly == CapabilityKind.IntentGate && sessionLimitOn ->
                        "打开前写下意图，并承诺本次多久。"
                    primaryOnly == CapabilityKind.IntentGate ->
                        "打开前写下意图，再进入。"
                    requireIntent && sessionLimitOn ->
                        "打开前写下意图，并承诺本次多久。"
                    requireIntent ->
                        "打开前写下意图，再进入。"
                    else -> "已关闭。打开时不再经过这扇门。"
                }
            ) {
                val showIntentMaster =
                    primaryOnly != CapabilityKind.IntentGate || allowMasterToggle
                if (showIntentMaster) {
                    SettingsSwitchRow(
                        title = "打开前写下意图",
                        checked = requireIntent,
                        onCheckedChange = { on ->
                            onRequireIntentChange(on)
                            if (on) {
                                onSessionLimitChange(true)
                            } else {
                                onSessionLimitChange(false)
                                onIntentQualityCheckChange(false)
                            }
                        }
                    )
                }
                AnimatedVisibility(
                    visible = requireIntent ||
                        (primaryOnly == CapabilityKind.IntentGate && !allowMasterToggle),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        if (showIntentMaster) {
                            SettingsInsetDivider()
                        }
                        SettingsSwitchRow(
                            title = "进门先定时长",
                            checked = sessionLimitOn,
                            onCheckedChange = onSessionLimitChange,
                            subtitle = "写下意图后再选多久；到点结束，可续时一次",
                            hint = if (sessionLimitOn) "推荐" else null
                        )
                    }
                }
            }
        }

        if (showTime) {
            SettingsSection(
                header = "时长锁",
                headerIcon = {
                    CapabilityMark(
                        kind = CapabilityKind.TimeLock,
                        form = CapabilityForm.Standard,
                        size = 15.dp
                    )
                },
                footer = when {
                    primaryOnly == CapabilityKind.TimeLock && dailyHint != null ->
                        "每日上限$dailyHint。"
                    primaryOnly == CapabilityKind.TimeLock ->
                        "用着时边缘会有计时胶囊。到点后阻断进入。"
                    timeLimitOn && dailyHint != null -> "每日上限$dailyHint。"
                    timeLimitOn -> "用着时边缘会有计时胶囊。"
                    else -> "已关闭每日上限。"
                }
            ) {
                val showTimeMaster =
                    primaryOnly != CapabilityKind.TimeLock || allowMasterToggle
                if (showTimeMaster) {
                    SettingsSwitchRow(
                        title = "限制每日时长",
                        checked = timeLimitOn,
                        onCheckedChange = onTimeLimitChange
                    )
                }
                AnimatedVisibility(
                    visible = timeLimitOn ||
                        (primaryOnly == CapabilityKind.TimeLock && !allowMasterToggle),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        if (showTimeMaster) {
                            SettingsInsetDivider()
                        }
                        DurationLimitSettings(
                            dailyMinutes = dailyLimit,
                            onDailyMinutesChange = onDailyLimitChange,
                            dailyHint = dailyHint
                        )
                    }
                }
            }
        }

        if (showPeriod) {
            SettingsSection(
                header = "时段锁",
                headerIcon = {
                    CapabilityMark(
                        kind = CapabilityKind.PeriodLock,
                        form = CapabilityForm.Standard,
                        size = 15.dp
                    )
                },
                footer = when {
                    primaryOnly == CapabilityKind.PeriodLock && !windowsOk ->
                        "请先添加锁定时段。"
                    primaryOnly == CapabilityKind.PeriodLock && allowMasterToggle && !periodLockOn ->
                        "开启后，锁定时段将硬挡进入。"
                    primaryOnly == CapabilityKind.PeriodLock ->
                        "锁定时段硬挡进入。多段互不重叠。"
                    periodLockOn && !windowsOk ->
                        "请先添加锁定时段。"
                    periodLockOn ->
                        "锁定时段硬挡进入。多段互不重叠；生效中关闭需过门槛。"
                    else -> "已关闭。"
                }
            ) {
                val showPeriodMaster =
                    primaryOnly != CapabilityKind.PeriodLock || allowMasterToggle
                if (showPeriodMaster) {
                    SettingsSwitchRow(
                        title = "锁定指定时段",
                        checked = periodLockOn,
                        onCheckedChange = { requestPeriodMaster(it) },
                        hint = if (periodLockOn) "更硬" else null
                    )
                }
                AnimatedVisibility(
                    visible = periodLockOn ||
                        (primaryOnly == CapabilityKind.PeriodLock && !allowMasterToggle),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        if (showPeriodMaster) {
                            SettingsInsetDivider()
                        }
                        SettingsValueRow(
                            title = "管理时段",
                            valueText = com.life.mindfulnessapp.domain.model.PeriodWindowsCodec
                                .summaryLabel(periodWindows),
                            onClick = onManagePeriods,
                            subtitle = run {
                                val active = policy.activeWindow(periodWindows)
                                when {
                                    active != null -> "此刻生效中 · ${active.label()}"
                                    activeNote.isNotBlank() -> {
                                        if (activeNote.length <= 20) activeNote
                                        else activeNote.take(20) + "…"
                                    }
                                    else -> "添加或编辑锁定时段"
                                }
                            }
                        )
                    }
                }
            }
        }

        if (primaryOnly == null && bothOff && !bothOffHint.isNullOrBlank()) {
            Text(
                text = bothOffHint,
                fontSize = 13.sp,
                color = Color(0xFFE74C3C).copy(alpha = 0.85f),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }

    if (pendingMasterOff) {
        val active = policy.activeWindow(periodWindows)
        PeriodLockDisableGateDialog(
            commitment = active?.message.orEmpty(),
            windowLabel = active?.label(),
            title = "关闭时段锁？",
            confirmLabel = "确认关闭",
            onConfirm = {
                onPeriodLockChange(false)
                pendingMasterOff = false
            },
            onDismiss = { pendingMasterOff = false }
        )
    }
}

@Composable
internal fun TodayGlanceCard(
    glance: AppTodayGlance,
    onClick: () -> Unit,
    cs: ColorScheme = MaterialTheme.colorScheme
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ConfigGroupShape)
            .background(cs.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "今日",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = cs.onSurface.copy(alpha = 0.40f),
                letterSpacing = 0.4.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "记录",
                fontSize = 12.sp,
                color = LogoGreen,
                fontWeight = FontWeight.Medium
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = LogoGreen.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (glance.requireIntentOnOpen) {
                TodayGlanceMetric(
                    label = "守住",
                    value = glance.dismissCount.toString()
                )
                TodayGlanceMetric(
                    label = "带着意图进入",
                    value = glance.mindfulEnterCount.toString()
                )
            }
            TodayGlanceMetric(
                label = "时长",
                value = formatGlanceDuration(glance.totalSeconds),
                emphasize = !glance.requireIntentOnOpen
            )
        }
    }
}

@Composable
private fun TodayGlanceMetric(
    label: String,
    value: String,
    emphasize: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.40f)
        )
        Text(
            text = value,
            fontSize = if (emphasize) 22.sp else 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            letterSpacing = (-0.3).sp
        )
    }
}

private fun formatGlanceDuration(seconds: Long): String {
    if (seconds <= 0L) return "0分"
    val totalMin = seconds / 60L
    return when {
        totalMin < 60L -> "${totalMin}分"
        else -> {
            val h = totalMin / 60L
            val m = totalMin % 60L
            if (m == 0L) "${h}小时" else "${h}小时${m}分"
        }
    }
}

@Composable
private fun MonitorEffectSummaryCard(
    summary: MonitorEffectSummary,
    cs: ColorScheme
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ConfigGroupShape)
            .background(cs.surface)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "下次打开时",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = cs.onSurface.copy(alpha = 0.40f),
            letterSpacing = 0.4.sp
        )
        Text(
            text = summary.headline,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurface,
            lineHeight = 24.sp
        )
        if (!summary.detail.isNullOrBlank()) {
            Text(
                text = summary.detail,
                fontSize = 13.sp,
                color = cs.onSurface.copy(alpha = 0.48f),
                lineHeight = 18.sp
            )
        }
        if (!summary.status.isNullOrBlank()) {
            Text(
                text = summary.status,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = LogoGreen.copy(alpha = 0.90f)
            )
        }
    }
}

/**
 * 首次添加监控：简短说明三件工具（详细预览见上方摘要）。
 */
@Composable
private fun FirstMonitorTeachBlock(cs: ColorScheme) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "三件可叠加的工具。默认开启意图门与时长锁；时段锁按需加开。上方摘要会告诉你下次打开时会发生什么。",
            fontSize = 14.sp,
            color = cs.onSurface.copy(alpha = 0.55f),
            lineHeight = 21.sp
        )
    }
}

@Composable
private fun CapabilityExpectRow(
    kind: CapabilityKind,
    title: String,
    expect: String,
    cs: ColorScheme
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CapabilityMark(
            kind = kind,
            form = CapabilityForm.Standard,
            size = 18.dp
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface.copy(alpha = 0.82f)
            )
            Text(
                text = expect,
                fontSize = 13.sp,
                color = cs.onSurface.copy(alpha = 0.45f),
                lineHeight = 18.sp
            )
        }
    }
}

/** 关闭最后一项能力时：确认停止监控 */
@Composable
internal fun StopMonitoringConfirmDialog(
    appName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        shape = RoundedCornerShape(14.dp),
        title = {
            Text(
                text = "停止监控「$appName」？",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface
            )
        },
        text = {
            Text(
                text = "意图门、时长锁与时段锁都关闭后，将不再监控此应用。之后打开将不再拦截，历史记录仍会保留。",
                fontSize = 14.sp,
                color = cs.onSurface.copy(alpha = 0.55f),
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFE74C3C))
            ) {
                Text("停止监控", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = cs.onSurface.copy(alpha = 0.45f)
                )
            ) {
                Text("取消")
            }
        }
    )
}

/** 配置页有未保存改动时离开确认 */
@Composable
internal fun DiscardChangesDialog(
    onDiscard: () -> Unit,
    onStay: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onStay,
        containerColor = cs.surface,
        shape = RoundedCornerShape(14.dp),
        title = {
            Text(
                text = "放弃更改？",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface
            )
        },
        text = {
            Text(
                text = "当前修改尚未保存。",
                fontSize = 14.sp,
                color = cs.onSurface.copy(alpha = 0.55f),
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            TextButton(
                onClick = onDiscard,
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFE74C3C))
            ) {
                Text("放弃", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onStay,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = cs.onSurface.copy(alpha = 0.45f)
                )
            ) {
                Text("继续编辑")
            }
        }
    )
}

/** 一组：可选 header / footer + 圆角列表容器 */
@Composable
internal fun SettingsSection(
    modifier: Modifier = Modifier,
    header: String? = null,
    headerIcon: (@Composable () -> Unit)? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth()) {
        if (!header.isNullOrBlank()) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                headerIcon?.invoke()
                Text(
                    text = header.uppercase(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = cs.onSurface.copy(alpha = 0.42f),
                    letterSpacing = 0.3.sp
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ConfigGroupShape)
                .background(cs.surface),
            content = content
        )
        if (!footer.isNullOrBlank()) {
            Text(
                text = footer,
                fontSize = 13.sp,
                color = cs.onSurface.copy(alpha = 0.42f),
                lineHeight = 18.sp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
            )
        }
    }
}

@Composable
internal fun SettingsInsetDivider(
    insetStart: Dp = ConfigDividerInset
) {
    val cs = MaterialTheme.colorScheme
    HorizontalDivider(
        modifier = Modifier.padding(start = insetStart),
        thickness = 0.5.dp,
        color = cs.outline.copy(alpha = 0.28f)
    )
}

/**
 * 意图限制关键词编辑：已选词可点 × 移除；示例可一键加入；底部输入添加。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IntentBlockKeywordsEditor(
    keywords: List<String>,
    onKeywordsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    var draft by remember { mutableStateOf("") }
    val sanitized = remember(keywords) { IntentBlockKeywords.sanitizeList(keywords) }
    val suggestionLeft = remember(sanitized) {
        IntentBlockKeywords.SUGGESTIONS.filter { s ->
            sanitized.none { it.equals(s, ignoreCase = true) }
        }
    }

    fun tryAdd(raw: String) {
        val n = IntentBlockKeywords.normalize(raw)
        if (n.isEmpty()) return
        onKeywordsChange(IntentBlockKeywords.sanitizeList(sanitized + n))
        draft = ""
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "限制关键词",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = cs.onSurface.copy(alpha = 0.72f)
        )
        Text(
            text = "意图里出现这些词就不能进入。点词可移除。",
            fontSize = 12.sp,
            color = cs.onSurface.copy(alpha = 0.42f),
            lineHeight = 16.sp
        )

        if (sanitized.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sanitized.forEach { kw ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(LogoGreen.copy(alpha = 0.12f))
                            .clickable {
                                onKeywordsChange(sanitized.filterNot { it == kw })
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = kw,
                            fontSize = 13.sp,
                            color = LogoGreen.copy(alpha = 0.95f)
                        )
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "移除",
                            tint = LogoGreen.copy(alpha = 0.65f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        } else {
            Text(
                text = "还没有限制词",
                fontSize = 13.sp,
                color = cs.onSurface.copy(alpha = 0.38f)
            )
        }

        if (suggestionLeft.isNotEmpty() && sanitized.size < IntentBlockKeywords.MAX_KEYWORDS) {
            Text(
                text = "常用示例",
                fontSize = 12.sp,
                color = cs.onSurface.copy(alpha = 0.42f)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestionLeft.forEach { s ->
                    Text(
                        text = "+ $s",
                        fontSize = 13.sp,
                        color = cs.onSurface.copy(alpha = 0.72f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(cs.onSurface.copy(alpha = 0.05f))
                            .clickable {
                                onKeywordsChange(IntentBlockKeywords.sanitizeList(sanitized + s))
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        if (sanitized.size < IntentBlockKeywords.MAX_KEYWORDS) {
            OutlinedTextField(
                value = draft,
                onValueChange = {
                    if (it.length <= IntentBlockKeywords.MAX_KEYWORD_LENGTH) draft = it
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = {
                    Text("自定义词，回车添加", fontSize = 14.sp)
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        tryAdd(draft)
                        focusManager.clearFocus()
                    }
                ),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LogoGreen.copy(alpha = 0.45f),
                    unfocusedBorderColor = cs.outline.copy(alpha = 0.35f),
                    cursorColor = LogoGreen
                )
            )
            Text(
                text = "最多 ${IntentBlockKeywords.MAX_KEYWORDS} 个 · 每个不超过 ${IntentBlockKeywords.MAX_KEYWORD_LENGTH} 字",
                fontSize = 11.sp,
                color = cs.onSurface.copy(alpha = 0.36f)
            )
        }
    }
}

@Composable
internal fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    subtitle: String? = null,
    /** 标题旁轻量提示（如「门槛更高」），品牌绿 */
    hint: String? = null
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ConfigRowMinHeight)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Normal,
                    color = if (enabled) cs.onSurface else cs.onSurface.copy(alpha = 0.35f)
                )
                if (!hint.isNullOrBlank()) {
                    Text(
                        text = hint,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LogoGreen.copy(alpha = if (enabled) 0.88f else 0.40f),
                        letterSpacing = 0.3.sp
                    )
                }
            }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = cs.onSurface.copy(alpha = if (enabled) 0.40f else 0.28f),
                    lineHeight = 17.sp
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = { if (enabled) onCheckedChange(it) },
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = LogoGreen,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = cs.outline.copy(alpha = 0.35f),
                uncheckedThumbColor = Color.White,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
internal fun SettingsValueRow(
    title: String,
    valueText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    subtitle: String? = null,
    enabled: Boolean = true
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ConfigRowMinHeight)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    color = if (enabled) cs.onSurface else cs.onSurface.copy(alpha = 0.35f)
                )
                if (!hint.isNullOrBlank()) {
                    Text(
                        text = hint,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = LogoGreen.copy(alpha = 0.80f)
                    )
                }
            }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = cs.onSurface.copy(alpha = 0.40f)
                )
            }
        }
        Text(
            text = valueText,
            fontSize = 17.sp,
            color = if (enabled) cs.onSurface.copy(alpha = 0.45f)
            else cs.onSurface.copy(alpha = 0.28f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = cs.onSurface.copy(alpha = 0.22f)
        )
    }
}


/** 每日上限预设（分钟） */
internal val DAILY_LIMIT_PRESETS = listOf(15, 30, 60, 90, 120, 180)

internal const val DAILY_LIMIT_MIN = 5
internal const val DAILY_LIMIT_MAX = 480

private data class PeriodPreset(
    val title: String,
    val startMinute: Int,
    val endMinute: Int,
    val daysMask: Int
)

private val PERIOD_PRESETS = listOf(
    PeriodPreset("全天", 0, 0, com.life.mindfulnessapp.domain.model.PeriodDays.EVERY_DAY),
    PeriodPreset("睡前", 22 * 60, 7 * 60, com.life.mindfulnessapp.domain.model.PeriodDays.EVERY_DAY),
    PeriodPreset("深夜", 0, 6 * 60, com.life.mindfulnessapp.domain.model.PeriodDays.EVERY_DAY),
    PeriodPreset("工作日", 9 * 60, 18 * 60, com.life.mindfulnessapp.domain.model.PeriodDays.WEEKDAYS)
)

private data class PeriodEditorState(
    val window: com.life.mindfulnessapp.domain.model.PeriodWindow,
    val isNew: Boolean
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PeriodWindowSettings(
    windows: List<com.life.mindfulnessapp.domain.model.PeriodWindow>,
    onWindowsChange: (List<com.life.mindfulnessapp.domain.model.PeriodWindow>) -> Unit,
    onRequestDisableWindow: (id: String) -> Unit,
    onRequestDeleteWindow: (id: String) -> Unit,
    lockIsLive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val policy = com.life.mindfulnessapp.domain.model.PeriodLockPolicy
    var pendingEditor by remember { mutableStateOf<PeriodEditorState?>(null) }
    var pendingEditBreath by remember {
        mutableStateOf<com.life.mindfulnessapp.domain.model.PeriodWindow?>(null)
    }
    var addHint by remember { mutableStateOf<String?>(null) }
    val overlapInList = remember(windows) { policy.hasInternalOverlap(windows) }

    val editLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val pending = pendingEditor
        pendingEditor = null
        if (result.resultCode != android.app.Activity.RESULT_OK || pending == null) return@rememberLauncherForActivityResult
        val updated = com.life.mindfulnessapp.PeriodWindowEditActivity.parseResult(result.data)
            ?: return@rememberLauncherForActivityResult
        val original = windows.find { it.id == pending.window.id }
        // 已武装且改的是开启中的时段：一律过门槛（窗外改掉也是旁路）
        val needsBreath = lockIsLive &&
            !pending.isNew &&
            original != null &&
            original.enabled
        if (needsBreath) {
            pendingEditBreath = updated
        } else if (pending.isNew) {
            onWindowsChange(windows + updated)
        } else {
            onWindowsChange(windows.map { if (it.id == updated.id) updated else it })
        }
    }

    fun openEditor(state: PeriodEditorState) {
        pendingEditor = state
        editLauncher.launch(
            com.life.mindfulnessapp.PeriodWindowEditActivity.createIntent(
                context = context,
                window = state.window,
                isNew = state.isNew,
                existing = windows
            )
        )
    }

    fun applyPreset(preset: PeriodPreset) {
        val candidate = periodFromPreset(preset)
        val conflict = policy.conflictWith(candidate, windows)
        if (conflict != null) {
            addHint = periodConflictLabel(conflict)
            return
        }
        addHint = null
        onWindowsChange(windows + candidate)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        windows.forEachIndexed { index, window ->
            if (index > 0) SettingsInsetDivider()
            val activeNow = lockIsLive && window.enabled && policy.wouldBeActiveNow(window)
            PeriodWindowRow(
                window = window,
                activeNow = activeNow,
                onToggle = { enabled ->
                    if (!enabled && lockIsLive && window.enabled) {
                        onRequestDisableWindow(window.id)
                    } else {
                        onWindowsChange(
                            windows.map {
                                if (it.id == window.id) it.copy(enabled = enabled) else it
                            }
                        )
                    }
                },
                onEdit = { openEditor(PeriodEditorState(window, isNew = false)) },
                onDelete = { onRequestDeleteWindow(window.id) }
            )
        }
        if (windows.isNotEmpty()) SettingsInsetDivider()
        PeriodAddChipRow(
            windows = windows,
            onPreset = { applyPreset(it) },
            onCustom = {
                addHint = null
                openEditor(
                    PeriodEditorState(
                        window = com.life.mindfulnessapp.domain.model.PeriodWindow.defaultSleep(),
                        isNew = true
                    )
                )
            }
        )
        val hint = addHint ?: if (overlapInList) "已有时段互相重叠，请删掉多余的一段。" else null
        if (hint != null) {
            Text(
                text = hint,
                fontSize = 12.sp,
                color = Color(0xFFE74C3C).copy(alpha = 0.85f),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            )
        }
    }

    pendingEditBreath?.let { updated ->
        val original = windows.find { it.id == updated.id }
        PeriodLockDisableGateDialog(
            commitment = original?.message.orEmpty().ifBlank { updated.message },
            windowLabel = original?.label() ?: updated.label(),
            title = "改动锁定中的时段？",
            confirmLabel = "确认改动",
            onConfirm = {
                onWindowsChange(windows.map { if (it.id == updated.id) updated else it })
                pendingEditBreath = null
            },
            onDismiss = { pendingEditBreath = null }
        )
    }
}

private fun periodConflictLabel(
    conflict: com.life.mindfulnessapp.domain.model.PeriodWindowConflict
): String = when (conflict) {
    com.life.mindfulnessapp.domain.model.PeriodWindowConflict.Duplicate -> "此时段已添加"
    com.life.mindfulnessapp.domain.model.PeriodWindowConflict.Overlap -> "与已有时段重叠"
}

private fun periodFromPreset(
    preset: PeriodPreset
): com.life.mindfulnessapp.domain.model.PeriodWindow =
    com.life.mindfulnessapp.domain.model.PeriodWindow(
        startMinute = preset.startMinute,
        endMinute = preset.endMinute,
        daysMask = preset.daysMask,
        enabled = true
    )

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeriodAddChipRow(
    windows: List<com.life.mindfulnessapp.domain.model.PeriodWindow>,
    onPreset: (PeriodPreset) -> Unit,
    onCustom: () -> Unit
) {
    val policy = com.life.mindfulnessapp.domain.model.PeriodLockPolicy
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "添加时段",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PERIOD_PRESETS.forEach { preset ->
                val blocked = policy.conflictWith(periodFromPreset(preset), windows) != null
                PeriodAddChip(
                    label = preset.title,
                    emphasized = false,
                    available = !blocked,
                    onClick = { onPreset(preset) }
                )
            }
            PeriodAddChip(
                label = "自定义",
                emphasized = true,
                available = true,
                onClick = onCustom
            )
        }
    }
}

@Composable
private fun PeriodAddChip(
    label: String,
    emphasized: Boolean,
    available: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    val fill = when {
        !available -> cs.onSurface.copy(alpha = 0.03f)
        emphasized -> LogoGreen.copy(alpha = 0.14f)
        else -> cs.onSurface.copy(alpha = 0.04f)
    }
    val stroke = when {
        !available -> cs.outline.copy(alpha = 0.12f)
        emphasized -> LogoGreen.copy(alpha = 0.45f)
        else -> cs.outline.copy(alpha = 0.22f)
    }
    val textColor = when {
        !available -> cs.onSurface.copy(alpha = 0.28f)
        emphasized -> LogoGreen
        else -> cs.onSurface.copy(alpha = 0.78f)
    }
    Box(
        modifier = Modifier
            .clip(shape)
            .background(fill)
            .border(width = 1.dp, color = stroke, shape = shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (emphasized && available) FontWeight.SemiBold else FontWeight.Medium,
            color = textColor
        )
    }
}

@Composable
private fun PeriodWindowRow(
    window: com.life.mindfulnessapp.domain.model.PeriodWindow,
    activeNow: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val note = window.message.trim()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onEdit),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = window.label(),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (window.enabled) cs.onSurface else cs.onSurface.copy(alpha = 0.38f)
                )
                if (activeNow) {
                    Text(
                        text = "生效中",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LogoGreen
                    )
                }
            }
            Text(
                text = buildString {
                    append(window.daysLabel())
                    window.rangeHint()?.let { append(" · "); append(it) }
                },
                fontSize = 13.sp,
                color = cs.onSurface.copy(alpha = 0.40f)
            )
            if (note.isNotEmpty()) {
                Text(
                    text = if (note.length <= 28) note else note.take(28) + "…",
                    fontSize = 12.sp,
                    color = LogoGreen.copy(alpha = 0.80f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = "删除",
            fontSize = 13.sp,
            color = Color(0xFFE74C3C).copy(alpha = 0.75f),
            modifier = Modifier
                .clickable(onClick = onDelete)
                .padding(horizontal = 4.dp, vertical = 6.dp)
        )
        Switch(
            checked = window.enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedTrackColor = LogoGreen,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = cs.outline.copy(alpha = 0.35f),
                uncheckedThumbColor = Color.White,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
fun PeriodDaysPickerContent(
    selectedMask: Int,
    onMaskChange: (Int) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val days = com.life.mindfulnessapp.domain.model.PeriodDays
    val mask = (selectedMask and days.EVERY_DAY).takeIf { it != 0 } ?: days.EVERY_DAY

    val quickOptions = listOf(
        "每天" to days.EVERY_DAY,
        "工作日" to days.WEEKDAYS,
        "周末" to days.WEEKENDS
    )
    val dayBits = listOf(
        "一" to days.MON,
        "二" to days.TUE,
        "三" to days.WED,
        "四" to days.THU,
        "五" to days.FRI,
        "六" to days.SAT,
        "日" to days.SUN
    )

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickOptions.forEach { (label, bits) ->
                val selected = mask == bits
                val shape = RoundedCornerShape(20.dp)
                Box(
                    modifier = Modifier
                        .clip(shape)
                        .background(
                            if (selected) LogoGreen.copy(alpha = 0.16f)
                            else cs.onSurface.copy(alpha = 0.04f)
                        )
                        .border(
                            width = 1.dp,
                            color = if (selected) LogoGreen.copy(alpha = 0.45f)
                            else cs.outline.copy(alpha = 0.22f),
                            shape = shape
                        )
                        .clickable { onMaskChange(bits) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) LogoGreen else cs.onSurface.copy(alpha = 0.70f)
                    )
                }
            }
        }
        Text(
            text = "或点选具体星期",
            fontSize = 13.sp,
            color = cs.onSurface.copy(alpha = 0.45f)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            dayBits.forEach { (label, bit) ->
                val on = (mask and bit) != 0
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (on) LogoGreen.copy(alpha = 0.18f)
                            else cs.outline.copy(alpha = 0.12f)
                        )
                        .clickable {
                            val toggled = (mask xor bit) and days.EVERY_DAY
                            onMaskChange(if (toggled == 0) bit else toggled)
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 14.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (on) LogoGreen else cs.onSurface.copy(alpha = 0.55f)
                    )
                }
            }
        }
    }
}

/**
 * 时长边界：直接键入分钟，确认后写入。
 */
@Composable
internal fun DurationLimitSettings(
    dailyMinutes: Int,
    onDailyMinutesChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    dailyHint: String? = null
) {
    var editing by remember { mutableStateOf(false) }
    var draftText by remember(dailyMinutes) { mutableStateOf(dailyMinutes.toString()) }
    var pendingConfirm by remember { mutableStateOf<Int?>(null) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val cs = MaterialTheme.colorScheme
    val parsed = draftText.toIntOrNull()
    val valid = parsed != null && parsed in DAILY_LIMIT_MIN..DAILY_LIMIT_MAX

    LaunchedEffect(editing) {
        if (editing) {
            draftText = dailyMinutes.toString()
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    fun askConfirm() {
        val value = parsed?.coerceIn(DAILY_LIMIT_MIN, DAILY_LIMIT_MAX) ?: return
        if (value == dailyMinutes) {
            editing = false
            keyboard?.hide()
            return
        }
        pendingConfirm = value
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (!editing) {
            SettingsValueRow(
                title = "每天最多多久",
                valueText = formatLimitMinutes(dailyMinutes),
                onClick = { editing = true },
                hint = dailyHint
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(
                    "每天最多多久",
                    fontSize = 13.sp,
                    color = cs.onSurface.copy(alpha = 0.45f)
                )
                dailyHint?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        fontSize = 11.sp,
                        color = cs.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Bottom
                ) {
                    BasicTextField(
                        value = draftText,
                        onValueChange = { raw ->
                            draftText = raw.filter { it.isDigit() }.take(3)
                        },
                        modifier = Modifier
                            .width(120.dp)
                            .focusRequester(focusRequester),
                        textStyle = TextStyle(
                            color = cs.onSurface,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { askConfirm() }),
                        cursorBrush = SolidColor(LogoGreen)
                    )
                    Text(
                        "分",
                        fontSize = 15.sp,
                        color = cs.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                    )
                }
                Text(
                    "$DAILY_LIMIT_MIN–$DAILY_LIMIT_MAX 分",
                    fontSize = 11.sp,
                    color = cs.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    textAlign = TextAlign.Center
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "取消",
                        fontSize = 14.sp,
                        color = cs.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.clickable {
                            editing = false
                            keyboard?.hide()
                        }
                    )
                    Text(
                        "下一步",
                        fontSize = 15.sp,
                        color = if (valid) LogoGreen else cs.onSurface.copy(alpha = 0.35f),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable(enabled = valid, onClick = { askConfirm() })
                    )
                }
            }
        }
    }

    pendingConfirm?.let { value ->
        AlertDialog(
            onDismissRequest = { pendingConfirm = null },
            title = { Text("日限改为 ${formatLimitMinutes(value)}？") },
            text = { Text("今天起按这个数执行。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDailyMinutesChange(value)
                        pendingConfirm = null
                        editing = false
                        keyboard?.hide()
                    }
                ) {
                    Text("确认", color = LogoGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirm = null }) {
                    Text("再改改", color = cs.onSurface.copy(alpha = 0.55f))
                }
            }
        )
    }
}

internal fun formatLimitMinutes(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}小时${m}分"
        h > 0 -> "${h}小时"
        else -> "${m}分钟"
    }
}
