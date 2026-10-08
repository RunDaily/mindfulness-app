package com.life.mindfulnessapp.ui.lookback

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.domain.model.WeekCapabilityMode
import com.life.mindfulnessapp.domain.model.WeekDayPulse
import com.life.mindfulnessapp.domain.model.WeekExcerpt
import com.life.mindfulnessapp.domain.model.WeekFulfillment
import com.life.mindfulnessapp.domain.model.WeekLookbackSnapshot
import com.life.mindfulnessapp.domain.model.WeekPulse
import com.life.mindfulnessapp.domain.model.WeekVerdictTone
import com.life.mindfulnessapp.domain.model.WeekVsPrevious
import com.life.mindfulnessapp.domain.model.formatWeekDeltaCount
import com.life.mindfulnessapp.domain.model.formatWeekDeltaSeconds
import com.life.mindfulnessapp.domain.model.formatWeekDuration
import com.life.mindfulnessapp.domain.model.formatWeekRangeLabel
import com.life.mindfulnessapp.ui.theme.DangerColor
import com.life.mindfulnessapp.ui.theme.HeatmapNeutral
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.theme.WarningColor

@Composable
fun WeekLookbackScreen(
    viewModel: WeekLookbackViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToHome: () -> Unit = {}
) {
    val snapshot by viewModel.snapshot.collectAsState()
    val canGoPrev by viewModel.canGoPrevWeek.collectAsState()
    val canGoNext by viewModel.canGoNextWeek.collectAsState()
    val isExportingCard by viewModel.isExportingCard.collectAsState()
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    var showLeaveSheet by remember { mutableStateOf(false) }

    val saveCardLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        if (uri == null) {
            viewModel.cancelPendingCardSave()
            return@rememberLauncherForActivityResult
        }
        viewModel.completeSaveCard(uri) { ok, msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            if (ok) showLeaveSheet = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = cs.onSurface
                )
            }
            Text(
                text = "周回望",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface,
                modifier = Modifier.padding(start = 2.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            val dataForLeave = snapshot
            IconButton(
                onClick = { showLeaveSheet = true },
                enabled = dataForLeave != null
            ) {
                Icon(
                    Icons.Outlined.SaveAlt,
                    contentDescription = "留下这一周",
                    tint = cs.onSurface.copy(alpha = if (dataForLeave != null) 0.55f else 0.18f)
                )
            }
            WeekSwitcher(
                canGoPrev = canGoPrev,
                canGoNext = canGoNext,
                onPrev = viewModel::goPrevWeek,
                onNext = viewModel::goNextWeek,
                onSurface = cs.onSurface
            )
        }

        val data = snapshot
        if (data == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "整理这一周…",
                    color = cs.onSurface.copy(alpha = 0.35f),
                    fontSize = 14.sp
                )
            }
        } else {
            AnimatedContent(
                targetState = data.weekStartMs,
                transitionSpec = {
                    fadeIn(tween(280)) togetherWith fadeOut(tween(180))
                },
                label = "weekLookbackContent",
                modifier = Modifier.fillMaxSize()
            ) {
                WeekLookbackBody(
                    snapshot = data,
                    onNavigateToHome = onNavigateToHome,
                    onGoCurrentWeek = viewModel::goCurrentWeek,
                    onLeaveWeek = { showLeaveSheet = true }
                )
            }
        }
    }

    val leaveSnapshot = snapshot
    if (showLeaveSheet && leaveSnapshot != null) {
        LeaveWeekSheet(
            snapshot = leaveSnapshot,
            isExporting = isExportingCard,
            onSave = { bitmap ->
                viewModel.prepareSaveCard(
                    bitmap = bitmap,
                    onReady = { fileName -> saveCardLauncher.launch(fileName) },
                    onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
                )
            },
            onShare = { bitmap ->
                viewModel.shareCard(bitmap) { _, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = {
                viewModel.cancelPendingCardSave()
                showLeaveSheet = false
            }
        )
    }
}

@Composable
private fun WeekSwitcher(
    canGoPrev: Boolean,
    canGoNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSurface: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onPrev,
            enabled = canGoPrev
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "上一周",
                tint = onSurface.copy(alpha = if (canGoPrev) 0.55f else 0.18f)
            )
        }
        IconButton(
            onClick = onNext,
            enabled = canGoNext
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "下一周",
                tint = onSurface.copy(alpha = if (canGoNext) 0.55f else 0.18f)
            )
        }
    }
}

@Composable
private fun WeekLookbackBody(
    snapshot: WeekLookbackSnapshot,
    onNavigateToHome: () -> Unit,
    onGoCurrentWeek: () -> Unit,
    onLeaveWeek: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val verdict = snapshot.verdict
    val toneColor = weekVerdictToneColor(verdict.tone, cs.onSurface)
    val rangeLabel = formatWeekRangeLabel(snapshot.weekStartMs, snapshot.weekEndMs)
    var moreExpanded by remember(snapshot.weekStartMs) { mutableStateOf(false) }

    val showBudget = snapshot.capabilityMode == WeekCapabilityMode.TimeLockOnly ||
        (snapshot.capabilityMode == WeekCapabilityMode.IntentGate &&
            (snapshot.overLimitDays > 0 || snapshot.totalSeconds > 0L))
    val showDayDist = snapshot.capabilityMode == WeekCapabilityMode.IntentGate ||
        snapshot.capabilityMode == WeekCapabilityMode.PeriodLockOnly ||
        snapshot.capabilityMode == WeekCapabilityMode.TimeLockOnly
    val vsPrev = snapshot.vsPreviousWeek
    val showVsPrev = vsPrev != null &&
        vsPrev.hasAnyComparable &&
        snapshot.capabilityMode != WeekCapabilityMode.Unmoored &&
        (snapshot.daysSinceFirstAnchor == null || snapshot.daysSinceFirstAnchor >= 3)
    val hasMore = showDayDist || showBudget || showVsPrev
    val activeDays = snapshot.dayPulses.count { it.hasSignal }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp)
    ) {
        // ── 01 Hero：一句真话 ───────────────────────────────────────────
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "周回望 · $rangeLabel",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = cs.onSurface.copy(alpha = 0.34f),
            letterSpacing = 0.35.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = verdict.headline,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = toneColor,
            lineHeight = 34.sp,
            letterSpacing = (-0.7).sp
        )
        if (verdict.detail != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = verdict.detail,
                fontSize = 13.sp,
                color = cs.onSurface.copy(alpha = 0.38f),
                lineHeight = 18.sp
            )
        }

        // ── 02 清醒结构（唯一常驻可视化）────────────────────────────────
        when (snapshot.capabilityMode) {
            WeekCapabilityMode.IntentGate -> {
                Spacer(modifier = Modifier.height(28.dp))
                SectionLabel(text = "这一周的打开")
                Spacer(modifier = Modifier.height(10.dp))
                AwarenessBar(pulse = snapshot.pulse)
                Spacer(modifier = Modifier.height(8.dp))
                AwarenessLegend(pulse = snapshot.pulse)
            }
            WeekCapabilityMode.Unmoored -> {
                Spacer(modifier = Modifier.height(28.dp))
                SoftCard {
                    Text(
                        text = "系上锚之后，这里会看见一周的意图与守住。",
                        fontSize = 14.sp,
                        color = cs.onSurface.copy(alpha = 0.45f),
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "去今日系锚",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LogoGreen,
                        modifier = Modifier.clickable(onClick = onNavigateToHome)
                    )
                }
            }
            WeekCapabilityMode.TimeLockOnly -> {
                Spacer(modifier = Modifier.height(28.dp))
                SoftCard {
                    Text(
                        text = "这一周以额度为主。打开意图门后，回望会更有味道。",
                        fontSize = 14.sp,
                        color = cs.onSurface.copy(alpha = 0.45f),
                        lineHeight = 20.sp
                    )
                }
            }
            else -> Unit
        }

        // ── 03 和意图比（干净两层）──────────────────────────────────────
        if (snapshot.capabilityMode == WeekCapabilityMode.IntentGate) {
            Spacer(modifier = Modifier.height(28.dp))
            SectionLabel(text = "和意图比")
            Spacer(modifier = Modifier.height(10.dp))
            FulfillmentBlock(
                fulfillment = snapshot.fulfillment,
                mindfulEnters = snapshot.pulse.mindfulEnters,
                excerpts = snapshot.excerpts
            )
        }

        // ── 04 更多（单一折叠）──────────────────────────────────────────
        if (hasMore && snapshot.capabilityMode != WeekCapabilityMode.Unmoored) {
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { moreExpanded = !moreExpanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SectionLabel(text = "更多")
                Text(
                    text = if (moreExpanded) "收起" else "按日 · 额度 · 与上周比",
                    fontSize = 12.sp,
                    color = cs.onSurface.copy(alpha = 0.32f)
                )
            }
            if (moreExpanded) {
                Spacer(modifier = Modifier.height(10.dp))
                if (showDayDist) {
                    Text(
                        text = "按日",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = cs.onSurface.copy(alpha = 0.36f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DayDistributionRow(
                        days = snapshot.dayPulses,
                        intentMode = snapshot.capabilityMode == WeekCapabilityMode.IntentGate,
                        sparseHint = activeDays <= 2
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                if (showBudget) {
                    Text(
                        text = "额度",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = cs.onSurface.copy(alpha = 0.36f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SoftCard {
                        val bits = buildList {
                            if (snapshot.totalSeconds > 0L) {
                                add("已用 ${formatWeekDuration(snapshot.totalSeconds)}")
                            }
                            if (snapshot.overLimitDays > 0) {
                                add("${snapshot.overLimitDays} 天触顶")
                            }
                            if (isEmpty()) add("额度内，安静")
                        }
                        Text(
                            text = bits.joinToString(" · "),
                            fontSize = 14.sp,
                            color = cs.onSurface.copy(alpha = 0.55f)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                if (showVsPrev) {
                    Text(
                        text = "与上周比",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = cs.onSurface.copy(alpha = 0.36f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SoftCard {
                        VsPreviousContent(vs = vsPrev!!, mode = snapshot.capabilityMode)
                    }
                }
            }
        }

        // ── 05 底动作 ───────────────────────────────────────────────────
        Spacer(modifier = Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "看今日时间轴",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = LogoGreen.copy(alpha = 0.9f),
                modifier = Modifier.clickable(onClick = onNavigateToHome)
            )
            Text(
                text = "留下这一周",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = cs.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.clickable(onClick = onLeaveWeek)
            )
            if (!snapshot.isCurrentWeek) {
                Text(
                    text = "回到本周",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = cs.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.clickable(onClick = onGoCurrentWeek)
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
        letterSpacing = 0.2.sp
    )
}

@Composable
private fun SoftCard(content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cs.surfaceVariant.copy(alpha = 0.35f))
            .border(1.dp, cs.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun AwarenessBar(pulse: WeekPulse) {
    val total = (pulse.mindfulEnters + pulse.ungatedEnters + pulse.dismisses).coerceAtLeast(0)
    if (total == 0) {
        SoftCard {
            Text(
                text = "这一周还没有打开或守住的记录",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
        return
    }

    val mindfulTarget = pulse.mindfulEnters.toFloat() / total
    val ungatedTarget = pulse.ungatedEnters.toFloat() / total
    val dismissTarget = pulse.dismisses.toFloat() / total
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "awarenessBarProgress"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    ) {
        val full = maxWidth
        val mindfulW = full * mindfulTarget * progress
        val ungatedW = full * ungatedTarget * progress
        val dismissW = full * dismissTarget * progress
        Row(modifier = Modifier.fillMaxSize()) {
            if (pulse.mindfulEnters > 0) {
                Box(
                    modifier = Modifier
                        .width(mindfulW)
                        .fillMaxSize()
                        .background(LogoGreen)
                )
            }
            if (pulse.ungatedEnters > 0) {
                Box(
                    modifier = Modifier
                        .width(ungatedW)
                        .fillMaxSize()
                        .background(WarningColor.copy(alpha = 0.85f))
                )
            }
            if (pulse.dismisses > 0) {
                Box(
                    modifier = Modifier
                        .width(dismissW)
                        .fillMaxSize()
                        .background(HeatmapNeutral.copy(alpha = 0.9f))
                )
            }
        }
    }
}

@Composable
private fun AwarenessLegend(pulse: WeekPulse) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        LegendChip("带着意图", pulse.mindfulEnters, LogoGreen, cs.onSurface)
        LegendChip("直进", pulse.ungatedEnters, WarningColor.copy(alpha = 0.9f), cs.onSurface)
        LegendChip("守住", pulse.dismisses, HeatmapNeutral, cs.onSurface)
    }
}

@Composable
private fun DayDistributionRow(
    days: List<WeekDayPulse>,
    intentMode: Boolean,
    sparseHint: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    val maxActivity = days.maxOfOrNull { it.activityCount }?.coerceAtLeast(1) ?: 1
    SoftCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            days.forEach { day ->
                DayDotColumn(
                    day = day,
                    maxActivity = maxActivity,
                    intentMode = intentMode,
                    onSurface = cs.onSurface
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = when {
                sparseHint -> "这周记录还很少，先看有信号的几天"
                intentMode -> "点越高：当天打开与守住越多"
                else -> "点越高：当天活动越多"
            },
            fontSize = 11.sp,
            color = cs.onSurface.copy(alpha = 0.28f)
        )
    }
}

@Composable
private fun DayDotColumn(
    day: WeekDayPulse,
    maxActivity: Int,
    intentMode: Boolean,
    onSurface: Color
) {
    val ratio = if (day.hasSignal) {
        (day.activityCount.toFloat() / maxActivity.toFloat()).coerceIn(0.18f, 1f)
    } else {
        0f
    }
    val dotSize = (6 + 14 * ratio).dp
    val color = when {
        !day.hasSignal -> onSurface.copy(alpha = 0.12f)
        intentMode && day.mindfulEnters > 0 -> LogoGreen.copy(alpha = 0.55f + 0.35f * ratio)
        intentMode && day.dismisses > 0 && day.mindfulEnters == 0 && day.ungatedEnters == 0 ->
            HeatmapNeutral.copy(alpha = 0.55f + 0.3f * ratio)
        intentMode && day.ungatedEnters > 0 -> WarningColor.copy(alpha = 0.45f + 0.35f * ratio)
        else -> LogoGreen.copy(alpha = 0.4f + 0.35f * ratio)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.width(36.dp)
    ) {
        Box(
            modifier = Modifier.height(22.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(color)
            )
        }
        Text(
            text = day.weekdayLabel,
            fontSize = 11.sp,
            color = onSurface.copy(alpha = 0.38f)
        )
    }
}

@Composable
private fun VsPreviousContent(vs: WeekVsPrevious, mode: WeekCapabilityMode) {
    val cs = MaterialTheme.colorScheme
    val lines = buildList {
        when (mode) {
            WeekCapabilityMode.IntentGate -> {
                vs.mindfulDelta?.let { add("带着意图 ${formatWeekDeltaCount(it)}") }
                vs.dismissDelta?.let { add("守住 ${formatWeekDeltaCount(it)}") }
            }
            WeekCapabilityMode.PeriodLockOnly -> {
                vs.dismissDelta?.let { add("守住 ${formatWeekDeltaCount(it)}") }
            }
            WeekCapabilityMode.TimeLockOnly, WeekCapabilityMode.WatchOnly -> {
                vs.secondsDelta?.let { add("用时 ${formatWeekDeltaSeconds(it)}") }
            }
            WeekCapabilityMode.Unmoored -> Unit
        }
    }
    if (lines.isEmpty()) {
        Text(
            text = "上周几乎没有可比的记录",
            fontSize = 14.sp,
            color = cs.onSurface.copy(alpha = 0.4f)
        )
    } else {
        Text(
            text = lines.joinToString(" · "),
            fontSize = 14.sp,
            color = cs.onSurface.copy(alpha = 0.58f),
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "只陈述差值，不作评价",
            fontSize = 11.sp,
            color = cs.onSurface.copy(alpha = 0.28f)
        )
    }
}

@Composable
private fun LegendChip(label: String, count: Int, color: Color, onSurface: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = "$label $count",
            fontSize = 12.sp,
            color = onSurface.copy(alpha = 0.42f)
        )
    }
}

@Composable
private fun FulfillmentBlock(
    fulfillment: WeekFulfillment,
    mindfulEnters: Int,
    excerpts: List<WeekExcerpt>
) {
    val cs = MaterialTheme.colorScheme
    SoftCard {
        if (!fulfillment.hasAny) {
            Text(
                text = if (mindfulEnters > 0) {
                    "结束时对照，下周这里会更清楚。"
                } else {
                    "带着意图进入并对照后，这里会看见兑现。"
                },
                fontSize = 14.sp,
                color = cs.onSurface.copy(alpha = 0.42f),
                lineHeight = 20.sp
            )
        } else {
            // 第一层：只放汇总，独占一行
            Text(
                text = buildString {
                    append("对照过 ${fulfillment.reviewedCount}")
                    append(" · 没跑偏 ${fulfillment.aligned}")
                    append(" · 跑偏 ${fulfillment.slight}")
                    append(" · 跑远 ${fulfillment.large}")
                },
                fontSize = 14.sp,
                color = cs.onSurface.copy(alpha = 0.62f),
                lineHeight = 20.sp
            )
            if (excerpts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(
                    color = cs.outlineVariant.copy(alpha = 0.35f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                excerpts.forEachIndexed { index, item ->
                    if (index > 0) Spacer(modifier = Modifier.height(12.dp))
                    ExcerptRow(item)
                }
            }
        }
    }
}

@Composable
private fun ExcerptRow(item: WeekExcerpt) {
    val cs = MaterialTheme.colorScheme
    val levelLabel = UsageRecordEntity.MindfulnessLevel.displayLabel(item.mindfulnessLevel)
    // 优先意图；无意图才用备注；都没有则只显示 App
    val purposeLine = item.purpose
    val noteLine = item.note?.takeIf { note ->
        note != purposeLine && note.length >= 2
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (purposeLine != null) {
            Text(
                text = "「$purposeLine」",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = cs.onSurface.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "$levelLabel · ${item.appName}",
                fontSize = 12.sp,
                color = cs.onSurface.copy(alpha = 0.34f)
            )
        } else {
            Text(
                text = "$levelLabel · ${item.appName}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = cs.onSurface.copy(alpha = 0.72f)
            )
        }
        if (noteLine != null) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = noteLine,
                fontSize = 12.sp,
                color = cs.onSurface.copy(alpha = 0.38f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun weekVerdictToneColor(tone: WeekVerdictTone, onSurface: Color): Color = when (tone) {
    WeekVerdictTone.Mindful, WeekVerdictTone.Bound -> LogoGreen
    WeekVerdictTone.Drift -> WarningColor
    WeekVerdictTone.Alert -> DangerColor
    WeekVerdictTone.Still -> onSurface.copy(alpha = 0.55f)
    WeekVerdictTone.Unmoored -> onSurface.copy(alpha = 0.42f)
}

/** 首页周日轻提示条 */
@Composable
fun SundayLookbackTipBar(
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(LogoGreen.copy(alpha = 0.08f))
            .border(1.dp, LogoGreen.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "这一周，要不要回望一眼",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface.copy(alpha = 0.78f)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "看看有目的、守住与空转，不是再堆报表",
                fontSize = 12.sp,
                color = cs.onSurface.copy(alpha = 0.38f)
            )
        }
        Text(
            text = "关闭",
            fontSize = 12.sp,
            color = cs.onSurface.copy(alpha = 0.32f),
            modifier = Modifier
                .padding(start = 8.dp)
                .clickable(onClick = onDismiss)
                .padding(4.dp)
        )
    }
}
