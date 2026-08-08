package com.life.mindfulnessapp.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity.MindfulnessLevel
import com.life.mindfulnessapp.ui.theme.CapabilityForm
import com.life.mindfulnessapp.ui.theme.CapabilityKind
import com.life.mindfulnessapp.ui.theme.CapabilityMark
import com.life.mindfulnessapp.ui.theme.MindfulnessAppTheme
import kotlinx.coroutines.delay

/**
 * 单次意图时长到点收口页（与日锁超限页语义分开）。
 *
 * 有意图时可轻量对照「和意图比，这一次」（可选）；出口进入心锚并定位该条。
 * 口径：到点 = 复盘默认（进心锚）；手动结束 = 脱身默认。
 * 续时已前移到胶囊临近结束时。
 */
@Composable
fun SessionLimitReachedOverlayScreen(
    appName: String,
    purpose: String?,
    committedMinutes: Int,
    isDarkTheme: Boolean = true,
    onConfirm: (mindfulnessLevel: Int?, note: String?) -> Unit
) {
    val themeConfig = remember(isDarkTheme) { getInterceptThemeConfig(isDark = isDarkTheme) }
    val accent = themeConfig.accentColor
    val enterAlpha = remember { Animatable(0f) }
    var showContent by remember { mutableStateOf(false) }
    var showButtons by remember { mutableStateOf(false) }
    var isActing by remember { mutableStateOf(false) }
    val intent = purpose?.trim().orEmpty()
    val enableCompare = intent.isNotEmpty()
    var selectedLevel by remember { mutableStateOf<Int?>(null) }
    var noteText by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        enterAlpha.animateTo(1f, tween(280))
        delay(80)
        showContent = true
        delay(160)
        showButtons = true
    }

    fun act() {
        if (isActing) return
        isActing = true
        onConfirm(
            selectedLevel?.takeIf { MindfulnessLevel.isValid(it) },
            noteText.trim().takeIf { it.isNotEmpty() }
        )
    }

    MindfulnessAppTheme(darkTheme = isDarkTheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(enterAlpha.value)
                .background(themeConfig.bgColor)
        ) {
            val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusBarHeight)
                    .background(themeConfig.bgColor)
                    .align(Alignment.TopStart)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .statusBarsPadding()
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.fillMaxHeight(0.12f))

                AnimatedVisibility(
                    visible = showContent,
                    enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 3 }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        CapabilityMark(
                            kind = CapabilityKind.TimeLock,
                            form = CapabilityForm.Emphasis,
                            tint = accent
                        )
                        Text(
                            text = "这次说好的时间到了",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = themeConfig.textPrimary,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "${appName.ifBlank { "这个 App" }} · ${committedMinutes} 分钟",
                            fontSize = 15.sp,
                            color = themeConfig.textSecondary,
                            textAlign = TextAlign.Center
                        )
                        if (intent.isNotEmpty()) {
                            Text(
                                text = "意图「$intent」",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = accent.copy(alpha = 0.92f),
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        if (enableCompare) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "${MindfulnessLevel.COMPARE_PROMPT}（可选）",
                                    fontSize = 12.sp,
                                    color = themeConfig.textTertiary,
                                    fontWeight = FontWeight.Medium
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(
                                        MindfulnessLevel.ALIGNED to Color(0xFF27AE60),
                                        MindfulnessLevel.SLIGHT to Color(0xFFD4A017),
                                        MindfulnessLevel.LARGE to Color(0xFFC47A6A)
                                    ).forEach { (level, levelAccent) ->
                                        val selected = selectedLevel == level
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    if (selected) levelAccent.copy(alpha = 0.18f)
                                                    else themeConfig.textPrimary.copy(alpha = 0.05f)
                                                )
                                                .border(
                                                    width = if (selected) 1.5.dp else 1.dp,
                                                    color = if (selected) levelAccent.copy(alpha = 0.85f)
                                                    else themeConfig.dividerColor,
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .clickable {
                                                    haptic.performHapticFeedback(
                                                        HapticFeedbackType.TextHandleMove
                                                    )
                                                    selectedLevel =
                                                        if (selectedLevel == level) null else level
                                                }
                                                .padding(vertical = 12.dp)
                                        ) {
                                            Text(
                                                text = MindfulnessLevel.tierLabel(level),
                                                fontSize = 13.sp,
                                                fontWeight = if (selected) {
                                                    FontWeight.SemiBold
                                                } else {
                                                    FontWeight.Medium
                                                },
                                                color = if (selected) {
                                                    levelAccent
                                                } else {
                                                    themeConfig.textSecondary
                                                },
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = MindfulnessLevel.noteSectionLabel(selectedLevel),
                                    fontSize = 12.sp,
                                    color = themeConfig.textTertiary,
                                    fontWeight = FontWeight.Medium
                                )
                                BasicTextField(
                                    value = noteText,
                                    onValueChange = { if (it.length <= 100) noteText = it },
                                    textStyle = TextStyle(
                                        color = themeConfig.textPrimary,
                                        fontSize = 14.sp
                                    ),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(themeConfig.textPrimary.copy(alpha = 0.05f))
                                        .border(1.dp, themeConfig.dividerColor, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    decorationBox = { inner ->
                                        Box {
                                            if (noteText.isEmpty()) {
                                                Text(
                                                    text = MindfulnessLevel.notePlaceholder(selectedLevel),
                                                    fontSize = 14.sp,
                                                    color = themeConfig.textTertiary
                                                )
                                            }
                                            inner()
                                        }
                                    }
                                )
                            }
                        } else {
                            Text(
                                text = "记下了 · 回心锚看看这一次",
                                fontSize = 14.sp,
                                color = themeConfig.textTertiary,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                AnimatedVisibility(
                    visible = showButtons,
                    enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 3 }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 52.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { act() },
                            enabled = !isActing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = themeConfig.accentForeground,
                                disabledContainerColor = themeConfig.dividerColor
                            ),
                            shape = RoundedCornerShape(14.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                        ) {
                            Text("记下了 · 回心锚", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
