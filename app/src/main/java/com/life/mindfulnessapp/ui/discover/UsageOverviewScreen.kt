package com.life.mindfulnessapp.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.domain.model.UsageOverviewAppRow
import com.life.mindfulnessapp.domain.model.UsageOverviewCut
import com.life.mindfulnessapp.domain.model.UsageOverviewDayRow
import com.life.mindfulnessapp.domain.model.UsageOverviewSnapshot
import com.life.mindfulnessapp.domain.model.formatOverviewDuration
import com.life.mindfulnessapp.ui.common.SecondaryPageScaffold
import com.life.mindfulnessapp.ui.theme.LogoGreen

/** 「用量」· 合计 / App / 按日 三刀。 */
@Composable
fun UsageOverviewScreen(
    onBack: () -> Unit,
    onOpenDayReport: (dateMs: Long) -> Unit,
    onOpenAppDetail: (packageName: String) -> Unit,
    viewModel: UsageOverviewViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val colors = MaterialTheme.colorScheme
    val durColor = if (isSystemInDarkTheme()) Color(0xFF8EB89A) else Color(0xFF3D7A4A)
    val snap = state.snapshot

    SecondaryPageScaffold(
        title = "用量",
        subtitle = state.rangeLabel.ifBlank { null },
        onBack = onBack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            UsageCutChips(
                selected = state.cut,
                onSelect = viewModel::selectCut
            )

            if (snap == null || snap.totalDurationSeconds <= 0L) {
                Text(
                    "还没有用量",
                    color = colors.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 36.dp)
                )
            } else {
                when (state.cut) {
                    UsageOverviewCut.TOTAL -> UsageTotalCut(
                        snap = snap,
                        durColor = durColor,
                        onOpenAppCut = { viewModel.selectCut(UsageOverviewCut.APP) }
                    )
                    UsageOverviewCut.APP -> UsageAppCut(
                        rows = snap.byApp,
                        durColor = durColor,
                        onOpenApp = onOpenAppDetail
                    )
                    UsageOverviewCut.DAY -> UsageDayCut(
                        rows = snap.byDay,
                        selectedDayStartMs = state.selectedDayStartMs,
                        onSelectDay = viewModel::selectDay,
                        onOpenDay = onOpenDayReport,
                        accent = LogoGreen,
                        durColor = durColor
                    )
                }
            }
        }
    }
}

@Composable
internal fun UsageCutChips(
    selected: UsageOverviewCut,
    onSelect: (UsageOverviewCut) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        listOf(
            UsageOverviewCut.TOTAL to "合计",
            UsageOverviewCut.APP to "App",
            UsageOverviewCut.DAY to "按日"
        ).forEach { (cut, label) ->
            val on = cut == selected
            Text(
                label,
                color = if (on) colors.onBackground else colors.onSurfaceVariant.copy(alpha = 0.45f),
                fontSize = 13.sp,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .clickable { onSelect(cut) }
                    .padding(vertical = 4.dp)
            )
        }
    }
    HorizontalDivider(
        color = colors.outlineVariant.copy(alpha = 0.25f),
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
internal fun UsageTotalCut(
    snap: UsageOverviewSnapshot,
    durColor: Color,
    onOpenAppCut: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val total = snap.totalDurationSeconds.coerceAtLeast(1L)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            val label = formatOverviewDuration(snap.totalDurationSeconds)
            val parts = label.split(' ', limit = 2)
            Text(
                parts.getOrElse(0) { "0" },
                color = durColor,
                fontSize = 34.sp,
                fontWeight = FontWeight.Medium
            )
            if (parts.size > 1) {
                Text(
                    parts[1],
                    color = durColor.copy(alpha = 0.75f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
                )
            }
        }
        Text(
            "日均 ${formatOverviewDuration(snap.avgDailySeconds())} · 监控中 ${snap.activeAppCount} 个",
            color = colors.onSurfaceVariant.copy(alpha = 0.55f),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
    HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.35f))

    snap.byApp.take(3).forEach { row ->
        val pct = ((row.durationSeconds * 100f) / total).toInt()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(row.appName, color = colors.onBackground, fontSize = 13.sp)
                Text(
                    "${formatOverviewDuration(row.durationSeconds)} · $pct%",
                    color = colors.onSurfaceVariant.copy(alpha = 0.55f),
                    fontSize = 12.sp
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.outlineVariant.copy(alpha = 0.25f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(
                            (row.durationSeconds.toFloat() / total).coerceIn(0f, 1f)
                        )
                        .background(durColor)
                )
            }
        }
    }

    Text(
        "按 App 看全部 ›",
        color = LogoGreen.copy(alpha = 0.9f),
        fontSize = 13.sp,
        modifier = Modifier
            .padding(top = 22.dp, bottom = 16.dp)
            .clickable(onClick = onOpenAppCut)
    )
}

@Composable
internal fun UsageAppCut(
    rows: List<UsageOverviewAppRow>,
    durColor: Color,
    onOpenApp: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    if (rows.isEmpty()) {
        Text(
            "还没有用量",
            color = colors.onSurfaceVariant,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 28.dp)
        )
        return
    }
    rows.forEachIndexed { index, row ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenApp(row.packageName) }
                .padding(top = if (index == 0) 4.dp else 0.dp)
        ) {
            if (index > 0) {
                HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.35f))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        row.appName,
                        color = colors.onBackground,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "进入 ${row.enterCount} 次",
                        color = colors.onSurfaceVariant.copy(alpha = 0.55f),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                Text(
                    formatOverviewDuration(row.durationSeconds),
                    color = durColor.copy(alpha = 0.9f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
internal fun UsageDayCut(
    rows: List<UsageOverviewDayRow>,
    selectedDayStartMs: Long,
    onSelectDay: (Long) -> Unit,
    onOpenDay: (Long) -> Unit,
    accent: Color,
    durColor: Color
) {
    val colors = MaterialTheme.colorScheme
    val maxDur = rows.maxOfOrNull { it.durationSeconds }?.coerceAtLeast(1L) ?: 1L
    val selected = rows.firstOrNull { it.dayStartMs == selectedDayStartMs } ?: rows.lastOrNull()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        rows.forEach { day ->
            val frac = (day.durationSeconds.toFloat() / maxDur).coerceIn(0.06f, 1f)
            val selectedBar = day.dayStartMs == selectedDayStartMs
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onSelectDay(day.dayStartMs) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .fillMaxHeight(frac)
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(
                            if (selectedBar) accent.copy(alpha = 0.75f)
                            else colors.onSurfaceVariant.copy(alpha = 0.28f)
                        )
                )
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        rows.forEach { day ->
            Text(
                UsageOverviewViewModel.formatWeekdayShort(day.dayStartMs),
                color = colors.onSurfaceVariant.copy(
                    alpha = if (day.dayStartMs == selectedDayStartMs) 0.75f else 0.4f
                ),
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
        }
    }

    if (selected != null) {
        Text(
            "选中 · ${UsageOverviewViewModel.formatMonthDay(selected.dayStartMs)}",
            color = colors.onSurfaceVariant.copy(alpha = 0.55f),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 18.dp, bottom = 4.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenDay(selected.dayStartMs) }
                .padding(vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        formatOverviewDuration(selected.durationSeconds),
                        color = durColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (selected.topAppName.isNotBlank() && selected.durationSeconds > 0L) {
                        Text(
                            "${selected.topAppName}偏多",
                            color = colors.onSurfaceVariant.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    } else if (selected.durationSeconds <= 0L) {
                        Text(
                            "这天还没有用量",
                            color = colors.onSurfaceVariant.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                }
                Text(
                    "›",
                    color = colors.onSurfaceVariant.copy(alpha = 0.35f),
                    fontSize = 14.sp
                )
            }
        }
        Text(
            "打开当天 ›",
            color = accent.copy(alpha = 0.9f),
            fontSize = 13.sp,
            modifier = Modifier
                .padding(top = 8.dp, bottom = 16.dp)
                .clickable { onOpenDay(selected.dayStartMs) }
        )
    } else {
        Spacer(Modifier.height(8.dp))
    }
}
