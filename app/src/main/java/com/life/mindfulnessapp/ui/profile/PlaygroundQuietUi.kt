package com.life.mindfulnessapp.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.ui.settings.ThemedSwitch

@Composable
internal fun QuietTopBar(
    title: String,
    textPrimary: Color,
    onBack: () -> Unit,
    textSecondary: Color = textPrimary.copy(alpha = 0.45f),
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = textSecondary
            )
        }
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = textPrimary,
            modifier = Modifier
                .weight(1f)
                .padding(start = 2.dp)
        )
        trailing?.invoke()
    }
}

@Composable
internal fun QuietNavRow(
    title: String,
    subtitle: String,
    textPrimary: Color,
    textSecondary: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    hairline: Color = textPrimary.copy(alpha = 0.12f),
    badge: String? = null,
    badgeColor: Color = Color(0xFFC4A35A)
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 13.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) textPrimary else textPrimary.copy(alpha = 0.35f)
            )
            if (!badge.isNullOrBlank()) {
                Text(
                    text = badge,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = badgeColor
                )
            }
        }
        if (subtitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = textSecondary.copy(alpha = if (enabled) 0.5f else 0.28f),
                lineHeight = 15.sp
            )
        }
    }
    HorizontalDivider(color = hairline.copy(alpha = 0.55f))
}

@Composable
internal fun QuietSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    isDark: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = textPrimary
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = textSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
        ThemedSwitch(checked = checked, onCheckedChange = onCheckedChange, isDark = isDark)
    }
}
