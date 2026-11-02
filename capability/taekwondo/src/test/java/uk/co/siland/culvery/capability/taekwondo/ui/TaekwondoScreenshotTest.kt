package uk.co.siland.culvery.capability.taekwondo.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import javax.inject.Provider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.taekwondo.CHOONG_MOO
import uk.co.siland.culvery.capability.taekwondo.FakeSpeaker
import uk.co.siland.culvery.capability.taekwondo.RunState
import uk.co.siland.culvery.capability.taekwondo.move
import uk.co.siland.culvery.capability.taekwondo.TaekwondoSettings
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.settingsDataStore
import uk.co.siland.culvery.core.plugin.test.TestOverlay
import uk.co.siland.culvery.core.ui.test.OneTapTray
import uk.co.siland.culvery.core.ui.test.snap

/** The practice screen full size, dark (the tablet is dark at night; the screen is a throwaway). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h800dp-land-hdpi")
class TaekwondoScreenshotTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val settings by lazy { TaekwondoSettings(settingsDataStore(scope) { File(folder.root, "taekwondo_settings.preferences_pb") }) }

    @After
    fun tearDown() = scope.cancel()

    private fun snap(name: String, state: RunState, detailed: Boolean = false) =
        compose.snap(name, dark = true, Modifier) {
            TaekwondoPanel(state, CHOONG_MOO, detailed, onDetailed = {}, onPattern = {}, onCancel = {}, onClose = {})
        }

    @Test
    fun tileDark() = compose.snap("taekwondo_tile_dark", dark = true, Modifier.padding(16.dp)) {
        CompositionLocalProvider(LocalOverlayHost provides TestOverlay()) {
            OneTapTray("taekwondo") { TaekwondoTile(settings, Provider { FakeSpeaker() }) }
        }
    }

    @Test
    fun tileLight() = compose.snap("taekwondo_tile_light", dark = false, Modifier.padding(16.dp)) {
        CompositionLocalProvider(LocalOverlayHost provides TestOverlay()) {
            OneTapTray("taekwondo") { TaekwondoTile(settings, Provider { FakeSpeaker() }) }
        }
    }

    @Test fun gridDark() = snap("taekwondo_grid_dark", RunState.Idle)

    @Test fun countingDark() = snap("taekwondo_counting_dark", RunState.Counting(10))

    @Test fun movingLongDark() = snap("taekwondo_moving_long_dark", RunState.Moving(21, move("21")), detailed = true)

    @Test fun movingLongestDark() = snap("taekwondo_moving_longest_dark", RunState.Moving(24, move("24")), detailed = true)

    @Test fun movingDark() = snap("taekwondo_moving_dark", RunState.Moving(1, CHOONG_MOO.moves[1]), detailed = true)
}
