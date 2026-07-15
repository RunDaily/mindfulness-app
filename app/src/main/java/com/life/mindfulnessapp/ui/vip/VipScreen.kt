package com.life.mindfulnessapp.ui.vip

import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.network.VipPlan
import com.life.mindfulnessapp.ui.theme.*

// ════════════════════════════════════════════
//  VIP 购买页面（Google Play Billing 版本）
// ════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VipScreen(
    viewModel: VipViewModel = hiltViewModel(),
    isDarkTheme: Boolean = true,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val uiState by viewModel.uiState.collectAsState()

    // 主题色
    val bgColor       = if (isDarkTheme) NightBg         else DayBg
    val cardColor     = if (isDarkTheme) NightCardBg     else DayCardBg
    val textPrimary   = if (isDarkTheme) NightTextPrimary else DayTextPrimary
    val textSecondary = if (isDarkTheme) NightTextSecondary else DayTextSecondary
    val borderColor   = if (isDarkTheme) NightBorder      else DayBorder
    val accentGreen   = if (isDarkTheme) LogoGreen        else Color(0xFF27AE60)

    val vipGold   = Color(0xFFFFCC44)

    var selectedPlan by remember { mutableStateOf<VipPlan?>(VipPlan.LIFETIME) }

    // Toast
    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    // 进入页面时刷新 VIP 状态（同时触发 Google Play 产品详情查询）
    LaunchedEffect(Unit) {
        viewModel.refreshVipStatus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "升级心锚",
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor)
            )
        },
        containerColor = bgColor
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {

            // ── 当前状态 Banner ────────────────────────────────────────────────
            VipStatusBanner(
                uiState = uiState,
                isDarkTheme = isDarkTheme,
                vipGold = vipGold,
                accentGreen = accentGreen,
                textPrimary = textPrimary,
                textSecondary = textSecondary
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── 权益对比表 ─────────────────────────────────────────────────────
            BenefitTable(
                isDarkTheme = isDarkTheme,
                cardColor = cardColor,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                borderColor = borderColor,
                accentGreen = accentGreen,
                vipGold = vipGold
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── 购买方案选择 ───────────────────────────────────────────────────
            PlanSelectionSection(
                selectedPlan = selectedPlan,
                productPrices = uiState.productPrices,
                onPlanSelected = { selectedPlan = it },
                isDarkTheme = isDarkTheme,
                cardColor = cardColor,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                borderColor = borderColor,
                accentGreen = accentGreen,
                vipGold = vipGold
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── 购买按钮（直接调起 Google Play 购买底栏，无额外确认弹窗）────────
            PurchaseButton(
                selectedPlan = selectedPlan,
                isLoading = uiState.isLoading || uiState.purchasingPlan != null,
                isLoggedIn = viewModel.isLoggedIn,
                vipGold = vipGold,
                onPurchase = {
                    if (!viewModel.isLoggedIn) {
                        Toast.makeText(context, "请先在设置页登录账号", Toast.LENGTH_SHORT).show()
                    } else if (selectedPlan != null && activity != null) {
                        viewModel.launchPurchase(activity, selectedPlan!!)
                    }
                }
            )

            // ── 免费试用入口（未激活时显示）─────────────────────────────────
            if (!uiState.isVip && viewModel.isLoggedIn) {
                Spacer(modifier = Modifier.height(12.dp))
                TrialButton(
                    isLoading = uiState.isLoading,
                    accentGreen = accentGreen,
                    onActivateTrial = { viewModel.activateTrial() }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── 底部说明文字 ───────────────────────────────────────────────────
            FooterNote(textSecondary = textSecondary)

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// ════════════════════════════════════════════
//  当前状态 Banner
// ════════════════════════════════════════════

@Composable
private fun VipStatusBanner(
    uiState: VipUiState,
    isDarkTheme: Boolean,
    vipGold: Color,
    accentGreen: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    when {
                        uiState.isPremium -> listOf(Color(0xFF1A1200), Color(0xFF2E2000))
                        uiState.isVip     -> listOf(Color(0xFF0A1A0D), Color(0xFF122018))
                        else -> if (isDarkTheme)
                            listOf(Color(0xFF111826), Color(0xFF1A1E2C))
                        else
                            listOf(Color(0xFFE8F5EE), Color(0xFFF0F7F4))
                    }
                )
            )
            .border(
                1.dp,
                when {
                    uiState.isPremium -> vipGold.copy(alpha = 0.4f)
                    uiState.isVip     -> accentGreen.copy(alpha = 0.3f)
                    else              -> if (isDarkTheme) Color(0xFF273045) else Color(0xFFCBD4E2)
                },
                RoundedCornerShape(20.dp)
            )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            uiState.isPremium -> vipGold.copy(alpha = 0.15f)
                            uiState.isVip     -> accentGreen.copy(alpha = 0.15f)
                            else              -> Color.White.copy(alpha = 0.05f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        uiState.isPremium -> "👑"
                        uiState.isVip     -> "⚡"
                        else              -> "⚓"
                    },
                    fontSize = 28.sp
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        uiState.isPremium -> "高级版"
                        uiState.isVip     -> "标准版"
                        else              -> "免费版"
                    },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        uiState.isPremium -> vipGold
                        uiState.isVip     -> accentGreen
                        else              -> textPrimary
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                AnimatedContent(
                    targetState = uiState.statusText,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "status"
                ) { text ->
                    Text(
                        text = text,
                        fontSize = 13.sp,
                        color = textSecondary.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════
//  权益对比表
// ════════════════════════════════════════════

private data class BenefitRow(
    val feature: String,
    val free: String,
    val standard: String,
    val premium: String
)

@Composable
private fun BenefitTable(
    isDarkTheme: Boolean,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    borderColor: Color,
    accentGreen: Color,
    vipGold: Color
) {
    val benefits = listOf(
        BenefitRow("Monitored Apps",    "Up to 3",   "Unlimited", "Unlimited"),
        BenefitRow("Block Themes",      "3 styles",  "All 8",     "All 8"),
        BenefitRow("Data History",      "7 days",    "30 days",   "Forever"),
        BenefitRow("Weekly Limit",      "—",         "✓",         "✓"),
        BenefitRow("Cloud Sync",        "—",         "✓",         "✓"),
        BenefitRow("Daily Limit Edits", "1×/day",    "2×/day",    "Unlimited"),
        BenefitRow("Deep Insights",     "—",         "—",         "✓"),
        BenefitRow("Data Export",       "—",         "—",         "✓")
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cardColor)
            .border(1.dp, borderColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(accentGreen.copy(alpha = 0.08f), vipGold.copy(alpha = 0.05f))
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Feature", modifier = Modifier.weight(2f), fontSize = 12.sp,
                fontWeight = FontWeight.Bold, color = textSecondary.copy(alpha = 0.5f))
            Text("Free", modifier = Modifier.weight(1.2f), fontSize = 12.sp,
                fontWeight = FontWeight.Bold, color = textSecondary.copy(alpha = 0.5f),
                textAlign = TextAlign.Center)
            Text("Standard⚡", modifier = Modifier.weight(1.4f), fontSize = 12.sp,
                fontWeight = FontWeight.Bold, color = accentGreen,
                textAlign = TextAlign.Center)
            Text("Premium👑", modifier = Modifier.weight(1.4f), fontSize = 12.sp,
                fontWeight = FontWeight.Bold, color = vipGold,
                textAlign = TextAlign.Center)
        }
        HorizontalDivider(color = borderColor.copy(alpha = 0.3f))

        benefits.forEachIndexed { index, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(row.feature, modifier = Modifier.weight(2f),
                    fontSize = 13.sp, color = textPrimary)
                Text(row.free, modifier = Modifier.weight(1.2f),
                    fontSize = 12.sp, color = textSecondary.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center)
                Text(row.standard, modifier = Modifier.weight(1.4f),
                    fontSize = 12.sp,
                    color = if (row.standard == "—") textSecondary.copy(alpha = 0.3f) else accentGreen,
                    fontWeight = if (row.standard != "—" && row.standard != row.free) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center)
                Text(row.premium, modifier = Modifier.weight(1.4f),
                    fontSize = 12.sp,
                    color = if (row.premium == "—") textSecondary.copy(alpha = 0.3f) else vipGold,
                    fontWeight = if (row.premium != "—" && row.premium != row.free) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center)
            }
            if (index < benefits.size - 1) {
                HorizontalDivider(
                    color = borderColor.copy(alpha = 0.15f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

// ════════════════════════════════════════════
//  购买方案选择
// ════════════════════════════════════════════

private data class PlanInfo(
    val plan: VipPlan,
    val title: String,
    /** null 时展示 loading 占位（价格从 Google Play 异步加载） */
    val price: String?,
    val tag: String? = null,
    val tagColor: Color = Color(0xFF26BB68),
    val desc: String
)

@Composable
private fun PlanSelectionSection(
    selectedPlan: VipPlan?,
    productPrices: Map<VipPlan, String>,
    onPlanSelected: (VipPlan) -> Unit,
    isDarkTheme: Boolean,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    borderColor: Color,
    accentGreen: Color,
    vipGold: Color
) {
    val plans = listOf(
        PlanInfo(
            plan  = VipPlan.LIFETIME,
            title = "Lifetime",
            price = productPrices[VipPlan.LIFETIME],
            tag   = "Best Value",
            tagColor = vipGold,
            desc  = "One-time purchase, all features forever"
        ),
        PlanInfo(
            plan  = VipPlan.YEARLY_PREMIUM,
            title = "Premium Yearly",
            price = productPrices[VipPlan.YEARLY_PREMIUM],
            tag   = "Premium",
            tagColor = vipGold,
            desc  = "Deep insights, forever data, export"
        ),
        PlanInfo(
            plan  = VipPlan.YEARLY_STANDARD,
            title = "Standard Yearly",
            price = productPrices[VipPlan.YEARLY_STANDARD],
            tag   = "Save 52%",
            tagColor = accentGreen,
            desc  = "Cloud sync, unlimited apps"
        ),
        PlanInfo(
            plan  = VipPlan.MONTHLY_STANDARD,
            title = "Standard Monthly",
            price = productPrices[VipPlan.MONTHLY_STANDARD],
            desc  = "Cancel anytime"
        )
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Choose a Plan",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = textSecondary.copy(alpha = 0.6f),
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        plans.forEach { info ->
            PlanCard(
                info = info,
                isSelected = selectedPlan == info.plan,
                isDarkTheme = isDarkTheme,
                cardColor = cardColor,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                borderColor = borderColor,
                accentGreen = accentGreen,
                vipGold = vipGold,
                onClick = { onPlanSelected(info.plan) }
            )
        }
    }
}

@Composable
private fun PlanCard(
    info: PlanInfo,
    isSelected: Boolean,
    isDarkTheme: Boolean,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    borderColor: Color,
    accentGreen: Color,
    vipGold: Color,
    onClick: () -> Unit
) {
    val accentColor = when (info.plan) {
        VipPlan.LIFETIME, VipPlan.YEARLY_PREMIUM -> vipGold
        else -> accentGreen
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) accentColor.copy(alpha = 0.08f) else cardColor
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) accentColor.copy(alpha = 0.5f)
                        else borderColor.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 选中圆圈
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) accentColor else Color.Transparent)
                    .border(
                        2.dp,
                        if (isSelected) accentColor else borderColor.copy(alpha = 0.5f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        info.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimary
                    )
                    info.tag?.let { tag ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(info.tagColor.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                tag,
                                fontSize = 10.sp,
                                color = info.tagColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Text(
                    info.desc,
                    fontSize = 12.sp,
                    color = textSecondary.copy(alpha = 0.5f)
                )
            }

            // 价格（从 Google Play 异步加载；未加载完显示 "..."）
            Text(
                text = info.price ?: "...",
                fontSize = if (info.price != null) 16.sp else 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) accentColor
                        else if (info.price != null) textPrimary
                        else textSecondary.copy(alpha = 0.4f)
            )
        }
    }
}

// ════════════════════════════════════════════
//  购买按钮
// ════════════════════════════════════════════

@Composable
private fun PurchaseButton(
    selectedPlan: VipPlan?,
    isLoading: Boolean,
    isLoggedIn: Boolean,
    vipGold: Color,
    onPurchase: () -> Unit
) {
    Button(
        onClick = onPurchase,
        enabled = !isLoading && selectedPlan != null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = vipGold,
            contentColor = Color(0xFF1A0E00),
            disabledContainerColor = vipGold.copy(alpha = 0.4f),
            disabledContentColor = Color(0xFF1A0E00).copy(alpha = 0.4f)
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = Color(0xFF1A0E00)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text("Processing...", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        } else if (!isLoggedIn) {
            Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sign in to Subscribe", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        } else {
            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = selectedPlan?.let { "Subscribe Now" } ?: "Select a Plan",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ════════════════════════════════════════════
//  免费试用按钮
// ════════════════════════════════════════════

@Composable
private fun TrialButton(
    isLoading: Boolean,
    accentGreen: Color,
    onActivateTrial: () -> Unit
) {
    TextButton(
        onClick = onActivateTrial,
        enabled = !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = accentGreen)
    ) {
        Icon(Icons.Default.CardGiftcard, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            "Try Free for 7 Days (one-time per account)",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ════════════════════════════════════════════
//  底部说明文字（Google Play 合规版本）
// ════════════════════════════════════════════

@Composable
private fun FooterNote(textSecondary: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        HorizontalDivider(color = textSecondary.copy(alpha = 0.1f))
        Spacer(modifier = Modifier.height(8.dp))
        listOf(
            "• Payment is charged to your Google Play account at confirmation",
            "• Subscriptions auto-renew unless cancelled 24h before the period ends",
            "• Manage or cancel subscriptions in Google Play > Subscriptions",
            "• Lifetime purchase is a one-time charge, bound to your account",
            "• Questions? Contact support@goodgoods.life"
        ).forEach { note ->
            Text(
                note,
                fontSize = 11.sp,
                color = textSecondary.copy(alpha = 0.4f),
                lineHeight = 17.sp
            )
        }
    }
}

// ════════════════════════════════════════════
//  VIP 升级引导弹窗（从 AppListScreen 调用）
// ════════════════════════════════════════════

@Composable
fun VipUpgradeDialog(
    isDarkTheme: Boolean,
    title: String = "Free Limit Reached",
    message: String = "Free plan supports up to ${AppPreferences.FREE_MONITOR_LIMIT} apps.\nUpgrade to Standard for unlimited monitoring.",
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    borderColor: Color,
    accentGreen: Color,
    onDismiss: () -> Unit,
    onUpgrade: () -> Unit
) {
    val vipGold = Color(0xFFFFCC44)

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
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
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(vipGold.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⚡", fontSize = 22.sp)
                    }
                    Column {
                        Text(
                            title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                        Text(
                            "Unlock more benefits",
                            fontSize = 12.sp,
                            color = vipGold
                        )
                    }
                }

                Text(
                    message,
                    fontSize = 14.sp,
                    color = textSecondary.copy(alpha = 0.7f),
                    lineHeight = 20.sp
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentGreen.copy(alpha = 0.06f))
                        .border(1.dp, accentGreen.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "📱 Unlimited monitored apps",
                            "🎨 All 8 block themes",
                            "☁️ Cloud sync",
                            "📅 Weekly usage limits"
                        ).forEach { item ->
                            Text(item, fontSize = 12.sp, color = accentGreen.copy(alpha = 0.85f))
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = textSecondary.copy(alpha = 0.5f)
                        )
                    ) {
                        Text("Not Now", fontSize = 14.sp)
                    }

                    Button(
                        onClick = onUpgrade,
                        modifier = Modifier.weight(2f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = vipGold,
                            contentColor = Color(0xFF1A0E00)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Upgrade", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
