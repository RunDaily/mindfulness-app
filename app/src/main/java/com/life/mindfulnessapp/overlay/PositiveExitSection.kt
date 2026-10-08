package com.life.mindfulnessapp.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.domain.model.MeaningfulThing
import com.life.mindfulnessapp.domain.model.PlanBlock
import com.life.mindfulnessapp.domain.model.PositiveDestination
import com.life.mindfulnessapp.domain.model.PositiveExitChoice
import com.life.mindfulnessapp.domain.model.PositiveExitKind

/** 门下区：此刻可做 / 守计划 / 想去的地方 */
@Composable
internal fun PositiveExitSection(
    themeConfig: InterceptThemeConfig,
    things: List<MeaningfulThing>,
    activePlan: PlanBlock?,
    destinations: List<PositiveDestination>,
    destinationLabels: Map<String, String>,
    onThing: (MeaningfulThing) -> Unit,
    onMoreThings: () -> Unit,
    onPlan: (PlanBlock) -> Unit,
    onPlace: (PositiveDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "也可以去做",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = themeConfig.textSecondary.copy(alpha = 0.92f)
        )
        Spacer(modifier = Modifier.height(12.dp))

        val previewThings = things.take(2)
        PositiveExitRow(
            icon = "◎",
            title = "此刻可做",
            subtitle = when {
                previewThings.isEmpty() -> "短、轻、立刻离开"
                else -> previewThings.joinToString(" · ") { it.title }
            },
            themeConfig = themeConfig,
            featured = false,
            onClick = {
                val first = things.firstOrNull()
                if (first != null) onThing(first) else onMoreThings()
            },
            trailing = if (things.size > 1) {
                {
                    Text(
                        text = "更多",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = themeConfig.accentColor.copy(alpha = 0.9f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = onMoreThings
                            )
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            } else null
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (activePlan != null) {
            PositiveExitRow(
                icon = "▤",
                title = "守计划",
                subtitle = buildString {
                    append(activePlan.title)
                    append(" · ")
                    append(activePlan.label())
                },
                sceneLine = activePlan.whyLine(),
                themeConfig = themeConfig,
                featured = true,
                onClick = { onPlan(activePlan) }
            )
        } else {
            PositiveExitRow(
                icon = "▤",
                title = "守计划",
                subtitle = "此刻没有要守的计划",
                themeConfig = themeConfig,
                featured = false,
                enabled = false,
                onClick = {}
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        val primaryPlace = destinations.firstOrNull()
        PositiveExitRow(
            icon = "→",
            title = "想去的地方",
            subtitle = when {
                primaryPlace == null -> "换一个更值得的 App"
                else -> destinations.take(2).joinToString(" · ") { dest ->
                    dest.displayLabel(destinationLabels[dest.packageName] ?: dest.packageName)
                }
            },
            themeConfig = themeConfig,
            featured = false,
            enabled = primaryPlace != null,
            onClick = { primaryPlace?.let(onPlace) }
        )

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "三条都是离开这条门的出口",
            fontSize = 11.sp,
            color = themeConfig.textTertiary.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PositiveExitRow(
    icon: String,
    title: String,
    subtitle: String,
    themeConfig: InterceptThemeConfig,
    featured: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    sceneLine: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val alpha = if (enabled) 1f else 0.45f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (featured) themeConfig.accentColor.copy(alpha = 0.10f)
                else themeConfig.bgColor.copy(alpha = 0.35f)
            )
            .border(
                width = if (featured) 1.5.dp else 1.dp,
                color = if (featured) {
                    themeConfig.accentColor.copy(alpha = 0.35f)
                } else {
                    themeConfig.dividerColor.copy(alpha = 0.55f)
                },
                shape = RoundedCornerShape(14.dp)
            )
            .then(
                if (enabled) {
                    Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onClick
                    )
                } else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(themeConfig.accentColor.copy(alpha = 0.14f * alpha)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = icon,
                fontSize = 13.sp,
                color = themeConfig.accentColor.copy(alpha = 0.95f * alpha)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = themeConfig.textPrimary.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = themeConfig.textSecondary.copy(alpha = 0.9f * alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!sceneLine.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = sceneLine,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = themeConfig.accentColor.copy(alpha = 0.88f * alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        trailing?.invoke()
    }
}

fun MeaningfulThing.toPositiveExitChoice(): PositiveExitChoice =
    PositiveExitChoice(
        kind = PositiveExitKind.THING,
        title = title,
        launchPackageName = boundPackageName
    )

fun PlanBlock.toPositiveExitChoice(): PositiveExitChoice =
    PositiveExitChoice(
        kind = PositiveExitKind.PLAN,
        title = title,
        why = whyLine()?.removePrefix("为了 · ")
    )

fun PositiveDestination.toPositiveExitChoice(label: String): PositiveExitChoice =
    PositiveExitChoice(
        kind = PositiveExitKind.PLACE,
        title = displayLabel(label),
        launchPackageName = packageName
    )
