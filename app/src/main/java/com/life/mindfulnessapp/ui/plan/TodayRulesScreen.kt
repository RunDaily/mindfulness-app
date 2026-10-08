package com.life.mindfulnessapp.ui.plan

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.life.mindfulnessapp.domain.model.UsageDigestRow
import com.life.mindfulnessapp.ui.theme.LogoGreen

@Composable
fun TodayRulesScreen(
    onOpenPlan: () -> Unit,
    onOpenTodayReceipt: () -> Unit,
    onOpenApp: (String) -> Unit = {},
    onOpenPhoneApp: (String) -> Unit = {},
    viewModel: TodayRulesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val colors = MaterialTheme.colorScheme
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        PageTitle("今日", state.heldHint)
        when {
            state.loading -> { /* 保持上一帧，避免闪空 */ }
            state.rows.isNotEmpty() -> {
                state.rows.forEach { row ->
                    val used = if (row.limitMinutes != null) {
                        "${row.usedMinutes} / ${row.limitMinutes} 分"
                    } else {
                        "${row.usedMinutes} 分"
                    }
                    val opens = if (row.openLimit != null) {
                        "打开 ${row.opens} / ${row.openLimit}" + if (row.exhausted) " · 已尽" else ""
                    } else {
                        "打开 ${row.opens}"
                    }
                    HairlineRow(
                        title = row.appName,
                        meta = row.summary,
                        trailing = used,
                        trailingSub = opens,
                        warn = row.exhausted,
                        onClick = { onOpenApp(row.packageName) }
                    )
                }
                Spacer(Modifier.height(8.dp))
                TodayReceiptPeek(
                    heldCount = state.heldCount,
                    enterCount = state.enterCount,
                    onClick = onOpenTodayReceipt
                )
            }
            state.phoneHeat.isNotEmpty() -> {
                state.phoneHeat.forEach { row ->
                    PhoneHeatRow(row = row, onClick = { onOpenPhoneApp(row.packageName) })
                }
                Spacer(Modifier.height(12.dp))
                HintChip("去方案添加", LogoGreen, onOpenPlan)
            }
            !state.hasUsagePermission -> {
                Text(
                    "授权使用情况后，可以看到这台手机近 7 日用得最多的 App。",
                    color = colors.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Spacer(Modifier.height(12.dp))
                HintChip("去方案添加", LogoGreen, onOpenPlan)
            }
            else -> {
                HintChip("去方案添加 App", LogoGreen, onOpenPlan)
            }
        }
    }
}

@Composable
private fun PhoneHeatRow(
    row: UsageDigestRow,
    onClick: () -> Unit
) {
    val meta = buildString {
        append("日均 ${row.avgDailyMinutes} 分")
        if (row.avgDailyOpens > 0) append(" · ${row.avgDailyOpens} 次")
        if (row.nightSharePercent >= 30) append(" · 晚上偏多")
    }
    val totalApprox = row.avgDailyMinutes * 7
    val trailing = when {
        totalApprox >= 60 -> String.format("%.1f 时", totalApprox / 60.0)
        totalApprox > 0 -> "$totalApprox 分"
        else -> "—"
    }
    HairlineRow(
        title = row.appName,
        meta = meta,
        trailing = trailing,
        trailingSub = "",
        packageName = row.packageName,
        onClick = onClick
    )
}
