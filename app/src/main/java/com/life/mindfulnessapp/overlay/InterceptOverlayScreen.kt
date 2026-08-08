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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.collectAsState
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.data.repository.FALLBACK_QUOTES
import com.life.mindfulnessapp.data.repository.QuoteRepository
import com.life.mindfulnessapp.domain.model.PendingInterrupt
import com.life.mindfulnessapp.domain.model.RecentPurpose
import com.life.mindfulnessapp.domain.usecase.GetAppHistoryUsageUseCase
import com.life.mindfulnessapp.ui.theme.CapabilityForm
import com.life.mindfulnessapp.ui.theme.CapabilityKind
import com.life.mindfulnessapp.ui.theme.CapabilityMark
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.theme.LogoGreenBright
import com.life.mindfulnessapp.ui.theme.MindfulnessAppTheme
import com.life.mindfulnessapp.ui.theme.MonitorCapability
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
 * MVP 仅保留极简拦截风格。
 * [themeId] 保留以兼容旧调用，已忽略；[isDark] = true 为夜间，false 为日间。
 */
fun getInterceptThemeConfig(themeId: String = "simple", isDark: Boolean = true): InterceptThemeConfig =
    if (isDark) InterceptThemeConfig(
        // 夜间：纯黑 + 白字
        bgColor                 = Color(0xFF000000),
        surfaceColor            = Color(0xFF1C1C1E),
        dividerColor            = Color(0xFF38383A),
        textPrimary             = Color(0xFFFFFFFF),
        textSecondary           = Color(0xFF8E8E93),
        textTertiary            = Color(0xFF48484A),
        // 与 App 品牌绿统一（勿用系统蓝，避免拦截/胶囊两套强调色）
        accentColor             = LogoGreenBright,
        accentForeground        = Color(0xFFFFFFFF),
        limitAccentColor        = Color(0xFFFF453A),
        limitAccentForeground   = Color(0xFFFFFFFF),
        titleText               = "停一下",
        limitTitleText          = "时间到了",
        dismissButtonText       = "好的，离开",
        capsuleBgColor          = Color(0xF0000000),
        capsuleAccentColor      = LogoGreenBright,
        capsuleStopButtonColor  = LogoGreenBright,
        ceremonyBgColor         = Color(0xFF1C1C1E),
        ceremonyTextColor       = Color(0xFFFFFFFF),
        ceremonySubLabelColor   = Color(0xFF8E8E93)
    ) else InterceptThemeConfig(
        // 日间：浅灰底 + 深字
        bgColor                 = Color(0xFFF2F2F7),
        surfaceColor            = Color(0xFFFFFFFF),
        dividerColor            = Color(0xFFD1D1D6),
        textPrimary             = Color(0xFF000000),
        textSecondary           = Color(0xFF6C6C70),
        textTertiary            = Color(0xFFAEAEB2),
        accentColor             = LogoGreen,
        accentForeground        = Color(0xFFFFFFFF),
        limitAccentColor        = Color(0xFFFF3B30),
        limitAccentForeground   = Color(0xFFFFFFFF),
        titleText               = "停一下",
        limitTitleText          = "时间到了",
        dismissButtonText       = "好的，离开",
        capsuleBgColor          = Color(0xF0F2F2F7),
        capsuleAccentColor      = LogoGreen,
        capsuleStopButtonColor  = LogoGreen,
        ceremonyBgColor         = Color(0xFFFFFFFF),
        ceremonyTextColor       = Color(0xFF000000),
        ceremonySubLabelColor   = Color(0xFF6C6C70)
    )

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
    /** 是否启用单次时长契约 */
    sessionLimitEnabled: Boolean = true,
    /** 默认单次时长（分钟） */
    defaultSessionLimitMinutes: Int = 15,
    /** 是否启用意图关键词检验 */
    intentQualityCheckEnabled: Boolean = false,
    /** 用户自定义限制关键词 */
    intentBlockKeywords: List<String> = emptyList(),
    /** 该 App 去重后的最近意图（最多 3 条），点选填入，不自动进入 */
    recentPurposes: List<RecentPurpose> = emptyList(),
    /** 未标准闭环快照：上方「最近操作」条，展示相对时刻并可一键继续 */
    pendingInterrupt: PendingInterrupt? = null,
    /** 今日冲动次数（含本次） */
    impulseCount: Int = 1,
    /** 今日放行进入次数 */
    enterCount: Int = 0,
    /** 今日克制次数 */
    dismissCount: Int = 0,
    onReset: ((newDailyMinutes: Int, newWeeklyMinutes: Int) -> Unit)? = null,
    onContinue: (com.life.mindfulnessapp.domain.model.InterceptEnterDecision) -> Unit,
    /** 用户选择「继续上次」 */
    onResumePrevious: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    /** 离开后打开心锚（可选去处；null 则不展示该入口） */
    onOpenOwnApp: (() -> Unit)? = null
) {
    val themeConfig = remember(isDarkTheme) { getInterceptThemeConfig(isDark = isDarkTheme) }
    // MVP：固定极简风格（日/夜由 isDarkTheme 驱动）
    val isSimpleTheme = true

    val todayUsedMinutes = todayUsedSeconds / 60
    val dailyLimitSeconds = dailyLimitMinutes * 60L
    val weeklyLimitSeconds = weeklyLimitMinutes * 60L
    val isOverDailyLimit = dailyLimitSeconds > 0 && todayUsedSeconds >= dailyLimitSeconds
    val isOverWeeklyLimit = weeklyLimitSeconds > 0 && weekUsedSeconds >= weeklyLimitSeconds
    val isOverLimit = isOverDailyLimit || isOverWeeklyLimit
    /** 生效限额 > 0 即时长锁开（调用方已传入 effective*） */
    val timeLimitActive = dailyLimitMinutes > 0 || weeklyLimitMinutes > 0
    val dailyProgress = if (dailyLimitMinutes > 0)
        (todayUsedMinutes.toFloat() / dailyLimitMinutes).coerceAtMost(1f) else 0f
    val weeklyProgress = if (weeklyLimitMinutes > 0) {
        (weekUsedSeconds / 60f / weeklyLimitMinutes).coerceAtMost(1f)
    } else 0f
    val includesPreMonitorUsage = todayRecords.any { it.isSeed }

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

    var showResetDialog by remember { mutableStateOf(false) }

    // ── 退场动画 ──────────────────────────────────────────────────────────────
    // isExiting：确认进入 → 缩向胶囊
    // isLeaveDismissing：点离开 → 内容下沉 + 背景淡出（放下，不钻进）
    var isExiting by remember { mutableStateOf(false) }
    var isLeaveDismissing by remember { mutableStateOf(false) }
    val exitProgress = remember { Animatable(0f) }
    val leaveProgress = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    var isDismissing by remember { mutableStateOf(false) }

    suspend fun playExitAnimation() {
        isExiting = true
        exitProgress.animateTo(1f, animationSpec = tween(520, easing = FastOutSlowInEasing))
    }

    suspend fun playLeaveDismissAnimation() {
        isLeaveDismissing = true
        leaveProgress.animateTo(1f, animationSpec = tween(260, easing = FastOutSlowInEasing))
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
    val translateY by remember(screenCenterOffset, targetPos, isLeaveDismissing) {
        derivedStateOf {
            when {
                isLeaveDismissing -> leaveProgress.value * 36f
                isExiting -> (targetPos.y - screenCenterOffset.y) * exitProgress.value
                else -> 0f
            }
        }
    }
    val scaleValue by remember(isExiting) {
        derivedStateOf { if (!isExiting) 1f else 1f - exitProgress.value * 0.78f }
    }
    val cornerRadius by remember(isExiting) {
        derivedStateOf { if (!isExiting) 0f else exitProgress.value * 26f }
    }
    val bgAlpha by remember(isExiting, isLeaveDismissing) {
        derivedStateOf {
            when {
                isLeaveDismissing -> 1f - leaveProgress.value
                isExiting -> 1f - exitProgress.value * 0.7f
                else -> 1f
            }
        }
    }
    val contentAlpha by remember(isLeaveDismissing) {
        derivedStateOf {
            if (!isLeaveDismissing) 1f else 1f - leaveProgress.value * 0.85f
        }
    }

    // ── 入场动画 ──────────────────────────────────────────────────────────────
    var showContent by remember { mutableStateOf(false) }
    val iconScale = remember { Animatable(0f) }
    val bgEnterAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        bgEnterAlpha.animateTo(1f, animationSpec = tween(300))
        delay(60)
        showContent = true
        iconScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
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
                    .graphicsLayer {
                        scaleX = scaleValue
                        scaleY = scaleValue
                        translationX = translateX
                        translationY = translateY
                        alpha = contentAlpha
                        shape = RoundedCornerShape(cornerRadius.dp)
                        clip = true
                    }
                    .padding(horizontal = if (isSimpleTheme) 24.dp else 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(20.dp))

                LaunchedEffect(Unit) {
                    try {
                        quote = quoteRepository.getRandomQuote()
                    } catch (_: Exception) { /* 保留兜底 */ }
                }

                // ── App 身份：轻量横排 ───────────────────────────────────────
                AnimatedVisibility(
                    visible = showContent,
                    enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 3 }
                ) {
                    SimpleAppHeader(
                        appName = appName,
                        packageName = packageName,
                        iconScale = iconScale.value,
                        themeConfig = themeConfig
                    )
                }
                if (!showContent) Spacer(Modifier.height(48.dp))

                Spacer(Modifier.height(16.dp))

                // ── 一体决策流（内部上滚 + 动作吸底）────────────────────────
                AnimatedVisibility(
                    visible = showContent && !isExiting && !isLeaveDismissing,
                    enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 2 },
                    exit = fadeOut(tween(150)),
                    modifier = Modifier.weight(1f)
                ) {
                    InterceptDecisionContent(
                        themeConfig = themeConfig,
                        dailyLimitMinutes = dailyLimitMinutes,
                        weeklyLimitMinutes = weeklyLimitMinutes,
                        todayUsedSeconds = todayUsedSeconds,
                        weekUsedSeconds = weekUsedSeconds,
                        includesPreMonitorUsage = includesPreMonitorUsage,
                        sessionLimitEnabled = sessionLimitEnabled,
                        defaultSessionLimitMinutes = defaultSessionLimitMinutes,
                        intentQualityCheckEnabled = intentQualityCheckEnabled,
                        intentBlockKeywords = intentBlockKeywords,
                        pendingInterrupt = pendingInterrupt,
                        isExiting = isExiting,
                        isDismissing = isDismissing,
                        impulseCount = impulseCount,
                        enterCount = enterCount,
                        dismissCount = dismissCount,
                        quote = quote.first,
                        quoteAuthor = quote.second,
                        recentPurposes = recentPurposes,
                        modifier = Modifier.fillMaxSize(),
                        onEnter = { decision ->
                            coroutineScope.launch {
                                playExitAnimation()
                                onContinue(decision)
                            }
                        },
                        onResumePrevious = onResumePrevious?.let { resume ->
                            {
                                isDismissing = true
                                resume()
                            }
                        },
                        onDismiss = {
                            if (!isDismissing) {
                                isDismissing = true
                                coroutineScope.launch {
                                    playLeaveDismissAnimation()
                                    onDismiss()
                                }
                            }
                        },
                        onOpenOwnApp = onOpenOwnApp?.let { open ->
                            {
                                if (!isDismissing) {
                                    isDismissing = true
                                    coroutineScope.launch {
                                        playLeaveDismissAnimation()
                                        open()
                                    }
                                }
                            }
                        }
                    )
                }
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

/** 拦截页 App 身份：轻量横排，不与用量大数字抢第一眼 */
@Composable
private fun SimpleAppHeader(
    appName: String,
    packageName: String,
    iconScale: Float,
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
        modifier = Modifier
            .fillMaxWidth()
            .scale(iconScale),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(themeConfig.surfaceColor),
                contentAlignment = Alignment.Center
            ) {
                if (appIcon != null) {
                    val bitmap = remember(appIcon) { appIcon.toBitmap(72, 72).asImageBitmap() }
                    androidx.compose.foundation.Image(
                        bitmap = bitmap,
                        contentDescription = appName,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(9.dp))
                    )
                } else {
                    Text(
                        text = appName.take(1),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = themeConfig.textPrimary
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = appName,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = themeConfig.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "打开前，先想一想",
            fontSize = 12.sp,
            color = themeConfig.textSecondary.copy(alpha = 0.9f)
        )
    }
}

/**
 * 仅意图门：用量降成一行淡提示，不抢「这一次为什么」的焦点。
 */
@Composable
private fun IntentOnlyUsageHint(
    todayUsedSeconds: Long,
    includesPreMonitorUsage: Boolean,
    themeConfig: InterceptThemeConfig
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = "今日已用 ${formatSecondsToText(todayUsedSeconds)}",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = themeConfig.textTertiary.copy(alpha = 0.9f),
            textAlign = TextAlign.Center
        )
        if (includesPreMonitorUsage) {
            Text(
                text = "含加入前今日使用",
                fontSize = 11.sp,
                color = themeConfig.textTertiary.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * 意图 + 时长锁：独立「时长锁」区块（偏紧凑，不抢意图门）。
 * 文案顺序：标签（今日还剩）→ 剩余 → 已用/限 → 进度条。
 */
@Composable
private fun SimpleUsageStats(
    todayUsedSeconds: Long,
    weekUsedSeconds: Long,
    dailyLimitMinutes: Int,
    weeklyLimitMinutes: Int,
    dailyProgress: Float,
    weeklyProgress: Float,
    isOverLimit: Boolean,
    includesPreMonitorUsage: Boolean = false,
    themeConfig: InterceptThemeConfig
) {
    val accentColor = if (isOverLimit) themeConfig.limitAccentColor else themeConfig.accentColor
    val useDaily = dailyLimitMinutes > 0
    val limitSeconds = if (useDaily) dailyLimitMinutes * 60L else weeklyLimitMinutes * 60L
    val usedSeconds = if (useDaily) todayUsedSeconds else weekUsedSeconds
    val remainingSeconds = (limitSeconds - usedSeconds).coerceAtLeast(0L)
    val progress = if (useDaily) dailyProgress else weeklyProgress

    val heroText = when {
        isOverLimit -> "已用完"
        else -> formatSecondsToText(remainingSeconds)
    }
    val heroLabel = when {
        isOverLimit && useDaily -> "今日额度"
        isOverLimit -> "本周期额"
        useDaily -> "今日还剩"
        else -> "本周还剩"
    }
    val detailText = buildString {
        append("已用 ${formatSecondsToText(usedSeconds)}")
        append(" · 限 ${formatMinutes(if (useDaily) dailyLimitMinutes else weeklyLimitMinutes)}")
    }

    InterceptSectionCard(
        themeConfig = themeConfig,
        borderColor = accentColor.copy(alpha = if (isOverLimit) 0.45f else 0.22f),
        compact = true
    ) {
        InterceptCapabilitySectionHeader(
            kind = CapabilityKind.TimeLock,
            tint = accentColor.copy(alpha = 0.85f)
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = heroLabel,
                fontSize = 12.sp,
                color = themeConfig.textSecondary
            )
            Text(
                text = heroText,
                fontSize = 34.sp,
                fontWeight = FontWeight.Light,
                color = accentColor,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = detailText,
                fontSize = 12.sp,
                color = themeConfig.textTertiary
            )
            if (includesPreMonitorUsage) {
                Text(
                    text = "含加入前今日使用",
                    fontSize = 11.sp,
                    color = themeConfig.textTertiary
                )
            }

            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(themeConfig.dividerColor)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(accentColor)
                )
            }
        }
    }
}

/** 拦截页能力分区外框：浅底 + 描边，形成包裹感 */
@Composable
private fun InterceptSectionCard(
    themeConfig: InterceptThemeConfig,
    borderColor: Color = themeConfig.dividerColor,
    compact: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (compact) 12.dp else 14.dp))
            .background(themeConfig.surfaceColor.copy(alpha = 0.92f))
            .border(1.dp, borderColor, RoundedCornerShape(if (compact) 12.dp else 14.dp))
            .padding(
                horizontal = if (compact) 12.dp else 14.dp,
                vertical = if (compact) 10.dp else 14.dp
            ),
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
        content = content
    )
}

/** 拦截页能力分区标头：能力 icon +「能力名·开启中」 */
@Composable
private fun InterceptCapabilitySectionHeader(
    kind: CapabilityKind,
    tint: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CapabilityMark(
            kind = kind,
            form = CapabilityForm.Standard,
            tint = tint,
            size = 16.dp
        )
        Text(
            text = "${MonitorCapability.label(kind)}·开启中",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = tint,
            letterSpacing = 0.3.sp
        )
    }
}

/** 拦截页底部名言页脚：弱氛围，不抢决策 */
@Composable
private fun SimpleQuoteFooter(
    quote: String,
    author: String,
    themeConfig: InterceptThemeConfig
) {
    AnimatedContent(
        targetState = quote to author,
        transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(200)) },
        label = "quote_footer_anim"
    ) { (q, a) ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            HorizontalDivider(
                color = themeConfig.dividerColor.copy(alpha = 0.55f),
                thickness = 0.5.dp,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Text(
                text = q,
                fontSize = 13.sp,
                fontWeight = FontWeight.Light,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                color = themeConfig.textTertiary.copy(alpha = 0.85f),
                lineHeight = 20.sp,
                letterSpacing = 0.2.sp,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            if (a.isNotBlank()) {
                Text(
                    text = a,
                    fontSize = 11.sp,
                    color = themeConfig.textTertiary.copy(alpha = 0.55f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** iOS 极简操作区：独立「意图门」区块 */
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
    recentPurposes: List<RecentPurpose> = emptyList(),
    pendingInterrupt: PendingInterrupt? = null,
    onIntentChange: (String) -> Unit,
    onEnterWithPurpose: () -> Unit,
    onResumePrevious: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onOpenOwnApp: (() -> Unit)? = null,
    onShowResetDialog: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val accentColor = if (isOverLimit) themeConfig.limitAccentColor else themeConfig.accentColor
    val accentFg = if (isOverLimit) themeConfig.limitAccentForeground else themeConfig.accentForeground
    val buttonEnabled = !isExiting && !isDismissing && cooldownRemaining == 0
    val hasPurpose = confirmedPurpose != null
    val leaveEnabled = !isExiting && !isDismissing
    val canResume = pendingInterrupt != null && onResumePrevious != null && !isExiting && !isDismissing

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isOverLimit) {
            InterceptSectionCard(
                themeConfig = themeConfig,
                borderColor = accentColor.copy(alpha = 0.28f)
            ) {
                InterceptCapabilitySectionHeader(
                    kind = CapabilityKind.IntentGate,
                    tint = accentColor.copy(alpha = 0.85f)
                )

                SimpleIntentInput(
                    intentText = intentText,
                    themeConfig = themeConfig,
                    recentPurposes = recentPurposes,
                    pendingInterrupt = pendingInterrupt,
                    resumeEnabled = canResume,
                    onResume = onResumePrevious?.let { resume ->
                        {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            resume()
                        }
                    },
                    onIntentChange = onIntentChange
                )

                val enterLabel = when {
                    cooldownRemaining > 0 -> "等待 ${cooldownRemaining}秒…"
                    !hasPurpose -> "写下意图后进入"
                    pendingInterrupt != null -> "确认，开始新的一次"
                    else -> "确认进入"
                }
                val enterReady = buttonEnabled && hasPurpose
                Button(
                    onClick = {
                        if (enterReady) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onEnterWithPurpose()
                        }
                    },
                    enabled = enterReady,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (enterReady) accentColor
                        else themeConfig.bgColor.copy(alpha = 0.35f),
                        disabledContainerColor = themeConfig.bgColor.copy(alpha = 0.35f)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Text(
                        text = enterLabel,
                        fontSize = 17.sp,
                        fontWeight = if (enterReady) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (enterReady) accentFg else themeConfig.textTertiary
                    )
                }
            }

            QuietLeaveDestinations(
                themeConfig = themeConfig,
                enabled = leaveEnabled,
                onDismiss = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDismiss()
                },
                onOpenOwnApp = onOpenOwnApp?.let { open ->
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        open()
                    }
                }
            )

        } else {
            QuietLeaveDestinations(
                themeConfig = themeConfig,
                enabled = leaveEnabled,
                leaveLabel = "好的，离开",
                emphasized = true,
                accentColor = accentColor,
                accentFg = accentFg,
                onDismiss = {
                    if (leaveEnabled) onDismiss()
                },
                onOpenOwnApp = onOpenOwnApp?.let { open ->
                    {
                        if (leaveEnabled) open()
                    }
                }
            )

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

/**
 * 离开路径：弱化呈现，不抢意图门主决策。
 * 普通拦截用文字链；超限时 [emphasized] 略抬一点可点性。
 */
@Composable
private fun QuietLeaveDestinations(
    themeConfig: InterceptThemeConfig,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onOpenOwnApp: (() -> Unit)?,
    leaveLabel: String = "回到桌面",
    emphasized: Boolean = false,
    accentColor: Color = themeConfig.accentColor,
    accentFg: Color = themeConfig.accentForeground
) {
    if (emphasized) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = onDismiss,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    disabledContainerColor = themeConfig.dividerColor
                ),
                shape = RoundedCornerShape(14.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Text(
                    text = leaveLabel,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accentFg
                )
            }
            if (onOpenOwnApp != null) {
                TextButton(
                    onClick = onOpenOwnApp,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "打开心锚",
                        fontSize = 13.sp,
                        color = themeConfig.textTertiary
                    )
                }
            }
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = "不想进去了",
            fontSize = 11.sp,
            color = themeConfig.textTertiary.copy(alpha = 0.75f)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onDismiss,
                enabled = enabled,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = leaveLabel,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = themeConfig.textSecondary.copy(alpha = 0.85f)
                )
            }
            if (onOpenOwnApp != null) {
                Text(
                    text = "·",
                    fontSize = 13.sp,
                    color = themeConfig.textTertiary.copy(alpha = 0.5f)
                )
                TextButton(
                    onClick = onOpenOwnApp,
                    enabled = enabled,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "打开心锚",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = themeConfig.textSecondary.copy(alpha = 0.85f)
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
    recentPurposes: List<RecentPurpose> = emptyList(),
    pendingInterrupt: PendingInterrupt? = null,
    resumeEnabled: Boolean = false,
    onResume: (() -> Unit)? = null,
    onIntentChange: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    "用一句话写下这次的目的",
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
        RecentIntentChips(
            recentPurposes = recentPurposes,
            selectedText = intentText,
            themeConfig = themeConfig,
            pendingInterrupt = pendingInterrupt,
            resumeEnabled = resumeEnabled,
            onResume = onResume,
            onSelect = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onIntentChange(it.take(40))
            }
        )
    }
}

/**
 * 最近意图区。
 * - 已闭环：单行扁 tag（只点选填入）
 * - 未闭环：「最近意图」下先放操作 clip（意图 + 几分钟前 + 轻量继续），再放扁 tag
 */
@Composable
private fun RecentIntentChips(
    recentPurposes: List<RecentPurpose>,
    selectedText: String,
    themeConfig: InterceptThemeConfig,
    pendingInterrupt: PendingInterrupt? = null,
    resumeEnabled: Boolean = false,
    onResume: (() -> Unit)? = null,
    onSelect: (String) -> Unit
) {
    val showResume = pendingInterrupt != null && onResume != null
    val pendingPurpose = pendingInterrupt?.purpose?.trim().orEmpty()
    val filteredRecent = if (showResume && pendingPurpose.isNotEmpty()) {
        recentPurposes.filterNot { it.purpose == pendingPurpose }
    } else {
        recentPurposes
    }
    if (!showResume && filteredRecent.isEmpty()) return

    val current = selectedText.trim()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "最近意图",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = themeConfig.textTertiary.copy(alpha = 0.85f),
            letterSpacing = 0.3.sp
        )

        if (showResume) {
            val interrupt = pendingInterrupt
            val resume = onResume
            if (interrupt != null && resume != null) {
                ResumeOperationChip(
                    interrupt = interrupt,
                    themeConfig = themeConfig,
                    enabled = resumeEnabled,
                    onResume = resume
                )
            }
        }

        if (filteredRecent.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                filteredRecent.forEach { item ->
                    RecentIntentTag(
                        text = item.purpose,
                        selected = current == item.purpose,
                        themeConfig = themeConfig,
                        onClick = { onSelect(item.purpose) }
                    )
                }
            }
        }
    }
}

/** 快捷 tag：单行、偏扁，只承载意图文案 */
@Composable
private fun RecentIntentTag(
    text: String,
    selected: Boolean,
    themeConfig: InterceptThemeConfig,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) themeConfig.accentColor.copy(alpha = 0.14f)
                else themeConfig.surfaceColor.copy(alpha = 0.9f)
            )
            .border(
                width = 1.dp,
                color = if (selected) themeConfig.accentColor.copy(alpha = 0.45f)
                else themeConfig.dividerColor,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) themeConfig.accentColor
            else themeConfig.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 未闭环「最近操作」clip：宽度随内容，不拉满。
 * 意图 + 相对时刻 + 轻量「继续」文字按钮。
 */
@Composable
private fun ResumeOperationChip(
    interrupt: PendingInterrupt,
    themeConfig: InterceptThemeConfig,
    enabled: Boolean,
    onResume: () -> Unit
) {
    val accent = themeConfig.accentColor
    val title = interrupt.purpose?.takeIf { it.isNotBlank() } ?: "上次未写意图"

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.08f))
            .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .alpha(if (enabled) 1f else 0.45f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = themeConfig.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 140.dp)
        )
        Text(
            text = interrupt.timeAgoLabel(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            color = themeConfig.textTertiary,
            maxLines = 1
        )
        Button(
            onClick = onResume,
            enabled = enabled,
            modifier = Modifier.height(26.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = accent.copy(alpha = 0.55f),
                contentColor = themeConfig.accentForeground,
                disabledContainerColor = themeConfig.dividerColor,
                disabledContentColor = themeConfig.textTertiary
            ),
            shape = RoundedCornerShape(7.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
        ) {
            Text(
                text = "继续",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
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
    includesPreMonitorUsage: Boolean = false,
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
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatItem(
                    value = formatSecondsToText(todayUsedSeconds),
                    label = "今日正念时长",
                    subLabel = if (dailyLimitMinutes > 0) "限 ${formatMinutes(dailyLimitMinutes)}" else null,
                    valueColor = accentColor
                )
            }
            if (includesPreMonitorUsage) {
                Text(
                    text = "含加入前今日使用",
                    fontSize = 11.sp,
                    color = themeConfig.textTertiary
                )
            }
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
    accentColor: Color
) {
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
                Text(
                    text = a,
                    fontSize = 12.sp,
                    color = accentColor.copy(alpha = 0.6f)
                )
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
    recentPurposes: List<RecentPurpose> = emptyList(),
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
                    themeConfig = themeConfig,
                    recentPurposes = recentPurposes,
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
    themeConfig: InterceptThemeConfig,
    recentPurposes: List<RecentPurpose> = emptyList(),
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
            placeholder = { Text("用一句话写下这次的目的", fontSize = 14.sp, color = _OldTextMuted) },
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

        RecentIntentChips(
            recentPurposes = recentPurposes,
            selectedText = intentText,
            themeConfig = themeConfig,
            onSelect = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onIntentChange(it.take(40))
            }
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
                    "今日正念时长 ${formatMinutes(todayUsedMinutes)}",
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
                        "⚠️ 低于今日正念时长，设定后将立即超限",
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