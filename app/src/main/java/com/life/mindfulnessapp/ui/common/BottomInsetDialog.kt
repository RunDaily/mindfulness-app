package com.life.mindfulnessapp.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Activity 内全屏底栏面板（非 Dialog），继承宿主 WindowInsets，按钮不会被虚拟导航栏遮挡。
 */
@Composable
fun BottomInsetSheetOverlay(
    onDismissRequest: () -> Unit,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    scrimAlpha: Float = 0.38f,
    content: @Composable ColumnScope.() -> Unit
) {
    val cs = MaterialTheme.colorScheme
    BackHandler(enabled = dismissOnBackPress, onBack = onDismissRequest)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(20f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .then(
                    if (dismissOnClickOutside) {
                        Modifier.clickable(onClick = onDismissRequest)
                    } else {
                        Modifier
                    }
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(cs.surface)
                .clickable(enabled = false, onClick = {}),
            content = content
        )
    }
}
