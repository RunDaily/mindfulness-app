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
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.ui.settings.SettingsViewModel
import com.life.mindfulnessapp.ui.theme.*

@Composable
fun ProfileScreen(
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToVip: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToWeekAwareness: () -> Unit = {},
) {
    val context = LocalContext.current
    val isDarkTheme by settingsViewModel.isDarkTheme.collectAsState()
    val vipLevel by settingsViewModel.vipLevel.collectAsState()

    val bgColor = if (isDarkTheme) NightBg else DayBg
    val cardColor = if (isDarkTheme) NightCardBg else DayCardBg
    val textPrimary = if (isDarkTheme) NightTextPrimary else DayTextPrimary
    val textSecondary = if (isDarkTheme) NightTextSecondary else DayTextSecondary
    val borderColor = if (isDarkTheme) NightBorder else DayBorder
    val accentGreen = if (isDarkTheme) LogoGreen else Color(0xFF27AE60)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
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

        ProfileIdentityCard(
            vipLevel = vipLevel,
            cardColor = cardColor,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            borderColor = borderColor,
            accentGreen = accentGreen,
            onUpgradeClick = onNavigateToVip
        )

        Spacer(modifier = Modifier.height(20.dp))

        // VIP 入口（免费公测期隐藏，正式收费时恢复）
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
                        subtitle = if (vipLevel > 0) "享受全部 VIP 权益" else "解锁无限 App 监控、全部主题等功能",
                        onClick = onNavigateToVip
                    )
                ),
                textPrimary = textPrimary,
                textSecondary = textSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))
        }

        ProfileSectionLabel("觉察", textSecondary)

        ProfileMenuGroup(
            cardColor = cardColor,
            borderColor = borderColor,
            items = listOf(
                ProfileMenuItem(
                    icon = Icons.Default.Visibility,
                    iconTint = accentGreen.copy(alpha = 0.85f),
                    title = "本周觉察",
                    subtitle = "主路径之一：看这一周清不清醒",
                    onClick = onNavigateToWeekAwareness
                )
            ),
            textPrimary = textPrimary,
            textSecondary = textSecondary
        )

        Spacer(modifier = Modifier.height(12.dp))

        ProfileSectionLabel("管理", textSecondary)

        ProfileMenuGroup(
            cardColor = cardColor,
            borderColor = borderColor,
            items = listOf(
                ProfileMenuItem(
                    icon = Icons.Default.Settings,
                    iconTint = textPrimary.copy(alpha = 0.55f),
                    title = "设置",
                    subtitle = "服务、权限、拦截体验、主题与数据",
                    onClick = onNavigateToSettings
                )
            ),
            textPrimary = textPrimary,
            textSecondary = textSecondary
        )

        Spacer(modifier = Modifier.height(12.dp))

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
}

@Composable
private fun ProfileIdentityCard(
    vipLevel: Int,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    borderColor: Color,
    accentGreen: Color,
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
                    Brush.linearGradient(listOf(vipGold.copy(alpha = 0.10f), cardColor))
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
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(accentGreen.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "⚓", fontSize = 28.sp)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "心锚",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "使用数据仅保存在本机",
                    fontSize = 13.sp,
                    color = textSecondary.copy(alpha = 0.55f)
                )
                if (isVip) {
                    Spacer(modifier = Modifier.height(6.dp))
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
            }

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
}

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

data class ProfileMenuItem(
    val icon: ImageVector,
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
