package com.life.mindfulnessapp.ui.applist

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.theme.MindfulGreen40
import com.life.mindfulnessapp.ui.vip.VipUpgradeDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    viewModel: AppListViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToVip: () -> Unit = {}
) {
    val apps by viewModel.apps.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val focusManager = LocalFocusManager.current
    val isAtFreeLimit by viewModel.isAtFreeLimit.collectAsState()
    val showVipUpgradeDialog by viewModel.showVipUpgradeDialog.collectAsState()
    val vipLevel by viewModel.vipLevel.collectAsState()
    val biweeklyLoading by viewModel.biweeklyLoading.collectAsState()
    val biweeklyData by viewModel.biweeklyData.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }

    val monitoredCount = apps.count { it.isMonitored }
    val cs = MaterialTheme.colorScheme
    val isDark = cs.background.red < 0.5f  // 简单判断当前主题

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "选择监控应用",
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            fontSize = 17.sp
                        )
                        if (monitoredCount > 0) {
                            if (vipLevel <= 0) {
                                // 免费版显示使用量 / 上限
                                Text(
                                    "已监控 $monitoredCount / ${AppPreferences.FREE_MONITOR_LIMIT} 个（免费版）",
                                    fontSize = 11.sp,
                                    color = if (isAtFreeLimit) Color(0xFFFFCC44).copy(alpha = 0.9f)
                                            else LogoGreen.copy(alpha = 0.85f)
                                )
                            } else {
                                Text(
                                    "已监控 $monitoredCount 个应用",
                                    fontSize = 11.sp,
                                    color = LogoGreen.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cs.surfaceVariant
                )
            )
        },
        containerColor = cs.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 搜索栏
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                placeholder = { Text("搜索应用名称", color = cs.onBackground.copy(alpha = 0.28f), fontSize = 14.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = cs.onBackground.copy(alpha = 0.28f))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "清除", tint = cs.onBackground.copy(alpha = 0.5f))
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LogoGreen,
                    unfocusedBorderColor = cs.outline.copy(alpha = 0.4f),
                    focusedContainerColor = cs.surface,
                    unfocusedContainerColor = cs.surface,
                    focusedTextColor = cs.onSurface,
                    unfocusedTextColor = cs.onSurface
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MindfulGreen40, strokeWidth = 2.dp)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    val monitoredApps = apps.filter { it.isMonitored }
                    val unmonitoredApps = apps.filter { !it.isMonitored }

                    if (monitoredApps.isNotEmpty()) {
                        item { SectionHeader(title = "已监控 (${monitoredApps.size})", cs = cs) }
                        items(monitoredApps, key = { "monitored_${it.packageName}" }) { app ->
                            AppListItem(
                                appInfo = app,
                                cs = cs,
                                onAdd = {
                                    selectedApp = app
                                    viewModel.loadBiweeklyUsage(app.packageName)
                                    showAddDialog = true
                                },
                                onRemove = { viewModel.removeFromMonitor(app.packageName) },
                                onEdit = {
                                    selectedApp = app
                                    viewModel.loadBiweeklyUsage(app.packageName)
                                    showAddDialog = true
                                }
                            )
                        }
                    }

                    if (unmonitoredApps.isNotEmpty()) {
                        item { SectionHeader(title = "所有应用 (${unmonitoredApps.size})", cs = cs) }
                        items(unmonitoredApps, key = { "unmonitored_${it.packageName}" }) { app ->
                            AppListItem(
                                appInfo = app,
                                cs = cs,
                                onAdd = {
                                    selectedApp = app
                                    viewModel.loadBiweeklyUsage(app.packageName)
                                    showAddDialog = true
                                },
                                onRemove = { viewModel.removeFromMonitor(app.packageName) },
                                onEdit = {
                                    selectedApp = app
                                    viewModel.loadBiweeklyUsage(app.packageName)
                                    showAddDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog && selectedApp != null) {
        AppConfigBottomSheet(
            appInfo = selectedApp!!,
            isVip = vipLevel > 0,
            biweeklyLoading = biweeklyLoading,
            biweeklyData = biweeklyData,
            onConfirm = { daily, weekly, timeLimitOn, overMsg ->
                val added = viewModel.addToMonitor(
                    appInfo = selectedApp!!,
                    dailyLimitMinutes = daily,
                    weeklyLimitMinutes = weekly,
                    timeLimitEnabled = timeLimitOn,
                    overTimeMessage = overMsg
                )
                if (added) {
                    viewModel.clearBiweeklyData()
                    showAddDialog = false
                }
                // 若 added=false，ViewModel 已触发 showVipUpgradeDialog，弹窗会在下方显示
            },
            onDismiss = {
                viewModel.clearBiweeklyData()
                showAddDialog = false
            }
        )
    }

    // VIP 升级引导弹窗
    if (showVipUpgradeDialog) {
        VipUpgradeDialog(
            isDarkTheme = isDark,
            cardColor = cs.surface,
            textPrimary = cs.onSurface,
            textSecondary = cs.onSurfaceVariant,
            borderColor = cs.outline,
            accentGreen = LogoGreen,
            onDismiss = { viewModel.dismissVipUpgradeDialog() },
            onUpgrade = {
                viewModel.dismissVipUpgradeDialog()
                onNavigateToVip()
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String, cs: ColorScheme) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = cs.onBackground.copy(alpha = 0.35f),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        letterSpacing = 0.8.sp
    )
}

@Composable
private fun AppListItem(
    appInfo: AppInfo,
    cs: ColorScheme,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onEdit: () -> Unit
) {
    // timeActive：跟随主题 primary（夜间=护眼绿，日间=深翠绿），统一品牌色
    val timeActive = cs.primary
    val cardBg = if (appInfo.isMonitored) cs.surfaceVariant else cs.surface
    val borderColor = if (appInfo.isMonitored) LogoGreen.copy(alpha = 0.2f) else cs.outline.copy(alpha = 0.15f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .then(if (appInfo.isMonitored) Modifier.clickable(onClick = onEdit) else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppIcon(drawable = appInfo.icon, modifier = Modifier.size(46.dp))

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = appInfo.appName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (appInfo.isUninstalled) cs.onSurface.copy(alpha = 0.45f) else cs.onSurface
                )
                if (appInfo.isMonitored && !appInfo.isUninstalled) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = null,
                            tint = timeActive.copy(alpha = 0.85f),
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = if (appInfo.dailyLimitMinutes > 0) "每日 ${appInfo.dailyLimitMinutes}分" else "点击设置时长",
                            fontSize = 11.sp,
                            color = timeActive
                        )
                    }
                } else if (!appInfo.isMonitored) {
                    Text(
                        text = appInfo.packageName,
                        fontSize = 11.sp,
                        color = cs.onSurface.copy(alpha = 0.22f),
                        maxLines = 1
                    )
                }
            }

            // 操作按钮
            if (appInfo.isMonitored) {
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.RemoveCircleOutline,
                        contentDescription = "移除监控",
                        tint = Color(0xFFE74C3C)
                    )
                }
            } else {
                IconButton(onClick = onAdd) {
                    Icon(
                        Icons.Default.AddCircleOutline,
                        contentDescription = "添加监控",
                        tint = MindfulGreen40
                    )
                }
            }
        }
    }
}

@Composable
fun AppIcon(drawable: Drawable?, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    if (drawable != null) {
        val bitmap = remember(drawable) { drawable.toBitmap().asImageBitmap() }
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier.clip(RoundedCornerShape(10.dp)))
    } else {
        Box(
            modifier = modifier.clip(RoundedCornerShape(10.dp)).background(cs.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Android, contentDescription = null, tint = cs.onSurface.copy(alpha = 0.22f))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  AppConfigBottomSheet  ——  新增/编辑 App 监控策略的底部弹出配置面板
//  风格参照设计图：深色卡片 + 绿色/蓝色激活态 + 圆角底部弹层
// ─────────────────────────────────────────────────────────────────────────────

/** 将分钟数格式化为 HH:MM 字符串 */
private fun minuteToHHMM(totalMin: Int): String {
    val h = (totalMin / 60).coerceIn(0, 23)
    val m = totalMin % 60
    return "%02d:%02d".format(h, m)
}

/** 将分钟数格式化为易读显示文本（去掉零值，更自然） */
private fun minuteToHM(totalMin: Int): String {
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h > 0 && m > 0 -> "${h}小时${m}分"
        h > 0           -> "${h}小时"
        else            -> "${m}分钟"
    }
}

/** 快速预设时长选项（分钟 to 显示文本），上限扩展至 4 小时 */
private val QUICK_PRESETS = listOf(
    15  to "15分",
    30  to "30分",
    60  to "1小时",
    90  to "1.5h",
    120 to "2小时",
    180 to "3小时"
)

// ─────────────────────────────────────────────────────────────────────────────
//  CustomTimeInputDialog  ——  点击大字时间后弹出的自定义时长输入弹窗
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun CustomTimeInputDialog(
    initialMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current

    // 分拆为小时 + 分钟两个输入框，更直观
    var hourInput by remember { mutableStateOf((initialMinutes / 60).toString()) }
    var minuteInput by remember { mutableStateOf((initialMinutes % 60).toString()) }

    val totalMinutes = (hourInput.toIntOrNull() ?: 0) * 60 + (minuteInput.toIntOrNull() ?: 0)
    val isValid = totalMinutes in 5..480  // 允许 5 分钟 ~ 8 小时

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                "自定义时长",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "输入每日最多使用时长（5分钟 ~ 8小时）",
                    fontSize = 12.sp,
                    color = cs.onSurface.copy(alpha = 0.45f)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 小时输入
                    OutlinedTextField(
                        value = hourInput,
                        onValueChange = { v ->
                            if (v.length <= 1 && (v.isEmpty() || v.all { it.isDigit() })) {
                                hourInput = v
                            }
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("小时", fontSize = 12.sp) },
                        suffix = { Text("h", fontSize = 13.sp, color = cs.onSurface.copy(alpha = 0.5f)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = cs.tertiary,
                            unfocusedBorderColor = cs.outline.copy(alpha = 0.4f),
                            focusedContainerColor = cs.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedContainerColor = cs.surfaceVariant.copy(alpha = 0.3f),
                        )
                    )
                    Text(
                        ":",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Light,
                        color = cs.onSurface.copy(alpha = 0.3f)
                    )
                    // 分钟输入
                    OutlinedTextField(
                        value = minuteInput,
                        onValueChange = { v ->
                            if (v.length <= 2 && (v.isEmpty() || (v.all { it.isDigit() } && (v.toIntOrNull() ?: 0) <= 59))) {
                                minuteInput = v
                            }
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("分钟", fontSize = 12.sp) },
                        suffix = { Text("m", fontSize = 13.sp, color = cs.onSurface.copy(alpha = 0.5f)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            if (isValid) onConfirm(totalMinutes)
                        }),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = cs.tertiary,
                            unfocusedBorderColor = cs.outline.copy(alpha = 0.4f),
                            focusedContainerColor = cs.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedContainerColor = cs.surfaceVariant.copy(alpha = 0.3f),
                        )
                    )
                }
                // 合计预览
                if (totalMinutes > 0) {
                    Text(
                        text = if (isValid) "= ${minuteToHM(totalMinutes)}"
                               else "请输入 5分钟 ~ 8小时",
                        fontSize = 12.sp,
                        color = if (isValid) cs.tertiary.copy(alpha = 0.8f)
                                else cs.error.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(totalMinutes) },
                enabled = isValid
            ) {
                Text(
                    "确定",
                    color = if (isValid) LogoGreen else cs.onSurface.copy(alpha = 0.3f),
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = cs.onSurface.copy(alpha = 0.45f))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppConfigBottomSheet(
    appInfo: AppInfo,
    isVip: Boolean = false,
    biweeklyLoading: Boolean = false,
    biweeklyData: List<DailyUsageBar> = emptyList(),
    onConfirm: (
        dailyMinutes: Int,
        weeklyMinutes: Int,
        timeLimitEnabled: Boolean,
        overTimeMessage: String
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val isEditing = appInfo.isMonitored
    val prevDailyLimit = appInfo.dailyLimitMinutes.coerceAtLeast(5)

    // 上限扩展到 480 分钟（8小时）
    val DAILY_MAX = 480

    var dailyLimit by remember { mutableIntStateOf(prevDailyLimit) }
    var weeklyLimit by remember {
        mutableIntStateOf(
            if (appInfo.weeklyLimitMinutes > 0) appInfo.weeklyLimitMinutes else prevDailyLimit * 7
        )
    }
    var weeklyExpanded by remember { mutableStateOf(false) }
    // 自定义时长输入弹窗状态
    var showCustomInput by remember { mutableStateOf(false) }

    val cs = MaterialTheme.colorScheme
    val sheetBg = cs.surface
    val timeActive = cs.tertiary
    val surfaceCard = cs.surfaceVariant
    val chipUnselected = cs.surfaceVariant.copy(alpha = 0.7f)

    fun applyDailyLimit(minutes: Int) {
        dailyLimit = minutes.coerceIn(5, DAILY_MAX)
        if (weeklyLimit < dailyLimit) weeklyLimit = dailyLimit * 7
    }

    val changeSign = when {
        !isEditing                  -> 0
        dailyLimit < prevDailyLimit -> -1
        dailyLimit > prevDailyLimit -> 1
        else                        -> 0
    }

    // 当前值是否命中预设（用于判断是否显示"自定义"chip 的选中态）
    val isCustomSelected = QUICK_PRESETS.none { it.first == dailyLimit }

    if (showCustomInput) {
        CustomTimeInputDialog(
            initialMinutes = dailyLimit,
            onConfirm = { applyDailyLimit(it); showCustomInput = false },
            onDismiss = { showCustomInput = false }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = sheetBg,
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(cs.onSurface.copy(alpha = 0.18f))
                )
            }
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
        ) {
            // ── 顶部 App 信息头 ───────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(drawable = appInfo.icon, modifier = Modifier.size(44.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appInfo.appName,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface
                    )
                    Text(
                        text = if (isEditing) "当前：${minuteToHM(prevDailyLimit)} / 天"
                               else "设置每天最多使用多久",
                        fontSize = 12.sp,
                        color = if (isEditing) timeActive.copy(alpha = 0.7f)
                                else cs.onSurface.copy(alpha = 0.38f)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = cs.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // ── 两周使用柱状图区域 ──────────────────────────────────────────
            BiweeklyUsageChart(
                isLoading = biweeklyLoading,
                data = biweeklyData,
                dailyLimitMinutes = dailyLimit,
                cs = cs,
                accentColor = timeActive
            )

            HorizontalDivider(
                modifier = Modifier.padding(top = 8.dp),
                color = cs.outlineVariant.copy(alpha = 0.15f)
            )

            // ── 时长配置区 ────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 24.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {

                // ── 每日上限主区域 ────────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                    // 标签行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "每日上限",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = cs.onSurface.copy(alpha = 0.55f),
                            letterSpacing = 0.6.sp
                        )
                        // 变化方向徽标（编辑且有变化时才显示）
                        if (isEditing && changeSign != 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (changeSign < 0) LogoGreen.copy(alpha = 0.12f)
                                        else Color(0xFFFF6B4A).copy(alpha = 0.12f)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = if (changeSign < 0) Icons.Default.ArrowDownward
                                                  else Icons.Default.ArrowUpward,
                                    contentDescription = null,
                                    modifier = Modifier.size(11.dp),
                                    tint = if (changeSign < 0) LogoGreen else Color(0xFFFF6B4A)
                                )
                                Text(
                                    text = if (changeSign < 0) "收紧了" else "放宽了",
                                    fontSize = 11.sp,
                                    color = if (changeSign < 0) LogoGreen else Color(0xFFFF6B4A),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // ── 大字时间展示（可点击 → 自定义输入）──────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(surfaceCard)
                            .clickable { showCustomInput = true }
                            .padding(vertical = 18.dp, horizontal = 16.dp),
                    ) {
                        AnimatedContent(
                            targetState = minuteToHM(dailyLimit),
                            transitionSpec = {
                                fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                            },
                            label = "timeDisplay",
                            modifier = Modifier.align(Alignment.Center)
                        ) { timeText ->
                            Text(
                                text = timeText,
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Bold,
                                color = timeActive,
                                textAlign = TextAlign.Center
                            )
                        }
                        // 右下角"编辑"提示，暗示可点击
                        Row(
                            modifier = Modifier.align(Alignment.BottomEnd),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "自定义",
                                modifier = Modifier.size(11.dp),
                                tint = timeActive.copy(alpha = 0.35f)
                            )
                            Text(
                                "自定义",
                                fontSize = 10.sp,
                                color = timeActive.copy(alpha = 0.35f)
                            )
                        }
                    }

                    // ── 预设 Chip 网格（2行×3列 + 末尾"自定义"） ─────────────
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // 6个预设分两行，每行3个
                        QUICK_PRESETS.chunked(3).forEach { rowPresets ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowPresets.forEach { (minutes, label) ->
                                    val isSelected = dailyLimit == minutes
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (isSelected) timeActive.copy(alpha = 0.18f)
                                                else chipUnselected
                                            )
                                            .then(
                                                if (isSelected) Modifier.border(
                                                    1.dp,
                                                    timeActive.copy(alpha = 0.6f),
                                                    RoundedCornerShape(10.dp)
                                                ) else Modifier
                                            )
                                            .clickable { applyDailyLimit(minutes) }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold
                                                         else FontWeight.Normal,
                                            color = if (isSelected) timeActive
                                                    else cs.onSurface.copy(alpha = 0.55f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── 步进微调区（替换纯滑块，精度更可控）────────────────
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        // 分组标题，比刻度更重，视觉层级清晰
                        Text(
                            "精细调整",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = cs.onSurface.copy(alpha = 0.45f)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // -5 按钮
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(cs.surfaceVariant)
                                    .clickable { applyDailyLimit(dailyLimit - 5) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "−5",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = cs.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            // 滑块占剩余宽度
                            Slider(
                                value = dailyLimit.toFloat(),
                                onValueChange = { applyDailyLimit(it.toInt()) },
                                valueRange = 5f..DAILY_MAX.toFloat(),
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = timeActive,
                                    activeTrackColor = timeActive,
                                    inactiveTrackColor = cs.outline.copy(alpha = 0.2f)
                                )
                            )
                            // +5 按钮
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(cs.surfaceVariant)
                                    .clickable { applyDailyLimit(dailyLimit + 5) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "+5",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = cs.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                        // 刻度提示，颜色比"精细调整"标题更淡，层级分明
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("5分", fontSize = 10.sp, color = cs.onSurface.copy(alpha = 0.22f))
                            Text("1小时", fontSize = 10.sp, color = cs.onSurface.copy(alpha = 0.22f))
                            Text("2小时", fontSize = 10.sp, color = cs.onSurface.copy(alpha = 0.22f))
                            Text("4小时", fontSize = 10.sp, color = cs.onSurface.copy(alpha = 0.22f))
                            Text("8小时", fontSize = 10.sp, color = cs.onSurface.copy(alpha = 0.22f))
                        }
                    }
                }

                // ── 每周上限（可折叠，VIP 专属） ─────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 折叠标题行
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                if (isVip) weeklyExpanded = !weeklyExpanded
                            }
                            .padding(vertical = 10.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                if (isVip) Icons.Default.DateRange else Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = if (isVip) LogoGreen.copy(alpha = 0.7f)
                                       else Color(0xFFFFCC44).copy(alpha = 0.6f)
                            )
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        "每周上限",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isVip) cs.onSurface.copy(alpha = 0.7f)
                                                else cs.onSurface.copy(alpha = 0.35f)
                                    )
                                    if (!isVip) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFFFFCC44).copy(alpha = 0.12f))
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                "VIP",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFFFCC44)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = if (!isVip) "升级 VIP 解锁每周上限设置"
                                           else if (weeklyExpanded) "防止周末集中透支"
                                           else minuteToHM(weeklyLimit) + " / 周",
                                    fontSize = 11.sp,
                                    color = if (!isVip) Color(0xFFFFCC44).copy(alpha = 0.5f)
                                            else if (weeklyExpanded) cs.onSurface.copy(alpha = 0.35f)
                                            else LogoGreen.copy(alpha = 0.6f)
                                )
                            }
                        }
                        val arrowRotation by animateFloatAsState(
                            targetValue = if (weeklyExpanded && isVip) 180f else 0f,
                            animationSpec = tween(200),
                            label = "arrow"
                        )
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier
                                .size(18.dp)
                                .rotate(arrowRotation),
                            tint = cs.onSurface.copy(alpha = if (isVip) 0.3f else 0.15f)
                        )
                    }

                    // 展开内容（仅 VIP 可见）
                    AnimatedVisibility(
                        visible = weeklyExpanded && isVip,
                        enter = expandVertically(tween(220)) + fadeIn(tween(220)),
                        exit = shrinkVertically(tween(180)) + fadeOut(tween(150))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(surfaceCard)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "本周总上限",
                                    fontSize = 13.sp,
                                    color = cs.onSurface.copy(alpha = 0.55f)
                                )
                                Text(
                                    minuteToHM(weeklyLimit),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = LogoGreen
                                )
                            }
                            Slider(
                                value = weeklyLimit.toFloat(),
                                onValueChange = { weeklyLimit = it.toInt() },
                                valueRange = dailyLimit.toFloat()..(dailyLimit * 7)
                                    .coerceAtLeast(dailyLimit + 1)
                                    .coerceAtMost(DAILY_MAX * 7).toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = LogoGreen,
                                    activeTrackColor = LogoGreen,
                                    inactiveTrackColor = cs.outline.copy(alpha = 0.2f)
                                )
                            )
                            Text(
                                text = "约每日上限 × ${"%.1f".format(weeklyLimit.toFloat() / dailyLimit)} 天",
                                fontSize = 11.sp,
                                color = cs.onSurface.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }

            // ── 保存按钮 ──────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Button(
                    onClick = {
                        onConfirm(dailyLimit, weeklyLimit, true, appInfo.overTimeMessage)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LogoGreen,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "设为每天 ${minuteToHM(dailyLimit)}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
//  BiweeklyUsageChart  ——  最近两周每日使用时长柱状图
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 两周柱状图：loading 时显示骨架占位，数据就绪后绘制柱形 + 日期标签。
 * 每根柱子代表一天的总使用分钟数，今天的柱子用强调色显示。
 * 若所有天均为 0（无数据），显示空状态提示。
 */
@Composable
internal fun BiweeklyUsageChart(
    isLoading: Boolean,
    data: List<DailyUsageBar>,
    dailyLimitMinutes: Int,
    cs: ColorScheme,
    accentColor: Color
) {
    val maxMinutes = data.maxOfOrNull { it.totalMinutes }?.coerceAtLeast(1f) ?: 1f
    val hasData = data.any { it.totalMinutes > 0f }
    val barColorToday = accentColor
    val barColorDim = accentColor.copy(alpha = 0.38f)
    val limitColor = Color(0xFFE74C3C).copy(alpha = 0.55f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 4.dp)
    ) {
        // 标题行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "最近两周使用",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = cs.onSurface.copy(alpha = 0.45f),
                letterSpacing = 0.6.sp
            )
            if (!isLoading && hasData) {
                val totalAvg = data.map { it.totalMinutes }.average().toFloat()
                Text(
                    text = "均 ${minuteToHM(totalAvg.toInt())} / 天",
                    fontSize = 11.sp,
                    color = accentColor.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (isLoading) {
            // ── Loading 骨架 ──────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    repeat(14) { i ->
                        val shimmerAlpha = if (i % 2 == 0) 0.12f else 0.07f
                        val barH = if (i % 3 == 0) 60.dp else if (i % 3 == 1) 40.dp else 80.dp
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(barH)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(cs.onSurface.copy(alpha = shimmerAlpha))
                        )
                    }
                }
            }
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        color = accentColor.copy(alpha = 0.6f),
                        strokeWidth = 1.5.dp
                    )
                    Text(
                        "加载使用数据…",
                        fontSize = 11.sp,
                        color = cs.onSurface.copy(alpha = 0.35f)
                    )
                }
            }
        } else if (!hasData) {
            // ── 无数据空状态 ──────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.BarChart,
                        contentDescription = null,
                        tint = cs.onSurface.copy(alpha = 0.18f),
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        "最近两周暂无使用记录",
                        fontSize = 12.sp,
                        color = cs.onSurface.copy(alpha = 0.3f)
                    )
                }
            }
        } else {
            // ── 柱状图 ────────────────────────────────────────────────────────
            val chartHeight = 100.dp
            val labelHeight = 18.dp
            val limitFraction = (dailyLimitMinutes.toFloat() / maxMinutes).coerceIn(0f, 1f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight + labelHeight)
            ) {
                // 画布：柱子 + 限制线
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeight)
                ) {
                    val totalBars = data.size
                    val barWidth = size.width / (totalBars * 1.5f)
                    val gap = barWidth * 0.5f
                    val availH = size.height

                    // 限制线（每日上限参考线）
                    if (dailyLimitMinutes > 0 && limitFraction <= 1f) {
                        val lineY = availH * (1f - limitFraction)
                        drawLine(
                            color = limitColor,
                            start = Offset(0f, lineY),
                            end = Offset(size.width, lineY),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                floatArrayOf(8.dp.toPx(), 4.dp.toPx())
                            )
                        )
                    }

                    // 柱子
                    data.forEachIndexed { i, bar ->
                        val fraction = (bar.totalMinutes / maxMinutes).coerceIn(0f, 1f)
                        val barH = availH * fraction
                        val x = i * (barWidth + gap) + gap / 2f
                        val y = availH - barH

                        val color = if (bar.dayOffset == 0) barColorToday
                                    else barColorDim

                        if (barH > 0f) {
                            drawRoundRect(
                                color = color,
                                topLeft = Offset(x, y),
                                size = Size(barWidth, barH),
                                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                            )
                        } else {
                            // 无数据：极细底部线
                            drawRoundRect(
                                color = cs.onSurface.copy(alpha = 0.08f),
                                topLeft = Offset(x, availH - 2.dp.toPx()),
                                size = Size(barWidth, 2.dp.toPx()),
                                cornerRadius = CornerRadius(1.dp.toPx())
                            )
                        }
                    }
                }

                // 日期标签行（Canvas 下方叠加）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    // 只显示第1、7、14天（首、中、末）标签，避免拥挤
                    val labelIndices = setOf(0, 6, 13)
                    data.forEachIndexed { i, bar ->
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            if (i in labelIndices || bar.dayOffset == 0) {
                                Text(
                                    text = if (bar.dayOffset == 0) "今天" else bar.dateLabel,
                                    fontSize = 9.sp,
                                    color = if (bar.dayOffset == 0)
                                        accentColor.copy(alpha = 0.85f)
                                    else
                                        cs.onSurface.copy(alpha = 0.3f),
                                    fontWeight = if (bar.dayOffset == 0) FontWeight.SemiBold
                                                 else FontWeight.Normal,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // 图例说明行
            if (dailyLimitMinutes > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(14.dp)
                            .height(1.5.dp)
                            .background(limitColor)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "每日上限 ${minuteToHM(dailyLimitMinutes)}",
                        fontSize = 10.sp,
                        color = cs.onSurface.copy(alpha = 0.35f)
                    )
                }
            }
        }
    }
}

/** 时长滑块组件（配置面板专用） */
@Composable
internal fun ConfigLimitSlider(
    label: String,
    value: Int,
    range: IntRange,
    accentColor: Color = MindfulGreen40,
    onValueChange: (Int) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 13.sp, color = cs.onSurface.copy(alpha = 0.55f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(cs.surfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = minuteToHM(value),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface
                )
            }
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = cs.outline.copy(alpha = 0.3f)
            )
        )
    }
}


@Composable
internal fun LimitSlider(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit
) {
    ConfigLimitSlider(label = label, value = value, range = range, onValueChange = onValueChange)
}
