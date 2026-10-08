package com.life.mindfulnessapp.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 今日记录入口 peek：`今天 · 守住 N · 进入 N ›`
 * 空日更淡；不解说架构。
 */
@Composable
fun TodayReceiptPeek(
    heldCount: Int,
    enterCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val empty = heldCount <= 0 && enterCount <= 0
    val label = if (empty) {
        "今天 · 还没有门口"
    } else {
        "今天 · 守住 $heldCount · 进入 $enterCount"
    }
    val ink = if (empty) {
        colors.onSurfaceVariant.copy(alpha = 0.45f)
    } else {
        colors.onSurface.copy(alpha = 0.78f)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = ink,
            fontSize = if (empty) 12.sp else 13.sp,
            fontWeight = if (empty) FontWeight.Normal else FontWeight.Medium
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "›",
            color = ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
