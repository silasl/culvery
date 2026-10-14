package uk.co.siland.culvery.capability.door.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.door.BitmapSnapshot
import uk.co.siland.culvery.capability.door.DoorDays
import uk.co.siland.culvery.capability.door.DoorEvent
import uk.co.siland.culvery.capability.door.DoorKind
import uk.co.siland.culvery.capability.door.NOW
import uk.co.siland.culvery.capability.door.picture
import uk.co.siland.culvery.core.ui.CulveryTheme

/** The tab in the content area beside the rail, as the shell gives it. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DoorTabScreenshotTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val folder = TemporaryFolder()
    private val london = ZoneId.of("Europe/London")

    private fun snap(name: String, dark: Boolean, days: DoorDays, status: String? = null) {
        compose.setContent { CulveryTheme(dark = dark) { Box(Modifier.size(1116.dp, 690.dp)) { DoorTab(days, london, status, onOpenSnapshot = {}) } } }
        if (days.today.any { it.snapshot != null }) compose.waitUntil(5_000) { compose.waitForIdle(); compose.onAllNodesWithTag("door_thumb_image", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun days(): DoorDays {
        val file = File(folder.root, "1.jpg").also { BitmapSnapshot(picture(640, 360)).saveTo(it) }
        return DoorDays(
            listOf(
                DoorEvent(1, NOW, DoorKind.RING, "Front Door", false, file, false),
                DoorEvent(2, NOW - 40 * 60_000, DoorKind.MOTION, "Front Door", false, null, false),
                DoorEvent(3, NOW - 3 * 60 * 60_000, DoorKind.RING, "Front Door", true, null, true),
            ),
            listOf(DoorEvent(4, NOW - 20 * 60 * 60_000, DoorKind.MOTION, "Back Garden", false, null, true)),
        )
    }

    @Test fun tabDark() = snap("door_tab_dark", true, days())
    @Test fun tabLight() = snap("door_tab_light", false, days())
    @Test fun tabEmptyDark() = snap("door_tab_empty_dark", true, DoorDays(emptyList(), emptyList()))
    @Test fun tabStatusDark() = snap("door_tab_status_dark", true, DoorDays(emptyList(), emptyList()), status = "Culvery can’t see Ring’s notifications. Allow notification access in Settings › Door.")
}
