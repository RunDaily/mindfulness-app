package com.life.mindfulnessapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.ui.schedule.ScheduleLockEditScreen
import com.life.mindfulnessapp.ui.schedule.ScheduleLockEditViewModel
import com.life.mindfulnessapp.ui.theme.MindfulnessAppTheme
import com.life.mindfulnessapp.ui.theme.chrome
import com.life.mindfulnessapp.ui.theme.rememberResolvedThemePack
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** 日程锁 · 添加/编辑时段（二级页）。 */
@AndroidEntryPoint
class ScheduleLockEditActivity : ComponentActivity() {

    @Inject
    lateinit var appPreferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val planId = intent.getStringExtra(EXTRA_PLAN_ID)
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
                    ScheduleLockEditScreen(
                        planId = planId,
                        onFinished = { finish() }
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_PLAN_ID = "plan_id"

        fun createIntent(context: Context, planId: String? = null): Intent =
            Intent(context, ScheduleLockEditActivity::class.java).apply {
                putExtra(EXTRA_PLAN_ID, planId ?: ScheduleLockEditViewModel.EXTRA_NEW)
            }
    }
}
