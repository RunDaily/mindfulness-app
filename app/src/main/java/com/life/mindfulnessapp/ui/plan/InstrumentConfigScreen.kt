package com.life.mindfulnessapp.ui.plan

import android.app.TimePickerDialog
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.domain.model.BrowseCasualIntent
import com.life.mindfulnessapp.domain.model.RulePack
import com.life.mindfulnessapp.ui.common.SecondaryPageScaffold
import com.life.mindfulnessapp.ui.theme.LogoGreen
import kotlinx.coroutines.launch

private enum class InstrumentPage { Hub, Daily, Period }

private const val DAILY_MIN = 5
private const val DAILY_MAX = 480
/** 随意浏览 0 = 不限 */
private const val BROWSE_MIN = 0
private const val BROWSE_MAX = 480
private const val DEFAULT_BROWSE_MINUTES = 20

data class InstrumentDraft(
    val packageName: String,
    val appName: String,
    val dailyOn: Boolean,
    val dailyMinutes: Int,
    /** 0 = 不限 */
    val browseCasualMinutes: Int,
    val periodOn: Boolean,
    val periodStart: Int,
    val periodEnd: Int,
    /** 仅当目录支持带词搜索时 UI 展示；默认开 */
    val searchDirectOn: Boolean = true,
    val searchDirectAvailable: Boolean = false
) {
    val anyOn: Boolean get() = dailyOn || periodOn

    fun toPack(): RulePack {
        val total = if (dailyOn) dailyMinutes.coerceIn(DAILY_MIN, DAILY_MAX) else null
        val browse = browseCasualMinutes.coerceIn(BROWSE_MIN, BROWSE_MAX).let { b ->
            if (total != null && b > 0) b.coerceAtMost(total) else b
        }
        return RulePack(
            packageName = packageName,
            appName = appName,
            dailyOpenLimit = null,
            dailyMinutes = total,
            browseCasualDailyMinutes = browse,
            periodStartMinute = if (periodOn) periodStart else null,
            periodEndMinute = if (periodOn) periodEnd else null,
            rationale = "",
            selected = true,
            blockDiscoverFeed = false,
            searchDirectEnabled = if (searchDirectAvailable) searchDirectOn else true,
        )
    }

    companion object {
        fun fresh(packageName: String, appName: String) = InstrumentDraft(
            packageName = packageName,
            appName = appName,
            dailyOn = true,
            dailyMinutes = 30,
            browseCasualMinutes = DEFAULT_BROWSE_MINUTES,
            periodOn = false,
            periodStart = 23 * 60,
            periodEnd = 7 * 60,
            searchDirectOn = true,
            searchDirectAvailable = com.life.mindfulnessapp.data.deeplink.SearchDeepLinkCatalog
                .preferSearchLanding(packageName)
        )

        fun from(pack: RulePack): InstrumentDraft {
            val daily = pack.dailyMinutes ?: 30
            val browseRaw = pack.browseCasualDailyMinutes
                ?: BrowseCasualIntent.DEFAULT_DAILY_LIMIT_MINUTES
            val browse = if (pack.dailyMinutes != null && browseRaw > 0) {
                browseRaw.coerceAtMost(daily)
            } else {
                browseRaw
            }
            return InstrumentDraft(
                packageName = pack.packageName,
                appName = pack.appName,
                dailyOn = pack.dailyMinutes != null,
                dailyMinutes = daily,
                browseCasualMinutes = browse,
                periodOn = pack.periodStartMinute != null && pack.periodEndMinute != null,
                periodStart = pack.periodStartMinute ?: 23 * 60,
                periodEnd = pack.periodEndMinute ?: 7 * 60,
                searchDirectOn = pack.searchDirectEnabled,
                searchDirectAvailable = com.life.mindfulnessapp.data.deeplink.SearchDeepLinkCatalog
                    .preferSearchLanding(pack.packageName)
            )
        }
    }
}

/**
 * 配置：两大约束（日限额 ⊃ 随意浏览、时段锁）+ 搜索直达次级开关。
 */
@Composable
fun InstrumentConfigScreen(
    draft: InstrumentDraft,
    onChange: (InstrumentDraft) -> Unit,
    onBack: () -> Unit,
    backLabel: String,
    indexLabel: String? = null,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    /** 触顶判定：传入放宽前的日限额分钟；添加流可省略 */
    isDailyLimitHitToday: (suspend (baseMinutes: Int) -> Boolean)? = null
) {
    val context = LocalContext.current
    val baseline = remember(draft.packageName) { draft }
    var page by remember(draft.packageName) { mutableStateOf(InstrumentPage.Hub) }
    var showDiscard by remember { mutableStateOf(false) }
    val dirty = draft != baseline

    fun requestBack() {
        if (page != InstrumentPage.Hub) {
            page = InstrumentPage.Hub
            return
        }
        if (!dirty) onBack() else showDiscard = true
    }

    BackHandler {
        requestBack()
    }

    when (page) {
        InstrumentPage.Hub -> InstrumentHub(
            draft = draft,
            onChange = onChange,
            onBack = { requestBack() },
            backLabel = backLabel,
            indexLabel = indexLabel,
            primaryLabel = primaryLabel,
            onPrimary = {
                if (!draft.anyOn) return@InstrumentHub
                Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                onPrimary()
            },
            secondaryLabel = secondaryLabel,
            onSecondary = onSecondary,
            onOpenDaily = { page = InstrumentPage.Daily },
            onOpenPeriod = { page = InstrumentPage.Period },
            periodWasLive = baseline.periodOn,
        )
        InstrumentPage.Daily -> DailyLimitEditPage(
            initialTotal = draft.dailyMinutes.coerceIn(DAILY_MIN, DAILY_MAX),
            initialBrowse = draft.browseCasualMinutes.coerceIn(BROWSE_MIN, BROWSE_MAX),
            baselineDailyOn = baseline.dailyOn,
            baselineTotal = baseline.dailyMinutes.coerceIn(DAILY_MIN, DAILY_MAX),
            appName = draft.appName,
            packageName = draft.packageName,
            isDailyLimitHitToday = isDailyLimitHitToday,
            onBack = { page = InstrumentPage.Hub },
            onConfirmed = { total, browse ->
                onChange(
                    draft.copy(
                        dailyOn = true,
                        dailyMinutes = total,
                        browseCasualMinutes = browse
                    )
                )
                page = InstrumentPage.Hub
            }
        )
        InstrumentPage.Period -> PeriodEditPage(
            startMinute = draft.periodStart,
            endMinute = draft.periodEnd,
            lockIsLive = baseline.periodOn,
            appName = draft.appName,
            packageName = draft.packageName,
            onBack = { page = InstrumentPage.Hub },
            onDone = { start, end ->
                onChange(
                    draft.copy(
                        periodOn = true,
                        periodStart = start,
                        periodEnd = end
                    )
                )
                page = InstrumentPage.Hub
            },
            onDelete = {
                onChange(draft.copy(periodOn = false))
                page = InstrumentPage.Hub
            }
        )
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("有未保存的修改") },
            confirmButton = {
                TextButton(onClick = { showDiscard = false }) {
                    Text("继续编辑", color = LogoGreen)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDiscard = false
                        onBack()
                    }
                ) {
                    Text("丢弃", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
private fun InstrumentHub(
    draft: InstrumentDraft,
    onChange: (InstrumentDraft) -> Unit,
    onBack: () -> Unit,
    backLabel: String,
    indexLabel: String?,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String?,
    onSecondary: (() -> Unit)?,
    onOpenDaily: () -> Unit,
    onOpenPeriod: () -> Unit,
    /** 进入本页时时段锁已在方案里（已武装），关掉需过呼吸门 */
    periodWasLive: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    var showRemoveConfirm by remember { mutableStateOf(false) }
    var pendingClosePeriod by remember { mutableStateOf(false) }
    SecondaryPageScaffold(
        title = draft.appName,
        onBack = onBack,
        subtitle = indexLabel
    ) { padding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        com.life.mindfulnessapp.domain.model.MonitorSuitability
            .unsuitableReminder(draft.packageName)
            ?.let { msg ->
                Text(
                    msg,
                    color = colors.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

        // ── 日限额 ──
        Text("日限额", color = colors.onBackground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenDaily)
                .padding(top = 10.dp, bottom = 4.dp)
        ) {
            ValueRow(
                label = "总限额",
                value = if (draft.dailyOn) "${draft.dailyMinutes} 分" else "未开",
                emphasize = draft.dailyOn
            )
            Spacer(Modifier.height(8.dp))
            ValueRow(
                label = "└ ${BrowseCasualIntent.DISPLAY_LABEL}",
                value = browseSummary(draft.browseCasualMinutes),
                emphasize = false,
                mutedLabel = true
            )
        }

        Spacer(Modifier.height(8.dp))
        Hairline()
        Spacer(Modifier.height(12.dp))

        // ── 时段锁 ──
        Text("时段锁", color = colors.onBackground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        if (draft.periodOn) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenPeriod)
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (draft.periodStart > draft.periodEnd) "夜间" else "时段",
                    color = colors.onSurfaceVariant,
                    fontSize = 13.sp
                )
                Text(
                    RulePack.formatPeriodRange(draft.periodStart, draft.periodEnd),
                    color = colors.onBackground,
                    fontSize = 15.sp
                )
            }
            Text(
                "关闭时段锁",
                color = colors.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .clickable {
                        if (periodWasLive) pendingClosePeriod = true
                        else onChange(draft.copy(periodOn = false))
                    }
            )
        } else {
            Text(
                "＋ 添加时段",
                color = LogoGreen,
                fontSize = 12.sp,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .clickable(onClick = onOpenPeriod)
            )
        }

        if (draft.searchDirectAvailable) {
            Spacer(Modifier.height(8.dp))
            Hairline()
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "搜索直达",
                    color = colors.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = draft.searchDirectOn,
                    onCheckedChange = { onChange(draft.copy(searchDirectOn = it)) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = LogoGreen,
                        checkedThumbColor = colors.onPrimary
                    )
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        if (secondaryLabel != null && onSecondary != null) {
            Text(
                secondaryLabel,
                color = colors.onSurfaceVariant.copy(
                    alpha = if (draft.anyOn) 0.55f else 0.78f
                ),
                fontSize = 12.sp,
                modifier = Modifier.clickable { showRemoveConfirm = true }
            )
            Spacer(Modifier.height(14.dp))
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!draft.anyOn && (secondaryLabel == null || onSecondary == null)) {
                Text("至少开一项", color = colors.onSurfaceVariant, fontSize = 12.sp)
            } else {
                Spacer(Modifier.width(1.dp))
            }
            Text(
                primaryLabel,
                color = if (draft.anyOn) LogoGreen else colors.onSurfaceVariant,
                fontSize = 16.sp,
                modifier = Modifier.clickable(enabled = draft.anyOn, onClick = onPrimary)
            )
        }
        Spacer(Modifier.height(16.dp))
    }
    }

    if (showRemoveConfirm && onSecondary != null) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text("把${draft.appName}移出方案？") },
            text = {
                Text(
                    "门口不再拦",
                    color = colors.onSurfaceVariant,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRemoveConfirm = false
                        onSecondary()
                    }
                ) {
                    Text("移出", color = colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) {
                    Text("取消", color = colors.onSurfaceVariant)
                }
            }
        )
    }

    if (pendingClosePeriod) {
        com.life.mindfulnessapp.ui.applist.PeriodLockDisableGateDialog(
            commitment = "",
            windowLabel = RulePack.formatPeriodRange(draft.periodStart, draft.periodEnd),
            title = "关闭时段锁？",
            confirmLabel = "确认关闭",
            breathReason = com.life.mindfulnessapp.data.analytics.HaEvents.BreathReason.PERIOD_DISABLE,
            appName = draft.appName,
            packageName = draft.packageName,
            onConfirm = {
                onChange(draft.copy(periodOn = false))
                pendingClosePeriod = false
            },
            onDismiss = { pendingClosePeriod = false }
        )
    }
}

@Composable
private fun ValueRow(
    label: String,
    value: String,
    emphasize: Boolean,
    mutedLabel: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = if (mutedLabel) colors.onSurfaceVariant.copy(alpha = 0.75f) else colors.onSurfaceVariant,
            fontSize = 13.sp
        )
        Text(
            value,
            color = when {
                emphasize -> LogoGreen
                else -> colors.onBackground.copy(alpha = 0.85f)
            },
            fontSize = 15.sp
        )
    }
}

@Composable
private fun Hairline() {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(colors.onSurface.copy(alpha = 0.12f))
    )
}

private fun browseSummary(minutes: Int): String =
    if (minutes <= 0) "不限" else "$minutes 分"

@Composable
private fun DailyLimitEditPage(
    initialTotal: Int,
    initialBrowse: Int,
    baselineDailyOn: Boolean,
    baselineTotal: Int,
    appName: String,
    packageName: String,
    isDailyLimitHitToday: (suspend (baseMinutes: Int) -> Boolean)?,
    onBack: () -> Unit,
    onConfirmed: (total: Int, browse: Int) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var totalText by remember(initialTotal) { mutableStateOf(initialTotal.toString()) }
    var browseText by remember(initialBrowse) {
        mutableStateOf(if (initialBrowse <= 0) "" else initialBrowse.toString())
    }
    var browseUnlimited by remember(initialBrowse) { mutableStateOf(initialBrowse <= 0) }
    var pendingLight by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var pendingBreath by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var checkingHit by remember { mutableStateOf(false) }
    val totalParsed = totalText.toIntOrNull()
    val totalValid = totalParsed != null && totalParsed in DAILY_MIN..DAILY_MAX
    val browseParsed = when {
        browseUnlimited -> 0
        browseText.isBlank() -> null
        else -> browseText.toIntOrNull()
    }
    val browseValid = browseUnlimited || (
        browseParsed != null &&
            browseParsed in BROWSE_MIN..BROWSE_MAX &&
            (totalParsed == null || browseParsed <= totalParsed)
        )
    val valid = totalValid && browseValid && !checkingHit

    fun askConfirm() {
        val total = totalParsed?.coerceIn(DAILY_MIN, DAILY_MAX) ?: return
        val browse = if (browseUnlimited) 0 else {
            (browseParsed ?: return).coerceIn(BROWSE_MIN, total)
        }
        if (total == initialTotal && browse == initialBrowse.coerceAtLeast(0)) {
            onBack()
            return
        }
        // 已开日限且放宽：若今日已触顶 → 呼吸门；否则轻确认
        val loosening = baselineDailyOn && total > baselineTotal
        if (!loosening || isDailyLimitHitToday == null) {
            pendingLight = total to browse
            return
        }
        checkingHit = true
        scope.launch {
            val hit = runCatching { isDailyLimitHitToday(baselineTotal) }.getOrDefault(false)
            checkingHit = false
            if (hit) pendingBreath = total to browse
            else pendingLight = total to browse
        }
    }

    SecondaryPageScaffold(title = "日限额", onBack = onBack) { padding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text("总限额", color = colors.onSurfaceVariant, fontSize = 13.sp)
        NumberField(
            text = totalText,
            onText = { totalText = it.filter(Char::isDigit).take(3) },
            unit = "分",
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(Modifier.height(28.dp))
        Text(
            "└ ${BrowseCasualIntent.DISPLAY_LABEL}",
            color = colors.onSurfaceVariant.copy(alpha = 0.75f),
            fontSize = 13.sp
        )
        if (browseUnlimited) {
            Text(
                "不限",
                color = colors.onBackground.copy(alpha = 0.85f),
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            NumberField(
                text = browseText,
                onText = { browseText = it.filter(Char::isDigit).take(3) },
                unit = "分",
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        Text(
            if (browseUnlimited) "改为限额" else "改为不限",
            color = LogoGreen,
            fontSize = 12.sp,
            modifier = Modifier
                .padding(top = 10.dp)
                .clickable {
                    browseUnlimited = !browseUnlimited
                    if (!browseUnlimited && browseText.isBlank()) {
                        browseText = DEFAULT_BROWSE_MINUTES.toString()
                    }
                }
        )
        if (!browseUnlimited &&
            browseParsed != null &&
            totalParsed != null &&
            browseParsed > totalParsed
        ) {
            Text(
                "${BrowseCasualIntent.DISPLAY_LABEL}不能超过日总限额",
                color = colors.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("取消", color = colors.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.clickable(onClick = onBack))
            Text(
                "确认",
                color = if (valid) LogoGreen else colors.onSurfaceVariant,
                fontSize = 16.sp,
                modifier = Modifier.clickable(enabled = valid, onClick = { askConfirm() })
            )
        }
    }
    }

    pendingLight?.let { (total, browse) ->
        AlertDialog(
            onDismissRequest = { pendingLight = null },
            title = {
                Text(
                    if (browse <= 0) "日限改为 $total 分，${BrowseCasualIntent.DISPLAY_LABEL}不限？"
                    else "日限改为 $total 分，${BrowseCasualIntent.DISPLAY_LABEL} $browse 分？"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingLight = null
                        onConfirmed(total, browse)
                    }
                ) {
                    Text("确认", color = LogoGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingLight = null }) {
                    Text("再改改", color = colors.onSurfaceVariant)
                }
            }
        )
    }

    pendingBreath?.let { (total, browse) ->
        val analytics = remember(context) {
            dagger.hilt.android.EntryPointAccessors.fromApplication(
                context.applicationContext,
                com.life.mindfulnessapp.ui.applist.AnalyticsEntryPoint::class.java
            ).analyticsRepository()
        }
        com.life.mindfulnessapp.ui.common.BreathCostGateDialog(
            title = "触顶后放宽限额？",
            subtitle = "今日额度已用完。把手指按在圆里，配合呼吸，再保存这次放宽。",
            confirmHint = "确认保存",
            onCompleted = {
                pendingBreath = null
                analytics.trackBreathGatePass(
                    reason = com.life.mindfulnessapp.data.analytics.HaEvents.BreathReason.DAILY_LOOSEN,
                    app = appName,
                    pkg = packageName
                )
                onConfirmed(total, browse)
            },
            onDismiss = { pendingBreath = null }
        )
    }
}

@Composable
private fun NumberField(
    text: String,
    onText: (String) -> Unit,
    unit: String,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        BasicTextField(
            value = text,
            onValueChange = onText,
            modifier = Modifier.width(100.dp),
            textStyle = TextStyle(
                color = colors.onBackground,
                fontSize = 36.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.5).sp
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            cursorBrush = SolidColor(LogoGreen)
        )
        Text(
            unit,
            color = colors.onSurfaceVariant,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
        )
    }
}

private enum class PeriodEditPending {
    Save,
    Delete
}

@Composable
private fun PeriodEditPage(
    startMinute: Int,
    endMinute: Int,
    lockIsLive: Boolean,
    appName: String,
    packageName: String,
    onBack: () -> Unit,
    onDone: (start: Int, end: Int) -> Unit,
    onDelete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    var start by remember(startMinute) { mutableStateOf(startMinute) }
    var end by remember(endMinute) { mutableStateOf(endMinute) }
    var pending by remember { mutableStateOf<PeriodEditPending?>(null) }
    val rangeChanged = start != startMinute || end != endMinute
    val rangeLabel = RulePack.formatPeriodRange(startMinute, endMinute)

    fun requestDone() {
        // 已武装且改了起止：过呼吸；首次添加或未改动直接落
        if (lockIsLive && rangeChanged) {
            pending = PeriodEditPending.Save
        } else {
            onDone(start, end)
        }
    }

    fun requestDelete() {
        if (lockIsLive) pending = PeriodEditPending.Delete
        else onDelete()
    }

    SecondaryPageScaffold(
        title = "时段锁",
        onBack = onBack,
        subtitle = RulePack.formatPeriodRange(start, end)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                TimeTap("开始", start) {
                    TimePickerDialog(context, { _, h, m ->
                        start = h * 60 + m
                    }, start / 60, start % 60, true).show()
                }
                TimeTap("结束", end) {
                    TimePickerDialog(context, { _, h, m ->
                        end = h * 60 + m
                    }, end / 60, end % 60, true).show()
                }
            }
            Spacer(Modifier.weight(1f))
            if (lockIsLive) {
                Text(
                    "删除",
                    color = colors.error.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    modifier = Modifier
                        .padding(bottom = 18.dp)
                        .clickable(onClick = ::requestDelete)
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "取消",
                    color = colors.onSurfaceVariant,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable(onClick = onBack)
                )
                Text(
                    "完成",
                    color = LogoGreen,
                    fontSize = 16.sp,
                    modifier = Modifier.clickable(onClick = ::requestDone)
                )
            }
        }
    }

    pending?.let { action ->
        com.life.mindfulnessapp.ui.applist.PeriodLockDisableGateDialog(
            commitment = "",
            windowLabel = rangeLabel,
            title = when (action) {
                PeriodEditPending.Save -> "改动锁定中的时段？"
                PeriodEditPending.Delete -> "删除此时段？"
            },
            confirmLabel = when (action) {
                PeriodEditPending.Save -> "确认改动"
                PeriodEditPending.Delete -> "确认删除"
            },
            breathReason = com.life.mindfulnessapp.data.analytics.HaEvents.BreathReason.PERIOD_WINDOW,
            appName = appName,
            packageName = packageName,
            onConfirm = {
                when (action) {
                    PeriodEditPending.Save -> onDone(start, end)
                    PeriodEditPending.Delete -> onDelete()
                }
                pending = null
            },
            onDismiss = { pending = null }
        )
    }
}

@Composable
private fun TimeTap(label: String, minute: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.clickable(onClick = onClick)) {
        Text(label, color = colors.onSurfaceVariant, fontSize = 11.sp)
        Text(RulePack.fmtMinute(minute), color = colors.onBackground, fontSize = 22.sp)
    }
}
