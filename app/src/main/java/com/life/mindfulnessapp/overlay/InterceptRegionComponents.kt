package com.life.mindfulnessapp.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 拦截页分区卡片：上 / 中 / 下共用外框 */
@Composable
internal fun InterceptRegionCard(
    themeConfig: InterceptThemeConfig,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(themeConfig.surfaceColor.copy(alpha = 0.96f))
            .border(1.dp, themeConfig.dividerColor.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 16.dp),
        content = content
    )
}

/** 上区左侧：App 图标 + 名称 + 副文案 */
@Composable
internal fun InterceptTopAppMeta(
    appName: String,
    subtitle: String,
    appIcon: (@Composable () -> Unit)?,
    themeConfig: InterceptThemeConfig,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(themeConfig.bgColor.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            if (appIcon != null) {
                appIcon()
            } else {
                Text(
                    text = appName.take(1),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = themeConfig.textPrimary
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = appName,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = themeConfig.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = themeConfig.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
