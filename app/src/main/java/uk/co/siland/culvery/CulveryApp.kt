package uk.co.siland.culvery

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uk.co.siland.culvery.capability.calendar.CalendarProvider
import uk.co.siland.culvery.capability.calendar.CalendarSetup
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.Startable

@HiltAndroidApp
class CulveryApp : Application() {
    @Inject lateinit var household: HouseholdRepository
    @Inject lateinit var calendarSetup: CalendarSetup
    @Inject lateinit var calendarProviders: Set<@JvmSuppressWildcards CalendarProvider>
    @Inject lateinit var startables: Set<@JvmSuppressWildcards Startable>
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        startAll(startables) { startable, e -> Log.e(TAG, "${startable.javaClass.name} failed to start", e) }
        appScope.launch { seedDebugData(household, calendarSetup, calendarProviders) }
        appScope.launch { removeSampleWhenReplaced(calendarSetup) }
    }

    private companion object {
        const val TAG = "Culvery"
    }
}
