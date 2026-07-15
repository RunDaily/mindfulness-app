package com.life.mindfulnessapp.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.PrivacyTip
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextAlign
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
import com.life.mindfulnessapp.service.WatchdogForegroundService
import com.life.mindfulnessapp.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ════════════════════════════════════════════════════════════════════════════
//  二级设置页面 —— 所有配置类选项
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    interceptThemeId: String = "default",
    onThemeSelected: (String) -> Unit = {},
    onNavigateToTheme: () -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val permStatus by viewModel.permissionStatus.collectAsState()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val enhancedKeepAlive by viewModel.enhancedKeepAlive.collectAsState()
    val dailyBriefEnabled by viewModel.dailyBriefEnabled.collectAsState()
    val dailyBriefHour by viewModel.dailyBriefHour.collectAsState()
    val dailyBriefMinute by viewModel.dailyBriefMinute.collectAsState()
    val quoteReminderEnabled by viewModel.quoteReminderEnabled.collectAsState()
    val quoteReminderIntervalHours by viewModel.quoteReminderIntervalHours.collectAsState()
    val quoteReminderStartHour by viewModel.quoteReminderStartHour.collectAsState()
    val favoriteCount by viewModel.favoriteCount.collectAsState()
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val vipLevel by viewModel.vipLevel.collectAsState()

    val bgColor        = if (isDarkTheme) NightBg           else DayBg
    val cardColor      = if (isDarkTheme) NightCardBg        else DayCardBg
    val cardGreenColor = if (isDarkTheme) NightCardGreen     else DayCardGreen
    val textPrimary    = if (isDarkTheme) NightTextPrimary   else DayTextPrimary
    val textSecondary  = if (isDarkTheme) NightTextSecondary else DayTextSecondary
    val borderColor    = if (isDarkTheme) NightBorder        else DayBorder
    val accentGreen    = if (isDarkTheme) LogoGreen          else Color(0xFF27AE60)

    val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.refreshPermissions() }
    val usageLauncher   = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.refreshPermissions() }
    val batteryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.refreshPermissions() }

    LaunchedEffect(Unit) { viewModel.refreshPermissions() }

    var showReminderTimeDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    val isClearingData by viewModel.isClearingData.collectAsState()

    // 清除数据 Toast
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = bgColor,
        topBar = {
            // 顶部导航栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = textPrimary
                    )
                }
                Text(
                    text = "设置",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimary,
                    modifier = Modifier.padding(start = 4.dp)
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
            Spacer(modifier = Modifier.height(4.dp))

            // ── 监控服务 ──────────────────────────────────────────────────────
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

            ThemedCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                isHighlight = enhancedKeepAlive,
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
                            isActive = enhancedKeepAlive,
                            activeIcon = Icons.Default.Security,
                            inactiveIcon = Icons.Default.SecurityUpdate,
                            activeColor = MindfulGreen40,
                            inactiveColor = textPrimary.copy(alpha = 0.25f)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("加强保活", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                            Text(
                                text = if (enhancedKeepAlive) "开启 · 双重守护，5秒内自动重启"
                                       else "关闭 · 开启后增加一个独立守护服务",
                                fontSize = 12.sp,
                                color = if (enhancedKeepAlive) MindfulGreen40 else textPrimary.copy(alpha = 0.35f)
                            )
                        }
                        ThemedSwitch(
                            checked = enhancedKeepAlive,
                            onCheckedChange = { enabled ->
                                viewModel.setEnhancedKeepAlive(enabled)
                                if (enabled) WatchdogForegroundService.start(context)
                                else WatchdogForegroundService.stop(context)
                            },
                            isDark = isDarkTheme
                        )
                    }
                    if (enhancedKeepAlive) {
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = accentGreen.copy(alpha = 0.15f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "💡 开启后将额外运行一个独立守护前台服务，每 5 秒检测主监控服务是否存活，" +
                                    "一旦发现被系统杀死将立即重启。适合在国产 ROM（MIUI / ColorOS 等）上" +
                                    "遇到服务频繁被杀问题的用户。",
                            fontSize = 12.sp,
                            color = textPrimary.copy(alpha = 0.5f),
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── 权限状态 ──────────────────────────────────────────────────────
            SettingsSectionTitle(title = "权限状态", textColor = textSecondary)
            PermissionsCard(
                permStatus = permStatus,
                accentGreen = accentGreen,
                cardColor = cardColor,
                borderColor = borderColor,
                textPrimary = textPrimary,
                onGrantOverlay = { overlayLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) },
                onGrantUsage = { usageLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                onGrantBattery = { batteryLauncher.launch(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))) },
                onGrantNotification = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply { putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName) }) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── 每日简报 ──────────────────────────────────────────────────────
            SettingsSectionTitle(title = "每日简报", textColor = textSecondary)
            ThemedCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                isHighlight = dailyBriefEnabled,
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
                            isActive = dailyBriefEnabled,
                            activeIcon = Icons.Default.Notifications,
                            inactiveIcon = Icons.Default.NotificationsNone,
                            activeColor = MindfulGreen40,
                            inactiveColor = textPrimary.copy(alpha = 0.25f)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("每日简报", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                            Text(
                                text = if (dailyBriefEnabled) "开启 · 每天 ${dailyBriefHour}:${dailyBriefMinute.toString().padStart(2, '0')} 推送"
                                else "关闭 · 不发送每日简报",
                                fontSize = 12.sp,
                                color = if (dailyBriefEnabled) MindfulGreen40 else textPrimary.copy(alpha = 0.35f)
                            )
                        }
                        ThemedSwitch(
                            checked = dailyBriefEnabled,
                            onCheckedChange = { viewModel.setDailyBriefEnabled(it) },
                            isDark = isDarkTheme
                        )
                    }
                    if (dailyBriefEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = accentGreen.copy(alpha = 0.15f))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "💡 开启后，每天到点推送一条今日简报，" +
                                    "用大白话总结你今天的手机使用时长和克制情况，三言两语帮你心里有数。",
                            fontSize = 12.sp,
                            color = textPrimary.copy(alpha = 0.5f),
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(accentGreen.copy(alpha = 0.08f))
                                .clickable { showReminderTimeDialog = true }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Schedule, contentDescription = null, tint = accentGreen, modifier = Modifier.size(16.dp))
                                Text("推送时间", fontSize = 13.sp, color = textPrimary)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "${dailyBriefHour}:${dailyBriefMinute.toString().padStart(2, '0')}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = accentGreen
                                )
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = accentGreen.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // ── 格言推送 ──────────────────────────────────────────────────────
            QuoteReminderCard(
                isDarkTheme = isDarkTheme,
                quoteReminderEnabled = quoteReminderEnabled,
                quoteReminderIntervalHours = quoteReminderIntervalHours,
                quoteReminderStartHour = quoteReminderStartHour,
                favoriteCount = favoriteCount,
                cardColor = cardColor,
                cardGreenColor = cardGreenColor,
                accentGreen = accentGreen,
                borderColor = borderColor,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onToggle = { enabled ->
                    if (favoriteCount >= 3) viewModel.setQuoteReminderEnabled(enabled)
                },
                onIntervalChange = { viewModel.setQuoteReminderIntervalHours(it) },
                onStartHourChange = { viewModel.setQuoteReminderStartHour(it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── 主题与外观 ────────────────────────────────────────────────────
            SettingsSectionTitle(title = "主题与外观", textColor = textSecondary)
            val currentThemeName = ALL_INTERCEPT_THEMES.find { it.id == interceptThemeId }
                ?.let { "${it.emoji} ${it.name}" } ?: "正念绿 · 默认"
            ThemedSettingsItem(
                icon = Icons.Default.Palette,
                title = "主题与外观",
                subtitle = "${if (isDarkTheme) "🌙 夜间" else "☀️ 日间"} · 拦截风格：$currentThemeName",
                iconTint = accentGreen,
                cardColor = cardColor,
                borderColor = borderColor,
                textPrimary = textPrimary,
                onClick = onNavigateToTheme
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── 数据管理 ──────────────────────────────────────────────────────
            SettingsSectionTitle(title = "数据管理", textColor = textSecondary)
            ThemedSettingsItem(
                icon = Icons.Default.DeleteSweep,
                title = "清除本地数据",
                subtitle = "删除所有本地使用记录（不影响账号与限额设置）",
                iconTint = Color(0xFFE74C3C).copy(alpha = 0.7f),
                cardColor = cardColor,
                borderColor = borderColor,
                textPrimary = textPrimary,
                onClick = { showClearDataDialog = true }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── 关于 ──────────────────────────────────────────────────────────
            SettingsSectionTitle(title = "关于", textColor = textSecondary)
            LegalLinksCard(
                cardColor = cardColor,
                borderColor = borderColor,
                textPrimary = textPrimary,
                accentGreen = accentGreen,
                onPrivacyClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://goodgoods.life/privacy"))) },
                onTermsClick  = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://goodgoods.life/terms"))) }
            )

            Spacer(modifier = Modifier.height(4.dp))
            EasterEggVersionItem(cardColor = cardColor, textPrimary = textPrimary)
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // ── 推送时间弹窗 ─────────────────────────────────────────────────────────
    if (showReminderTimeDialog) {
        DailyBriefTimePickerDialog(
            isDarkTheme = isDarkTheme,
            cardColor = cardColor,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            accentGreen = accentGreen,
            borderColor = borderColor,
            initHour = dailyBriefHour,
            initMinute = dailyBriefMinute,
            onDismiss = { showReminderTimeDialog = false },
            onConfirm = { hour, minute ->
                viewModel.setDailyBriefHour(hour)
                viewModel.setDailyBriefMinute(minute)
                showReminderTimeDialog = false
            }
        )
    }

    // ── 清除本地数据弹窗 ─────────────────────────────────────────────────────
    if (showClearDataDialog) {
        ClearDataDialog(
            isDarkTheme = isDarkTheme,
            cardColor = cardColor,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            accentGreen = accentGreen,
            borderColor = borderColor,
            isClearing = isClearingData,
            onDismiss = { showClearDataDialog = false },
            onConfirm = {
                viewModel.clearLocalUsageData {
                    showClearDataDialog = false
                    Toast.makeText(context, "本地数据已清除", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  隐私政策 + 用户协议 合并卡片（左右两列）
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun LegalLinksCard(
    cardColor: Color,
    borderColor: Color,
    textPrimary: Color,
    accentGreen: Color,
    onPrivacyClick: () -> Unit,
    onTermsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 隐私政策
        LegalLinkButton(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.PrivacyTip,
            label = "隐私政策",
            cardColor = cardColor,
            borderColor = borderColor,
            textPrimary = textPrimary,
            accentGreen = accentGreen,
            onClick = onPrivacyClick
        )
        // 用户协议
        LegalLinkButton(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Gavel,
            label = "用户协议",
            cardColor = cardColor,
            borderColor = borderColor,
            textPrimary = textPrimary,
            accentGreen = accentGreen,
            onClick = onTermsClick
        )
    }
}

@Composable
private fun LegalLinkButton(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    cardColor: Color,
    borderColor: Color,
    textPrimary: Color,
    accentGreen: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(cardColor)
            .border(1.dp, borderColor.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentGreen.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                fontSize = 13.sp,
                color = textPrimary.copy(alpha = 0.75f),
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = null,
                tint = textPrimary.copy(alpha = 0.2f),
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  彩蛋版本号条目
// ════════════════════════════════════════════════════════════════════════════

@Composable
internal fun EasterEggVersionItem(
    cardColor: Color,
    textPrimary: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(cardColor)
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
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  通用主题感知组件
// ════════════════════════════════════════════════════════════════════════════

@Composable
internal fun SettingsSectionTitle(title: String, textColor: Color) {
    Text(
        text = title.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = textColor.copy(alpha = 0.5f),
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
internal fun ThemedCard(
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
internal fun ServiceIconBox(
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
internal fun ThemedSwitch(
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
internal fun PermissionItemRow(
    icon: ImageVector,
    title: String,
    isGranted: Boolean,
    isOptional: Boolean = false,
    accentGreen: Color,
    textPrimary: Color,
    onGrant: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (isGranted) accentGreen.copy(alpha = 0.14f)
                    else textPrimary.copy(alpha = 0.06f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGranted) accentGreen else textPrimary.copy(alpha = 0.3f),
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                color = if (isGranted) accentGreen else Color(0xFFE74C3C).copy(alpha = 0.8f)
            )
        }
        if (!isGranted) {
            TextButton(
                onClick = onGrant,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("去开启", color = accentGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = accentGreen.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
internal fun PermissionsCard(
    permStatus: com.life.mindfulnessapp.domain.usecase.PermissionStatus,
    accentGreen: Color,
    cardColor: Color,
    borderColor: Color,
    textPrimary: Color,
    onGrantOverlay: () -> Unit,
    onGrantUsage: () -> Unit,
    onGrantBattery: () -> Unit,
    onGrantNotification: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cardColor)
            .border(1.dp, borderColor.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            PermissionItemRow(
                icon = Icons.Default.Layers,
                title = "悬浮窗权限",
                isGranted = permStatus.hasOverlay,
                accentGreen = accentGreen,
                textPrimary = textPrimary,
                onGrant = onGrantOverlay
            )
            HorizontalDivider(color = borderColor.copy(alpha = 0.25f))
            PermissionItemRow(
                icon = Icons.Default.QueryStats,
                title = "使用情况访问",
                isGranted = permStatus.hasUsageStats,
                accentGreen = accentGreen,
                textPrimary = textPrimary,
                onGrant = onGrantUsage
            )
            HorizontalDivider(color = borderColor.copy(alpha = 0.25f))
            PermissionItemRow(
                icon = Icons.Default.BatteryFull,
                title = "忽略电池优化",
                isGranted = permStatus.hasBatteryOptimizationIgnored,
                isOptional = true,
                accentGreen = accentGreen,
                textPrimary = textPrimary,
                onGrant = onGrantBattery
            )
            HorizontalDivider(color = borderColor.copy(alpha = 0.25f))
            PermissionItemRow(
                icon = Icons.Default.Notifications,
                title = "通知权限",
                isGranted = permStatus.hasNotification,
                isOptional = true,
                accentGreen = accentGreen,
                textPrimary = textPrimary,
                onGrant = onGrantNotification
            )
        }
    }
}

@Composable
internal fun ThemedSettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color = MindfulGreen40,
    cardColor: Color,
    borderColor: Color,
    textPrimary: Color,
    onClick: (() -> Unit)?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(cardColor)
            .border(1.dp, borderColor.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
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
//  每日简报推送时间选择弹窗
// ════════════════════════════════════════════════════════════════════════════

@Composable
internal fun DailyBriefTimePickerDialog(
    isDarkTheme: Boolean,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentGreen: Color,
    borderColor: Color,
    initHour: Int,
    initMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    var hour by remember { mutableIntStateOf(initHour) }
    var minute by remember { mutableIntStateOf(initMinute) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(cardColor)
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(accentGreen.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = accentGreen, modifier = Modifier.size(22.dp))
                    }
                    Column {
                        Text("推送时间", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                        Text("每天到点给你发今日简报", fontSize = 12.sp, color = textSecondary.copy(alpha = 0.5f))
                    }
                }

                TimePickerRow(
                    label = "小时",
                    value = hour,
                    maxValue = 23,
                    displayText = hour.toString().padStart(2, '0'),
                    accentGreen = accentGreen,
                    textPrimary = textPrimary,
                    onDecrease = { if (hour > 0) hour-- },
                    onIncrease = { if (hour < 23) hour++ }
                )

                TimePickerRow(
                    label = "分钟",
                    value = minute,
                    maxValue = 59,
                    displayText = minute.toString().padStart(2, '0'),
                    accentGreen = accentGreen,
                    textPrimary = textPrimary,
                    onDecrease = { if (minute > 0) minute-- },
                    onIncrease = { if (minute < 59) minute++ }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColors(contentColor = textSecondary.copy(alpha = 0.55f))
                    ) { Text("取消", fontSize = 14.sp) }

                    Button(
                        onClick = { onConfirm(hour, minute) },
                        modifier = Modifier.weight(2f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentGreen,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("保存", fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  清除本地数据确认弹窗
// ════════════════════════════════════════════════════════════════════════════

@Composable
internal fun ClearDataDialog(
    isDarkTheme: Boolean,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentGreen: Color,
    borderColor: Color,
    isClearing: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val dangerColor = Color(0xFFE74C3C)

    Dialog(onDismissRequest = { if (!isClearing) onDismiss() }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(cardColor)
                .padding(24.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier.size(42.dp).clip(CircleShape).background(dangerColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = dangerColor, modifier = Modifier.size(22.dp))
                    }
                    Column {
                        Text("清除本地数据", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                        Text("此操作不可撤销", fontSize = 12.sp, color = dangerColor.copy(alpha = 0.7f))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(dangerColor.copy(alpha = 0.06f))
                        .border(1.dp, dangerColor.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("将会清除：", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
                        listOf("全部本地使用记录（包含历史统计）", "无法通过本地手段恢复").forEach { hint ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(dangerColor.copy(alpha = 0.6f)))
                                Text(hint, fontSize = 12.sp, color = textPrimary.copy(alpha = 0.65f), lineHeight = 18.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("账号信息、限额设置和云端数据不受影响。", fontSize = 11.sp, color = textSecondary.copy(alpha = 0.45f), lineHeight = 16.sp, modifier = Modifier.padding(vertical = 4.dp))
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isClearing,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = textPrimary.copy(alpha = 0.6f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("取消", fontSize = 14.sp) }

                    Button(
                        onClick = onConfirm,
                        enabled = !isClearing,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = dangerColor,
                            contentColor = Color.White,
                            disabledContainerColor = dangerColor.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isClearing) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(if (isClearing) "清除中..." else "确认清除", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
internal fun TimePickerRow(
    label: String,
    value: Int,
    maxValue: Int,
    displayText: String,
    accentGreen: Color,
    textPrimary: Color,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 14.sp, color = textPrimary, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(
                onClick = onDecrease,
                modifier = Modifier.size(36.dp).clip(CircleShape).background(accentGreen.copy(alpha = 0.12f))
            ) {
                Icon(Icons.Default.Remove, contentDescription = "减", tint = accentGreen, modifier = Modifier.size(18.dp))
            }
            Text(
                text = displayText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = accentGreen,
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.Center
            )
            IconButton(
                onClick = onIncrease,
                modifier = Modifier.size(36.dp).clip(CircleShape).background(accentGreen.copy(alpha = 0.12f))
            ) {
                Icon(Icons.Default.Add, contentDescription = "加", tint = accentGreen, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  拦截主题元数据（用于设置页 subtitle 展示当前主题名）
// ════════════════════════════════════════════════════════════════════════════

internal data class ThemeMeta(
    val id: String,
    val name: String,
    val emoji: String,
    val accentColor: Color,
    val bgPreviewColor: Color
)

// ════════════════════════════════════════════════════════════════════════════
//  格言推送卡片
// ════════════════════════════════════════════════════════════════════════════

private val QUOTE_INTERVAL_OPTIONS = listOf(2, 4, 8)
private val QUOTE_INTERVAL_LABELS  = listOf("勤快 · 每2小时", "适中 · 每4小时", "清淡 · 每8小时")

@Composable
internal fun QuoteReminderCard(
    isDarkTheme: Boolean,
    quoteReminderEnabled: Boolean,
    quoteReminderIntervalHours: Int,
    quoteReminderStartHour: Int,
    favoriteCount: Int,
    cardColor: Color,
    cardGreenColor: Color,
    accentGreen: Color,
    borderColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    onToggle: (Boolean) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onStartHourChange: (Int) -> Unit
) {
    val canEnable = favoriteCount >= 3

    ThemedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        isHighlight = quoteReminderEnabled,
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
                    isActive = quoteReminderEnabled,
                    activeIcon = Icons.Default.FormatQuote,
                    inactiveIcon = Icons.Default.FormatQuote,
                    activeColor = MindfulGreen40,
                    inactiveColor = textPrimary.copy(alpha = if (canEnable) 0.25f else 0.12f)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "格言推送",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (canEnable) textPrimary else textPrimary.copy(alpha = 0.4f)
                    )
                    Text(
                        text = when {
                            !canEnable -> "收藏 ${favoriteCount}/3 条格言后可开启"
                            quoteReminderEnabled -> "开启 · 每 $quoteReminderIntervalHours 小时推送一次"
                            else -> "关闭 · 不推送格言"
                        },
                        fontSize = 12.sp,
                        color = when {
                            !canEnable -> accentGreen.copy(alpha = 0.5f)
                            quoteReminderEnabled -> MindfulGreen40
                            else -> textPrimary.copy(alpha = 0.35f)
                        }
                    )
                }
                ThemedSwitch(
                    checked = quoteReminderEnabled,
                    onCheckedChange = { if (canEnable) onToggle(it) },
                    isDark = isDarkTheme
                )
            }

            // 门槛提示条
            if (!canEnable) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = accentGreen.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentGreen.copy(alpha = 0.06f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🤍", fontSize = 14.sp)
                    Text(
                        "在拦截页点击心形收藏打动你的格言，收满 3 条即可开启",
                        fontSize = 12.sp,
                        color = textPrimary.copy(alpha = 0.45f),
                        lineHeight = 18.sp
                    )
                }
            }

            // 已开启时展示详细配置
            if (quoteReminderEnabled && canEnable) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = accentGreen.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(10.dp))

                // 推送频率
                Text("推送频率", fontSize = 12.sp, color = textSecondary.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QUOTE_INTERVAL_OPTIONS.forEachIndexed { idx, hours ->
                        val selected = hours == quoteReminderIntervalHours
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) accentGreen.copy(alpha = 0.18f)
                                    else textPrimary.copy(alpha = 0.06f)
                                )
                                .border(
                                    1.dp,
                                    if (selected) accentGreen.copy(alpha = 0.5f) else borderColor.copy(alpha = 0.3f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onIntervalChange(hours) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                QUOTE_INTERVAL_LABELS[idx],
                                fontSize = 11.sp,
                                color = if (selected) accentGreen else textPrimary.copy(alpha = 0.5f),
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 活跃时段起始时间
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("起始时间", fontSize = 13.sp, color = textPrimary)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = { if (quoteReminderStartHour > 6) onStartHourChange(quoteReminderStartHour - 1) },
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(accentGreen.copy(alpha = 0.12f))
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "减", tint = accentGreen, modifier = Modifier.size(16.dp))
                        }
                        Text(
                            "${quoteReminderStartHour}:00",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accentGreen,
                            modifier = Modifier.width(48.dp),
                            textAlign = TextAlign.Center
                        )
                        IconButton(
                            onClick = { if (quoteReminderStartHour < 20) onStartHourChange(quoteReminderStartHour + 1) },
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(accentGreen.copy(alpha = 0.12f))
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "加", tint = accentGreen, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "推送范围：${quoteReminderStartHour}:00 ～ 22:00",
                    fontSize = 11.sp,
                    color = textSecondary.copy(alpha = 0.4f)
                )
            }
        }
    }
}

internal val ALL_INTERCEPT_THEMES = listOf(
    ThemeMeta("simple",  "极简",   "◻", Color(0xFF007AFF), Color(0xFFF2F2F7)),
    ThemeMeta("default", "正念",   "🌿", Color(0xFF3DDC84), Color(0xFF0D1117)),
    ThemeMeta("zen",     "禅",    "◯", Color(0xFFCCCCCC), Color(0xFF0A0A0A)),
    ThemeMeta("gauge",   "仪表盘", "▣", Color(0xFFFFB300), Color(0xFF080808)),
)
