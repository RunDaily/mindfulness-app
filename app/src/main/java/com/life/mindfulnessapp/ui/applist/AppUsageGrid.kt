package com.life.mindfulnessapp.ui.applist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.domain.model.AppWeeklySystemUsage
import com.life.mindfulnessapp.domain.model.BatchPickAppRow
import com.life.mindfulnessapp.domain.model.BatchPickSortMode
import com.life.mindfulnessapp.ui.theme.CapabilityForm
import com.life.mindfulnessapp.ui.theme.CapabilityKind
import com.life.mindfulnessapp.ui.theme.CapabilityMark
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.theme.MonitorCapability
import com.life.mindfulnessapp.util.AppUsageFormat

const val APP_USAGE_GRID_COLUMNS = 4

@Composable
fun AppUsageSortRow(
    sortMode: BatchPickSortMode,
    usageLoading: Boolean,
    hasUsagePermission: Boolean,
    caption: String?,
    onSortModeChange: (BatchPickSortMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppUsageSortChip(
                label = "按时长",
                selected = sortMode == BatchPickSortMode.Duration,
                enabled = hasUsagePermission,
                onClick = { onSortModeChange(BatchPickSortMode.Duration) }
            )
            AppUsageSortChip(
                label = "按打开",
                selected = sortMode == BatchPickSortMode.Launches,
                enabled = hasUsagePermission,
                onClick = { onSortModeChange(BatchPickSortMode.Launches) }
            )
            if (usageLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(18.dp)
                        .align(Alignment.CenterVertically),
                    color = LogoGreen,
                    strokeWidth = 2.dp
                )
            }
        }
        Text(
            text = caption ?: when {
                !hasUsagePermission ->
                    "开启「使用情况访问」后可按时长 / 打开排序，并显示近 7 日日均"
                sortMode == BatchPickSortMode.Duration ->
                    "近 7 日日均（不含今天）· 加粗为当前排序维度"
                else ->
                    "近 7 日日均（不含今天）· 加粗为当前排序维度"
            },
            fontSize = 10.sp,
            color = cs.onSurface.copy(alpha = if (hasUsagePermission) 0.34f else 0.48f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun AppUsageSortChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label, fontSize = 12.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = LogoGreen.copy(alpha = 0.16f),
            selectedLabelColor = LogoGreen
        )
    )
}

@Composable
fun AppUsageGrid(
    rows: List<BatchPickAppRow>,
    columns: Int = APP_USAGE_GRID_COLUMNS,
    usageLoading: Boolean,
    hasUsagePermission: Boolean,
    sortMode: BatchPickSortMode = BatchPickSortMode.Duration,
    dimPredicate: (AppInfo) -> Boolean = { false },
    multiSelectMode: Boolean = false,
    selectedPackages: Set<String> = emptySet(),
    selectionAccent: Color = LogoGreen,
    onAppClick: ((AppInfo) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val gridRows = rows.chunked(columns)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        gridRows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row.forEach { item ->
                    Box(modifier = Modifier.weight(1f)) {
                        AppUsageGridCell(
                            row = item,
                            usageLoading = usageLoading,
                            hasUsagePermission = hasUsagePermission,
                            sortMode = sortMode,
                            dimmed = dimPredicate(item.app),
                            selected = multiSelectMode && item.app.packageName in selectedPackages,
                            selectionAccent = selectionAccent,
                            onClick = onAppClick?.let { click -> { click(item.app) } }
                        )
                    }
                }
                repeat(columns - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun AppUsageGridCell(
    row: BatchPickAppRow,
    usageLoading: Boolean,
    hasUsagePermission: Boolean,
    sortMode: BatchPickSortMode = BatchPickSortMode.Duration,
    dimmed: Boolean = false,
    selected: Boolean = false,
    selectionAccent: Color = LogoGreen,
    highlightCapability: CapabilityKind? = null,
    onClick: (() -> Unit)? = null
) {
    val app = row.app
    val cs = MaterialTheme.colorScheme
    val capabilityMarks = remember(app) { app.boundCapabilities() }
    val clickable = onClick != null && !dimmed

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(
                when {
                    dimmed -> Modifier.background(cs.onSurface.copy(alpha = 0.04f))
                    selected -> Modifier
                        .background(selectionAccent.copy(alpha = 0.12f))
                        .border(1.5.dp, selectionAccent.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    highlightCapability != null ->
                        Modifier.background(MonitorCapability.accent(highlightCapability).copy(alpha = 0.08f))
                    else -> Modifier
                }
            )
            .then(
                if (clickable) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(horizontal = 2.dp, vertical = 8.dp)
            .alpha(if (dimmed) 0.55f else 1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            AppIcon(
                drawable = app.icon,
                modifier = Modifier.size(46.dp)
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(selectionAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            } else if (app.isSystemDualRow) {
                Text(
                    text = "分身",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(LogoGreen.copy(alpha = 0.92f))
                        .padding(horizontal = 3.dp, vertical = 1.dp)
                )
            }
        }

        Box(
            modifier = Modifier.height(12.dp),
            contentAlignment = Alignment.Center
        ) {
            if (capabilityMarks.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    capabilityMarks.forEach { kind ->
                        CapabilityMark(
                            kind = kind,
                            form = CapabilityForm.Compact,
                            active = true,
                            tint = MonitorCapability.accent(kind),
                            size = 10.dp
                        )
                    }
                }
            }
        }

        Text(
            text = app.appName,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = cs.onSurface.copy(alpha = 0.78f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp,
            modifier = Modifier.fillMaxWidth()
        )

        if (hasUsagePermission) {
            AppUsageMetricsText(
                usage = row.usage,
                loading = usageLoading,
                highlightSortMode = sortMode
            )
        }
    }
}

@Composable
fun AppUsageMetricsText(
    usage: AppWeeklySystemUsage?,
    loading: Boolean,
    highlightSortMode: BatchPickSortMode? = null
) {
    val cs = MaterialTheme.colorScheme
    val muted = cs.onSurface.copy(alpha = 0.38f)
    val active = cs.onSurface.copy(alpha = 0.72f)
    if (loading && usage == null) {
        Text(
            text = "…",
            fontSize = 9.sp,
            color = muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        return
    }
    val stats = usage ?: AppWeeklySystemUsage.Zero
    val durationEmphasized = highlightSortMode == BatchPickSortMode.Duration
    val launchesEmphasized = highlightSortMode == BatchPickSortMode.Launches
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = AppUsageFormat.avgDailyDurationShort(stats.avgDailySeconds),
            fontSize = 9.sp,
            fontWeight = if (durationEmphasized) FontWeight.SemiBold else FontWeight.Normal,
            color = if (durationEmphasized) active else muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = AppUsageFormat.avgDailyLaunchesShort(stats.avgDailyLaunches),
            fontSize = 9.sp,
            fontWeight = if (launchesEmphasized) FontWeight.SemiBold else FontWeight.Normal,
            color = if (launchesEmphasized) active else muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun AppInfo.boundCapabilities(): List<CapabilityKind> {
    if (!isMonitored) return emptyList()
    return CapabilityKind.entries.filter { hasCapability(it) }
}
