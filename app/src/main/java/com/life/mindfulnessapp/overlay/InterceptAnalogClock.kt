package com.life.mindfulnessapp.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.domain.model.MomentTimeContexts
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

/**
 * 拦截页拟物时钟 —— 靠形态精致（比例 / 针型 / 刻度结构），不做光影拟物。
 *
 * - Peek：时/分针 + 12/3/6/9，无秒针
 * - Fullscreen：完整表盘 + 流畅扫秒；数字与指针同源
 */
@Composable
internal fun InterceptPeekClock(
    themeConfig: InterceptThemeConfig,
    modifier: Modifier = Modifier,
    size: Dp = 76.dp,
    onClick: (() -> Unit)? = null
) {
    val haptics = LocalHapticFeedback.current
    val palette = remember(themeConfig) { ClockPalette.from(themeConfig) }
    val reading = rememberClockReading(smoothSeconds = false)

    Column(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onClick()
                        }
                    )
                } else Modifier
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnalogClockFace(
            palette = palette,
            size = size,
            reading = reading,
            showSeconds = false,
            style = ClockFaceStyle.Peek
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = reading.hm,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = palette.labelSecondary,
            letterSpacing = 0.6.sp
        )
    }
}

@Composable
internal fun InterceptFullscreenClock(
    themeConfig: InterceptThemeConfig,
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(280)),
        exit = fadeOut(tween(200)),
        modifier = modifier
    ) {
        val palette = remember(themeConfig) { ClockPalette.from(themeConfig) }
        val reading = rememberClockReading(smoothSeconds = true)
        val moment = remember(reading.minuteBucket) { MomentTimeContexts.now() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.fullscreenBg)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss
                )
                .statusBarsPadding()
                .padding(horizontal = 28.dp, vertical = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDismiss
                    )
            ) {
                Text(
                    text = moment.dateLine,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.labelSecondary,
                    letterSpacing = 0.3.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = moment.shiChenName + "  ·  " + moment.shiChenRange,
                    fontSize = 13.sp,
                    color = palette.accent.copy(alpha = 0.88f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                BoxWithConstraints {
                    val dial = minOf(maxWidth, 320.dp)
                    AnalogClockFace(
                        palette = palette,
                        size = dial,
                        reading = reading,
                        showSeconds = true,
                        style = ClockFaceStyle.Fullscreen
                    )
                }

                Spacer(modifier = Modifier.height(30.dp))

                Text(
                    text = reading.hms,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Light,
                    color = palette.labelPrimary,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = moment.yearDayLine,
                    fontSize = 13.sp,
                    color = palette.labelMuted,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(40.dp))
                Text(
                    text = "轻触关闭",
                    fontSize = 12.sp,
                    color = palette.labelMuted.copy(alpha = 0.7f),
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

private enum class ClockFaceStyle { Peek, Fullscreen }

/** 扁平色板：只服务形态可读，不做材质光泽 */
private data class ClockPalette(
    val dial: Color,
    val ring: Color,
    val track: Color,
    val tickHour: Color,
    val tickMinute: Color,
    val numeral: Color,
    val hourHand: Color,
    val minuteHand: Color,
    val accent: Color,
    val hub: Color,
    val fullscreenBg: Color,
    val labelPrimary: Color,
    val labelSecondary: Color,
    val labelMuted: Color
) {
    companion object {
        fun from(theme: InterceptThemeConfig): ClockPalette {
            val isDark = theme.bgColor.luminance() < 0.45f
            return if (isDark) {
                ClockPalette(
                    dial = theme.surfaceColor,
                    ring = theme.dividerColor.copy(alpha = 0.95f),
                    track = theme.dividerColor.copy(alpha = 0.55f),
                    tickHour = theme.textPrimary.copy(alpha = 0.92f),
                    tickMinute = theme.textSecondary.copy(alpha = 0.42f),
                    numeral = theme.textPrimary.copy(alpha = 0.88f),
                    hourHand = theme.textPrimary,
                    minuteHand = theme.textPrimary.copy(alpha = 0.9f),
                    accent = theme.accentColor,
                    hub = theme.textPrimary,
                    fullscreenBg = theme.bgColor,
                    labelPrimary = theme.textPrimary,
                    labelSecondary = theme.textSecondary.copy(alpha = 0.95f),
                    labelMuted = theme.textTertiary.copy(alpha = 0.95f)
                )
            } else {
                ClockPalette(
                    dial = Color.White,
                    ring = Color(0xFFC7C7CC),
                    track = Color(0xFFD1D1D6),
                    tickHour = Color(0xFF1C1C1E),
                    tickMinute = Color(0xFF8E8E93).copy(alpha = 0.55f),
                    numeral = Color(0xFF1C1C1E).copy(alpha = 0.88f),
                    hourHand = Color(0xFF1C1C1E),
                    minuteHand = Color(0xFF2C2C2E),
                    accent = theme.accentColor,
                    hub = Color(0xFF1C1C1E),
                    fullscreenBg = theme.bgColor,
                    labelPrimary = theme.textPrimary,
                    labelSecondary = theme.textSecondary,
                    labelMuted = theme.textTertiary
                )
            }
        }
    }
}

private data class ClockReading(
    val nowMs: Long,
    val hourAngleDeg: Float,
    val minuteAngleDeg: Float,
    val secondAngleDeg: Float,
    val hm: String,
    val hms: String,
    val minuteBucket: Long
)

@Composable
private fun AnalogClockFace(
    palette: ClockPalette,
    size: Dp,
    reading: ClockReading,
    showSeconds: Boolean,
    style: ClockFaceStyle
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val fullscreen = style == ClockFaceStyle.Fullscreen

    val numeralStyle = TextStyle(
        color = palette.numeral,
        fontSize = if (fullscreen) 14.sp else 8.5.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center
    )

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(palette.dial),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f
            val c = Offset(this.size.width / 2f, this.size.height / 2f)

            // 结构：外圈 → 分轨 → 刻度 → 数字 → 针 → 轴芯
            val outerStroke = if (fullscreen) 2.2.dp.toPx() else 1.4.dp.toPx()
            val outerR = r - outerStroke / 2f
            drawCircle(
                color = palette.ring,
                radius = outerR,
                style = Stroke(width = outerStroke)
            )

            val trackR = r * (if (fullscreen) 0.86f else 0.82f)
            if (fullscreen) {
                drawCircle(
                    color = palette.track,
                    radius = trackR,
                    style = Stroke(width = 0.9.dp.toPx())
                )
            }

            val tickOuter = trackR - (if (fullscreen) 1.5.dp else 0.5.dp).toPx()
            drawMinuteTrack(
                center = c,
                outer = tickOuter,
                dense = fullscreen,
                hourColor = palette.tickHour,
                minuteColor = palette.tickMinute,
                // Peek 四方位留给数字，不画粗刻
                skipCardinalsForPeek = !fullscreen
            )

            val labels = if (fullscreen) (1..12).toList() else listOf(12, 3, 6, 9)
            // 数字落在分轨内侧，与刻度留气口
            val labelRadius = r * (if (fullscreen) 0.68f else 0.58f)
            labels.forEach { hour ->
                val angle = Math.toRadians(hour * 30.0 - 90.0)
                val layout = textMeasurer.measure(
                    text = hour.toString(),
                    style = numeralStyle,
                    density = density
                )
                val x = c.x + cos(angle).toFloat() * labelRadius - layout.size.width / 2f
                val y = c.y + sin(angle).toFloat() * labelRadius - layout.size.height / 2f
                drawText(layout, topLeft = Offset(x, y))
            }

            // 针长：时针到数字圈内侧，分针近分轨，秒针贴分轨内沿
            val hourLen = r * (if (fullscreen) 0.48f else 0.50f)
            val minuteLen = r * (if (fullscreen) 0.70f else 0.68f)
            val secondLen = tickOuter - 2.dp.toPx()

            // 时针：短宽 baton，略收尖
            drawBatonHand(
                center = c,
                angleDeg = reading.hourAngleDeg,
                length = hourLen,
                baseHalfWidth = (if (fullscreen) 3.6.dp else 2.4.dp).toPx(),
                tipHalfWidth = (if (fullscreen) 1.6.dp else 1.1.dp).toPx(),
                overhang = r * 0.10f,
                color = palette.hourHand
            )
            // 分针：细长 baton
            drawBatonHand(
                center = c,
                angleDeg = reading.minuteAngleDeg,
                length = minuteLen,
                baseHalfWidth = (if (fullscreen) 2.2.dp else 1.5.dp).toPx(),
                tipHalfWidth = (if (fullscreen) 0.9.dp else 0.7.dp).toPx(),
                overhang = r * 0.12f,
                color = palette.minuteHand
            )
            if (showSeconds) {
                drawSecondsNeedle(
                    center = c,
                    angleDeg = reading.secondAngleDeg,
                    length = secondLen,
                    color = palette.accent,
                    overhang = r * 0.16f
                )
            }

            // 轴芯：小双环，压住三针交点
            val hubOuter = if (fullscreen) 4.8.dp.toPx() else 3.4.dp.toPx()
            drawCircle(color = palette.accent, radius = hubOuter)
            drawCircle(color = palette.hub, radius = hubOuter * 0.42f)
        }
    }
}

/** 分轨刻度：时标 / 分标层次分明 */
private fun DrawScope.drawMinuteTrack(
    center: Offset,
    outer: Float,
    dense: Boolean,
    hourColor: Color,
    minuteColor: Color,
    skipCardinalsForPeek: Boolean
) {
    val steps = if (dense) 60 else 12
    for (i in 0 until steps) {
        val isHour = if (dense) i % 5 == 0 else true
        val isCardinal = if (dense) i % 15 == 0 else i % 3 == 0
        if (skipCardinalsForPeek && isCardinal) continue

        val angle = Math.toRadians(i * (360.0 / steps) - 90.0)
        val len = when {
            isCardinal && dense -> 9.5.dp.toPx()
            isHour -> if (dense) 6.5.dp.toPx() else 5.5.dp.toPx()
            else -> 3.0.dp.toPx()
        }
        val width = when {
            isCardinal && dense -> 2.2.dp.toPx()
            isHour -> if (dense) 1.7.dp.toPx() else 1.5.dp.toPx()
            else -> 0.9.dp.toPx()
        }
        val cosA = cos(angle).toFloat()
        val sinA = sin(angle).toFloat()
        drawLine(
            color = if (isHour) hourColor else minuteColor,
            start = Offset(center.x + cosA * (outer - len), center.y + sinA * (outer - len)),
            end = Offset(center.x + cosA * outer, center.y + sinA * outer),
            strokeWidth = width,
            cap = StrokeCap.Butt
        )
    }
}

/**
 * Baton 针：近轴略宽、向尖端收窄的实心针身（形态拟物，非线宽冒充）。
 * 局部坐标：0° 朝上，再整体 rotate。
 */
private fun DrawScope.drawBatonHand(
    center: Offset,
    angleDeg: Float,
    length: Float,
    baseHalfWidth: Float,
    tipHalfWidth: Float,
    overhang: Float,
    color: Color
) {
    rotate(degrees = angleDeg, pivot = center) {
        val path = Path().apply {
            moveTo(center.x - baseHalfWidth, center.y)
            lineTo(center.x - tipHalfWidth, center.y - length)
            lineTo(center.x + tipHalfWidth, center.y - length)
            lineTo(center.x + baseHalfWidth, center.y)
            lineTo(center.x + baseHalfWidth * 0.85f, center.y + overhang)
            lineTo(center.x - baseHalfWidth * 0.85f, center.y + overhang)
            close()
        }
        drawPath(path, color = color)
    }
}

/**
 * 秒针：极细针杆 + 梭形尾锤（真正的针型，不是圆点配重）。
 */
private fun DrawScope.drawSecondsNeedle(
    center: Offset,
    angleDeg: Float,
    length: Float,
    color: Color,
    overhang: Float
) {
    rotate(degrees = angleDeg, pivot = center) {
        val shaft = 0.7.dp.toPx()
        // 针杆：从尾到尖
        drawLine(
            color = color,
            start = Offset(center.x, center.y + overhang),
            end = Offset(center.x, center.y - length),
            strokeWidth = shaft,
            cap = StrokeCap.Round
        )
        // 梭形尾锤
        val hammerTop = overhang * 0.18f
        val hammerBottom = overhang * 0.92f
        val hammerHalf = 2.1.dp.toPx()
        val hammer = Path().apply {
            moveTo(center.x, center.y + hammerTop)
            lineTo(center.x + hammerHalf, center.y + (hammerTop + hammerBottom) / 2f)
            lineTo(center.x, center.y + hammerBottom)
            lineTo(center.x - hammerHalf, center.y + (hammerTop + hammerBottom) / 2f)
            close()
        }
        drawPath(hammer, color = color)
    }
}

private fun clockReadingOf(nowMs: Long): ClockReading {
    val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
    val h24 = cal.get(Calendar.HOUR_OF_DAY)
    val m = cal.get(Calendar.MINUTE)
    val s = cal.get(Calendar.SECOND)
    val ms = cal.get(Calendar.MILLISECOND)

    val secFrac = s + ms / 1000.0
    val minFrac = m + secFrac / 60.0
    val hourFrac = (h24 % 12) + minFrac / 60.0

    return ClockReading(
        nowMs = nowMs,
        hourAngleDeg = (hourFrac * 30.0).toFloat(),
        minuteAngleDeg = (minFrac * 6.0).toFloat(),
        secondAngleDeg = (secFrac * 6.0).toFloat(),
        hm = "%02d:%02d".format(h24, m),
        hms = "%02d:%02d:%02d".format(h24, m, s),
        minuteBucket = nowMs / 60_000L
    )
}

@Composable
private fun rememberClockReading(smoothSeconds: Boolean): ClockReading {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(smoothSeconds) {
        if (smoothSeconds) {
            while (true) {
                withFrameMillis {
                    nowMs = System.currentTimeMillis()
                }
            }
        } else {
            while (true) {
                nowMs = System.currentTimeMillis()
                delay(1_000L)
            }
        }
    }
    return remember(nowMs) { clockReadingOf(nowMs) }
}
