package com.life.mindfulnessapp.overlay

import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
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
import androidx.compose.foundation.border

/** 展开状态下无操作自动收起的延迟（毫秒） */
private const val AUTO_COLLAPSE_DELAY_MS = 3000L

/** 闪光描边提醒的间隔（毫秒），每隔此时间触发一次双闪 */
private const val FLASH_BORDER_INTERVAL_MS = 2 * 60 * 1000L  // 2分钟

/** 紧急状态下闪光间隔缩短（毫秒） */
private const val FLASH_BORDER_INTERVAL_URGENT_MS = 40 * 1000L  // 40秒

/** 单次闪光动画时长（ms）*/
private const val FLASH_ANIM_DURATION_MS = 600

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
    onEndSession: (effectScore: Int?, note: String?) -> Unit,
    onReturnToApp: (() -> Unit)? = null,
    onRegisterWakeUp: ((wakeUpFn: () -> Unit) -> Unit)? = null,
    onRegisterShowConfirm: ((showConfirmFn: () -> Unit) -> Unit)? = null,
    onRegisterWarnFiveMin: ((fn: () -> Unit) -> Unit)? = null,
    onRegisterStartCountdown: ((fn: () -> Unit) -> Unit)? = null,
    onExtendLimit: ((extraMinutes: Int) -> Unit)? = null,
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


    // 【新增】收起态时间数字主题色呼吸（非紧急时轻微脉动，强调主题归属感）
    val timePulse by infiniteTransition.animateFloat(
        initialValue = 0.88f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse
        ), label = "time_pulse"
    )

    // ── 闪光描边 ───────────────────────────────────────────────────────
    // flashBorderAlpha: 0f = 不显示, 1f = 完全显示；由定时双闪动画驱动
    val flashBorderAlpha = remember { Animatable(0f) }

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

    // ── 5分钟预警状态（true = 正在展示提示 banner，展示3秒后自动恢复） ─────
    var showFiveMinWarning by remember { mutableStateOf(false) }

    // ── 1分钟倒计时模式 ───────────────────────────────────────────────────
    var countdownMode by remember { mutableStateOf(false) }
    // 延长选项展开状态
    var showExtendOptions by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onRegisterWakeUp?.invoke { dormantResetKey++; wakeUp() }
        onRegisterShowConfirm?.invoke { showEndConfirmDialog = true }
        onRegisterWarnFiveMin?.invoke {
            // 自动展开 + 显示5分钟提示 banner，3秒后自动收起
            showFiveMinWarning = true
        }
        onRegisterStartCountdown?.invoke {
            // 切换到倒计时形态
            countdownMode = true
            showExtendOptions = false
        }
        onDispose {
            onRegisterWakeUp?.invoke {}
            onRegisterShowConfirm?.invoke {}
            onRegisterWarnFiveMin?.invoke {}
            onRegisterStartCountdown?.invoke {}
        }
    }

    // 5分钟预警：自动展开胶囊，震动，3秒后收起
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(showFiveMinWarning) {
        if (showFiveMinWarning) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            // 若未展开则自动展开
            if (!expanded.value) onToggleExpand()
            delay(3500L)
            showFiveMinWarning = false
            // 3.5秒后自动收起
            if (expanded.value) onToggleExpand()
        }
    }

    // 1分钟倒计时：进入倒计时模式时唤醒胶囊 + 震动
    LaunchedEffect(countdownMode) {
        if (countdownMode) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(80)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            wakeUp()
            dormantResetKey++
        }
    }

    // 延长后剩余恢复到60秒以上：退出倒计时模式
    LaunchedEffect(dailyRemainingSeconds.value) {
        if (countdownMode && dailyRemainingSeconds.value > 60L) {
            countdownMode = false
            showExtendOptions = false
        }
    }

    // ── 定时闪光描边提醒 ────────────────────────────────────────────────
    // 循环运行：等待 interval → 双闪 → 再等待 → 再双闪 ...
    // isPaused 或 isUrgent 变化时 LaunchedEffect 重启，间隔自动切换。
    LaunchedEffect(isPaused.value, isUrgent) {
        if (isPaused.value) return@LaunchedEffect
        while (true) {
            val interval = if (isUrgent) FLASH_BORDER_INTERVAL_URGENT_MS else FLASH_BORDER_INTERVAL_MS
            delay(interval)
            // 双闪：亮 → 灭 → 亮 → 灭
            repeat(2) {
                flashBorderAlpha.animateTo(
                    1f, tween(FLASH_ANIM_DURATION_MS, easing = FastOutSlowInEasing)
                )
                flashBorderAlpha.animateTo(
                    0f, tween(FLASH_ANIM_DURATION_MS, easing = FastOutSlowInEasing)
                )
                if (it == 0) delay(120) // 两闪之间短暂停顿
            }
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
        // 闪光描边颜色（随紧急程度变化）
        val flashBorderColor = urgencyColor(themeConfig.capsuleAccentColor, ratio)

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
                // 闪光描边：叠加在 shadow 之上、clip 之外，形成外发光效果
                .then(
                    if (flashBorderAlpha.value > 0.01f)
                        Modifier.border(
                            width = (1.5f + flashBorderAlpha.value * 1.5f).dp,
                            brush = Brush.linearGradient(
                                listOf(
                                    flashBorderColor.copy(alpha = flashBorderAlpha.value * 0.95f),
                                    flashBorderColor.copy(alpha = flashBorderAlpha.value * 0.55f),
                                    flashBorderColor.copy(alpha = flashBorderAlpha.value * 0.85f)
                                )
                            ),
                            shape = RoundedCornerShape(24.dp)
                        )
                    else Modifier
                )
                .clip(RoundedCornerShape(24.dp))
                .background(bgColor)
            ) {
                if (expanded.value) {
                    // ══════════════════════════════════════════════════════
                    //  展开态
                    // ══════════════════════════════════════════════════════
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                size           = 38
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
                                        text = "点击返回 ${appName.value}",
                                        fontSize = 10.sp,
                                        color = subColor.copy(alpha = 0.75f)
                                    )
                                } else {
                                    // 本次会话时长
                                    Text(
                                        text = formatSeconds(sessionSeconds.value),
                                        fontSize = 17.sp,
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
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(themeConfig.capsuleStopButtonColor)
                                        .clickable { showEndConfirmDialog = true }
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(11.dp)
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
                            // 闪光时：描边高亮 + 背景微染 + 文字提亮，与外层胶囊描边联动
                            val purposeFlash = flashBorderAlpha.value
                            // 平时底色：主题色极淡背景，让目的行有独立的「区域感」
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(0.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (purposeFlash > 0.01f)
                                            Modifier
                                                .background(
                                                    color = flashBorderColor.copy(alpha = purposeFlash * 0.10f),
                                                    shape = RoundedCornerShape(6.dp)
                                                )
                                                .border(
                                                    width = 1.dp,
                                                    color = flashBorderColor.copy(alpha = purposeFlash * 0.75f),
                                                    shape = RoundedCornerShape(6.dp)
                                                )
                                        else
                                            Modifier.background(
                                                color = themeConfig.capsuleAccentColor.copy(alpha = 0.07f),
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                // 左侧细色条：视觉锚点
                                Box(
                                    modifier = Modifier
                                        .width(2.5.dp)
                                        .height(14.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(
                                            androidx.compose.ui.graphics.lerp(
                                                themeConfig.capsuleAccentColor.copy(alpha = 0.60f),
                                                flashBorderColor.copy(alpha = 1f),
                                                purposeFlash * 0.8f
                                            )
                                        )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = currentPurposeText,
                                    fontSize = 13.sp,
                                    // 闪光时：从接近白色提亮到 flashBorderColor
                                    color = androidx.compose.ui.graphics.lerp(
                                        Color.White.copy(alpha = 0.90f),
                                        flashBorderColor.copy(alpha = 1f),
                                        purposeFlash * 0.65f
                                    ),
                                    fontWeight = if (purposeFlash > 0.5f) FontWeight.SemiBold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontFamily = timeFont
                                )
                            }
                        }

                        // ── 5分钟预警 banner（展开态内，临时显示后消失） ───────────
                        AnimatedVisibility(
                            visible = showFiveMinWarning && !isPaused.value,
                            enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 2 },
                            exit  = fadeOut(tween(250))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFFFD54F).copy(alpha = 0.15f))
                                    .border(
                                        1.dp,
                                        Color(0xFFFFD54F).copy(alpha = 0.55f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Text("⏳", fontSize = 13.sp)
                                Text(
                                    text = "还有 5 分钟，准备收尾了",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFFFD54F),
                                    fontFamily = timeFont
                                )
                            }
                        }
                    }
                } else {
                    // ══════════════════════════════════════════════════════
                    //  收起态
                    // ══════════════════════════════════════════════════════
                    if (countdownMode && !isPaused.value) {
                        // ── 倒计时形态（最后1分钟）────────────────────────────
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // 主行：倒计时 + 延长按钮
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // 倒计时数字（大红色，醒目）
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "还剩",
                                        fontSize = 9.sp,
                                        color = Color(0xFFEF5350).copy(alpha = 0.8f),
                                        fontFamily = timeFont,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        text = formatSeconds(dailyRemainingSeconds.value),
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (pulseAlpha > 0.7f) Color(0xFFEF5350) else Color(0xFFFF7043),
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 0.sp
                                    )
                                }

                                // 分隔竖线
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(32.dp)
                                        .background(Color.White.copy(alpha = 0.18f))
                                )

                                // 延长按钮
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (showExtendOptions)
                                                    Color(0xFF4CAF50).copy(alpha = 0.25f)
                                                else
                                                    Color(0xFF4CAF50).copy(alpha = 0.18f)
                                            )
                                            .border(
                                                1.dp,
                                                Color(0xFF4CAF50).copy(alpha = if (showExtendOptions) 0.8f else 0.45f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable { showExtendOptions = !showExtendOptions }
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = if (showExtendOptions) "▲ 取消" else "+ 延长",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF81C784),
                                            fontFamily = timeFont
                                        )
                                    }
                                    Text(
                                        text = "宽限时间",
                                        fontSize = 9.sp,
                                        color = Color.White.copy(alpha = 0.40f),
                                        fontFamily = timeFont
                                    )
                                }
                            }

                            // 延长选项列表（点击后展开）
                            AnimatedVisibility(
                                visible = showExtendOptions,
                                enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                                exit  = fadeOut(tween(150)) + shrinkVertically(tween(150))
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    listOf(5, 10, 15).forEach { mins ->
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF4CAF50).copy(alpha = 0.20f))
                                                .border(
                                                    1.dp,
                                                    Color(0xFF4CAF50).copy(alpha = 0.55f),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .clickable {
                                                    showExtendOptions = false
                                                    onExtendLimit?.invoke(mins)
                                                }
                                                .padding(vertical = 7.dp)
                                        ) {
                                            Text(
                                                text = "+${mins}分",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF81C784),
                                                fontFamily = timeFont
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                    // ── 普通收起态（三段式：时间 | 标签 | 停止）──────────────
                    Row(
                        modifier = Modifier
                            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        // ① 计时数字（主题色微染色：非紧急时呼吸，紧急时红色脉冲）
                        if (isPaused.value) {
                            Text(
                                text = if (themeConfig.capsuleUseMonoFont) "PAUSED" else "已暂停",
                                fontSize = 13.sp,
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
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = timeColor,
                                letterSpacing = 0.5.sp,
                                fontFamily = timeFont
                            )
                        }

                        // ② 分隔符
                        Text(
                            text = "·",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        // ③ 标签（目的优先 / AppName）
                        val collapsedPurpose = purpose.value?.takeIf { it.isNotBlank() }
                        val collapsedLabel = collapsedPurpose ?: appName.value
                        // 有目的文案时：字号/颜色/字重拉升，与计时形成对等视觉权重
                        Text(
                            text = collapsedLabel,
                            fontSize = if (collapsedPurpose != null) 12.sp else 11.sp,
                            fontWeight = if (collapsedPurpose != null) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (collapsedPurpose != null)
                                Color.White.copy(alpha = 0.92f)
                            else
                                Color.White.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 88.dp),
                            fontFamily = timeFont
                        )

                        // ④ 分隔符
                        Text(
                            text = "·",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        // ⑤ 停止按钮
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(themeConfig.capsuleStopButtonColor)
                                .clickable { showEndConfirmDialog = true }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.White)
                            )
                        }
                    }
                    } // end普通收起态
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
            onConfirm = { score, note ->
                showEndConfirmDialog = false
                onEndSession(score, note)
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
//  结束会话确认弹窗（v3：加入效果评分滑动条 + 备注输入框）
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
    onConfirm: (effectScore: Int?, note: String?) -> Unit,
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

    // 效果评分状态（null 表示未评分，滑动后才设置）
    var scoreEnabled by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(0f) }
    val effectScore: Int? = if (scoreEnabled) sliderValue.toInt() else null

    // 备注输入状态
    var noteText by remember { mutableStateOf("") }

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

    // 评分对应的颜色（低分红→高分绿）
    val scoreColor = when {
        !scoreEnabled -> subColor.copy(alpha = 0.4f)
        sliderValue <= 3f -> Color(0xFFE57373)
        sliderValue <= 6f -> Color(0xFFFFB74D)
        else -> Color(0xFF66BB6A)
    }

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale.value; scaleY = scale.value; this.alpha = alpha.value
            }
            .shadow(16.dp, shape = RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .width(300.dp)
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 标题与App信息 ──────────────────────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (useMonoFont) "END_SESSION?" else "结束本次使用？",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontFamily = font
                )
                Text(
                    text = appName,
                    fontSize = 13.sp,
                    color = subColor.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = font
                )
            }

            // ── 会话时长回顾小胶囊 ────────────────────────────────────────
            if (sessionSeconds >= 10L) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconColor.copy(alpha = 0.12f))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = sessionLabel,
                        fontSize = 13.sp,
                        color = iconColor.copy(alpha = 0.9f),
                        fontFamily = font,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // ── 分隔线 ────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(Color.White.copy(alpha = 0.10f))
            )

            // ── 效果评分区 ────────────────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = if (useMonoFont) "EFFECT_SCORE" else "效果评分",
                        fontSize = 13.sp,
                        color = subColor.copy(alpha = 0.85f),
                        fontFamily = font,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    // 分值展示（未评分时显示 -/10，评分后显示具体分值）
                    Text(
                        text = if (scoreEnabled) "${sliderValue.toInt()}/10" else "-/10",
                        fontSize = 20.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = scoreColor
                    )
                }
                Slider(
                    value = sliderValue,
                    onValueChange = { newVal ->
                        sliderValue = newVal
                        scoreEnabled = true
                    },
                    valueRange = 0f..10f,
                    steps = 9,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = scoreColor,
                        activeTrackColor = scoreColor.copy(alpha = 0.8f),
                        inactiveTrackColor = Color.White.copy(alpha = 0.12f),
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent
                    )
                )
                // 刻度标签
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("0", fontSize = 11.sp, color = subColor.copy(alpha = 0.4f), fontFamily = FontFamily.Monospace)
                    Text("5", fontSize = 11.sp, color = subColor.copy(alpha = 0.4f), fontFamily = FontFamily.Monospace)
                    Text("10", fontSize = 11.sp, color = subColor.copy(alpha = 0.4f), fontFamily = FontFamily.Monospace)
                }
            }

            // ── 备注输入框 ────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 90.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .padding(14.dp)
            ) {
                if (noteText.isEmpty()) {
                    Text(
                        text = if (useMonoFont) "// add note..." else "写点备注…",
                        fontSize = 14.sp,
                        color = subColor.copy(alpha = 0.35f),
                        fontFamily = font
                    )
                }
                BasicTextField(
                    value = noteText,
                    onValueChange = { if (it.length <= 100) noteText = it },
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        fontFamily = font,
                        lineHeight = 20.sp
                    ),
                    cursorBrush = SolidColor(iconColor),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )
            }

            // ── 操作按钮 ──────────────────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable { onDismiss() }
                        .padding(vertical = 12.dp)
                ) {
                    Text(
                        text = if (useMonoFont) "CANCEL" else "取消",
                        fontSize = 15.sp,
                        color = subColor.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Medium,
                        fontFamily = font
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(stopButtonColor.copy(alpha = 0.85f))
                        .clickable { onConfirm(effectScore, noteText.takeIf { it.isNotBlank() }) }
                        .padding(vertical = 12.dp)
                ) {
                    Text(
                        text = if (useMonoFont) "CONFIRM" else "结束",
                        fontSize = 15.sp,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = font
                    )
                }
            }
        }
    }
}
