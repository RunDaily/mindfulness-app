package com.life.mindfulnessapp.ui.applist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.data.repository.IntentPoolRepository
import com.life.mindfulnessapp.domain.model.CategoryBreakdown
import com.life.mindfulnessapp.domain.model.IntentCategory
import com.life.mindfulnessapp.domain.model.IntentPoolItem
import com.life.mindfulnessapp.domain.model.IntentPoolSnapshot
import com.life.mindfulnessapp.domain.model.IntentPoolSort
import com.life.mindfulnessapp.domain.model.IntentPoolTimeScope
import com.life.mindfulnessapp.ui.common.formatCompactDuration
import com.life.mindfulnessapp.ui.theme.LogoGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class AppRelationTab(val label: String) {
    Overview("概览"),
    Records("记录"),
    Intent("意图"),
    Guard("守护")
}

/** 详情页四 Tab：概览 / 记录 / 意图 / 守护（图标 + 下划线） */
@Composable
fun AppRelationTabSwitcher(
    selected: AppRelationTab,
    onSelected: (AppRelationTab) -> Unit,
    visibleTabs: List<AppRelationTab> = AppRelationTab.entries,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val tabs = visibleTabs.ifEmpty { AppRelationTab.entries }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        tabs.forEach { tab ->
            val active = tab == selected
            val tint = if (active) LogoGreen else cs.onSurface.copy(alpha = 0.38f)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelected(tab) }
                    .padding(top = 4.dp, bottom = 0.dp)
            ) {
                Icon(
                    imageVector = tabIcon(tab),
                    contentDescription = tab.label,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = tab.label,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    color = tint,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(if (active) LogoGreen else Color.Transparent)
                )
            }
        }
    }
}

private fun tabIcon(tab: AppRelationTab): androidx.compose.ui.graphics.vector.ImageVector =
    when (tab) {
        AppRelationTab.Overview -> Icons.Outlined.BarChart
        AppRelationTab.Records -> Icons.Outlined.Schedule
        AppRelationTab.Intent -> Icons.Outlined.GpsFixed
        AppRelationTab.Guard -> Icons.Outlined.Shield
    }

@Composable
fun IntentPoolSummaryCard(
    snapshot: IntentPoolSnapshot,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    val scopedSeconds = snapshot.totalMindfulSeconds
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cs.surface)
            .border(1.dp, cs.onSurface.copy(alpha = 0.06f), shape)
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Text(
            text = if (snapshot.timeScope == IntentPoolTimeScope.Today) "今日有意识使用"
            else "累积有意识使用",
            fontSize = 13.sp,
            color = cs.onSurface.copy(alpha = 0.45f)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = formatIntentPoolDuration(scopedSeconds),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = cs.onSurface.copy(alpha = 0.94f),
            letterSpacing = (-0.5).sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = buildString {
                append("${snapshot.totalEntryCount} 种意图")
                if (snapshot.categoryBreakdown.any { it.category != null }) {
                    val catCount = snapshot.categoryBreakdown.count { it.category != null }
                    append(" · $catCount 个分类")
                }
                if (snapshot.uncategorizedCount > 0) {
                    append(" · ${snapshot.uncategorizedCount} 条未分类")
                }
            },
            fontSize = 13.sp,
            color = cs.onSurface.copy(alpha = 0.52f),
            lineHeight = 18.sp
        )
    }
}

@Composable
fun IntentPoolScopeSortRow(
    timeScope: IntentPoolTimeScope,
    sort: IntentPoolSort,
    onTimeScopeChange: (IntentPoolTimeScope) -> Unit,
    onSortChange: (IntentPoolSort) -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IntentPoolChip(
                label = "全部",
                selected = timeScope == IntentPoolTimeScope.All,
                onClick = { onTimeScopeChange(IntentPoolTimeScope.All) }
            )
            IntentPoolChip(
                label = "今日",
                selected = timeScope == IntentPoolTimeScope.Today,
                onClick = { onTimeScopeChange(IntentPoolTimeScope.Today) }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IntentPoolChip(
                label = "按时长",
                selected = sort == IntentPoolSort.Duration,
                onClick = { onSortChange(IntentPoolSort.Duration) }
            )
            IntentPoolChip(
                label = "按次数",
                selected = sort == IntentPoolSort.Count,
                onClick = { onSortChange(IntentPoolSort.Count) }
            )
            IntentPoolChip(
                label = "按最近",
                selected = sort == IntentPoolSort.Recent,
                onClick = { onSortChange(IntentPoolSort.Recent) }
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "看看每种意图占用了多少注意力",
            fontSize = 12.sp,
            color = cs.onSurface.copy(alpha = 0.38f)
        )
    }
}

@Composable
fun IntentPoolCategoryChips(
    breakdown: List<CategoryBreakdown>,
    selectedFilterId: String?,
    onFilterSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (breakdown.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IntentPoolChip(
            label = "全部",
            selected = selectedFilterId == null,
            onClick = { onFilterSelected(null) }
        )
        breakdown.forEach { row ->
            val cat = row.category
            val label = if (cat == null) {
                "未分类 ${formatIntentPoolDurationShort(row.totalSeconds)}"
            } else {
                val prefix = cat.emoji?.let { "$it " }.orEmpty()
                "$prefix${cat.name} ${formatIntentPoolDurationShort(row.totalSeconds)}"
            }
            val filterId = cat?.id ?: IntentPoolRepository.UNCategorizedFilterId
            IntentPoolChip(
                label = label,
                selected = selectedFilterId == filterId,
                onClick = { onFilterSelected(filterId) }
            )
        }
    }
}

@Composable
fun IntentPoolItemRow(
    item: IntentPoolItem,
    timeScope: IntentPoolTimeScope,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    val scopedSeconds = when (timeScope) {
        IntentPoolTimeScope.All -> item.totalSeconds
        IntentPoolTimeScope.Today -> item.todaySeconds
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cs.surface)
            .border(1.dp, cs.onSurface.copy(alpha = 0.05f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.entry.displayName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = cs.onSurface.copy(alpha = 0.92f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append(formatIntentPoolDuration(scopedSeconds))
                        append(" · ${item.useCount} 次")
                        item.category?.let { cat ->
                            val prefix = cat.emoji?.let { "$it " }.orEmpty()
                            append(" · $prefix${cat.name}")
                        }
                    },
                    fontSize = 12.sp,
                    color = cs.onSurface.copy(alpha = 0.45f)
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = cs.onSurface.copy(alpha = 0.28f)
            )
        }
        if (item.shareOfMindful > 0.02f) {
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { item.shareOfMindful.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = LogoGreen.copy(alpha = 0.72f),
                trackColor = cs.onSurface.copy(alpha = 0.06f),
                strokeCap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun IntentPoolSectionHeader(
    category: IntentCategory?,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val title = if (category == null) {
        "未分类"
    } else {
        val prefix = category.emoji?.let { "$it " }.orEmpty()
        "$prefix${category.name}"
    }
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = cs.onSurface.copy(alpha = 0.40f),
        modifier = modifier.padding(top = 4.dp, bottom = 8.dp)
    )
}

@Composable
fun IntentPoolEmptyState(
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "意图池还是空的",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurface.copy(alpha = 0.72f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "下次打开时写下具体打算，\n这里会慢慢浮现你真实的用法。",
            fontSize = 14.sp,
            color = cs.onSurface.copy(alpha = 0.42f),
            lineHeight = 21.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun IntentPoolChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Text(
        text = label,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        color = if (selected) LogoGreen else cs.onSurface.copy(alpha = 0.62f),
        modifier = Modifier
            .clip(shape)
            .background(
                if (selected) LogoGreen.copy(alpha = 0.12f)
                else cs.onSurface.copy(alpha = 0.05f)
            )
            .border(
                width = 1.dp,
                color = if (selected) LogoGreen.copy(alpha = 0.28f) else Color.Transparent,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

fun formatIntentPoolDuration(seconds: Long): String {
    if (seconds <= 0L) return "0 分钟"
    val hours = TimeUnit.SECONDS.toHours(seconds)
    val minutes = (seconds % 3600) / 60
    return when {
        hours <= 0L -> "${minutes} 分钟"
        minutes <= 0L -> "${hours} 小时"
        else -> "${hours} 小时 ${minutes} 分"
    }
}

fun formatIntentPoolDurationShort(seconds: Long): String {
    if (seconds <= 0L) return "0分"
    return formatCompactDuration(seconds)
}

fun formatIntentPoolRelativeTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val now = System.currentTimeMillis()
    val dayMs = 24L * 60 * 60 * 1000
    val diffDays = ((now - timestamp) / dayMs).toInt()
    return when {
        diffDays <= 0 -> "今天"
        diffDays == 1 -> "昨天"
        diffDays < 7 -> "${diffDays} 天前"
        else -> SimpleDateFormat("M月d日", Locale.CHINESE).format(Date(timestamp))
    }
}

fun formatIntentPoolFirstSeen(timestamp: Long): String {
    if (timestamp <= 0L) return "—"
    return SimpleDateFormat("M月d日", Locale.CHINESE).format(Date(timestamp))
}

fun scopedSeconds(item: IntentPoolItem, timeScope: IntentPoolTimeScope): Long =
    when (timeScope) {
        IntentPoolTimeScope.All -> item.totalSeconds
        IntentPoolTimeScope.Today -> item.todaySeconds
    }
