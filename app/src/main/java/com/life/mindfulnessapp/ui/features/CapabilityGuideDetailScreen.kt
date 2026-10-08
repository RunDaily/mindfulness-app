package com.life.mindfulnessapp.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.ui.applist.CapabilityCopy
import com.life.mindfulnessapp.ui.applist.CapabilityGuideMorphGallery
import com.life.mindfulnessapp.ui.theme.CapabilityForm
import com.life.mindfulnessapp.ui.theme.CapabilityKind
import com.life.mindfulnessapp.ui.theme.CapabilityMark
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.theme.MonitorCapability

/**
 * 能力入门详情：编辑式头部 + 功能说明 + 完整形态标本。
 */
@Composable
fun CapabilityGuideDetailScreen(
    kind: CapabilityKind,
    onNavigateBack: () -> Unit,
    onNavigateToBind: (CapabilityKind) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val copy = CapabilityCopy.of(kind)
    val accent = MonitorCapability.accent(kind)
    val isDark = cs.background.luminance() < 0.5f
    val scroll = rememberScrollState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = cs.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = cs.onSurface
                    )
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cs.background)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp, bottom = 20.dp)
            ) {
                Button(
                    onClick = { onNavigateToBind(kind) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LogoGreen,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "去添加",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            GuideHero(copy = copy, accent = accent)

            GuideSection(
                title = "功能说明",
                body = copy.featureExplain,
                note = copy.featureNote
            )

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "使用形态",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface.copy(alpha = 0.88f)
                )
                CapabilityGuideMorphGallery(
                    kind = kind,
                    isDarkTheme = isDark
                )
            }
        }
    }
}

/** 详情头部：大标题编辑区，不与列表卡共用 surface / 顶条。 */
@Composable
private fun GuideHero(
    copy: CapabilityCopy,
    accent: Color
) {
    val cs = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxWidth()) {
        CapabilityMark(
            kind = copy.kind,
            form = CapabilityForm.Emphasis,
            tint = accent.copy(alpha = 0.10f),
            size = 88.dp,
            modifier = Modifier.align(Alignment.TopEnd)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = copy.label,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = cs.onSurface,
                letterSpacing = (-0.8).sp,
                lineHeight = 36.sp
            )
            Text(
                text = copy.slogan,
                fontSize = 15.sp,
                color = cs.onSurface.copy(alpha = 0.42f),
                lineHeight = 22.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            GuideDetailIdentityBadges(
                identityOne = copy.identityOne,
                identityTwo = copy.identityTwo,
                accent = accent
            )
        }
    }
}

@Composable
private fun GuideSection(
    title: String,
    body: String,
    note: String? = null
) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurface.copy(alpha = 0.36f),
            letterSpacing = 0.6.sp
        )
        Text(
            text = body,
            fontSize = 15.sp,
            color = cs.onSurface.copy(alpha = 0.72f),
            lineHeight = 24.sp
        )
        if (!note.isNullOrBlank()) {
            Text(
                text = note,
                fontSize = 13.sp,
                color = cs.onSurface.copy(alpha = 0.44f),
                lineHeight = 20.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(cs.onSurface.copy(alpha = 0.035f))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }
    }
}
