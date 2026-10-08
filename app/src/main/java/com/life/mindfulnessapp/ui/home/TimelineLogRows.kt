package com.life.mindfulnessapp.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import com.life.mindfulnessapp.R
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.domain.model.ComparePolicy
import com.life.mindfulnessapp.domain.model.HeldAwayOrdinalIndex
import com.life.mindfulnessapp.domain.model.IntentKind
import com.life.mindfulnessapp.domain.model.PeriodAppStat
import com.life.mindfulnessapp.domain.model.PeriodSectionStats
import com.life.mindfulnessapp.domain.model.TimelineDisplayItem
import com.life.mindfulnessapp.domain.model.TimelineEvent
import com.life.mindfulnessapp.ui.common.CompareTierColors
import com.life.mindfulnessapp.ui.theme.HeatmapNeutral
import com.life.mindfulnessapp.ui.theme.LogoGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 首页 / 历史：扁平流水记录。
 * 左紧凑时间 · 图标+名称 · 主行意图/动作 · 副行时长；
 * 右侧为对照入口 / 对照结果 badge / 备注。
 */

private val LogTimeColWidth = 40.dp
private val LogIconSize = 20.dp
private val LogIdentityColWidth = 40.dp
private val LogRowPadV = 8.dp
private val LogColGap = 10.dp
private val LogBadgeRadius = 4.dp

/** 条目进出语义（合并簇等仍用） */
internal enum class LogMotionKind {
    Enter,
    Leave,
    Blocked,
    Quiet
}

/** 时段分段标题：段名 + 钟点范围 + 可选关键统计 */
@Composable
internal fun TimelinePeriodHeader(
    label: String,
    onSurface: Color,
    range: String? = null,
    stats: PeriodSectionStats? = null,
    iconMap: Map<String, AppInfo> = emptyMap()
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = onSurface.copy(alpha = 0.48f),
                letterSpacing = 0.4.sp
            )
            if (!range.isNullOrBlank()) {
                Text(
                    text = range,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    color = onSurface.copy(alpha = 0.28f),
                    letterSpacing = 0.sp
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(onSurface.copy(alpha = 0.08f))
            )
            if (stats != null && stats.durationSeconds > 0L) {
                Text(
                    text = formatDurationLog(stats.durationSeconds) ?: "",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = onSurface.copy(alpha = 0.36f),
                    maxLines = 1
                )
            }
        }
        if (stats != null && stats.hasSignal) {
            PeriodStatsRow(
                stats = stats,
                iconMap = iconMap,
                onSurface = onSurface
            )
        }
    }
}

@Composable
private fun PeriodStatsRow(
    stats: PeriodSectionStats,
    iconMap: Map<String, AppInfo>,
    onSurface: Color
) {
    val apps = stats.apps.take(3)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        apps.forEach { app ->
            PeriodAppStatChip(
                stat = app,
                appInfo = iconMap[app.packageName],
                onSurface = onSurface
            )
        }
        if (stats.apps.size > 3) {
            Text(
                text = "等${stats.apps.size}个",
                fontSize = 10.sp,
                color = onSurface.copy(alpha = 0.28f)
            )
        }
    }
}

@Composable
private fun PeriodAppStatChip(
    stat: PeriodAppStat,
    appInfo: AppInfo?,
    onSurface: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.widthIn(max = 200.dp)
    ) {
        TimelineLogAppIcon(
            appInfo = appInfo,
            appName = stat.appName,
            dimmed = false,
            onSurface = onSurface,
            size = 14.dp
        )
        Text(
            text = stat.appName,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = onSurface.copy(alpha = 0.55f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 56.dp)
        )
        if (stat.enters > 0) {
            MotionCountMark(
                kind = LogMotionKind.Enter,
                count = stat.enters,
                onSurface = onSurface
            )
        }
        if (stat.leaves > 0) {
            MotionCountMark(
                kind = LogMotionKind.Leave,
                count = stat.leaves,
                onSurface = onSurface
            )
        }
    }
}

@Composable
internal fun MotionCountMark(
    kind: LogMotionKind,
    count: Int,
    onSurface: Color,
    compact: Boolean = true
) {
    val tint = when (kind) {
        LogMotionKind.Enter -> LogoGreen.copy(alpha = 0.78f)
        LogMotionKind.Leave -> HeatmapNeutral.copy(alpha = 0.78f)
        LogMotionKind.Blocked -> onSurface.copy(alpha = 0.45f)
        LogMotionKind.Quiet -> onSurface.copy(alpha = 0.28f)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        LogMotionGlyph(
            kind = kind,
            tint = tint,
            size = if (compact) 10.dp else 12.dp
        )
        Text(
            text = count.toString(),
            fontSize = if (compact) 11.sp else 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = tint,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun LogMotionGlyph(
    kind: LogMotionKind,
    tint: Color,
    size: Dp
) {
    when (kind) {
        LogMotionKind.Enter -> {
            Icon(
                painter = painterResource(R.drawable.ic_leave_extract),
                contentDescription = "进入使用",
                tint = tint,
                modifier = Modifier
                    .size(size)
                    .scale(scaleX = -1f, scaleY = 1f)
            )
        }
        LogMotionKind.Leave -> {
            Icon(
                painter = painterResource(R.drawable.ic_leave_extract),
                contentDescription = "门外离开",
                tint = tint,
                modifier = Modifier.size(size)
            )
        }
        LogMotionKind.Blocked -> {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "拦住",
                tint = tint,
                modifier = Modifier.size(size)
            )
        }
        LogMotionKind.Quiet -> Unit
    }
}

/**
 * 同段内长空档（≥30 分）：无字加高留白，不读文案。
 * 时长越大略增高，但封顶，避免一天被掏空。
 */
@Composable
internal fun TimelineGapRow(
    seconds: Long,
    @Suppress("UNUSED_PARAMETER") onSurface: Color
) {
    if (seconds < 30L * 60L) return
    val extra = when {
        seconds >= 3L * 60L * 60L -> 28.dp
        seconds >= 60L * 60L -> 20.dp
        else -> 12.dp
    }
    Spacer(modifier = Modifier.height(extra))
}

@Composable
internal fun TimelineDisplayNode(
    item: TimelineDisplayItem,
    isLast: Boolean,
    iconMap: Map<String, AppInfo>,
    onRecordEdit: (TimelineEvent.UsageEvent, RecordEditFocus) -> Unit,
    highlightRecordId: Long? = null,
    forceExpandMerged: Boolean = false,
    onHighlightDone: () -> Unit = {},
    realtimeSeconds: Long? = null,
    editable: Boolean = true,
    showAppIdentity: Boolean = true,
    /** 副行是否展示结束原因（到点 / 息屏结束等）；时长与对照结果不受影响 */
    showEndReason: Boolean = false,
    heldAwayIndex: HeldAwayOrdinalIndex = HeldAwayOrdinalIndex(),
    cardBg: Color,
    onSurface: Color,
    @Suppress("UNUSED_PARAMETER") outline: Color
) {
    when (item) {
        is TimelineDisplayItem.Single -> when (val event = item.event) {
            is TimelineEvent.UsageEvent -> UsageLogEntry(
                event = event,
                appInfo = iconMap[event.packageName],
                cardBg = cardBg,
                onSurface = onSurface,
                isHighlighted = event.recordId == highlightRecordId,
                onHighlightDone = onHighlightDone,
                realtimeSeconds = realtimeSeconds,
                showAppIdentity = showAppIdentity,
                showEndReason = showEndReason,
                heldOrdinal = heldAwayIndex.ordinalFor(event.recordId, event.packageName),
                editable = editable,
                onRecordEdit = onRecordEdit
            )
            is TimelineEvent.LimitResetEvent -> LimitResetLogEntry(
                event = event,
                appInfo = iconMap[event.packageName],
                cardBg = cardBg,
                onSurface = onSurface,
                showAppIdentity = showAppIdentity
            )
        }
        is TimelineDisplayItem.MergedCluster -> MergedClusterLogEntry(
            cluster = item,
            iconMap = iconMap,
            cardBg = cardBg,
            onSurface = onSurface,
            forceExpand = forceExpandMerged,
            highlightRecordId = highlightRecordId,
            onHighlightDone = onHighlightDone,
            showAppIdentity = showAppIdentity,
            heldAwayIndex = heldAwayIndex
        )
    }
}

@Composable
internal fun TimelineEventNode(
    event: TimelineEvent,
    isLast: Boolean,
    iconMap: Map<String, AppInfo>,
    onRecordEdit: (TimelineEvent.UsageEvent, RecordEditFocus) -> Unit,
    isHighlighted: Boolean = false,
    onHighlightDone: () -> Unit = {},
    realtimeSeconds: Long? = null,
    cardBg: Color,
    onSurface: Color,
    outline: Color
) {
    TimelineDisplayNode(
        item = TimelineDisplayItem.Single(event),
        isLast = isLast,
        iconMap = iconMap,
        onRecordEdit = onRecordEdit,
        highlightRecordId = (event as? TimelineEvent.UsageEvent)?.recordId?.takeIf { isHighlighted },
        onHighlightDone = onHighlightDone,
        realtimeSeconds = realtimeSeconds,
        cardBg = cardBg,
        onSurface = onSurface,
        outline = outline
    )
}

@Composable
internal fun TimelineConnector(
    item: TimelineDisplayItem,
    isLast: Boolean,
    outline: Color,
    quiet: Boolean = false,
    heldAway: Boolean = false,
    ongoing: Boolean = false
) = Unit

@Composable
internal fun TimelineConnector(
    event: TimelineEvent,
    isLast: Boolean,
    outline: Color
) = Unit

@Composable
internal fun UsageEventCard(
    event: TimelineEvent.UsageEvent,
    appInfo: AppInfo? = null,
    cardBg: Color,
    onSurface: Color,
    isHighlighted: Boolean = false,
    onHighlightDone: () -> Unit = {},
    realtimeSeconds: Long? = null,
    showAppIdentity: Boolean = true,
    showEndReason: Boolean = false,
    onClick: (() -> Unit)? = null,
    onCompareClick: (() -> Unit)? = null,
    onNoteChipClick: (() -> Unit)? = null
) {
    val editable = onClick != null || onCompareClick != null || onNoteChipClick != null
    UsageLogEntry(
        event = event,
        appInfo = appInfo,
        cardBg = cardBg,
        onSurface = onSurface,
        isHighlighted = isHighlighted,
        onHighlightDone = onHighlightDone,
        realtimeSeconds = realtimeSeconds,
        showAppIdentity = showAppIdentity,
        showEndReason = showEndReason,
        heldOrdinal = null,
        editable = editable,
        onRecordEdit = { _, focus ->
            when (focus) {
                RecordEditFocus.Compare -> onCompareClick?.invoke() ?: onClick?.invoke()
                RecordEditFocus.Note -> onNoteChipClick?.invoke() ?: onClick?.invoke()
            }
        }
    )
}

@Composable
internal fun SeedUsageCard(
    event: TimelineEvent.UsageEvent,
    appInfo: AppInfo? = null,
    cardBg: Color,
    onSurface: Color
) = UsageEventCard(event, appInfo, cardBg, onSurface)

@Composable
internal fun InterceptedQuitCard(
    event: TimelineEvent.UsageEvent,
    appInfo: AppInfo? = null,
    cardBg: Color,
    onSurface: Color
) = UsageEventCard(event, appInfo, cardBg, onSurface)

@Composable
internal fun UsageDetailCard(
    event: TimelineEvent.UsageEvent,
    appInfo: AppInfo? = null,
    cardBg: Color,
    onSurface: Color,
    isHighlighted: Boolean = false,
    onHighlightDone: () -> Unit = {},
    realtimeSeconds: Long? = null,
    onClick: () -> Unit = {}
) = UsageEventCard(
    event = event,
    appInfo = appInfo,
    cardBg = cardBg,
    onSurface = onSurface,
    isHighlighted = isHighlighted,
    onHighlightDone = onHighlightDone,
    realtimeSeconds = realtimeSeconds,
    onClick = onClick
)

// ── 单条使用记录：扁平流水 ────────────────────────────────────────────────────

@Composable
private fun UsageLogEntry(
    event: TimelineEvent.UsageEvent,
    appInfo: AppInfo?,
    cardBg: Color,
    onSurface: Color,
    isHighlighted: Boolean,
    onHighlightDone: () -> Unit,
    realtimeSeconds: Long?,
    showAppIdentity: Boolean,
    showEndReason: Boolean,
    heldOrdinal: Int?,
    editable: Boolean,
    onRecordEdit: (TimelineEvent.UsageEvent, RecordEditFocus) -> Unit
) {
    LaunchedEffect(isHighlighted) {
        if (isHighlighted) {
            kotlinx.coroutines.delay(2400)
            onHighlightDone()
        }
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeStr = remember(event.startTime) { timeFormat.format(Date(event.startTime)) }

    val mindfulnessTier = event.mindfulnessLevel
        ?.takeIf { UsageRecordEntity.MindfulnessLevel.isValid(it) }
    val compareDone = mindfulnessTier != null
    val noteText = event.note?.trim()?.takeIf { it.isNotEmpty() }
    val canEdit = editable && event.hasIntentGate && !event.isGateQuit &&
        !event.isPositiveExit && !event.isSeed && !event.isOngoing
    val effectiveDuration = when {
        event.isOngoing ->
            realtimeSeconds ?: ((System.currentTimeMillis() - event.startTime) / 1000L)
        else -> event.durationSeconds
    }
    val meetsCompare = ComparePolicy.shouldOfferInlineCompare(
        hasIntentGate = event.hasIntentGate,
        purpose = event.purpose,
        durationSeconds = effectiveDuration,
        compareEnabled = event.compareEnabled,
        compareMinMinutes = event.compareMinMinutes,
        intentKind = event.intentKind
    )
    val pendingCompare = canEdit && !compareDone && meetsCompare
    val pendingNote = canEdit && compareDone && noteText == null

    val content = usageLogContent(
        event = event,
        showAppIdentity = showAppIdentity,
        showEndReason = showEndReason,
        heldOrdinal = heldOrdinal,
        realtimeSeconds = realtimeSeconds
    )

    val onCompareClick: (() -> Unit)? =
        if (pendingCompare) ({ onRecordEdit(event, RecordEditFocus.Compare) }) else null
    val onNoteClick: (() -> Unit)? =
        if (canEdit && compareDone) ({ onRecordEdit(event, RecordEditFocus.Note) }) else null

    val seed = event.isSeed
    val subtitleColor = when {
        content.subtitleEmphasized -> LogoGreen.copy(alpha = 0.88f)
        seed -> onSurface.copy(alpha = 0.32f)
        else -> onSurface.copy(alpha = 0.38f)
    }

    LogEntryCard(
        timeStr = timeStr,
        cardBg = cardBg,
        onSurface = onSurface,
        isHighlighted = isHighlighted,
        dimmed = seed,
        showAppIdentity = showAppIdentity,
        appInfo = appInfo,
        appName = event.appName,
        title = content.title,
        subtitle = content.subtitle,
        titleColor = when {
            seed -> onSurface.copy(alpha = 0.48f)
            else -> onSurface.copy(alpha = 0.88f)
        },
        subtitleColor = subtitleColor,
        trailing = LogEntryTrailing(
            compareAction = onCompareClick != null,
            onCompareClick = onCompareClick,
            mindfulnessLevel = mindfulnessTier,
            intentKind = event.intentKind,
            noteText = noteText,
            pendingNote = pendingNote,
            onNoteClick = onNoteClick
        )
    )
}

/** 右侧：对照 CTA / 对照 badge / 备注 */
private data class LogEntryTrailing(
    val compareAction: Boolean = false,
    val onCompareClick: (() -> Unit)? = null,
    val mindfulnessLevel: Int? = null,
    val intentKind: IntentKind? = null,
    val noteText: String? = null,
    val pendingNote: Boolean = false,
    val onNoteClick: (() -> Unit)? = null
)

/**
 * 主行：意图 / 动作；副行：时长 ·（可选结束原因）
 * 对照结果与备注改走右侧 trailing。
 */
private data class UsageLogContent(
    val title: String,
    val subtitle: String,
    val subtitleEmphasized: Boolean = false
)

private fun usageLogContent(
    event: TimelineEvent.UsageEvent,
    showAppIdentity: Boolean,
    showEndReason: Boolean,
    heldOrdinal: Int?,
    realtimeSeconds: Long?
): UsageLogContent {
    val durationText = when {
        event.isGateQuit || event.isPositiveExit -> null
        else -> durationLogLabel(event, realtimeSeconds)
    }
    val intentText = event.intentLine

    fun endReasonBit(): String? {
        if (!showEndReason || event.isOngoing) return null
        val kind = UsageRecordEntity.EndReason.displayKind(event.endReason)
        return when (kind) {
            UsageRecordEntity.EndReason.DisplayKind.MANUAL -> null
            UsageRecordEntity.EndReason.DisplayKind.LIMIT -> when (event.endReason) {
                UsageRecordEntity.EndReason.PERIOD_LOCK -> "时段锁拦住"
                else -> UsageRecordEntity.EndReason.displayKindLabel(event.endReason) ?: "到点"
            }
            UsageRecordEntity.EndReason.DisplayKind.INTERRUPT ->
                event.softEndReasonLabel
                    ?: UsageRecordEntity.EndReason.displayKindLabel(event.endReason)
                    ?: "中断"
            else -> null
        }
    }

    when {
        event.isPositiveExit -> {
            val title = event.purpose?.trim()?.takeIf { it.isNotEmpty() } ?: "去做了"
            val kind = event.positiveExitKind?.kindLabel
            val bits = ArrayList<String>(3)
            if (!showAppIdentity) bits += event.appName
            if (kind != null) bits += kind
            event.note?.trim()?.takeIf { it.isNotEmpty() }?.let { bits += it }
            return UsageLogContent(
                title = "去做了 · $title",
                subtitle = bits.joinToString(" · ").ifEmpty { "—" }
            )
        }
        event.isGateQuit -> {
            val title = if (event.isGateDismissToOwnApp) "到了心锚" else "离开了"
            val bits = ArrayList<String>(2)
            if (!showAppIdentity) bits += event.appName
            heldOrdinal?.takeIf { it > 0 }?.let { bits += "第${it}次" }
            return UsageLogContent(
                title = title,
                subtitle = bits.joinToString(" · ").ifEmpty { "—" }
            )
        }
        event.isSeed -> {
            return UsageLogContent(title = "加入前", subtitle = durationText ?: "—")
        }
        event.endReason == UsageRecordEntity.EndReason.PERIOD_LOCK &&
            !event.isOngoing &&
            event.durationSeconds <= 0L -> {
            return UsageLogContent(title = "时段锁拦住", subtitle = "—")
        }
        else -> {
            val title = intentText ?: "进入使用"
            val bits = ArrayList<String>(4)
            bits += (durationText ?: "—")
            endReasonBit()?.let { bits += it }
            if (event.isOngoing) bits += "进行中"
            return UsageLogContent(
                title = title,
                subtitle = bits.joinToString(" · "),
                subtitleEmphasized = event.isOngoing
            )
        }
    }
}

private fun durationLogLabel(
    event: TimelineEvent.UsageEvent,
    realtimeSeconds: Long?
): String? {
    val sec = when {
        event.isOngoing ->
            realtimeSeconds ?: ((System.currentTimeMillis() - event.startTime) / 1000L)
        event.durationSeconds > 0L -> event.durationSeconds
        else -> return null
    }
    val base = formatDurationLog(sec) ?: return null
    val over = event.sessionOvertimeSeconds?.takeIf { it > 0L }?.let { formatDurationLog(it) }
    return if (over != null) "$base+$over" else base
}

internal fun formatDurationLog(seconds: Long): String? {
    if (seconds <= 0L) return null
    if (seconds < 60L) return "${seconds} 秒"
    val m = seconds / 60L
    val r = seconds % 60L
    if (m < 60L) return if (r == 0L) "${m} 分钟" else "${m} 分 ${r} 秒"
    val h = m / 60L
    val rm = m % 60L
    return if (rm == 0L) "${h} 时" else "${h} 时 ${rm} 分"
}

// ── 限额重设 ─────────────────────────────────────────────────────────────────

@Composable
private fun LimitResetLogEntry(
    event: TimelineEvent.LimitResetEvent,
    appInfo: AppInfo?,
    cardBg: Color,
    onSurface: Color,
    showAppIdentity: Boolean
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeStr = remember(event.resetTime) { timeFormat.format(Date(event.resetTime)) }
    val change = buildString {
        if (event.dailyChanged || !event.weeklyChanged) {
            append("每日 ${event.oldDailyLimitMinutes}→${event.newDailyLimitMinutes}分")
        }
        if (event.weeklyChanged) {
            if (isNotEmpty()) append(" · ")
            append("每周 ${event.oldWeeklyLimitMinutes}→${event.newWeeklyLimitMinutes}分")
        }
    }

    LogEntryCard(
        timeStr = timeStr,
        cardBg = cardBg,
        onSurface = onSurface,
        isHighlighted = false,
        dimmed = true,
        showAppIdentity = showAppIdentity,
        appInfo = appInfo,
        appName = event.appName,
        title = "调整限制",
        subtitle = if (change.isNotEmpty()) change else "—",
        titleColor = onSurface.copy(alpha = 0.55f),
        subtitleColor = onSurface.copy(alpha = 0.36f)
    )
}

// ── 守住合并簇 ───────────────────────────────────────────────────────────────

@Composable
private fun MergedClusterLogEntry(
    cluster: TimelineDisplayItem.MergedCluster,
    iconMap: Map<String, AppInfo>,
    cardBg: Color,
    onSurface: Color,
    forceExpand: Boolean,
    highlightRecordId: Long?,
    onHighlightDone: () -> Unit,
    showAppIdentity: Boolean,
    heldAwayIndex: HeldAwayOrdinalIndex
) {
    var expanded by remember(cluster.key) { mutableStateOf(false) }
    LaunchedEffect(forceExpand) {
        if (forceExpand) expanded = true
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeStr = remember(cluster.timeMs) { timeFormat.format(Date(cluster.timeMs)) }
    val distinctApps = remember(cluster.key) { cluster.events.distinctBy { it.packageName } }
    val allToOwn = remember(cluster.key) {
        cluster.events.isNotEmpty() && cluster.events.all { it.isGateDismissToOwnApp }
    }
    val heldVerb = if (allToOwn && !cluster.isMixedApps) "到了心锚" else "离开了"
    val rangeLabel = remember(cluster.key, heldAwayIndex) {
        heldAwayIndex.rangeLabelFor(cluster.events)
    }
    val subtitle = buildList {
        if (showAppIdentity && cluster.isMixedApps) {
            val names = distinctApps.take(2).joinToString("、") { it.appName }
            val extra = distinctApps.size - 2
            add(if (extra > 0) "$names 等" else names)
        }
        add("${cluster.events.size}次")
        rangeLabel?.let { add(it) }
    }.joinToString(" · ").ifEmpty { "—" }
    val highlightBg = if (highlightRecordId != null && cluster.containsRecordId(highlightRecordId)) {
        LogoGreen.copy(alpha = 0.08f).compositeOver(cardBg)
    } else {
        Color.Transparent
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            .background(highlightBg)
            .clickable { expanded = !expanded }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = LogRowPadV),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LogColGap)
        ) {
            Text(
                text = timeStr,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                letterSpacing = (-0.6).sp,
                color = onSurface.copy(alpha = 0.40f),
                modifier = Modifier.width(LogTimeColWidth)
            )
            if (showAppIdentity) {
                if (cluster.isMixedApps) {
                    MergedClusterAppMarks(apps = distinctApps, iconMap = iconMap, onSurface = onSurface)
                } else {
                    TimelineLogAppIdentity(
                        appInfo = iconMap[cluster.packageName],
                        appName = cluster.appName,
                        dimmed = false,
                        onSurface = onSurface
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = heldVerb,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = onSurface.copy(alpha = 0.88f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = onSurface.copy(alpha = 0.38f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                tint = onSurface.copy(alpha = 0.28f),
                modifier = Modifier.size(16.dp)
            )
        }

        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = LogTimeColWidth + if (showAppIdentity) (LogIdentityColWidth + 10.dp) else 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                cluster.events.forEach { child ->
                    val childTime = remember(child.startTime) {
                        timeFormat.format(Date(child.startTime))
                    }
                    val childHighlight = child.recordId == highlightRecordId
                    LaunchedEffect(childHighlight) {
                        if (childHighlight) {
                            kotlinx.coroutines.delay(2400)
                            onHighlightDone()
                        }
                    }
                    val childBits = buildList {
                        add(if (child.isGateDismissToOwnApp) "到了心锚" else "离开了")
                        heldAwayIndex.ordinalFor(child.recordId, child.packageName)
                            ?.takeIf { it > 0 }
                            ?.let { add("第${it}次") }
                        if (showAppIdentity && cluster.isMixedApps) add(child.appName)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (childHighlight) {
                                    Modifier.background(LogoGreen.copy(alpha = 0.08f))
                                } else Modifier
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = childTime,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = onSurface.copy(alpha = 0.32f)
                        )
                        Text(
                            text = childBits.joinToString(" · "),
                            fontSize = 13.sp,
                            color = onSurface.copy(alpha = 0.45f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = onSurface.copy(alpha = 0.08f)
        )
    }
}

@Composable
private fun MergedClusterAppMarks(
    apps: List<TimelineEvent.UsageEvent>,
    iconMap: Map<String, AppInfo>,
    onSurface: Color
) {
    val shown = apps.take(3)
    if (shown.isEmpty()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy((-8).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        shown.forEachIndexed { index, event ->
            Box(
                modifier = Modifier
                    .zIndex((shown.size - index).toFloat())
                    .border(1.5.dp, onSurface.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
            ) {
                TimelineLogAppIcon(
                    appInfo = iconMap[event.packageName],
                    appName = event.appName,
                    dimmed = true,
                    onSurface = onSurface,
                    size = 20.dp
                )
            }
        }
    }
}

@Composable
private fun LogEntryCard(
    timeStr: String,
    cardBg: Color,
    onSurface: Color,
    isHighlighted: Boolean,
    dimmed: Boolean,
    showAppIdentity: Boolean,
    appInfo: AppInfo?,
    appName: String,
    title: String,
    subtitle: String,
    titleColor: Color,
    subtitleColor: Color,
    trailing: LogEntryTrailing = LogEntryTrailing()
) {
    val bg = when {
        isHighlighted -> LogoGreen.copy(alpha = 0.08f).compositeOver(cardBg)
        else -> Color.Transparent
    }
    val hasTrailing = trailing.compareAction ||
        trailing.mindfulnessLevel != null ||
        trailing.pendingNote ||
        trailing.noteText != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = LogRowPadV),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LogColGap)
        ) {
            // 时间：固定列宽，与内容区垂直居中对齐
            Text(
                text = timeStr,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                letterSpacing = (-0.6).sp,
                textAlign = TextAlign.Start,
                color = when {
                    dimmed -> onSurface.copy(alpha = 0.26f)
                    else -> onSurface.copy(alpha = 0.40f)
                },
                modifier = Modifier.width(LogTimeColWidth)
            )
            if (showAppIdentity) {
                TimelineLogAppIdentity(
                    appInfo = appInfo,
                    appName = appName,
                    dimmed = dimmed,
                    onSurface = onSurface
                )
            }
            // 主文案：意图 + 时长，占满中间
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = subtitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (hasTrailing) {
                LogEntryTrailingColumn(
                    trailing = trailing,
                    onSurface = onSurface
                )
            }
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = onSurface.copy(alpha = 0.08f)
        )
    }
}

@Composable
private fun LogEntryTrailingColumn(
    trailing: LogEntryTrailing,
    onSurface: Color
) {
    val isDark = onSurface.luminance() > 0.5f
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.widthIn(max = 88.dp)
    ) {
        when {
            trailing.compareAction && trailing.onCompareClick != null -> {
                Text(
                    text = "对照",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LogoGreen.copy(alpha = 0.92f),
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .clickable(onClick = trailing.onCompareClick)
                        .padding(horizontal = 2.dp, vertical = 2.dp)
                )
            }
            trailing.mindfulnessLevel != null -> {
                MindfulnessTierBadge(
                    level = trailing.mindfulnessLevel,
                    isDark = isDark,
                    intentKind = trailing.intentKind,
                    onClick = trailing.onNoteClick
                )
            }
        }
        when {
            trailing.noteText != null -> {
                Text(
                    text = trailing.noteText,
                    fontSize = 10.sp,
                    color = onSurface.copy(alpha = 0.36f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .widthIn(max = 88.dp)
                        .then(
                            if (trailing.onNoteClick != null) {
                                Modifier.clickable(onClick = trailing.onNoteClick)
                            } else Modifier
                        )
                )
            }
            trailing.pendingNote && trailing.onNoteClick != null -> {
                Text(
                    text = "记一句",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = onSurface.copy(alpha = 0.45f),
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .clickable(onClick = trailing.onNoteClick)
                        .padding(horizontal = 2.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
private fun MindfulnessTierBadge(
    level: Int,
    isDark: Boolean,
    intentKind: IntentKind? = null,
    onClick: (() -> Unit)? = null
) {
    val accent = CompareTierColors.accent(level, isDark)
    val label = UsageRecordEntity.MindfulnessLevel.tierLabel(level, intentKind = intentKind)
    if (label.isEmpty()) return
    Text(
        text = label,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = accent,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(LogBadgeRadius))
            .background(accent.copy(alpha = 0.14f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/** 图标 + 正下方 App 名 */
@Composable
private fun TimelineLogAppIdentity(
    appInfo: AppInfo?,
    appName: String,
    dimmed: Boolean,
    onSurface: Color
) {
    Column(
        modifier = Modifier.width(LogIdentityColWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        TimelineLogAppIcon(
            appInfo = appInfo,
            appName = appName,
            dimmed = dimmed,
            onSurface = onSurface
        )
        Text(
            text = appName,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            color = onSurface.copy(alpha = if (dimmed) 0.28f else 0.42f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TimelineLogAppIcon(
    appInfo: AppInfo?,
    appName: String,
    dimmed: Boolean,
    onSurface: Color,
    size: Dp = LogIconSize,
    accentFallback: Color = onSurface
) {
    if (appInfo?.icon != null) {
        val bitmap = remember(appInfo.icon) { appInfo.icon.toBitmap().asImageBitmap() }
        Image(
            bitmap = bitmap,
            contentDescription = appName,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape((size.value * 0.22f).dp))
                .then(if (dimmed) Modifier.alpha(0.55f) else Modifier)
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape((size.value * 0.22f).dp))
                .background(accentFallback.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Android,
                contentDescription = null,
                tint = accentFallback.copy(alpha = if (dimmed) 0.28f else 0.55f),
                modifier = Modifier.size((size.value * 0.5f).dp)
            )
        }
    }
}
