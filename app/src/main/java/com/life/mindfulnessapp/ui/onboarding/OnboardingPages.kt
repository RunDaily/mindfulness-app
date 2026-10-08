package com.life.mindfulnessapp.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.domain.model.AppCapabilityFit
import com.life.mindfulnessapp.domain.model.CapabilityFitDimension
import com.life.mindfulnessapp.domain.model.CapabilityFitLevel
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.domain.model.BatchPickAppRow
import com.life.mindfulnessapp.domain.model.BatchPickSortMode
import com.life.mindfulnessapp.domain.model.UsageInsights
import com.life.mindfulnessapp.domain.usecase.PermissionStatus
import com.life.mindfulnessapp.ui.applist.AppIcon
import com.life.mindfulnessapp.ui.applist.AppUsageGrid
import com.life.mindfulnessapp.ui.applist.AppUsageMetricsText
import com.life.mindfulnessapp.ui.applist.AppUsageSortRow
import com.life.mindfulnessapp.ui.applist.CapabilityCopy
import com.life.mindfulnessapp.ui.theme.CapabilityForm
import com.life.mindfulnessapp.ui.theme.CapabilityKind
import com.life.mindfulnessapp.ui.theme.CapabilityMark
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.theme.LogoGreenBright
import com.life.mindfulnessapp.ui.theme.MindfulGreen40
import com.life.mindfulnessapp.ui.theme.MonitorCapability
import com.life.mindfulnessapp.util.AppUsageFormat
import kotlinx.coroutines.launch

// ── 使用情况权限 ──────────────────────────────────────────────────────────────

@Composable
fun OnboardingUsageAccessPage(
    hasUsageStats: Boolean,
    onGrantUsage: () -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        OnboardingTopBar(title = "看见使用情况", onBack = onBack)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "心锚需要了解你打开了哪些 App，才能在门口等你，并帮你看见真实用量。此权限只需开启一次，后续步骤不会重复索要。",
            fontSize = 14.sp,
            color = cs.onBackground.copy(alpha = 0.52f),
            lineHeight = 22.sp
        )
        Spacer(Modifier.height(24.dp))
        OnboardingPermissionCard(
            icon = Icons.Default.QueryStats,
            title = "使用情况访问",
            description = "仅用于本机统计近 7 日使用时长与打开次数，不会上传你的使用记录",
            isGranted = hasUsageStats,
            onGrant = onGrantUsage
        )
        Spacer(Modifier.weight(1f))
        if (hasUsageStats) {
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MindfulGreen40),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("查看我的使用概况", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        } else {
            Button(
                onClick = onGrantUsage,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MindfulGreen40),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("去开启", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                Text("稍后再说", color = cs.onSurface.copy(alpha = 0.5f))
            }
        }
    }
}

// ── 用量镜子 ──────────────────────────────────────────────────────────────────

@Composable
fun OnboardingUsageMirrorPage(
    insights: UsageInsights?,
    rows: List<BatchPickAppRow>,
    sortMode: BatchPickSortMode,
    usageLoading: Boolean,
    onSortModeChange: (BatchPickSortMode) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showBackToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 2 || listState.firstVisibleItemScrollOffset > 400
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item(key = "header") {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    OnboardingTopBar(title = "你的手机使用概况", onBack = onBack, showBack = true)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "近 7 日日均（不含今天）· 系统统计",
                        fontSize = 12.sp,
                        color = cs.onSurface.copy(alpha = 0.42f)
                    )
                    Spacer(Modifier.height(16.dp))
                    insights?.let { UsageInsightsCard(it) }
                }
            }
            item(key = "sort") {
                AppUsageSortRow(
                    sortMode = sortMode,
                    usageLoading = usageLoading,
                    hasUsagePermission = true,
                    caption = null,
                    onSortModeChange = onSortModeChange
                )
            }
            item(key = "grid") {
                AppUsageGrid(
                    rows = rows,
                    usageLoading = usageLoading,
                    hasUsagePermission = true,
                    sortMode = sortMode,
                    onAppClick = null
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(cs.background.copy(alpha = 0.96f))
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MindfulGreen40),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("继续 · 了解如何介入", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }

        if (showBackToTop) {
            FloatingActionButton(
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 20.dp, bottom = 88.dp),
                containerColor = cs.surface,
                contentColor = LogoGreen
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "回到顶部")
            }
        }
    }
}

@Composable
private fun UsageInsightsCard(insights: UsageInsights) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cs.surface)
            .border(1.dp, cs.outline.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        InsightLine(
            label = "有记录的 App",
            value = "${insights.trackedAppCount} 个"
        )
        insights.topByDuration?.let { top ->
            InsightLine(
                label = "用时最长",
                value = "${top.app.appName} · ${AppUsageFormat.avgDailyDurationShort(top.usage.avgDailySeconds)}"
            )
        }
        insights.topByLaunches?.let { top ->
            InsightLine(
                label = "打开最勤",
                value = "${top.app.appName} · ${AppUsageFormat.avgDailyLaunchesShort(top.usage.avgDailyLaunches)}"
            )
        }
    }
}

@Composable
private fun InsightLine(label: String, value: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = cs.onSurface.copy(alpha = 0.45f))
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = cs.onSurface.copy(alpha = 0.88f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
            textAlign = TextAlign.End
        )
    }
}

// ── 能力导读 + 推荐 ───────────────────────────────────────────────────────────

@Composable
fun OnboardingGuidePage(
    hasUsageData: Boolean,
    recommendations: List<AppCapabilityFit>,
    onPickRecommendation: (AppCapabilityFit) -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item(key = "head") {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    OnboardingTopBar(title = "心锚能怎么帮你", onBack = onBack)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (hasUsageData) {
                            "根据近 7 日打开频率、日总时长、单次深度三项信号，判断哪种能力更匹配。"
                        } else {
                            "开启使用情况访问后，可获得基于数据的匹配建议。下方先了解三种能力。"
                        },
                        fontSize = 14.sp,
                        color = cs.onSurface.copy(alpha = 0.48f),
                        lineHeight = 22.sp
                    )
                }
            }

            if (hasUsageData) {
                item(key = "framework") {
                    CapabilityFitFrameworkCard(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            if (recommendations.isNotEmpty()) {
                item(key = "rec_title") {
                    Text(
                        "值得先系锚",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
                items(recommendations.size, key = { recommendations[it].app.listKey }) { index ->
                    val fit = recommendations[index]
                    FitRecommendationCard(
                        fit = fit,
                        onClick = { onPickRecommendation(fit) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }

            item(key = "cap_title") {
                Text(
                    "三种能力速览",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
            items(CapabilityCopy.All.size, key = { CapabilityCopy.All[it].kind.name }) { index ->
                val copy = CapabilityCopy.All[index]
                CapabilityBriefCard(
                    copy = copy,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                Text("稍后再系锚", color = cs.onSurface.copy(alpha = 0.5f), fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun CapabilityFitFrameworkCard(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cs.surface)
            .border(1.dp, cs.outline.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "匹配逻辑",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurface
        )
        FitSignalRow(
            title = "打开频率",
            desc = "日均次数高 → 更像无意识顺手点开 → 偏意图门"
        )
        FitSignalRow(
            title = "日总时长",
            desc = "每天累计久 → 时间黑洞 → 偏时长锁"
        )
        FitSignalRow(
            title = "单次深度",
            desc = "单次短、次数多 → 碎片化刷；单次长 → 沉浸 → 辅助判断"
        )
        Text(
            "时段锁需你自选关键时段，常在已有觉察/额度基础上叠加。",
            fontSize = 11.sp,
            color = cs.onSurface.copy(alpha = 0.4f),
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun FitSignalRow(title: String, desc: String) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = LogoGreen)
        Text(desc, fontSize = 11.sp, color = cs.onSurface.copy(alpha = 0.48f), lineHeight = 16.sp)
    }
}

@Composable
private fun FitRecommendationCard(
    fit: AppCapabilityFit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val accent = MonitorCapability.accent(fit.primary)
    val label = MonitorCapability.label(fit.primary)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cs.surface)
            .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(drawable = fit.app.icon, modifier = Modifier.size(48.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        fit.app.appName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    CapabilityMark(
                        kind = fit.primary,
                        form = CapabilityForm.Compact,
                        active = true,
                        tint = accent,
                        size = 12.dp
                    )
                }
                Text(
                    fit.dataSnapshot,
                    fontSize = 10.sp,
                    color = cs.onSurface.copy(alpha = 0.42f)
                )
                Text(
                    fit.headline,
                    fontSize = 12.sp,
                    color = cs.onSurface.copy(alpha = 0.62f),
                    lineHeight = 17.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${fit.primaryDimension.score}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                Text("匹配", fontSize = 10.sp, color = cs.onSurface.copy(alpha = 0.38f))
            }
        }
        FitScoreChips(dimensions = fit.dimensions, highlight = fit.primary)
        fit.primaryDimension.reasons.take(2).forEach { reason ->
            Text(
                "· $reason",
                fontSize = 11.sp,
                color = cs.onSurface.copy(alpha = 0.45f),
                lineHeight = 16.sp
            )
        }
        Text(
            "推荐 $label · 点此配置",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            modifier = Modifier.align(Alignment.End)
        )
    }
}

@Composable
private fun FitScoreChips(
    dimensions: List<CapabilityFitDimension>,
    highlight: com.life.mindfulnessapp.ui.theme.CapabilityKind
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        dimensions.forEach { dim ->
            val accent = MonitorCapability.accent(dim.kind)
            val name = MonitorCapability.label(dim.kind)
            val emphasized = dim.kind == highlight
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (emphasized) accent.copy(alpha = 0.14f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    "$name ${dim.score}",
                    fontSize = 10.sp,
                    fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (emphasized) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }
        }
    }
}

@Composable
private fun CapabilityBriefCard(
    copy: CapabilityCopy,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val accent = MonitorCapability.accent(copy.kind)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cs.surface.copy(alpha = 0.65f))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        CapabilityMark(
            kind = copy.kind,
            form = CapabilityForm.Standard,
            active = true,
            tint = accent,
            size = 28.dp
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(copy.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
            Text(copy.description, fontSize = 12.sp, color = cs.onSurface.copy(alpha = 0.48f), lineHeight = 18.sp)
            copy.scenes.firstOrNull()?.let { scene ->
                Text("· $scene", fontSize = 11.sp, color = accent.copy(alpha = 0.85f), lineHeight = 16.sp)
            }
        }
    }
}

// ── 收尾权限 ──────────────────────────────────────────────────────────────────

@Composable
fun OnboardingFinishPermissionsPage(
    permStatus: PermissionStatus,
    missing: List<FinishPermissionKind>,
    usageAlreadyGranted: Boolean,
    onGrantOverlay: () -> Unit,
    onGrantBattery: () -> Unit,
    onGrantAccessibility: () -> Unit,
    onComplete: () -> Unit,
    onBack: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val allReady = missing.isEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        OnboardingTopBar(
            title = if (allReady) "已准备就绪" else "补全剩余权限",
            onBack = onBack
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = when {
                allReady -> "核心权限已就绪，可以开始使用心锚了。"
                missing.contains(FinishPermissionKind.Overlay) ->
                    "悬浮窗是拦截层出现的必要条件。使用情况${if (usageAlreadyGranted) "已开启，不会重复索要" else "可在设置中补开"}。"
                else -> "以下为可选优化项，有助于后台稳定拦截；可跳过直接进入。"
            },
            fontSize = 14.sp,
            color = cs.onBackground.copy(alpha = 0.5f),
            lineHeight = 22.sp
        )
        Spacer(Modifier.height(20.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (allReady) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(LogoGreen.copy(alpha = 0.1f))
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "✓ 悬浮窗",
                            fontSize = 13.sp,
                            color = LogoGreen,
                            fontWeight = FontWeight.Medium
                        )
                        if (usageAlreadyGranted) {
                            Text(
                                "✓ 使用情况访问",
                                fontSize = 13.sp,
                                color = LogoGreen,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            "建议保持电池优化与无障碍保活开启，可在「我的」中随时调整。",
                            fontSize = 12.sp,
                            color = cs.onSurface.copy(alpha = 0.45f),
                            lineHeight = 18.sp
                        )
                    }
                }
            } else {
                if (missing.contains(FinishPermissionKind.Overlay)) {
                    OnboardingPermissionCard(
                        icon = Icons.Default.Layers,
                        title = "悬浮窗权限",
                        description = "打开被守护的 App 时，让心锚在门口等你",
                        isGranted = false,
                        onGrant = onGrantOverlay,
                        required = true
                    )
                }
                if (missing.contains(FinishPermissionKind.Battery)) {
                    OnboardingPermissionCard(
                        icon = Icons.Default.BatteryFull,
                        title = "忽略电池优化",
                        description = "建议 · 降低后台被杀、门突然消失的概率",
                        isGranted = false,
                        onGrant = onGrantBattery,
                        recommended = true
                    )
                }
                if (missing.contains(FinishPermissionKind.Accessibility)) {
                    OnboardingPermissionCard(
                        icon = Icons.Default.AccessibilityNew,
                        title = "无障碍保活",
                        description = "建议 · 保持监控服务稳定运行",
                        isGranted = false,
                        onGrant = onGrantAccessibility,
                        recommended = true
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onComplete,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MindfulGreen40),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "进入心锚",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

// ── 共用组件 ──────────────────────────────────────────────────────────────────

@Composable
fun OnboardingTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    showBack: Boolean = onBack != null
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showBack && onBack != null) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(cs.onSurface.copy(alpha = 0.06f))
                    .clickable { onBack() }
                    .padding(8.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = cs.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.size(12.dp))
        }
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = cs.onBackground
        )
    }
}

@Composable
fun OnboardingPermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    onGrant: () -> Unit,
    required: Boolean = false,
    recommended: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cs.surface)
            .border(
                width = 1.dp,
                color = if (isGranted) LogoGreen.copy(alpha = 0.25f) else cs.outline.copy(alpha = 0.2f),
                shape = RoundedCornerShape(18.dp)
            )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (isGranted) LogoGreen.copy(alpha = 0.2f) else cs.onSurface.copy(alpha = 0.05f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Default.CheckCircle else icon,
                    contentDescription = null,
                    tint = if (isGranted) LogoGreen else cs.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isGranted) LogoGreenBright else cs.onSurface
                    )
                    if (required && !isGranted) {
                        TagChip("必备", Color(0xFFE85D5D))
                    } else if (recommended && !isGranted) {
                        TagChip("建议", Color(0xFFE8941A))
                    }
                }
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = cs.onSurface.copy(alpha = if (isGranted) 0.45f else 0.35f),
                    lineHeight = 18.sp
                )
            }
            if (!isGranted) {
                TextButton(onClick = onGrant) {
                    Text("去开启", color = LogoGreen, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun TagChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, fontSize = 10.sp, color = color)
    }
}
