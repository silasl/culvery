package uk.co.siland.culvery

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uk.co.siland.culvery.core.access.PinManager
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.plugin.ApplicationScope

@HiltAndroidApp
class CulveryApp : Application() {
    @Inject lateinit var household: HouseholdRepository
    @Inject lateinit var pins: PinManager
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        appScope.launch { seedDebugData(household, pins) }
    }
}
