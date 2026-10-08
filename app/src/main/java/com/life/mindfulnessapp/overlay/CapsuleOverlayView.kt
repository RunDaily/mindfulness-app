package com.life.mindfulnessapp.overlay

import android.os.SystemClock
import androidx.annotation.Keep
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.domain.model.CapsuleCompanionPrefs
import com.life.mindfulnessapp.domain.model.CapsuleMarkStyle
import com.life.mindfulnessapp.domain.model.CompanionBarForm
import com.life.mindfulnessapp.domain.model.CompanionPath
import com.life.mindfulnessapp.domain.model.CompanionScene
import com.life.mindfulnessapp.domain.model.ComparePolicy
import com.life.mindfulnessapp.domain.model.SessionAwarenessCopy
import com.life.mindfulnessapp.domain.model.SessionAwarenessMode
import com.life.mindfulnessapp.domain.model.ThemePack
import com.life.mindfulnessapp.service.SessionManager
import com.life.mindfulnessapp.util.BackgroundMediaPauser
import com.life.mindfulnessapp.ui.theme.MistBg
import com.life.mindfulnessapp.ui.theme.MistCardBg
import kotlinx.coroutines.awaitCancellation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

/** 岛壳自适应高对比轮廓线宽 */
private val ShellContrastBorderWidth = 1.dp

/** 岛内发丝分割槽宽（左右 pad + 线） */
private val MiniDividerSlot = 7.dp

/** 展开态无操作时自动收起 */
// ── 灵动岛尺寸：收起分段胶囊 / 展开横幅 ─────────────────────────────────────
/**
 * 迷你态尺寸档：只影响收起壳与字号，不改信息结构。
 * - [MiniStandard]：更舒展（设置默认）
 * - [MiniCompact]：当前偏省空间的一档
 * 壳宽按内容测量，再夹在 [wMin]…[wMax]。
 */
private data class CapsuleMiniMetrics(
    val height: Dp,
    val corner: Dp,
    /** 暂离：略收圆角，与前台全圆角迷你区分 */
    val pauseCorner: Dp,
    val wMin: Dp,
    val wMax: Dp,
    /** 已用显示到秒时放宽最大宽，避免 mm:ss/限额 被裁切 */
    val wMaxWithSeconds: Dp,
    val padH: Dp,
    val breathDot: Dp,
    val pauseIcon: Dp,
    val backArrow: Dp,
    val pauseLeadGap: Dp,
    val pauseIconGap: Dp,
    val nameSp: Float,
    val labelSp: Float,
    val timerSp: Float,
    val nameMax: Dp,
    val labelMax: Dp,
    /** 纯时长锁迷你：锁标边长 */
    val lockSize: Dp,
    val dividerH: Dp,
    val nameGap: Dp
)

/** 紧凑：现网偏省空间的迷你岛 */
private val MiniCompact = CapsuleMiniMetrics(
    height = 34.dp,
    corner = 17.dp,
    pauseCorner = 12.dp,
    wMin = 96.dp,
    wMax = 200.dp,
    wMaxWithSeconds = 252.dp,
    padH = 7.dp,
    breathDot = 5.dp,
    pauseIcon = 22.dp,
    backArrow = 16.dp,
    pauseLeadGap = 2.dp,
    pauseIconGap = 6.dp,
    nameSp = 11f,
    labelSp = 11f,
    timerSp = 12f,
    nameMax = 36.dp,
    labelMax = 96.dp,
    lockSize = 13.dp,
    dividerH = 11.dp,
    nameGap = 4.dp
)

/** 标准：对照更舒展的形态（默认） */
private val MiniStandard = CapsuleMiniMetrics(
    height = 38.dp,
    corner = 19.dp,
    pauseCorner = 14.dp,
    wMin = 108.dp,
    wMax = 220.dp,
    wMaxWithSeconds = 272.dp,
    padH = 8.dp,
    breathDot = 6.dp,
    pauseIcon = 24.dp,
    backArrow = 18.dp,
    pauseLeadGap = 2.dp,
    pauseIconGap = 6.dp,
    nameSp = 12f,
    labelSp = 12f,
    timerSp = 13f,
    nameMax = 40.dp,
    labelMax = 112.dp,
    lockSize = 14.dp,
    dividerH = 13.dp,
    nameGap = 5.dp
)

/**
 * 展开态：近全宽、两侧留边、水平居中（对齐 iOS Dynamic Island expanded）。
 * 外层水平 [CapsuleOuterPadH] 含在 WRAP 宽内；[ExpandedSideMargin] 即屏边到黑壳的视觉边距。
 */
/** 固定外扩：进场光晕在此范围内画，禁止随 blooming 改 pad（改 pad 会顶窗位跳动） */
internal val CapsuleOuterPadH = 20.dp
internal val CapsuleOuterPadTop = 14.dp
internal val CapsuleOuterPadBottom = 16.dp
/** 光晕画布：落在固定 pad 内，不驱使 WRAP 窗口改尺寸 */
private val BloomHaloCanvas = 72.dp
private val BloomCoreSize = 22.dp
private val BloomEase = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)
private val BloomHaloEase = CubicBezierEasing(0.16f, 0.84f, 0.3f, 1f)
internal val CompanionCollapsedWidthMin = 118.dp
internal val CompanionCollapsedWidthMax = 280.dp
private val CompanionCollapsedWidthSlack = 10.dp
private val ExpandedSideMargin = 10.dp
/** 展开高度：日常 / 决策 / 觉察 共用一壳 */
private val ExpandedH = 108.dp
/** 意图主行可换行时的额外高度（避免被裁成点点点） */
private val IntentWrapExtraH = 24.dp
/** 展开圆角：略小于半高，保持「岛」感 */
private val ExpandedR = 26.dp
private val ExpandedInnerPadH = 16.dp
private val ExpandedInnerPadV = 12.dp
/** 展开态意图正文：纯白 / 纯黑（随岛面深浅） */
private fun intentInkColor(isDarkTheme: Boolean): Color =
    if (isDarkTheme) Color.White else Color.Black

/**
 * 展开横幅模板（同一岛壳）：
 * Daily 已废弃（不再展示信息大条）· Decision 紧急/续时 · Check 进行中对照 · SoftExit 未专注轻确认
 */
private enum class ExpandBannerMode {
    Daily,
    Decision,
    Check,
    SoftExit
}

/** 按屏宽计算展开壳宽：屏宽 − 两侧视觉边距 */
private fun expandedIslandWidth(screenWidth: Dp): Dp =
    (screenWidth - ExpandedSideMargin * 2).coerceAtLeast(MiniStandard.wMax + 40.dp)

private fun measureCapsuleTextWidth(
    measurer: TextMeasurer,
    density: Density,
    text: String,
    fontSizeSp: Float,
    fontWeight: FontWeight,
    fontFamily: FontFamily,
    letterSpacingSp: Float = 0f
): Dp {
    if (text.isEmpty()) return 0.dp
    val px = measurer.measure(
        text = text,
        style = TextStyle(
            fontSize = fontSizeSp.sp,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacing = letterSpacingSp.sp
        ),
        maxLines = 1,
        softWrap = false
    ).size.width
    return with(density) { px.toDp() }
}

/**
 * 迷你壳宽：按当前文案/图标槽位估宽，再夹在档位最小/最大之间。
 * 迷你态不放结束入口，估宽不含操作键。
 */
private fun estimateMiniCollapsedWidth(
    mini: CapsuleMiniMetrics,
    paused: Boolean,
    labelText: String?,
    timerText: String,
    appName: String,
    showAppName: Boolean,
    showLockMark: Boolean,
    measurer: TextMeasurer,
    density: Density,
    timeFont: FontFamily,
    timerMono: Boolean,
    wideTimer: Boolean = false
): Dp {
    var w = mini.padH * 2
    if (paused) {
        w += mini.backArrow
        w += mini.pauseLeadGap
        w += mini.pauseIcon
        w += mini.pauseIconGap
        w += measureCapsuleTextWidth(
            measurer, density, labelText.orEmpty(),
            mini.labelSp, FontWeight.SemiBold, timeFont
        ).coerceAtMost(mini.labelMax)
        // 离开倒计时跟秒：意图保留，右侧补剩余时间
        if (timerText.isNotBlank()) {
            w += MiniDividerSlot
            w += measureCapsuleTextWidth(
                measurer, density, timerText,
                mini.timerSp, FontWeight.SemiBold,
                if (timerMono) FontFamily.Monospace else timeFont,
                letterSpacingSp = if (timerMono) 0.2f else 0f
            )
        }
    } else {
        w += mini.breathDot
        if (showLockMark) {
            w += mini.nameGap
            w += mini.lockSize
        }
        if (showAppName) {
            w += mini.nameGap
            w += measureCapsuleTextWidth(
                measurer, density, appName,
                mini.nameSp, FontWeight.Medium, timeFont
            ).coerceAtMost(mini.nameMax)
        }
        if (!labelText.isNullOrBlank()) {
            w += MiniDividerSlot
            w += measureCapsuleTextWidth(
                measurer, density, labelText,
                mini.labelSp, FontWeight.SemiBold, timeFont
            ).coerceAtMost(mini.labelMax)
        }
        w += MiniDividerSlot
        w += measureCapsuleTextWidth(
            measurer, density, timerText,
            mini.timerSp, FontWeight.SemiBold,
            if (timerMono) FontFamily.Monospace else timeFont,
            letterSpacingSp = if (timerMono) 0.2f else 0f
        )
    }
    val minW = mini.wMin
    val maxW = when {
        wideTimer -> mini.wMaxWithSeconds
        else -> mini.wMax
    }
    return w.coerceIn(minW, maxW)
}

/** 暂离环边：从 12 点钟方向顺时针收束（与顶部停靠胶囊对齐） */
private const val AWAY_RING_PATH_START_FRACTION = 0.125f

/** 纯时长锁：圆环种子可见停留（毫秒），再气泡展开 */
private const val RING_ENTRANCE_HOLD_MS = 420L

/** 意图倒计时最后 N 秒：变红并自动展开 */
private const val SESSION_COUNTDOWN_URGENT_SEC = 30L

/** 意图倒计时 1 分钟内：仅变黄 */
private const val SESSION_COUNTDOWN_WARN_SEC = 60L

/** 纯时长锁：剩余 5 分钟起迷你态变黄（不撑高壳；展开横幅另给一句提示） */
private const val TIME_LOCK_FIVE_MIN_WARN_SEC = 300L

/** 意图门入场：compact 落岛后短暂停顿再展开（毫秒） */
private const val INTENT_COMPACT_SETTLE_MS = 90L

/** 宽轴弹簧：利落、轻过冲（对齐系统岛体感） */
private val IslandWidthSpring = spring<Float>(
    dampingRatio = 0.82f,
    stiffness = 340f
)

/** 纯时长锁：圆环 → 迷你的气泡拉宽（略软、轻过冲） */
private val BubbleExpandSpring = spring<Float>(
    dampingRatio = 0.74f,
    stiffness = 320f
)

/** 高轴弹簧：与宽轴同拍，避免「先胖后高」的二次形变感 */
private val IslandHeightSpring = spring<Float>(
    dampingRatio = 0.84f,
    stiffness = 360f
)

/** 圆角弹簧：跟形态一起走 */
private val IslandCornerSpring = spring<Float>(
    dampingRatio = 0.88f,
    stiffness = 480f
)

/** 意图门 compact 冒泡落点 */
private val IntentPillPopSpring = spring<Float>(
    dampingRatio = 0.68f,
    stiffness = 480f
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

/**
 * 陪伴岛入参。单独成对象，避免每个子 Composable 再摊开 50 个参数。
 * 子函数在组合期间读取 [State.value]，重组范围记在调用方上。
 */
private class CapsuleOverlayInputs(
    val appName: State<String>,
    val appPackageName: State<String>,
    val sessionSeconds: State<Long>,
    val dailyRemainingSeconds: State<Long>,
    val dailyLimitSeconds: State<Long>,
    val purpose: State<String?>,
    val intentKind: State<com.life.mindfulnessapp.domain.model.IntentKind?>,
    val expanded: State<Boolean>,
    val isPaused: State<Boolean>,
    val isOverLimit: State<Boolean>,
    val hasIntentGate: State<Boolean>,
    val compareEnabled: State<Boolean>,
    val compareMinMinutes: State<Int>,
    /** 觉察练习总闸（中途轻问 + 离开对照） */
    val awarenessPracticeEnabled: State<Boolean>,
    /** 中途轻问间隔（秒）：疏 600 / 常 300 */
    val awarenessPracticeGapSec: State<Long>,
    val hasTimeLock: State<Boolean>,
    val todayEnterCount: State<Int>,
    val sessionOriginStartMs: State<Long>,
    val todayTotalSeconds: State<Long>,
    val hasSessionLimit: State<Boolean>,
    val canOfferExtension: State<Boolean>,
    val sessionLimitMinutes: State<Int>,
    val miniCompact: State<Boolean>,
    val shellOpacity: State<Float>,
    val companionPrefs: State<CapsuleCompanionPrefs>,
    val awayCountdownSeconds: State<Long>,
    val awayCountdownTotalSeconds: State<Long>,
    val themePack: State<ThemePack>,
    val isDarkTheme: Boolean,
    val onToggleExpand: () -> Unit,
    val onCompanionActivate: (() -> Unit)?,
    val onEndSession: (note: String?, mindfulnessLevel: Int?, driftSeconds: Long?, openToAnchor: Boolean) -> Unit,
    val onExtendSession: ((extraMinutes: Int) -> Unit)?,
    val onReturnToApp: (() -> Unit)?,
    val onRegisterWakeUp: ((wakeUpFn: () -> Unit) -> Unit)?,
    val onRegisterShowConfirm: ((showConfirmFn: () -> Unit) -> Unit)?,
    val onRegisterWarnFiveMin: ((fn: () -> Unit) -> Unit)?,
    val onRegisterStartCountdown: ((fn: () -> Unit) -> Unit)?,
    val onRegisterStopAction: ((stopFn: () -> Unit) -> Unit)?,
    val onRegisterSkipEntrance: ((skipFn: (() -> Unit)?) -> Unit)?,
    val onStopHitRectChanged: ((rect: android.graphics.RectF?) -> Unit)?,
    val onEndDialogVisibilityChanged: ((open: Boolean) -> Unit)?,
    val onConfirmDialogOpen: (() -> Unit)?,
    val onConfirmDialogClose: (() -> Unit)?,
    val onMiniSettled: (() -> Unit)?,
    val onTopPinChanged: ((Boolean) -> Unit)?,
    /**
     * 贴条锚提示占用的顶部额外高度（px）。
     * Overlay 用它把窗口上移，让陪伴条视觉位置不变。
     */
    val onAttachedTopInsetChanged: ((insetPx: Int) -> Unit)?,
    val onRegisterRequestCheckFocus: ((() -> Unit) -> Unit)?,
    /** 外部（陪伴面板）作答写入运行态；返回 true 表示已接入 */
    val onRegisterApplyExternalAwareness: ((((still: Boolean) -> Boolean)) -> Unit)?,
    val onOrbDiscoverHintShown: (() -> Unit)?,
    /**
     * 使用中觉察埋点。
     * kind: show | action | soft_exit；action 仅后两者有值（still/drift/dismiss 或 end/stay）。
     */
    val onMidSessionAwareness: ((kind: String, action: String?, checkIndex: Int, mode: String) -> Unit)?,
    val onAwarenessSessionStateChanged: ((
        checks: Int,
        lastCheckAtSec: Long,
        enterHintShown: Boolean,
        browseHintShown: Boolean
    ) -> Unit)?,
    /** 开播即应被消费为 false，避免 restack 重建后再入场 */
    val onEnterAnimationConsumed: (() -> Unit)?,
    val playEnterAnimation: State<Boolean>,
    val softReveal: State<Boolean>,
)

/** 跨重组保留的岛内状态。用一个 remember 代替几十个 remember，把寄存器从主函数里挪走。 */
private class CapsuleOverlayRuntime(
    showDiscoverHint: Boolean,
    userMorphEnabled: Boolean,
    initialEnterAnchorHintShown: Boolean = false,
    initialBrowseNearEndHintShown: Boolean = false,
    initialFocusChecksCompleted: Int = 0,
    initialLastCheckAtSec: Long = 0L,
) {
    var showEndConfirmDialog by mutableStateOf(false)
    var endConfirmReason by mutableStateOf(EndConfirmReason.Manual)
    var showFiveMinWarning by mutableStateOf(false)
    var countdownMode by mutableStateOf(false)
    var showExtendDialog by mutableStateOf(false)
    var showCheckFocus by mutableStateOf(false)
    /** 锚提示播放中（与岛展开 / 觉察问题互斥） */
    var runwayPlaying by mutableStateOf(false)
    /** 非空时在陪伴条上方展示贴条锚提示 */
    var anchorHintText by mutableStateOf<String?>(null)
    var showSoftExit by mutableStateOf(false)
    var showTimeFlash by mutableStateOf(false)
    var timeFlashLabel by mutableStateOf("")
    var lastFlashedMinute by mutableStateOf(-1L)
    var lastCheckAtSec by mutableStateOf(initialLastCheckAtSec)
    var focusChecksCompleted by mutableStateOf(initialFocusChecksCompleted)
    var enterAnchorHintShown by mutableStateOf(initialEnterAnchorHintShown)
    var browseNearEndHintShown by mutableStateOf(initialBrowseNearEndHintShown)
    var showDiscoverHint by mutableStateOf(showDiscoverHint)
    var islandExpanded by mutableStateOf(false)
    var entranceHolding by mutableStateOf(false)
    var ringEntranceActive by mutableStateOf(false)
    /** 搜索前 30s：胶囊尚未落成 */
    var appearanceDeferred by mutableStateOf(false)
    /** 光点落成进场进行中 */
    var bloomEntranceActive by mutableStateOf(false)
    var userMorphEnabled by mutableStateOf(userMorphEnabled)
    var skipNextUserCollapse by mutableStateOf(false)
    var didUrgentExpand by mutableStateOf(false)
    var entranceSkipRequested by mutableStateOf(false)
    var userMorphRunning by mutableStateOf(false)
    var awayRingProgress by mutableFloatStateOf(1f)
    var thisMediaPlaying by mutableStateOf(false)
    var foreignPackage by mutableStateOf<String?>(null)
    var emitPulseSeq by mutableIntStateOf(0)
    var motion: CapsuleMotion? = null

    fun requestEmitPulse() {
        emitPulseSeq++
    }

    fun openEndConfirm(reason: EndConfirmReason) {
        endConfirmReason = reason
        showEndConfirmDialog = true
    }

    fun openPrimaryEndAction() {
        openEndConfirm(EndConfirmReason.Manual)
    }
}

private class CapsuleMotion(
    val islandW: Animatable<Float, AnimationVector1D>,
    val islandH: Animatable<Float, AnimationVector1D>,
    val islandR: Animatable<Float, AnimationVector1D>,
    val appearScale: Animatable<Float, AnimationVector1D>,
    val appearAlpha: Animatable<Float, AnimationVector1D>,
    /** 光核不透明 */
    val bloomCoreAlpha: Animatable<Float, AnimationVector1D>,
    /** 光核尺度 */
    val bloomCoreScale: Animatable<Float, AnimationVector1D>,
    /** 光晕不透明 */
    val bloomHaloAlpha: Animatable<Float, AnimationVector1D>,
    /** 光晕尺度（相对大画布，0.08→1，避免 graphicsLayer×10 被 WRAP 裁切） */
    val bloomHaloScale: Animatable<Float, AnimationVector1D>,
    /** 落成瞬间壳后柔光 */
    val bloomShellGlow: Animatable<Float, AnimationVector1D>,
    /** 条内文字 / 停键（光落成后再淡入） */
    val contentAlpha: Animatable<Float, AnimationVector1D>,
)

/** 一帧里算好的文案和旗标。普通函数，不进 Composable 的寄存器。 */
private class CapsuleMetrics(
    val themeConfig: InterceptThemeConfig,
    val overLimit: Boolean,
    val overLimitColor: Color,
    val intentGate: Boolean,
    val awarenessMode: SessionAwarenessMode,
    val timeLock: Boolean,
    val intentOnly: Boolean,
    val todaySummaryLine: String,
    val hasLimit: Boolean,
    val isUrgent: Boolean,
    val ratio: Float,
    val showAwayCountdown: Boolean,
    val shellTextPrimary: Color,
    val shellTextSecondary: Color,
    val timeFont: FontFamily,
    val activeAccent: Color,
    val collapsedPurpose: String?,
    val sessionTick: String,
    val requiredIntent: String,
    val fullAppName: String,
    val usedTick: String,
    val intentSessionCountdown: Boolean,
    val sessionRemainSec: Long,
    val timeLockBudgetPrimary: Boolean,
    val sessionCountdownUrgent: Boolean,
    val sessionCountdownWarn: Boolean,
    val timeLockCountdownUrgent: Boolean,
    val timeLockCountdownWarn: Boolean,
    val showExtendOffer: Boolean,
    val expandedW: Dp,
    val mini: CapsuleMiniMetrics,
    val orb: FloatingOrbMetrics,
    val companionBar: CompanionBarMetrics,
    val companionSessionLine: String,
    val companionSecondLine: String?,
    val companionShowStop: Boolean,
    val companionShowBar: Boolean,
    val companionStopIsMusicExit: Boolean,
    val companionForeignPackage: String?,
    val companionPath: CompanionPath,
    val markStyle: CapsuleMarkStyle,
    val thisMediaPlaying: Boolean,
    val collapsedW: Dp,
    val collapsedH: Dp,
    val collapsedR: Dp,
)

private class CapsuleChromeModel(
    val expandedPrimary: String,
    val dailyIdentityLine: String,
    val primaryIsIntent: Boolean,
    val dailyTrailingMeta: String?,
    val dailyCaption: String?,
    val captionIsIntent: Boolean,
    val secondaryIsIntent: Boolean,
    val expandedEyebrow: String?,
    val expandedSecondary: String,
    val expandBannerMode: ExpandBannerMode,
    val awayRingActive: Color,
    val awayRingTrack: Color,
    val expandedPrimaryColor: Color,
    val expandedSecondaryColor: Color,
    val captionColor: Color,
    val expandedTimeColor: Color,
    val paused: Boolean,
    val showEndControl: Boolean,
    val breathDotColor: Color,
    val capabilityTint: Color,
    val shellTextSecondary: Color,
    val miniAccent: Color,
    val urgentMini: Boolean,
)

private fun capsuleWakeUp() { /* 已取消休眠变暗，保留空实现兼容回调 */ }

private fun shellExtraHeight(expandedShell: Boolean, metrics: CapsuleMetrics): Float {
    if (!expandedShell) return 0f
    // 续时动作已并入顶行文字 CTA，不再加高壳
    return if (metrics.intentGate) IntentWrapExtraH.value else 0f
}

private suspend fun morphIsland(
    motion: CapsuleMotion,
    metrics: CapsuleMetrics,
    expandedTarget: Boolean,
) = coroutineScope {
    val tw = if (expandedTarget) metrics.expandedW.value else metrics.collapsedW.value
    val th = (if (expandedTarget) ExpandedH.value else metrics.collapsedH.value) +
        shellExtraHeight(expandedTarget, metrics)
    val tr = if (expandedTarget) ExpandedR.value else metrics.collapsedR.value
    launch { motion.islandW.animateTo(tw, IslandWidthSpring) }
    launch { motion.islandH.animateTo(th, IslandHeightSpring) }
    motion.islandR.animateTo(tr, IslandCornerSpring)
}

@Keep
private fun buildCapsuleMetrics(
    inputs: CapsuleOverlayInputs,
    measurer: TextMeasurer,
    density: Density,
    screenWidth: Dp,
    thisMediaPlaying: Boolean = false,
    foreignPackage: String? = null,
): CapsuleMetrics {
    val themeConfig = getInterceptThemeConfig(inputs.themePack.value)
    val overLimit = inputs.isOverLimit.value
    val intentGate = inputs.hasIntentGate.value
    val purpose = inputs.purpose.value
    val awarenessMode = SessionAwarenessMode.from(inputs.intentKind.value, purpose)
    val companionPath = CompanionPath.resolve(
        intentKind = inputs.intentKind.value,
        purpose = purpose,
        hasSessionLimit = inputs.hasSessionLimit.value
    )
    val markStyle = CapsuleMarkStyle.from(companionPath)
    val timeLock = inputs.hasTimeLock.value || overLimit
    val intentOnly = intentGate && !timeLock
    val awayRemain = inputs.awayCountdownSeconds.value
    val awayTotal = inputs.awayCountdownTotalSeconds.value.coerceAtLeast(0L)
    val showAwayCountdown = inputs.isPaused.value &&
        awayRemain >= 0L &&
        awayTotal > 0L &&
        !thisMediaPlaying
    val limit = inputs.dailyLimitSeconds.value
    val ratio = if (overLimit || !timeLock || limit <= 0L) {
        1f
    } else {
        (inputs.dailyRemainingSeconds.value.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
    }
    val isUrgent = timeLock && !overLimit && ratio <= 0.10f && limit > 0L
    val hasLimit = timeLock && limit > 0L
    val shellTextPrimary = if (inputs.isDarkTheme) ShellTextLight else ShellTextDark
    val shellTextSecondary = shellTextPrimary.copy(alpha = 0.68f)
    val timeFont = if (themeConfig.capsuleUseMonoFont) FontFamily.Monospace else FontFamily.Default
    val activeAccent = themeConfig.capsuleAccentColor
    val collapsedPurpose = purpose?.takeIf { it.isNotBlank() }
    val sessionTick = formatSeconds(inputs.sessionSeconds.value)
    val requiredIntent = collapsedPurpose ?: inputs.appName.value.ifBlank { "这一次" }
    val fullAppName = inputs.appName.value.trim()
    val dailyUsedSeconds = when {
        inputs.todayTotalSeconds.value > 0L -> inputs.todayTotalSeconds.value
        hasLimit -> (limit - inputs.dailyRemainingSeconds.value).coerceAtLeast(0L)
        else -> inputs.sessionSeconds.value
    }
    val usedTick = formatSeconds(dailyUsedSeconds)
    val sessionLimitOn = inputs.hasSessionLimit.value
    val paused = inputs.isPaused.value
    val intentSessionCountdown = intentGate && sessionLimitOn && hasLimit && !paused
    val sessionRemainSec = inputs.dailyRemainingSeconds.value.coerceAtLeast(0L)
    val timeLockBudgetPrimary = timeLock && hasLimit && !paused && !intentSessionCountdown
    val sessionCountdownUrgent = intentSessionCountdown &&
        sessionRemainSec in 1L..SESSION_COUNTDOWN_URGENT_SEC
    val sessionCountdownWarn = intentSessionCountdown &&
        sessionRemainSec in (SESSION_COUNTDOWN_URGENT_SEC + 1)..SESSION_COUNTDOWN_WARN_SEC
    val timeLockCountdownUrgent = timeLockBudgetPrimary &&
        sessionRemainSec in 1L..SESSION_COUNTDOWN_URGENT_SEC
    val timeLockCountdownWarn = timeLockBudgetPrimary &&
        sessionRemainSec in (SESSION_COUNTDOWN_URGENT_SEC + 1)..TIME_LOCK_FIVE_MIN_WARN_SEC
    val showExtendOffer = inputs.canOfferExtension.value &&
        sessionRemainSec in 1L..SESSION_COUNTDOWN_WARN_SEC &&
        !paused &&
        !overLimit &&
        (intentSessionCountdown || timeLockBudgetPrimary) &&
        inputs.onExtendSession != null
    val expandedW = expandedIslandWidth(screenWidth)
    val mini = if (inputs.miniCompact.value) MiniCompact else MiniStandard
    val orb = floatingOrbMetrics(inputs.miniCompact.value)
    val todayMinLabel = formatCompanionMinutes(
        if (inputs.todayTotalSeconds.value > 0L) inputs.todayTotalSeconds.value else dailyUsedSeconds
    )
    val todaySummaryLine =
        "今日 $todayMinLabel · ${inputs.todayEnterCount.value.coerceAtLeast(0)}次"
    val sessionLimitMin = inputs.sessionLimitMinutes.value.coerceAtLeast(0)
    val companion = CompanionScene.resolve(
        packageName = inputs.appPackageName.value,
        prefs = inputs.companionPrefs.value,
        hasIntentGate = intentGate,
        thisMediaPlaying = thisMediaPlaying,
    )
    val lightForm = companion.form == CompanionBarForm.LIGHT
    val companionBar = companionBarMetrics(inputs.miniCompact.value, light = lightForm)
    // 有单次上限：倒计时（非搜索，分钟来自门上）。无单次上限：正向计时（搜索，靠日限额）。
    val countdown = intentGate && sessionLimitOn
    val sessionClock = formatSeconds(
        if (countdown) sessionRemainSec else inputs.sessionSeconds.value
    )
    val sessionClockLine = sessionClock
    val todayLine = "今日 $todayMinLabel"
    val dailyApproaching = timeLock && hasLimit &&
        sessionRemainSec in 1L..TIME_LOCK_FIVE_MIN_WARN_SEC
    val companionSessionLine = when {
        companion.emphasizeSession -> sessionClockLine
        else -> todayLine
    }
    val companionSecondLine: String? = when {
        !companion.showBar || lightForm -> null
        // 随意浏览：条上不写意图 / 「随意浏览」
        companionPath == CompanionPath.BROWSE -> null
        companion.emphasizeSession && collapsedPurpose != null -> collapsedPurpose
        companion.emphasizeSession && dailyApproaching -> todayLine
        companion.emphasizeSession && intentGate -> "没写意图"
        companion.emphasizeSession -> todayLine
        // 非随意浏览且无强调时：有单次上限可显示时钟次行
        companionPath != CompanionPath.BROWSE && sessionLimitOn && sessionLimitMin > 0 ->
            sessionClockLine
        else -> null
    }
    val companionShowStop = companion.showStop
    val foreignSlot = if (foreignPackage.isNullOrBlank()) {
        0.dp
    } else {
        (companionBar.icon * 0.72f).coerceAtLeast(16.dp) + companionBar.gap
    }
    val t1 = measureCapsuleTextWidth(
        measurer, density, companionSessionLine, companionBar.sessionSp,
        FontWeight.SemiBold, FontFamily.Monospace, letterSpacingSp = 0.15f
    )
    val t2 = companionSecondLine?.let {
        measureCapsuleTextWidth(
            measurer, density, it, companionBar.todaySp,
            FontWeight.Medium, timeFont
        )
    } ?: 0.dp
    val stopSlot = if (companionShowStop) companionBar.stopSlot + companionBar.gap else 0.dp
    val (collapsedW, collapsedH, collapsedR) = if (companion.showBar) {
        val w = (maxOf(t1, t2) + companionBar.padStart + companionBar.icon + companionBar.gap +
            foreignSlot + stopSlot + companionBar.padEnd + CompanionCollapsedWidthSlack)
            .coerceIn(CompanionCollapsedWidthMin, CompanionCollapsedWidthMax)
        Triple(w, companionBar.height, companionBar.corner)
    } else {
        val extra = if (foreignPackage.isNullOrBlank()) 0.dp else 6.dp
        val size = orb.shell + extra
        Triple(size, size, size / 2)
    }
    return CapsuleMetrics(
        themeConfig = themeConfig,
        overLimit = overLimit,
        overLimitColor = themeConfig.limitAccentColor,
        intentGate = intentGate,
        awarenessMode = awarenessMode,
        timeLock = timeLock,
        intentOnly = intentOnly,
        todaySummaryLine = todaySummaryLine,
        hasLimit = hasLimit,
        isUrgent = isUrgent,
        ratio = ratio,
        showAwayCountdown = showAwayCountdown,
        shellTextPrimary = shellTextPrimary,
        shellTextSecondary = shellTextSecondary,
        timeFont = timeFont,
        activeAccent = activeAccent,
        collapsedPurpose = collapsedPurpose,
        sessionTick = sessionTick,
        requiredIntent = requiredIntent,
        fullAppName = fullAppName,
        usedTick = usedTick,
        intentSessionCountdown = intentSessionCountdown,
        sessionRemainSec = sessionRemainSec,
        timeLockBudgetPrimary = timeLockBudgetPrimary,
        sessionCountdownUrgent = sessionCountdownUrgent,
        sessionCountdownWarn = sessionCountdownWarn,
        timeLockCountdownUrgent = timeLockCountdownUrgent,
        timeLockCountdownWarn = timeLockCountdownWarn,
        showExtendOffer = showExtendOffer,
        expandedW = expandedW,
        mini = mini,
        orb = orb,
        companionBar = companionBar,
        companionSessionLine = companionSessionLine,
        companionSecondLine = companionSecondLine,
        companionShowStop = companionShowStop,
        companionShowBar = companion.showBar,
        companionStopIsMusicExit = companion.stopIsMusicExit,
        companionForeignPackage = foreignPackage,
        companionPath = companionPath,
        markStyle = markStyle,
        thisMediaPlaying = thisMediaPlaying,
        collapsedW = collapsedW,
        collapsedH = collapsedH,
        collapsedR = collapsedR,
    )
}

@Keep
private fun deriveCapsuleChrome(
    inputs: CapsuleOverlayInputs,
    metrics: CapsuleMetrics,
    runtime: CapsuleOverlayRuntime,
    iconColor: Color,
    effectiveIconColor: Color,
    pulseAlpha: Float,
    breathAlpha: Float,
    shouldPulse: Boolean,
    shouldBreathe: Boolean,
    awayRingProgress: Float,
): CapsuleChromeModel {
    val paused = inputs.isPaused.value
    val awayUrgent = metrics.showAwayCountdown && awayRingProgress in 0.001f..0.34f
    val collapsedPurpose = metrics.collapsedPurpose
    val expandedPrimary = when {
        paused -> collapsedPurpose ?: metrics.fullAppName.ifBlank { "这一次" }
        metrics.intentSessionCountdown && metrics.overLimit -> "本次已超额"
        metrics.intentSessionCountdown -> formatSeconds(metrics.sessionRemainSec)
        metrics.timeLock && metrics.hasLimit && metrics.overLimit -> "${metrics.usedTick}  ·  已超额"
        metrics.timeLock && metrics.hasLimit ->
            "${metrics.usedTick}  /  ${formatLimitCompact(inputs.dailyLimitSeconds.value)}"
        metrics.overLimit -> "已超额"
        metrics.intentOnly -> metrics.requiredIntent
        else -> "本次  ${metrics.sessionTick}"
    }
    val dailyIdentityLine = metrics.fullAppName.ifBlank { "这一次" }
    val primaryIsIntent = when {
        paused -> !collapsedPurpose.isNullOrBlank()
        metrics.intentOnly -> true
        else -> false
    }
    val dailyTrailingMeta: String? = when {
        paused -> null
        metrics.intentOnly -> metrics.sessionTick
        metrics.intentGate && metrics.timeLock && metrics.hasLimit && !metrics.intentSessionCountdown &&
            !collapsedPurpose.isNullOrBlank() -> metrics.sessionTick
        else -> null
    }
    val dailyCaption: String? = when {
        paused -> "本次已用 ${metrics.sessionTick}"
        metrics.intentSessionCountdown && !collapsedPurpose.isNullOrBlank() -> collapsedPurpose
        metrics.intentSessionCountdown -> "本次剩余"
        metrics.intentOnly -> null
        metrics.intentGate && !collapsedPurpose.isNullOrBlank() && !primaryIsIntent -> collapsedPurpose
        metrics.timeLock && metrics.hasLimit -> "本次 ${metrics.sessionTick}"
        !collapsedPurpose.isNullOrBlank() && expandedPrimary != metrics.requiredIntent -> collapsedPurpose
        else -> null
    }
    val captionIsIntent = metrics.intentGate &&
        !collapsedPurpose.isNullOrBlank() &&
        dailyCaption == collapsedPurpose
    val secondaryIsIntent = metrics.intentGate &&
        !collapsedPurpose.isNullOrBlank() &&
        !primaryIsIntent &&
        metrics.intentSessionCountdown
    val expandedEyebrow = when {
        paused && metrics.showAwayCountdown && awayUrgent -> "即将结束"
        paused && metrics.showAwayCountdown -> "点按可回去"
        paused -> null
        metrics.intentSessionCountdown && metrics.overLimit -> "本次会话"
        metrics.intentSessionCountdown && metrics.sessionCountdownUrgent -> "即将结束"
        metrics.intentSessionCountdown && metrics.showExtendOffer -> "还剩不到 1 分钟"
        metrics.intentSessionCountdown -> "本次剩余"
        metrics.timeLockCountdownUrgent -> "即将结束"
        metrics.timeLockBudgetPrimary && metrics.showExtendOffer -> "还剩不到 1 分钟"
        metrics.timeLock && metrics.hasLimit && !metrics.intentSessionCountdown -> "今日已用"
        else -> null
    }
    val expandedSecondary = when {
        paused -> dailyCaption.orEmpty()
        metrics.intentSessionCountdown -> collapsedPurpose.orEmpty()
        metrics.timeLock && metrics.hasLimit -> "本次 ${metrics.sessionTick}"
        metrics.intentOnly -> "本次 ${metrics.sessionTick}"
        !collapsedPurpose.isNullOrBlank() -> collapsedPurpose
        else -> "本次 ${metrics.sessionTick}"
    }
    // SoftExit / Check 优先；暂停态走 Daily，让「回来」叙事压过紧急色
    val expandBannerMode = when {
        runtime.showSoftExit -> ExpandBannerMode.SoftExit
        runtime.showCheckFocus -> ExpandBannerMode.Check
        !paused && (
            metrics.showExtendOffer ||
                metrics.sessionCountdownUrgent ||
                metrics.timeLockCountdownUrgent
            ) -> ExpandBannerMode.Decision
        else -> ExpandBannerMode.Daily
    }
    val pausedContentColor = metrics.shellTextPrimary.copy(alpha = 0.92f)
    val awayRingActive = if (awayUrgent) {
        Color(0xFFE0B85C)
    } else if (inputs.isDarkTheme) {
        Color(0xFF9AA3B0)
    } else {
        Color(0xFF6B7280)
    }
    val awayRingTrack = if (inputs.isDarkTheme) {
        Color.White.copy(alpha = 0.16f)
    } else {
        Color.Black.copy(alpha = 0.12f)
    }
    val intentInk = intentInkColor(inputs.isDarkTheme)
    val expandedTimeColor = when {
        metrics.overLimit -> metrics.overLimitColor
        paused && awayUrgent -> Color(0xFFE0B85C)
        paused -> pausedContentColor
        metrics.sessionCountdownUrgent || metrics.timeLockCountdownUrgent -> Color(0xFFEF5350)
        metrics.sessionCountdownWarn || metrics.timeLockCountdownWarn -> Color(0xFFE0B85C)
        shouldPulse -> effectiveIconColor
        metrics.isUrgent -> iconColor
        else -> metrics.activeAccent
    }
    val expandedPrimaryColor = if (primaryIsIntent) intentInk else expandedTimeColor
    val expandedSecondaryColor = when {
        secondaryIsIntent ||
            (metrics.intentGate && !collapsedPurpose.isNullOrBlank() &&
                expandedSecondary == collapsedPurpose) -> intentInk
        metrics.intentOnly -> expandedTimeColor
        metrics.timeLock && metrics.hasLimit -> expandedTimeColor
        else -> metrics.shellTextSecondary
    }
    val captionColor = when {
        captionIsIntent -> intentInk
        dailyCaption?.startsWith("本次") == true -> expandedTimeColor.copy(alpha = 0.92f)
        else -> metrics.shellTextSecondary
    }
    val breathDotColor = when {
        shouldPulse || metrics.isUrgent ->
            iconColor.copy(alpha = if (shouldPulse) pulseAlpha else 0.95f)
        shouldBreathe -> metrics.activeAccent.copy(alpha = breathAlpha)
        metrics.overLimit -> metrics.overLimitColor.copy(alpha = 0.9f)
        paused -> Color(0xFF8E8E93).copy(alpha = 0.7f)
        else -> metrics.activeAccent.copy(alpha = 0.55f)
    }
    val capabilityTint = when {
        metrics.overLimit -> metrics.overLimitColor
        paused -> Color(0xFF8E8E93)
        else -> metrics.themeConfig.capsuleAccentColor
    }
    val miniAccent = when {
        metrics.overLimit -> metrics.overLimitColor
        shouldPulse || metrics.isUrgent -> iconColor
        else -> metrics.activeAccent
    }
    return CapsuleChromeModel(
        expandedPrimary = expandedPrimary,
        dailyIdentityLine = dailyIdentityLine,
        primaryIsIntent = primaryIsIntent,
        dailyTrailingMeta = dailyTrailingMeta,
        dailyCaption = dailyCaption,
        captionIsIntent = captionIsIntent,
        secondaryIsIntent = secondaryIsIntent,
        expandedEyebrow = expandedEyebrow,
        expandedSecondary = expandedSecondary,
        expandBannerMode = expandBannerMode,
        awayRingActive = awayRingActive,
        awayRingTrack = awayRingTrack,
        expandedPrimaryColor = expandedPrimaryColor,
        expandedSecondaryColor = expandedSecondaryColor,
        captionColor = captionColor,
        expandedTimeColor = expandedTimeColor,
        paused = paused,
        showEndControl = metrics.intentGate || metrics.companionStopIsMusicExit,
        breathDotColor = breathDotColor,
        capabilityTint = capabilityTint,
        shellTextSecondary = metrics.shellTextSecondary,
        miniAccent = miniAccent,
        urgentMini = shouldPulse || metrics.isUrgent ||
            metrics.sessionCountdownUrgent || metrics.timeLockCountdownUrgent,
    )
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun CapsuleOverlayView(
    sessionManager: SessionManager?,
    appName: State<String>,
    appPackageName: State<String> = mutableStateOf(""),
    sessionSeconds: State<Long>,
    dailyRemainingSeconds: State<Long>,
    dailyLimitSeconds: State<Long>,
    dailyBaseLimitSeconds: State<Long> = mutableStateOf(0L),
    dailyGraceBonusSeconds: State<Long> = mutableStateOf(0L),
    purpose: State<String?>,
    intentKind: State<com.life.mindfulnessapp.domain.model.IntentKind?> = mutableStateOf(null),
    expanded: State<Boolean>,
    isPaused: State<Boolean> = mutableStateOf(false),
    isOverLimit: State<Boolean> = mutableStateOf(false),
    hasIntentGate: State<Boolean> = mutableStateOf(true),
    compareEnabled: State<Boolean> = mutableStateOf(true),
    compareMinMinutes: State<Int> = mutableStateOf(10),
    awarenessPracticeEnabled: State<Boolean> = mutableStateOf(false),
    awarenessPracticeGapSec: State<Long> = mutableStateOf(
        com.life.mindfulnessapp.domain.model.MidSessionCheckPolicy.GAP_NORMAL_SEC
    ),
    hasTimeLock: State<Boolean> = mutableStateOf(true),
    todayEnterCount: State<Int> = mutableStateOf(0),
    sessionOriginStartMs: State<Long> = mutableStateOf(0L),
    todayTotalSeconds: State<Long> = mutableStateOf(0L),
    hasSessionLimit: State<Boolean> = mutableStateOf(false),
    /** 单次时长临近结束且尚未续过时可续 */
    canOfferExtension: State<Boolean> = mutableStateOf(false),
    /** 本次会话基础时长上限（分钟，不含续时）；用于续时三档上限 */
    sessionLimitMinutes: State<Int> = mutableStateOf(0),
    /** 纯时长锁迷你态：已用侧是否显示到秒 */
    showUsedSeconds: State<Boolean> = mutableStateOf(false),
    /** 迷你态尺寸：true = 紧凑（现网偏小档），false = 标准（默认更舒展） */
    miniCompact: State<Boolean> = mutableStateOf(false),
    /** 收起态壳透明度；展开岛不受影响 */
    shellOpacity: State<Float> = mutableStateOf(AppPreferences.CAPSULE_SHELL_OPACITY_DEFAULT),
    companionPrefs: State<CapsuleCompanionPrefs> = mutableStateOf(CapsuleCompanionPrefs()),
    awayCountdownSeconds: State<Long> = mutableStateOf(-1L),
    /** 本轮离开倒计时总量；与剩余秒配对，驱动暂停环边耗尽 */
    awayCountdownTotalSeconds: State<Long> = mutableStateOf(0L),
    themePack: State<ThemePack> = mutableStateOf(ThemePack.Night),
    isDarkTheme: Boolean = true,
    onToggleExpand: () -> Unit,
    /** 用户点收起态陪伴条。意图门开 Session Hub；未传则回退为展开岛 */
    onCompanionActivate: (() -> Unit)? = null,
    /** note / 正念程度 / 跑偏秒数 / 是否「结束并去心锚」 */
    onEndSession: (note: String?, mindfulnessLevel: Int?, driftSeconds: Long?, openToAnchor: Boolean) -> Unit,
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
    /** 入场/显现收成圆球后回调（用于窗口落到记忆悬浮点） */
    onMiniSettled: (() -> Unit)? = null,
    /** 入场动效一旦开播就回调，供外层清掉 State，防止重建重播 */
    onEnterAnimationConsumed: (() -> Unit)? = null,
    /** 对照岛期间请求窗口钉在顶部中央 */
    onTopPinChanged: ((Boolean) -> Unit)? = null,
    /** 贴条锚提示顶部 inset（px），用于窗口上移保条位 */
    onAttachedTopInsetChanged: ((insetPx: Int) -> Unit)? = null,
    onRegisterRequestCheckFocus: ((() -> Unit) -> Unit)? = null,
    onRegisterApplyExternalAwareness: ((((still: Boolean) -> Boolean)) -> Unit)? = null,
    /** 首次展示「轻点可看详情」 */
    showOrbDiscoverHint: Boolean = false,
    onOrbDiscoverHintShown: (() -> Unit)? = null,
    /**
     * 使用中觉察埋点。
     * kind: show | action | soft_exit；action 仅后两者有值。
     */
    onMidSessionAwareness: ((kind: String, action: String?, checkIndex: Int, mode: String) -> Unit)? = null,
    enterAnchorHintAlreadyShown: Boolean = false,
    browseNearEndHintAlreadyShown: Boolean = false,
    initialFocusChecksCompleted: Int = 0,
    initialLastCheckAtSec: Long = 0L,
    onAwarenessSessionStateChanged: ((
        checks: Int,
        lastCheckAtSec: Long,
        enterHintShown: Boolean,
        browseHintShown: Boolean
    ) -> Unit)? = null,
    playEnterAnimation: State<Boolean> = mutableStateOf(false),
    softReveal: State<Boolean> = mutableStateOf(false)
) {
    // 单方法寄存器超过 255 时 ART 直接 VerifyError。这里只组参数，逻辑在下面的函数里。
    val inputs = CapsuleOverlayInputs(
        appName = appName,
        appPackageName = appPackageName,
        sessionSeconds = sessionSeconds,
        dailyRemainingSeconds = dailyRemainingSeconds,
        dailyLimitSeconds = dailyLimitSeconds,
        purpose = purpose,
        intentKind = intentKind,
        expanded = expanded,
        isPaused = isPaused,
        isOverLimit = isOverLimit,
        hasIntentGate = hasIntentGate,
        compareEnabled = compareEnabled,
        compareMinMinutes = compareMinMinutes,
        awarenessPracticeEnabled = awarenessPracticeEnabled,
        awarenessPracticeGapSec = awarenessPracticeGapSec,
        hasTimeLock = hasTimeLock,
        todayEnterCount = todayEnterCount,
        sessionOriginStartMs = sessionOriginStartMs,
        todayTotalSeconds = todayTotalSeconds,
        hasSessionLimit = hasSessionLimit,
        canOfferExtension = canOfferExtension,
        sessionLimitMinutes = sessionLimitMinutes,
        miniCompact = miniCompact,
        shellOpacity = shellOpacity,
        companionPrefs = companionPrefs,
        awayCountdownSeconds = awayCountdownSeconds,
        awayCountdownTotalSeconds = awayCountdownTotalSeconds,
        themePack = themePack,
        isDarkTheme = isDarkTheme,
        onToggleExpand = onToggleExpand,
        onCompanionActivate = onCompanionActivate,
        onEndSession = onEndSession,
        onExtendSession = onExtendSession,
        onReturnToApp = onReturnToApp,
        onRegisterWakeUp = onRegisterWakeUp,
        onRegisterShowConfirm = onRegisterShowConfirm,
        onRegisterWarnFiveMin = onRegisterWarnFiveMin,
        onRegisterStartCountdown = onRegisterStartCountdown,
        onRegisterStopAction = onRegisterStopAction,
        onRegisterSkipEntrance = onRegisterSkipEntrance,
        onStopHitRectChanged = onStopHitRectChanged,
        onEndDialogVisibilityChanged = onEndDialogVisibilityChanged,
        onConfirmDialogOpen = onConfirmDialogOpen,
        onConfirmDialogClose = onConfirmDialogClose,
        onMiniSettled = onMiniSettled,
        onEnterAnimationConsumed = onEnterAnimationConsumed,
        onTopPinChanged = onTopPinChanged,
        onAttachedTopInsetChanged = onAttachedTopInsetChanged,
        onRegisterRequestCheckFocus = onRegisterRequestCheckFocus,
        onRegisterApplyExternalAwareness = onRegisterApplyExternalAwareness,
        onOrbDiscoverHintShown = onOrbDiscoverHintShown,
        onMidSessionAwareness = onMidSessionAwareness,
        onAwarenessSessionStateChanged = onAwarenessSessionStateChanged,
        playEnterAnimation = playEnterAnimation,
        softReveal = softReveal,
    )
    val runtime = remember {
        CapsuleOverlayRuntime(
            showDiscoverHint = showOrbDiscoverHint,
            userMorphEnabled = !playEnterAnimation.value && !softReveal.value,
            initialEnterAnchorHintShown = enterAnchorHintAlreadyShown,
            initialBrowseNearEndHintShown = browseNearEndHintAlreadyShown,
            initialFocusChecksCompleted = initialFocusChecksCompleted,
            initialLastCheckAtSec = initialLastCheckAtSec,
        )
    }
    CapsuleOverlayHost(inputs, runtime)
}

@Keep
@Composable
private fun CapsuleOverlayHost(
    inputs: CapsuleOverlayInputs,
    runtime: CapsuleOverlayRuntime,
) {
    runtime.thisMediaPlaying = rememberPackageMediaPlaying(inputs.appPackageName.value)
    runtime.foreignPackage = rememberForeignMediaPlaying(inputs.appPackageName.value)
    CapsuleOverlayEffects(inputs, runtime)
    CapsuleIslandShell(inputs, runtime)
}

@Keep
@Composable
private fun CapsuleOverlayEffects(
    inputs: CapsuleOverlayInputs,
    runtime: CapsuleOverlayRuntime,
) {
    val metrics = buildCapsuleMetrics(
        inputs,
        rememberTextMeasurer(),
        LocalDensity.current,
        LocalConfiguration.current.screenWidthDp.dp,
        thisMediaPlaying = runtime.thisMediaPlaying,
        foreignPackage = runtime.foreignPackage,
    )
    val motion = runtime.motion ?: CapsuleMotion(
        islandW = Animatable(metrics.collapsedW.value),
        islandH = Animatable(metrics.collapsedH.value),
        islandR = Animatable(metrics.collapsedR.value),
        appearScale = Animatable(if (inputs.playEnterAnimation.value) 0.5f else 1f),
        appearAlpha = Animatable(
            when {
                inputs.playEnterAnimation.value -> 0f
                inputs.softReveal.value -> 0.4f
                else -> 1f
            }
        ),
        bloomCoreAlpha = Animatable(0f),
        bloomCoreScale = Animatable(0.35f),
        bloomHaloAlpha = Animatable(0f),
        bloomHaloScale = Animatable(0.08f),
        bloomShellGlow = Animatable(0f),
        contentAlpha = Animatable(if (inputs.playEnterAnimation.value) 0f else 1f),
    ).also { runtime.motion = it }
    val shouldPulse = metrics.timeLock && runtime.countdownMode &&
        !metrics.overLimit && !inputs.isPaused.value
    val haptic = LocalHapticFeedback.current
    val capsuleView = LocalView.current

    // 日常信息岛已去掉：只允许对照 / SoftExit / 到点决策展开
    val allowExpandedIsland = runtime.showCheckFocus ||
        runtime.showSoftExit ||
        (!inputs.isPaused.value && !metrics.overLimit && (
            metrics.showExtendOffer ||
                metrics.sessionCountdownUrgent ||
                metrics.timeLockCountdownUrgent ||
                (inputs.canOfferExtension.value &&
                    inputs.dailyRemainingSeconds.value in 1L..SESSION_COUNTDOWN_WARN_SEC &&
                    inputs.onExtendSession != null) ||
                (metrics.intentGate && inputs.hasSessionLimit.value &&
                    inputs.dailyRemainingSeconds.value in 1L..SESSION_COUNTDOWN_URGENT_SEC)
            ))
    LaunchedEffect(
        inputs.expanded.value,
        allowExpandedIsland,
        runtime.showEndConfirmDialog,
        runtime.showExtendDialog,
    ) {
        if (!inputs.expanded.value) return@LaunchedEffect
        if (runtime.showEndConfirmDialog || runtime.showExtendDialog) return@LaunchedEffect
        if (!allowExpandedIsland) {
            // 拦掉 Daily 大条（含日限额 5 分钟误展开）
            inputs.onToggleExpand()
        }
        // 对照 / SoftExit / 到点决策：各自收起，不在这里超时收回
    }

    DisposableEffect(Unit) {
        inputs.onRegisterWakeUp?.invoke { runtime.requestEmitPulse() }
        inputs.onRegisterShowConfirm?.invoke {
            runtime.openEndConfirm(EndConfirmReason.BackgroundTimeout)
        }
        inputs.onRegisterWarnFiveMin?.invoke { runtime.showFiveMinWarning = true }
        inputs.onRegisterStartCountdown?.invoke { runtime.countdownMode = true }
        inputs.onRegisterStopAction?.invoke { runtime.openPrimaryEndAction() }
        onDispose {
            inputs.onRegisterWakeUp?.invoke {}
            inputs.onRegisterShowConfirm?.invoke {}
            inputs.onRegisterWarnFiveMin?.invoke {}
            inputs.onRegisterStartCountdown?.invoke {}
            inputs.onRegisterStopAction?.invoke {}
            inputs.onRegisterSkipEntrance?.invoke(null)
            inputs.onStopHitRectChanged?.invoke(null)
            inputs.onEndDialogVisibilityChanged?.invoke(false)
        }
    }

    LaunchedEffect(runtime.emitPulseSeq) {
        if (runtime.emitPulseSeq == 0) return@LaunchedEffect
        if (runtime.islandExpanded || inputs.expanded.value) return@LaunchedEffect
        if (runtime.ringEntranceActive) return@LaunchedEffect
        val scale = motion.appearScale
        if (scale.value < 0.97f) return@LaunchedEffect
        scale.stop()
        scale.snapTo(1f)
        scale.animateTo(1.03f, tween(88, easing = CompanionInspectEaseOut))
        scale.animateTo(1f, tween(240, easing = CompanionInspectEaseOut))
    }

    LaunchedEffect(runtime.showEndConfirmDialog, runtime.showExtendDialog, runtime.showSoftExit) {
        inputs.onEndDialogVisibilityChanged?.invoke(
            runtime.showEndConfirmDialog || runtime.showExtendDialog || runtime.showSoftExit
        )
    }

    LaunchedEffect(runtime.showFiveMinWarning, metrics.timeLock) {
        if (runtime.showFiveMinWarning && metrics.timeLock) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            capsuleWakeUp()
            delay(5500L)
            runtime.showFiveMinWarning = false
        } else if (runtime.showFiveMinWarning && !metrics.timeLock) {
            runtime.showFiveMinWarning = false
        }
    }

    LaunchedEffect(runtime.countdownMode, metrics.timeLock) {
        if (runtime.countdownMode && metrics.timeLock) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(80)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            capsuleWakeUp()
        } else if (runtime.countdownMode && !metrics.timeLock) {
            runtime.countdownMode = false
        }
    }

    LaunchedEffect(inputs.dailyRemainingSeconds.value) {
        if (runtime.countdownMode && inputs.dailyRemainingSeconds.value > 60L) {
            runtime.countdownMode = false
        }
    }

    // 入场：光点落成；搜索前 30s 不出条
    LaunchedEffect(Unit) {
        suspend fun playGlowBloomEntrance() {
            // 开播即消费：点开面板 restack 即使重建 Compose 也不会再入场
            inputs.onEnterAnimationConsumed?.invoke()
            runtime.bloomEntranceActive = true
            runtime.ringEntranceActive = false
            runtime.entranceHolding = false
            runtime.islandExpanded = false
            motion.islandW.snapTo(metrics.collapsedW.value)
            motion.islandH.snapTo(metrics.collapsedH.value)
            motion.islandR.snapTo(metrics.collapsedR.value)
            motion.appearScale.snapTo(0.5f)
            motion.appearAlpha.snapTo(0f)
            motion.contentAlpha.snapTo(0f)
            motion.bloomCoreAlpha.snapTo(0f)
            motion.bloomCoreScale.snapTo(0.35f)
            motion.bloomHaloAlpha.snapTo(0f)
            motion.bloomHaloScale.snapTo(0.08f)
            motion.bloomShellGlow.snapTo(0f)

            // 远点靠近（~1.05s，对齐草图 coreApproach）
            launch {
                motion.bloomCoreAlpha.animateTo(0.95f, tween(190, easing = BloomEase))
                motion.bloomCoreAlpha.animateTo(1f, tween(250, easing = BloomEase))
                motion.bloomCoreAlpha.animateTo(0.55f, tween(290, easing = BloomEase))
                motion.bloomCoreAlpha.animateTo(0f, tween(320, easing = BloomEase))
            }
            launch {
                motion.bloomCoreScale.animateTo(0.7f, tween(190, easing = BloomEase))
                motion.bloomCoreScale.animateTo(1.15f, tween(250, easing = BloomEase))
                motion.bloomCoreScale.animateTo(1.6f, tween(290, easing = BloomEase))
                motion.bloomCoreScale.animateTo(2.2f, tween(320, easing = BloomEase))
            }
            // 光晕在大画布内展开到 1.0（不再 ×10 裁切）
            launch {
                delay(80)
                motion.bloomHaloAlpha.animateTo(0.85f, tween(340, easing = BloomHaloEase))
                motion.bloomHaloAlpha.animateTo(0.45f, tween(280, easing = BloomHaloEase))
                motion.bloomHaloAlpha.animateTo(0f, tween(520, easing = BloomHaloEase))
            }
            launch {
                delay(80)
                motion.bloomHaloScale.animateTo(1f, tween(1140, easing = BloomHaloEase))
            }
            // 壳落成 + 瞬时柔光
            delay(280)
            launch {
                motion.bloomShellGlow.animateTo(0.9f, tween(220, easing = BloomEase))
                motion.bloomShellGlow.animateTo(0f, tween(620, easing = BloomEase))
            }
            launch {
                motion.appearAlpha.animateTo(1f, tween(520, easing = BloomEase))
            }
            launch {
                motion.appearScale.animateTo(0.92f, tween(420, easing = BloomEase))
                motion.appearScale.animateTo(1f, tween(480, easing = BloomEase))
            }
            delay(440)
            motion.contentAlpha.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
            delay(120)
            runtime.bloomEntranceActive = false
            runtime.skipNextUserCollapse = true
            runtime.userMorphEnabled = true
            inputs.onMiniSettled?.invoke()
        }

        val deferSearch = com.life.mindfulnessapp.domain.model.MidSessionCheckPolicy.shouldDeferSearchCapsule(
            intentGate = metrics.intentGate,
            intentKind = inputs.intentKind.value,
            purpose = inputs.purpose.value,
            hasSessionLimit = inputs.hasSessionLimit.value,
            sessionSeconds = inputs.sessionSeconds.value
        )
        when {
            deferSearch -> {
                runtime.appearanceDeferred = true
                runtime.bloomEntranceActive = false
                runtime.userMorphEnabled = false
                motion.appearAlpha.snapTo(0f)
                motion.contentAlpha.snapTo(0f)
                // 等到 ≥30s 再落成（同会话挂载时若已过点则立刻）
                while (
                    com.life.mindfulnessapp.domain.model.MidSessionCheckPolicy.shouldDeferSearchCapsule(
                        intentGate = metrics.intentGate,
                        intentKind = inputs.intentKind.value,
                        purpose = inputs.purpose.value,
                        hasSessionLimit = inputs.hasSessionLimit.value,
                        sessionSeconds = inputs.sessionSeconds.value
                    )
                ) {
                    delay(200L)
                }
                runtime.appearanceDeferred = false
                if (inputs.playEnterAnimation.value || inputs.softReveal.value) {
                    playGlowBloomEntrance()
                } else {
                    motion.appearAlpha.snapTo(1f)
                    motion.appearScale.snapTo(1f)
                    motion.contentAlpha.snapTo(1f)
                    runtime.userMorphEnabled = true
                    inputs.onMiniSettled?.invoke()
                }
            }
            inputs.playEnterAnimation.value -> playGlowBloomEntrance()
            inputs.softReveal.value -> {
                inputs.onEnterAnimationConsumed?.invoke()
                motion.islandW.snapTo(metrics.collapsedW.value)
                motion.islandH.snapTo(metrics.collapsedH.value)
                motion.islandR.snapTo(metrics.collapsedR.value)
                motion.appearScale.snapTo(1f)
                motion.contentAlpha.snapTo(1f)
                motion.appearAlpha.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
                runtime.skipNextUserCollapse = true
                runtime.userMorphEnabled = true
                inputs.onMiniSettled?.invoke()
            }
            else -> {
                val startExpanded = inputs.expanded.value
                runtime.islandExpanded = startExpanded
                motion.islandW.snapTo(
                    if (startExpanded) metrics.expandedW.value else metrics.collapsedW.value
                )
                motion.islandH.snapTo(
                    if (startExpanded) ExpandedH.value else metrics.collapsedH.value
                )
                motion.islandR.snapTo(
                    if (startExpanded) ExpandedR.value else metrics.collapsedR.value
                )
                motion.appearScale.snapTo(1f)
                motion.appearAlpha.snapTo(1f)
                motion.contentAlpha.snapTo(1f)
                runtime.userMorphEnabled = true
                if (!startExpanded) inputs.onMiniSettled?.invoke()
            }
        }
    }

    LaunchedEffect(runtime.showCheckFocus, runtime.showSoftExit) {
        inputs.onTopPinChanged?.invoke(runtime.showCheckFocus || runtime.showSoftExit)
    }

    fun persistAwarenessSessionState() {
        inputs.onAwarenessSessionStateChanged?.invoke(
            runtime.focusChecksCompleted,
            runtime.lastCheckAtSec,
            runtime.enterAnchorHintShown,
            runtime.browseNearEndHintShown
        )
    }

    fun dismissAttachedAnchorHint() {
        if (runtime.anchorHintText == null && !runtime.runwayPlaying) return
        runtime.anchorHintText = null
        runtime.runwayPlaying = false
        inputs.onAttachedTopInsetChanged?.invoke(0)
    }

    fun playAttachedAnchorHint(hint: String) {
        runtime.anchorHintText = hint
        runtime.runwayPlaying = true
    }

    DisposableEffect(inputs.onRegisterRequestCheckFocus) {
        inputs.onRegisterRequestCheckFocus?.invoke {
            dismissAttachedAnchorHint()
            runtime.showCheckFocus = true
            if (!inputs.expanded.value) inputs.onToggleExpand()
        }
        onDispose {
            inputs.onRegisterRequestCheckFocus?.invoke {}
        }
    }

    DisposableEffect(inputs.onRegisterApplyExternalAwareness) {
        inputs.onRegisterApplyExternalAwareness?.invoke { _ ->
            runtime.focusChecksCompleted =
                (runtime.focusChecksCompleted + 1).coerceAtMost(99)
            runtime.lastCheckAtSec = inputs.sessionSeconds.value
            runtime.showCheckFocus = false
            runtime.showSoftExit = false
            persistAwarenessSessionState()
            true
        }
        onDispose {
            inputs.onRegisterApplyExternalAwareness?.invoke { false }
        }
    }

    fun dismissCheckToOrb(action: String) {
        val mode = metrics.awarenessMode.name.lowercase()
        val index = runtime.focusChecksCompleted
        inputs.onMidSessionAwareness?.invoke("action", action, index, mode)
        runtime.focusChecksCompleted = (runtime.focusChecksCompleted + 1).coerceAtMost(99)
        persistAwarenessSessionState()
        runtime.showCheckFocus = false
        runtime.showSoftExit = false
        if (inputs.expanded.value) inputs.onToggleExpand()
        runtime.lastCheckAtSec = inputs.sessionSeconds.value
        persistAwarenessSessionState()
    }

    LaunchedEffect(
        inputs.sessionSeconds.value,
        inputs.isPaused.value,
        runtime.runwayPlaying,
        runtime.showCheckFocus,
        runtime.islandExpanded,
        inputs.expanded.value,
        shouldPulse,
        metrics.isUrgent,
        metrics.sessionCountdownUrgent,
        metrics.timeLockCountdownUrgent
    ) {
        if (inputs.isPaused.value || runtime.runwayPlaying || runtime.showCheckFocus || runtime.showSoftExit) {
            return@LaunchedEffect
        }
        if (inputs.expanded.value || runtime.islandExpanded) return@LaunchedEffect
        if (shouldPulse || metrics.isUrgent || metrics.sessionCountdownUrgent || metrics.timeLockCountdownUrgent) {
            return@LaunchedEffect
        }
        val sec = inputs.sessionSeconds.value
        val minute = sec / 60L
        if (sec % 60L != 0L) return@LaunchedEffect
        if (minute == runtime.lastFlashedMinute) return@LaunchedEffect
        if (!shouldFlashElapsedMinute(minute)) return@LaunchedEffect
        runtime.lastFlashedMinute = minute
        runtime.timeFlashLabel = formatFocusElapsedFlash(sec)
        runtime.showTimeFlash = true
        delay(TIME_FLASH_HOLD_MS)
        runtime.showTimeFlash = false
    }

    // 30s 锚提示（仅意图回声；出现时条隐）
    LaunchedEffect(
        inputs.sessionSeconds.value,
        inputs.isPaused.value,
        metrics.intentGate,
        inputs.intentKind.value,
        runtime.enterAnchorHintShown,
        runtime.runwayPlaying,
        runtime.showCheckFocus,
        runtime.showSoftExit
    ) {
        if (inputs.isPaused.value) return@LaunchedEffect
        if (runtime.runwayPlaying || runtime.showCheckFocus || runtime.showSoftExit ||
            runtime.showEndConfirmDialog || inputs.expanded.value
        ) {
            return@LaunchedEffect
        }
        val trigger = com.life.mindfulnessapp.domain.model.MidSessionCheckPolicy.shouldTriggerEnterAnchorHint(
            intentGate = metrics.intentGate,
            intentKind = inputs.intentKind.value,
            sessionSeconds = inputs.sessionSeconds.value,
            alreadyShown = runtime.enterAnchorHintShown,
            purpose = inputs.purpose.value,
            hasSessionLimit = inputs.hasSessionLimit.value,
            uiBlocking = false
        )
        if (!trigger) return@LaunchedEffect
        runtime.enterAnchorHintShown = true
        persistAwarenessSessionState()
        playAttachedAnchorHint(
            SessionAwarenessCopy.anchorHint(
                path = metrics.companionPath,
                naming = metrics.collapsedPurpose ?: metrics.requiredIntent
            )
        )
    }

    // 觉察练习开着时：按节奏抛轻问（意图 / 搜索）
    LaunchedEffect(
        inputs.sessionSeconds.value,
        inputs.isPaused.value,
        metrics.intentGate,
        inputs.intentKind.value,
        inputs.sessionLimitMinutes.value,
        inputs.purpose.value,
        inputs.awarenessPracticeEnabled.value,
        inputs.awarenessPracticeGapSec.value
    ) {
        if (inputs.isPaused.value) return@LaunchedEffect
        if (runtime.runwayPlaying || runtime.showCheckFocus || runtime.showSoftExit ||
            runtime.showEndConfirmDialog
        ) {
            return@LaunchedEffect
        }
        val limitMin = inputs.sessionLimitMinutes.value.coerceAtLeast(0)
        val limitSec = limitMin * 60L
        val uiBlocking = inputs.expanded.value || runtime.islandExpanded
        val urgent = shouldPulse || metrics.isUrgent ||
            metrics.sessionCountdownUrgent || metrics.timeLockCountdownUrgent
        val trigger = com.life.mindfulnessapp.domain.model.MidSessionCheckPolicy.shouldTriggerNow(
            intentGate = metrics.intentGate,
            intentKind = inputs.intentKind.value,
            sessionSeconds = inputs.sessionSeconds.value,
            completedChecks = runtime.focusChecksCompleted,
            lastCheckAtSec = runtime.lastCheckAtSec,
            sessionLimitMinutes = limitMin,
            sessionLimitSec = limitSec,
            dailyRemainingSec = inputs.dailyRemainingSeconds.value,
            timeLockEnabled = metrics.timeLock,
            uiBlocking = uiBlocking,
            urgentChrome = urgent,
            purpose = inputs.purpose.value,
            practiceEnabled = inputs.awarenessPracticeEnabled.value,
            gapSec = inputs.awarenessPracticeGapSec.value
        )
        if (!trigger) return@LaunchedEffect
        dismissAttachedAnchorHint()
        runtime.lastCheckAtSec = inputs.sessionSeconds.value
        persistAwarenessSessionState()
        runtime.showCheckFocus = true
        if (!inputs.expanded.value) inputs.onToggleExpand()
    }

    LaunchedEffect(runtime.showDiscoverHint, runtime.userMorphEnabled, runtime.runwayPlaying) {
        if (!runtime.showDiscoverHint || runtime.runwayPlaying || !runtime.userMorphEnabled) return@LaunchedEffect
        delay(420L)
        if (!runtime.showDiscoverHint) return@LaunchedEffect
        delay(3_200L)
        runtime.showDiscoverHint = false
        inputs.onOrbDiscoverHintShown?.invoke()
    }

    LaunchedEffect(runtime.showCheckFocus) {
        if (!runtime.showCheckFocus) return@LaunchedEffect
        val mode = metrics.awarenessMode.name.lowercase()
        inputs.onMidSessionAwareness?.invoke(
            "show",
            null,
            runtime.focusChecksCompleted,
            mode
        )
    }

    LaunchedEffect(runtime.showCheckFocus, runtime.showSoftExit) {
        if (!runtime.showCheckFocus || runtime.showSoftExit) return@LaunchedEffect
        delay(CHECK_ISLAND_AUTO_COLLAPSE_MS)
        if (runtime.showCheckFocus && !runtime.showSoftExit) {
            dismissCheckToOrb(
                com.life.mindfulnessapp.domain.model.MidSessionCheckPolicy.ACTION_DISMISS
            )
        }
    }

    LaunchedEffect(inputs.expanded.value, runtime.userMorphEnabled) {
        if (!runtime.userMorphEnabled) return@LaunchedEffect
        if (inputs.expanded.value) {
            capsuleWakeUp()
            runtime.islandExpanded = true
            runtime.userMorphRunning = true
            try {
                morphIsland(motion, metrics, true)
            } finally {
                runtime.userMorphRunning = false
            }
        } else {
            if (runtime.skipNextUserCollapse) {
                runtime.skipNextUserCollapse = false
                return@LaunchedEffect
            }
            runtime.userMorphRunning = true
            try {
                morphIsland(motion, metrics, false)
            } finally {
                runtime.userMorphRunning = false
                runtime.islandExpanded = false
            }
        }
    }

    LaunchedEffect(
        metrics.collapsedW,
        runtime.islandExpanded,
        runtime.userMorphEnabled,
        runtime.entranceHolding,
        runtime.userMorphRunning
    ) {
        if (!runtime.userMorphEnabled || runtime.islandExpanded ||
            runtime.entranceHolding || runtime.userMorphRunning
        ) {
            return@LaunchedEffect
        }
        if (kotlin.math.abs(motion.islandW.value - metrics.collapsedW.value) > 0.5f) {
            motion.islandW.animateTo(metrics.collapsedW.value, IslandWidthSpring)
        }
    }
    LaunchedEffect(
        metrics.collapsedH,
        metrics.collapsedR,
        runtime.islandExpanded,
        runtime.userMorphEnabled,
        runtime.entranceHolding,
        runtime.userMorphRunning
    ) {
        if (!runtime.userMorphEnabled || runtime.islandExpanded ||
            runtime.entranceHolding || runtime.userMorphRunning
        ) {
            return@LaunchedEffect
        }
        launch {
            if (kotlin.math.abs(motion.islandH.value - metrics.collapsedH.value) > 0.5f) {
                motion.islandH.animateTo(metrics.collapsedH.value, IslandHeightSpring)
            }
        }
        launch {
            if (kotlin.math.abs(motion.islandR.value - metrics.collapsedR.value) > 0.5f) {
                motion.islandR.animateTo(metrics.collapsedR.value, IslandCornerSpring)
            }
        }
    }

    LaunchedEffect(
        metrics.intentGate,
        runtime.islandExpanded,
        runtime.userMorphEnabled,
        runtime.entranceHolding,
        runtime.userMorphRunning
    ) {
        if (runtime.userMorphRunning) return@LaunchedEffect
        if (!runtime.userMorphEnabled && !runtime.entranceHolding && !runtime.islandExpanded) {
            return@LaunchedEffect
        }
        val base = if (runtime.islandExpanded) ExpandedH.value else metrics.collapsedH.value
        val target = base + shellExtraHeight(runtime.islandExpanded, metrics)
        if (kotlin.math.abs(motion.islandH.value - target) > 0.5f) {
            motion.islandH.animateTo(target, IslandHeightSpring)
        }
    }

    // 临期：收起条变色 / 轻震即可，不再自动抬 Decision 岛
    LaunchedEffect(metrics.sessionCountdownUrgent, runtime.userMorphEnabled) {
        if (!metrics.sessionCountdownUrgent || !runtime.userMorphEnabled) {
            if (!metrics.sessionCountdownUrgent) runtime.didUrgentExpand = false
            return@LaunchedEffect
        }
        if (!runtime.didUrgentExpand) {
            runtime.didUrgentExpand = true
            capsuleView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(70)
            capsuleView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            capsuleWakeUp()
        }
    }

    // 展开 Decision / 觉察时收掉贴条锚提示
    LaunchedEffect(
        inputs.expanded.value,
        runtime.showCheckFocus,
        runtime.showSoftExit,
        runtime.showEndConfirmDialog
    ) {
        if (inputs.expanded.value || runtime.showCheckFocus || runtime.showSoftExit ||
            runtime.showEndConfirmDialog
        ) {
            dismissAttachedAnchorHint()
        }
    }
}

@Composable
private fun BindAwayRing(inputs: CapsuleOverlayInputs, runtime: CapsuleOverlayRuntime) {
    val awayRemain = inputs.awayCountdownSeconds.value
    val awayTotal = inputs.awayCountdownTotalSeconds.value.coerceAtLeast(0L)
    val showAwayCountdown = inputs.isPaused.value &&
        awayRemain >= 0L &&
        awayTotal > 0L &&
        !runtime.thisMediaPlaying
    LaunchedEffect(showAwayCountdown, awayTotal) {
        if (!showAwayCountdown || awayTotal <= 0L) {
            runtime.awayRingProgress = 0f
            return@LaunchedEffect
        }
        runtime.awayRingProgress = (awayRemain.toFloat() / awayTotal.toFloat()).coerceIn(0f, 1f)
    }
    LaunchedEffect(showAwayCountdown, awayRemain, awayTotal) {
        if (!showAwayCountdown || awayTotal <= 0L) return@LaunchedEffect
        var anchorRemain = awayRemain.toFloat()
        var anchorMs = SystemClock.elapsedRealtime()
        while (showAwayCountdown) {
            val latestRemain = inputs.awayCountdownSeconds.value
            if (latestRemain < 0L) break
            if (kotlin.math.abs(latestRemain - anchorRemain) > 0.75f) {
                anchorRemain = latestRemain.toFloat()
                anchorMs = SystemClock.elapsedRealtime()
            }
            val elapsedSec = (SystemClock.elapsedRealtime() - anchorMs) / 1000f
            val currentRemain = (anchorRemain - elapsedSec).coerceAtLeast(0f)
            runtime.awayRingProgress = (currentRemain / awayTotal.toFloat()).coerceIn(0f, 1f)
            if (currentRemain <= 0f) break
            delay(16L)
        }
    }
}

@Keep
@Composable
private fun CapsuleIslandShell(
    inputs: CapsuleOverlayInputs,
    runtime: CapsuleOverlayRuntime,
) {
    val metrics = buildCapsuleMetrics(
        inputs,
        rememberTextMeasurer(),
        LocalDensity.current,
        LocalConfiguration.current.screenWidthDp.dp,
        thisMediaPlaying = runtime.thisMediaPlaying,
        foreignPackage = runtime.foreignPackage,
    )
    val motion = runtime.motion ?: return
    BindAwayRing(inputs, runtime)
    val shouldPulse = metrics.timeLock && runtime.countdownMode &&
        !metrics.overLimit && !inputs.isPaused.value
    val shouldBreathe = !inputs.isPaused.value && !metrics.overLimit && !shouldPulse && !metrics.isUrgent
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
    val targetIcon = when {
        metrics.overLimit -> metrics.overLimitColor
        metrics.intentOnly -> metrics.themeConfig.capsuleAccentColor
        else -> urgencyColor(metrics.themeConfig.capsuleAccentColor, metrics.ratio)
    }
    val iconColor by animateColorAsState(
        targetValue = targetIcon,
        animationSpec = tween(800, easing = FastOutSlowInEasing), label = "capsule_icon"
    )
    val effectiveIconColor = if (shouldPulse) iconColor.copy(alpha = pulseAlpha) else iconColor
    val chrome = deriveCapsuleChrome(
        inputs = inputs,
        metrics = metrics,
        runtime = runtime,
        iconColor = iconColor,
        effectiveIconColor = effectiveIconColor,
        pulseAlpha = pulseAlpha,
        breathAlpha = breathAlpha,
        shouldPulse = shouldPulse,
        shouldBreathe = shouldBreathe,
        awayRingProgress = runtime.awayRingProgress,
    )
    // 结束 / 续时确认时只留弹层，避免条与弹窗叠在同一 WRAP_CONTENT 里撑抖
    if (!runtime.showEndConfirmDialog && !runtime.showExtendDialog) {
        CapsuleIslandFrame(inputs, metrics, runtime, motion, chrome)
    }
    CapsuleOverlayDialogs(inputs, metrics, runtime)
}

@Keep
@Composable
private fun CapsuleIslandFrame(
    inputs: CapsuleOverlayInputs,
    metrics: CapsuleMetrics,
    runtime: CapsuleOverlayRuntime,
    motion: CapsuleMotion,
    chrome: CapsuleChromeModel,
) {
    val shellW = motion.islandW.value.dp
    val shellH = motion.islandH.value.dp
    val shellR = motion.islandR.value.dp
    val shellShape = RoundedCornerShape(shellR)
    val pack = inputs.themePack.value
    val baseIsland = when (pack) {
        ThemePack.Night -> IslandBlack
        ThemePack.Day -> IslandLight
        ThemePack.Mist -> MistCardBg
    }
    val userShellOpacity = AppPreferences.normalizeCapsuleShellOpacity(inputs.shellOpacity.value)
    val collapsedFillAlpha = if (chrome.paused) {
        (userShellOpacity * 0.68f).coerceIn(0.28f, 0.72f)
    } else {
        userShellOpacity
    }
    val islandFill = when {
        !runtime.islandExpanded -> when (pack) {
            ThemePack.Night -> Color(0xFF1C1C1E).copy(alpha = collapsedFillAlpha)
            ThemePack.Day -> Color(0xFFF5F5F7).copy(alpha = collapsedFillAlpha)
            ThemePack.Mist -> MistBg.copy(alpha = collapsedFillAlpha)
        }
        else -> baseIsland
    }
    val shellContrastBorder = when {
        !runtime.islandExpanded -> when (pack) {
            ThemePack.Night -> Color.White.copy(alpha = 0.06f + 0.14f * userShellOpacity)
            ThemePack.Day -> Color.Black.copy(alpha = 0.04f + 0.08f * userShellOpacity)
            ThemePack.Mist -> Color(0xFF24302A).copy(alpha = 0.05f + 0.10f * userShellOpacity)
        }
        pack == ThemePack.Night -> Color.White.copy(alpha = if (chrome.paused) 0.52f else 0.40f)
        else -> Color.Black.copy(alpha = if (chrome.paused) 0.32f else 0.22f)
    }
    val shellBorderWidth = if (!runtime.islandExpanded) 0.8.dp else ShellContrastBorderWidth
    val spanW = (metrics.expandedW.value - metrics.collapsedW.value).coerceAtLeast(1f)
    val expandedShellH = ExpandedH.value + shellExtraHeight(true, metrics)
    val spanH = (expandedShellH - metrics.collapsedH.value).coerceAtLeast(1f)
    val expandProgress = maxOf(
        ((motion.islandW.value - metrics.collapsedW.value) / spanW).coerceIn(0f, 1f),
        ((motion.islandH.value - metrics.collapsedH.value) / spanH).coerceIn(0f, 1f)
    )
    val bubbleExpandProgress = if (runtime.ringEntranceActive) {
        val seed = metrics.collapsedH.value
        val span = (metrics.collapsedW.value - seed).coerceAtLeast(1f)
        ((motion.islandW.value - seed) / span).coerceIn(0f, 1f)
    } else {
        1f
    }
    val collapsedContentAlpha = 1f - smoothstep(0.08f, 0.55f, expandProgress)
    val expandedContentAlpha = smoothstep(0.28f, 0.78f, expandProgress)
    val ringChromeAlpha = if (runtime.ringEntranceActive) {
        (1f - smoothstep(0.10f, 0.42f, bubbleExpandProgress)) * collapsedContentAlpha
    } else {
        0f
    }
    val miniCollapsedAlpha = if (runtime.ringEntranceActive) {
        smoothstep(0.18f, 0.58f, bubbleExpandProgress) * collapsedContentAlpha
    } else {
        collapsedContentAlpha
    }
    val bubbleTransformOrigin = TransformOrigin(
        pivotFractionX = (metrics.mini.padH.value + metrics.mini.breathDot.value * 0.5f) /
            metrics.collapsedW.value.coerceAtLeast(1f),
        pivotFractionY = 0.5f
    )
    val mediaPlaying = metrics.thisMediaPlaying
    val foreignPkg = metrics.companionForeignPackage
    val context = LocalContext.current
    val onStopCollapsed: () -> Unit = {
        if (metrics.companionStopIsMusicExit && !metrics.intentGate) {
            inputs.onEndSession(null, null, null, false)
        } else {
            runtime.openPrimaryEndAction()
        }
    }
    val onStopForeign: () -> Unit = {
        val pkg = foreignPkg?.trim().orEmpty()
        if (pkg.isNotEmpty()) {
            BackgroundMediaPauser.pauseActivePlayback(context, pkg)
        }
    }
    val decisionPending = metrics.showExtendOffer ||
        metrics.sessionCountdownUrgent ||
        metrics.timeLockCountdownUrgent
    val hintText = runtime.anchorHintText
    val hintExclusive = hintText != null && !runtime.islandExpanded && !inputs.expanded.value
    val markPaused = chrome.paused || runtime.runwayPlaying
    val contentReveal = motion.contentAlpha.value.coerceIn(0f, 1f)

    // 搜索前 30s：完全不出胶囊
    if (runtime.appearanceDeferred) {
        Box(modifier = Modifier.size(0.dp))
        return
    }

    // 锚提示独占：条隐，只留提示
    if (hintExclusive) {
        Box(
            modifier = Modifier
                .padding(
                    start = CapsuleOuterPadH,
                    end = CapsuleOuterPadH,
                    top = CapsuleOuterPadTop,
                    bottom = CapsuleOuterPadBottom
                )
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            AttachedAnchorHint(
                text = hintText!!,
                isDarkTheme = inputs.isDarkTheme,
                accent = metrics.activeAccent,
                timeFont = metrics.timeFont,
                onHeightChanged = { h ->
                    inputs.onAttachedTopInsetChanged?.invoke(h.coerceAtLeast(0))
                },
                onFinished = {
                    runtime.anchorHintText = null
                    runtime.runwayPlaying = false
                    inputs.onAttachedTopInsetChanged?.invoke(0)
                }
            )
        }
        return
    }

    val blooming = runtime.bloomEntranceActive ||
        motion.bloomHaloAlpha.value > 0.01f ||
        motion.bloomCoreAlpha.value > 0.01f ||
        motion.bloomShellGlow.value > 0.01f
    // pad 固定，进场只动 alpha/scale，绝不改窗口尺寸或 y
    val glowCenterY = (shellH - BloomHaloCanvas) / 2

    Box(
        modifier = Modifier.padding(
            start = CapsuleOuterPadH,
            end = CapsuleOuterPadH,
            top = CapsuleOuterPadTop,
            bottom = CapsuleOuterPadBottom
        ),
        contentAlignment = Alignment.TopCenter
    ) {
        // 光点落成：画在固定外扩内，布局尺寸全程不变
        if (blooming) {
            val glowAccent = metrics.activeAccent
            Box(
                modifier = Modifier
                    .offset(y = glowCenterY)
                    .size(BloomHaloCanvas)
                    .graphicsLayer {
                        scaleX = motion.bloomHaloScale.value
                        scaleY = motion.bloomHaloScale.value
                        alpha = motion.bloomHaloAlpha.value
                    }
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.28f),
                                glowAccent.copy(alpha = 0.16f),
                                glowAccent.copy(alpha = 0.04f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .offset(y = (shellH - BloomCoreSize) / 2)
                    .size(BloomCoreSize)
                    .graphicsLayer {
                        scaleX = motion.bloomCoreScale.value
                        scaleY = motion.bloomCoreScale.value
                        alpha = motion.bloomCoreAlpha.value
                    }
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 1f),
                                Color.White.copy(alpha = 0.75f),
                                glowAccent.copy(alpha = 0.45f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
            if (motion.bloomShellGlow.value > 0.01f) {
                Box(
                    modifier = Modifier
                        .offset(x = (-8).dp, y = (-6).dp)
                        .width(shellW + 16.dp)
                        .height(shellH + 12.dp)
                        .graphicsLayer { alpha = motion.bloomShellGlow.value }
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.22f),
                                    glowAccent.copy(alpha = 0.14f),
                                    Color.Transparent
                                )
                            ),
                            shape = RoundedCornerShape(shellR + 8.dp)
                        )
                )
            }
        }

        Box(
            modifier = if (runtime.ringEntranceActive) {
                Modifier
                    .width(metrics.collapsedW)
                    .height((metrics.collapsedH.value + shellExtraHeight(false, metrics)).dp)
            } else {
                Modifier
            }
        ) {
            Box(
                modifier = Modifier
                    .then(if (runtime.ringEntranceActive) Modifier.align(Alignment.CenterStart) else Modifier)
                    .graphicsLayer {
                        scaleX = motion.appearScale.value
                        scaleY = motion.appearScale.value
                        alpha = motion.appearAlpha.value
                        if (runtime.ringEntranceActive) {
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
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !runtime.showEndConfirmDialog && !runtime.showExtendDialog
                    ) {
                        when {
                            runtime.entranceHolding -> runtime.entranceSkipRequested = true
                            runtime.islandExpanded || inputs.expanded.value -> inputs.onToggleExpand()
                            decisionPending -> {
                                if (runtime.runwayPlaying) {
                                    runtime.anchorHintText = null
                                    runtime.runwayPlaying = false
                                    inputs.onAttachedTopInsetChanged?.invoke(0)
                                }
                                inputs.onToggleExpand()
                            }
                            else -> (inputs.onCompanionActivate ?: inputs.onToggleExpand)()
                        }
                    }
                    .border(shellBorderWidth, shellContrastBorder, shellShape)
                    .then(
                        if (metrics.showAwayCountdown) {
                            val ringRemain = runtime.awayRingProgress
                            val ringCorner = shellR
                            val ringActiveColor = chrome.awayRingActive
                            val ringTrackColor = chrome.awayRingTrack
                            Modifier.drawWithContent {
                                drawContent()
                                val strokeW = 2.6.dp.toPx()
                                val inset = strokeW / 2f + ShellContrastBorderWidth.toPx()
                                val rr = RoundRect(
                                    left = inset,
                                    top = inset,
                                    right = size.width - inset,
                                    bottom = size.height - inset,
                                    cornerRadius = CornerRadius(
                                        (ringCorner.toPx() - inset).coerceAtLeast(0f)
                                    )
                                )
                                val full = Path().apply { addRoundRect(rr) }
                                val measure = PathMeasure().apply { setPath(full, forceClosed = false) }
                                val len = measure.length
                                if (len <= 0f) return@drawWithContent
                                drawPath(
                                    path = full,
                                    color = ringTrackColor,
                                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                                )
                                val remain = ringRemain.coerceIn(0f, 1f)
                                if (remain <= 0.001f) return@drawWithContent
                                val progressPath = Path()
                                val start = len * AWAY_RING_PATH_START_FRACTION
                                val take = len * remain
                                val first = (len - start).coerceAtMost(take)
                                measure.getSegment(start, start + first, progressPath, startWithMoveTo = true)
                                val rest = take - first
                                if (rest > 0f) {
                                    measure.getSegment(0f, rest, progressPath, startWithMoveTo = false)
                                }
                                drawPath(
                                    path = progressPath,
                                    color = ringActiveColor,
                                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                                )
                            }
                        } else {
                            Modifier
                        }
                    )
            ) {
                if (expandedContentAlpha > 0.02f &&
                    chrome.expandBannerMode != ExpandBannerMode.Daily
                ) {
                    CapsuleExpandedLayer(
                        inputs, metrics, runtime, chrome, expandedContentAlpha * contentReveal
                    )
                }
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
                                .size(metrics.mini.breathDot + 17.dp)
                                .border(1.6.dp, chrome.breathDotColor, CircleShape)
                        ) {
                            CapsuleBreathDot(color = chrome.breathDotColor, size = metrics.mini.breathDot)
                        }
                    }
                }
                if (miniCollapsedAlpha > 0.02f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = miniCollapsedAlpha * contentReveal },
                        contentAlignment = if (metrics.companionShowBar) {
                            Alignment.CenterStart
                        } else {
                            Alignment.Center
                        }
                    ) {
                        if (metrics.companionShowBar) {
                            CompanionTimeMiniBar(
                                sessionClockText = metrics.companionSessionLine,
                                todayLineText = metrics.companionSecondLine.orEmpty(),
                                accent = chrome.miniAccent,
                                isDarkTheme = inputs.isDarkTheme,
                                paused = markPaused,
                                urgent = chrome.urgentMini,
                                showStop = metrics.companionShowStop,
                                onStop = onStopCollapsed,
                                onStopBoundsChanged = inputs.onStopHitRectChanged,
                                timeFont = metrics.timeFont,
                                packageName = inputs.appPackageName.value,
                                orb = metrics.orb,
                                bar = metrics.companionBar,
                                musicPlaying = mediaPlaying,
                                foreignPackageName = foreignPkg,
                                onStopForeign = if (foreignPkg.isNullOrBlank()) null else onStopForeign,
                                markStyle = metrics.markStyle,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            CompanionOrbCollapsed(
                                packageName = inputs.appPackageName.value,
                                orb = metrics.orb,
                                paused = markPaused,
                                musicPlaying = mediaPlaying,
                                foreignPackageName = foreignPkg,
                                onStopForeign = if (foreignPkg.isNullOrBlank()) null else onStopForeign,
                                onStopBoundsChanged = inputs.onStopHitRectChanged,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        FocusOrbDiscoverHint(
                            visible = runtime.showDiscoverHint && !chrome.paused && contentReveal > 0.85f,
                            color = metrics.activeAccent.copy(alpha = 0.85f),
                            timeFont = metrics.timeFont,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .offset(y = 18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Keep
@Composable
private fun CapsuleExpandedLayer(
    inputs: CapsuleOverlayInputs,
    metrics: CapsuleMetrics,
    runtime: CapsuleOverlayRuntime,
    chrome: CapsuleChromeModel,
    expandedContentAlpha: Float,
) {
    Box(
        modifier = Modifier
            .width(metrics.expandedW)
            .height(ExpandedH + shellExtraHeight(true, metrics).dp)
            .graphicsLayer { alpha = expandedContentAlpha }
            .padding(horizontal = ExpandedInnerPadH, vertical = ExpandedInnerPadV)
    ) {
        ExpandedBannerContent(
            mode = chrome.expandBannerMode,
            appName = metrics.fullAppName,
            packageName = inputs.appPackageName.value,
            eyebrow = chrome.expandedEyebrow,
            capabilityTint = chrome.capabilityTint,
            primaryLine = chrome.expandedPrimary,
            secondaryLine = chrome.expandedSecondary,
            primaryColor = chrome.expandedPrimaryColor,
            secondaryColor = chrome.expandedSecondaryColor,
            appNameColor = chrome.shellTextSecondary,
            timeFont = metrics.timeFont,
            secondaryIsIntent = chrome.secondaryIsIntent,
            isDarkTheme = inputs.isDarkTheme,
            showReturn = chrome.paused && inputs.onReturnToApp != null,
            onReturn = { inputs.onReturnToApp?.invoke() },
            onStop = {
                if (metrics.companionStopIsMusicExit && !metrics.intentGate) {
                    inputs.onEndSession(null, null, null, false)
                } else {
                    runtime.openPrimaryEndAction()
                }
            },
            showStop = chrome.showEndControl &&
                chrome.expandBannerMode != ExpandBannerMode.Check &&
                chrome.expandBannerMode != ExpandBannerMode.SoftExit,
            showExtendOffer = metrics.showExtendOffer,
            onRequestExtend = { runtime.showExtendDialog = true },
            onStillFocused = {
                val mode = metrics.awarenessMode.name.lowercase()
                val index = runtime.focusChecksCompleted
                inputs.onMidSessionAwareness?.invoke(
                    "action",
                    com.life.mindfulnessapp.domain.model.MidSessionCheckPolicy.ACTION_STILL,
                    index,
                    mode
                )
                runtime.focusChecksCompleted = (runtime.focusChecksCompleted + 1).coerceAtMost(99)
                runtime.showCheckFocus = false
                runtime.showSoftExit = false
                runtime.lastCheckAtSec = inputs.sessionSeconds.value
                inputs.onAwarenessSessionStateChanged?.invoke(
                    runtime.focusChecksCompleted,
                    runtime.lastCheckAtSec,
                    runtime.enterAnchorHintShown,
                    runtime.browseNearEndHintShown
                )
                if (inputs.expanded.value) inputs.onToggleExpand()
            },
            onNotFocused = {
                val mode = metrics.awarenessMode.name.lowercase()
                inputs.onMidSessionAwareness?.invoke(
                    "action",
                    com.life.mindfulnessapp.domain.model.MidSessionCheckPolicy.ACTION_DRIFT,
                    runtime.focusChecksCompleted,
                    mode
                )
                runtime.focusChecksCompleted =
                    (runtime.focusChecksCompleted + 1).coerceAtMost(99)
                runtime.showCheckFocus = false
                runtime.showSoftExit = false
                runtime.lastCheckAtSec = inputs.sessionSeconds.value
                inputs.onAwarenessSessionStateChanged?.invoke(
                    runtime.focusChecksCompleted,
                    runtime.lastCheckAtSec,
                    runtime.enterAnchorHintShown,
                    runtime.browseNearEndHintShown
                )
                // 偏了 = 准备离开：直接结束对照，不再叠 SoftExit
                if (inputs.expanded.value) inputs.onToggleExpand()
                runtime.openPrimaryEndAction()
            },
            onSoftExitEnd = {
                val mode = metrics.awarenessMode.name.lowercase()
                inputs.onMidSessionAwareness?.invoke(
                    "soft_exit",
                    com.life.mindfulnessapp.domain.model.MidSessionCheckPolicy.SOFT_EXIT_END,
                    runtime.focusChecksCompleted,
                    mode
                )
                runtime.focusChecksCompleted =
                    (runtime.focusChecksCompleted + 1).coerceAtMost(99)
                runtime.showSoftExit = false
                runtime.showCheckFocus = false
                inputs.onAwarenessSessionStateChanged?.invoke(
                    runtime.focusChecksCompleted,
                    runtime.lastCheckAtSec,
                    runtime.enterAnchorHintShown,
                    runtime.browseNearEndHintShown
                )
                runtime.openPrimaryEndAction()
            },
            onSoftExitStay = {
                val mode = metrics.awarenessMode.name.lowercase()
                inputs.onMidSessionAwareness?.invoke(
                    "soft_exit",
                    com.life.mindfulnessapp.domain.model.MidSessionCheckPolicy.SOFT_EXIT_STAY,
                    runtime.focusChecksCompleted,
                    mode
                )
                runtime.focusChecksCompleted =
                    (runtime.focusChecksCompleted + 1).coerceAtMost(99)
                runtime.showSoftExit = false
                runtime.showCheckFocus = false
                runtime.lastCheckAtSec = inputs.sessionSeconds.value
                inputs.onAwarenessSessionStateChanged?.invoke(
                    runtime.focusChecksCompleted,
                    runtime.lastCheckAtSec,
                    runtime.enterAnchorHintShown,
                    runtime.browseNearEndHintShown
                )
                if (inputs.expanded.value) inputs.onToggleExpand()
            },
            checkIntentText = metrics.collapsedPurpose ?: metrics.requiredIntent,
            awarenessMode = metrics.awarenessMode,
            companionPath = metrics.companionPath,
            promptVariant = runtime.focusChecksCompleted
        )
    }
}

@Keep
@Composable
private fun CapsuleOverlayDialogs(
    inputs: CapsuleOverlayInputs,
    metrics: CapsuleMetrics,
    runtime: CapsuleOverlayRuntime,
) {
    val shouldHoldPause = runtime.showExtendDialog ||
        (runtime.showEndConfirmDialog &&
            runtime.endConfirmReason == EndConfirmReason.Manual &&
            !inputs.isPaused.value)
    LaunchedEffect(shouldHoldPause) {
        if (!shouldHoldPause) return@LaunchedEffect
        inputs.onConfirmDialogOpen?.invoke()
        try {
            awaitCancellation()
        } finally {
            inputs.onConfirmDialogClose?.invoke()
        }
    }
    if (runtime.showEndConfirmDialog) {
        val purposeText = inputs.purpose.value?.trim().orEmpty()
        val enableCompare = runtime.endConfirmReason == EndConfirmReason.Manual &&
            ComparePolicy.shouldOfferInlineCompare(
                hasIntentGate = purposeText.isNotEmpty(),
                purpose = purposeText,
                durationSeconds = inputs.sessionSeconds.value,
                compareEnabled = inputs.compareEnabled.value,
                compareMinMinutes = inputs.compareMinMinutes.value,
                practiceEnabled = inputs.awarenessPracticeEnabled.value
            )
        EndConfirmDialog(
            reason = runtime.endConfirmReason,
            appName = inputs.appName.value,
            purpose = inputs.purpose.value,
            sessionSeconds = inputs.sessionSeconds.value,
            isDarkTheme = inputs.isDarkTheme,
            useMonoFont = metrics.themeConfig.capsuleUseMonoFont,
            enableCompare = enableCompare,
            awarenessMode = metrics.awarenessMode,
            companionPath = metrics.companionPath,
            onConfirm = { note, level, drift, openToAnchor ->
                runtime.showEndConfirmDialog = false
                inputs.onEndSession(note, level, drift, openToAnchor)
            },
            onDismiss = { runtime.showEndConfirmDialog = false }
        )
    }
    if (runtime.showExtendDialog && inputs.onExtendSession != null) {
        val baseLimitMin = inputs.sessionLimitMinutes.value
        val options = remember(baseLimitMin) {
            com.life.mindfulnessapp.domain.model.SessionLimitPolicy
                .extensionMinuteOptions(baseLimitMin)
        }
        val onExtend = inputs.onExtendSession
        SessionExtendDialog(
            themeConfig = metrics.themeConfig,
            optionsMinutes = options,
            onConfirm = { minutes ->
                runtime.showExtendDialog = false
                onExtend.invoke(minutes)
            },
            onDismiss = { runtime.showExtendDialog = false }
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  展开横幅内容（同一岛壳 · 四态文案）
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ExpandedBannerContent(
    mode: ExpandBannerMode,
    appName: String,
    packageName: String = "",
    eyebrow: String?,
    capabilityTint: Color,
    primaryLine: String,
    secondaryLine: String,
    primaryColor: Color,
    secondaryColor: Color,
    appNameColor: Color,
    timeFont: FontFamily,
    secondaryIsIntent: Boolean,
    isDarkTheme: Boolean = true,
    showReturn: Boolean,
    onReturn: () -> Unit,
    onStop: () -> Unit,
    showStop: Boolean = true,
    showExtendOffer: Boolean = false,
    onRequestExtend: (() -> Unit)? = null,
    onStillFocused: (() -> Unit)? = null,
    onNotFocused: (() -> Unit)? = null,
    onSoftExitEnd: (() -> Unit)? = null,
    onSoftExitStay: (() -> Unit)? = null,
    checkIntentText: String = "",
    awarenessMode: com.life.mindfulnessapp.domain.model.SessionAwarenessMode =
        com.life.mindfulnessapp.domain.model.SessionAwarenessMode.TASK,
    companionPath: CompanionPath = CompanionPath.INTENT,
    promptVariant: Int = 0
) {
    when (mode) {
        ExpandBannerMode.Decision -> ExpandedDecisionBanner(
            appName = appName,
            packageName = packageName,
            eyebrow = eyebrow,
            primaryLine = primaryLine,
            secondaryLine = secondaryLine,
            primaryColor = primaryColor,
            secondaryColor = secondaryColor,
            appNameColor = appNameColor,
            timeFont = timeFont,
            secondaryIsIntent = secondaryIsIntent,
            showReturn = showReturn,
            onReturn = onReturn,
            showStop = showStop,
            onStop = onStop,
            showExtendOffer = showExtendOffer,
            onRequestExtend = onRequestExtend
        )
        ExpandBannerMode.Check -> CheckFocusBanner(
            intentText = checkIntentText,
            accent = capabilityTint,
            primaryColor = primaryColor,
            secondaryColor = secondaryColor,
            timeFont = timeFont,
            onStillFocused = { onStillFocused?.invoke() },
            onNotFocused = { onNotFocused?.invoke() },
            awarenessMode = awarenessMode,
            promptVariant = promptVariant,
            companionPath = companionPath
        )
        ExpandBannerMode.SoftExit -> SoftExitIslandBanner(
            accent = capabilityTint,
            secondaryColor = secondaryColor,
            timeFont = timeFont,
            awarenessMode = awarenessMode,
            companionPath = companionPath,
            isDarkTheme = isDarkTheme,
            onEndSession = { onSoftExitEnd?.invoke() },
            onStay = { onSoftExitStay?.invoke() }
        )
        ExpandBannerMode.Daily -> Unit // 日常信息大条已去掉，展开态由外层立即收回
    }
}

/** 岛内顶行动作：文字 CTA，不加实心底。续时与结束同级。 */
@Composable
private fun ExpandedBannerActions(
    showReturn: Boolean,
    onReturn: () -> Unit,
    showStop: Boolean,
    onStop: () -> Unit,
    stopAccent: Color,
    timeFont: FontFamily,
    showExtend: Boolean = false,
    onExtend: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (showReturn) {
            val interaction = remember { MutableInteractionSource() }
            Text(
                text = "回来",
                modifier = Modifier.clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onReturn
                ),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF81C784),
                maxLines = 1,
                fontFamily = timeFont
            )
        }
        if (showExtend && onExtend != null) {
            val interaction = remember { MutableInteractionSource() }
            Text(
                text = "续一会儿",
                modifier = Modifier.clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onExtend
                ),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFE0B85C),
                maxLines = 1,
                fontFamily = timeFont
            )
        }
        if (showStop) {
            CapsuleStopControl(
                accent = if (showReturn) Color(0xFF8E8E93) else stopAccent,
                onClick = onStop
            )
        }
    }
}

/** 紧急 / 续时：大倒计时；「续一会儿」为顶行文字动作 */
@Composable
private fun ExpandedDecisionBanner(
    appName: String,
    packageName: String = "",
    eyebrow: String?,
    primaryLine: String,
    secondaryLine: String,
    primaryColor: Color,
    secondaryColor: Color,
    appNameColor: Color,
    timeFont: FontFamily,
    secondaryIsIntent: Boolean,
    showReturn: Boolean,
    onReturn: () -> Unit,
    showStop: Boolean,
    onStop: () -> Unit,
    showExtendOffer: Boolean,
    onRequestExtend: (() -> Unit)?
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
            Row(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CapsuleAnchorMark(
                    accent = if (eyebrow != null) primaryColor else appNameColor,
                    size = 14.dp,
                    packageName = packageName
                )
                Text(
                    text = eyebrow?.takeIf { it.isNotBlank() } ?: appName.ifBlank { "这一次" },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (eyebrow != null) primaryColor.copy(alpha = 0.9f) else appNameColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = timeFont
                )
            }
            ExpandedBannerActions(
                showReturn = showReturn,
                onReturn = onReturn,
                showStop = showStop,
                onStop = onStop,
                stopAccent = appNameColor,
                timeFont = timeFont,
                showExtend = showExtendOffer && onRequestExtend != null,
                onExtend = onRequestExtend
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
        if (secondaryLine.isNotBlank()) {
            CapsuleIntentOrTimeText(
                text = secondaryLine,
                isIntent = secondaryIsIntent,
                color = secondaryColor,
                timeFont = timeFont,
                intentSize = 15.sp,
                timeSize = 12.sp,
                emphasizedTime = false
            )
        }
    }
}

/**
 * 展开态意图 / 时间共用排版：
 * - 意图：纯白或纯黑、可读字号、最多两行完整换行（不用点点点）
 * - 时间：主题色（由调用方传入）、等宽强调可选
 */
@Composable
private fun CapsuleIntentOrTimeText(
    text: String,
    isIntent: Boolean,
    color: Color,
    timeFont: FontFamily,
    intentSize: TextUnit,
    timeSize: TextUnit,
    emphasizedTime: Boolean,
    modifier: Modifier = Modifier
) {
    if (text.isBlank()) return
    if (isIntent) {
        Text(
            text = text,
            fontSize = intentSize,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 2,
            softWrap = true,
            overflow = TextOverflow.Clip,
            fontFamily = timeFont,
            lineHeight = (intentSize.value * 1.25f).sp,
            modifier = modifier
        )
    } else {
        Text(
            text = text,
            fontSize = timeSize,
            fontWeight = if (emphasizedTime) FontWeight.Bold else FontWeight.Medium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = if (emphasizedTime) FontFamily.Monospace else timeFont,
            letterSpacing = if (emphasizedTime) 0.4.sp else 0.sp,
            modifier = modifier
        )
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

@Composable
private fun CapsuleHairlineDivider(
    isDarkTheme: Boolean = true,
    height: Dp = 11.dp
) {
    Box(
        modifier = Modifier
            .padding(horizontal = 3.dp)
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
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    Text(
        text = "结束",
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = if (enabled) accent.copy(alpha = 0.88f) else accent.copy(alpha = 0.40f),
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 2.dp, vertical = 4.dp)
    )
}

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

/**
 * 胶囊临近结束时的续时档位对话框。
 * 最长可续 = 原时长 1/3；选项为均分后的最多三档。
 */
@Composable
private fun SessionExtendDialog(
    themeConfig: InterceptThemeConfig,
    optionsMinutes: List<Int>,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember(optionsMinutes) {
        mutableStateOf(optionsMinutes.lastOrNull())
    }
    val canConfirm = selected != null && selected in optionsMinutes
    val scale = remember { Animatable(0.97f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { alpha.animateTo(1f, tween(200, easing = FastOutSlowInEasing)) }
        launch { scale.animateTo(1f, tween(240, easing = FastOutSlowInEasing)) }
    }
    val shellShape = RoundedCornerShape(22.dp)

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
            }
            .shadow(10.dp, shape = shellShape, ambientColor = Color.Black.copy(alpha = 0.18f))
            .clip(shellShape)
            .background(themeConfig.bgColor)
            .border(1.dp, themeConfig.dividerColor.copy(alpha = 0.55f), shellShape)
            .width(280.dp)
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "再续一会儿",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = themeConfig.textPrimary
            )
            Text(
                text = "只能续一次",
                fontSize = 12.sp,
                color = themeConfig.textSecondary,
                textAlign = TextAlign.Center
            )
            if (optionsMinutes.isEmpty()) {
                Text(
                    text = "这次时长太短，无法续时",
                    fontSize = 14.sp,
                    color = themeConfig.textTertiary,
                    textAlign = TextAlign.Center
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    optionsMinutes.forEach { minutes ->
                        val isSelected = selected == minutes
                        Text(
                            text = "${minutes}分",
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isSelected) themeConfig.accentColor else themeConfig.textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) themeConfig.accentColor.copy(alpha = 0.16f)
                                    else Color.Transparent
                                )
                                .clickable { selected = minutes }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (canConfirm) themeConfig.accentColor.copy(alpha = 0.92f)
                        else themeConfig.dividerColor
                    )
                    .clickable(enabled = canConfirm) {
                        selected?.let(onConfirm)
                    }
                    .padding(vertical = 12.dp)
            ) {
                Text(
                    text = if (canConfirm) "续 $selected 分钟" else "选择时长",
                    fontSize = 15.sp,
                    color = if (canConfirm) themeConfig.accentForeground else themeConfig.textTertiary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = "取消",
                fontSize = 13.sp,
                color = themeConfig.textTertiary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}
