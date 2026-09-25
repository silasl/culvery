package uk.co.siland.culvery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import uk.co.siland.culvery.capability.calendar.CalendarSetup
import uk.co.siland.culvery.provider.calendar_fake.FakeCalendarProvider

/**
 * Debug builds only: takes the sample calendar offline, or brings it back, so the walkthrough can add, change and
 * delete an event the calendar can't be reached for (2b-2 design §7):
 * `adb shell am broadcast -n uk.co.siland.culvery/.DebugOfflineReceiver --ez offline true`
 */
class DebugOfflineReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun fake(): FakeCalendarProvider

        fun calendarSetup(): CalendarSetup
    }

    override fun onReceive(context: Context, intent: Intent) {
        // A mistyped command must not take the calendar offline by default.
        if (!intent.hasExtra(EXTRA_OFFLINE)) return
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, Dependencies::class.java)
        deps.fake().setOffline(intent.getBooleanExtra(EXTRA_OFFLINE, false))
        // A pass now shows the new state; queued changes still wait out their backoff.
        deps.calendarSetup().syncSoon()
    }

    companion object {
        const val EXTRA_OFFLINE = "offline"
    }
}
