package com.life.mindfulnessapp.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import android.widget.Toast
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.ui.account.AccountUiState
import com.life.mindfulnessapp.ui.account.AccountViewModel
import com.life.mindfulnessapp.ui.settings.SettingsViewModel
import com.life.mindfulnessapp.ui.theme.*

@Composable
fun ProfileScreen(
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    accountViewModel: AccountViewModel = hiltViewModel(),
    interceptThemeId: String = "default",
    onNavigateToVip: () -> Unit = {},
    onNavigateToInvite: () -> Unit = {},
    onNavigateToAppList: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
) {
    val context = LocalContext.current
    val isDarkTheme by settingsViewModel.isDarkTheme.collectAsState()
    val vipLevel by settingsViewModel.vipLevel.collectAsState()
    val accountState by accountViewModel.uiState.collectAsState()

    val bgColor        = if (isDarkTheme) NightBg          else DayBg
    val cardColor      = if (isDarkTheme) NightCardBg       else DayCardBg
    val textPrimary    = if (isDarkTheme) NightTextPrimary  else DayTextPrimary
    val textSecondary  = if (isDarkTheme) NightTextSecondary else DayTextSecondary
    val borderColor    = if (isDarkTheme) NightBorder       else DayBorder
    val accentGreen    = if (isDarkTheme) LogoGreen         else Color(0xFF27AE60)

    var showAccountDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    // Toast
    LaunchedEffect(accountState.toastMessage) {
        accountState.toastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            accountViewModel.clearToast()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        // ── 顶部标题 ────────────────────────────────────────────────────────
        Text(
            text = "我",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = textPrimary,
            letterSpacing = (-0.5).sp,
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(top = 14.dp, bottom = 4.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── 账号信息卡片 ────────────────────────────────────────────────────
        ProfileAccountCard(
            state = accountState,
            vipLevel = vipLevel,
            isDarkTheme = isDarkTheme,
            cardColor = cardColor,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            borderColor = borderColor,
            accentGreen = accentGreen,
            onLoginClick = { showAccountDialog = true },
            onLogoutClick = { accountViewModel.logout() },
            onSyncClick = { accountViewModel.syncToCloud() },
            onDeleteAccountClick = { showDeleteAccountDialog = true },
            onUpgradeClick = onNavigateToVip
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── 功能区：VIP & 邀请（免费公测期隐藏，正式收费时恢复）────────────
        if (!AppPreferences.FREE_PERIOD_ENABLED) {
            ProfileSectionLabel("权益", textSecondary)

            ProfileMenuGroup(
                cardColor = cardColor,
                borderColor = borderColor,
                items = listOf(
                    ProfileMenuItem(
                        icon = Icons.Default.WorkspacePremium,
                        iconTint = Color(0xFFFFCC44),
                        title = if (vipLevel > 0) "已开通 VIP" else "升级 VIP",
                        subtitle = if (vipLevel > 0) "享受全部 VIP 权益" else "解锁无限App监控、全部主题等功能",
                        onClick = onNavigateToVip
                    ),
                    ProfileMenuItem(
                        icon = Icons.Default.People,
                        iconTint = Color(0xFFFFCC44).copy(alpha = 0.85f),
                        title = "邀请好友",
                        subtitle = "邀请 1 人各获 7 天 VIP，最多叠加 21 天",
                        onClick = onNavigateToInvite
                    )
                ),
                textPrimary = textPrimary,
                textSecondary = textSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))
        }

        // ── 功能区：应用管理 ────────────────────────────────────────────────
        ProfileSectionLabel("管理", textSecondary)

        ProfileMenuGroup(
            cardColor = cardColor,
            borderColor = borderColor,
            items = listOf(
                ProfileMenuItem(
                    icon = Icons.Default.Apps,
                    iconTint = accentGreen,
                    title = "管理监控应用",
                    subtitle = "添加、移除或修改 App 的每日使用限额",
                    onClick = onNavigateToAppList
                ),
                ProfileMenuItem(
                    icon = Icons.Default.Favorite,
                    iconTint = Color(0xFFE05C6A),
                    title = "我的收藏",
                    subtitle = "查看在拦截页收藏的格言",
                    onClick = onNavigateToFavorites
                ),
                ProfileMenuItem(
                    icon = Icons.Default.Settings,
                    iconTint = textPrimary.copy(alpha = 0.55f),
                    title = "设置",
                    subtitle = "服务、权限、提醒、主题与外观、数据",
                    onClick = onNavigateToSettings
                )
            ),
            textPrimary = textPrimary,
            textSecondary = textSecondary
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ── 功能区：帮助与关于 ───────────────────────────────────────────────
        ProfileSectionLabel("帮助", textSecondary)

        ProfileMenuGroup(
            cardColor = cardColor,
            borderColor = borderColor,
            items = listOf(
                ProfileMenuItem(
                    icon = Icons.Default.Mail,
                    iconTint = textPrimary.copy(alpha = 0.4f),
                    title = "意见反馈",
                    subtitle = "发送邮件反馈问题或建议",
                    onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:support@goodgoods.life")
                            putExtra(Intent.EXTRA_SUBJECT, "[心锚] 意见反馈")
                        }
                        context.startActivity(Intent.createChooser(intent, "选择邮件应用"))
                    }
                ),
                ProfileMenuItem(
                    icon = Icons.Default.StarRate,
                    iconTint = Color(0xFFFFCC44).copy(alpha = 0.7f),
                    title = "给我们评分",
                    subtitle = "喜欢心锚？在应用商店给我们五星好评 🌟",
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
                            )
                        } catch (_: Exception) {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}"))
                            )
                        }
                    }
                ),
                ProfileMenuItem(
                    icon = Icons.Default.Info,
                    iconTint = textPrimary.copy(alpha = 0.35f),
                    title = "关于",
                    subtitle = "版本 1.0.0  ·  隐私政策  ·  用户协议",
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://goodgoods.life/privacy"))
                        )
                    }
                )
            ),
            textPrimary = textPrimary,
            textSecondary = textSecondary
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── 底部理念语 ──────────────────────────────────────────────────────
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
        Text(
            text = "「${quotes[quoteIndex]}」",
            fontSize = 13.sp,
            color = textSecondary.copy(alpha = 0.35f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))
    }

    // ── 登录弹窗 ────────────────────────────────────────────────────────────
    if (showAccountDialog) {
        ProfileAccountDialog(
            state = accountState,
            cardColor = cardColor,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            accentGreen = accentGreen,
            borderColor = borderColor,
            onDismiss = { showAccountDialog = false },
            onLogin = { phone, password, inviteCode ->
                accountViewModel.loginOrRegister(phone, password, inviteCode) {
                    showAccountDialog = false
                }
            }
        )
    }

    // ── 注销账号弹窗 ─────────────────────────────────────────────────────────
    if (showDeleteAccountDialog) {
        ProfileDeleteAccountDialog(
            cardColor = cardColor,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            accentGreen = accentGreen,
            borderColor = borderColor,
            isDeleting = accountState.isDeleting,
            onDismiss = { showDeleteAccountDialog = false },
            onConfirm = {
                accountViewModel.deleteAccount {
                    showDeleteAccountDialog = false
                }
            }
        )
    }
}

// ── 账号信息卡片 ──────────────────────────────────────────────────────────────

@Composable
private fun ProfileAccountCard(
    state: AccountUiState,
    vipLevel: Int,
    isDarkTheme: Boolean,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    borderColor: Color,
    accentGreen: Color,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onSyncClick: () -> Unit,
    onDeleteAccountClick: () -> Unit,
    onUpgradeClick: () -> Unit
) {
    val isVip = vipLevel > 0
    val vipGold = Color(0xFFFFCC44)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isVip) {
                    Brush.linearGradient(
                        listOf(
                            vipGold.copy(alpha = 0.10f),
                            cardColor
                        )
                    )
                } else {
                    Brush.linearGradient(listOf(cardColor, cardColor))
                }
            )
            .border(
                1.dp,
                if (isVip) vipGold.copy(alpha = 0.25f) else borderColor.copy(alpha = 0.5f),
                RoundedCornerShape(20.dp)
            )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // ── 头像 + 账号信息行 ────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 大头像
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(
                            if (state.isLoggedIn) accentGreen.copy(alpha = 0.15f)
                            else textPrimary.copy(alpha = 0.07f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (state.isLoggedIn) state.avatarEmoji else "⚓",
                        fontSize = 28.sp
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    if (state.isLoggedIn) {
                        Text(
                            text = state.nickname.ifBlank { state.username },
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "⚓ ${state.username}",
                            fontSize = 13.sp,
                            color = accentGreen.copy(alpha = 0.8f)
                        )
                        if (isVip) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(vipGold.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = if (vipLevel >= 2) "👑 高级版 VIP" else "⚡ 标准版 VIP",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = vipGold
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "未登录",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "登录后可将使用数据同步到云端",
                            fontSize = 13.sp,
                            color = textSecondary.copy(alpha = 0.5f)
                        )
                    }
                }

                // 右侧操作
                if (!state.isLoggedIn) {
                    Button(
                        onClick = onLoginClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentGreen,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text("登录", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    // 已登录：仅在正式收费阶段显示「升级 VIP」按钮
                    if (!AppPreferences.FREE_PERIOD_ENABLED && !isVip) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(vipGold.copy(alpha = 0.12f))
                                .clickable { onUpgradeClick() }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                "升级 VIP",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = vipGold
                            )
                        }
                    }
                }
            }

            // ── 已登录时的操作行（同步 + 退出）────────────────────────────────
            if (state.isLoggedIn) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = accentGreen.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(14.dp))

                if (state.syncStatus.isNotBlank()) {
                    Text(
                        text = state.syncStatus,
                        fontSize = 12.sp,
                        color = textPrimary.copy(alpha = 0.45f),
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
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
                        contentPadding = PaddingValues(vertical = 9.dp)
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
                    OutlinedButton(
                        onClick = onLogoutClick,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = textPrimary.copy(alpha = 0.5f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, borderColor.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 9.dp)
                    ) {
                        Text("退出登录", fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 注销账号：低调文字链，不加颜色强调
                Text(
                    text = "注销账号",
                    fontSize = 12.sp,
                    color = textPrimary.copy(alpha = 0.22f),
                    modifier = Modifier
                        .align(Alignment.End)
                        .clickable(
                            indication = null,
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        ) { onDeleteAccountClick() }
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                )
            }
        }
    }
}

// ── 组分标题 ──────────────────────────────────────────────────────────────────

@Composable
private fun ProfileSectionLabel(title: String, textSecondary: Color) {
    Text(
        text = title.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = textSecondary.copy(alpha = 0.45f),
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

// ── 菜单组（多项放在同一张卡片里，用分割线分隔）──────────────────────────────

data class ProfileMenuItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val iconTint: Color,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit
)

@Composable
private fun ProfileMenuGroup(
    cardColor: Color,
    borderColor: Color,
    items: List<ProfileMenuItem>,
    textPrimary: Color,
    textSecondary: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cardColor)
            .border(1.dp, borderColor.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
    ) {
        Column {
            items.forEachIndexed { idx, item ->
                ProfileMenuRow(
                    icon = item.icon,
                    iconTint = item.iconTint,
                    title = item.title,
                    subtitle = item.subtitle,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onClick = item.onClick
                )
                if (idx < items.lastIndex) {
                    HorizontalDivider(
                        color = borderColor.copy(alpha = 0.25f),
                        modifier = Modifier.padding(start = 56.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileMenuRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    textPrimary: Color,
    textSecondary: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = textPrimary
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = textSecondary.copy(alpha = 0.45f)
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = textPrimary.copy(alpha = 0.2f),
            modifier = Modifier.size(18.dp)
        )
    }
}

// ── 登录弹窗 ──────────────────────────────────────────────────────────────────

@Composable
private fun ProfileAccountDialog(
    state: AccountUiState,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentGreen: Color,
    borderColor: Color,
    onDismiss: () -> Unit,
    onLogin: (phone: String, password: String, inviteCode: String) -> Unit
) {
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var inviteCode by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
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

                OutlinedTextField(
                    value = phone,
                    onValueChange = { if (it.length <= 11) phone = it },
                    label = { Text("手机号", fontSize = 14.sp) },
                    placeholder = { Text("请输入手机号", color = textSecondary.copy(alpha = 0.4f)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
                    ),
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

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码（至少 6 位）", fontSize = 14.sp) },
                    placeholder = { Text("请输入密码", color = textSecondary.copy(alpha = 0.4f)) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible)
                        androidx.compose.ui.text.input.VisualTransformation.None
                    else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
                    ),
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

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = inviteCode,
                    onValueChange = { if (it.length <= 12) inviteCode = it.uppercase() },
                    label = { Text("邀请码（选填）", fontSize = 14.sp) },
                    placeholder = { Text("新用户填写后可获 14 天 VIP", color = textSecondary.copy(alpha = 0.35f)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFFCC44).copy(alpha = 0.7f),
                        unfocusedBorderColor = borderColor.copy(alpha = 0.5f),
                        focusedLabelColor = Color(0xFFFFCC44),
                        unfocusedLabelColor = textSecondary.copy(alpha = 0.5f),
                        focusedTextColor = textPrimary,
                        unfocusedTextColor = textPrimary,
                        cursorColor = Color(0xFFFFCC44)
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
                    ) { Text("取消", fontSize = 14.sp) }

                    Button(
                        onClick = { onLogin(phone.trim(), password, inviteCode.trim()) },
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

// ── 注销账号弹窗 ──────────────────────────────────────────────────────────────

@Composable
private fun ProfileDeleteAccountDialog(
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentGreen: Color,
    borderColor: Color,
    isDeleting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val dangerColor = Color(0xFFE74C3C)

    Dialog(onDismissRequest = { if (!isDeleting) onDismiss() }) {
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
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(dangerColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.DeleteForever,
                            contentDescription = null,
                            tint = dangerColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            "注销账号",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                        Text(
                            "此操作不可撤销，请谨慎操作",
                            fontSize = 12.sp,
                            color = dangerColor.copy(alpha = 0.7f)
                        )
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
                        Text("注销后将会：", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
                        listOf("永久删除云端所有使用记录", "清除账号及个人信息", "无法找回，无法撤销").forEach { hint ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(5.dp).clip(CircleShape)
                                        .background(dangerColor.copy(alpha = 0.6f))
                                )
                                Text(hint, fontSize = 12.sp, color = textPrimary.copy(alpha = 0.65f), lineHeight = 18.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isDeleting,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = textPrimary.copy(alpha = 0.6f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("再想想", fontSize = 14.sp) }

                    Button(
                        onClick = onConfirm,
                        enabled = !isDeleting,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = dangerColor,
                            contentColor = Color.White,
                            disabledContainerColor = dangerColor.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isDeleting) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(if (isDeleting) "注销中..." else "确认注销", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
