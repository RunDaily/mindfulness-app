package com.life.mindfulnessapp.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.collectAsState
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.data.repository.FALLBACK_QUOTES
import com.life.mindfulnessapp.data.repository.QuoteRepository
import com.life.mindfulnessapp.domain.usecase.GetAppHistoryUsageUseCase
import com.life.mindfulnessapp.ui.theme.MindfulnessAppTheme
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars

/** 胶囊目标位置（屏幕坐标系，用于退场动画定位） */
data class CapsuleTargetPosition(
    val x: Float,
    val y: Float
)

/** 按钮冷静期倒计时秒数 */
private const val COOLDOWN_SECONDS = 3

/** 已填写目的后的缩短冷静期秒数 */
private const val COOLDOWN_WITH_PURPOSE = 1

// ── 本地兜底名言（格式化 author 供 UI 使用）────────────────────────────────────
private val DISPLAY_FALLBACK_QUOTES: List<Pair<String, String>> = FALLBACK_QUOTES.map { (content, author) ->
    Pair(content, if (author.isNotBlank()) "— $author" else "")
}

// ── 拦截主题配置 ──────────────────────────────────────────────────────────────

data class InterceptThemeConfig(
    // 背景 & 层次
    val bgColor: Color,
    val surfaceColor: Color,
    val dividerColor: Color,
    // 文字
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    // 强调色（按钮、高亮数字）
    val accentColor: Color,
    val accentForeground: Color,     // 强调色上的文字颜色
    // 超限状态颜色
    val limitAccentColor: Color,
    val limitAccentForeground: Color,
    // 文案
    val titleText: String,
    val limitTitleText: String,
    val dismissButtonText: String,
    // 胶囊 & 仪式感（保留，部分主题用）
    val capsuleBgColor: Color,
    val capsuleAccentColor: Color,
    val capsuleStopButtonColor: Color,
    val capsuleUseMonoFont: Boolean = false,
    val ceremonyBgColor: Color,
    val ceremonyTextColor: Color,
    val ceremonySubLabelColor: Color
)

/**
 * 根据主题 ID 和明暗模式返回对应的主题配置。
 * [isDark] = true 为夜间，false 为日间。
 */
fun getInterceptThemeConfig(themeId: String, isDark: Boolean = true): InterceptThemeConfig = when (themeId) {

    // ── iOS 极简主题 ──────────────────────────────────────────────────────────
    "simple" -> if (isDark) InterceptThemeConfig(
        // 夜间：纯黑 + 白字
        bgColor                 = Color(0xFF000000),
        surfaceColor            = Color(0xFF1C1C1E),
        dividerColor            = Color(0xFF38383A),
        textPrimary             = Color(0xFFFFFFFF),
        textSecondary           = Color(0xFF8E8E93),
        textTertiary            = Color(0xFF48484A),
        accentColor             = Color(0xFF0A84FF),
        accentForeground        = Color(0xFFFFFFFF),
        limitAccentColor        = Color(0xFFFF453A),
        limitAccentForeground   = Color(0xFFFFFFFF),
        titleText               = "停一下",
        limitTitleText          = "时间到了",
        dismissButtonText       = "好的，离开",
        capsuleBgColor          = Color(0xF0000000),
        capsuleAccentColor      = Color(0xFF0A84FF),
        capsuleStopButtonColor  = Color(0xFF0A84FF),
        ceremonyBgColor         = Color(0xFF1C1C1E),
        ceremonyTextColor       = Color(0xFFFFFFFF),
        ceremonySubLabelColor   = Color(0xFF8E8E93)
    ) else InterceptThemeConfig(
        // 日间：纯白 + 深字
        bgColor                 = Color(0xFFF2F2F7),
        surfaceColor            = Color(0xFFFFFFFF),
        dividerColor            = Color(0xFFD1D1D6),
        textPrimary             = Color(0xFF000000),
        textSecondary           = Color(0xFF6C6C70),
        textTertiary            = Color(0xFFAEAEB2),
        accentColor             = Color(0xFF007AFF),
        accentForeground        = Color(0xFFFFFFFF),
        limitAccentColor        = Color(0xFFFF3B30),
        limitAccentForeground   = Color(0xFFFFFFFF),
        titleText               = "停一下",
        limitTitleText          = "时间到了",
        dismissButtonText       = "好的，离开",
        capsuleBgColor          = Color(0xF0F2F2F7),
        capsuleAccentColor      = Color(0xFF007AFF),
        capsuleStopButtonColor  = Color(0xFF007AFF),
        ceremonyBgColor         = Color(0xFFFFFFFF),
        ceremonyTextColor       = Color(0xFF000000),
        ceremonySubLabelColor   = Color(0xFF6C6C70)
    )

    // ── 禅主题（仅深色）────────────────────────────────────────────────────────
    "zen" -> InterceptThemeConfig(
        bgColor                 = Color(0xFF0A0A0A),
        surfaceColor            = Color(0xFF141414),
        dividerColor            = Color(0xFF222222),
        textPrimary             = Color(0xFFE8E8E8),
        textSecondary           = Color(0xFF888888),
        textTertiary            = Color(0xFF444444),
        accentColor             = Color(0xFFE8E8E8),
        accentForeground        = Color(0xFF000000),
        limitAccentColor        = Color(0xFFAAAAAA),
        limitAccentForeground   = Color(0xFF000000),
        titleText               = "停下来",
        limitTitleText          = "时间到了",
        dismissButtonText       = "离开",
        capsuleBgColor          = Color(0xF0111111),
        capsuleAccentColor      = Color(0xFFDDDDDD),
        capsuleStopButtonColor  = Color(0xFF666666),
        ceremonyBgColor         = Color(0xFF141414),
        ceremonyTextColor       = Color(0xFFE8E8E8),
        ceremonySubLabelColor   = Color(0xFFAAAAAA)
    )

    // ── 默认主题（正念绿，仅深色）──────────────────────────────────────────────
    else -> InterceptThemeConfig(
        bgColor                 = Color(0xFF111318),
        surfaceColor            = Color(0xFF1E2130),
        dividerColor            = Color(0xFF273045),
        textPrimary             = Color(0xFFF0F4F8),
        textSecondary           = Color(0xFF8E99B0),
        textTertiary            = Color(0xFF4A5468),
        accentColor             = Color(0xFF3DDC84),
        accentForeground        = Color(0xFF000000),
        limitAccentColor        = Color(0xFFFF6B4A),
        limitAccentForeground   = Color(0xFF000000),
        titleText               = "停一下",
        limitTitleText          = "时间到了",
        dismissButtonText       = "好的，我去做别的事",
        capsuleBgColor          = Color(0xF01A1E2A),
        capsuleAccentColor      = Color(0xFF3DDC84),
        capsuleStopButtonColor  = Color(0xFF27AE60),
        ceremonyBgColor         = Color(0xFF1A2030),
        ceremonyTextColor       = Color(0xFF6AECA0),
        ceremonySubLabelColor   = Color(0xFF3DDC84).copy(alpha = 0.70f)
    )
}

// ── 时间格式化辅助 ────────────────────────────────────────────────────────────

private fun formatMinutes(minutes: Int): String = when {
    minutes <= 0 -> "0分钟"
    minutes < 60 -> "${minutes}分钟"
    minutes % 60 == 0 -> "${minutes / 60}小时"
    else -> "${minutes / 60}小时${minutes % 60}分"
}

// ── 秒数格式化辅助 ────────────────────────────────────────────────────────────

private fun formatSecondsToText(seconds: Long): String {
    val totalMinutes = seconds / 60
    return when {
        totalMinutes <= 0 -> "${seconds}秒"
        totalMinutes < 60 -> "${totalMinutes}分钟"
        totalMinutes % 60 == 0L -> "${totalMinutes / 60}小时"
        else -> "${totalMinutes / 60}小时${totalMinutes % 60}分"
    }
}

// ────────────────────────────────────────────────────────────────────────────
//  主拦截页
// ────────────────────────────────────────────────────────────────────────────

@Composable
fun InterceptOverlayScreen(
    appName: String,
    packageName: String = "",
    dailyLimitMinutes: Int,
    weeklyLimitMinutes: Int,
    todayUsedSeconds: Long,
    weekUsedSeconds: Long,
    todayRecords: List<UsageRecordEntity> = emptyList(),
    capsuleTargetPosition: CapsuleTargetPosition? = null,
    remainingModifyCount: Int = 0,
    themeId: String = "default",
    isDarkTheme: Boolean = true,
    onReset: ((newDailyMinutes: Int, newWeeklyMinutes: Int) -> Unit)? = null,
    onContinue: (purpose: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val themeConfig = remember(themeId, isDarkTheme) { getInterceptThemeConfig(themeId, isDarkTheme) }
    val isSimpleTheme = themeId == "simple"

    val todayUsedMinutes = todayUsedSeconds / 60
    val dailyLimitSeconds = dailyLimitMinutes * 60L
    val weeklyLimitSeconds = weeklyLimitMinutes * 60L
    val isOverDailyLimit = dailyLimitSeconds > 0 && todayUsedSeconds >= dailyLimitSeconds
    val isOverWeeklyLimit = weeklyLimitSeconds > 0 && weekUsedSeconds >= weeklyLimitSeconds
    val isOverLimit = isOverDailyLimit || isOverWeeklyLimit
    val dailyProgress = if (dailyLimitMinutes > 0)
        (todayUsedMinutes.toFloat() / dailyLimitMinutes).coerceAtMost(1f) else 0f

    val context = LocalContext.current

    // ── 名言状态 ──────────────────────────────────────────────────────────────
    val quoteRepository = remember {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            QuoteRepositoryEntryPoint::class.java
        )
        entryPoint.quoteRepository()
    }
    var quote by remember { mutableStateOf(DISPLAY_FALLBACK_QUOTES.random()) }
    // 收藏状态（跟随当前显示的 quote.first 实时查询）
    val isFavorited by quoteRepository.isFavorite(quote.first).collectAsState(initial = false)

    var intentText by remember { mutableStateOf("") }
    val confirmedPurpose: String? = if (intentText.isNotBlank()) intentText.trim() else null

    var showResetDialog by remember { mutableStateOf(false) }

    // ── 退场动画 ──────────────────────────────────────────────────────────────
    var isExiting by remember { mutableStateOf(false) }
    val exitProgress = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    var isDismissing by remember { mutableStateOf(false) }

    suspend fun playExitAnimation() {
        isExiting = true
        exitProgress.animateTo(1f, animationSpec = tween(520, easing = FastOutSlowInEasing))
    }

    val screenCenterOffset = remember {
        try {
            val wm = context.getSystemService(android.content.Context.WINDOW_SERVICE)
                    as android.view.WindowManager
            val bounds = wm.currentWindowMetrics.bounds
            Offset(bounds.width() / 2f, bounds.height() / 2f)
        } catch (e: Exception) {
            Offset(540f, 1200f)
        }
    }
    val targetPos = capsuleTargetPosition ?: CapsuleTargetPosition(8f, 160f)

    val translateX by remember(screenCenterOffset, targetPos) {
        derivedStateOf {
            if (!isExiting) 0f else (targetPos.x - screenCenterOffset.x) * exitProgress.value
        }
    }
    val translateY by remember(screenCenterOffset, targetPos) {
        derivedStateOf {
            if (!isExiting) 0f else (targetPos.y - screenCenterOffset.y) * exitProgress.value
        }
    }
    val scaleValue by remember(isExiting) {
        derivedStateOf { if (!isExiting) 1f else 1f - exitProgress.value * 0.78f }
    }
    val cornerRadius by remember(isExiting) {
        derivedStateOf { if (!isExiting) 0f else exitProgress.value * 26f }
    }
    val bgAlpha by remember(isExiting) {
        derivedStateOf { if (!isExiting) 1f else 1f - exitProgress.value * 0.7f }
    }

    // ── 入场动画 ──────────────────────────────────────────────────────────────
    var showContent by remember { mutableStateOf(false) }
    val iconScale = remember { Animatable(0f) }
    var cooldownRemaining by remember { mutableIntStateOf(COOLDOWN_SECONDS) }
    val bgEnterAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        bgEnterAlpha.animateTo(1f, animationSpec = tween(300))
        delay(60)
        showContent = true
        iconScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
    }

    LaunchedEffect(Unit) {
        repeat(COOLDOWN_SECONDS) {
            delay(1000)
            cooldownRemaining--
        }
    }

    LaunchedEffect(confirmedPurpose) {
        if (confirmedPurpose != null && cooldownRemaining > COOLDOWN_WITH_PURPOSE) {
            delay(300)
            cooldownRemaining = cooldownRemaining.coerceAtMost(COOLDOWN_WITH_PURPOSE)
        }
    }

    MindfulnessAppTheme(darkTheme = isDarkTheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(bgAlpha * bgEnterAlpha.value)
                .background(themeConfig.bgColor)
        ) {
            // 状态栏遮罩
            val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusBarHeight)
                    .background(themeConfig.bgColor)
                    .align(Alignment.TopStart)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .graphicsLayer {
                        scaleX = scaleValue
                        scaleY = scaleValue
                        translationX = translateX
                        translationY = translateY
                        shape = RoundedCornerShape(cornerRadius.dp)
                        clip = true
                    }
                    .padding(horizontal = if (isSimpleTheme) 24.dp else 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(if (isSimpleTheme) 48.dp else 32.dp))

                // ── 区域1：App 图标 + 名称 ────────────────────────────────────
                AnimatedVisibility(
                    visible = showContent,
                    enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 3 }
                ) {
                    if (isSimpleTheme) {
                        SimpleAppHeader(
                            appName = appName,
                            packageName = packageName,
                            iconScale = iconScale.value,
                            isOverLimit = isOverLimit,
                            themeConfig = themeConfig
                        )
                    } else {
                        CompactAppHeader(
                            appName = appName,
                            packageName = packageName,
                            iconScale = iconScale.value,
                            isOverLimit = isOverLimit,
                            themeConfig = themeConfig
                        )
                    }
                }
                if (!showContent) Spacer(Modifier.height(72.dp))

                Spacer(Modifier.height(if (isSimpleTheme) 32.dp else 20.dp))

                // ── 区域2：使用数据展示 ────────────────────────────────────────
                AnimatedVisibility(
                    visible = showContent && !isExiting,
                    enter = fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 2 },
                    exit = fadeOut(tween(150))
                ) {
                    if (isSimpleTheme) {
                        SimpleUsageStats(
                            todayUsedSeconds = todayUsedSeconds,
                            dailyLimitMinutes = dailyLimitMinutes,
                            dailyProgress = dailyProgress,
                            isOverLimit = isOverLimit,
                            themeConfig = themeConfig
                        )
                    } else {
                        UsageTripleStats(
                            todayUsedSeconds = todayUsedSeconds,
                            dailyLimitMinutes = dailyLimitMinutes,
                            dailyProgress = dailyProgress,
                            isOverLimit = isOverLimit,
                            themeConfig = themeConfig
                        )
                    }
                }

                Spacer(Modifier.height(if (isSimpleTheme) 24.dp else 10.dp))

                // ── 区域3：名言区 ────────────────────────────────────────────
                AnimatedVisibility(
                    visible = showContent && !isExiting,
                    enter = fadeIn(tween(500)),
                    exit = fadeOut(tween(150))
                ) {
                    if (isSimpleTheme) {
                        SimpleQuoteSection(
                            quote = quote.first,
                            author = quote.second,
                            isOverLimit = isOverLimit,
                            themeConfig = themeConfig,
                            isFavorited = isFavorited,
                            onFavorite = {
                                coroutineScope.launch {
                                    if (isFavorited) quoteRepository.removeFavorite(quote.first)
                                    else quoteRepository.addFavorite(quote.first, quote.second)
                                }
                            }
                        )
                    } else {
                        QuoteSection(
                            quote = quote.first,
                            author = quote.second,
                            accentColor = if (isOverLimit) themeConfig.limitAccentColor else themeConfig.accentColor,
                            isFavorited = isFavorited,
                            onFavorite = {
                                coroutineScope.launch {
                                    if (isFavorited) quoteRepository.removeFavorite(quote.first)
                                    else quoteRepository.addFavorite(quote.first, quote.second)
                                }
                            }
                        )
                    }
                }

                // 入场后从后端加载名言
                LaunchedEffect(Unit) {
                    try {
                        quote = quoteRepository.getRandomQuote()
                    } catch (_: Exception) { /* 保留兜底 */ }
                }

                Spacer(Modifier.height(if (isSimpleTheme) 32.dp else 24.dp))

                // ── 区域4：操作区 ─────────────────────────────────────────────
                AnimatedVisibility(
                    visible = showContent && !isExiting,
                    enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 2 },
                    exit = fadeOut(tween(150))
                ) {
                    if (isSimpleTheme) {
                        SimpleActionSection(
                            isOverLimit = isOverLimit,
                            intentText = intentText,
                            confirmedPurpose = confirmedPurpose,
                            cooldownRemaining = cooldownRemaining,
                            isExiting = isExiting,
                            isDismissing = isDismissing,
                            remainingModifyCount = remainingModifyCount,
                            themeConfig = themeConfig,
                            onIntentChange = { intentText = it },
                            onEnterWithPurpose = {
                                coroutineScope.launch {
                                    playExitAnimation()
                                    onContinue(confirmedPurpose)
                                }
                            },
                            onDismiss = {
                                isDismissing = true
                                onDismiss()
                            },
                            onShowResetDialog = { showResetDialog = true }
                        )
                    } else {
                        ActionSection(
                            isOverLimit = isOverLimit,
                            intentText = intentText,
                            confirmedPurpose = confirmedPurpose,
                            cooldownRemaining = cooldownRemaining,
                            isExiting = isExiting,
                            isDismissing = isDismissing,
                            remainingModifyCount = remainingModifyCount,
                            themeConfig = themeConfig,
                            onIntentChange = { intentText = it },
                            onEnterWithPurpose = {
                                coroutineScope.launch {
                                    playExitAnimation()
                                    onContinue(confirmedPurpose)
                                }
                            },
                            onDismiss = {
                                isDismissing = true
                                onDismiss()
                            },
                            onShowResetDialog = { showResetDialog = true }
                        )
                    }
                }

                Spacer(Modifier.height(48.dp))
            }
        }

        if (showResetDialog && onReset != null) {
            ResetLimitDialog(
                todayUsedMinutes = todayUsedMinutes.toInt(),
                currentDailyLimitMinutes = dailyLimitMinutes,
                currentWeeklyLimitMinutes = weeklyLimitMinutes,
                historyUsage = null,
                themeConfig = themeConfig,
                onConfirm = { newDailyMinutes, newWeeklyMinutes ->
                    showResetDialog = false
                    onReset(newDailyMinutes, newWeeklyMinutes)
                },
                onDismiss = { showResetDialog = false }
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  iOS 极简风格子组件
// ════════════════════════════════════════════════════════════════════════════

/** iOS 极简头部：大号 App 图标居中，App 名称下方，副标题提示 */
@Composable
private fun SimpleAppHeader(
    appName: String,
    packageName: String,
    iconScale: Float,
    isOverLimit: Boolean,
    themeConfig: InterceptThemeConfig
) {
    val context = LocalContext.current
    val appIcon = remember(packageName) {
        if (packageName.isNotEmpty()) {
            try { context.packageManager.getApplicationIcon(packageName) }
            catch (e: Exception) { null }
        } else null
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // App 图标（大号，圆角方形）
        Box(
            modifier = Modifier
                .scale(iconScale)
                .size(72.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(themeConfig.surfaceColor),
            contentAlignment = Alignment.Center
        ) {
            if (appIcon != null) {
                val bitmap = remember(appIcon) { appIcon.toBitmap(144, 144).asImageBitmap() }
                androidx.compose.foundation.Image(
                    bitmap = bitmap,
                    contentDescription = appName,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(18.dp))
                )
            } else {
                Text(
                    text = appName.take(1),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = themeConfig.textPrimary
                )
            }
        }

        // App 名称
        Text(
            text = appName,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = themeConfig.textPrimary,
            textAlign = TextAlign.Center
        )

        // 副标题
        Text(
            text = if (isOverLimit) "今天的时间已经用完了" else "打开前，先想一想",
            fontSize = 15.sp,
            color = if (isOverLimit) themeConfig.limitAccentColor else themeConfig.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}

/** iOS 极简用量统计：大号数字 + 分割线 */
@Composable
private fun SimpleUsageStats(
    todayUsedSeconds: Long,
    dailyLimitMinutes: Int,
    dailyProgress: Float,
    isOverLimit: Boolean,
    themeConfig: InterceptThemeConfig
) {
    val accentColor = if (isOverLimit) themeConfig.limitAccentColor else themeConfig.accentColor
    val usageText = formatSecondsToText(todayUsedSeconds)
    val limitText = if (dailyLimitMinutes > 0) "限额 ${formatMinutes(dailyLimitMinutes)}" else null

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 大号时长数字
        Text(
            text = usageText,
            fontSize = 48.sp,
            fontWeight = FontWeight.Light,
            color = accentColor,
            letterSpacing = (-1).sp
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "今日已用",
                fontSize = 13.sp,
                color = themeConfig.textSecondary
            )
            if (limitText != null) {
                Text(
                    text = "·",
                    fontSize = 13.sp,
                    color = themeConfig.textTertiary
                )
                Text(
                    text = limitText,
                    fontSize = 13.sp,
                    color = themeConfig.textTertiary
                )
            }
        }

        // 进度条（仅有限额时显示）
        if (dailyLimitMinutes > 0) {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(themeConfig.dividerColor)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(dailyProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(accentColor)
                )
            }
        }
    }
}

/** iOS 极简名言区：仅文字，无卡片边框 */
@Composable
private fun SimpleQuoteSection(
    quote: String,
    author: String,
    isOverLimit: Boolean,
    themeConfig: InterceptThemeConfig,
    isFavorited: Boolean = false,
    onFavorite: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    AnimatedContent(
        targetState = quote to author,
        transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(200)) },
        label = "quote_anim"
    ) { (q, a) ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 分割线
            HorizontalDivider(
                color = themeConfig.dividerColor,
                thickness = 0.5.dp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = q,
                fontSize = 16.sp,
                fontWeight = FontWeight.Light,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                color = themeConfig.textSecondary,
                lineHeight = 26.sp,
                letterSpacing = 0.3.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (a.isNotBlank()) {
                    Text(
                        text = a,
                        fontSize = 12.sp,
                        color = themeConfig.textTertiary
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                // 收藏心形按钮
                Icon(
                    imageVector = if (isFavorited) Icons.Filled.Favorite
                    else Icons.Filled.FavoriteBorder,
                    contentDescription = if (isFavorited) "取消收藏" else "收藏",
                    tint = if (isFavorited)
                        Color(0xFFE05C6A)
                    else
                        themeConfig.textTertiary.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(18.dp)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onFavorite()
                        }
                )
            }
            Spacer(Modifier.height(4.dp))
            HorizontalDivider(
                color = themeConfig.dividerColor,
                thickness = 0.5.dp
            )
        }
    }
}

/** iOS 极简操作区 */
@Composable
private fun SimpleActionSection(
    isOverLimit: Boolean,
    intentText: String,
    confirmedPurpose: String?,
    cooldownRemaining: Int,
    isExiting: Boolean,
    isDismissing: Boolean,
    remainingModifyCount: Int,
    themeConfig: InterceptThemeConfig,
    onIntentChange: (String) -> Unit,
    onEnterWithPurpose: () -> Unit,
    onDismiss: () -> Unit,
    onShowResetDialog: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val accentColor = if (isOverLimit) themeConfig.limitAccentColor else themeConfig.accentColor
    val accentFg = if (isOverLimit) themeConfig.limitAccentForeground else themeConfig.accentForeground
    val buttonEnabled = !isExiting && !isDismissing && cooldownRemaining == 0

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isOverLimit) {
            // 目的输入框
            SimpleIntentInput(
                intentText = intentText,
                themeConfig = themeConfig,
                onIntentChange = onIntentChange
            )

            // 主按钮：离开
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDismiss()
                },
                enabled = !isExiting && !isDismissing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    disabledContainerColor = themeConfig.dividerColor
                ),
                shape = RoundedCornerShape(14.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Text(
                    text = "先不进去了",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accentFg
                )
            }

            // 次要按钮：有目的进入（冷静期/目的锁定）
            val enterLabel = when {
                cooldownRemaining > 0 -> "等待 ${cooldownRemaining}秒…"
                confirmedPurpose == null -> "写下意图后进入"
                else -> "确认，有目的地进入"
            }
            Button(
                onClick = {
                    if (buttonEnabled && confirmedPurpose != null) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onEnterWithPurpose()
                    }
                },
                enabled = buttonEnabled && confirmedPurpose != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = themeConfig.surfaceColor,
                    disabledContainerColor = themeConfig.surfaceColor
                ),
                shape = RoundedCornerShape(14.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Text(
                    text = enterLabel,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (buttonEnabled && confirmedPurpose != null)
                        themeConfig.textPrimary
                    else
                        themeConfig.textTertiary
                )
            }

        } else {
            // 超限：主按钮离开
            Button(
                onClick = {
                    if (!isExiting && !isDismissing) onDismiss()
                },
                enabled = !isExiting && !isDismissing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(14.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Text(
                    text = "好的，离开",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accentFg
                )
            }

            // 可选：重设目标
            if (remainingModifyCount > 0) {
                TextButton(
                    onClick = { onShowResetDialog() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = themeConfig.textTertiary
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "调整今日目标（还剩 $remainingModifyCount 次）",
                        fontSize = 14.sp,
                        color = themeConfig.textTertiary
                    )
                }
            }
        }
    }
}

/** iOS 极简意图输入框 */
@Composable
private fun SimpleIntentInput(
    intentText: String,
    themeConfig: InterceptThemeConfig,
    onIntentChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "此刻打开它，是为了什么？",
            fontSize = 13.sp,
            color = themeConfig.textSecondary
        )
        OutlinedTextField(
            value = intentText,
            onValueChange = { if (it.length <= 40) onIntentChange(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    "写下你的意图（可选）",
                    fontSize = 15.sp,
                    color = themeConfig.textTertiary
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = themeConfig.accentColor,
                unfocusedBorderColor = themeConfig.dividerColor,
                focusedTextColor     = themeConfig.textPrimary,
                unfocusedTextColor   = themeConfig.textPrimary,
                cursorColor          = themeConfig.accentColor,
                focusedContainerColor   = themeConfig.surfaceColor,
                unfocusedContainerColor = themeConfig.surfaceColor
            )
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  旧版风格子组件（default / zen / gauge 主题复用）
// ════════════════════════════════════════════════════════════════════════════

// 旧风格颜色常量（仅旧主题内部使用）
private val _OldDarkBg            = Color(0xFF111318)
private val _OldDarkSurface       = Color(0xFF1E2130)
private val _OldDarkSurfaceVariant = Color(0xFF252840)
private val _OldTextPrimary       = Color(0xFFF0F4F8)
private val _OldTextSecondary     = Color(0xFF8E99B0)
private val _OldTextMuted         = Color(0xFF4A5468)
private val _OldMindfulSectionBg  = Color(0xFF181E2E)
private val _OldMindfulSectionBorder = Color(0xFF2A3550)
private val _OldMindfulTextMuted  = Color(0xFF4A5468)

@Composable
private fun CompactAppHeader(
    appName: String,
    packageName: String,
    iconScale: Float,
    isOverLimit: Boolean,
    themeConfig: InterceptThemeConfig
) {
    val context = LocalContext.current
    val appIcon = remember(packageName) {
        if (packageName.isNotEmpty()) {
            try { context.packageManager.getApplicationIcon(packageName) }
            catch (e: Exception) { null }
        } else null
    }

    val glowColor = if (isOverLimit) themeConfig.limitAccentColor else themeConfig.accentColor

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .scale(iconScale)
                .size(52.dp)
                .clip(CircleShape)
                .background(_OldDarkSurface)
                .border(1.dp, glowColor.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (appIcon != null) {
                val bitmap = remember(appIcon) { appIcon.toBitmap(128, 128).asImageBitmap() }
                androidx.compose.foundation.Image(
                    bitmap = bitmap,
                    contentDescription = appName,
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                )
            } else {
                Text(
                    text = appName.take(1),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = glowColor
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column {
            Text(
                text = appName,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = _OldTextPrimary
            )
            Text(
                text = if (isOverLimit) "今天的时间已用完" else "打开前，先想想",
                fontSize = 13.sp,
                color = if (isOverLimit) themeConfig.limitAccentColor.copy(alpha = 0.8f) else _OldTextSecondary
            )
        }
    }
}

@Composable
private fun UsageTripleStats(
    todayUsedSeconds: Long,
    dailyLimitMinutes: Int,
    dailyProgress: Float,
    isOverLimit: Boolean,
    themeConfig: InterceptThemeConfig
) {
    val accentColor = if (isOverLimit) themeConfig.limitAccentColor else themeConfig.accentColor
    val bgColor = themeConfig.surfaceColor
    val borderColor = themeConfig.dividerColor

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatItem(
                value = formatSecondsToText(todayUsedSeconds),
                label = "今日记录",
                subLabel = if (dailyLimitMinutes > 0) "限 ${formatMinutes(dailyLimitMinutes)}" else null,
                valueColor = accentColor
            )
        }
    }
}

@Composable
private fun StatItem(
    value: String,
    label: String,
    subLabel: String?,
    valueColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            textAlign = TextAlign.Center
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = _OldTextSecondary,
            textAlign = TextAlign.Center
        )
        if (subLabel != null) {
            Text(
                text = subLabel,
                fontSize = 11.sp,
                color = _OldTextMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun QuoteSection(
    quote: String,
    author: String,
    accentColor: Color,
    isFavorited: Boolean = false,
    onFavorite: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    AnimatedContent(
        targetState = quote to author,
        transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(200)) },
        label = "quote_anim"
    ) { (q, a) ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(accentColor.copy(alpha = 0.04f))
                .border(1.dp, accentColor.copy(alpha = 0.20f), RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "\u201C",
                    fontSize = 22.sp,
                    color = accentColor.copy(alpha = 0.35f),
                    fontWeight = FontWeight.Bold,
                    lineHeight = 1.sp
                )
                Text(
                    text = q,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Light,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = _OldTextPrimary,
                    lineHeight = 26.sp,
                    letterSpacing = 0.3.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = a,
                        fontSize = 12.sp,
                        color = accentColor.copy(alpha = 0.6f)
                    )
                    // 收藏心形按钮
                    Icon(
                        imageVector = if (isFavorited) Icons.Filled.Favorite
                        else Icons.Filled.FavoriteBorder,
                        contentDescription = if (isFavorited) "取消收藏" else "收藏",
                        tint = if (isFavorited)
                            Color(0xFFE05C6A)
                        else
                            accentColor.copy(alpha = 0.4f),
                        modifier = Modifier
                            .size(18.dp)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onFavorite()
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionSection(
    isOverLimit: Boolean,
    intentText: String,
    confirmedPurpose: String?,
    cooldownRemaining: Int,
    isExiting: Boolean,
    isDismissing: Boolean,
    remainingModifyCount: Int,
    themeConfig: InterceptThemeConfig,
    onIntentChange: (String) -> Unit,
    onEnterWithPurpose: () -> Unit,
    onDismiss: () -> Unit,
    onShowResetDialog: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val accentColor = if (isOverLimit) themeConfig.limitAccentColor else themeConfig.accentColor
    val accentFg = if (isOverLimit) themeConfig.limitAccentForeground else themeConfig.accentForeground
    val buttonEnabled = !isExiting && !isDismissing && cooldownRemaining == 0

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isOverLimit) {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDismiss()
                },
                enabled = !isExiting && !isDismissing,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    disabledContainerColor = _OldDarkSurfaceVariant
                ),
                shape = RoundedCornerShape(26.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Text(
                    text = "先不进去了",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accentFg.copy(alpha = 0.85f)
                )
            }

            Spacer(Modifier.height(20.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "写下目的，有意识地进入",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = accentColor.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(8.dp))
                PurposeInputExpanded(
                    intentText = intentText,
                    confirmedPurpose = confirmedPurpose,
                    cooldownRemaining = cooldownRemaining,
                    buttonEnabled = buttonEnabled && confirmedPurpose != null,
                    accentColor = accentColor,
                    accentForeground = accentFg,
                    onIntentChange = onIntentChange,
                    onEnter = onEnterWithPurpose
                )
            }

        } else {
            Button(
                onClick = {
                    if (!isExiting && !isDismissing) onDismiss()
                },
                enabled = !isExiting && !isDismissing,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = themeConfig.limitAccentColor),
                shape = RoundedCornerShape(26.dp)
            ) {
                Text(
                    text = "好的，我去做别的事",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = themeConfig.limitAccentForeground.copy(alpha = 0.85f)
                )
            }

            if (remainingModifyCount > 0) {
                OutlinedButton(
                    onClick = { onShowResetDialog() },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = _OldTextSecondary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, themeConfig.dividerColor),
                    shape = RoundedCornerShape(23.dp)
                ) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = _OldTextSecondary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "重新设定今日目标（剩余 $remainingModifyCount 次）",
                        fontSize = 13.sp,
                        color = _OldTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun PurposeInputExpanded(
    intentText: String,
    confirmedPurpose: String?,
    cooldownRemaining: Int,
    buttonEnabled: Boolean,
    accentColor: Color,
    accentForeground: Color,
    onIntentChange: (String) -> Unit,
    onEnter: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val buttonScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(_OldMindfulSectionBg)
            .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = intentText,
            onValueChange = onIntentChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("我想要…", fontSize = 13.sp, color = accentColor.copy(alpha = 0.45f)) },
            placeholder = { Text("写下此刻打开它的原因", fontSize = 14.sp, color = _OldTextMuted) },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor      = accentColor,
                unfocusedBorderColor    = _OldMindfulSectionBorder,
                focusedLabelColor       = accentColor,
                unfocusedLabelColor     = _OldMindfulTextMuted,
                focusedTextColor        = _OldTextPrimary,
                unfocusedTextColor      = _OldTextPrimary,
                cursorColor             = accentColor,
                focusedContainerColor   = _OldDarkSurface,
                unfocusedContainerColor = _OldDarkSurface
            )
        )

        val enterLabel = when {
            cooldownRemaining > 0 -> "冷静 ${cooldownRemaining}s …"
            confirmedPurpose == null -> "请先填写目的"
            else -> "确认，带着目的进入"
        }

        Button(
            onClick = {
                if (buttonEnabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.launch {
                        buttonScale.animateTo(0.94f, tween(70))
                        buttonScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy))
                    }
                    onEnter()
                }
            },
            enabled = buttonEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .graphicsLayer { scaleX = buttonScale.value; scaleY = buttonScale.value },
            colors = ButtonDefaults.buttonColors(
                containerColor = accentColor,
                disabledContainerColor = _OldDarkSurfaceVariant
            ),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text(
                text = enterLabel,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (buttonEnabled) accentForeground.copy(alpha = 0.85f) else _OldTextMuted
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  🎯 重设时间目标 Dialog
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ResetLimitDialog(
    todayUsedMinutes: Int,
    currentDailyLimitMinutes: Int,
    currentWeeklyLimitMinutes: Int,
    historyUsage: GetAppHistoryUsageUseCase.HistoryUsageResult?,
    themeConfig: InterceptThemeConfig,
    onConfirm: (newDailyMinutes: Int, newWeeklyMinutes: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val initDaily = if (currentDailyLimitMinutes > 0) currentDailyLimitMinutes
                    else (todayUsedMinutes + 15).coerceAtLeast(30)
    var newDailyMinutes by remember { mutableIntStateOf(initDaily) }

    fun step(current: Int, delta: Int): Int {
        val s = if (current < 60) 5 else 15
        return (current + delta * s).coerceIn(5, 480)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = themeConfig.surfaceColor,
        shape = RoundedCornerShape(24.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "调整今日限制",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = themeConfig.textPrimary
                )
                Text(
                    "今日已用 ${formatMinutes(todayUsedMinutes)}",
                    fontSize = 12.sp,
                    color = themeConfig.textSecondary
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(themeConfig.bgColor)
                            .clickable { newDailyMinutes = step(newDailyMinutes, -1) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "−",
                            fontSize = 22.sp,
                            color = themeConfig.textPrimary,
                            fontWeight = FontWeight.Light
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            formatMinutes(newDailyMinutes),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = themeConfig.accentColor
                        )
                        Text("今日新目标", fontSize = 12.sp, color = themeConfig.textSecondary)
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(themeConfig.bgColor)
                            .clickable { newDailyMinutes = step(newDailyMinutes, +1) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "+",
                            fontSize = 22.sp,
                            color = themeConfig.textPrimary,
                            fontWeight = FontWeight.Light
                        )
                    }
                }
                if (newDailyMinutes < todayUsedMinutes) {
                    Text(
                        "⚠️ 低于今日已用时长，设定后将立即超限",
                        fontSize = 12.sp,
                        color = themeConfig.limitAccentColor,
                        lineHeight = 16.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(newDailyMinutes, currentWeeklyLimitMinutes) },
                colors = ButtonDefaults.buttonColors(containerColor = themeConfig.accentColor),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    "确认调整",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = themeConfig.accentForeground
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", fontSize = 14.sp, color = themeConfig.textSecondary)
            }
        }
    )
}

// ════════════════════════════════════════════════════════════════════════════
//  主题背景动效层（保留函数签名，simple 主题无需背景效果）
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun ThemeBackground(themeId: String, modifier: Modifier = Modifier) {
    // 背景光效已移除，保留函数签名供兼容
}

// ════════════════════════════════════════════════════════════════════════════
//  Hilt EntryPoint：供 Service/非 ViewModel Composable 访问 QuoteRepository
// ════════════════════════════════════════════════════════════════════════════

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface QuoteRepositoryEntryPoint {
    fun quoteRepository(): QuoteRepository
}