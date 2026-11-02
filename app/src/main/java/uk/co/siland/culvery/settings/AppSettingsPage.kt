package uk.co.siland.culvery.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import javax.inject.Inject
import javax.inject.Singleton
import uk.co.siland.culvery.core.plugin.HomeApp
import uk.co.siland.culvery.core.plugin.LocalSettingsStart
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.plugin.SettingsPageIds
import uk.co.siland.culvery.core.setup.PeopleEditor
import uk.co.siland.culvery.core.setup.pages.LocationPage
import uk.co.siland.culvery.core.setup.pages.PeopleSection
import uk.co.siland.culvery.core.setup.pages.TabletLockSection
import uk.co.siland.culvery.core.ui.HhBackLink
import uk.co.siland.culvery.core.ui.HhPageTitle
import uk.co.siland.culvery.display.ui.DISPLAY_PAGE_ID
import uk.co.siland.culvery.display.ui.DisplaySections
import uk.co.siland.culvery.display.ui.SEASONAL_PAGE_ID

internal const val APP_SETTINGS = "App settings"
internal const val APP_SETTINGS_LINE = "The home, the people who use the wall, the screen and the lock."
internal const val APP_SETTINGS_PAGE_ID = "app"

/** App settings' sub-pages, each opened from its row and left by "‹ App settings" (15b §3). */
private val SUB_PAGES = setOf(SettingsPageIds.LOCATION, DISPLAY_PAGE_ID, SEASONAL_PAGE_ID)

/** Sections the main page scrolls to when Settings opens on them. */
private val SECTIONS = setOf(SettingsPageIds.PEOPLE, SettingsPageIds.TABLET_LOCK)

/** Settings › App settings (15b §3): Home location, People, Display and Tablet lock on one page, first in the list. */
@Singleton
class AppSettingsPage @Inject constructor(
    private val location: LocationPage,
    private val people: PeopleEditor,
    private val homeApp: HomeApp,
    private val display: DisplaySections,
) : SettingsPage {
    override val id = APP_SETTINGS_PAGE_ID
    override val title = APP_SETTINGS
    override val order = 0
    override val opens = SUB_PAGES + SECTIONS

    /** Opens on [LocalSettingsStart]'s sub-page, or scrolled to its section, once. */
    @Composable
    override fun Content() {
        val start = LocalSettingsStart.current
        var open by rememberSaveable { mutableStateOf(start?.takeIf { it in SUB_PAGES }) }
        var scrollTo by rememberSaveable { mutableStateOf(start?.takeIf { it in SECTIONS }) }
        val sub = open
        if (sub != null) {
            val top = remember { BringIntoViewRequester() }
            Box(Modifier.bringIntoViewRequester(top)) { HhBackLink(APP_SETTINGS, "app_back") { open = null } }
            when (sub) {
                SettingsPageIds.LOCATION -> location.Content()
                DISPLAY_PAGE_ID -> display.BrightnessAndNight()
                SEASONAL_PAGE_ID -> display.SeasonalLooks()
            }
            LaunchedEffect(sub) { top.bringIntoView() }
            return
        }
        val sections = remember { SECTIONS.associateWith { BringIntoViewRequester() } }
        HhPageTitle(APP_SETTINGS, APP_SETTINGS_LINE)
        location.HomeLocationRow { open = SettingsPageIds.LOCATION }
        Box(Modifier.bringIntoViewRequester(sections.getValue(SettingsPageIds.PEOPLE))) { PeopleSection(people) }
        display.Rows { open = it }
        Box(Modifier.bringIntoViewRequester(sections.getValue(SettingsPageIds.TABLET_LOCK))) { TabletLockSection(homeApp) }
        LaunchedEffect(Unit) {
            scrollTo?.let { sections.getValue(it).bringIntoView() }
            scrollTo = null
        }
    }
}
