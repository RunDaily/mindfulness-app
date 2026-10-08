package com.life.mindfulnessapp.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.isSystemInDarkTheme
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
import com.life.mindfulnessapp.domain.model.BrowseCasualIntent
import com.life.mindfulnessapp.domain.model.WeekOverviewAppRow
import com.life.mindfulnessapp.domain.model.WeekOverviewCut
import com.life.mindfulnessapp.domain.model.WeekOverviewDayRow
import com.life.mindfulnessapp.domain.model.WeekOverviewSnapshot
import com.life.mindfulnessapp.ui.common.SecondaryPageScaffold
import com.life.mindfulnessapp.ui.theme.LogoGreen

internal data class EventPalette(
    val hold: Color,
    val search: Color,
    val write: Color,
    val browse: Color
)

@Composable
internal fun rememberEventPalette(dark: Boolean): EventPalette =
    if (dark) {
        EventPalette(
            hold = Color(0xFFC4A35A),
            search = Color(0xFF7EB8C9),
            write = Color(0xFFA8B4C4),
            browse = Color(0xFFC47A9A)
        )
    } else {
        EventPalette(
            hold = Color(0xFF9A7B3C),
            search = Color(0xFF4A8A9A),
            write = Color(0xFF6A7585),
            browse = Color(0xFFA85A7A)
        )
    }

/** 「这一周」· 事件 / App / 按日 三刀。 */
@Composable
fun WeekOverviewScreen(
    onBack: () -> Unit,
    onOpenDayReport: (dateMs: Long) -> Unit,
    onOpenAppDayReport: (packageName: String) -> Unit,
    viewModel: WeekOverviewViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val colors = MaterialTheme.colorScheme
    val palette = rememberEventPalette(isSystemInDarkTheme())
    val snap = state.snapshot

    SecondaryPageScaffold(
        title = "这一周",
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
            GateCutChips(
                selected = state.cut,
                onSelect = viewModel::selectCut
            )

            if (snap == null || snap.totalGateEvents <= 0) {
                Text(
                    "还没有门口",
                    color = colors.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 36.dp)
                )
            } else {
                when (state.cut) {
                    WeekOverviewCut.EVENT -> EventCut(
                        snap = snap,
                        palette = palette,
                        onOpenToday = { onOpenDayReport(System.currentTimeMillis()) }
                    )
                    WeekOverviewCut.APP -> GateAppCut(
                        rows = snap.byApp,
                        onOpenApp = onOpenAppDayReport
                    )
                    WeekOverviewCut.DAY -> GateDayCut(
                        rows = snap.byDay,
                        selectedDayStartMs = state.selectedDayStartMs,
                        onSelectDay = viewModel::selectDay,
                        onOpenDay = onOpenDayReport,
                        accent = LogoGreen
                    )
                }
            }
        }
    }
}

@Composable
internal fun GateCutChips(
    selected: WeekOverviewCut,
    onSelect: (WeekOverviewCut) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        listOf(
            WeekOverviewCut.EVENT to "事件",
            WeekOverviewCut.APP to "App",
            WeekOverviewCut.DAY to "按日"
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
                    .then(
                        if (on) Modifier.padding(bottom = 0.dp) else Modifier
                    )
            )
        }
    }
    // underline for selected via bottom border on row of selected only — keep minimal
    HorizontalDivider(
        color = colors.outlineVariant.copy(alpha = 0.25f),
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
internal fun EventCut(
    snap: WeekOverviewSnapshot,
    palette: EventPalette,
    onOpenToday: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val total = snap.totalGateEvents.coerceAtLeast(1)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        HeroStat(snap.heldCount, "守住", palette.hold)
        HeroStat(snap.searchCount, "搜索", palette.search)
        HeroStat(snap.writeCount, "写下", colors.onSurfaceVariant)
        HeroStat(snap.browseCount, BrowseCasualIntent.DISPLAY_LABEL, palette.browse)
    }
    HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.35f))

    val shares = listOf(
        Triple(BrowseCasualIntent.DISPLAY_LABEL, snap.browseCount, palette.browse),
        Triple("守住", snap.heldCount, palette.hold),
        Triple("写下意图", snap.writeCount, colors.onSurfaceVariant.copy(alpha = 0.75f)),
        Triple("搜索", snap.searchCount, palette.search)
    ).sortedByDescending { it.second }

    shares.forEachIndexed { index, (label, count, tint) ->
        val pct = ((count * 100f) / total).toInt()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(label, color = colors.onBackground, fontSize = 13.sp)
                Text(
                    "$count 次 · $pct%",
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
                        .fillMaxWidth((count.toFloat() / total).coerceIn(0f, 1f))
                        .background(tint)
                )
            }
            if (index == shares.lastIndex) {
                Spacer(Modifier.height(4.dp))
            }
        }
    }

    Text(
        "看某一天 ›",
        color = LogoGreen.copy(alpha = 0.9f),
        fontSize = 13.sp,
        modifier = Modifier
            .padding(top = 22.dp, bottom = 16.dp)
            .clickable(onClick = onOpenToday)
    )
}

@Composable
internal fun HeroStat(count: Int, label: String, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                count.toString(),
                color = tint,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                "次",
                color = tint.copy(alpha = 0.7f),
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 1.dp, bottom = 3.dp)
            )
        }
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
internal fun GateAppCut(
    rows: List<WeekOverviewAppRow>,
    onOpenApp: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    if (rows.isEmpty()) {
        Text(
            "还没有门口",
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
                        "守住 ${row.heldCount} 次 · 进入 ${row.enterCount} 次",
                        color = colors.onSurfaceVariant.copy(alpha = 0.55f),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                Text(
                    "›",
                    color = colors.onSurfaceVariant.copy(alpha = 0.35f),
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
internal fun GateDayCut(
    rows: List<WeekOverviewDayRow>,
    selectedDayStartMs: Long,
    onSelectDay: (Long) -> Unit,
    onOpenDay: (Long) -> Unit,
    accent: Color
) {
    val colors = MaterialTheme.colorScheme
    val maxGate = rows.maxOfOrNull { it.gateTotal }?.coerceAtLeast(1) ?: 1
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
            val frac = (day.gateTotal.toFloat() / maxGate).coerceIn(0.06f, 1f)
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
                WeekOverviewViewModel.formatWeekdayShort(day.dayStartMs),
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
            "选中 · ${WeekOverviewViewModel.formatMonthDay(selected.dayStartMs)}",
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
                        "守住 ${selected.heldCount} 次 · 进入 ${selected.enterCount} 次",
                        color = colors.onBackground,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    val tip = when {
                        selected.gateTotal <= 0 -> "这天还没有门口"
                        selected.browseCount >= selected.heldCount &&
                            selected.browseCount >= selected.writeCount &&
                            selected.browseCount >= selected.searchCount &&
                            selected.browseCount > 0 -> "${BrowseCasualIntent.DISPLAY_LABEL}偏多"
                        selected.heldCount >= selected.enterCount && selected.heldCount > 0 -> "守住偏多"
                        else -> ""
                    }
                    if (tip.isNotBlank()) {
                        Text(
                            tip,
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
            "打开当天收据 ›",
            color = accent.copy(alpha = 0.9f),
            fontSize = 13.sp,
            modifier = Modifier
                .padding(top = 8.dp, bottom = 16.dp)
                .clickable { onOpenDay(selected.dayStartMs) }
        )
    }
}
