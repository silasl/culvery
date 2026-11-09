package uk.co.siland.culvery.display

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraPromptTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val opened = mutableListOf<String>()
    private var launched = 0
    private var pins = 0

    @After
    fun tearDown() = scope.cancel()

    private fun prompt(settings: DisplaySettings, mayUnpin: suspend () -> Boolean) = CameraPrompt(
        CameraAccess({ true }, { false }, settings, scope),
        mayUnpin,
        openSystemScreen = { missing, launch ->
            opened += missing
            launch()
        },
        returnToPinning = { pins++ },
    )

    /** Fails if a cancelled or wrong PIN still unpins the tablet or shows Android's dialog. */
    @Test
    fun aRefusedPinOpensNothing() = runTest {
        prompt(displaySettings(folder.root, scope)) { false }.request { launched++ }
        assertThat(opened).isEmpty()
        assertThat(launched).isEqualTo(0)
    }

    /** Fails if a granted PIN doesn't open Android's dialog exactly once with the prompt's own missing line. */
    @Test
    fun aGrantedPinOpensTheScreenOnce() = runTest {
        prompt(displaySettings(folder.root, scope)) { true }.request { launched++ }
        assertThat(opened).containsExactly(CAMERA_PROMPT_MISSING)
        assertThat(launched).isEqualTo(1)
    }

    /** Regression guard: Culvery pins again after the answer even when saving the ask throws. */
    @Test
    fun answeredRepinsAfterAnException() = runTest {
        // A directory where the settings file should be makes the save fail.
        val broken = displaySettings(folder.newFolder("settings").also { File(it, FILE_NAME).mkdir() }, scope)
        val prompt = CameraPrompt(CameraAccess({ true }, { false }, broken, scope), { true }, { _, _ -> }, { pins++ })
        assertThat(runCatching { prompt.answered(granted = true, rationale = false) }.isFailure).isTrue()
        assertThat(pins).isEqualTo(1)
    }
}
