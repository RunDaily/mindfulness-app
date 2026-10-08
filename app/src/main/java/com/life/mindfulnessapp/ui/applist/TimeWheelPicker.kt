package com.life.mindfulnessapp.ui.applist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.domain.model.PeriodWindow
import com.life.mindfulnessapp.ui.theme.LogoGreen
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

private val WheelItemHeight = 40.dp
private const val WheelVisibleCount = 5

/**
 * 时段起止：全天开关 + 开始/结束双滑轮。可嵌在编辑层里，不必再套一层对话框。
 * 起止相等会规范为全天（0/0）。
 */
@Composable
fun PeriodRangePickerContent(
    initialStartMinute: Int,
    initialEndMinute: Int,
    onRangeChange: (startMinute: Int, endMinute: Int) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val initialAllDay = initialStartMinute == initialEndMinute
    var allDay by remember(initialStartMinute, initialEndMinute) {
        mutableStateOf(initialAllDay)
    }
    var startHour by remember(initialStartMinute, initialEndMinute) {
        mutableIntStateOf(
            if (initialAllDay) 22 else (initialStartMinute.coerceIn(0, 1439) / 60)
        )
    }
    var startMinute by remember(initialStartMinute, initialEndMinute) {
        mutableIntStateOf(
            if (initialAllDay) 0 else (initialStartMinute.coerceIn(0, 1439) % 60)
        )
    }
    var endHour by remember(initialStartMinute, initialEndMinute) {
        mutableIntStateOf(
            if (initialAllDay) 7 else (initialEndMinute.coerceIn(0, 1439) / 60)
        )
    }
    var endMinute by remember(initialStartMinute, initialEndMinute) {
        mutableIntStateOf(
            if (initialAllDay) 0 else (initialEndMinute.coerceIn(0, 1439) % 60)
        )
    }

    val startTotal = startHour * 60 + startMinute
    val endTotal = endHour * 60 + endMinute
    val previewLabel = when {
        allDay -> "全天"
        startTotal == endTotal -> "全天"
        endTotal < startTotal ->
            "${PeriodWindow.formatHm(startTotal)} – ${PeriodWindow.formatHm(endTotal)} · 跨午夜"
        else ->
            "${PeriodWindow.formatHm(startTotal)} – ${PeriodWindow.formatHm(endTotal)}"
    }
    val hint = when {
        allDay || startTotal == endTotal -> "选定日期内全程锁定，至次日 00:00 为一轮"
        endTotal < startTotal -> "跨午夜，至次日结束时刻解锁"
        else -> "半开区间：到点即解锁"
    }
    val normalized = if (allDay || startTotal == endTotal) {
        0 to 0
    } else {
        PeriodWindow.normalizeRange(startTotal, endTotal)
    }

    LaunchedEffect(normalized.first, normalized.second) {
        onRangeChange(normalized.first, normalized.second)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AllDayToggleChip(
            selected = allDay,
            onClick = { allDay = !allDay }
        )
        AnimatedVisibility(
            visible = !allDay,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "滑动选择 · 24 小时制",
                    fontSize = 13.sp,
                    color = cs.onSurface.copy(alpha = 0.45f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "开始",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = cs.onSurface.copy(alpha = 0.45f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        HourMinuteWheel(
                            hour = startHour,
                            minute = startMinute,
                            onHourChange = { startHour = it },
                            onMinuteChange = { startMinute = it },
                            compact = true
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "结束",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = cs.onSurface.copy(alpha = 0.45f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        HourMinuteWheel(
                            hour = endHour,
                            minute = endMinute,
                            onHourChange = { endHour = it },
                            onMinuteChange = { endMinute = it },
                            compact = true
                        )
                    }
                }
            }
        }
        Text(
            text = previewLabel,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = LogoGreen
        )
        Text(
            text = hint,
            fontSize = 12.sp,
            color = cs.onSurface.copy(alpha = 0.40f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AllDayToggleChip(
    selected: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                if (selected) LogoGreen.copy(alpha = 0.16f)
                else cs.onSurface.copy(alpha = 0.04f)
            )
            .border(
                width = 1.dp,
                color = if (selected) LogoGreen.copy(alpha = 0.45f)
                else cs.outline.copy(alpha = 0.28f),
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "全天",
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) LogoGreen else cs.onSurface.copy(alpha = 0.65f)
        )
    }
}

/**
 * 时长（分钟）：预设 chip + 时/分滑轮，一次选完。
 */
@Composable
internal fun DurationMinutesPickerDialog(
    title: String,
    initialMinutes: Int,
    minMinutes: Int,
    maxMinutes: Int,
    presets: List<Int>,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
    formatMinutes: (Int) -> String = { formatLimitMinutes(it) }
) {
    val cs = MaterialTheme.colorScheme
    val clampedInitial = initialMinutes.coerceIn(minMinutes, maxMinutes)
    var totalMinutes by remember(clampedInitial) { mutableIntStateOf(clampedInitial) }
    val maxHour = maxMinutes / 60
    val hour = (totalMinutes / 60).coerceIn(0, maxHour)
    val minute = totalMinutes % 60
    val isValid = totalMinutes in minMinutes..maxMinutes

    fun applyTotal(next: Int) {
        totalMinutes = next.coerceIn(minMinutes, maxMinutes)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        shape = RoundedCornerShape(14.dp),
        title = {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                DurationPresetChipRow(
                    presets = presets.filter { it in minMinutes..maxMinutes },
                    selectedMinutes = totalMinutes,
                    formatMinutes = formatMinutes,
                    onSelect = { applyTotal(it) }
                )
                Text(
                    text = "或滑动细调 · ${formatMinutes(minMinutes)} ~ ${formatMinutes(maxMinutes)}",
                    fontSize = 12.sp,
                    color = cs.onSurface.copy(alpha = 0.42f)
                )
                DurationHourMinuteWheel(
                    hour = hour,
                    minute = minute,
                    maxHour = maxHour,
                    onHourChange = { h ->
                        val raw = h * 60 + minute
                        applyTotal(raw)
                    },
                    onMinuteChange = { m ->
                        val raw = hour * 60 + m
                        applyTotal(raw)
                    }
                )
                Text(
                    text = formatMinutes(totalMinutes),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isValid) LogoGreen else cs.error
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (isValid) onConfirm(totalMinutes) },
                enabled = isValid,
                colors = ButtonDefaults.textButtonColors(contentColor = LogoGreen)
            ) { Text("确定", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = cs.onSurface.copy(alpha = 0.45f)
                )
            ) { Text("取消") }
        }
    )
}

@Composable
internal fun DurationPresetChipRow(
    presets: List<Int>,
    selectedMinutes: Int,
    formatMinutes: (Int) -> String,
    onSelect: (Int) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        presets.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { minutes ->
                    val selected = minutes == selectedMinutes
                    val shape = RoundedCornerShape(10.dp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(shape)
                            .background(
                                if (selected) LogoGreen.copy(alpha = 0.14f)
                                else cs.onSurface.copy(alpha = 0.04f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (selected) LogoGreen.copy(alpha = 0.45f)
                                else cs.outline.copy(alpha = 0.22f),
                                shape = shape
                            )
                            .clickable { onSelect(minutes) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = formatMinutes(minutes),
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) LogoGreen else cs.onSurface.copy(alpha = 0.78f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                repeat(3 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DurationHourMinuteWheel(
    hour: Int,
    minute: Int,
    maxHour: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(WheelItemHeight * WheelVisibleCount),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WheelItemHeight)
                .clip(RoundedCornerShape(10.dp))
                .background(LogoGreen.copy(alpha = 0.12f))
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            NumberWheelColumn(
                value = hour.coerceIn(0, maxHour),
                range = 0..maxHour,
                onValueChange = onHourChange,
                format = { "%d".format(it) },
                modifier = Modifier.width(72.dp)
            )
            Text(
                text = "时",
                fontSize = 14.sp,
                color = cs.onSurface.copy(alpha = 0.40f),
                modifier = Modifier.padding(end = 10.dp)
            )
            NumberWheelColumn(
                value = minute.coerceIn(0, 59),
                range = 0..59,
                onValueChange = onMinuteChange,
                format = { "%02d".format(it) },
                modifier = Modifier.width(72.dp)
            )
            Text(
                text = "分",
                fontSize = 14.sp,
                color = cs.onSurface.copy(alpha = 0.40f),
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun HourMinuteWheel(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    compact: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    val colWidth = if (compact) 56.dp else 88.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(WheelItemHeight * WheelVisibleCount),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WheelItemHeight)
                .clip(RoundedCornerShape(10.dp))
                .background(LogoGreen.copy(alpha = 0.12f))
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            NumberWheelColumn(
                value = hour,
                range = 0..23,
                onValueChange = onHourChange,
                format = { "%02d".format(it) },
                modifier = Modifier.width(colWidth)
            )
            Text(
                text = ":",
                fontSize = if (compact) 18.sp else 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface.copy(alpha = 0.35f),
                modifier = Modifier.padding(horizontal = 2.dp)
            )
            NumberWheelColumn(
                value = minute,
                range = 0..59,
                onValueChange = onMinuteChange,
                format = { "%02d".format(it) },
                modifier = Modifier.width(colWidth)
            )
        }
    }
}

/**
 * 选择一天中的时刻（00:00–23:59），用于格言推送等场景。
 */
@Composable
fun ClockTimePickerDialog(
    title: String,
    initialMinuteOfDay: Int,
    onDismiss: () -> Unit,
    onConfirm: (minuteOfDay: Int) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val clamped = initialMinuteOfDay.coerceIn(0, 1439)
    var hour by remember(clamped) { mutableIntStateOf(clamped / 60) }
    var minute by remember(clamped) { mutableIntStateOf(clamped % 60) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        shape = RoundedCornerShape(14.dp),
        title = {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HourMinuteWheel(
                    hour = hour,
                    minute = minute,
                    onHourChange = { hour = it },
                    onMinuteChange = { minute = it }
                )
                Text(
                    text = "%02d:%02d".format(hour, minute),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LogoGreen
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(hour * 60 + minute) },
                colors = ButtonDefaults.textButtonColors(contentColor = LogoGreen)
            ) { Text("确定", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = cs.onSurface.copy(alpha = 0.45f)
                )
            ) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NumberWheelColumn(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    format: (Int) -> String,
    modifier: Modifier = Modifier,
    itemHeight: Dp = WheelItemHeight
) {
    val values = remember(range) { range.toList() }
    val initialIndex = (value - range.first).coerceIn(0, values.lastIndex)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val fling = rememberSnapFlingBehavior(lazyListState = listState)
    val haptic = LocalHapticFeedback.current
    val onValueChangeState = rememberUpdatedState(onValueChange)
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }
    val sidePad = itemHeight * ((WheelVisibleCount - 1) / 2)

    val centeredIndex by remember {
        derivedStateOf {
            val offset = listState.firstVisibleItemScrollOffset
            val idx = listState.firstVisibleItemIndex +
                if (offset > itemHeightPx / 2f) 1 else 0
            idx.coerceIn(0, values.lastIndex)
        }
    }

    LaunchedEffect(value, range.first, range.last) {
        val target = (value - range.first).coerceIn(0, values.lastIndex)
        val current = values.getOrElse(centeredIndex) { value }
        if (current != value && !listState.isScrollInProgress) {
            listState.scrollToItem(target)
        }
    }

    LaunchedEffect(listState, values) {
        snapshotFlow { listState.isScrollInProgress to centeredIndex }
            .filter { (scrolling, _) -> !scrolling }
            .map { (_, index) -> index }
            .distinctUntilChanged()
            .collect { index ->
                val next = values[index]
                if (next != value) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onValueChangeState.value(next)
                }
            }
    }

    val cs = MaterialTheme.colorScheme
    LazyColumn(
        state = listState,
        flingBehavior = fling,
        modifier = modifier.height(itemHeight * WheelVisibleCount),
        contentPadding = PaddingValues(vertical = sidePad),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(values.size, key = { values[it] }) { index ->
            val selected = index == centeredIndex
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = format(values[index]),
                    fontSize = if (selected) 22.sp else 17.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) cs.onSurface.copy(alpha = 0.92f)
                    else cs.onSurface.copy(alpha = 0.28f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
