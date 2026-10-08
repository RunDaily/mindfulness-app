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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.ui.applist.CapabilityBatchPickScreen
import com.life.mindfulnessapp.ui.navigation.Screen
import com.life.mindfulnessapp.ui.theme.CapabilityKind
import com.life.mindfulnessapp.ui.theme.parseCapabilityKind
import com.life.mindfulnessapp.ui.theme.MindfulnessAppTheme
import com.life.mindfulnessapp.ui.theme.chrome
import com.life.mindfulnessapp.ui.theme.rememberResolvedThemePack
import com.life.mindfulnessapp.ui.theme.rememberResolvedIsDark
import com.life.mindfulnessapp.ui.vip.VipScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * App 多选网格：独立 Activity，系统进场整页压住主界面。
 */
@AndroidEntryPoint
class CapabilityBatchPickActivity : ComponentActivity() {

    @Inject
    lateinit var appPreferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val capabilityName = intent.getStringExtra(EXTRA_CAPABILITY).orEmpty()
        val capability = parseCapabilityKind(capabilityName)

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
                    CapabilityBatchPickNavHost(
                        capability = capability,
                        isDarkTheme = isDarkTheme,
                        onFinish = { finish() }
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_CAPABILITY = "capability"

        fun createIntent(context: Context, capability: CapabilityKind): Intent =
            Intent(context, CapabilityBatchPickActivity::class.java).putExtra(
                EXTRA_CAPABILITY,
                capability.name
            )
    }
}

@Composable
private fun CapabilityBatchPickNavHost(
    capability: CapabilityKind,
    isDarkTheme: Boolean,
    onFinish: () -> Unit
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "batch_pick_root"
    ) {
        composable("batch_pick_root") {
            CapabilityBatchPickScreen(
                capability = capability,
                onNavigateBack = onFinish,
                onBindSuccess = { /* 保存后留在网格 */ },
                onNavigateToVip = { navController.navigate(Screen.Vip.route) }
            )
        }

        composable(Screen.Vip.route) {
            VipScreen(
                isDarkTheme = isDarkTheme,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
