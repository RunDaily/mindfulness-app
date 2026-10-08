package com.life.mindfulnessapp.overlay

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.domain.model.ThemePack
import com.life.mindfulnessapp.ui.theme.DayBg
import com.life.mindfulnessapp.ui.theme.DayTextPrimary
import com.life.mindfulnessapp.ui.theme.DayTextSecondary
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.theme.MistAccent
import com.life.mindfulnessapp.ui.theme.MistBg
import com.life.mindfulnessapp.ui.theme.MistTextHint
import com.life.mindfulnessapp.ui.theme.MistTextPrimary
import com.life.mindfulnessapp.ui.theme.MistTextSecondary

/**
 * 意图门 / 硬挡等拦截页墨色板。随 [ThemePack] 切换。
 */
data class GateInkPalette(
    val bg: Color,
    val ink: Color,
    val paper: Color,
    val fog: Color,
    val mist: Color,
    val leaf: Color,
    val leafSoft: Color,
    val sand: Color,
    val line: Color,
    val leafText: Color,
)

fun ThemePack.gateInk(): GateInkPalette = when (this) {
    ThemePack.Night -> GateInkPalette(
        bg = Color(0xFF070908),
        ink = Color(0xFF070908),
        paper = Color(0xFFE8EDE6),
        fog = Color(0xFF8B938C),
        mist = Color(0xFF5C655E),
        leaf = Color(0xFF3D7A4A),
        leafSoft = Color(0x243D7A4A),
        sand = Color(0xFFC4A35A),
        line = Color(0x1FE8EDE6),
        leafText = Color(0xFF9FD4A8),
    )
    ThemePack.Day -> GateInkPalette(
        bg = DayBg,
        ink = DayBg,
        paper = DayTextPrimary,
        fog = DayTextSecondary,
        mist = Color(0xFF8A948C),
        leaf = Color(0xFF1B9E55),
        leafSoft = Color(0x241B9E55),
        sand = Color(0xFFB08A3A),
        line = Color(0x1F191D2B),
        leafText = LogoGreen,
    )
    ThemePack.Mist -> GateInkPalette(
        bg = MistBg,
        ink = MistBg,
        paper = MistTextPrimary,
        fog = MistTextSecondary,
        mist = MistTextHint,
        leaf = MistAccent,
        leafSoft = Color(0x245A8F72),
        sand = Color(0xFFA89058),
        line = Color(0x1F24302A),
        leafText = MistAccent,
    )
}

/**
 * 拦截页取色。各拦截门面根组合经 [ProvideGateInk] 注入当前 [ThemePack]；
 * 未注入时回落到夜锚墨色（预览 / 兜底）。
 *
 * 浮层均在主线程绘制，用可变当前板即可让非 Composable 辅助函数也能取色。
 */
internal object LockInk {
    @Volatile
    private var active: GateInkPalette = ThemePack.Night.gateInk()

    fun install(palette: GateInkPalette) {
        active = palette
    }

    fun resetToNight() {
        active = ThemePack.Night.gateInk()
    }

    val bg: Color get() = active.bg
    val ink: Color get() = active.ink
    val paper: Color get() = active.paper
    val fog: Color get() = active.fog
    val mist: Color get() = active.mist
    val leaf: Color get() = active.leaf
    val leafSoft: Color get() = active.leafSoft
    val sand: Color get() = active.sand
    val line: Color get() = active.line
    val leafText: Color get() = active.leafText
}

/** 意图门根组合调用：装入气质墨色，离开时恢复夜锚默认。 */
@Composable
internal fun ProvideGateInk(themePack: ThemePack, content: @Composable () -> Unit) {
    val palette = remember(themePack) { themePack.gateInk() }
    DisposableEffect(palette) {
        LockInk.install(palette)
        onDispose { LockInk.resetToNight() }
    }
    // 同步装一次，避免首帧仍用旧板
    LockInk.install(palette)
    content()
}

@Composable
internal fun GateOutlineButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, LockInk.paper.copy(alpha = 0.28f), RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) LockInk.paper.copy(alpha = 0.9f) else LockInk.mist
        )
    }
}
