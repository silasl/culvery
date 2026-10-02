package uk.co.siland.culvery

import android.app.Application
import android.util.Log
import dagger.Lazy
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
    // Lazy and read on the application scope (Dispatchers.Default): the loops and the providers are built off Main (4c §4.1).
    @Inject lateinit var household: Lazy<HouseholdRepository>
    @Inject lateinit var calendarSetup: Lazy<CalendarSetup>
    @Inject lateinit var calendarProviders: Lazy<Set<@JvmSuppressWildcards CalendarProvider>>
    @Inject lateinit var startables: Lazy<Set<@JvmSuppressWildcards Startable>>
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            startAll(startables.get()) { startable, e -> Log.e(TAG, "${startable::class.simpleName} failed to start (${e::class.simpleName})") }
        }
        appScope.launch { seedDebugData(household.get(), calendarSetup.get(), calendarProviders.get()) }
        appScope.launch { removeSampleWhenReplaced(calendarSetup.get()) }
    }

    private companion object {
        const val TAG = "Culvery"
    }
}
