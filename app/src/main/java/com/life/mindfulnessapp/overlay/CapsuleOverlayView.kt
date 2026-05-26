package com.life.mindfulnessapp.overlay

import android.content.pm.PackageManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.life.mindfulnessapp.service.SessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas

/** 展开状态下无操作自动收起的延迟（毫秒） */
private const val AUTO_COLLAPSE_DELAY_MS = 3000L

/**
 * 无操作多少毫秒后开始进入休眠（前台状态下5分钟）
 */
private const val DORMANT_IDLE_DELAY_MS = 5 * 60 * 1000L   // 5分钟

/** 休眠态目标透明度 */
private const val DORMANT_ALPHA = 0.35f

/** 休眠态目标缩放 */
private const val DORMANT_SCALE = 0.88f

/** 渐入休眠的动画时长（ms） */
private const val DORMANT_FADE_MS = 1500

/** 唤醒动画时长（ms） */
private const val WAKE_FADE_MS = 120

/** 光盘旋转一圈的时长（ms） */
private const val DISC_ROTATION_DURATION_MS = 4000

// ── 时间压力颜色（四档：宽松→紧张） ────────────────────────────────────────
private fun urgencyColor(themeAccent: Color, ratio: Float): Color = when {
    ratio > 0.50f -> themeAccent
    ratio > 0.20f -> Color(0xFFFFD54F)
    ratio > 0.10f -> Color(0xFFFF8A65)
    else          -> Color(0xFFEF5350)
}

private fun urgencySubColor(themeAccent: Color, ratio: Float): Color = when {
    ratio > 0.50f -> themeAccent.copy(alpha = 0.75f)
    ratio > 0.20f -> Color(0xFFFFE082)
    ratio > 0.10f -> Color(0xFFFFCC80)
    else          -> Color(0xFFEF9A9A)
}

@Composable
fun CapsuleOverlayView(
    sessionManager: SessionManager?,
    appName: State<String>,
    appPackageName: State<String> = mutableStateOf(""),
    sessionSeconds: State<Long>,
    dailyRemainingSeconds: State<Long>,
    dailyLimitSeconds: State<Long>,
    purpose: State<String?>,
    expanded: State<Boolean>,
    isPaused: State<Boolean> = mutableStateOf(false),
    themeId: String = "default",
    onToggleExpand: () -> Unit,
    onEndSession: () -> Unit,
    onReturnToApp: (() -> Unit)? = null,
    onRegisterWakeUp: ((wakeUpFn: () -> Unit) -> Unit)? = null,
    onRegisterShowConfirm: ((showConfirmFn: () -> Unit) -> Unit)? = null,
    onConfirmDialogOpen: (() -> Unit)? = null,
    onConfirmDialogClose: (() -> Unit)? = null,
    playEnterAnimation: Boolean = true
) {
    val context = LocalContext.current

    // ── 主题配置 ──────────────────────────────────────────────────────────
    val themeConfig = remember(themeId) { getInterceptThemeConfig(themeId) }

    // ── 加载 App 图标 ────────────────────────────────────────────────────
    val appIconBitmap: ImageBitmap? = remember(appPackageName.value) {
        try {
            val pm = context.packageManager
            pm.getApplicationIcon(appPackageName.value).toBitmap(96, 96).asImageBitmap()
        } catch (e: Exception) { null }
    }

    // ── 时间压力比例 ──────────────────────────────────────────────────────
    val ratio by remember {
        derivedStateOf {
            val limit = dailyLimitSeconds.value
            if (limit <= 0L) 1f
            else (dailyRemainingSeconds.value.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
        }
    }
    val isUrgent by remember { derivedStateOf { ratio <= 0.10f && dailyLimitSeconds.value > 0L } }
    // 剩余进度弧：0→1（1=全满，0=耗尽）
    val arcProgress by remember { derivedStateOf { ratio } }

    // ── 颜色过渡 ──────────────────────────────────────────────────────────
    val targetIcon = urgencyColor(themeConfig.capsuleAccentColor, ratio)
    val targetSub  = urgencySubColor(themeConfig.capsuleAccentColor, ratio)
    val bgColor by animateColorAsState(
        targetValue = themeConfig.capsuleBgColor,
        animationSpec = tween(800, easing = FastOutSlowInEasing), label = "capsule_bg"
    )
    val iconColor by animateColorAsState(
        targetValue = targetIcon,
        animationSpec = tween(800, easing = FastOutSlowInEasing), label = "capsule_icon"
    )
    val subColor by animateColorAsState(
        targetValue = targetSub,
        animationSpec = tween(800, easing = FastOutSlowInEasing), label = "capsule_sub"
    )

    // ── 无限动画 ─────────────────────────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "capsule_anim")

    // 紧急脉冲
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing), repeatMode = RepeatMode.Reverse
        ), label = "pulse_alpha"
    )
    val effectiveIconColor = if (isUrgent) iconColor.copy(alpha = pulseAlpha) else iconColor

    // 光盘旋转
    val discRotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(DISC_ROTATION_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "disc_rotation"
    )
    val discAngle = if (isPaused.value) 0f else discRotation

    // cyberpunk/glitch 扫描线
    val scanlineAlpha by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing), repeatMode = RepeatMode.Restart
        ), label = "scan_alpha"
    )

    // 【新增】收起态时间数字主题色呼吸（非紧急时轻微脉动，强调主题归属感）
    val timePulse by infiniteTransition.animateFloat(
        initialValue = 0.88f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse
        ), label = "time_pulse"
    )

    // 【新增】收起态：竖线光晕脉动
    val dividerGlow by infiniteTransition.animateFloat(
        initialValue = 0.18f, targetValue = 0.42f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse
        ), label = "divider_glow"
    )

    // ── 休眠 / 唤醒 ──────────────────────────────────────────────────────
    val dormantAlpha  = remember { Animatable(1f) }
    val dormantScale  = remember { Animatable(1f) }
    val coroutineScope = rememberCoroutineScope()

    fun wakeUp() {
        coroutineScope.launch {
            launch { dormantAlpha.animateTo(1f, tween(WAKE_FADE_MS, easing = FastOutSlowInEasing)) }
            dormantScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
        }
    }

    var dormantResetKey by remember { mutableStateOf(0) }

    LaunchedEffect(dormantResetKey, isUrgent, isPaused.value) {
        if (isUrgent || isPaused.value) { wakeUp(); return@LaunchedEffect }
        delay(DORMANT_IDLE_DELAY_MS)
        launch { dormantAlpha.animateTo(DORMANT_ALPHA, tween(DORMANT_FADE_MS, easing = LinearEasing)) }
        dormantScale.animateTo(DORMANT_SCALE, tween(DORMANT_FADE_MS, easing = FastOutSlowInEasing))
    }

    // ── 结束确认弹窗 ──────────────────────────────────────────────────────
    var showEndConfirmDialog by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onRegisterWakeUp?.invoke { dormantResetKey++; wakeUp() }
        onRegisterShowConfirm?.invoke { showEndConfirmDialog = true }
        onDispose {
            onRegisterWakeUp?.invoke {}
            onRegisterShowConfirm?.invoke {}
        }
    }

    // ── 展开自动收起 ──────────────────────────────────────────────────────
    LaunchedEffect(expanded.value) {
        if (expanded.value) {
            delay(AUTO_COLLAPSE_DELAY_MS)
            if (expanded.value) onToggleExpand()
        }
    }

    // ── 入场动画 ─────────────────────────────────────────────────────────
    val enterScale = remember { Animatable(if (playEnterAnimation) 0.22f else 1f) }
    val enterAlpha = remember { Animatable(if (playEnterAnimation) 0.4f else 1f) }

    LaunchedEffect(Unit) {
        if (playEnterAnimation) {
            delay(16)
            launch {
                enterScale.animateTo(1f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow))
            }
            enterAlpha.animateTo(1f, tween(160, easing = FastOutSlowInEasing))
        }
    }

    val timeFont = if (themeConfig.capsuleUseMonoFont) FontFamily.Monospace else FontFamily.Default

    // ── 布局 ─────────────────────────────────────────────────────────────
    run {
        Box(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        val combinedScale = enterScale.value * dormantScale.value
                        scaleX = combinedScale
                        scaleY = combinedScale
                        alpha  = enterAlpha.value * dormantAlpha.value
                    }
                    .shadow(
                        elevation = if (dormantScale.value < 0.95f) 2.dp else 10.dp,
                        shape = RoundedCornerShape(24.dp),
                        ambientColor = themeConfig.capsuleAccentColor.copy(alpha = 0.25f),
                        spotColor    = themeConfig.capsuleAccentColor.copy(alpha = 0.35f)
                    )
                    .clip(RoundedCornerShape(24.dp))
                    .background(bgColor)
                    .then(
                        when (themeId) {
                            "cyberpunk", "glitch" ->
                                Modifier.background(
                                    Brush.linearGradient(
                                        listOf(
                                            themeConfig.capsuleAccentColor.copy(alpha = 0.08f + scanlineAlpha * 0.06f),
                                            Color.Transparent,
                                            themeConfig.capsuleAccentColor.copy(alpha = 0.04f + (1f - scanlineAlpha) * 0.06f)
                                        )
                                    )
                                )
                            "lava" ->
                                Modifier.background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, themeConfig.capsuleAccentColor.copy(alpha = 0.10f))
                                    )
                                )
                            "moon" ->
                                Modifier.background(
                                    Brush.radialGradient(
                                        listOf(themeConfig.capsuleAccentColor.copy(alpha = 0.08f), Color.Transparent)
                                    )
                                )
                            "sakura" ->
                                Modifier.background(
                                    Brush.linearGradient(
                                        listOf(
                                            themeConfig.capsuleAccentColor.copy(alpha = 0.06f),
                                            Color.Transparent,
                                            themeConfig.capsuleAccentColor.copy(alpha = 0.04f)
                                        )
                                    )
                                )
                            else -> Modifier
                        }
                    )
            ) {
                if (expanded.value) {
                    // ══════════════════════════════════════════════════════
                    //  展开态
                    // ══════════════════════════════════════════════════════
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // 【改进】光盘图标：外环增加剩余时间进度弧
                            DiscIconWithArc(
                                appIconBitmap  = appIconBitmap,
                                tintColor      = effectiveIconColor,
                                rotationAngle  = discAngle,
                                isPaused       = isPaused.value,
                                arcProgress    = arcProgress,
                                hasLimit       = dailyLimitSeconds.value > 0L,
                                size           = 36
                            )
                            Column {
                                if (isPaused.value) {
                                    Text(
                                        text = "已暂停",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFAAAAAA),
                                        fontFamily = timeFont
                                    )
                                    Text(
                                        text = "点击胶囊返回 ${appName.value}",
                                        fontSize = 10.sp,
                                        color = subColor.copy(alpha = 0.75f)
                                    )
                                } else {
                                    // 本次会话时长
                                    Text(
                                        text = formatSeconds(sessionSeconds.value),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontFamily = timeFont
                                    )
                                    // 今日剩余（有限制时显示）
                                    if (dailyRemainingSeconds.value < Long.MAX_VALUE) {
                                        val remainStr = formatSeconds(dailyRemainingSeconds.value)
                                        // 剩余不足 10% 时用橙红色提示
                                        val remainColor = if (ratio <= 0.10f) subColor else subColor.copy(alpha = 0.85f)
                                        Text(
                                            text = "今日剩 $remainStr",
                                            fontSize = 11.sp,
                                            color = remainColor,
                                            fontFamily = timeFont
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(4.dp))

                            if (!isPaused.value) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(11.dp))
                                        .background(themeConfig.capsuleStopButtonColor)
                                        .clickable { showEndConfirmDialog = true }
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(Color.White)
                                        )
                                        Text(
                                            text = if (themeConfig.capsuleUseMonoFont) "END" else "结束",
                                            fontSize = 8.sp,
                                            color = Color.White.copy(alpha = 0.85f),
                                            fontWeight = FontWeight.Medium,
                                            fontFamily = timeFont
                                        )
                                    }
                                }
                            }
                        }

                        // 使用目的行
                        val currentPurposeText = purpose.value
                        if (!currentPurposeText.isNullOrBlank() && !isPaused.value) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(start = 2.dp)
                            ) {
                                Text(
                                    text = if (themeConfig.capsuleUseMonoFont) ">" else "📌",
                                    fontSize = 11.sp,
                                    color = if (themeConfig.capsuleUseMonoFont)
                                        themeConfig.capsuleAccentColor else Color.Unspecified
                                )
                                Text(
                                    text = currentPurposeText,
                                    fontSize = 11.sp,
                                    color = subColor,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontFamily = timeFont
                                )
                            }
                        }
                    }
                } else {
                    // ══════════════════════════════════════════════════════
                    //  收起态（三段式：时间 | 标签 | 停止）
                    // ══════════════════════════════════════════════════════
                    Row(
                        modifier = Modifier
                            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        // ① 计时数字（主题色微染色：非紧急时呼吸，紧急时红色脉冲）
                        if (isPaused.value) {
                            Text(
                                text = if (themeConfig.capsuleUseMonoFont) "PAUSED" else "已暂停",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFAAAAAA),
                                letterSpacing = 0.5.sp,
                                fontFamily = timeFont
                            )
                        } else {
                            val timeColor = when {
                                isUrgent -> effectiveIconColor  // 红色脉冲
                                ratio > 0.50f ->
                                    // 主题色微染：白色基底 + 主题色呼吸叠加
                                    Color.White.copy(alpha = timePulse).let { white ->
                                        // lerp: 大部分白，加一点主题色
                                        Color(
                                            red   = white.red   * 0.82f + themeConfig.capsuleAccentColor.red   * 0.18f,
                                            green = white.green * 0.82f + themeConfig.capsuleAccentColor.green * 0.18f,
                                            blue  = white.blue  * 0.82f + themeConfig.capsuleAccentColor.blue  * 0.18f,
                                            alpha = 1f
                                        )
                                    }
                                else -> iconColor  // 黄/橙过渡
                            }
                            Text(
                                text = formatSeconds(sessionSeconds.value),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = timeColor,
                                letterSpacing = 1.sp,
                                fontFamily = timeFont
                            )
                        }

                        // ② 分隔符：Canvas 弧形光晕竖线（替代简单方块）
                        Spacer(modifier = Modifier.width(10.dp))
                        Canvas(
                            modifier = Modifier
                                .width(8.dp)
                                .height(24.dp)
                        ) {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val lineH = size.height * 0.75f
                            // 光晕：主题色散射
                            drawLine(
                                color = themeConfig.capsuleAccentColor.copy(alpha = dividerGlow * 0.6f),
                                start = Offset(cx, cy - lineH / 2f),
                                end   = Offset(cx, cy + lineH / 2f),
                                strokeWidth = 4.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                            // 核心线
                            drawLine(
                                color = themeConfig.capsuleAccentColor.copy(alpha = dividerGlow + 0.15f),
                                start = Offset(cx, cy - lineH / 2f),
                                end   = Offset(cx, cy + lineH / 2f),
                                strokeWidth = 1.2.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))

                        // ③ 标签（目的优先 / AppName）
                        val collapsedLabel = purpose.value?.takeIf { it.isNotBlank() } ?: appName.value
                        Text(
                            text = collapsedLabel,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.80f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 120.dp),
                            fontFamily = timeFont
                        )

                        // ④ 分隔符
                        Spacer(modifier = Modifier.width(8.dp))
                        Canvas(
                            modifier = Modifier
                                .width(8.dp)
                                .height(24.dp)
                        ) {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val lineH = size.height * 0.75f
                            drawLine(
                                color = themeConfig.capsuleAccentColor.copy(alpha = dividerGlow * 0.5f),
                                start = Offset(cx, cy - lineH / 2f),
                                end   = Offset(cx, cy + lineH / 2f),
                                strokeWidth = 4.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                color = themeConfig.capsuleAccentColor.copy(alpha = dividerGlow + 0.12f),
                                start = Offset(cx, cy - lineH / 2f),
                                end   = Offset(cx, cy + lineH / 2f),
                                strokeWidth = 1.2.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))

                        // ⑤ 停止按钮
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(themeConfig.capsuleStopButtonColor)
                                .clickable { showEndConfirmDialog = true }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.White)
                            )
                        }
                    }
                }
            }

            // ── 结束确认弹窗 ──────────────────────────────────────────────
            // 弹窗打开/关闭时通知外部暂停或恢复计时（仅前台场景生效，后台场景已由 isInBackground 控制）
            LaunchedEffect(showEndConfirmDialog) {
                if (showEndConfirmDialog && !isPaused.value) {
                    onConfirmDialogOpen?.invoke()
                } else if (!showEndConfirmDialog) {
                    onConfirmDialogClose?.invoke()
                }
            }
            if (showEndConfirmDialog) {
                EndConfirmDialog(
                    appName         = appName.value,
                    sessionSeconds  = sessionSeconds.value,
                    bgColor         = bgColor,
                    iconColor       = effectiveIconColor,
                    subColor        = subColor,
                    useMonoFont     = themeConfig.capsuleUseMonoFont,
                    stopButtonColor = themeConfig.capsuleStopButtonColor,
                    onConfirm = {
                        showEndConfirmDialog = false
                        onEndSession()
                    },
                    onDismiss = { showEndConfirmDialog = false }
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  光盘图标（带外圈剩余时间进度弧）
// ════════════════════════════════════════════════════════════════════════════

/**
 * 展开态光盘图标
 * - 最外圈：剩余时间进度弧（有限制时显示，颜色随紧急程度变化）
 * - 中圈：主题色轨道纹
 * - 内圈：App图标
 */
@Composable
private fun DiscIconWithArc(
    appIconBitmap: ImageBitmap?,
    tintColor: Color,
    rotationAngle: Float,
    isPaused: Boolean,
    arcProgress: Float,   // 0..1，剩余比例
    hasLimit: Boolean,
    size: Int
) {
    val sizeDp = size.dp
    Box(
        modifier = Modifier.size(sizeDp),
        contentAlignment = Alignment.Center
    ) {
        // 外圈进度弧（不随光盘旋转，固定朝向）
        if (hasLimit && !isPaused) {
            Canvas(modifier = Modifier.size(sizeDp)) {
                val strokeW = 2.5.dp.toPx()
                val inset   = strokeW / 2f
                val arcSize = Size(this.size.width - strokeW, this.size.height - strokeW)
                // 轨道底
                drawArc(
                    color      = tintColor.copy(alpha = 0.15f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter  = false,
                    topLeft    = Offset(inset, inset),
                    size       = arcSize,
                    style      = Stroke(width = strokeW, cap = StrokeCap.Round)
                )
                // 剩余进度
                if (arcProgress > 0f) {
                    drawArc(
                        color      = tintColor.copy(alpha = 0.80f),
                        startAngle = -90f,
                        sweepAngle = 360f * arcProgress,
                        useCenter  = false,
                        topLeft    = Offset(inset, inset),
                        size       = arcSize,
                        style      = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                }
            }
        }

        // 光盘主体（旋转）
        Box(
            modifier = Modifier
                .size((size * 0.82f).dp)
                .graphicsLayer { rotationZ = rotationAngle },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = this.size.minDimension / 2f
                val center = Offset(this.size.width / 2f, this.size.height / 2f)

                // 外层光晕
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            tintColor.copy(alpha = if (isPaused) 0.15f else 0.40f),
                            tintColor.copy(alpha = 0f)
                        ),
                        center = center, radius = radius
                    ),
                    radius = radius, center = center
                )

                // 轨道环
                val trackColors = if (isPaused) {
                    listOf(Color(0xFF3A3A3A), Color(0xFF2A2A2A), Color(0xFF333333), Color(0xFF252525))
                } else {
                    listOf(
                        tintColor.copy(alpha = 0.65f), tintColor.copy(alpha = 0.22f),
                        tintColor.copy(alpha = 0.55f), tintColor.copy(alpha = 0.18f)
                    )
                }
                for (i in 3 downTo 1) {
                    val ringRadius = radius * (0.45f + i * 0.17f)
                    drawCircle(
                        color = trackColors[i % trackColors.size],
                        radius = ringRadius,
                        center = center,
                        style = Stroke(width = radius * 0.06f)
                    )
                }

                // 中心光点
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (isPaused) 0.3f else 0.75f),
                            Color.White.copy(alpha = 0f)
                        ),
                        center = center, radius = radius * 0.2f
                    ),
                    radius = radius * 0.2f, center = center
                )
            }

            // App图标
            if (appIconBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = appIconBitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .size((size * 0.82f * 0.55f).dp)
                        .clip(CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size((size * 0.82f * 0.4f).dp)
                        .clip(CircleShape)
                        .background(tintColor.copy(alpha = if (isPaused) 0.2f else 0.55f))
                )
            }
        }
    }
}

fun formatSeconds(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%02d:%02d".format(m, s)
}

// ════════════════════════════════════════════════════════════════════════════
//  结束会话确认弹窗（v2：加入本次会话时长回顾）
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun EndConfirmDialog(
    appName: String,
    sessionSeconds: Long,
    bgColor: Color,
    iconColor: Color,
    subColor: Color,
    useMonoFont: Boolean = false,
    stopButtonColor: Color = Color(0xFF27AE60),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val scale = remember { Animatable(0.8f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch {
            scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh))
        }
        alpha.animateTo(1f, tween(120, easing = FastOutSlowInEasing))
    }

    val font = if (useMonoFont) FontFamily.Monospace else FontFamily.Default

    // 本次时长文案
    val sessionLabel = buildString {
        val h = sessionSeconds / 3600
        val m = (sessionSeconds % 3600) / 60
        val s = sessionSeconds % 60
        if (useMonoFont) {
            append("SESSION_DURATION: ")
            if (h > 0) append("${h}h${m}m") else if (m > 0) append("${m}m${s}s") else append("${s}s")
        } else {
            append("本次已使用 ")
            when {
                h > 0 && m > 0 -> append("${h}小时${m}分")
                h > 0          -> append("${h}小时")
                m > 0 && s > 0 -> append("${m}分${s}秒")
                m > 0          -> append("${m}分钟")
                else           -> append("${s}秒")
            }
        }
    }

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale.value; scaleY = scale.value; this.alpha = alpha.value
            }
            .shadow(12.dp, shape = RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (useMonoFont) "END_SESSION?" else "结束本次使用？",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontFamily = font
            )
            Text(
                text = appName,
                fontSize = 11.sp,
                color = subColor.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = font
            )

            // 【新增】会话时长回顾小胶囊
            if (sessionSeconds >= 10L) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconColor.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = sessionLabel,
                        fontSize = 11.sp,
                        color = iconColor.copy(alpha = 0.85f),
                        fontFamily = font,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable { onDismiss() }
                        .padding(vertical = 7.dp)
                ) {
                    Text(
                        text = if (useMonoFont) "CANCEL" else "取消",
                        fontSize = 12.sp,
                        color = subColor.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Medium,
                        fontFamily = font
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(stopButtonColor.copy(alpha = 0.85f))
                        .clickable { onConfirm() }
                        .padding(vertical = 7.dp)
                ) {
                    Text(
                        text = if (useMonoFont) "CONFIRM" else "结束",
                        fontSize = 12.sp,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = font
                    )
                }
            }
        }
    }
}
