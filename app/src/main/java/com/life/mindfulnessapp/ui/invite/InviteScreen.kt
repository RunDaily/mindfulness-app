package com.life.mindfulnessapp.ui.invite

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.ui.theme.*

// ════════════════════════════════════════════
//  邀请好友页面
// ════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteScreen(
    viewModel: InviteViewModel = hiltViewModel(),
    isDarkTheme: Boolean = true,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val uiState by viewModel.uiState.collectAsState()

    // 主题色
    val bgColor       = if (isDarkTheme) NightBg         else DayBg
    val cardColor     = if (isDarkTheme) NightCardBg     else DayCardBg
    val textPrimary   = if (isDarkTheme) NightTextPrimary else DayTextPrimary
    val textSecondary = if (isDarkTheme) NightTextSecondary else DayTextSecondary
    val borderColor   = if (isDarkTheme) NightBorder      else DayBorder
    val accentGreen   = if (isDarkTheme) LogoGreen        else Color(0xFF27AE60)
    val vipGold       = Color(0xFFFFCC44)

    // 兑换码输入框状态
    var redeemInput by remember { mutableStateOf("") }

    // 进入时刷新
    LaunchedEffect(Unit) { viewModel.refresh() }

    // Toast
    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "邀请好友",
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

            // ── 顶部奖励 Banner ────────────────────────────────────────────
            InviteHeroBanner(
                isDarkTheme = isDarkTheme,
                vipGold = vipGold,
                accentGreen = accentGreen,
                textPrimary = textPrimary,
                textSecondary = textSecondary
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── 我的邀请码 ─────────────────────────────────────────────────
            if (uiState.isLoggedIn) {
                MyInviteCodeCard(
                    code = uiState.myCode,
                    invitedCount = uiState.invitedCount,
                    rewardSummary = uiState.rewardSummary,
                    isLoading = uiState.isLoading,
                    isDarkTheme = isDarkTheme,
                    cardColor = cardColor,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    borderColor = borderColor,
                    accentGreen = accentGreen,
                    vipGold = vipGold,
                    onCopy = {
                        val code = uiState.myCode
                        if (code.isNotBlank()) {
                            clipboard.setText(AnnotatedString(code))
                            Toast.makeText(context, "邀请码已复制", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onShare = {
                        val code = uiState.myCode
                        if (code.isNotBlank()) {
                            val shareText = "我在用「心锚」管理手机使用时间，已坚持了一段时间。\n" +
                                "用我的邀请码 $code 注册，可以获得 14 天高级版试用（普通注册只有 7 天）✨\n" +
                                "下载地址：https://goodgoods.life"
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(intent, "分享邀请码"))
                        } else {
                            Toast.makeText(context, "邀请码加载中，请稍候", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ── 使用他人邀请码 ─────────────────────────────────────────
                RedeemCodeCard(
                    hasRedeemed = uiState.hasRedeemed,
                    redeemInput = redeemInput,
                    onInputChange = { redeemInput = it.uppercase().take(12) },
                    isRedeeming = uiState.isRedeeming,
                    isDarkTheme = isDarkTheme,
                    cardColor = cardColor,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    borderColor = borderColor,
                    accentGreen = accentGreen,
                    vipGold = vipGold,
                    onRedeem = {
                        viewModel.redeemCode(redeemInput)
                        redeemInput = ""
                    }
                )

            } else {
                // 未登录提示
                NotLoggedInCard(
                    isDarkTheme = isDarkTheme,
                    cardColor = cardColor,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    borderColor = borderColor,
                    accentGreen = accentGreen
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── 规则说明 ───────────────────────────────────────────────────
            InviteRulesCard(
                isDarkTheme = isDarkTheme,
                cardColor = cardColor,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                borderColor = borderColor,
                accentGreen = accentGreen
            )

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// ════════════════════════════════════════════
//  顶部奖励 Banner
// ════════════════════════════════════════════

@Composable
private fun InviteHeroBanner(
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
                    listOf(
                        if (isDarkTheme) Color(0xFF1A1200) else Color(0xFFFFF8E7),
                        if (isDarkTheme) Color(0xFF0D1A0D) else Color(0xFFEAF6EF)
                    )
                )
            )
            .border(
                1.dp,
                vipGold.copy(alpha = 0.25f),
                RoundedCornerShape(20.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🎁", fontSize = 44.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "邀请好友，双向互赢",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = vipGold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "每成功邀请 1 位好友注册\n你获得 7 天标准版延期，好友获得 14 天试用",
                fontSize = 13.sp,
                color = textSecondary.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 奖励说明卡片
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RewardChip(
                    modifier = Modifier.weight(1f),
                    emoji = "🙋",
                    label = "你获得",
                    value = "+7天/人",
                    subText = "最多 3 人",
                    color = accentGreen
                )
                RewardChip(
                    modifier = Modifier.weight(1f),
                    emoji = "👥",
                    label = "好友获得",
                    value = "14天试用",
                    subText = "普通仅7天",
                    color = vipGold
                )
            }
        }
    }
}

@Composable
private fun RewardChip(
    modifier: Modifier = Modifier,
    emoji: String,
    label: String,
    value: String,
    subText: String,
    color: Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 22.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, fontSize = 11.sp, color = color.copy(alpha = 0.7f))
            Text(
                value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                subText,
                fontSize = 10.sp,
                color = color.copy(alpha = 0.5f)
            )
        }
    }
}

// ════════════════════════════════════════════
//  我的邀请码卡片
// ════════════════════════════════════════════

@Composable
private fun MyInviteCodeCard(
    code: String,
    invitedCount: Int,
    rewardSummary: String,
    isLoading: Boolean,
    isDarkTheme: Boolean,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    borderColor: Color,
    accentGreen: Color,
    vipGold: Color,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cardColor)
            .border(1.dp, borderColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        // 标题行
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(vipGold.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Share, null, tint = vipGold, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("我的邀请码", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
                Text(
                    text = if (invitedCount > 0) "已成功邀请 $invitedCount 人" else "分享给好友，让他们填写",
                    fontSize = 12.sp,
                    color = if (invitedCount > 0) accentGreen else textSecondary.copy(alpha = 0.5f)
                )
            }
            // 邀请数量徽章
            if (invitedCount > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentGreen.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        "$invitedCount 人",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentGreen
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 邀请码展示区
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        listOf(vipGold.copy(alpha = 0.08f), vipGold.copy(alpha = 0.04f))
                    )
                )
                .border(1.dp, vipGold.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                .clickable(onClick = onCopy)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading && code.isBlank()) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = vipGold
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (code.isBlank()) "加载中..." else code,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (code.isBlank()) vipGold.copy(alpha = 0.4f) else vipGold,
                        letterSpacing = 6.sp
                    )
                    if (code.isNotBlank()) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "复制",
                            tint = vipGold.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "点击邀请码可一键复制",
            fontSize = 11.sp,
            color = textSecondary.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 奖励进度条
        if (invitedCount > 0 || rewardSummary.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentGreen.copy(alpha = 0.06f))
                    .border(1.dp, accentGreen.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.EmojiEvents, null, tint = accentGreen, modifier = Modifier.size(16.dp))
                    Text(
                        rewardSummary,
                        fontSize = 12.sp,
                        color = accentGreen,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // 分享按钮
        Button(
            onClick = onShare,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = vipGold,
                contentColor = Color(0xFF1A0E00)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("一键分享邀请码", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ════════════════════════════════════════════
//  使用他人邀请码
// ════════════════════════════════════════════

@Composable
private fun RedeemCodeCard(
    hasRedeemed: Boolean,
    redeemInput: String,
    onInputChange: (String) -> Unit,
    isRedeeming: Boolean,
    isDarkTheme: Boolean,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    borderColor: Color,
    accentGreen: Color,
    vipGold: Color,
    onRedeem: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cardColor)
            .border(1.dp, borderColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accentGreen.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CardGiftcard, null, tint = accentGreen, modifier = Modifier.size(18.dp))
            }
            Column {
                Text("使用邀请码", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
                Text(
                    text = if (hasRedeemed) "已使用过邀请码" else "填写朋友的邀请码，获得 14 天 VIP 试用",
                    fontSize = 12.sp,
                    color = if (hasRedeemed) accentGreen else textSecondary.copy(alpha = 0.5f)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (hasRedeemed) {
            // 已使用状态
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentGreen.copy(alpha = 0.06f))
                    .border(1.dp, accentGreen.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = accentGreen, modifier = Modifier.size(18.dp))
                    Text(
                        "您已成功使用过邀请码，试用期已延长至 14 天 🎉",
                        fontSize = 13.sp,
                        color = accentGreen,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            // 输入框
            OutlinedTextField(
                value = redeemInput,
                onValueChange = onInputChange,
                placeholder = {
                    Text(
                        "输入 6~8 位邀请码",
                        color = textSecondary.copy(alpha = 0.4f),
                        fontSize = 14.sp
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentGreen,
                    unfocusedBorderColor = borderColor,
                    focusedTextColor = textPrimary,
                    unfocusedTextColor = textPrimary,
                    cursorColor = accentGreen
                ),
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    AnimatedVisibility(visible = redeemInput.isNotBlank(), enter = fadeIn(), exit = fadeOut()) {
                        IconButton(onClick = { onInputChange("") }) {
                            Icon(Icons.Default.Close, null, tint = textSecondary.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onRedeem,
                enabled = redeemInput.length >= 4 && !isRedeeming,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentGreen,
                    contentColor = Color.White,
                    disabledContainerColor = accentGreen.copy(alpha = 0.35f),
                    disabledContentColor = Color.White.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isRedeeming) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("兑换中...", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.Redeem, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("立即兑换", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ════════════════════════════════════════════
//  未登录提示卡片
// ════════════════════════════════════════════

@Composable
private fun NotLoggedInCard(
    isDarkTheme: Boolean,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    borderColor: Color,
    accentGreen: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cardColor)
            .border(1.dp, borderColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(accentGreen.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text("⚓", fontSize = 28.sp)
            }
            Text("登录后查看邀请码", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
            Text(
                "前往「设置 → 云端同步」登录心锚账号，即可获取专属邀请码",
                fontSize = 13.sp,
                color = textSecondary.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                lineHeight = 19.sp
            )
        }
    }
}

// ════════════════════════════════════════════
//  邀请规则说明
// ════════════════════════════════════════════

@Composable
private fun InviteRulesCard(
    isDarkTheme: Boolean,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    borderColor: Color,
    accentGreen: Color
) {
    val rules = listOf(
        "邀请码为注册时填写，已注册用户无法补填",
        "被邀请方：试用从 7 天延长至 14 天（仅限一次）",
        "邀请方：每成功邀请 1 人获 7 天标准版延期，上限 3 人（共 21 天）",
        "不能使用自己的邀请码",
        "奖励在被邀请方完成注册后自动发放"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cardColor)
            .border(1.dp, borderColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Default.Info, null, tint = accentGreen.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
            Text(
                "活动规则",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = textSecondary.copy(alpha = 0.6f),
                letterSpacing = 0.8.sp
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        rules.forEach { rule ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(textSecondary.copy(alpha = 0.3f))
                )
                Text(
                    rule,
                    fontSize = 12.sp,
                    color = textSecondary.copy(alpha = 0.55f),
                    lineHeight = 18.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
