package uk.co.siland.culvery

import android.content.ActivityNotFoundException
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/** The kiosk's pinning over a real activity lifecycle (Robolectric drives it), with the window's effects recorded. */
@RunWith(AndroidJUnit4::class)
class KioskLifecycleTest {
    private class RecordingWindow : KioskWindow {
        val calls = mutableListOf<String>()
        override fun pin() { calls += "pin" }
        override fun unpin() { calls += "unpin" }
        override fun hideBars() { calls += "hideBars" }
        override fun showBars() { calls += "showBars" }
        override fun moveToBack() { calls += "moveToBack" }
    }

    private val window = RecordingWindow()
    private val front = FrontTracker()
    private var exited = false
    private var home = true
    private var changing = false
    private val said = mutableListOf<String>()
    private val controller = Robolectric.buildActivity(ComponentActivity::class.java)
    private val kiosk = kioskFor()

    private fun kioskFor() = KioskLifecycle(
        window,
        setupComplete = { true },
        isHomeApp = { home },
        kioskExited = { exited },
        returnedToFront = { exited = false },
        front = front,
        changingConfigurations = { changing },
        say = { said += it },
    )

    @Before
    fun setUp() {
        controller.get().lifecycle.addObserver(kiosk)
        controller.setup()
    }

    /** Review Focus 2: a fresh start (after a process death, say) remembers no "exited", so it pins. */
    @Test
    fun aFreshStartPins() {
        assertThat(window.calls).contains("pin")
    }

    /** Review Focus 5, D3: as the home app, Exit kiosk stays in front, and a Home press doesn't pin it again. */
    @Test
    fun exitKioskAsTheHomeAppStaysInFrontUnpinnedThroughAHomePress() {
        window.calls.clear()
        exited = true
        kiosk.exitKiosk()
        assertThat(window.calls).containsExactly("unpin", "showBars").inOrder()
        // Home pressed while Culvery is home and in front: paused and resumed, never stopped.
        controller.pause().resume()
        assertThat(window.calls).doesNotContain("pin")
    }

    @Test
    fun leavingAfterExitKioskAndComingBackPinsAgain() {
        exited = true
        kiosk.exitKiosk()
        window.calls.clear()
        controller.pause().stop().start().resume()
        assertThat(window.calls).contains("pin")
    }

    @Test
    fun withAnotherHomeAppExitKioskMovesToTheBack() {
        home = false
        exited = true
        window.calls.clear()
        kiosk.exitKiosk()
        assertThat(window.calls).containsExactly("unpin", "showBars", "moveToBack").inOrder()
    }

    /** A configuration change that waited for Culvery to return builds a new activity that never saw the stop. */
    @Test
    fun aRelaunchAfterBeingStoppedPinsAgain() {
        exited = true
        kiosk.exitKiosk()
        controller.pause().stop()
        window.calls.clear()
        val next = Robolectric.buildActivity(ComponentActivity::class.java)
        next.get().lifecycle.addObserver(kioskFor())
        next.setup()
        assertThat(window.calls).contains("pin")
    }

    @Test
    fun aConfigurationChangeWhileInFrontKeepsExited() {
        exited = true
        kiosk.exitKiosk()
        changing = true
        controller.pause().stop()
        window.calls.clear()
        val next = Robolectric.buildActivity(ComponentActivity::class.java)
        next.get().lifecycle.addObserver(kioskFor())
        next.setup()
        assertThat(exited).isTrue()
        assertThat(window.calls).doesNotContain("pin")
    }

    @Test
    fun whenNoHomeAppScreenOpensItPinsAgainAndSaysSo() {
        exited = true
        window.calls.clear()
        kiosk.openHomeAppScreen { throw ActivityNotFoundException() }
        assertThat(window.calls).containsExactly("unpin", "pin").inOrder()
        assertThat(exited).isFalse()
        assertThat(said).containsExactly(HOME_APP_NOT_OFFERED)
    }

    @Test
    fun aScreenThatOpensIsLeftToPinOnTheNextResume() {
        window.calls.clear()
        kiosk.openHomeAppScreen { }
        assertThat(window.calls).containsExactly("unpin")
        assertThat(said).isEmpty()
    }

    @Test
    fun aCancelledRoleDialogSaysSoUnlessCulveryIsNowHome() {
        home = true
        kiosk.roleAnswered(cancelled = true)
        kiosk.roleAnswered(cancelled = false)
        assertThat(said).isEmpty()
        home = false
        kiosk.roleAnswered(cancelled = false)
        assertThat(said).isEmpty()
        kiosk.roleAnswered(cancelled = true)
        assertThat(said).containsExactly(HOME_APP_NOT_OFFERED)
    }

    @Test
    fun theActivityWindowMovesTheTaskToTheBack() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        ActivityKioskWindow(activity).moveToBack()
        assertThat(shadowOf(activity).isTaskMovedToBack).isTrue()
    }
}
