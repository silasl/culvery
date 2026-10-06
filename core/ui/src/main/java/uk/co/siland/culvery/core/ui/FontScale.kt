package uk.co.siland.culvery.core.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Draws [content] at [fontScale], scaled as the platform scales it (non-linearly for large text from API 34), for tests
 * and screenshots: Robolectric's own font scale reaches only activities started after it is set.
 */
@Composable
fun WithFontScale(fontScale: Float, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val density = remember(context, fontScale) {
        val config = Configuration(context.resources.configuration).apply { this.fontScale = fontScale }
        Density(context.createConfigurationContext(config))
    }
    CompositionLocalProvider(LocalDensity provides density, content = content)
}
