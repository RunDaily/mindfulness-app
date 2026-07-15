package com.life.mindfulnessapp.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.domain.usecase.GetAppHistoryUsageUseCase
import com.life.mindfulnessapp.ui.theme.MindfulnessAppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars

// ── 通用深色基底（兜底色，不依赖主题时使用） ──────────────────────────────────
private val LimitDarkBg = Color(0xFF111318)
private val LimitDarkSurface = Color(0xFF1E2130)
private val LimitDarkSurfaceVariant = Color(0xFF252840)
private val LimitTextPrimary = Color(0xFFF0F4F8)
private val LimitTextSecondary = Color(0xFF8E99B0)
private val LimitTextMuted = Color(0xFF4A5468)

/**
 * 时间用完后的全屏浮窗（主题色联动版）。
 *
 * 若今日还有剩余修改机会（[remainingModifyCount] > 0），则展示"重新设定目标"按钮，
 * 点击后直接触发 [onReset] 回调，由 Service 负责关闭浮窗并跳转到 App 限制设置页。
 *
 * @param todayUsedSeconds       今日已使用秒数
 * @param remainingModifyCount   今日剩余可修改次数（0 = 不再显示修改按钮）
 * @param themeId                当前拦截主题 ID，用于主题化全页配色
 * @param isDarkTheme            是否夜间模式（仅 simple 主题有效）
 * @param onReset                用户点击"重新设定"时的回调（无参数，跳转到设置页完成）
 * @param onDismiss              用户选择离开时的回调
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
    onContinueOverLimit: (() -> Unit)? = null
) {
    val isSimpleTheme = themeId == "simple"

    // simple 主题走专用的极简页面
    if (isSimpleTheme) {
        SimpleLimitReachedScreen(
            todayUsedSeconds = todayUsedSeconds,
            remainingModifyCount = remainingModifyCount,
            isDarkTheme = isDarkTheme,
            onReset = onReset,
            onDismiss = onDismiss,
            onContinueOverLimit = onContinueOverLimit
        )
        return
    }

    // ── 读取主题配置 ──────────────────────────────────────────────────────
    val themeConfig = remember(themeId, isDarkTheme) { getInterceptThemeConfig(themeId, isDarkTheme) }

    val todayUsedMinutes = todayUsedSeconds / 60

    // ── 分层入场动画状态 ────────────────────────────────────────────────────
    val bgEnterAlpha = remember { Animatable(0f) }
    val iconScale = remember { Animatable(0f) }
    var showTitle by remember { mutableStateOf(false) }
    var showCards by remember { mutableStateOf(false) }
    var showButtons by remember { mutableStateOf(false) }

    // 拦截页保持全程不透明，dismiss 路径不做任何淡出动画
    var isDismissing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        bgEnterAlpha.animateTo(1f, animationSpec = tween(350))
        delay(100)
        iconScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
        delay(120)
        showTitle = true
        delay(200)
        showCards = true
        delay(200)
        showButtons = true
    }

    // ── 主题化本地派生色 ──────────────────────────────────────────────────
    val bgColor        = themeConfig.bgColor
    val surfaceColor   = themeConfig.surfaceColor
    val accentColor    = themeConfig.accentColor
    val borderColor    = themeConfig.dividerColor

    val limitIcon = "⏰"

    // 主标题文字（使用 themeConfig 的 limitTitleText）
    val titleText = themeConfig.limitTitleText

    val subtitleText = "今日的时间配额用完了 🌱"

    val praiseText = "很棒！你守住了自己的承诺 ✨"

    // 离开按钮文案（直接复用 themeConfig）
    val dismissButtonText = themeConfig.dismissButtonText

    MindfulnessAppTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // dismiss 路径不修改 alpha，拦截页保持全程不透明，防止用户看到 App 界面
                .alpha(bgEnterAlpha.value)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            // 主题背景动效层（与拦截页一致）
            ThemeBackground(themeId = themeId, modifier = Modifier.fillMaxSize())

            // 状态栏遮罩：与主题背景色完全一致，让状态栏无缝融入
            val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusBarHeight)
                    .background(bgColor)
                    .align(Alignment.TopStart)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // ── 1. 图标：spring 弹跳入场 ─────────────────────────────────
                Box(
                    modifier = Modifier
                        .scale(iconScale.value)
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(surfaceColor)
                        .then(
                            // 主题边框光晕
                            Modifier.then(Modifier)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(limitIcon, fontSize = 42.sp)
                }

                Spacer(modifier = Modifier.height(28.dp))

                // ── 2. 大标题：淡入上滑 ───────────────────────────────────────
                AnimatedVisibility(
                    visible = showTitle,
                    enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 2 }
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = titleText,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            color = LimitTextPrimary,
                            textAlign = TextAlign.Center,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = subtitleText,
                            fontSize = 15.sp,
                            color = LimitTextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                if (!showTitle) Spacer(modifier = Modifier.height(62.dp))

                Spacer(modifier = Modifier.height(24.dp))

                // ── 3. 信息卡片：从下滑入 ───────────────────────────────────
                AnimatedVisibility(
                    visible = showCards,
                    enter = fadeIn(tween(350)) + slideInVertically(tween(350)) { it }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // 使用时长信息卡片
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
.background(themeConfig.surfaceColor)
.border(1.dp, borderColor, RoundedCornerShape(16.dp))
.padding(18.dp),
horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "你今天已使用 ${formatMinutes(todayUsedMinutes.toInt())}",
                                fontSize = 15.sp,
                                color = LimitTextSecondary,
                                textAlign = TextAlign.Center
                            )
                            HorizontalDivider(
                                color = borderColor,
                                thickness = 1.dp
                            )
                            Text(
                                text = praiseText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = accentColor,
                                textAlign = TextAlign.Center
                            )
                        }

                        // 离开手机建议卡片
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
.background(themeConfig.surfaceColor)
.border(1.dp, borderColor, RoundedCornerShape(16.dp))
.padding(16.dp),
horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "离开手机，你可以...",
                                fontSize = 13.sp,
                                color = LimitTextMuted,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "散步 · 阅读 · 冥想 · 与人面对面交流",
                                fontSize = 15.sp,
                                color = LimitTextSecondary
                            )
                        }
                    }
                }
                if (!showCards) Spacer(modifier = Modifier.height(148.dp))

                Spacer(modifier = Modifier.height(32.dp))

                // ── 4. 按钮区：淡入上滑 ─────────────────────────────────────
                AnimatedVisibility(
                    visible = showButtons,
                    enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 2 }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        // 主按钮（主题强调色）
                        Button(
                            onClick = {
                                if (!isDismissing) {
                                    // 拦截页保持不透明，先 pressHomeButton 让 App 退出，
                                    // OverlayManager 延迟后才移除 View（此时下面是桌面，不是 App）
                                    isDismissing = true
                                    onDismiss()
                                }
                            },
                            enabled = !isDismissing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor
                            ),
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            Text(
                                dismissButtonText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Black.copy(alpha = 0.85f)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 次要按钮：重新设定目标（弱化样式）
                        AnimatedVisibility(
                            visible = remainingModifyCount > 0 && onReset != null,
                            enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 2 },
                            exit = fadeOut()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onReset?.invoke() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = LimitTextSecondary
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        borderColor
                                    ),
                                    shape = RoundedCornerShape(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Tune,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = LimitTextSecondary
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "重新设定今日目标",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = LimitTextSecondary
                                    )
                                }
                                Text(
                                    text = "今日还有 $remainingModifyCount 次调整机会",
                                    fontSize = 12.sp,
                                    color = LimitTextMuted
                                )
                            }
                        }

                        // 最弱化入口：超限后仍坚持继续使用（时长照常记录）
                        // 仅在外部传入 onContinueOverLimit 时展示，且点击后立即关闭本页
                        AnimatedVisibility(
                            visible = onContinueOverLimit != null,
                            enter = fadeIn(tween(600)),
                            exit = fadeOut()
                        ) {
                            TextButton(
                                onClick = {
                                    if (!isDismissing) {
                                        isDismissing = true
                                        onContinueOverLimit?.invoke()
                                    }
                                },
                                enabled = !isDismissing,
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .fillMaxWidth()
                            ) {
                                Text(
                                    text = "我知道超了，继续使用（将记录时长）",
                                    fontSize = 12.sp,
                                    color = LimitTextMuted,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

    }
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
//
//  设计理念：
//  · 日间：纯白/浅灰底，深色文字，系统蓝/红按钮，接近 iOS Screen Time 弹窗
//  · 夜间：纯黑底，白色文字，蓝色强调色
//  · 去掉所有卡片边框、渐变、光晕；信息层次靠字号和透明度区分
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun SimpleLimitReachedScreen(
    todayUsedSeconds: Long,
    remainingModifyCount: Int = 0,
    isDarkTheme: Boolean = true,
    onReset: (() -> Unit)? = null,
    onDismiss: () -> Unit,
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
                // 顶部留白（约 1/5 屏高）
                Spacer(modifier = Modifier.fillMaxHeight(0.18f))

                // ── 核心信息区 ─────────────────────────────────────────────
                AnimatedVisibility(
                    visible = showContent,
                    enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 3 }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 大号时长显示
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

                        // 分割线
                        HorizontalDivider(
                            color = themeConfig.dividerColor,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        // 鼓励文字
                        Text(
                            text = "很棒，你守住了自己的承诺 ✨",
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

                        // 离开建议
                        Text(
                            text = "现在你可以：散步、阅读、冥想\n或者与人面对面交流",
                            fontSize = 14.sp,
                            color = themeConfig.textTertiary,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }
                }
                if (!showContent) Spacer(modifier = Modifier.height(280.dp))

                Spacer(modifier = Modifier.weight(1f))

                // ── 底部按钮区 ─────────────────────────────────────────────
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
                        // 主按钮：离开
                        Button(
                            onClick = {
                                if (!isDismissing) {
                                    isDismissing = true
                                    onDismiss()
                                }
                            },
                            enabled = !isDismissing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = themeConfig.limitAccentColor,
                                disabledContainerColor = themeConfig.dividerColor
                            ),
                            shape = RoundedCornerShape(14.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                        ) {
                            Text(
                                text = "好的，离开",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = themeConfig.limitAccentForeground
                            )
                        }

                        // 次要按钮：重设目标
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

                        // 最弱化：超限继续
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

// ════════════════════════════════════════════════════════════════════════════
//  禅模式 · 超限拦截页（时间用完）
//
//  设计哲学：少即是多。
//  · 纯黑背景，无任何装饰粒子
//  · 唯一动效：屏幕正中央一个缓慢「呼吸」的描边圆，引导深呼吸
//  · 一句话：大字留白，无卡片、无边框、无图标
//  · 一个动作：底部极淡的文字按钮，仅写「离开」
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun ZenLimitReachedOverlayScreen(
    todayUsedSeconds: Long,
    remainingModifyCount: Int = 0,
    onReset: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val enterAlpha = remember { Animatable(0f) }
    var contentVisible by remember { mutableStateOf(false) }
    var isDismissing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        enterAlpha.animateTo(1f, tween(600))
        delay(200)
        contentVisible = true
    }

    val breathTransition = rememberInfiniteTransition(label = "zen_breath_limit")
    val breathScale by breathTransition.animateFloat(
        initialValue = 0.82f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "zbl_scale"
    )
    val breathStrokeAlpha by breathTransition.animateFloat(
        initialValue = 0.10f, targetValue = 0.28f,
        animationSpec = infiniteRepeatable(
            tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "zbl_alpha"
    )

    val zenQuote = remember {
        listOf(
            "此刻，已经足够。",
            "放下手机，\n拾起此刻。",
            "停止滚动，\n开始呼吸。",
            "你不需要更多内容，\n你需要更多空间。",
            "世界在你放下屏幕后\n依然存在。"
        ).random()
    }

    val todayUsedMinutes = todayUsedSeconds / 60

    MindfulnessAppTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(enterAlpha.value)
                .background(Color(0xFF0A0A0A)),
            contentAlignment = Alignment.Center
        ) {
            val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusBarHeight)
                    .background(Color(0xFF0A0A0A))
                    .align(Alignment.TopStart)
            )

            // 呼吸圆
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height * 0.38f
                val r = size.minDimension * 0.30f * breathScale
                drawCircle(
                    color = Color.White.copy(alpha = breathStrokeAlpha),
                    radius = r,
                    center = Offset(cx, cy),
                    style = Stroke(width = 0.8.dp.toPx())
                )
                drawCircle(
                    color = Color.White.copy(alpha = breathStrokeAlpha * 0.4f),
                    radius = r * 0.60f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 0.5.dp.toPx())
                )
            }

            // 正文内容
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.fillMaxHeight(0.30f))

                AnimatedVisibility(
                    visible = contentVisible,
                    enter = fadeIn(tween(800))
                ) {
                    Text(
                        text = zenQuote,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Light,
                        color = Color(0xFFEEEEEE),
                        textAlign = TextAlign.Center,
                        lineHeight = 42.sp,
                        letterSpacing = 0.sp
                    )
                }

                Spacer(modifier = Modifier.height(36.dp))

                AnimatedVisibility(
                    visible = contentVisible,
                    enter = fadeIn(tween(1200))
                ) {
                    Text(
                        text = "今日已用 ${formatMinutes(todayUsedMinutes.toInt())}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Light,
                        color = Color(0xFF555555),
                        textAlign = TextAlign.Center,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // 底部动作区
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 52.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AnimatedVisibility(
                    visible = contentVisible,
                    enter = fadeIn(tween(1400))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            onClick = {
                                if (!isDismissing) { isDismissing = true; onDismiss() }
                            },
                            enabled = !isDismissing
                        ) {
                            Text(
                                text = "离开",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Light,
                                color = Color(0xFF888888),
                                letterSpacing = 2.sp
                            )
                        }
                        if (remainingModifyCount > 0 && onReset != null) {
                            TextButton(onClick = { onReset() }) {
                                Text(
                                    text = "重设今日目标（剩余 $remainingModifyCount 次）",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Light,
                                    color = Color(0xFF3C3C3C),
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  禅模式 · 普通拦截页（进入 App 前的意图确认）
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun ZenInterceptOverlayScreen(
    appName: String,
    todayUsedSeconds: Long,
    dailyLimitMinutes: Int,
    remainingModifyCount: Int = 0,
    onReset: (() -> Unit)? = null,
    onContinue: (purpose: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val enterAlpha = remember { Animatable(0f) }
    var contentVisible by remember { mutableStateOf(false) }
    var inputVisible by remember { mutableStateOf(false) }
    var buttonsVisible by remember { mutableStateOf(false) }
    var isDismissing by remember { mutableStateOf(false) }
    var isEntering by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        enterAlpha.animateTo(1f, tween(500))
        delay(100); contentVisible = true
        delay(400); inputVisible = true
        delay(300); buttonsVisible = true
    }

    val breathTransition = rememberInfiniteTransition(label = "zen_intercept_bt")
    val breathScale by breathTransition.animateFloat(
        initialValue = 0.82f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "zib_scale"
    )
    val breathStrokeAlpha by breathTransition.animateFloat(
        initialValue = 0.08f, targetValue = 0.22f,
        animationSpec = infiniteRepeatable(
            tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "zib_alpha"
    )

    var intentText by remember { mutableStateOf("") }
    val confirmedPurpose: String? = if (intentText.isNotBlank()) intentText.trim() else null

    var cooldownRemaining by remember { mutableIntStateOf(3) }
    LaunchedEffect(Unit) {
        repeat(3) { delay(1000); cooldownRemaining-- }
    }
    LaunchedEffect(confirmedPurpose) {
        if (confirmedPurpose != null && cooldownRemaining > 1) {
            delay(300)
            cooldownRemaining = cooldownRemaining.coerceAtMost(1)
        }
    }
    val canContinue = confirmedPurpose != null
    val buttonEnabled = canContinue && !isEntering && !isDismissing && cooldownRemaining == 0

    val todayUsedMinutes = todayUsedSeconds / 60
    val dailyProgress = if (dailyLimitMinutes > 0)
        (todayUsedMinutes.toFloat() / dailyLimitMinutes).coerceAtMost(1f) else 0f

    val circleColor = when {
        dailyProgress >= 0.85f -> Color(0xFFCC8866)
        dailyProgress >= 0.60f -> Color(0xFF999999)
        else                   -> Color(0xFFCCCCCC)
    }

    MindfulnessAppTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(enterAlpha.value)
                .background(Color(0xFF0A0A0A)),
            contentAlignment = Alignment.Center
        ) {
            val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusBarHeight)
                    .background(Color(0xFF0A0A0A))
                    .align(Alignment.TopStart)
            )

            // 呼吸圆 + 进度弧
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height * 0.35f
                val r = size.minDimension * 0.28f * breathScale
                drawCircle(
                    color = circleColor.copy(alpha = breathStrokeAlpha),
                    radius = r,
                    center = Offset(cx, cy),
                    style = Stroke(width = 0.8.dp.toPx())
                )
                drawCircle(
                    color = circleColor.copy(alpha = breathStrokeAlpha * 0.35f),
                    radius = r * 0.58f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 0.5.dp.toPx())
                )
                if (dailyProgress > 0f && dailyLimitMinutes > 0) {
                    val sweepAngle = 360f * dailyProgress
                    drawArc(
                        color = circleColor.copy(alpha = breathStrokeAlpha * 0.6f),
                        startAngle = -90f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = Offset(cx - r, cy - r),
                        size = Size(r * 2f, r * 2f),
                        style = Stroke(
                            width = 1.2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .imePadding()
                    .padding(horizontal = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.fillMaxHeight(0.28f))

                AnimatedVisibility(
                    visible = contentVisible,
                    enter = fadeIn(tween(700))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = appName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Light,
                            color = Color(0xFF555555),
                            letterSpacing = 3.sp
                        )
                        Text(
                            text = "此刻打开它\n是为了什么？",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Light,
                            color = Color(0xFFEEEEEE),
                            textAlign = TextAlign.Center,
                            lineHeight = 40.sp,
                            letterSpacing = 0.sp
                        )
                        if (dailyLimitMinutes > 0) {
                            Text(
                                text = "今日已用 ${formatMinutes(todayUsedMinutes.toInt())} / ${formatMinutes(dailyLimitMinutes)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Light,
                                color = Color(0xFF3C3C3C),
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(44.dp))

                // 目的输入框
                AnimatedVisibility(
                    visible = inputVisible,
                    enter = fadeIn(tween(600))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        OutlinedTextField(
                            value = intentText,
                            onValueChange = { if (it.length <= 40) intentText = it },
                            placeholder = {
                                Text(
                                    "写下你的意图…",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Light,
                                    color = Color(0xFF333333)
                                )
                            },
                            textStyle = TextStyle(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Light,
                                color = Color(0xFFCCCCCC),
                                letterSpacing = 0.3.sp,
                                textAlign = TextAlign.Center
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 1,
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF333333),
                                unfocusedBorderColor = Color(0xFF222222),
                                cursorColor = Color(0xFF888888),
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(4.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "（可选，填写后等待缩短）",
                            fontSize = 11.sp,
                            color = Color(0xFF2A2A2A),
                            fontWeight = FontWeight.Light,
                            letterSpacing = 0.3.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                // 操作按钮
                AnimatedVisibility(
                    visible = buttonsVisible,
                    enter = fadeIn(tween(700))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextButton(
                            onClick = {
                                if (buttonEnabled) { isEntering = true; onContinue(confirmedPurpose) }
                            },
                            enabled = buttonEnabled
                        ) {
                            val label = when {
                                cooldownRemaining > 0 -> "$cooldownRemaining…"
                                !canContinue         -> "写下意图后继续"
                                else                 -> "进入"
                            }
                            Text(
                                text = label,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Light,
                                color = if (buttonEnabled) Color(0xFFAAAAAA) else Color(0xFF3C3C3C),
                                letterSpacing = 2.sp
                            )
                        }
                        TextButton(
                            onClick = {
                                if (!isDismissing && !isEntering) { isDismissing = true; onDismiss() }
                            },
                            enabled = !isDismissing && !isEntering
                        ) {
                            Text(
                                text = "算了，离开",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Light,
                                color = Color(0xFF333333),
                                letterSpacing = 1.sp
                            )
                        }
                        if (remainingModifyCount > 0 && onReset != null) {
                            TextButton(onClick = { onReset() }) {
                                Text(
                                    text = "重设今日目标（剩余 $remainingModifyCount 次）",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Light,
                                    color = Color(0xFF2A2A2A),
                                    letterSpacing = 0.3.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  仪表盘模式 · 超限拦截页（时间用完）
//
//  设计语言：工业仪表盘 / LCD 面板 / 琥珀终端
//  ·  纯黑背景 #080808，所有文字使用等宽字体
//  ·  顶部状态栏：[ SYSTEM: TIMELIMIT ] 居中
//  ·  核心区域：大号单行等宽数字显示今日耗时
//  ·  进度条：实心方块 ██████░░░░ 样式，琥珀黄
//  ·  底部 [ EXIT ] 按钮，方框包裹，等宽字
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun GaugeLimitReachedOverlayScreen(
    todayUsedSeconds: Long,
    remainingModifyCount: Int = 0,
    onReset: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val GaugeAmber   = Color(0xFFFFB300)
    val GaugeAmberDim = Color(0xFFCC8800)
    val GaugeBg      = Color(0xFF080808)
    val GaugeText    = Color(0xFFFFCC44)
    val GaugeMuted   = Color(0xFF3A2A00)
    val GaugeBorder  = Color(0xFF2A2000)

    // ── 入场 ─────────────────────────────────────────────────────────────
    val enterAlpha = remember { Animatable(0f) }
    var rowVisible by remember { mutableStateOf(false) }
    var barVisible by remember { mutableStateOf(false) }
    var btnVisible by remember { mutableStateOf(false) }
    var isDismissing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        enterAlpha.animateTo(1f, tween(300))
        delay(100); rowVisible = true
        delay(180); barVisible = true
        delay(300); btnVisible = true
    }

    // ── 数据 ─────────────────────────────────────────────────────────────
    val todayUsedMinutes = todayUsedSeconds / 60
    val hh = (todayUsedMinutes / 60).toString().padStart(2, '0')
    val mm = (todayUsedMinutes % 60).toString().padStart(2, '0')
    val ss = (todayUsedSeconds % 60).toString().padStart(2, '0')

    // 超限时进度条始终满格
    val filledBlocks = 10
    val barStr = "█".repeat(filledBlocks) + "░".repeat(0)

    MindfulnessAppTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(enterAlpha.value)
                .background(GaugeBg)
        ) {
            // ── 状态栏遮色 ────────────────────────────────────────────────
            val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusBarHeight)
                    .background(GaugeBg)
                    .align(Alignment.TopStart)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 28.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // ── 顶部系统头 ───────────────────────────────────────────
                Column(modifier = Modifier.fillMaxWidth()) {
                    AnimatedVisibility(visible = rowVisible, enter = fadeIn(tween(300))) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // 标题行
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, GaugeBorder)
                                    .background(Color(0xFF0D0D00))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SYS:TIMELIMIT",
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = GaugeAmberDim,
                                    letterSpacing = 1.5.sp
                                )
                                Text(
                                    text = "STATUS:LOCKED",
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color(0xFFCC4400),
                                    letterSpacing = 1.5.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // 核心标题
                            Text(
                                text = "[ LIMIT  REACHED ]",
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = GaugeAmber,
                                letterSpacing = 2.sp,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(40.dp))

                            // ── 大时钟 ───────────────────────────────────
                            Text(
                                text = "$hh:$mm:$ss",
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 56.sp,
                                fontWeight = FontWeight.Bold,
                                color = GaugeText,
                                letterSpacing = 4.sp,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "TODAY_USAGE",
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = GaugeMuted,
                                letterSpacing = 2.sp,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(32.dp))

                            // ── 进度条 ───────────────────────────────────
                            AnimatedVisibility(visible = barVisible, enter = fadeIn(tween(400))) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "USAGE",
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            color = GaugeMuted,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            text = "100%",
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            color = Color(0xFFCC4400),
                                            letterSpacing = 1.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, GaugeBorder)
                                            .background(Color(0xFF0D0D00))
                                            .padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = barStr,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            fontSize = 14.sp,
                                            color = Color(0xFFCC4400),
                                            letterSpacing = 2.sp,
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── 底部操作区 ───────────────────────────────────────────
                AnimatedVisibility(visible = btnVisible, enter = fadeIn(tween(500))) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, GaugeAmber.copy(alpha = 0.35f))
                                .background(Color(0xFF0D0A00))
                                .clickable { if (!isDismissing) { isDismissing = true; onDismiss() } }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "[ EXIT ]",
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDismissing) GaugeMuted else GaugeAmber,
                                letterSpacing = 3.sp
                            )
                        }
                        if (remainingModifyCount > 0 && onReset != null) {
                            Text(
                                text = "> RESET_LIMIT  ($remainingModifyCount remaining)",
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = GaugeMuted,
                                letterSpacing = 1.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onReset() },
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  仪表盘模式 · 普通拦截页（进入 App 前的意图确认）
//
//  设计语言：工业仪表盘 / LCD 面板 / 琥珀终端
//  ·  顶部状态栏：SYS  /  APP名  /  今日剩余百分比
//  ·  方块进度条显示今日用量百分比
//  ·  意图输入框：等宽字、细方框
//  ·  底部 [ PROCEED ] 和 < EXIT 按钮
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun GaugeInterceptOverlayScreen(
    appName: String,
    todayUsedSeconds: Long,
    dailyLimitMinutes: Int,
    remainingModifyCount: Int = 0,
    onReset: (() -> Unit)? = null,
    onContinue: (purpose: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val GaugeAmber    = Color(0xFFFFB300)
    val GaugeAmberDim = Color(0xFFCC8800)
    val GaugeBg       = Color(0xFF080808)
    val GaugeText     = Color(0xFFFFCC44)
    val GaugeMuted    = Color(0xFF3A2A00)
    val GaugeBorder   = Color(0xFF2A2000)
    val GaugeSurface  = Color(0xFF0D0D00)

    // ── 入场 ─────────────────────────────────────────────────────────────
    val enterAlpha = remember { Animatable(0f) }
    var headerVisible by remember { mutableStateOf(false) }
    var barVisible by remember { mutableStateOf(false) }
    var inputVisible by remember { mutableStateOf(false) }
    var btnVisible by remember { mutableStateOf(false) }
    var isDismissing by remember { mutableStateOf(false) }
    var isEntering by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        enterAlpha.animateTo(1f, tween(300))
        delay(80);  headerVisible = true
        delay(160); barVisible = true
        delay(200); inputVisible = true
        delay(220); btnVisible = true
    }

    // ── 意图输入 + 冷静倒计时 ────────────────────────────────────────────
    var intentText by remember { mutableStateOf("") }
    val confirmedPurpose: String? = if (intentText.isNotBlank()) intentText.trim() else null
    var cooldownRemaining by remember { mutableIntStateOf(3) }
    LaunchedEffect(Unit) { repeat(3) { delay(1000); cooldownRemaining-- } }
    LaunchedEffect(confirmedPurpose) {
        if (confirmedPurpose != null && cooldownRemaining > 1) {
            delay(300); cooldownRemaining = cooldownRemaining.coerceAtMost(1)
        }
    }
    val canContinue = confirmedPurpose != null
    val buttonEnabled = canContinue && !isEntering && !isDismissing && cooldownRemaining == 0

    // ── 进度数据 ─────────────────────────────────────────────────────────
    val todayUsedMinutes = todayUsedSeconds / 60
    val dailyProgress = if (dailyLimitMinutes > 0)
        (todayUsedMinutes.toFloat() / dailyLimitMinutes).coerceAtMost(1f) else 0f
    val hh = (todayUsedMinutes / 60).toString().padStart(2, '0')
    val mm = (todayUsedMinutes % 60).toString().padStart(2, '0')
    val pct = (dailyProgress * 100).toInt()

    // 10 格方块进度条
    val filled = (dailyProgress * 10).toInt().coerceIn(0, 10)
    val barStr = "█".repeat(filled) + "░".repeat(10 - filled)

    // 进度颜色：低→琥珀，中→橙，高→红
    val barColor = when {
        dailyProgress >= 0.85f -> Color(0xFFCC4400)
        dailyProgress >= 0.60f -> Color(0xFFDD7700)
        else                   -> GaugeAmber
    }

    val appNameDisplay = appName.uppercase().take(14)

    MindfulnessAppTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(enterAlpha.value)
                .background(GaugeBg)
        ) {
            // ── 状态栏遮色 ────────────────────────────────────────────────
            val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusBarHeight)
                    .background(GaugeBg)
                    .align(Alignment.TopStart)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 28.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // ── 主内容区 ─────────────────────────────────────────────
                Column(modifier = Modifier.fillMaxWidth()) {

                    // 顶部状态栏
                    AnimatedVisibility(visible = headerVisible, enter = fadeIn(tween(280))) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, GaugeBorder)
                                    .background(GaugeSurface)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SYS:TIMELIMIT",
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = GaugeAmberDim,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "APP:$appNameDisplay",
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = GaugeText,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "USED:${pct}%",
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = barColor,
                                    letterSpacing = 1.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(22.dp))

                            // 核心标题
                            Text(
                                text = "[ PAUSE ]",
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = GaugeAmber,
                                letterSpacing = 3.sp,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(30.dp))

                            // 大时钟（今日已用）
                            Text(
                                text = "$hh:$mm",
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 54.sp,
                                fontWeight = FontWeight.Bold,
                                color = GaugeText,
                                letterSpacing = 4.sp,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "TODAY_USAGE  /  ${if (dailyLimitMinutes > 0) "${dailyLimitMinutes / 60}:${(dailyLimitMinutes % 60).toString().padStart(2, '0')}" else "--:--"}  LIMIT",
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = GaugeMuted,
                                letterSpacing = 1.sp,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            // 进度条
                            AnimatedVisibility(visible = barVisible, enter = fadeIn(tween(350))) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "USAGE",
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            color = GaugeMuted,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            text = "$pct%  [$filled/10]",
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            color = barColor,
                                            letterSpacing = 1.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(5.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, GaugeBorder)
                                            .background(GaugeSurface)
                                            .padding(horizontal = 10.dp, vertical = 7.dp)
                                    ) {
                                        Text(
                                            text = barStr,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            fontSize = 14.sp,
                                            color = barColor,
                                            letterSpacing = 2.sp,
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(30.dp))

                            // 意图输入框
                            AnimatedVisibility(visible = inputVisible, enter = fadeIn(tween(400))) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "> INPUT_PURPOSE:",
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = GaugeMuted,
                                        letterSpacing = 1.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = intentText,
                                        onValueChange = { if (it.length <= 40) intentText = it },
                                        placeholder = {
                                            Text(
                                                "_",
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                fontSize = 14.sp,
                                                color = GaugeMuted
                                            )
                                        },
                                        textStyle = TextStyle(
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            fontSize = 14.sp,
                                            color = GaugeText,
                                            letterSpacing = 1.sp
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        maxLines = 1,
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = GaugeAmber.copy(alpha = 0.6f),
                                            unfocusedBorderColor = GaugeBorder,
                                            cursorColor = GaugeAmber,
                                            focusedContainerColor = GaugeSurface,
                                            unfocusedContainerColor = GaugeSurface
                                        ),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (confirmedPurpose != null)
                                            "// PURPOSE CONFIRMED"
                                        else
                                            "// REQUIRED TO PROCEED",
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        color = if (confirmedPurpose != null) GaugeAmberDim else GaugeMuted,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }
                        }

                        // ── 底部操作区 ───────────────────────────────────────────
                    }
                }

                AnimatedVisibility(visible = btnVisible, enter = fadeIn(tween(500))) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // PROCEED 按钮
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    if (buttonEnabled) GaugeAmber else GaugeBorder,
                                )
                                .background(if (buttonEnabled) Color(0xFF1A1000) else GaugeSurface)
                                .then(
                                    if (buttonEnabled) Modifier.clickable {
                                        isEntering = true
                                        onContinue(confirmedPurpose)
                                    } else Modifier
                                )
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val label = when {
                                cooldownRemaining > 0 -> "[ WAIT  $cooldownRemaining ]"
                                !canContinue         -> "[ INPUT_PURPOSE_FIRST ]"
                                else                 -> "[ PROCEED ]"
                            }
                            Text(
                                text = label,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (buttonEnabled) GaugeAmber else GaugeMuted,
                                letterSpacing = 2.sp
                            )
                        }

                        // EXIT 按钮
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "< EXIT",
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = if (isDismissing) GaugeMuted else GaugeAmberDim,
                                letterSpacing = 2.sp,
                                modifier = Modifier
                                    .clickable {
                                        if (!isDismissing && !isEntering) {
                                            isDismissing = true; onDismiss()
                                        }
                                    }
                                    .padding(vertical = 6.dp)
                            )
                            if (remainingModifyCount > 0 && onReset != null) {
                                Text(
                                    text = "> RESET ($remainingModifyCount)",
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = GaugeMuted,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier
                                        .clickable { onReset() }
                                        .padding(vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
