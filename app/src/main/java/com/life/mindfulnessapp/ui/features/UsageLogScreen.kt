package com.life.mindfulnessapp.ui.features

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.domain.model.UsageLogHourChip
import com.life.mindfulnessapp.domain.model.UsageLogListItem
import com.life.mindfulnessapp.domain.model.UsageLogPeriodGroup
import com.life.mindfulnessapp.domain.model.UsageTransitionBuilder
import com.life.mindfulnessapp.domain.model.UsageTransitionDotKind
import com.life.mindfulnessapp.domain.model.UsageTransitionScene
import com.life.mindfulnessapp.ui.theme.LogoGreen
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.launch

private val FocusEnterBlue = Color(0xFF6BB4F0)
private val LimitRed = Color(0xFFFF453A)
private val Weekdays = arrayOf("日", "一", "二", "三", "四", "五", "六")
private val TimeColWidth = 48.dp
private val SpineColWidth = 18.dp
private val AppIconSize = 14.dp
private val SoftOpenIconSize = 12.dp
private val EmptyHourHeight = 12.dp
private val QuietRangeHeight = 14.dp
private val AmbientRowHeight = 36.dp
private val SoftOpenRowHeight = 36.dp
private val ContentRowMin = 48.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun UsageLogScreen(
    viewModel: UsageLogViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val selectedPage by viewModel.selectedPage.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val periodGroups = remember(uiState.items) {
        UsageTransitionBuilder.groupByPeriod(uiState.items)
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val keyToIndex = remember(periodGroups) {
        buildListKeyIndex(periodGroups)
    }
    val appShade = remember(periodGroups) { usageLogAppShade(periodGroups) }
    val rowByKey = remember(periodGroups) {
        periodGroups.asSequence()
            .flatMap { it.rows.asSequence() }
            .filterIsInstance<UsageLogListItem.Row>()
            .associateBy { it.key }
    }
    val activeHour by remember(rowByKey) {
        derivedStateOf { resolveUsageLogActiveHour(listState, rowByKey) }
    }
    val tailKey = uiState.items.lastOrNull()?.key
    LaunchedEffect(selectedPage, uiState.loading, tailKey) {
        if (uiState.loading) return@LaunchedEffect
        val index = if (uiState.isToday) {
            lastUsageLogRowIndex(periodGroups)
        } else {
            0
        }
        listState.scrollToItem(index.coerceAtLeast(0))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "使用日志",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                            color = cs.onSurface
                        )
                        Text(
                            text = "一天中的切换",
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        containerColor = cs.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            UsageLogDayStrip(
                dayCount = viewModel.dayCount,
                selectedPage = selectedPage,
                dayStartMs = viewModel::dayStartMs,
                onSelectPage = viewModel::selectPage
            )

            when {
                !uiState.hasUsageStats -> {
                    UsageLogPermissionEmpty(
                        onOpenSettings = {
                            context.startActivity(
                                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        }
                    )
                }
                uiState.loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "整理这一天…",
                            fontSize = 13.sp,
                            color = cs.onSurface.copy(alpha = 0.40f)
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        periodGroups.forEach { group ->
                            stickyHeader(key = group.header.key) {
                                UsageLogPeriodHeader(
                                    item = group.header,
                                    hourChips = group.hourChips,
                                    activeHour = activeHour,
                                    onHourClick = { chip ->
                                        val index = keyToIndex[chip.anchorKey] ?: return@UsageLogPeriodHeader
                                        scope.launch {
                                            listState.animateScrollToItem(index)
                                        }
                                    }
                                )
                            }
                            items(
                                items = group.rows,
                                key = { it.key }
                            ) { item ->
                                when (item) {
                                    is UsageLogListItem.Row -> UsageLogTransitionRow(
                                        item = item,
                                        shaded = appShade[item.key] == true
                                    )
                                    is UsageLogListItem.EmptyHour -> UsageLogEmptyHour(item.hour)
                                    is UsageLogListItem.QuietRange -> UsageLogQuietRange(item)
                                    is UsageLogListItem.PeriodHeader -> Unit
                                }
                            }
                        }
                        if (uiState.isToday) {
                            item(key = "now_anchor") {
                                Text(
                                    text = "· 现在 ·",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp, bottom = 8.dp),
                                    textAlign = TextAlign.Center,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = LogoGreen.copy(alpha = 0.70f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildListKeyIndex(
    groups: List<UsageLogPeriodGroup>
): Map<String, Int> {
    val map = HashMap<String, Int>()
    var index = 0
    for (group in groups) {
        map[group.header.key] = index
        index++
        for (row in group.rows) {
            map[row.key] = index
            index++
        }
    }
    return map
}

private fun lastUsageLogRowIndex(groups: List<UsageLogPeriodGroup>): Int {
    var index = 0
    var lastRow = -1
    for (group in groups) {
        index++
        for (row in group.rows) {
            if (row is UsageLogListItem.Row) lastRow = index
            index++
        }
    }
    return lastRow
}

internal fun resolveUsageLogActiveHour(
    listState: LazyListState,
    rowByKey: Map<String, UsageLogListItem.Row>
): Int? {
    val visible = listState.layoutInfo.visibleItemsInfo
    if (visible.isEmpty()) return null
    for (info in visible) {
        val key = info.key as? String ?: continue
        if (key.startsWith("period_")) continue
        when {
            key.startsWith("t_") -> return rowByKey[key]?.hour
            key.startsWith("empty_") -> return key.removePrefix("empty_").toIntOrNull()
            key.startsWith("quiet_") -> {
                val parts = key.removePrefix("quiet_").split("_")
                return parts.firstOrNull()?.toIntOrNull()
            }
        }
    }
    return null
}

@Composable
private fun UsageLogDayStrip(
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
    ) {
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
                val weekday = Weekdays[cal.get(Calendar.DAY_OF_WEEK) - 1]
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

@Composable
internal fun UsageLogPeriodHeader(
    item: UsageLogListItem.PeriodHeader,
    hourChips: List<UsageLogHourChip>,
    activeHour: Int?,
    onHourClick: (UsageLogHourChip) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(cs.background.copy(alpha = 0.96f))
            .padding(top = 6.dp, bottom = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 20.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = item.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface.copy(alpha = 0.55f),
                letterSpacing = 0.4.sp
            )
            Text(
                text = item.range,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                color = cs.onSurface.copy(alpha = 0.28f)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(cs.onSurface.copy(alpha = 0.08f))
            )
        }
        if (hourChips.isNotEmpty()) {
            // 尺子：数字 + 有内容短刻度；选中为底边绿线，不做厚底板
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                hourChips.forEach { chip ->
                    val selected = activeHour == chip.hour
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onHourClick(chip) }
                            .padding(vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .width(if (chip.hasContent) 3.dp else 1.dp)
                                .height(4.dp)
                                .background(
                                    when {
                                        selected -> LogoGreen.copy(alpha = 0.85f)
                                        chip.hasContent -> cs.onSurface.copy(alpha = 0.35f)
                                        else -> cs.onSurface.copy(alpha = 0.10f)
                                    },
                                    RoundedCornerShape(1.dp)
                                )
                        )
                        Text(
                            text = chip.hour.toString(),
                            fontSize = 11.sp,
                            fontWeight = if (selected || chip.hasContent) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Medium
                            },
                            fontFamily = FontFamily.Monospace,
                            color = when {
                                selected -> LogoGreen
                                chip.hasContent -> cs.onSurface.copy(alpha = 0.58f)
                                else -> cs.onSurface.copy(alpha = 0.22f)
                            },
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 2.dp, bottom = 3.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                                .height(2.dp)
                                .background(
                                    if (selected) LogoGreen.copy(alpha = 0.85f)
                                    else Color.Transparent,
                                    RoundedCornerShape(1.dp)
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageLogEmptyHour(hour: Int) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(EmptyHourHeight)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UsageLogTimeSpacer()
        Box(
            modifier = Modifier
                .width(SpineColWidth)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(cs.onSurface.copy(alpha = 0.06f))
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(cs.onSurface.copy(alpha = 0.04f))
        )
    }
}

@Composable
private fun UsageLogQuietRange(item: UsageLogListItem.QuietRange) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(QuietRangeHeight)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (item.fromHour == item.toHour) {
                "${item.fromHour}时"
            } else {
                "${item.fromHour}–${item.toHour}时"
            },
            modifier = Modifier.width(TimeColWidth),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            color = cs.onSurface.copy(alpha = 0.22f),
            textAlign = TextAlign.End,
            maxLines = 1
        )
        Box(
            modifier = Modifier
                .width(SpineColWidth)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(cs.onSurface.copy(alpha = 0.06f))
            )
        }
        Text(
            text = "安静",
            fontSize = 9.sp,
            color = cs.onSurface.copy(alpha = 0.16f),
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
private fun UsageLogTimeSpacer() {
    Spacer(modifier = Modifier.width(TimeColWidth))
}

internal fun usageLogAppShade(
    groups: List<UsageLogPeriodGroup>
): Map<String, Boolean> {
    var shade = false
    var lastPkg: String? = null
    val out = HashMap<String, Boolean>()
    for (group in groups) {
        for (item in group.rows) {
            if (item is UsageLogListItem.Row) {
                val pkg = item.transition.packageName
                if (lastPkg != null && pkg != lastPkg) shade = !shade
                lastPkg = pkg
                out[item.key] = shade
            }
        }
    }
    return out
}

@Composable
internal fun UsageLogTransitionRow(
    item: UsageLogListItem.Row,
    trailing: (@Composable () -> Unit)? = null,
    highlighted: Boolean = false,
    shaded: Boolean = false
) {
    val transition = item.transition
    val cs = MaterialTheme.colorScheme
    val isAmbient = transition.scene == UsageTransitionScene.HOME
    val isSoftOpen = transition.scene == UsageTransitionScene.OPEN
    val detailNote = transition.secondary?.trim()?.takeIf { it.isNotEmpty() }
    val showAppMeta = !isAmbient && transition.appName.isNotBlank()
    val rowMinHeight = when {
        isAmbient -> AmbientRowHeight
        isSoftOpen -> SoftOpenRowHeight
        showAppMeta && detailNote != null -> 60.dp
        showAppMeta -> ContentRowMin
        else -> 40.dp
    }
    val topPad = if (isAmbient || isSoftOpen) 8.dp else 12.dp
    val contentAlpha = if (isSoftOpen) 0.58f else 1f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                when {
                    highlighted -> LogoGreen.copy(alpha = 0.08f)
                    shaded -> cs.onSurface.copy(alpha = 0.045f)
                    else -> Color.Transparent
                }
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(TimeColWidth)
                .height(rowMinHeight),
            contentAlignment = Alignment.TopEnd
        ) {
            Text(
                text = UsageLogViewModel.formatClock(transition.timeMs),
                fontSize = if (isSoftOpen) 10.sp else 11.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                color = cs.onSurface.copy(alpha = if (isSoftOpen) 0.32f else 0.46f),
                modifier = Modifier.padding(top = topPad, end = 2.dp)
            )
        }
        Box(
            modifier = Modifier
                .width(SpineColWidth)
                .height(rowMinHeight),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(cs.onSurface.copy(alpha = if (isSoftOpen) 0.07f else 0.12f))
            )
            UsageLogDot(
                kind = transition.scene.dotKind,
                modifier = Modifier.padding(top = if (isAmbient || isSoftOpen) 10.dp else 14.dp),
                soft = isSoftOpen
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (isAmbient) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UsageLogSystemGlyph()
                Text(
                    text = transition.scene.chipLabel,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LogoGreen.copy(alpha = 0.90f)
                )
                if (detailNote != null) {
                    Text(
                        text = detailNote,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = cs.onSurface.copy(alpha = 0.40f),
                        maxLines = 1
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = if (isSoftOpen) 6.dp else 8.dp, bottom = if (isSoftOpen) 6.dp else 8.dp),
                verticalArrangement = Arrangement.spacedBy(if (isSoftOpen) 2.dp else 4.dp)
            ) {
                UsageLogSceneChip(scene = transition.scene, soft = isSoftOpen)
                if (showAppMeta) {
                    val iconSize = if (isSoftOpen) SoftOpenIconSize else AppIconSize
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        UsageLogAppIcon(
                            packageName = transition.packageName,
                            appName = transition.appName,
                            size = iconSize,
                            alpha = contentAlpha
                        )
                        Text(
                            text = transition.appName,
                            fontSize = if (isSoftOpen) 12.sp else 14.sp,
                            fontWeight = if (isSoftOpen) FontWeight.Medium else FontWeight.SemiBold,
                            color = cs.onSurface.copy(alpha = if (isSoftOpen) 0.55f else 0.88f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (transition.repeatCount > 1) {
                            Text(
                                text = "×${transition.repeatCount}",
                                fontSize = if (isSoftOpen) 11.sp else 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace,
                                color = cs.onSurface.copy(alpha = if (isSoftOpen) 0.28f else 0.40f)
                            )
                        }
                    }
                    if (detailNote != null && !isSoftOpen) {
                        Text(
                            text = detailNote,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = cs.onSurface.copy(alpha = 0.62f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = iconSize + 6.dp)
                        )
                    }
                } else if (detailNote != null) {
                    Text(
                        text = detailNote,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface.copy(alpha = 0.88f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (trailing != null) {
            Box(
                modifier = Modifier.padding(top = 8.dp, start = 6.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                trailing()
            }
        }
    }
}

@Composable
private fun UsageLogSystemGlyph() {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(AppIconSize)
            .clip(RoundedCornerShape(5.dp))
            .background(cs.onSurface.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Home,
            contentDescription = null,
            tint = LogoGreen.copy(alpha = 0.75f),
            modifier = Modifier.size(11.dp)
        )
    }
}

@Composable
private fun UsageLogSceneChip(scene: UsageTransitionScene, soft: Boolean = false) {
    val (fg, bg) = sceneChipColors(scene)
    // 决策色块：意图拦截 / 进入使用 / 门外离开 / 结束 / 回到 / 到点 / 时段锁拦住
    // 其余动作用字色（打开 / 跳到 / 离开）
    val textOnly = scene == UsageTransitionScene.OPEN ||
        scene == UsageTransitionScene.JUMP ||
        scene == UsageTransitionScene.LEAVE
    val light = soft || textOnly ||
        scene == UsageTransitionScene.GATE_LEAVE ||
        scene == UsageTransitionScene.LEAVE
    Text(
        text = scene.chipLabel,
        fontSize = if (soft || textOnly) 11.sp else if (light) 11.sp else 12.sp,
        fontWeight = if (soft || textOnly || light) FontWeight.Medium else FontWeight.SemiBold,
        color = when {
            soft -> fg.copy(alpha = 0.55f)
            textOnly -> fg.copy(alpha = 0.72f)
            else -> fg
        },
        maxLines = 1,
        modifier = Modifier
            .then(
                if (textOnly) {
                    Modifier.padding(vertical = 1.dp)
                } else {
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (soft) bg.copy(alpha = 0.45f) else bg)
                        .padding(
                            horizontal = if (soft || light) 6.dp else 8.dp,
                            vertical = if (soft || light) 2.dp else 3.dp
                        )
                }
            )
    )
}

@Composable
private fun sceneChipColors(scene: UsageTransitionScene): Pair<Color, Color> {
    val on = MaterialTheme.colorScheme.onSurface
    return when (scene.dotKind) {
        UsageTransitionDotKind.GREEN ->
            LogoGreen to LogoGreen.copy(alpha = 0.16f)
        UsageTransitionDotKind.SOFT_GREEN ->
            LogoGreen.copy(alpha = 0.72f) to LogoGreen.copy(alpha = 0.08f)
        UsageTransitionDotKind.BLUE ->
            FocusEnterBlue to FocusEnterBlue.copy(alpha = 0.16f)
        UsageTransitionDotKind.RED ->
            LimitRed to LimitRed.copy(alpha = 0.14f)
        UsageTransitionDotKind.HOLLOW ->
            on.copy(alpha = 0.72f) to on.copy(alpha = 0.08f)
        UsageTransitionDotKind.MUTED ->
            on.copy(alpha = 0.70f) to on.copy(alpha = 0.08f)
    }
}

@Composable
private fun UsageLogAppIcon(
    packageName: String,
    appName: String,
    size: Dp = AppIconSize,
    alpha: Float = 1f
) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        runCatching {
            context.packageManager
                .getApplicationIcon(packageName)
                .toBitmap(72, 72)
                .asImageBitmap()
        }.getOrNull()
    }
    val radius = (size.value * 0.22f).dp
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = appName,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(radius)),
            alpha = alpha
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(radius))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f * alpha)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Android,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.40f * alpha),
                modifier = Modifier.size(size * 0.55f)
            )
        }
    }
}

@Composable
private fun UsageLogDot(
    kind: UsageTransitionDotKind,
    modifier: Modifier = Modifier,
    soft: Boolean = false
) {
    val bg = MaterialTheme.colorScheme.background
    val size = if (soft) 7.dp else if (kind == UsageTransitionDotKind.SOFT_GREEN) 8.dp else 9.dp
    when (kind) {
        UsageTransitionDotKind.HOLLOW -> {
            Box(
                modifier = modifier
                    .size(if (soft) 7.dp else 8.dp)
                    .border(1.25.dp, Color.White.copy(alpha = if (soft) 0.22f else 0.32f), CircleShape)
                    .background(bg, CircleShape)
            )
        }
        else -> {
            val color = when (kind) {
                UsageTransitionDotKind.GREEN -> LogoGreen
                UsageTransitionDotKind.SOFT_GREEN -> LogoGreen.copy(alpha = 0.55f)
                UsageTransitionDotKind.BLUE -> FocusEnterBlue
                UsageTransitionDotKind.RED -> LimitRed
                else -> Color.White.copy(alpha = if (soft) 0.32f else 0.55f)
            }
            Box(
                modifier = modifier
                    .size(size)
                    .border(3.dp, bg, CircleShape)
                    .background(color, CircleShape)
            )
        }
    }
}

@Composable
private fun UsageLogPermissionEmpty(onOpenSettings: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "需要「使用情况访问」才能看到整机切换",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = cs.onSurface.copy(alpha = 0.55f)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "去授权",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = LogoGreen,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onOpenSettings)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}
