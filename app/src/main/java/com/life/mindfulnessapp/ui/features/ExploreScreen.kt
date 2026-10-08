package com.life.mindfulnessapp.ui.features

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.life.mindfulnessapp.domain.model.BatchPickSortMode
import com.life.mindfulnessapp.domain.model.ParetoRankRow
import com.life.mindfulnessapp.domain.model.ParetoUsageSnapshot
import com.life.mindfulnessapp.ui.applist.AppIcon
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.util.AppUsageFormat
import kotlin.math.roundToInt

/**
 * 二八统计：近 7 日用量里，八成时间（或打开次数）由哪些 App 占据。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreUsageRankScreen(
    viewModel: ExploreViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToAppDetail: (packageName: String) -> Unit
) {
    val pareto by viewModel.pareto.collectAsState()
    val searchRows by viewModel.searchRows.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val usageLoading by viewModel.usageLoading.collectAsState()
    val hasLoadedOnce by viewModel.hasLoadedOnce.collectAsState()
    val permission by viewModel.permissionStatus.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val maxDuration by viewModel.maxDurationSeconds.collectAsState()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val listState = rememberLazyListState()
    var tailExpanded by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    LaunchedEffect(sortMode, searchQuery) {
        listState.scrollToItem(0)
        if (searchQuery.isNotBlank()) tailExpanded = false
    }

    val cs = MaterialTheme.colorScheme
    val isSearching = searchRows != null
    val hasData = !pareto.isEmpty

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("全量榜", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        Text(
                            "与今日热同源 · 近 7 日",
                            fontSize = 11.sp,
                            color = cs.onSurface.copy(alpha = 0.40f)
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
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        if (!permission.hasUsageStats) {
            ExplorePermissionCard(
                onGrant = {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        } else {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = viewModel::setSearchQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = {
                Text(
                    "搜索应用",
                    color = cs.onBackground.copy(alpha = 0.28f),
                    fontSize = 14.sp
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = cs.onBackground.copy(alpha = 0.28f)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "清除",
                            tint = cs.onBackground.copy(alpha = 0.5f)
                        )
                    }
                }
            },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LogoGreen,
                unfocusedBorderColor = cs.outline.copy(alpha = 0.4f),
                focusedContainerColor = cs.surface,
                unfocusedContainerColor = cs.surface,
                focusedTextColor = cs.onSurface,
                unfocusedTextColor = cs.onSurface
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExploreSortChip(
                label = "按时长",
                selected = sortMode == BatchPickSortMode.Duration,
                onClick = { viewModel.setSortMode(BatchPickSortMode.Duration) }
            )
            ExploreSortChip(
                label = "按打开",
                selected = sortMode == BatchPickSortMode.Launches,
                onClick = { viewModel.setSortMode(BatchPickSortMode.Launches) }
            )
            if (usageLoading && hasData) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(16.dp),
                    color = LogoGreen,
                    strokeWidth = 2.dp
                )
            }
        }

        when {
            !hasData && (usageLoading || !hasLoadedOnce) -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "正在读取系统用量…",
                        fontSize = 12.sp,
                        color = cs.onSurface.copy(alpha = 0.38f),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                    ExploreRankListSkeleton()
                }
            }
            isSearching && searchRows!!.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "没有匹配的应用",
                        fontSize = 14.sp,
                        color = cs.onSurface.copy(alpha = 0.38f),
                        textAlign = TextAlign.Center
                    )
                }
            }
            !hasData -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "近 7 日没有可展示的前台记录",
                        fontSize = 14.sp,
                        color = cs.onSurface.copy(alpha = 0.38f),
                        textAlign = TextAlign.Center
                    )
                }
            }
            isSearching -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        top = 6.dp,
                        bottom = 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item(key = "search_hint") {
                        Text(
                            text = "搜索结果 · ${searchRows!!.size} 款",
                            fontSize = 10.sp,
                            color = cs.onSurface.copy(alpha = 0.36f),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }
                    items(searchRows!!, key = { it.app.listKey }) { row ->
                        ExploreParetoRankCard(
                            row = row,
                            sortMode = sortMode,
                            maxDurationSeconds = maxDuration.coerceAtLeast(1L),
                            emphasizeShare = true,
                            onClick = { onNavigateToAppDetail(row.app.packageName) }
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        top = 4.dp,
                        bottom = 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item(key = "pareto_summary") {
                        ParetoSummaryCard(
                            snapshot = pareto,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    item(key = "core_header") {
                        Text(
                            text = "占据约八成 · ${pareto.core.size} 款",
                            fontSize = 10.sp,
                            color = cs.onSurface.copy(alpha = 0.36f),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }
                    items(pareto.core, key = { "core_${it.app.listKey}" }) { row ->
                        ExploreParetoRankCard(
                            row = row,
                            sortMode = sortMode,
                            maxDurationSeconds = maxDuration.coerceAtLeast(1L),
                            emphasizeShare = true,
                            onClick = { onNavigateToAppDetail(row.app.packageName) }
                        )
                    }
                    if (pareto.tail.isNotEmpty()) {
                        item(key = "tail_toggle") {
                            ParetoTailToggle(
                                count = pareto.tail.size,
                                sharePercent = (pareto.tailShare * 100f).roundToInt().coerceIn(0, 100),
                                expanded = tailExpanded,
                                onClick = { tailExpanded = !tailExpanded },
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                            )
                        }
                        if (tailExpanded) {
                            items(pareto.tail, key = { "tail_${it.app.listKey}" }) { row ->
                                ExploreParetoRankCard(
                                    row = row,
                                    sortMode = sortMode,
                                    maxDurationSeconds = maxDuration.coerceAtLeast(1L),
                                    emphasizeShare = false,
                                    onClick = { onNavigateToAppDetail(row.app.packageName) }
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }
    }
}

@Composable
private fun ParetoSummaryCard(
    snapshot: ParetoUsageSnapshot,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val pct = (snapshot.threshold * 100f).roundToInt()
    val coreSharePct = (snapshot.coreShare * 100f).roundToInt().coerceIn(0, 100)
    val metricLabel = when (snapshot.metric) {
        BatchPickSortMode.Duration -> "时长"
        BatchPickSortMode.Launches -> "打开次数"
    }
    val totalLabel = when (snapshot.metric) {
        BatchPickSortMode.Duration -> AppUsageFormat.totalDurationCompact(snapshot.totalSeconds)
        BatchPickSortMode.Launches -> "${snapshot.totalLaunches}次"
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cs.surface)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "近 7 日约 ${pct}% ${metricLabel}，由 ${snapshot.core.size} 个 App 占据",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurface,
            letterSpacing = (-0.2).sp,
            lineHeight = 22.sp
        )
        Text(
            text = "合计 ${totalLabel} · 这 ${snapshot.core.size} 款约占 ${coreSharePct}%",
            fontSize = 12.sp,
            color = cs.onSurface.copy(alpha = 0.42f),
            lineHeight = 17.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(cs.onSurface.copy(alpha = 0.06f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(snapshot.coreShare.coerceIn(0.04f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                LogoGreen.copy(alpha = 0.55f),
                                LogoGreen.copy(alpha = 0.9f)
                            )
                        )
                    )
            )
        }
    }
}

@Composable
private fun ParetoTailToggle(
    count: Int,
    sharePercent: Int,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cs.surface.copy(alpha = 0.72f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "其余 $count 个 App · 约占 ${sharePercent}%",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = cs.onSurface.copy(alpha = 0.55f)
        )
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (expanded) "收起" else "展开",
            tint = LogoGreen.copy(alpha = 0.75f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ExploreRankListSkeleton(count: Int = 8) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 14.dp,
            end = 14.dp,
            top = 2.dp,
            bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(count) {
            ExploreRankSkeletonCard()
        }
    }
}

@Composable
private fun ExploreRankSkeletonCard() {
    val cs = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "rank_skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton_alpha"
    )
    val bone = cs.onSurface.copy(alpha = 0.08f * alpha + 0.06f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cs.surface)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(22.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(bone)
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(bone)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(bone)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.38f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(bone)
            )
        }
    }
}

@Composable
private fun ExploreSortChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = LogoGreen.copy(alpha = 0.16f),
            selectedLabelColor = LogoGreen
        )
    )
}

@Composable
private fun ExplorePermissionCard(
    onGrant: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(cs.surface)
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "打开使用情况访问",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurface,
            letterSpacing = (-0.2).sp
        )
        Text(
            text = "二八统计需要读取系统近 7 日的前台用量，才能看见八成时间落在哪些 App。",
            fontSize = 13.sp,
            color = cs.onSurface.copy(alpha = 0.45f),
            lineHeight = 19.sp
        )
        TextButton(
            onClick = onGrant,
            modifier = Modifier.padding(top = 2.dp)
        ) {
            Text("去授权", color = LogoGreen, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ExploreParetoRankCard(
    row: ParetoRankRow,
    sortMode: BatchPickSortMode,
    maxDurationSeconds: Long,
    emphasizeShare: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val fraction = (row.usage.totalSeconds.toFloat() / maxDurationSeconds.toFloat())
        .coerceIn(0.04f, 1f)
    val barProgress by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "explore_bar_${row.app.packageName}"
    )
    val rankTint = remember(row.rank) {
        when (row.rank) {
            1 -> Color(0xFFC9A227)
            2 -> Color(0xFF8A96B0)
            3 -> Color(0xFFB8734A)
            else -> null
        }
    }
    val sharePct = (row.shareOfTotal * 100f).roundToInt().coerceIn(0, 100)
    val cumPct = (row.cumulativeShare * 100f).roundToInt().coerceIn(0, 100)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cs.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = row.rank.toString().padStart(2, '0'),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = rankTint ?: cs.onSurface.copy(alpha = 0.26f),
                letterSpacing = (-0.4).sp,
                modifier = Modifier.width(22.dp)
            )
            AppIcon(drawable = row.app.icon, modifier = Modifier.size(36.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.app.appName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 17.sp
                )
                val duration = AppUsageFormat.totalDurationCompact(row.usage.totalSeconds)
                val launches = "${row.usage.totalLaunches}次"
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = duration,
                        fontSize = 11.sp,
                        fontWeight = if (sortMode == BatchPickSortMode.Duration) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Normal
                        },
                        color = if (sortMode == BatchPickSortMode.Duration) {
                            cs.onSurface.copy(alpha = 0.68f)
                        } else {
                            cs.onSurface.copy(alpha = 0.38f)
                        }
                    )
                    Text(
                        text = "·",
                        fontSize = 11.sp,
                        color = cs.onSurface.copy(alpha = 0.24f)
                    )
                    Text(
                        text = launches,
                        fontSize = 11.sp,
                        fontWeight = if (sortMode == BatchPickSortMode.Launches) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Normal
                        },
                        color = if (sortMode == BatchPickSortMode.Launches) {
                            cs.onSurface.copy(alpha = 0.68f)
                        } else {
                            cs.onSurface.copy(alpha = 0.38f)
                        }
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${sharePct}%",
                    fontSize = 13.sp,
                    fontWeight = if (emphasizeShare) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (emphasizeShare) LogoGreen else cs.onSurface.copy(alpha = 0.45f),
                    letterSpacing = (-0.3).sp
                )
                if (emphasizeShare) {
                    Text(
                        text = "累计 ${cumPct}%",
                        fontSize = 10.sp,
                        color = cs.onSurface.copy(alpha = 0.32f)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.5.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(cs.onSurface.copy(alpha = 0.06f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(barProgress)
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                LogoGreen.copy(alpha = 0.5f),
                                LogoGreen.copy(alpha = 0.82f)
                            )
                        )
                    )
            )
        }
    }
}
