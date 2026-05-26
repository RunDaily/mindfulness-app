package com.life.mindfulnessapp.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import android.widget.Toast
import com.life.mindfulnessapp.service.MonitorForegroundService
import com.life.mindfulnessapp.ui.account.AccountUiState
import com.life.mindfulnessapp.ui.account.AccountViewModel
import com.life.mindfulnessapp.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    accountViewModel: AccountViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val permStatus by viewModel.permissionStatus.collectAsState()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val hourlyReminderEnabled by viewModel.hourlyReminderEnabled.collectAsState()
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val isExploreUnlocked by viewModel.isExploreUnlocked.collectAsState()
    val allThemesUnlocked by viewModel.allThemesUnlocked.collectAsState()
    val accountState by accountViewModel.uiState.collectAsState()

    // 根据当前主题动态调整颜色
    val bgColor        = if (isDarkTheme) NightBg           else DayBg
    val cardColor      = if (isDarkTheme) NightCardBg        else DayCardBg
    val cardGreenColor = if (isDarkTheme) NightCardGreen     else DayCardGreen
    val textPrimary    = if (isDarkTheme) NightTextPrimary   else DayTextPrimary
    val textSecondary  = if (isDarkTheme) NightTextSecondary else DayTextSecondary
    val borderColor    = if (isDarkTheme) NightBorder        else DayBorder
    val dividerColor   = if (isDarkTheme) NightDivider       else DayDivider
    val accentGreen    = if (isDarkTheme) LogoGreen          else Color(0xFF27AE60)

    val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.refreshPermissions() }
    val usageLauncher   = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.refreshPermissions() }
    val batteryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.refreshPermissions() }

    LaunchedEffect(Unit) { viewModel.refreshPermissions() }

    // 账号弹窗状态
    var showAccountDialog by remember { mutableStateOf(false) }

    // 监听 Toast 消息
    LaunchedEffect(accountState.toastMessage) {
        accountState.toastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            accountViewModel.clearToast()
        }
    }

    // 彩蛋：连击计数 & SnackbarHostState
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var eggClickCount by remember { mutableIntStateOf(0) }
    var lastClickTime by remember { mutableLongStateOf(0L) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = bgColor,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = if (isDarkTheme) NightCardBg else DayCardBg,
                    contentColor = textPrimary,
                    actionColor = accentGreen,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // ── 顶部简洁标题区 ──────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 14.dp, bottom = 12.dp)
            ) {
                Text(
                    text = "设置",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    letterSpacing = (-0.5).sp
                )
            }
            HorizontalDivider(
                color = if (isDarkTheme) NightDivider else DayDivider,
                thickness = 0.5.dp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── 外观主题 ─────────────────────────────────────────────────────────
            SettingsSectionTitle(title = "外观主题", textColor = textSecondary)

            ThemeToggleCard(
                isDark = isDarkTheme,
                onToggle = { viewModel.setDarkTheme(it) },
                cardColor = cardColor,
                accentGreen = accentGreen,
                textPrimary = textPrimary,
                borderColor = borderColor,
                dividerColor = dividerColor
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── 监控服务 ─────────────────────────────────────────────────────────
            SettingsSectionTitle(title = "监控服务", textColor = textSecondary)

            ThemedCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                isHighlight = isServiceRunning,
                cardColor = cardColor,
                cardGreenColor = cardGreenColor,
                accentGreen = accentGreen,
                borderColor = borderColor
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ServiceIconBox(
                        isActive = isServiceRunning,
                        activeIcon = Icons.Default.Shield,
                        inactiveIcon = Icons.Default.ShieldMoon,
                        activeColor = MindfulGreen40,
                        inactiveColor = if (isDarkTheme) Color(0xFFE74C3C) else Color(0xFFB94040)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("后台监控服务", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                        Text(
                            text = if (isServiceRunning) "运行中 · 正在守护你的注意力" else "已停止",
                            fontSize = 12.sp,
                            color = if (isServiceRunning) MindfulGreen40 else Color(0xFFE74C3C)
                        )
                    }
                    ThemedSwitch(
                        checked = isServiceRunning,
                        onCheckedChange = { enabled ->
                            if (enabled) MonitorForegroundService.start(context)
                            else MonitorForegroundService.stop(context)
                            viewModel.setServiceRunning(enabled)
                        },
                        isDark = isDarkTheme
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── 拦截主题 ────────────────────────────────────────────
            SettingsSectionTitle(title = "拦截主题", textColor = textSecondary)

            ThemedCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                isHighlight = allThemesUnlocked,
                cardColor = cardColor,
                cardGreenColor = cardGreenColor,
                accentGreen = accentGreen,
                borderColor = borderColor
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ServiceIconBox(
                            isActive = allThemesUnlocked,
                            activeIcon = Icons.Default.Style,
                            inactiveIcon = Icons.Default.Style,
                            activeColor = MindfulGreen40,
                            inactiveColor = textPrimary.copy(alpha = 0.25f)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "开放全部主题",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = textPrimary
                            )
                            Text(
                                text = if (allThemesUnlocked) "已开放 · 色彩拦截界面全部可用"
                                       else "尚未开放 · 默认仅 3 个主题",
                                fontSize = 12.sp,
                                color = if (allThemesUnlocked) MindfulGreen40
                                        else textPrimary.copy(alpha = 0.35f)
                            )
                        }
                        ThemedSwitch(
                            checked = allThemesUnlocked,
                            onCheckedChange = { viewModel.setAllThemesUnlocked(it) },
                            isDark = isDarkTheme
                        )
                    }

                    if (allThemesUnlocked) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = accentGreen.copy(alpha = 0.15f))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "🎨 已开放 深海 / 赛博 / 熔岩 / 樱花 / 月球 / 故障 / 勇者共 8 种主题，前往主题页切换体验。",
                            fontSize = 12.sp,
                            color = textPrimary.copy(alpha = 0.5f),
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── 每小时使用提醒 ────────────────────────────────────────────────────
            SettingsSectionTitle(title = "使用提醒", textColor = textSecondary)

            ThemedCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                isHighlight = hourlyReminderEnabled,
                cardColor = cardColor,
                cardGreenColor = cardGreenColor,
                accentGreen = accentGreen,
                borderColor = borderColor
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ServiceIconBox(
                            isActive = hourlyReminderEnabled,
                            activeIcon = Icons.Default.Notifications,
                            inactiveIcon = Icons.Default.NotificationsNone,
                            activeColor = MindfulGreen40,
                            inactiveColor = textPrimary.copy(alpha = 0.25f)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("每小时使用提醒", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                            Text(
                                text = if (hourlyReminderEnabled) "开启 · 整点推送当日使用时长"
                                else "关闭 · 不发送使用提醒",
                                fontSize = 12.sp,
                                color = if (hourlyReminderEnabled) MindfulGreen40 else textPrimary.copy(alpha = 0.35f)
                            )
                        }
                        ThemedSwitch(
                            checked = hourlyReminderEnabled,
                            onCheckedChange = { viewModel.setHourlyReminderEnabled(it) },
                            isDark = isDarkTheme
                        )
                    }

                    if (hourlyReminderEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = accentGreen.copy(alpha = 0.15f))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "💡 开启后，每天 10:00～22:00 每小时整点发送一条通知，" +
                                    "展示当日各 App 的累计使用时长，帮你随时掌握手机使用情况。",
                            fontSize = 12.sp,
                            color = textPrimary.copy(alpha = 0.5f),
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── 权限状态 ─────────────────────────────────────────────────────────
            SettingsSectionTitle(title = "权限状态", textColor = textSecondary)

            ThemedPermissionRow(
                icon = Icons.Default.Layers, title = "悬浮窗权限", isGranted = permStatus.hasOverlay,
                isDark = isDarkTheme, accentGreen = accentGreen,
                cardColor = cardColor, cardGreenColor = cardGreenColor,
                textPrimary = textPrimary, borderColor = borderColor,
                onGrant = { overlayLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) }
            )
            ThemedPermissionRow(
                icon = Icons.Default.QueryStats, title = "使用情况访问", isGranted = permStatus.hasUsageStats,
                isDark = isDarkTheme, accentGreen = accentGreen,
                cardColor = cardColor, cardGreenColor = cardGreenColor,
                textPrimary = textPrimary, borderColor = borderColor,
                onGrant = { usageLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            )
            ThemedPermissionRow(
                icon = Icons.Default.BatteryFull, title = "忽略电池优化", isGranted = permStatus.hasBatteryOptimizationIgnored, isOptional = true,
                isDark = isDarkTheme, accentGreen = accentGreen,
                cardColor = cardColor, cardGreenColor = cardGreenColor,
                textPrimary = textPrimary, borderColor = borderColor,
                onGrant = { batteryLauncher.launch(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))) }
            )
            ThemedPermissionRow(
                icon = Icons.Default.Notifications, title = "通知权限", isGranted = permStatus.hasNotification, isOptional = true,
                isDark = isDarkTheme, accentGreen = accentGreen,
                cardColor = cardColor, cardGreenColor = cardGreenColor,
                textPrimary = textPrimary, borderColor = borderColor,
                onGrant = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply { putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName) }) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── 云端同步（账号）─────────────────────────────────────────────────
            SettingsSectionTitle(title = "云端同步", textColor = textSecondary)

            AccountCard(
                state = accountState,
                isDarkTheme = isDarkTheme,
                cardColor = cardColor,
                cardGreenColor = cardGreenColor,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                accentGreen = accentGreen,
                borderColor = borderColor,
                onLoginClick = { showAccountDialog = true },
                onSyncClick = { accountViewModel.syncToCloud() },
                onLogoutClick = { accountViewModel.logout() }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── 关于 ─────────────────────────────────────────────────────────────
            SettingsSectionTitle(title = "关于", textColor = textSecondary)

            // 版本号 —— 彩蛋入口（连点 7 次解锁探索 Tab）
            EasterEggVersionItem(
                isExploreUnlocked = isExploreUnlocked,
                cardColor = cardColor,
                textPrimary = textPrimary,
                accentGreen = accentGreen,
                isDark = isDarkTheme,
                onClick = {
                    val now = System.currentTimeMillis()
                    // 超过 1.5 秒没点就重置计数
                    if (now - lastClickTime > 1500L) eggClickCount = 0
                    lastClickTime = now
                    eggClickCount++

                    if (!isExploreUnlocked) {
                        val remaining = 7 - eggClickCount
                        when {
                            eggClickCount in 3..6 -> {
                                scope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    snackbarHostState.showSnackbar(
                                        message = "再点 $remaining 次…",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                            eggClickCount >= 7 -> {
                                eggClickCount = 0
                                viewModel.unlockExplore()
                                scope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    snackbarHostState.showSnackbar(
                                        message = "🎉 彩蛋解锁！探索 Tab 已开启",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        }
                    }
                }
            )

            // 理念卡片（升级版）
            MindfulPhilosophyCard(
                cardColor = cardColor,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                accentGreen = accentGreen,
                isDark = isDarkTheme
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // ── 账号弹窗 ────────────────────────────────────────────────────────────
    if (showAccountDialog) {
        AccountDialog(
            state = accountState,
            isDarkTheme = isDarkTheme,
            cardColor = cardColor,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            accentGreen = accentGreen,
            borderColor = borderColor,
            onDismiss = { showAccountDialog = false },
            onLogin = { phone, password ->
                accountViewModel.loginOrRegister(phone, password) {
                    showAccountDialog = false
                }
            }
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  彩蛋版本号条目
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun EasterEggVersionItem(
    isExploreUnlocked: Boolean,
    cardColor: Color,
    textPrimary: Color,
    accentGreen: Color,
    isDark: Boolean,
    onClick: () -> Unit
) {
    // 解锁时的光晕动画
    val infiniteTransition = rememberInfiniteTransition(label = "egg_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(cardColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MindfulGreen40,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "版本", fontSize = 14.sp, color = textPrimary)
                Text(text = "1.0.0", fontSize = 12.sp, color = textPrimary.copy(alpha = 0.4f))
            }
            // 已解锁标记
            AnimatedVisibility(
                visible = isExploreUnlocked,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    accentGreen.copy(alpha = glowAlpha * 0.25f),
                                    Color(0xFF9B59B6).copy(alpha = glowAlpha * 0.25f)
                                )
                            )
                        )
                        .border(
                            1.dp,
                            Brush.horizontalGradient(
                                listOf(
                                    accentGreen.copy(alpha = glowAlpha * 0.6f),
                                    Color(0xFF9B59B6).copy(alpha = glowAlpha * 0.6f)
                                )
                            ),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "✦ 探索",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = accentGreen.copy(alpha = glowAlpha)
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  主题切换卡片
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ThemeToggleCard(
    isDark: Boolean,
    onToggle: (Boolean) -> Unit,
    cardColor: Color,
    accentGreen: Color,
    textPrimary: Color,
    borderColor: Color,
    dividerColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cardColor)
            .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 标题行
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(accentGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isDark) Icons.Default.NightlightRound else Icons.Default.LightMode,
                        contentDescription = null,
                        tint = accentGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isDark) "夜间模式" else "日间模式",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = textPrimary
                    )
                    Text(
                        text = if (isDark) "深蓝黑底，护眼沉浸" else "明亮清爽，清晰易读",
                        fontSize = 12.sp,
                        color = accentGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = dividerColor)
            Spacer(modifier = Modifier.height(14.dp))

            // 两个主题预览按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 日间预览
                ThemePreviewButton(
                    modifier = Modifier.weight(1f),
                    label = "☀️ 日间",
                    isSelected = !isDark,
                    bgColor = Color(0xFFF5F7F5),
                    borderHighlight = Color(0xFF34C26A),
                    textColor = Color(0xFF1A1D1A),
                    onClick = { onToggle(false) }
                )
                // 夜间预览
                ThemePreviewButton(
                    modifier = Modifier.weight(1f),
                    label = "🌙 夜间",
                    isSelected = isDark,
                    bgColor = Color(0xFF0D1117),
                    borderHighlight = Color(0xFF4CD980),
                    textColor = Color(0xFFE6EDF3),
                    onClick = { onToggle(true) }
                )
            }
        }
    }
}

@Composable
private fun ThemePreviewButton(
    modifier: Modifier = Modifier,
    label: String,
    isSelected: Boolean,
    bgColor: Color,
    borderHighlight: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    val borderW = if (isSelected) 2.dp else 1.dp
    val borderC = if (isSelected) borderHighlight else borderHighlight.copy(alpha = 0.2f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(borderW, borderC, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 小预览条
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(borderHighlight.copy(alpha = if (isSelected) 0.9f else 0.3f))
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) borderHighlight else textColor.copy(alpha = 0.5f)
            )
            if (isSelected) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(borderHighlight)
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  通用主题感知组件
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun SettingsSectionTitle(title: String, textColor: Color) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = textColor.copy(alpha = 0.6f),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
private fun ThemedCard(
    modifier: Modifier = Modifier,
    isHighlight: Boolean = false,
    cardColor: Color,
    cardGreenColor: Color,
    accentGreen: Color,
    borderColor: Color,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isHighlight) cardGreenColor else cardColor)
            .border(
                width = 1.dp,
                color = if (isHighlight) accentGreen.copy(alpha = 0.25f) else borderColor.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            )
    ) { content() }
}

@Composable
private fun ServiceIconBox(
    isActive: Boolean,
    activeIcon: ImageVector,
    inactiveIcon: ImageVector,
    activeColor: Color,
    inactiveColor: Color
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (isActive) activeColor.copy(alpha = 0.18f) else inactiveColor.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isActive) activeIcon else inactiveIcon,
            contentDescription = null,
            tint = if (isActive) activeColor else inactiveColor,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun ThemedSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isDark: Boolean
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = MindfulGreen40,
            uncheckedThumbColor = if (isDark) Color.White.copy(alpha = 0.55f) else Color(0xFF9E9E9E),
            uncheckedTrackColor = if (isDark) Color(0xFF2A3347) else Color(0xFFDDDDDD)
        )
    )
}

@Composable
private fun ThemedPermissionRow(
    icon: ImageVector,
    title: String,
    isGranted: Boolean,
    isOptional: Boolean = false,
    isDark: Boolean,
    accentGreen: Color,
    cardColor: Color,
    cardGreenColor: Color,
    textPrimary: Color,
    borderColor: Color,
    onGrant: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isGranted) cardGreenColor else cardColor)
            .border(
                width = 1.dp,
                color = if (isGranted) accentGreen.copy(alpha = 0.25f) else borderColor.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp)
            )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.CheckCircle else icon,
                contentDescription = null,
                tint = if (isGranted) accentGreen else textPrimary.copy(alpha = 0.3f),
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = title, fontSize = 14.sp, color = textPrimary)
                    if (isOptional) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(accentGreen.copy(alpha = 0.15f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text("可选", fontSize = 10.sp, color = accentGreen)
                        }
                    }
                }
                Text(
                    text = if (isGranted) "已开启" else "未开启",
                    fontSize = 12.sp,
                    color = if (isGranted) MindfulGreen40 else Color(0xFFE74C3C).copy(alpha = 0.8f)
                )
            }
            if (!isGranted) {
                TextButton(onClick = onGrant) {
                    Text("去开启", color = accentGreen, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ThemedSettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color = MindfulGreen40,
    cardColor: Color,
    textPrimary: Color,
    onClick: (() -> Unit)?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(cardColor)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 14.sp, color = textPrimary)
                Text(text = subtitle, fontSize = 12.sp, color = textPrimary.copy(alpha = 0.4f))
            }
            if (onClick != null) {
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = textPrimary.copy(alpha = 0.25f))
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  理念卡片
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun MindfulPhilosophyCard(
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentGreen: Color,
    isDark: Boolean
) {
    val quotes = remember {
        listOf(
            "手机是工具，不是目的地。",
            "每一次觉察，都是重新选择的机会。",
            "注意力是你最宝贵的资源。",
            "有意识地使用，而不是被使用。"
        )
    }
    val quoteIndex = remember {
        (System.currentTimeMillis() / (1000 * 60 * 60 * 6) % quotes.size).toInt()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        if (isDark) Color(0xFF1A2038) else Color(0xFFE8F5EE),
                        if (isDark) Color(0xFF131720) else Color(0xFFF0F7F4)
                    )
                )
            )
            .border(
                1.dp,
                accentGreen.copy(alpha = if (isDark) 0.12f else 0.2f),
                RoundedCornerShape(16.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Color(0xFFE74C3C).copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    "理念",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textSecondary.copy(alpha = 0.5f),
                    letterSpacing = 0.8.sp
                )
            }
            Text(
                text = "「${quotes[quoteIndex]}」",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = textPrimary.copy(alpha = 0.8f),
                lineHeight = 22.sp,
                letterSpacing = 0.2.sp
            )
            Text(
                text = "GoodGoods · 帮助你有意识地使用手机，把注意力放回当下最重要的事情上。",
                fontSize = 11.sp,
                color = textSecondary.copy(alpha = 0.38f),
                lineHeight = 17.sp
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  账号卡片（云端同步入口）
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun AccountCard(
    state: AccountUiState,
    isDarkTheme: Boolean,
    cardColor: Color,
    cardGreenColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentGreen: Color,
    borderColor: Color,
    onLoginClick: () -> Unit,
    onSyncClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (state.isLoggedIn) cardGreenColor else cardColor)
            .border(
                width = 1.dp,
                color = if (state.isLoggedIn) accentGreen.copy(alpha = 0.25f) else borderColor.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ── 顶部行：头像 + 信息 ──────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 头像圆圈
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (state.isLoggedIn) accentGreen.copy(alpha = 0.18f)
                            else textPrimary.copy(alpha = 0.07f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (state.isLoggedIn) state.avatarEmoji else "⚓",
                        fontSize = 22.sp
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (state.isLoggedIn) state.nickname.ifBlank { state.username }
                               else "未登录",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = textPrimary
                    )
                    Text(
                        text = if (state.isLoggedIn) "⚓ ${state.username}"
                               else "登录后可将使用数据同步到云端",
                        fontSize = 12.sp,
                        color = if (state.isLoggedIn) accentGreen else textPrimary.copy(alpha = 0.4f)
                    )
                }

                // 操作按钮
                if (!state.isLoggedIn) {
                    TextButton(
                        onClick = onLoginClick,
                        colors = ButtonDefaults.textButtonColors(contentColor = accentGreen)
                    ) {
                        Text("登录 / 注册", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // ── 已登录时的操作区 ──────────────────────────────────────────────
            if (state.isLoggedIn) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = accentGreen.copy(alpha = 0.12f))
                Spacer(modifier = Modifier.height(12.dp))

                // 同步状态提示
                if (state.syncStatus.isNotBlank()) {
                    Text(
                        text = state.syncStatus,
                        fontSize = 12.sp,
                        color = textPrimary.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 同步按钮
                    Button(
                        onClick = onSyncClick,
                        enabled = !state.isSyncing,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentGreen,
                            contentColor = Color.White,
                            disabledContainerColor = accentGreen.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        if (state.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = if (state.isSyncing) "同步中..." else "立即同步",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // 退出按钮
                    OutlinedButton(
                        onClick = onLogoutClick,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = textPrimary.copy(alpha = 0.55f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Text("退出登录", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  登录 / 注册弹窗
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun AccountDialog(
    state: AccountUiState,
    isDarkTheme: Boolean,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentGreen: Color,
    borderColor: Color,
    onDismiss: () -> Unit,
    onLogin: (phone: String, password: String) -> Unit
) {
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(cardColor)
                .padding(24.dp)
        ) {
            Column {
                // ── 标题 ──────────────────────────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("⚓", fontSize = 26.sp)
                    Column {
                        Text(
                            text = "心锚账号",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                        Text(
                            text = "登录或注册，开启云端数据同步",
                            fontSize = 12.sp,
                            color = textSecondary.copy(alpha = 0.5f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── 手机号输入框 ──────────────────────────────────────────────
                OutlinedTextField(
                    value = phone,
                    onValueChange = { if (it.length <= 11) phone = it },
                    label = { Text("手机号", fontSize = 14.sp) },
                    placeholder = { Text("请输入手机号", color = textSecondary.copy(alpha = 0.4f)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentGreen,
                        unfocusedBorderColor = borderColor,
                        focusedLabelColor = accentGreen,
                        unfocusedLabelColor = textSecondary.copy(alpha = 0.5f),
                        focusedTextColor = textPrimary,
                        unfocusedTextColor = textPrimary,
                        cursorColor = accentGreen
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ── 密码输入框 ────────────────────────────────────────────────
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码（至少 6 位）", fontSize = 14.sp) },
                    placeholder = { Text("请输入密码", color = textSecondary.copy(alpha = 0.4f)) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                                           else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff
                                              else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = textSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentGreen,
                        unfocusedBorderColor = borderColor,
                        focusedLabelColor = accentGreen,
                        unfocusedLabelColor = textSecondary.copy(alpha = 0.5f),
                        focusedTextColor = textPrimary,
                        unfocusedTextColor = textPrimary,
                        cursorColor = accentGreen
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "💡 手机号未注册时将自动为你创建账号",
                    fontSize = 11.sp,
                    color = textSecondary.copy(alpha = 0.4f),
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // ── 按钮区 ────────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 取消
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColors(contentColor = textSecondary.copy(alpha = 0.5f))
                    ) {
                        Text("取消", fontSize = 14.sp)
                    }

                    // 登录 / 注册
                    Button(
                        onClick = { onLogin(phone.trim(), password) },
                        enabled = !state.isLoading,
                        modifier = Modifier.weight(2f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentGreen,
                            contentColor = Color.White,
                            disabledContainerColor = accentGreen.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = if (state.isLoading) "登录中..." else "登录 / 注册",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
