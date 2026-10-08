package com.life.mindfulnessapp.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.repository.RulePlanRepository
import com.life.mindfulnessapp.domain.model.AppDiary
import com.life.mindfulnessapp.ui.common.SecondaryPageScaffold
import com.life.mindfulnessapp.ui.features.TodayRhythmStrip
import com.life.mindfulnessapp.ui.theme.LogoGreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TodayAppDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: RulePlanRepository
) : ViewModel() {
    private val packageName: String = savedStateHandle.get<String>("packageName").orEmpty()
    private val _diary = MutableStateFlow<AppDiary?>(null)
    val diary: StateFlow<AppDiary?> = _diary.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (packageName.isBlank()) {
            _diary.value = null
            return
        }
        viewModelScope.launch {
            _diary.value = repository.appDiary(packageName)
        }
    }

    fun requestEdit() {
        if (packageName.isNotBlank()) repository.requestEdit(packageName)
    }
}

/**
 * 少详情：今天用量独立置顶 → 时长/打开 前|后对照 + 矮图（不含今天）+ 节奏 + 配置。
 * 返回时刷新，配置区可挂「今天改过」。
 */
@Composable
fun TodayAppDetailScreen(
    onBack: () -> Unit,
    onEditRules: () -> Unit,
    onOpenTrend: () -> Unit = {},
    onWeekRhythm: () -> Unit = {},
    onOpenTodayReceipt: () -> Unit = {},
    viewModel: TodayAppDetailViewModel = hiltViewModel()
) {
    val diary by viewModel.diary.collectAsState()
    val colors = MaterialTheme.colorScheme
    val row = diary
    val peekDays = row?.trendDays?.let { trendDetailPeek(it) }.orEmpty()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    SecondaryPageScaffold(
        title = row?.appName ?: "今日详情",
        onBack = onBack
    ) { padding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        SectionTitle("今天", first = true)
        TodayGlanceRow(row)
        TodayReceiptPeek(
            heldCount = row?.held ?: 0,
            enterCount = row?.opens ?: 0,
            onClick = onOpenTodayReceipt
        )

        SectionTitle("时长（日均）")
        BeforeAfterRow(
            before = {
                val m = row?.baselineAvgMinutes
                if (m != null && m > 0) AvgValueHero(m, "分")
                else PlaceholderHero()
            },
            after = {
                if (row?.avgReady == true) AvgValueHero(row.avgMinutes, "分", after = true)
                else PlaceholderHero()
            }
        )
        if (peekDays.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            TrendPeekChart(
                days = peekDays,
                baselineAvgMinutes = null,
                afterAvgMinutes = null,
                limitMinutes = null,
                onOpenFull = onOpenTrend,
                height = TrendChartHeights.Duration
            )
        }

        SectionTitle("打开（日均）")
        BeforeAfterRow(
            before = {
                val o = row?.baselineAvgOpens
                if (o != null) AvgValueHero(o, "次")
                else PlaceholderHero()
            },
            after = {
                if (row?.avgReady == true) AvgValueHero(row.avgOpens, "次", after = true)
                else PlaceholderHero()
            }
        )
        if (peekDays.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenTrend)
            ) {
                TrendOpensChart(
                    days = peekDays,
                    selectedStart = null,
                    onSelect = { onOpenTrend() },
                    chartHeight = TrendChartHeights.Opens,
                    fitToWidth = true,
                    showValueLabels = false
                )
            }
        }

        SectionTitle("今天 · 何时在用")
        if (row != null && row.todayDayStartMs > 0L) {
            TodayRhythmStrip(
                dayStartMs = row.todayDayStartMs,
                sessions = row.todaySessions
            )
        }
        Text(
            "一周节奏 ›",
            color = LogoGreen,
            fontSize = 13.sp,
            modifier = Modifier
                .padding(top = 8.dp)
                .clickable(onClick = onWeekRhythm)
        )

        Spacer(Modifier.height(18.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.onSurface.copy(alpha = 0.12f))
        )
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "配置",
                color = colors.onBackground,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            row?.configRecentLabel?.let { label ->
                Text(
                    label,
                    color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }
        }
        val summary = formatInstrumentSummary(row)
        if (summary.isNotBlank()) {
            Text(
                summary,
                color = colors.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        Text(
            "去改规则",
            color = LogoGreen,
            fontSize = 15.sp,
            modifier = Modifier
                .padding(top = 12.dp)
                .clickable {
                    viewModel.requestEdit()
                    onEditRules()
                }
        )

        Spacer(Modifier.height(14.dp))
        Text(
            row?.dataSourceNote ?: AppDiary.DATA_SOURCE_NOTE,
            color = colors.onSurfaceVariant.copy(alpha = 0.4f),
            fontSize = 10.sp,
            lineHeight = 14.sp
        )
    }
    }
}

@Composable
private fun SectionTitle(text: String, first: Boolean = false) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = if (first) 4.dp else 18.dp, bottom = 8.dp)
    )
}

@Composable
private fun TodayGlanceRow(row: AppDiary?) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text("使用", color = colors.onSurfaceVariant, fontSize = 11.sp)
            Spacer(Modifier.height(2.dp))
            AvgValueHero(row?.usedMinutes ?: 0, "分")
        }
        Column(Modifier.weight(1f)) {
            Text("打开", color = colors.onSurfaceVariant, fontSize = 11.sp)
            Spacer(Modifier.height(2.dp))
            AvgValueHero(row?.opens ?: 0, "次")
        }
        Column(Modifier.weight(1f)) {
            Text("守住", color = colors.onSurfaceVariant, fontSize = 11.sp)
            Spacer(Modifier.height(2.dp))
            AvgValueHero(row?.held ?: 0, "次")
        }
    }
}

@Composable
private fun BeforeAfterRow(
    before: @Composable () -> Unit,
    after: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text("监控前", color = colors.onSurfaceVariant, fontSize = 11.sp)
            Spacer(Modifier.height(2.dp))
            before()
        }
        Column(Modifier.weight(1f)) {
            Text("加入后", color = colors.onSurfaceVariant, fontSize = 11.sp)
            Spacer(Modifier.height(2.dp))
            after()
        }
    }
}

@Composable
private fun PlaceholderHero() {
    Text(
        "—",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 24.sp,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun AvgValueHero(value: Int, unit: String, after: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val numColor = if (after) LogoGreen else colors.onBackground
    if (unit == "分" && value >= 60) {
        val hours = value / 60
        val rem = value % 60
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "$hours",
                color = numColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.8).sp
            )
            Text(
                "时",
                color = colors.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
            )
            if (rem > 0) {
                Text(
                    "$rem",
                    color = numColor,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.8).sp,
                    modifier = Modifier.padding(start = 2.dp)
                )
                Text(
                    "分",
                    color = colors.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
                )
            }
        }
    } else {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "$value",
                color = numColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.8).sp
            )
            Text(
                unit,
                color = colors.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 3.dp, bottom = 2.dp)
            )
        }
    }
}

private fun formatInstrumentSummary(row: AppDiary?): String {
    if (row == null) return ""
    val raw = row.instrumentSummary
    if (raw.isBlank() || raw == "未配器械") return raw
    return raw
        .replace(Regex("""呼吸 (\d+)s"""), "呼吸 $1 秒")
        .replace(Regex("""≤(\d+) 次"""), "$1 次")
}
