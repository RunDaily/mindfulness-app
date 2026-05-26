package com.life.mindfulnessapp.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.domain.usecase.PermissionStatus
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.theme.LogoGreenBright
import com.life.mindfulnessapp.ui.theme.MindfulGreen40

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val permStatus by viewModel.permissionStatus.collectAsState()

    val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.refreshPermissions() }
    val usageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.refreshPermissions() }
    val batteryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.refreshPermissions() }

    LaunchedEffect(Unit) { viewModel.refreshPermissions() }

    val cs = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 顶部 Hero 卡片（深翠绿，与 Home 风格一致）──────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(cs.surfaceVariant)
                    .padding(36.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Logo 圆（带发光效果）
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(LogoGreen.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SelfImprovement,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                    Text(
                        text = "欢迎使用有意识时限",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "需要以下权限才能正常工作\n请放心，所有数据仅存储在本机",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                }
            }

            // ── 权限卡片 ────────────────────────────────────────────────────
            PermissionCard(
                icon = Icons.Default.Layers,
                title = "悬浮窗权限",
                description = "在使用 App 时显示拦截浮窗和计时胶囊",
                isGranted = permStatus.hasOverlay,
                onGrant = {
                    overlayLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                    )
                }
            )

            PermissionCard(
                icon = Icons.Default.QueryStats,
                title = "使用情况访问权限",
                description = "检测当前使用的 App，是核心监控功能的基础",
                isGranted = permStatus.hasUsageStats,
                onGrant = { usageLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            )

            PermissionCard(
                icon = Icons.Default.BatteryFull,
                title = "忽略电池优化（推荐）",
                description = "防止系统在后台杀掉监控服务，确保提醒功能正常",
                isGranted = permStatus.hasBatteryOptimizationIgnored,
                isOptional = true,
                onGrant = {
                    batteryLauncher.launch(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                    )
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── 完成按钮 ────────────────────────────────────────────────────
            val canProceed = permStatus.hasOverlay && permStatus.hasUsageStats
            Button(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = canProceed,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MindfulGreen40,
                    disabledContainerColor = Color(0xFF1A2E20)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = if (canProceed) "开始使用 🌿" else "请先完成必要权限授权",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (canProceed) Color.White else Color.White.copy(alpha = 0.4f)
                )
            }

            if (!canProceed) {
                Text(
                    text = "悬浮窗 和 使用情况访问 是必须开启的",
                    fontSize = 12.sp,
                    color = Color(0xFFE74C3C).copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    isOptional: Boolean = false,
    onGrant: () -> Unit
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
            // 图标区
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isGranted) LogoGreenBright else cs.onSurface
                    )
                    if (isOptional) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(LogoGreen.copy(alpha = 0.18f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("可选", fontSize = 10.sp, color = LogoGreen)
                        }
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
