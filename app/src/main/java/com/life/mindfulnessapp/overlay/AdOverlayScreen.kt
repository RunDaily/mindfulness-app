package com.life.mindfulnessapp.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import com.life.mindfulnessapp.ui.theme.MindfulnessAppTheme
import kotlinx.coroutines.delay

// ── 广告页配色 ────────────────────────────────────────────────────────────────
private val AdBg          = Color(0xFF0D0D14)
private val AdSkipBg      = Color(0x99000000)   // 跳过按钮半透明黑底
private val AdSkipColor   = Color(0xFFFFD166)   // 跳过按钮文字：暖黄
private val AdSkipBorder  = Color(0x66FFD166)
private val AdMutedText   = Color(0xFF6B7280)
private val AdLabelBg     = Color(0x99000000)
private val AdLabelText   = Color(0xFFAEB5C0)

/** 广告倒计时总秒数（5 秒） */
private const val AD_TOTAL_SECONDS = 5

/** 最少强制观看秒数（前 3 秒不可跳过） */
private const val AD_SKIP_AFTER_SECONDS = 3

/**
 * 全屏广告页（非 VIP 用户超限后弹出）。
 *
 * 设计规范：
 *  - 广告内容铺满全屏（模拟真实广告素材占满屏幕）
 *  - 右上角悬浮「跳过广告」胶囊按钮，前 3 秒灰显不可点，3 秒后变黄可点击
 *  - 圆弧倒计时指示器随时间填充
 *  - 左上角「广告」小标签
 *  - 底部半透明渐变区说明广告原因
 *  - 全程 BackHandler 拦截返回键
 *
 * @param onAdFinished 广告看完或用户跳过后回调，由 OverlayManager 衔接超限页
 */
@Composable
fun AdOverlayScreen(
    onAdFinished: () -> Unit
) {
    // ── 入场淡入 ─────────────────────────────────────────────────────────────
    val bgAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        bgAlpha.animateTo(1f, animationSpec = tween(250))
    }

    // ── 倒计时状态 ──────────────────────────────────────────────────────────
    var remainingSeconds by remember { mutableIntStateOf(AD_TOTAL_SECONDS) }
    var canSkip          by remember { mutableStateOf(false) }
    var isFinished       by remember { mutableStateOf(false) }

    // 圆弧进度 0f→1f，对应完整倒计时时长
    val arcProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        arcProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = AD_TOTAL_SECONDS * 1000, easing = LinearEasing)
        )
    }

    // 倒计时主循环
    LaunchedEffect(Unit) {
        repeat(AD_TOTAL_SECONDS) { tick ->
            delay(1000L)
            val left = AD_TOTAL_SECONDS - tick - 1
            remainingSeconds = left
            // 过了强制观看时长后解锁跳过
            if (AD_TOTAL_SECONDS - left >= AD_SKIP_AFTER_SECONDS) {
                canSkip = true
            }
        }
        // 倒计时自然结束
        if (!isFinished) {
            isFinished = true
            onAdFinished()
        }
    }

    // 跳过按钮可用状态动画
    val skipAlpha by animateFloatAsState(
        targetValue = if (canSkip) 1f else 0.38f,
        animationSpec = tween(300),
        label = "skipAlpha"
    )

    // 注意：Back 键拦截由外层 FrameLayout.dispatchKeyEvent 负责，
    // 此处不使用 BackHandler（Overlay 无 OnBackPressedDispatcherOwner，会崩溃）。

    MindfulnessAppTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(bgAlpha.value)
        ) {

            // ════════════════════════════════════════════════════════════════
            // 全屏广告主视觉（铺满整个屏幕）
            // ════════════════════════════════════════════════════════════════
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // 全屏渐变背景（模拟广告视觉稿）
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF2D1B69),
                                    Color(0xFF1A0A45),
                                    Color(0xFF0D0520),
                                    Color(0xFF050010)
                                ),
                                radius = 1200f
                            )
                        )
                )

                // 装饰光晕层
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.55f)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0x406C63FF),
                                    Color(0x208B5CF6),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // 广告主内容：居中大块视觉
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    // 品牌图标
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF7C3AED), Color(0xFF4F46E5)),
                                    start = Offset(0f, 0f),
                                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🧘", fontSize = 44.sp)
                    }

                    // 品牌主标题
                    Text(
                        text = "专注力训练营",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        letterSpacing = 0.5.sp
                    )

                    // 副标题
                    Text(
                        text = "每天 10 分钟\n科学重塑你的注意力",
                        fontSize = 18.sp,
                        color = Color.White.copy(alpha = 0.80f),
                        textAlign = TextAlign.Center,
                        lineHeight = 26.sp
                    )

                    Spacer(Modifier.height(8.dp))

                    // 数据亮点行
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        listOf("50万+" to "活跃用户", "91%" to "好评率", "21天" to "见效周期")
                            .forEach { (num, label) ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.10f))
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = num,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFBBA4FF)
                                    )
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.60f)
                                    )
                                }
                            }
                    }

                    Spacer(Modifier.height(8.dp))

                    // CTA 按钮
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF7C3AED), Color(0xFF4F46E5))
                                )
                            )
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "立即免费体验 →",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // ════════════════════════════════════════════════════════════════
            // 顶部浮层（左：广告标签  右：跳过按钮）
            // ════════════════════════════════════════════════════════════════
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左上角「广告」标签
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AdLabelBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "广告",
                        fontSize = 11.sp,
                        color = AdLabelText,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp
                    )
                }

                // 右上角跳过按钮
                Row(
                    modifier = Modifier
                        .alpha(skipAlpha)
                        .clip(RoundedCornerShape(20.dp))
                        .background(AdSkipBg)
                        .border(1.dp, AdSkipBorder, RoundedCornerShape(20.dp))
                        .clickable(enabled = canSkip && !isFinished) {
                            if (!isFinished) {
                                isFinished = true
                                onAdFinished()
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 圆弧倒计时指示器
                    Box(
                        modifier = Modifier.size(22.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // 轨道圆圈（灰）
                            drawArc(
                                color = Color.White.copy(alpha = 0.25f),
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                            )
                            // 进度弧（随时间填充，可跳后变黄）
                            drawArc(
                                color = if (canSkip) AdSkipColor else Color.White.copy(alpha = 0.50f),
                                startAngle = -90f,
                                sweepAngle = 360f * arcProgress.value,
                                useCenter = false,
                                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        Text(
                            text = if (remainingSeconds > 0) "$remainingSeconds" else "✓",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (canSkip) AdSkipColor else Color.White.copy(alpha = 0.70f)
                        )
                    }

                    Text(
                        text = if (canSkip) "跳过广告" else "跳过广告",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (canSkip) AdSkipColor else Color.White.copy(alpha = 0.55f)
                    )
                }
            }

            // ════════════════════════════════════════════════════════════════
            // 底部半透明渐变区：说明广告原因
            // ════════════════════════════════════════════════════════════════
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xCC050010)),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "💡 你正在使用免费版，广告帮助我们维持服务运营。开通会员即可永久去除广告。",
                    fontSize = 12.sp,
                    color = AdMutedText,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
