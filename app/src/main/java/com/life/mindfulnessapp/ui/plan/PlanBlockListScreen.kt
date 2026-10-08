package com.life.mindfulnessapp.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.domain.model.PeriodLockPolicy
import com.life.mindfulnessapp.domain.model.PlanBlock
import com.life.mindfulnessapp.domain.model.PlanBlockPolicy
import com.life.mindfulnessapp.ui.applist.PeriodLockDisableGateDialog
import com.life.mindfulnessapp.ui.theme.LogoGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanBlockListScreen(
    onNavigateBack: () -> Unit,
    onNavigateEdit: (planId: String?) -> Unit,
    viewModel: PlanBlockListViewModel = hiltViewModel()
) {
    val plans by viewModel.plans.collectAsState()
    val cs = MaterialTheme.colorScheme
    val active = remember(plans) { PlanBlockPolicy.activeNow(plans) }
    var pendingDisableId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "守计划",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "按时段安排要做的事，并限制干扰 App",
                            fontSize = 12.sp,
                            color = cs.onSurface.copy(alpha = 0.42f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateEdit(null) },
                containerColor = LogoGreen
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "新建计划",
                    tint = Color.White
                )
            }
        },
        containerColor = cs.background
    ) { padding ->
        if (plans.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "还没有计划",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = cs.onSurface
                    )
                    Box(modifier = Modifier.height(8.dp))
                    Text(
                        text = "例如：深度工作时段锁住刷机 App",
                        fontSize = 13.sp,
                        color = cs.onSurface.copy(alpha = 0.42f)
                    )
                    Box(modifier = Modifier.height(20.dp))
                    TextButton(onClick = { onNavigateEdit(null) }) {
                        Text(text = "新建第一个计划", color = LogoGreen)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (active != null) {
                    item(key = "active_banner") {
                        ActivePlanBanner(plan = active)
                    }
                }
                items(plans, key = { it.id }) { plan ->
                    PlanBlockRow(
                        plan = plan,
                        activeNow = plan.enabled &&
                            PeriodLockPolicy.wouldBeActiveNow(plan.toPeriodWindow()),
                        onClick = { onNavigateEdit(plan.id) },
                        onToggle = { on ->
                            if (!on && plan.enabled) {
                                pendingDisableId = plan.id
                            } else {
                                viewModel.setEnabled(plan.id, on)
                            }
                        }
                    )
                }
                item { Box(modifier = Modifier.height(72.dp)) }
            }
        }
    }

    pendingDisableId?.let { id ->
        val plan = plans.find { it.id == id }
        PeriodLockDisableGateDialog(
            commitment = plan?.title.orEmpty(),
            windowLabel = plan?.label().orEmpty(),
            title = "关闭日程锁？",
            confirmLabel = "确认关闭",
            breathReason = com.life.mindfulnessapp.data.analytics.HaEvents.BreathReason.SCHEDULE_WEAKEN,
            onConfirm = {
                viewModel.setEnabled(id, false)
                pendingDisableId = null
            },
            onDismiss = { pendingDisableId = null }
        )
    }
}

@Composable
private fun ActivePlanBanner(plan: PlanBlock) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(LogoGreen.copy(alpha = 0.12f))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = "进行中",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = LogoGreen
        )
        Box(modifier = Modifier.height(4.dp))
        Text(
            text = plan.title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = cs.onSurface
        )
        plan.whyLine()?.let { why ->
            Text(
                text = why,
                fontSize = 12.sp,
                color = LogoGreen.copy(alpha = 0.9f)
            )
        }
        Text(
            text = "${plan.label()} · ${PlanBlockPolicy.remainingUnlockLabel(plan)}",
            fontSize = 12.sp,
            color = cs.onSurface.copy(alpha = 0.45f)
        )
    }
}

@Composable
private fun PlanBlockRow(
    plan: PlanBlock,
    activeNow: Boolean,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cs.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = plan.title.ifBlank { "未命名计划" },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface
                )
                Box(modifier = Modifier.width(8.dp))
                Text(
                    text = plan.scene.label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = cs.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(cs.surfaceVariant.copy(alpha = 0.55f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                if (activeNow) {
                    Box(modifier = Modifier.width(8.dp))
                    Text(
                        text = "生效中",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = LogoGreen,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(LogoGreen.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            plan.whyLine()?.let { why ->
                Text(
                    text = why,
                    fontSize = 12.sp,
                    color = LogoGreen.copy(alpha = 0.85f)
                )
            }
            Text(
                text = "${plan.label()} · ${plan.daysLabel()}",
                fontSize = 13.sp,
                color = cs.onSurface.copy(alpha = 0.45f)
            )
            Text(
                text = "限制 ${plan.packageNames.size} 个 App",
                fontSize = 12.sp,
                color = cs.onSurface.copy(alpha = 0.36f)
            )
        }
        Switch(
            checked = plan.enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedTrackColor = LogoGreen.copy(alpha = 0.55f),
                checkedThumbColor = LogoGreen
            )
        )
    }
}
