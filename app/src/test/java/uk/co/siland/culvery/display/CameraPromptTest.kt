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
import uk.co.siland.culvery.core.access.CorePermissions
import uk.co.siland.culvery.core.access.test.FakeAccess
import uk.co.siland.culvery.core.household.Role

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

    /** Fails if the gate MainActivity uses lets an Adult through, or asks for anything but the Admin PIN labelled "Allow the camera". */
    @Test
    fun theGateRefusesAnAdultAndAsksForTheAdminPin() = runTest {
        val access = FakeAccess(Role.ADULT)

        prompt(displaySettings(folder.root, scope), cameraUnpinGate(access)).request { launched++ }

        assertThat(access.asked).containsExactly(listOf(CorePermissions.CONNECTIONS_MANAGE))
        assertThat(access.labels).containsExactly(ALLOW_THE_CAMERA)
        assertThat(opened).isEmpty()
        assertThat(launched).isEqualTo(0)
    }

    /** Fails if an Admin's PIN through the gate doesn't open Android's dialog once. */
    @Test
    fun theGateOpensTheScreenOnceForAnAdmin() = runTest {
        val access = FakeAccess(Role.ADMIN)

        prompt(displaySettings(folder.root, scope), cameraUnpinGate(access)).request { launched++ }

        assertThat(access.asked).containsExactly(listOf(CorePermissions.CONNECTIONS_MANAGE))
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

    /** Fails if the activity builds the camera prompt without the Admin PIN gate. */
    @Test
    fun theActivityPassesTheAdminPinGate() {
        // Unit tests run in the module's directory.
        val activity = File("src/main/java/uk/co/siland/culvery/MainActivity.kt").readText()
        assertThat(activity).contains("CameraPrompt(camera, cameraUnpinGate(access),")
    }
}
