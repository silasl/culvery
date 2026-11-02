package uk.co.siland.culvery.settings

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import uk.co.siland.culvery.core.access.PinHasher
import uk.co.siland.culvery.core.access.PinManager
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.HomeApp
import uk.co.siland.culvery.core.plugin.test.RecordingToaster
import uk.co.siland.culvery.core.setup.LocationSearch
import uk.co.siland.culvery.core.setup.PeopleEditor
import uk.co.siland.culvery.core.setup.PlaceMatch
import uk.co.siland.culvery.core.setup.pages.LocationPage
import uk.co.siland.culvery.core.ui.PersonPalette
import uk.co.siland.culvery.core.ui.defaultSeasonDates
import uk.co.siland.culvery.core.ui.test.waitFor
import uk.co.siland.culvery.display.CameraAccess
import uk.co.siland.culvery.display.SeasonalThemes
import uk.co.siland.culvery.display.displaySettings
import uk.co.siland.culvery.display.ui.DisplaySections
import uk.co.siland.culvery.shell.FakeAccessControl
import uk.co.siland.culvery.shell.SunToday

/** The real App settings page over an in-memory household and a display settings file in [dir]. */
internal class AppSettingsRig(dir: File) {
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HouseholdDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    val household = HouseholdRepository(db)
    private val access = FakeAccessControl()
    private val pins = PinManager(household, PinHasher())
    val isHome = MutableStateFlow(false)
    private val homeApp = object : HomeApp {
        override val isDefault = isHome
    }
    private val display = displaySettings(dir, io)
    private val noTowns = object : LocationSearch {
        override suspend fun search(query: String) = emptyList<PlaceMatch>()
    }
    val page = AppSettingsPage(
        LocationPage(household, noTowns, access),
        PeopleEditor(household, pins, access, RecordingToaster()),
        homeApp,
        DisplaySections(display, CameraAccess({ true }, { false }, display, io), SunToday(null, io), RecordingToaster(), SeasonalThemes(MutableStateFlow(null), flowOf(defaultSeasonDates()), io)),
    )

    /** Brighton, and Alex, Sam and Mia. */
    fun household() = runBlocking {
        household.setLocation(HomeLocation("Brighton", 50.82838, -0.13947, "Europe/London"))
        pins.addPerson("Alex", PersonPalette.colors[0], Role.ADMIN, "1234")
        pins.addPerson("Sam", PersonPalette.colors[1], Role.ADULT, "2468")
        pins.addPerson("Mia", PersonPalette.colors[2], Role.CHILD, null)
    }

    fun close() {
        io.cancel()
        db.close()
    }
}

/** Waits for what App settings reads from Room and DataStore, or for a sub-page's own first read. */
internal fun ComposeContentTestRule.awaitAppSettings() = waitFor {
    listOf(hasTestTag("person_Mia"), hasText("Night clock", substring = true), hasText("Brighton")).all { onAllNodes(it).fetchSemanticsNodes().isNotEmpty() } ||
        onAllNodes(hasTestTag("app_back")).fetchSemanticsNodes().isNotEmpty()
}
