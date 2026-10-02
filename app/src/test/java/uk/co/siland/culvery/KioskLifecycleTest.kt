package uk.co.siland.culvery

import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

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
    private var exited = false
    private var home = true
    private val controller = Robolectric.buildActivity(ComponentActivity::class.java)
    private val kiosk = KioskLifecycle(
        window,
        setupComplete = { true },
        isHomeApp = { home },
        kioskExited = { exited },
        returnedToFront = { exited = false },
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
}
