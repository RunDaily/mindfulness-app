package com.life.mindfulnessapp.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ChipShape = RoundedCornerShape(22.dp)
private val Gold = Color(0xFFE8B84A)
private val GoldSoft = Color(0xFFFFF0C8)

/**
 * 离开后的轻量肯定：顶部居中小条。
 *
 * 可带主动作（去正向 App / 配置引导 / 去看这一次）、可选「更多」展开。
 */
@Composable
fun DismissAffirmationOverlay(
    copy: LeaveFeedbackCopy,
    isDarkTheme: Boolean = true,
    onAction: (() -> Unit)? = null,
    onMoreChoice: ((LeaveDestinationChoice) -> Unit)? = null,
    onManage: (() -> Unit)? = null,
    onFinished: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(-10f) }
    var finished by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    fun finishOnce(invokeAction: Boolean = false) {
        if (finished) return
        finished = true
        if (invokeAction) onAction?.invoke()
        onFinished()
    }

    val bg = if (isDarkTheme) Color(0xE6121418) else Color(0xF2FFFFFF)
    val border = if (isDarkTheme) Gold.copy(alpha = 0.28f) else Gold.copy(alpha = 0.35f)
    val titleColor = if (isDarkTheme) GoldSoft else Color(0xFF5C4A1A)
    val subtitleColor = if (isDarkTheme) Color(0xFF9A9A9A) else Color(0xFF6B6B6B)
    val actionColor = if (isDarkTheme) Gold else Color(0xFF8A7020)
    val moreBg = if (isDarkTheme) Color(0xFF1C1F24) else Color(0xFFF7F5F0)
    val hasPrimaryAction = onAction != null && !copy.actionLabel.isNullOrBlank()
    val hasMore = (!copy.moreLabel.isNullOrBlank()) &&
        (copy.moreChoices.isNotEmpty() || copy.showManageLink)
    val interactive = hasPrimaryAction || hasMore
    val holdMs = when {
        expanded -> 5200L
        hasMore || copy.opensSettings -> 3200L
        hasPrimaryAction -> 2800L
        else -> 900L
    }

    LaunchedEffect(expanded) {
        if (finished) return@LaunchedEffect
        // 展开后重新计时，给选择留时间
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (alpha.value < 0.5f) {
            launch {
                alpha.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
            }
            offsetY.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
        }
        delay(holdMs)
        if (finished) return@LaunchedEffect
        launch {
            offsetY.animateTo(-6f, tween(200, easing = FastOutSlowInEasing))
        }
        alpha.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
        finishOnce(invokeAction = false)
    }

    Box(
        modifier = Modifier
            .then(
                if (interactive) {
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 12.dp, bottom = 8.dp)
                } else {
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(top = 12.dp)
                }
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .graphicsLayer {
                    this.alpha = alpha.value
                    translationY = offsetY.value.dp.toPx()
                }
                .widthIn(max = 340.dp)
                .clip(ChipShape)
                .background(bg)
                .border(1.dp, border, ChipShape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Gold.copy(alpha = 0.9f))
                )
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = copy.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = titleColor,
                        letterSpacing = 0.4.sp,
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!copy.subtitle.isNullOrBlank()) {
                        Text(
                            text = copy.subtitle,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = subtitleColor,
                            textAlign = TextAlign.Start,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (hasPrimaryAction) {
                    Text(
                        text = copy.actionLabel.orEmpty(),
                        fontSize = if (copy.opensSettings) 12.sp else 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = actionColor,
                        letterSpacing = 0.2.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                finishOnce(invokeAction = true)
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
                if (hasMore) {
                    Text(
                        text = if (expanded) "收起" else copy.moreLabel.orEmpty(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = subtitleColor,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                expanded = !expanded
                            }
                            .padding(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 2.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded && hasMore,
                enter = fadeIn(tween(160)) + expandVertically(tween(200)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(160))
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(moreBg)
                        .padding(8.dp)
                ) {
                    copy.moreChoices.forEach { choice ->
                        Text(
                            text = "去「${choice.label}」",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = actionColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (finished) return@clickable
                                    finished = true
                                    onMoreChoice?.invoke(choice)
                                    onFinished()
                                }
                                .padding(horizontal = 10.dp, vertical = 10.dp)
                        )
                    }
                    if (copy.showManageLink && onManage != null) {
                        Text(
                            text = "管理想去的地方",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = subtitleColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (finished) return@clickable
                                    finished = true
                                    onManage.invoke()
                                    onFinished()
                                }
                                .padding(horizontal = 10.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 兼容旧调用：按去向生成门外轻提示文案。
 */
@Composable
fun DismissAffirmationOverlay(
    dismissCount: Int,
    destination: DismissDestination,
    isDarkTheme: Boolean = true,
    onFinished: () -> Unit
) {
    val copy = leaveFeedbackCopy(
        LeaveFeedbackRequest(
            kind = LeaveFeedbackKind.GateLight,
            packageName = "",
            destination = destination,
            dismissCount = dismissCount
        )
    )
    DismissAffirmationOverlay(
        copy = copy,
        isDarkTheme = isDarkTheme,
        onFinished = onFinished
    )
}
