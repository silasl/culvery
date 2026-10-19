package uk.co.siland.culvery.display

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** The first value matching [predicate], or a timeout failure after 5 s of real time instead of a hang. */
private suspend fun <T> Flow<T>.firstWithin(predicate: (T) -> Boolean = { true }): T =
    // Off the test's scheduler, whose virtual time would reach the timeout while the saving thread is still at work.
    withContext(Dispatchers.Default) { withTimeout(5_000) { first(predicate) } }

@RunWith(AndroidJUnit4::class)
class CameraAccessTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val settings by lazy { displaySettings(folder.root, scope) }
    private var granted = false

    private fun access(hasFrontCamera: Boolean = true) = CameraAccess({ hasFrontCamera }, { granted }, settings, scope)

    @After
    fun tearDown() = scope.cancel()

    /** 8 §4.3: Android won't ask again after a denial with no rationale. Fails if a denial without a rationale reads as NOT_ASKED. */
    @Test
    fun aDenialWithoutARationaleAfterAskingIsRefused() {
        assertThat(cameraState(hasFrontCamera = true, granted = false, asked = false, rationale = false)).isEqualTo(CameraState.NOT_ASKED)
        assertThat(cameraState(true, granted = false, asked = true, rationale = true)).isEqualTo(CameraState.NOT_ASKED)
        assertThat(cameraState(true, granted = false, asked = true, rationale = false)).isEqualTo(CameraState.REFUSED)
        assertThat(cameraState(true, granted = true, asked = true, rationale = false)).isEqualTo(CameraState.ALLOWED)
        assertThat(cameraState(false, granted = true, asked = true, rationale = false)).isEqualTo(CameraState.NO_CAMERA)
    }

    /** Fails if the switch counts as on before the camera is allowed, or as off when it was never touched once it is. */
    @Test
    fun theSwitchIsOnByDefaultOnlyOnceAllowed() {
        assertThat(DisplayPrefs().wakesByCamera(CameraState.ALLOWED)).isTrue()
        assertThat(DisplayPrefs().wakesByCamera(CameraState.NOT_ASKED)).isFalse()
        assertThat(DisplayPrefs(wakeByCamera = false).wakesByCamera(CameraState.ALLOWED)).isFalse()
    }

    /** Fails if one ask sends no request, or more than one, to the activity. */
    @Test
    fun askSendsOneRequest() = runTest {
        val camera = access()
        camera.requests.test {
            camera.ask()
            assertThat(awaitItem()).isEqualTo(Unit)
            expectNoEvents()
        }
    }

    /** Fails if a refusal isn't remembered as asked, so that it reads as NOT_ASKED again. */
    @Test
    fun answeredSavesThatCulveryAsked() = runTest {
        val camera = access()
        camera.answered(granted = false, rationale = false)
        camera.state.firstWithin { it == CameraState.REFUSED }
        assertThat(settings.prefs.firstWithin().cameraAsked).isTrue()
    }

    /** Fails if a permission taken away in Android's settings keeps reading ALLOWED after a resume, or reads NOT_ASKED. */
    @Test
    fun aPermissionTakenAwayInAndroidsSettingsIsSeenOnResume() = runTest {
        granted = true
        val camera = access()
        camera.answered(granted = true, rationale = false)
        camera.state.firstWithin { it == CameraState.ALLOWED }
        granted = false
        camera.refresh(rationale = false)
        camera.state.firstWithin { it == CameraState.REFUSED }
    }
}
