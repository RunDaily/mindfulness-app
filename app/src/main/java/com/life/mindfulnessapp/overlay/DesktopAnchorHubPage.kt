package com.life.mindfulnessapp.overlay

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.life.mindfulnessapp.domain.model.PeriodLockPolicy
import com.life.mindfulnessapp.domain.model.ScheduleOrbPolicy
import kotlinx.coroutines.delay

private const val RankedAppLimit = 4

/** 生效中态：芥末金，与日程锁列表一致，不抢绿动作色。 */
private val ActiveSand = Color(0xFFC4A35A)

/**
 * 桌面 Hub：此刻（有才有）→ 今日（总时长 · 热力 · 四枚芯片）→ 离开（字）。
 */
@Composable
internal fun DesktopAnchorHubPage(
    topApps: List<DesktopAnchorTopApp>,
    hourlySeconds: LongArray,
    todayTotalSeconds: Long,
    activeLock: ScheduleOrbPolicy.ActiveGlance?,
    playingPackage: String?,
    isDarkTheme: Boolean,
    accent: Color,
    onOpenPulse: (String) -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenToday: () -> Unit,
    onOpenHeartAnchor: () -> Unit,
    onOpenPlayingApp: (String) -> Unit,
) {
    val ink = if (isDarkTheme) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val muted = if (isDarkTheme) Color(0xFF8E8E93) else Color(0xFF6C6C70)
    val dim = if (isDarkTheme) Color(0xFF636366) else Color(0xFF8E8E93)
    val line = if (isDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
    val ranked = remember(topApps) {
        topApps.filter { it.totalSeconds > 0L }.take(RankedAppLimit)
    }
    val playing = playingPackage?.trim().orEmpty()

    Column(modifier = Modifier.fillMaxWidth()) {
        if (activeLock != null) {
            ActiveLockStrip(
                active = activeLock,
                muted = muted,
                dim = dim,
                line = line,
                onOpen = onOpenSchedule,
            )
            Spacer(modifier = Modifier.height(11.dp))
        }

        if (playing.isNotEmpty()) {
            NowPlayingRow(
                packageName = playing,
                ink = ink,
                muted = muted,
                dim = dim,
                line = line,
                accent = accent,
                onOpen = { onOpenPlayingApp(playing) }
            )
            Spacer(modifier = Modifier.height(11.dp))
        }

        Text(
            text = buildString {
                append("今日  ")
                append(formatDeskMinutes(todayTotalSeconds))
            },
            color = muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.2.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        DayHeatStrip(hourlySeconds = hourlySeconds, accent = accent, dim = dim)

        if (ranked.isEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (todayTotalSeconds <= 0L) "还没有监控用量" else "今日暂无打开",
                color = dim,
                fontSize = 12.sp
            )
        } else {
            Spacer(modifier = Modifier.height(12.dp))
            RankedAppChips(
                apps = ranked,
                ink = ink,
                muted = muted,
                dim = dim,
                onOpenPulse = onOpenPulse
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "今天",
                color = muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onOpenToday)
                    .padding(horizontal = 2.dp, vertical = 4.dp)
                    .semantics { contentDescription = "今天" }
            )
            Text(
                text = "打开心锚",
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onOpenHeartAnchor)
                    .padding(horizontal = 2.dp, vertical = 4.dp)
            )
        }
    }
}

/**
 * 日程锁生效中：段名 · 时段 · 剩余 · 锁了谁。
 * Hub / 使用中 Pulse 共用，点开进日程编辑。
 */
@Composable
internal fun ActiveLockStrip(
    active: ScheduleOrbPolicy.ActiveGlance,
    muted: Color,
    dim: Color,
    line: Color,
    onOpen: () -> Unit,
    showDivider: Boolean = true,
) {
    var now by remember(active.planId) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active.planId) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000L)
        }
    }
    val remain = remember(active, now) {
        PeriodLockPolicy.remainingUnlockLabel(active.toPeriodWindow(), now)
    }
    val appsLine = remember(active.lockedApps, active.lockedAppOverflow, active.emptyScopeHint) {
        when {
            active.lockedApps.isNotEmpty() -> buildString {
                append(active.lockedApps.joinToString("、") { it.appName })
                if (active.lockedAppOverflow > 0) {
                    append(" 等")
                    append(active.lockedApps.size + active.lockedAppOverflow)
                    append("个")
                }
            }
            !active.emptyScopeHint.isNullOrBlank() -> active.emptyScopeHint
            else -> null
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onOpen)
                .padding(vertical = 2.dp)
                .semantics {
                    contentDescription = buildString {
                        append(active.title)
                        append(' ')
                        append(active.timeLabel)
                        append(' ')
                        append(remain)
                        if (!appsLine.isNullOrBlank()) {
                            append(' ')
                            append(appsLine)
                        }
                    }
                }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = active.title,
                    color = ActiveSand,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = remain,
                    color = dim,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Text(
                text = active.timeLabel,
                color = muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 3.dp)
            )
            if (active.lockedApps.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    active.lockedApps.forEach { app ->
                        LockAppIcon(packageName = app.packageName, appName = app.appName)
                    }
                    if (active.lockedAppOverflow > 0) {
                        Text(
                            text = "+${active.lockedAppOverflow}",
                            color = dim,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Text(
                    text = "锁着 $appsLine",
                    color = dim,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
            } else if (!appsLine.isNullOrBlank()) {
                Text(
                    text = "锁着 $appsLine",
                    color = dim,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Text(
                text = "查看日程",
                color = dim,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        if (showDivider) {
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.6.dp)
                    .background(line)
            )
        }
    }
}

@Composable
private fun LockAppIcon(packageName: String, appName: String) {
    val context = LocalContext.current
    val iconBitmap = remember(packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(packageName)
                .toBitmap(72, 72)
                .asImageBitmap()
        }.getOrNull()
    }
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap,
                contentDescription = appName,
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(5.dp))
            )
        } else {
            Text(
                text = appName.take(1),
                color = ActiveSand,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun NowPlayingRow(
    packageName: String,
    ink: Color,
    muted: Color,
    dim: Color,
    line: Color,
    accent: Color,
    onOpen: () -> Unit,
) {
    val context = LocalContext.current
    val label = remember(packageName) { overlayAppLabel(context, packageName) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "${label.ifBlank { "应用" }}后台在播" },
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpinningAppIconDisk(
                packageName = packageName,
                size = 20.dp,
                musicPlaying = false
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp, end = 8.dp)
            ) {
                Text(
                    text = label.ifBlank { "正在播放" },
                    color = ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "后台在播",
                    color = muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
            PlayingEqualizerMark(color = accent, modifier = Modifier.padding(end = 6.dp))
            Text(
                text = "打开",
                color = dim,
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onOpen)
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.6.dp)
                .background(line)
        )
    }
}

@Composable
internal fun DayHeatStrip(
    hourlySeconds: LongArray,
    accent: Color,
    dim: Color,
) {
    val buckets = remember(hourlySeconds) {
        IntArray(12) { i ->
            val a = hourlySeconds.getOrElse(i * 2) { 0L }
            val b = hourlySeconds.getOrElse(i * 2 + 1) { 0L }
            (a + b).coerceAtLeast(0L).toInt()
        }
    }
    val peak = buckets.maxOrNull()?.coerceAtLeast(1) ?: 1
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .semantics { contentDescription = "监控用量节奏" },
        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        buckets.forEach { value ->
            val ratio = (value.toFloat() / peak.toFloat()).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(((2.5f + 11.5f * ratio).dp).coerceAtLeast(2.5.dp))
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        if (value > 0) accent.copy(alpha = 0.18f + 0.42f * ratio)
                        else dim.copy(alpha = 0.10f)
                    )
            )
        }
    }
}

@Composable
private fun RankedAppChips(
    apps: List<DesktopAnchorTopApp>,
    ink: Color,
    muted: Color,
    dim: Color,
    onOpenPulse: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "今日用量前 ${apps.size} 个" },
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        apps.forEachIndexed { index, app ->
            RankedAppChip(
                app = app,
                emphasize = index == 0,
                ink = ink,
                muted = muted,
                onOpenPulse = onOpenPulse
            )
        }
        Text(
            text = "分",
            color = dim,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun RankedAppChip(
    app: DesktopAnchorTopApp,
    emphasize: Boolean,
    ink: Color,
    muted: Color,
    onOpenPulse: (String) -> Unit,
) {
    val context = LocalContext.current
    val iconBitmap = remember(app.packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(app.packageName)
                .toBitmap(72, 72)
                .asImageBitmap()
        }.getOrNull()
    }
    val minutes = formatChipMinutes(app.totalSeconds)
    Row(
        modifier = Modifier
            .widthIn(max = 72.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable { onOpenPulse(app.packageName) }
            .padding(vertical = 2.dp)
            .semantics {
                contentDescription = "${app.appName} ${formatDeskMinutes(app.totalSeconds)}"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
            } else {
                Text(
                    text = app.appName.take(1),
                    color = ink,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text(
            text = minutes,
            color = if (emphasize) ink else muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

internal fun launchOverlayPackage(context: Context, packageName: String) {
    val pkg = packageName.trim()
    if (pkg.isEmpty()) return
    val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    runCatching { context.startActivity(intent) }
}

private fun formatDeskMinutes(totalSeconds: Long): String {
    val m = totalSeconds.coerceAtLeast(0L) / 60L
    return when {
        m <= 0L -> if (totalSeconds > 0L) "<1分" else "0分"
        m < 60L -> "${m}分"
        else -> {
            val h = m / 60L
            val rm = m % 60L
            if (rm == 0L) "${h}时" else "${h}时${rm}分"
        }
    }
}

/** 芯片旁数字：单位「分」挂在整排末尾。 */
private fun formatChipMinutes(totalSeconds: Long): String {
    val m = totalSeconds.coerceAtLeast(0L) / 60L
    return when {
        m <= 0L -> if (totalSeconds > 0L) "<1" else "0"
        else -> m.toString()
    }
}
