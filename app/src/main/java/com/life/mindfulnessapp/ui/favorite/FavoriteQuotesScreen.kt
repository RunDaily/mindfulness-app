package com.life.mindfulnessapp.ui.favorite

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.data.db.entity.FavoriteQuoteEntity
import com.life.mindfulnessapp.ui.settings.SettingsViewModel
import com.life.mindfulnessapp.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteQuotesScreen(
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    viewModel: FavoriteQuotesViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val isDarkTheme by settingsViewModel.isDarkTheme.collectAsState()
    val favorites by viewModel.favorites.collectAsState()

    val bgColor       = if (isDarkTheme) NightBg          else DayBg
    val cardColor     = if (isDarkTheme) NightCardBg       else DayCardBg
    val textPrimary   = if (isDarkTheme) NightTextPrimary  else DayTextPrimary
    val textSecondary = if (isDarkTheme) NightTextSecondary else DayTextSecondary
    val borderColor   = if (isDarkTheme) NightBorder       else DayBorder
    val accentGreen   = if (isDarkTheme) LogoGreen         else Color(0xFF27AE60)
    val heartColor    = Color(0xFFE05C6A)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
    ) {
        // ── 顶部导航栏 ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "返回",
                    tint = textPrimary
                )
            }
            Text(
                text = "我的收藏",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary,
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
            // 收藏数量徽标
            if (favorites.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(heartColor.copy(alpha = 0.10f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = null,
                            tint = heartColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "${favorites.size}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = heartColor
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
            }
        }

        HorizontalDivider(color = borderColor.copy(alpha = 0.3f))

        if (favorites.isEmpty()) {
            // ── 空状态 ──────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = "🤍", fontSize = 48.sp)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "还没有收藏的格言",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = textPrimary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "在拦截页点击 ♡ 即可收藏打动你的格言",
                    fontSize = 14.sp,
                    color = textSecondary.copy(alpha = 0.5f),
                    lineHeight = 22.sp
                )
            }
        } else {
            // ── 收藏列表 ─────────────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                itemsIndexed(
                    items = favorites,
                    key = { _, item -> item.content }
                ) { _, quote ->
                    FavoriteQuoteCard(
                        quote = quote,
                        cardColor = cardColor,
                        borderColor = borderColor,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        accentGreen = accentGreen,
                        heartColor = heartColor,
                        onDelete = { viewModel.removeFavorite(quote.content) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteQuoteCard(
    quote: FavoriteQuoteEntity,
    cardColor: Color,
    borderColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentGreen: Color,
    heartColor: Color,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardColor)
            .border(1.dp, borderColor.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // 引号装饰
            Text(
                text = "\u201C",
                fontSize = 20.sp,
                color = heartColor.copy(alpha = 0.3f),
                fontWeight = FontWeight.Bold,
                lineHeight = 1.sp
            )

            // 格言正文
            Text(
                text = quote.content,
                fontSize = 15.sp,
                fontWeight = FontWeight.Light,
                fontStyle = FontStyle.Italic,
                color = textPrimary,
                lineHeight = 26.sp,
                letterSpacing = 0.3.sp
            )

            // 作者 + 删除按钮行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = quote.author.ifBlank { "" },
                    fontSize = 12.sp,
                    color = textSecondary.copy(alpha = 0.55f)
                )

                if (showDeleteConfirm) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { showDeleteConfirm = false },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("取消", fontSize = 12.sp, color = textSecondary.copy(alpha = 0.5f))
                        }
                        TextButton(
                            onClick = {
                                showDeleteConfirm = false
                                onDelete()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "取消收藏",
                                fontSize = 12.sp,
                                color = heartColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "取消收藏",
                            tint = textSecondary.copy(alpha = 0.3f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
