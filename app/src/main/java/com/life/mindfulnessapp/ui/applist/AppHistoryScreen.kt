package com.life.mindfulnessapp.ui.applist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.domain.model.AppTodayGlance
import com.life.mindfulnessapp.domain.model.TimelineDisplayItem
import com.life.mindfulnessapp.domain.model.TimelineEvent
import com.life.mindfulnessapp.domain.model.TimelineSectionItem
import com.life.mindfulnessapp.domain.model.buildHeldAwayOrdinalIndex
import com.life.mindfulnessapp.domain.model.buildTimelineSections
import com.life.mindfulnessapp.domain.model.collapseTimelineForDisplay
import com.life.mindfulnessapp.domain.model.groupTimelineSections
import com.life.mindfulnessapp.ui.home.NoteEditDialog
import com.life.mindfulnessapp.ui.home.RecordEditFocus
import com.life.mindfulnessapp.ui.home.TimelineDisplayNode
import com.life.mindfulnessapp.ui.home.TimelineGapRow
import com.life.mindfulnessapp.ui.home.TimelinePeriodHeader
import com.life.mindfulnessapp.ui.theme.LogoGreen
import kotlinx.coroutines.delay

private val muted = Color(0xFF8E8E93)

/**
 * 单 App 使用记录：今日一行事实 + 按日倒序时间轴。
 * [highlightRecordId] >0 高亮该条；0 高亮今日最新；-1 不高亮。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppHistoryScreen(
    packageName: String,
    highlightRecordId: Long = -1L,
    viewModel: AppHistoryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    LaunchedEffect(packageName) {
        viewModel.load(packageName)
    }

    val days by viewModel.days.collectAsState()
    val appInfo by viewModel.appInfo.collectAsState()
    val todayGlance by viewModel.todayGlance.collectAsState()
    val cs = MaterialTheme.colorScheme
    val iconMap = remember(appInfo) {
        appInfo?.let { mapOf(it.packageName to it) }.orEmpty()
    }
    val listState = rememberLazyListState()

    var editingEvent by remember { mutableStateOf<TimelineEvent.UsageEvent?>(null) }
    var editFocus by remember { mutableStateOf(RecordEditFocus.Note) }
    var expandedOverrides by remember(packageName) { mutableStateOf<Map<Long, Boolean>>(emptyMap()) }
    var activeHighlightId by remember(packageName, highlightRecordId) { mutableStateOf<Long?>(null) }
    var didScrollHighlight by remember(packageName, highlightRecordId) { mutableStateOf(false) }

    val ready = todayGlance != null

    LaunchedEffect(packageName, highlightRecordId) {
        if (highlightRecordId < 0L) return@LaunchedEffect
        repeat(8) {
            val snapshot = viewModel.days.value
            val resolved = if (highlightRecordId > 0L) {
                highlightRecordId
            } else {
                snapshot.firstOrNull { it.isToday }?.events?.firstOrNull()?.recordId
            }
            if (resolved != null && snapshot.any { day -> day.events.any { it.recordId == resolved } }) {
                activeHighlightId = resolved
                return@LaunchedEffect
            }
            delay(80L)
        }
    }

    fun isDayExpanded(day: AppHistoryDay): Boolean {
        expandedOverrides[day.dayStartMs]?.let { return it }
        val highlight = activeHighlightId
        if (highlight != null && day.events.any { it.recordId == highlight }) return true
        return day.isToday || day.isYesterday
    }

    LaunchedEffect(activeHighlightId, days, expandedOverrides) {
        val id = activeHighlightId ?: return@LaunchedEffect
        if (didScrollHighlight) return@LaunchedEffect
        val hasStrip = days.isNotEmpty()
        val index = historyHighlightIndex(
            days = days,
            isExpanded = { day -> isDayExpanded(day) },
            highlightRecordId = id,
            hasStrip = hasStrip
        )
        if (index >= 0) {
            listState.animateScrollToItem(index)
            didScrollHighlight = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            appInfo?.appName ?: "记录",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp
                        )
                        Text(
                            "使用记录",
                            fontSize = 11.sp,
                            color = muted
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        containerColor = cs.background
    ) { padding ->
        when {
            !ready -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = LogoGreen, strokeWidth = 2.dp)
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 20.dp,
                        top = 8.dp,
                        bottom = 40.dp
                    )
                ) {
                    if (days.isNotEmpty()) {
                        item(key = "today_strip") {
                            HistoryTodayStrip(
                                glance = todayGlance ?: AppTodayGlance(
                                    openCount = 0,
                                    dismissCount = 0,
                                    mindfulEnterCount = 0,
                                    totalSeconds = 0L,
                                    requireIntentOnOpen = appInfo?.requireIntentOnOpen == true
                                ),
                                showIntentMetrics = appInfo?.requireIntentOnOpen != false
                            )
                        }
                    }

                    if (days.isEmpty()) {
                        item(key = "empty") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "还没有这只锚的记录",
                                    fontSize = 14.sp,
                                    color = cs.onSurface.copy(alpha = 0.45f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        days.forEach { day ->
                            val expanded = isDayExpanded(day)
                            val collapsible = !day.isToday
                            item(key = "header_${day.dayStartMs}") {
                                HistoryDayHeader(
                                    day = day,
                                    expanded = expanded,
                                    collapsible = collapsible,
                                    onToggle = {
                                        expandedOverrides = expandedOverrides +
                                            (day.dayStartMs to !expanded)
                                    }
                                )
                            }
                            if (expanded) {
                                val displayItems = collapseTimelineForDisplay(day.events)
                                val sectionItems = buildTimelineSections(displayItems)
                                val periodGroups = groupTimelineSections(sectionItems)
                                val heldAwayIndex = buildHeldAwayOrdinalIndex(day.events)
                                periodGroups.forEach { group ->
                                    item(key = "${day.dayStartMs}_period_${group.name}") {
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
                                    items(
                                        items = group.rows,
                                        key = { section -> "${day.dayStartMs}_${section.key}" }
                                    ) { section ->
                                        when (section) {
                                            is TimelineSectionItem.PeriodHeader -> Unit
                                            is TimelineSectionItem.Gap -> {
                                                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                                    TimelineGapRow(
                                                        seconds = section.seconds,
                                                        onSurface = cs.onSurface
                                                    )
                                                }
                                            }
                                            is TimelineSectionItem.Event -> {
                                                val item = section.item
                                                val highlight = activeHighlightId
                                                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                                    TimelineDisplayNode(
                                                        item = item,
                                                        isLast = false,
                                                        iconMap = iconMap,
                                                        onRecordEdit = { event, focus ->
                                                            if (day.isToday &&
                                                                event.hasIntentGate &&
                                                                !event.isGateQuit &&
                                                                !event.isSeed
                                                            ) {
                                                                editingEvent = event
                                                                editFocus = focus
                                                            }
                                                        },
                                                        highlightRecordId = highlight,
                                                        forceExpandMerged = highlight != null &&
                                                            item is TimelineDisplayItem.MergedCluster &&
                                                            item.containsRecordId(highlight),
                                                        onHighlightDone = { activeHighlightId = null },
                                                        editable = day.isToday,
                                                        showAppIdentity = false,
                                                        heldAwayIndex = heldAwayIndex,
                                                        cardBg = cs.surface,
                                                        onSurface = cs.onSurface,
                                                        outline = cs.onSurface.copy(alpha = 0.12f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            item(key = "spacer_${day.dayStartMs}") {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    editingEvent?.let { event ->
        NoteEditDialog(
            event = event,
            cs = cs,
            focus = editFocus,
            onConfirm = { note, level, drift ->
                viewModel.updateRecordReview(event.recordId, note, level, drift)
                editingEvent = null
            },
            onDismiss = { editingEvent = null }
        )
    }
}

@Composable
private fun HistoryTodayStrip(
    glance: AppTodayGlance,
    showIntentMetrics: Boolean
) {
    val cs = MaterialTheme.colorScheme
    val used = formatCapabilityDuration(glance.totalSeconds)
    val text = if (showIntentMetrics) {
        "今日　打开 ${glance.enterCount} · 守住 ${glance.dismissCount} · 已用 $used"
    } else {
        "今日　已用 $used"
    }
    Text(
        text = text,
        fontSize = 13.sp,
        color = cs.onSurface.copy(alpha = 0.45f),
        modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 4.dp)
    )
}

@Composable
private fun HistoryDayHeader(
    day: AppHistoryDay,
    expanded: Boolean,
    collapsible: Boolean,
    onToggle: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (collapsible) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(start = 4.dp, top = 12.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = day.label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (day.isToday) LogoGreen else muted
            )
            if (!day.isToday) {
                Text(
                    text = day.pulseText(),
                    fontSize = 11.sp,
                    color = muted.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        if (collapsible) {
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                tint = cs.onSurface.copy(alpha = 0.28f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun AppHistoryDay.pulseText(): String {
    val used = formatCapabilityDuration(totalSeconds)
    // 打开 = 真正进门；守住另计，不并入「次」
    return "$enterCount 次 · $used"
}

private fun historyHighlightIndex(
    days: List<AppHistoryDay>,
    isExpanded: (AppHistoryDay) -> Boolean,
    highlightRecordId: Long,
    hasStrip: Boolean
): Int {
    var index = if (hasStrip) 1 else 0
    for (day in days) {
        val expanded = isExpanded(day)
        val displayItems = collapseTimelineForDisplay(day.events)
        val itemIdx = displayItems.indexOfFirst { item ->
            when (item) {
                is TimelineDisplayItem.Single ->
                    (item.event as? TimelineEvent.UsageEvent)?.recordId == highlightRecordId
                is TimelineDisplayItem.MergedCluster ->
                    item.containsRecordId(highlightRecordId)
            }
        }
        if (itemIdx >= 0 && expanded) {
            return index + 1 + itemIdx
        }
        index += 1
        if (expanded) index += displayItems.size
        index += 1
    }
    return -1
}
