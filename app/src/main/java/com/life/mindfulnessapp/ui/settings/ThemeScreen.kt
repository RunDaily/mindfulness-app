package com.life.mindfulnessapp.ui.settings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.domain.model.ThemePack
import com.life.mindfulnessapp.ui.theme.DayBg
import com.life.mindfulnessapp.ui.theme.DayBorder
import com.life.mindfulnessapp.ui.theme.DayCardBg
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.theme.MistAccent
import com.life.mindfulnessapp.ui.theme.MistBg
import com.life.mindfulnessapp.ui.theme.MistBorder
import com.life.mindfulnessapp.ui.theme.MistCardBg
import com.life.mindfulnessapp.ui.theme.NightBg
import com.life.mindfulnessapp.ui.theme.NightBorder
import com.life.mindfulnessapp.ui.theme.NightCardBg
import com.life.mindfulnessapp.ui.theme.themeChrome

@Composable
fun ThemeScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val preferredPack by viewModel.themePack.collectAsState()
    val followSystem by viewModel.themeFollowSystem.collectAsState()
    val chrome = themeChrome()
    val bgColor = chrome.bg
    val cardColor = chrome.card
    val textPrimary = chrome.textPrimary
    val textSecondary = chrome.textSecondary
    val borderColor = chrome.border
    val accentGreen = chrome.accent

    Scaffold(
        containerColor = bgColor
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = textPrimary
                    )
                }
                Text(
                    text = "外观",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimary,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "气质",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = textSecondary.copy(alpha = 0.55f),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            ThemePackGrid(
                preferred = preferredPack,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                accentGreen = accentGreen,
                onSelect = viewModel::setThemePack
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = "明暗",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = textSecondary.copy(alpha = 0.55f),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(cardColor)
                    .border(1.dp, borderColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "跟随系统",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = textPrimary
                        )
                        Text(
                            text = if (preferredPack == ThemePack.Mist) {
                                "雾青为刻意锁定，不跟系统"
                            } else {
                                "在日间与夜锚之间切换"
                            },
                            fontSize = 12.sp,
                            color = textSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    SettingsThemeFollowSwitch(
                        checked = followSystem,
                        enabled = preferredPack.allowsFollowSystem,
                        accentGreen = accentGreen,
                        trackOff = textPrimary.copy(alpha = 0.16f),
                        onCheckedChange = viewModel::setThemeFollowSystem
                    )
                }
            }

            Text(
                text = "气质定色与胶囊壳透明度。跟随只绑日间与夜锚。",
                fontSize = 12.sp,
                color = textSecondary.copy(alpha = 0.55f),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ThemePackGrid(
    preferred: ThemePack,
    textPrimary: Color,
    textSecondary: Color,
    accentGreen: Color,
    onSelect: (ThemePack) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ThemePackCard(
                pack = ThemePack.Night,
                selected = preferred == ThemePack.Night,
                modifier = Modifier.weight(1f),
                accentGreen = accentGreen,
                labelColor = textPrimary,
                hintColor = textSecondary,
                onClick = { onSelect(ThemePack.Night) }
            )
            ThemePackCard(
                pack = ThemePack.Day,
                selected = preferred == ThemePack.Day,
                modifier = Modifier.weight(1f),
                accentGreen = accentGreen,
                labelColor = textPrimary,
                hintColor = textSecondary,
                onClick = { onSelect(ThemePack.Day) }
            )
        }
        ThemePackCard(
            pack = ThemePack.Mist,
            selected = preferred == ThemePack.Mist,
            modifier = Modifier.fillMaxWidth(),
            accentGreen = accentGreen,
            labelColor = textPrimary,
            hintColor = textSecondary,
            wide = true,
            onClick = { onSelect(ThemePack.Mist) }
        )
    }
}

@Composable
private fun ThemePackCard(
    pack: ThemePack,
    selected: Boolean,
    modifier: Modifier = Modifier,
    accentGreen: Color,
    labelColor: Color,
    hintColor: Color,
    wide: Boolean = false,
    onClick: () -> Unit
) {
    val preview = packPreview(pack)
    val shape = RoundedCornerShape(14.dp)
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (wide) 64.dp else 88.dp)
                .clip(shape)
                .border(
                    width = if (selected) 1.5.dp else 1.dp,
                    color = if (selected) accentGreen.copy(alpha = 0.65f) else preview.outline.copy(alpha = 0.7f),
                    shape = shape
                )
                .clickable(onClick = onClick)
                .background(preview.canvas)
                .padding(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(preview.surface)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(preview.bar)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(preview.bar.copy(alpha = 0.22f))
                )
                if (!wide) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.48f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(preview.bar.copy(alpha = 0.12f))
                    )
                }
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(accentGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "已选中",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, start = 2.dp, end = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = pack.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = labelColor
            )
            Text(
                text = if (wide) "${pack.subtitle} · 壳更透" else pack.subtitle,
                fontSize = 11.sp,
                color = hintColor.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun SettingsThemeFollowSwitch(
    checked: Boolean,
    enabled: Boolean,
    accentGreen: Color,
    trackOff: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    val track = when {
        !enabled -> trackOff.copy(alpha = 0.08f)
        checked -> accentGreen.copy(alpha = 0.9f)
        else -> trackOff
    }
    Box(
        modifier = Modifier
            .width(42.dp)
            .height(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(track)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(3.dp),
        contentAlignment = if (checked && enabled) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = if (enabled) 1f else 0.5f))
        )
    }
}

private data class PackPreview(
    val canvas: Color,
    val surface: Color,
    val bar: Color,
    val outline: Color,
)

private fun packPreview(pack: ThemePack): PackPreview = when (pack) {
    ThemePack.Night -> PackPreview(NightBg, NightCardBg, LogoGreen, NightBorder)
    ThemePack.Day -> PackPreview(DayBg, DayCardBg, Color(0xFF1B9E55), DayBorder)
    ThemePack.Mist -> PackPreview(MistBg, MistCardBg, MistAccent, MistBorder)
}
