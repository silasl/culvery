package uk.co.siland.culvery.capability.music.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import uk.co.siland.culvery.capability.music.SpeakerRoom
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme

/** Settings › Music connected, with three rooms, the Kitchen usual and 20 %. */
internal val connectedSettings = MusicSettingsUi(
    connected = true, status = null, findLine = null, finding = false,
    rooms = listOf(SpeakerRoom("RINCON_A", "Kitchen", 30), SpeakerRoom("RINCON_B", "Lounge", 40), SpeakerRoom("RINCON_C", "Dining room", 35)),
    usualRoomId = "RINCON_A", lowerTo = 20,
)

/** Draws [content] on the theme's background with grey art for every picture, and compares it with [name]'s baseline. */
internal fun ComposeContentTestRule.snap(name: String, dark: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val grey = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }.asImageBitmap()
    setContent {
        CulveryTheme(dark = dark) {
            CompositionLocalProvider(LocalArtSource provides ArtSource { grey }) {
                Box(Modifier.background(Culvery.colors.bg).then(modifier)) { content() }
            }
        }
    }
    onRoot().captureRoboImage("src/test/screenshots/$name.png")
}
