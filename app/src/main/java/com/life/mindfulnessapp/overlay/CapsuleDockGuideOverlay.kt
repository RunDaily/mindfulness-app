package com.life.mindfulnessapp.overlay

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.theme.LogoGreenBright

/** 格与格之间的透明分割线 */
private val DockGuideGap = 2.dp

/** 单行格高：整齐色块，不必对齐胶囊外形 */
private val DockGuideRowH = 36.dp

private val DockGuideGreen = LogoGreen.copy(alpha = 0.40f)
private val DockGuideGreenLight = LogoGreenBright.copy(alpha = 0.26f)
private val DockGuideGreenSelected = LogoGreenBright.copy(alpha = 0.68f)
private val DockGuideGreenLightSelected = LogoGreen.copy(alpha = 0.56f)

/** 上排三列 → 下排三列，与 6 个停靠位一一对应 */
private val DockGuideRows = listOf(
    listOf(
        AppPreferences.CAPSULE_DOCK_LEFT,
        AppPreferences.CAPSULE_DOCK_CENTER,
        AppPreferences.CAPSULE_DOCK_RIGHT
    ),
    listOf(
        AppPreferences.CAPSULE_DOCK_LOWER_LEFT,
        AppPreferences.CAPSULE_DOCK_LOWER_CENTER,
        AppPreferences.CAPSULE_DOCK_LOWER_RIGHT
    )
)

/**
 * 拖拽迷你胶囊时浮现的吸附网格：
 * **水平铺满**整屏宽（3 列均分），上下两排对应 6 个停靠位；
 * 绿 / 浅绿实心矩形 + 透明分割线；当前吸附格加深。
 *
 * @param bandTopYPx 上排格带顶边（屏幕坐标，与 WindowManager y 同源）
 * @param rowPitchPx 下排相对上排的行距（两行中心间距）；行高固定，中间空隙即透明分割
 */
@Composable
fun CapsuleDockGuideOverlay(
    highlightedDock: String?,
    visible: Boolean,
    bandTopYPx: Int,
    rowPitchPx: Int
) {
    val appear by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(180),
        label = "dock_guide_appear"
    )

    val density = LocalDensity.current
    val rowHPx = with(density) { DockGuideRowH.roundToPx() }
    val gapPx = with(density) { DockGuideGap.roundToPx() }
    val bandHPx = maxOf(rowPitchPx + rowHPx, rowHPx * 2 + gapPx)

    // 窗口本身只有顶部一条；始终占位，用 alpha 显隐，避免拖拽时临时挂全屏窗
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(appear)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset { IntOffset(0, bandTopYPx) }
                .fillMaxWidth()
                .height(with(density) { bandHPx.toDp() }),
            verticalArrangement = Arrangement.spacedBy(DockGuideGap)
        ) {
            DockGuideRows.forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(DockGuideGap)
                ) {
                    row.forEachIndexed { colIndex, dock ->
                        val selected = dock == highlightedDock
                        val lightCell = (rowIndex + colIndex) % 2 == 1
                        val fill = when {
                            selected && lightCell -> DockGuideGreenLightSelected
                            selected -> DockGuideGreenSelected
                            lightCell -> DockGuideGreenLight
                            else -> DockGuideGreen
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(fill)
                        )
                    }
                }
            }
        }
    }
}
