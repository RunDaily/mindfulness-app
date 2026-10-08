package com.life.mindfulnessapp.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.domain.model.ThemePack
import com.life.mindfulnessapp.ui.theme.MistCardBg
import com.life.mindfulnessapp.ui.theme.MistTextHint
import com.life.mindfulnessapp.ui.theme.MistTextPrimary
import com.life.mindfulnessapp.ui.theme.MistTextSecondary
import com.life.mindfulnessapp.ui.theme.chrome
import com.life.mindfulnessapp.util.AppUsageFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class InspectPage { Main, TodayGlance }

data class CompanionInspectGlance(
    val packageName: String,
    val appName: String,
    val purpose: String?,
    val sessionSeconds: Long,
    val todayTotalSeconds: Long,
    /** 当前 App 今日进入次数（心锚口径） */
    val todayEnterCount: Int = 0,
    val sessionLimitMinutes: Int,
    val hasSessionLimit: Boolean,
    val sessionRemainingSeconds: Long = Long.MAX_VALUE,
    val hasIntentGate: Boolean,
    val showEnd: Boolean,
    val endIsMusicExit: Boolean,
    /** 点开面板时的轻问；随意浏览 / 搜索安静窗内为 null */
    val awarenessPrompt: String? = null,
    val awarenessYes: String? = null,
    val awarenessNo: String? = null,
    val selectedMode: String,
    val opacity: Float,
    val miniCompact: Boolean,
    val capsuleLeft: Int,
    val capsuleTop: Int,
    val capsuleWidth: Int,
    val capsuleHeight: Int,
    val screenWidth: Int,
    val screenHeight: Int,
    val topInset: Int,
    val bottomInset: Int,
)

internal val CompanionInspectEaseOut = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
internal val CompanionInspectEaseIn = CubicBezierEasing(0.32f, 0.04f, 0.62f, 1f)
internal const val COMPANION_INSPECT_OPEN_MS = 300
internal const val COMPANION_INSPECT_CLOSE_MS = 220
internal const val COMPANION_INSPECT_SCRIM_OPEN_MS = 280
internal const val COMPANION_INSPECT_REMOVE_MS = 300L

private const val INSPECT_IDLE_MS = 7_500L
private val InspectCardWidth = 292.dp
private val InspectCardShape = RoundedCornerShape(22.dp)
private val InspectTravel = 11.dp
private const val InspectFromScale = 0.88f

@Composable
fun CompanionInspectOverlay(
    open: State<Boolean>,
    glance: State<CompanionInspectGlance?>,
    topApps: State<List<DesktopAnchorTopApp>>,
    todayMonitorTotalSeconds: State<Long>,
    todayEnterCount: State<Int>,
    todayDismissCount: State<Int>,
    themePack: ThemePack = ThemePack.Night,
    isDarkTheme: Boolean = themePack.isDark,
    onDismiss: () -> Unit,
    onOpacityChange: (Float) -> Unit,
    onMiniCompactChange: (Boolean) -> Unit,
    onEndSession: () -> Unit,
    onOpenTodayReceipt: () -> Unit,
    onOpenHeartAnchor: () -> Unit,
    onAwarenessStill: (() -> Unit)? = null,
    onAwarenessDrift: (() -> Unit)? = null,
) {
    val shown = open.value
    val live = glance.value
    var held by remember { mutableStateOf(live) }
    if (live != null) held = live
    val data = live ?: held

    val scrim = remember { Animatable(0f) }
    val card = remember { Animatable(0f) }
    val content = remember { Animatable(0f) }
    var linger by remember { mutableIntStateOf(0) }
    var page by remember { mutableStateOf(InspectPage.Main) }
    var appearanceOpen by remember { mutableStateOf(false) }

    // 点胶囊开面板：直接到位，不做进场动效；收起仍轻收一下
    LaunchedEffect(shown) {
        if (shown) {
            scrim.snapTo(1f)
            card.snapTo(1f)
            content.snapTo(1f)
        } else {
            page = InspectPage.Main
            appearanceOpen = false
            launch {
                content.animateTo(0f, tween(90, easing = CompanionInspectEaseIn))
            }
            launch {
                card.animateTo(
                    0f,
                    tween(COMPANION_INSPECT_CLOSE_MS, easing = CompanionInspectEaseIn)
                )
            }
            delay(36)
            scrim.animateTo(
                0f,
                tween(240, easing = CompanionInspectEaseIn)
            )
        }
    }
    LaunchedEffect(shown, linger, data?.packageName, page, appearanceOpen) {
        if (!shown) return@LaunchedEffect
        delay(INSPECT_IDLE_MS)
        onDismiss()
    }

    val visible = shown || card.value > 0.012f || scrim.value > 0.012f
    if (!visible || data == null) return

    val density = LocalDensity.current
    val cardWidthPx = with(density) { InspectCardWidth.roundToPx() }
    val gapPx = with(density) { 10.dp.roundToPx() }
    val marginPx = with(density) { 16.dp.roundToPx() }
    val hasAsk = !data.awarenessPrompt.isNullOrBlank()
    val estimatedH = with(density) {
        when {
            page == InspectPage.TodayGlance -> 380.dp.roundToPx()
            appearanceOpen && hasAsk -> 320.dp.roundToPx()
            appearanceOpen -> 250.dp.roundToPx()
            hasAsk -> 250.dp.roundToPx()
            else -> 190.dp.roundToPx()
        }
    }
    val travelPx = with(density) { InspectTravel.toPx() }
    val placement = remember(
        data.capsuleLeft,
        data.capsuleTop,
        data.capsuleWidth,
        data.capsuleHeight,
        data.screenWidth,
        data.screenHeight,
        data.topInset,
        data.bottomInset,
        cardWidthPx,
        gapPx,
        marginPx,
        estimatedH
    ) {
        placeInspectCard(data, cardWidthPx, estimatedH, gapPx, marginPx)
    }
    val origin = remember(
        placement.x,
        placement.y,
        placement.growsDown,
        data.capsuleLeft,
        data.capsuleWidth,
        cardWidthPx
    ) {
        inspectOrigin(data, placement, cardWidthPx)
    }
    val palette = remember(themePack) { InspectPalette.of(themePack) }
    val cardT = card.value
    val fromY = if (placement.growsDown) -travelPx else travelPx
    val scrimColor = palette.scrim.copy(alpha = palette.scrim.alpha * scrim.value)

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrimColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )
        Box(
            modifier = Modifier
                .offset { IntOffset(placement.x, placement.y) }
                .width(InspectCardWidth)
                .graphicsLayer {
                    transformOrigin = origin
                    translationY = fromY * (1f - cardT)
                    val scale = InspectFromScale + (1f - InspectFromScale) * cardT
                    scaleX = scale
                    scaleY = scale
                    alpha = (cardT / 0.42f).coerceIn(0f, 1f)
                }
                .shadow(
                    elevation = (6f + 12f * cardT).dp,
                    shape = InspectCardShape,
                    ambientColor = Color.Black.copy(alpha = if (isDarkTheme) 0.38f else 0.12f),
                    spotColor = Color.Black.copy(alpha = if (isDarkTheme) 0.24f else 0.08f)
                )
                .clip(InspectCardShape)
                .background(palette.surface)
                .border(0.6.dp, palette.hairline, InspectCardShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { linger++ }
                )
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(palette.sheen, Color.Transparent)
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .graphicsLayer { alpha = content.value }
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                InspectCardBody(
                    data = data,
                    page = page,
                    appearanceOpen = appearanceOpen,
                    topApps = topApps.value,
                    todayMonitorTotalSeconds = todayMonitorTotalSeconds.value,
                    todayEnterCount = todayEnterCount.value,
                    todayDismissCount = todayDismissCount.value,
                    palette = palette,
                    accent = themePack.chrome().accent,
                    isDarkTheme = isDarkTheme,
                    onLinger = { linger++ },
                    onPageChange = { page = it },
                    onAppearanceOpenChange = { appearanceOpen = it },
                    onOpacityChange = onOpacityChange,
                    onMiniCompactChange = onMiniCompactChange,
                    onEndSession = onEndSession,
                    onOpenTodayReceipt = onOpenTodayReceipt,
                    onOpenHeartAnchor = onOpenHeartAnchor,
                    onAwarenessStill = onAwarenessStill,
                    onAwarenessDrift = onAwarenessDrift
                )
            }
        }
    }
}

@Composable
private fun InspectCardBody(
    data: CompanionInspectGlance,
    page: InspectPage,
    appearanceOpen: Boolean,
    topApps: List<DesktopAnchorTopApp>,
    todayMonitorTotalSeconds: Long,
    todayEnterCount: Int,
    todayDismissCount: Int,
    palette: InspectPalette,
    accent: Color,
    isDarkTheme: Boolean,
    onLinger: () -> Unit,
    onPageChange: (InspectPage) -> Unit,
    onAppearanceOpenChange: (Boolean) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onMiniCompactChange: (Boolean) -> Unit,
    onEndSession: () -> Unit,
    onOpenTodayReceipt: () -> Unit,
    onOpenHeartAnchor: () -> Unit,
    onAwarenessStill: (() -> Unit)? = null,
    onAwarenessDrift: (() -> Unit)? = null,
) {
    if (page == InspectPage.TodayGlance) {
        DesktopAnchorTodayGlancePage(
            topApps = topApps,
            todayTotalSeconds = todayMonitorTotalSeconds,
            todayEnterCount = todayEnterCount,
            todayDismissCount = todayDismissCount,
            currentPackageName = data.packageName,
            currentAppName = data.appName,
            currentTodaySeconds = data.todayTotalSeconds,
            currentOpenCount = data.todayEnterCount,
            currentSessionSeconds = data.sessionSeconds,
            currentLive = true,
            isDarkTheme = isDarkTheme,
            accent = accent,
            onBack = {
                onLinger()
                onPageChange(InspectPage.Main)
            },
            onOpenToday = {
                onLinger()
                onOpenTodayReceipt()
            },
            onOpenHeartAnchor = {
                onLinger()
                onOpenHeartAnchor()
            },
        )
        return
    }

    val purpose = data.purpose?.trim().orEmpty()
    val sessionLine = if (data.hasSessionLimit && data.sessionRemainingSeconds != Long.MAX_VALUE) {
        formatSeconds(data.sessionRemainingSeconds.coerceAtLeast(0L))
    } else {
        formatSeconds(data.sessionSeconds)
    }
    val ask = data.awarenessPrompt?.trim().orEmpty()
    val yes = data.awarenessYes?.trim().orEmpty()
    val no = data.awarenessNo?.trim().orEmpty()
    val showAsk = ask.isNotEmpty() && yes.isNotEmpty() && no.isNotEmpty()
    val sizeLabel = if (data.miniCompact) "紧凑" else "标准"
    val opacityLabel = (data.opacity * 100f).toInt().toString()

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when {
                    purpose.isNotEmpty() -> purpose
                    data.hasIntentGate -> "没写意图"
                    else -> "本次"
                },
                color = palette.muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = sessionLine,
                color = palette.ink,
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.Monospace,
                letterSpacing = (-0.6).sp
            )
        }
        if (data.showEnd) {
            Text(
                text = if (data.endIsMusicExit) "停播离开" else "结束本次",
                color = palette.end,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        onLinger()
                        onEndSession()
                    }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
    }

    if (showAsk) {
        Spacer(modifier = Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.6.dp)
                .background(palette.hairline)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = ask,
            color = palette.ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = yes,
                color = palette.muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        onLinger()
                        onAwarenessStill?.invoke()
                    }
                    .padding(vertical = 4.dp, horizontal = 2.dp)
            )
            Text(
                text = no,
                color = palette.ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        onLinger()
                        onAwarenessDrift?.invoke()
                    }
                    .padding(vertical = 4.dp, horizontal = 2.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(14.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.6.dp)
            .background(palette.hairline)
    )
    Spacer(modifier = Modifier.height(10.dp))

    InspectSideRail(
        title = "今日 · 监控中",
        summary = buildString {
            append("共 ")
            append(AppUsageFormat.totalDurationCompact(todayMonitorTotalSeconds))
            append(" · 守住 ")
            append(todayDismissCount.coerceAtLeast(0))
            append(" · 进入 ")
            append(todayEnterCount.coerceAtLeast(0))
        },
        trailing = "›",
        accentTrailing = true,
        accent = accent,
        muted = palette.muted,
        ink = palette.ink,
        contentDescription = "今日监控概况",
        onClick = {
            onLinger()
            onAppearanceOpenChange(false)
            onPageChange(InspectPage.TodayGlance)
        },
    )

    Spacer(modifier = Modifier.height(10.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.6.dp)
            .background(palette.hairline)
    )
    Spacer(modifier = Modifier.height(10.dp))

    InspectSideRail(
        title = "外观",
        summary = "$sizeLabel · $opacityLabel",
        trailing = if (appearanceOpen) "∧" else "∨",
        accentTrailing = false,
        accent = accent,
        muted = palette.muted,
        ink = palette.ink,
        contentDescription = if (appearanceOpen) "收起外观设置" else "展开外观设置",
        onClick = {
            onLinger()
            onAppearanceOpenChange(!appearanceOpen)
        },
    )

    if (appearanceOpen) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "大小",
                color = palette.dim,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.6.sp,
                modifier = Modifier.padding(end = 12.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                InspectSizeChip(
                    label = "标准",
                    selected = !data.miniCompact,
                    palette = palette,
                    onClick = {
                        onLinger()
                        onMiniCompactChange(false)
                    }
                )
                InspectSizeChip(
                    label = "紧凑",
                    selected = data.miniCompact,
                    palette = palette,
                    onClick = {
                        onLinger()
                        onMiniCompactChange(true)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "透明",
                color = palette.dim,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.6.sp,
                modifier = Modifier.padding(end = 10.dp)
            )
            Slider(
                value = data.opacity,
                onValueChange = {
                    onLinger()
                    onOpacityChange(it)
                },
                valueRange = AppPreferences.CAPSULE_SHELL_OPACITY_MIN..
                    AppPreferences.CAPSULE_SHELL_OPACITY_MAX,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = palette.ink,
                    activeTrackColor = palette.ink.copy(alpha = 0.72f),
                    inactiveTrackColor = palette.hairline
                )
            )
            Text(
                text = opacityLabel,
                color = palette.muted,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun InspectSideRail(
    title: String,
    summary: String,
    trailing: String,
    accentTrailing: Boolean,
    accent: Color,
    muted: Color,
    ink: Color,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
            .semantics { this.contentDescription = contentDescription },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = title,
                color = muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = summary,
                color = ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        Text(
            text = trailing,
            color = if (accentTrailing) accent else muted,
            fontSize = if (trailing == "›") 14.sp else 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}

@Composable
private fun InspectSizeChip(
    label: String,
    selected: Boolean,
    palette: InspectPalette,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = if (selected) palette.ink else palette.dim,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp)
    )
}

private data class InspectPlacement(
    val x: Int,
    val y: Int,
    val growsDown: Boolean,
)

private fun placeInspectCard(
    data: CompanionInspectGlance,
    cardW: Int,
    cardH: Int,
    gap: Int,
    margin: Int,
): InspectPlacement {
    val minX = margin
    val maxX = (data.screenWidth - margin - cardW).coerceAtLeast(minX)
    val centerX = data.capsuleLeft + data.capsuleWidth / 2
    val x = (centerX - cardW / 2).coerceIn(minX, maxX)
    val belowTop = data.capsuleTop + data.capsuleHeight + gap
    val aboveTop = data.capsuleTop - gap - cardH
    val spaceBelow = data.screenHeight - data.bottomInset - belowTop - margin
    val mid = data.capsuleTop + data.capsuleHeight / 2
    val preferBelow = mid < data.screenHeight * 0.55f && spaceBelow >= cardH * 0.55f
    val y = if (preferBelow) {
        belowTop.coerceAtMost(
            (data.screenHeight - data.bottomInset - margin - cardH).coerceAtLeast(data.topInset + margin)
        )
    } else {
        aboveTop.coerceAtLeast(data.topInset + margin)
    }
    return InspectPlacement(x = x, y = y, growsDown = preferBelow)
}

private fun inspectOrigin(
    data: CompanionInspectGlance,
    placement: InspectPlacement,
    cardW: Int,
): TransformOrigin {
    val companionCenterX = data.capsuleLeft + data.capsuleWidth / 2f
    val pivotX = if (cardW <= 0) {
        0.5f
    } else {
        ((companionCenterX - placement.x) / cardW.toFloat()).coerceIn(0.18f, 0.82f)
    }
    val pivotY = if (placement.growsDown) 0.04f else 0.96f
    return TransformOrigin(pivotX, pivotY)
}

private data class InspectPalette(
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val dim: Color,
    val hairline: Color,
    val sheen: Color,
    val scrim: Color,
    val end: Color,
) {
    companion object {
        fun of(dark: Boolean) = of(if (dark) ThemePack.Night else ThemePack.Day)

        fun of(pack: ThemePack) = when (pack) {
            ThemePack.Night -> InspectPalette(
                surface = Color(0xF2141416),
                ink = Color(0xFFF4F4F5),
                muted = Color(0x99F4F4F5),
                dim = Color(0x66F4F4F5),
                hairline = Color.White.copy(alpha = 0.08f),
                sheen = Color.White.copy(alpha = 0.045f),
                scrim = Color.Black.copy(alpha = 0.24f),
                end = Color(0xFFE8A0A3),
            )
            ThemePack.Day -> InspectPalette(
                surface = Color(0xF6F7F5F2),
                ink = Color(0xFF1C1C1E),
                muted = Color(0x991C1C1E),
                dim = Color(0x661C1C1E),
                hairline = Color.Black.copy(alpha = 0.08f),
                sheen = Color.White.copy(alpha = 0.55f),
                scrim = Color.Black.copy(alpha = 0.14f),
                end = Color(0xFFB4232C),
            )
            ThemePack.Mist -> InspectPalette(
                surface = MistCardBg.copy(alpha = 0.96f),
                ink = MistTextPrimary,
                muted = MistTextSecondary.copy(alpha = 0.85f),
                dim = MistTextHint,
                hairline = Color(0xFF24302A).copy(alpha = 0.10f),
                sheen = Color.White.copy(alpha = 0.40f),
                scrim = Color(0xFF24302A).copy(alpha = 0.16f),
                end = Color(0xFFC06B55),
            )
        }
    }
}
