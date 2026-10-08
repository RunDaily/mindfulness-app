package com.life.mindfulnessapp.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity.MindfulnessLevel
import com.life.mindfulnessapp.domain.model.CompanionPath
import com.life.mindfulnessapp.domain.model.SessionAwarenessCopy
import com.life.mindfulnessapp.domain.model.SessionAwarenessMode
import com.life.mindfulnessapp.ui.common.CompareSectionColors
import com.life.mindfulnessapp.ui.common.CompareTierStyle
import com.life.mindfulnessapp.ui.common.compareCanConfirm
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.theme.LogoGreenBright
import com.life.mindfulnessapp.ui.theme.LogoGreenDeep
import kotlinx.coroutines.launch

/** 结束确认触发原因 */
enum class EndConfirmReason {
    /** 用户主动点胶囊「结束」 */
    Manual,
    /** 后台超时，询问是否结束计时 */
    BackgroundTimeout
}

private data class EndConfirmPalette(
    val surface: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val primaryButton: Color,
    val primaryButtonText: Color,
)

private fun endConfirmPalette(isDark: Boolean): EndConfirmPalette =
    if (isDark) EndConfirmPalette(
        surface = Color(0xF2181B20),
        border = Color.White.copy(alpha = 0.09f),
        textPrimary = Color(0xFFECEEF2),
        textSecondary = Color(0xFF8B919A),
        textMuted = Color(0xFF6E747C),
        accent = LogoGreenBright,
        primaryButton = LogoGreen,
        primaryButtonText = Color(0xFFFFFFFF),
    ) else EndConfirmPalette(
        surface = Color(0xF5FAFAFC),
        border = Color.Black.copy(alpha = 0.06f),
        textPrimary = Color(0xFF1C1C1E),
        textSecondary = Color(0xFF6B6B6B),
        textMuted = Color(0xFF8A8A8E),
        accent = LogoGreenDeep,
        primaryButton = LogoGreen,
        primaryButtonText = Color(0xFFFFFFFF),
    )

/**
 * 手动结束 / 后台超时确认。
 *
 * 轻形态：一句问 + 一行 meta + 决策色块「结束」+ 字色「继续用」。
 * 有对照时先二选一，偏航再细档；入场只做淡入轻缩放，不弹跳。
 */
@Composable
fun EndConfirmDialog(
    reason: EndConfirmReason,
    appName: String,
    purpose: String?,
    sessionSeconds: Long,
    isDarkTheme: Boolean,
    useMonoFont: Boolean = false,
    enableCompare: Boolean = false,
    awarenessMode: SessionAwarenessMode = SessionAwarenessMode.TASK,
    companionPath: CompanionPath = CompanionPath.INTENT,
    onConfirm: (note: String?, mindfulnessLevel: Int?, driftSeconds: Long?, openToAnchor: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val scale = remember { Animatable(0.97f) }
    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(8f) }
    LaunchedEffect(Unit) {
        launch { alpha.animateTo(1f, tween(200, easing = FastOutSlowInEasing)) }
        launch { scale.animateTo(1f, tween(240, easing = FastOutSlowInEasing)) }
        launch { offsetY.animateTo(0f, tween(240, easing = FastOutSlowInEasing)) }
    }

    val palette = remember(isDarkTheme) { endConfirmPalette(isDarkTheme) }
    val font = if (useMonoFont) FontFamily.Monospace else FontFamily.Default
    val isBackgroundTimeout = reason == EndConfirmReason.BackgroundTimeout
    var showTier by remember { mutableStateOf(false) }
    var selectedLevel by remember { mutableStateOf<Int?>(null) }
    var noteText by remember { mutableStateOf("") }
    var driftSeconds by remember { mutableStateOf<Long?>(null) }
    val haptic = LocalHapticFeedback.current
    val canConfirm = when {
        !enableCompare -> true
        !showTier -> false
        else -> compareCanConfirm(
            enableCompare = true,
            selectedLevel = selectedLevel,
            driftSeconds = driftSeconds,
            durationSeconds = sessionSeconds,
        )
    }

    val title = when {
        useMonoFont && isBackgroundTimeout -> "STILL AWAY?"
        useMonoFont -> "END_SESSION?"
        isBackgroundTimeout -> "还要继续计时吗？"
        enableCompare && !showTier -> SessionAwarenessCopy.endComparePrompt(companionPath)
        enableCompare -> "看见偏了多少"
        else -> "结束这次使用？"
    }

    val dismissLabel = when {
        useMonoFont && isBackgroundTimeout -> "KEEP"
        useMonoFont -> "CANCEL"
        isBackgroundTimeout -> "继续计时"
        else -> "继续用"
    }

    val confirmLabel = when {
        useMonoFont && isBackgroundTimeout -> "END"
        useMonoFont -> "CONFIRM"
        isBackgroundTimeout -> "结束计时"
        else -> "结束"
    }

    val sessionLabel = formatEndConfirmDuration(sessionSeconds, useMonoFont)
    val purposeText = purpose?.trim().orEmpty()
    val metaLine = buildString {
        when {
            purposeText.isNotEmpty() -> append(purposeText)
            appName.isNotBlank() -> append(appName.trim())
        }
        if (sessionSeconds >= 10L && sessionLabel.isNotEmpty()) {
            if (isNotEmpty()) append(" · ")
            append(sessionLabel)
        }
    }.ifBlank { null }

    val shellShape = RoundedCornerShape(22.dp)

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
                translationY = offsetY.value
            }
            .shadow(10.dp, shape = shellShape, ambientColor = Color.Black.copy(alpha = 0.18f))
            .clip(shellShape)
            .background(palette.surface)
            .border(1.dp, palette.border, shellShape)
            .width(if (enableCompare) 300.dp else 280.dp)
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            when {
                enableCompare && !isBackgroundTimeout && !showTier -> {
                    EndCompareBinaryAsk(
                        path = companionPath,
                        metaLine = metaLine,
                        accent = palette.primaryButton,
                        ink = palette.textPrimary,
                        muted = palette.textSecondary,
                        isDarkTheme = isDarkTheme,
                        onAligned = {
                            onConfirm(null, MindfulnessLevel.ALIGNED, null, false)
                        },
                        onDrift = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showTier = true
                        }
                    )
                    QuietTextAction(
                        text = dismissLabel,
                        color = palette.textMuted,
                        font = font,
                        onClick = onDismiss,
                    )
                }

                enableCompare && !isBackgroundTimeout -> {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.textPrimary,
                        fontFamily = font,
                        textAlign = TextAlign.Center,
                    )
                    if (metaLine != null) {
                        Text(
                            text = metaLine,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = palette.textSecondary,
                            fontFamily = font,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val compareColors = remember(isDarkTheme) {
                        CompareSectionColors.forAwayBar(isDarkTheme)
                    }
                    EndCompareTierPanel(
                        visible = true,
                        selectedLevel = selectedLevel,
                        onLevelSelected = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedLevel = it
                        },
                        noteText = noteText,
                        onNoteChange = { noteText = it },
                        colors = compareColors,
                        awarenessMode = awarenessMode,
                        durationSeconds = sessionSeconds,
                        driftSeconds = driftSeconds,
                        onDriftSecondsChange = { driftSeconds = it },
                        style = CompareTierStyle.CompactDivider,
                        hint = if (canConfirm) null else SessionAwarenessCopy.compareHint(awarenessMode),
                    )
                    DecisionPrimaryButton(
                        label = confirmLabel,
                        enabled = canConfirm,
                        palette = palette,
                        font = font,
                        onClick = {
                            onConfirm(
                                noteText.trim().ifEmpty { null },
                                selectedLevel?.takeIf { MindfulnessLevel.isValid(it) },
                                driftSeconds,
                                false,
                            )
                        },
                    )
                    QuietTextAction(
                        text = dismissLabel,
                        color = palette.textMuted,
                        font = font,
                        onClick = onDismiss,
                    )
                    QuietTextAction(
                        text = if (useMonoFont) "END → ANCHOR" else "结束并去心锚",
                        color = if (canConfirm) palette.accent else palette.textMuted.copy(alpha = 0.45f),
                        font = font,
                        enabled = canConfirm,
                        onClick = {
                            onConfirm(
                                noteText.trim().ifEmpty { null },
                                selectedLevel?.takeIf { MindfulnessLevel.isValid(it) },
                                driftSeconds,
                                true,
                            )
                        },
                    )
                }

                else -> {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.textPrimary,
                        fontFamily = font,
                        textAlign = TextAlign.Center,
                        letterSpacing = 0.1.sp,
                    )
                    if (metaLine != null) {
                        Text(
                            text = metaLine,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = palette.textSecondary,
                            fontFamily = font,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    DecisionPrimaryButton(
                        label = confirmLabel,
                        enabled = true,
                        palette = palette,
                        font = font,
                        onClick = { onConfirm(null, null, null, false) },
                    )
                    QuietTextAction(
                        text = dismissLabel,
                        color = palette.textMuted,
                        font = font,
                        onClick = onDismiss,
                    )
                }
            }
        }
    }
}

@Composable
private fun DecisionPrimaryButton(
    label: String,
    enabled: Boolean,
    palette: EndConfirmPalette,
    font: FontFamily,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (enabled) palette.primaryButton
                else palette.textMuted.copy(alpha = 0.18f)
            )
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            color = if (enabled) palette.primaryButtonText
            else palette.textMuted.copy(alpha = 0.55f),
            fontWeight = FontWeight.SemiBold,
            fontFamily = font,
        )
    }
}

@Composable
private fun QuietTextAction(
    text: String,
    color: Color,
    font: FontFamily,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = color,
        fontFamily = font,
        modifier = Modifier
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

private fun formatEndConfirmDuration(sessionSeconds: Long, useMonoFont: Boolean): String {
    val h = sessionSeconds / 3600
    val m = (sessionSeconds % 3600) / 60
    val s = sessionSeconds % 60
    return if (useMonoFont) {
        when {
            h > 0 -> "${h}h${m}m"
            m > 0 -> "${m}m${s}s"
            else -> "${s}s"
        }
    } else {
        when {
            h > 0 && m > 0 -> "${h}小时${m}分"
            h > 0 -> "${h}小时"
            m > 0 && s > 0 -> "${m}分${s}秒"
            m > 0 -> "${m}分钟"
            sessionSeconds >= 10L -> "${s}秒"
            else -> ""
        }
    }
}
