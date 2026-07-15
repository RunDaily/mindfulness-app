package com.life.mindfulnessapp.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.domain.usecase.PermissionStatus
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.theme.LogoGreenBright
import com.life.mindfulnessapp.ui.theme.LogoGreenDeep
import com.life.mindfulnessapp.ui.theme.MindfulGreen40
import kotlinx.coroutines.launch

// ══════════════════════════════════════════════════════════════════════════════
//  Onboarding 主入口
//
//  页面流程：
//   0 = 隐私政策（新安装时必经）
//   1 = 功能亮点 1（拦截胶囊 + 会话意图）
//   2 = 功能亮点 2（统计 + 仪式感）
//   3 = 权限授权
//
//  initialPage：
//   - 新安装且从未同意过隐私 → 0
//   - 已同意隐私但未完成 onboarding → 1
// ══════════════════════════════════════════════════════════════════════════════

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    initialPage: Int = 0,
    onPrivacyAccept: () -> Unit = {},
    onPrivacyDecline: () -> Unit = {},
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val permStatus by viewModel.permissionStatus.collectAsState()
    val scope = rememberCoroutineScope()

    // 共 4 个步骤：0=隐私, 1=功能亮点1, 2=功能亮点2, 3=权限授权
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { 4 }
    )

    val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.refreshPermissions() }
    val usageLauncher   = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.refreshPermissions() }
    val batteryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.refreshPermissions() }

    LaunchedEffect(Unit) { viewModel.refreshPermissions() }

    val cs = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // 隐私页不允许手势滑过（必须主动选择），权限页不允许往左滑回功能页
            userScrollEnabled = pagerState.currentPage in 1..2
        ) { page ->
            when (page) {
                0 -> PrivacyPolicyPage(
                    onAccept = {
                        onPrivacyAccept()
                        scope.launch { pagerState.animateScrollToPage(1) }
                    },
                    onDecline = onPrivacyDecline
                )
                1 -> FeatureIntroPage1(
                    onNext = { scope.launch { pagerState.animateScrollToPage(2) } }
                )
                2 -> FeatureIntroPage2(
                    onNext = { scope.launch { pagerState.animateScrollToPage(3) } },
                    onBack = { scope.launch { pagerState.animateScrollToPage(1) } }
                )
                3 -> PermissionSetupPage(
                    permStatus = permStatus,
                    onBack = { scope.launch { pagerState.animateScrollToPage(2) } },
                    onComplete = onComplete,
                    onGrantOverlay = {
                        overlayLauncher.launch(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                        )
                    },
                    onGrantUsage = { usageLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                    onGrantBattery = {
                        batteryLauncher.launch(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                        )
                    }
                )
            }
        }

        // 底部页码指示器（仅功能介绍两页显示，即第 1、2 页）
        if (pagerState.currentPage in 1..2) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(2) { index ->
                    val isActive = index == (pagerState.currentPage - 1)
                    val width by animateDpAsState(
                        targetValue = if (isActive) 20.dp else 6.dp,
                        animationSpec = tween(300),
                        label = "dot_width"
                    )
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(
                                if (isActive) LogoGreen else LogoGreen.copy(alpha = 0.25f)
                            )
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  第 0 页：隐私政策（新安装必经）
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun PrivacyPolicyPage(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val accentGreen = LogoGreen

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 40.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // ── 顶部 Logo + 标题 ────────────────────────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(accentGreen.copy(alpha = 0.15f))
                    .border(1.dp, accentGreen.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PrivacyTip,
                    contentDescription = null,
                    tint = accentGreen,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "使用前请阅读",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = cs.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "心锚非常重视您的隐私与数据安全",
                fontSize = 14.sp,
                color = cs.onBackground.copy(alpha = 0.45f),
                textAlign = TextAlign.Center
            )
        }

        // ── 协议内容区（可滚动卡片）────────────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 20.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(cs.surface)
                .border(1.dp, cs.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PrivacySection(
                    icon = Icons.Default.DataUsage,
                    iconTint = Color(0xFF64B5F6),
                    title = "我们收集的信息",
                    items = listOf(
                        "手机应用使用时长（本地统计，不含内容）",
                        "您设置的应用限额及使用目的",
                        "注册账号时的手机号（用于云端同步）"
                    )
                )
                HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.3f))
                PrivacySection(
                    icon = Icons.Default.Shield,
                    iconTint = accentGreen,
                    title = "我们承诺不会",
                    items = listOf(
                        "读取您的通讯录、短信或其他隐私内容",
                        "将您的数据出售给任何第三方",
                        "在无您授权的情况下访问您的数据"
                    )
                )
                // 链接行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "《隐私政策》",
                        fontSize = 12.sp,
                        color = accentGreen,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://goodgoods.life/privacy"))
                            )
                        }
                    )
                    Text("  ·  ", fontSize = 12.sp, color = cs.onSurface.copy(alpha = 0.3f))
                    Text(
                        text = "《用户协议》",
                        fontSize = 12.sp,
                        color = accentGreen,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://goodgoods.life/terms"))
                            )
                        }
                    )
                }
            }
        }

        // ── 按钮区 ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onDecline,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, cs.outlineVariant.copy(alpha = 0.5f)
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = cs.onSurface.copy(alpha = 0.5f)
                )
            ) {
                Text("不同意", fontSize = 14.sp)
            }
            Button(
                onClick = onAccept,
                modifier = Modifier.weight(2f).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MindfulGreen40),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    "同意并继续",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun PrivacySection(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    items: List<String>
) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
            }
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEach { item ->
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(cs.onSurface.copy(alpha = 0.3f))
                    )
                    Text(
                        text = item,
                        fontSize = 13.sp,
                        color = cs.onSurface.copy(alpha = 0.55f),
                        lineHeight = 19.sp
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  第 1 页：功能亮点 1 —— 拦截胶囊 + 会话意图
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun FeatureIntroPage1(onNext: () -> Unit) {
    val cs = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp)
            .padding(top = 52.dp, bottom = 100.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 顶部 Hero 区
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 品牌 Logo
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(LogoGreen.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(LogoGreen.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.SelfImprovement,
                        contentDescription = null,
                        tint = LogoGreenBright,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "心锚",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = cs.onBackground,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "用觉察，而不是意志力",
                    fontSize = 15.sp,
                    color = cs.onBackground.copy(alpha = 0.45f),
                    letterSpacing = 0.5.sp
                )
            }
        }

        // 功能卡片区
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FeatureCard(
                icon = Icons.Default.Timer,
                iconBg = Color(0xFF1A3A2E),
                iconTint = LogoGreen,
                title = "拦截胶囊",
                description = "打开受监控的 App 时，屏幕边缘会出现一个悬浮计时胶囊，实时显示已用时长和剩余额度。"
            )
            FeatureCard(
                icon = Icons.Default.Lightbulb,
                iconBg = Color(0xFF2D2A12),
                iconTint = Color(0xFFFFD54F),
                title = "每次都问一句",
                description = "打开 App 前，心锚会轻轻问你：「你为什么要打开它？」帮你从冲动使用变成有意识使用。"
            )
        }

        // 下一步按钮
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MindfulGreen40),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                "下一步",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  第 1 页：功能亮点 2 —— 数据统计 + 仪式感结束
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun FeatureIntroPage2(onNext: () -> Unit, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp)
            .padding(top = 52.dp, bottom = 100.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 顶部标题
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 动态统计预览图
            StatPreviewIllustration()

            Spacer(Modifier.height(4.dp))

            Text(
                text = "你的使用轨迹，一目了然",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = cs.onBackground,
                textAlign = TextAlign.Center,
                letterSpacing = (-0.3).sp
            )
            Text(
                text = "每一次打开、每一次觉察，\n都会记录在今日时间轴上",
                fontSize = 14.sp,
                color = cs.onBackground.copy(alpha = 0.45f),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        }

        // 功能卡片区
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FeatureCard(
                icon = Icons.Default.BarChart,
                iconBg = Color(0xFF1A2A3A),
                iconTint = Color(0xFF64B5F6),
                title = "时间轴 & 热力图",
                description = "今日时间轴记录每次使用，30天热力图让你看清哪些 App 在悄悄消耗你的时间。"
            )
            FeatureCard(
                icon = Icons.Default.SelfImprovement,
                iconBg = Color(0xFF1A3A2E),
                iconTint = LogoGreen,
                title = "仪式感结束",
                description = "每次结束使用时，可以留下一句感受备注。小小的反思，会让你对自己的使用更有掌控感。"
            )
        }

        // 按钮行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(54.dp),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, LogoGreen.copy(alpha = 0.3f))
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = LogoGreen,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("上一步", fontSize = 15.sp, color = LogoGreen)
            }
            Button(
                onClick = onNext,
                modifier = Modifier.weight(2f).height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MindfulGreen40),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    "去开启权限",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  统计预览插图（纯 Compose 绘制，不依赖任何外部资源）
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun StatPreviewIllustration() {
    val infiniteTransition = rememberInfiniteTransition(label = "stat_preview")
    val barAnim by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar_anim"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            val barData = listOf(
                0.3f to LogoGreen,
                0.6f to LogoGreen,
                0.45f to LogoGreen,
                0.8f * barAnim to Color(0xFFE8941A),
                0.5f to LogoGreen,
                0.35f to LogoGreen,
                0.2f to LogoGreen
            )
            barData.forEach { (h, color) ->
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height((h * 72).dp)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(color.copy(alpha = 0.7f))
                )
            }
        }
        // 底线
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  第 2 页：权限授权（原有逻辑，增加返回按钮）
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun PermissionSetupPage(
    permStatus: PermissionStatus,
    onBack: () -> Unit,
    onComplete: () -> Unit,
    onGrantOverlay: () -> Unit,
    onGrantUsage: () -> Unit,
    onGrantBattery: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val canProceed = permStatus.hasOverlay && permStatus.hasUsageStats

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
            // ── 顶部返回 ────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "开启权限",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onBackground
                )
            }

            // ── 步骤说明 ─────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(cs.surfaceVariant)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 权限图标组
                    Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                        listOf(
                            Icons.Default.Layers to LogoGreen,
                            Icons.Default.QueryStats to Color(0xFF64B5F6),
                            Icons.Default.BatteryFull to Color(0xFFFFD54F)
                        ).forEach { (icon, tint) ->
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(cs.background.copy(alpha = 0.8f))
                                    .border(1.5.dp, tint.copy(alpha = 0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    Text(
                        text = "最后一步",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "需要以下权限才能正常工作\n所有数据仅存储在本机，绝不上传",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.55f),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            }

            // ── 权限卡片 ────────────────────────────────────────────────────
            PermissionCard(
                icon = Icons.Default.Layers,
                title = "悬浮窗权限",
                description = "在使用 App 时显示拦截浮窗和计时胶囊",
                isGranted = permStatus.hasOverlay,
                onGrant = onGrantOverlay
            )

            PermissionCard(
                icon = Icons.Default.QueryStats,
                title = "使用情况访问权限",
                description = "检测当前使用的 App，是核心监控功能的基础",
                isGranted = permStatus.hasUsageStats,
                onGrant = onGrantUsage
            )

            PermissionCard(
                icon = Icons.Default.BatteryFull,
                title = "忽略电池优化（推荐）",
                description = "防止系统在后台杀掉监控服务，确保提醒功能正常",
                isGranted = permStatus.hasBatteryOptimizationIgnored,
                isOptional = true,
                onGrant = onGrantBattery
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── 完成按钮 ────────────────────────────────────────────────────
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

// ══════════════════════════════════════════════════════════════════════════════
//  功能特性卡片（通用）
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun FeatureCard(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    description: String
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cs.surface)
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = cs.onSurface.copy(alpha = 0.5f),
                lineHeight = 18.sp
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  权限卡片（复用）
// ══════════════════════════════════════════════════════════════════════════════

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
