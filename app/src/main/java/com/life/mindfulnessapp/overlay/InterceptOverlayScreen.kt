package com.life.mindfulnessapp.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.collectAsState
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.data.repository.DisplayQuote
import com.life.mindfulnessapp.data.repository.FALLBACK_QUOTES
import com.life.mindfulnessapp.data.repository.AppLimitRepository
import com.life.mindfulnessapp.data.repository.QuoteRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository
import com.life.mindfulnessapp.domain.model.IntentGateKind
import com.life.mindfulnessapp.domain.model.IntentGateProfiles
import com.life.mindfulnessapp.domain.model.LeaveRitual
import com.life.mindfulnessapp.domain.model.MomentTimeContext
import com.life.mindfulnessapp.domain.model.MomentTimeContexts
import com.life.mindfulnessapp.domain.model.RecentPurposeStat
import com.life.mindfulnessapp.domain.model.PendingInterrupt
import com.life.mindfulnessapp.domain.model.SessionLimitPolicy
import com.life.mindfulnessapp.domain.model.ThemePack
import com.life.mindfulnessapp.domain.usecase.GetAppHistoryUsageUseCase
import com.life.mindfulnessapp.ui.theme.CapabilityForm
import com.life.mindfulnessapp.ui.theme.CapabilityKind
import com.life.mindfulnessapp.ui.theme.CapabilityMark
import com.life.mindfulnessapp.ui.theme.DayBg
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.theme.LogoGreenBright
import com.life.mindfulnessapp.ui.theme.MindfulnessAppTheme
import com.life.mindfulnessapp.ui.theme.MistAccent
import com.life.mindfulnessapp.ui.theme.MistBg
import com.life.mindfulnessapp.ui.theme.MistCardBg
import com.life.mindfulnessapp.ui.theme.MistDivider
import com.life.mindfulnessapp.ui.theme.MistTextHint
import com.life.mindfulnessapp.ui.theme.MistTextPrimary
import com.life.mindfulnessapp.ui.theme.MistTextSecondary
import com.life.mindfulnessapp.ui.theme.MonitorCapability
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 胶囊目标位置（屏幕坐标系，用于退场动画定位） */
data class CapsuleTargetPosition(
    val x: Float,
    val y: Float
)

/** 确认进入：快速淡出，由胶囊自行入场（避免悬浮窗硬件层飞行动画残影） */
private const val ENTER_EXIT_FADE_MS = 200

/** 冷却内离开：极短淡出（防刷） */
private const val LEAVE_COOLDOWN_FADE_MS = LeaveRitual.COOLDOWN_FADE_MS

/** 合格离开：门内呼气收束 */
private const val LEAVE_EXHALE_MS = LeaveRitual.EXHALE_MS

private const val COOLDOWN_SECONDS = 3

/** 已填写目的后的缩短冷静期秒数 */
private const val COOLDOWN_WITH_PURPOSE = 1

/** 入场：先停（只有图标）→ 名言 → 再问。点空白可跳过。 */
private const val PAUSE_ICON_MS = 720L
private const val PAUSE_IDENTITY_MS = 360L
private const val PAUSE_QUOTE_MS = 520L
private const val PAUSE_QUOTE_HEAVY_MS = 820L
private const val CONTEXT_AFTER_ASK_MS = 140L
/**
 * 拦截页格言暂关：占空间过大；后续作为探索模块子功能再开。
 * 设为 true 可恢复入场停顿与名言展示。
 */
private const val INTERCEPT_QUOTES_ENABLED = false
/** 第几次起才出示「今日第 N 次」 */
private const val IMPULSE_CONTEXT_FROM = 3
/** 第几次起名言多停一瞬 */
private const val IMPULSE_HEAVY_FROM = 4

// ── 本地兜底名言（格式化 author 供 UI 使用）────────────────────────────────────
private val DISPLAY_FALLBACK_QUOTES: List<DisplayQuote> = FALLBACK_QUOTES.map { q ->
    q.copy(author = if (q.author.isNotBlank()) "— ${q.author}" else "")
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
    /** 「带着意图进入」— 专注蓝（与品牌绿克制/守住区分） */
    val focusEnterColor: Color,
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
 * 拦截门 / 陪伴条共用色板，跟 [ThemePack] 气质一致。
 * [themeId] 保留兼容，已忽略。
 */
fun getInterceptThemeConfig(themeId: String = "simple", isDark: Boolean = true): InterceptThemeConfig =
    getInterceptThemeConfig(if (isDark) ThemePack.Night else ThemePack.Day)

fun getInterceptThemeConfig(pack: ThemePack): InterceptThemeConfig = when (pack) {
    ThemePack.Night -> InterceptThemeConfig(
        bgColor = Color(0xFF070908),
        surfaceColor = Color(0xFF1C1C1E),
        dividerColor = Color(0xFF38383A),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFF8E8E93),
        textTertiary = Color(0xFF48484A),
        accentColor = LogoGreenBright,
        accentForeground = Color(0xFFFFFFFF),
        limitAccentColor = Color(0xFFFF453A),
        limitAccentForeground = Color(0xFFFFFFFF),
        titleText = "这一次是什么？",
        limitTitleText = "时间到了",
        dismissButtonText = "不进去了",
        focusEnterColor = Color(0xFF6BB4F0),
        capsuleBgColor = Color(0xF0000000),
        capsuleAccentColor = LogoGreenBright,
        capsuleStopButtonColor = LogoGreenBright,
        ceremonyBgColor = Color(0xFF1C1C1E),
        ceremonyTextColor = Color(0xFFFFFFFF),
        ceremonySubLabelColor = Color(0xFF8E8E93)
    )
    ThemePack.Day -> InterceptThemeConfig(
        bgColor = DayBg,
        surfaceColor = Color(0xFFFFFFFF),
        dividerColor = Color(0xFFD1D1D6),
        textPrimary = Color(0xFF000000),
        textSecondary = Color(0xFF6C6C70),
        textTertiary = Color(0xFFAEAEB2),
        accentColor = LogoGreen,
        accentForeground = Color(0xFFFFFFFF),
        limitAccentColor = Color(0xFFFF3B30),
        limitAccentForeground = Color(0xFFFFFFFF),
        titleText = "这一次是什么？",
        limitTitleText = "时间到了",
        dismissButtonText = "不进去了",
        focusEnterColor = Color(0xFF2A7FD4),
        capsuleBgColor = Color(0xF0F2F2F7),
        capsuleAccentColor = LogoGreen,
        capsuleStopButtonColor = LogoGreen,
        ceremonyBgColor = Color(0xFFFFFFFF),
        ceremonyTextColor = Color(0xFF000000),
        ceremonySubLabelColor = Color(0xFF6C6C70)
    )
    ThemePack.Mist -> InterceptThemeConfig(
        bgColor = MistBg,
        surfaceColor = MistCardBg,
        dividerColor = MistDivider,
        textPrimary = MistTextPrimary,
        textSecondary = MistTextSecondary,
        textTertiary = MistTextHint,
        accentColor = MistAccent,
        accentForeground = Color(0xFFFFFFFF),
        limitAccentColor = Color(0xFFC06B55),
        limitAccentForeground = Color(0xFFFFFFFF),
        titleText = "这一次是什么？",
        limitTitleText = "时间到了",
        dismissButtonText = "不进去了",
        focusEnterColor = Color(0xFF5B7F96),
        capsuleBgColor = Color(0xE6E6ECE8),
        capsuleAccentColor = MistAccent,
        capsuleStopButtonColor = MistAccent,
        ceremonyBgColor = MistCardBg,
        ceremonyTextColor = MistTextPrimary,
        ceremonySubLabelColor = MistTextSecondary
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

@OptIn(ExperimentalLayoutApi::class)
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
    themePack: ThemePack = ThemePack.Night,
    isDarkTheme: Boolean = themePack.isDark,
    /** 是否启用单次时长契约 */
    sessionLimitEnabled: Boolean = true,
    /** 默认单次时长（分钟） */
    defaultSessionLimitMinutes: Int = 15,
    /** 是否启用意图关键词检验 */
    intentQualityCheckEnabled: Boolean = false,
    /** 用户自定义限制关键词 */
    intentBlockKeywords: List<String> = emptyList(),
    /** 未闭环有名意图/搜索快照：总门弱链「刚刚 · 意图」 */
    pendingInterrupt: PendingInterrupt? = null,
    /** 今日冲动次数（含本次） */
    impulseCount: Int = 1,
    /** 今日放行进入次数 */
    enterCount: Int = 0,
    /** 今日克制次数 */
    dismissCount: Int = 0,
    onReset: ((newDailyMinutes: Int, newWeeklyMinutes: Int) -> Unit)? = null,
    onContinue: (com.life.mindfulnessapp.domain.model.InterceptEnterDecision) -> Unit,
    /** 用户点「刚刚 · 意图　继续」 */
    onResumePrevious: (() -> Unit)? = null,
    /**
     * 同 App 仪式冷却中：门内仅极短淡出，无触感高潮。
     * 由 OverlayManager 按 [LeaveRitual.COOLDOWN_MS] 判定。
     */
    leaveRitualInCooldown: Boolean = false,
    onDismiss: () -> Unit,
    onKeywordBlocked: (() -> Unit)? = null,
    /** 意图草稿非空变化（门外离开埋点） */
    onIntentDraftChanged: ((Boolean) -> Unit)? = null,
    /** false：静默重展 / 续接上次，跳过入场停顿 */
    playPauseBeat: Boolean = true,
    /** 用户确认进入、退场动画开始时（早于 [onContinue]） */
    onEnterAnimating: (() -> Unit)? = null,
    /** 「有意义的事」绑了 App：离开门后拉起 */
    onLaunchMeaningfulApp: ((packageName: String) -> Unit)? = null,
    /** 正向出口：去做了（事 / 计划 / 地方） */
    onPositiveExit: ((com.life.mindfulnessapp.domain.model.PositiveExitChoice) -> Unit)? = null,
    /** 打开该 App 整体监控设置页 */
    onOpenAppSettings: (() -> Unit)? = null,
    /**
     * 注册 Compose 侧返回键处理：true = 已回到拦截首页/上一级，勿守住离开。
     * Overlay 无 OnBackPressedDispatcher，走 OverlayManager 按键通道。
     */
    onComposeBackHandlerChange: ((() -> Boolean)?) -> Unit = {}
) {
    val themeConfig = remember(themePack) { getInterceptThemeConfig(themePack) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var commonIntents by remember {
        mutableStateOf<List<com.life.mindfulnessapp.domain.model.CommonIntentItem>>(emptyList())
    }
    var recentPurposes by remember { mutableStateOf<List<RecentPurposeStat>>(emptyList()) }
    var decisionBackHandler by remember { mutableStateOf<(() -> Boolean)?>(null) }

    DisposableEffect(decisionBackHandler) {
        onComposeBackHandlerChange {
            decisionBackHandler?.invoke() == true
        }
        onDispose { onComposeBackHandlerChange(null) }
    }

    LaunchedEffect(packageName) {
        if (packageName.isBlank()) return@LaunchedEffect
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            InterceptOverlayEntryPoint::class.java
        )
        recentPurposes = withContext(Dispatchers.IO) {
            entryPoint.usageRecordRepository().getRecentPurposeStats(packageName, 24)
        }
        withContext(Dispatchers.IO) {
            commonIntents = entryPoint.appLimitRepository().getCommonIntents(packageName)
        }
    }

    var isExiting by remember { mutableStateOf(false) }
    var isLeaveDismissing by remember { mutableStateOf(false) }
    var leaveHolding by remember { mutableStateOf(false) }
    val exitProgress = remember { Animatable(0f) }
    val leaveProgress = remember { Animatable(0f) }
    var isDismissing by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    suspend fun playExitAnimation() {
        isExiting = true
        exitProgress.animateTo(1f, animationSpec = tween(ENTER_EXIT_FADE_MS, easing = FastOutSlowInEasing))
    }

    /** 页上离开：合格则门内呼气；冷却内极短淡出（防刷）。 */
    suspend fun playLeaveDismissAnimation() {
        leaveHolding = true
        isLeaveDismissing = true
        val duration = if (leaveRitualInCooldown) LEAVE_COOLDOWN_FADE_MS else LEAVE_EXHALE_MS
        if (!leaveRitualInCooldown) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        leaveProgress.animateTo(
            1f,
            animationSpec = tween(duration, easing = FastOutSlowInEasing)
        )
    }

    val overlayBgAlpha = when {
        isLeaveDismissing -> 1f - leaveProgress.value
        isExiting -> 1f - exitProgress.value
        else -> 1f
    }

    @Suppress("UNUSED_PARAMETER")
    fun absorbUnused(
        a: Any?, b: Any?, c: Any?, d: Any?, e: Any?, f: Any?, g: Any?, h: Any?
    ) = Unit
    absorbUnused(
        weeklyLimitMinutes, weekUsedSeconds, todayRecords, remainingModifyCount,
        themeId, capsuleTargetPosition, onReset, onOpenAppSettings
    )
    @Suppress("UNUSED_VARIABLE")
    val absorbEnterCount = enterCount

    MindfulnessAppTheme(themePack = themePack) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(overlayBgAlpha)
        ) {
            BreathGateOverlayScreen(
                appName = appName,
                remainingLabel = buildString {
                    if (impulseCount > 0) append("今日第 ${impulseCount.coerceAtLeast(1)} 次")
                    val usedMin = (todayUsedSeconds / 60L).toInt()
                    when {
                        dailyLimitMinutes > 0 -> {
                            if (isNotEmpty()) append(" · ")
                            append("已用 $usedMin/$dailyLimitMinutes 分钟")
                        }
                        usedMin > 0 -> {
                            if (isNotEmpty()) append(" · ")
                            append("已用 $usedMin 分钟")
                        }
                    }
                }.ifBlank { "停一下再决定" },
                canSearch = com.life.mindfulnessapp.data.deeplink.SearchDeepLinkCatalog
                    .preferSearchLanding(packageName),
                offerBrowse = com.life.mindfulnessapp.data.deeplink.SearchDeepLinkCatalog
                    .preferSearchLanding(packageName),
                intentTags = com.life.mindfulnessapp.domain.model.IntentGateProfiles
                    .quickIntentTags(packageName),
                browseUsedMinutes = (todayUsedSeconds / 60L).toInt().coerceAtLeast(0),
                browseLimitMinutes = dailyLimitMinutes.takeIf { it > 0 },
                maxSessionMinutes = if (dailyLimitMinutes > 0) {
                    val left = SessionLimitPolicy.dailyRemainingMinutes(
                        dailyLimitMinutes,
                        todayUsedSeconds
                    )
                    if (left <= 0) 0 else minOf(SessionLimitPolicy.MAX_SESSION_MINUTES, left)
                } else {
                    SessionLimitPolicy.MAX_SESSION_MINUTES
                },
                packageName = packageName,
                themePack = themePack,
                pendingInterrupt = pendingInterrupt,
                onResumePrevious = onResumePrevious,
                leaveHolding = leaveHolding,
                onAdmit = { choice, done ->
                    if (isDismissing || isExiting) {
                        done(false)
                        return@BreathGateOverlayScreen
                    }
                    isDismissing = true
                    onEnterAnimating?.invoke()
                    coroutineScope.launch {
                        playExitAnimation()
                        val written = choice.text.trim()
                        onContinue(
                            com.life.mindfulnessapp.domain.model.InterceptEnterDecision(
                                purpose = written,
                                intentKind = when {
                                    choice.search ->
                                        com.life.mindfulnessapp.domain.model.IntentKind.SEARCH
                                    written.isEmpty() ->
                                        com.life.mindfulnessapp.domain.model.IntentKind.PURPOSEFUL
                                    else ->
                                        com.life.mindfulnessapp.domain.model.NamingKind
                                            .resolveIntentKind(written)
                                },
                                sessionLimitMinutes = choice.minutes.coerceAtLeast(0),
                                landMode = if (choice.search) {
                                    com.life.mindfulnessapp.domain.model.IntentLandMode.SEARCH
                                } else {
                                    com.life.mindfulnessapp.domain.model.IntentLandMode.NORMAL
                                }
                            )
                        )
                        done(true)
                    }
                },
                onLeave = {
                    if (!isDismissing) {
                        isDismissing = true
                        coroutineScope.launch {
                            playLeaveDismissAnimation()
                            onDismiss()
                        }
                    }
                }
            )
        }
    }
}


// ════════════════════════════════════════════════════════════════════════════
//  iOS 极简风格子组件
// ════════════════════════════════════════════════════════════════════════════

/** 入场注视 / 决策态身份图标 */
private val InterceptIconHeroDp = 84.dp
private val InterceptIconCompactDp = 44.dp

/** 无 Ripple：点击后立刻卸悬浮窗时，波纹会绑到已 detach 的 View 崩溃 */
@Composable
private fun InterceptSettingsButton(
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Settings,
            contentDescription = "打开设置",
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * 拦截头区（上区卡片）：App 语境 + 时钟占位。
 */
@Composable
private fun SimpleAppHeader(
    appName: String,
    packageName: String,
    iconScale: Float,
    themeConfig: InterceptThemeConfig,
    impulseCount: Int,
    enterCount: Int,
    dismissCount: Int,
    timeLimitActive: Boolean,
    todayUsedSeconds: Long,
    weekUsedSeconds: Long,
    dailyLimitMinutes: Int,
    weeklyLimitMinutes: Int,
    dailyProgress: Float,
    weeklyProgress: Float,
    includesPreMonitorUsage: Boolean,
    showIdentity: Boolean,
    showContext: Boolean,
    heroIcon: Boolean = true,
    todayIntentCount: Int = 0,
    onTodayIntentsClick: (() -> Unit)? = null,
    /** 搜索门：日期·时辰语境 */
    exploreTimeContext: MomentTimeContext? = null,
    /** 非探索门：时长锁用量条 */
    forceUsageStrip: Boolean = false,
    onOpenAppSettings: (() -> Unit)? = null,
    onOpenUsage: (() -> Unit)? = null,
    onOpenClock: (() -> Unit)? = null
) {
    @Suppress("UNUSED_PARAMETER")
    fun absorbHeaderCompat(a: Any?, b: Any?, c: Any?, d: Any?, e: Any?, f: Any?, g: Any?) = Unit
    absorbHeaderCompat(
        dismissCount, todayIntentCount, onTodayIntentsClick, enterCount,
        heroIcon, forceUsageStrip, weekUsedSeconds
    )
    absorbHeaderCompat(
        dailyProgress, weeklyProgress, weeklyLimitMinutes, null, null, null, null
    )

    val context = LocalContext.current
    val appIcon = remember(packageName) {
        if (packageName.isNotEmpty()) {
            try { context.packageManager.getApplicationIcon(packageName) }
            catch (e: Exception) { null }
        } else null
    }

    val subtitle = remember(
        exploreTimeContext, timeLimitActive, todayUsedSeconds, dailyLimitMinutes,
        impulseCount, includesPreMonitorUsage
    ) {
        when {
            exploreTimeContext != null -> {
                val cost = buildExploreCostLine(
                    timeLimitActive = timeLimitActive,
                    todayUsedSeconds = todayUsedSeconds,
                    dailyLimitMinutes = dailyLimitMinutes,
                    impulseCount = impulseCount,
                    includesPreMonitorUsage = includesPreMonitorUsage
                )
                listOfNotNull(
                    exploreTimeContext.headerLine.takeIf { it.isNotBlank() },
                    cost.takeIf { it.isNotBlank() }
                ).joinToString(" · ").ifBlank {
                    if (impulseCount > 0) "今日第 ${impulseCount.coerceAtLeast(1)} 次" else ""
                }
            }
            todayUsedSeconds > 0L -> {
                buildString {
                    append("今日已用 ${formatSecondsToText(todayUsedSeconds)}")
                    if (includesPreMonitorUsage) append(" · 含系锚前")
                }
            }
            impulseCount > 0 -> "今日第 ${impulseCount.coerceAtLeast(1)} 次"
            else -> ""
        }
    }

    // 上区无卡片壳：语境信息平铺，决策感留给中区
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showIdentity) {
                InterceptTopAppMeta(
                    appName = appName,
                    subtitle = if (showContext) subtitle else "",
                    appIcon = if (appIcon != null) {
                        {
                            val bitmap = remember(appIcon) {
                                appIcon.toBitmap(96, 96).asImageBitmap()
                            }
                            androidx.compose.foundation.Image(
                                bitmap = bitmap,
                                contentDescription = appName,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .scale(iconScale)
                            )
                        }
                    } else null,
                    themeConfig = themeConfig,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            InterceptPeekClock(
                themeConfig = themeConfig,
                size = 76.dp,
                onClick = onOpenClock
            )
        }
        if (onOpenAppSettings != null || onOpenUsage != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onOpenUsage != null && showContext && subtitle.isNotBlank()) {
                    Text(
                        text = "使用量 ›",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = themeConfig.accentColor.copy(alpha = 0.9f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = onOpenUsage
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
                if (onOpenAppSettings != null) {
                    InterceptSettingsButton(
                        tint = themeConfig.textTertiary.copy(alpha = 0.85f),
                        onClick = onOpenAppSettings
                    )
                }
            }
        }
    }
}

/**
 * 探索门上区：小身份 + 日期时辰 + 一行代价；右上角进整体设置。
 */
@Composable
private fun ExploreGateHeader(
    appName: String,
    appIcon: android.graphics.drawable.Drawable?,
    iconScale: Float,
    themeConfig: InterceptThemeConfig,
    moment: MomentTimeContext,
    timeLimitActive: Boolean,
    todayUsedSeconds: Long,
    dailyLimitMinutes: Int,
    impulseCount: Int,
    includesPreMonitorUsage: Boolean,
    showIdentity: Boolean,
    showContext: Boolean,
    onOpenAppSettings: (() -> Unit)?,
    onOpenUsage: (() -> Unit)?
) {
    val iconSize = 36.dp
    val costLine = remember(
        timeLimitActive, todayUsedSeconds, dailyLimitMinutes, impulseCount, includesPreMonitorUsage
    ) {
        buildExploreCostLine(
            timeLimitActive = timeLimitActive,
            todayUsedSeconds = todayUsedSeconds,
            dailyLimitMinutes = dailyLimitMinutes,
            impulseCount = impulseCount,
            includesPreMonitorUsage = includesPreMonitorUsage
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(
                visible = showIdentity,
                enter = InterceptMotion.revealEnter(260),
                exit = InterceptMotion.revealExit()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .scale(iconScale)
                            .size(iconSize)
                            .clip(RoundedCornerShape(10.dp))
                            .background(themeConfig.surfaceColor),
                        contentAlignment = Alignment.Center
                    ) {
                        if (appIcon != null) {
                            val bitmap = remember(appIcon) {
                                appIcon.toBitmap(96, 96).asImageBitmap()
                            }
                            androidx.compose.foundation.Image(
                                bitmap = bitmap,
                                contentDescription = appName,
                                modifier = Modifier
                                    .size(iconSize)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        } else {
                            Text(
                                text = appName.take(1),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Light,
                                color = themeConfig.textPrimary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = appName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = themeConfig.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (!showIdentity) {
                Spacer(modifier = Modifier.weight(1f))
            }
            if (onOpenAppSettings != null) {
                InterceptSettingsButton(
                    tint = themeConfig.textTertiary.copy(alpha = 0.85f),
                    onClick = onOpenAppSettings
                )
            }
        }

        AnimatedVisibility(
            visible = showContext,
            enter = InterceptMotion.contextEnter(),
            exit = InterceptMotion.contextExit()
        ) {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                Text(
                    text = moment.headerLine,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = themeConfig.textSecondary.copy(alpha = 0.95f),
                    letterSpacing = 0.15.sp
                )
                if (costLine.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .then(
                                if (onOpenUsage != null) {
                                    Modifier
                                        .border(
                                            1.dp,
                                            themeConfig.dividerColor.copy(alpha = 0.35f),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable(
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() },
                                            onClick = onOpenUsage
                                        )
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                } else {
                                    Modifier
                                }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = costLine,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            color = themeConfig.textTertiary.copy(alpha = 0.92f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (onOpenUsage != null) {
                            Text(
                                text = "使用量 ›",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = themeConfig.accentColor.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }
        }

        // 上区底部分隔：与中区拉开
        if (showContext) {
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(
                thickness = 1.dp,
                color = themeConfig.dividerColor.copy(alpha = 0.32f)
            )
        }
    }
}

private fun buildExploreCostLine(
    timeLimitActive: Boolean,
    todayUsedSeconds: Long,
    dailyLimitMinutes: Int,
    impulseCount: Int,
    includesPreMonitorUsage: Boolean
): String {
    val used = if (todayUsedSeconds > 0L) {
        "今日已用 ${formatSecondsToText(todayUsedSeconds)}"
    } else {
        null
    }
    val remaining = if (dailyLimitMinutes > 0) {
        val left = SessionLimitPolicy.dailyRemainingMinutes(dailyLimitMinutes, todayUsedSeconds)
        when {
            left <= 0 -> "已用完"
            else -> "还剩 ${left}分"
        }
    } else null

    return when {
        used != null && remaining != null -> {
            val base = "$used · $remaining"
            if (includesPreMonitorUsage) "$base · 含系锚前" else base
        }
        used != null -> {
            if (includesPreMonitorUsage) "$used · 含系锚前" else used
        }
        timeLimitActive && remaining != null -> "今日尚未使用 · $remaining"
        impulseCount > 0 -> "今日想打开 ${impulseCount.coerceAtLeast(1)} 次"
        else -> "今日尚未使用"
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
                    text = themeConfig.dismissButtonText,
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
    var text by remember { mutableStateOf(initDaily.toString()) }
    val parsed = text.toIntOrNull()
    val newDailyMinutes = parsed?.coerceIn(5, 480)
    val valid = newDailyMinutes != null

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
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { raw ->
                            text = raw.filter { it.isDigit() }.take(3)
                        },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = themeConfig.accentColor,
                            textAlign = TextAlign.Center
                        ),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = themeConfig.accentColor,
                            unfocusedBorderColor = themeConfig.textSecondary.copy(alpha = 0.35f),
                            focusedTextColor = themeConfig.accentColor,
                            unfocusedTextColor = themeConfig.accentColor,
                            cursorColor = themeConfig.accentColor
                        ),
                        modifier = Modifier.width(120.dp)
                    )
                    Text(
                        "分",
                        fontSize = 14.sp,
                        color = themeConfig.textSecondary,
                        modifier = Modifier.padding(start = 6.dp, bottom = 18.dp)
                    )
                }
                Text("今日新目标", fontSize = 12.sp, color = themeConfig.textSecondary)
                if (newDailyMinutes != null && newDailyMinutes < todayUsedMinutes) {
                    Text(
                        "低于今日正念时长，设定后将立即超限",
                        fontSize = 12.sp,
                        color = themeConfig.limitAccentColor,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val minutes = newDailyMinutes ?: return@Button
                    onConfirm(minutes, currentWeeklyLimitMinutes)
                },
                enabled = valid,
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
    fun appPreferences(): com.life.mindfulnessapp.data.AppPreferences
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface InterceptOverlayEntryPoint {
    fun appLimitRepository(): AppLimitRepository
    fun usageRecordRepository(): UsageRecordRepository
    fun appPreferences(): com.life.mindfulnessapp.data.AppPreferences
    fun getInstalledAppsUseCase(): com.life.mindfulnessapp.domain.usecase.GetInstalledAppsUseCase
    fun planBlockRepository(): com.life.mindfulnessapp.data.repository.PlanBlockRepository
    fun walkAwarenessDetector(): com.life.mindfulnessapp.service.WalkAwarenessDetector
}
