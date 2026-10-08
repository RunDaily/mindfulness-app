package com.life.mindfulnessapp.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.domain.model.buildHeldAwayOrdinalIndex
import com.life.mindfulnessapp.domain.model.buildTimelineSections
import com.life.mindfulnessapp.domain.model.collapseTimelineForDisplay
import com.life.mindfulnessapp.domain.model.groupTimelineSections
import com.life.mindfulnessapp.domain.model.TimelineSectionItem
import com.life.mindfulnessapp.ui.theme.LogoGreen
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 全部记录：月历选天 + 当日流水。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordHistoryScreen(
    viewModel: RecordHistoryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToDayReport: () -> Unit = {}
) {
    val cs = MaterialTheme.colorScheme
    val selectedDay by viewModel.selectedDayStartMs.collectAsState()
    val visibleMonth by viewModel.visibleMonthStartMs.collectAsState()
    val timeline by viewModel.timeline.collectAsState()
    val daysWithRecords by viewModel.daysWithRecords.collectAsState()
    val monitoredApps by viewModel.monitoredApps.collectAsState()
    val isToday by viewModel.isToday.collectAsState()

    val iconMap = remember(monitoredApps) { monitoredApps.associateBy { it.packageName } }
    val displayItems = remember(timeline) { collapseTimelineForDisplay(timeline) }
    val sectionItems = remember(displayItems) { buildTimelineSections(displayItems) }
    val periodGroups = remember(sectionItems) { groupTimelineSections(sectionItems) }
    val heldAwayIndex = remember(timeline) { buildHeldAwayOrdinalIndex(timeline) }

    val dayTitle = remember(selectedDay, isToday) {
        val date = SimpleDateFormat("M月d日 EEEE", Locale.CHINESE).format(Date(selectedDay))
        if (isToday) "今天 · $date" else date
    }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "全部记录",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        color = cs.onSurface
                    )
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
                    Text(
                        text = "日报",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = LogoGreen.copy(alpha = 0.88f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onNavigateToDayReport)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cs.background,
                    titleContentColor = cs.onSurface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item(key = "calendar") {
                HistoryMonthCalendar(
                    monthStartMs = visibleMonth,
                    selectedDayStartMs = selectedDay,
                    daysWithRecords = daysWithRecords,
                    earliestDayStartMs = viewModel.earliestDayStartMs,
                    latestDayStartMs = viewModel.latestDayStartMs,
                    canPrevMonth = viewModel.canShiftMonth(-1),
                    canNextMonth = viewModel.canShiftMonth(1),
                    onPrevMonth = { viewModel.shiftMonth(-1) },
                    onNextMonth = { viewModel.shiftMonth(1) },
                    onSelectDay = viewModel::selectDay,
                    onSurface = cs.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            item(key = "day_label") {
                Text(
                    text = dayTitle,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface.copy(alpha = 0.48f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                )
            }

            if (periodGroups.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = if (isToday) "今天还没有记录" else "这天没有记录",
                        fontSize = 13.sp,
                        color = cs.onSurface.copy(alpha = 0.32f),
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp)
                    )
                }
            } else {
                periodGroups.forEach { group ->
                    item(key = "period_${group.name}") {
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            TimelinePeriodHeader(
                                label = group.name,
                                range = group.range,
                                stats = group.stats,
                                iconMap = iconMap,
                                onSurface = cs.onSurface
                            )
                        }
                    }
                    items(group.rows, key = { it.key }) { section ->
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            when (section) {
                                is TimelineSectionItem.PeriodHeader -> Unit
                                is TimelineSectionItem.Gap -> TimelineGapRow(
                                    seconds = section.seconds,
                                    onSurface = cs.onSurface
                                )
                                is TimelineSectionItem.Event -> TimelineDisplayNode(
                                    item = section.item,
                                    isLast = false,
                                    iconMap = iconMap,
                                    onRecordEdit = { _, _ -> },
                                    editable = false,
                                    showAppIdentity = true,
                                    showEndReason = true,
                                    heldAwayIndex = heldAwayIndex,
                                    cardBg = cs.surface,
                                    onSurface = cs.onSurface,
                                    outline = cs.onSurface.copy(alpha = 0.08f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryMonthCalendar(
    monthStartMs: Long,
    selectedDayStartMs: Long,
    daysWithRecords: Set<Long>,
    earliestDayStartMs: Long,
    latestDayStartMs: Long,
    canPrevMonth: Boolean,
    canNextMonth: Boolean,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDay: (Long) -> Unit,
    onSurface: Color,
    modifier: Modifier = Modifier
) {
    val monthLabel = remember(monthStartMs) {
        SimpleDateFormat("yyyy年M月", Locale.CHINESE).format(Date(monthStartMs))
    }
    val cells = remember(monthStartMs) { buildMonthCells(monthStartMs) }
    val weekdays = listOf("一", "二", "三", "四", "五", "六", "日")

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onPrevMonth,
                enabled = canPrevMonth
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "上个月",
                    tint = onSurface.copy(alpha = if (canPrevMonth) 0.55f else 0.18f)
                )
            }
            Text(
                text = monthLabel,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = onSurface.copy(alpha = 0.82f)
            )
            IconButton(
                onClick = onNextMonth,
                enabled = canNextMonth
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "下个月",
                    tint = onSurface.copy(alpha = if (canNextMonth) 0.55f else 0.18f)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            weekdays.forEach { w ->
                Text(
                    text = w,
                    fontSize = 11.sp,
                    color = onSurface.copy(alpha = 0.32f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        cells.chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                week.forEach { cell ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (cell != null) {
                            val enabled = cell in earliestDayStartMs..latestDayStartMs
                            val selected = cell == selectedDayStartMs
                            val hasRecord = cell in daysWithRecords
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        when {
                                            selected -> LogoGreen.copy(alpha = 0.16f)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .then(
                                        if (enabled) Modifier.clickable { onSelectDay(cell) }
                                        else Modifier
                                    )
                            ) {
                                Text(
                                    text = dayOfMonth(cell).toString(),
                                    fontSize = 14.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = when {
                                        !enabled -> onSurface.copy(alpha = 0.18f)
                                        selected -> LogoGreen.copy(alpha = 0.95f)
                                        else -> onSurface.copy(alpha = 0.78f)
                                    }
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                hasRecord && selected -> LogoGreen.copy(alpha = 0.85f)
                                                hasRecord -> onSurface.copy(alpha = 0.28f)
                                                else -> Color.Transparent
                                            }
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 月初对齐到周一的格子；空位为 null */
private fun buildMonthCells(monthStartMs: Long): List<Long?> {
    val cal = Calendar.getInstance().apply { timeInMillis = monthStartMs }
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    // Calendar: SUNDAY=1 … SATURDAY=7 → 转成周一=0
    val firstDow = ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7)
    val cells = ArrayList<Long?>(42)
    repeat(firstDow) { cells += null }
    repeat(daysInMonth) { i ->
        cells += run {
            val dayCal = Calendar.getInstance().apply {
                timeInMillis = monthStartMs
                add(Calendar.DAY_OF_MONTH, i)
            }
            RecordHistoryViewModel.startOfDay(dayCal.timeInMillis)
        }
    }
    while (cells.size % 7 != 0) cells += null
    return cells
}

private fun dayOfMonth(dayStartMs: Long): Int {
    val cal = Calendar.getInstance().apply { timeInMillis = dayStartMs }
    return cal.get(Calendar.DAY_OF_MONTH)
}
