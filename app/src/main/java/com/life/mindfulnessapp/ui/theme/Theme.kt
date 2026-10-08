package com.life.mindfulnessapp.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.life.mindfulnessapp.domain.model.ThemeMode
import com.life.mindfulnessapp.domain.model.ThemePack

private val NightColorScheme = darkColorScheme(
    primary = LogoGreen,
    onPrimary = Color(0xFF00180A),
    primaryContainer = Color(0xFF0D2235),
    onPrimaryContainer = LogoGreenBright,
    secondary = LogoGreenDeep,
    onSecondary = Color(0xFF00180A),
    secondaryContainer = Color(0xFF152435),
    onSecondaryContainer = LogoGreenBright,
    tertiary = Color(0xFF5096D8),
    onTertiary = Color(0xFF001840),
    tertiaryContainer = Color(0xFF182845),
    onTertiaryContainer = Color(0xFFA8C8F0),
    background = NightBg,
    onBackground = NightTextPrimary,
    surface = NightCardBg,
    onSurface = NightTextPrimary,
    surfaceVariant = NightCardElevated,
    onSurfaceVariant = NightTextSecondary,
    outline = NightBorder,
    outlineVariant = NightDivider,
    error = DangerColor,
    onError = Color.White,
    scrim = Color(0xFF000000),
)

private val DayColorScheme = lightColorScheme(
    primary = Color(0xFF1B9E55),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCCF0DE),
    onPrimaryContainer = Color(0xFF003D20),
    secondary = Color(0xFF26BB68),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEAF6EF),
    onSecondaryContainer = Color(0xFF003D20),
    tertiary = Color(0xFF3577CC),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD4E7FF),
    onTertiaryContainer = Color(0xFF003070),
    background = DayBg,
    onBackground = DayTextPrimary,
    surface = DayCardBg,
    onSurface = DayTextPrimary,
    surfaceVariant = DayCardGreen,
    onSurfaceVariant = DayTextSecondary,
    outline = DayBorder,
    outlineVariant = DayDivider,
    error = Color(0xFFC0392B),
    onError = Color.White,
    scrim = Color(0xFF000000),
)

private val MistColorScheme = lightColorScheme(
    primary = MistAccent,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD0E4D8),
    onPrimaryContainer = Color(0xFF1A3026),
    secondary = Color(0xFF6FA08A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2EDE7),
    onSecondaryContainer = Color(0xFF1A3026),
    tertiary = Color(0xFF5B7F96),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD5E4EC),
    onTertiaryContainer = Color(0xFF1A3040),
    background = MistBg,
    onBackground = MistTextPrimary,
    surface = MistCardBg,
    onSurface = MistTextPrimary,
    surfaceVariant = MistCardGreen,
    onSurfaceVariant = MistTextSecondary,
    outline = MistBorder,
    outlineVariant = MistDivider,
    error = Color(0xFFC0392B),
    onError = Color.White,
    scrim = Color(0xFF000000),
)

@Composable
fun MindfulnessAppTheme(
    themePack: ThemePack? = null,
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val pack = themePack ?: if (darkTheme) ThemePack.Night else ThemePack.Day
    val colorScheme = when (pack) {
        ThemePack.Night -> NightColorScheme
        ThemePack.Day -> DayColorScheme
        ThemePack.Mist -> MistColorScheme
    }
    val chrome = remember(pack) { pack.chrome() }
    val lightBars = !pack.isDark

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            window?.let {
                WindowCompat.getInsetsController(it, view).apply {
                    isAppearanceLightStatusBars = lightBars
                    isAppearanceLightNavigationBars = lightBars
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalThemePack provides pack,
        LocalThemeChrome provides chrome,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

/** @deprecated 使用 [rememberResolvedThemePack] */
@Composable
fun rememberResolvedIsDark(themeMode: ThemeMode): Boolean =
    themeMode.resolveIsDark(isSystemInDarkTheme())

@Composable
fun rememberResolvedThemePack(
    preferred: ThemePack,
    followSystem: Boolean,
): ThemePack {
    val systemDark = isSystemInDarkTheme()
    return remember(preferred, followSystem, systemDark) {
        ThemePack.resolve(preferred, followSystem, systemDark)
    }
}

@Composable
fun rememberResolvedIsDark(
    preferred: ThemePack,
    followSystem: Boolean,
): Boolean = rememberResolvedThemePack(preferred, followSystem).isDark
