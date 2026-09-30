package uk.co.siland.culvery.core.setup.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import javax.inject.Inject
import javax.inject.Singleton
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.CorePermissions
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.setup.HOME_LOCATION
import uk.co.siland.culvery.core.setup.LocationPane
import uk.co.siland.culvery.core.setup.LocationSearch
import uk.co.siland.culvery.core.setup.PlaceMatch
import uk.co.siland.culvery.core.setup.StepTitle
import uk.co.siland.culvery.core.setup.toHome

/** Settings › Home location (4a design §4.3): the saved home above the search. */
@Singleton
class LocationPage @Inject constructor(
    private val household: HouseholdRepository,
    private val search: LocationSearch,
    private val access: AccessControl,
) : SettingsPage {
    override val id = "location"
    override val title = HOME_LOCATION
    override val order = 0

    @Composable
    override fun Content() {
        val current by household.location.collectAsState(initial = null)
        StepTitle(HOME_LOCATION)
        LocationPane(search, current, showCurrent = true, save = ::saveHome)
    }

    internal suspend fun saveHome(place: PlaceMatch): Boolean {
        access.authorise(CorePermissions.SETTINGS_MANAGE) ?: return false
        household.setLocation(place.toHome())
        return true
    }
}
