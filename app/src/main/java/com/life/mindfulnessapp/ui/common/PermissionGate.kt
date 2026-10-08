package com.life.mindfulnessapp.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.life.mindfulnessapp.util.RestrictedSettingsGuide
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.life.mindfulnessapp.domain.usecase.PermissionStatus

private val PermissionWarning = Color(0xFFE8941A)

/**
 * 权限门文案模式：
 * - Soft：首页完整性提醒，可关；展示核心 + 电池/通知（建议必备）
 * - Required：绑 App / 开启监控时的场景化硬门，聚焦核心两项
 */
enum class PermissionGateMode {
    Soft,
    Required,
    /** 首启绑定时：使用情况已在前面授权，只补悬浮窗 */
    OverlayOnly
}

object PermissionSettingsIntents {
    fun overlay(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )

    fun usageAccess(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun ignoreBattery(context: Context): Intent =
        Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        )

    fun appNotification(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }

    fun accessibilityKeepAlive(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun appDetails(context: Context): Intent =
        RestrictedSettingsGuide.appDetailsIntent(context)
}

@Composable
fun PermissionGateDialog(
    permissionStatus: PermissionStatus,
    mode: PermissionGateMode = PermissionGateMode.Soft,
    onDismiss: () -> Unit,
    onGrantOverlay: () -> Unit,
    onGrantUsage: () -> Unit,
    onGrantBattery: () -> Unit = {},
    onGrantNotification: () -> Unit = {},
    onGrantAccessibility: () -> Unit = {}
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val (title, subtitle, dismissLabel) = when (mode) {
        PermissionGateMode.Soft -> Triple(
            "权限还不完整",
            "电池、通知与无障碍保活可显著提升后台稳定性",
            "稍后"
        )
        PermissionGateMode.Required -> Triple(
            "要拦住 App，先开这两项",
            "悬浮窗用于拦截浮层；使用情况访问用于检测当前 App",
            "取消"
        )
        PermissionGateMode.OverlayOnly -> Triple(
            "还差一步：允许悬浮窗",
            "使用情况已在前面开启；悬浮窗用于在门口显示拦截层",
            "稍后"
        )
    }
    val showStability = mode == PermissionGateMode.Soft
    val showUsage = mode == PermissionGateMode.Required

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(cs.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PermissionWarning.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = PermissionWarning,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = cs.onSurface
                        )
                        Text(
                            subtitle,
                            fontSize = 12.sp,
                            color = cs.onSurface.copy(alpha = 0.45f)
                        )
                    }
                }

                HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.4f))

                if (!permissionStatus.hasOverlay) {
                    PermissionGateRow(
                        icon = Icons.Default.Layers,
                        title = "悬浮窗权限",
                        desc = "显示拦截浮层与计时胶囊",
                        accentColor = PermissionWarning,
                        cs = cs,
                        onGrant = onGrantOverlay
                    )
                }
                if (!permissionStatus.hasUsageStats && showUsage) {
                    PermissionGateRow(
                        icon = Icons.Default.QueryStats,
                        title = "使用情况访问",
                        desc = "检测当前 App，是监控基础",
                        accentColor = PermissionWarning,
                        cs = cs,
                        onGrant = onGrantUsage
                    )
                }
                if (showStability && !permissionStatus.hasBatteryOptimizationIgnored) {
                    PermissionGateRow(
                        icon = Icons.Default.BatteryFull,
                        title = "忽略电池优化",
                        desc = "强烈建议开启 · 降低后台被系统杀掉",
                        accentColor = PermissionWarning,
                        cs = cs,
                        onGrant = onGrantBattery
                    )
                }
                if (showStability && !permissionStatus.hasNotification) {
                    PermissionGateRow(
                        icon = Icons.Default.Notifications,
                        title = "通知权限",
                        desc = "强烈建议开启 · 前台服务与提醒依赖它",
                        accentColor = PermissionWarning,
                        cs = cs,
                        onGrant = onGrantNotification
                    )
                }
                if (showStability && !permissionStatus.hasAccessibilityKeepAlive) {
                    val a11yDesc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        "推荐 · ${RestrictedSettingsGuide.accessibilityHow}"
                    } else {
                        "推荐 · 系统绑定，降低后台被杀（不读屏幕内容）"
                    }
                    PermissionGateRow(
                        icon = Icons.Default.AccessibilityNew,
                        title = "无障碍保活",
                        desc = a11yDesc,
                        accentColor = PermissionWarning,
                        cs = cs,
                        onGrant = onGrantAccessibility
                    )
                }
                if (showStability &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !permissionStatus.hasAccessibilityKeepAlive
                ) {
                    PermissionGateRow(
                        icon = Icons.Default.Warning,
                        title = "允许受限制设置",
                        desc = RestrictedSettingsGuide.deniedDialogHint,
                        accentColor = PermissionWarning,
                        cs = cs,
                        onGrant = {
                            context.startActivity(PermissionSettingsIntents.appDetails(context))
                        }
                    )
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        dismissLabel,
                        fontSize = 14.sp,
                        color = cs.onSurface.copy(alpha = 0.45f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionGateRow(
    icon: ImageVector,
    title: String,
    desc: String,
    accentColor: Color,
    cs: ColorScheme,
    onGrant: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accentColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = cs.onSurface)
            Text(desc, fontSize = 11.sp, color = cs.onSurface.copy(alpha = 0.4f))
        }
        TextButton(
            onClick = onGrant,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                "去开启",
                fontSize = 12.sp,
                color = accentColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
