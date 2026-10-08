package com.life.mindfulnessapp.ui.plan

import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.domain.model.AppDiaryDaySource
import com.life.mindfulnessapp.domain.model.AppDiaryTrendDay
import com.life.mindfulnessapp.ui.theme.LogoGreen

/** 详情预览与走势细看共用柱图高度，形状对照一致。 */
object TrendChartHeights {
    val Duration = 140.dp
    val Opens = 88.dp
}

/**
 * 详情页变化图窗口：贴宽约 [maxSlots] 柱。
 * 不含今天（未完结日不进图）；监控前柱与走势同口径（最多 7 日全留）；
 * 余下槽位给加入后完整日。加入分界可见。详情不横滑；全长见细看。
 */
fun trendDetailPeek(
    days: List<AppDiaryTrendDay>,
    maxSlots: Int = 12
): List<AppDiaryTrendDay> {
    val complete = days.filterNot { it.isToday }
    if (complete.size <= maxSlots) return complete
    val before = complete.filter { it.source == AppDiaryDaySource.SystemBefore }
    val after = complete.filter { it.source == AppDiaryDaySource.AnchorAfter }
    if (before.isEmpty()) return complete.takeLast(maxSlots)
    // 监控前与走势对齐：能留尽留（通常 7），至少留 1 槽给加入后
    val beforeKeep = before.size.coerceAtMost((maxSlots - 1).coerceAtLeast(1))
    val afterKeep = (maxSlots - beforeKeep).coerceAtLeast(1)
    return before.takeLast(beforeKeep) + after.takeLast(afterKeep)
}

/** 折线只连完整日，不连到今天。 */
private fun DrawScope.drawTrendPolyline(
    days: List<AppDiaryTrendDay>,
    color: Color,
    strokePx: Float,
    xOf: (Int) -> Float,
    yOf: (AppDiaryTrendDay) -> Float
) {
    val path = Path()
    var started = false
    days.forEachIndexed { i, d ->
        if (d.isToday) return@forEachIndexed
        val x = xOf(i)
        val y = yOf(d)
        if (!started) {
            path.moveTo(x, y)
            started = true
        } else {
            path.lineTo(x, y)
        }
    }
    if (started) {
        drawPath(path, color, style = Stroke(strokePx, cap = StrokeCap.Round))
    }
}

/**
 * 详情页矮预览：柱 + 折线，只读形状，**不标时长**。
 */
@Composable
fun TrendPeekChart(
    days: List<AppDiaryTrendDay>,
    baselineAvgMinutes: Int?,
    limitMinutes: Int?,
    onOpenFull: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = TrendChartHeights.Duration,
    afterAvgMinutes: Int? = null
) {
    val colors = MaterialTheme.colorScheme
    val peekDays = trendDetailPeek(days)
    val maxY = listOfNotNull(
        peekDays.maxOfOrNull { it.minutes },
        baselineAvgMinutes,
        afterAvgMinutes,
        limitMinutes
    ).maxOrNull()?.coerceAtLeast(1) ?: 1
    val barAfter = LogoGreen
    val barBefore = colors.onBackground.copy(alpha = 0.28f)
    val lineColor = colors.onBackground.copy(alpha = 0.42f)
    val baselineColor = colors.tertiary.copy(alpha = 0.35f)
    val afterAvgColor = colors.onBackground.copy(alpha = 0.28f)
    val limitColor = LogoGreen.copy(alpha = 0.35f)
    val joinColor = colors.tertiary.copy(alpha = 0.45f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clickable(onClick = onOpenFull)
    ) {
        val w = size.width
        val h = size.height
        val n = peekDays.size
        if (n == 0) return@Canvas
        val slot = w / n
        val barW = (slot * 0.42f).coerceAtMost(10.dp.toPx())
        fun yOf(minutes: Int): Float {
            val frac = (minutes.toFloat() / maxY).coerceIn(0f, 1f)
            return h * (1f - frac)
        }
        baselineAvgMinutes?.takeIf { it > 0 }?.let { avg ->
            drawLine(baselineColor, Offset(0f, yOf(avg)), Offset(w, yOf(avg)), 1.dp.toPx())
        }
        afterAvgMinutes?.takeIf { it > 0 }?.let { avg ->
            drawLine(
                afterAvgColor,
                Offset(0f, yOf(avg)),
                Offset(w, yOf(avg)),
                1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
            )
        }
        limitMinutes?.takeIf { it > 0 }?.let { lim ->
            drawLine(
                limitColor,
                Offset(0f, yOf(lim)),
                Offset(w, yOf(lim)),
                1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))
            )
        }
        peekDays.forEachIndexed { i, d ->
            val segmentStart = d.isJoinDay ||
                (i > 0 &&
                    d.source == AppDiaryDaySource.AnchorAfter &&
                    peekDays[i - 1].source == AppDiaryDaySource.SystemBefore)
            if (segmentStart) {
                drawLine(
                    joinColor,
                    Offset(slot * i, 0f),
                    Offset(slot * i, h),
                    1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 4f))
                )
            }
            val cx = slot * i + slot / 2f
            val top = yOf(d.minutes)
            val barH = (h - top).coerceAtLeast(2.dp.toPx())
            val fill = if (d.source == AppDiaryDaySource.AnchorAfter) {
                barAfter.copy(alpha = 0.85f)
            } else {
                barBefore
            }
            drawRect(fill, Offset(cx - barW / 2f, top), Size(barW, barH))
        }
        if (peekDays.size >= 2) {
            drawTrendPolyline(
                days = peekDays,
                color = lineColor,
                strokePx = 1.3.dp.toPx(),
                xOf = { i -> slot * i + slot / 2f },
                yOf = { d -> yOf(d.minutes) }
            )
        }
    }
}

/**
 * 细看图：柱 + 折线 + 对比线 + 点选。
 * [showValueLabels] 柱顶标分钟；[valueLabelsSelectedOnly] 只标选中日。
 * [drawBaselineLine] 图内画加入前均；竖屏细看可关，改由图例承担。
 * [drawAfterAvgLine] 图内画加入后日均（不含今天）。
 * [fitToWidth] 横屏一屏铺开不横滑。
 * [showAxisValues] 横屏左侧贴基线/日限数值。
 * [scrollState] 与打开图共用横滑，保证同轴。
 */
@Composable
fun TrendFullChart(
    days: List<AppDiaryTrendDay>,
    baselineAvgMinutes: Int?,
    limitMinutes: Int?,
    selectedStart: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
    slotWidth: Dp = 26.dp,
    chartHeight: Dp = TrendChartHeights.Duration,
    showPolyline: Boolean = true,
    showValueLabels: Boolean = true,
    valueLabelsSelectedOnly: Boolean = false,
    drawBaselineLine: Boolean = true,
    drawAfterAvgLine: Boolean = true,
    afterAvgMinutes: Int? = null,
    fitToWidth: Boolean = false,
    showAxisValues: Boolean = false,
    labelSp: Float = 9f,
    scrollState: ScrollState? = null
) {
    val colors = MaterialTheme.colorScheme
    val palette = TrendPalette(
        barAfter = LogoGreen,
        barBefore = colors.onBackground.copy(alpha = 0.28f),
        lineColor = colors.onBackground.copy(alpha = 0.45f),
        baselineColor = colors.tertiary,
        afterAvgColor = colors.onBackground.copy(alpha = 0.55f),
        limitColor = LogoGreen.copy(alpha = 0.75f),
        joinColor = colors.tertiary.copy(alpha = 0.7f),
        labelMuted = colors.onSurfaceVariant,
        labelOn = colors.onBackground,
        labelSelected = LogoGreen
    )
    val maxY = listOfNotNull(
        days.maxOfOrNull { it.minutes },
        baselineAvgMinutes.takeIf { drawBaselineLine },
        afterAvgMinutes.takeIf { drawAfterAvgLine },
        limitMinutes
    ).maxOrNull()?.coerceAtLeast(1) ?: 1
    val density = LocalDensity.current
    val needLabelPad = showValueLabels || days.any { it.isJoinDay } ||
        days.zipWithNext().any { (a, b) ->
            a.source == AppDiaryDaySource.SystemBefore && b.source == AppDiaryDaySource.AnchorAfter
        }
    val labelPad = if (needLabelPad) with(density) { 14.dp.toPx() } else 0f
    val axisPad = if (showAxisValues) with(density) { 28.dp.toPx() } else 0f

    if (fitToWidth) {
        Column(modifier = modifier.fillMaxWidth()) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight)
                    .pointerInput(days, axisPad) {
                        detectTapGestures { offset ->
                            selectDayAt(days, offset.x, size.width.toFloat(), axisPad, onSelect)
                        }
                    }
            ) {
                drawTrendPlot(
                    days = days,
                    selectedStart = selectedStart,
                    maxY = maxY,
                    showPolyline = showPolyline,
                    showValueLabels = showValueLabels,
                    valueLabelsSelectedOnly = valueLabelsSelectedOnly,
                    drawBaselineLine = drawBaselineLine,
                    drawAfterAvgLine = drawAfterAvgLine,
                    showAxisValues = showAxisValues,
                    axisPadPx = axisPad,
                    labelPadPx = labelPad,
                    labelSp = labelSp,
                    palette = palette,
                    baselineAvgMinutes = baselineAvgMinutes,
                    afterAvgMinutes = afterAvgMinutes,
                    limitMinutes = limitMinutes
                )
            }
            WeekdayRow(
                days = days,
                selectedStart = selectedStart,
                onSelect = onSelect,
                startPad = with(density) { axisPad.toDp() },
                equalWeight = true
            )
        }
    } else {
        val scroll = scrollState ?: rememberScrollState()
        Column(modifier = modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scroll)
            ) {
                val chartW = slotWidth * days.size.coerceAtLeast(1)
                Canvas(
                    modifier = Modifier
                        .width(chartW)
                        .height(chartHeight)
                        .pointerInput(days) {
                            detectTapGestures { offset ->
                                selectDayAt(days, offset.x, size.width.toFloat(), 0f, onSelect)
                            }
                        }
                ) {
                    drawTrendPlot(
                        days = days,
                        selectedStart = selectedStart,
                        maxY = maxY,
                        showPolyline = showPolyline,
                        showValueLabels = showValueLabels,
                        valueLabelsSelectedOnly = valueLabelsSelectedOnly,
                        drawBaselineLine = drawBaselineLine,
                        drawAfterAvgLine = drawAfterAvgLine,
                        showAxisValues = false,
                        axisPadPx = 0f,
                        labelPadPx = labelPad,
                        labelSp = labelSp,
                        palette = palette,
                        baselineAvgMinutes = baselineAvgMinutes,
                        afterAvgMinutes = afterAvgMinutes,
                        limitMinutes = limitMinutes
                    )
                }
            }
            Row(
                modifier = Modifier
                    .horizontalScroll(scroll)
                    .padding(top = 6.dp)
            ) {
                days.forEach { d ->
                    val on = d.dayStartMs == selectedStart
                    Text(
                        d.weekday,
                        color = if (on) colors.onBackground else colors.onSurfaceVariant,
                        fontSize = 9.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .width(slotWidth)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelect(d.dayStartMs) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekdayRow(
    days: List<AppDiaryTrendDay>,
    selectedStart: Long?,
    onSelect: (Long) -> Unit,
    startPad: Dp,
    equalWeight: Boolean
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = startPad, top = 4.dp)
    ) {
        days.forEach { d ->
            val on = d.dayStartMs == selectedStart
            Text(
                d.weekday,
                color = if (on) colors.onBackground else colors.onSurfaceVariant,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .then(if (equalWeight) Modifier.weight(1f) else Modifier)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(d.dayStartMs) }
            )
        }
    }
}

private fun selectDayAt(
    days: List<AppDiaryTrendDay>,
    x: Float,
    width: Float,
    axisPad: Float,
    onSelect: (Long) -> Unit
) {
    if (days.isEmpty()) return
    val plotW = width - axisPad
    if (plotW <= 0f || x < axisPad) return
    val idx = ((x - axisPad) / (plotW / days.size)).toInt().coerceIn(0, days.lastIndex)
    onSelect(days[idx].dayStartMs)
}

private data class TrendPalette(
    val barAfter: Color,
    val barBefore: Color,
    val lineColor: Color,
    val baselineColor: Color,
    val afterAvgColor: Color,
    val limitColor: Color,
    val joinColor: Color,
    val labelMuted: Color,
    val labelOn: Color,
    val labelSelected: Color
)

private fun DrawScope.drawTrendPlot(
    days: List<AppDiaryTrendDay>,
    selectedStart: Long?,
    maxY: Int,
    showPolyline: Boolean,
    showValueLabels: Boolean,
    valueLabelsSelectedOnly: Boolean,
    drawBaselineLine: Boolean,
    drawAfterAvgLine: Boolean,
    showAxisValues: Boolean,
    axisPadPx: Float,
    labelPadPx: Float,
    labelSp: Float,
    palette: TrendPalette,
    baselineAvgMinutes: Int?,
    afterAvgMinutes: Int?,
    limitMinutes: Int?
) {
    val n = days.size
    if (n == 0) return
    val plotLeft = axisPadPx
    val plotW = size.width - plotLeft
    val plotTop = labelPadPx
    val plotH = (size.height - plotTop).coerceAtLeast(1f)
    val slotPx = plotW / n
    val barW = (slotPx * 0.42f).coerceIn(
        6.dp.toPx(),
        if (slotPx > 30.dp.toPx()) 18.dp.toPx() else 14.dp.toPx()
    )

    fun yOf(minutes: Int): Float {
        val frac = (minutes.toFloat() / maxY).coerceIn(0f, 1f)
        return plotTop + plotH * (1f - frac)
    }

    if (drawBaselineLine) {
        baselineAvgMinutes?.takeIf { it > 0 }?.let { avg ->
            val y = yOf(avg)
            drawLine(palette.baselineColor, Offset(plotLeft, y), Offset(size.width, y), 1.5.dp.toPx())
            if (showAxisValues) {
                drawMinuteText("$avg", 0f, y + labelSp * 0.35f, palette.baselineColor, labelSp - 0.5f, center = false)
            }
        }
    }
    if (drawAfterAvgLine) {
        afterAvgMinutes?.takeIf { it > 0 }?.let { avg ->
            val y = yOf(avg)
            drawLine(
                palette.afterAvgColor,
                Offset(plotLeft, y),
                Offset(size.width, y),
                1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))
            )
            if (showAxisValues) {
                drawMinuteText("$avg", 0f, y + labelSp * 0.35f, palette.afterAvgColor, labelSp - 0.5f, center = false)
            }
        }
    }
    limitMinutes?.takeIf { it > 0 }?.let { lim ->
        val y = yOf(lim)
        drawLine(
            palette.limitColor,
            Offset(plotLeft, y),
            Offset(size.width, y),
            1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
        )
        if (showAxisValues) {
            drawMinuteText("$lim", 0f, y + labelSp * 0.35f, palette.limitColor, labelSp - 0.5f, center = false)
        }
    }

    days.forEachIndexed { i, d ->
        val segmentStart = d.isJoinDay ||
            (i > 0 &&
                d.source == AppDiaryDaySource.AnchorAfter &&
                days[i - 1].source == AppDiaryDaySource.SystemBefore)
        if (segmentStart) {
            drawLine(
                palette.joinColor,
                Offset(plotLeft + slotPx * i, plotTop * 0.25f),
                Offset(plotLeft + slotPx * i, size.height),
                1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))
            )
        }
    }

    days.forEachIndexed { i, d ->
        val cx = plotLeft + slotPx * i + slotPx / 2f
        val top = yOf(d.minutes)
        val barH = (size.height - top).coerceAtLeast(2.dp.toPx())
        val selected = d.dayStartMs == selectedStart
        val fill = when {
            selected -> palette.barAfter
            d.isToday -> palette.barAfter.copy(alpha = 0.4f)
            d.source == AppDiaryDaySource.AnchorAfter -> palette.barAfter.copy(alpha = 0.82f)
            else -> palette.barBefore
        }
        drawRect(fill, Offset(cx - barW / 2f, top), Size(barW, barH))
        if (selected) {
            drawRect(
                Color.White.copy(alpha = 0.9f),
                Offset(cx - barW / 2f, top),
                Size(barW, barH),
                style = Stroke(1.2.dp.toPx())
            )
        }
        val labelThis = showValueLabels && (!valueLabelsSelectedOnly || selected)
        if (labelThis) {
            val color = when {
                selected -> palette.labelSelected
                d.source == AppDiaryDaySource.AnchorAfter -> palette.labelOn
                else -> palette.labelMuted
            }
            val sizeBoost = if (selected) 1f else 0f
            drawMinuteText(
                "${d.minutes}",
                cx,
                (top - 3.dp.toPx()).coerceAtLeast(labelSp + 2.dp.toPx()),
                color,
                labelSp + sizeBoost,
                center = true
            )
        }
    }

    if (showPolyline && days.size >= 2) {
        drawTrendPolyline(
            days = days,
            color = palette.lineColor,
            strokePx = 1.6.dp.toPx(),
            xOf = { i -> plotLeft + slotPx * i + slotPx / 2f },
            yOf = { d -> yOf(d.minutes) }
        )
    }

    // 分界字最后画、贴柱脚，避开柱顶数字且不被柱盖住
    days.forEachIndexed { i, d ->
        val showJoinLabel = d.isJoinDay ||
            (i > 0 &&
                d.source == AppDiaryDaySource.AnchorAfter &&
                days[i - 1].source == AppDiaryDaySource.SystemBefore &&
                days.none { it.isJoinDay })
        if (!showJoinLabel) return@forEachIndexed
        drawMinuteText(
            "加入",
            plotLeft + slotPx * i + 4.dp.toPx(),
            (size.height - 3.dp.toPx()).coerceAtLeast(plotTop + labelSp),
            palette.joinColor,
            labelSp - 1f,
            center = false
        )
    }
}

private fun DrawScope.drawMinuteText(
    text: String,
    x: Float,
    y: Float,
    color: Color,
    sp: Float,
    center: Boolean
) {
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        textSize = sp * density
        textAlign = if (center) AndroidPaint.Align.CENTER else AndroidPaint.Align.LEFT
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
}

@Composable
fun TrendLegendRow(
    baselineAvgMinutes: Int?,
    limitMinutes: Int?,
    compact: Boolean = false,
    afterAvgMinutes: Int? = null
) {
    val colors = MaterialTheme.colorScheme
    val gap = if (compact) 10.dp else 12.dp
    Row(
        modifier = Modifier.padding(top = if (compact) 0.dp else 8.dp),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (baselineAvgMinutes != null && baselineAvgMinutes > 0) {
            LegendItem(
                label = if (compact) "前均 $baselineAvgMinutes" else "监控前均 ${baselineAvgMinutes} 分",
                color = colors.tertiary,
                dashed = false
            )
        }
        if (afterAvgMinutes != null && afterAvgMinutes > 0) {
            LegendItem(
                label = if (compact) "后均 $afterAvgMinutes" else "加入后均 ${afterAvgMinutes} 分",
                color = colors.onSurfaceVariant,
                dashed = true
            )
        }
        if (limitMinutes != null && limitMinutes > 0) {
            LegendItem(
                label = if (compact) "日限 $limitMinutes" else "日限 ${limitMinutes} 分",
                color = LogoGreen.copy(alpha = 0.85f),
                dashed = true
            )
        }
    }
}

@Composable
private fun LegendItem(
    label: String,
    color: Color,
    dashed: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Canvas(modifier = Modifier.width(14.dp).height(2.dp)) {
            val y = size.height / 2f
            drawLine(
                color = color,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 2.dp.toPx(),
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4f, 3f)) else null
            )
        }
        Text(label, color = color, fontSize = 10.sp)
    }
}

/**
 * 走势细看 · 打开次数柱，与 [TrendFullChart] 共日轴、共选中、可选共横滑。
 */
@Composable
fun TrendOpensChart(
    days: List<AppDiaryTrendDay>,
    selectedStart: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
    slotWidth: Dp = 26.dp,
    chartHeight: Dp = TrendChartHeights.Opens,
    fitToWidth: Boolean = false,
    showValueLabels: Boolean = true,
    valueLabelsSelectedOnly: Boolean = false,
    scrollState: ScrollState? = null
) {
    val colors = MaterialTheme.colorScheme
    val maxY = days.maxOfOrNull { it.opens }?.coerceAtLeast(1) ?: 1
    val barBefore = colors.onBackground.copy(alpha = 0.28f)
    val barAfter = LogoGreen.copy(alpha = 0.85f)
    val selectedFill = LogoGreen
    val joinColor = colors.tertiary.copy(alpha = 0.55f)
    val labelMuted = colors.onSurfaceVariant
    val labelOn = colors.onBackground
    val labelSelected = LogoGreen
    val density = LocalDensity.current
    val needLabelPad = showValueLabels
    val labelPad = if (needLabelPad) with(density) { 12.dp.toPx() } else 0f

    fun DrawScope.drawOpens(plotW: Float, axisPad: Float = 0f) {
        val n = days.size
        if (n == 0) return
        val plotTop = labelPad
        val plotH = (size.height - plotTop).coerceAtLeast(1f)
        val slotPx = plotW / n
        val barW = (slotPx * 0.42f).coerceIn(5.dp.toPx(), 14.dp.toPx())
        fun yOf(opens: Int): Float {
            val frac = (opens.toFloat() / maxY).coerceIn(0f, 1f)
            return plotTop + plotH * (1f - frac)
        }
        days.forEachIndexed { i, d ->
            val segmentStart = d.isJoinDay ||
                (i > 0 &&
                    d.source == AppDiaryDaySource.AnchorAfter &&
                    days[i - 1].source == AppDiaryDaySource.SystemBefore)
            if (segmentStart) {
                drawLine(
                    joinColor,
                    Offset(axisPad + slotPx * i, plotTop * 0.2f),
                    Offset(axisPad + slotPx * i, size.height),
                    1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                )
            }
            val cx = axisPad + slotPx * i + slotPx / 2f
            val top = yOf(d.opens)
            val barH = (size.height - top).coerceAtLeast(2.dp.toPx())
            val selected = d.dayStartMs == selectedStart
            val fill = when {
                selected -> selectedFill
                d.source == AppDiaryDaySource.AnchorAfter -> barAfter
                else -> barBefore
            }
            drawRect(fill, Offset(cx - barW / 2f, top), Size(barW, barH))
            if (selected) {
                drawRect(
                    Color.White.copy(alpha = 0.85f),
                    Offset(cx - barW / 2f, top),
                    Size(barW, barH),
                    style = Stroke(1.dp.toPx())
                )
            }
            val labelThis = showValueLabels && (
                if (valueLabelsSelectedOnly) selected
                else selected || d.opens > 0
            )
            if (labelThis) {
                val color = when {
                    selected -> labelSelected
                    d.source == AppDiaryDaySource.AnchorAfter -> labelOn
                    else -> labelMuted
                }
                drawMinuteText(
                    "${d.opens}",
                    cx,
                    (top - 2.dp.toPx()).coerceAtLeast(labelSpSafe(labelPad)),
                    color,
                    8f,
                    center = true
                )
            }
        }
    }

    if (fitToWidth) {
        Canvas(
            modifier = modifier
                .fillMaxWidth()
                .height(chartHeight)
                .pointerInput(days) {
                    detectTapGestures { offset ->
                        selectDayAt(days, offset.x, size.width.toFloat(), 0f, onSelect)
                    }
                }
        ) {
            drawOpens(size.width)
        }
    } else {
        val scroll = scrollState ?: rememberScrollState()
        Box(
            modifier = modifier
                .fillMaxWidth()
                .horizontalScroll(scroll)
        ) {
            val chartW = slotWidth * days.size.coerceAtLeast(1)
            Canvas(
                modifier = Modifier
                    .width(chartW)
                    .height(chartHeight)
                    .pointerInput(days) {
                        detectTapGestures { offset ->
                            selectDayAt(days, offset.x, size.width.toFloat(), 0f, onSelect)
                        }
                    }
            ) {
                drawOpens(size.width)
            }
        }
    }
}

private fun labelSpSafe(labelPad: Float): Float = (labelPad * 0.7f).coerceAtLeast(9f)

/** 横屏专用：图吃满高度，一屏铺开、柱疏、标量 + 左侧轴值。 */
@Composable
fun TrendLandscapeChart(
    days: List<AppDiaryTrendDay>,
    baselineAvgMinutes: Int?,
    limitMinutes: Int?,
    selectedStart: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
    afterAvgMinutes: Int? = null
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        TrendFullChart(
            days = days,
            baselineAvgMinutes = baselineAvgMinutes,
            limitMinutes = limitMinutes,
            afterAvgMinutes = afterAvgMinutes,
            selectedStart = selectedStart,
            onSelect = onSelect,
            modifier = Modifier.fillMaxWidth(),
            chartHeight = maxHeight - 22.dp,
            showValueLabels = true,
            valueLabelsSelectedOnly = false,
            fitToWidth = true,
            showAxisValues = true,
            labelSp = 10f
        )
    }
}
