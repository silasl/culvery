package uk.co.siland.culvery

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/** What the kiosk does to the activity's window and task. */
internal interface KioskWindow {
    fun pin()

    fun unpin()

    fun hideBars()

    fun showBars()

    fun moveToBack()
}

/**
 * The kiosk over the activity's lifecycle (4a design D10, 4c §5.1, K2): every resume hides the bars and pins, once setup
 * is complete and the kiosk wasn't exited; coming back to the front after being stopped clears "exited".
 */
internal class KioskLifecycle(
    private val window: KioskWindow,
    private val setupComplete: () -> Boolean,
    private val isHomeApp: () -> Boolean,
    private val kioskExited: () -> Boolean,
    private val returnedToFront: () -> Unit,
) : DefaultLifecycleObserver {
    // A configuration change makes a new one, unstopped, so it doesn't count as coming back.
    private var stopped = false

    override fun onStop(owner: LifecycleOwner) {
        stopped = true
    }

    override fun onStart(owner: LifecycleOwner) {
        if (!stopped) return
        stopped = false
        returnedToFront()
    }

    override fun onResume(owner: LifecycleOwner) {
        if (!kioskExited()) window.hideBars()
        if (shouldPin(setupComplete(), kioskExited())) window.pin()
    }

    /**
     * Exit kiosk (4c §5.1, ruling 28): unpinned, with the bars showing. As the home app Culvery stays in front: moving to
     * the back would resume it as home and pin it again at once. Otherwise it moves to the back, as before.
     */
    fun exitKiosk() {
        window.unpin()
        window.showBars()
        if (!isHomeApp()) window.moveToBack()
    }
}
