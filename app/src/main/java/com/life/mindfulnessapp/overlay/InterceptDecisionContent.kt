package com.life.mindfulnessapp.overlay

import android.graphics.drawable.Drawable
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.life.mindfulnessapp.domain.model.IntentBlockKeywords
import com.life.mindfulnessapp.domain.model.IntentKind
import com.life.mindfulnessapp.domain.model.InterceptEnterDecision
import com.life.mindfulnessapp.domain.model.PendingInterrupt
import com.life.mindfulnessapp.domain.model.RecentPurpose
import com.life.mindfulnessapp.domain.model.SessionLimitPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 进入确认的短冷静（毫秒） */
private const val ENTER_COOLDOWN_MS = 900L

/** 离开肯定态展示时长（毫秒），随后交给外层退场 / 仪式 */
private const val LEAVE_AFFIRM_MS = 560L

/**
 * 拦截页决策流：
 * 用量镜像 → 名言停顿 → 意图提问 → 离开为主 / 进入为次（吸底）
 */
@Composable
fun InterceptDecisionContent(
    themeConfig: InterceptThemeConfig,
    dailyLimitMinutes: Int,
    weeklyLimitMinutes: Int,
    todayUsedSeconds: Long,
    weekUsedSeconds: Long,
    includesPreMonitorUsage: Boolean,
    sessionLimitEnabled: Boolean,
    defaultSessionLimitMinutes: Int,
    intentQualityCheckEnabled: Boolean = false,
    intentBlockKeywords: List<String> = emptyList(),
    pendingInterrupt: PendingInterrupt?,
    isExiting: Boolean,
    isDismissing: Boolean,
    impulseCount: Int = 1,
    enterCount: Int = 0,
    dismissCount: Int = 0,
    quote: String = "",
    quoteAuthor: String = "",
    recentPurposes: List<RecentPurpose> = emptyList(),
    modifier: Modifier = Modifier,
    onEnter: (InterceptEnterDecision) -> Unit,
    onResumePrevious: (() -> Unit)?,
    onDismiss: () -> Unit,
    onOpenOwnApp: (() -> Unit)?
) {
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val ownAppIcon = remember {
        try {
            context.packageManager.getApplicationIcon(context.packageName)
        } catch (_: Exception) {
            null
        }
    }

    val dailyRemaining = SessionLimitPolicy.dailyRemainingMinutes(dailyLimitMinutes, todayUsedSeconds)
    val maxSelectable = SessionLimitPolicy.maxSelectableMinutes(dailyRemaining)
    val timeLimitActive = dailyLimitMinutes > 0 || weeklyLimitMinutes > 0
    val todayUsedMinutes = todayUsedSeconds / 60f
    val dailyProgress = if (dailyLimitMinutes > 0) {
        (todayUsedMinutes / dailyLimitMinutes).coerceAtMost(1f)
    } else 0f
    val weeklyProgress = if (weeklyLimitMinutes > 0) {
        (weekUsedSeconds / 60f / weeklyLimitMinutes).coerceAtMost(1f)
    } else 0f

    var intentText by remember { mutableStateOf("") }
    var minutesDigits by remember { mutableStateOf("") }
    var customMinutes by remember { mutableStateOf(false) }
    var inputFocused by remember { mutableStateOf(false) }
    var leaveAffirming by remember { mutableStateOf(false) }
    var enterPending by remember { mutableStateOf(false) }

    val matchedBlockKeyword = remember(intentText, intentQualityCheckEnabled, intentBlockKeywords) {
        if (!intentQualityCheckEnabled) return@remember null
        val trimmed = intentText.trim()
        if (trimmed.isEmpty()) null
        else IntentBlockKeywords.findMatch(trimmed, intentBlockKeywords)
    }
    val purposeOk = matchedBlockKeyword == null
    val purposeTip = matchedBlockKeyword?.let { IntentBlockKeywords.tipFor(it) }

    val leaveEnabled = !isExiting && !isDismissing && !leaveAffirming
    val hasPurpose = intentText.trim().isNotEmpty()
    val parsedMinutes = minutesDigits.toIntOrNull()
    val minutesValid = parsedMinutes != null &&
        parsedMinutes in SessionLimitPolicy.MIN_SESSION_MINUTES..maxSelectable
    val canEnter = leaveEnabled && hasPurpose && purposeOk && (!sessionLimitEnabled || minutesValid)

    val presets = remember(defaultSessionLimitMinutes, maxSelectable) {
        sessionMinutePresets(defaultSessionLimitMinutes, maxSelectable)
    }

    // 写完意图后预选默认档；清空意图时复位
    LaunchedEffect(hasPurpose, sessionLimitEnabled, maxSelectable, defaultSessionLimitMinutes) {
        if (!hasPurpose) {
            minutesDigits = ""
            customMinutes = false
            enterPending = false
            return@LaunchedEffect
        }
        if (sessionLimitEnabled && minutesDigits.isEmpty()) {
            val preset = defaultSessionLimitMinutes
                .coerceIn(SessionLimitPolicy.MIN_SESSION_MINUTES, maxSelectable)
            minutesDigits = preset.toString()
            customMinutes = presets.none { it == preset }
        }
    }

    LaunchedEffect(canEnter) {
        if (!canEnter) enterPending = false
    }

    LaunchedEffect(enterPending) {
        if (!enterPending) return@LaunchedEffect
        delay(ENTER_COOLDOWN_MS)
        if (!enterPending) return@LaunchedEffect
        enterPending = false
        if (!canEnter) return@LaunchedEffect
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onEnter(
            InterceptEnterDecision(
                purpose = intentText.trim(),
                intentKind = IntentKind.PURPOSEFUL,
                sessionLimitMinutes = if (sessionLimitEnabled) parsedMinutes!! else 0
            )
        )
    }

    fun requestLeave() {
        if (!leaveEnabled) return
        leaveAffirming = true
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        scope.launch {
            delay(LEAVE_AFFIRM_MS)
            onDismiss()
        }
    }

    fun requestEnterOrCancel() {
        if (!canEnter) return
        if (enterPending) {
            enterPending = false
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            return
        }
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        enterPending = true
    }

    val leaveAffirmLabel = when {
        dismissCount + 1 <= 1 -> "守住了"
        else -> "今日已守住 ${dismissCount + 1} 次"
    }

    val density = LocalDensity.current
    var viewportPx by remember { mutableIntStateOf(0) }
    var bodyPx by remember { mutableIntStateOf(0) }
    // 预留给动作区的高度：离开 + 进入 + 心锚/冲动
    val actionReservePx = with(density) { 168.dp.roundToPx() }
    val contentOverflows = viewportPx > 0 && bodyPx + actionReservePx > viewportPx
    // 短内容：动作贴在提问区下方；聚焦 / 自定义键盘 / 溢出：吸底
    val dockActions = inputFocused || customMinutes || contentOverflows

    val actionBar: @Composable () -> Unit = {
        DecisionActionBar(
            themeConfig = themeConfig,
            leaveEnabled = leaveEnabled,
            leaveAffirming = leaveAffirming,
            leaveAffirmLabel = leaveAffirmLabel,
            canEnter = canEnter,
            enterPending = enterPending,
            sessionLimitEnabled = sessionLimitEnabled,
            parsedMinutes = parsedMinutes,
            impulseCount = impulseCount,
            enterCount = enterCount,
            dismissCount = dismissCount,
            ownAppIcon = ownAppIcon,
            onLeave = { requestLeave() },
            onEnterOrCancel = { requestEnterOrCancel() },
            onOpenOwnApp = onOpenOwnApp
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { viewportPx = it.height }
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { bodyPx = it.height },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (timeLimitActive) {
                    UsageMirror(
                        themeConfig = themeConfig,
                        todayUsedSeconds = todayUsedSeconds,
                        weekUsedSeconds = weekUsedSeconds,
                        dailyLimitMinutes = dailyLimitMinutes,
                        weeklyLimitMinutes = weeklyLimitMinutes,
                        dailyProgress = dailyProgress,
                        weeklyProgress = weeklyProgress,
                        includesPreMonitorUsage = includesPreMonitorUsage
                    )
                } else if (todayUsedSeconds > 0L) {
                    Text(
                        text = "今日已用 ${formatRemain(todayUsedSeconds)}",
                        fontSize = 13.sp,
                        color = themeConfig.textTertiary,
                        textAlign = TextAlign.Center
                    )
                    if (includesPreMonitorUsage) {
                        Text(
                            text = "含加入前今日使用",
                            fontSize = 11.sp,
                            color = themeConfig.textTertiary.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                val showQuote = quote.isNotBlank() && !inputFocused && !hasPurpose
                AnimatedVisibility(
                    visible = showQuote,
                    enter = fadeIn(tween(280)) + expandVertically(tween(280)),
                    exit = fadeOut(tween(180)) + shrinkVertically(tween(180))
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(if (timeLimitActive) 18.dp else 12.dp))
                        QuoteBreath(
                            quote = quote,
                            author = quoteAuthor,
                            themeConfig = themeConfig
                        )
                    }
                }

                Spacer(modifier = Modifier.height(if (showQuote) 22.dp else 18.dp))

                Text(
                    text = "此刻打开它，是为了什么？",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = themeConfig.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (pendingInterrupt != null && onResumePrevious != null) {
                    SoftResumeLink(
                        interrupt = pendingInterrupt,
                        themeConfig = themeConfig,
                        enabled = leaveEnabled,
                        onResume = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onResumePrevious()
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                OutlinedTextField(
                    value = intentText,
                    onValueChange = { if (it.length <= 40) intentText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { inputFocused = it.isFocused },
                    enabled = leaveEnabled,
                    placeholder = {
                        Text("写下这一次要做的事", fontSize = 15.sp, color = themeConfig.textTertiary)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = themeConfig.accentColor.copy(alpha = 0.55f),
                        unfocusedBorderColor = themeConfig.dividerColor.copy(alpha = 0.55f),
                        focusedTextColor = themeConfig.textPrimary,
                        unfocusedTextColor = themeConfig.textPrimary,
                        cursorColor = themeConfig.accentColor,
                        focusedContainerColor = themeConfig.surfaceColor,
                        unfocusedContainerColor = themeConfig.surfaceColor
                    )
                )

                AnimatedVisibility(
                    visible = purposeTip != null,
                    enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(120))
                ) {
                    Text(
                        text = purposeTip.orEmpty(),
                        fontSize = 12.sp,
                        color = themeConfig.textSecondary,
                        lineHeight = 17.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }

                if (recentPurposes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    RecentPurposeChips(
                        purposes = recentPurposes,
                        selectedText = intentText,
                        themeConfig = themeConfig,
                        enabled = leaveEnabled,
                        onSelect = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            intentText = it
                        }
                    )
                }

                AnimatedVisibility(
                    visible = sessionLimitEnabled && hasPurpose,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "这次多久？",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = themeConfig.textPrimary.copy(alpha = 0.88f),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "最长 $maxSelectable 分 · 到点结束",
                            fontSize = 11.sp,
                            color = themeConfig.textTertiary,
                            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                        )
                        MinutePresetRow(
                            presets = presets,
                            selectedMinutes = parsedMinutes,
                            customSelected = customMinutes,
                            themeConfig = themeConfig,
                            enabled = leaveEnabled,
                            onPreset = { minutes ->
                                customMinutes = false
                                minutesDigits = minutes.toString()
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            },
                            onCustom = {
                                customMinutes = true
                                if (parsedMinutes != null && parsedMinutes in presets) {
                                    minutesDigits = ""
                                }
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        )
                        AnimatedVisibility(
                            visible = customMinutes,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(10.dp))
                                SessionMinutesKeypad(
                                    themeConfig = themeConfig,
                                    digits = minutesDigits,
                                    maxMinutes = maxSelectable,
                                    enabled = leaveEnabled,
                                    onDigitsChange = { minutesDigits = it },
                                    onDigitHaptic = {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (!dockActions) {
                Spacer(modifier = Modifier.height(22.dp))
                actionBar()
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                // 吸底时给滚动内容留出动作区余量，避免最后一项被挡住
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        if (dockActions) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(themeConfig.bgColor)
                    .padding(top = 6.dp, bottom = 10.dp)
            ) {
                actionBar()
            }
        }
    }
}

@Composable
private fun DecisionActionBar(
    themeConfig: InterceptThemeConfig,
    leaveEnabled: Boolean,
    leaveAffirming: Boolean,
    leaveAffirmLabel: String,
    canEnter: Boolean,
    enterPending: Boolean,
    sessionLimitEnabled: Boolean,
    parsedMinutes: Int?,
    impulseCount: Int,
    enterCount: Int,
    dismissCount: Int,
    ownAppIcon: Drawable?,
    onLeave: () -> Unit,
    onEnterOrCancel: () -> Unit,
    onOpenOwnApp: (() -> Unit)?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onLeave,
            enabled = leaveEnabled || leaveAffirming,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = themeConfig.accentColor,
                disabledContainerColor = themeConfig.accentColor,
                contentColor = themeConfig.accentForeground,
                disabledContentColor = themeConfig.accentForeground
            ),
            shape = RoundedCornerShape(14.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
        ) {
            Text(
                text = if (leaveAffirming) leaveAffirmLabel else "先不进去了",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        AnimatedVisibility(
            visible = canEnter || enterPending,
            enter = fadeIn(tween(200)) + expandVertically(),
            exit = fadeOut(tween(150)) + shrinkVertically()
        ) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onEnterOrCancel,
                    enabled = canEnter || enterPending,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = themeConfig.textSecondary,
                        disabledContentColor = themeConfig.textTertiary
                    )
                ) {
                    val enterLabel = when {
                        enterPending -> "点按可取消 · 即将进入"
                        sessionLimitEnabled && parsedMinutes != null ->
                            "仍要进入 · ${parsedMinutes} 分钟"
                        else -> "仍要进入"
                    }
                    Text(
                        text = enterLabel,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        if (onOpenOwnApp != null) {
            Spacer(modifier = Modifier.height(2.dp))
            OpenOwnAppLink(
                enabled = leaveEnabled,
                ownAppIcon = ownAppIcon,
                themeConfig = themeConfig,
                onClick = onOpenOwnApp
            )
        }

        if (!leaveAffirming && impulseCount >= 2) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "想打开 $impulseCount · 进去 $enterCount · 守住 $dismissCount",
                fontSize = 11.sp,
                color = themeConfig.textTertiary.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** 构建分钟快捷档：默认值优先入列，再补 5 / 15 / 30，受当日剩余上限约束 */
internal fun sessionMinutePresets(defaultMinutes: Int, maxMinutes: Int): List<Int> {
    val safeMax = maxMinutes.coerceAtLeast(SessionLimitPolicy.MIN_SESSION_MINUTES)
    val preferred = listOf(
        defaultMinutes.coerceIn(SessionLimitPolicy.MIN_SESSION_MINUTES, safeMax),
        5,
        15,
        30
    )
    return preferred
        .map { it.coerceIn(SessionLimitPolicy.MIN_SESSION_MINUTES, safeMax) }
        .distinct()
        .sorted()
}

@Composable
private fun MinutePresetRow(
    presets: List<Int>,
    selectedMinutes: Int?,
    customSelected: Boolean,
    themeConfig: InterceptThemeConfig,
    enabled: Boolean,
    onPreset: (Int) -> Unit,
    onCustom: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        presets.forEach { minutes ->
            val selected = !customSelected && selectedMinutes == minutes
            MinuteChip(
                label = "${minutes}分",
                selected = selected,
                themeConfig = themeConfig,
                enabled = enabled,
                onClick = { onPreset(minutes) }
            )
        }
        MinuteChip(
            label = "其他",
            selected = customSelected,
            themeConfig = themeConfig,
            enabled = enabled,
            onClick = onCustom
        )
    }
}

@Composable
private fun MinuteChip(
    label: String,
    selected: Boolean,
    themeConfig: InterceptThemeConfig,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                if (selected) themeConfig.accentColor.copy(alpha = 0.18f)
                else themeConfig.surfaceColor.copy(alpha = 0.85f)
            )
            .border(
                width = 1.dp,
                color = if (selected) themeConfig.accentColor.copy(alpha = 0.55f)
                else themeConfig.dividerColor.copy(alpha = 0.7f),
                shape = shape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) themeConfig.accentColor
            else themeConfig.textSecondary.copy(alpha = if (enabled) 0.95f else 0.4f)
        )
    }
}

@Composable
private fun RecentPurposeChips(
    purposes: List<RecentPurpose>,
    selectedText: String,
    themeConfig: InterceptThemeConfig,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    val current = selectedText.trim()
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "最近意图",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = themeConfig.textTertiary.copy(alpha = 0.85f),
            letterSpacing = 0.3.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            purposes.forEach { item ->
                val selected = current == item.purpose
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) themeConfig.accentColor.copy(alpha = 0.14f)
                            else themeConfig.surfaceColor.copy(alpha = 0.9f)
                        )
                        .border(
                            width = 1.dp,
                            color = if (selected) themeConfig.accentColor.copy(alpha = 0.45f)
                            else themeConfig.dividerColor,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable(enabled = enabled) { onSelect(item.purpose) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = item.purpose,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                        color = if (selected) themeConfig.accentColor else themeConfig.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** 续接：一行淡文字链，不抢主决策 */
@Composable
private fun SoftResumeLink(
    interrupt: PendingInterrupt,
    themeConfig: InterceptThemeConfig,
    enabled: Boolean,
    onResume: () -> Unit
) {
    val purpose = interrupt.purpose?.trim()?.ifEmpty { null }
    val summary = purpose ?: interrupt.reasonShortLabel
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onResume)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = "刚刚那次 · ${interrupt.timeAgoLabel()}",
            fontSize = 12.sp,
            color = themeConfig.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 180.dp)
        )
        Text(
            text = " · ",
            fontSize = 12.sp,
            color = themeConfig.textTertiary
        )
        Text(
            text = summary,
            fontSize = 12.sp,
            color = themeConfig.textSecondary.copy(alpha = 0.9f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 100.dp)
        )
        Text(
            text = "  继续",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = themeConfig.accentColor.copy(alpha = if (enabled) 0.95f else 0.4f)
        )
    }
}

@Composable
private fun UsageMirror(
    themeConfig: InterceptThemeConfig,
    todayUsedSeconds: Long,
    weekUsedSeconds: Long,
    dailyLimitMinutes: Int,
    weeklyLimitMinutes: Int,
    dailyProgress: Float,
    weeklyProgress: Float,
    includesPreMonitorUsage: Boolean
) {
    val useDaily = dailyLimitMinutes > 0
    val limitSeconds = if (useDaily) dailyLimitMinutes * 60L else weeklyLimitMinutes * 60L
    val usedSeconds = if (useDaily) todayUsedSeconds else weekUsedSeconds
    val remainingSeconds = (limitSeconds - usedSeconds).coerceAtLeast(0L)
    val progress = if (useDaily) dailyProgress else weeklyProgress
    val over = remainingSeconds <= 0L && limitSeconds > 0L
    val accent = if (over) themeConfig.limitAccentColor else themeConfig.accentColor
    val limitLabel = formatMinutesLabel(if (useDaily) dailyLimitMinutes else weeklyLimitMinutes)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (over) "已用完" else formatRemain(usedSeconds),
            fontSize = 36.sp,
            fontWeight = FontWeight.Light,
            color = accent,
            letterSpacing = (-1).sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (over) {
                "${if (useDaily) "今日" else "本周"}已用 · 限额 $limitLabel"
            } else {
                "${if (useDaily) "今日已用" else "本周已用"} · 限额 $limitLabel"
            },
            fontSize = 12.sp,
            color = themeConfig.textSecondary,
            textAlign = TextAlign.Center
        )
        if (includesPreMonitorUsage) {
            Text(
                text = "含加入前今日使用",
                fontSize = 11.sp,
                color = themeConfig.textTertiary.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(themeConfig.dividerColor.copy(alpha = 0.75f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(1.dp))
                    .background(accent.copy(alpha = 0.92f))
            )
        }
    }
}

@Composable
private fun QuoteBreath(
    quote: String,
    author: String,
    themeConfig: InterceptThemeConfig
) {
    val authorDisplay = author
        .removePrefix("—")
        .removePrefix("–")
        .trim()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = quote,
            fontSize = 13.sp,
            fontWeight = FontWeight.Light,
            fontStyle = FontStyle.Italic,
            color = themeConfig.textSecondary.copy(alpha = 0.92f),
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        if (authorDisplay.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = authorDisplay,
                fontSize = 11.sp,
                color = themeConfig.textTertiary.copy(alpha = 0.75f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun OpenOwnAppLink(
    enabled: Boolean,
    ownAppIcon: Drawable?,
    themeConfig: InterceptThemeConfig,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (ownAppIcon != null) {
            val bmp = remember(ownAppIcon) {
                ownAppIcon.toBitmap(48, 48).asImageBitmap()
            }
            Image(
                bitmap = bmp,
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
            )
        }
        Text(
            text = "打开心锚",
            fontSize = 13.sp,
            color = themeConfig.textTertiary.copy(alpha = if (enabled) 0.95f else 0.4f)
        )
    }
}

@Composable
internal fun SessionMinutesKeypad(
    themeConfig: InterceptThemeConfig,
    digits: String,
    maxMinutes: Int,
    enabled: Boolean,
    onDigitsChange: (String) -> Unit,
    onDigitHaptic: () -> Unit
) {
    val safeMax = maxMinutes.coerceAtLeast(SessionLimitPolicy.MIN_SESSION_MINUTES)
    val maxLen = safeMax.toString().length
    val accent = themeConfig.accentColor

    fun appendDigit(d: Int) {
        if (!enabled) return
        val next = if (digits == "0") d.toString() else digits + d
        if (next.length > maxLen) return
        val n = next.toIntOrNull() ?: return
        if (n > safeMax) return
        onDigitHaptic()
        onDigitsChange(next)
    }

    fun backspace() {
        if (!enabled || digits.isEmpty()) return
        onDigitHaptic()
        onDigitsChange(digits.dropLast(1))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(themeConfig.surfaceColor.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "自定义分钟",
                fontSize = 12.sp,
                color = themeConfig.textTertiary
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = digits.ifEmpty { "—" },
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Light,
                    color = when {
                        digits.isEmpty() -> themeConfig.textTertiary
                        else -> accent
                    },
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = " 分",
                    fontSize = 12.sp,
                    color = themeConfig.textTertiary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }

        val rows: List<List<KeypadCell>> = listOf(
            listOf(KeypadCell.Digit(1), KeypadCell.Digit(2), KeypadCell.Digit(3), KeypadCell.Digit(4)),
            listOf(KeypadCell.Digit(5), KeypadCell.Digit(6), KeypadCell.Digit(7), KeypadCell.Digit(8)),
            listOf(KeypadCell.Digit(9), KeypadCell.Digit(0), KeypadCell.Backspace, KeypadCell.Empty)
        )
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    row.forEach { cell ->
                        when (cell) {
                            is KeypadCell.Digit -> KeypadKey(
                                themeConfig = themeConfig,
                                enabled = enabled,
                                onClick = { appendDigit(cell.value) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "${cell.value}",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = themeConfig.textPrimary.copy(alpha = 0.88f)
                                )
                            }
                            KeypadCell.Backspace -> KeypadKey(
                                themeConfig = themeConfig,
                                enabled = enabled && digits.isNotEmpty(),
                                onClick = { backspace() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Backspace,
                                    contentDescription = "删除",
                                    tint = themeConfig.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            KeypadCell.Empty -> Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

private sealed class KeypadCell {
    data class Digit(val value: Int) : KeypadCell()
    data object Backspace : KeypadCell()
    data object Empty : KeypadCell()
}

@Composable
private fun KeypadKey(
    themeConfig: InterceptThemeConfig,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var flash by remember { mutableStateOf(false) }
    LaunchedEffect(flash) {
        if (!flash) return@LaunchedEffect
        delay(120)
        flash = false
    }
    val visuallyPressed = enabled && (pressed || flash)
    val shape = RoundedCornerShape(9.dp)
    val targetBg = when {
        !enabled -> themeConfig.bgColor.copy(alpha = 0.25f)
        visuallyPressed -> themeConfig.accentColor.copy(alpha = 0.28f)
        else -> themeConfig.bgColor.copy(alpha = 0.45f)
    }
    val bg by animateColorAsState(targetBg, tween(90), label = "key_bg")
    val scale by animateFloatAsState(
        targetValue = if (visuallyPressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "key_scale"
    )

    Box(
        modifier = modifier
            .aspectRatio(1.55f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(bg)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = {
                    flash = true
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

private fun formatRemain(totalSeconds: Long): String {
    val m = (totalSeconds / 60).toInt()
    val s = (totalSeconds % 60).toInt()
    return when {
        m >= 60 -> "${m / 60}小时${m % 60}分"
        m > 0 && s > 0 -> "${m}分${s}秒"
        m > 0 -> "${m}分钟"
        else -> "${s}秒"
    }
}

private fun formatMinutesLabel(minutes: Int): String = when {
    minutes >= 60 && minutes % 60 == 0 -> "${minutes / 60}小时"
    minutes >= 60 -> "${minutes / 60}小时${minutes % 60}分"
    else -> "${minutes}分钟"
}
