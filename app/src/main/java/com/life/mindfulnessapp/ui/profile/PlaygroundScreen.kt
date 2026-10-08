package com.life.mindfulnessapp.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.ui.settings.SettingsViewModel
import com.life.mindfulnessapp.ui.theme.DayBg
import com.life.mindfulnessapp.ui.theme.DayBorder
import com.life.mindfulnessapp.ui.theme.DayTextPrimary
import com.life.mindfulnessapp.ui.theme.DayTextSecondary
import com.life.mindfulnessapp.ui.theme.NightBg
import com.life.mindfulnessapp.ui.theme.NightBorder
import com.life.mindfulnessapp.ui.theme.NightTextPrimary
import com.life.mindfulnessapp.ui.theme.NightTextSecondary
import com.life.mindfulnessapp.ui.theme.themeChrome

/**
 * 试玩入口：脊线列表。从设置「试验」进入；只留已开放玩法。
 */
@Composable
fun PlaygroundScreen(
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToWallpaperStudio: () -> Unit = {},
    onNavigateToQuotePlay: () -> Unit = {}
) {
    val chrome = themeChrome()
    val isDark = chrome.isDark
    val bg = if (isDark) NightBg else DayBg
    val textPrimary = chrome.textPrimary
    val textSecondary = chrome.textSecondary
    val border = if (isDark) NightBorder else DayBorder
    val hairline = border.copy(alpha = if (isDark) 0.45f else 0.55f)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = bg,
        topBar = {
            QuietTopBar(
                title = "试玩",
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onBack = onNavigateBack
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            QuietNavRow(
                title = "桌面壁纸",
                subtitle = "静图 · 设到桌面或锁屏",
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                hairline = hairline,
                onClick = onNavigateToWallpaperStudio
            )
            QuietNavRow(
                title = "格言",
                subtitle = "门页一句 · 收藏与订阅",
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                hairline = hairline,
                onClick = onNavigateToQuotePlay
            )
        }
    }
}
