package com.life.mindfulnessapp.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import kotlin.random.Random

/**
 * 退出仪式：「获得勋章」全屏动画（精致版）
 *
 * 视觉层次（从后到前）：
 *  1. 深空背景 + 全屏金色 bloom 晕染
 *  2. 菱形+圆点混合粒子扩散
 *  3. 16条交替宽窄光芒射线（缓慢旋转）
 *  4. 勋章外环：双层浮雕圆环 + 点线装饰
 *  5. 勋章主体：圆形 + 径向渐变填充 + 内嵌星纹
 *  6. 顶部丝带挂钩
 *  7. 对勾：描绘动画（从无到有）
 *  8. 文字：主标题 + 装饰分割线 + 副标题
 */
@Composable
fun DismissCeremonyOverlayView(
    themeId: String = "default",
    dismissCount: Int = 1,
    onFinished: () -> Unit
) {
    // ── 色彩体系 ──────────────────────────────────────────────────────────
    val gold        = Color(0xFFFFD166)   // 主金
    val goldLight   = Color(0xFFFFF3B0)   // 亮金（高光）
    val goldMid     = Color(0xFFE8A020)   // 中金（阴影过渡）
    val goldDark    = Color(0xFF8B5E00)   // 暗金（最深阴影）
    val goldGlow    = Color(0xFFFFCC44)   // bloom 晕染色
    val bgColor     = Color(0xFF080A0E)   // 近纯黑深空

    // ── 动画状态 ──────────────────────────────────────────────────────────
    val bgAlpha       = remember { Animatable(0f) }
    val bloomAlpha    = remember { Animatable(0f) }     // 全屏金色 bloom
    val medalScale    = remember { Animatable(0f) }
    val medalAlpha    = remember { Animatable(0f) }
    val outerRingScale= remember { Animatable(0.5f) }
    val outerRingAlpha= remember { Animatable(0f) }
    val raysAlpha     = remember { Animatable(0f) }
    val raysRotation  = remember { Animatable(0f) }
    val checkProgress = remember { Animatable(0f) }     // 对勾描绘进度 0→1
    val ribbonAlpha   = remember { Animatable(0f) }     // 顶部丝带挂钩
    val textAlpha     = remember { Animatable(0f) }
    val textOffset    = remember { Animatable(24f) }
    val dividerWidth  = remember { Animatable(0f) }     // 分割线展开进度

    // ── 粒子系统（菱形 + 圆点各 10 个）──────────────────────────────────
    val particleCount = 20
    val particleProgress = remember { List(particleCount) { Animatable(0f) } }
    val particleAngles = remember {
        List(particleCount) { i ->
            (i * 360f / particleCount) + Random.nextFloat() * (360f / particleCount * 0.6f)
        }
    }
    val particleSizes = remember { List(particleCount) { 2.5f + Random.nextFloat() * 3.5f } }

    LaunchedEffect(Unit) {

        // ══ Step 1 · 背景 + bloom 淡入 ════════════════════════════════════
        launch { bgAlpha.animateTo(1f, tween(200, easing = FastOutSlowInEasing)) }
        bloomAlpha.animateTo(0.25f, tween(400, easing = FastOutSlowInEasing))

        // ══ Step 2 · 外环从外向内弹入 ════════════════════════════════════
        delay(60L)
        launch { outerRingAlpha.animateTo(1f, tween(220)) }
        outerRingScale.animateTo(
            1f, spring(dampingRatio = 0.60f, stiffness = Spring.StiffnessMediumLow)
        )

        // ══ Step 3 · 勋章主体弹出 ════════════════════════════════════════
        delay(40L)
        launch { medalAlpha.animateTo(1f, tween(160)) }
        launch { ribbonAlpha.animateTo(1f, tween(260, delayMillis = 80)) }
        medalScale.animateTo(
            1f, spring(dampingRatio = 0.48f, stiffness = Spring.StiffnessMedium)
        )

        // ══ Step 4 · 射线淡入 + 旋转 ════════════════════════════════════
        launch { raysAlpha.animateTo(0.90f, tween(320)) }
        launch {
            raysRotation.animateTo(30f, tween(2800, easing = LinearEasing))
        }

        // ══ Step 5 · 对勾「描绘」动画 ════════════════════════════════════
        delay(80L)
        checkProgress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))

        // ══ Step 6 · 粒子扩散 ════════════════════════════════════════════
        launch {
            particleProgress.forEachIndexed { i, anim ->
                launch {
                    delay(Random.nextLong(0, 180))
                    anim.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
                }
            }
        }

        // ══ Step 7 · 文字 + 分割线 滑入 ═════════════════════════════════
        delay(100L)
        launch { textAlpha.animateTo(1f, tween(380)) }
        launch { textOffset.animateTo(0f, tween(400, easing = FastOutSlowInEasing)) }
        dividerWidth.animateTo(1f, tween(500, easing = FastOutSlowInEasing))

        // ══ Step 8 · bloom 脉冲（微弱呼吸感）════════════════════════════
        launch {
            bloomAlpha.animateTo(0.40f, tween(600, easing = FastOutSlowInEasing))
            bloomAlpha.animateTo(0.22f, tween(600, easing = FastOutSlowInEasing))
        }

        // ══ Step 9 · 停留 ════════════════════════════════════════════════
        delay(1000L)

        // ══ Step 10 · 整体淡出 ═══════════════════════════════════════════
        launch { medalAlpha.animateTo(0f, tween(300)) }
        launch { outerRingAlpha.animateTo(0f, tween(280)) }
        launch { raysAlpha.animateTo(0f, tween(220)) }
        launch { textAlpha.animateTo(0f, tween(260)) }
        launch { ribbonAlpha.animateTo(0f, tween(260)) }
        launch { bloomAlpha.animateTo(0f, tween(300)) }
        bgAlpha.animateTo(0f, tween(340, easing = FastOutSlowInEasing))

        delay(40L)
        onFinished()
    }

    // ── 渲染 ──────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor.copy(alpha = bgAlpha.value)),
        contentAlignment = Alignment.Center
    ) {

        // ── 全屏 bloom 光晕（背景层）──────────────────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val bloomR = size.minDimension * 0.72f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        goldGlow.copy(alpha = bloomAlpha.value),
                        goldGlow.copy(alpha = bloomAlpha.value * 0.3f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = bloomR
                ),
                radius = bloomR,
                center = Offset(cx, cy)
            )
        }

        // ── 粒子层 ────────────────────────────────────────────────────────
        Canvas(modifier = Modifier.size(340.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val maxDist = size.minDimension * 0.50f

            particleProgress.forEachIndexed { i, anim ->
                val p = anim.value
                if (p <= 0f) return@forEachIndexed
                val angleRad = particleAngles[i] * PI.toFloat() / 180f
                val dist = maxDist * p
                val px = cx + cos(angleRad) * dist
                val py = cy + sin(angleRad) * dist
                val fade = ((1f - p) * 1.4f).coerceIn(0f, 1f) * raysAlpha.value
                val sz = particleSizes[i].dp.toPx() * (1f - p * 0.4f)

                if (i % 2 == 0) {
                    // 菱形粒子
                    val diamond = Path().apply {
                        moveTo(px, py - sz)
                        lineTo(px + sz * 0.55f, py)
                        lineTo(px, py + sz)
                        lineTo(px - sz * 0.55f, py)
                        close()
                    }
                    drawPath(
                        diamond,
                        color = if (i % 4 == 0) goldLight else gold,
                        alpha = fade
                    )
                } else {
                    // 圆点粒子
                    drawCircle(
                        color = if (i % 3 == 0) goldLight else gold,
                        radius = sz * 0.75f,
                        center = Offset(px, py),
                        alpha = fade
                    )
                }
            }
        }

        // ── 射线层（16条，长短交替，菱形笔触感）─────────────────────────
        Canvas(
            modifier = Modifier
                .size(280.dp)
                .graphicsLayer {
                    alpha = raysAlpha.value
                    rotationZ = raysRotation.value
                }
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val rayCount = 16

            repeat(rayCount) { i ->
                val isMajor = i % 2 == 0
                val innerR = size.minDimension * (if (isMajor) 0.30f else 0.32f)
                val outerR = size.minDimension * (if (isMajor) 0.50f else 0.41f)
                val angleRad = (i * 360f / rayCount) * PI.toFloat() / 180f
                val sx = cx + cos(angleRad) * innerR
                val sy = cy + sin(angleRad) * innerR
                val ex = cx + cos(angleRad) * outerR
                val ey = cy + sin(angleRad) * outerR

                // 主射线（带渐隐：内浅→外深模拟锥形）
                val rayAlpha = if (isMajor) 0.88f else 0.45f
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            gold.copy(alpha = rayAlpha),
                            gold.copy(alpha = rayAlpha * 0.1f)
                        ),
                        start = Offset(sx, sy),
                        end = Offset(ex, ey)
                    ),
                    start = Offset(sx, sy),
                    end = Offset(ex, ey),
                    strokeWidth = (if (isMajor) 2.2f else 1.0f).dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        // ── 勋章外环（双层浮雕）──────────────────────────────────────────
        Canvas(
            modifier = Modifier
                .size(160.dp)
                .graphicsLayer {
                    alpha = outerRingAlpha.value
                    scaleX = outerRingScale.value
                    scaleY = outerRingScale.value
                }
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = size.minDimension / 2f

            // 最外层：半透明宽光晕环
            drawCircle(
                color = gold.copy(alpha = 0.12f),
                radius = r - 0.5f.dp.toPx(),
                center = Offset(cx, cy),
                style = Stroke(width = 6.dp.toPx())
            )
            // 外主环（亮金）
            drawCircle(
                color = gold,
                radius = r - 4.dp.toPx(),
                center = Offset(cx, cy),
                style = Stroke(width = 1.8f.dp.toPx())
            )
            // 内细环（亮金更透明）
            drawCircle(
                color = goldLight.copy(alpha = 0.55f),
                radius = r - 8.5f.dp.toPx(),
                center = Offset(cx, cy),
                style = Stroke(width = 0.7f.dp.toPx())
            )
            // 最内层虚线点环（间隔小圆点装饰，用12个小点模拟）
            val dotR = r - 12.dp.toPx()
            val dotCount = 36
            repeat(dotCount) { i ->
                if (i % 3 != 0) return@repeat  // 每隔两个画一个
                val a = (i * 360f / dotCount) * PI.toFloat() / 180f
                val dx = cx + cos(a) * dotR
                val dy = cy + sin(a) * dotR
                drawCircle(
                    color = gold.copy(alpha = 0.40f),
                    radius = 1.2f.dp.toPx(),
                    center = Offset(dx, dy)
                )
            }
        }

        // ── 勋章主体（圆形+径向渐变+内部星纹）────────────────────────────
        Canvas(
            modifier = Modifier
                .size(120.dp)
                .graphicsLayer {
                    alpha = medalAlpha.value
                    scaleX = medalScale.value
                    scaleY = medalScale.value
                }
        ) {
            drawPremiumMedal(
                gold = gold,
                goldLight = goldLight,
                goldMid = goldMid,
                goldDark = goldDark,
                checkProgress = checkProgress.value
            )
        }

        // ── 顶部丝带挂钩 ──────────────────────────────────────────────────
        Canvas(
            modifier = Modifier
                .size(120.dp)
                .graphicsLayer {
                    alpha = ribbonAlpha.value * medalAlpha.value
                    scaleX = medalScale.value
                    scaleY = medalScale.value
                }
        ) {
            drawMedalRibbon(gold = gold, goldLight = goldLight, goldDark = goldDark)
        }

        // ── 文字区域 ───────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    alpha = textAlpha.value
                    translationY = textOffset.value.dp.toPx()
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(130.dp))   // 勋章 + 丝带高度占位

            Spacer(Modifier.height(20.dp))

            Text(
                text = "克制 · 已解锁",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = goldLight,
                letterSpacing = 3.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(10.dp))

            // 装饰分割线（动态展开）
            Canvas(modifier = Modifier.size(width = 120.dp, height = 1.dp)) {
                val lineW = size.width * dividerWidth.value
                val lineStart = (size.width - lineW) / 2f
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            gold.copy(alpha = 0.7f),
                            gold.copy(alpha = 0.7f),
                            Color.Transparent
                        ),
                        startX = lineStart,
                        endX = lineStart + lineW
                    ),
                    start = Offset(lineStart, 0f),
                    end = Offset(lineStart + lineW, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = if (dismissCount <= 1) "今日第 1 次守住了" else "今日已守住 $dismissCount 次",
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF999999),
                letterSpacing = 0.8.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 勋章主体绘制：圆形 + 多层径向渐变 + 内嵌8角星纹 + 对勾描绘动画
// ─────────────────────────────────────────────────────────────────────────────
private fun DrawScope.drawPremiumMedal(
    gold: Color,
    goldLight: Color,
    goldMid: Color,
    goldDark: Color,
    checkProgress: Float
) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val medalR = w * 0.42f

    // ── 层1：深色外阴影光晕 ───────────────────────────────────────────────
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                goldMid.copy(alpha = 0.30f),
                goldMid.copy(alpha = 0.10f),
                Color.Transparent
            ),
            center = Offset(cx, cy),
            radius = medalR * 1.35f
        ),
        radius = medalR * 1.35f,
        center = Offset(cx, cy)
    )

    // ── 层2：勋章圆形主体（模拟金属质感：顶亮底暗）──────────────────────
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                goldLight,          // 中心高光
                gold,               // 主金
                goldMid,            // 阴影过渡
                goldDark.copy(alpha = 0.85f)  // 边缘暗影
            ),
            center = Offset(cx * 0.78f, cy * 0.72f),  // 高光偏左上，模拟光源
            radius = medalR * 1.8f
        ),
        radius = medalR,
        center = Offset(cx, cy)
    )

    // ── 层3：内嵌细边框（亮金描边，增强立体感）────────────────────────────
    drawCircle(
        color = goldLight.copy(alpha = 0.70f),
        radius = medalR - 1.5f.dp.toPx(),
        center = Offset(cx, cy),
        style = Stroke(width = 1.2f.dp.toPx())
    )
    // 内凹暗边（贴里）
    drawCircle(
        color = goldDark.copy(alpha = 0.50f),
        radius = medalR - 3.5f.dp.toPx(),
        center = Offset(cx, cy),
        style = Stroke(width = 0.8f.dp.toPx())
    )

    // ── 层4：内部8角星纹（精细装饰，半透明）─────────────────────────────
    val starR1 = medalR * 0.68f   // 外顶点
    val starR2 = medalR * 0.42f   // 内顶点
    val starPath = Path()
    val starPoints = 8
    for (i in 0 until starPoints * 2) {
        val a = (i * 360f / (starPoints * 2) - 90f) * PI.toFloat() / 180f
        val r = if (i % 2 == 0) starR1 else starR2
        val px = cx + cos(a) * r
        val py = cy + sin(a) * r
        if (i == 0) starPath.moveTo(px, py) else starPath.lineTo(px, py)
    }
    starPath.close()
    drawPath(
        starPath,
        color = goldDark.copy(alpha = 0.22f)
    )
    drawPath(
        starPath,
        color = goldLight.copy(alpha = 0.18f),
        style = Stroke(width = 0.6f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // ── 层5：对勾（描绘动画）──────────────────────────────────────────────
    if (checkProgress > 0f) {
        val checkPath = Path().apply {
            // 精心调整坐标：对勾重心稍偏上，笔触饱满
            val x1 = cx - w * 0.185f;  val y1 = cy + h * 0.025f
            val x2 = cx - w * 0.010f;  val y2 = cy + h * 0.185f
            val x3 = cx + w * 0.240f;  val y3 = cy - h * 0.150f
            moveTo(x1, y1)
            lineTo(x2, y2)
            lineTo(x3, y3)
        }

        // 用 PathMeasure 截取 checkProgress 段，实现描绘效果
        val measure = PathMeasure()
        measure.setPath(checkPath, false)
        val totalLen = measure.length
        val partialPath = Path()
        measure.getSegment(0f, totalLen * checkProgress, partialPath, true)

        // 底层粗描（深色，对比）
        drawPath(
            partialPath,
            color = goldDark.copy(alpha = 0.90f),
            style = Stroke(width = 5.2f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        // 中层主笔（亮金）
        drawPath(
            partialPath,
            color = goldLight,
            style = Stroke(width = 3.5f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        // 表面高光细线
        drawPath(
            partialPath,
            color = Color.White.copy(alpha = 0.55f),
            style = Stroke(width = 1.2f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 顶部丝带挂钩绘制（勋章顶端的梯形挂件 + 细节纹理）
// ─────────────────────────────────────────────────────────────────────────────
private fun DrawScope.drawMedalRibbon(
    gold: Color,
    goldLight: Color,
    goldDark: Color
) {
    val w = size.width
    val h = size.height
    val cx = w / 2f

    // 挂钩：梯形（勋章正上方，偏小）
    val ribbonTop    = h * 0.00f
    val ribbonBottom = h * 0.12f
    val topHalfW     = w * 0.095f
    val botHalfW     = w * 0.130f

    val ribbonPath = Path().apply {
        moveTo(cx - topHalfW, ribbonTop)
        lineTo(cx + topHalfW, ribbonTop)
        lineTo(cx + botHalfW, ribbonBottom)
        lineTo(cx - botHalfW, ribbonBottom)
        close()
    }

    // 填充（渐变：顶亮底暗）
    drawPath(
        ribbonPath,
        brush = Brush.verticalGradient(
            colors = listOf(goldLight, gold, goldDark.copy(alpha = 0.7f)),
            startY = ribbonTop,
            endY = ribbonBottom
        )
    )
    // 描边
    drawPath(
        ribbonPath,
        color = goldLight.copy(alpha = 0.65f),
        style = Stroke(width = 0.8f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    // 中间竖分缝（细节）
    drawLine(
        color = goldDark.copy(alpha = 0.50f),
        start = Offset(cx, ribbonTop),
        end = Offset(cx, ribbonBottom),
        strokeWidth = 0.6f.dp.toPx()
    )
}
