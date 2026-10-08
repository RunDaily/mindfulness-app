package com.life.mindfulnessapp.ui.features

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.domain.model.SystemUsageDayDetail
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.util.AppUsageFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppWeekRhythmScreen(
    viewModel: AppWeekRhythmViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val ui by viewModel.ui.collectAsState()
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.trackViewIfNeeded()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = ui.app?.appName ?: "一周节奏",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (ui.rangeLabel.isNotBlank()) {
                            Text(
                                text = ui.rangeLabel,
                                fontSize = 11.sp,
                                color = cs.onSurface.copy(alpha = 0.42f)
                            )
                        }
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
            !ui.hasUsagePermission -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("需要使用情况访问", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "授权后可查看这个 App 近 7 日的使用时段。",
                        fontSize = 13.sp,
                        color = cs.onSurface.copy(alpha = 0.45f),
                        textAlign = TextAlign.Center
                    )
                    TextButton(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }
                    ) {
                        Text("去授权", color = LogoGreen, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            ui.loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = LogoGreen, strokeWidth = 2.dp)
                }
            }
            else -> {
                val boardDays = remember(ui.days) {
                    ui.days.map { day ->
                        WeekRhythmDayUi(
                            dayStartMs = day.dayStartMs,
                            weekdayLetter = AppWeekRhythmViewModel.weekdayLetter(day.dayStartMs),
                            dayOfMonth = AppWeekRhythmViewModel.dayOfMonth(day.dayStartMs),
                            isToday = day.isToday,
                            sessions = day.sessions
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 14.dp)
                ) {
                    WeekRhythmBoard(
                        days = boardDays,
                        selectedDayStartMs = ui.selectedDay?.dayStartMs,
                        onSelectDay = viewModel::selectDay,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )

                    val selected = ui.selectedDay
                    if (selected != null) {
                        RhythmSelectedFoot(
                            day = selected,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, bottom = 4.dp)
                        )
                    }
                    if (ui.dataSourceNote.isNotBlank()) {
                        Text(
                            text = ui.dataSourceNote,
                            fontSize = 11.sp,
                            color = cs.onSurface.copy(alpha = 0.36f),
                            modifier = Modifier.padding(bottom = 12.dp, start = 2.dp, end = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RhythmSelectedFoot(
    day: SystemUsageDayDetail,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(cs.onSurface.copy(alpha = 0.04f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = day.label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = cs.onSurface
            )
            Text(
                text = if (day.openCount > 0) "打开 ${day.openCount} 次" else "没有打开",
                fontSize = 11.sp,
                color = cs.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text(
            text = if (day.totalSeconds > 0L) {
                AppUsageFormat.totalDurationCompact(day.totalSeconds)
            } else {
                "—"
            },
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (day.totalSeconds > 0L) LogoGreen else cs.onSurface.copy(alpha = 0.28f)
        )
    }
}
