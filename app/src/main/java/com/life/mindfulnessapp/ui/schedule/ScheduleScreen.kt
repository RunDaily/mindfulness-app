package com.life.mindfulnessapp.ui.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.life.mindfulnessapp.ScheduleLockEditActivity
import com.life.mindfulnessapp.domain.model.PlanBlock
import com.life.mindfulnessapp.ui.common.SecondaryPageScaffold
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.theme.ScheduleSlotNow
import java.util.Calendar

/** 日程锁：发现子页；添加/编辑走 [ScheduleLockEditActivity]。 */
@Composable
fun ScheduleScreen(
    onBack: () -> Unit,
    viewModel: ScheduleViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val todayLine = rememberTodayLine()

    SecondaryPageScaffold(
        title = "日程锁",
        subtitle = todayLine,
        onBack = onBack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            if (state.plans.isEmpty()) {
                Text(
                    "还没有时段锁",
                    color = colors.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 28.dp)
                )
            } else {
                state.plans.forEach { plan ->
                    ScheduleLockRow(
                        plan = plan,
                        isActive = plan.id == state.activeId,
                        appName = { pkg -> state.appLabels[pkg] ?: pkg },
                        onClick = {
                            context.startActivity(
                                ScheduleLockEditActivity.createIntent(context, plan.id)
                            )
                        }
                    )
                }
            }

            Text(
                "＋ 添加时段",
                color = LogoGreen.copy(alpha = 0.9f),
                fontSize = 13.sp,
                modifier = Modifier
                    .padding(top = 14.dp, bottom = 24.dp)
                    .clickable {
                        context.startActivity(ScheduleLockEditActivity.createIntent(context))
                    }
            )
        }
    }
}

@Composable
private fun rememberTodayLine(): String {
    val cal = Calendar.getInstance()
    val week = arrayOf("日", "一", "二", "三", "四", "五", "六")
    return "今天 · 周${week[cal.get(Calendar.DAY_OF_WEEK) - 1]}"
}

@Composable
private fun ScheduleLockRow(
    plan: PlanBlock,
    isActive: Boolean,
    appName: (String) -> String,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val nameColor = if (isActive) ScheduleSlotNow else colors.onBackground
    val stateColor = if (isActive) ScheduleSlotNow else colors.onSurfaceVariant.copy(alpha = 0.55f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(top = 12.dp)
    ) {
        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.35f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(plan.title, color = nameColor, fontSize = 15.sp)
            Text(
                if (isActive) "生效中" else if (plan.enabled) "未生效" else "已关闭",
                color = stateColor,
                fontSize = 11.sp
            )
        }
        Text(
            "${plan.label()} · ${plan.daysLabel()}",
            color = colors.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            plan.scopeLine(appName),
            color = colors.onSurfaceVariant.copy(alpha = 0.7f),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 3.dp, bottom = 2.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
