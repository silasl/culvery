package uk.co.siland.culvery.core.setup.steps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.CorePermissions
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.plugin.SetupStep
import uk.co.siland.culvery.core.setup.LocationPane
import uk.co.siland.culvery.core.setup.LocationSearch
import uk.co.siland.culvery.core.setup.PlaceMatch
import uk.co.siland.culvery.core.setup.StepTitle
import uk.co.siland.culvery.core.setup.WHERES_HOME
import uk.co.siland.culvery.core.setup.toHome

/** 4a design §4.3, D2: the home town, or Skip for now (the tablet's own time zone). */
@Singleton
class LocationStep @Inject constructor(
    private val household: HouseholdRepository,
    private val search: LocationSearch,
    private val access: AccessControl,
) : SetupStep {
    override val id = "location"
    override val order = 100
    override val skippable = true
    override val done: Flow<Boolean> = household.location.map { it != null }

    @Composable
    override fun Content(onNext: () -> Unit) {
        val current by household.location.collectAsState(initial = null)
        StepTitle(WHERES_HOME)
        LocationPane(search, current, showCurrent = false, save = ::saveHome)
    }

    /** Before the first Admin exists the wizard saves directly; once one does, it takes `settings.manage` (4a design §3.4). */
    internal suspend fun saveHome(place: PlaceMatch): Boolean {
        if (household.hasActiveAdmin.first() && access.authorise(CorePermissions.SETTINGS_MANAGE) == null) return false
        household.setLocation(place.toHome())
        return true
    }
}
