package com.life.mindfulnessapp.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity.MindfulnessLevel
import com.life.mindfulnessapp.domain.model.DriftSecondsPolicy
import com.life.mindfulnessapp.domain.model.SessionAwarenessCopy
import com.life.mindfulnessapp.domain.model.SessionAwarenessMode
import com.life.mindfulnessapp.ui.theme.LogoGreen

/** 三档对照色：全 App 统一 */
object CompareTierColors {
    fun accent(level: Int, isDark: Boolean = true): Color = when (level) {
        MindfulnessLevel.ALIGNED -> if (isDark) Color(0xFF5FD08E) else Color(0xFF1F9A58)
        MindfulnessLevel.SLIGHT -> if (isDark) Color(0xFFE8B04A) else Color(0xFFC4891A)
        MindfulnessLevel.LARGE -> if (isDark) Color(0xFFE09078) else Color(0xFFC06B55)
        else -> if (isDark) Color(0xFF5FD08E) else LogoGreen
    }
}

enum class CompareTierStyle {
    /** 拦截页 / 到点页 / 首页弹窗：描边卡片 */
    BorderedBox,
    /** 离开横条：文字分栏 */
    CompactDivider
}

data class CompareSectionColors(
    val prompt: Color,
    val hint: Color,
    val tierMuted: Color,
    val tierBorder: Color,
    val tierBackground: Color,
    val noteLabel: Color,
    val noteText: Color,
    val notePlaceholder: Color,
    val noteBorder: Color,
    val noteBackground: Color,
    val divider: Color,
    val isDark: Boolean
) {
    fun tierAccent(level: Int): Color = CompareTierColors.accent(level, isDark)

    companion object {
        fun fromInterceptTheme(
            textPrimary: Color,
            textSecondary: Color,
            textTertiary: Color,
            dividerColor: Color,
            isDark: Boolean
        ) = CompareSectionColors(
            prompt = textSecondary,
            hint = textTertiary,
            tierMuted = textSecondary,
            tierBorder = dividerColor,
            tierBackground = textPrimary.copy(alpha = 0.05f),
            noteLabel = textTertiary,
            noteText = textPrimary,
            notePlaceholder = textTertiary,
            noteBorder = dividerColor,
            noteBackground = textPrimary.copy(alpha = 0.05f),
            divider = dividerColor,
            isDark = isDark
        )

        fun forAwayBar(isDark: Boolean) = CompareSectionColors(
            prompt = if (isDark) Color(0xFF8B919A) else Color(0xFF6B6B6B),
            hint = if (isDark) Color(0xFF8B919A) else Color(0xFF6B6B6B),
            tierMuted = if (isDark) Color(0xFF8B919A) else Color(0xFF6B6B6B),
            tierBorder = if (isDark) Color(0x33A8B5AE) else Color(0x241A211D),
            tierBackground = if (isDark) Color(0xFF1E2220) else Color(0xFFEEF4F0),
            noteLabel = if (isDark) Color(0xFF8B919A) else Color(0xFF6B6B6B),
            noteText = if (isDark) Color(0xFFECEEF2) else Color(0xFF1C1C1E),
            notePlaceholder = if (isDark) Color(0xFF8B919A) else Color(0xFF6B6B6B),
            noteBorder = if (isDark) Color(0x33A8B5AE) else Color(0x241A211D),
            noteBackground = if (isDark) Color(0xFF1E2220) else Color(0xFFEEF4F0),
            divider = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f),
            isDark = isDark
        )

        fun fromMaterial(
            onSurface: Color,
            outlineVariant: Color,
            isDark: Boolean
        ) = CompareSectionColors(
            prompt = onSurface.copy(alpha = 0.55f),
            hint = onSurface.copy(alpha = 0.40f),
            tierMuted = onSurface.copy(alpha = 0.50f),
            tierBorder = outlineVariant.copy(alpha = 0.7f),
            tierBackground = onSurface.copy(alpha = 0.04f),
            noteLabel = onSurface.copy(alpha = 0.55f),
            noteText = onSurface,
            notePlaceholder = onSurface.copy(alpha = 0.28f),
            noteBorder = outlineVariant,
            noteBackground = onSurface.copy(alpha = 0.02f),
            divider = outlineVariant.copy(alpha = 0.5f),
            isDark = isDark
        )
    }
}

@Composable
fun IntentCompareSection(
    selectedLevel: Int?,
    onLevelSelected: (Int) -> Unit,
    noteText: String,
    onNoteChange: (String) -> Unit,
    colors: CompareSectionColors,
    modifier: Modifier = Modifier,
    style: CompareTierStyle = CompareTierStyle.BorderedBox,
    enabled: Boolean = true,
    showNote: Boolean = true,
    showPrompt: Boolean = true,
    awarenessMode: SessionAwarenessMode = SessionAwarenessMode.TASK,
    prompt: String = SessionAwarenessCopy.comparePrompt(awarenessMode),
    hint: String? = SessionAwarenessCopy.compareHint(awarenessMode),
    noteMaxLength: Int = 100,
    useOutlinedNoteField: Boolean = false,
    showNoteCharCount: Boolean = false,
    onNoteFocusChanged: (Boolean) -> Unit = {},
    /** 本次时长；>0 且跑偏/跑远时展示跑偏时长采集 */
    durationSeconds: Long = 0L,
    driftSeconds: Long? = null,
    onDriftSecondsChange: (Long?) -> Unit = {},
    showDriftPicker: Boolean = true
) {
    val urgeMode = awarenessMode == SessionAwarenessMode.URGE
    val showNoteField = showNote &&
        (style != CompareTierStyle.CompactDivider || MindfulnessLevel.isValid(selectedLevel))
    val needsDrift = showDriftPicker &&
        durationSeconds > 0L &&
        (selectedLevel == MindfulnessLevel.SLIGHT || selectedLevel == MindfulnessLevel.LARGE)
    val driftPresets = remember(durationSeconds) {
        DriftSecondsPolicy.fractionPresets(durationSeconds)
    }
    val driftPrompt = SessionAwarenessCopy.driftPrompt(awarenessMode)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (showPrompt) {
            Text(
                text = prompt,
                fontSize = if (style == CompareTierStyle.CompactDivider) 12.sp else 13.sp,
                color = colors.prompt,
                fontWeight = FontWeight.SemiBold
            )
            if (!hint.isNullOrBlank() && style == CompareTierStyle.BorderedBox) {
                Text(
                    text = hint,
                    fontSize = 12.sp,
                    color = colors.hint
                )
            }
        }

        when (style) {
            CompareTierStyle.BorderedBox -> BorderedTierRow(
                selectedLevel = selectedLevel,
                onLevelSelected = { level ->
                    onLevelSelected(level)
                    onDriftSecondsChange(DriftSecondsPolicy.defaultFor(level, durationSeconds))
                },
                colors = colors,
                enabled = enabled,
                urgeMode = urgeMode
            )
            CompareTierStyle.CompactDivider -> CompactTierRow(
                selectedLevel = selectedLevel,
                onLevelSelected = { level ->
                    onLevelSelected(level)
                    onDriftSecondsChange(DriftSecondsPolicy.defaultFor(level, durationSeconds))
                },
                colors = colors,
                enabled = enabled,
                urgeMode = urgeMode
            )
        }

        if (needsDrift) {
            DriftSecondsPicker(
                durationSeconds = durationSeconds,
                driftSeconds = driftSeconds,
                presets = driftPresets,
                colors = colors,
                compact = style == CompareTierStyle.CompactDivider,
                enabled = enabled,
                prompt = driftPrompt,
                onDriftSecondsChange = onDriftSecondsChange
            )
        }

        if (showNoteField) {
            Text(
                text = MindfulnessLevel.noteSectionLabel(selectedLevel),
                fontSize = 12.sp,
                color = colors.noteLabel,
                fontWeight = FontWeight.Medium
            )
            val placeholder = MindfulnessLevel.notePlaceholder(selectedLevel, urgeMode)
            if (useOutlinedNoteField) {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { if (it.length <= noteMaxLength) onNoteChange(it) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    placeholder = {
                        Text(
                            placeholder,
                            fontSize = 14.sp,
                            color = colors.notePlaceholder
                        )
                    },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = selectedLevel?.let(colors::tierAccent) ?: LogoGreen,
                        unfocusedBorderColor = colors.noteBorder,
                        focusedTextColor = colors.noteText,
                        unfocusedTextColor = colors.noteText,
                        cursorColor = selectedLevel?.let(colors::tierAccent) ?: LogoGreen
                    )
                )
            } else {
                BasicTextField(
                    value = noteText,
                    onValueChange = { if (it.length <= noteMaxLength) onNoteChange(it) },
                    enabled = enabled,
                    textStyle = TextStyle(color = colors.noteText, fontSize = 14.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.noteBackground)
                        .border(1.dp, colors.noteBorder, RoundedCornerShape(12.dp))
                        .onFocusChanged { onNoteFocusChanged(it.isFocused) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    decorationBox = { inner ->
                        Box {
                            if (noteText.isEmpty() && placeholder.isNotEmpty()) {
                                Text(
                                    text = placeholder,
                                    fontSize = 14.sp,
                                    color = colors.notePlaceholder.copy(alpha = 0.85f)
                                )
                            }
                            inner()
                        }
                    }
                )
            }
            if (showNoteCharCount) {
                Text(
                    text = "${noteText.length} / $noteMaxLength",
                    fontSize = 11.sp,
                    color = colors.hint,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun BorderedTierRow(
    selectedLevel: Int?,
    onLevelSelected: (Int) -> Unit,
    colors: CompareSectionColors,
    enabled: Boolean,
    urgeMode: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            MindfulnessLevel.ALIGNED,
            MindfulnessLevel.SLIGHT,
            MindfulnessLevel.LARGE
        ).forEach { level ->
            val selected = selectedLevel == level
            val accent = colors.tierAccent(level)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) accent.copy(alpha = 0.18f) else colors.tierBackground
                    )
                    .border(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) accent.copy(alpha = 0.85f) else colors.tierBorder,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable(enabled = enabled) { onLevelSelected(level) }
                    .padding(vertical = 12.dp)
            ) {
                Text(
                    text = MindfulnessLevel.tierLabel(level, urgeMode),
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) accent else colors.tierMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun CompactTierRow(
    selectedLevel: Int?,
    onLevelSelected: (Int) -> Unit,
    colors: CompareSectionColors,
    enabled: Boolean,
    urgeMode: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(
            MindfulnessLevel.ALIGNED,
            MindfulnessLevel.SLIGHT,
            MindfulnessLevel.LARGE
        ).forEachIndexed { index, level ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(14.dp)
                        .background(colors.divider)
                )
            }
            val selected = selectedLevel == level
            val accent = colors.tierAccent(level)
            Text(
                text = MindfulnessLevel.tierLabel(level, urgeMode),
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = when {
                    selected -> accent
                    !enabled -> colors.tierMuted.copy(alpha = 0.45f)
                    else -> colors.tierMuted
                },
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = enabled) { onLevelSelected(level) }
                    .padding(vertical = 8.dp, horizontal = 4.dp)
            )
        }
    }
}

fun compareCanConfirm(
    enableCompare: Boolean,
    selectedLevel: Int?,
    driftSeconds: Long? = null,
    durationSeconds: Long = 0L,
    requireDrift: Boolean = true
): Boolean {
    if (!enableCompare) return true
    if (!MindfulnessLevel.isValid(selectedLevel)) return false
    if (!requireDrift || durationSeconds <= 0L) return true
    if (selectedLevel == MindfulnessLevel.ALIGNED) return true
    val resolved = DriftSecondsPolicy.resolveStored(selectedLevel, driftSeconds, durationSeconds)
    return resolved != null
}

@Composable
private fun DriftSecondsPicker(
    durationSeconds: Long,
    driftSeconds: Long?,
    presets: List<Pair<String, Long>>,
    colors: CompareSectionColors,
    compact: Boolean,
    enabled: Boolean,
    prompt: String,
    onDriftSecondsChange: (Long?) -> Unit
) {
    val labelSize = if (compact) 11.sp else 12.sp
    val chipSize = if (compact) 11.sp else 12.sp
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = prompt,
            fontSize = labelSize,
            color = colors.noteLabel,
            fontWeight = FontWeight.Medium
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            presets.forEach { (label, seconds) ->
                val selected = driftSeconds != null &&
                    kotlin.math.abs(driftSeconds - seconds) <= 1L
                val accent = colors.tierAccent(
                    if (seconds >= durationSeconds * 3 / 4) MindfulnessLevel.LARGE
                    else MindfulnessLevel.SLIGHT
                )
                Text(
                    text = label,
                    fontSize = chipSize,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) accent else colors.tierMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            width = 1.dp,
                            color = if (selected) accent.copy(alpha = 0.55f) else colors.tierBorder,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .background(
                            if (selected) accent.copy(alpha = 0.12f) else colors.tierBackground
                        )
                        .clickable(enabled = enabled) { onDriftSecondsChange(seconds) }
                        .padding(vertical = if (compact) 6.dp else 8.dp, horizontal = 2.dp)
                )
            }
        }
        if (driftSeconds != null && durationSeconds > 0L) {
            Text(
                text = "约 ${formatDriftLabel(driftSeconds)} / ${formatDriftLabel(durationSeconds)}",
                fontSize = 11.sp,
                color = colors.hint
            )
        }
    }
}

private fun formatDriftLabel(seconds: Long): String {
    val s = seconds.coerceAtLeast(0L)
    val m = s / 60L
    val rem = s % 60L
    return when {
        m <= 0L -> "${rem}秒"
        rem == 0L -> "${m}分钟"
        else -> "${m}分${rem}秒"
    }
}
