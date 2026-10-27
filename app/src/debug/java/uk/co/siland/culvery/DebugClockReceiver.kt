package uk.co.siland.culvery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import uk.co.siland.culvery.di.DebugClock

/**
 * Debug builds only: moves the wall's clock ahead by some minutes, 0 putting it back, so the walkthrough can reach a
 * Holiday mode step's time (11 §8.1):
 * `adb shell am broadcast -n uk.co.siland.culvery/.DebugClockReceiver -a uk.co.siland.culvery.debug.CLOCK --el minutes 90`
 */
class DebugClockReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun clock(): DebugClock
    }

    override fun onReceive(context: Context, intent: Intent) {
        // A mistyped command must not move the clock.
        if (intent.action != ACTION || !intent.hasExtra(EXTRA_MINUTES)) return
        EntryPointAccessors.fromApplication(context.applicationContext, Dependencies::class.java).clock().minutes =
            intent.getLongExtra(EXTRA_MINUTES, 0L)
    }

    companion object {
        const val ACTION = "uk.co.siland.culvery.debug.CLOCK"
        const val EXTRA_MINUTES = "minutes"
    }
}
