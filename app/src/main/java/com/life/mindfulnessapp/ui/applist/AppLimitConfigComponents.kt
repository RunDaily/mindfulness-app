package com.life.mindfulnessapp.ui.applist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    showDesignPhilosophy: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    val bothOff = !requireIntent && !timeLimitOn && !periodLockOn
    val commitmentOk = periodCommitment.trim().length >=
        com.life.mindfulnessapp.domain.model.PeriodLockPolicy.COMMITMENT_MIN_CHARS
    val windowsOk = periodWindows.isNotEmpty()
    val policy = com.life.mindfulnessapp.domain.model.PeriodLockPolicy
    val effect = remember(
        requireIntent, sessionLimitOn, intentQualityCheckOn, intentBlockKeywords,
        timeLimitOn, dailyLimit, periodLockOn, periodWindows, periodCommitment, todayUsedLabel
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
            commitment = periodCommitment,
            todayUsedLabel = todayUsedLabel
        )
    }

    var pendingMasterOff by remember { mutableStateOf(false) }
    var showCommitmentEnable by remember { mutableStateOf(false) }

    fun requestPeriodMaster(on: Boolean) {
        when {
            on && !commitmentOk -> showCommitmentEnable = true
            !on && policy.hasActiveEnabledWindow(periodWindows) -> pendingMasterOff = true
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
        if (showDesignPhilosophy) {
            FirstMonitorTeachBlock(cs = cs)
        }

        if (todayGlance != null && onTodayGlanceClick != null) {
            TodayGlanceCard(
                glance = todayGlance.copy(requireIntentOnOpen = requireIntent),
                onClick = onTodayGlanceClick,
                cs = cs
            )
        }

        MonitorEffectSummaryCard(summary = effect, cs = cs)

        SettingsSection(
            header = "意图门",
            headerIcon = {
                CapabilityMark(
                    kind = CapabilityKind.IntentGate,
                    form = CapabilityForm.Standard,
                    tint = LogoGreen.copy(alpha = 0.75f),
                    size = 15.dp
                )
            },
            footer = when {
                requireIntent && intentQualityCheckOn && intentBlockKeywords.isEmpty() ->
                    "已开启检验，请先添加限制关键词，否则不会拦截。"
                requireIntent && intentQualityCheckOn && sessionLimitOn ->
                    "打开前写意图；命中你设的限制词不能进，并承诺本次多久。"
                requireIntent && intentQualityCheckOn ->
                    "打开前写意图；命中你设的限制词不能进入。"
                requireIntent && sessionLimitOn ->
                    "打开前写下意图，并承诺本次多久。"
                requireIntent ->
                    "打开前写下意图，再进入。"
                else -> "已关闭。打开时跳过写意图。"
            }
        ) {
            SettingsSwitchRow(
                title = "打开前写下意图",
                checked = requireIntent,
                onCheckedChange = { on ->
                    onRequireIntentChange(on)
                    if (!on) {
                        onSessionLimitChange(false)
                        onIntentQualityCheckChange(false)
                    }
                }
            )
            AnimatedVisibility(
                visible = requireIntent,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    SettingsInsetDivider()
                    SettingsSwitchRow(
                        title = "用关键词限制意图",
                        checked = intentQualityCheckOn,
                        onCheckedChange = { on ->
                            onIntentQualityCheckChange(on)
                            if (on && intentBlockKeywords.isEmpty()) {
                                onIntentBlockKeywordsChange(IntentBlockKeywords.SUGGESTIONS)
                            }
                        },
                        subtitle = "意图含你设的词则不能进入",
                        hint = if (intentQualityCheckOn) "自定" else null
                    )
                    AnimatedVisibility(
                        visible = intentQualityCheckOn,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            SettingsInsetDivider()
                            IntentBlockKeywordsEditor(
                                keywords = intentBlockKeywords,
                                onKeywordsChange = onIntentBlockKeywordsChange
                            )
                        }
                    }
                    SettingsInsetDivider()
                    SettingsSwitchRow(
                        title = "进入时承诺本次多久",
                        checked = sessionLimitOn,
                        onCheckedChange = onSessionLimitChange,
                        subtitle = "到点结束；每次进入时当场确认",
                        hint = if (sessionLimitOn) "门槛更高" else null
                    )
                }
            }
        }

        SettingsSection(
            header = "时长锁",
            headerIcon = {
                CapabilityMark(
                    kind = CapabilityKind.TimeLock,
                    form = CapabilityForm.Standard,
                    tint = LogoGreen.copy(alpha = 0.75f),
                    size = 15.dp
                )
            },
            footer = when {
                timeLimitOn && dailyHint != null -> "每日上限$dailyHint。"
                timeLimitOn -> "用着时边缘会有计时胶囊。"
                else -> "已关闭每日上限。"
            }
        ) {
            SettingsSwitchRow(
                title = "限制每日时长",
                checked = timeLimitOn,
                onCheckedChange = onTimeLimitChange
            )
            AnimatedVisibility(
                visible = timeLimitOn,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    SettingsInsetDivider()
                    DurationLimitSettings(
                        dailyMinutes = dailyLimit,
                        onDailyMinutesChange = onDailyLimitChange,
                        dailyHint = dailyHint
                    )
                }
            }
        }

        SettingsSection(
            header = "时段锁",
            headerIcon = {
                CapabilityMark(
                    kind = CapabilityKind.PeriodLock,
                    form = CapabilityForm.Standard,
                    tint = LogoGreen.copy(alpha = 0.75f),
                    size = 15.dp
                )
            },
            footer = when {
                periodLockOn && !windowsOk ->
                    "请先添加锁定时段。"
                periodLockOn ->
                    "锁定时段硬挡进入。多段在「管理时段」中叠加配置；生效中关闭需过门槛。"
                else -> "已关闭。"
            }
        ) {
            SettingsSwitchRow(
                title = "锁定指定时段",
                checked = periodLockOn,
                onCheckedChange = { requestPeriodMaster(it) },
                hint = if (periodLockOn) "更硬" else null
            )
            AnimatedVisibility(
                visible = periodLockOn,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    SettingsInsetDivider()
                    SettingsValueRow(
                        title = "管理时段",
                        valueText = com.life.mindfulnessapp.domain.model.PeriodWindowsCodec
                            .summaryLabel(periodWindows),
                        onClick = onManagePeriods,
                        subtitle = run {
                            val active = policy.activeWindow(periodWindows)
                            when {
                                active != null -> "此刻生效中 · ${active.label()}"
                                periodCommitment.isNotBlank() -> "守护「${periodCommitment.trim()}」"
                                else -> "添加或编辑锁定时段"
                            }
                        }
                    )
                }
            }
        }

        if (bothOff && !bothOffHint.isNullOrBlank()) {
            Text(
                text = bothOffHint,
                fontSize = 13.sp,
                color = Color(0xFFE74C3C).copy(alpha = 0.85f),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }

    if (pendingMasterOff) {
        PeriodLockDisableGateDialog(
            commitment = periodCommitment,
            windowLabel = policy.activeWindow(periodWindows)?.label(),
            title = "关闭时段锁？",
            confirmLabel = "确认关闭",
            onConfirm = {
                onPeriodLockChange(false)
                pendingMasterOff = false
            },
            onDismiss = { pendingMasterOff = false }
        )
    }

    if (showCommitmentEnable) {
        PeriodCommitmentEnableDialog(
            initial = periodCommitment,
            onConfirm = { text ->
                onPeriodCommitmentChange(text)
                onPeriodLockChange(true)
                if (periodWindows.isEmpty()) {
                    onPeriodWindowsChange(
                        listOf(com.life.mindfulnessapp.domain.model.PeriodWindow.defaultSleep())
                    )
                }
                showCommitmentEnable = false
            },
            onDismiss = { showCommitmentEnable = false }
        )
    }
}

@Composable
private fun TodayGlanceCard(
    glance: AppTodayGlance,
    onClick: () -> Unit,
    cs: ColorScheme
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
 * 开启时段锁时的承诺仪式：写下守护什么，再真正打开能力。
 */
@Composable
internal fun PeriodCommitmentEnableDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val max = com.life.mindfulnessapp.domain.model.PeriodLockPolicy.COMMITMENT_MAX_CHARS
    val min = com.life.mindfulnessapp.domain.model.PeriodLockPolicy.COMMITMENT_MIN_CHARS
    var text by remember { mutableStateOf(initial) }
    val ok = text.trim().length >= min

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        shape = RoundedCornerShape(14.dp),
        title = {
            Text(
                "开启时段锁",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "先写下这段时间你在守护什么。锁定中它会出现在拦截页；生效中关闭时也会回显。",
                    fontSize = 14.sp,
                    color = cs.onSurface.copy(alpha = 0.55f),
                    lineHeight = 20.sp
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(max) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "例如：睡眠 / 深度工作 / 陪家人",
                            fontSize = 15.sp,
                            color = cs.onSurface.copy(alpha = 0.35f)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LogoGreen,
                        unfocusedBorderColor = cs.outline.copy(alpha = 0.35f)
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (ok) onConfirm(text.trim()) },
                enabled = ok,
                colors = ButtonDefaults.textButtonColors(contentColor = LogoGreen)
            ) { Text("开启", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = cs.onSurface.copy(alpha = 0.45f)
                )
            ) { Text("取消") }
        }
    )
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
            tint = LogoGreen.copy(alpha = 0.8f),
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
    PeriodPreset("睡前 · 22:00–07:00", 22 * 60, 7 * 60, com.life.mindfulnessapp.domain.model.PeriodDays.EVERY_DAY),
    PeriodPreset("深夜 · 00:00–06:00", 0, 6 * 60, com.life.mindfulnessapp.domain.model.PeriodDays.EVERY_DAY),
    PeriodPreset("工作日白天 · 09:00–18:00", 9 * 60, 18 * 60, com.life.mindfulnessapp.domain.model.PeriodDays.WEEKDAYS)
)

@Composable
internal fun PeriodWindowSettings(
    windows: List<com.life.mindfulnessapp.domain.model.PeriodWindow>,
    onWindowsChange: (List<com.life.mindfulnessapp.domain.model.PeriodWindow>) -> Unit,
    onRequestDisableWindow: (id: String) -> Unit,
    onRequestDeleteWindow: (id: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val policy = com.life.mindfulnessapp.domain.model.PeriodLockPolicy
    var editingId by remember { mutableStateOf<String?>(null) }
    var showAddPreset by remember { mutableStateOf(false) }
    val editing = windows.find { it.id == editingId }

    Column(modifier = modifier.fillMaxWidth()) {
        windows.forEachIndexed { index, window ->
            if (index > 0) SettingsInsetDivider()
            val activeNow = window.enabled && policy.wouldBeActiveNow(window)
            PeriodWindowRow(
                window = window,
                activeNow = activeNow,
                onToggle = { enabled ->
                    if (!enabled && policy.wouldBeActiveNow(window)) {
                        onRequestDisableWindow(window.id)
                    } else {
                        onWindowsChange(
                            windows.map {
                                if (it.id == window.id) it.copy(enabled = enabled) else it
                            }
                        )
                    }
                },
                onEdit = { editingId = window.id },
                onDelete = { onRequestDeleteWindow(window.id) }
            )
        }
        SettingsInsetDivider()
        SettingsValueRow(
            title = "添加时段",
            valueText = "",
            onClick = { showAddPreset = true },
            subtitle = "可叠加多段，例如睡前 + 工作专注"
        )
    }

    if (editing != null) {
        PeriodWindowEditorDialog(
            window = editing,
            onConfirm = { updated ->
                onWindowsChange(windows.map { if (it.id == updated.id) updated else it })
                editingId = null
            },
            onDismiss = { editingId = null }
        )
    }

    if (showAddPreset) {
        val cs = MaterialTheme.colorScheme
        AlertDialog(
            onDismissRequest = { showAddPreset = false },
            containerColor = cs.surface,
            shape = RoundedCornerShape(14.dp),
            title = {
                Text(
                    "添加时段",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface
                )
            },
            text = {
                Column {
                    PERIOD_PRESETS.forEachIndexed { index, preset ->
                        if (index > 0) SettingsInsetDivider(insetStart = 0.dp)
                        Text(
                            text = preset.title,
                            fontSize = 17.sp,
                            color = cs.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onWindowsChange(
                                        windows + com.life.mindfulnessapp.domain.model.PeriodWindow(
                                            startMinute = preset.startMinute,
                                            endMinute = preset.endMinute,
                                            daysMask = preset.daysMask,
                                            enabled = true
                                        )
                                    )
                                    showAddPreset = false
                                }
                                .padding(horizontal = 4.dp, vertical = 14.dp)
                        )
                    }
                    SettingsInsetDivider(insetStart = 0.dp)
                    Text(
                        text = "自定义…",
                        fontSize = 17.sp,
                        color = LogoGreen,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val custom = com.life.mindfulnessapp.domain.model.PeriodWindow.defaultSleep()
                                onWindowsChange(windows + custom)
                                showAddPreset = false
                                editingId = custom.id
                            }
                            .padding(horizontal = 4.dp, vertical = 14.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showAddPreset = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = LogoGreen)
                ) { Text("取消", fontWeight = FontWeight.SemiBold) }
            }
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
                text = window.daysLabel() + if (window.crossesMidnight) " · 跨午夜" else "",
                fontSize = 13.sp,
                color = cs.onSurface.copy(alpha = 0.40f)
            )
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
private fun PeriodWindowEditorDialog(
    window: com.life.mindfulnessapp.domain.model.PeriodWindow,
    onConfirm: (com.life.mindfulnessapp.domain.model.PeriodWindow) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember(window.id) { mutableStateOf(window) }
    var showStart by remember { mutableStateOf(false) }
    var showEnd by remember { mutableStateOf(false) }
    var showDays by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        shape = RoundedCornerShape(14.dp),
        title = {
            Text(
                "编辑时段",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface
            )
        },
        text = {
            Column {
                SettingsValueRow(
                    title = "开始",
                    valueText = com.life.mindfulnessapp.domain.model.PeriodWindow.formatHm(draft.startMinute),
                    onClick = { showStart = true }
                )
                SettingsInsetDivider(insetStart = 0.dp)
                SettingsValueRow(
                    title = "结束",
                    valueText = com.life.mindfulnessapp.domain.model.PeriodWindow.formatHm(draft.endMinute),
                    onClick = { showEnd = true },
                    subtitle = if (draft.crossesMidnight) "跨午夜，至次日该时刻" else null
                )
                SettingsInsetDivider(insetStart = 0.dp)
                SettingsValueRow(
                    title = "重复",
                    valueText = draft.daysLabel(),
                    onClick = { showDays = true }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(draft) },
                colors = ButtonDefaults.textButtonColors(contentColor = LogoGreen)
            ) { Text("完成", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = cs.onSurface.copy(alpha = 0.45f)
                )
            ) { Text("取消") }
        }
    )

    if (showStart) {
        MinuteOfDayPickerDialog(
            title = "开始时间",
            initialMinuteOfDay = draft.startMinute,
            onConfirm = {
                draft = draft.copy(startMinute = it)
                showStart = false
            },
            onDismiss = { showStart = false }
        )
    }
    if (showEnd) {
        MinuteOfDayPickerDialog(
            title = "结束时间",
            initialMinuteOfDay = draft.endMinute,
            onConfirm = {
                draft = draft.copy(endMinute = it)
                showEnd = false
            },
            onDismiss = { showEnd = false }
        )
    }
    if (showDays) {
        PeriodDaysPickerDialog(
            selectedMask = draft.daysMask,
            onConfirm = {
                draft = draft.copy(daysMask = it)
                showDays = false
            },
            onDismiss = { showDays = false }
        )
    }
}

@Composable
internal fun MinuteOfDayPickerDialog(
    title: String,
    initialMinuteOfDay: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    val clamped = initialMinuteOfDay.coerceIn(0, 1439)
    var hourInput by remember { mutableStateOf((clamped / 60).toString()) }
    var minuteInput by remember { mutableStateOf((clamped % 60).toString().padStart(2, '0')) }
    val hour = hourInput.toIntOrNull()
    val minute = minuteInput.toIntOrNull()
    val isValid = hour != null && hour in 0..23 && minute != null && minute in 0..59
    val total = if (isValid) hour!! * 60 + minute!! else -1

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        shape = RoundedCornerShape(14.dp),
        title = {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "24 小时制 · 00:00–23:59",
                    fontSize = 13.sp,
                    color = cs.onSurface.copy(alpha = 0.45f)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = hourInput,
                        onValueChange = { v ->
                            if (v.length <= 2 && (v.isEmpty() || v.all { it.isDigit() })) hourInput = v
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("时", fontSize = 12.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LogoGreen,
                            unfocusedBorderColor = cs.outline.copy(alpha = 0.35f)
                        )
                    )
                    Text(":", fontSize = 22.sp, color = cs.onSurface.copy(alpha = 0.3f))
                    OutlinedTextField(
                        value = minuteInput,
                        onValueChange = { v ->
                            if (v.length <= 2 &&
                                (v.isEmpty() || (v.all { it.isDigit() } && (v.toIntOrNull() ?: 0) <= 59))
                            ) {
                                minuteInput = v
                            }
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("分", fontSize = 12.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            if (isValid) onConfirm(total)
                        }),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LogoGreen,
                            unfocusedBorderColor = cs.outline.copy(alpha = 0.35f)
                        )
                    )
                }
                if (isValid) {
                    Text(
                        text = com.life.mindfulnessapp.domain.model.PeriodWindow.formatHm(total),
                        fontSize = 13.sp,
                        color = LogoGreen
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (isValid) onConfirm(total) },
                enabled = isValid,
                colors = ButtonDefaults.textButtonColors(contentColor = LogoGreen)
            ) { Text("确定", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = cs.onSurface.copy(alpha = 0.45f)
                )
            ) { Text("取消") }
        }
    )
}

@Composable
internal fun PeriodDaysPickerDialog(
    selectedMask: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val days = com.life.mindfulnessapp.domain.model.PeriodDays
    var mask by remember(selectedMask) {
        mutableStateOf(
            (selectedMask and days.EVERY_DAY).takeIf { it != 0 } ?: days.EVERY_DAY
        )
    }

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

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        shape = RoundedCornerShape(14.dp),
        title = {
            Text(
                "重复",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    quickOptions.forEachIndexed { index, (label, bits) ->
                        if (index > 0) SettingsInsetDivider(insetStart = 0.dp)
                        val selected = mask == bits
                        Text(
                            text = label,
                            fontSize = 17.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) LogoGreen else cs.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { mask = bits }
                                .padding(horizontal = 4.dp, vertical = 12.dp)
                        )
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
                                    mask = if (toggled == 0) bit else toggled
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
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(mask and days.EVERY_DAY) },
                enabled = mask and days.EVERY_DAY != 0,
                colors = ButtonDefaults.textButtonColors(contentColor = LogoGreen)
            ) { Text("确定", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = cs.onSurface.copy(alpha = 0.45f)
                )
            ) { Text("取消") }
        }
    )
}

@Composable
internal fun PeriodCommitmentField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val max = com.life.mindfulnessapp.domain.model.PeriodLockPolicy.COMMITMENT_MAX_CHARS
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "这段时间，我守护的是",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = cs.onSurface.copy(alpha = 0.75f)
        )
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it.take(max)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    "例如：睡眠 / 深度工作 / 陪家人",
                    fontSize = 15.sp,
                    color = cs.onSurface.copy(alpha = 0.35f)
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LogoGreen,
                unfocusedBorderColor = cs.outline.copy(alpha = 0.35f)
            )
        )
        Text(
            text = "生效中关闭时段时会回显这句话。这是承诺，不是罚款。",
            fontSize = 12.sp,
            color = cs.onSurface.copy(alpha = 0.40f),
            lineHeight = 16.sp
        )
    }
}

/**
 * 时长边界行：嵌在时长锁分组内（无独立底色）。仅每日上限。
 */
@Composable
internal fun DurationLimitSettings(
    dailyMinutes: Int,
    onDailyMinutesChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    dailyHint: String? = null
) {
    var showDailyPicker by remember { mutableStateOf(false) }
    var showCustomDaily by remember { mutableStateOf(false) }

    fun applyDaily(minutes: Int) {
        onDailyMinutesChange(minutes.coerceIn(DAILY_LIMIT_MIN, DAILY_LIMIT_MAX))
    }

    Column(modifier = modifier.fillMaxWidth()) {
        SettingsValueRow(
            title = "每天最多多久",
            valueText = formatLimitMinutes(dailyMinutes),
            onClick = { showDailyPicker = true },
            hint = dailyHint
        )
    }

    if (showDailyPicker) {
        LimitPresetPickerDialog(
            title = "每天最多多久",
            options = DAILY_LIMIT_PRESETS.map { it to formatLimitMinutes(it) },
            selectedMinutes = dailyMinutes,
            onSelect = {
                applyDaily(it)
                showDailyPicker = false
            },
            onCustom = {
                showDailyPicker = false
                showCustomDaily = true
            },
            onDismiss = { showDailyPicker = false }
        )
    }
    if (showCustomDaily) {
        CustomTimeInputDialog(
            title = "自定义每日时长",
            initialMinutes = dailyMinutes,
            minMinutes = DAILY_LIMIT_MIN,
            maxMinutes = DAILY_LIMIT_MAX,
            onConfirm = {
                applyDaily(it)
                showCustomDaily = false
            },
            onDismiss = { showCustomDaily = false }
        )
    }
}

@Composable
internal fun LimitPresetPickerDialog(
    title: String,
    options: List<Pair<Int, String>>,
    selectedMinutes: Int,
    onSelect: (Int) -> Unit,
    onCustom: () -> Unit,
    onDismiss: () -> Unit,
    formatSelected: (Int) -> String = { formatLimitMinutes(it) }
) {
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        shape = RoundedCornerShape(14.dp),
        title = {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
        },
        text = {
            Column {
                options.forEachIndexed { index, (minutes, label) ->
                    if (index > 0) SettingsInsetDivider(insetStart = 0.dp)
                    val selected = minutes == selectedMinutes
                    Text(
                        text = label,
                        fontSize = 17.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) LogoGreen else cs.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(minutes) }
                            .padding(horizontal = 4.dp, vertical = 14.dp)
                    )
                }
                SettingsInsetDivider(insetStart = 0.dp)
                val isCustom = options.none { it.first == selectedMinutes }
                Text(
                    text = if (isCustom) "自定义 · ${formatSelected(selectedMinutes)}"
                    else "自定义…",
                    fontSize = 17.sp,
                    fontWeight = if (isCustom) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isCustom) LogoGreen else cs.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onCustom)
                        .padding(horizontal = 4.dp, vertical = 14.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = LogoGreen)
            ) { Text("取消", fontWeight = FontWeight.SemiBold) }
        }
    )
}

@Composable
internal fun CustomTimeInputDialog(
    title: String,
    initialMinutes: Int,
    minMinutes: Int,
    maxMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    var hourInput by remember { mutableStateOf((initialMinutes / 60).toString()) }
    var minuteInput by remember { mutableStateOf((initialMinutes % 60).toString()) }
    val totalMinutes = (hourInput.toIntOrNull() ?: 0) * 60 + (minuteInput.toIntOrNull() ?: 0)
    val isValid = totalMinutes in minMinutes..maxMinutes

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        shape = RoundedCornerShape(14.dp),
        title = {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "${formatLimitMinutes(minMinutes)} ~ ${formatLimitMinutes(maxMinutes)}",
                    fontSize = 13.sp,
                    color = cs.onSurface.copy(alpha = 0.45f)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = hourInput,
                        onValueChange = { v ->
                            if (v.length <= 2 && (v.isEmpty() || v.all { it.isDigit() })) hourInput = v
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("小时", fontSize = 12.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LogoGreen,
                            unfocusedBorderColor = cs.outline.copy(alpha = 0.35f)
                        )
                    )
                    Text(":", fontSize = 22.sp, color = cs.onSurface.copy(alpha = 0.3f))
                    OutlinedTextField(
                        value = minuteInput,
                        onValueChange = { v ->
                            if (v.length <= 2 &&
                                (v.isEmpty() || (v.all { it.isDigit() } && (v.toIntOrNull() ?: 0) <= 59))
                            ) {
                                minuteInput = v
                            }
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("分钟", fontSize = 12.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            if (isValid) onConfirm(totalMinutes)
                        }),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LogoGreen,
                            unfocusedBorderColor = cs.outline.copy(alpha = 0.35f)
                        )
                    )
                }
                if (totalMinutes > 0) {
                    Text(
                        text = if (isValid) "= ${formatLimitMinutes(totalMinutes)}"
                        else "请输入有效时长",
                        fontSize = 13.sp,
                        color = if (isValid) LogoGreen else Color(0xFFE74C3C).copy(alpha = 0.8f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (isValid) onConfirm(totalMinutes) },
                enabled = isValid,
                colors = ButtonDefaults.textButtonColors(contentColor = LogoGreen)
            ) { Text("确定", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = cs.onSurface.copy(alpha = 0.45f)
                )
            ) { Text("取消") }
        }
    )
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
