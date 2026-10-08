package com.life.mindfulnessapp.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 入门列表：紧凑扫读 badge。
 * 小星点 + 胶囊，适合一卡内并排放两个称号。
 */
@Composable
fun GuideListIdentityBadges(
    identityOne: String,
    identityTwo: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompactIdentityBadge(title = identityOne, accent = accent)
        CompactIdentityBadge(title = identityTwo, accent = accent)
    }
}

/**
 * 详情页：展示态 badge。
 * 略大、成对并排，像可佩戴的身份铭牌，但不做成巨型圆环勋章。
 */
@Composable
fun GuideDetailIdentityBadges(
    identityOne: String,
    identityTwo: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShowcaseIdentityBadge(
            title = identityOne,
            accent = accent,
            modifier = Modifier.weight(1f, fill = false)
        )
        ShowcaseIdentityBadge(
            title = identityTwo,
            accent = accent,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

@Composable
private fun CompactIdentityBadge(
    title: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier
            .clip(shape)
            .border(1.dp, accent.copy(alpha = 0.28f), shape)
            .background(accent.copy(alpha = 0.07f))
            .padding(start = 6.dp, end = 9.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.75f))
        )
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurface.copy(alpha = 0.72f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ShowcaseIdentityBadge(
    title: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .border(1.dp, accent.copy(alpha = 0.30f), shape)
            .background(accent.copy(alpha = 0.09f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.16f))
                .border(1.dp, accent.copy(alpha = 0.40f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(11.dp)
            )
        }
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurface.copy(alpha = 0.84f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
