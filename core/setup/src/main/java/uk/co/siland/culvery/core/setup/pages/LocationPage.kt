package uk.co.siland.culvery.core.setup.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import javax.inject.Inject
import javax.inject.Singleton
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.CorePermissions
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.setup.CHANGE_HOME_LOCATION
import uk.co.siland.culvery.core.setup.HOME_LOCATION
import uk.co.siland.culvery.core.setup.HOME_LOCATION_LINE
import uk.co.siland.culvery.core.setup.LocationPane
import uk.co.siland.culvery.core.setup.LocationSearch
import uk.co.siland.culvery.core.setup.NO_TOWN_YET
import uk.co.siland.culvery.core.setup.PlaceMatch
import uk.co.siland.culvery.core.setup.toHome
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhListRow
import uk.co.siland.culvery.core.ui.HhPageTitle
import uk.co.siland.culvery.core.ui.HhSectionLabel
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.ListRowTokens
import uk.co.siland.culvery.core.ui.SettingsTokens

/** App settings › Home location (4a design §4.3, 15b §3): its row on App settings, and the sub-page with the saved home above the search. */
@Singleton
class LocationPage @Inject constructor(
    private val household: HouseholdRepository,
    private val search: LocationSearch,
    private val access: AccessControl,
) {
    /** The HOME LOCATION section: the saved place, opening the sub-page. */
    @Composable
    fun HomeLocationRow(onOpen: () -> Unit) {
        val current by household.location.collectAsState(initial = null)
        Column(verticalArrangement = Arrangement.spacedBy(SettingsTokens.rowGap)) {
            HhSectionLabel(HOME_LOCATION)
            HhListRow(
                current?.name ?: NO_TOWN_YET,
                Modifier.testTag("app_home_location"),
                subtitle = HOME_LOCATION_LINE,
                trailing = { HhIcon(Icons.CHEVRON_RIGHT, size = ListRowTokens.icon, tint = Culvery.colors.mute) },
                onClick = onOpen,
                clickLabel = CHANGE_HOME_LOCATION,
            )
        }
    }

    @Composable
    fun Content() {
        val current by household.location.collectAsState(initial = null)
        HhPageTitle(HOME_LOCATION)
        LocationPane(search, current, showCurrent = true, save = ::saveHome)
    }

    internal suspend fun saveHome(place: PlaceMatch): Boolean {
        access.authorise(CorePermissions.SETTINGS_MANAGE) ?: return false
        household.setLocation(place.toHome())
        return true
    }
}
