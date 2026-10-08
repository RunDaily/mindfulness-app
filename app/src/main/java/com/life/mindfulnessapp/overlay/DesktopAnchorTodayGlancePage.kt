package com.life.mindfulnessapp.overlay

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.life.mindfulnessapp.util.AppUsageFormat

/**
 * Pulse 脚「今日旁路」一行 + 点开后的轻览页。
 * 主语仍是当前 App；这里只给监控合计一瞥，不把 Hub 搬进 App 内。
 */
@Composable
internal fun DeskPulseTodayRail(
    todayTotalSeconds: Long,
    todayEnterCount: Int,
    todayDismissCount: Int,
    accent: Color,
    muted: Color,
    ink: Color,
    line: Color,
    onOpen: () -> Unit,
) {
    val totalLabel = AppUsageFormat.totalDurationCompact(todayTotalSeconds)
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .height(0.6.dp)
                .background(line)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onOpen)
                .padding(top = 12.dp, bottom = 2.dp)
                .semantics { contentDescription = "今日监控概况" },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = "今日 · 监控中",
                    color = muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = buildString {
                        append("共 ")
                        append(totalLabel)
                        append(" · 守住 ")
                        append(todayDismissCount.coerceAtLeast(0))
                        append(" · 进入 ")
                        append(todayEnterCount.coerceAtLeast(0))
                    },
                    color = ink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            Text(
                text = "›",
                color = accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 10.dp)
            )
        }
    }
}

@Composable
internal fun DesktopAnchorTodayGlancePage(
    topApps: List<DesktopAnchorTopApp>,
    todayTotalSeconds: Long,
    todayEnterCount: Int,
    todayDismissCount: Int,
    currentPackageName: String,
    currentAppName: String,
    currentTodaySeconds: Long,
    currentOpenCount: Int,
    currentSessionSeconds: Long,
    currentLive: Boolean,
    isDarkTheme: Boolean,
    accent: Color,
    onBack: () -> Unit,
    onOpenToday: () -> Unit,
    onOpenHeartAnchor: () -> Unit,
) {
    val ink = if (isDarkTheme) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val muted = if (isDarkTheme) Color(0xFF8E8E93) else Color(0xFF6C6C70)
    val dim = if (isDarkTheme) Color(0xFF636366) else Color(0xFF8E8E93)
    val line = if (isDarkTheme) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f)
    val pkg = currentPackageName.trim()
    val rows = remember(
        topApps,
        pkg,
        currentAppName,
        currentTodaySeconds,
        currentOpenCount,
        currentLive,
        currentSessionSeconds,
    ) {
        buildTodayGlanceRows(
            topApps = topApps,
            currentPackageName = pkg,
            currentAppName = currentAppName,
            currentTodaySeconds = currentTodaySeconds,
            currentOpenCount = currentOpenCount,
            currentLive = currentLive,
            currentSessionSeconds = currentSessionSeconds,
        )
    }
    val restSeconds = (todayTotalSeconds - currentTodaySeconds).coerceAtLeast(0L)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "← ${currentAppName.ifBlank { "返回" }}",
                color = muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 2.dp, vertical = 2.dp)
            )
            Text(
                text = "今日",
                color = ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(modifier = Modifier.size(48.dp))
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            GlanceStat(
                value = AppUsageFormat.totalDurationCompact(todayTotalSeconds),
                label = "监控合计",
                accent = true,
                ink = ink,
                muted = muted,
                accentColor = accent,
            )
            GlanceStat(
                value = "${todayEnterCount.coerceAtLeast(0)}次",
                label = "进入",
                accent = false,
                ink = ink,
                muted = muted,
                accentColor = accent,
            )
            GlanceStat(
                value = "${todayDismissCount.coerceAtLeast(0)}次",
                label = "守住",
                accent = false,
                ink = ink,
                muted = muted,
                accentColor = accent,
            )
        }

        if (currentTodaySeconds > 0L || todayTotalSeconds > 0L) {
            Text(
                text = buildString {
                    append(currentAppName.ifBlank { "当前" })
                    append("占 ")
                    append(AppUsageFormat.totalDurationCompact(currentTodaySeconds))
                    if (restSeconds > 0L) {
                        append(" · 其余 ")
                        append(AppUsageFormat.totalDurationCompact(restSeconds))
                    }
                },
                color = muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "今天用过",
            color = muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        if (rows.isEmpty()) {
            Text(
                text = "还没有监控用量",
                color = dim,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                rows.forEachIndexed { index, row ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.6.dp)
                                .background(line.copy(alpha = 0.5f))
                        )
                    }
                    TodayGlanceAppRow(
                        row = row,
                        ink = ink,
                        dim = dim,
                        accent = accent,
                    )
                }
            }
        }

        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .height(0.6.dp)
                .background(line)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "打开心锚",
                color = muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onOpenHeartAnchor)
                    .padding(horizontal = 2.dp, vertical = 4.dp)
            )
            Text(
                text = "今日小票 →",
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onOpenToday)
                    .padding(horizontal = 2.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun GlanceStat(
    value: String,
    label: String,
    accent: Boolean,
    ink: Color,
    muted: Color,
    accentColor: Color,
) {
    Column {
        Text(
            text = value,
            color = if (accent) accentColor else ink,
            fontSize = 20.sp,
            fontWeight = if (accent) FontWeight.Normal else FontWeight.Light,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
        Text(
            text = label,
            color = muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

private data class TodayGlanceAppRowModel(
    val packageName: String,
    val appName: String,
    val totalSeconds: Long,
    val openCount: Int,
    val live: Boolean,
    val sessionSeconds: Long,
)

private fun buildTodayGlanceRows(
    topApps: List<DesktopAnchorTopApp>,
    currentPackageName: String,
    currentAppName: String,
    currentTodaySeconds: Long,
    currentOpenCount: Int,
    currentLive: Boolean,
    currentSessionSeconds: Long,
): List<TodayGlanceAppRowModel> {
    if (currentPackageName.isEmpty() && topApps.isEmpty()) return emptyList()
    val mapped = topApps
        .filter { it.totalSeconds > 0L || it.openCount > 0 }
        .map {
            TodayGlanceAppRowModel(
                packageName = it.packageName,
                appName = it.appName,
                totalSeconds = it.totalSeconds,
                openCount = it.openCount,
                live = it.packageName == currentPackageName && currentLive,
                sessionSeconds = if (it.packageName == currentPackageName) currentSessionSeconds else 0L,
            )
        }
        .toMutableList()
    val idx = mapped.indexOfFirst { it.packageName == currentPackageName }
    if (idx >= 0) {
        val old = mapped[idx]
        mapped[idx] = old.copy(
            appName = currentAppName.ifBlank { old.appName },
            totalSeconds = maxOf(old.totalSeconds, currentTodaySeconds),
            openCount = maxOf(old.openCount, currentOpenCount),
            live = currentLive,
            sessionSeconds = currentSessionSeconds,
        )
        if (idx != 0) {
            val cur = mapped.removeAt(idx)
            mapped.add(0, cur)
        }
    } else if (currentPackageName.isNotEmpty() &&
        (currentTodaySeconds > 0L || currentOpenCount > 0 || currentLive)
    ) {
        mapped.add(
            0,
            TodayGlanceAppRowModel(
                packageName = currentPackageName,
                appName = currentAppName.ifBlank { currentPackageName },
                totalSeconds = currentTodaySeconds,
                openCount = currentOpenCount,
                live = currentLive,
                sessionSeconds = currentSessionSeconds,
            )
        )
    }
    return mapped.take(6)
}

@Composable
private fun TodayGlanceAppRow(
    row: TodayGlanceAppRowModel,
    ink: Color,
    dim: Color,
    accent: Color,
) {
    val context = LocalContext.current
    val iconBitmap = remember(row.packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(row.packageName)
                .toBitmap(72, 72)
                .asImageBitmap()
        }.getOrNull()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Text(
                    text = row.appName.take(1),
                    color = ink,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.appName.ifBlank { "App" },
                    color = ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (row.live) {
                    Text(
                        text = "进行中",
                        color = accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
            Text(
                text = buildString {
                    append("进入 ")
                    append(row.openCount.coerceAtLeast(0))
                    if (row.live && row.sessionSeconds > 0L) {
                        append(" · 本次 ")
                        append(AppUsageFormat.sessionDuration(row.sessionSeconds))
                    }
                },
                color = dim,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text(
            text = AppUsageFormat.totalDurationCompact(row.totalSeconds),
            color = if (row.live) accent else ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}
