package com.life.mindfulnessapp.overlay

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.domain.model.WalkAwarenessLevel

/**
 * 步行觉察 · 路况锚点。
 *
 * 视觉故意与监控用量胶囊区分：更窄、偏雾感中性色、慢呼吸；
 * 不走红黄告警升级。点击 = 本次步行隐藏。
 */
@Composable
fun WalkAwareOverlay(
    level: State<WalkAwarenessLevel>,
    label: State<String>,
    isDarkTheme: Boolean,
    onHideThisWalk: () -> Unit
) {
    val lv = level.value
    val text = label.value

    val islandBg = if (isDarkTheme) Color(0xE61C1F26) else Color(0xF0F2F3F5)
    val accent = if (isDarkTheme) Color(0xFF8B93A7) else Color(0xFF6B7385)
    val textColor = if (isDarkTheme) Color(0xFFE8EAED) else Color(0xFF2A2E38)

    val breath = rememberInfiniteTransition(label = "walk_aware_breath")
    val breathAlpha by breath.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "walk_dot_alpha"
    )
    val shiftX by breath.animateFloat(
        initialValue = -1.2f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "walk_shift"
    )

    Box(
        modifier = Modifier
            .statusBarsPadding()
            .padding(top = 6.dp, end = 12.dp)
            .wrapContentWidth(Alignment.End)
    ) {
        Row(
            modifier = Modifier
                .graphicsLayer { translationX = shiftX }
                .clip(RoundedCornerShape(percent = 50))
                .background(islandBg)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onHideThisWalk
                )
                .animateContentSize(
                    animationSpec = tween(320, easing = FastOutSlowInEasing)
                )
                .padding(
                    horizontal = when (lv) {
                        WalkAwarenessLevel.L0 -> 8.dp
                        WalkAwarenessLevel.L1 -> 12.dp
                        WalkAwarenessLevel.L2 -> 14.dp
                    },
                    vertical = when (lv) {
                        WalkAwarenessLevel.L0 -> 8.dp
                        else -> 9.dp
                    }
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(
                        when (lv) {
                            WalkAwarenessLevel.L0 -> 8.dp
                            WalkAwarenessLevel.L1 -> 7.dp
                            WalkAwarenessLevel.L2 -> 7.dp
                        }
                    )
                    .graphicsLayer { alpha = breathAlpha }
                    .clip(CircleShape)
                    .background(accent)
            )

            if (lv != WalkAwarenessLevel.L0 && text.isNotBlank()) {
                Box(modifier = Modifier.width(8.dp))
                Text(
                    text = text,
                    color = textColor.copy(
                        alpha = if (lv == WalkAwarenessLevel.L2) 0.92f else 0.78f
                    ),
                    fontSize = if (lv == WalkAwarenessLevel.L2) 13.sp else 12.sp,
                    fontWeight = if (lv == WalkAwarenessLevel.L2) {
                        FontWeight.SemiBold
                    } else {
                        FontWeight.Medium
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = (-0.2).sp,
                    modifier = Modifier.widthIn(
                        max = if (lv == WalkAwarenessLevel.L2) 200.dp else 96.dp
                    )
                )
            }
        }
    }
}
