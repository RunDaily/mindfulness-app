package com.life.mindfulnessapp.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.PlanBlockListActivity
import com.life.mindfulnessapp.ui.theme.LogoGreen

/**
 * 能力 · 探索：功能入口总览（后续可扩展更多卡片）。
 */
@Composable
fun ExploreHubPane(
    onNavigateToUsageRank: () -> Unit,
    onNavigateToTimeRuler: () -> Unit,
    onNavigateToWalkAwareness: () -> Unit,
    onNavigateToPlanBlocks: (() -> Unit)? = null,
    onNavigateToMeaningfulThings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val openPlanBlocks = onNavigateToPlanBlocks ?: {
        context.startActivity(PlanBlockListActivity.createIntent(context))
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "从真实场景出发",
            fontSize = 12.sp,
            color = cs.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )
        ExploreHubEntryCard(
            title = "有意义的事",
            subtitle = "预置好事清单，也可自定义；拦截页可直达",
            onClick = onNavigateToMeaningfulThings
        )
        ExploreHubEntryCard(
            title = "守计划",
            subtitle = "按时段安排要做的事，并限制干扰 App",
            onClick = openPlanBlocks
        )
        ExploreHubEntryCard(
            title = "时间之尺",
            subtitle = "坑位 App · 日 / 周占用面积，对照当日日志",
            onClick = onNavigateToTimeRuler
        )
        ExploreHubEntryCard(
            title = "步行觉察",
            subtitle = "边走边看手机时 · 路况锚点提醒注意安全",
            onClick = onNavigateToWalkAwareness
        )
        Text(
            text = "用量参考",
            fontSize = 11.sp,
            color = cs.onSurface.copy(alpha = 0.32f),
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp, bottom = 2.dp)
        )
        ExploreHubEntryCard(
            title = "全量榜",
            subtitle = "与今日热同源 · 可按打开排 · 主路在今日",
            onClick = onNavigateToUsageRank
        )
    }
}

@Composable
private fun ExploreHubEntryCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cs.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = cs.onSurface,
                letterSpacing = (-0.3).sp
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = cs.onSurface.copy(alpha = 0.42f),
                lineHeight = 18.sp
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = LogoGreen.copy(alpha = 0.75f),
            modifier = Modifier.size(22.dp)
        )
    }
    Spacer(modifier = Modifier.height(4.dp))
}
