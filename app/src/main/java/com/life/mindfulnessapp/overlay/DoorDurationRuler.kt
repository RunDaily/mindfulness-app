package com.life.mindfulnessapp.overlay

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.domain.model.SessionLimitPolicy
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlin.math.abs

/**
 * 门口时长尺子：默认可从 0 起（未定）；1 分钟一格，横滑吸附。
 * 刻度线在上、数字在下，避免挤在一起；过格轻震。
 */
@Composable
internal fun DoorDurationRuler(
    selectedMinutes: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    maxMinutes: Int = SessionLimitPolicy.MAX_SESSION_MINUTES,
    minMinutes: Int = 0
) {
    val marks = remember(minMinutes, maxMinutes) {
        (minMinutes.coerceAtLeast(0)..maxMinutes.coerceAtLeast(minMinutes)).toList()
    }
    val haptic = LocalHapticFeedback.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val itemWidth = 18.dp
    val sidePad = ((screenWidth - 52.dp) - itemWidth) / 2
    val listState = rememberLazyListState()
    val fling = rememberSnapFlingBehavior(lazyListState = listState)
    val onSelectLatest = rememberUpdatedState(onSelect)
    var lastEmitted by remember { mutableIntStateOf(selectedMinutes) }
    var lastHapticIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(marks, selectedMinutes) {
        val idx = marks.indexOf(selectedMinutes).coerceAtLeast(0)
        if (listState.firstVisibleItemIndex != idx || listState.firstVisibleItemScrollOffset != 0) {
            listState.scrollToItem(idx)
        }
        lastEmitted = selectedMinutes
        lastHapticIndex = idx
    }

    LaunchedEffect(listState, marks) {
        snapshotFlow {
            val info = listState.layoutInfo
            if (info.visibleItemsInfo.isEmpty()) return@snapshotFlow -1
            val viewportCenter =
                (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { item ->
                abs((item.offset + item.size / 2) - viewportCenter)
            }?.index ?: -1
        }
            .distinctUntilChanged()
            .filter { it >= 0 }
            .collect { idx ->
                if (idx != lastHapticIndex && listState.isScrollInProgress) {
                    lastHapticIndex = idx
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
    }

    LaunchedEffect(listState, marks) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .filter { scrolling -> !scrolling }
            .collect {
                val info = listState.layoutInfo
                if (info.visibleItemsInfo.isEmpty()) return@collect
                val viewportCenter =
                    (info.viewportStartOffset + info.viewportEndOffset) / 2
                val nearest = info.visibleItemsInfo.minByOrNull { item ->
                    abs((item.offset + item.size / 2) - viewportCenter)
                } ?: return@collect
                val value = marks.getOrNull(nearest.index) ?: return@collect
                lastHapticIndex = nearest.index
                if (value != lastEmitted) {
                    lastEmitted = value
                    onSelectLatest.value(value)
                }
            }
    }

    val liveIndex by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            if (info.visibleItemsInfo.isEmpty()) {
                marks.indexOf(selectedMinutes).coerceAtLeast(0)
            } else {
                val viewportCenter =
                    (info.viewportStartOffset + info.viewportEndOffset) / 2
                info.visibleItemsInfo.minByOrNull { item ->
                    abs((item.offset + item.size / 2) - viewportCenter)
                }?.index ?: marks.indexOf(selectedMinutes).coerceAtLeast(0)
            }
        }
    }
    val liveMinutes = marks.getOrNull(liveIndex) ?: selectedMinutes

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (liveMinutes <= 0) "选择时长" else "${liveMinutes}分",
            fontSize = if (liveMinutes <= 0) 16.sp else 22.sp,
            fontWeight = if (liveMinutes <= 0) FontWeight.Normal else FontWeight.Medium,
            color = if (liveMinutes <= 0) LockInk.mist else LockInk.leafText,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .drawBehind {
                    // 中线只穿过刻度带，不压到下方数字
                    val cx = size.width / 2f
                    val tickBandBottom = size.height * 0.58f
                    drawLine(
                        color = LockInk.leafText.copy(alpha = 0.85f),
                        start = Offset(cx, size.height * 0.06f),
                        end = Offset(cx, tickBandBottom),
                        strokeWidth = 1.5.dp.toPx()
                    )
                },
            contentAlignment = Alignment.TopCenter
        ) {
            LazyRow(
                state = listState,
                flingBehavior = fling,
                contentPadding = PaddingValues(horizontal = sidePad.coerceAtLeast(0.dp)),
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(marks, key = { _, m -> m }) { index, mark ->
                    val on = index == liveIndex
                    val major = mark % 5 == 0
                    Column(
                        modifier = Modifier.width(itemWidth),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .drawBehind {
                                    val cx = size.width / 2f
                                    val tickH = when {
                                        on -> size.height * 0.92f
                                        major -> size.height * 0.68f
                                        else -> size.height * 0.42f
                                    }
                                    drawLine(
                                        color = when {
                                            on -> LockInk.leafText.copy(alpha = 0.92f)
                                            major -> LockInk.mist.copy(alpha = 0.72f)
                                            else -> LockInk.mist.copy(alpha = 0.38f)
                                        },
                                        start = Offset(cx, size.height - tickH),
                                        end = Offset(cx, size.height),
                                        strokeWidth = when {
                                            on -> 1.5.dp.toPx()
                                            major -> 1.2.dp.toPx()
                                            else -> 1.dp.toPx()
                                        }
                                    )
                                }
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            if (major) {
                                Text(
                                    text = "$mark",
                                    fontSize = if (on) 11.sp else 10.sp,
                                    color = if (on) LockInk.leafText else LockInk.mist
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
