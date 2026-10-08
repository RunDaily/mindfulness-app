package com.life.mindfulnessapp.ui.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.domain.model.UsageOverviewCut
import com.life.mindfulnessapp.domain.model.WeekOverviewCut
import com.life.mindfulnessapp.ui.common.SecondaryPageScaffold
import com.life.mindfulnessapp.ui.theme.LogoGreen

/** 发现 · 总览：次数 / 用量 两镜头。 */
@Composable
fun OverviewScreen(
    onBack: () -> Unit,
    onOpenDayReport: (dateMs: Long) -> Unit,
    onOpenAppDayReport: (packageName: String) -> Unit,
    onOpenAppDetail: (packageName: String) -> Unit,
    viewModel: OverviewViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val colors = MaterialTheme.colorScheme
    val palette = rememberEventPalette(isSystemInDarkTheme())
    val durColor = if (isSystemInDarkTheme()) Color(0xFF8EB89A) else Color(0xFF3D7A4A)

    SecondaryPageScaffold(
        title = "总览",
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
            LensRow(
                selected = state.lens,
                onSelect = viewModel::selectLens
            )

            when (state.lens) {
                OverviewLens.GATE -> {
                    GateCutChips(
                        selected = state.gateCut,
                        onSelect = viewModel::selectGateCut
                    )
                    val snap = state.gate
                    if (snap == null || snap.totalGateEvents <= 0) {
                        Text(
                            "还没有门口",
                            color = colors.onSurfaceVariant,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 28.dp)
                        )
                    } else {
                        when (state.gateCut) {
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
                OverviewLens.USAGE -> {
                    UsageCutChips(
                        selected = state.usageCut,
                        onSelect = viewModel::selectUsageCut
                    )
                    val snap = state.usage
                    if (snap == null || snap.totalDurationSeconds <= 0L) {
                        Text(
                            "还没有用量",
                            color = colors.onSurfaceVariant,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 28.dp)
                        )
                    } else {
                        when (state.usageCut) {
                            UsageOverviewCut.TOTAL -> UsageTotalCut(
                                snap = snap,
                                durColor = durColor,
                                onOpenAppCut = { viewModel.selectUsageCut(UsageOverviewCut.APP) }
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
    }
}

@Composable
private fun LensRow(
    selected: OverviewLens,
    onSelect: (OverviewLens) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        listOf(
            OverviewLens.GATE to "次数",
            OverviewLens.USAGE to "用量"
        ).forEach { (lens, label) ->
            val on = lens == selected
            Text(
                label,
                color = if (on) colors.onBackground else colors.onSurfaceVariant.copy(alpha = 0.4f),
                fontSize = 15.sp,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                modifier = Modifier
                    .clickable { onSelect(lens) }
                    .padding(vertical = 6.dp)
            )
        }
    }
}
