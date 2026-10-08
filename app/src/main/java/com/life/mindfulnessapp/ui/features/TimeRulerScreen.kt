package com.life.mindfulnessapp.ui.features

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.domain.model.TimeRulerDaySummary
import com.life.mindfulnessapp.domain.model.TimeRulerMode
import com.life.mindfulnessapp.domain.model.TimeRulerPeriod
import com.life.mindfulnessapp.domain.model.TimeRulerWeekDay
import com.life.mindfulnessapp.domain.model.timeRulerDuration
import com.life.mindfulnessapp.ui.theme.LogoGreen
import java.util.Calendar
import java.util.Locale

private val HourH = 52.dp
private val DayAxisH = HourH * 24
private val HourLabelW = 28.dp
/** 日视图左轴轨道宽度：地图感，不抢阅读 */
private val DayMapTrackW = 44.dp
private val DayMapMinBlockH = 6.dp
/** 同小时 ≥ 此数时折叠为簇 */
private const val HourClusterThreshold = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeRulerScreen(
    viewModel: TimeRulerViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val cs = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("时间之尺", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    ModeToggleChip(
                        mode = state.mode,
                        onToggle = viewModel::toggleMode
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        containerColor = cs.background
    ) { innerPadding ->
        when {
            state.loadingPitApps -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = LogoGreen, strokeWidth = 2.dp)
            }
            state.pitApps.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(innerPadding).padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "还没有坑位里的 App\n先去能力里加入监控，再回来看时间版图",
                    fontSize = 14.sp,
                    color = cs.onSurface.copy(alpha = 0.45f),
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }
            else -> Column(Modifier.fillMaxSize().padding(innerPadding)) {
                PitChipRow(
                    apps = state.pitApps,
                    selected = state.selectedPackages,
                    onToggle = viewModel::toggleApp
                )
                RangeSummaryUnderApps(
                    mode = state.mode,
                    label = state.rangeLabel,
                    summary = state.rangeSummary,
                    loading = state.loadingSessions,
                    canGoNext = state.canGoNext,
                    onPrev = { viewModel.shiftAnchor(-1) },
                    onNext = { viewModel.shiftAnchor(1) }
                )
                Spacer(Modifier.height(6.dp))
                if (state.mode == TimeRulerMode.Week) {
                    WeekContinuousPane(
                        state = state,
                        colorOf = viewModel::colorOf,
                        onOpenDay = viewModel::openDay,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                } else {
                    DayLinkedPane(
                        state = state,
                        colorOf = viewModel::colorOf,
                        onSelectIntent = { id -> viewModel.selectBlock(id) },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeToggleChip(
    mode: TimeRulerMode,
    onToggle: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val weekOn = mode == TimeRulerMode.Week
    Row(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(cs.onSurface.copy(alpha = 0.06f))
            .clickable(onClick = onToggle)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModeSegment("周", weekOn)
        ModeSegment("日", !weekOn)
    }
}

@Composable
private fun ModeSegment(label: String, active: Boolean) {
    Text(
        text = label,
        fontSize = 12.sp,
        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
        color = if (active) Color.White.copy(alpha = 0.95f)
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (active) LogoGreen.copy(alpha = 0.92f) else Color.Transparent)
            .padding(horizontal = 11.dp, vertical = 5.dp)
    )
}

@Composable
private fun PitChipRow(
    apps: List<TimeRulerPitApp>,
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val scroll = rememberScrollState()
    Row(
        Modifier.horizontalScroll(scroll).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        apps.forEach { app ->
            val on = app.packageName in selected
            Row(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (on) app.color.copy(alpha = 0.16f) else cs.surface)
                    .border(
                        1.dp,
                        if (on) app.color.copy(alpha = 0.55f) else cs.outline.copy(alpha = 0.12f),
                        RoundedCornerShape(20.dp)
                    )
                    .clickable { onToggle(app.packageName) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmallIcon(app.icon)
                Spacer(Modifier.width(6.dp))
                Text(
                    app.appName,
                    fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    color = cs.onSurface.copy(alpha = if (on) 0.90f else 0.50f),
                    maxLines = 1
                )
            }
        }
    }
}

/** App 标签下方：日期切换 + 当前范围总时长 / 打开次数 */
@Composable
private fun RangeSummaryUnderApps(
    mode: TimeRulerMode,
    label: String,
    summary: TimeRulerDaySummary,
    loading: Boolean,
    canGoNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrev, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    null,
                    tint = cs.onSurface.copy(alpha = 0.50f)
                )
            }
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface.copy(alpha = 0.82f),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = onNext, enabled = canGoNext, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    null,
                    tint = cs.onSurface.copy(alpha = if (canGoNext) 0.50f else 0.18f)
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (loading) {
                CircularProgressIndicator(
                    color = LogoGreen,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("读取中…", fontSize = 12.sp, color = cs.onSurface.copy(alpha = 0.40f))
            } else {
                Text(
                    timeRulerDuration(summary.totalSeconds),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface.copy(alpha = 0.88f)
                )
                val countLabel = if (mode == TimeRulerMode.Day) {
                    "  ·  意图 ${summary.openCount} 次"
                } else {
                    "  ·  打开 ${summary.openCount} 次"
                }
                Text(
                    countLabel,
                    fontSize = 13.sp,
                    color = cs.onSurface.copy(alpha = 0.42f)
                )
            }
        }
    }
}

// ── 周：7 列连续日轴 ─────────────────────────────────────────────────────────

@Composable
private fun WeekContinuousPane(
    state: TimeRulerUiState,
    colorOf: (String) -> Color,
    onOpenDay: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val axisHeightPx = with(density) { DayAxisH.toPx() }
    val days = state.weekDays
    if (days.isEmpty() && state.loadingSessions) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = LogoGreen, strokeWidth = 2.dp)
        }
        return
    }

    LaunchedEffect(state.rangeStartMs, state.blocks) {
        val focus = state.blocks.minOfOrNull { it.startTime }
            ?: (state.rangeStartMs + 8L * 3_600_000L)
        val dayStart = state.rangeStartMs
        // 映射到「日内」时刻再滚
        val tod = ((focus - dayStart) % (24L * 3600_000L) + 24L * 3600_000L) % (24L * 3600_000L)
        val y = (tod.toFloat() / (24f * 3600f * 1000f) * axisHeightPx) -
            with(density) { 40.dp.toPx() }
        scroll.scrollTo(y.toInt().coerceAtLeast(0))
    }

    Column(modifier.padding(horizontal = 4.dp)) {
        // 星期头
        Row(Modifier.fillMaxWidth().padding(start = HourLabelW, bottom = 4.dp)) {
            days.forEach { day ->
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (!day.isFuture) Modifier.clickable { onOpenDay(day.dayStartMs) }
                            else Modifier
                        )
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        day.weekdayLabel.removePrefix("周"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            day.isToday -> LogoGreen
                            day.isFuture -> cs.onSurface.copy(alpha = 0.22f)
                            else -> cs.onSurface.copy(alpha = 0.70f)
                        }
                    )
                    Text(
                        day.dateLabel.substringAfter('/'),
                        fontSize = 10.sp,
                        color = cs.onSurface.copy(alpha = if (day.isFuture) 0.18f else 0.36f)
                    )
                    if (!day.isFuture && day.summary.totalSeconds > 0L) {
                        Text(
                            timeRulerDuration(day.summary.totalSeconds),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = when {
                                day.isToday -> LogoGreen.copy(alpha = 0.75f)
                                else -> cs.onSurface.copy(alpha = 0.28f)
                            },
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(cs.surface)
                .verticalScroll(scroll)
        ) {
            Box(Modifier.height(DayAxisH).fillMaxWidth()) {
                // 时段底色横贯
                TimeRulerPeriod.entries.forEach { period ->
                    Box(
                        Modifier
                            .offset(y = HourH * period.hourStart)
                            .fillMaxWidth()
                            .height(HourH * period.hourCount)
                            .background(periodTint(period).copy(alpha = 0.04f))
                    )
                }
                Row(Modifier.fillMaxSize()) {
                    HourLabelsColumn()
                    days.forEach { day ->
                        WeekDayColumn(
                            day = day,
                            rangeStartMs = state.rangeStartMs,
                            axisHeightPx = axisHeightPx,
                            colorOf = colorOf,
                            onOpenDay = onOpenDay,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HourLabelsColumn() {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.width(HourLabelW)) {
        repeat(24) { hour ->
            Box(
                Modifier.fillMaxWidth().height(HourH),
                contentAlignment = Alignment.TopEnd
            ) {
                Text(
                    String.format("%02d", hour),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = cs.onSurface.copy(alpha = if (hour % 6 == 0) 0.48f else 0.24f),
                    modifier = Modifier.padding(end = 3.dp, top = 1.dp)
                )
            }
        }
    }
}

@Composable
private fun WeekDayColumn(
    day: TimeRulerWeekDay,
    rangeStartMs: Long,
    axisHeightPx: Float,
    colorOf: (String) -> Color,
    onOpenDay: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier
            .border(0.5.dp, cs.onSurface.copy(alpha = 0.06f))
            .then(
                if (!day.isFuture) Modifier.clickable { onOpenDay(day.dayStartMs) }
                else Modifier
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val hourPx = size.height / 24f
            for (h in 0..24) {
                drawLine(
                    color = cs.onSurface.copy(alpha = if (h % 6 == 0) 0.12f else 0.05f),
                    start = Offset(0f, h * hourPx),
                    end = Offset(size.width, h * hourPx),
                    strokeWidth = 1f
                )
            }
        }
        day.blocks.forEach { block ->
            val topPx = msToY(block.startTime, day.dayStartMs, axisHeightPx)
            val botPx = msToY(block.endTime, day.dayStartMs, axisHeightPx)
            if (botPx <= 0f || topPx >= axisHeightPx) return@forEach
            val y = topPx.coerceIn(0f, axisHeightPx)
            val hPx = (botPx - topPx).coerceAtLeast(with(density) { 2.dp.toPx() })
            Box(
                Modifier
                    .offset(x = 1.dp, y = with(density) { y.toDp() })
                    .width(maxWidth - 2.dp)
                    .height(with(density) { hPx.toDp() })
                    .background(
                        colorOf(block.packageName).copy(alpha = 0.75f),
                        RectangleShape
                    )
            )
        }
    }
}

// ── 日：窄轴地图 + 意图故事列表（选中联动，无长连线）────────────────────────

@Composable
private fun DayLinkedPane(
    state: TimeRulerUiState,
    colorOf: (String) -> Color,
    onSelectIntent: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val rows = state.intentRows
    val selectedId = state.selectedBlockId
    val mapScroll = rememberScrollState()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val axisHeightPx = with(density) { DayAxisH.toPx() }
    var expandedHours by remember(state.rangeStartMs) { mutableStateOf(setOf<Int>()) }

    val storyItems = remember(rows, expandedHours) {
        buildDayStoryItems(rows, expandedHours)
    }
    val listIndexOfRecord = remember(storyItems) {
        buildMap {
            storyItems.forEachIndexed { index, item ->
                when (item) {
                    is DayStoryItem.Single -> put(item.row.recordId, index)
                    is DayStoryItem.ClusterChild -> put(item.row.recordId, index)
                    else -> Unit
                }
            }
        }
    }

    fun hourOf(ms: Long): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = ms
        return cal.get(Calendar.HOUR_OF_DAY)
    }

    fun toggleSelect(id: Long) {
        onSelectIntent(if (selectedId == id) null else id)
    }

    // 选中若在折叠簇内，先展开
    LaunchedEffect(selectedId, rows) {
        val id = selectedId ?: return@LaunchedEffect
        val row = rows.firstOrNull { it.recordId == id } ?: return@LaunchedEffect
        val hour = hourOf(row.startTime)
        val hourCount = rows.count { hourOf(it.startTime) == hour }
        if (hourCount >= HourClusterThreshold && hour !in expandedHours) {
            expandedHours = expandedHours + hour
        }
    }

    // 选中 → 列表 / 地图滚到对应位置
    LaunchedEffect(selectedId, storyItems, state.rangeStartMs) {
        val id = selectedId ?: return@LaunchedEffect
        val row = rows.firstOrNull { it.recordId == id } ?: return@LaunchedEffect
        listIndexOfRecord[id]?.let { idx ->
            listState.animateScrollToItem(idx.coerceAtLeast(0))
        }
        val y = msToY(row.startTime, state.rangeStartMs, axisHeightPx) -
            with(density) { 72.dp.toPx() }
        mapScroll.animateScrollTo(y.toInt().coerceAtLeast(0))
    }

    // 初次进入：地图滚到首条意图附近；无意图则滚到上午 8 点
    LaunchedEffect(state.rangeStartMs, rows.map { it.recordId }) {
        if (selectedId != null) return@LaunchedEffect
        val focus = rows.firstOrNull()?.startTime
            ?: (state.rangeStartMs + 8L * 3_600_000L)
        val y = msToY(focus, state.rangeStartMs, axisHeightPx) -
            with(density) { 48.dp.toPx() }
        mapScroll.scrollTo(y.toInt().coerceAtLeast(0))
    }

    if (state.loadingSessions) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                color = LogoGreen,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp)
            )
        }
        return
    }

    Row(
        modifier
            .padding(start = 6.dp, end = 8.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(cs.surface)
    ) {
        DayIntentMap(
            rows = rows,
            rangeStartMs = state.rangeStartMs,
            selectedId = selectedId,
            colorOf = colorOf,
            scrollState = mapScroll,
            onSelect = ::toggleSelect,
            modifier = Modifier
                .width(HourLabelW + DayMapTrackW + 6.dp)
                .fillMaxHeight()
        )

        // 分割：地图与故事
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .padding(vertical = 12.dp)
                .background(cs.onSurface.copy(alpha = 0.06f))
        )

        if (rows.isEmpty()) {
            Box(
                Modifier.weight(1f).fillMaxHeight().padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "这一天还没有意图进入",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = cs.onSurface.copy(alpha = 0.48f)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "左轴是时间地图，右边会留下你写下的意图",
                        fontSize = 12.sp,
                        color = cs.onSurface.copy(alpha = 0.32f),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(
                    items = storyItems,
                    key = { it.key }
                ) { item ->
                    when (item) {
                        is DayStoryItem.PeriodHead -> PeriodSectionHeader(item.period)
                        is DayStoryItem.HourCluster -> HourClusterCard(
                            hour = item.hour,
                            rows = item.rows,
                            colorOf = colorOf,
                            expanded = false,
                            onExpand = { expandedHours = expandedHours + item.hour }
                        )
                        is DayStoryItem.Single -> IntentStoryRow(
                            row = item.row,
                            selected = item.row.recordId == selectedId,
                            accent = colorOf(item.row.packageName),
                            indented = false,
                            onClick = { toggleSelect(item.row.recordId) }
                        )
                        is DayStoryItem.ClusterChild -> IntentStoryRow(
                            row = item.row,
                            selected = item.row.recordId == selectedId,
                            accent = colorOf(item.row.packageName),
                            indented = true,
                            onClick = { toggleSelect(item.row.recordId) }
                        )
                        is DayStoryItem.ClusterHead -> HourClusterCard(
                            hour = item.hour,
                            rows = item.rows,
                            colorOf = colorOf,
                            expanded = true,
                            onExpand = {
                                expandedHours = expandedHours - item.hour
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayIntentMap(
    rows: List<TimeRulerIntentRow>,
    rangeStartMs: Long,
    selectedId: Long?,
    colorOf: (String) -> Color,
    scrollState: androidx.compose.foundation.ScrollState,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val axisHeightPx = with(density) { DayAxisH.toPx() }
    val minBlockPx = with(density) { DayMapMinBlockH.toPx() }
    val nowMs = remember { System.currentTimeMillis() }
    val showNow = nowMs in rangeStartMs until (rangeStartMs + 24L * 3_600_000L)

    Box(
        modifier
            .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
            .verticalScroll(scrollState)
    ) {
        Box(Modifier.height(DayAxisH).fillMaxWidth()) {
            TimeRulerPeriod.entries.forEach { period ->
                Box(
                    Modifier
                        .offset(y = HourH * period.hourStart)
                        .fillMaxWidth()
                        .height(HourH * period.hourCount)
                        .background(periodTint(period).copy(alpha = 0.05f))
                )
            }

            Row(Modifier.fillMaxSize()) {
                HourLabelsColumn()
                Box(
                    Modifier
                        .width(DayMapTrackW)
                        .fillMaxHeight()
                        .padding(end = 4.dp)
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val hourPx = size.height / 24f
                        for (h in 0..24) {
                            drawLine(
                                color = cs.onSurface.copy(alpha = if (h % 6 == 0) 0.16f else 0.06f),
                                start = Offset(0f, h * hourPx),
                                end = Offset(size.width, h * hourPx),
                                strokeWidth = if (h % 6 == 0) 1.2f else 1f
                            )
                        }
                    }

                    // 时段字：贴在轨道内侧
                    TimeRulerPeriod.entries.forEach { period ->
                        Text(
                            period.label,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = periodTint(period).copy(alpha = 0.40f),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = 3.dp, y = HourH * period.hourStart + 3.dp)
                        )
                    }

                    rows.forEach { row ->
                        val endMs = intentEffectiveEnd(row)
                        val topPx = msToY(row.startTime, rangeStartMs, axisHeightPx)
                        val botPx = msToY(endMs, rangeStartMs, axisHeightPx)
                        val hPx = (botPx - topPx).coerceAtLeast(minBlockPx)
                        val selected = row.recordId == selectedId
                        val dimOthers = selectedId != null && !selected
                        val accent = colorOf(row.packageName)
                        val short = row.durationSeconds in 1 until 180

                        if (short && hPx <= minBlockPx + 1f) {
                            // 短意图：色点，避免「看不见」
                            Box(
                                Modifier
                                    .offset(y = with(density) { (topPx - 5.dp.toPx()).toDp() })
                                    .width(DayMapTrackW - 4.dp)
                                    .height(10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(accent.copy(alpha = if (dimOthers) 0.28f else 0.92f))
                                        .then(
                                            if (selected) {
                                                Modifier.border(
                                                    1.5.dp,
                                                    Color.White.copy(alpha = 0.7f),
                                                    CircleShape
                                                )
                                            } else Modifier
                                        )
                                        .clickable { onSelect(row.recordId) }
                                )
                            }
                        } else {
                            Box(
                                Modifier
                                    .offset(x = 3.dp, y = with(density) { topPx.toDp() })
                                    .width(DayMapTrackW - 10.dp)
                                    .height(with(density) { hPx.toDp() })
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        accent.copy(
                                            alpha = when {
                                                selected -> 0.95f
                                                dimOthers -> 0.28f
                                                else -> 0.72f
                                            }
                                        )
                                    )
                                    .then(
                                        if (selected) {
                                            Modifier.border(
                                                1.5.dp,
                                                Color.White.copy(alpha = 0.55f),
                                                RoundedCornerShape(3.dp)
                                            )
                                        } else Modifier
                                    )
                                    .clickable { onSelect(row.recordId) }
                            )
                        }
                    }

                    if (showNow) {
                        val nowY = msToY(nowMs, rangeStartMs, axisHeightPx)
                        Box(
                            Modifier
                                .offset(y = with(density) { nowY.toDp() })
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(LogoGreen.copy(alpha = 0.85f), RoundedCornerShape(1.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodSectionHeader(period: TimeRulerPeriod) {
    val tint = periodTint(period)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(tint.copy(alpha = 0.70f))
        )
        Text(
            period.label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = tint.copy(alpha = 0.78f)
        )
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(tint.copy(alpha = 0.12f))
        )
    }
}

@Composable
private fun HourClusterCard(
    hour: Int,
    rows: List<TimeRulerIntentRow>,
    colorOf: (String) -> Color,
    expanded: Boolean,
    onExpand: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val totalSec = rows.sumOf { it.durationSeconds.coerceAtLeast(0L) }
    val colors = rows.map { colorOf(it.packageName) }.distinct().take(4)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(cs.onSurface.copy(alpha = 0.04f))
            .clickable(onClick = onExpand)
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                String.format(Locale.getDefault(), "%02d时", hour),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface.copy(alpha = 0.55f)
            )
            Spacer(Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                colors.forEach { c ->
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(c.copy(alpha = 0.85f))
                            .border(1.dp, cs.surface, CircleShape)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "${rows.size} 次意图",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = cs.onSurface.copy(alpha = 0.78f),
                modifier = Modifier.weight(1f)
            )
            Text(
                timeRulerDuration(totalSec),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = cs.onSurface.copy(alpha = 0.40f)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (expanded) "收起" else "展开",
                fontSize = 11.sp,
                color = LogoGreen.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun IntentStoryRow(
    row: TimeRulerIntentRow,
    selected: Boolean,
    accent: Color,
    indented: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val title = when {
        !row.purpose.isNullOrBlank() -> "「${row.purpose}」"
        else -> row.appName
    }
    val time = clockOf(row.startTime)
    val dur = if (row.durationSeconds > 0L) timeRulerDuration(row.durationSeconds) else "—"
    val level = row.mindfulnessLevel?.let { UsageRecordEntity.MindfulnessLevel.tierLabel(it) }
    val sub = buildString {
        if (!row.purpose.isNullOrBlank()) append(row.appName)
        if (level != null) {
            if (isNotEmpty()) append(" · ")
            append(level)
        }
        when {
            row.isGateQuit -> {
                if (isNotEmpty()) append(" · ")
                append("门口离开")
            }
            row.isGateToOwn -> {
                if (isNotEmpty()) append(" · ")
                append("门口改去自己的")
            }
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = if (indented) 10.dp else 0.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    selected -> accent.copy(alpha = 0.14f)
                    else -> cs.onSurface.copy(alpha = 0.035f)
                }
            )
            .then(
                if (selected) Modifier.border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(if (sub.isEmpty()) 18.dp else 28.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent.copy(alpha = if (selected) 0.95f else 0.70f))
        )
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    time,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = cs.onSurface.copy(alpha = 0.38f)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface.copy(alpha = 0.90f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    dur,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = cs.onSurface.copy(alpha = 0.48f)
                )
            }
            if (sub.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    sub,
                    fontSize = 11.sp,
                    color = cs.onSurface.copy(alpha = 0.34f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private sealed class DayStoryItem {
    abstract val key: String

    data class PeriodHead(val period: TimeRulerPeriod) : DayStoryItem() {
        override val key: String get() = "p-${period.name}"
    }

    data class HourCluster(val hour: Int, val rows: List<TimeRulerIntentRow>) : DayStoryItem() {
        override val key: String get() = "c-$hour"
    }

    data class ClusterHead(val hour: Int, val rows: List<TimeRulerIntentRow>) : DayStoryItem() {
        override val key: String get() = "ch-$hour"
    }

    data class ClusterChild(val row: TimeRulerIntentRow) : DayStoryItem() {
        override val key: String get() = "cc-${row.recordId}"
    }

    data class Single(val row: TimeRulerIntentRow) : DayStoryItem() {
        override val key: String get() = "s-${row.recordId}"
    }
}

private fun buildDayStoryItems(
    rows: List<TimeRulerIntentRow>,
    expandedHours: Set<Int>
): List<DayStoryItem> {
    if (rows.isEmpty()) return emptyList()
    val cal = Calendar.getInstance()
    fun hourOf(ms: Long): Int {
        cal.timeInMillis = ms
        return cal.get(Calendar.HOUR_OF_DAY)
    }
    val byHour = rows.groupBy { hourOf(it.startTime) }
    val result = mutableListOf<DayStoryItem>()
    var lastPeriod: TimeRulerPeriod? = null

    // 按小时顺序遍历，保证正序
    byHour.keys.sorted().forEach { hour ->
        val hourRows = byHour.getValue(hour).sortedBy { it.startTime }
        val period = TimeRulerPeriod.ofHour(hour)
        if (period != lastPeriod) {
            result += DayStoryItem.PeriodHead(period)
            lastPeriod = period
        }
        if (hourRows.size >= HourClusterThreshold && hour !in expandedHours) {
            result += DayStoryItem.HourCluster(hour, hourRows)
        } else if (hourRows.size >= HourClusterThreshold) {
            result += DayStoryItem.ClusterHead(hour, hourRows)
            hourRows.forEach { result += DayStoryItem.ClusterChild(it) }
        } else {
            hourRows.forEach { result += DayStoryItem.Single(it) }
        }
    }
    return result
}

private fun clockOf(ms: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = ms }
    return String.format(
        Locale.getDefault(),
        "%02d:%02d",
        cal.get(Calendar.HOUR_OF_DAY),
        cal.get(Calendar.MINUTE)
    )
}

private fun intentEffectiveEnd(row: TimeRulerIntentRow): Long = when {
    row.endTime > 0L -> row.endTime
    row.durationSeconds > 0L -> row.startTime + row.durationSeconds * 1000L
    else -> row.startTime + 60_000L
}

@Composable
private fun SmallIcon(drawable: Drawable?) {
    val bmp = remember(drawable) {
        drawable?.let { runCatching { it.toBitmap(48, 48) }.getOrNull() }
    }
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(20.dp).clip(RoundedCornerShape(5.dp))
        )
    } else {
        Box(
            Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    }
}

private fun periodTint(period: TimeRulerPeriod): Color = when (period) {
    TimeRulerPeriod.Night -> Color(0xFF5B6B8C)
    TimeRulerPeriod.Morning -> Color(0xFFD4A017)
    TimeRulerPeriod.Afternoon -> Color(0xFF2E9B6A)
    TimeRulerPeriod.Evening -> Color(0xFF7B6BB0)
}

private fun msToY(timeMs: Long, dayStartMs: Long, axisHeightPx: Float): Float {
    val dayMs = 24L * 60 * 60 * 1000
    val offset = (timeMs - dayStartMs).toFloat().coerceIn(0f, dayMs.toFloat())
    return offset / dayMs.toFloat() * axisHeightPx
}
