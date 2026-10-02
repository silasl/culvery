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

    /** Device owner only: whether Play services may run in lock-task (4c §5.2). */
    fun allowPlayServices(allowed: Boolean)
}

/** Told when the home-app choice was declined or never shown (4c §5.1): what is still undone and how to do it. */
internal const val HOME_APP_NOT_SET =
    "Culvery isn't the home app yet — exit kiosk, then set it in Android's Settings › Apps › Default apps."

/**
 * Whether Culvery has left the front since the kiosk was exited. It outlives the activity (the view model holds it), so
 * a relaunch after the activity was stopped still counts as coming back.
 */
internal class FrontTracker {
    var left = false
}

/**
 * The kiosk over the activity's lifecycle (4a design D10, 4c §5.1, K2): every resume hides the bars and pins, once
 * setup is complete and the kiosk wasn't exited; coming back to the front after being stopped clears "exited".
 */
internal class KioskLifecycle(
    private val window: KioskWindow,
    private val setupComplete: () -> Boolean,
    private val isHomeApp: () -> Boolean,
    private val isDeviceOwner: () -> Boolean,
    private val kioskExited: () -> Boolean,
    private val returnedToFront: () -> Unit,
    private val front: FrontTracker,
    private val changingConfigurations: () -> Boolean,
    private val say: (String) -> Unit,
) : DefaultLifecycleObserver {
    private var resumed = false

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
        resumed = true
        // Culvery is back in front, however Google's screens ended: Play services is no longer allowed in lock-task.
        if (isDeviceOwner()) window.allowPlayServices(false)
        if (!kioskExited()) window.hideBars()
        if (shouldPin(setupComplete(), kioskExited())) window.pin()
    }

    override fun onPause(owner: LifecycleOwner) {
        resumed = false
    }

    /**
     * Google's account chooser can't open over a pinned app (4c §5.3, D5): unpin, or as device owner stay in lock-task
     * with Play services allowed.
     */
    fun leaveForGoogle() {
        if (isDeviceOwner()) window.allowPlayServices(true) else window.unpin()
    }

    /**
     * Google's screens ended or never opened. A resume restores the kiosk itself; this is for when Culvery never
     * paused, so nothing else would: lock-task goes back to Culvery alone, or an unpinned Culvery in front pins again.
     */
    fun returnToPinning() {
        if (isDeviceOwner()) {
            window.allowPlayServices(false)
        } else if (resumed && shouldPin(setupComplete(), kioskExited())) {
            window.pin()
        }
    }

    /**
     * Exit kiosk (4c §5.1, ruling 28): unpinned, with the bars showing. As the home app Culvery stays in front: moving
     * to the back would resume it as home and pin it again at once. Otherwise it moves to the back, as before.
     */
    fun exitKiosk() {
        window.unpin()
        window.showBars()
        if (!isHomeApp()) window.moveToBack()
    }

    /**
     * Android's screens can't open over a pinned app, so unpin, then [launch] one. When there is none, pin again at
     * once: nothing pauses Culvery, so nothing else would.
     */
    fun openHomeAppScreen(launch: () -> Unit) {
        window.unpin()
        try {
            launch()
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No screen to change the home app on this tablet (${e::class.simpleName})")
            returnedToFront()
            if (shouldPin(setupComplete(), kioskExited())) window.pin()
            say(HOME_APP_NOT_SET)
        }
    }

    /** Android's role dialog closed: cancelled with Culvery still not home means it was refused or never shown. */
    fun roleAnswered(cancelled: Boolean) {
        if (cancelled && !isHomeApp()) say(HOME_APP_NOT_SET)
    }

    private companion object {
        const val TAG = "Culvery"
    }
}
