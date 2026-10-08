package com.life.mindfulnessapp.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.domain.model.UsageDigestRow
import com.life.mindfulnessapp.ui.theme.LogoGreen

@Composable
fun UsageMirrorList(rows: List<UsageDigestRow>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val maxMin = rows.maxOfOrNull { it.avgDailyMinutes.coerceAtLeast(1) } ?: 1
    Column(modifier) {
        rows.forEach { row ->
            val fraction = (row.avgDailyMinutes / maxMin.toFloat()).coerceIn(0.08f, 1f)
            Column(Modifier.padding(vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(row.appName, color = colors.onBackground, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text(
                        "${row.avgDailyMinutes} 分 · ${row.avgDailyOpens} 次",
                        color = colors.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.onSurface.copy(alpha = 0.08f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(3.dp)
                            .background(if (row.eligible) LogoGreen else colors.onSurface.copy(alpha = 0.28f))
                    )
                }
                val note = buildString {
                    if (row.nightSharePercent >= 20) append("夜间 ${row.nightSharePercent}%")
                    if (!row.eligible) {
                        if (isNotEmpty()) append(" · ")
                        append("用量还轻，先看着")
                    }
                }
                if (note.isNotEmpty()) {
                    Text(note, color = colors.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}
