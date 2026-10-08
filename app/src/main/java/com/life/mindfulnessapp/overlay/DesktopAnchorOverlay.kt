package com.life.mindfulnessapp.overlay

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import com.life.mindfulnessapp.domain.model.ScheduleOrbPolicy
import com.life.mindfulnessapp.domain.model.ThemePack
import com.life.mindfulnessapp.domain.model.TimeRulerPeriod
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.theme.chrome
import com.life.mindfulnessapp.util.AppUsageFormat
import kotlinx.coroutines.delay

private const val PULSE_SHORT_SESSION_SEC = 60L

/** 桌面心锚玻璃微粒核 — 标准档；实际以 [floatingOrbMetrics] 为准 */
val DesktopAnchorVisualSize = FloatingOrbStandard.desktopJewel
/** 桌面命中壳（含软边光晕）— 标准档 */
val DesktopAnchorShellSize = FloatingOrbStandard.desktopHit

private val PanelCorner = RoundedCornerShape(24.dp)
private val PanelWidthMin = 292.dp
private val PanelWidthMax = 320.dp
private val PanelScrimAlpha = 0.16f

/** 静止后开始让路（接近 iOS 辅助触控的空闲节奏） */
private const val DESKTOP_ORB_IDLE_DELAY_MS = 3_000L
private const val DESKTOP_ORB_IDLE_ALPHA = 0.50f
private const val DESKTOP_ORB_IDLE_IN_APP_ALPHA = 0.78f
private const val DESKTOP_ORB_PANEL_ALPHA = 0.78f

private data class DesktopPanelPlacement(
    val x: Int,
    val y: Int,
    val origin: TransformOrigin,
)

private fun computeDesktopPanelPlacement(
    orb: DesktopAnchorOrbAnchor,
    panel: IntSize,
    gapPx: Int,
    marginPx: Int,
): DesktopPanelPlacement {
    val pw = panel.width.coerceAtLeast(1)
    val ph = panel.height.coerceAtLeast(1)
    val parentW = orb.parentWidth.coerceAtLeast(pw + marginPx * 2)
    val parentH = orb.parentHeight.coerceAtLeast(ph + marginPx * 2)
    val orbCx = orb.x + orb.width / 2f
    val orbCy = orb.y + orb.height / 2f
    val onRight = orbCx >= parentW / 2f
    val minX = marginPx
    val maxX = (parentW - marginPx - pw).coerceAtLeast(minX)
    val x = if (onRight) {
        (orb.x - gapPx - pw).coerceIn(minX, maxX)
    } else {
        (orb.x + orb.width + gapPx).coerceIn(minX, maxX)
    }
    val originX = if (onRight) 1f else 0f
    val minY = maxOf(marginPx, orb.topInset)
    val maxY = (parentH - maxOf(marginPx, orb.bottomInset) - ph).coerceAtLeast(minY)
    val y = (orbCy - ph / 2f).toInt().coerceIn(minY, maxY)
    val originY = ((orbCy - y) / ph.toFloat()).coerceIn(0.08f, 0.92f)
    return DesktopPanelPlacement(x, y, TransformOrigin(originX, originY))
}

private enum class DeskNav { Hub, Pulse, TodayGlance }

private data class DeskPalette(
    val ink: Color,
    val muted: Color,
    val dim: Color,
    val line: Color,
    val fieldBg: Color,
    val panelBrush: Brush,
    val edgeBrush: Brush,
    val accent: Color,
    val isDark: Boolean,
)

/**
 * 桌面心锚：自由态微粒 + 就地展开的 Hub / App Pulse。
 *
 * - 桌面 Hub：此刻（听歌/锁，有才有）→ 今日（总时长 · 热力 · 四枚芯片）→ 离开（字）
 * - 点图标同壳下钻一次 Pulse；监控 Pulse 脚有「今日旁路」轻览，不把 Hub 搬进 App 内
 * - 圆球是玻璃微粒：命中区大于视觉；点开时面板从圆球长出；浅压暗收回
 * - 有意图门：微粒隐藏；点陪伴条展开同一套玻璃 Pulse（含今日旁路）
 */
/**
 * 桌面心锚圆球（位置由悬浮窗锁定；开合面板不改此窗几何）。
 * 空闲后降透明让路；拖动 / 开面板 / 触碰会醒回来。
 */
@Composable
fun DesktopAnchorOverlay(
    panelOpen: State<Boolean>,
    inAppGlance: State<DesktopAnchorInAppGlance?>,
    miniCompact: State<Boolean>,
    themePack: ThemePack = ThemePack.Night,
    isDarkTheme: Boolean = themePack.isDark,
    dragging: State<Boolean>,
    wakeSeq: State<Int>,
    onTogglePanel: () -> Unit,
) {
    val accent = themePack.chrome().accent
    val open = panelOpen.value
    val inApp = inAppGlance.value
    val inAppActive = inApp != null
    val isDragging = dragging.value
    val musicPackage = rememberForeignMediaPlaying(LocalContext.current.packageName)
    val orb = floatingOrbMetrics(miniCompact.value)
    val idleAlpha = remember { Animatable(1f) }
    var visuallyIdle by remember { mutableStateOf(false) }

    LaunchedEffect(wakeSeq.value, isDragging, open, inAppActive) {
        visuallyIdle = false
        val heldTarget = when {
            isDragging -> 1f
            open -> DESKTOP_ORB_PANEL_ALPHA
            else -> 1f
        }
        if (isDragging || open) {
            idleAlpha.animateTo(heldTarget, tween(180, easing = FastOutSlowInEasing))
            return@LaunchedEffect
        }
        if (idleAlpha.value < 0.98f) {
            idleAlpha.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
        }
        delay(DESKTOP_ORB_IDLE_DELAY_MS)
        visuallyIdle = true
        val dim = if (inAppActive) DESKTOP_ORB_IDLE_IN_APP_ALPHA else DESKTOP_ORB_IDLE_ALPHA
        idleAlpha.animateTo(dim, tween(480, easing = FastOutSlowInEasing))
    }

    DesktopAnchorOrb(
        accent = accent,
        isDarkTheme = isDarkTheme,
        orb = orb,
        panelOpen = open,
        idle = visuallyIdle && !open && !isDragging,
        dragging = isDragging,
        contentAlpha = idleAlpha.value,
        inAppPackageName = inApp?.packageName,
        musicPackageName = musicPackage,
        onClick = onTogglePanel
    )
}

/**
 * 全屏面板层：浅压暗 + 卡片从圆球长出。外点 / 返回只关面板，不透传。
 */
@Composable
fun DesktopAnchorPanelOverlay(
    panelOpen: State<Boolean>,
    topApps: State<List<DesktopAnchorTopApp>>,
    hourlySeconds: State<LongArray>,
    todayTotalSeconds: State<Long>,
    todayEnterCount: State<Int>,
    todayDismissCount: State<Int>,
    activeLock: State<ScheduleOrbPolicy.ActiveGlance?>,
    inAppGlance: State<DesktopAnchorInAppGlance?>,
    pulseGlance: State<DesktopAnchorPulseGlance?>,
    orbAnchor: State<DesktopAnchorOrbAnchor>,
    intentSession: State<IntentSessionHubGlance?>? = null,
    themePack: ThemePack = ThemePack.Night,
    isDarkTheme: Boolean = themePack.isDark,
    onDismiss: () -> Unit,
    onOpenApp: () -> Unit,
    onOpenSchedule: () -> Unit = {},
    onOpenUsageLog: () -> Unit = {},
    onRequestPulse: (String) -> Unit,
    onClearPulse: () -> Unit = {},
    onOpenFullReport: (String) -> Unit = {},
    onAddToPlan: (String) -> Unit = {},
    onLaunchSearch: (entryId: String, query: String) -> Unit = { _, _ -> },
    onNeedImeFocus: (Boolean) -> Unit = {},
    onNeedClipboardFocus: () -> Unit = {},
    onEndSession: () -> Unit = {},
    onBindBackHandler: ((() -> Boolean)?) -> Unit = {},
    miniCompact: State<Boolean>? = null,
    onSetMiniCompact: (Boolean) -> Unit = {},
) {
    val accent = themePack.chrome().accent
    val open = panelOpen.value
    val inApp = inAppGlance.value
    val session = intentSession?.value
    val context = LocalContext.current
    val playingPackage = rememberForeignMediaPlaying(context.packageName)
    var nav by remember { mutableStateOf(DeskNav.Hub) }
    val contentAlpha = remember { Animatable(0f) }
    val density = LocalDensity.current
    val gapPx = with(density) { 10.dp.roundToPx() }
    val marginPx = with(density) { 12.dp.roundToPx() }
    val estimated = with(density) {
        IntSize(306.dp.roundToPx(), 292.dp.roundToPx())
    }
    var measured by remember { mutableStateOf(IntSize.Zero) }
    val orb = orbAnchor.value
    val panelSize = if (measured.width > 0) measured else estimated
    val placement = remember(orb, panelSize, gapPx, marginPx) {
        computeDesktopPanelPlacement(orb, panelSize, gapPx, marginPx)
    }
    val inAppLive = inApp != null

    LaunchedEffect(open, inApp?.packageName) {
        if (!open) {
            nav = DeskNav.Hub
            onClearPulse()
            contentAlpha.snapTo(0f)
            onNeedImeFocus(false)
        } else {
            if (inApp != null) {
                onRequestPulse(inApp.packageName)
                nav = DeskNav.Pulse
            } else {
                nav = DeskNav.Hub
            }
            contentAlpha.snapTo(0f)
            delay(60)
            contentAlpha.animateTo(1f, tween(200, easing = FastOutSlowInEasing))
        }
    }
    LaunchedEffect(nav, open) {
        onNeedImeFocus(false)
    }
    DisposableEffect(open, nav, inAppLive) {
        val handler: () -> Boolean = {
            when {
                !open -> false
                nav == DeskNav.TodayGlance -> {
                    nav = DeskNav.Pulse
                    true
                }
                nav == DeskNav.Pulse && inAppLive -> {
                    onDismiss()
                    true
                }
                nav == DeskNav.Pulse -> {
                    nav = DeskNav.Hub
                    onClearPulse()
                    true
                }
                else -> {
                    onDismiss()
                    true
                }
            }
        }
        onBindBackHandler(handler)
        onDispose { onBindBackHandler(null) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(140))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = PanelScrimAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(placement.x, placement.y) }
                .onSizeChanged { measured = it }
        ) {
            AnimatedVisibility(
                visible = open,
                enter = fadeIn(tween(140)) +
                    scaleIn(
                        animationSpec = spring(
                            dampingRatio = 0.82f,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        initialScale = 0.86f,
                        transformOrigin = placement.origin
                    ),
                exit = fadeOut(tween(120)) +
                    scaleOut(
                        animationSpec = spring(
                            dampingRatio = 0.92f,
                            stiffness = Spring.StiffnessMedium
                        ),
                        targetScale = 0.92f,
                        transformOrigin = placement.origin
                    )
            ) {
                Box(
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                ) {
                    DeskGlassShell(
                        isDarkTheme = isDarkTheme,
                        accent = accent,
                        glowOrigin = placement.origin,
                        modifier = Modifier
                            .widthIn(min = PanelWidthMin, max = PanelWidthMax)
                            .heightIn(max = 460.dp)
                    ) {
                        Box(modifier = Modifier.graphicsLayer { alpha = contentAlpha.value }) {
                            AnimatedContent(
                                targetState = nav,
                                transitionSpec = {
                                    val forward = targetState.ordinal > initialState.ordinal
                                    val slide = 28
                                    val enter = fadeIn(tween(200, easing = FastOutSlowInEasing)) +
                                        slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) {
                                            if (forward) slide else -slide
                                        }
                                    val exit = fadeOut(tween(160)) +
                                        slideOutHorizontally(tween(200, easing = FastOutSlowInEasing)) {
                                            if (forward) -slide else slide
                                        }
                                    (enter togetherWith exit).using(
                                        SizeTransform(clip = true) { _, _ ->
                                            tween(260, easing = FastOutSlowInEasing)
                                        }
                                    )
                                },
                                label = "desk_nav"
                            ) { page ->
                                when (page) {
                                    DeskNav.Hub -> DesktopAnchorHubPage(
                                        topApps = topApps.value,
                                        hourlySeconds = hourlySeconds.value,
                                        todayTotalSeconds = todayTotalSeconds.value,
                                        activeLock = activeLock.value,
                                        playingPackage = playingPackage,
                                        isDarkTheme = isDarkTheme,
                                        accent = accent,
                                        onOpenPulse = { pkg ->
                                            onRequestPulse(pkg)
                                            nav = DeskNav.Pulse
                                        },
                                        onOpenSchedule = onOpenSchedule,
                                        onOpenToday = {
                                            onDismiss()
                                            onOpenUsageLog()
                                        },
                                        onOpenHeartAnchor = onOpenApp,
                                        onOpenPlayingApp = { pkg ->
                                            onDismiss()
                                            launchOverlayPackage(context, pkg)
                                        }
                                    )
                                    DeskNav.Pulse -> DeskPulsePage(
                                        pulse = pulseGlance.value,
                                        fallbackInApp = inApp,
                                        intentPurpose = session?.purpose,
                                        activeLock = activeLock.value,
                                        todayTotalSeconds = todayTotalSeconds.value,
                                        todayEnterCount = todayEnterCount.value,
                                        todayDismissCount = todayDismissCount.value,
                                        isDarkTheme = isDarkTheme,
                                        accent = accent,
                                        onBack = {
                                            if (inAppLive) {
                                                onDismiss()
                                            } else {
                                                nav = DeskNav.Hub
                                                onClearPulse()
                                            }
                                        },
                                        onOpenTodayGlance = { nav = DeskNav.TodayGlance },
                                        onOpenFullReport = onOpenFullReport,
                                        onAddToPlan = onAddToPlan,
                                        onOpenSchedule = onOpenSchedule,
                                    )
                                    DeskNav.TodayGlance -> {
                                        val pulse = pulseGlance.value
                                        val fallback = inApp
                                        DesktopAnchorTodayGlancePage(
                                            topApps = topApps.value,
                                            todayTotalSeconds = todayTotalSeconds.value,
                                            todayEnterCount = todayEnterCount.value,
                                            todayDismissCount = todayDismissCount.value,
                                            currentPackageName = pulse?.packageName
                                                ?: fallback?.packageName.orEmpty(),
                                            currentAppName = pulse?.appName
                                                ?: fallback?.appName.orEmpty(),
                                            currentTodaySeconds = pulse?.todayTotalSeconds
                                                ?: fallback?.todayTotalSeconds
                                                ?: 0L,
                                            currentOpenCount = pulse?.openCount
                                                ?: fallback?.openCount
                                                ?: 0,
                                            currentSessionSeconds = pulse?.sessionSeconds
                                                ?: fallback?.sessionSeconds
                                                ?: 0L,
                                            currentLive = pulse?.live
                                                ?: (fallback != null),
                                            isDarkTheme = isDarkTheme,
                                            accent = accent,
                                            onBack = { nav = DeskNav.Pulse },
                                            onOpenToday = {
                                                onDismiss()
                                                onOpenUsageLog()
                                            },
                                            onOpenHeartAnchor = onOpenApp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeskGlassShell(
    isDarkTheme: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    glowOrigin: TransformOrigin = TransformOrigin(0.9f, 1f),
    content: @Composable () -> Unit,
) {
    val palette = rememberDeskPalette(isDarkTheme, accent)
    val cornerPx = 24.dp
    Box(
        modifier = modifier
            .clip(PanelCorner)
            .drawBehind {
                val radius = CornerRadius(cornerPx.toPx())
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accent.copy(alpha = if (isDarkTheme) 0.10f else 0.06f),
                            Color.Transparent
                        ),
                        center = Offset(
                            size.width * glowOrigin.pivotFractionX,
                            size.height * glowOrigin.pivotFractionY
                        ),
                        radius = size.minDimension * 0.7f
                    ),
                    cornerRadius = radius
                )
            }
            .background(palette.panelBrush)
            .drawWithContent {
                drawContent()
                // 内层顶光 + 底收：材质像磨砂玻璃，而不是一块实色卡
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.White.copy(alpha = if (isDarkTheme) 0.07f else 0.28f),
                            0.18f to Color.Transparent,
                            0.85f to Color.Transparent,
                            1f to Color.Black.copy(alpha = if (isDarkTheme) 0.22f else 0.04f)
                        )
                    ),
                    cornerRadius = CornerRadius(cornerPx.toPx())
                )
            }
            .border(
                BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (isDarkTheme) 0.18f else 0.55f),
                            accent.copy(alpha = if (isDarkTheme) 0.28f else 0.18f),
                            Color.White.copy(alpha = if (isDarkTheme) 0.06f else 0.2f)
                        )
                    )
                ),
                PanelCorner
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        Box(modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp)) {
            content()
        }
    }
}

@Composable
private fun rememberDeskPalette(isDarkTheme: Boolean, accent: Color): DeskPalette {
    return remember(isDarkTheme, accent) {
        if (isDarkTheme) {
            DeskPalette(
                ink = Color(0xFFF2F2F7),
                muted = Color(0xFF9A9AA0),
                dim = Color(0xFF636366),
                line = Color.White.copy(alpha = 0.09f),
                fieldBg = Color.White.copy(alpha = 0.05f),
                // 更深、更静的雾面，减少渐变带来的高度变化闪烁
                panelBrush = Brush.verticalGradient(
                    listOf(Color(0xF214171C), Color(0xE60B0D10))
                ),
                edgeBrush = Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.28f), Color.White.copy(alpha = 0.06f))
                ),
                accent = accent,
                isDark = true
            )
        } else {
            DeskPalette(
                ink = Color(0xFF1C1C1E),
                muted = Color(0xFF6C6C70),
                dim = Color(0xFF8E8E93),
                line = Color.Black.copy(alpha = 0.06f),
                fieldBg = Color.Black.copy(alpha = 0.03f),
                panelBrush = Brush.verticalGradient(
                    listOf(Color(0xF5FBFCFE), Color(0xE8F0F2F6))
                ),
                edgeBrush = Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.2f), Color.Black.copy(alpha = 0.04f))
                ),
                accent = accent,
                isDark = false
            )
        }
    }
}

@Composable
private fun LiveSessionHeader(
    glance: DesktopAnchorInAppGlance,
    palette: DeskPalette,
    onDetails: (() -> Unit)? = null,
    titleOverride: String? = null,
) {
    val infinite = rememberInfiniteTransition(label = "session_scan")
    val scan by infinite.animateFloat(
        initialValue = -0.2f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scan_x"
    )
    val context = LocalContext.current
    val iconBitmap = remember(glance.packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(glance.packageName)
                .toBitmap(96, 96)
                .asImageBitmap()
        }.getOrNull()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        palette.accent.copy(alpha = 0.14f),
                        palette.fieldBg
                    )
                )
            )
            .border(1.dp, palette.accent.copy(alpha = 0.28f), RoundedCornerShape(16.dp))
            .then(
                if (onDetails != null) {
                    Modifier.clickable(onClick = onDetails)
                } else {
                    Modifier
                }
            )
            .drawBehind {
                val x = size.width * scan
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            palette.accent.copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        startX = x - size.width * 0.25f,
                        endX = x + size.width * 0.25f
                    )
                )
            }
            .padding(horizontal = 11.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = glance.appName,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Text(
                        text = glance.appName.take(1),
                        color = palette.ink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titleOverride ?: glance.appName.ifBlank { "当前 App" },
                    color = palette.ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = if (titleOverride != null) 2 else 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (titleOverride != null) {
                        glance.appName.ifBlank { "当前 App" }
                    } else {
                        "进行中"
                    },
                    color = palette.muted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (titleOverride == null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatPanelMinutes(glance.todayTotalSeconds),
                        color = palette.ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "今日",
                        color = palette.muted,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = AppUsageFormat.sessionDuration(glance.sessionSeconds),
                    color = palette.accent,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "本次",
                    color = palette.muted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun DeskPulsePage(
    pulse: DesktopAnchorPulseGlance?,
    fallbackInApp: DesktopAnchorInAppGlance?,
    intentPurpose: String? = null,
    activeLock: ScheduleOrbPolicy.ActiveGlance? = null,
    todayTotalSeconds: Long = 0L,
    todayEnterCount: Int = 0,
    todayDismissCount: Int = 0,
    isDarkTheme: Boolean,
    accent: Color,
    onBack: () -> Unit,
    onOpenTodayGlance: () -> Unit = {},
    onOpenFullReport: (String) -> Unit,
    onAddToPlan: (String) -> Unit = {},
    onOpenSchedule: () -> Unit = {},
) {
    val palette = rememberDeskPalette(isDarkTheme, accent)
    val resolved = pulse ?: fallbackInApp?.let {
        DesktopAnchorPulseGlance(
            packageName = it.packageName,
            appName = it.appName,
            todayTotalSeconds = it.todayTotalSeconds,
            openCount = it.openCount,
            sessionSeconds = it.sessionSeconds,
            live = true,
            sessions = it.sessions,
            dismissCount = it.dismissCount,
            source = it.source,
            hourlySeconds = it.hourlySeconds,
            canAddToPlan = it.canAddToPlan,
        )
    }

    if (resolved == null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "加载中…", color = palette.muted, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "← 返回",
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onBack)
            )
        }
        return
    }

    val fromSystem = resolved.source == DesktopInAppSource.SystemUsage
    val openLabel = if (fromSystem) "打开" else "进入"
    val reportLabel = if (fromSystem) "完整看看 →" else "完整记录 →"
    val spine = remember(resolved.sessions) { buildPulseSpine(resolved.sessions) }
    val hasHeat = remember(resolved.hourlySeconds) {
        resolved.hourlySeconds.any { it > 0L }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "← 返回",
                color = palette.muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 2.dp, vertical = 2.dp)
            )
            Text(
                text = resolved.appName.ifBlank { "App" },
                color = palette.ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.width(48.dp))
        }

        if (activeLock != null) {
            Spacer(modifier = Modifier.height(12.dp))
            ActiveLockStrip(
                active = activeLock,
                muted = palette.muted,
                dim = palette.dim,
                line = palette.line,
                onOpen = onOpenSchedule,
                showDivider = true,
            )
        }

        if (resolved.live) {
            Spacer(modifier = Modifier.height(if (activeLock != null) 4.dp else 12.dp))
            PulseLiveStrip(
                purpose = intentPurpose?.trim()?.takeIf { it.isNotBlank() },
                sessionSeconds = resolved.sessionSeconds,
                subtitle = when {
                    fromSystem && resolved.canAddToPlan -> "进行中 · 还没加进规则"
                    fromSystem -> "进行中"
                    else -> null
                },
                palette = palette,
            )
        }

        Spacer(modifier = Modifier.height(if (resolved.live) 12.dp else 10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            Column {
                Text(
                    text = formatPanelMinutes(resolved.todayTotalSeconds),
                    color = palette.accent,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
                Text(
                    text = "今日",
                    color = palette.muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Column {
                Text(
                    text = "${resolved.openCount}次",
                    color = palette.ink,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
                Text(
                    text = openLabel,
                    color = palette.muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        if (fromSystem && hasHeat) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "今天什么时候在用",
                color = palette.dim,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            DayHeatStrip(
                hourlySeconds = resolved.hourlySeconds,
                accent = accent,
                dim = palette.dim,
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        val hasSpine = spine.shown.isNotEmpty() || spine.shortFoldCount > 0
        when {
            !hasSpine && resolved.openCount == 0 -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(text = "今天还没有记录", color = palette.muted, fontSize = 13.sp)
                    if (resolved.dismissCount > 0) {
                        Text(
                            text = "守住 ${resolved.dismissCount} 次",
                            color = palette.dim,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = if (fromSystem && hasHeat) 160.dp else 200.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    spine.shown.forEach { session ->
                        PulseSpineRow(session = session, palette = palette)
                    }
                    if (spine.shortFoldCount > 0) {
                        Text(
                            text = "其余 ${spine.shortFoldCount} 次很短",
                            color = palette.dim,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    if (resolved.dismissCount > 0) {
                        Text(
                            text = "守住 ${resolved.dismissCount} 次",
                            color = palette.dim,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }

        if (fromSystem) {
            Text(
                text = "按系统用量 · 与加规则后的心锚记录不同",
                color = palette.dim,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        val showTodayRail = !fromSystem
        if (showTodayRail) {
            DeskPulseTodayRail(
                todayTotalSeconds = todayTotalSeconds,
                todayEnterCount = todayEnterCount,
                todayDismissCount = todayDismissCount,
                accent = accent,
                muted = palette.muted,
                ink = palette.ink,
                line = palette.line,
                onOpen = onOpenTodayGlance,
            )
        }

        Spacer(modifier = Modifier.height(if (showTodayRail) 8.dp else 10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = when {
                resolved.canAddToPlan -> Arrangement.SpaceBetween
                showTodayRail -> Arrangement.Start
                else -> Arrangement.End
            },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = reportLabel,
                color = if (resolved.canAddToPlan || showTodayRail) palette.muted else accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpenFullReport(resolved.packageName) }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
            if (resolved.canAddToPlan) {
                Text(
                    text = "去方案添加",
                    color = Color(0xFF0B1A10),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(accent)
                        .clickable { onAddToPlan(resolved.packageName) }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun PulseLiveStrip(
    purpose: String?,
    sessionSeconds: Long,
    palette: DeskPalette,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = if (purpose != null) "意图 · $purpose" else "本次进行中",
                color = palette.ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle ?: "进行中",
                color = palette.muted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = AppUsageFormat.sessionDuration(sessionSeconds),
                color = palette.accent,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "本次",
                color = palette.muted,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

private data class PulseSpineLayout(
    val shown: List<DesktopPulseSession>,
    val shortFoldCount: Int,
)

private fun buildPulseSpine(sessions: List<DesktopPulseSession>): PulseSpineLayout {
    val enters = sessions
        .filter { it.kind == DesktopPulseSessionKind.Enter }
        .sortedBy { it.startMs }
    val shown = enters.filter { it.ongoing || it.durationSeconds >= PULSE_SHORT_SESSION_SEC }
    val shortFoldCount = enters.count { !it.ongoing && it.durationSeconds < PULSE_SHORT_SESSION_SEC }
    return PulseSpineLayout(shown = shown, shortFoldCount = shortFoldCount)
}

@Composable
private fun PulseSpineRow(
    session: DesktopPulseSession,
    palette: DeskPalette,
) {
    val start = AppUsageFormat.clockHm(session.startMs)
    val end = if (session.ongoing) "现在" else AppUsageFormat.clockHm(session.endMs)
    val label = session.purpose?.takeIf { it.isNotBlank() } ?: "进入"
    val duration = AppUsageFormat.sessionDuration(session.durationSeconds)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$start – $end · $label",
            color = palette.muted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Text(
            text = duration,
            color = if (session.ongoing) palette.accent else palette.ink,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}

@Composable
private fun DesktopAnchorOrb(
    accent: Color,
    isDarkTheme: Boolean,
    orb: FloatingOrbMetrics,
    panelOpen: Boolean,
    onClick: () -> Unit,
    inAppPackageName: String? = null,
    musicPackageName: String? = null,
    idle: Boolean = false,
    dragging: Boolean = false,
    contentAlpha: Float = 1f,
) {
    val inApp = !inAppPackageName.isNullOrBlank()
    val musicPkg = musicPackageName?.trim().orEmpty()
    val spinPackage = when {
        inApp -> inAppPackageName
        musicPkg.isNotEmpty() -> musicPkg
        else -> null
    }
    val showMusicEq = !inApp && musicPkg.isNotEmpty()
    val hitSize = orb.desktopHit
    val jewelSize = orb.desktopJewel
    val innerDisk = jewelSize * 0.74f
    val markSize = jewelSize * 0.52f
    val infinite = rememberInfiniteTransition(label = "desktop_anchor_breath")
    val breath by infinite.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_core_breath"
    )
    val breathScale by animateFloatAsState(
        targetValue = if (idle) 0.14f else 1f,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "orb_breath_scale"
    )
    val hingeScale by animateFloatAsState(
        targetValue = if (panelOpen) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = 0.86f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "orb_hinge_scale"
    )
    val haloReveal by animateFloatAsState(
        targetValue = if (spinPackage != null) 0f else 1f,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "orb_halo_reveal"
    )
    val haloExtent by animateFloatAsState(
        targetValue = when {
            idle -> 0.86f
            dragging || panelOpen -> 1f
            else -> 0.96f
        },
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "orb_halo_extent"
    )
    val fillTop = if (isDarkTheme) Color(0xD91A1F26) else Color(0xF5FFFFFF)
    val fillBottom = if (isDarkTheme) Color(0xC10A0C0F) else Color(0xD6EEF0F4)
    val rimBrush = Brush.linearGradient(
        colors = if (isDarkTheme) {
            listOf(Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0.07f))
        } else {
            listOf(Color.White.copy(alpha = 0.78f), Color.Black.copy(alpha = 0.06f))
        }
    )
    val specular = when {
        spinPackage != null && isDarkTheme -> 0.08f
        spinPackage != null -> 0.16f
        isDarkTheme -> if (idle) 0.08f else 0.16f
        else -> if (idle) 0.18f else 0.34f
    }
    val coreWash = if (spinPackage != null) {
        0f
    } else {
        (0.06f + breath * 0.08f) * breathScale * if (isDarkTheme) 1f else 0.68f
    }
    val coreGlowAlpha = (0.12f + breath * 0.16f) * breathScale
    val coreGlowScale = 0.92f + breath * 0.10f * breathScale
    val glyphAlpha = 0.84f + breath * 0.10f * breathScale
    val haloPeak = when {
        dragging -> 0.32f
        panelOpen -> 0.26f
        idle -> 0.11f
        else -> 0.20f
    } * haloReveal
    val haloTint = if (isDarkTheme) Color.White else Color(0xFFF4F6FA)

    Box(
        modifier = Modifier
            .size(hitSize)
            .graphicsLayer {
                alpha = contentAlpha
                scaleX = hingeScale
                scaleY = hingeScale
            }
            .drawBehind {
                if (haloPeak < 0.01f) return@drawBehind
                val hitR = size.minDimension / 2f
                val jewelR = jewelSize.toPx() / 2f
                val radius = hitR * haloExtent
                val start = (jewelR / radius).coerceIn(0.35f, 0.88f)
                val mid = start + (1f - start) * 0.36f
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            (start * 0.92f) to Color.Transparent,
                            start to haloTint.copy(alpha = haloPeak),
                            mid to haloTint.copy(alpha = haloPeak * 0.34f),
                            1f to Color.Transparent
                        ),
                        center = center,
                        radius = radius
                    )
                )
                if (!isDarkTheme) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                start to accent.copy(alpha = haloPeak * 0.22f * breathScale),
                                1f to Color.Transparent
                            ),
                            center = center,
                            radius = radius
                        )
                    )
                }
            }
            .semantics {
                role = Role.Button
                contentDescription = when {
                    panelOpen && inApp -> "收起用量"
                    panelOpen -> "收起今日"
                    inApp -> "心锚 · 当前 App 用量"
                    showMusicEq -> "心锚 · 有音乐在播"
                    else -> "心锚 · 今日"
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(jewelSize)
                .clip(CircleShape)
                .drawBehind {
                    drawCircle(
                        brush = Brush.verticalGradient(listOf(fillTop, fillBottom))
                    )
                    if (coreWash > 0.01f) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    accent.copy(alpha = coreWash),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = size.minDimension * 0.48f
                            )
                        )
                    }
                }
                .border(BorderStroke(1.dp, rimBrush), CircleShape)
                .drawWithContent {
                    drawContent()
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = specular),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.36f, size.height * 0.30f),
                            radius = size.minDimension * 0.52f
                        )
                    )
                    drawCircle(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.58f to Color.Transparent,
                                1f to Color.Black.copy(alpha = if (isDarkTheme) 0.20f else 0.05f)
                            )
                        )
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (spinPackage != null) {
                SpinningAppIconDisk(
                    packageName = spinPackage,
                    size = innerDisk,
                    periodMs = FLOATING_ORB_SPIN_PERIOD_MS,
                    musicPlaying = showMusicEq
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(jewelSize * 0.48f)
                        .graphicsLayer {
                            scaleX = coreGlowScale
                            scaleY = coreGlowScale
                            alpha = coreGlowAlpha
                        }
                        .clip(CircleShape)
                        .background(accent)
                )
                DesktopAnchorGlyph(
                    accent = accent,
                    alpha = glyphAlpha,
                    modifier = Modifier.size(markSize)
                )
            }
        }
    }
}

/** 桌面微粒用的简化锚：环、杆、横档、两爪。通知栏剪影在此尺寸会糊。 */
@Composable
private fun DesktopAnchorGlyph(
    accent: Color,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.minDimension
        if (w <= 0f) return@Canvas
        val s = w / 24f
        val stroke = (w * 0.09f).coerceIn(2.2f, 5.2f)
        val color = accent.copy(alpha = alpha)
        val style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val cx = size.width / 2f

        drawCircle(
            color = color,
            radius = 3.15f * s,
            center = Offset(cx, 5.7f * s),
            style = style
        )
        drawLine(
            color = color,
            start = Offset(cx, 9.05f * s),
            end = Offset(cx, 17.4f * s),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(cx - 4.15f * s, 11.35f * s),
            end = Offset(cx + 4.15f * s, 11.35f * s),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        val flukes = Path().apply {
            moveTo(cx - 6.35f * s, 15.15f * s)
            quadraticBezierTo(cx - 5.4f * s, 19.05f * s, cx, 19.55f * s)
            quadraticBezierTo(cx + 5.4f * s, 19.05f * s, cx + 6.35f * s, 15.15f * s)
        }
        drawPath(flukes, color = color, style = style)
    }
}

private fun formatPanelMinutes(totalSeconds: Long): String {
    val m = totalSeconds.coerceAtLeast(0L) / 60L
    return when {
        m <= 0L -> if (totalSeconds > 0L) "<1分" else "0分"
        m < 60L -> "${m}分"
        else -> {
            val h = m / 60L
            val rm = m % 60L
            if (rm == 0L) "${h}时" else "${h}时${rm}分"
        }
    }
}
