package com.life.mindfulnessapp.ui.applist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.ui.theme.LogoGreen

/** 单能力配置页共用视觉：开关 / 嵌套卡 / 问号说明 */

@Composable
internal fun CapabilityGateSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    val cs = MaterialTheme.colorScheme
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = LogoGreen,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = cs.onSurface.copy(alpha = 0.18f),
            uncheckedBorderColor = Color.Transparent
        )
    )
}

@Composable
internal fun CapabilityHelpMark(
    expanded: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(LogoGreen.copy(alpha = 0.16f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.HelpOutline,
            contentDescription = if (expanded) "收起说明" else "查看说明",
            tint = LogoGreen,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
internal fun CapabilityNestedCard(
    emphasized: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cs.surface)
            .border(
                width = 1.dp,
                color = if (emphasized) LogoGreen.copy(alpha = 0.28f)
                else cs.outline.copy(alpha = 0.14f),
                shape = shape
            )
            .padding(horizontal = 16.dp, vertical = 16.dp),
        content = content
    )
}

@Composable
internal fun CapabilityHeroTitle(
    title: String,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Text(
        text = title,
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        color = cs.onSurface.copy(alpha = 0.94f),
        letterSpacing = (-0.6).sp,
        modifier = modifier
    )
}

@Composable
internal fun CapabilityHeroDescription(
    text: String,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Text(
        text = text,
        fontSize = 14.sp,
        color = cs.onSurface.copy(alpha = 0.42f),
        lineHeight = 21.sp,
        modifier = modifier
    )
}

/** 规则句：会改变行为的说明，不藏在问号后。 */
@Composable
internal fun CapabilityRuleCaption(
    text: String,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Text(
        text = text,
        fontSize = 13.sp,
        color = cs.onSurface.copy(alpha = 0.42f),
        lineHeight = 20.sp,
        modifier = modifier
    )
}

/**
 * 主能力头：左标题+效果句，右开关（编辑流）。
 * 添加流 [allowToggle]=false，视为已开，不再二次确认。
 */
@Composable
internal fun CapabilityHeroWithSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    allowToggle: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            CapabilityHeroTitle(title = title)
            Spacer(modifier = Modifier.height(10.dp))
            CapabilityHeroDescription(text = description)
        }
        if (allowToggle) {
            CapabilityGateSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

/** 轻松便笺：挂在问号下方 */
@Composable
internal fun CapabilityStickyNote(
    text: String,
    modifier: Modifier = Modifier
) {
    val noteBg = Color(0xFFFFF6D8)
    val noteEdge = Color(0xFFE8D9A8)
    val noteText = Color(0xFF5C5340)
    Column(
        modifier = modifier
            .rotate(-0.6f)
            .shadow(3.dp, RoundedCornerShape(6.dp), clip = false)
            .clip(RoundedCornerShape(6.dp))
            .background(noteBg)
            .border(1.dp, noteEdge.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 10.dp)
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0x55A8C99A))
        )
        Text(
            text = text,
            fontSize = 14.sp,
            color = noteText.copy(alpha = 0.90f),
            lineHeight = 22.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** 问号 + 可展开便笺的轻量块 */
@Composable
internal fun CapabilityHelpBlock(
    note: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        CapabilityHelpMark(expanded = expanded, onClick = onToggle)
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            CapabilityStickyNote(
                text = note,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth(0.92f)
            )
        }
    }
}
