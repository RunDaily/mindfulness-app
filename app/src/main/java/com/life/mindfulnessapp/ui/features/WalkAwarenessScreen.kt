package com.life.mindfulnessapp.ui.features

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.domain.model.WalkAwarenessCopy
import com.life.mindfulnessapp.domain.model.WalkAwarenessLevel
import com.life.mindfulnessapp.domain.usecase.CheckPermissionsUseCase
import com.life.mindfulnessapp.service.MonitorForegroundService
import com.life.mindfulnessapp.service.WalkAwarenessDetector
import com.life.mindfulnessapp.ui.settings.SettingsSwitchRow
import com.life.mindfulnessapp.ui.theme.LogoGreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class WalkAwarenessViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val detector: WalkAwarenessDetector,
    private val checkPermissionsUseCase: CheckPermissionsUseCase
) : ViewModel() {

    val enabled: StateFlow<Boolean> = appPreferences.walkAwarenessEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), appPreferences.isWalkAwarenessEnabled())

    fun hasStepSensor(): Boolean = detector.hasStepSensor()

    fun hasActivityPermission(): Boolean = detector.hasActivityPermission()

    fun hasOverlayPermission(): Boolean = checkPermissionsUseCase.hasOverlayPermission()

    fun setEnabled(enabled: Boolean) {
        appPreferences.setWalkAwarenessEnabled(enabled)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalkAwarenessScreen(
    viewModel: WalkAwarenessViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val enabled by viewModel.enabled.collectAsState()
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val activity = context as? Activity
    val isDark = cs.background.luminance() < 0.3f

    var previewLevel by remember { mutableStateOf(WalkAwarenessLevel.L1) }
    var permissionHint by remember { mutableStateOf<String?>(null) }

    val activityPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionHint = null
            finalizeEnable(viewModel, context)
        } else {
            permissionHint = "需要「身体活动」权限才能识别步行"
            viewModel.setEnabled(false)
        }
    }

    fun tryEnable(on: Boolean) {
        if (!on) {
            viewModel.setEnabled(false)
            permissionHint = null
            return
        }
        if (!viewModel.hasStepSensor()) {
            permissionHint = "当前设备没有可用的计步传感器"
            return
        }
        if (!viewModel.hasOverlayPermission()) {
            permissionHint = "请先开启悬浮窗权限，路况锚点才能浮在其他 App 上"
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !viewModel.hasActivityPermission()) {
            val perm = Manifest.permission.ACTIVITY_RECOGNITION
            if (activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
            ) {
                permissionHint = "识别步行需要身体活动权限"
            }
            activityPermLauncher.launch(perm)
            return
        }
        finalizeEnable(viewModel, context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("步行觉察", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cs.background,
                    titleContentColor = cs.onBackground,
                    navigationIconContentColor = cs.onBackground
                )
            )
        },
        containerColor = cs.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "走路时若还在看手机，右上角会浮着一枚路况锚点：常驻在场，用形态变化轻轻提醒注意安全。不拦截、不定时，点一下可隐藏至本次步行结束。",
                fontSize = 14.sp,
                color = cs.onSurface.copy(alpha = 0.62f),
                lineHeight = 21.sp
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(cs.surface)
            ) {
                SettingsSwitchRow(
                    title = "开启步行觉察",
                    subtitle = "全局生效 · 任意 App",
                    checked = enabled,
                    textPrimary = cs.onSurface,
                    textSecondary = cs.onSurface,
                    isDark = isDark,
                    onCheckedChange = { tryEnable(it) }
                )
            }

            permissionHint?.let { hint ->
                Text(
                    text = hint,
                    fontSize = 13.sp,
                    color = cs.error.copy(alpha = 0.85f),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            Text(
                text = "形态标本",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface.copy(alpha = 0.45f),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(cs.surface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WalkLevelChip(
                        label = "锚点",
                        selected = previewLevel == WalkAwarenessLevel.L0,
                        onClick = { previewLevel = WalkAwarenessLevel.L0 }
                    )
                    WalkLevelChip(
                        label = "轻语",
                        selected = previewLevel == WalkAwarenessLevel.L1,
                        onClick = { previewLevel = WalkAwarenessLevel.L1 }
                    )
                    WalkLevelChip(
                        label = "醒神",
                        selected = previewLevel == WalkAwarenessLevel.L2,
                        onClick = { previewLevel = WalkAwarenessLevel.L2 }
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(88.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(cs.background)
                        .padding(16.dp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    WalkAwarePreviewIsland(
                        level = previewLevel,
                        label = WalkAwarenessCopy.labelFor(previewLevel, 1),
                        isDark = isDark
                    )
                }

                Text(
                    text = when (previewLevel) {
                        WalkAwarenessLevel.L0 -> "刚开始边走边看：极小圆点，只表示系统在场。"
                        WalkAwarenessLevel.L1 -> "持续约半分钟后：展开短词，如「慢一点」「抬头」。"
                        WalkAwarenessLevel.L2 -> "再久一些：整句短暂醒神，随后收回轻语；之后约两分钟再醒一次。"
                    },
                    fontSize = 13.sp,
                    color = cs.onSurface.copy(alpha = 0.5f),
                    lineHeight = 18.sp
                )
            }

            Text(
                text = "依赖设备计步传感器识别步行；息屏或回到心锚时自动收起。",
                fontSize = 12.sp,
                color = cs.onSurface.copy(alpha = 0.35f),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )
        }
    }
}

private fun finalizeEnable(viewModel: WalkAwarenessViewModel, context: android.content.Context) {
    viewModel.setEnabled(true)
    MonitorForegroundService.start(context)
}

@Composable
private fun WalkLevelChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Text(
        text = label,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        color = if (selected) LogoGreen else cs.onSurface.copy(alpha = 0.45f),
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) LogoGreen.copy(alpha = 0.12f) else cs.onSurface.copy(alpha = 0.04f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun WalkAwarePreviewIsland(
    level: WalkAwarenessLevel,
    label: String,
    isDark: Boolean
) {
    val islandBg = if (isDark) Color(0xE61C1F26) else Color(0xF0F2F3F5)
    val accent = if (isDark) Color(0xFF8B93A7) else Color(0xFF6B7385)
    val textColor = if (isDark) Color(0xFFE8EAED) else Color(0xFF2A2E38)
    val breath = rememberInfiniteTransition(label = "preview_breath")
    val alpha by breath.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            tween(2000, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "a"
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(islandBg)
            .animateContentSize(tween(280, easing = FastOutSlowInEasing))
            .padding(
                horizontal = if (level == WalkAwarenessLevel.L0) 8.dp else 12.dp,
                vertical = if (level == WalkAwarenessLevel.L0) 8.dp else 9.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .graphicsLayer { this.alpha = alpha }
                .clip(CircleShape)
                .background(accent)
        )
        if (level != WalkAwarenessLevel.L0 && label.isNotBlank()) {
            Box(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = textColor.copy(alpha = 0.85f),
                fontSize = if (level == WalkAwarenessLevel.L2) 13.sp else 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}
