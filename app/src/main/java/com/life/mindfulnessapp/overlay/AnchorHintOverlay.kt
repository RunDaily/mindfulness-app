package com.life.mindfulnessapp.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.domain.model.MidSessionCheckPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 锚提示：贴在陪伴条正上方的短胶囊，定住约 2.5 秒后淡出。
 * 与条同一窗口，条动它跟着动；不贴系统状态栏。
 */
@Composable
fun AttachedAnchorHint(
    text: String,
    isDarkTheme: Boolean,
    accent: Color,
    onFinished: () -> Unit,
    onHeightChanged: ((heightPx: Int) -> Unit)? = null,
    timeFont: FontFamily = FontFamily.Default,
    holdMs: Long = MidSessionCheckPolicy.ANCHOR_HINT_HOLD_MS,
) {
    val line = remember(text) { text.trim().ifBlank { "看见一下" } }
    val alpha = remember { Animatable(0f) }
    val rise = remember { Animatable(8f) }

    val fill = if (isDarkTheme) {
        Color(0xF01A1A1C)
    } else {
        Color(0xF5F2F2F4)
    }
    val stroke = if (isDarkTheme) {
        Color.White.copy(alpha = 0.16f)
    } else {
        Color.Black.copy(alpha = 0.10f)
    }
    val ink = if (isDarkTheme) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)

    LaunchedEffect(line) {
        alpha.snapTo(0f)
        rise.snapTo(8f)
        launch {
            alpha.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
        }
        rise.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
        delay(holdMs.coerceIn(1_800L, 3_200L))
        launch {
            rise.animateTo(-3f, tween(240, easing = FastOutSlowInEasing))
        }
        alpha.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
        onHeightChanged?.invoke(0)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .onSizeChanged { onHeightChanged?.invoke(it.height) },
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier
                .graphicsLayer {
                    this.alpha = alpha.value
                    translationY = rise.value
                }
                .wrapContentWidth()
                .clip(RoundedCornerShape(999.dp))
                .background(fill)
                .border(1.dp, stroke, RoundedCornerShape(999.dp))
                .padding(horizontal = 15.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .padding(end = 9.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.95f))
            )
            Text(
                text = line,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = ink.copy(alpha = 0.94f),
                fontFamily = timeFont,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                letterSpacing = 0.01.sp
            )
        }
    }
}
