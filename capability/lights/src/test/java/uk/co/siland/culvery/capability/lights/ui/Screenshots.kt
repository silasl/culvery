package uk.co.siland.culvery.capability.lights.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme

/** Draws [content] on the theme's background and compares it with [name]'s baseline. */
internal fun ComposeContentTestRule.snap(name: String, dark: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    setContent {
        CulveryTheme(dark = dark) {
            Box(Modifier.background(Culvery.colors.bg).then(modifier)) { content() }
        }
    }
    onRoot().captureRoboImage("src/test/screenshots/$name.png")
}
