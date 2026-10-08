package com.life.mindfulnessapp.ui.features

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.ui.theme.LogoGreen
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val BaseHourHeight = 56.dp
private val AxisLabelWidth = 48.dp
private val RailWidth = 14.dp
private const val ScaleMin = 0.4f
private const val ScaleMax = 5f
private const val ScaleDefault = 1.4f
/** 低于此：信息密度合并；高于：逐条完整信息 */
private const val DenseBelowScale = 1.05f

private data class DayPeriod(
    val startHour: Int,
    val endHour: Int,
    val label: String,
    val tint: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayTimelineScreen(
    viewModel: DayTimelineViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val pagerState = rememberPagerState(
        initialPage = viewModel.dayCount - 1,
        pageCount = { viewModel.dayCount }
    )
    val scope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(ScaleDefault) }
    var pinching by remember { mutableStateOf(false) }
    val denseMode = scale < DenseBelowScale

    fun applyZoom(factor: Float) {
        if (factor == 1f || abs(factor - 1f) < 0.001f) return
        scale = (scale * factor).coerceIn(ScaleMin, ScaleMax)
    }

    LaunchedEffect(Unit) {
        viewModel.trackViewIfNeeded()
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { viewModel.selectPage(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "时间线",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                            color = cs.onSurface
                        )
                        Text(
                            text = if (denseMode) {
                                "信息已合并 · 放大查看逐次细节"
                            } else {
                                "完整时间流 · 双指或按钮缩放"
                            },
                            fontSize = 11.sp,
                            color = cs.onSurface.copy(alpha = 0.40f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = cs.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { applyZoom(1f / 1.18f) },
                        enabled = scale > ScaleMin + 0.01f
                    ) {
                        Icon(
                            Icons.Default.Remove,
                            contentDescription = "缩小",
                            tint = cs.onSurface.copy(alpha = 0.65f)
                        )
                    }
                    Text(
                        text = "${(scale * 100f).roundToInt()}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = cs.onSurface.copy(alpha = 0.45f)
                    )
                    IconButton(
                        onClick = { applyZoom(1.18f) },
                        enabled = scale < ScaleMax - 0.01f
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "放大",
                            tint = cs.onSurface.copy(alpha = 0.65f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        containerColor = cs.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pointerInput(Unit) {
                    // 仅双指时缩放并消费事件，避免与单击滚动/横滑冲突
                    awaitEachGesture {
                        var multiTouch = false
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.size >= 2) {
                                multiTouch = true
                                pinching = true
                                val z = event.calculateZoom()
                                if (z != 1f) {
                                    applyZoom(z)
                                }
                                event.changes.forEach { it.consume() }
                            } else if (multiTouch) {
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                        pinching = false
                    }
                }
        ) {
            DayCalendarStrip(
                dayCount = viewModel.dayCount,
                selectedPage = pagerState.currentPage,
                dayStartMs = viewModel::dayStartMs,
                onSelectPage = { page ->
                    if (!pinching) {
                        scope.launch { pagerState.animateScrollToPage(page) }
                    }
                }
            )
            DensityHint(denseMode = denseMode, scale = scale)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                userScrollEnabled = !pinching
            ) { page ->
                val sessionsFlow = remember(page) { viewModel.sessionsForPage(page) }
                val sessions by sessionsFlow.collectAsState(initial = emptyList())
                DayFlowPage(
                    dayStartMs = viewModel.dayStartMs(page),
                    sessions = sessions,
                    isToday = page == viewModel.dayCount - 1,
                    scale = scale,
                    denseMode = denseMode
                )
            }
        }
    }
}

@Composable
private fun DensityHint(denseMode: Boolean, scale: Float) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (denseMode) "密度合并" else "逐次展开",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (denseMode) cs.onSurface.copy(alpha = 0.42f) else LogoGreen
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "刻度 ${(scale * 100f).roundToInt()}%",
            fontSize = 11.sp,
            color = cs.onSurface.copy(alpha = 0.32f)
        )
    }
}

@Composable
private fun DayCalendarStrip(
    dayCount: Int,
    selectedPage: Int,
    dayStartMs: (Int) -> Long,
    onSelectPage: (Int) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val selectedStart = dayStartMs(selectedPage)
    val monthLabel = remember(selectedStart) {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedStart }
        String.format(
            Locale.CHINA,
            "%d年%d月",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1
        )
    }
    val weekPages = remember(selectedPage, dayCount) {
        val end = (selectedPage + 3).coerceAtMost(dayCount - 1)
        val start = (end - 6).coerceAtLeast(0)
        (start..end).toList()
    }

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text(
            text = monthLabel,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = cs.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            weekPages.forEach { page ->
                val start = dayStartMs(page)
                val cal = Calendar.getInstance().apply { timeInMillis = start }
                val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
                val weekday = WEEKDAYS[cal.get(Calendar.DAY_OF_WEEK) - 1]
                val selected = page == selectedPage
                val isToday = page == dayCount - 1
                Column(
                    modifier = Modifier
                        .width(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) LogoGreen.copy(alpha = 0.14f) else Color.Transparent)
                        .border(
                            width = if (selected) 1.dp else 0.dp,
                            color = if (selected) LogoGreen.copy(alpha = 0.45f) else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onSelectPage(page) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = weekday,
                        fontSize = 10.sp,
                        color = if (selected) LogoGreen else cs.onSurface.copy(alpha = 0.40f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = dayOfMonth.toString(),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            selected -> LogoGreen
                            isToday -> cs.onSurface
                            else -> cs.onSurface.copy(alpha = 0.70f)
                        }
                    )
                }
            }
        }
    }
}

private val WEEKDAYS = arrayOf("日", "一", "二", "三", "四", "五", "六")

@Composable
private fun DayFlowPage(
    dayStartMs: Long,
    sessions: List<DayAxisSession>,
    isToday: Boolean,
    scale: Float,
    denseMode: Boolean
) {
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val hourHeight = BaseHourHeight * scale
    val hourHeightPx = with(density) { hourHeight.toPx() }
    val mergeWindowMs = remember(scale) {
        (18L * 60_000L / scale.coerceAtLeast(0.35f))
            .toLong()
            .coerceIn(8L * 60_000L, 120L * 60_000L)
    }
    val layout = remember(sessions, dayStartMs, hourHeightPx, denseMode, mergeWindowMs) {
        layoutDayFlow(
            sessions = sessions,
            dayStartMs = dayStartMs,
            dayMs = DayTimelineViewModel.DAY_MS,
            hourHeightPx = hourHeightPx,
            dense = denseMode,
            mergeWindowMs = mergeWindowMs
        )
    }
    val axisHeight = with(density) { layout.axisHeightPx.toDp() }
    val scroll = rememberScrollState()

    LaunchedEffect(isToday, dayStartMs, scale) {
        if (isToday) {
            val hour = ((System.currentTimeMillis() - dayStartMs).coerceAtLeast(0L) /
                (60L * 60L * 1000L)).toInt().coerceIn(0, 23)
            scroll.scrollTo(with(density) { (hourHeight * (hour - 1).coerceAtLeast(0)).roundToPx() })
        } else {
            scroll.scrollTo(0)
        }
    }

    val periods = remember(cs) {
        listOf(
            DayPeriod(0, 6, "凌晨", cs.primary.copy(alpha = 0.04f)),
            DayPeriod(6, 12, "上午", LogoGreen.copy(alpha = 0.05f)),
            DayPeriod(12, 18, "下午", cs.tertiary.copy(alpha = 0.05f)),
            DayPeriod(18, 24, "晚上", cs.secondary.copy(alpha = 0.05f))
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(start = 8.dp, end = 14.dp, bottom = 40.dp)
    ) {
        val flowLeft = AxisLabelWidth + RailWidth
        val flowWidth = maxWidth - flowLeft

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(axisHeight)
        ) {
            FullDayAxis(
                modifier = Modifier.fillMaxSize(),
                hourHeight = hourHeight,
                labelWidth = AxisLabelWidth,
                railWidth = RailWidth,
                scale = scale,
                periods = periods,
                lineColor = cs.outline.copy(alpha = 0.22f),
                majorLineColor = cs.outline.copy(alpha = 0.38f),
                labelColor = cs.onSurface.copy(alpha = 0.42f),
                periodLabelColor = cs.onSurface.copy(alpha = 0.28f),
                railColor = LogoGreen.copy(alpha = 0.35f)
            )

            layout.items.forEach { item ->
                val y = with(density) { item.anchorYPx.toDp() }
                // 落在轴点旁：文字垂直居中于锚点
                Box(
                    modifier = Modifier
                        .offset(x = flowLeft, y = y - 10.dp)
                        .width(flowWidth)
                ) {
                    // 轴上圆点
                    Box(
                        modifier = Modifier
                            .offset(x = (-RailWidth / 2) - 3.dp, y = 8.dp)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                when (item) {
                                    is DayFlowItem.Dense -> LogoGreen.copy(alpha = 0.55f)
                                    is DayFlowItem.Detail -> LogoGreen
                                }
                            )
                    )
                    when (item) {
                        is DayFlowItem.Detail -> FlowDetailText(item.session)
                        is DayFlowItem.Dense -> FlowDenseText(item)
                    }
                }
            }

            if (isToday) {
                val nowY = with(density) {
                    val t = ((System.currentTimeMillis() - dayStartMs).toFloat() /
                        DayTimelineViewModel.DAY_MS).coerceIn(0f, 1f)
                    (layout.axisHeightPx * t).toDp()
                }
                NowLine(
                    modifier = Modifier
                        .offset(y = nowY)
                        .fillMaxWidth(),
                    labelWidth = AxisLabelWidth
                )
            }

            if (sessions.isEmpty()) {
                Text(
                    text = "这一天还没有记录",
                    fontSize = 13.sp,
                    color = cs.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(start = AxisLabelWidth)
                )
            }
        }
    }
}

@Composable
private fun FullDayAxis(
    modifier: Modifier,
    hourHeight: Dp,
    labelWidth: Dp,
    railWidth: Dp,
    scale: Float,
    periods: List<DayPeriod>,
    lineColor: Color,
    majorLineColor: Color,
    labelColor: Color,
    periodLabelColor: Color,
    railColor: Color
) {
    val density = LocalDensity.current
    val showHalf = scale >= 1.6f
    val showQuarter = scale >= 2.6f

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val labelW = with(density) { labelWidth.toPx() }
            val railW = with(density) { railWidth.toPx() }
            val hourH = with(density) { hourHeight.toPx() }
            val railX = labelW + railW / 2f

            // 日段底色
            periods.forEach { p ->
                val top = hourH * p.startHour
                val h = hourH * (p.endHour - p.startHour)
                drawRect(
                    color = p.tint,
                    topLeft = Offset(labelW, top),
                    size = Size(size.width - labelW, h)
                )
            }

            // 竖向时间轨
            drawLine(
                color = railColor,
                start = Offset(railX, 0f),
                end = Offset(railX, hourH * 24f),
                strokeWidth = 2f
            )

            for (hour in 0..24) {
                val y = hourH * hour
                val major = hour % 3 == 0
                drawLine(
                    color = if (major) majorLineColor else lineColor,
                    start = Offset(labelW, y),
                    end = Offset(size.width, y),
                    strokeWidth = if (major) 1.6f else 1f,
                    pathEffect = if (major) null else PathEffect.dashPathEffect(
                        floatArrayOf(3f, 5f),
                        0f
                    )
                )
                // 半点 / 刻
                if (hour < 24) {
                    if (showHalf) {
                        val y30 = y + hourH * 0.5f
                        drawLine(
                            color = lineColor.copy(alpha = 0.55f),
                            start = Offset(railX - 4f, y30),
                            end = Offset(railX + 10f, y30),
                            strokeWidth = 1.2f
                        )
                    }
                    if (showQuarter) {
                        listOf(0.25f, 0.75f).forEach { f ->
                            val yq = y + hourH * f
                            drawLine(
                                color = lineColor.copy(alpha = 0.4f),
                                start = Offset(railX - 2f, yq),
                                end = Offset(railX + 7f, yq),
                                strokeWidth = 1f
                            )
                        }
                    }
                }
            }
        }

        // 时刻文字
        Column {
            for (hour in 0 until 24) {
                Box(
                    modifier = Modifier
                        .height(hourHeight)
                        .width(labelWidth),
                    contentAlignment = Alignment.TopEnd
                ) {
                    Text(
                        text = "%02d:00".format(hour),
                        fontSize = if (hour % 3 == 0) 11.sp else 10.sp,
                        fontWeight = if (hour % 3 == 0) FontWeight.SemiBold else FontWeight.Normal,
                        color = labelColor,
                        modifier = Modifier.padding(end = 6.dp, top = 1.dp)
                    )
                }
            }
        }

        // 日段标签（贴在段起始）
        periods.forEach { p ->
            Text(
                text = p.label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = periodLabelColor,
                modifier = Modifier
                    .offset(
                        x = labelWidth + railWidth + 4.dp,
                        y = hourHeight * p.startHour + 4.dp
                    )
            )
        }
    }
}

@Composable
private fun FlowDetailText(session: DayAxisSession) {
    val cs = MaterialTheme.colorScheme
    val duration = if (session.isGateQuit) {
        null
    } else {
        DayTimelineViewModel.formatDuration(session.durationSeconds)
    }
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = session.appName,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            when {
                duration != null -> Text(
                    text = duration,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = LogoGreen
                )
                session.isGateQuit -> Text(
                    text = "守住",
                    fontSize = 12.sp,
                    color = cs.onSurface.copy(alpha = 0.45f)
                )
            }
        }
        if (session.purpose != null) {
            Text(
                text = "意图 · ${session.purpose}",
                fontSize = 12.sp,
                color = cs.onSurface.copy(alpha = 0.58f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!session.isGateQuit) {
            Text(
                text = session.leaveLabel,
                fontSize = 11.sp,
                color = cs.onSurface.copy(alpha = 0.38f)
            )
        }
    }
}

@Composable
private fun FlowDenseText(item: DayFlowItem.Dense) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            text = item.label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = cs.onSurface.copy(alpha = 0.82f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (item.detailLine != null) {
            Text(
                text = item.detailLine,
                fontSize = 12.sp,
                color = cs.onSurface.copy(alpha = 0.50f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun NowLine(modifier: Modifier, labelWidth: Dp) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "现在",
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = LogoGreen,
            modifier = Modifier.width(labelWidth - 4.dp)
        )
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(LogoGreen)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.5.dp)
                .background(LogoGreen.copy(alpha = 0.7f))
        )
    }
}
