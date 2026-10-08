package com.life.mindfulnessapp.ui.settings

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.data.export.QuoteCardExporter
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.theme.themeChrome
import kotlinx.coroutines.launch

/**
 * 通知点入的格言时刻页：可读、可藏、可分享成卡片。
 */
@Composable
fun QuoteMomentScreen(
    quoteId: Int = 0,
    quoteContent: String = "",
    quoteAuthor: String = "",
    quoteAuthorId: Int = 0,
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val chrome = themeChrome()
    val isDark = chrome.isDark
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var content by remember { mutableStateOf(quoteContent) }
    var author by remember { mutableStateOf(quoteAuthor) }
    var id by remember { mutableStateOf(quoteId) }
    var authorId by remember { mutableStateOf(quoteAuthorId) }
    var favorited by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }

    LaunchedEffect(quoteId, quoteContent) {
        val resolved = viewModel.resolveQuoteMoment(
            id = quoteId,
            content = quoteContent,
            author = quoteAuthor,
            authorId = quoteAuthorId
        )
        content = resolved.content
        author = resolved.author
        id = resolved.id
        authorId = resolved.authorId
        favorited = viewModel.isQuoteFavorited(resolved.id, resolved.content)
    }

    val bgTop = if (isDark) Color(0xFF0B1210) else Color(0xFFF4F7F4)
    val bgBottom = if (isDark) Color(0xFF121A16) else Color(0xFFE8EEE8)
    val cardBg = if (isDark) Color(0xFF1A2420) else Color(0xFFFFFFFF)
    val textPrimary = if (isDark) Color(0xFFF2F5F2) else Color(0xFF1A221C)
    val textSecondary = if (isDark) Color(0xFF9AABA0) else Color(0xFF5C6B60)
    val accent = if (isDark) Color(0xFF7BC49A) else LogoGreen
    val authorClean = author.trim().removePrefix("—").trim()

    val favoriteScale by animateFloatAsState(
        targetValue = if (favorited) 1.12f else 1f,
        label = "fav_scale"
    )
    val cardLayer = rememberGraphicsLayer()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(bgTop, bgBottom))
            )
    ) {
        // soft atmospheric orbs
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    drawCircle(
                        color = accent.copy(alpha = if (isDark) 0.08f else 0.10f),
                        radius = size.minDimension * 0.42f,
                        center = Offset(size.width * 0.15f, size.height * 0.18f)
                    )
                    drawCircle(
                        color = accent.copy(alpha = if (isDark) 0.06f else 0.08f),
                        radius = size.minDimension * 0.35f,
                        center = Offset(size.width * 0.88f, size.height * 0.72f)
                    )
                }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = textPrimary
                    )
                }
                Text(
                    text = "一句格言",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimary,
                    modifier = Modifier.weight(1f)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawWithContent {
                            cardLayer.record {
                                this@drawWithContent.drawContent()
                            }
                            drawContent()
                        }
                ) {
                    QuoteShareCard(
                        content = content.ifBlank { "还没有可展示的格言" },
                        author = authorClean,
                        cardBg = cardBg,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        accent = accent,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(28.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MomentActionChip(
                        label = if (favorited) "已收藏" else "收藏",
                        icon = {
                            Icon(
                                imageVector = if (favorited) Icons.Default.Favorite
                                else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = if (favorited) Color(0xFFE2556E) else accent,
                                modifier = Modifier
                                    .size(20.dp)
                                    .scale(favoriteScale)
                            )
                        },
                        accent = accent,
                        textPrimary = textPrimary,
                        filled = favorited,
                        enabled = content.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            favorited = viewModel.toggleFavoriteQuote(
                                id = id,
                                content = content,
                                author = authorClean,
                                authorId = authorId
                            )
                            Toast.makeText(
                                context,
                                if (favorited) "已收藏" else "已取消收藏",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                    MomentActionChip(
                        label = if (sharing) "生成中…" else "分享卡片",
                        icon = {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        accent = accent,
                        textPrimary = textPrimary,
                        filled = false,
                        enabled = content.isNotBlank() && !sharing,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (sharing) return@MomentActionChip
                            sharing = true
                            scope.launch {
                                try {
                                    if (cardLayer.size.width <= 0 || cardLayer.size.height <= 0) {
                                        Toast.makeText(
                                            context,
                                            "卡片还在生成，请稍后再试",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@launch
                                    }
                                    val bitmap = cardLayer.toImageBitmap().asAndroidBitmap()
                                    val png = QuoteCardExporter.bitmapToPng(bitmap)
                                    val file = QuoteCardExporter.writeToCache(
                                        context,
                                        QuoteCardExporter.suggestedFileName(authorClean),
                                        png
                                    )
                                    QuoteCardExporter.sharePng(context, file)
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        e.message?.takeIf { it.isNotBlank() } ?: "分享失败",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } finally {
                                    sharing = false
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuoteShareCard(
    content: String,
    author: String,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(cardBg)
            .border(1.dp, accent.copy(alpha = 0.18f), RoundedCornerShape(24.dp))
            .padding(horizontal = 28.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.55f))
        )
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = "「",
            fontSize = 42.sp,
            fontWeight = FontWeight.Light,
            color = accent.copy(alpha = 0.35f),
            modifier = Modifier
                .align(Alignment.Start)
                .padding(bottom = 4.dp)
        )
        Text(
            text = content,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            color = textPrimary,
            lineHeight = 36.sp,
            textAlign = TextAlign.Start,
            fontFamily = FontFamily.Serif,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(28.dp))
        if (author.isNotBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(1.dp)
                        .background(accent.copy(alpha = 0.35f))
                )
                Text(
                    text = author,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = textSecondary,
                    letterSpacing = 0.6.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(36.dp))
        Text(
            text = "心 锚",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = accent.copy(alpha = 0.55f),
            letterSpacing = 4.sp
        )
    }
}

@Composable
private fun MomentActionChip(
    label: String,
    icon: @Composable () -> Unit,
    accent: Color,
    textPrimary: Color,
    filled: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (filled) accent.copy(alpha = 0.16f)
                else textPrimary.copy(alpha = 0.05f)
            )
            .border(
                1.dp,
                if (filled) accent.copy(alpha = 0.4f)
                else textPrimary.copy(alpha = 0.08f),
                RoundedCornerShape(16.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        icon()
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (filled) accent else textPrimary.copy(alpha = if (enabled) 0.88f else 0.4f)
        )
    }
}
