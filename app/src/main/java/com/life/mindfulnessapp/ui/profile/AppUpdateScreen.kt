package com.life.mindfulnessapp.ui.profile

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.BuildConfig
import com.life.mindfulnessapp.data.repository.AppUpdateRepository
import com.life.mindfulnessapp.ui.settings.SettingsViewModel
import com.life.mindfulnessapp.ui.theme.DayBg
import com.life.mindfulnessapp.ui.theme.DayBorder
import com.life.mindfulnessapp.ui.theme.DayCardBg
import com.life.mindfulnessapp.ui.theme.DayTextPrimary
import com.life.mindfulnessapp.ui.theme.DayTextSecondary
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.theme.NightBg
import com.life.mindfulnessapp.ui.theme.NightBorder
import com.life.mindfulnessapp.ui.theme.NightCardBg
import com.life.mindfulnessapp.ui.theme.NightTextPrimary
import com.life.mindfulnessapp.ui.theme.NightTextSecondary
import com.life.mindfulnessapp.ui.theme.themeChrome
import com.life.mindfulnessapp.ui.update.AppUpdateToast
import com.life.mindfulnessapp.ui.update.AppUpdateViewModel
import com.life.mindfulnessapp.ui.update.UpdatePhase
import kotlin.math.roundToInt

@Composable
fun AppUpdateScreen(
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    appUpdateViewModel: AppUpdateViewModel = hiltViewModel(
        LocalContext.current as ComponentActivity
    ),
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val chrome = themeChrome()
    val isDarkTheme = chrome.isDark
    val pendingUpdate by appUpdateViewModel.pendingUpdate.collectAsState()
    val updateUi by appUpdateViewModel.uiState.collectAsState()

    val bgColor = chrome.bg
    val cardColor = chrome.card
    val textPrimary = chrome.textPrimary
    val textSecondary = chrome.textSecondary
    val borderColor = chrome.border
    val accentGreen = chrome.accent

    val installPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        appUpdateViewModel.clearInstallPermissionError()
        if (!appUpdateViewModel.needsInstallPermission()) {
            appUpdateViewModel.installDownloaded()
        } else {
            Toast.makeText(context, "需要允许安装未知应用后才能更新", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        appUpdateViewModel.prepareUpdatePage()
        appUpdateViewModel.checkOnUpdatePageVisible()
    }

    LaunchedEffect(Unit) {
        appUpdateViewModel.toasts.collect { toast ->
            when (toast) {
                AppUpdateToast.AlreadyLatest -> {
                    Toast.makeText(context, "已是最新版本", Toast.LENGTH_SHORT).show()
                }
                is AppUpdateToast.Message -> {
                    Toast.makeText(context, toast.text, Toast.LENGTH_SHORT).show()
                }
                is AppUpdateToast.OpenUrl -> {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(toast.url)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    }.onFailure {
                        Toast.makeText(context, "无法打开下载页", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    LaunchedEffect(updateUi.errorMessage, updateUi.phase) {
        if (updateUi.errorMessage == AppUpdateRepository.NEED_INSTALL_PERMISSION &&
            updateUi.phase == UpdatePhase.ReadyToInstall
        ) {
            installPermissionLauncher.launch(appUpdateViewModel.installPermissionSettingsIntent())
        }
    }

    val release = pendingUpdate?.release ?: updateUi.release
    val force = pendingUpdate?.forceUpdate == true || updateUi.forceUpdate
    val hasUpdate = release != null
    val downloading = updateUi.phase == UpdatePhase.Downloading
    val installing = updateUi.phase == UpdatePhase.Installing
    val readyToInstall = updateUi.phase == UpdatePhase.ReadyToInstall
    val busy = updateUi.pageChecking || downloading || installing ||
        updateUi.phase == UpdatePhase.Checking

    BackHandler(enabled = force && hasUpdate) { /* 强更不可返回 */ }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = bgColor,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!force || !hasUpdate) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = textPrimary
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }
                Text(
                    text = "检查更新",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimary,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(cardColor)
                    .border(1.dp, borderColor.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(accentGreen.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = accentGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = if (hasUpdate) {
                                    if (force) "需要更新后继续使用" else "发现新版本"
                                } else {
                                    "当前已是最新"
                                },
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                            Text(
                                text = if (hasUpdate) {
                                    "可更新至 v${release!!.version_name}"
                                } else {
                                    "当前 v${BuildConfig.VERSION_NAME}"
                                },
                                fontSize = 13.sp,
                                color = if (hasUpdate) accentGreen.copy(alpha = 0.9f) else textSecondary
                            )
                        }
                    }

                    if (hasUpdate) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "当前安装 v${BuildConfig.VERSION_NAME}",
                            fontSize = 12.sp,
                            color = textSecondary.copy(alpha = 0.65f)
                        )
                    }

                    if (updateUi.pageChecking) {
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = accentGreen
                            )
                            Text("正在检查更新…", fontSize = 13.sp, color = textSecondary)
                        }
                    }

                    val changelog = release?.changelog?.trim().orEmpty()
                    if (changelog.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "更新说明",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(accentGreen.copy(alpha = 0.06f))
                                .border(1.dp, accentGreen.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = changelog,
                                fontSize = 13.sp,
                                color = textPrimary.copy(alpha = 0.78f),
                                lineHeight = 20.sp
                            )
                        }
                    }

                    if (hasUpdate) {
                        Spacer(modifier = Modifier.height(20.dp))
                        when {
                            downloading -> {
                                val progress = updateUi.downloadProgress
                                if (progress < 0f) {
                                    LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = accentGreen,
                                        trackColor = accentGreen.copy(alpha = 0.15f)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("正在下载…", fontSize = 12.sp, color = textSecondary)
                                } else {
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.fillMaxWidth(),
                                        color = accentGreen,
                                        trackColor = accentGreen.copy(alpha = 0.15f)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "下载中 ${(progress * 100).roundToInt()}%",
                                        fontSize = 12.sp,
                                        color = textSecondary
                                    )
                                }
                            }
                            installing -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = accentGreen
                                    )
                                    Text("正在打开安装程序…", fontSize = 13.sp, color = textSecondary)
                                }
                            }
                            updateUi.phase == UpdatePhase.Error -> {
                                Text(
                                    text = updateUi.errorMessage ?: "更新失败",
                                    fontSize = 13.sp,
                                    color = Color(0xFFE74C3C)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = { appUpdateViewModel.retry() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("重试")
                                }
                            }
                            else -> {
                                Button(
                                    onClick = {
                                        if (readyToInstall) {
                                            appUpdateViewModel.installDownloaded()
                                        } else {
                                            appUpdateViewModel.startDownloadFromPage()
                                        }
                                    },
                                    enabled = !busy,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = accentGreen)
                                ) {
                                    Text(
                                        when {
                                            readyToInstall -> "立即安装"
                                            else -> "立即更新"
                                        },
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                if (!force) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    TextButton(
                                        onClick = { appUpdateViewModel.skipThisVersion() },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !busy
                                    ) {
                                        Text("跳过此版本", color = textSecondary)
                                    }
                                }
                            }
                        }
                    } else if (!updateUi.pageChecking) {
                        Spacer(modifier = Modifier.height(20.dp))
                        OutlinedButton(
                            onClick = { appUpdateViewModel.checkOnUpdatePageVisible() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy
                        ) {
                            Text("重新检查")
                        }
                    }
                }
            }
        }
    }
}
