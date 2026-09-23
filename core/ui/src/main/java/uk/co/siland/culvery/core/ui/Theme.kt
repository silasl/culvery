package uk.co.siland.culvery.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LocalHhColors = staticCompositionLocalOf { DarkColors }

object Culvery {
    val colors: HhColors
        @Composable get() = LocalHhColors.current
}

@Composable
fun CulveryTheme(dark: Boolean, content: @Composable () -> Unit) {
    val target = if (dark) DarkColors else LightColors
    val colors = HhColors(
        bg = animated(target.bg),
        surf = animated(target.surf),
        surf2 = animated(target.surf2),
        surf3 = animated(target.surf3),
        line = animated(target.line),
        ink = animated(target.ink),
        mute = animated(target.mute),
        accent = animated(target.accent),
        accentInk = animated(target.accentInk),
        accentSoft = animated(target.accentSoft),
        danger = animated(target.danger),
        dangerSoft = animated(target.dangerSoft),
        dangerInk = animated(target.dangerInk),
    )
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = colors.accent,
        onPrimary = colors.accentInk,
        background = colors.bg,
        onBackground = colors.ink,
        surface = colors.surf,
        onSurface = colors.ink,
        onSurfaceVariant = colors.mute,
    )
    val typography = Typography().let { t ->
        t.copy(
            bodyLarge = t.bodyLarge.copy(fontFamily = DmSans),
            bodyMedium = t.bodyMedium.copy(fontFamily = DmSans),
            labelLarge = t.labelLarge.copy(fontFamily = DmSans),
            titleMedium = t.titleMedium.copy(fontFamily = DmSans),
        )
    }
    CompositionLocalProvider(LocalHhColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

@Composable
private fun animated(target: Color): Color =
    animateColorAsState(target, animationSpec = tween(durationMillis = 400), label = "theme").value
