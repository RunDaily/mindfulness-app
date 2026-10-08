package com.life.mindfulnessapp.ui.update

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.life.mindfulnessapp.data.repository.AppUpdateRepository
import com.life.mindfulnessapp.ui.theme.DayBorder
import com.life.mindfulnessapp.ui.theme.DayCardBg
import com.life.mindfulnessapp.ui.theme.DayTextPrimary
import com.life.mindfulnessapp.ui.theme.DayTextSecondary
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.theme.NightBorder
import com.life.mindfulnessapp.ui.theme.NightCardBg
import com.life.mindfulnessapp.ui.theme.NightTextPrimary
import com.life.mindfulnessapp.ui.theme.NightTextSecondary
import kotlin.math.roundToInt

/**
 * 全局更新弹窗宿主：订阅 [AppUpdateViewModel]，处理 Toast / 未知来源权限 / 外链打开。
 */
@Composable
fun AppUpdateHost(
    viewModel: AppUpdateViewModel,
    isDarkTheme: Boolean
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val whatsNew by viewModel.whatsNew.collectAsState()

    val installPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.clearInstallPermissionError()
        if (!viewModel.needsInstallPermission()) {
            viewModel.installDownloaded()
        } else {
            Toast.makeText(context, "需要允许安装未知应用后才能更新", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.toasts.collect { toast ->
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

    LaunchedEffect(state.errorMessage, state.phase) {
        if (state.errorMessage == AppUpdateRepository.NEED_INSTALL_PERMISSION &&
            state.phase == UpdatePhase.ReadyToInstall
        ) {
            installPermissionLauncher.launch(viewModel.installPermissionSettingsIntent())
        }
    }

    if (state.visible && state.release != null) {
        AppUpdateDialog(
            state = state,
            isDarkTheme = isDarkTheme,
            onDismiss = { viewModel.dismissOptional() },
            onSkipVersion = { viewModel.skipThisVersion() },
            onPrimary = {
                when (state.phase) {
                    UpdatePhase.Available, UpdatePhase.Error -> viewModel.startDownload()
                    UpdatePhase.ReadyToInstall -> viewModel.installDownloaded()
                    else -> Unit
                }
            },
            onOpenBrowser = { viewModel.openExternal() },
            onRetry = { viewModel.retry() }
        )
    } else if (whatsNew.visible && whatsNew.changelog.isNotBlank()) {
        WhatsNewDialog(
            versionName = whatsNew.versionName,
            changelog = whatsNew.changelog,
            isDarkTheme = isDarkTheme,
            onDismiss = { viewModel.dismissWhatsNew() }
        )
    }
}

@Composable
private fun WhatsNewDialog(
    versionName: String,
    changelog: String,
    isDarkTheme: Boolean,
    onDismiss: () -> Unit
) {
    val cardColor = if (isDarkTheme) NightCardBg else DayCardBg
    val textPrimary = if (isDarkTheme) NightTextPrimary else DayTextPrimary
    val accentGreen = if (isDarkTheme) LogoGreen else Color(0xFF27AE60)
    val displayName = versionName.trim().ifBlank { "" }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(cardColor)
                .padding(24.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(accentGreen.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = accentGreen,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "本版更新说明",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                        if (displayName.isNotEmpty()) {
                            Text(
                                text = "v$displayName",
                                fontSize = 12.sp,
                                color = accentGreen.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

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
                        color = textPrimary.copy(alpha = 0.75f),
                        lineHeight = 20.sp,
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentGreen,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("知道了", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun AppUpdateDialog(
    state: AppUpdateUiState,
    isDarkTheme: Boolean,
    onDismiss: () -> Unit,
    onSkipVersion: () -> Unit,
    onPrimary: () -> Unit,
    onOpenBrowser: () -> Unit,
    onRetry: () -> Unit
) {
    val release = state.release ?: return
    val cardColor = if (isDarkTheme) NightCardBg else DayCardBg
    val textPrimary = if (isDarkTheme) NightTextPrimary else DayTextPrimary
    val textSecondary = if (isDarkTheme) NightTextSecondary else DayTextSecondary
    val borderColor = if (isDarkTheme) NightBorder else DayBorder
    val accentGreen = if (isDarkTheme) LogoGreen else Color(0xFF27AE60)
    val force = state.forceUpdate
    val downloading = state.phase == UpdatePhase.Downloading
    val installing = state.phase == UpdatePhase.Installing
    val busy = downloading || installing || state.phase == UpdatePhase.Checking

    BackHandler(enabled = force) { /* 强更不可返回关闭 */ }

    Dialog(
        onDismissRequest = { if (!force && !downloading) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !force && !downloading,
            dismissOnClickOutside = !force && !downloading,
            usePlatformDefaultWidth = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(cardColor)
                .padding(24.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(accentGreen.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = accentGreen,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = if (force) "需要更新后继续使用" else "发现新版本",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                        Text(
                            text = "v${release.version_name}",
                            fontSize = 12.sp,
                            color = accentGreen.copy(alpha = 0.85f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val changelog = release.changelog.trim()
                if (changelog.isNotEmpty()) {
                    Text(
                        text = "更新说明",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(accentGreen.copy(alpha = 0.06f))
                            .border(1.dp, accentGreen.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = changelog,
                            fontSize = 13.sp,
                            color = textPrimary.copy(alpha = 0.75f),
                            lineHeight = 20.sp,
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                when (state.phase) {
                    UpdatePhase.Downloading -> {
                        val progress = state.downloadProgress
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
                                progress = { progress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                                color = accentGreen,
                                trackColor = accentGreen.copy(alpha = 0.15f)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "正在下载 ${(progress * 100).roundToInt()}%",
                                fontSize = 12.sp,
                                color = textSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    UpdatePhase.Installing -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = accentGreen
                            )
                            Text("正在打开安装程序…", fontSize = 12.sp, color = textSecondary)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    UpdatePhase.Error -> {
                        val msg = state.errorMessage
                            ?.takeIf { it != AppUpdateRepository.NEED_INSTALL_PERMISSION }
                            .orEmpty()
                        if (msg.isNotBlank()) {
                            Text(msg, fontSize = 12.sp, color = Color(0xFFE74C3C))
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                    else -> Unit
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!force && !busy) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = textPrimary.copy(alpha = 0.6f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                borderColor.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("稍后", fontSize = 14.sp)
                        }
                    }

                    val primaryLabel = when (state.phase) {
                        UpdatePhase.ReadyToInstall -> "立即安装"
                        UpdatePhase.Error -> "重试"
                        UpdatePhase.Downloading, UpdatePhase.Installing -> "请稍候"
                        else -> "立即更新"
                    }
                    Button(
                        onClick = {
                            when (state.phase) {
                                UpdatePhase.Error -> onRetry()
                                UpdatePhase.Downloading, UpdatePhase.Installing -> Unit
                                else -> onPrimary()
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentGreen,
                            contentColor = Color.White,
                            disabledContainerColor = accentGreen.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(primaryLabel, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (!busy) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onOpenBrowser) {
                            Text(
                                "浏览器下载",
                                fontSize = 12.sp,
                                color = textSecondary.copy(alpha = 0.7f)
                            )
                        }
                        if (!force) {
                            TextButton(onClick = onSkipVersion) {
                                Text(
                                    "跳过此版本",
                                    fontSize = 12.sp,
                                    color = textSecondary.copy(alpha = 0.55f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
