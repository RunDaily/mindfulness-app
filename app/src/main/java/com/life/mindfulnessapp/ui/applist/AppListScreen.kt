package com.life.mindfulnessapp.ui.applist

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.theme.MindfulGreen40

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    viewModel: AppListViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val apps by viewModel.apps.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val focusManager = LocalFocusManager.current

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }

    val monitoredCount = apps.count { it.isMonitored }
    val cs = MaterialTheme.colorScheme

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
                            Text(
                                "已监控 $monitoredCount 个应用",
                                fontSize = 11.sp,
                                color = LogoGreen.copy(alpha = 0.85f)
                            )
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
                                onAdd = { selectedApp = app; showAddDialog = true },
                                onRemove = { viewModel.removeFromMonitor(app.packageName) },
                                onEdit = { selectedApp = app; showAddDialog = true }
                            )
                        }
                    }

                    if (unmonitoredApps.isNotEmpty()) {
                        item { SectionHeader(title = "所有应用 (${unmonitoredApps.size})", cs = cs) }
                        items(unmonitoredApps, key = { "unmonitored_${it.packageName}" }) { app ->
                            AppListItem(
                                appInfo = app,
                                cs = cs,
                                onAdd = { selectedApp = app; showAddDialog = true },
                                onRemove = { viewModel.removeFromMonitor(app.packageName) },
                                onEdit = { selectedApp = app; showAddDialog = true }
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
            onConfirm = { daily, weekly, timeLimitOn, overMsg ->
                viewModel.addToMonitor(
                    appInfo = selectedApp!!,
                    dailyLimitMinutes = daily,
                    weeklyLimitMinutes = weekly,
                    timeLimitEnabled = timeLimitOn,
                    overTimeMessage = overMsg
                )
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
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
    val timeActive = Color(0xFF2979FF)
    val cardBg = if (appInfo.isMonitored) cs.secondaryContainer else cs.surface
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

/** 快速预设时长选项（分钟 to 显示文本） */
private val QUICK_PRESETS = listOf(
    15  to "15分",
    30  to "30分",
    45  to "45分",
    60  to "1小时",
    90  to "1.5h",
    120 to "2小时"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppConfigBottomSheet(
    appInfo: AppInfo,
    onConfirm: (
        dailyMinutes: Int,
        weeklyMinutes: Int,
        timeLimitEnabled: Boolean,
        overTimeMessage: String
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var dailyLimit by remember { mutableIntStateOf(appInfo.dailyLimitMinutes.coerceAtLeast(5)) }
    var weeklyLimit by remember {
        mutableIntStateOf(
            if (appInfo.weeklyLimitMinutes > 0) appInfo.weeklyLimitMinutes else dailyLimit * 7
        )
    }
    // 每日输入框文本（允许直接键入分钟数）
    var dailyInputText by remember { mutableStateOf(dailyLimit.toString()) }
    var dailyInputError by remember { mutableStateOf(false) }

    val cs = MaterialTheme.colorScheme
    val sheetBg = Color(0xFF0E1117)
    val timeActive = Color(0xFF2979FF)
    val surfaceVariant = Color(0xFF1A2030)

    fun applyDailyLimit(minutes: Int) {
        dailyLimit = minutes.coerceIn(5, 120)
        dailyInputText = dailyLimit.toString()
        dailyInputError = false
        if (weeklyLimit < dailyLimit) weeklyLimit = dailyLimit * 7
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = sheetBg,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // ── 顶部 App 信息头 ───────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(drawable = appInfo.icon, modifier = Modifier.size(52.dp))
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appInfo.appName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "设置每日使用上限",
                        fontSize = 13.sp,
                        color = cs.onSurface.copy(alpha = 0.45f)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = cs.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.25f))

            // ── 时长配置区 ────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // ── 每日上限：快速预设 + 滑块 + 输入框 ───────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "每日上限",
                            fontSize = 13.sp,
                            color = cs.onSurface.copy(alpha = 0.55f)
                        )
                        // 直接输入分钟数框
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = dailyInputText,
                                onValueChange = { raw ->
                                    dailyInputText = raw.filter { it.isDigit() }.take(3)
                                    val parsed = dailyInputText.toIntOrNull()
                                    if (parsed != null && parsed in 5..120) {
                                        dailyLimit = parsed
                                        dailyInputError = false
                                        if (weeklyLimit < parsed) weeklyLimit = parsed * 7
                                    } else {
                                        dailyInputError = dailyInputText.isNotEmpty()
                                    }
                                },
                                modifier = Modifier.width(72.dp),
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 14.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                singleLine = true,
                                isError = dailyInputError,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        val parsed = dailyInputText.toIntOrNull()
                                        if (parsed != null) applyDailyLimit(parsed)
                                        else { dailyInputText = dailyLimit.toString(); dailyInputError = false }
                                    }
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = timeActive,
                                    unfocusedBorderColor = surfaceVariant,
                                    focusedContainerColor = surfaceVariant,
                                    unfocusedContainerColor = surfaceVariant,
                                    errorBorderColor = Color(0xFFE74C3C)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "分钟",
                                fontSize = 12.sp,
                                color = cs.onSurface.copy(alpha = 0.45f)
                            )
                        }
                    }

                    // 快速预设按钮行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        QUICK_PRESETS.forEach { (minutes, label) ->
                            val isSelected = dailyLimit == minutes
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) timeActive.copy(alpha = 0.18f)
                                        else surfaceVariant
                                    )
                                    .border(
                                        width = if (isSelected) 1.dp else 0.dp,
                                        color = if (isSelected) timeActive.copy(alpha = 0.7f)
                                                else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { applyDailyLimit(minutes) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) timeActive else cs.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                    // 滑块
                    Slider(
                        value = dailyLimit.toFloat(),
                        onValueChange = { applyDailyLimit(it.toInt()) },
                        valueRange = 5f..120f,
                        steps = 0,
                        colors = SliderDefaults.colors(
                            thumbColor = timeActive,
                            activeTrackColor = timeActive,
                            inactiveTrackColor = cs.outline.copy(alpha = 0.3f)
                        )
                    )

                    // 当前选择说明
                    Text(
                        text = "= ${minuteToHM(dailyLimit)}",
                        fontSize = 12.sp,
                        color = timeActive.copy(alpha = 0.75f)
                    )
                }

                HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.15f))

                // ── 每周上限滑块 ──────────────────────────────────────────────
                ConfigLimitSlider(
                    label = "每周上限",
                    value = weeklyLimit,
                    range = dailyLimit..(dailyLimit * 7).coerceAtLeast(dailyLimit + 1).coerceAtMost(120 * 7),
                    accentColor = LogoGreen,
                    onValueChange = { weeklyLimit = it }
                )
            }

            // ── 保存按钮 ──────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Button(
                    onClick = {
                        onConfirm(
                            dailyLimit,
                            weeklyLimit,
                            true,
                            appInfo.overTimeMessage
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LogoGreen,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("保存", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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
                    .background(Color(0xFF1A2030))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = minuteToHM(value),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
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

// LocalTextStyle 引用辅助
private val LocalTextStyle @Composable get() = androidx.compose.material3.LocalTextStyle

@Composable
internal fun LimitSlider(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit
) {
    ConfigLimitSlider(label = label, value = value, range = range, onValueChange = onValueChange)
}
