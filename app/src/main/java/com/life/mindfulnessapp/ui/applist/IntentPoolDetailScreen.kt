package com.life.mindfulnessapp.ui.applist

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.domain.model.IntentPoolSession
import com.life.mindfulnessapp.ui.common.formatCompactDuration
import com.life.mindfulnessapp.ui.theme.LogoGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IntentPoolDetailScreen(
    packageName: String,
    entryId: String,
    onNavigateBack: () -> Unit,
    onNavigateToHistory: (Long) -> Unit = {},
    viewModel: IntentPoolViewModel = hiltViewModel()
) {
    LaunchedEffect(packageName, entryId) {
        viewModel.load(packageName)
        viewModel.loadDetail(entryId)
    }

    val detail by viewModel.detail.collectAsState()
    val snapshot by viewModel.snapshot.collectAsState()
    val timeScope by viewModel.timeScope.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme

    LaunchedEffect(actionMessage) {
        val msg = actionMessage ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        viewModel.consumeActionMessage()
    }

    var showCategoryMenu by remember { mutableStateOf(false) }
    var showMergePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = detail?.item?.entry?.displayName ?: "意图详情",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        containerColor = cs.background
    ) { padding ->
        val data = detail ?: run {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.CircularProgressIndicator(color = LogoGreen)
            }
            return@Scaffold
        }
        val item = data.item
        val categories = snapshot?.categoryBreakdown?.mapNotNull { it.category }.orEmpty()
        val scoped = scopedSeconds(item, timeScope)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(cs.surface)
                        .border(1.dp, cs.onSurface.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = formatIntentPoolDuration(scoped),
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                                color = cs.onSurface.copy(alpha = 0.94f)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${item.useCount} 次 · 首次 ${formatIntentPoolFirstSeen(item.firstUsedAt)} · 最近 ${formatIntentPoolRelativeTime(item.lastUsedAt)}",
                                fontSize = 13.sp,
                                color = cs.onSurface.copy(alpha = 0.48f),
                                lineHeight = 18.sp
                            )
                        }
                        Box {
                            TextButton(onClick = { showCategoryMenu = true }) {
                                val catLabel = item.category?.let { cat ->
                                    cat.emoji?.let { "$it " }.orEmpty() + cat.name
                                } ?: "未分类"
                                Text(catLabel, color = LogoGreen, fontSize = 14.sp)
                            }
                            DropdownMenu(
                                expanded = showCategoryMenu,
                                onDismissRequest = { showCategoryMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("未分类") },
                                    onClick = {
                                        showCategoryMenu = false
                                        viewModel.assignCategory(entryId, null)
                                    }
                                )
                                categories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(cat.emoji?.let { "$it ${cat.name}" } ?: cat.name)
                                        },
                                        onClick = {
                                            showCategoryMenu = false
                                            viewModel.assignCategory(entryId, cat.id)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    if (item.shareOfMindful > 0f) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "占该 App 有意识用量 ${(item.shareOfMindful * 100).toInt()}%",
                            fontSize = 13.sp,
                            color = cs.onSurface.copy(alpha = 0.52f)
                        )
                    }
                }

                if (item.aliases.size > 1) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "别名",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface.copy(alpha = 0.40f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item.aliases.forEach { alias ->
                            Text(
                                text = alias,
                                fontSize = 13.sp,
                                color = cs.onSurface.copy(alpha = 0.72f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(cs.onSurface.copy(alpha = 0.06f))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IntentDetailActionChip(
                        label = "合并",
                        onClick = { showMergePicker = true },
                        modifier = Modifier.weight(1f)
                    )
                    IntentDetailActionChip(
                        label = "隐藏",
                        onClick = {
                            viewModel.hideEntry(entryId)
                            onNavigateBack()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "相关记录",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface.copy(alpha = 0.40f)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            val grouped = data.sessions.groupBy { sessionDayLabel(it.startTime) }
            grouped.forEach { (dayLabel, sessions) ->
                item(key = "day_$dayLabel") {
                    Text(
                        text = dayLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = cs.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(sessions, key = { it.recordId }) { session ->
                    IntentSessionRow(
                        session = session,
                        onClick = { onNavigateToHistory(session.recordId) }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }

    if (showMergePicker) {
        val others = snapshot?.items?.filter { it.entry.id != entryId }.orEmpty()
        IntentMergePickerSheet(
            sourceName = detail?.item?.entry?.displayName.orEmpty(),
            candidates = others,
            onDismiss = { showMergePicker = false },
            onMergeIntoExisting = { targetId ->
                showMergePicker = false
                viewModel.mergeEntries(entryId, targetId, null)
                onNavigateBack()
            }
        )
    }
}

@Composable
private fun IntentDetailActionChip(
    label: String,
    accent: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Text(
        text = label,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = if (accent) LogoGreen else cs.onSurface.copy(alpha = 0.72f),
        modifier = modifier
            .clip(shape)
            .background(
                if (accent) LogoGreen.copy(alpha = 0.10f) else cs.onSurface.copy(alpha = 0.05f)
            )
            .border(
                1.dp,
                if (accent) LogoGreen.copy(alpha = 0.22f) else cs.onSurface.copy(alpha = 0.08f),
                shape
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

@Composable
private fun IntentSessionRow(
    session: IntentPoolSession,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val time = remember(session.startTime) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(session.startTime))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cs.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = time,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = cs.onSurface.copy(alpha = 0.82f),
            modifier = Modifier.width(52.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            if (session.purpose != session.purpose.trim()) {
                Text(
                    text = session.purpose,
                    fontSize = 13.sp,
                    color = cs.onSurface.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = formatCompactDuration(session.durationSeconds),
            fontSize = 13.sp,
            color = cs.onSurface.copy(alpha = 0.45f)
        )
    }
}

private fun sessionDayLabel(startTime: Long): String {
    val now = System.currentTimeMillis()
    val dayMs = 24L * 60 * 60 * 1000
    val diff = ((now - startTime) / dayMs).toInt()
    return when (diff) {
        0 -> "今天"
        1 -> "昨天"
        else -> SimpleDateFormat("M月d日 EEEE", Locale.CHINESE).format(Date(startTime))
    }
}
