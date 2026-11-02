package uk.co.siland.culvery.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.multibindings.IntoSet
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.LocalSettingsStart
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.plugin.test.RecordingNavigator
import uk.co.siland.culvery.core.plugin.test.TestOverlay
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.display.ui.InSettingsPane
import uk.co.siland.culvery.settings.di.SettingsModule

/** 15b §3: App settings' four sections, its sub-pages and the way back. */
@RunWith(AndroidJUnit4::class)
class AppSettingsPageTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val folder = TemporaryFolder()
    private val rig by lazy { AppSettingsRig(folder.root) }
    private val navigator = RecordingNavigator()

    @After
    fun tearDown() = rig.close()

    private fun show(start: String? = null) {
        rig.household()
        compose.setContent {
            CompositionLocalProvider(LocalShellNavigator provides navigator, LocalOverlayHost provides TestOverlay(), LocalSettingsStart provides start) {
                CulveryTheme(dark = true) { Box { InSettingsPane { rig.page.Content() } } }
            }
        }
        compose.awaitAppSettings()
    }

    /** Fails if the page's id, name, place in the list or sub-pages change, or it isn't bound into Settings. */
    @Test
    fun itIsFirstInTheList() {
        val page = rig.page
        assertThat(listOf(page.id, page.title, page.order)).containsExactly("app", "App settings", 0).inOrder()
        assertThat(page.opens).containsExactly("location", "people", "kiosk", "display", "seasonal")
        val binding = SettingsModule::class.java.declaredMethods.filterNot { it.isSynthetic }.single()
        assertThat(binding.parameterTypes.single()).isEqualTo(AppSettingsPage::class.java)
        assertThat(binding.returnType).isEqualTo(SettingsPage::class.java)
        assertThat(binding.isAnnotationPresent(IntoSet::class.java)).isTrue()
    }

    @Test
    fun fourSectionsInOrder() {
        show()
        compose.onNode(isHeading() and hasText("App settings")).assertExists()
        compose.onNodeWithText("The home, the people who use the wall, the screen and the lock.").assertExists()
        val tops = listOf("Home location", "People", "Display", "Tablet lock").map {
            compose.onNodeWithContentDescription(it).getUnclippedBoundsInRoot().top.value
        }
        assertThat(tops).isInStrictOrder()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun homeLocationRowShowsThePlaceAndOpens() {
        show()
        compose.onNodeWithText("Brighton").assertExists()
        compose.onNodeWithText("Used for the clock’s time zone and the weather. Tap to change.").assertExists()
        compose.onNodeWithTag("app_home_location").performClick()
        compose.onNodeWithTag("app_back").assertTextEquals("App settings")
        compose.onNode(isHeading() and hasText("Home location")).assertExists()
        compose.onNodeWithTag("location_current").assertExists()
    }

    @Test
    fun theBackLinkReturns() {
        show()
        compose.onNodeWithTag("app_home_location").performClick()
        compose.onNodeWithTag("app_back").performClick()
        compose.onNodeWithTag("app_home_location").assertExists()
        compose.onNodeWithTag("app_back").assertDoesNotExist()
    }

    @Test
    fun peopleTwoToARow() {
        show()
        val alex = compose.onNodeWithTag("person_Alex").getUnclippedBoundsInRoot()
        val sam = compose.onNodeWithTag("person_Sam").getUnclippedBoundsInRoot()
        assertThat(sam.top).isEqualTo(alex.top)
        assertThat(sam.width.value).isWithin(1f).of(alex.width.value)
    }

    @Test
    fun addAPersonIsTheDashedRow() {
        show()
        compose.onNodeWithTag("people_add").performScrollTo().assertTextEquals("Add a person")
    }

    @Test
    fun displayRowsOpenTheirSubPages() {
        show()
        compose.onNodeWithTag("app_display_display").assertTextEquals("Brightness and night", "Night clock 22:00–07:00 · Sleep after 5 min")
        compose.onNodeWithTag("app_display_seasonal").assertTextEquals("Seasonal looks", "Halloween 24–31 Oct")
        compose.onNodeWithTag("app_display_display").performScrollTo().performClick()
        compose.onNodeWithTag("display_brightness").assertExists()
        compose.onNodeWithTag("app_back").performClick()
        compose.onNodeWithTag("app_display_seasonal").performScrollTo().performClick()
        compose.onNodeWithTag("display_seasonal").assertExists()
        compose.onNode(isHeading() and hasText("Seasonal looks")).assertExists()
    }

    @Test
    fun tabletLockSaysOnOrOff() {
        show()
        compose.onNodeWithTag("tablet_lock_status").performScrollTo()
        compose.onNodeWithText("Tablet lock is off").assertExists()
        compose.onNodeWithTag("choose_home_app").assertExists()
        rig.isHome.value = true
        compose.onNodeWithText("Tablet lock is on").assertExists()
        compose.onNodeWithTag("settings_change_home_app").assertExists()
        compose.onNodeWithTag("settings_exit_kiosk").assertExists()
    }

    /** 15b Ruling 15: the weather header's Home location link lands on the sub-page. */
    @Test
    fun aStartOpensItsSubPage() {
        show(start = "location")
        compose.onNodeWithTag("app_back").assertExists()
        compose.onNodeWithTag("location_current").assertExists()
    }

    @Test
    fun aStartOnASectionOpensTheMainPage() {
        show(start = "kiosk")
        compose.onNodeWithTag("app_back").assertDoesNotExist()
        compose.onNodeWithTag("tablet_lock_status").assertExists()
    }
}
