package com.life.mindfulnessapp.ui.common

import android.view.ViewTreeObserver
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.life.mindfulnessapp.domain.model.BreathCostPolicy
import com.life.mindfulnessapp.ui.theme.LogoGreen
import kotlinx.coroutines.delay

private val BreathPageBg = Color(0xFF0B0F0C)
private val BreathPageFg = Color(0xFFE8EDE9)
private val BreathPageMuted = Color(0xFF8A958E)

private const val BREATH_SCALE_MIN = 0.92f
private const val BREATH_SCALE_MAX = 1.06f
private const val BREATH_SCALE_REST = 0.94f

private enum class BreathPhase {
    Idle,
    Inhale,
    Exhale,
    Done
}

/**
 * 全屏呼吸代价页：必须把手指按在触控区全程配合。
 * - 按住才计时；松手 / 滑出 / 失焦 → 进度清零
 * - 4s 吸 / 6s 呼，圆环与口令跟相位同步
 * - 满时长后点「确认继续」才真正放行（界面不展示倒计时，只跟呼吸）
 * - 返回 / 取消 = 放弃本次动作
 */
@Composable
fun BreathCostHoldScreen(
    title: String,
    subtitle: String,
    confirmLabel: String = "确认继续",
    accent: Color = LogoGreen,
    onCompleted: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current

    var isHolding by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var remainingSec by remember { mutableIntStateOf(BreathCostPolicy.DURATION_SECONDS) }
    var completed by remember { mutableStateOf(false) }
    var holdGeneration by remember { mutableIntStateOf(0) }
    var breathPhase by remember { mutableStateOf(BreathPhase.Idle) }
    var breathScale by remember { mutableFloatStateOf(BREATH_SCALE_REST) }

    fun resetHold(notify: Boolean = true) {
        if (completed) return
        isHolding = false
        progress = 0f
        remainingSec = BreathCostPolicy.DURATION_SECONDS
        breathPhase = BreathPhase.Idle
        breathScale = BREATH_SCALE_REST
        if (notify) holdGeneration++
    }

    BackHandler { onDismiss() }

    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (!hasFocus) resetHold()
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener(listener)
        onDispose {
            view.viewTreeObserver.removeOnWindowFocusChangeListener(listener)
        }
    }

    LaunchedEffect(isHolding, holdGeneration, completed) {
        if (completed || !isHolding) return@LaunchedEffect
        val start = System.currentTimeMillis()
        val total = BreathCostPolicy.DURATION_MS
        var lastPhase: BreathPhase? = null
        while (isHolding && !completed) {
            val elapsed = System.currentTimeMillis() - start
            progress = (elapsed.toFloat() / total).coerceIn(0f, 1f)
            remainingSec = ((total - elapsed + 999L) / 1000L)
                .toInt()
                .coerceIn(0, BreathCostPolicy.DURATION_SECONDS)

            val inCycle = elapsed % BreathCostPolicy.CYCLE_MS
            val phase = if (inCycle < BreathCostPolicy.INHALE_MS) {
                BreathPhase.Inhale
            } else {
                BreathPhase.Exhale
            }
            if (phase != lastPhase) {
                // 首拍不震；吸↔呼切换给轻触，方便跟上节奏
                if (lastPhase != null) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                lastPhase = phase
            }
            breathPhase = phase
            breathScale = if (phase == BreathPhase.Inhale) {
                val t = inCycle.toFloat() / BreathCostPolicy.INHALE_MS
                lerp(BREATH_SCALE_MIN, BREATH_SCALE_MAX, t.coerceIn(0f, 1f))
            } else {
                val t = (inCycle - BreathCostPolicy.INHALE_MS).toFloat() /
                    BreathCostPolicy.EXHALE_MS
                lerp(BREATH_SCALE_MAX, BREATH_SCALE_MIN, t.coerceIn(0f, 1f))
            }

            if (elapsed >= total) {
                progress = 1f
                remainingSec = 0
                completed = true
                isHolding = false
                breathPhase = BreathPhase.Done
                breathScale = 1f
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                break
            }
            delay(16)
        }
        if (!completed) {
            progress = 0f
            remainingSec = BreathCostPolicy.DURATION_SECONDS
            breathPhase = BreathPhase.Idle
            breathScale = BREATH_SCALE_REST
        }
    }

    val isLastCycle = remainingSec <= BreathCostPolicy.CYCLE_MS / 1000
    val phaseLabel = when {
        completed -> "可以继续了"
        breathPhase == BreathPhase.Inhale -> "吸气"
        breathPhase == BreathPhase.Exhale && isLastCycle -> "慢慢放下"
        breathPhase == BreathPhase.Exhale -> "呼气"
        progress > 0f -> "继续按住"
        else -> "按住这里"
    }

    // 不展示时长 / 倒计时：圆心只留呼吸锚点，进度靠圆环
    val centerPrimary = when {
        completed -> "✓"
        else -> "·"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(BreathPageBg, Color(0xFF121A14), BreathPageBg)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = BreathPageFg,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = subtitle,
                fontSize = 15.sp,
                color = BreathPageMuted,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(240.dp)
                    .scale(breathScale)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 10.dp.toPx()
                    val pad = stroke / 2f + 4.dp.toPx()
                    val glowR = size.minDimension / 2f - pad
                    drawCircle(
                        color = accent.copy(
                            alpha = when {
                                completed -> 0.20f
                                isHolding -> 0.16f
                                else -> 0.08f
                            }
                        ),
                        radius = glowR
                    )
                    drawArc(
                        color = accent.copy(alpha = 0.18f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(pad, pad),
                        size = Size(size.width - 2 * pad, size.height - 2 * pad),
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    if (progress > 0f) {
                        drawArc(
                            color = accent,
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            topLeft = Offset(pad, pad),
                            size = Size(size.width - 2 * pad, size.height - 2 * pad),
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }
                }

                // 触控区：必须按住圆内才计时
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(168.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                completed -> accent.copy(alpha = 0.18f)
                                isHolding -> accent.copy(alpha = 0.14f)
                                else -> BreathPageFg.copy(alpha = 0.06f)
                            }
                        )
                        .pointerInput(completed) {
                            if (completed) return@pointerInput
                            detectTapGestures(
                                onPress = {
                                    isHolding = true
                                    try {
                                        tryAwaitRelease()
                                    } finally {
                                        if (!completed) {
                                            isHolding = false
                                            progress = 0f
                                            remainingSec = BreathCostPolicy.DURATION_SECONDS
                                            breathPhase = BreathPhase.Idle
                                            breathScale = BREATH_SCALE_REST
                                            holdGeneration++
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = centerPrimary,
                            fontSize = if (completed) 36.sp else 28.sp,
                            fontWeight = FontWeight.Light,
                            color = accent,
                            letterSpacing = (-1).sp
                        )
                        Text(
                            text = phaseLabel,
                            fontSize = if (isHolding && !completed) 16.sp else 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = BreathPageMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = when {
                    completed -> "你在场"
                    isHolding -> "跟着圆，慢慢呼吸"
                    else -> "把手指放在圆里，按住配合呼吸"
                },
                fontSize = 14.sp,
                color = BreathPageMuted,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(1f))

            if (completed) {
                Button(
                    onClick = onCompleted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Text(
                        text = confirmLabel,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
            ) {
                Text(
                    text = "取消",
                    fontSize = 14.sp,
                    color = BreathPageMuted
                )
            }
        }
    }
}

private fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t

/**
 * 以全屏 Dialog 呈现 [BreathCostHoldScreen]，供设置页 / 配置门槛调用。
 */
@Composable
fun BreathCostGateDialog(
    title: String,
    subtitle: String,
    confirmHint: String = "确认继续",
    onCompleted: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BreathCostHoldScreen(
            title = title,
            subtitle = subtitle,
            confirmLabel = confirmHint,
            onCompleted = onCompleted,
            onDismiss = onDismiss
        )
    }
}
