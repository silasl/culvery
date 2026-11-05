package uk.co.siland.culvery.capability.door.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.door.FOLLOW_THEME_ID
import uk.co.siland.culvery.capability.door.KeepDays
import uk.co.siland.culvery.core.sound.BuiltInSound
import uk.co.siland.culvery.core.sound.Sound
import uk.co.siland.culvery.core.sound.SoundSource
import uk.co.siland.culvery.core.sound.asSound
import uk.co.siland.culvery.core.sound.id
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.SeasonalTheme

/** Settings › Door as Settings shows it: in the page area beside the 320 dp page list, tall enough for the whole page as Display is. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h1200dp-land-hdpi")
class DoorSettingsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val actions = DoorSettingsActions({}, {}, {}, {}, { _, _, _ -> }, {}, {}, {}, {}, {})

    private val sounds = BuiltInSound.entries.filter { it != BuiltInSound.HAPPY_BIRTHDAY }.map { it.asSound() } +
        Sound("file:Test Honk.wav", "Test Honk", SoundSource.OnTablet(File("Test Honk.wav")), null)

    private val connected = DoorSettingsUi(
        service = "Ring", hint = "Sign in as a Ring Shared User, and turn off the Ring app’s notification sound.", volume = 1.0f, doorbell = FOLLOW_THEME_ID,
        sounds = sounds,
        quietOn = true, quietFrom = LocalTime.of(21, 0), quietUntil = LocalTime.of(7, 0), status = null,
        lastHeard = "Last heard from Ring: Wednesday 14:02", appInstalled = true, accessAllowed = true, capturing = false, keep = KeepDays.TWO,
    )

    /** The window holds 1280 dp of the page, which is now less than all of it: [toEnd] scrolls to what is below. */
    private fun snap(name: String, dark: Boolean, ui: DoorSettingsUi, seasonal: SeasonalTheme? = null, toEnd: Boolean = false) {
        compose.setContent {
            val scroll = rememberScrollState()
            if (toEnd) LaunchedEffect(Unit) { scroll.scrollTo(scroll.maxValue) }
            CulveryTheme(dark = dark, seasonal = seasonal) {
                Box(Modifier.size(960.dp, 1200.dp).background(Culvery.colors.bg)) { InSettingsPane(scroll = scroll) { DoorSettingsScreen(ui, actions) } }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun doorDark() = snap("settings_door_dark", true, connected)

    @Test fun doorLight() = snap("settings_door_light", false, connected)

    @Test fun doorHalloweenDark() = snap("settings_door_halloween_dark", true, connected.copy(doorbell = BuiltInSound.ORGAN.id), SeasonalTheme.HALLOWEEN)

    @Test fun doorHalloweenLight() = snap("settings_door_halloween_light", false, connected.copy(doorbell = BuiltInSound.ORGAN.id), SeasonalTheme.HALLOWEEN)

    @Test fun doorKeepConfirmDark() = snap("settings_door_keep_confirm_dark", true, connected.copy(keep = KeepDays.THIRTY, shortenTo = KeepDays.TWO), toEnd = true)

    @Test fun doorKeepConfirmLight() = snap("settings_door_keep_confirm_light", false, connected.copy(keep = KeepDays.THIRTY, shortenTo = KeepDays.TWO), toEnd = true)

    @Test
    fun doorStepsDark() = snap("settings_door_steps_dark", true, connected.copy(accessAllowed = false, lastHeard = "Culvery hasn’t heard from Ring yet."), toEnd = true)
}
