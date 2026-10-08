package com.life.mindfulnessapp.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 监控能力的产品方言：
 * - **意图门**：门槛 / 停一下再进
 * - **时长锁**：边界 / 用完即止
 * - **时段锁**：时间主权 / 指定时段硬挡
 *
 * 语义全产品统一；绘制分 [CapabilityForm] 光学变体（小形态去细节、大形态可读完整隐喻）。
 */
enum class CapabilityKind {
    IntentGate,
    TimeLock,
    PeriodLock
}

/** 解析路由 / Intent 中的能力名；未知或已下线能力名 → 意图门 */
fun parseCapabilityKind(name: String?): CapabilityKind {
    if (name.isNullOrBlank()) return CapabilityKind.IntentGate
    return runCatching { CapabilityKind.valueOf(name) }.getOrDefault(CapabilityKind.IntentGate)
}

/**
 * 字形形态（同一隐喻，不同光学复杂度）：
 * - [Compact]：首页栏、列表、胶囊 — 约 11–13dp
 * - [Standard]：配置分区、拦截副标题 — 约 16–18dp
 * - [Emphasis]：超限页等单枚强调 — 约 22–28dp
 */
enum class CapabilityForm {
    Compact,
    Standard,
    Emphasis
}

object MonitorCapability {
    const val IntentGateLabel = "意图门"
    const val TimeLockLabel = "时长锁"
    const val PeriodLockLabel = "时段锁"

    /** 配置托盘占位：提醒 / 仪式（弱化，非主能力） */
    val Reminder: ImageVector = Icons.Outlined.NotificationsNone
    val Ritual: ImageVector = Icons.Outlined.AutoAwesome

    /**
     * 能力主题色（同家族微差，便于一眼区分）：
     * - 意图门：品牌绿
     * - 时长锁：暖橄榄（额度 / 消耗）
     * - 时段锁：冷青绿（时段 / 边界）
     */
    val IntentGateAccent = LogoGreen
    val TimeLockAccent = Color(0xFFB0892E)
    val PeriodLockAccent = Color(0xFF2F9B9B)

    fun accent(kind: CapabilityKind): Color = when (kind) {
        CapabilityKind.IntentGate -> IntentGateAccent
        CapabilityKind.TimeLock -> TimeLockAccent
        CapabilityKind.PeriodLock -> PeriodLockAccent
    }

    /** 标准形态（默认对外引用） */
    val IntentGate: ImageVector
        get() = IntentGateStandard
    val TimeLock: ImageVector
        get() = TimeLockStandard
    val PeriodLock: ImageVector
        get() = PeriodLockStandard

    fun label(kind: CapabilityKind): String = when (kind) {
        CapabilityKind.IntentGate -> IntentGateLabel
        CapabilityKind.TimeLock -> TimeLockLabel
        CapabilityKind.PeriodLock -> PeriodLockLabel
    }

    fun glyph(kind: CapabilityKind, form: CapabilityForm): ImageVector = when (kind) {
        CapabilityKind.IntentGate -> IntentGateStandard
        CapabilityKind.TimeLock -> TimeLockStandard
        CapabilityKind.PeriodLock -> when (form) {
            CapabilityForm.Compact -> PeriodLockCompact
            CapabilityForm.Standard, CapabilityForm.Emphasis -> PeriodLockStandard
        }
    }

    fun opticalSize(form: CapabilityForm): Dp = when (form) {
        CapabilityForm.Compact -> 12.dp
        CapabilityForm.Standard -> 17.dp
        CapabilityForm.Emphasis -> 26.dp
    }
}

// ── 能力字形 ─────────────────────────────────────────────────────────────────

private var _intentGateStandard: ImageVector? = null
private var _timeLockStandard: ImageVector? = null
private var _periodLockCompact: ImageVector? = null
private var _periodLockStandard: ImageVector? = null

/** 将 SVG pathData 写入当前 ImageVector.Builder */
private fun ImageVector.Builder.addSvgFillPath(pathData: String) {
    addPath(
        pathData = PathParser().parsePathString(pathData).toNodes(),
        fill = SolidColor(Color.Black)
    )
}

/**
 * 意图门：用户资源「房屋大门.svg」
 * （相对「大门口」双扇透视门，单扇+门框在小尺寸更清晰）
 */
private val IntentGateStandard: ImageVector
    get() = _intentGateStandard ?: ImageVector.Builder(
        name = "capability.intent_gate.standard",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 1024f,
        viewportHeight = 1024f
    ).apply {
        addSvgFillPath(
            "M516.608 998.4c-2.816 0-5.376-0.256-8.192-1.024l-407.808-89.6c-17.664-3.84-30.208-19.456-30.208-37.376V153.6c0-17.92 12.544-33.536 30.208-37.376l407.808-89.6c11.52-2.56 23.296 0.256 32.256 7.68 8.96 7.168 14.336 18.176 14.336 29.952v896c0 11.52-5.376 22.528-14.336 29.952-6.912 5.12-15.616 8.192-24.064 8.192zM147.2 839.424l331.008 72.704V111.872L147.2 184.576v654.848z"
        )
        addSvgFillPath(
            "M915.2 908.8H516.608c-21.248 0-38.4-17.152-38.4-38.4s17.152-38.4 38.4-38.4h360.192v-640H516.608c-21.248 0-38.4-17.152-38.4-38.4s17.152-38.4 38.4-38.4h398.592c21.248 0 38.4 17.152 38.4 38.4v716.8c0 21.248-17.152 38.4-38.4 38.4zM377.6 595.2c-21.248 0-38.4-17.152-38.4-38.4v-89.6c0-21.248 17.152-38.4 38.4-38.4s38.4 17.152 38.4 38.4v89.6c0 21.248-17.152 38.4-38.4 38.4z"
        )
    }.build().also { _intentGateStandard = it }

/**
 * 时长锁：用户资源「waiting.svg」沙漏。
 */
private val TimeLockStandard: ImageVector
    get() = _timeLockStandard ?: ImageVector.Builder(
        name = "capability.time_lock.standard",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 1024f,
        viewportHeight = 1024f
    ).apply {
        addSvgFillPath(
            "M763.178667 106.666667c16.938667 0 30.72 13.44 31.317333 30.293333V309.290667a31.36 31.36 0 0 1-5.888 18.346666l-0.853333 1.109334-147.029334 185.472 146.986667 184.746666a31.36 31.36 0 0 1 6.698667 17.109334l0.085333 1.322666v1.365334l-1.194667 167.466666a31.36 31.36 0 0 1-30.293333 31.104h-1.109333l-495.914667-0.682666a31.36 31.36 0 0 1-31.317333-30.293334v-1.109333l0.256-166.784c0-6.186667 1.877333-12.288 5.290666-17.408l0.768-1.109333 136.661334-186.026667-136.661334-186.069333a31.36 31.36 0 0 1-5.973333-15.786667l-0.085333-1.450667V138.026667c0-16.981333 13.44-30.762667 30.293333-31.36H763.136z m-31.36 62.72H297.6v129.621333l144.213333 196.352c7.594667 10.325333 8.106667 24.192 1.408 34.986667l-0.682666 1.066666-0.725334 1.066667-144.213333 196.352-0.170667 125.141333 433.408 0.597334 0.896-125.184-155.562666-195.626667a31.36 31.36 0 0 1-1.536-36.949333l0.725333-1.024 0.768-1.024 155.690667-196.394667V169.386667z m-96.128 584.746666c7.424 0 13.44 5.973333 13.44 13.44v35.84a13.44 13.44 0 0 1-13.44 13.397334H375.168a13.44 13.44 0 0 1-13.44-13.397334v-35.84c0-7.424 6.016-13.44 13.44-13.44h260.522667z"
        )
    }.build().also { _timeLockStandard = it }

/**
 * 小形态时段锁：空心钟圈 + **实心时段扇形** + 角锁。
 */
private val PeriodLockCompact: ImageVector
    get() = _periodLockCompact ?: ImageVector.Builder(
        name = "capability.period_lock.compact",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.2f
        ) {
            moveTo(12f, 3.2f)
            curveTo(7.14f, 3.2f, 3.2f, 7.14f, 3.2f, 12f)
            curveTo(3.2f, 16.86f, 7.14f, 20.8f, 12f, 20.8f)
            curveTo(16.86f, 20.8f, 20.8f, 16.86f, 20.8f, 12f)
            curveTo(20.8f, 7.14f, 16.86f, 3.2f, 12f, 3.2f)
            close()
        }
        path(fill = SolidColor(Color.Black.copy(alpha = 0.52f))) {
            moveTo(12f, 12f)
            lineTo(12f, 3.2f)
            curveTo(16.86f, 3.2f, 20.8f, 7.14f, 20.8f, 12f)
            close()
        }
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(12f, 7.0f)
            lineTo(12f, 12f)
            lineTo(15.6f, 14.0f)
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(16.5f, 15.5f)
            lineTo(21.2f, 15.5f)
            curveTo(21.64f, 15.5f, 22f, 15.86f, 22f, 16.3f)
            lineTo(22f, 20.6f)
            curveTo(22f, 21.04f, 21.64f, 21.4f, 21.2f, 21.4f)
            lineTo(16.5f, 21.4f)
            curveTo(16.06f, 21.4f, 15.7f, 21.04f, 15.7f, 20.6f)
            lineTo(15.7f, 16.3f)
            curveTo(15.7f, 15.86f, 16.06f, 15.5f, 16.5f, 15.5f)
            close()
        }
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(17.3f, 15.5f)
            lineTo(17.3f, 14.1f)
            curveTo(17.3f, 13.15f, 18.05f, 12.5f, 18.85f, 12.5f)
            curveTo(19.65f, 12.5f, 20.4f, 13.15f, 20.4f, 14.1f)
            lineTo(20.4f, 15.5f)
        }
    }.build().also { _periodLockCompact = it }

/**
 * 标准/强调 · 时段锁：日环上的「硬挡扇区」为主，角锁极简。
 * 与时长锁拉开：一眼是「某一段」，不是「一整日额度」。
 */
private val PeriodLockStandard: ImageVector
    get() = _periodLockStandard ?: ImageVector.Builder(
        name = "capability.period_lock.standard",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.85f
        ) {
            moveTo(11.2f, 2.9f)
            curveTo(6.6f, 2.9f, 2.9f, 6.6f, 2.9f, 11.2f)
            curveTo(2.9f, 15.8f, 6.6f, 19.5f, 11.2f, 19.5f)
            curveTo(15.8f, 19.5f, 19.5f, 15.8f, 19.5f, 11.2f)
            curveTo(19.5f, 6.6f, 15.8f, 2.9f, 11.2f, 2.9f)
            close()
        }
        // 右上扇区：被守护的时段
        path(fill = SolidColor(Color.Black.copy(alpha = 0.42f))) {
            moveTo(11.2f, 11.2f)
            lineTo(11.2f, 2.9f)
            curveTo(15.8f, 2.9f, 19.5f, 6.6f, 19.5f, 11.2f)
            close()
        }
        // 中心点，稳住构图
        path(fill = SolidColor(Color.Black)) {
            moveTo(11.2f, 10.35f)
            curveTo(11.67f, 10.35f, 12.05f, 10.73f, 12.05f, 11.2f)
            curveTo(12.05f, 11.67f, 11.67f, 12.05f, 11.2f, 12.05f)
            curveTo(10.73f, 12.05f, 10.35f, 11.67f, 10.35f, 11.2f)
            curveTo(10.35f, 10.73f, 10.73f, 10.35f, 11.2f, 10.35f)
            close()
        }
        // 锁梁
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.45f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(16.55f, 15.35f)
            lineTo(16.55f, 14.05f)
            curveTo(16.55f, 13.0f, 17.4f, 12.25f, 18.45f, 12.25f)
            curveTo(19.5f, 12.25f, 20.35f, 13.0f, 20.35f, 14.05f)
            lineTo(20.35f, 15.35f)
        }
        // 锁身
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.45f,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(15.55f, 15.35f)
            lineTo(21.35f, 15.35f)
            lineTo(21.35f, 20.85f)
            lineTo(15.55f, 20.85f)
            close()
        }
    }.build().also { _periodLockStandard = it }

/**
 * 能力徽标：按 [CapabilityForm] 选用字形与光学尺寸。
 * 默认 tint 为能力主题色；[active] = false 时灰显。
 */
@Composable
fun CapabilityMark(
    kind: CapabilityKind,
    form: CapabilityForm = CapabilityForm.Standard,
    active: Boolean = true,
    tint: Color = MonitorCapability.accent(kind),
    size: Dp = MonitorCapability.opticalSize(form),
    contentDescription: String? = MonitorCapability.label(kind),
    modifier: Modifier = Modifier
) {
    val icon = remember(kind, form) { MonitorCapability.glyph(kind, form) }
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = if (active) tint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
    )
}

/**
 * 能力徽标托盘：浅底 + 圆角容器，用于卡片等需要「有设计感」的入口。
 */
@Composable
fun CapabilityMarkPlate(
    kind: CapabilityKind,
    modifier: Modifier = Modifier,
    form: CapabilityForm = CapabilityForm.Emphasis,
    tint: Color = MonitorCapability.accent(kind),
    plateSize: Dp = 44.dp,
    corner: Dp = 13.dp,
    contentDescription: String? = MonitorCapability.label(kind)
) {
    val shape = RoundedCornerShape(corner)
    val markSize = when (form) {
        CapabilityForm.Compact -> 16.dp
        CapabilityForm.Standard -> 22.dp
        CapabilityForm.Emphasis -> 24.dp
    }
    Box(
        modifier = modifier
            .size(plateSize)
            .clip(shape)
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        CapabilityMark(
            kind = kind,
            form = form,
            tint = tint,
            size = markSize,
            contentDescription = contentDescription
        )
    }
}

/**
 * 兼容旧调用：直接传入 ImageVector。
 * 新代码请优先用 [CapabilityMark]。
 */
@Composable
fun CapabilityIcon(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean = true,
    tint: Color = LogoGreen,
    size: Dp = 18.dp,
    modifier: Modifier = Modifier
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = if (active) tint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
    )
}

/**
 * 成对/三能力徽标：开亮关灰。默认各用主题色；[activeTint] 非空时三枚同色。
 */
@Composable
fun CapabilityPairMarks(
    intentOn: Boolean,
    timeOn: Boolean,
    modifier: Modifier = Modifier,
    periodOn: Boolean = false,
    form: CapabilityForm = CapabilityForm.Compact,
    activeTint: Color? = null,
    chip: Boolean = true
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    val gap = if (form == CapabilityForm.Compact) 4.dp else 8.dp
    val markSize = MonitorCapability.opticalSize(form)
    Row(
        modifier = if (chip) {
            modifier
                .clip(shape)
                .background(cs.background.copy(alpha = 0.55f))
                .border(1.dp, cs.outline.copy(alpha = 0.12f), shape)
                .padding(
                    horizontal = if (form == CapabilityForm.Compact) 5.dp else 8.dp,
                    vertical = if (form == CapabilityForm.Compact) 2.dp else 5.dp
                )
        } else modifier,
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CapabilityMark(
            kind = CapabilityKind.IntentGate,
            form = form,
            active = intentOn,
            tint = activeTint ?: MonitorCapability.accent(CapabilityKind.IntentGate),
            size = markSize
        )
        CapabilityMark(
            kind = CapabilityKind.TimeLock,
            form = form,
            active = timeOn,
            tint = activeTint ?: MonitorCapability.accent(CapabilityKind.TimeLock),
            size = markSize
        )
        CapabilityMark(
            kind = CapabilityKind.PeriodLock,
            form = form,
            active = periodOn,
            tint = activeTint ?: MonitorCapability.accent(CapabilityKind.PeriodLock),
            size = markSize
        )
    }
}
