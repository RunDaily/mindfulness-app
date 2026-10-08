package com.life.mindfulnessapp.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.ui.theme.LogoGreen

@Composable
fun PageTitle(title: String, subtitle: String = "", modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = colors.onBackground)
        if (subtitle.isNotBlank()) {
            Text(subtitle, color = colors.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun HintChip(text: String, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(tint)
        )
        Text(text, color = tint, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
fun HairlineRow(
    title: String,
    meta: String,
    trailing: String,
    trailingSub: String,
    warn: Boolean = false,
    packageName: String? = null,
    onClick: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!packageName.isNullOrBlank()) {
            QuietAppMark(packageName)
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.onBackground, fontSize = 15.sp)
            if (meta.isNotBlank()) {
                Text(meta, color = colors.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                trailing,
                color = if (warn) colors.error else colors.onBackground,
                fontSize = 13.sp
            )
            if (trailingSub.isNotBlank()) {
                Text(trailingSub, color = colors.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
fun StatusLine(label: String, value: String, ok: Boolean, onClick: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = colors.onBackground, fontSize = 14.sp)
        Text(
            value,
            color = if (ok) LogoGreen else colors.error,
            fontSize = 12.sp
        )
    }
}
