package com.life.mindfulnessapp.ui.applist

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.life.mindfulnessapp.overlay.getInterceptThemeConfig
import com.life.mindfulnessapp.ui.theme.CapabilityForm
import com.life.mindfulnessapp.ui.theme.CapabilityKind
import com.life.mindfulnessapp.ui.theme.CapabilityMark
import com.life.mindfulnessapp.ui.theme.MonitorCapability

/** 形态预览里展示的 App：绑定流用已选；启动页用 [Anonymous]，不指向任何产品。 */
data class ExperiencePreviewSubject(
    val appName: String,
    val packageName: String = "",
    val icon: Drawable? = null,
    val sampleIntent: String = ""
) {
    companion object {
        val Anonymous = ExperiencePreviewSubject(appName = "", sampleIntent = "")
    }
}

/** 入门详情页展示的单一使用形态 */
enum class CapabilityMorph {
    IntentGateDoor,
    IntentGateCapsule,
    TimeLockCapsule,
    TimeLockLimitReached,
    PeriodLockDoor;

    /** 入门详情页标本框高度：按实际 UI 内容定高，避免截断。 */
    fun guideFrameHeight(): Dp = when (this) {
        IntentGateDoor -> 256.dp
        IntentGateCapsule -> 184.dp
        TimeLockCapsule -> 184.dp
        TimeLockLimitReached -> 272.dp
        PeriodLockDoor -> 296.dp
    }
}

/**
 * 三能力「真实使用形态」静默预览（绑定流等单形态场景）。
 */
@Composable
fun CapabilityExperiencePreview(
    kind: CapabilityKind,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = true,
    tall: Boolean = false,
    showCaption: Boolean = true,
    subject: ExperiencePreviewSubject? = null
) {
    val morph = when (kind) {
        CapabilityKind.IntentGate -> CapabilityMorph.IntentGateDoor
        CapabilityKind.TimeLock -> CapabilityMorph.TimeLockCapsule
        CapabilityKind.PeriodLock -> CapabilityMorph.PeriodLockDoor
    }
    CapabilityMorphPreview(
        morph = morph,
        modifier = modifier,
        isDarkTheme = isDarkTheme,
        tall = tall,
        showCaption = showCaption,
        subject = subject
    )
}

/** 入门详情：按能力展示完整、不截断的形态标本。 */
@Composable
fun CapabilityGuideMorphGallery(
    kind: CapabilityKind,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = true,
    subject: ExperiencePreviewSubject? = null
) {
    val morphs = when (kind) {
        CapabilityKind.IntentGate -> listOf(
            "打开前" to CapabilityMorph.IntentGateDoor,
            "使用中" to CapabilityMorph.IntentGateCapsule
        )
        CapabilityKind.TimeLock -> listOf(
            "使用中" to CapabilityMorph.TimeLockCapsule,
            "额度用尽" to CapabilityMorph.TimeLockLimitReached
        )
        CapabilityKind.PeriodLock -> listOf(
            "时段内打开" to CapabilityMorph.PeriodLockDoor
        )
    }
    val cs = MaterialTheme.colorScheme
    val resolved = subject ?: ExperiencePreviewSubject.Anonymous
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        morphs.forEach { (label, morph) ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = cs.onSurface.copy(alpha = 0.52f)
                )
                MorphSpecimenFrame(
                    isDarkTheme = isDarkTheme,
                    height = morph.guideFrameHeight()
                ) {
                    MorphContent(
                        morph = morph,
                        isDarkTheme = isDarkTheme,
                        subject = resolved,
                        forGuide = true
                    )
                }
            }
        }
    }
}

@Composable
fun CapabilityMorphPreview(
    morph: CapabilityMorph,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = true,
    tall: Boolean = false,
    showCaption: Boolean = true,
    subject: ExperiencePreviewSubject? = null
) {
    val resolved = subject ?: ExperiencePreviewSubject.Anonymous
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (showCaption) 8.dp else 0.dp)
    ) {
        if (showCaption) {
            val caption = when (morph) {
                CapabilityMorph.IntentGateDoor -> "打开前会看见这扇门"
                CapabilityMorph.IntentGateCapsule -> "用着时顶部浮着胶囊，显示本次意图"
                CapabilityMorph.TimeLockCapsule -> "用着时顶部浮着胶囊，显示今日用量"
                CapabilityMorph.TimeLockLimitReached -> "额度用尽后会拦住继续进入"
                CapabilityMorph.PeriodLockDoor -> "关键时段打开时会看见这扇硬门"
            }
            Text(
                text = caption,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
                letterSpacing = 0.2.sp
            )
        }
        PhoneFrame(isDarkTheme = isDarkTheme, tall = tall) {
            MorphContent(
                morph = morph,
                isDarkTheme = isDarkTheme,
                subject = resolved,
                forGuide = false
            )
        }
    }
}

@Composable
private fun MorphContent(
    morph: CapabilityMorph,
    isDarkTheme: Boolean,
    subject: ExperiencePreviewSubject,
    forGuide: Boolean
) {
    when (morph) {
        CapabilityMorph.IntentGateDoor -> IntentGateMorphPreview(isDarkTheme, subject, forGuide)
        CapabilityMorph.IntentGateCapsule -> IntentGateCapsuleMorphPreview(isDarkTheme, subject, forGuide)
        CapabilityMorph.TimeLockCapsule -> TimeLockMorphPreview(isDarkTheme, subject, forGuide)
        CapabilityMorph.TimeLockLimitReached -> TimeLockLimitReachedMorphPreview(isDarkTheme, subject, forGuide)
        CapabilityMorph.PeriodLockDoor -> PeriodLockMorphPreview(isDarkTheme, subject, forGuide)
    }
}

/** 绑定流等场景：固定高度手机框。 */
@Composable
private fun PhoneFrame(
    isDarkTheme: Boolean,
    tall: Boolean = false,
    content: @Composable () -> Unit
) {
    MorphSpecimenFrame(
        isDarkTheme = isDarkTheme,
        height = if (tall) 292.dp else 168.dp,
        content = content
    )
}

/** 入门详情：定高标本框，高度随形态匹配。 */
@Composable
private fun MorphSpecimenFrame(
    isDarkTheme: Boolean,
    height: Dp,
    content: @Composable () -> Unit
) {
    val frame = if (isDarkTheme) Color(0xFF2C2C2E) else Color(0xFFD1D1D6)
    val screen = if (isDarkTheme) Color(0xFF000000) else Color(0xFFF2F2F7)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, frame.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .background(screen)
    ) {
        content()
    }
}

@Composable
private fun IntentGateMorphPreview(
    isDarkTheme: Boolean,
    subject: ExperiencePreviewSubject,
    forGuide: Boolean = false
) {
    val theme = remember(isDarkTheme) { getInterceptThemeConfig(isDark = isDarkTheme) }
    val named = subject.appName.isNotBlank()
    val bone = theme.textTertiary.copy(alpha = 0.38f)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.bgColor)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (named) {
            PreviewAppBadge(
                appName = subject.appName,
                icon = subject.icon,
                size = 40.dp,
                fallbackColor = theme.surfaceColor,
                fallbackTextColor = theme.textPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subject.appName,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = theme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            PlaceholderBlock(
                color = theme.surfaceColor,
                width = 40.dp,
                height = 40.dp,
                corner = 10.dp
            )
            Spacer(modifier = Modifier.height(8.dp))
            PlaceholderBlock(
                color = bone,
                width = 72.dp,
                height = 10.dp
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "花时间思考，而不仅仅是消费。",
            fontSize = 11.sp,
            fontWeight = FontWeight.Light,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            color = theme.textSecondary.copy(alpha = 0.88f),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "此刻打开它，是为了什么？",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = theme.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "写下这一次要做的事",
            fontSize = 12.sp,
            fontWeight = FontWeight.Light,
            color = theme.textTertiary.copy(alpha = 0.9f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = if (forGuide) Modifier.height(20.dp) else Modifier.weight(1f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(theme.accentColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = theme.dismissButtonText,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = theme.accentForeground
            )
        }
    }
}

@Composable
private fun TimeLockMorphPreview(
    isDarkTheme: Boolean,
    subject: ExperiencePreviewSubject,
    forGuide: Boolean = false
) {
    val theme = remember(isDarkTheme) { getInterceptThemeConfig(isDark = isDarkTheme) }
    val island = if (isDarkTheme) Color(0xFF0A0A0C) else Color(0xFFF2F2F7)
    val shellText = if (isDarkTheme) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val hairline = if (isDarkTheme) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.20f)
    val bone = shellText.copy(alpha = 0.16f)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        if (isDarkTheme) Color(0xFF1A2330) else Color(0xFFD8E4F0),
                        if (isDarkTheme) Color(0xFF0E141C) else Color(0xFFB8C8DA)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 28.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (subject.appName.isNotBlank()) {
                PreviewAppBadge(
                    appName = subject.appName,
                    icon = subject.icon,
                    size = 52.dp,
                    fallbackColor = shellText.copy(alpha = 0.12f),
                    fallbackTextColor = shellText.copy(alpha = 0.88f)
                )
                Text(
                    text = subject.appName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = shellText.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                PlaceholderBlock(
                    color = bone,
                    width = 52.dp,
                    height = 52.dp,
                    corner = 14.dp
                )
                PlaceholderBlock(
                    color = bone,
                    width = 72.dp,
                    height = 10.dp
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlaceholderBlock(color = bone, widthFraction = 0.92f, height = 8.dp)
                PlaceholderBlock(color = bone, widthFraction = 0.64f, height = 8.dp)
                PlaceholderBlock(color = bone, widthFraction = 0.78f, height = 8.dp)
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(island)
                .border(0.5.dp, shellText.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(theme.capsuleAccentColor.copy(alpha = 0.55f))
            )
            CapabilityMark(
                kind = CapabilityKind.TimeLock,
                form = CapabilityForm.Compact,
                tint = theme.capsuleAccentColor.copy(alpha = 0.85f),
                size = 13.dp,
                modifier = Modifier.padding(start = 5.dp)
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .width(1.dp)
                    .height(12.dp)
                    .background(hairline)
            )
            PlaceholderBlock(
                color = theme.capsuleAccentColor.copy(alpha = 0.35f),
                width = 44.dp,
                height = 8.dp
            )
        }
    }
}

/** 意图门 · 使用中：顶部胶囊展示本次意图与剩余时长。 */
@Composable
private fun IntentGateCapsuleMorphPreview(
    isDarkTheme: Boolean,
    subject: ExperiencePreviewSubject,
    forGuide: Boolean = false
) {
    val theme = remember(isDarkTheme) { getInterceptThemeConfig(isDark = isDarkTheme) }
    val island = if (isDarkTheme) Color(0xFF0A0A0C) else Color(0xFFF2F2F7)
    val shellText = if (isDarkTheme) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val hairline = if (isDarkTheme) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.20f)
    val bone = shellText.copy(alpha = 0.16f)
    val accent = theme.capsuleAccentColor
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        if (isDarkTheme) Color(0xFF1A2330) else Color(0xFFD8E4F0),
                        if (isDarkTheme) Color(0xFF0E141C) else Color(0xFFB8C8DA)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 28.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (subject.appName.isNotBlank()) {
                PreviewAppBadge(
                    appName = subject.appName,
                    icon = subject.icon,
                    size = 52.dp,
                    fallbackColor = shellText.copy(alpha = 0.12f),
                    fallbackTextColor = shellText.copy(alpha = 0.88f)
                )
            } else {
                PlaceholderBlock(color = bone, width = 52.dp, height = 52.dp, corner = 14.dp)
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlaceholderBlock(color = bone, widthFraction = 0.92f, height = 8.dp)
                PlaceholderBlock(color = bone, widthFraction = 0.64f, height = 8.dp)
                PlaceholderBlock(color = bone, widthFraction = 0.78f, height = 8.dp)
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(island)
                .border(0.5.dp, shellText.copy(alpha = 0.10f), RoundedCornerShape(17.dp))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CapabilityMark(
                kind = CapabilityKind.IntentGate,
                form = CapabilityForm.Compact,
                tint = accent.copy(alpha = 0.85f),
                size = 13.dp
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .width(1.dp)
                    .height(12.dp)
                    .background(hairline)
            )
            Text(
                text = subject.sampleIntent.ifBlank { "查一下资料" },
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = shellText.copy(alpha = 0.82f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 88.dp)
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .width(1.dp)
                    .height(12.dp)
                    .background(hairline)
            )
            PlaceholderBlock(
                color = accent.copy(alpha = 0.40f),
                width = 28.dp,
                height = 8.dp
            )
        }
    }
}

/** 时长锁 · 额度用尽：日额度触顶后的拦截页。 */
@Composable
private fun TimeLockLimitReachedMorphPreview(
    isDarkTheme: Boolean,
    subject: ExperiencePreviewSubject,
    forGuide: Boolean = false
) {
    val theme = remember(isDarkTheme) { getInterceptThemeConfig("simple", isDarkTheme) }
    val bone = theme.textTertiary.copy(alpha = 0.40f)
    val accent = theme.limitAccentColor
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.bgColor)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "40 分",
            fontSize = 36.sp,
            fontWeight = FontWeight.Light,
            color = accent,
            letterSpacing = (-1).sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        CapabilityMark(
            kind = CapabilityKind.TimeLock,
            form = CapabilityForm.Emphasis,
            tint = accent,
            size = 20.dp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (subject.appName.isNotBlank()) "「${subject.appName}」今日额度已用完"
            else "今日额度已用完",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = theme.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "已用 40 分 · 每日限制 40 分",
            fontSize = 12.sp,
            color = theme.textSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = if (forGuide) Modifier.height(20.dp) else Modifier.weight(1f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "回到桌面",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = theme.limitAccentForeground
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        PlaceholderBlock(color = bone, width = 96.dp, height = 10.dp)
    }
}

@Composable
private fun PeriodLockMorphPreview(
    isDarkTheme: Boolean,
    subject: ExperiencePreviewSubject,
    forGuide: Boolean = false
) {
    val theme = remember(isDarkTheme) { getInterceptThemeConfig("simple", isDarkTheme) }
    val bone = theme.textTertiary.copy(alpha = 0.40f)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.bgColor)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PlaceholderBlock(
            color = theme.limitAccentColor.copy(alpha = 0.28f),
            width = 168.dp,
            height = 22.dp,
            corner = 6.dp
        )
        Spacer(modifier = Modifier.height(14.dp))
        CapabilityMark(
            kind = CapabilityKind.PeriodLock,
            form = CapabilityForm.Emphasis,
            tint = theme.limitAccentColor,
            size = 18.dp
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (subject.appName.isNotBlank()) {
            Text(
                text = "「${subject.appName}」此时段已锁定",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = theme.textPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PlaceholderBlock(color = bone, width = 64.dp, height = 12.dp)
                Text(
                    text = "此时段已锁定",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        PlaceholderBlock(
            color = bone,
            width = 120.dp,
            height = 8.dp
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "寄语",
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = theme.textTertiary,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        PlaceholderBlock(
            color = bone,
            width = 156.dp,
            height = 12.dp
        )
        Spacer(modifier = Modifier.height(6.dp))
        PlaceholderBlock(
            color = bone,
            width = 96.dp,
            height = 12.dp
        )
        Spacer(modifier = if (forGuide) Modifier.height(20.dp) else Modifier.weight(1f))
        Text(
            text = "离开，就是守住",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = theme.textTertiary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(theme.limitAccentColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "回到桌面",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = theme.limitAccentForeground
            )
        }
    }
}

@Composable
private fun PlaceholderBlock(
    color: Color,
    modifier: Modifier = Modifier,
    width: Dp? = null,
    widthFraction: Float? = null,
    height: Dp,
    corner: Dp = 4.dp
) {
    Box(
        modifier = modifier
            .then(
                when {
                    width != null -> Modifier.width(width)
                    widthFraction != null -> Modifier.fillMaxWidth(widthFraction)
                    else -> Modifier.fillMaxWidth()
                }
            )
            .height(height)
            .clip(RoundedCornerShape(corner))
            .background(color)
    )
}

@Composable
private fun PreviewAppBadge(
    appName: String,
    icon: Drawable?,
    size: Dp,
    fallbackColor: Color,
    fallbackTextColor: Color
) {
    val bitmap = remember(icon) {
        icon?.let { runCatching { it.toBitmap(128, 128) }.getOrNull() }
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.25f))
            .background(fallbackColor),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = appName,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(size * 0.25f))
            )
        } else {
            Text(
                text = appName.take(1),
                fontSize = (size.value * 0.38f).sp,
                fontWeight = FontWeight.Light,
                color = fallbackTextColor
            )
        }
    }
}
