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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.data.CachedAuthor
import com.life.mindfulnessapp.data.CachedPushedQuote
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
 * 格言模块：门页快关 + 收藏夹 + 作者订阅。
 * 订阅决定抽句来源；收藏是个人夹，不收窄门页句子。
 */
@Composable
fun QuotePlayScreen(
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    quoteViewModel: QuotePlayViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToBrowse: () -> Unit = {},
    onNavigateToAuthors: () -> Unit = {}
) {
    val chrome = themeChrome()
    val isDark = chrome.isDark
    val bg = if (isDark) NightBg else DayBg
    val textPrimary = chrome.textPrimary
    val textSecondary = chrome.textSecondary
    val accent = if (isDark) LogoGreen else Color(0xFF27AE60)
    val hairline = textPrimary.copy(alpha = if (isDark) 0.12f else 0.1f)

    val favorites by quoteViewModel.favorites.collectAsState()
    val stopQuoteEnabled by quoteViewModel.stopQuoteEnabled.collectAsState()
    val subscribedAuthors by quoteViewModel.subscribedAuthors.collectAsState()
    var tab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        quoteViewModel.refreshAuthors()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = bg,
        topBar = {
            QuietTopBar(title = "格言", textPrimary = textPrimary, onBack = onNavigateBack)
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
            QuietSwitchRow(
                title = "门页格言",
                subtitle = if (stopQuoteEnabled) {
                    "到点与锁门时显示一句"
                } else {
                    "已关闭 · 停住页不显示"
                },
                checked = stopQuoteEnabled,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                isDark = isDark,
                onCheckedChange = quoteViewModel::setStopQuoteEnabled
            )
            HorizontalDivider(color = hairline.copy(alpha = 0.55f))

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                QuoteTab(
                    label = "收藏",
                    selected = tab == 0,
                    accent = accent,
                    textSecondary = textSecondary,
                    onClick = { tab = 0 }
                )
                QuoteTab(
                    label = "订阅",
                    selected = tab == 1,
                    accent = accent,
                    textSecondary = textSecondary,
                    onClick = { tab = 1 }
                )
            }

            Spacer(Modifier.height(16.dp))

            when (tab) {
                0 -> FavoritesTab(
                    favorites = favorites,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    accent = accent,
                    onBrowse = onNavigateToBrowse,
                    onUnfavorite = quoteViewModel::unfavorite
                )
                else -> SubscribeTab(
                    authors = subscribedAuthors,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    accent = accent,
                    onPickAuthors = onNavigateToAuthors
                )
            }
        }
    }
}

@Composable
private fun QuoteTab(
    label: String,
    selected: Boolean,
    accent: Color,
    textSecondary: Color,
    onClick: () -> Unit
) {
    Text(
        text = label,
        fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) accent else textSecondary.copy(alpha = 0.45f),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    )
}

@Composable
private fun FavoritesTab(
    favorites: List<CachedPushedQuote>,
    textPrimary: Color,
    textSecondary: Color,
    accent: Color,
    onBrowse: () -> Unit,
    onUnfavorite: (CachedPushedQuote) -> Unit
) {
    Text(
        text = "从池里选",
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = accent,
        modifier = Modifier
            .clickable(onClick = onBrowse)
            .padding(vertical = 8.dp)
    )
    Spacer(Modifier.height(4.dp))
    if (favorites.isEmpty()) {
        Text(
            text = "还没有收藏",
            fontSize = 15.sp,
            color = textPrimary.copy(alpha = 0.38f),
            modifier = Modifier.padding(vertical = 20.dp)
        )
        Text(
            text = "门页遇见好句时可点 ♡，或从池里选。",
            fontSize = 12.sp,
            color = textSecondary.copy(alpha = 0.5f),
            lineHeight = 18.sp
        )
    } else {
        favorites.forEach { item ->
            FavoriteQuoteRow(
                item = item,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                accent = accent,
                onUnfavorite = { onUnfavorite(item) }
            )
        }
    }
}

@Composable
private fun SubscribeTab(
    authors: List<CachedAuthor>,
    textPrimary: Color,
    textSecondary: Color,
    accent: Color,
    onPickAuthors: () -> Unit
) {
    Text(
        text = "选作者",
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = accent,
        modifier = Modifier
            .clickable(onClick = onPickAuthors)
            .padding(vertical = 8.dp)
    )
    Text(
        text = "已订作者优先供句；未订阅用精选池。",
        fontSize = 12.sp,
        color = textSecondary.copy(alpha = 0.5f),
        lineHeight = 18.sp,
        modifier = Modifier.padding(bottom = 12.dp)
    )
    if (authors.isEmpty()) {
        Text(
            text = "还没有订阅作者",
            fontSize = 15.sp,
            color = textPrimary.copy(alpha = 0.38f),
            modifier = Modifier.padding(vertical = 16.dp)
        )
    } else {
        authors.forEach { author ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Text(
                    text = author.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = textPrimary
                )
                if (author.bio.isNotBlank()) {
                    Text(
                        text = author.bio,
                        fontSize = 12.sp,
                        color = textSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 2
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteQuoteRow(
    item: CachedPushedQuote,
    textPrimary: Color,
    textSecondary: Color,
    accent: Color,
    onUnfavorite: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Text(
            text = item.content,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = textPrimary,
            lineHeight = 24.sp
        )
        val author = item.author.trim().removePrefix("—").trim()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = author,
                fontSize = 13.sp,
                color = textSecondary.copy(alpha = 0.45f),
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(
                text = "♥",
                fontSize = 13.sp,
                color = accent,
                modifier = Modifier.clickable(onClick = onUnfavorite)
            )
        }
    }
}
