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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
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

/** How long a section opened on is kept in view while the sections above it grow. */
private const val SETTLE_NANOS = 2_000_000_000L

/** A section Settings can open on, and where it was last laid out: in the page, and on screen. */
private class Section {
    private val requester = BringIntoViewRequester()
    private var inPage = Float.NaN
    private var onScreen = Float.NaN
    val modifier = Modifier.bringIntoViewRequester(requester).onGloballyPositioned {
        inPage = it.positionInParent().y
        onScreen = it.positionInRoot().y
    }

    /**
     * Brings the section into view, and again each time a section above it grows, until its place in the page holds
     * across a frame. It stops when the user scrolls, and after [SETTLE_NANOS].
     */
    suspend fun settle() {
        val start = withFrameNanos { it }
        var aimed = Float.NaN
        while (inPage != aimed) {
            aimed = inPage
            try {
                requester.bringIntoView()
            } catch (e: CancellationException) {
                // A user scroll cancels the request.
                currentCoroutineContext().ensureActive()
                return
            }
            withFrameNanos {}
            val now = withFrameNanos { it }
            val shown = onScreen
            withFrameNanos {}
            if (inPage == aimed && onScreen != shown) return
            if (now - start > SETTLE_NANOS) return
        }
    }
}

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
        val sections = remember { SECTIONS.associateWith { Section() } }
        HhPageTitle(APP_SETTINGS, APP_SETTINGS_LINE)
        location.HomeLocationRow { open = SettingsPageIds.LOCATION }
        Box(sections.getValue(SettingsPageIds.PEOPLE).modifier) { PeopleSection(people) }
        display.Rows { open = it }
        Box(sections.getValue(SettingsPageIds.TABLET_LOCK).modifier) { TabletLockSection(homeApp) }
        LaunchedEffect(Unit) {
            scrollTo?.let { target ->
                // People and Display grow as their data arrives; scroll once it is read, and again while it lays out.
                people.members.first()
                display.awaitRead()
                sections.getValue(target).settle()
            }
            scrollTo = null
        }
    }
}
