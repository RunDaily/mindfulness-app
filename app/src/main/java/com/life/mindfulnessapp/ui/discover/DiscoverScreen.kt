package com.life.mindfulnessapp.ui.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.ui.plan.PageTitle

/** 发现 · 低频工具目录（工具在上 · 氛围淡挂）。 */
@Composable
fun DiscoverScreen(
    onOpenOverview: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenAwarenessPractice: () -> Unit,
    onOpenQuotePlay: () -> Unit,
    onOpenWallpaperStudio: () -> Unit,
    viewModel: DiscoverViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        PageTitle("发现")

        SectionLabel("工具", first = true)
        DirectoryRow(
            title = "总览",
            meta = state.overviewGlance,
            quiet = false,
            onClick = onOpenOverview
        )
        DirectoryRow(
            title = "日程锁",
            meta = state.scheduleGlance,
            quiet = false,
            onClick = onOpenSchedule
        )
        DirectoryRow(
            title = "觉察练习",
            meta = state.awarenessGlance,
            quiet = false,
            onClick = onOpenAwarenessPractice,
            showDivider = false
        )

        SectionLabel("氛围")
        DirectoryRow(
            title = "名言",
            meta = state.quoteGlance,
            quiet = true,
            onClick = onOpenQuotePlay
        )
        DirectoryRow(
            title = "壁纸",
            meta = state.wallpaperGlance.ifBlank { "制作" },
            quiet = true,
            onClick = onOpenWallpaperStudio,
            showDivider = false
        )
    }
}

@Composable
private fun SectionLabel(text: String, first: Boolean = false) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        fontSize = 11.sp,
        letterSpacing = 0.6.sp,
        modifier = Modifier.padding(
            top = if (first) 18.dp else 22.dp,
            bottom = 2.dp
        )
    )
}

@Composable
private fun DirectoryRow(
    title: String,
    meta: String,
    quiet: Boolean,
    onClick: () -> Unit,
    showDivider: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    val titleAlpha = if (quiet) 0.55f else 1f
    val metaAlpha = if (quiet) 0.32f else 0.55f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(top = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    color = colors.onBackground.copy(alpha = titleAlpha),
                    fontSize = if (quiet) 13.sp else 15.sp,
                    fontWeight = if (quiet) FontWeight.Normal else FontWeight.Medium
                )
                if (meta.isNotBlank()) {
                    Text(
                        meta,
                        color = colors.onSurfaceVariant.copy(alpha = metaAlpha),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
            Text(
                "›",
                color = colors.onSurfaceVariant.copy(alpha = 0.35f),
                fontSize = 14.sp
            )
        }
        if (showDivider) {
            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.35f))
        }
    }
}
