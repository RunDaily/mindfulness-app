package com.life.mindfulnessapp.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import android.graphics.RectF
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.life.mindfulnessapp.R
import com.life.mindfulnessapp.domain.model.CapsuleMarkStyle
import com.life.mindfulnessapp.domain.model.CompanionPath
import com.life.mindfulnessapp.domain.model.SessionAwarenessCopy
import com.life.mindfulnessapp.domain.model.SessionAwarenessMode
import com.life.mindfulnessapp.util.BackgroundMediaPauser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 专注圆球视觉直径（壳内） */
val FocusOrbVisualSize = 22.dp
/** 圆球命中/壳尺寸（略大于视觉，减少误触同时保持小） */
val FocusOrbShellSize = 36.dp

/**
 * 悬浮圆球尺寸档（桌面 ⚓️ / 使用中转圈 / 意图门锚标共用同一套）。
 * 由设置「收起态大小」驱动；转速见 [FLOATING_ORB_SPIN_PERIOD_MS]。
 */
data class FloatingOrbMetrics(
    /** 胶囊 / 伴侣条用的外圈（不含桌面光晕） */
    val shell: Dp,
    /** 转圈 App 图标 / 视觉主体 */
    val disk: Dp,
    /** 桌面锚剪影边长 */
    val anchorIcon: Dp,
    /** 桌面心锚命中壳：含软边光晕，拖动与点击都落在这圈里 */
    val desktopHit: Dp,
    /** 桌面玻璃微粒核，光晕从它的边缘向外褪 */
    val desktopJewel: Dp,
)

val FloatingOrbStandard = FloatingOrbMetrics(
    shell = 40.dp,
    disk = 30.dp,
    anchorIcon = 18.dp,
    desktopHit = 52.dp,
    desktopJewel = 32.dp,
)

val FloatingOrbCompact = FloatingOrbMetrics(
    shell = 32.dp,
    disk = 24.dp,
    anchorIcon = 14.dp,
    desktopHit = 40.dp,
    desktopJewel = 26.dp,
)

/** 黑胶转圈一周时长：各态一致 */
const val FLOATING_ORB_SPIN_PERIOD_MS = 10_000

fun floatingOrbMetrics(compact: Boolean): FloatingOrbMetrics =
    if (compact) FloatingOrbCompact else FloatingOrbStandard

/**
 * 意图门时间伴侣条尺寸：与圆球同一设置档联动。
 */
data class CompanionBarMetrics(
    val height: Dp,
    val corner: Dp,
    val padStart: Dp,
    val padEnd: Dp,
    val padV: Dp,
    val sessionSp: Float,
    val todaySp: Float,
    val stopSlot: Dp,
    val gap: Dp,
    /** 条上转圈图标；比桌面圆球核更小，把宽度留给时长 */
    val icon: Dp,
)

val CompanionBarStandard = CompanionBarMetrics(
    height = 50.dp,
    corner = 25.dp,
    padStart = 8.dp,
    padEnd = 6.dp,
    padV = 8.dp,
    sessionSp = 13.5f,
    todaySp = 11f,
    stopSlot = 26.dp,
    gap = 7.dp,
    icon = 22.dp,
)

val CompanionBarCompact = CompanionBarMetrics(
    height = 44.dp,
    corner = 22.dp,
    padStart = 7.dp,
    padEnd = 5.dp,
    padV = 7.dp,
    sessionSp = 12.5f,
    todaySp = 10.5f,
    stopSlot = 22.dp,
    gap = 6.dp,
    icon = 20.dp,
)

val CompanionBarLight = CompanionBarMetrics(
    height = 38.dp,
    corner = 19.dp,
    padStart = 6.dp,
    padEnd = 6.dp,
    padV = 5.dp,
    sessionSp = 13f,
    todaySp = 11f,
    stopSlot = 20.dp,
    gap = 6.dp,
    icon = 18.dp,
)

val CompanionBarLightCompact = CompanionBarMetrics(
    height = 34.dp,
    corner = 17.dp,
    padStart = 5.dp,
    padEnd = 5.dp,
    padV = 4.dp,
    sessionSp = 12f,
    todaySp = 10.5f,
    stopSlot = 18.dp,
    gap = 5.dp,
    icon = 16.dp,
)

fun companionBarMetrics(compact: Boolean, light: Boolean = false): CompanionBarMetrics =
    when {
        light && compact -> CompanionBarLightCompact
        light -> CompanionBarLight
        compact -> CompanionBarCompact
        else -> CompanionBarStandard
    }

/** 当前包是否在出媒体声。无额外权限；视频音轨也会为 true。 */
@Composable
fun rememberPackageMediaPlaying(packageName: String): Boolean {
    val context = LocalContext.current
    var playing by remember(packageName) { mutableStateOf(false) }
    DisposableEffect(packageName, context) {
        if (packageName.isBlank()) {
            playing = false
            return@DisposableEffect onDispose { }
        }
        val stop = BackgroundMediaPauser.observePackageMediaPlayback(
            context,
            packageName
        ) { playing = it }
        onDispose { stop() }
    }
    return playing
}

/** 别的 App 是否在出媒体声。返回那个包名。 */
@Composable
fun rememberForeignMediaPlaying(excludePackage: String): String? {
    val context = LocalContext.current
    var foreign by remember(excludePackage) { mutableStateOf<String?>(null) }
    DisposableEffect(excludePackage, context) {
        if (excludePackage.isBlank()) {
            foreign = null
            return@DisposableEffect onDispose { }
        }
        val stop = BackgroundMediaPauser.observeForeignMediaPlayback(
            context,
            excludePackage
        ) { foreign = it }
        onDispose { stop() }
    }
    return foreign
}

fun overlayAppLabel(context: android.content.Context, packageName: String): String {
    if (packageName.isBlank()) return ""
    return runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName.substringAfterLast('.'))
}

/** 分钟闪现停留 */
const val TIME_FLASH_HOLD_MS = 2_000L
/** 前 N 分钟：每满 1 分钟闪一次 */
const val TIME_FLASH_EVERY_MINUTE_UNTIL_MIN = 15L
/** 之后：仅整 5 分钟闪 */
const val TIME_FLASH_STEP_MINUTES = 5L
/** 跑马灯/定格 → 对照岛间隔 */
const val MARQUEE_TO_CHECK_GAP_MS = 400L

/** 对照岛无操作自动收：短，不占着屏幕等回答 */
val CHECK_ISLAND_AUTO_COLLAPSE_MS: Long
    get() = com.life.mindfulnessapp.domain.model.MidSessionCheckPolicy.AUTO_COLLAPSE_MS

/** @deprecated 见 [com.life.mindfulnessapp.domain.model.MidSessionCheckPolicy.FIRST_DEFAULT_SEC] */
@Deprecated("Use MidSessionCheckPolicy", ReplaceWith("MidSessionCheckPolicy.FIRST_DEFAULT_SEC"))
const val FOCUS_CHECK_FIRST_SEC = 8 * 60L

/** 兼容旧引用 */
@Deprecated("Use MidSessionCheckPolicy", ReplaceWith("MidSessionCheckPolicy.FIRST_DEFAULT_SEC"))
const val FOCUS_CHECK_INTERVAL_SEC = FOCUS_CHECK_FIRST_SEC

/** 是否应在该整分钟触发时长闪现（紧急态由调用方另行跳过） */
fun shouldFlashElapsedMinute(minute: Long): Boolean {
    if (minute < 1L) return false
    if (minute <= TIME_FLASH_EVERY_MINUTE_UNTIL_MIN) return true
    return minute % TIME_FLASH_STEP_MINUTES == 0L
}

/**
 * 第 [completedChecks] 次轻问完成后，到下一次的间隔（秒）。
 * 委托 [com.life.mindfulnessapp.domain.model.MidSessionCheckPolicy]。
 */
fun nextFocusCheckGapSec(
    completedChecks: Int,
    sessionLimitSec: Long = 0L,
    gapSec: Long = com.life.mindfulnessapp.domain.model.MidSessionCheckPolicy.GAP_NORMAL_SEC,
): Long =
    com.life.mindfulnessapp.domain.model.MidSessionCheckPolicy.nextGapSec(
        completedChecks = completedChecks,
        sessionLimitSec = sessionLimitSec,
        gapSec = gapSec
    )

/**
 * 专注圆球（轮廓优先）：
 * - 常驻细外圈：保证在复杂 App 背景上能认出轮廓
 * - 轻呼吸：外圈透明度微变（不做扩散涟漪，避免像加载/通知）
 * - 时长镶边：常驻极弱进度弧（按「本小时内已用」填满一圈）；整分钟短暂加亮
 */
@Composable
fun FocusOrb(
    accent: Color,
    modifier: Modifier = Modifier,
    urgent: Boolean = false,
    paused: Boolean = false,
    dimmed: Boolean = false,
    /** 本次前台已用秒数；驱动镶边弧 */
    elapsedSeconds: Long = 0L,
    /** 整分钟节律：短暂加亮镶边（替代旁侧数字弹窗） */
    rimHighlight: Boolean = false,
    size: Dp = FocusOrbVisualSize
) {
    val orbSize = size
    val infinite = rememberInfiniteTransition(label = "focus_orb")
    val breath by infinite.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.88f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (urgent) 900 else 3200,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_ring_breath"
    )
    val coreBreath by infinite.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (urgent) 900 else 3200,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_core_breath"
    )
    val highlight = remember { Animatable(0f) }
    LaunchedEffect(rimHighlight) {
        if (rimHighlight) {
            highlight.snapTo(0f)
            highlight.animateTo(1f, tween(160, easing = FastOutSlowInEasing))
            delay(TIME_FLASH_HOLD_MS)
            highlight.animateTo(0f, tween(280, easing = FastOutSlowInEasing))
        } else {
            highlight.animateTo(0f, tween(180))
        }
    }

    val coreColor = when {
        paused -> Color(0xFF8E8E93)
        dimmed -> accent.copy(alpha = 0.55f)
        else -> accent
    }
    // 一小时一圈：常驻可读「这小时走了多远」，不制造单次会话倒计时焦虑
    val hourProgress = ((elapsedSeconds % 3600L).toFloat() / 3600f).coerceIn(0f, 1f)
    val hi = highlight.value

    Box(
        modifier = modifier
            .size(orbSize)
            .drawBehind {
                val stroke = 1.6.dp.toPx()
                val inset = stroke / 2f
                val diameter = orbSize.toPx() - stroke
                val left = inset
                val top = inset

                // 轮廓底环（常驻）
                val trackAlpha = when {
                    paused -> 0.28f
                    dimmed -> 0.26f + breath * 0.12f
                    else -> 0.34f + breath * 0.22f
                } + hi * 0.18f
                drawArc(
                    color = coreColor.copy(alpha = trackAlpha.coerceIn(0f, 0.85f)),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(diameter, diameter),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )

                // 时长镶边弧（从 12 点顺时针）
                if (!paused && hourProgress > 0.002f) {
                    val arcAlpha = (0.42f + hi * 0.48f).coerceIn(0.35f, 0.95f)
                    val arcStroke = stroke + hi * 0.7.dp.toPx()
                    drawArc(
                        color = coreColor.copy(alpha = arcAlpha),
                        startAngle = -90f,
                        sweepAngle = 360f * hourProgress,
                        useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(left, top),
                        size = androidx.compose.ui.geometry.Size(diameter, diameter),
                        style = Stroke(width = arcStroke, cap = StrokeCap.Round)
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(orbSize * 0.34f)
                .graphicsLayer {
                    scaleX = if (paused) 1f else coreBreath
                    scaleY = if (paused) 1f else coreBreath
                    alpha = if (paused) 0.7f else 0.92f
                }
                .clip(CircleShape)
                .background(coreColor)
        )
    }
}

/** 整分钟闪现的已用时长标签 */
@Composable
fun FocusOrbTimeFlash(
    label: String,
    color: Color,
    visible: Boolean,
    modifier: Modifier = Modifier,
    timeFont: FontFamily = FontFamily.Default
) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(visible, label) {
        if (visible) {
            alpha.snapTo(0f)
            alpha.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
        } else {
            alpha.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
        }
    }
    if (alpha.value <= 0.02f && !visible) return
    Text(
        text = label,
        modifier = modifier.graphicsLayer { this.alpha = alpha.value },
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        fontFamily = timeFont,
        maxLines = 1,
        softWrap = false
    )
}

/** 默认时间文字胶囊高度（标准档；紧凑见 [CompanionBarCompact]） */
val CompanionMiniHeight = CompanionBarStandard.height
val CompanionMiniCorner = CompanionBarStandard.corner
/** 停止方块命中槽（标准档） */
val CompanionStopSlot = CompanionBarStandard.stopSlot

/** 分钟级短文案：今日行用 */
fun formatCompanionMinutes(seconds: Long): String {
    val total = seconds.coerceAtLeast(0L)
    val m = total / 60L
    return when {
        m <= 0L -> if (total > 0L) "<1分" else "0分"
        m < 60L -> "${m}分"
        else -> {
            val h = m / 60L
            val rm = m % 60L
            if (rm == 0L) "${h}时" else "${h}时${rm}分"
        }
    }
}

/** 叠描边文字：保证浮在任意 App 背景上可读 */
@Composable
fun StrokedCapsuleText(
    text: String,
    fill: Color,
    stroke: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    fontFamily: FontFamily,
    modifier: Modifier = Modifier,
    strokeWidthPx: Float = 4.5f,
    letterSpacing: TextUnit = 0.sp,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Box(modifier = modifier) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            color = stroke,
            maxLines = 1,
            softWrap = false,
            overflow = overflow,
            style = TextStyle(
                drawStyle = Stroke(
                    width = strokeWidthPx,
                    join = StrokeJoin.Round,
                    miter = 2f
                )
            )
        )
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            color = fill,
            maxLines = 1,
            softWrap = false,
            overflow = overflow
        )
    }
}

/** 呼吸圆点：内核 + 柔光晕，活着但不抢戏 */
@Composable
private fun CompanionBreathDot(
    accent: Color,
    paused: Boolean,
    urgent: Boolean,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "companion_breath_dot")
    val period = if (urgent) 860 else 2600
    val halo by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(period, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo"
    )
    val core by infinite.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(period, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "core_scale"
    )
    val ink = when {
        paused -> Color(0xFF8E8E93)
        else -> accent
    }
    Box(
        modifier = modifier.size(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .graphicsLayer {
                    val s = if (paused) 1f else 0.86f + halo * 0.28f
                    scaleX = s
                    scaleY = s
                    alpha = if (paused) 0.18f else 0.18f + halo * 0.32f
                }
                .clip(CircleShape)
                .background(ink.copy(alpha = 0.9f))
        )
        Box(
            modifier = Modifier
                .size(7.dp)
                .graphicsLayer {
                    val s = if (paused) 1f else core
                    scaleX = s
                    scaleY = s
                    alpha = if (paused) 0.62f else 0.82f + halo * 0.18f
                }
                .clip(CircleShape)
                .background(ink)
        )
    }
}

/**
 * App 圆标。默认转圈（桌面球 / 沉浸点）；[spin]=false 时静置（意图陪伴条）。
 * 转速固定 [FLOATING_ORB_SPIN_PERIOD_MS]。[paused] 时停转并略降透明度。
 */
@Composable
fun SpinningAppIconDisk(
    packageName: String,
    size: Dp,
    modifier: Modifier = Modifier,
    paused: Boolean = false,
    spin: Boolean = true,
    periodMs: Int = FLOATING_ORB_SPIN_PERIOD_MS,
    musicPlaying: Boolean = false,
    musicAccent: Color = Color(0xFF34C759),
) {
    val context = LocalContext.current
    val iconBitmap = remember(packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(packageName)
                .toBitmap(128, 128)
                .asImageBitmap()
        }.getOrNull()
    }
    val infinite = rememberInfiniteTransition(label = "spinning_app_disk")
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vinyl_spin"
    )
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = if (paused || !spin) 0f else rotation
                        alpha = if (paused) 0.62f else 1f
                    }
                    .clip(CircleShape)
                    .border(0.6.dp, Color.White.copy(alpha = 0.22f), CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color(0xFF8E8E93).copy(alpha = 0.35f))
                    .border(0.6.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            )
        }
        if (musicPlaying) {
            PlayingEqualizerMark(
                color = musicAccent,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 1.dp, y = 1.dp)
            )
        }
    }
}

/**
 * 时间伴侣锚标：按 [markStyle] 三路差分。
 * - [CapsuleMarkStyle.APP]：App 图标轻呼吸（意图）
 * - [CapsuleMarkStyle.TIMER]：计时器（随意浏览）
 * - [CapsuleMarkStyle.SEARCH]：搜索静置
 * 无 package 且为 APP 时回退品牌锚剪影。
 */
@Composable
fun CapsuleAnchorMark(
    accent: Color,
    modifier: Modifier = Modifier,
    paused: Boolean = false,
    size: Dp = FloatingOrbStandard.disk,
    urgent: Boolean = false,
    packageName: String? = null,
    musicPlaying: Boolean = false,
    markStyle: CapsuleMarkStyle = CapsuleMarkStyle.APP,
) {
    when (markStyle) {
        CapsuleMarkStyle.TIMER -> {
            CompanionTimerMark(
                accent = accent,
                paused = paused,
                urgent = urgent,
                size = size,
                modifier = modifier
            )
            return
        }
        CapsuleMarkStyle.SEARCH -> {
            CompanionSearchMark(
                accent = accent,
                paused = paused,
                size = size,
                modifier = modifier
            )
            return
        }
        CapsuleMarkStyle.APP -> Unit
    }
    val pkg = packageName?.trim().orEmpty()
    if (pkg.isNotEmpty()) {
        val infinite = rememberInfiniteTransition(label = "intent_app_breathe")
        val breath by infinite.animateFloat(
            initialValue = 1f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(3200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "app_breathe"
        )
        SpinningAppIconDisk(
            packageName = pkg,
            size = size,
            paused = paused,
            spin = false,
            musicPlaying = musicPlaying,
            musicAccent = accent,
            modifier = modifier.graphicsLayer {
                if (!paused) {
                    scaleX = breath
                    scaleY = breath
                }
            }
        )
        return
    }
    val infinite = rememberInfiniteTransition(label = "capsule_anchor_mark")
    val period = if (urgent) 900 else 2800
    val pulse by infinite.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(period, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "anchor_pulse"
    )
    val tint = when {
        paused -> Color(0xFF8E8E93).copy(alpha = 0.75f)
        else -> accent.copy(alpha = 0.72f + pulse * 0.28f)
    }
    Icon(
        painter = painterResource(id = R.drawable.ic_stat_anchor),
        contentDescription = null,
        modifier = modifier.size(size * 0.6f),
        tint = tint
    )
}

@Composable
private fun CompanionTimerMark(
    accent: Color,
    paused: Boolean,
    urgent: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val tint = when {
        paused -> Color(0xFF8E8E93).copy(alpha = 0.7f)
        urgent -> Color(0xFFE0B85C)
        else -> accent.copy(alpha = 0.88f)
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (paused) 0.04f else 0.06f))
            .border(0.7.dp, Color.White.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Timer,
            contentDescription = null,
            modifier = Modifier.size(size * 0.52f),
            tint = tint
        )
    }
}

@Composable
private fun CompanionSearchMark(
    accent: Color,
    paused: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val tint = if (paused) {
        Color(0xFF8E8E93).copy(alpha = 0.7f)
    } else {
        accent.copy(alpha = 0.88f)
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (paused) 0.04f else 0.06f))
            .border(0.7.dp, Color.White.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            modifier = Modifier.size(size * 0.5f),
            tint = tint
        )
    }
}

@Composable
internal fun PlayingEqualizerMark(
    color: Color,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "playing_eq")
    val a by infinite.animateFloat(
        initialValue = 0.32f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(260, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq_a"
    )
    val b by infinite.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(320, delayMillis = 70, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq_b"
    )
    val c by infinite.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(290, delayMillis = 40, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq_c"
    )
    Row(
        modifier = modifier
            .size(13.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.78f))
            .padding(horizontal = 2.4.dp, vertical = 2.2.dp),
        horizontalArrangement = Arrangement.spacedBy(1.15.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Bottom
    ) {
        EqBar(level = a, color = color)
        EqBar(level = b, color = color)
        EqBar(level = c, color = color)
    }
}

@Composable
private fun EqBar(level: Float, color: Color) {
    Box(
        modifier = Modifier
            .width(1.45.dp)
            .fillMaxHeight(0.28f + 0.72f * level.coerceIn(0f, 1f))
            .clip(RoundedCornerShape(0.8.dp))
            .background(color)
    )
}

/**
 * 停止方块：经典「停」形，圆角略收，右侧独立热区。
 * 点按走确认框，不直接踢出。
 * 热区用屏幕坐标上报，供 Window 拖拽层放行点击。
 */
@Composable
fun CompanionStopSquare(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    slotSize: Dp = CompanionStopSlot,
    onBoundsChanged: ((RectF?) -> Unit)? = null
) {
    val stop = Color(0xFFE5484D)
    val hostView = LocalView.current
    val iconSize = (slotSize * 0.46f).coerceAtLeast(10.dp)
    DisposableEffect(Unit) {
        onDispose { onBoundsChanged?.invoke(null) }
    }
    Box(
        modifier = modifier
            .size(slotSize)
            .semantics {
                contentDescription = "结束本次"
                role = Role.Button
            }
            .onGloballyPositioned { coords ->
                if (!enabled) {
                    onBoundsChanged?.invoke(null)
                    return@onGloballyPositioned
                }
                // Overlay 窗的 boundsInWindow 相对窗口；拖拽层用 rawX/Y，必须换算到屏幕
                val topLeft = try {
                    coords.localToScreen(Offset.Zero)
                } catch (_: Throwable) {
                    val loc = IntArray(2)
                    hostView.getLocationOnScreen(loc)
                    val win = coords.localToWindow(Offset.Zero)
                    Offset(loc[0] + win.x, loc[1] + win.y)
                }
                val w = coords.size.width.toFloat()
                val h = coords.size.height.toFloat()
                val pad = 8f
                onBoundsChanged?.invoke(
                    RectF(
                        topLeft.x - pad,
                        topLeft.y - pad,
                        topLeft.x + w + pad,
                        topLeft.y + h + pad
                    )
                )
            }
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    if (enabled) Color.White.copy(alpha = 0.08f)
                    else Color.White.copy(alpha = 0.04f)
                )
        )
        Box(
            modifier = Modifier
                .size(iconSize)
                .clip(RoundedCornerShape(3.dp))
                .background(
                    if (enabled) stop.copy(alpha = 0.92f)
                    else stop.copy(alpha = 0.38f)
                )
        )
    }
}

/**
 * 时间文字胶囊（默认态）：
 * 左：三路锚标 + 本次时钟；有次行时再跟今日 / 意图。
 * 尺寸由 [orb] / [bar] 与设置「收起态大小」联动。
 */
@Composable
fun CompanionTimeMiniBar(
    sessionClockText: String,
    todayLineText: String,
    accent: Color,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier,
    paused: Boolean = false,
    urgent: Boolean = false,
    showStop: Boolean = false,
    onStop: (() -> Unit)? = null,
    onStopBoundsChanged: ((RectF?) -> Unit)? = null,
    timeFont: FontFamily = FontFamily.Default,
    packageName: String? = null,
    orb: FloatingOrbMetrics = FloatingOrbStandard,
    bar: CompanionBarMetrics = CompanionBarStandard,
    musicPlaying: Boolean = false,
    foreignPackageName: String? = null,
    onStopForeign: (() -> Unit)? = null,
    markStyle: CapsuleMarkStyle = CapsuleMarkStyle.APP,
) {
    val fill = when {
        paused -> if (isDarkTheme) Color(0xFFD1D1D6) else Color(0xFF3A3A3C)
        isDarkTheme -> Color(0xFFF5F5F7)
        else -> Color(0xFF1C1C1E)
    }
    val stroke = if (isDarkTheme) {
        Color.Black.copy(alpha = 0.28f)
    } else {
        Color.White.copy(alpha = 0.38f)
    }
    val todayFill = fill.copy(alpha = if (paused) 0.68f else 0.70f)
    val hasSecondary = todayLineText.isNotBlank()
    var stopRect by remember { mutableStateOf<RectF?>(null) }
    var foreignRect by remember { mutableStateOf<RectF?>(null) }
    fun emitHit() {
        onStopBoundsChanged?.invoke(unionOverlayHitRects(stopRect, foreignRect))
    }
    val foreignPkg = foreignPackageName?.trim().orEmpty()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = bar.padStart,
                end = bar.padEnd,
                top = bar.padV,
                bottom = bar.padV
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(bar.gap)
    ) {
        CapsuleAnchorMark(
            accent = accent,
            paused = paused,
            urgent = urgent,
            size = bar.icon,
            packageName = packageName,
            musicPlaying = musicPlaying,
            markStyle = markStyle
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = if (hasSecondary) {
                Arrangement.spacedBy(1.dp)
            } else {
                Arrangement.Center
            }
        ) {
            StrokedCapsuleText(
                text = sessionClockText,
                fill = fill,
                stroke = stroke,
                fontSize = bar.sessionSp.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                strokeWidthPx = 1.15f,
                letterSpacing = 0.15.sp,
                overflow = TextOverflow.Clip
            )
            if (hasSecondary) {
                StrokedCapsuleText(
                    text = todayLineText,
                    fill = todayFill,
                    stroke = stroke.copy(alpha = 0.75f),
                    fontSize = bar.todaySp.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = timeFont,
                    strokeWidthPx = 0.95f,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (foreignPkg.isNotEmpty() && onStopForeign != null) {
            CompanionForeignMark(
                packageName = foreignPkg,
                size = (bar.icon * 0.72f).coerceAtLeast(16.dp),
                onClick = onStopForeign,
                onBoundsChanged = {
                    foreignRect = it
                    emitHit()
                }
            )
        } else {
            LaunchedEffect(foreignPkg) {
                foreignRect = null
                emitHit()
            }
        }
        if (showStop && onStop != null) {
            CompanionStopSquare(
                onClick = onStop,
                enabled = true,
                slotSize = bar.stopSlot,
                onBoundsChanged = {
                    stopRect = it
                    emitHit()
                }
            )
        } else {
            LaunchedEffect(showStop) {
                stopRect = null
                emitHit()
            }
        }
    }
}

@Composable
fun CompanionOrbCollapsed(
    packageName: String,
    orb: FloatingOrbMetrics,
    paused: Boolean,
    musicPlaying: Boolean,
    foreignPackageName: String?,
    onStopForeign: (() -> Unit)?,
    onStopBoundsChanged: ((RectF?) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val foreignPkg = foreignPackageName?.trim().orEmpty()
    Box(
        modifier = modifier.size(orb.shell),
        contentAlignment = Alignment.Center
    ) {
        SpinningAppIconDisk(
            packageName = packageName,
            size = orb.disk,
            paused = paused,
            musicPlaying = musicPlaying
        )
        if (foreignPkg.isNotEmpty() && onStopForeign != null) {
            CompanionForeignMark(
                packageName = foreignPkg,
                size = (orb.disk * 0.46f).coerceAtLeast(14.dp),
                onClick = onStopForeign,
                onBoundsChanged = onStopBoundsChanged,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        } else {
            LaunchedEffect(foreignPkg) { onStopBoundsChanged?.invoke(null) }
        }
    }
}

/** 意图门轻提示：别的 App 还在播。默认继续，不自动停。 */
@Composable
fun ForeignPlaybackGateHint(
    excludePackage: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    val foreignPkg = rememberForeignMediaPlaying(excludePackage)
    var dismissed by remember(excludePackage) { mutableStateOf(false) }
    val pkg = foreignPkg?.trim().orEmpty()
    if (pkg.isEmpty() || dismissed) return
    val label = remember(pkg) { overlayAppLabel(context, pkg) }
    val iconBitmap = remember(pkg) {
        runCatching {
            context.packageManager.getApplicationIcon(pkg)
                .toBitmap(72, 72)
                .asImageBitmap()
        }.getOrNull()
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                )
            }
            PlayingEqualizerMark(
                color = Color(0xFF34C759),
                modifier = Modifier.offset(x = 2.dp, y = 2.dp)
            )
        }
        Text(
            text = "${label.ifBlank { "后台" }}还在播",
            color = Color(0xFFE8EDE6).copy(alpha = 0.88f),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "继续",
            color = Color(0xFF5C655E),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = enabled) { dismissed = true }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
        Text(
            text = "先停掉",
            color = Color(0xFF3D7A4A),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = enabled) {
                    BackgroundMediaPauser.pauseActivePlayback(context, pkg)
                    dismissed = true
                }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun CompanionForeignMark(
    packageName: String,
    size: Dp,
    onClick: () -> Unit,
    onBoundsChanged: ((RectF?) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val hostView = LocalView.current
    val iconBitmap = remember(packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(packageName)
                .toBitmap(96, 96)
                .asImageBitmap()
        }.getOrNull()
    }
    DisposableEffect(Unit) {
        onDispose { onBoundsChanged?.invoke(null) }
    }
    Box(
        modifier = modifier
            .size(size)
            .semantics {
                contentDescription = "停掉后台播放"
                role = Role.Button
            }
            .onGloballyPositioned { coords ->
                val topLeft = try {
                    coords.localToScreen(Offset.Zero)
                } catch (_: Throwable) {
                    val loc = IntArray(2)
                    hostView.getLocationOnScreen(loc)
                    val win = coords.localToWindow(Offset.Zero)
                    Offset(loc[0] + win.x, loc[1] + win.y)
                }
                val pad = 8f
                onBoundsChanged?.invoke(
                    RectF(
                        topLeft.x - pad,
                        topLeft.y - pad,
                        topLeft.x + coords.size.width + pad,
                        topLeft.y + coords.size.height + pad
                    )
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.BottomEnd
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .border(0.6.dp, Color.White.copy(alpha = 0.28f), CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
            )
        }
        PlayingEqualizerMark(
            color = Color(0xFF34C759),
            modifier = Modifier.offset(x = 1.dp, y = 1.dp)
        )
    }
}

private fun unionOverlayHitRects(a: RectF?, b: RectF?): RectF? {
    if (a == null) return b
    if (b == null) return a
    return RectF(a).apply { union(b) }
}

/** 进行中轻确认：事务问「还在做吗」，搜索问「还在搜吗」，冲动问「还要继续吗」 */
@Composable
fun CheckFocusBanner(
    intentText: String,
    accent: Color,
    primaryColor: Color,
    secondaryColor: Color,
    timeFont: FontFamily,
    onStillFocused: () -> Unit,
    onNotFocused: () -> Unit,
    modifier: Modifier = Modifier,
    awarenessMode: SessionAwarenessMode = SessionAwarenessMode.TASK,
    promptVariant: Int = 0,
    companionPath: CompanionPath = CompanionPath.INTENT,
) {
    val intent = intentText.trim().ifBlank {
        when (companionPath) {
            CompanionPath.SEARCH -> "这次搜索"
            CompanionPath.BROWSE -> "这一次"
            CompanionPath.INTENT -> if (awarenessMode == SessionAwarenessMode.URGE) "这一次" else "这件事"
        }
    }
    val prompt = SessionAwarenessCopy.midCheckPrompt(
        mode = awarenessMode,
        naming = intentText,
        variant = promptVariant,
        path = companionPath
    )
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = prompt,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent.copy(alpha = 0.92f),
                fontFamily = timeFont,
                maxLines = 1
            )
            if (companionPath == CompanionPath.INTENT && awarenessMode == SessionAwarenessMode.TASK) {
                Text(
                    text = intent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryColor,
                    fontFamily = timeFont,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (companionPath != CompanionPath.SEARCH || intentText.trim().isNotEmpty()) {
                Text(
                    text = intent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = secondaryColor,
                    fontFamily = timeFont,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = SessionAwarenessCopy.midCheckYes(awarenessMode, companionPath),
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onStillFocused
                    )
                    .padding(vertical = 4.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                fontFamily = timeFont
            )
            Text(
                text = SessionAwarenessCopy.midCheckNo(awarenessMode, companionPath),
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onNotFocused
                    )
                    .padding(vertical = 4.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = secondaryColor,
                fontFamily = timeFont
            )
        }
    }
}

/**
 * 未专注轻确认：留在岛内，与 [CheckFocusBanner] 同一套骨架。
 * 顶行问句 → 底行文字动作（不另开卡片、不用实心底）。
 */
@Composable
fun SoftExitIslandBanner(
    accent: Color,
    @Suppress("UNUSED_PARAMETER") secondaryColor: Color,
    timeFont: FontFamily,
    onEndSession: () -> Unit,
    onStay: () -> Unit,
    modifier: Modifier = Modifier,
    awarenessMode: SessionAwarenessMode = SessionAwarenessMode.TASK,
    companionPath: CompanionPath = CompanionPath.INTENT,
    isDarkTheme: Boolean = true,
) {
    // 不跟意图色 / 能力色抢主文：主句用壳字色，强调只留给「结束本次」
    val titleInk = if (isDarkTheme) {
        Color(0xFFF2F2F7).copy(alpha = 0.94f)
    } else {
        Color(0xFF1C1C1E).copy(alpha = 0.94f)
    }
    val bodyInk = if (isDarkTheme) {
        Color(0xFFF2F2F7).copy(alpha = 0.58f)
    } else {
        Color(0xFF1C1C1E).copy(alpha = 0.55f)
    }
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = SessionAwarenessCopy.softExitTitle(awarenessMode, companionPath),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = titleInk,
                fontFamily = timeFont,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = SessionAwarenessCopy.softExitBody(awarenessMode, companionPath),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = bodyInk,
                fontFamily = timeFont,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = SessionAwarenessCopy.softExitStay(),
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onStay
                    )
                    .padding(vertical = 4.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = bodyInk,
                fontFamily = timeFont
            )
            Text(
                text = SessionAwarenessCopy.softExitEnd(),
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onEndSession
                    )
                    .padding(vertical = 4.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent.copy(alpha = 0.95f),
                fontFamily = timeFont
            )
        }
    }
}

/** 会话秒 →「N 分钟」或 mm:ss（不足 1 分钟仍显示 mm:ss） */
fun formatFocusElapsedFlash(sessionSeconds: Long): String {
    val sec = sessionSeconds.coerceAtLeast(0L)
    val minutes = sec / 60L
    return if (minutes >= 1L) {
        "${minutes} 分钟"
    } else {
        String.format("%d:%02d", minutes, sec % 60L)
    }
}

/** 首次发现提示：轻点可看详情 */
@Composable
fun FocusOrbDiscoverHint(
    visible: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    timeFont: FontFamily = FontFamily.Default
) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            alpha.snapTo(0f)
            alpha.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
            delay(2_800L)
            alpha.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
        } else {
            alpha.snapTo(0f)
        }
    }
    if (alpha.value <= 0.02f && !visible) return
    Text(
        text = "轻点可看详情",
        modifier = modifier.graphicsLayer { this.alpha = alpha.value },
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        color = color,
        fontFamily = timeFont,
        maxLines = 1,
        softWrap = false
    )
}
