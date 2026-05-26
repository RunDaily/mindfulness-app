package com.life.mindfulnessapp.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** 气泡停留时长（毫秒），不含淡入/淡出 */
const val PAUSED_TOAST_SHOW_MS = 10_000L

/** 淡入/淡出动画时长（毫秒） */
private const val FADE_DURATION_MS = 380

/** 进度条更新间隔（毫秒） */
private const val PROGRESS_TICK_MS = 100L

private val CardShape = RoundedCornerShape(18.dp)

/**
 * 为任意 Modifier 添加彩色发光阴影，确保在深色/浅色壁纸上都清晰可见。
 * 原理：在 Canvas 上用 BlurMaskFilter 绘制一层彩色扩散层。
 */
private fun Modifier.coloredShadow(
    color: Color,
    borderRadius: Dp = 18.dp,
    blurRadius: Dp = 20.dp,
    offsetY: Dp = 2.dp,
    spread: Dp = 2.dp
): Modifier = this.drawBehind {
    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            asFrameworkPaint().apply {
                isAntiAlias = true
                this.color = android.graphics.Color.TRANSPARENT
                setShadowLayer(
                    blurRadius.toPx(),
                    0f,
                    offsetY.toPx(),
                    color.copy(alpha = 0.55f).toArgb()
                )
            }
        }
        canvas.drawRoundRect(
            left   = -spread.toPx(),
            top    = -spread.toPx(),
            right  = size.width  + spread.toPx(),
            bottom = size.height + spread.toPx(),
            radiusX = borderRadius.toPx(),
            radiusY = borderRadius.toPx(),
            paint   = paint
        )
    }
}

/**
 * 暂停提示气泡（顶部居中）
 *
 * 外观：暖色发光描边 + 略带暖调深色背景 + 暂停图标 + 两行文字 + 底部倒计时进度条
 * 行为：淡入 → 停留 [PAUSED_TOAST_SHOW_MS] 毫秒（进度条从满走到空）→ 淡出 → 回调 [onDismissed]
 *
 * 可见性策略：
 *  - 暖色调背景（#1C1713）与纯黑/深灰壁纸存在色相差异
 *  - 黄色发光阴影在深色背景上产生光晕，在浅色背景上形成暗色投影
 *  - 半透明黄色描边提供额外轮廓对比，确保边界清晰
 *
 * @param appName        被监控 App 的名称
 * @param pauseMinutes   暂停状态将保持的分钟数（用于文案）
 * @param timeoutMs      后台真实超时毫秒数（用于进度条满→空的完整周期）
 * @param onDismissed    淡出完毕后由 OverlayManager 调用以移除 View
 */
@Composable
fun PausedToastOverlay(
    appName: String,
    pauseMinutes: Int,
    timeoutMs: Long,
    onDismissed: () -> Unit
) {
    val alpha = remember { Animatable(0f) }
    var dismissed by remember { mutableStateOf(false) }

    // 进度条：1f（满）→ 0f（空），与 timeoutMs 同步递减
    var progressFraction by remember { mutableFloatStateOf(1f) }

    // 进度条颜色 smooth 过渡（黄→橙→红）
    val progressColorLerp by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = tween(durationMillis = PROGRESS_TICK_MS.toInt(), easing = LinearEasing),
        label = "progress_color_lerp"
    )

    LaunchedEffect(Unit) {
        // ── 淡入 ──────────────────────────────────────────────
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = FADE_DURATION_MS, easing = FastOutSlowInEasing)
        )

        // ── 进度条倒计时（停留 10s，但进度映射到完整的 timeoutMs 周期）──
        val totalTicks = (PAUSED_TOAST_SHOW_MS / PROGRESS_TICK_MS).toInt()
        val progressStep = 1f / (timeoutMs / PROGRESS_TICK_MS.toFloat())
        repeat(totalTicks) {
            delay(PROGRESS_TICK_MS)
            progressFraction = (progressFraction - progressStep).coerceAtLeast(0f)
        }

        // ── 淡出 ──────────────────────────────────────────────
        alpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = FADE_DURATION_MS, easing = FastOutSlowInEasing)
        )
        dismissed = true
        onDismissed()
    }

    // 发光颜色跟随进度条状态变化
    val glowColor = when {
        progressColorLerp > 0.6f -> Color(0xFFFFD54F)   // 黄
        progressColorLerp > 0.3f -> Color(0xFFFF8A65)   // 橙
        else                     -> Color(0xFFEF5350)   // 红
    }
    val barColor = glowColor

    if (!dismissed) {
        Box(
            modifier = Modifier
                .graphicsLayer { this.alpha = alpha.value }
                .padding(horizontal = 16.dp, vertical = 10.dp)
                // 发光阴影（颜色与进度条联动，营造统一的"状态感"）
                .coloredShadow(
                    color = glowColor,
                    borderRadius = 18.dp,
                    blurRadius = 22.dp,
                    offsetY = 2.dp,
                    spread = 1.dp
                )
                // 半透明暖色描边，确保轮廓在任何壁纸上都可见
                .border(
                    width = 1.dp,
                    color = glowColor.copy(alpha = 0.45f),
                    shape = CardShape
                )
                .clip(CardShape)
                // 略带暖色调的深色背景，与纯黑壁纸形成色相差异
                .background(Color(0xF51C1713))
        ) {
            Column {
                // ── 主体内容行 ─────────────────────────────────
                Row(
                    modifier = Modifier.padding(
                        start = 16.dp, end = 20.dp,
                        top = 14.dp, bottom = 10.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 图标容器：背景色与卡片同色系，略亮
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF2E2718)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = null,
                            tint = glowColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "「$appName」计时已暂停",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "返回后继续计时，${pauseMinutes} 分钟内有效",
                            fontSize = 11.sp,
                            color = Color(0xFFAAAAAA)
                        )
                    }
                }

                // ── 底部倒计时进度条 ────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color(0xFF2A2520))  // 轨道色：暖色深灰，融入卡片
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressFraction)
                            .height(3.dp)
                            .background(barColor)
                    )
                }
            }
        }
    }
}
