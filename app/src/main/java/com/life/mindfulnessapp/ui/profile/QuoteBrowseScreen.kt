package com.life.mindfulnessapp.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.data.repository.DisplayQuote
import com.life.mindfulnessapp.ui.settings.SettingsViewModel
import com.life.mindfulnessapp.ui.theme.DayBg
import com.life.mindfulnessapp.ui.theme.DayTextPrimary
import com.life.mindfulnessapp.ui.theme.DayTextSecondary
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.theme.NightBg
import com.life.mindfulnessapp.ui.theme.NightTextPrimary
import com.life.mindfulnessapp.ui.theme.NightTextSecondary
import com.life.mindfulnessapp.ui.theme.themeChrome

/**
 * 从精选池点选收藏 / 取消；已收藏用字色加重。
 */
@Composable
fun QuoteBrowseScreen(
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    quoteViewModel: QuotePlayViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val chrome = themeChrome()
    val isDark = chrome.isDark
    val bg = if (isDark) NightBg else DayBg
    val textPrimary = chrome.textPrimary
    val textSecondary = chrome.textSecondary
    val accent = if (isDark) LogoGreen else Color(0xFF27AE60)

    val browse by quoteViewModel.browse.collectAsState()
    val loading by quoteViewModel.browseLoading.collectAsState()
    val favorites by quoteViewModel.favorites.collectAsState()
    val favoriteKeys = remember(favorites) {
        buildSet {
            favorites.forEach { item ->
                if (item.id > 0) add("id:${item.id}")
                add("c:${item.content}")
            }
        }
    }

    LaunchedEffect(Unit) {
        quoteViewModel.loadBrowse()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = bg,
        topBar = {
            QuietTopBar(title = "从池里选", textPrimary = textPrimary, onBack = onNavigateBack)
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

            when {
                loading && browse.isEmpty() -> {
                    Text(
                        text = "加载中",
                        fontSize = 14.sp,
                        color = textSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.padding(vertical = 20.dp)
                    )
                }
                browse.isEmpty() -> {
                    Text(
                        text = "暂时没有可选项",
                        fontSize = 14.sp,
                        color = textSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.padding(vertical = 20.dp)
                    )
                }
                else -> {
                    browse.forEach { quote ->
                        val on = (quote.id > 0 && "id:${quote.id}" in favoriteKeys) ||
                            "c:${quote.content}" in favoriteKeys
                        BrowseQuoteRow(
                            quote = quote,
                            favorited = on,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            accent = accent,
                            onToggle = { quoteViewModel.toggleFavorite(quote) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseQuoteRow(
    quote: DisplayQuote,
    favorited: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    accent: Color,
    onToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 16.dp)
    ) {
        Text(
            text = quote.content,
            fontSize = 16.sp,
            fontWeight = if (favorited) FontWeight.SemiBold else FontWeight.Normal,
            color = if (favorited) textPrimary else textPrimary.copy(alpha = 0.72f),
            lineHeight = 24.sp
        )
        val author = quote.author.trim().removePrefix("—").trim()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = author,
                fontSize = 13.sp,
                color = textSecondary.copy(alpha = 0.42f),
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(
                text = if (favorited) "♥" else "♡",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (favorited) accent else textSecondary.copy(alpha = 0.5f)
            )
        }
    }
}
