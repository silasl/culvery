package uk.co.siland.culvery.capability.door.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.door.BitmapSnapshot
import uk.co.siland.culvery.capability.door.picture
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.SeasonalTheme

/** The card at its own width on `bg`, with room round it for its shadow. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DoorCardScreenshotTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val folder = TemporaryFolder()

    private fun snap(name: String, dark: Boolean, card: DoorCardUi, lowered: Boolean = false, seasonal: SeasonalTheme? = null) {
        compose.setContent {
            CulveryTheme(dark = dark, seasonal = seasonal) { Box(Modifier.background(Culvery.colors.bg).padding(32.dp)) { DoorCard(card, onGotIt = {}, lowered = lowered) } }
        }
        if (card.snapshot != null) compose.waitUntil(5_000) { compose.onAllNodesWithTag("door_snapshot").fetchSemanticsNodes().isNotEmpty() }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private val ring = DoorCardUi(unrecognised = false, device = "Front Door", time = "14:02", snapshot = null)

    @Test fun cardDark() = snap("door_card_dark", true, ring)
    @Test fun cardLight() = snap("door_card_light", false, ring)
    @Test fun cardSnapshotDark() = snap("door_card_snapshot_dark", true, ring.copy(snapshot = File(folder.root, "1.jpg").also { BitmapSnapshot(picture(640, 360)).saveTo(it) }))
    @Test fun cardUnrecognisedDark() = snap("door_card_unrecognised_dark", true, ring.copy(unrecognised = true))
    @Test fun cardLoweredDark() = snap("door_card_lowered_dark", true, ring, lowered = true)
    @Test fun cardHalloweenDark() = snap("door_card_halloween_dark", true, ring, seasonal = SeasonalTheme.HALLOWEEN)
    @Test fun cardHalloweenLight() = snap("door_card_halloween_light", false, ring, seasonal = SeasonalTheme.HALLOWEEN)
}
