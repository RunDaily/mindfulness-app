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
 * 时段锁生效期间的全屏拦截形态。
 *
 * **不提供破界入口**——解锁只能去心锚配置里关闭时段（生效中关闭会触发门槛）。
 * 本页只鼓励守住：回桌面 / 打开心锚。
 *
 * 英雄信号：锁定窗口时刻；情感中心：开启时的承诺。
 */
@Composable
fun PeriodLockOverlayScreen(
    windowLabel: String,
    daysLabel: String,
    commitment: String,
    remainingUnlockLabel: String,
    appName: String = "",
    isDarkTheme: Boolean = true,
    onDismiss: () -> Unit,
    onOpenOwnApp: (() -> Unit)? = null
) {
    val themeConfig = remember(isDarkTheme) { getInterceptThemeConfig("simple", isDarkTheme) }
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
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.fillMaxHeight(0.14f))

                AnimatedVisibility(
                    visible = showContent,
                    enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 3 }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = windowLabel.replace(" – ", "–"),
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Light,
                            color = themeConfig.limitAccentColor,
                            letterSpacing = (-1).sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CapabilityMark(
                                kind = CapabilityKind.PeriodLock,
                                form = CapabilityForm.Emphasis,
                                tint = themeConfig.limitAccentColor
                            )
                            Text(
                                text = "此时段已锁定",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = themeConfig.textPrimary,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = buildString {
                                    append(daysLabel)
                                    append(" · ")
                                    append(remainingUnlockLabel)
                                    if (appName.isNotBlank()) {
                                        append("\n")
                                        append(appName)
                                    }
                                },
                                fontSize = 15.sp,
                                color = themeConfig.textSecondary,
                                textAlign = TextAlign.Center,
                                lineHeight = 22.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        HorizontalDivider(
                            color = themeConfig.dividerColor,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )

                        if (commitment.isNotBlank()) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = "你守护的是",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = themeConfig.textTertiary,
                                    letterSpacing = 0.6.sp
                                )
                                Text(
                                    text = "「$commitment」",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = themeConfig.textPrimary,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 30.sp
                                )
                            }
                        } else {
                            Text(
                                text = "把这段时间留给更重要的事",
                                fontSize = 15.sp,
                                color = themeConfig.textSecondary,
                                textAlign = TextAlign.Center
                            )
                        }

                        HorizontalDivider(
                            color = themeConfig.dividerColor,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )

                        Text(
                            text = "守住了 · 时段未到",
                            fontSize = 15.sp,
                            color = themeConfig.textSecondary,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "若要调整，请打开心锚，在监控配置中关闭此时段",
                            fontSize = 13.sp,
                            color = themeConfig.textTertiary,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                if (!showContent) Spacer(modifier = Modifier.height(300.dp))

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
                            text = "守住时段",
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
                    }
                }
            }
        }
    }
}
