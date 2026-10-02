package uk.co.siland.culvery

import android.content.ActivityNotFoundException
import android.util.Log
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

/** Told when Android offers no home-app screen (4c §5.1): what went wrong and how to fix it. */
internal const val HOME_APP_NOT_OFFERED =
    "Android didn't offer the home-app choice — set Culvery as the home app in Android's Settings › Apps › Default apps."

/**
 * Whether Culvery has left the front since the kiosk was exited. It outlives the activity (the view model holds it), so
 * a relaunch after the activity was stopped still counts as coming back.
 */
internal class FrontTracker {
    var left = false
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
    private val front: FrontTracker,
    private val changingConfigurations: () -> Boolean,
    private val say: (String) -> Unit,
) : DefaultLifecycleObserver {
    // A stop for a configuration change is the same activity coming straight back, so it doesn't count as leaving.
    override fun onStop(owner: LifecycleOwner) {
        if (!changingConfigurations()) front.left = true
    }

    override fun onStart(owner: LifecycleOwner) {
        if (!front.left) return
        front.left = false
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

    /**
     * Android's screens can't open over a pinned app, so unpin, then [launch] one. When there is none, pin again at once:
     * nothing pauses Culvery, so nothing else would.
     */
    fun openHomeAppScreen(launch: () -> Unit) {
        window.unpin()
        try {
            launch()
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No screen to change the home app on this tablet (${e::class.simpleName})")
            returnedToFront()
            if (shouldPin(setupComplete(), kioskExited())) window.pin()
            say(HOME_APP_NOT_OFFERED)
        }
    }

    /** Android's role dialog closed: cancelled with Culvery still not home means it was refused or never shown. */
    fun roleAnswered(cancelled: Boolean) {
        if (cancelled && !isHomeApp()) say(HOME_APP_NOT_OFFERED)
    }

    private companion object {
        const val TAG = "Culvery"
    }
}
