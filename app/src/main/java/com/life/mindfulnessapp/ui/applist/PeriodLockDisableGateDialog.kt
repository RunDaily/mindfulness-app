package com.life.mindfulnessapp.ui.applist

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.domain.model.PeriodLockPolicy
import com.life.mindfulnessapp.ui.theme.LogoGreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 时段锁生效期间关闭（总开关 / 某一段）时的解锁门槛：
 * 回显承诺 → 长按冷静 → 再确认关闭。
 * 拦截页本身不提供破界入口。
 */
@Composable
fun PeriodLockDisableGateDialog(
    commitment: String,
    windowLabel: String?,
    title: String = "关闭时段锁？",
    confirmLabel: String = "确认关闭",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var holdJob by remember { mutableStateOf<Job?>(null) }
    var heldReady by remember { mutableStateOf(false) }

    fun cancelHold() {
        holdJob?.cancel()
        holdJob = null
        holdProgress = 0f
    }

    fun startHold() {
        if (heldReady) return
        cancelHold()
        holdJob = scope.launch {
            val start = System.currentTimeMillis()
            while (true) {
                val elapsed = System.currentTimeMillis() - start
                holdProgress = (elapsed.toFloat() / PeriodLockPolicy.BREAK_HOLD_MS).coerceIn(0f, 1f)
                if (elapsed >= PeriodLockPolicy.BREAK_HOLD_MS) {
                    holdProgress = 1f
                    heldReady = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    break
                }
                delay(16)
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            cancelHold()
            onDismiss()
        },
        containerColor = cs.surface,
        shape = RoundedCornerShape(14.dp),
        title = {
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (!windowLabel.isNullOrBlank()) {
                    Text(
                        text = "当前正处于锁定时段：$windowLabel",
                        fontSize = 14.sp,
                        color = cs.onSurface.copy(alpha = 0.55f),
                        lineHeight = 20.sp
                    )
                } else {
                    Text(
                        text = "当前正处于锁定时段。关闭后即可进入受监控的 App。",
                        fontSize = 14.sp,
                        color = cs.onSurface.copy(alpha = 0.55f),
                        lineHeight = 20.sp
                    )
                }
                if (commitment.isNotBlank()) {
                    Text(
                        text = "你曾承诺：「$commitment」",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = cs.onSurface,
                        lineHeight = 22.sp
                    )
                }
                Text(
                    text = "生效期间关闭需要长按冷静，再确认。",
                    fontSize = 13.sp,
                    color = cs.onSurface.copy(alpha = 0.42f)
                )

                if (!heldReady) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(cs.outline.copy(alpha = 0.12f))
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        startHold()
                                        try {
                                            tryAwaitRelease()
                                        } finally {
                                            if (!heldReady) cancelHold()
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (holdProgress > 0f) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(holdProgress)
                                        .background(LogoGreen.copy(alpha = 0.22f))
                                        .align(Alignment.CenterStart)
                                )
                            }
                        }
                        Text(
                            text = if (holdProgress > 0f) "继续按住…" else "长按以继续关闭",
                            fontSize = 13.sp,
                            color = cs.onSurface.copy(alpha = 0.45f)
                        )
                    }
                } else {
                    Text(
                        text = "已冷静。确认关闭后，锁定立即解除。",
                        fontSize = 13.sp,
                        color = LogoGreen,
                        textAlign = TextAlign.Start
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    cancelHold()
                    onConfirm()
                },
                enabled = heldReady,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LogoGreen,
                    disabledContainerColor = cs.outline.copy(alpha = 0.25f)
                ),
                shape = RoundedCornerShape(10.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Text(
                    confirmLabel,
                    fontWeight = FontWeight.SemiBold,
                    color = if (heldReady) androidx.compose.ui.graphics.Color.White
                    else cs.onSurface.copy(alpha = 0.35f)
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    cancelHold()
                    onDismiss()
                }
            ) {
                Text("继续锁定", color = cs.onSurface.copy(alpha = 0.45f))
            }
        }
    )
}
