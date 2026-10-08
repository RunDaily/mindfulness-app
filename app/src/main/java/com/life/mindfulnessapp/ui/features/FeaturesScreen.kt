package com.life.mindfulnessapp.ui.features

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.ui.applist.AppListViewModel
import com.life.mindfulnessapp.ui.applist.CapabilityCopy
import com.life.mindfulnessapp.ui.applist.MonitorManageScreen
import com.life.mindfulnessapp.ui.theme.CapabilityForm
import com.life.mindfulnessapp.ui.theme.CapabilityKind
import com.life.mindfulnessapp.ui.theme.CapabilityMarkPlate
import com.life.mindfulnessapp.ui.theme.MonitorCapability

private val TabAnimMs = 280

private enum class FeaturesInnerTab(val label: String) {
    Guide("入门"),
    Manage("管理"),
    Explore("探索")
}

/**
 * 底栏「能力」。
 * - 入门：大卡片认识三能力（身份文案 → 详情）
 * - 管理：按能力维度管理已绑定应用
 * - 探索：功能入口（二八统计等）
 *
 * [preferManageTab] 为 true 时强制切到「管理」（如首页坑位「+」）；消费后回调 [onPreferManageConsumed]。
 */
@Composable
fun FeaturesScreen(
    viewModel: AppListViewModel = hiltViewModel(),
    preferManageTab: Boolean = false,
    onPreferManageConsumed: () -> Unit = {},
    onNavigateToAdd: (CapabilityKind?) -> Unit = {},
    onNavigateToEdit: (packageName: String) -> Unit = {},
    onNavigateToVip: () -> Unit = {},
    onNavigateToGuideDetail: (CapabilityKind) -> Unit = {},
    onNavigateToExploreUsageRank: () -> Unit = {},
    onNavigateToExploreTimeRuler: () -> Unit = {},
    onNavigateToWalkAwareness: () -> Unit = {},
    onNavigateToPlanBlocks: (() -> Unit)? = null,
    onNavigateToMeaningfulThings: () -> Unit = {},
    onNavigateToExploreApp: (packageName: String) -> Unit = {}
) {
    var tabName by rememberSaveable { mutableStateOf(FeaturesInnerTab.Guide.name) }
    val selectedTab = FeaturesInnerTab.entries
        .firstOrNull { it.name == tabName }
        ?: FeaturesInnerTab.Guide

    LaunchedEffect(preferManageTab) {
        if (preferManageTab) {
            tabName = FeaturesInnerTab.Manage.name
            onPreferManageConsumed()
        }
    }

    val cs = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
            .statusBarsPadding()
    ) {
        FeaturesInnerTabBar(
            selected = selectedTab,
            onSelect = { tabName = it.name }
        )

        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                val enterOffset = { width: Int -> if (forward) width / 12 else -width / 12 }
                val exitOffset = { width: Int -> if (forward) -width / 12 else width / 12 }
                (
                    fadeIn(tween(TabAnimMs, easing = FastOutSlowInEasing)) +
                        slideInHorizontally(tween(TabAnimMs, easing = FastOutSlowInEasing), enterOffset)
                    ) togetherWith (
                    fadeOut(tween(TabAnimMs / 2, easing = FastOutSlowInEasing)) +
                        slideOutHorizontally(tween(TabAnimMs, easing = FastOutSlowInEasing), exitOffset)
                    )
            },
            label = "features_inner_tab",
            modifier = Modifier.fillMaxSize()
        ) { tab ->
            when (tab) {
                FeaturesInnerTab.Guide -> {
                    CapabilityGuidePane(
                        onCardClick = onNavigateToGuideDetail
                    )
                }
                FeaturesInnerTab.Manage -> {
                    MonitorManageScreen(
                        viewModel = viewModel,
                        embedded = true,
                        scrollToTopToken = tab.name,
                        onNavigateToAdd = onNavigateToAdd,
                        onNavigateToEdit = onNavigateToEdit,
                        onNavigateToVip = onNavigateToVip
                    )
                }
                FeaturesInnerTab.Explore -> {
                    ExploreHubPane(
                        onNavigateToUsageRank = onNavigateToExploreUsageRank,
                        onNavigateToTimeRuler = onNavigateToExploreTimeRuler,
                        onNavigateToWalkAwareness = onNavigateToWalkAwareness,
                        onNavigateToPlanBlocks = onNavigateToPlanBlocks,
                        onNavigateToMeaningfulThings = onNavigateToMeaningfulThings
                    )
                }
            }
        }
    }
}

@Composable
private fun FeaturesInnerTabBar(
    selected: FeaturesInnerTab,
    onSelect: (FeaturesInnerTab) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    // 与「我」Tab 页标题对齐：26.sp / 水平 20.dp / 上下 14·16
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 14.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FeaturesInnerTab.entries.forEach { tab ->
            val active = tab == selected
            val scale by animateFloatAsState(
                targetValue = if (active) 1f else 0.92f,
                animationSpec = tween(TabAnimMs, easing = FastOutSlowInEasing),
                label = "tab_scale_${tab.name}"
            )
            val alpha by animateFloatAsState(
                targetValue = if (active) 1f else 0.38f,
                animationSpec = tween(TabAnimMs, easing = FastOutSlowInEasing),
                label = "tab_alpha_${tab.name}"
            )
            Column(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 2.dp, vertical = 2.dp)
            ) {
                Text(
                    text = tab.label,
                    fontSize = if (active) 26.sp else 16.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = cs.onSurface.copy(alpha = alpha),
                    letterSpacing = if (active) (-0.5).sp else 0.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .width(if (active) 22.dp else 0.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (active) cs.onSurface.copy(alpha = 0.88f)
                            else cs.onSurface.copy(alpha = 0f)
                        )
                )
            }
        }
    }
}

@Composable
private fun CapabilityGuidePane(
    onCardClick: (CapabilityKind) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CapabilityCopy.All.forEach { copy ->
            CapabilityGuideCard(
                copy = copy,
                onClick = { onCardClick(copy.kind) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }
}

/** 入门列表卡：surface 与背景色差分界，双成就用紧凑扫读 badge。 */
@Composable
private fun CapabilityGuideCard(
    copy: CapabilityCopy,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val accent = MonitorCapability.accent(copy.kind)
    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier = modifier
            .clip(shape)
            .background(cs.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = copy.label,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface,
                    letterSpacing = (-0.3).sp
                )
                Text(
                    text = copy.slogan,
                    fontSize = 13.sp,
                    color = cs.onSurface.copy(alpha = 0.40f),
                    lineHeight = 18.sp
                )
            }
            CapabilityMarkPlate(
                kind = copy.kind,
                form = CapabilityForm.Standard,
                plateSize = 38.dp,
                corner = 11.dp
            )
        }
        GuideListIdentityBadges(
            identityOne = copy.identityOne,
            identityTwo = copy.identityTwo,
            accent = accent
        )
    }
}
