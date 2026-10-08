package com.life.mindfulnessapp.ui.vip

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.network.EarlyBirdPricing
import com.life.mindfulnessapp.data.network.VipPlan
import com.life.mindfulnessapp.ui.theme.*
import com.life.mindfulnessapp.util.PhoneNumberHintHelper
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

// ════════════════════════════════════════════
//  VIP 购买页面（Google Play Billing 版本）
// ════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VipScreen(
    viewModel: VipViewModel = hiltViewModel(),
    accessGateViewModel: AccessGateViewModel = hiltViewModel(),
    isDarkTheme: Boolean = true,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val uiState by viewModel.uiState.collectAsState()
    val accessGate by accessGateViewModel.accessGate.collectAsState()

    // 主题色
    val bgColor       = if (isDarkTheme) NightBg         else DayBg
    val cardColor     = if (isDarkTheme) NightCardBg     else DayCardBg
    val textPrimary   = if (isDarkTheme) NightTextPrimary else DayTextPrimary
    val textSecondary = if (isDarkTheme) NightTextSecondary else DayTextSecondary
    val borderColor   = if (isDarkTheme) NightBorder      else DayBorder
    val accentGreen   = if (isDarkTheme) LogoGreen        else Color(0xFF27AE60)

    val vipGold   = Color(0xFFFFCC44)

    val checkoutPhoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && activity != null) {
            PhoneNumberHintHelper.parsePhoneFromResult(activity, result.data)
                ?.let(viewModel::onCheckoutPhoneChange)
        }
    }
    val restorePhoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && activity != null) {
            PhoneNumberHintHelper.parsePhoneFromResult(activity, result.data)
                ?.let(viewModel::onRestorePhoneChange)
        }
    }

    val showEarlyBird = uiState.showEarlyBirdUpgrade
    val showPlans = !uiState.isVip || showEarlyBird
    var selectedPlan by remember {
        mutableStateOf<VipPlan?>(VipPlan.YEARLY)
    }
    LaunchedEffect(showEarlyBird) {
        if (showEarlyBird) {
            selectedPlan = VipPlan.LIFETIME
        }
    }

    // Toast
    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    // 进入页面：刷新状态 + 曝光埋点
    LaunchedEffect(Unit) {
        viewModel.onVipPageOpened()
    }

    // 从微信返回 App 时主动查一次订单（保留：后续接 SDK 仍可用）
    DisposableEffect(activity) {
        val owner = activity as? LifecycleOwner ?: return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onVipScreenResumed()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val pageTitle = when {
        showEarlyBird && uiState.isVip -> "锁价升级"
        uiState.isVip -> "我的会员"
        else -> "会员方案"
    }

    val earlyBirdTierLabel = uiState.earlyBird.tier_label?.takeIf { it.isNotBlank() } ?: "早鸟"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        pageTitle,
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

            val pending = uiState.pendingOrder
            if (uiState.isWebsiteChannel && !uiState.isVip && pending != null && pending.orderNo.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                PendingOrderCard(
                    checkout = pending,
                    cardColor = cardColor,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    vipGold = vipGold,
                    accentGreen = accentGreen,
                    onOpen = viewModel::reopenPendingCheckout,
                    onRestore = viewModel::refreshPendingFromPage
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (showEarlyBird) {
                // 已是会员码会员：讲「锁价续期」，不重复推销「成为会员」
                UpgradeContinuityCard(
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    cardColor = cardColor,
                    borderColor = borderColor,
                    accentGreen = accentGreen
                )
                Spacer(modifier = Modifier.height(16.dp))
                LockPriceCard(
                    earlyBird = uiState.earlyBird,
                    membershipDaysLeft = membershipDaysLeft(uiState.vipExpireTime),
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    vipGold = vipGold,
                    accentGreen = accentGreen,
                    cardColor = cardColor,
                    borderColor = borderColor
                )
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                // ── 会员权益（未开通 / 正式会员方案页）──
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
            }

            // ── 未开通：页内直接领码/兑码，不再绕回第 4 坑 ──
            if (!uiState.isVip) {
                MembershipCodeEntryCard(
                    hasClaimedCode = uiState.claimedInviteCode.isNotBlank(),
                    claimedCode = uiState.claimedInviteCode,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    accentGreen = accentGreen,
                    cardColor = cardColor,
                    borderColor = borderColor,
                    onOpenGate = { accessGateViewModel.request(preferRedeemIfClaimed = false) }
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // ── 会员方案：未兑码只展示正式价；兑码赠送期内才谈锁价升级 ──
            if (showPlans) {
                PlanSelectionSection(
                    selectedPlan = selectedPlan,
                    productPrices = uiState.productPrices,
                    serverPlans = uiState.serverPlans,
                    earlyBirdOnly = showEarlyBird,
                    showEarlyBirdPricing = showEarlyBird,
                    earlyBirdTierLabel = earlyBirdTierLabel,
                    isWebsiteChannel = uiState.isWebsiteChannel,
                    onPlanSelected = {
                        selectedPlan = it
                        viewModel.onPlanSelected(it)
                    },
                    isDarkTheme = isDarkTheme,
                    cardColor = cardColor,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    borderColor = borderColor,
                    accentGreen = accentGreen,
                    vipGold = vipGold
                )

                Spacer(modifier = Modifier.height(20.dp))

                PurchaseButton(
                    selectedPlan = selectedPlan,
                    isLoading = uiState.isLoading ||
                        uiState.wxPay.creating ||
                        uiState.manualCheckout.creating ||
                        (!uiState.isWebsiteChannel && uiState.purchasingPlan != null),
                    vipGold = vipGold,
                    label = when {
                        showEarlyBird -> "按${earlyBirdTierLabel}价升级"
                        uiState.isWebsiteChannel -> "去转账开通"
                        else -> "立即购买"
                    },
                    onPurchase = {
                        if (selectedPlan == null) return@PurchaseButton
                        if (uiState.isWebsiteChannel) {
                            viewModel.startManualCheckout(selectedPlan!!)
                        } else if (activity != null) {
                            viewModel.launchPurchase(activity, selectedPlan!!)
                        }
                    }
                )

                if (showEarlyBird) {
                    Spacer(modifier = Modifier.height(10.dp))
                    UpgradeCheckoutHint(
                        selectedPlan = selectedPlan,
                        textSecondary = textSecondary
                    )
                }

                if (!uiState.isWebsiteChannel && uiState.trialAvailable && !uiState.isVip) {
                    Spacer(modifier = Modifier.height(12.dp))
                    TrialButton(
                        isLoading = uiState.isLoading,
                        accentGreen = accentGreen,
                        onActivateTrial = { viewModel.activateTrial() }
                    )
                }
            }

            if (uiState.isWebsiteChannel && !uiState.isVip) {
                Spacer(modifier = Modifier.height(8.dp))
                RestoreVipRow(
                    restoring = uiState.restoring || uiState.restorePhone.busy,
                    textSecondary = textSecondary,
                    accentGreen = accentGreen,
                    onRestore = viewModel::restorePurchases
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            FooterNote(
                textSecondary = textSecondary,
                isWebsiteChannel = uiState.isWebsiteChannel,
                showEarlyBird = showEarlyBird
            )

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    if (accessGate.visible || accessGate.unlockToast != null) {
        AccessGateDialog(
            state = accessGate,
            cardColor = cardColor,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            borderColor = borderColor,
            accentGreen = accentGreen,
            onDismiss = accessGateViewModel::dismiss,
            onCodeChange = accessGateViewModel::onCodeChange,
            onRedeem = accessGateViewModel::redeem,
            onOpenClaim = accessGateViewModel::openClaimStep,
            onOpenRedeem = accessGateViewModel::openRedeemStep,
            onClaimChannelChange = accessGateViewModel::onClaimChannelChange,
            onClaimContactChange = accessGateViewModel::onClaimContactChange,
            onSubmitClaim = accessGateViewModel::submitClaim,
            onRedeemIssued = accessGateViewModel::redeemIssued,
            onViewMembership = accessGateViewModel::dismiss,
            onBackToGate = accessGateViewModel::backToGate,
            onConsumeUnlockToast = accessGateViewModel::consumeUnlockToast,
            limitReachedContext = false
        )
    }

    if (uiState.manualCheckout.visible) {
        ManualCheckoutDialog(
            checkout = uiState.manualCheckout,
            cardColor = cardColor,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            accentGreen = accentGreen,
            vipGold = vipGold,
            borderColor = borderColor,
            onDismiss = { viewModel.dismissManualCheckout(abandoned = true) },
            onCopyWechat = {
                copyToClipboard(context, wechatCopyValue(uiState.manualCheckout.contactWechat))
                Toast.makeText(context, "微信号已复制", Toast.LENGTH_SHORT).show()
            },
            onCopyRemark = {
                copyToClipboard(context, uiState.manualCheckout.remarkText)
                Toast.makeText(context, "转账备注已复制", Toast.LENGTH_SHORT).show()
            },
            onCopyOrderNo = {
                copyToClipboard(context, uiState.manualCheckout.orderNo)
                Toast.makeText(context, "订单号已复制", Toast.LENGTH_SHORT).show()
            },
            onMarkPaid = viewModel::markManualPaid,
            onRefresh = viewModel::refreshManualCheckoutStatus,
            onRetry = {
                val plan = VipPlan.entries.firstOrNull {
                    it.productId == uiState.manualCheckout.planId
                }
                if (plan != null) viewModel.startManualCheckout(plan)
            },
            onRevealPay = viewModel::revealCheckoutPayDetails,
            onPhoneChange = viewModel::onCheckoutPhoneChange,
            onSubmitPhone = viewModel::submitCheckoutPhone,
            onPickPhone = {
                val act = activity ?: return@ManualCheckoutDialog
                PhoneNumberHintHelper.requestHintIntentSender(act) { sender ->
                    if (sender != null) {
                        checkoutPhoneLauncher.launch(
                            androidx.activity.result.IntentSenderRequest.Builder(sender).build()
                        )
                    }
                }
            }
        )
    }

    if (uiState.restorePhone.visible) {
        RestorePhoneDialog(
            state = uiState.restorePhone,
            cardColor = cardColor,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            accentGreen = accentGreen,
            onDismiss = viewModel::dismissRestorePhone,
            onPhoneChange = viewModel::onRestorePhoneChange,
            onConfirm = viewModel::confirmRestorePhone,
            onPickPhone = {
                val act = activity ?: return@RestorePhoneDialog
                PhoneNumberHintHelper.requestHintIntentSender(act) { sender ->
                    if (sender != null) {
                        restorePhoneLauncher.launch(
                            androidx.activity.result.IntentSenderRequest.Builder(sender).build()
                        )
                    }
                }
            }
        )
    }

    if (uiState.wxPay.visible) {
        WxPayDialog(
            wxPay = uiState.wxPay,
            cardColor = cardColor,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            borderColor = borderColor,
            accentGreen = accentGreen,
            vipGold = vipGold,
            onDismiss = viewModel::dismissWxPay,
            onRefresh = viewModel::refreshWxPayStatus,
            onRelaunch = viewModel::relaunchWeChatPay,
            onShowQr = viewModel::showPayQr
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("heart_anchor", text))
}

private fun wechatCopyValue(server: String): String {
    val s = server.trim()
    if (s.isNotEmpty() && s.all { it.isLetterOrDigit() || it == '_' || it == '-' }) return s
    return AppPreferences.CONTACT_WECHAT
}

private fun wechatIsId(value: String): Boolean =
    value.isNotBlank() && value.all { it.isLetterOrDigit() || it == '_' || it == '-' }

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
                        uiState.isLifetimeVip -> "永久会员"
                        uiState.betaUnlocked && uiState.isVip -> "会员码会员"
                        uiState.isVip -> "会员"
                        else -> "免费版"
                    },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (uiState.isVip) vipGold else textPrimary
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

@Composable
private fun RestoreVipRow(
    restoring: Boolean,
    textSecondary: Color,
    accentGreen: Color,
    onRestore: () -> Unit
) {
    TextButton(
        onClick = onRestore,
        enabled = !restoring,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = accentGreen)
    ) {
        if (restoring) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = accentGreen
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("正在恢复…", fontSize = 13.sp)
        } else {
            Text(
                "换过手机？用绑定手机号恢复",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = textSecondary.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun PendingOrderCard(
    checkout: ManualCheckoutUi,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    vipGold: Color,
    accentGreen: Color,
    onOpen: () -> Unit,
    onRestore: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cardColor)
            .border(1.dp, vipGold.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .padding(16.dp)
    ) {
        Text(
            if (checkout.notified) "待确认订单" else "未完成订单",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = vipGold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            buildString {
                if (checkout.planTitle.isNotBlank()) append(checkout.planTitle)
                if (checkout.amountYuan.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append("¥${checkout.amountYuan}")
                }
            }.ifBlank { "微信转账开通" },
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = textPrimary
        )
        if (checkout.orderNo.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "订单号 ${checkout.orderNo}",
                fontSize = 12.sp,
                color = textSecondary.copy(alpha = 0.7f)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            if (checkout.notified)
                "确认到账后打开本页会自动开通。"
            else
                "订单已保存，点此继续转账。",
            fontSize = 12.sp,
            color = textSecondary.copy(alpha = 0.7f),
            lineHeight = 17.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onOpen) {
                Text("查看订单", color = accentGreen, fontWeight = FontWeight.Medium)
            }
            TextButton(onClick = onRestore) {
                Text("刷新状态", color = accentGreen, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ════════════════════════════════════════════
//  权益对比表（当前只强调监控坑位）
// ════════════════════════════════════════════

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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cardColor)
            .border(1.dp, borderColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        Text(
            "会员权益",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = textSecondary.copy(alpha = 0.65f)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "App 管理坑位",
                modifier = Modifier.weight(1f),
                fontSize = 15.sp,
                color = textPrimary,
                fontWeight = FontWeight.Medium
            )
            Text(
                "免费 ${AppPreferences.FREE_MONITOR_LIMIT} 个",
                fontSize = 13.sp,
                color = textSecondary.copy(alpha = 0.65f)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                "会员无限",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = vipGold
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "能力",
                modifier = Modifier.weight(1f),
                fontSize = 15.sp,
                color = textPrimary,
                fontWeight = FontWeight.Medium
            )
            Text(
                "现有三项可用",
                fontSize = 13.sp,
                color = textSecondary.copy(alpha = 0.65f)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                "后续新能力优先",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = vipGold
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "会员不受免费坑位限制；后续新能力将优先对会员开放。",
            fontSize = 12.sp,
            color = textSecondary.copy(alpha = 0.55f),
            lineHeight = 18.sp
        )
    }
}

// ════════════════════════════════════════════
//  会员码入口（会员页内，无需绕回第 4 坑）
// ════════════════════════════════════════════

@Composable
private fun MembershipCodeEntryCard(
    hasClaimedCode: Boolean,
    claimedCode: String,
    textPrimary: Color,
    textSecondary: Color,
    accentGreen: Color,
    cardColor: Color,
    borderColor: Color,
    onOpenGate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cardColor)
            .border(1.dp, borderColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .clickable(onClick = onOpenGate)
            .padding(16.dp)
    ) {
        Text(
            "会员码开通",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = textPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            if (hasClaimedCode) {
                "本机已有会员码 $claimedCode，点此兑换开通；也可重新领取。"
            } else {
                "免费领取会员码并立即开通，或兑换已有会员码。"
            },
            fontSize = 12.sp,
            color = textSecondary.copy(alpha = 0.75f),
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            if (hasClaimedCode) "兑换 / 领取会员码" else "领取或兑换会员码",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = accentGreen
        )
    }
}

// ════════════════════════════════════════════
//  锁价升级：升级后果（已是会员，不再推销「成为会员」）
// ════════════════════════════════════════════

@Composable
private fun UpgradeContinuityCard(
    textPrimary: Color,
    textSecondary: Color,
    cardColor: Color,
    borderColor: Color,
    accentGreen: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cardColor)
            .border(1.dp, borderColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(
            "升级后会怎样",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = textPrimary
        )
        Spacer(modifier = Modifier.height(10.dp))
        listOf(
            "坑位与能力不变：仍是无限 App 管理坑位，新能力优先开放",
            "期限改为付费方案：年卡自确认到账起计 365 天；永久一次买断",
            "会员码剩余赠送天数不叠加进付费期限"
        ).forEach { line ->
            Row(
                modifier = Modifier.padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("·", fontSize = 13.sp, color = accentGreen, fontWeight = FontWeight.Bold)
                Text(
                    line,
                    fontSize = 13.sp,
                    color = textSecondary.copy(alpha = 0.9f),
                    lineHeight = 19.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ════════════════════════════════════════════
//  锁价说明：拆清「权益天数」与「价格档位天数」
// ════════════════════════════════════════════

@Composable
private fun LockPriceCard(
    earlyBird: com.life.mindfulnessapp.data.network.HaEarlyBirdMeta,
    membershipDaysLeft: Int?,
    textPrimary: Color,
    textSecondary: Color,
    vipGold: Color,
    accentGreen: Color,
    cardColor: Color,
    borderColor: Color
) {
    val tier = earlyBird.tier_label?.takeIf { it.isNotBlank() } ?: "早鸟"
    var ladderExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cardColor)
            .border(1.dp, vipGold.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(
            "当前可锁定「$tier」价",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = vipGold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "赠送期内升级年卡或永久，按当前档位价成交；之后档位会分档上涨。",
            fontSize = 12.sp,
            color = textSecondary.copy(alpha = 0.78f),
            lineHeight = 18.sp
        )

        val nextDays = earlyBird.next_tier_in_days
        val nextLabel = earlyBird.next_tier_label
        if (nextDays != null && nextDays > 0 && !nextLabel.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "价格档：再 $nextDays 天涨至「$nextLabel」",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = accentGreen
            )
        }

        membershipDaysLeft?.let { days ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "权益档：会员码权益还剩 $days 天（与涨价无关）",
                fontSize = 12.sp,
                color = textSecondary.copy(alpha = 0.72f),
                lineHeight = 18.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            if (ladderExpanded) "收起档位示意" else "了解档位怎么涨",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = vipGold.copy(alpha = 0.9f),
            modifier = Modifier.clickable { ladderExpanded = !ladderExpanded }
        )
        if (ladderExpanded) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "永久：${EarlyBirdPricing.ladderLine(VipPlan.LIFETIME)}",
                fontSize = 12.sp,
                color = textPrimary.copy(alpha = 0.85f),
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "年卡：${EarlyBirdPricing.ladderLine(VipPlan.YEARLY)}",
                fontSize = 12.sp,
                color = textPrimary.copy(alpha = 0.85f),
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "示意与本地档位规则一致；下单以生成订单时锁定的金额为准。",
                fontSize = 11.sp,
                color = textSecondary.copy(alpha = 0.55f),
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun UpgradeCheckoutHint(
    selectedPlan: VipPlan?,
    textSecondary: Color
) {
    val termLine = when (selectedPlan) {
        VipPlan.LIFETIME -> "永久自确认到账起生效；会员码剩余天数不另叠加。"
        VipPlan.YEARLY -> "年卡自确认到账起计 365 天；会员码剩余天数不另叠加。"
        else -> "付费期限自确认到账起算；会员码剩余天数不另叠加。"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            termLine,
            fontSize = 11.sp,
            color = textSecondary.copy(alpha = 0.55f),
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "确认到账后打开本页自动开通。换机请用「换机恢复」。",
            fontSize = 11.sp,
            color = textSecondary.copy(alpha = 0.5f),
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun membershipDaysLeft(expireTime: Long): Int? {
    if (expireTime <= 0L) return null
    val now = System.currentTimeMillis()
    if (expireTime <= now) return 0
    return ((expireTime - now) / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(0)
}

// ════════════════════════════════════════════
//  购买方案选择
// ════════════════════════════════════════════

private data class PlanInfo(
    val plan: VipPlan,
    val title: String,
    val price: String?,
    val listPrice: String? = null,
    val tag: String? = null,
    val tagColor: Color = Color(0xFF26BB68),
    val desc: String
)

@Composable
private fun PlanSelectionSection(
    selectedPlan: VipPlan?,
    productPrices: Map<VipPlan, String>,
    serverPlans: Map<VipPlan, com.life.mindfulnessapp.data.network.HaPayPlanDto>,
    earlyBirdOnly: Boolean,
    showEarlyBirdPricing: Boolean,
    earlyBirdTierLabel: String = "早鸟",
    isWebsiteChannel: Boolean,
    onPlanSelected: (VipPlan) -> Unit,
    isDarkTheme: Boolean,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    borderColor: Color,
    accentGreen: Color,
    vipGold: Color
) {
    fun priceOf(plan: VipPlan): String {
        if (isWebsiteChannel) {
            serverPlans[plan]?.let { dto ->
                val raw = dto.amount_yuan.trimStart('¥')
                val n = raw.toDoubleOrNull()
                return if (n != null && n == n.toLong().toDouble()) "¥${n.toLong()}" else "¥$raw"
            }
            return plan.listPriceCny
        }
        return productPrices[plan] ?: plan.listPriceCny
    }

    fun listPriceOf(plan: VipPlan): String? {
        if (!showEarlyBirdPricing || !isWebsiteChannel) return null
        val dto = serverPlans[plan] ?: return null
        if (!dto.early_bird || dto.list_fen <= dto.amount_fen) return null
        val raw = dto.list_yuan.trimStart('¥')
        val n = raw.toDoubleOrNull()
        return if (n != null && n == n.toLong().toDouble()) "¥${n.toLong()}" else "¥$raw"
    }

    fun earlyTag(plan: VipPlan): Pair<String, Color>? {
        if (!showEarlyBirdPricing) return null
        val dto = serverPlans[plan] ?: return null
        if (!dto.early_bird) return null
        val label = dto.tier_label?.takeIf { it.isNotBlank() } ?: "早鸟"
        return label to vipGold
    }

    val allPlans = listOf(
        PlanInfo(
            plan = VipPlan.YEARLY,
            title = VipPlan.YEARLY.titleZh,
            price = priceOf(VipPlan.YEARLY),
            listPrice = listPriceOf(VipPlan.YEARLY),
            tag = earlyTag(VipPlan.YEARLY)?.first ?: "推荐",
            tagColor = earlyTag(VipPlan.YEARLY)?.second ?: accentGreen,
            desc = if (earlyBirdOnly) {
                "锁价续期 · 自确认到账起 365 天"
            } else if (listPriceOf(VipPlan.YEARLY) != null) {
                "早鸟价 · 约一年无限坑位 + 能力更新"
            } else {
                "约 ¥4 / 月"
            }
        ),
        PlanInfo(
            plan = VipPlan.QUARTERLY,
            title = VipPlan.QUARTERLY.titleZh,
            price = priceOf(VipPlan.QUARTERLY),
            desc = "约 ¥9 / 月"
        ),
        PlanInfo(
            plan = VipPlan.MONTHLY,
            title = VipPlan.MONTHLY.titleZh,
            price = priceOf(VipPlan.MONTHLY),
            desc = "按月灵活开通"
        ),
        PlanInfo(
            plan = VipPlan.LIFETIME,
            title = VipPlan.LIFETIME.titleZh,
            price = priceOf(VipPlan.LIFETIME),
            listPrice = listPriceOf(VipPlan.LIFETIME),
            tag = earlyTag(VipPlan.LIFETIME)?.first ?: "一次买断",
            tagColor = earlyTag(VipPlan.LIFETIME)?.second ?: vipGold,
            desc = if (earlyBirdOnly) {
                "锁价买断 · 永久无限坑位 + 能力更新"
            } else if (listPriceOf(VipPlan.LIFETIME) != null) {
                "早鸟买断 · 永久无限坑位 + 能力更新"
            } else {
                "永久无限坑位 + 能力更新"
            }
        )
    )
    val plans = if (earlyBirdOnly) {
        allPlans.filter { it.plan == VipPlan.YEARLY || it.plan == VipPlan.LIFETIME }
    } else {
        allPlans
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            if (earlyBirdOnly) "选择升级方案" else "选择方案",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = textSecondary.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 2.dp)
        )
        Text(
            when {
                earlyBirdOnly ->
                    "当前按「$earlyBirdTierLabel」价；坑位与能力不变，只把期限换成年卡或永久。"
                isWebsiteChannel ->
                    "选方案后转账开通。确认到账后打开本页会自动开通。"
                else ->
                    "会员不受免费坑位限制，后续新能力优先开放。选择方案后通过 Google Play 购买。"
            },
            fontSize = 12.sp,
            color = textSecondary.copy(alpha = 0.5f),
            lineHeight = 17.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        plans.forEach { info ->
            PlanCard(
                info = info,
                isSelected = selectedPlan == info.plan,
                selectable = true,
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
    selectable: Boolean = true,
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
        VipPlan.LIFETIME, VipPlan.YEARLY -> vipGold
        else -> accentGreen
    }
    val showSelected = selectable && isSelected

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (showSelected) accentColor.copy(alpha = 0.08f) else cardColor
            )
            .border(
                width = if (showSelected) 2.dp else 1.dp,
                color = if (showSelected) accentColor.copy(alpha = 0.5f)
                        else borderColor.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp)
            )
            .then(if (selectable) Modifier.clickable(onClick = onClick) else Modifier)
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
                    .background(if (showSelected) accentColor else Color.Transparent)
                    .border(
                        2.dp,
                        if (showSelected) accentColor else borderColor.copy(alpha = 0.5f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (showSelected) {
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

            // 价格（官网早鸟可展示划线原价；Play 异步加载）
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = info.price ?: "...",
                    fontSize = if (info.price != null) 16.sp else 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) accentColor
                            else if (info.price != null) textPrimary
                            else textSecondary.copy(alpha = 0.4f)
                )
                info.listPrice?.let { list ->
                    Text(
                        text = list,
                        fontSize = 11.sp,
                        color = textSecondary.copy(alpha = 0.45f),
                        textDecoration = TextDecoration.LineThrough
                    )
                }
            }
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
    vipGold: Color,
    label: String = "立即购买",
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
            Text("处理中…", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        } else {
            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (selectedPlan != null) label else "请先选择方案",
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
            "Try Free for 7 Days (one-time per device)",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ════════════════════════════════════════════
//  底部说明文字（Google Play 合规版本）
// ════════════════════════════════════════════

@Composable
private fun FooterNote(
    textSecondary: Color,
    isWebsiteChannel: Boolean,
    showEarlyBird: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        HorizontalDivider(color = textSecondary.copy(alpha = 0.1f))
        Spacer(modifier = Modifier.height(8.dp))
        val notes = if (isWebsiteChannel) {
            buildList {
                if (showEarlyBird) {
                    add("• 下单金额以生成订单时锁定为准")
                    add("• 使用数据仅保存在本机")
                    add("• 问题反馈：${AppPreferences.HEART_ANCHOR_SUPPORT_EMAIL}")
                } else {
                    add("• 免费版可监控最多 ${AppPreferences.FREE_MONITOR_LIMIT} 个 App")
                    add("• 转账时备注订单号；确认到账后打开本页自动开通")
                    add("• 换机请用「换机恢复」")
                    add("• 使用数据仅保存在本机")
                    add("• 问题反馈：${AppPreferences.HEART_ANCHOR_SUPPORT_EMAIL}")
                }
            }
        } else {
            listOf(
                "• 付款将通过 Google Play 账户结算",
                "• 订阅到期前 24 小时可取消自动续费",
                "• 可在 Google Play → 订阅中管理",
                "• 买断为一次性购买",
                "• 问题反馈：${AppPreferences.HEART_ANCHOR_SUPPORT_EMAIL}"
            )
        }
        notes.forEach { note ->
            Text(
                note,
                fontSize = 11.sp,
                color = textSecondary.copy(alpha = 0.4f),
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun ManualCheckoutDialog(
    checkout: ManualCheckoutUi,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentGreen: Color,
    vipGold: Color,
    borderColor: Color,
    onDismiss: () -> Unit,
    onCopyWechat: () -> Unit,
    onCopyRemark: () -> Unit,
    onCopyOrderNo: () -> Unit,
    onMarkPaid: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onRevealPay: () -> Unit,
    onPhoneChange: (String) -> Unit,
    onSubmitPhone: () -> Unit,
    onPickPhone: () -> Unit
) {
    val phase = when {
        checkout.creating -> CheckoutPhase.Creating
        checkout.collectingPhone -> CheckoutPhase.Phone
        checkout.error != null && checkout.orderNo.isBlank() -> CheckoutPhase.Error
        checkout.notified && !checkout.showPayDetails -> CheckoutPhase.Wait
        else -> CheckoutPhase.Pay
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(cardColor)
                .padding(horizontal = 22.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            AnimatedContent(
                targetState = phase,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "checkout-phase"
            ) { current ->
                when (current) {
                    CheckoutPhase.Creating -> CheckoutCreatingBody(
                        hint = checkout.hint,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        vipGold = vipGold,
                        onDismiss = onDismiss
                    )
                    CheckoutPhase.Phone -> CheckoutPhoneBody(
                        phoneInput = checkout.phoneInput,
                        error = checkout.error,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        accentGreen = accentGreen,
                        vipGold = vipGold,
                        onPhoneChange = onPhoneChange,
                        onSubmitPhone = onSubmitPhone,
                        onPickPhone = onPickPhone,
                        onDismiss = onDismiss
                    )
                    CheckoutPhase.Error -> CheckoutErrorBody(
                        error = checkout.error.orEmpty(),
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        vipGold = vipGold,
                        onRetry = onRetry,
                        onDismiss = onDismiss
                    )
                    CheckoutPhase.Wait -> CheckoutWaitBody(
                        orderNo = checkout.orderNo,
                        polling = checkout.polling,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        accentGreen = accentGreen,
                        onCopyOrderNo = onCopyOrderNo,
                        onRefresh = onRefresh,
                        onRevealPay = onRevealPay,
                        onDismiss = onDismiss
                    )
                    CheckoutPhase.Pay -> CheckoutPayBody(
                        checkout = checkout,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        accentGreen = accentGreen,
                        vipGold = vipGold,
                        borderColor = borderColor,
                        onCopyWechat = onCopyWechat,
                        onCopyRemark = onCopyRemark,
                        onMarkPaid = onMarkPaid,
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
}

private enum class CheckoutPhase { Creating, Phone, Error, Wait, Pay }

@Composable
private fun CheckoutCreatingBody(
    hint: String,
    textPrimary: Color,
    textSecondary: Color,
    vipGold: Color,
    onDismiss: () -> Unit
) {
    Column {
        Text("正在准备", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textPrimary)
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = vipGold
            )
            Text(hint.ifBlank { "正在生成订单…" }, color = textSecondary)
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("取消", color = textSecondary)
        }
    }
}

@Composable
private fun CheckoutPhoneBody(
    phoneInput: String,
    error: String?,
    textPrimary: Color,
    textSecondary: Color,
    accentGreen: Color,
    vipGold: Color,
    onPhoneChange: (String) -> Unit,
    onSubmitPhone: () -> Unit,
    onPickPhone: () -> Unit,
    onDismiss: () -> Unit
) {
    Column {
        Text("绑定手机号", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textPrimary)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "开通后换机可用此号恢复。不会发送验证码。",
            fontSize = 13.sp,
            color = textSecondary.copy(alpha = 0.7f),
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(14.dp))
        OutlinedTextField(
            value = phoneInput,
            onValueChange = onPhoneChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = {
                Text("请输入手机号", color = textSecondary.copy(alpha = 0.4f))
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            shape = RoundedCornerShape(12.dp)
        )
        Text(
            "选择本机号码",
            fontSize = 13.sp,
            color = accentGreen,
            modifier = Modifier
                .clickable(onClick = onPickPhone)
                .padding(vertical = 8.dp)
        )
        error?.let {
            Text(it, fontSize = 12.sp, color = Color(0xFFE74C3C))
            Spacer(modifier = Modifier.height(8.dp))
        }
        Button(
            onClick = onSubmitPhone,
            enabled = phoneInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(46.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = vipGold,
                contentColor = Color(0xFF1A0E00)
            ),
            shape = RoundedCornerShape(12.dp)
        ) { Text("继续", fontWeight = FontWeight.Bold) }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("取消", color = textSecondary)
        }
    }
}

@Composable
private fun CheckoutErrorBody(
    error: String,
    textPrimary: Color,
    textSecondary: Color,
    vipGold: Color,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Column {
        Text("没能生成订单", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textPrimary)
        Spacer(modifier = Modifier.height(10.dp))
        Text(error, color = Color(0xFFE74C3C), fontSize = 13.sp, lineHeight = 18.sp)
        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().height(46.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = vipGold,
                contentColor = Color(0xFF1A0E00)
            ),
            shape = RoundedCornerShape(12.dp)
        ) { Text("重试", fontWeight = FontWeight.Bold) }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("关闭", color = textSecondary)
        }
    }
}

@Composable
private fun CheckoutWaitBody(
    orderNo: String,
    polling: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    accentGreen: Color,
    onCopyOrderNo: () -> Unit,
    onRefresh: () -> Unit,
    onRevealPay: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "等待确认",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = textPrimary,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "已收到申报。确认到账后这台手机会自动开通。",
            fontSize = 14.sp,
            color = textPrimary,
            lineHeight = 20.sp,
            modifier = Modifier.fillMaxWidth()
        )
        if (orderNo.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "订单号 $orderNo",
                fontSize = 12.sp,
                color = textSecondary.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCopyOrderNo)
            )
        }
        if (polling) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = accentGreen
                )
                Text(
                    "正在自动检测开通…",
                    fontSize = 12.sp,
                    color = textSecondary.copy(alpha = 0.65f)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(
            onClick = onRefresh,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColors(contentColor = accentGreen)
        ) {
            Text("刷新状态")
        }
        TextButton(
            onClick = onRevealPay,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColors(contentColor = textSecondary)
        ) {
            Text("还没转账？查看转账信息")
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("关掉", color = textSecondary)
        }
    }
}

@Composable
private fun CheckoutPayBody(
    checkout: ManualCheckoutUi,
    textPrimary: Color,
    textSecondary: Color,
    accentGreen: Color,
    vipGold: Color,
    borderColor: Color,
    onCopyWechat: () -> Unit,
    onCopyRemark: () -> Unit,
    onMarkPaid: () -> Unit,
    onDismiss: () -> Unit
) {
    val wechatId = wechatCopyValue(checkout.contactWechat)
    Column {
        Text("转账开通", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textPrimary)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            buildString {
                if (checkout.earlyBird && checkout.tierLabel.isNotBlank()) {
                    append("${checkout.tierLabel} · ")
                }
                append(checkout.planTitle)
            },
            fontSize = 14.sp,
            color = textPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "¥${checkout.amountYuan}",
                fontSize = 28.sp,
                color = vipGold,
                fontWeight = FontWeight.Bold
            )
            if (checkout.earlyBird && checkout.listAmountYuan.isNotBlank()) {
                Text(
                    "原价 ¥${checkout.listAmountYuan}",
                    fontSize = 12.sp,
                    color = textSecondary.copy(alpha = 0.5f),
                    textDecoration = TextDecoration.LineThrough,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "转 ¥${checkout.amountYuan}，备注粘贴下方内容。",
            fontSize = 13.sp,
            color = textSecondary.copy(alpha = 0.8f),
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        CheckoutCopyRow(
            label = "转账备注",
            value = checkout.remarkText.ifBlank { checkout.orderNo },
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            accent = vipGold,
            fill = vipGold.copy(alpha = 0.08f),
            onCopy = onCopyRemark
        )
        Spacer(modifier = Modifier.height(8.dp))
        CheckoutCopyRow(
            label = "客服微信",
            value = wechatId,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            accent = accentGreen,
            fill = borderColor.copy(alpha = 0.18f),
            onCopy = onCopyWechat
        )
        if (!wechatIsId(checkout.contactWechat) && checkout.contactWechat.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "也可微信搜索「${checkout.contactWechat}」",
                fontSize = 12.sp,
                color = textSecondary.copy(alpha = 0.6f)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onMarkPaid,
            enabled = checkout.orderNo.isNotBlank() && !checkout.submitting,
            modifier = Modifier.fillMaxWidth().height(46.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = accentGreen,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (checkout.submitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
            } else {
                Text("我已转账", fontWeight = FontWeight.Bold)
            }
        }
        checkout.error?.let { err ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(err, fontSize = 12.sp, color = Color(0xFFE74C3C))
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("稍后再说", color = textSecondary)
        }
    }
}

@Composable
private fun CheckoutCopyRow(
    label: String,
    value: String,
    textPrimary: Color,
    textSecondary: Color,
    accent: Color,
    fill: Color,
    onCopy: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(fill)
            .clickable(onClick = onCopy)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                fontSize = 11.sp,
                color = textSecondary.copy(alpha = 0.65f)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = textPrimary,
                fontFamily = FontFamily.Monospace
            )
        }
        Icon(
            Icons.Default.ContentCopy,
            contentDescription = "复制$label",
            tint = accent,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun RestorePhoneDialog(
    state: RestorePhoneUi,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentGreen: Color,
    onDismiss: () -> Unit,
    onPhoneChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onPickPhone: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = { if (!state.busy) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(cardColor)
                .padding(22.dp)
        ) {
            Text("换机恢复", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "输入开通时绑定的手机号。对得上这台手机就直接开通；对不上就把会员转到这里。",
                fontSize = 13.sp,
                color = textSecondary.copy(alpha = 0.7f),
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedTextField(
                value = state.phoneInput,
                onValueChange = onPhoneChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
                placeholder = {
                    Text("请输入手机号", color = textSecondary.copy(alpha = 0.4f))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                shape = RoundedCornerShape(12.dp)
            )
            Text(
                "选择本机号码",
                fontSize = 13.sp,
                color = accentGreen,
                modifier = Modifier
                    .clickable(enabled = !state.busy, onClick = onPickPhone)
                    .padding(vertical = 8.dp)
            )
            state.error?.let {
                Text(it, fontSize = 12.sp, color = Color(0xFFE74C3C))
                Spacer(modifier = Modifier.height(8.dp))
            }
            Button(
                onClick = onConfirm,
                enabled = !state.busy && state.phoneInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(46.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentGreen,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (state.busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text("恢复到这台手机", fontWeight = FontWeight.Bold)
                }
            }
            TextButton(
                onClick = onDismiss,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("取消", color = textSecondary)
            }
        }
    }
}

@Composable
private fun WxPayDialog(
    wxPay: WxPayUi,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    borderColor: Color,
    accentGreen: Color,
    vipGold: Color,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onRelaunch: () -> Unit,
    onShowQr: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(cardColor)
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "微信支付",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "${wxPay.planTitle} · ¥${wxPay.amountYuan}",
                fontSize = 14.sp,
                color = vipGold,
                fontWeight = FontWeight.SemiBold
            )
            if (wxPay.orderNo.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "订单 ${wxPay.orderNo}",
                    fontSize = 11.sp,
                    color = textSecondary.copy(alpha = 0.5f)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            when {
                wxPay.creating -> {
                    CircularProgressIndicator(color = accentGreen)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("正在创建订单…", color = textSecondary, fontSize = 13.sp)
                }
                wxPay.showQr && wxPay.qrBitmap != null -> {
                    Image(
                        bitmap = wxPay.qrBitmap.asImageBitmap(),
                        contentDescription = "支付二维码",
                        modifier = Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .padding(8.dp)
                    )
                }
                wxPay.error != null && wxPay.qrBitmap == null && wxPay.payUrl.isBlank() -> {
                    Text(
                        wxPay.error,
                        color = Color(0xFFE74C3C),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(accentGreen.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Payments,
                            contentDescription = null,
                            tint = accentGreen,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        "请在微信中完成付款",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                wxPay.hint.ifBlank { "支付完成后将自动开通会员" },
                fontSize = 13.sp,
                color = textSecondary.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
            if (wxPay.contactWechat.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "客服微信：${wxPay.contactWechat}",
                    fontSize = 12.sp,
                    color = accentGreen
                )
            }
            if (wxPay.polling) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "返回本页后将自动检测开通状态…",
                    fontSize = 12.sp,
                    color = accentGreen.copy(alpha = 0.85f)
                )
            }
            if (wxPay.error != null && (wxPay.showQr || wxPay.payUrl.isNotBlank())) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(wxPay.error, fontSize = 12.sp, color = Color(0xFFE74C3C), textAlign = TextAlign.Center)
            }

            Spacer(modifier = Modifier.height(18.dp))
            if (!wxPay.creating &&
                ((wxPay.payMode == "wxpay_app" && wxPay.appPay != null) || wxPay.payUrl.isNotBlank()) &&
                !wxPay.showQr
            ) {
                TextButton(
                    onClick = onRelaunch,
                    colors = ButtonDefaults.textButtonColors(contentColor = accentGreen)
                ) {
                    Text("重新打开微信支付")
                }
            }
            if (!wxPay.creating && !wxPay.showQr && wxPay.qrBitmap != null) {
                TextButton(
                    onClick = onShowQr,
                    colors = ButtonDefaults.textButtonColors(contentColor = textSecondary)
                ) {
                    Text("打不开微信？显示收款码")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColors(contentColor = textSecondary)
                ) {
                    Text("关闭")
                }
                Button(
                    onClick = onRefresh,
                    modifier = Modifier.weight(1.4f),
                    enabled = !wxPay.creating && wxPay.orderNo.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentGreen,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("我已支付", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** 打开 H5 支付页或 weixin:// Native 码；H5 附带支付完成回跳官网。 */
private fun openWeChatPayUrl(context: android.content.Context, rawUrl: String, payMode: String): Boolean {
    return try {
        val url = if (payMode == "wxpay_h5" && rawUrl.startsWith("http")) {
            val redirect = URLEncoder.encode(
                AppPreferences.HEART_ANCHOR_WEB_URL,
                StandardCharsets.UTF_8.name()
            )
            val sep = if (rawUrl.contains("?")) "&" else "?"
            // 微信 H5 要求拼接 redirect_url，支付后回到浏览器页
            if (rawUrl.contains("redirect_url=")) rawUrl else "$rawUrl${sep}redirect_url=$redirect"
        } else {
            rawUrl
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }
}


