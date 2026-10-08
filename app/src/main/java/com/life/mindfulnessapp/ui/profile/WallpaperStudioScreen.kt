package com.life.mindfulnessapp.ui.profile

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.service.KeepAliveWallpaperService
import com.life.mindfulnessapp.ui.settings.SettingsViewModel
import com.life.mindfulnessapp.ui.theme.DayBg
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.theme.NightBg
import com.life.mindfulnessapp.ui.theme.themeChrome
import com.life.mindfulnessapp.wallpaper.WallpaperApplier
import com.life.mindfulnessapp.wallpaper.WallpaperLines
import com.life.mindfulnessapp.wallpaper.WallpaperRenderer
import com.life.mindfulnessapp.wallpaper.WallpaperStyleStore
import com.life.mindfulnessapp.wallpaper.WallpaperTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 桌面壁纸工作室：与保活「壁纸守护」同一条路。
 * 设为桌面 → Live Wallpaper（视觉 + 守护）；锁屏仍可另设静图。
 */
@Composable
fun WallpaperStudioScreen(
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    studioViewModel: WallpaperStudioViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val chrome = themeChrome()
    val isDark = chrome.isDark
    val bg = if (isDark) NightBg else DayBg
    val textPrimary = chrome.textPrimary
    val textSecondary = chrome.textSecondary
    val accent = if (isDark) LogoGreen else Color(0xFF27AE60)

    val lineState by studioViewModel.line.collectAsState()
    val quoteLoading by studioViewModel.quoteLoading.collectAsState()

    val saved = remember { WallpaperStyleStore.load(context) }
    var template by remember { mutableStateOf(saved.template) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var applying by remember { mutableStateOf(false) }
    var applyHome by remember { mutableStateOf(true) }
    var applyLock by remember { mutableStateOf(false) }
    val lockSupported = remember { WallpaperApplier.supportsLockSeparately() }
    var guardianOn by remember { mutableStateOf(KeepAliveWallpaperService.isActive(context)) }

    LaunchedEffect(Unit) {
        studioViewModel.restoreSaved(saved.line, saved.author)
    }

    val previewWidthPx = with(density) { 200.dp.roundToPx() }
    val previewHeightPx = (previewWidthPx * 19f / 9f).toInt()

    LaunchedEffect(template, lineState.text, lineState.author, previewWidthPx, previewHeightPx) {
        previewBitmap = withContext(Dispatchers.Default) {
            WallpaperRenderer.render(
                width = previewWidthPx,
                height = previewHeightPx,
                template = template,
                line = lineState.text,
                author = lineState.author
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose { previewBitmap = null }
    }

    val canApply = applyHome || (applyLock && lockSupported)
    val actionLabel = when {
        applyHome && applyLock && lockSupported -> "设为桌面壁纸并同步锁屏"
        applyHome -> "设为桌面壁纸"
        applyLock && lockSupported -> "设为锁屏"
        else -> "设为壁纸"
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = bg,
        topBar = {
            QuietTopBar(title = "桌面壁纸", textPrimary = textPrimary, onBack = onNavigateBack)
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .width(200.dp)
                    .aspectRatio(9f / 19f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(textPrimary.copy(alpha = 0.04f)),
                contentAlignment = Alignment.Center
            ) {
                val bmp = previewBitmap
                if (bmp != null && !bmp.isRecycled) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            if (guardianOn) {
                Text(
                    text = "桌面守护中",
                    fontSize = 12.sp,
                    color = accent.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            Spacer(Modifier.height(28.dp))

            QuietFieldLabel("氛围", textSecondary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                WallpaperTemplate.entries.forEach { item ->
                    QuietChoiceText(
                        label = item.label,
                        selected = item == template,
                        accent = accent,
                        textPrimary = textPrimary,
                        onClick = { template = item }
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            QuietFieldLabel("句子", textSecondary)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                WallpaperLines.all.forEach { item ->
                    QuietChoiceText(
                        label = item,
                        selected = lineState.fromPreset && lineState.text == item,
                        accent = accent,
                        textPrimary = textPrimary,
                        onClick = { studioViewModel.selectPreset(item) }
                    )
                }
            }
            Text(
                text = if (quoteLoading) "取句中" else "换一句格言",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (quoteLoading) textSecondary.copy(alpha = 0.35f) else accent.copy(alpha = 0.9f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .clickable(enabled = !quoteLoading) { studioViewModel.refreshQuote() }
            )

            Spacer(Modifier.height(28.dp))

            QuietFieldLabel("设到", textSecondary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                QuietChoiceText(
                    label = "桌面",
                    selected = applyHome,
                    accent = accent,
                    textPrimary = textPrimary,
                    onClick = { applyHome = !applyHome }
                )
                if (lockSupported) {
                    QuietChoiceText(
                        label = "锁屏",
                        selected = applyLock,
                        accent = accent,
                        textPrimary = textPrimary,
                        onClick = { applyLock = !applyLock }
                    )
                }
            }

            Text(
                text = if (applyHome) {
                    "设为桌面即用心锚壁纸，并开启后台守护"
                } else {
                    "仅锁屏为静图，不含守护"
                },
                fontSize = 11.sp,
                color = textSecondary.copy(alpha = 0.45f),
                lineHeight = 15.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            )

            Spacer(Modifier.height(36.dp))

            Text(
                text = if (applying) "正在设置…" else actionLabel,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    applying || !canApply -> textSecondary.copy(alpha = 0.35f)
                    else -> accent
                },
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !applying && canApply) {
                        applying = true
                        scope.launch {
                            WallpaperStyleStore.save(
                                context = context,
                                template = template,
                                line = lineState.text,
                                author = lineState.author
                            )
                            var lockMsg: String? = null
                            if (applyLock && lockSupported) {
                                val result = withContext(Dispatchers.Default) {
                                    val metrics = context.resources.displayMetrics
                                    val (w, h) = WallpaperRenderer.targetSize(metrics)
                                    val full = WallpaperRenderer.render(
                                        width = w,
                                        height = h,
                                        template = template,
                                        line = lineState.text,
                                        author = lineState.author
                                    )
                                    try {
                                        WallpaperApplier.apply(
                                            context,
                                            full,
                                            setOf(WallpaperApplier.Target.Lock)
                                        )
                                    } finally {
                                        full.recycle()
                                    }
                                }
                                lockMsg = when (result) {
                                    is WallpaperApplier.Result.Success -> "锁屏已设"
                                    is WallpaperApplier.Result.Partial -> result.message
                                    is WallpaperApplier.Result.Failure -> result.message
                                }
                            }
                            applying = false
                            val alreadyOn = KeepAliveWallpaperService.isActive(context)
                            if (applyHome) {
                                val tip = when {
                                    alreadyOn -> {
                                        listOfNotNull(lockMsg, "桌面样式已更新，稍后刷新").joinToString(" · ")
                                    }
                                    KeepAliveWallpaperService.openSetter(context) -> {
                                        listOfNotNull(
                                            lockMsg,
                                            "请确认设为桌面壁纸，即开启守护"
                                        ).joinToString(" · ")
                                    }
                                    else -> "无法打开系统壁纸设置"
                                }
                                Toast.makeText(context, tip, Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    lockMsg ?: "已完成",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            guardianOn = KeepAliveWallpaperService.isActive(context)
                        }
                    }
                    .padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun QuietFieldLabel(text: String, textSecondary: Color) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = textSecondary.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    )
}

@Composable
private fun QuietChoiceText(
    label: String,
    selected: Boolean,
    accent: Color,
    textPrimary: Color,
    onClick: () -> Unit
) {
    Text(
        text = label,
        fontSize = 15.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = when {
            selected -> accent
            else -> textPrimary.copy(alpha = 0.42f)
        },
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    )
}
