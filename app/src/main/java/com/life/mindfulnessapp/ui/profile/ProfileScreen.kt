package com.life.mindfulnessapp.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.domain.usecase.PermissionStatus
import com.life.mindfulnessapp.ui.plan.PageTitle
import com.life.mindfulnessapp.ui.settings.SettingsViewModel
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.theme.themeChrome
import com.life.mindfulnessapp.util.OemKeepAliveGuide

private val WarnCoral = Color(0xFFC47A6A)
private val Sand = Color(0xFFC4A35A)

/**
 * 「我」：基建脊线。健康一行绿灯；需修时异常加重。
 * 菜单：后台保活 / 设置 / 和小锚聊 / 产品说明书 / 关于。无卡片、无图标列。
 */
@Composable
fun ProfileScreen(
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit = {},
    onNavigateToKeepAliveGuide: () -> Unit = {},
    onNavigateToProductManual: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onNavigateToFeedback: () -> Unit = {},
) {
    val context = LocalContext.current
    val chrome = themeChrome()
    val isDarkTheme = chrome.isDark
    val feedbackUnread by settingsViewModel.feedbackUnreadCount.collectAsState()
    val perm by settingsViewModel.permissionStatus.collectAsState()

    val bgColor = chrome.bg
    val textPrimary = chrome.textPrimary
    val textSecondary = chrome.textSecondary
    val borderColor = chrome.border
    val hairline = borderColor.copy(alpha = if (isDarkTheme) 0.45f else 0.55f)

    LaunchedEffect(Unit) {
        settingsViewModel.syncFeedbackReplies(notify = false)
        settingsViewModel.refreshPermissions()
    }

    val monitorHealthy = perm.isMonitorHealthy
    var healthExpanded by rememberSaveable { mutableStateOf(false) }
    val showHealthDetail = !monitorHealthy || healthExpanded
    val missingCount = perm.monitorHealthMissingCount
    val (workDone, workTotal) = OemKeepAliveGuide.workReadyProgress(perm)
    val workReady = workDone >= workTotal && workTotal > 0
    val harsh = OemKeepAliveGuide.prioritizeAntiClean()
    val keepAliveSubtitle = when {
        !workReady -> "能工作 $workDone / $workTotal"
        harsh -> "防清理 · 能工作已齐"
        else -> "能工作已齐"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        PageTitle(
            "我",
            modifier = Modifier.padding(vertical = 8.dp)
        )

        if (!monitorHealthy) {
            Text(
                "有 $missingCount 项需处理 · 否则无法拦截",
                color = WarnCoral,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
            )
        } else {
            Text(
                text = if (healthExpanded) "监控正常" else "监控正常 ›",
                color = LogoGreen,
                fontSize = 13.sp,
                modifier = Modifier
                    .padding(top = 6.dp, bottom = 2.dp)
                    .clickable { healthExpanded = !healthExpanded }
                    .fillMaxWidth()
            )
        }

        AnimatedVisibility(
            visible = showHealthDetail,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                MonitorHealthLine(
                    label = "用量访问",
                    ok = perm.hasUsageStats,
                    okLabel = "正常",
                    badLabel = "需开启",
                    faintOk = true,
                    textPrimary = textPrimary,
                    hairline = hairline,
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                )
                MonitorHealthLine(
                    label = "悬浮窗",
                    ok = perm.hasOverlay,
                    okLabel = "正常",
                    badLabel = "需开启",
                    faintOk = true,
                    textPrimary = textPrimary,
                    hairline = hairline,
                    onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }
                )
                MonitorHealthLine(
                    label = "无障碍",
                    ok = perm.hasAccessibilityKeepAlive,
                    okLabel = "已开启",
                    badLabel = "需开启",
                    faintOk = true,
                    textPrimary = textPrimary,
                    hairline = hairline,
                    onClick = onNavigateToKeepAliveGuide
                )
                MonitorHealthLine(
                    label = "电池与自启",
                    ok = perm.hasBatteryOptimizationIgnored,
                    okLabel = "已优化",
                    badLabel = "去设置",
                    faintOk = true,
                    textPrimary = textPrimary,
                    hairline = hairline,
                    onClick = onNavigateToKeepAliveGuide
                )
            }
        }

        Spacer(modifier = Modifier.height(if (showHealthDetail) 10.dp else 8.dp))

        SpineNavRow(
            title = "后台保活",
            subtitle = if (!workReady) "防清理未就绪 · 去完成" else keepAliveSubtitle,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            hairline = hairline,
            trailing = if (!workReady) {
                {
                    Text(
                        text = "${workTotal - workDone} 项",
                        color = WarnCoral,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else null,
            onClick = onNavigateToKeepAliveGuide
        )
        SpineNavRow(
            title = "设置",
            subtitle = "主题、胶囊与陪伴",
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            hairline = hairline,
            onClick = onNavigateToSettings
        )
        SpineNavRow(
            title = "和小锚聊",
            subtitle = if (feedbackUnread > 0) "有新回复" else "问题、想法，直接说",
            badge = feedbackUnread.takeIf { it > 0 },
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            hairline = hairline,
            onClick = onNavigateToFeedback
        )
        SpineNavRow(
            title = "产品说明书",
            subtitle = "怎么用、规则是什么",
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            hairline = hairline,
            onClick = onNavigateToProductManual
        )
        SpineNavRow(
            title = "关于",
            subtitle = "版本、更新与协议",
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            hairline = hairline,
            onClick = onNavigateToAbout
        )

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "使用数据仅保存在本机 · ${Build.MANUFACTURER} ${Build.MODEL}",
            fontSize = 11.sp,
            color = textSecondary.copy(alpha = 0.38f),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(36.dp))
    }
}

private val PermissionStatus.isMonitorHealthy: Boolean
    get() = hasUsageStats &&
        hasOverlay &&
        hasAccessibilityKeepAlive &&
        hasBatteryOptimizationIgnored

private val PermissionStatus.monitorHealthMissingCount: Int
    get() = listOf(
        hasUsageStats,
        hasOverlay,
        hasAccessibilityKeepAlive,
        hasBatteryOptimizationIgnored
    ).count { !it }

@Composable
private fun MonitorHealthLine(
    label: String,
    ok: Boolean,
    okLabel: String,
    badLabel: String,
    faintOk: Boolean,
    textPrimary: Color,
    hairline: Color,
    onClick: () -> Unit
) {
    val faint = ok && faintOk
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = if (faint) textPrimary.copy(alpha = 0.38f) else textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            if (ok) okLabel else badLabel,
            color = when {
                !ok -> WarnCoral
                faint -> textPrimary.copy(alpha = 0.4f)
                else -> LogoGreen.copy(alpha = 0.65f)
            },
            fontSize = 12.sp
        )
    }
    HorizontalDivider(color = hairline.copy(alpha = 0.55f))
}

@Composable
private fun SpineNavRow(
    title: String,
    subtitle: String,
    textPrimary: Color,
    textSecondary: Color,
    hairline: Color,
    badge: Int? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    title,
                    color = textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                if (badge != null) {
                    Text(
                        text = if (badge > 99) "99+" else badge.toString(),
                        color = Sand,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                subtitle,
                color = textSecondary.copy(alpha = 0.55f),
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
        if (trailing != null) {
            trailing()
        }
    }
    HorizontalDivider(color = hairline.copy(alpha = 0.55f))
}
