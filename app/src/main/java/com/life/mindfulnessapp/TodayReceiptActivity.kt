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
import com.life.mindfulnessapp.ui.home.DayReportScreen
import com.life.mindfulnessapp.ui.theme.MindfulnessAppTheme
import com.life.mindfulnessapp.ui.theme.chrome
import com.life.mindfulnessapp.ui.theme.rememberResolvedThemePack
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 桌面球入口：打开「今天」收据（全日门口决策脊线）。
 */
@AndroidEntryPoint
class TodayReceiptActivity : ComponentActivity() {

    @Inject
    lateinit var appPreferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

            SideEffect {
                window.setBackgroundDrawableResource(themePack.chrome().windowBgRes)
            }

            MindfulnessAppTheme(themePack = themePack) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DayReportScreen(
                        onNavigateBack = { finish() },
                        onOpenOverview = { dateMs ->
                            startActivity(MainActivity.openOverviewIntent(this, dateMs))
                            finish()
                        }
                    )
                }
            }
        }
    }

    companion object {
        fun createIntent(context: Context): Intent =
            Intent(context, TodayReceiptActivity::class.java)
    }
}
