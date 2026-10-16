package uk.co.siland.culvery.capability.lights.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.lights.BRIGHT
import uk.co.siland.culvery.capability.lights.LightRoom
import uk.co.siland.culvery.capability.lights.LightsProblem
import uk.co.siland.culvery.capability.lights.RELAX
import uk.co.siland.culvery.capability.lights.problemLine
import uk.co.siland.culvery.capability.lights.room

/** The Lights tab in the shell's content area on the 1280×800 canvas. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h800dp-land-hdpi")
class LightsTabScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val rooms = listOf(
        room("Lounge", on = true, brightness = 70, scenes = listOf(RELAX, BRIGHT), active = RELAX.id),
        room("Kitchen", scenes = listOf(RELAX, BRIGHT)),
        room("Hall", on = true, dimmable = false),
        room("Garden", on = true, brightness = 30),
    )

    private fun snap(name: String, dark: Boolean, rooms: List<LightRoom> = this.rooms, status: String? = null) =
        compose.snap(name, dark, Modifier.padding(16.dp)) {
            Box(Modifier.size(1100.dp, 780.dp)) {
                LightsTab(rooms, false, null, status, null, emptyMap(), null, {}, NO_HOUSE_ACTIONS, RoomActions.None)
            }
        }

    @Test fun tabDark() = snap("lights_tab_dark", true)

    @Test fun tabLight() = snap("lights_tab_light", false)

    @Test fun statusDark() = snap("lights_tab_status_dark", true, status = problemLine(LightsProblem.UNREACHABLE))

    @Test fun rowNoScenesDark() = snap("lights_row_no_scenes_dark", true, rooms = rooms.drop(3))
}
