package com.life.mindfulnessapp.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
 * 监控三能力的产品方言：
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
        CapabilityKind.IntentGate -> when (form) {
            CapabilityForm.Compact -> IntentGateCompact
            CapabilityForm.Standard, CapabilityForm.Emphasis -> IntentGateStandard
        }
        CapabilityKind.TimeLock -> when (form) {
            CapabilityForm.Compact -> TimeLockCompact
            CapabilityForm.Standard, CapabilityForm.Emphasis -> TimeLockStandard
        }
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

private var _intentGateCompact: ImageVector? = null
private var _intentGateStandard: ImageVector? = null
private var _timeLockCompact: ImageVector? = null
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
 * 小形态门：粗门框 + 内门线 + 把手，12dp 仍可辨。
 * （完整双线门框素材在小尺寸易糊）
 */
private val IntentGateCompact: ImageVector
    get() = _intentGateCompact ?: ImageVector.Builder(
        name = "capability.intent_gate.compact",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.4f,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(6f, 3.5f)
            lineTo(18f, 3.5f)
            lineTo(18f, 20.5f)
            lineTo(6f, 20.5f)
            close()
        }
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f
        ) {
            moveTo(7.6f, 5.2f)
            lineTo(16.4f, 5.2f)
            lineTo(16.4f, 18.8f)
            lineTo(7.6f, 18.8f)
            close()
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(14.6f, 11.3f)
            curveTo(15.26f, 11.3f, 15.8f, 11.84f, 15.8f, 12.5f)
            curveTo(15.8f, 13.16f, 15.26f, 13.7f, 14.6f, 13.7f)
            curveTo(13.94f, 13.7f, 13.4f, 13.16f, 13.4f, 12.5f)
            curveTo(13.4f, 11.84f, 13.94f, 11.3f, 14.6f, 11.3f)
            close()
        }
    }.build().also { _intentGateCompact = it }

/** 标准/强调：完整门素材（与 drawable/ic_capability_intent_gate 同源） */
private val IntentGateStandard: ImageVector
    get() = _intentGateStandard ?: ImageVector.Builder(
        name = "capability.intent_gate.standard",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 1024f,
        viewportHeight = 1024f
    ).apply {
        addSvgFillPath(
            "M761.344 119.296H226.816c-18.944 0-39.424 11.776-39.424 30.72V921.6h614.4V148.48c0.512-18.944-22.016-29.184-40.448-29.184z m-537.6 768c-2.048 0-2.048 0 0 0L221.696 163.84c0-5.12 5.12-10.24 10.24-10.24h522.24c8.704 0 13.824 5.12 13.824 13.824v720.384s0 1.536-1.536 1.536h-15.36V194.56c0-16.896-8.704-24.064-29.184-24.064H266.24c-18.944 0-27.136 8.704-27.136 24.064v692.736h-15.36z m49.152 0V204.8H716.8v682.496H272.896z"
        )
        addSvgFillPath(
            "M648.704 508.416c-16.896 0-32.256 13.824-32.256 32.256 0 16.896 13.824 32.256 32.256 32.256s32.256-13.824 32.256-32.256c0-16.896-13.824-32.256-32.256-32.256z"
        )
    }.build().also { _intentGateStandard = it }

/**
 * 小形态：粗钟圈 + 指针 + 角上小锁，12dp 仍可读「时间锁」。
 */
private val TimeLockCompact: ImageVector
    get() = _timeLockCompact ?: ImageVector.Builder(
        name = "capability.time_lock.compact",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.4f
        ) {
            moveTo(12f, 3.2f)
            curveTo(7.14f, 3.2f, 3.2f, 7.14f, 3.2f, 12f)
            curveTo(3.2f, 16.86f, 7.14f, 20.8f, 12f, 20.8f)
            curveTo(16.86f, 20.8f, 20.8f, 16.86f, 20.8f, 12f)
            curveTo(20.8f, 7.14f, 16.86f, 3.2f, 12f, 3.2f)
            close()
        }
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(12f, 7.2f)
            lineTo(12f, 12.2f)
            lineTo(15.6f, 14.4f)
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(16.6f, 15.6f)
            lineTo(21.2f, 15.6f)
            curveTo(21.64f, 15.6f, 22f, 15.96f, 22f, 16.4f)
            lineTo(22f, 20.6f)
            curveTo(22f, 21.04f, 21.64f, 21.4f, 21.2f, 21.4f)
            lineTo(16.6f, 21.4f)
            curveTo(16.16f, 21.4f, 15.8f, 21.04f, 15.8f, 20.6f)
            lineTo(15.8f, 16.4f)
            curveTo(15.8f, 15.96f, 16.16f, 15.6f, 16.6f, 15.6f)
            close()
        }
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(17.4f, 15.6f)
            lineTo(17.4f, 14.2f)
            curveTo(17.4f, 13.2f, 18.2f, 12.5f, 19f, 12.5f)
            curveTo(19.8f, 12.5f, 20.6f, 13.2f, 20.6f, 14.2f)
            lineTo(20.6f, 15.6f)
        }
    }.build().also { _timeLockCompact = it }

/** 标准/强调：完整时间锁定素材（与 drawable/ic_capability_time_lock 同源） */
private val TimeLockStandard: ImageVector
    get() = _timeLockStandard ?: ImageVector.Builder(
        name = "capability.time_lock.standard",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 1024f,
        viewportHeight = 1024f
    ).apply {
        addSvgFillPath(
            "M512 128a384 384 0 0 1 383.616 366.677333c-20.48-10.368-42.666667-17.877333-66.005333-21.888a320 320 0 1 0-242.474667 350.336c11.861333 20.138667 26.709333 38.4 43.861333 54.058667A384 384 0 1 1 512 128z m280.874667 384a96 96 0 0 1 96 96v10.666667H896a42.666667 42.666667 0 0 1 42.666667 42.666666V810.666667a42.666667 42.666667 0 0 1-42.666667 42.666666h-213.333333a42.666667 42.666667 0 0 1-42.666667-42.666666v-149.333334a42.666667 42.666667 0 0 1 42.666667-42.666666h14.208v-10.666667a96 96 0 0 1 96-96z m-3.541334 170.666667a21.333333 21.333333 0 0 0-20.992 17.493333L768 704v42.666667a21.333333 21.333333 0 0 0 42.325333 3.84L810.666667 746.666667v-42.666667a21.333333 21.333333 0 0 0-21.333334-21.333333z m3.541334-128c-29.44 0-53.333333 23.893333-53.333334 53.333333v10.666667h106.666667v-10.666667c0-29.44-23.893333-53.333333-53.333333-53.333333zM512 245.333333a32 32 0 0 1 32 32v202.666667h109.866667c15.914667 0 28.8 14.336 28.8 32s-12.885333 32-28.8 32h-134.4l-3.498667-0.256A32 32 0 0 1 480 512V277.333333a32 32 0 0 1 32-32z"
        )
    }.build().also { _timeLockStandard = it }

/** 小形态时段锁：钟圈 + 扇形时段 + 角锁 */
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
            strokeLineWidth = 2.4f
        ) {
            moveTo(12f, 3.2f)
            curveTo(7.14f, 3.2f, 3.2f, 7.14f, 3.2f, 12f)
            curveTo(3.2f, 16.86f, 7.14f, 20.8f, 12f, 20.8f)
            curveTo(16.86f, 20.8f, 20.8f, 16.86f, 20.8f, 12f)
            curveTo(20.8f, 7.14f, 16.86f, 3.2f, 12f, 3.2f)
            close()
        }
        path(fill = SolidColor(Color.Black.copy(alpha = 0.28f))) {
            moveTo(12f, 3.2f)
            curveTo(16.86f, 3.2f, 20.8f, 7.14f, 20.8f, 12f)
            lineTo(12f, 12f)
            close()
        }
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(12f, 7.2f)
            lineTo(12f, 12f)
            lineTo(15f, 13.8f)
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

/** 标准/强调时段锁：与 Compact 同构图，略加细节 */
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
            strokeLineWidth = 1.8f
        ) {
            moveTo(12f, 2.8f)
            curveTo(6.92f, 2.8f, 2.8f, 6.92f, 2.8f, 12f)
            curveTo(2.8f, 17.08f, 6.92f, 21.2f, 12f, 21.2f)
            curveTo(17.08f, 21.2f, 21.2f, 17.08f, 21.2f, 12f)
            curveTo(21.2f, 6.92f, 17.08f, 2.8f, 12f, 2.8f)
            close()
        }
        path(fill = SolidColor(Color.Black.copy(alpha = 0.22f))) {
            moveTo(12f, 2.8f)
            curveTo(17.08f, 2.8f, 21.2f, 6.92f, 21.2f, 12f)
            lineTo(12f, 12f)
            close()
        }
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(12f, 6.8f)
            lineTo(12f, 12f)
            lineTo(15.5f, 14.1f)
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(15.8f, 14.8f)
            lineTo(21.4f, 14.8f)
            curveTo(21.9f, 14.8f, 22.3f, 15.2f, 22.3f, 15.7f)
            lineTo(22.3f, 20.8f)
            curveTo(22.3f, 21.3f, 21.9f, 21.7f, 21.4f, 21.7f)
            lineTo(15.8f, 21.7f)
            curveTo(15.3f, 21.7f, 14.9f, 21.3f, 14.9f, 20.8f)
            lineTo(14.9f, 15.7f)
            curveTo(14.9f, 15.2f, 15.3f, 14.8f, 15.8f, 14.8f)
            close()
        }
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.4f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(16.7f, 14.8f)
            lineTo(16.7f, 13.2f)
            curveTo(16.7f, 12.1f, 17.55f, 11.35f, 18.6f, 11.35f)
            curveTo(19.65f, 11.35f, 20.5f, 12.1f, 20.5f, 13.2f)
            lineTo(20.5f, 14.8f)
        }
    }.build().also { _periodLockStandard = it }

/**
 * 能力徽标：按 [CapabilityForm] 选用字形与光学尺寸。
 * [active] = false 时灰显（能力关闭）。
 */
@Composable
fun CapabilityMark(
    kind: CapabilityKind,
    form: CapabilityForm = CapabilityForm.Standard,
    active: Boolean = true,
    tint: Color = LogoGreen,
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
 * 成对/三能力徽标：开亮关灰。默认 [CapabilityForm.Compact]。
 */
@Composable
fun CapabilityPairMarks(
    intentOn: Boolean,
    timeOn: Boolean,
    modifier: Modifier = Modifier,
    periodOn: Boolean = false,
    form: CapabilityForm = CapabilityForm.Compact,
    activeTint: Color = LogoGreen,
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
            tint = activeTint,
            size = markSize
        )
        CapabilityMark(
            kind = CapabilityKind.TimeLock,
            form = form,
            active = timeOn,
            tint = activeTint,
            size = markSize
        )
        CapabilityMark(
            kind = CapabilityKind.PeriodLock,
            form = form,
            active = periodOn,
            tint = activeTint,
            size = markSize
        )
    }
}
