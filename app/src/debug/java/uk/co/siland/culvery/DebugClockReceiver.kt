package uk.co.siland.culvery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.di.DebugClock
import uk.co.siland.culvery.di.parseClockTime

/**
 * Debug builds only: moves the wall's clock ahead by some minutes, 0 putting it back, so the walkthrough can reach a
 * Holiday mode step's time (11 §8.1):
 * `adb shell am broadcast -n uk.co.siland.culvery/.DebugClockReceiver -a uk.co.siland.culvery.debug.CLOCK --el minutes 90`
 * `--es at 17:55` moves it to the next such time in the household's zone, and `--el add 30` moves it on by that many minutes.
 */
class DebugClockReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun clock(): DebugClock
        fun zone(): HouseholdZone
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, Dependencies::class.java)
        val clock = deps.clock()
        val at = intent.getStringExtra(EXTRA_AT)
        when {
            at != null -> {
                // A mistyped time must not move the clock.
                val time = parseClockTime(at) ?: return
                val pending = goAsync()
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        clock.moveTo(time, deps.zone().current())
                    } finally {
                        pending.finish()
                    }
                }
            }
            intent.hasExtra(EXTRA_ADD) -> clock.add(intent.getLongExtra(EXTRA_ADD, 0L))
            intent.hasExtra(EXTRA_MINUTES) -> clock.minutes = intent.getLongExtra(EXTRA_MINUTES, 0L)
        }
    }

    companion object {
        const val ACTION = "uk.co.siland.culvery.debug.CLOCK"
        const val EXTRA_MINUTES = "minutes"
        const val EXTRA_ADD = "add"
        const val EXTRA_AT = "at"
    }
}
