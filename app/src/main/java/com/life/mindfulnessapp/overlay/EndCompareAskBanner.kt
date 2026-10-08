package com.life.mindfulnessapp.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.domain.model.CompanionPath
import com.life.mindfulnessapp.domain.model.SessionAwarenessCopy
import com.life.mindfulnessapp.domain.model.SessionAwarenessMode
import com.life.mindfulnessapp.ui.common.CompareSectionColors
import com.life.mindfulnessapp.ui.common.CompareTierStyle
import com.life.mindfulnessapp.ui.common.IntentCompareSection

/**
 * 结束后对照 · 第一步：路径差分二选一。
 * 「对齐」侧由调用方立刻收束；「偏航」侧展开 [EndCompareTierPanel]。
 */
@Composable
fun EndCompareBinaryAsk(
    path: CompanionPath,
    metaLine: String?,
    accent: Color,
    ink: Color,
    muted: Color,
    onAligned: () -> Unit,
    onDrift: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDarkTheme: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    val yesShape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = SessionAwarenessCopy.endComparePrompt(path),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = ink,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (!metaLine.isNullOrBlank()) {
            Text(
                text = metaLine,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = muted,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // 决策色块「完成了」；「没做完」只做字色，少容器
        Text(
            text = SessionAwarenessCopy.endCompareYes(path),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(yesShape)
                .background(accent.copy(alpha = if (isDarkTheme) 0.88f else 0.92f))
                .clickable(
                    enabled = enabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onAligned()
                }
                .padding(vertical = 12.dp)
        )
        Text(
            text = SessionAwarenessCopy.endCompareNo(path),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = muted,
            modifier = Modifier
                .clickable(
                    enabled = enabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDrift()
                }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/**
 * 结束后对照 · 第二步：可选细档（偏航侧才出现）。
 */
@Composable
fun EndCompareTierPanel(
    visible: Boolean,
    selectedLevel: Int?,
    onLevelSelected: (Int) -> Unit,
    noteText: String,
    onNoteChange: (String) -> Unit,
    colors: CompareSectionColors,
    awarenessMode: SessionAwarenessMode,
    durationSeconds: Long,
    driftSeconds: Long?,
    onDriftSecondsChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    style: CompareTierStyle = CompareTierStyle.CompactDivider,
    enabled: Boolean = true,
    onNoteFocusChanged: ((Boolean) -> Unit)? = null,
    hint: String? = "可选 · 点一档就收",
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) + expandVertically(tween(260)),
        exit = fadeOut(tween(160)) + shrinkVertically(tween(200)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IntentCompareSection(
                selectedLevel = selectedLevel,
                onLevelSelected = onLevelSelected,
                noteText = noteText,
                onNoteChange = onNoteChange,
                colors = colors,
                style = style,
                enabled = enabled,
                showPrompt = false,
                awarenessMode = awarenessMode,
                onNoteFocusChanged = onNoteFocusChanged ?: {},
                durationSeconds = durationSeconds,
                driftSeconds = driftSeconds,
                onDriftSecondsChange = onDriftSecondsChange
            )
            if (!hint.isNullOrBlank()) {
                Text(
                    text = hint,
                    fontSize = 10.sp,
                    color = colors.hint.copy(alpha = 0.75f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
