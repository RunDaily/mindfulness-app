package com.life.mindfulnessapp.ui.lookback

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.domain.model.WeekCapabilityMode
import com.life.mindfulnessapp.domain.model.WeekExcerpt
import com.life.mindfulnessapp.domain.model.WeekLookbackSnapshot
import com.life.mindfulnessapp.domain.model.WeekVerdictTone
import com.life.mindfulnessapp.domain.model.formatWeekDuration
import com.life.mindfulnessapp.domain.model.formatWeekRangeLabel
import com.life.mindfulnessapp.ui.theme.DangerColor
import com.life.mindfulnessapp.ui.theme.HeatmapNeutral
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.theme.WarningColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaveWeekSheet(
    snapshot: WeekLookbackSnapshot,
    isExporting: Boolean,
    onSave: (Bitmap) -> Unit,
    onShare: (Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val graphicsLayer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun capture(then: (Bitmap) -> Unit) {
        if (isExporting) return
        if (graphicsLayer.size.width <= 0 || graphicsLayer.size.height <= 0) {
            Toast.makeText(context, "卡片还在生成，请稍后再试", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
            then(bitmap)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = cs.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 20.dp)
        ) {
            Text(
                text = "留下这一周",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "一张回望卡，不是用量表格。",
                fontSize = 13.sp,
                color = cs.onSurface.copy(alpha = 0.45f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawWithContent {
                        graphicsLayer.record {
                            this@drawWithContent.drawContent()
                        }
                        drawContent()
                    }
            ) {
                WeekLookbackShareCard(snapshot = snapshot)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { capture(onSave) },
                enabled = !isExporting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LogoGreen,
                    contentColor = Color.White,
                    disabledContainerColor = LogoGreen.copy(alpha = 0.35f),
                    disabledContentColor = Color.White.copy(alpha = 0.7f)
                )
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                } else {
                    Icon(
                        Icons.Outlined.FileDownload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("保存图片", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = { capture(onShare) },
                enabled = !isExporting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = cs.onSurface.copy(alpha = 0.85f)
                )
            ) {
                Icon(
                    Icons.Outlined.IosShare,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("分享", fontWeight = FontWeight.Medium, fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "卡片含本周判词；有对照时含摘录。请只发给自己信任的人。",
                fontSize = 12.sp,
                color = cs.onSurface.copy(alpha = 0.38f),
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = cs.onSurface.copy(alpha = 0.40f)
                )
            ) {
                Text("取消", fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun WeekLookbackShareCard(
    snapshot: WeekLookbackSnapshot,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val verdict = snapshot.verdict
    val toneColor = when (verdict.tone) {
        WeekVerdictTone.Mindful, WeekVerdictTone.Bound -> LogoGreen
        WeekVerdictTone.Drift -> WarningColor
        WeekVerdictTone.Alert -> DangerColor
        WeekVerdictTone.Still -> cs.onSurface.copy(alpha = 0.55f)
        WeekVerdictTone.Unmoored -> cs.onSurface.copy(alpha = 0.42f)
    }
    val rangeLabel = formatWeekRangeLabel(snapshot.weekStartMs, snapshot.weekEndMs)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cs.background)
            .border(1.dp, cs.outlineVariant.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .padding(horizontal = 20.dp, vertical = 22.dp)
    ) {
        Text(
            text = "心锚 · 周回望",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = cs.onSurface.copy(alpha = 0.34f),
            letterSpacing = 0.35.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = rangeLabel,
            fontSize = 12.sp,
            color = cs.onSurface.copy(alpha = 0.34f)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = verdict.headline,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = toneColor,
            lineHeight = 30.sp,
            letterSpacing = (-0.5).sp
        )
        if (verdict.detail != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = verdict.detail,
                fontSize = 13.sp,
                color = cs.onSurface.copy(alpha = 0.42f),
                lineHeight = 18.sp
            )
        }

        when (snapshot.capabilityMode) {
            WeekCapabilityMode.IntentGate -> {
                if (snapshot.pulse.hasAnySignal) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        CardMetric("带着意图", snapshot.pulse.mindfulEnters.toString(), LogoGreen)
                        CardMetric("守住", snapshot.pulse.dismisses.toString(), HeatmapNeutral)
                        CardMetric("直进", snapshot.pulse.ungatedEnters.toString(), WarningColor)
                    }
                }
                if (snapshot.fulfillment.hasAny) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "和意图比",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface.copy(alpha = 0.40f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "对照过 ${snapshot.fulfillment.reviewedCount}" +
                            " · 没跑偏 ${snapshot.fulfillment.aligned}" +
                            " · 跑偏 ${snapshot.fulfillment.slight}" +
                            " · 跑远 ${snapshot.fulfillment.large}",
                        fontSize = 13.sp,
                        color = cs.onSurface.copy(alpha = 0.58f),
                        lineHeight = 18.sp
                    )
                    val excerpts = snapshot.excerpts.take(2)
                    if (excerpts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        excerpts.forEachIndexed { index, item ->
                            if (index > 0) Spacer(modifier = Modifier.height(10.dp))
                            CardExcerpt(item)
                        }
                    }
                }
            }
            WeekCapabilityMode.TimeLockOnly, WeekCapabilityMode.WatchOnly -> {
                if (snapshot.totalSeconds > 0L) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "已用 ${formatWeekDuration(snapshot.totalSeconds)}",
                        fontSize = 14.sp,
                        color = cs.onSurface.copy(alpha = 0.55f)
                    )
                }
            }
            WeekCapabilityMode.PeriodLockOnly -> {
                if (snapshot.pulse.dismisses > 0) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "守住 ${snapshot.pulse.dismisses} 次",
                        fontSize = 14.sp,
                        color = cs.onSurface.copy(alpha = 0.55f)
                    )
                }
            }
            WeekCapabilityMode.Unmoored -> Unit
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "心锚",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = LogoGreen.copy(alpha = 0.75f)
        )
    }
}

@Composable
private fun CardMetric(label: String, value: String, color: Color) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface.copy(alpha = 0.88f)
            )
        }
        Text(
            text = label,
            fontSize = 11.sp,
            color = cs.onSurface.copy(alpha = 0.40f)
        )
    }
}

@Composable
private fun CardExcerpt(item: WeekExcerpt) {
    val cs = MaterialTheme.colorScheme
    val levelLabel = UsageRecordEntity.MindfulnessLevel.displayLabel(item.mindfulnessLevel)
    val purposeLine = item.purpose
    Column(modifier = Modifier.fillMaxWidth()) {
        if (purposeLine != null) {
            Text(
                text = "「$purposeLine」",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = cs.onSurface.copy(alpha = 0.72f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "$levelLabel · ${item.appName}",
                fontSize = 12.sp,
                color = cs.onSurface.copy(alpha = 0.34f)
            )
        } else {
            Text(
                text = "$levelLabel · ${item.appName}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = cs.onSurface.copy(alpha = 0.72f)
            )
        }
    }
}
