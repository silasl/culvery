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
import uk.co.siland.culvery.core.ui.test.snap

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
                LightsTab(rooms, false, rooms.any { it.on }, null, status, null, emptyMap(), null, {}, NO_HOUSE_ACTIONS, RoomActions.None)
            }
        }

    /** Nine rooms, on and off mixed in Hue's order, so the images show the on rooms first. */
    private val many = listOf(
        room("Lounge", on = true, brightness = 70, scenes = listOf(RELAX, BRIGHT), active = RELAX.id),
        room("Kitchen", scenes = listOf(RELAX, BRIGHT)),
        room("Hall", on = true, dimmable = false),
        room("Garden", brightness = 30),
        room("Front Door", on = true, brightness = 45),
        room("Back Garden", scenes = listOf(RELAX)),
        room("Room 7", on = true, brightness = 90, scenes = listOf(BRIGHT), active = BRIGHT.id),
        room("Room 8"),
        room("Room 9", on = true, brightness = 20),
    )

    /** The tab at the shell's content size, as the grid fills it. */
    private fun grid(name: String, dark: Boolean, count: Int) = compose.snap(name, dark) {
        val rooms = many.take(count)
        Box(Modifier.size(1116.dp, 690.dp)) {
            LightsTab(rooms, false, rooms.any { it.on }, null, null, null, emptyMap(), null, {}, NO_HOUSE_ACTIONS, RoomActions.None)
        }
    }

    @Test fun grid4Dark() = grid("lights_grid_4_dark", true, 4)

    @Test fun grid6Dark() = grid("lights_grid_6_dark", true, 6)

    @Test fun grid9Dark() = grid("lights_grid_9_dark", true, 9)

    @Test fun grid9Light() = grid("lights_grid_9_light", false, 9)

    @Test fun tabDark() = snap("lights_tab_dark", true)

    @Test fun tabLight() = snap("lights_tab_light", false)

    @Test fun statusDark() = snap("lights_tab_status_dark", true, status = problemLine(LightsProblem.UNREACHABLE))

    @Test fun roomNoScenesDark() = snap("lights_room_no_scenes_dark", true, rooms = rooms.drop(3))
}
