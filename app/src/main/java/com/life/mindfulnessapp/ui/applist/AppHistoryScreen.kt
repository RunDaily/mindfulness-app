package com.life.mindfulnessapp.ui.applist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.domain.model.TimelineEvent
import com.life.mindfulnessapp.domain.model.collapseTimelineForDisplay
import com.life.mindfulnessapp.ui.home.NoteEditDialog
import com.life.mindfulnessapp.ui.home.RecordEditFocus
import com.life.mindfulnessapp.ui.home.TimelineDisplayNode
import com.life.mindfulnessapp.ui.theme.LogoGreen

private val muted = Color(0xFF8E8E93)

/**
 * 单 App 历史记录：按日倒序，形态对齐首页时间轴；仅「今日」可编辑对照/备注。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppHistoryScreen(
    packageName: String,
    viewModel: AppHistoryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    LaunchedEffect(packageName) {
        viewModel.load(packageName)
    }

    val days by viewModel.days.collectAsState()
    val appInfo by viewModel.appInfo.collectAsState()
    val cs = MaterialTheme.colorScheme
    val iconMap = remember(appInfo) {
        appInfo?.let { mapOf(it.packageName to it) }.orEmpty()
    }

    var editingEvent by remember { mutableStateOf<TimelineEvent.UsageEvent?>(null) }
    var editFocus by remember { mutableStateOf(RecordEditFocus.Note) }
    var ready by remember(packageName) { mutableStateOf(false) }
    LaunchedEffect(packageName) {
        ready = false
        kotlinx.coroutines.delay(80)
        ready = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            appInfo?.appName ?: "记录",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp
                        )
                        Text(
                            "历史记录",
                            fontSize = 11.sp,
                            color = muted
                        )
                    }
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
        when {
            !ready -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = LogoGreen, strokeWidth = 2.dp)
                }
            }
            days.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "还没有这只锚的记录",
                        fontSize = 14.sp,
                        color = cs.onSurface.copy(alpha = 0.45f),
                        textAlign = TextAlign.Center
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 20.dp,
                        top = 8.dp,
                        bottom = 40.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    days.forEach { day ->
                        item(key = "header_${day.dayStartMs}") {
                            Text(
                                text = day.label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (day.isToday) LogoGreen else muted,
                                modifier = Modifier.padding(
                                    start = 4.dp,
                                    top = 12.dp,
                                    bottom = 10.dp
                                )
                            )
                            if (!day.isToday) {
                                Text(
                                    "往日记录仅供回看",
                                    fontSize = 11.sp,
                                    color = muted.copy(alpha = 0.75f),
                                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                                )
                            }
                        }
                        val displayItems = collapseTimelineForDisplay(day.events)
                        itemsIndexed(
                            items = displayItems,
                            key = { _, item -> "${day.dayStartMs}_${item.key}" }
                        ) { index, item ->
                            TimelineDisplayNode(
                                item = item,
                                isLast = index == displayItems.lastIndex,
                                iconMap = iconMap,
                                onRecordEdit = { event, focus ->
                                    if (day.isToday) {
                                        editingEvent = event
                                        editFocus = focus
                                    }
                                },
                                editable = day.isToday,
                                cardBg = cs.surface,
                                onSurface = cs.onSurface,
                                outline = cs.onSurface.copy(alpha = 0.12f)
                            )
                        }
                        item(key = "spacer_${day.dayStartMs}") {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }

    editingEvent?.let { event ->
        NoteEditDialog(
            event = event,
            cs = cs,
            focus = editFocus,
            onConfirm = { note, level ->
                viewModel.updateRecordReview(event.recordId, note, level)
                editingEvent = null
            },
            onDismiss = { editingEvent = null }
        )
    }
}
