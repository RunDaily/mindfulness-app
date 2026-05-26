package com.life.mindfulnessapp.overlay

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 仪式感展示浮窗（主题色联动版 v2）
 *
 * 改进：
 * - 打字完成后主题色呼吸光晕脉冲（边框 + 左侧竖条渐亮）
 * - 左侧主题色竖条（随打字进度从 0 → 100% 高度填充）
 * - 赛博/故障/熔岩主题：背景增加微粒子 Canvas 层（不干扰文字）
 * - 打字完成后自动缩短停留时间（文字越短停留越短，更流畅）
 *
 * @param purposeText  用户输入的使用目的文字（打字机效果展示）
 * @param themeId      当前拦截主题 ID，用于主题化背景色和文字色
 * @param onFinished   仪式感动画全部结束后的回调（用于移除浮窗 + 弹入普通胶囊）
 */
@Composable
fun CeremonyOverlayView(
    purposeText: String,
    themeId: String = "default",
    onFinished: () -> Unit
) {
    // ── 读取主题配置 ──────────────────────────────────────────────────────
    val themeConfig = remember(themeId) { getInterceptThemeConfig(themeId) }
    val font = if (themeConfig.capsuleUseMonoFont) FontFamily.Monospace else FontFamily.Default

    // ── 动画状态 ──────────────────────────────────────────────────────────
    val scaleX        = remember { Animatable(0.04f) }
    val scaleY        = remember { Animatable(0.04f) }
    val contentAlpha  = remember { Animatable(0f) }
    val capsuleAlpha  = remember { Animatable(1f) }

    // 打字机
    var typedCharCount by remember { mutableIntStateOf(0) }
    var typingDone     by remember { mutableStateOf(false) }

    // 左侧竖条填充进度（0f → 1f，随打字进度）
    val barFill = remember { Animatable(0f) }

    // 打字完成后的呼吸光晕（驱动边框 + 光晕脉冲）
    val glowAnim = remember { Animatable(0f) }

    // 停留时长：文字 ≤ 4 字 → 1.8s，否则按字数线性延长（最长 2.8s）
    val holdMs = remember(purposeText) {
        (1800L + (purposeText.length - 1).coerceAtLeast(0) * 80L).coerceAtMost(2800L)
    }

    LaunchedEffect(Unit) {
        // ══ Step 1 · 弹出展开 ══════════════════════════════════════════════
        delay(20L)
        launch {
            scaleX.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium))
        }
        delay(55L)
        scaleY.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium))

        // ══ Step 2 · 内容整体淡入 ════════════════════════════════════════
        delay(240L)
        launch {
            contentAlpha.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
        }

        // ══ Step 3 · 打字机逐字出现 + 竖条同步填充 ══════════════════════
        for (i in 1..purposeText.length) {
            typedCharCount = i
            // 竖条填充进度跟随字数
            launch {
                barFill.animateTo(
                    i.toFloat() / purposeText.length,
                    tween(70, easing = FastOutSlowInEasing)
                )
            }
            delay(75L)
        }
        typingDone = true

        // ══ Step 3.5 · 打字完成 → 边框呼吸光晕脉冲（2 个周期）════════════
        repeat(2) {
            launch { glowAnim.animateTo(1f, tween(380, easing = FastOutSlowInEasing)) }
            delay(380L)
            launch { glowAnim.animateTo(0.25f, tween(420, easing = FastOutSlowInEasing)) }
            delay(420L)
        }

        // ══ Step 4 · 停留展示 ═════════════════════════════════════════════
        delay(holdMs)

        // ══ Step 5 · 内容先淡出 ══════════════════════════════════════════
        contentAlpha.animateTo(0f, tween(160, easing = FastOutSlowInEasing))

        // ══ Step 6 · 胶囊收起（同时 scaleX/Y → 0，alpha → 0）══════════
        launch { capsuleAlpha.animateTo(0f, tween(200, easing = FastOutSlowInEasing)) }
        launch {
            scaleX.animateTo(
                0.04f,
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
            )
        }
        scaleY.animateTo(
            0.04f,
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
        )

        // ══ Step 7 · 通知外部 ══════════════════════════════════════════
        delay(50L)
        onFinished()
    }

    // ── 主题背景渐变（根据 themeId 差异化）───────────────────────────────
    val bgBrush = remember(themeId, themeConfig) {
        when (themeId) {
            "cyberpunk", "glitch" ->
                Brush.verticalGradient(
                    listOf(
                        themeConfig.ceremonyBgColor,
                        themeConfig.capsuleAccentColor.copy(alpha = 0.10f),
                        themeConfig.ceremonyBgColor
                    )
                )
            "lava" ->
                Brush.verticalGradient(
                    listOf(
                        themeConfig.ceremonyBgColor,
                        themeConfig.capsuleAccentColor.copy(alpha = 0.20f)
                    )
                )
            "sakura" ->
                Brush.linearGradient(
                    listOf(
                        themeConfig.ceremonyBgColor,
                        themeConfig.capsuleAccentColor.copy(alpha = 0.13f),
                        themeConfig.ceremonyBgColor
                    )
                )
            "moon" ->
                Brush.radialGradient(
                    listOf(
                        themeConfig.capsuleAccentColor.copy(alpha = 0.14f),
                        themeConfig.ceremonyBgColor
                    )
                )
            "deep_sea" ->
                Brush.verticalGradient(
                    listOf(
                        themeConfig.capsuleAccentColor.copy(alpha = 0.12f),
                        themeConfig.ceremonyBgColor
                    )
                )
            "rpg" ->
                Brush.linearGradient(
                    listOf(
                        themeConfig.capsuleAccentColor.copy(alpha = 0.08f),
                        themeConfig.ceremonyBgColor,
                        themeConfig.capsuleAccentColor.copy(alpha = 0.06f)
                    )
                )
            else ->
                Brush.linearGradient(
                    listOf(themeConfig.ceremonyBgColor, themeConfig.ceremonyBgColor)
                )
        }
    }

    // ── 布局：全屏透明容器，胶囊在顶部居中 ──────────────────────────────
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .padding(top = 52.dp, start = 16.dp, end = 16.dp)
                .graphicsLayer {
                    this.scaleX        = scaleX.value
                    this.scaleY        = scaleY.value
                    this.alpha         = capsuleAlpha.value
                    this.transformOrigin = TransformOrigin(0.5f, 0.5f)
                }
                .fillMaxWidth()
                .height(72.dp)
                // 边框呼吸光晕：打字完成后 glowAnim 驱动阴影扩张
                .shadow(
                    elevation = (16.dp + 18.dp * glowAnim.value),
                    shape = RoundedCornerShape(20.dp),
                    ambientColor = themeConfig.capsuleAccentColor.copy(alpha = 0.30f + 0.25f * glowAnim.value),
                    spotColor   = themeConfig.capsuleAccentColor.copy(alpha = 0.40f + 0.30f * glowAnim.value)
                )
                .clip(RoundedCornerShape(20.dp))
                .background(bgBrush),
            contentAlignment = Alignment.CenterStart
        ) {
            // ── 赛博/故障/熔岩：微粒子 Canvas 层（在文字下方） ────────────
            if (themeId in listOf("cyberpunk", "glitch", "lava", "rpg")) {
                CeremonyParticles(
                    themeId    = themeId,
                    accentColor = themeConfig.capsuleAccentColor,
                    modifier   = Modifier.matchParentSize()
                )
            }

            // ── 左侧主题色竖条（打字进度条） ─────────────────────────────
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 0.dp)
                    .width(3.dp)
                    .fillMaxSize()
            ) {
                // 底层轨道（极淡）
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(themeConfig.capsuleAccentColor.copy(alpha = 0.08f))
                )
                // 填充段（从顶部向下，高度随 barFill）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxSize(barFill.value)
                        .align(Alignment.TopStart)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    themeConfig.capsuleAccentColor.copy(alpha = 0.9f),
                                    themeConfig.capsuleAccentColor.copy(alpha = 0.45f)
                                )
                            )
                        )
                )
            }

            // ── 内容区 ─────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .graphicsLayer { alpha = contentAlpha.value }
                    .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 顶部小标签（主题次要色）
                Text(
                    text = when (themeId) {
                        "cyberpunk" -> "> INTENT_"
                        "glitch"    -> "// INTENT"
                        "rpg"       -> "⚔ 冒险目标"
                        "lava"      -> "🔥 此刻意图"
                        "sakura"    -> "🌸 心中所念"
                        "moon"      -> "🌙 此刻意图"
                        "deep_sea"  -> "🌊 潜入目的"
                        else        -> "此刻的意图"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = themeConfig.ceremonySubLabelColor,
                    letterSpacing = 1.5.sp,
                    fontFamily = font
                )

                // 打字机文字 + 光标
                val displayText = purposeText.take(typedCharCount)
                val isTyping    = !typingDone

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Text(
                        text = displayText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = themeConfig.ceremonyTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = font
                    )
                    // 闪烁光标（打字进行中显示）
                    if (isTyping) {
                        val cursorTransition = rememberInfiniteTransition(label = "cursor")
                        val cursorAlpha by cursorTransition.animateFloat(
                            initialValue = 1f,
                            targetValue  = 0f,
                            animationSpec = infiniteRepeatable(
                                animation  = tween(380, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "cursor_blink"
                        )
                        Text(
                            text  = if (themeConfig.capsuleUseMonoFont) "_" else "▌",
                            fontSize = 16.sp,
                            color = themeConfig.capsuleAccentColor.copy(alpha = cursorAlpha * 0.80f),
                            fontFamily = font
                        )
                    } else {
                        // 打字完成：主题色呼吸小点（确认感）
                        androidx.compose.foundation.Canvas(
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            drawCircle(
                                color  = themeConfig.capsuleAccentColor.copy(alpha = 0.55f + 0.35f * glowAnim.value),
                                radius = 4.dp.toPx() * (0.85f + 0.2f * glowAnim.value),
                                center = Offset(4.dp.toPx(), 0f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  微粒子背景（仅用于 cyberpunk / glitch / lava / rpg 主题）
//  极轻量：固定 8 个粒子，用 rememberInfiniteTransition 驱动，零 allocation
// ════════════════════════════════════════════════════════════════════════════

private data class CeremonyParticle(
    val x: Float,   // 0..1 相对宽
    val y: Float,   // 0..1 相对高
    val r: Float,   // 半径系数
    val phase: Float // 时间偏移
)

private val CEREMONY_PARTICLES = listOf(
    CeremonyParticle(0.08f, 0.30f, 1.6f, 0.0f),
    CeremonyParticle(0.18f, 0.70f, 1.0f, 0.3f),
    CeremonyParticle(0.55f, 0.20f, 1.4f, 0.6f),
    CeremonyParticle(0.72f, 0.80f, 0.9f, 0.1f),
    CeremonyParticle(0.88f, 0.40f, 1.2f, 0.8f),
    CeremonyParticle(0.42f, 0.65f, 0.8f, 0.5f),
    CeremonyParticle(0.93f, 0.75f, 1.1f, 0.2f),
    CeremonyParticle(0.30f, 0.35f, 0.7f, 0.9f)
)

@Composable
private fun CeremonyParticles(
    themeId: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "ceremony_particles")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ceremony_time"
    )

    androidx.compose.foundation.Canvas(modifier = modifier) {
        CEREMONY_PARTICLES.forEach { p ->
            val t = (time + p.phase) % 1f
            // 透明度：正弦波，0 → 峰值 → 0
            val alpha = kotlin.math.sin(t * kotlin.math.PI.toFloat()) *
                when (themeId) {
                    "glitch" -> 0.55f
                    "lava"   -> 0.50f
                    "rpg"    -> 0.40f
                    else     -> 0.45f  // cyberpunk
                }
            if (alpha <= 0.02f) return@forEach

            val px = size.width  * p.x
            val py = size.height * p.y
            val radius = p.r.dp.toPx()

            drawCircle(
                color  = accentColor.copy(alpha = alpha),
                radius = radius,
                center = Offset(px, py)
            )
        }
    }
}
