package com.life.mindfulnessapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.repository.RulePlanRepository
import com.life.mindfulnessapp.domain.model.RulePack
import com.life.mindfulnessapp.ui.plan.InstrumentConfigScreen
import com.life.mindfulnessapp.ui.plan.InstrumentDraft
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.theme.MindfulnessAppTheme
import com.life.mindfulnessapp.ui.theme.chrome
import com.life.mindfulnessapp.ui.theme.rememberResolvedThemePack
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 方案 · 单 App 规则配置（二级页）。 */
@AndroidEntryPoint
class InstrumentConfigActivity : ComponentActivity() {

    @Inject
    lateinit var appPreferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
        if (packageName.isBlank()) {
            finish()
            return
        }
        val bootPack = appPreferences.resolvedThemePack()
        window.setBackgroundDrawableResource(bootPack.chrome().windowBgRes)
        val darkTheme = bootPack.isDark
        val barStyle = if (darkTheme) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        }
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)

        setContent {
            val themePackPref by appPreferences.themePack.collectAsState()
            val themeFollow by appPreferences.themeFollowSystem.collectAsState()
            val themePack = rememberResolvedThemePack(themePackPref, themeFollow)
            val isDarkTheme = themePack.isDark
            SideEffect {
                window.setBackgroundDrawableResource(themePack.chrome().windowBgRes)
            }
            MindfulnessAppTheme(themePack = themePack) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    InstrumentConfigHost(
                        packageName = packageName,
                        onFinished = { finish() }
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "package_name"

        fun createIntent(context: Context, packageName: String): Intent =
            Intent(context, InstrumentConfigActivity::class.java).apply {
                putExtra(EXTRA_PACKAGE_NAME, packageName)
            }
    }
}

@HiltViewModel
class InstrumentConfigHostViewModel @Inject constructor(
    private val repository: RulePlanRepository,
    private val usageRecordRepository: com.life.mindfulnessapp.data.repository.UsageRecordRepository,
    private val sessionManager: com.life.mindfulnessapp.service.SessionManager,
    private val appPreferences: AppPreferences
) : ViewModel() {
    private val _pack = MutableStateFlow<RulePack?>(null)
    val pack: StateFlow<RulePack?> = _pack.asStateFlow()
    private val _gone = MutableStateFlow(false)
    val gone: StateFlow<Boolean> = _gone.asStateFlow()

    fun load(packageName: String) {
        viewModelScope.launch {
            _pack.value = repository.activePacks().find { it.packageName == packageName }
            if (_pack.value == null) _gone.value = true
        }
    }

    fun save(pack: RulePack) {
        viewModelScope.launch {
            repository.updatePack(pack)
            _gone.value = true
        }
    }

    fun remove(packageName: String) {
        viewModelScope.launch {
            repository.remove(packageName)
            _gone.value = true
        }
    }

    /** 今日已用是否已触达放宽前的日限额（含生效天花板）。 */
    suspend fun isDailyLimitHitToday(
        packageName: String,
        dailyLimitMinutes: Int
    ): Boolean {
        if (packageName.isBlank() || dailyLimitMinutes <= 0) return false
        val used = usageRecordRepository.getDailyUsageSeconds(
            packageName,
            System.currentTimeMillis()
        )
        val live = sessionManager.currentSession.value
            ?.takeIf { it.packageName == packageName }
            ?.currentSessionSeconds
            ?: 0L
        val usedSec = com.life.mindfulnessapp.domain.model.DailyCapFacts.usedSeconds(used + live)
        val baseSec = dailyLimitMinutes * 60L
        val effectiveSec = appPreferences.effectiveDailyLimitSeconds(packageName, dailyLimitMinutes)
        return com.life.mindfulnessapp.domain.model.DailyCapFacts.exhausted(usedSec, baseSec) ||
            com.life.mindfulnessapp.domain.model.DailyCapFacts.exhausted(usedSec, effectiveSec)
    }
}

@Composable
private fun InstrumentConfigHost(
    packageName: String,
    onFinished: () -> Unit,
    viewModel: InstrumentConfigHostViewModel = hiltViewModel()
) {
    val pack by viewModel.pack.collectAsState()
    val gone by viewModel.gone.collectAsState()
    LaunchedEffect(packageName) { viewModel.load(packageName) }
    LaunchedEffect(gone) { if (gone) onFinished() }

    val detail = pack
    if (detail == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = LogoGreen)
        }
        return
    }
    val baseline = remember(
        detail.packageName,
        detail.dailyMinutes,
        detail.periodStartMinute,
        detail.periodEndMinute,
        detail.browseCasualDailyMinutes,
        detail.searchDirectEnabled
    ) {
        InstrumentDraft.from(detail)
    }
    var editing by remember(detail.packageName) { mutableStateOf(baseline) }
    InstrumentConfigScreen(
        draft = editing,
        onChange = { editing = it },
        onBack = onFinished,
        backLabel = "‹ 返回",
        primaryLabel = "保存",
        onPrimary = {
            viewModel.save(editing.toPack().copy(rationale = detail.rationale))
        },
        secondaryLabel = "移出方案",
        onSecondary = { viewModel.remove(detail.packageName) },
        isDailyLimitHitToday = { baseMinutes ->
            viewModel.isDailyLimitHitToday(detail.packageName, baseMinutes)
        }
    )
}
