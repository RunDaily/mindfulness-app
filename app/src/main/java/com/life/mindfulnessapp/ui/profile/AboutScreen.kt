package com.life.mindfulnessapp.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.BuildConfig
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.ui.settings.SettingsViewModel
import com.life.mindfulnessapp.ui.theme.DayBg
import com.life.mindfulnessapp.ui.theme.DayBorder
import com.life.mindfulnessapp.ui.theme.DayTextPrimary
import com.life.mindfulnessapp.ui.theme.DayTextSecondary
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.theme.NightBg
import com.life.mindfulnessapp.ui.theme.NightBorder
import com.life.mindfulnessapp.ui.theme.NightTextPrimary
import com.life.mindfulnessapp.ui.theme.NightTextSecondary
import com.life.mindfulnessapp.ui.theme.themeChrome
import com.life.mindfulnessapp.ui.update.AppUpdateViewModel

/**
 * 关于：脊线列表。无图标卡片墙。
 */
@Composable
fun AboutScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    appUpdateViewModel: AppUpdateViewModel = hiltViewModel(
        LocalContext.current as ComponentActivity
    ),
    onNavigateBack: () -> Unit = {},
    onNavigateToAppUpdate: () -> Unit = {},
    onNavigateToProductManual: () -> Unit = {}
) {
    val context = LocalContext.current
    val chrome = themeChrome()
    val isDarkTheme = chrome.isDark
    val pendingUpdate by appUpdateViewModel.pendingUpdate.collectAsState()
    val bgColor = chrome.bg
    val textPrimary = chrome.textPrimary
    val textSecondary = chrome.textSecondary
    val borderColor = chrome.border
    val hairline = borderColor.copy(alpha = if (isDarkTheme) 0.45f else 0.55f)
    val accentGreen = chrome.accent

    LaunchedEffect(Unit) {
        appUpdateViewModel.prepareUpdatePage()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = bgColor,
        topBar = {
            QuietTopBar(
                title = "关于",
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onBack = onNavigateBack
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "心锚",
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                color = textPrimary
            )
            Text(
                text = "版本 ${BuildConfig.VERSION_NAME}",
                fontSize = 12.sp,
                color = textSecondary.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            QuietNavRow(
                title = "检查更新",
                subtitle = when {
                    pendingUpdate != null ->
                        "可更新至 v${pendingUpdate!!.release.version_name}"
                    else -> "当前已是最新版本"
                },
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                hairline = hairline,
                badge = if (pendingUpdate != null) "新" else null,
                badgeColor = accentGreen,
                onClick = onNavigateToAppUpdate
            )
            QuietNavRow(
                title = "产品说明书",
                subtitle = "目录与章节",
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                hairline = hairline,
                onClick = onNavigateToProductManual
            )
            QuietNavRow(
                title = "隐私政策",
                subtitle = "我们如何处理你的数据",
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                hairline = hairline,
                onClick = {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(AppPreferences.HEART_ANCHOR_PRIVACY_URL)
                        )
                    )
                }
            )
            QuietNavRow(
                title = "用户协议",
                subtitle = "使用条款与约定",
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                hairline = hairline,
                onClick = {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(AppPreferences.HEART_ANCHOR_TERMS_URL)
                        )
                    )
                }
            )
            QuietNavRow(
                title = "心锚官网",
                subtitle = "下载与更新说明",
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                hairline = hairline,
                onClick = {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(AppPreferences.HEART_ANCHOR_WEB_URL)
                        )
                    )
                }
            )
            QuietNavRow(
                title = "联系支持",
                subtitle = AppPreferences.HEART_ANCHOR_SUPPORT_EMAIL,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                hairline = hairline,
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:${AppPreferences.HEART_ANCHOR_SUPPORT_EMAIL}")
                        }
                    )
                }
            )

            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = "ICP 备案",
                fontSize = 10.sp,
                letterSpacing = 0.1.sp,
                color = textSecondary.copy(alpha = 0.5f)
            )
            Text(
                text = AppPreferences.HEART_ANCHOR_ICP_NUMBER,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = textPrimary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clickable {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(AppPreferences.MIIT_BEIAN_URL))
                        )
                    }
            )
            Text(
                text = "beian.miit.gov.cn",
                fontSize = 11.sp,
                color = textSecondary.copy(alpha = 0.45f),
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "使用数据仅保存在本机",
                fontSize = 11.sp,
                color = textSecondary.copy(alpha = 0.38f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
