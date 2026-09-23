package uk.co.siland.househub

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uk.co.siland.househub.core.access.PinManager
import uk.co.siland.househub.core.household.HouseholdRepository
import uk.co.siland.househub.core.plugin.ApplicationScope

@HiltAndroidApp
class HouseHubApp : Application() {
    @Inject lateinit var household: HouseholdRepository
    @Inject lateinit var pins: PinManager
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        appScope.launch { seedDebugData(household, pins) }
    }
}
