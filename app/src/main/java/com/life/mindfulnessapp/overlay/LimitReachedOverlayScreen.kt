package com.life.mindfulnessapp.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.ui.theme.CapabilityForm
import com.life.mindfulnessapp.ui.theme.CapabilityKind
import com.life.mindfulnessapp.ui.theme.CapabilityMark
import com.life.mindfulnessapp.ui.theme.MindfulnessAppTheme
import kotlinx.coroutines.delay

/**
 * 时间用完后的全屏浮窗（主题色联动版）。
 *
 * 若今日还有剩余修改机会（[remainingModifyCount] > 0），则展示"重新设定目标"按钮，
 * 点击后直接触发 [onReset] 回调，由 Service 负责关闭浮窗并跳转到 App 限制设置页。
 *
 * @param todayUsedSeconds       今日已使用秒数
 * @param remainingModifyCount   今日剩余可修改次数（0 = 不再显示修改按钮）
 * @param themeId                保留以兼容调用方，始终使用极简 UI
 * @param isDarkTheme            是否夜间模式
 * @param onReset                用户点击"重新设定"时的回调（无参数，跳转到设置页完成）
 * @param onDismiss              用户选择离开时的回调
 * @param onOpenOwnApp           离开后打开心锚
 * @param onContinueOverLimit    用户明确选择「超限继续使用」时的回调（null = 不展示此选项）；
 *                               点击后将开启超限续记 session，后续时长照常记录
 */
@Composable
fun LimitReachedOverlayScreen(
    todayUsedSeconds: Long,
    remainingModifyCount: Int = 0,
    themeId: String = "default",
    isDarkTheme: Boolean = true,
    onReset: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onOpenOwnApp: (() -> Unit)? = null,
    onContinueOverLimit: (() -> Unit)? = null
) {
    SimpleLimitReachedScreen(
        todayUsedSeconds = todayUsedSeconds,
        remainingModifyCount = remainingModifyCount,
        isDarkTheme = isDarkTheme,
        onReset = onReset,
        onDismiss = onDismiss,
        onOpenOwnApp = onOpenOwnApp,
        onContinueOverLimit = onContinueOverLimit
    )
}

/** 将分钟数格式化为"X小时Y分"或"Y分钟"的可读字符串 */
private fun formatMinutes(minutes: Int): String {
    return if (minutes >= 60) {
        val h = minutes / 60
        val m = minutes % 60
        if (m > 0) "${h}小时${m}分" else "${h}小时"
    } else {
        "${minutes}分钟"
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  iOS 极简风格 · 超限拦截页（日间/夜间双模）
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun SimpleLimitReachedScreen(
    todayUsedSeconds: Long,
    remainingModifyCount: Int = 0,
    isDarkTheme: Boolean = true,
    onReset: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onOpenOwnApp: (() -> Unit)? = null,
    onContinueOverLimit: (() -> Unit)? = null
) {
    val themeConfig = remember(isDarkTheme) { getInterceptThemeConfig("simple", isDarkTheme) }
    val todayUsedMinutes = todayUsedSeconds / 60

    val enterAlpha = remember { Animatable(0f) }
    var showContent by remember { mutableStateOf(false) }
    var showButtons by remember { mutableStateOf(false) }
    var isDismissing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        enterAlpha.animateTo(1f, tween(280))
        delay(80)
        showContent = true
        delay(180)
        showButtons = true
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
                Spacer(modifier = Modifier.fillMaxHeight(0.18f))

                AnimatedVisibility(
                    visible = showContent,
                    enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 3 }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = formatMinutes(todayUsedMinutes.toInt()),
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Light,
                            color = themeConfig.limitAccentColor,
                            letterSpacing = (-2).sp,
                            textAlign = TextAlign.Center
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CapabilityMark(
                                kind = CapabilityKind.TimeLock,
                                form = CapabilityForm.Emphasis,
                                tint = themeConfig.limitAccentColor
                            )
                            Text(
                                text = "时间到了",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = themeConfig.textPrimary,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "今天的时间配额已经用完",
                                fontSize = 16.sp,
                                color = themeConfig.textSecondary,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        HorizontalDivider(
                            color = themeConfig.dividerColor,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Text(
                            text = "守住了 · 额度用完",
                            fontSize = 15.sp,
                            color = themeConfig.textSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )

                        HorizontalDivider(
                            color = themeConfig.dividerColor,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Text(
                            text = "把时间还给此刻真正重要的事",
                            fontSize = 14.sp,
                            color = themeConfig.textTertiary,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }
                }
                if (!showContent) Spacer(modifier = Modifier.height(280.dp))

                Spacer(modifier = Modifier.weight(1f))

                AnimatedVisibility(
                    visible = showButtons,
                    enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 3 }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 52.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "去做点别的",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = themeConfig.textTertiary,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (!isDismissing) {
                                        isDismissing = true
                                        onDismiss()
                                    }
                                },
                                enabled = !isDismissing,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = themeConfig.limitAccentColor,
                                    disabledContainerColor = themeConfig.dividerColor
                                ),
                                shape = RoundedCornerShape(14.dp),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                            ) {
                                Text(
                                    text = "回到桌面",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = themeConfig.limitAccentForeground,
                                    maxLines = 1
                                )
                            }
                            if (onOpenOwnApp != null) {
                                Button(
                                    onClick = {
                                        if (!isDismissing) {
                                            isDismissing = true
                                            onOpenOwnApp()
                                        }
                                    },
                                    enabled = !isDismissing,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = themeConfig.surfaceColor,
                                        disabledContainerColor = themeConfig.dividerColor
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                                ) {
                                    Text(
                                        text = "打开心锚",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = themeConfig.textPrimary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        if (remainingModifyCount > 0 && onReset != null) {
                            TextButton(
                                onClick = { onReset() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "调整今日目标（还剩 $remainingModifyCount 次）",
                                    fontSize = 14.sp,
                                    color = themeConfig.accentColor
                                )
                            }
                        }

                        if (onContinueOverLimit != null) {
                            TextButton(
                                onClick = {
                                    if (!isDismissing) {
                                        isDismissing = true
                                        onContinueOverLimit()
                                    }
                                },
                                enabled = !isDismissing,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "我知道超了，继续使用",
                                    fontSize = 13.sp,
                                    color = themeConfig.textTertiary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
