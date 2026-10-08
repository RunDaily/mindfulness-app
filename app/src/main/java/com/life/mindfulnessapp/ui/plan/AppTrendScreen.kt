package com.life.mindfulnessapp.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.repository.RulePlanRepository
import com.life.mindfulnessapp.domain.model.AppDiary
import com.life.mindfulnessapp.domain.model.AppDiaryDaySessions
import com.life.mindfulnessapp.domain.model.AppDiaryDaySource
import com.life.mindfulnessapp.domain.model.AppDiarySessionEntry
import com.life.mindfulnessapp.domain.model.AppDiarySessionKind
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.util.AppUsageFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration

@HiltViewModel
class AppTrendViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: RulePlanRepository
) : ViewModel() {
    private val packageName: String = savedStateHandle.get<String>("packageName").orEmpty()

    private val _diary = MutableStateFlow<AppDiary?>(null)
    val diary: StateFlow<AppDiary?> = _diary.asStateFlow()

    private val _selectedDayStart = MutableStateFlow<Long?>(null)
    val selectedDayStart: StateFlow<Long?> = _selectedDayStart.asStateFlow()

    private val _daySessions = MutableStateFlow<AppDiaryDaySessions?>(null)
    val daySessions: StateFlow<AppDiaryDaySessions?> = _daySessions.asStateFlow()

    init {
        viewModelScope.launch {
            val loaded = if (packageName.isBlank()) null else repository.appDiary(packageName)
            _diary.value = loaded
            // 默认点完整日；今天未完结，不抢默认选中
            val default = loaded?.trendDays?.lastOrNull { !it.isToday }?.dayStartMs
                ?: loaded?.trendDays?.lastOrNull()?.dayStartMs
            _selectedDayStart.value = default
            if (default != null) loadDay(default)
        }
    }

    fun selectDay(dayStartMs: Long) {
        if (_selectedDayStart.value == dayStartMs) return
        _selectedDayStart.value = dayStartMs
        viewModelScope.launch { loadDay(dayStartMs) }
    }

    private suspend fun loadDay(dayStartMs: Long) {
        _daySessions.value = repository.daySessions(packageName, dayStartMs)
    }
}

@Composable
fun AppTrendScreen(
    onBack: () -> Unit,
    viewModel: AppTrendViewModel = hiltViewModel()
) {
    val diary by viewModel.diary.collectAsState()
    val selected by viewModel.selectedDayStart.collectAsState()
    val sessions by viewModel.daySessions.collectAsState()
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val activity = context as? Activity
    val config = LocalConfiguration.current
    val landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE

    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    val row = diary
    if (landscape) {
        LandscapeTrendBody(
            diary = row,
            selected = selected,
            sessions = sessions,
            onBack = onBack,
            onSelect = viewModel::selectDay
        )
    } else {
        PortraitTrendBody(
            diary = row,
            selected = selected,
            sessions = sessions,
            onBack = onBack,
            onSelect = viewModel::selectDay
        )
    }
}

@Composable
private fun PortraitTrendBody(
    diary: AppDiary?,
    selected: Long?,
    sessions: AppDiaryDaySessions?,
    onBack: () -> Unit,
    onSelect: (Long) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            "‹ ${diary?.appName ?: "详情"}",
            color = colors.onSurfaceVariant,
            modifier = Modifier.clickable(onClick = onBack)
        )
        Spacer(Modifier.height(10.dp))
        Text("走势", style = MaterialTheme.typography.headlineSmall, color = colors.onBackground)
        if (diary != null && diary.trendDays.isNotEmpty()) {
            val trendScroll = rememberScrollState()
            val afterAvg = diary.avgMinutes.takeIf { diary.avgReady && it > 0 }
            Spacer(Modifier.height(12.dp))
            Text("时长", color = colors.onSurfaceVariant, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            TrendFullChart(
                days = diary.trendDays,
                baselineAvgMinutes = diary.baselineAvgMinutes,
                afterAvgMinutes = afterAvg,
                limitMinutes = diary.limitMinutes,
                selectedStart = selected,
                onSelect = onSelect,
                showValueLabels = true,
                valueLabelsSelectedOnly = false,
                drawBaselineLine = true,
                drawAfterAvgLine = true,
                chartHeight = TrendChartHeights.Duration,
                slotWidth = 26.dp,
                scrollState = trendScroll
            )
            TrendLegendRow(
                baselineAvgMinutes = diary.baselineAvgMinutes,
                afterAvgMinutes = afterAvg,
                limitMinutes = diary.limitMinutes
            )
            Spacer(Modifier.height(14.dp))
            Text("打开", color = colors.onSurfaceVariant, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            TrendOpensChart(
                days = diary.trendDays,
                selectedStart = selected,
                onSelect = onSelect,
                chartHeight = TrendChartHeights.Opens,
                slotWidth = 26.dp,
                showValueLabels = true,
                valueLabelsSelectedOnly = false,
                scrollState = trendScroll
            )
        }
        Spacer(Modifier.height(10.dp))
        DaySessionsBlock(sessions)
        Spacer(Modifier.height(14.dp))
        Text(
            diary?.dataSourceNote ?: AppDiary.DATA_SOURCE_NOTE,
            color = colors.onSurfaceVariant.copy(alpha = 0.55f),
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LandscapeTrendBody(
    diary: AppDiary?,
    selected: Long?,
    sessions: AppDiaryDaySessions?,
    onBack: () -> Unit,
    onSelect: (Long) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(end = 10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "‹ ${diary?.appName ?: "详情"}",
                        color = colors.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable(onClick = onBack)
                    )
                    Text(
                        "走势",
                        color = colors.onBackground,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (diary != null) {
                    val afterAvg = diary.avgMinutes.takeIf { diary.avgReady && it > 0 }
                    TrendLegendRow(
                        baselineAvgMinutes = diary.baselineAvgMinutes,
                        afterAvgMinutes = afterAvg,
                        limitMinutes = diary.limitMinutes,
                        compact = true
                    )
                }
            }
            if (diary != null && diary.trendDays.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Column(Modifier.weight(1f).fillMaxWidth()) {
                    TrendLandscapeChart(
                        days = diary.trendDays,
                        baselineAvgMinutes = diary.baselineAvgMinutes,
                        afterAvgMinutes = diary.avgMinutes.takeIf { diary.avgReady && it > 0 },
                        limitMinutes = diary.limitMinutes,
                        selectedStart = selected,
                        onSelect = onSelect,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                    Text(
                        "打开",
                        color = colors.onSurfaceVariant,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                    TrendOpensChart(
                        days = diary.trendDays,
                        selectedStart = selected,
                        onSelect = onSelect,
                        chartHeight = TrendChartHeights.Opens,
                        fitToWidth = true,
                        showValueLabels = true,
                        valueLabelsSelectedOnly = false
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .width(168.dp)
                .fillMaxHeight()
                .padding(start = 10.dp)
        ) {
            Text("选中", color = colors.onSurfaceVariant, fontSize = 10.sp)
            Text(
                sessions?.label ?: "—",
                color = colors.onBackground,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                sessions?.let { "${it.totalMinutes}" } ?: "—",
                color = LogoGreen,
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.5).sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                "分 · ${sessions?.sectionLabel.orEmpty()}" +
                    (sessions?.entries?.let { " · ${it.size} 次" } ?: ""),
                color = colors.onSurfaceVariant,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(8.dp))
            val entries = sessions?.entries.orEmpty()
            if (entries.isEmpty()) {
                Text(
                    "这一天没有记录",
                    color = colors.onSurfaceVariant,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    items(entries.size, key = { entries[it].startMs to entries[it].kind }) { i ->
                        LandscapeSessionRow(entries[i])
                    }
                }
            }
            val foot = when (sessions?.source) {
                AppDiaryDaySource.AnchorAfter -> "门口不计时"
                AppDiaryDaySource.SystemBefore -> "系统前台段"
                null -> ""
            }
            if (foot.isNotEmpty()) {
                Text(
                    foot,
                    color = colors.onSurfaceVariant.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun DaySessionsBlock(sessions: AppDiaryDaySessions?) {
    val colors = MaterialTheme.colorScheme
    if (sessions == null) return
    Spacer(Modifier.height(8.dp))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(sessions.label, color = colors.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text(
            "${sessions.totalMinutes} 分 · ${sessions.sectionLabel}",
            color = colors.onSurfaceVariant,
            fontSize = 11.sp
        )
    }
    if (sessions.entries.isEmpty()) {
        Text(
            if (sessions.totalMinutes > 0) "这一天没有可展开的逐次" else "这一天没有使用记录",
            color = colors.onSurfaceVariant,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 12.dp)
        )
        return
    }
    sessions.entries.forEach { entry ->
        SessionSpineRow(entry)
    }
}

@Composable
private fun SessionSpineRow(entry: AppDiarySessionEntry) {
    val colors = MaterialTheme.colorScheme
    val muted = entry.kind == AppDiarySessionKind.GateQuit ||
        entry.kind == AppDiarySessionKind.DayTotalOnly
    val start = AppUsageFormat.clockHm(entry.startMs)
    val end = when (entry.kind) {
        AppDiarySessionKind.Ongoing -> "现在"
        AppDiarySessionKind.DayTotalOnly -> ""
        else -> AppUsageFormat.clockHm(entry.endMs)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                if (end.isBlank()) start else "$start – $end",
                color = colors.onSurfaceVariant,
                fontSize = 11.sp
            )
            Text(
                entry.title,
                color = if (muted) colors.onSurfaceVariant else colors.onBackground,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 1.dp)
            )
            entry.subtitle?.let {
                Text(it, color = colors.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(top = 1.dp))
            }
        }
        Text(
            when (entry.kind) {
                AppDiarySessionKind.GateQuit -> "—"
                AppDiarySessionKind.DayTotalOnly ->
                    if (entry.durationMinutes > 0) "${entry.durationMinutes} 分" else "—"
                AppDiarySessionKind.SystemForeground ->
                    AppUsageFormat.sessionDuration(entry.durationSeconds)
                AppDiarySessionKind.Ongoing ->
                    AppUsageFormat.sessionDuration(entry.durationSeconds)
                AppDiarySessionKind.AnchorUse ->
                    AppUsageFormat.sessionDuration(entry.durationSeconds)
                else -> if (entry.durationMinutes > 0) "${entry.durationMinutes} 分" else "—"
            },
            color = when (entry.kind) {
                AppDiarySessionKind.GateQuit, AppDiarySessionKind.DayTotalOnly -> colors.tertiary
                else -> colors.onSurfaceVariant
            },
            fontSize = 13.sp
        )
    }
}

@Composable
private fun LandscapeSessionRow(entry: AppDiarySessionEntry) {
    val colors = MaterialTheme.colorScheme
    val muted = entry.kind == AppDiarySessionKind.GateQuit ||
        entry.kind == AppDiarySessionKind.DayTotalOnly
    val dur = when (entry.kind) {
        AppDiarySessionKind.GateQuit -> "—"
        AppDiarySessionKind.DayTotalOnly ->
            if (entry.durationMinutes > 0) "${entry.durationMinutes}′" else "—"
        AppDiarySessionKind.SystemForeground, AppDiarySessionKind.Ongoing ->
            if (entry.source == AppDiaryDaySource.SystemBefore) {
                AppUsageFormat.sessionDuration(entry.durationSeconds)
            } else if (entry.durationMinutes > 0) {
                "${entry.durationMinutes}′"
            } else {
                "—"
            }
        else -> if (entry.durationMinutes > 0) "${entry.durationMinutes}′" else "—"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f).padding(end = 6.dp)) {
            Text(
                AppUsageFormat.clockHm(entry.startMs),
                color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                fontSize = 10.sp
            )
            Text(
                entry.title,
                color = if (muted) colors.onSurfaceVariant else colors.onBackground,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp)
            )
            entry.subtitle?.let {
                Text(
                    it,
                    color = colors.onSurfaceVariant.copy(alpha = 0.55f),
                    fontSize = 9.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            dur,
            color = if (muted) colors.tertiary else colors.onSurfaceVariant,
            fontSize = 11.sp
        )
    }
}
