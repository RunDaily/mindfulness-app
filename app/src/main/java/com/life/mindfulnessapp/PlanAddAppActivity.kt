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
import com.life.mindfulnessapp.ui.plan.PlanAddAppScreen
import com.life.mindfulnessapp.ui.theme.MindfulnessAppTheme
import com.life.mindfulnessapp.ui.theme.chrome
import com.life.mindfulnessapp.ui.theme.rememberResolvedThemePack
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** 方案「添加 App」：名单多选，写入中档默认后回方案。 */
@AndroidEntryPoint
class PlanAddAppActivity : ComponentActivity() {

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
        val preselect = intent.getStringExtra(EXTRA_PRESELECT_PACKAGE)?.trim().orEmpty()

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
                    PlanAddAppScreen(
                        onDone = { finish() },
                        preselectPackage = preselect.takeIf { it.isNotEmpty() },
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_PRESELECT_PACKAGE = "preselect_package"

        fun createIntent(context: Context, preselectPackage: String? = null): Intent =
            Intent(context, PlanAddAppActivity::class.java).apply {
                val pkg = preselectPackage?.trim().orEmpty()
                if (pkg.isNotEmpty()) putExtra(EXTRA_PRESELECT_PACKAGE, pkg)
            }
    }
}
