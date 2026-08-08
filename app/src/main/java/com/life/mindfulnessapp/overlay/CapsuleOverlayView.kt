package com.life.mindfulnessapp.overlay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity.MindfulnessLevel
import com.life.mindfulnessapp.service.SessionManager
import com.life.mindfulnessapp.ui.theme.CapabilityForm
import com.life.mindfulnessapp.ui.theme.CapabilityKind
import com.life.mindfulnessapp.ui.theme.CapabilityMark
import com.life.mindfulnessapp.ui.theme.MonitorCapability
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 岛内正文（深色主题 / 实心黑壳） */
private val ShellTextLight = Color(0xFFF2F2F7)

/** 岛内正文（浅色主题） */
private val ShellTextDark = Color(0xFF1C1C1E)

/** 实心黑岛（灵动岛材质） */
private val IslandBlack = Color(0xFF0A0A0C)

/** 浅色主题岛面 */
private val IslandLight = Color(0xFFF2F2F7)

// ── 灵动岛尺寸：收起分段胶囊 / 展开横幅 ─────────────────────────────────────
/**
 * 迷你态尺寸档：只影响收起壳与字号，不改信息结构。
 * - [MiniStandard]：更舒展（设置默认）
 * - [MiniCompact]：当前偏省空间的一档
 */
private data class CapsuleMiniMetrics(
    val height: Dp,
    val corner: Dp,
    val wCompact: Dp,
    val wCompactWide: Dp,
    val wIntentPlain: Dp,
    val wIntentLabeled: Dp,
    val wPaused: Dp,
    val padH: Dp,
    val breathDot: Dp,
    val pauseIcon: Dp,
    val nameSp: Float,
    val labelSp: Float,
    val timerSp: Float,
    val stopSp: Float,
    val nameMax: Dp,
    val labelMaxWithStop: Dp,
    val labelMaxNoStop: Dp,
    val stopStartPad: Dp,
    val stopPadH: Dp,
    val stopPadV: Dp,
    val dividerH: Dp,
    val nameGap: Dp
)

/** 紧凑：现网偏省空间的迷你岛 */
private val MiniCompact = CapsuleMiniMetrics(
    height = 34.dp,
    corner = 17.dp,
    wCompact = 130.dp,
    wCompactWide = 148.dp,
    wIntentPlain = 142.dp,
    wIntentLabeled = 192.dp,
    wPaused = 176.dp,
    padH = 9.dp,
    breathDot = 5.dp,
    pauseIcon = 22.dp,
    nameSp = 11f,
    labelSp = 11f,
    timerSp = 12f,
    stopSp = 10f,
    nameMax = 40.dp,
    labelMaxWithStop = 72.dp,
    labelMaxNoStop = 88.dp,
    stopStartPad = 5.dp,
    stopPadH = 8.dp,
    stopPadV = 4.dp,
    dividerH = 11.dp,
    nameGap = 6.dp
)

/** 标准：对照更舒展的形态（默认） */
private val MiniStandard = CapsuleMiniMetrics(
    height = 38.dp,
    corner = 19.dp,
    wCompact = 148.dp,
    wCompactWide = 168.dp,
    wIntentPlain = 160.dp,
    wIntentLabeled = 216.dp,
    wPaused = 200.dp,
    padH = 11.dp,
    breathDot = 6.dp,
    pauseIcon = 24.dp,
    nameSp = 12f,
    labelSp = 12f,
    timerSp = 13f,
    stopSp = 11f,
    nameMax = 48.dp,
    labelMaxWithStop = 88.dp,
    labelMaxNoStop = 104.dp,
    stopStartPad = 6.dp,
    stopPadH = 9.dp,
    stopPadV = 5.dp,
    dividerH = 13.dp,
    nameGap = 7.dp
)

/**
 * 展开态：近全宽、两侧留边、水平居中（对齐 iOS Dynamic Island expanded）。
 * 外层水平 [CapsuleOuterPadH] 含在 WRAP 宽内；[ExpandedSideMargin] 即屏边到黑壳的视觉边距。
 */
private val CapsuleOuterPadH = 8.dp
private val CapsuleOuterPadTop = 2.dp
private val CapsuleOuterPadBottom = 4.dp
private val ExpandedSideMargin = 10.dp
/** 展开高度：日常/仪式信息岛；决策岛另加 [DecisionExtendExtraH] */
private val ExpandedH = 108.dp
/** 决策岛带「续一会儿」时的额外高度 */
private val DecisionExtendExtraH = 40.dp
/** 迷你态底部预警条额外高度（仅非意图门时长锁） */
private val CollapsedWarnExtraH = 20.dp
/** 展开圆角：略小于半高，保持「岛」感 */
private val ExpandedR = 26.dp
private val ExpandedInnerPadH = 16.dp
private val ExpandedInnerPadV = 12.dp

/** 展开横幅模板：入场仪式 / 日常信息 / 紧急·续时决策 */
private enum class ExpandBannerMode {
    Ritual,
    Daily,
    Decision
}

/** 按屏宽计算展开壳宽：屏宽 − 两侧视觉边距 */
private fun expandedIslandWidth(screenWidth: Dp): Dp =
    (screenWidth - ExpandedSideMargin * 2).coerceAtLeast(MiniStandard.wIntentLabeled + 40.dp)

/** 入场从窄条长出，不先落成收起胶囊（意图门仪式用） */
private val EntranceSeedW = 44.dp
private val EntranceSeedH = 30.dp

/** 纯时长锁：圆环种子可见停留（毫秒），再气泡展开 */
private const val RING_ENTRANCE_HOLD_MS = 420L

/** 意图倒计时最后 N 秒：变红并自动展开 */
private const val SESSION_COUNTDOWN_URGENT_SEC = 30L

/** 意图倒计时 1 分钟内：仅变黄，不加挤边距的提示条 */
private const val SESSION_COUNTDOWN_WARN_SEC = 60L

/** 入场展开态停留（毫秒） */
private const val ENTRANCE_HOLD_MS = 2400L

/** 宽轴弹簧：利落、轻过冲（对齐系统岛体感） */
private val IslandWidthSpring = spring<Float>(
    dampingRatio = 0.78f,
    stiffness = 380f
)

/** 纯时长锁：圆环 → 迷你的气泡拉宽（略软、轻过冲） */
private val BubbleExpandSpring = spring<Float>(
    dampingRatio = 0.74f,
    stiffness = 320f
)

/** 高轴弹簧：略跟宽轴，避免二次弹跳感 */
private val IslandHeightSpring = spring<Float>(
    dampingRatio = 0.82f,
    stiffness = 420f
)

/** 圆角弹簧：跟形态一起走 */
private val IslandCornerSpring = spring<Float>(
    dampingRatio = 0.85f,
    stiffness = 500f
)

/** 入场落岛：轻过冲 */
private val IslandAppearSpring = spring<Float>(
    dampingRatio = 0.72f,
    stiffness = Spring.StiffnessMedium
)

/** 圆环种子冒泡：更明显的从点弹出 */
private val BubblePopSpring = spring<Float>(
    dampingRatio = 0.62f,
    stiffness = 420f
)

/** 平滑阶跃：内容随壳宽显现/隐去 */
private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    if (edge0 == edge1) return if (x >= edge1) 1f else 0f
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

// ── 时间压力颜色（四档：宽松→紧张） ────────────────────────────────────────
private fun urgencyColor(themeAccent: Color, ratio: Float): Color = when {
    ratio > 0.50f -> themeAccent
    ratio > 0.20f -> Color(0xFFFFD54F)
    ratio > 0.10f -> Color(0xFFFF8A65)
    else          -> Color(0xFFEF5350)
}

private fun urgencySubColor(themeAccent: Color, ratio: Float): Color = when {
    ratio > 0.50f -> themeAccent.copy(alpha = 0.75f)
    ratio > 0.20f -> Color(0xFFFFE082)
    ratio > 0.10f -> Color(0xFFFFCC80)
    else          -> Color(0xFFEF9A9A)
}

@Composable
fun CapsuleOverlayView(
    sessionManager: SessionManager?,
    appName: State<String>,
    appPackageName: State<String> = mutableStateOf(""),
    sessionSeconds: State<Long>,
    dailyRemainingSeconds: State<Long>,
    dailyLimitSeconds: State<Long>,
    purpose: State<String?>,
    expanded: State<Boolean>,
    isPaused: State<Boolean> = mutableStateOf(false),
    isOverLimit: State<Boolean> = mutableStateOf(false),
    hasIntentGate: State<Boolean> = mutableStateOf(true),
    hasTimeLock: State<Boolean> = mutableStateOf(true),
    hasSessionLimit: State<Boolean> = mutableStateOf(false),
    /** 单次时长临近结束且尚未续过时可续 */
    canOfferExtension: State<Boolean> = mutableStateOf(false),
    /** 纯时长锁迷你态：已用侧是否显示到秒 */
    showUsedSeconds: State<Boolean> = mutableStateOf(false),
    /** 迷你态尺寸：true = 紧凑（现网偏小档），false = 标准（默认更舒展） */
    miniCompact: State<Boolean> = mutableStateOf(false),
    awayCountdownSeconds: State<Long> = mutableStateOf(-1L),
    isDarkTheme: Boolean = true,
    onToggleExpand: () -> Unit,
    /** note / 正念程度 / 是否「结束并去心锚」 */
    onEndSession: (note: String?, mindfulnessLevel: Int?, openToAnchor: Boolean) -> Unit,
    onExtendSession: ((extraMinutes: Int) -> Unit)? = null,
    onReturnToApp: (() -> Unit)? = null,
    onRegisterWakeUp: ((wakeUpFn: () -> Unit) -> Unit)? = null,
    onRegisterShowConfirm: ((showConfirmFn: () -> Unit) -> Unit)? = null,
    onRegisterWarnFiveMin: ((fn: () -> Unit) -> Unit)? = null,
    onRegisterStartCountdown: ((fn: () -> Unit) -> Unit)? = null,
    onRegisterStopAction: ((stopFn: () -> Unit) -> Unit)? = null,
    /** 入场展开停留期间：点按/外侧点按可提前收起；传 null 表示注销 */
    onRegisterSkipEntrance: ((skipFn: (() -> Unit)?) -> Unit)? = null,
    onStopHitRectChanged: ((rect: android.graphics.RectF?) -> Unit)? = null,
    onEndDialogVisibilityChanged: ((open: Boolean) -> Unit)? = null,
    onConfirmDialogOpen: (() -> Unit)? = null,
    onConfirmDialogClose: (() -> Unit)? = null,
    /** 入场/显现收成迷你后回调（用于窗口吸到停靠点） */
    onMiniSettled: (() -> Unit)? = null,
    playEnterAnimation: Boolean = true,
    softReveal: Boolean = false,
    playIntentSeal: Boolean = false
) {
    val themeConfig = remember(isDarkTheme) { getInterceptThemeConfig(isDark = isDarkTheme) }
    val overLimitColor = themeConfig.limitAccentColor
    val coroutineScope = rememberCoroutineScope()

    val overLimit by remember { derivedStateOf { isOverLimit.value } }
    val intentGate by remember { derivedStateOf { hasIntentGate.value } }
    val timeLock by remember { derivedStateOf { hasTimeLock.value || isOverLimit.value } }
    val intentOnly by remember { derivedStateOf { intentGate && !timeLock } }
    val awayRemain = awayCountdownSeconds.value
    val showAwayCountdown = isPaused.value && awayRemain >= 0L
    val awayUrgent = showAwayCountdown && awayRemain in 1L..30L
    val shellTextPrimary = if (isDarkTheme) ShellTextLight else ShellTextDark
    val shellTextSecondary = shellTextPrimary.copy(alpha = 0.68f)

    val ratio by remember {
        derivedStateOf {
            if (overLimit || !timeLock) 1f
            else {
                val limit = dailyLimitSeconds.value
                if (limit <= 0L) 1f
                else (dailyRemainingSeconds.value.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
            }
        }
    }
    val isUrgent by remember {
        derivedStateOf { timeLock && !overLimit && ratio <= 0.10f && dailyLimitSeconds.value > 0L }
    }
    val hasLimit by remember {
        derivedStateOf { timeLock && dailyLimitSeconds.value > 0L }
    }

    val targetIcon = when {
        overLimit -> overLimitColor
        intentOnly -> themeConfig.capsuleAccentColor
        else -> urgencyColor(themeConfig.capsuleAccentColor, ratio)
    }
    val targetSub = when {
        overLimit -> overLimitColor.copy(alpha = 0.85f)
        intentOnly -> themeConfig.capsuleAccentColor.copy(alpha = 0.75f)
        else -> urgencySubColor(themeConfig.capsuleAccentColor, ratio)
    }
    val bgColor by animateColorAsState(
        targetValue = themeConfig.capsuleBgColor,
        animationSpec = tween(800, easing = FastOutSlowInEasing), label = "capsule_bg"
    )
    val iconColor by animateColorAsState(
        targetValue = targetIcon,
        animationSpec = tween(800, easing = FastOutSlowInEasing), label = "capsule_icon"
    )
    val subColor by animateColorAsState(
        targetValue = targetSub,
        animationSpec = tween(800, easing = FastOutSlowInEasing), label = "capsule_sub"
    )

    var showEndConfirmDialog by remember { mutableStateOf(false) }
    var endConfirmReason by remember { mutableStateOf(EndConfirmReason.Manual) }
    var showFiveMinWarning by remember { mutableStateOf(false) }
    var countdownMode by remember { mutableStateOf(false) }
    var showExtendDialog by remember { mutableStateOf(false) }

    val shouldPulse = timeLock && countdownMode && !overLimit && !isPaused.value
    /** 常态弱呼吸：进行中且非紧迫/休眠触发态；急迫时改用更快的脉冲 */
    val shouldBreathe = !isPaused.value && !overLimit && !shouldPulse && !isUrgent
    val infiniteTransition = rememberInfiniteTransition(label = "capsule_anim")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing), repeatMode = RepeatMode.Reverse
        ), label = "pulse_alpha"
    )
    val breathAlpha by infiniteTransition.animateFloat(
        initialValue = 0.34f, targetValue = 0.70f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "breath_alpha"
    )
    val breathBorderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.05f, targetValue = 0.11f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "breath_border"
    )
    val effectiveIconColor = if (shouldPulse) iconColor.copy(alpha = pulseAlpha) else iconColor

    fun wakeUp() { /* 已取消休眠变暗，保留空实现兼容回调 */ }

    // ── 灵动岛：宽/高/圆角分轨；内容由壳宽驱动 ─────────────────────────────
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val expandedW = remember(screenWidthDp) { expandedIslandWidth(screenWidthDp) }
    val mini = if (miniCompact.value) MiniCompact else MiniStandard
    val collapsedH = mini.height
    val collapsedR = mini.corner
    val ringSeed = mini.height
    val intentGateEarly by remember { derivedStateOf { hasIntentGate.value } }
    val pausedEarly by remember { derivedStateOf { isPaused.value } }
    val purposeEarly by remember { derivedStateOf { purpose.value?.takeIf { it.isNotBlank() } } }
    val showSecondsEarly by remember { derivedStateOf { showUsedSeconds.value } }
    val collapsedW = when {
        pausedEarly -> mini.wPaused
        intentGateEarly && purposeEarly != null -> mini.wIntentLabeled
        intentGateEarly -> mini.wIntentPlain
        showSecondsEarly -> mini.wCompactWide
        else -> mini.wCompact
    }
    val islandW = remember {
        Animatable(
            if (playEnterAnimation && !hasIntentGate.value) ringSeed.value
            else collapsedW.value
        )
    }
    val islandH = remember {
        Animatable(
            if (playEnterAnimation && !hasIntentGate.value) ringSeed.value
            else collapsedH.value
        )
    }
    val islandR = remember {
        Animatable(
            if (playEnterAnimation && !hasIntentGate.value) ringSeed.value / 2f
            else collapsedR.value
        )
    }
    val appearScale = remember {
        Animatable(
            when {
                playEnterAnimation && !hasIntentGate.value -> 0.52f
                playEnterAnimation -> 0.88f
                else -> 1f
            }
        )
    }
    val appearAlpha = remember {
        Animatable(
            when {
                playEnterAnimation -> 0f
                softReveal -> 0.4f
                else -> 1f
            }
        )
    }

    var islandExpanded by remember { mutableStateOf(false) }
    var entranceHolding by remember { mutableStateOf(false) }
    var entranceCeremony by remember { mutableStateOf(false) }
    /** 纯时长锁：圆环种子 → 气泡展开整段（含拉宽），用于左锚布局 */
    var ringEntranceActive by remember {
        mutableStateOf(playEnterAnimation && !hasIntentGate.value)
    }
    var userMorphEnabled by remember { mutableStateOf(!playEnterAnimation && !softReveal) }
    var skipNextUserCollapse by remember { mutableStateOf(false) }
    var didUrgentExpand by remember { mutableStateOf(false) }

    val wantExpanded = entranceHolding || expanded.value

    // 意图门单次时长：只用倒计时变色提示，不加底部黄字条（会挤边距）
    val showCountdownHint = !intentGate && timeLock && countdownMode && !isPaused.value && !overLimit &&
        !(showFiveMinWarning && timeLock)
    val showCollapsedExtras =
        !intentGate && (
            (showFiveMinWarning && timeLock && !isPaused.value && !overLimit) ||
                showCountdownHint
        )
    val showExtendExtras = canOfferExtension.value &&
        hasIntentGate.value &&
        hasSessionLimit.value &&
        dailyRemainingSeconds.value in 1L..SESSION_COUNTDOWN_WARN_SEC &&
        !isPaused.value &&
        !overLimit &&
        onExtendSession != null
    /** 展开态额外高度：续时 CTA 或（非意图）预警条；收起态仅预警条 */
    fun shellExtraHeight(expandedShell: Boolean): Float = when {
        expandedShell && showExtendExtras -> DecisionExtendExtraH.value
        showCollapsedExtras -> CollapsedWarnExtraH.value
        else -> 0f
    }

    var entranceSkipRequested by remember { mutableStateOf(false) }

    suspend fun morphIsland(expandedTarget: Boolean) = coroutineScope {
        val tw = if (expandedTarget) expandedW.value else collapsedW.value
        val th = (if (expandedTarget) ExpandedH.value else collapsedH.value) +
            shellExtraHeight(expandedTarget)
        val tr = if (expandedTarget) ExpandedR.value else collapsedR.value
        if (expandedTarget) {
            // 先拉宽 + 圆角，高度略滞后（胶体跟进）
            launch { islandW.animateTo(tw, IslandWidthSpring) }
            launch { islandR.animateTo(tr, IslandCornerSpring) }
            delay(28)
            islandH.animateTo(th, IslandHeightSpring)
        } else {
            // 先收高，再收宽并恢复全圆角
            launch { islandH.animateTo(th, IslandHeightSpring) }
            launch { islandR.animateTo(tr, IslandCornerSpring) }
            delay(22)
            islandW.animateTo(tw, IslandWidthSpring)
        }
    }

    /** 圆环种子 → 迷你：只拉宽（高已对齐），左锚由布局保证 */
    suspend fun morphBubbleToMini() = coroutineScope {
        val tw = collapsedW.value
        val th = collapsedH.value + shellExtraHeight(false)
        val tr = collapsedR.value
        launch { islandW.animateTo(tw, BubbleExpandSpring) }
        launch { islandR.animateTo(tr, IslandCornerSpring) }
        if (kotlin.math.abs(islandH.value - th) > 0.5f) {
            launch { islandH.animateTo(th, IslandHeightSpring) }
        }
    }

    fun openEndConfirm(reason: EndConfirmReason) {
        endConfirmReason = reason
        showEndConfirmDialog = true
    }

    /**
     * 结束入口统一走确认框。
     * 有意图时：确认框内直接轻量对照（正念程度必选 + 备注可选）。
     */
    fun openPrimaryEndAction() {
        openEndConfirm(EndConfirmReason.Manual)
    }

    DisposableEffect(Unit) {
        onRegisterWakeUp?.invoke { wakeUp() }
        onRegisterShowConfirm?.invoke { openEndConfirm(EndConfirmReason.BackgroundTimeout) }
        onRegisterWarnFiveMin?.invoke { showFiveMinWarning = true }
        onRegisterStartCountdown?.invoke { countdownMode = true }
        onRegisterStopAction?.invoke { openPrimaryEndAction() }
        onDispose {
            onRegisterWakeUp?.invoke {}
            onRegisterShowConfirm?.invoke {}
            onRegisterWarnFiveMin?.invoke {}
            onRegisterStartCountdown?.invoke {}
            onRegisterStopAction?.invoke {}
            onRegisterSkipEntrance?.invoke(null)
            onStopHitRectChanged?.invoke(null)
            onEndDialogVisibilityChanged?.invoke(false)
        }
    }

    LaunchedEffect(showEndConfirmDialog, showExtendDialog) {
        onEndDialogVisibilityChanged?.invoke(showEndConfirmDialog || showExtendDialog)
    }

    val haptic = LocalHapticFeedback.current
    val capsuleView = LocalView.current
    LaunchedEffect(showFiveMinWarning, timeLock) {
        if (showFiveMinWarning && timeLock) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            wakeUp()
            delay(5500L)
            showFiveMinWarning = false
        } else if (showFiveMinWarning && !timeLock) {
            showFiveMinWarning = false
        }
    }

    LaunchedEffect(countdownMode, timeLock) {
        if (countdownMode && timeLock) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(80)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            wakeUp()
                    } else if (countdownMode && !timeLock) {
            countdownMode = false
        }
    }

    LaunchedEffect(dailyRemainingSeconds.value) {
        if (countdownMode && dailyRemainingSeconds.value > 60L) {
            countdownMode = false
        }
    }

    // 入场：
    // - 纯时长锁：圆环种子（呼吸点）→ 气泡拉宽成迷你（不经展开横幅）
    // - 意图门：窄条 → 展开横幅停留 → 再收成迷你（点按/外侧可提前收）
    LaunchedEffect(Unit) {
        when {
            playEnterAnimation && !intentGateEarly -> {
                ringEntranceActive = true
                entranceCeremony = false
                entranceHolding = false
                islandExpanded = false
                islandW.snapTo(ringSeed.value)
                islandH.snapTo(ringSeed.value)
                islandR.snapTo(ringSeed.value / 2f)
                appearScale.snapTo(0.52f)
                appearAlpha.snapTo(0f)
                delay(16)
                // 先冒泡落点，再拉宽，避免 scale+拉宽同时抢戏
                launch { appearAlpha.animateTo(1f, tween(170, easing = FastOutSlowInEasing)) }
                launch { appearScale.animateTo(1f, BubblePopSpring) }
                delay(RING_ENTRANCE_HOLD_MS)
                morphBubbleToMini()
                ringEntranceActive = false
                skipNextUserCollapse = true
                userMorphEnabled = true
                onMiniSettled?.invoke()
            }
            playEnterAnimation -> {
                islandW.snapTo(EntranceSeedW.value)
                islandH.snapTo(EntranceSeedH.value)
                islandR.snapTo(EntranceSeedH.value / 2f)
                appearScale.snapTo(0.90f)
                appearAlpha.snapTo(0f)
                entranceCeremony = true
                entranceHolding = true
                entranceSkipRequested = false
                islandExpanded = true
                delay(16)
                launch { appearAlpha.animateTo(1f, tween(140, easing = FastOutSlowInEasing)) }
                launch { appearScale.animateTo(1f, IslandAppearSpring) }
                delay(70)
                morphIsland(true)
                onRegisterSkipEntrance?.invoke { entranceSkipRequested = true }
                val holdDeadline = System.nanoTime() + ENTRANCE_HOLD_MS * 1_000_000L
                while (!entranceSkipRequested && System.nanoTime() < holdDeadline) {
                    delay(40)
                }
                onRegisterSkipEntrance?.invoke(null)
                entranceHolding = false
                entranceCeremony = false
                islandExpanded = false
                morphIsland(false)
                skipNextUserCollapse = true
                userMorphEnabled = true
                onMiniSettled?.invoke()
            }
            softReveal -> {
                islandW.snapTo(collapsedW.value)
                islandH.snapTo(collapsedH.value)
                islandR.snapTo(collapsedR.value)
                appearScale.snapTo(1f)
                appearAlpha.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
                skipNextUserCollapse = true
                userMorphEnabled = true
                onMiniSettled?.invoke()
            }
            else -> {
                val startExpanded = expanded.value
                islandExpanded = startExpanded
                islandW.snapTo(if (startExpanded) expandedW.value else collapsedW.value)
                islandH.snapTo(if (startExpanded) ExpandedH.value else collapsedH.value)
                islandR.snapTo(if (startExpanded) ExpandedR.value else collapsedR.value)
                appearScale.snapTo(1f)
                appearAlpha.snapTo(1f)
                userMorphEnabled = true
                if (!startExpanded) onMiniSettled?.invoke()
            }
        }
    }

    // 用户再展开 / 收起（简化版）
    LaunchedEffect(expanded.value, userMorphEnabled) {
        if (!userMorphEnabled) return@LaunchedEffect
        if (expanded.value) {
            wakeUp()
                        islandExpanded = true
            morphIsland(true)
        } else {
            if (skipNextUserCollapse) {
                skipNextUserCollapse = false
                return@LaunchedEffect
            }
            islandExpanded = false
            morphIsland(false)
        }
    }

    // 收起档宽变化（如纯时长锁 ↔ 意图门 / 标准 ↔ 紧凑）：壳跟内容走
    LaunchedEffect(collapsedW, islandExpanded, userMorphEnabled, entranceHolding) {
        if (!userMorphEnabled || islandExpanded || entranceHolding) return@LaunchedEffect
        if (kotlin.math.abs(islandW.value - collapsedW.value) > 0.5f) {
            islandW.animateTo(collapsedW.value, IslandWidthSpring)
        }
    }
    LaunchedEffect(collapsedH, collapsedR, islandExpanded, userMorphEnabled, entranceHolding) {
        if (!userMorphEnabled || islandExpanded || entranceHolding) return@LaunchedEffect
        launch {
            if (kotlin.math.abs(islandH.value - collapsedH.value) > 0.5f) {
                islandH.animateTo(collapsedH.value, IslandHeightSpring)
            }
        }
        launch {
            if (kotlin.math.abs(islandR.value - collapsedR.value) > 0.5f) {
                islandR.animateTo(collapsedR.value, IslandCornerSpring)
            }
        }
    }

    // 预警/续时：壳高微调（续时高度只加在展开态，避免收起被撑高）
    LaunchedEffect(
        showExtendExtras,
        showCollapsedExtras,
        islandExpanded,
        userMorphEnabled,
        entranceHolding
    ) {
        if (!userMorphEnabled && !entranceHolding && !islandExpanded) return@LaunchedEffect
        val base = if (islandExpanded) ExpandedH.value else collapsedH.value
        val target = base + shellExtraHeight(islandExpanded)
        if (kotlin.math.abs(islandH.value - target) > 0.5f) {
            islandH.animateTo(target, IslandHeightSpring)
        }
    }


    val timeFont = if (themeConfig.capsuleUseMonoFont) FontFamily.Monospace else FontFamily.Default
    /** 平静进行中的强调色：跟品牌绿主题，不再硬编码系统绿 */
    val activeAccent = themeConfig.capsuleAccentColor

    val collapsedPurpose = purpose.value?.takeIf { it.isNotBlank() }
    val sessionTick = formatSeconds(sessionSeconds.value)
    val requiredIntent = collapsedPurpose ?: appName.value.ifBlank { "这一次" }
    val fullAppName = appName.value.trim()

    val dailyUsedSeconds = when {
        hasLimit -> (dailyLimitSeconds.value - dailyRemainingSeconds.value).coerceAtLeast(0L)
        else -> sessionSeconds.value
    }
    val usedTick = formatSeconds(dailyUsedSeconds)
    val limitTick = formatSeconds(dailyLimitSeconds.value)
    val showSecondsPref = showUsedSeconds.value
    val sessionLimitOn by remember { derivedStateOf { hasSessionLimit.value } }
    /**
     * 带意图且有本次会话限额：迷你态主信号用剩余倒计时（秒级），
     * 不用「已用/限额」结构。
     */
    val intentSessionCountdown = intentGate && sessionLimitOn && hasLimit && !isPaused.value
    val sessionRemainSec = dailyRemainingSeconds.value.coerceAtLeast(0L)
    val sessionCountdownUrgent = intentSessionCountdown &&
        sessionRemainSec in 1L..SESSION_COUNTDOWN_URGENT_SEC
    /** 剩余 1 分钟内（未到 30s）：倒计时变黄，不改变壳高/不加提示条 */
    val sessionCountdownWarn = intentSessionCountdown &&
        sessionRemainSec in (SESSION_COUNTDOWN_URGENT_SEC + 1)..SESSION_COUNTDOWN_WARN_SEC
    val canExtendNow = canOfferExtension.value &&
        intentSessionCountdown &&
        sessionRemainSec in 1L..SESSION_COUNTDOWN_WARN_SEC &&
        !isPaused.value &&
        !overLimit
    val showExtendOffer = canExtendNow && onExtendSession != null
    /** 时长锁有日限额且非意图倒计时：迷你态主信息用今日预算 */
    val timeLockBudgetPrimary = timeLock && hasLimit && !isPaused.value && !intentSessionCountdown
    val budgetUsedPart = if (showSecondsPref) {
        formatSeconds(dailyUsedSeconds)
    } else {
        "${dailyUsedSeconds.coerceAtLeast(0L) / 60L}"
    }
    val budgetLimitPart = "${dailyLimitSeconds.value.coerceAtLeast(0L) / 60L}分"

    val capsuleTimer = when {
        isPaused.value && showAwayCountdown -> formatSeconds(awayRemain)
        intentSessionCountdown && overLimit -> "超额"
        intentSessionCountdown -> formatSeconds(sessionRemainSec)
        timeLockBudgetPrimary && overLimit -> "超额 ${budgetUsedPart}/${budgetLimitPart}"
        timeLockBudgetPrimary -> "$budgetUsedPart/$budgetLimitPart"
        overLimit -> "+$sessionTick"
        else -> sessionTick
    }
    val capsuleLabel: String? = when {
        isPaused.value && showAwayCountdown -> "${formatSeconds(awayRemain)} 后结束"
        isPaused.value -> "两分钟后自动结束"
        // 意图会话：副位放意图（高亮）
        intentGate && !collapsedPurpose.isNullOrBlank() -> collapsedPurpose
        timeLockBudgetPrimary -> null
        !collapsedPurpose.isNullOrBlank() -> collapsedPurpose
        overLimit -> "超额中"
        else -> null
    }
    /** 意图文字高亮 */
    val labelIsAccentSignal = intentGate &&
        !collapsedPurpose.isNullOrBlank() &&
        capsuleLabel == collapsedPurpose
    val budgetSplitHighlight = timeLockBudgetPrimary && !overLimit && !shouldPulse && !isUrgent

    // 最后 30s：震动 + 变红并自动展开；期间不自动收起
    LaunchedEffect(sessionCountdownUrgent, userMorphEnabled) {
        if (!sessionCountdownUrgent || !userMorphEnabled) {
            if (!sessionCountdownUrgent) didUrgentExpand = false
            return@LaunchedEffect
        }
        if (!didUrgentExpand) {
            didUrgentExpand = true
            capsuleView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(70)
            capsuleView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            wakeUp()
            if (!expanded.value) onToggleExpand()
        }
    }


    val ritualStatus = when {
        isPaused.value && showAwayCountdown -> "暂离 · 即将自动结束"
        isPaused.value -> "暂离"
        overLimit -> "超额续记中"
        entranceCeremony && timeLock -> "时长锁已开启"
        entranceCeremony && intentGate -> "意图已确认"
        else -> null
    }
    /** 展开主行 */
    val expandedPrimary = when {
        isPaused.value && showAwayCountdown -> formatSeconds(awayRemain)
        isPaused.value -> "两分钟后自动结束"
        intentSessionCountdown && overLimit -> "本次已超额"
        intentSessionCountdown -> formatSeconds(sessionRemainSec)
        timeLock && hasLimit && overLimit -> "$usedTick  ·  已超额"
        timeLock && hasLimit -> "$usedTick  /  ${formatLimitCompact(dailyLimitSeconds.value)}"
        overLimit -> "已超额"
        intentOnly -> requiredIntent
        else -> "本次  $sessionTick"
    }
    /**
     * 日常展开顶栏身份：
     * - 有本次倒计时：App · 意图
     * - 其余：App 名
     */
    val dailyIdentityLine = when {
        isPaused.value -> fullAppName.ifBlank { "这一次" }
        intentSessionCountdown && !collapsedPurpose.isNullOrBlank() ->
            listOf(fullAppName, collapsedPurpose)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString(" · ")
                .ifBlank { fullAppName.ifBlank { "这一次" } }
        intentGate && !collapsedPurpose.isNullOrBlank() && !intentOnly && timeLock ->
            listOf(fullAppName, collapsedPurpose)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString(" · ")
                .ifBlank { fullAppName.ifBlank { "这一次" } }
        else -> fullAppName.ifBlank { "这一次" }
    }
    /** 无时长限制：顶栏右侧放本次已用，与「结束」对仗 */
    val dailyTrailingMeta: String? = when {
        isPaused.value -> null
        intentOnly -> sessionTick
        else -> null
    }
    /** 主行下方说明：倒计时态用「本次剩余」；预算态用本次时长 */
    val dailyCaption: String? = when {
        isPaused.value -> {
            if (!collapsedPurpose.isNullOrBlank()) collapsedPurpose
            else "点按胶囊回来 · 本次已用 $sessionTick"
        }
        intentSessionCountdown && overLimit -> null
        intentSessionCountdown -> "本次剩余"
        intentOnly -> null
        timeLock && hasLimit -> "本次 $sessionTick"
        !collapsedPurpose.isNullOrBlank() && expandedPrimary != requiredIntent -> collapsedPurpose
        else -> null
    }
    val expandedEyebrow = when {
        isPaused.value && showAwayCountdown -> "暂离倒计时"
        intentSessionCountdown && overLimit -> "本次会话"
        intentSessionCountdown && sessionCountdownUrgent -> "即将结束"
        intentSessionCountdown && showExtendOffer -> "还剩不到 1 分钟"
        intentSessionCountdown -> "本次剩余"
        timeLock && hasLimit && !intentSessionCountdown -> "今日已用"
        else -> null
    }
    /** 决策/仪式副行仍用此字段 */
    val expandedSecondary = when {
        isPaused.value -> dailyCaption.orEmpty()
        intentSessionCountdown -> collapsedPurpose.orEmpty()
        timeLock && hasLimit -> "本次 $sessionTick"
        intentOnly -> "本次 $sessionTick"
        !collapsedPurpose.isNullOrBlank() -> collapsedPurpose
        else -> "本次 $sessionTick"
    }
    val expandBannerMode = when {
        entranceCeremony -> ExpandBannerMode.Ritual
        showExtendOffer || sessionCountdownUrgent -> ExpandBannerMode.Decision
        else -> ExpandBannerMode.Daily
    }

    // 收起态：前台无 App 图标、用短名；非前台用图标做身份锚点
    val capsuleRingAccent = when {
        overLimit -> overLimitColor
        isPaused.value && awayUrgent -> Color(0xFFE0B85C)
        isPaused.value -> Color(0xFF8E8E93)
        isUrgent || shouldPulse -> iconColor
        else -> activeAccent
    }
    val capsuleTimerColor = when {
        overLimit -> overLimitColor
        isPaused.value && awayUrgent -> Color(0xFFE0B85C)
        isPaused.value -> shellTextPrimary.copy(alpha = 0.88f)
        sessionCountdownUrgent -> Color(0xFFEF5350)
        sessionCountdownWarn -> Color(0xFFE0B85C)
        shouldPulse -> effectiveIconColor
        isUrgent -> iconColor
        else -> shellTextPrimary
    }
    // 意图文字高亮；其余副文案中性
    val capsuleLabelColor = when {
        labelIsAccentSignal -> activeAccent
        else -> shellTextSecondary
    }
    val expandedPrimaryColor = when {
        overLimit -> overLimitColor
        isPaused.value -> shellTextPrimary
        sessionCountdownUrgent -> Color(0xFFEF5350)
        sessionCountdownWarn -> Color(0xFFE0B85C)
        shouldPulse -> effectiveIconColor
        isUrgent -> iconColor
        intentGate && !collapsedPurpose.isNullOrBlank() && intentOnly -> activeAccent
        timeLock && hasLimit && !intentSessionCountdown -> activeAccent
        else -> shellTextPrimary
    }
    val paused = isPaused.value
    /** 仅意图门会话提供手动「结束」；纯时长锁靠离开/限额收口，不必占胶囊操作位 */
    val showEndControl = intentGate
    val density = LocalDensity.current
    val stopHitSlopPx = with(density) { 6.dp.toPx() }

    // 壳几何：收起全圆角，展开用更小的横幅圆角
    val shellW = islandW.value.dp
    val shellH = islandH.value.dp
    val shellR = islandR.value.dp
    val shellShape = RoundedCornerShape(shellR)
    val islandFill = if (isDarkTheme) IslandBlack else IslandLight
    val hairline = when {
        shouldBreathe && isDarkTheme -> Color.White.copy(alpha = breathBorderAlpha)
        shouldBreathe -> Color.Black.copy(alpha = breathBorderAlpha * 0.75f)
        isDarkTheme -> Color.White.copy(alpha = 0.10f)
        else -> Color.Black.copy(alpha = 0.08f)
    }
    val breathDotColor = when {
        shouldPulse || isUrgent -> iconColor.copy(alpha = if (shouldPulse) pulseAlpha else 0.95f)
        shouldBreathe -> activeAccent.copy(alpha = breathAlpha)
        overLimit -> overLimitColor.copy(alpha = 0.9f)
        paused -> Color(0xFF8E8E93).copy(alpha = 0.7f)
        else -> activeAccent.copy(alpha = 0.55f)
    }

    val spanW = (expandedW.value - collapsedW.value).coerceAtLeast(1f)
    val spanH = (ExpandedH.value - collapsedH.value).coerceAtLeast(1f)
    val expandProgress = maxOf(
        ((islandW.value - collapsedW.value) / spanW).coerceIn(0f, 1f),
        ((islandH.value - collapsedH.value) / spanH).coerceIn(0f, 1f)
    )
    // 圆环 → 迷你拉宽进度（仅气泡入场）
    val bubbleExpandProgress = if (ringEntranceActive) {
        val span = (collapsedW.value - ringSeed.value).coerceAtLeast(1f)
        ((islandW.value - ringSeed.value) / span).coerceIn(0f, 1f)
    } else {
        1f
    }
    // 内容交叉：收起更早灭、展开更晚亮，减少中段重影
    val collapsedContentAlpha = 1f - smoothstep(0.05f, 0.30f, expandProgress)
    val expandedContentAlpha = smoothstep(0.48f, 0.90f, expandProgress)
    // 气泡入场：圆环铬层先在，迷你内容随后跟进
    val ringChromeAlpha = if (ringEntranceActive) {
        (1f - smoothstep(0.10f, 0.42f, bubbleExpandProgress)) * collapsedContentAlpha
    } else {
        0f
    }
    val miniCollapsedAlpha = if (ringEntranceActive) {
        smoothstep(0.18f, 0.58f, bubbleExpandProgress) * collapsedContentAlpha
    } else {
        collapsedContentAlpha
    }
    // 呼吸点约在壳左侧 12% 处；冒泡/拉宽以此为视觉锚
    val bubbleTransformOrigin = TransformOrigin(
        pivotFractionX = (mini.padH.value + mini.breathDot.value * 0.5f) /
            collapsedW.value.coerceAtLeast(1f),
        pivotFractionY = 0.5f
    )
    // 收起态仍由 Window 命中「结束」；展开态触摸已交给 Compose，不必再报 hitRect
    val reportCollapsedStopHit = showEndControl && !islandExpanded && miniCollapsedAlpha > 0.5f
    val reportExpandedStopHit = false

    LaunchedEffect(showEndControl, reportCollapsedStopHit) {
        if (!showEndControl || !reportCollapsedStopHit) {
            // 展开态或无结束键时清掉，避免残留命中区误触
            if (!reportCollapsedStopHit) onStopHitRectChanged?.invoke(null)
        }
    }

    // 贴状态栏时外层留白收紧；水平缓冲计入 WRAP，视觉边距由 ExpandedSideMargin 保证
    // 气泡入场：外层先占满迷你宽，岛从左侧（呼吸点）向右长出，停靠不漂
    Box(
        modifier = Modifier.padding(
            start = CapsuleOuterPadH,
            end = CapsuleOuterPadH,
            top = CapsuleOuterPadTop,
            bottom = CapsuleOuterPadBottom
        )
    ) {
        Box(
            modifier = if (ringEntranceActive) {
                Modifier
                    .width(collapsedW)
                    .height((collapsedH.value + shellExtraHeight(false)).dp)
            } else {
                Modifier
            }
        ) {
        Box(
            modifier = Modifier
                .then(if (ringEntranceActive) Modifier.align(Alignment.CenterStart) else Modifier)
                .graphicsLayer {
                    scaleX = appearScale.value
                    scaleY = appearScale.value
                    alpha = appearAlpha.value
                    if (ringEntranceActive) {
                        transformOrigin = if (bubbleExpandProgress < 0.02f) {
                            TransformOrigin.Center
                        } else {
                            bubbleTransformOrigin
                        }
                    }
                }
                .width(shellW)
                .height(shellH)
                .clip(shellShape)
                .background(islandFill)
                .border(0.5.dp, hairline, shellShape)
        ) {
            // 展开层（固定按展开尺寸排布，由壳裁切 + alpha 显现）
            if (expandedContentAlpha > 0.02f) {
                Box(
                    modifier = Modifier
                        .width(expandedW)
                        .height(ExpandedH + shellExtraHeight(true).dp)
                        .graphicsLayer { alpha = expandedContentAlpha }
                        .padding(horizontal = ExpandedInnerPadH, vertical = ExpandedInnerPadV)
                ) {
                    ExpandedBannerContent(
                        mode = expandBannerMode,
                        intentOn = intentGate,
                        timeOn = timeLock,
                        appName = fullAppName,
                        statusLine = ritualStatus,
                        eyebrow = expandedEyebrow,
                        dailyIdentity = dailyIdentityLine,
                        dailyTrailingMeta = dailyTrailingMeta,
                        dailyCaption = dailyCaption,
                        capabilityTint = when {
                            overLimit -> overLimitColor
                            isPaused.value -> Color(0xFF8E8E93)
                            else -> themeConfig.capsuleAccentColor
                        },
                        primaryLine = expandedPrimary,
                        secondaryLine = expandedSecondary,
                        statusColor = when {
                            overLimit -> overLimitColor.copy(alpha = 0.95f)
                            else -> themeConfig.capsuleAccentColor.copy(alpha = 0.92f)
                        },
                        primaryColor = expandedPrimaryColor,
                        secondaryColor = shellTextSecondary,
                        labelColor = shellTextSecondary,
                        appNameColor = shellTextSecondary,
                        timeFont = timeFont,
                        primaryEmphasized = intentSessionCountdown || intentOnly,
                        showReturn = isPaused.value && onReturnToApp != null,
                        onReturn = { onReturnToApp?.invoke() },
                        onStop = { openPrimaryEndAction() },
                        showStop = showEndControl,
                        showFiveMinWarning = showFiveMinWarning && !intentGate && timeLock && !isPaused.value && !overLimit,
                        showExtendOffer = showExtendOffer,
                        onRequestExtend = { showExtendDialog = true },
                        onStopHitRectInRoot = if (reportExpandedStopHit) {
                            { rect ->
                                if (rect == null) {
                                    onStopHitRectChanged?.invoke(null)
                                } else {
                                    onStopHitRectChanged?.invoke(
                                        android.graphics.RectF(
                                            rect.left - stopHitSlopPx,
                                            rect.top - stopHitSlopPx,
                                            rect.right + stopHitSlopPx,
                                            rect.bottom + stopHitSlopPx
                                        )
                                    )
                                }
                            }
                        } else null
                    )
                }
            }

            // 收起层：圆环入场 / 前台迷你 / 暂离
            // 能力图标仅展开态展示；暂离点按主体回来，「结束」由 Window 命中
            if (ringChromeAlpha > 0.02f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = ringChromeAlpha },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(mini.breathDot + 17.dp)
                            .border(1.6.dp, breathDotColor, CircleShape)
                    ) {
                        CapsuleBreathDot(color = breathDotColor, size = mini.breathDot)
                    }
                }
            }
            if (miniCollapsedAlpha > 0.02f) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = miniCollapsedAlpha }
                        .padding(horizontal = mini.padH),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (paused) {
                        CapsuleAppIcon(
                            packageName = appPackageName.value,
                            appName = appName.value,
                            accent = capsuleRingAccent,
                            pulse = false,
                            size = mini.pauseIcon
                        )
                        CapsuleHairlineDivider(isDarkTheme = isDarkTheme, height = mini.dividerH)
                        Text(
                            text = capsuleLabel.orEmpty(),
                            fontSize = mini.labelSp.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = capsuleTimerColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontFamily = if (showAwayCountdown) FontFamily.Monospace else timeFont,
                            letterSpacing = if (showAwayCountdown) 0.2.sp else 0.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (showEndControl) {
                            CapsuleStopControl(
                                accent = Color(0xFF8E8E93),
                                onClick = { openPrimaryEndAction() },
                                compact = true,
                                mini = mini,
                                onHitRectInRoot = if (reportCollapsedStopHit) {
                                    { rect ->
                                        if (rect == null) {
                                            onStopHitRectChanged?.invoke(null)
                                        } else {
                                            onStopHitRectChanged?.invoke(
                                                android.graphics.RectF(
                                                    rect.left - stopHitSlopPx,
                                                    rect.top - stopHitSlopPx,
                                                    rect.right + stopHitSlopPx,
                                                    rect.bottom + stopHitSlopPx
                                                )
                                            )
                                        }
                                    }
                                } else null
                            )
                        }
                    } else {
                        // 前台迷你：左侧信息簇 + 右侧结束（避免标准档宽壳内容左堆）
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CapsuleBreathDot(color = breathDotColor, size = mini.breathDot)
                            val showMiniAppName = capsuleLabel.isNullOrBlank() && fullAppName.isNotBlank()
                            if (showMiniAppName) {
                                Spacer(modifier = Modifier.width(mini.nameGap))
                                Text(
                                    text = fullAppName,
                                    fontSize = mini.nameSp.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = shellTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontFamily = timeFont,
                                    modifier = Modifier.widthIn(max = mini.nameMax)
                                )
                            }
                            if (!capsuleLabel.isNullOrBlank()) {
                                CapsuleHairlineDivider(isDarkTheme = isDarkTheme, height = mini.dividerH)
                                Text(
                                    text = capsuleLabel,
                                    fontSize = mini.labelSp.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = capsuleLabelColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontFamily = timeFont,
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .widthIn(
                                            max = if (showEndControl) mini.labelMaxWithStop
                                            else mini.labelMaxNoStop
                                        )
                                )
                            }
                            CapsuleHairlineDivider(isDarkTheme = isDarkTheme, height = mini.dividerH)
                            if (budgetSplitHighlight) {
                                Text(
                                    text = buildAnnotatedString {
                                        withStyle(
                                            SpanStyle(
                                                color = activeAccent,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        ) { append(budgetUsedPart) }
                                        withStyle(
                                            SpanStyle(
                                                color = shellTextSecondary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        ) { append("/$budgetLimitPart") }
                                    },
                                    fontSize = mini.timerSp.sp,
                                    maxLines = 1,
                                    fontFamily = if (showSecondsPref) FontFamily.Monospace else timeFont,
                                    letterSpacing = if (showSecondsPref) 0.15.sp else 0.sp
                                )
                            } else {
                                Text(
                                    text = capsuleTimer,
                                    fontSize = mini.timerSp.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = capsuleTimerColor,
                                    maxLines = 1,
                                    fontFamily = if (intentSessionCountdown || !timeLockBudgetPrimary) {
                                        FontFamily.Monospace
                                    } else timeFont,
                                    letterSpacing = 0.2.sp
                                )
                            }
                        }
                        if (showEndControl) {
                            CapsuleStopControl(
                                accent = if (overLimit) overLimitColor
                                else if (isDarkTheme) shellTextSecondary
                                else Color(0xFF3A3A3C),
                                onClick = { openPrimaryEndAction() },
                                compact = true,
                                mini = mini,
                                onHitRectInRoot = if (reportCollapsedStopHit) {
                                    { rect ->
                                        if (rect == null) {
                                            onStopHitRectChanged?.invoke(null)
                                        } else {
                                            onStopHitRectChanged?.invoke(
                                                android.graphics.RectF(
                                                    rect.left - stopHitSlopPx,
                                                    rect.top - stopHitSlopPx,
                                                    rect.right + stopHitSlopPx,
                                                    rect.bottom + stopHitSlopPx
                                                )
                                            )
                                        }
                                    }
                                } else null
                            )
                        }
                    }
                }

                if (showCollapsedExtras) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 12.dp, end = 12.dp, bottom = 4.dp)
                            .graphicsLayer { alpha = miniCollapsedAlpha }
                    ) {
                        if (showFiveMinWarning && timeLock && !isPaused.value && !overLimit) {
                            Text(
                                text = "还有 5 分钟，准备收尾了",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFE0B85C),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontFamily = timeFont
                            )
                        } else if (showCountdownHint) {
                            Text(
                                text = "时间快到了，准备结束",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFE0B85C),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontFamily = timeFont
                            )
                        }
                    }
                }
            }
        }
        }

        val shouldHoldPause = showExtendDialog ||
            (showEndConfirmDialog &&
                endConfirmReason == EndConfirmReason.Manual &&
                !isPaused.value)
        LaunchedEffect(shouldHoldPause) {
            if (!shouldHoldPause) return@LaunchedEffect
            onConfirmDialogOpen?.invoke()
            try {
                awaitCancellation()
            } finally {
                onConfirmDialogClose?.invoke()
            }
        }
        if (showEndConfirmDialog) {
            val purposeText = purpose.value?.trim().orEmpty()
            val enableCompare = endConfirmReason == EndConfirmReason.Manual &&
                purposeText.isNotEmpty()
            EndConfirmDialog(
                reason = endConfirmReason,
                appName = appName.value,
                purpose = purpose.value,
                sessionSeconds = sessionSeconds.value,
                bgColor = bgColor,
                iconColor = effectiveIconColor,
                subColor = subColor,
                useMonoFont = themeConfig.capsuleUseMonoFont,
                stopButtonColor = themeConfig.capsuleStopButtonColor,
                enableCompare = enableCompare,
                onConfirm = { note, level, openToAnchor ->
                    showEndConfirmDialog = false
                    onEndSession(note, level, openToAnchor)
                },
                onDismiss = { showEndConfirmDialog = false }
            )
        }
        if (showExtendDialog && onExtendSession != null) {
            SessionExtendDialog(
                themeConfig = themeConfig,
                maxMinutes = com.life.mindfulnessapp.domain.model.SessionLimitPolicy.MAX_SESSION_MINUTES,
                onConfirm = { minutes ->
                    showExtendDialog = false
                    onExtendSession.invoke(minutes)
                },
                onDismiss = { showExtendDialog = false }
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  展开横幅内容（三套模板）
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ExpandedBannerContent(
    mode: ExpandBannerMode,
    intentOn: Boolean,
    timeOn: Boolean,
    appName: String,
    statusLine: String?,
    eyebrow: String?,
    dailyIdentity: String,
    dailyTrailingMeta: String?,
    dailyCaption: String?,
    capabilityTint: Color,
    primaryLine: String,
    secondaryLine: String,
    statusColor: Color,
    primaryColor: Color,
    secondaryColor: Color,
    labelColor: Color,
    appNameColor: Color,
    timeFont: FontFamily,
    primaryEmphasized: Boolean,
    showReturn: Boolean,
    onReturn: () -> Unit,
    onStop: () -> Unit,
    showStop: Boolean = true,
    showFiveMinWarning: Boolean,
    showExtendOffer: Boolean = false,
    onRequestExtend: (() -> Unit)? = null,
    onStopHitRectInRoot: ((androidx.compose.ui.geometry.Rect?) -> Unit)? = null
) {
    when (mode) {
        ExpandBannerMode.Ritual -> ExpandedRitualBanner(
            intentOn = intentOn,
            timeOn = timeOn,
            appName = appName,
            statusLine = statusLine,
            capabilityTint = capabilityTint,
            primaryLine = primaryLine,
            secondaryLine = secondaryLine,
            statusColor = statusColor,
            primaryColor = primaryColor,
            secondaryColor = secondaryColor,
            labelColor = labelColor,
            appNameColor = appNameColor,
            timeFont = timeFont,
            showStop = showStop,
            onStop = onStop,
            onStopHitRectInRoot = onStopHitRectInRoot
        )
        ExpandBannerMode.Decision -> ExpandedDecisionBanner(
            appName = appName,
            eyebrow = eyebrow,
            primaryLine = primaryLine,
            secondaryLine = secondaryLine,
            primaryColor = primaryColor,
            secondaryColor = secondaryColor,
            appNameColor = appNameColor,
            timeFont = timeFont,
            showReturn = showReturn,
            onReturn = onReturn,
            showStop = showStop,
            onStop = onStop,
            showExtendOffer = showExtendOffer,
            onRequestExtend = onRequestExtend,
            onStopHitRectInRoot = onStopHitRectInRoot
        )
        ExpandBannerMode.Daily -> ExpandedDailyBanner(
            identityLine = dailyIdentity,
            trailingMeta = dailyTrailingMeta,
            primaryLine = primaryLine,
            captionLine = dailyCaption,
            primaryColor = primaryColor,
            secondaryColor = secondaryColor,
            identityColor = appNameColor,
            timeFont = timeFont,
            primaryEmphasized = primaryEmphasized,
            showReturn = showReturn,
            onReturn = onReturn,
            showStop = showStop,
            onStop = onStop,
            showFiveMinWarning = showFiveMinWarning,
            onStopHitRectInRoot = onStopHitRectInRoot
        )
    }
}

@Composable
private fun ExpandedBannerActions(
    showReturn: Boolean,
    onReturn: () -> Unit,
    showStop: Boolean,
    onStop: () -> Unit,
    stopAccent: Color,
    timeFont: FontFamily,
    onStopHitRectInRoot: ((androidx.compose.ui.geometry.Rect?) -> Unit)?
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (showReturn) {
            val interaction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier.clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onReturn
                )
            ) {
                Text(
                    text = "回来",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF81C784),
                    maxLines = 1,
                    fontFamily = timeFont
                )
            }
        }
        if (showStop) {
            CapsuleStopControl(
                accent = if (showReturn) Color(0xFF8E8E93) else stopAccent,
                onClick = onStop,
                onHitRectInRoot = onStopHitRectInRoot
            )
        }
    }
}

/** 入场仪式：能力标签 + 状态确认，短暂停留后收起 */
@Composable
private fun ExpandedRitualBanner(
    intentOn: Boolean,
    timeOn: Boolean,
    appName: String,
    statusLine: String?,
    capabilityTint: Color,
    primaryLine: String,
    secondaryLine: String,
    statusColor: Color,
    primaryColor: Color,
    secondaryColor: Color,
    labelColor: Color,
    appNameColor: Color,
    timeFont: FontFamily,
    showStop: Boolean,
    onStop: () -> Unit,
    onStopHitRectInRoot: ((androidx.compose.ui.geometry.Rect?) -> Unit)?
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CapsuleCapabilityLabels(
                    intentOn = intentOn,
                    timeOn = timeOn,
                    tint = capabilityTint,
                    labelColor = labelColor,
                    timeFont = timeFont
                )
                if (!statusLine.isNullOrBlank()) {
                    Text(
                        text = statusLine,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = timeFont,
                        letterSpacing = 0.3.sp
                    )
                } else if (appName.isNotBlank()) {
                    Text(
                        text = appName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = appNameColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = timeFont
                    )
                }
            }
            ExpandedBannerActions(
                showReturn = false,
                onReturn = {},
                showStop = showStop,
                onStop = onStop,
                stopAccent = secondaryColor,
                timeFont = timeFont,
                onStopHitRectInRoot = onStopHitRectInRoot
            )
        }
        Text(
            text = primaryLine,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = primaryColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = timeFont
        )
        Text(
            text = secondaryLine,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            color = secondaryColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = timeFont
        )
    }
}

/**
 * 日常展开（左右对仗）：
 * - 有本次时限：`App · 意图` | 结束 → 大倒计时 →「本次剩余」
 * - 无时限意图：`App` | 本次时长 + 结束 → 大意图
 * - 纯预算：`App` | 结束 → 已用/限额 → 本次
 */
@Composable
private fun ExpandedDailyBanner(
    identityLine: String,
    trailingMeta: String?,
    primaryLine: String,
    captionLine: String?,
    primaryColor: Color,
    secondaryColor: Color,
    identityColor: Color,
    timeFont: FontFamily,
    primaryEmphasized: Boolean,
    showReturn: Boolean,
    onReturn: () -> Unit,
    showStop: Boolean,
    onStop: () -> Unit,
    showFiveMinWarning: Boolean,
    onStopHitRectInRoot: ((androidx.compose.ui.geometry.Rect?) -> Unit)?
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = identityLine.ifBlank { "这一次" },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = identityColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = timeFont,
                modifier = Modifier.weight(1f)
            )
            if (!trailingMeta.isNullOrBlank()) {
                Text(
                    text = trailingMeta,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = secondaryColor.copy(alpha = 0.92f),
                    maxLines = 1,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.2.sp
                )
            }
            ExpandedBannerActions(
                showReturn = showReturn,
                onReturn = onReturn,
                showStop = showStop,
                onStop = onStop,
                stopAccent = secondaryColor,
                timeFont = timeFont,
                onStopHitRectInRoot = onStopHitRectInRoot
            )
        }
        Text(
            text = primaryLine,
            fontSize = if (primaryEmphasized) 24.sp else 20.sp,
            fontWeight = FontWeight.Bold,
            color = primaryColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = if (primaryEmphasized && trailingMeta == null) {
                FontFamily.Monospace
            } else {
                timeFont
            },
            letterSpacing = if (primaryEmphasized && trailingMeta == null) 0.4.sp else 0.sp,
            modifier = Modifier.fillMaxWidth()
        )
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (!captionLine.isNullOrBlank()) {
                Text(
                    text = captionLine,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = secondaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = timeFont,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (showFiveMinWarning) {
                Text(
                    text = "还有 5 分钟，准备收尾了",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE0B85C),
                    maxLines = 1,
                    fontFamily = timeFont
                )
            }
        }
    }
}

/** 紧急 / 续时决策：大倒计时 + 意图 + 可选续时 CTA */
@Composable
private fun ExpandedDecisionBanner(
    appName: String,
    eyebrow: String?,
    primaryLine: String,
    secondaryLine: String,
    primaryColor: Color,
    secondaryColor: Color,
    appNameColor: Color,
    timeFont: FontFamily,
    showReturn: Boolean,
    onReturn: () -> Unit,
    showStop: Boolean,
    onStop: () -> Unit,
    showExtendOffer: Boolean,
    onRequestExtend: (() -> Unit)?,
    onStopHitRectInRoot: ((androidx.compose.ui.geometry.Rect?) -> Unit)?
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = eyebrow?.takeIf { it.isNotBlank() } ?: appName.ifBlank { "这一次" },
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (eyebrow != null) primaryColor.copy(alpha = 0.9f) else appNameColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = timeFont,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(end = 10.dp)
            )
            ExpandedBannerActions(
                showReturn = showReturn,
                onReturn = onReturn,
                showStop = showStop,
                onStop = onStop,
                stopAccent = secondaryColor,
                timeFont = timeFont,
                onStopHitRectInRoot = onStopHitRectInRoot
            )
        }
        Text(
            text = primaryLine,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = primaryColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.4.sp
        )
        if (showExtendOffer && onRequestExtend != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (secondaryLine.isNotBlank()) {
                    Text(
                        text = secondaryLine,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = secondaryColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = timeFont,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFE0B85C).copy(alpha = 0.20f))
                        .border(0.5.dp, Color(0xFFE0B85C).copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        .clickable(onClick = onRequestExtend)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "续一会儿",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE0B85C),
                        fontFamily = timeFont
                    )
                }
            }
        } else if (secondaryLine.isNotBlank()) {
            Text(
                text = secondaryLine,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = secondaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = timeFont
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  分段胶囊零件：呼吸点 / App 图标 / 发丝分割 / 结束
// ════════════════════════════════════════════════════════════════════════════

/** 迷你态存活指示：极弱呼吸点（替代能力图标占位） */
@Composable
private fun CapsuleBreathDot(color: Color, size: Dp = 5.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}

/** 展开态：能力图标 + 文案（仅入场仪式使用） */
@Composable
private fun CapsuleCapabilityLabels(
    intentOn: Boolean,
    timeOn: Boolean,
    tint: Color,
    labelColor: Color,
    timeFont: FontFamily
) {
    if (!intentOn && !timeOn) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (intentOn) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CapabilityMark(
                    kind = CapabilityKind.IntentGate,
                    form = CapabilityForm.Standard,
                    tint = tint,
                    size = 14.dp
                )
                Text(
                    text = MonitorCapability.IntentGateLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = labelColor,
                    maxLines = 1,
                    fontFamily = timeFont
                )
            }
        }
        if (timeOn) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CapabilityMark(
                    kind = CapabilityKind.TimeLock,
                    form = CapabilityForm.Standard,
                    tint = tint,
                    size = 14.dp
                )
                Text(
                    text = MonitorCapability.TimeLockLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = labelColor,
                    maxLines = 1,
                    fontFamily = timeFont
                )
            }
        }
    }
}

@Composable
private fun CapsuleHairlineDivider(
    isDarkTheme: Boolean = true,
    height: Dp = 11.dp
) {
    Box(
        modifier = Modifier
            .padding(horizontal = 5.dp)
            .width(1.dp)
            .height(height)
            .background(
                if (isDarkTheme) Color.White.copy(alpha = 0.14f)
                else Color.Black.copy(alpha = 0.20f)
            )
    )
}

@Composable
private fun CapsuleAppIcon(
    packageName: String,
    appName: String,
    accent: Color,
    pulse: Boolean,
    size: Dp = 22.dp
) {
    val context = LocalContext.current
    val appIcon = remember(packageName) {
        if (packageName.isNotEmpty()) {
            try {
                context.packageManager.getApplicationIcon(packageName)
            } catch (_: Exception) {
                null
            }
        } else null
    }
    val iconBitmap = remember(appIcon) {
        appIcon?.toBitmap(72, 72)?.asImageBitmap()
    }
    val ringAlpha = if (pulse) 0.70f else 0.92f
    val ringWidth = if (size <= 22.dp) 1.1.dp else 1.3.dp
    val innerPad = if (size <= 22.dp) 2.dp else 2.5.dp

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .border(ringWidth, accent.copy(alpha = ringAlpha), CircleShape)
                .padding(innerPad)
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = appName,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f))
                ) {
                    Text(
                        text = appName.take(1).ifBlank { "A" },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.90f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CapsuleStopControl(
    accent: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    compact: Boolean = false,
    mini: CapsuleMiniMetrics? = null,
    onHitRectInRoot: ((androidx.compose.ui.geometry.Rect?) -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(if (compact) 8.dp else 10.dp)
    val startPad = when {
        compact && mini != null -> mini.stopStartPad
        compact -> 5.dp
        else -> 6.dp
    }
    val padH = when {
        compact && mini != null -> mini.stopPadH
        compact -> 8.dp
        else -> 9.dp
    }
    val padV = when {
        compact && mini != null -> mini.stopPadV
        compact -> 4.dp
        else -> 5.dp
    }
    val labelSp = when {
        compact && mini != null -> mini.stopSp.sp
        compact -> 10.sp
        else -> 11.sp
    }
    var lastBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    // 回调从 null → 非 null 时补报一次，避免 onGloballyPositioned 不重入导致命中区一直为空
    LaunchedEffect(onHitRectInRoot) {
        if (onHitRectInRoot == null) return@LaunchedEffect
        lastBounds?.let { onHitRectInRoot.invoke(it) }
    }
    DisposableEffect(onHitRectInRoot) {
        onDispose { onHitRectInRoot?.invoke(null) }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(start = startPad)
            .onGloballyPositioned { coords ->
                val bounds = coords.boundsInRoot()
                lastBounds = bounds
                onHitRectInRoot?.invoke(bounds)
            }
            .clip(shape)
            .background(accent.copy(alpha = 0.12f))
            .border(0.5.dp, accent.copy(alpha = 0.28f), shape)
            // padding 在 clickable 之前：命中区含内边距，与 Window 层 stopHitRect 一致
            .padding(horizontal = padH, vertical = padV)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
    ) {
        Text(
            text = "结束",
            fontSize = labelSp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) accent.copy(alpha = 0.92f) else accent.copy(alpha = 0.40f),
            maxLines = 1
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  收起态停止块已并入 CapsuleStopControl
// ════════════════════════════════════════════════════════════════════════════

fun formatSeconds(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%02d:%02d".format(m, s)
}

/** 限额侧短写：优先「小时/分」，避免展开主行被 1:00:00 挤爆 */
private fun formatLimitCompact(seconds: Long): String {
    val h = seconds.coerceAtLeast(0L) / 3600
    val m = (seconds.coerceAtLeast(0L) % 3600) / 60
    return when {
        h > 0 && m > 0 -> "${h}小时${m}分"
        h > 0 -> "${h}小时"
        else -> "${m}分"
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  结束确认弹窗（有意图时：确认结束 + 必选对照 + 可选备注）
// ════════════════════════════════════════════════════════════════════════════

private enum class EndConfirmReason {
    /** 用户主动点胶囊「结束」 */
    Manual,
    /** 后台超时，询问是否结束计时 */
    BackgroundTimeout
}

@Composable
private fun EndConfirmDialog(
    reason: EndConfirmReason,
    appName: String,
    purpose: String?,
    sessionSeconds: Long,
    bgColor: Color,
    iconColor: Color,
    subColor: Color,
    useMonoFont: Boolean = false,
    stopButtonColor: Color = Color(0xFF27AE60),
    enableCompare: Boolean = false,
    onConfirm: (note: String?, mindfulnessLevel: Int?, openToAnchor: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val scale = remember { Animatable(0.8f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch {
            scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh))
        }
        alpha.animateTo(1f, tween(120, easing = FastOutSlowInEasing))
    }

    val font = if (useMonoFont) FontFamily.Monospace else FontFamily.Default
    val isBackgroundTimeout = reason == EndConfirmReason.BackgroundTimeout
    var selectedLevel by remember { mutableStateOf<Int?>(null) }
    var noteText by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current
    val canConfirm = !enableCompare || MindfulnessLevel.isValid(selectedLevel)

    val title = when {
        useMonoFont && isBackgroundTimeout -> "STILL AWAY?"
        useMonoFont -> "END_SESSION?"
        isBackgroundTimeout -> "还要继续计时吗？"
        enableCompare -> "结束这次使用"
        else -> "结束这次使用？"
    }

    val subtitle = when {
        isBackgroundTimeout -> "「$appName」已在后台一段时间"
        else -> appName
    }

    val dismissLabel = when {
        useMonoFont && isBackgroundTimeout -> "KEEP"
        useMonoFont -> "CANCEL"
        isBackgroundTimeout -> "继续计时"
        else -> "继续用"
    }

    val confirmLabel = when {
        useMonoFont && isBackgroundTimeout -> "END"
        useMonoFont -> "CONFIRM"
        isBackgroundTimeout -> "结束计时"
        else -> "结束"
    }

    val sessionLabel = buildString {
        val h = sessionSeconds / 3600
        val m = (sessionSeconds % 3600) / 60
        val s = sessionSeconds % 60
        if (useMonoFont) {
            append("SESSION: ")
            if (h > 0) append("${h}h${m}m") else if (m > 0) append("${m}m${s}s") else append("${s}s")
        } else {
            append("本次 ")
            when {
                h > 0 && m > 0 -> append("${h}小时${m}分")
                h > 0 -> append("${h}小时")
                m > 0 && s > 0 -> append("${m}分${s}秒")
                m > 0 -> append("${m}分钟")
                else -> append("${s}秒")
            }
        }
    }

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale.value; scaleY = scale.value; this.alpha = alpha.value
            }
            .shadow(16.dp, shape = RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .width(if (enableCompare) 320.dp else 300.dp)
            .padding(horizontal = 22.dp, vertical = 22.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontFamily = font
                )
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = subColor.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = font,
                    textAlign = TextAlign.Center
                )
            }

            val purposeText = purpose?.trim().orEmpty()
            if (purposeText.isNotEmpty() && !isBackgroundTimeout) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconColor.copy(alpha = 0.12f))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (useMonoFont) "INTENT" else "这一次的意图",
                            fontSize = 11.sp,
                            color = iconColor.copy(alpha = 0.75f),
                            fontFamily = font,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = purposeText,
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.92f),
                            fontFamily = font,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            if (sessionSeconds >= 10L) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = sessionLabel,
                        fontSize = 13.sp,
                        color = subColor.copy(alpha = 0.9f),
                        fontFamily = font,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (enableCompare) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (useMonoFont) "COMPARE" else MindfulnessLevel.COMPARE_PROMPT,
                        fontSize = 11.sp,
                        color = subColor.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium,
                        fontFamily = font
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            MindfulnessLevel.ALIGNED to Color(0xFF27AE60),
                            MindfulnessLevel.SLIGHT to Color(0xFFD4A017),
                            MindfulnessLevel.LARGE to Color(0xFFC47A6A)
                        ).forEach { (level, accent) ->
                            val selected = selectedLevel == level
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (selected) accent.copy(alpha = 0.22f)
                                        else Color.White.copy(alpha = 0.06f)
                                    )
                                    .border(
                                        width = if (selected) 1.5.dp else 1.dp,
                                        color = if (selected) accent.copy(alpha = 0.85f)
                                        else Color.White.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedLevel = level
                                    }
                                    .padding(vertical = 11.dp)
                            ) {
                                Text(
                                    text = MindfulnessLevel.tierLabel(level),
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (selected) accent else subColor.copy(alpha = 0.85f),
                                    fontFamily = font,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (useMonoFont) {
                            "NOTE (OPTIONAL)"
                        } else {
                            MindfulnessLevel.noteSectionLabel(selectedLevel)
                        },
                        fontSize = 11.sp,
                        color = subColor.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium,
                        fontFamily = font
                    )
                    BasicTextField(
                        value = noteText,
                        onValueChange = { if (it.length <= 100) noteText = it },
                        textStyle = TextStyle(
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp,
                            fontFamily = font
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        decorationBox = { inner ->
                            Box {
                                if (noteText.isEmpty()) {
                                    Text(
                                        text = if (useMonoFont) {
                                            "OPTIONAL NOTE"
                                        } else {
                                            MindfulnessLevel.notePlaceholder(selectedLevel)
                                        },
                                        fontSize = 14.sp,
                                        color = subColor.copy(alpha = 0.45f),
                                        fontFamily = font
                                    )
                                }
                                inner()
                            }
                        }
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable { onDismiss() }
                        .padding(vertical = 12.dp)
                ) {
                    Text(
                        text = dismissLabel,
                        fontSize = 15.sp,
                        color = subColor.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Medium,
                        fontFamily = font
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (canConfirm) stopButtonColor.copy(alpha = 0.85f)
                            else Color.White.copy(alpha = 0.1f)
                        )
                        .clickable(enabled = canConfirm) {
                            onConfirm(
                                noteText.trim().ifEmpty { null },
                                selectedLevel?.takeIf { MindfulnessLevel.isValid(it) },
                                false
                            )
                        }
                        .padding(vertical = 12.dp)
                ) {
                    Text(
                        text = confirmLabel,
                        fontSize = 15.sp,
                        color = if (canConfirm) Color.White else subColor.copy(alpha = 0.4f),
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = font
                    )
                }
            }
            if (enableCompare) {
                Text(
                    text = "结束并去心锚",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (canConfirm) iconColor.copy(alpha = 0.85f)
                    else subColor.copy(alpha = 0.35f),
                    fontFamily = font,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable(enabled = canConfirm) {
                            onConfirm(
                                noteText.trim().ifEmpty { null },
                                selectedLevel?.takeIf { MindfulnessLevel.isValid(it) },
                                true
                            )
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/**
 * 胶囊临近结束时的续时手输对话框（与进门分钟键盘同形态）。
 */
@Composable
private fun SessionExtendDialog(
    themeConfig: InterceptThemeConfig,
    maxMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var digits by remember { mutableStateOf("") }
    val parsed = digits.toIntOrNull()
    val canConfirm = parsed != null &&
        parsed in com.life.mindfulnessapp.domain.model.SessionLimitPolicy.MIN_SESSION_MINUTES..maxMinutes
    val scale = remember { Animatable(0.8f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch {
            scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh))
        }
        alpha.animateTo(1f, tween(120, easing = FastOutSlowInEasing))
    }

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
            }
            .shadow(16.dp, shape = RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(themeConfig.bgColor)
            .width(300.dp)
            .padding(horizontal = 20.dp, vertical = 22.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "再续一会儿",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = themeConfig.textPrimary
            )
            Text(
                text = "手输分钟，本次只能续一次",
                fontSize = 13.sp,
                color = themeConfig.textSecondary,
                textAlign = TextAlign.Center
            )
            SessionMinutesKeypad(
                themeConfig = themeConfig,
                digits = digits,
                maxMinutes = maxMinutes,
                enabled = true,
                onDigitsChange = { digits = it },
                onDigitHaptic = {}
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 12.dp)
                ) {
                    Text(
                        text = "取消",
                        fontSize = 15.sp,
                        color = themeConfig.textSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (canConfirm) themeConfig.accentColor.copy(alpha = 0.9f)
                            else themeConfig.dividerColor
                        )
                        .clickable(enabled = canConfirm) {
                            parsed?.let(onConfirm)
                        }
                        .padding(vertical = 12.dp)
                ) {
                    Text(
                        text = if (canConfirm) "续 $parsed 分钟" else "输入分钟",
                        fontSize = 15.sp,
                        color = if (canConfirm) themeConfig.accentForeground else themeConfig.textTertiary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
