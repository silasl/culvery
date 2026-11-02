package uk.co.siland.culvery.capability.party

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.util.Optional
import javax.inject.Provider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.test.RecordingToaster
import uk.co.siland.culvery.core.plugin.TrayWidth
import uk.co.siland.culvery.core.ui.Icons

@RunWith(AndroidJUnit4::class)
class ModesCapabilityTest {
    private fun TestScope.capability(rig: HouseRig = HouseRig(backgroundScope)) = ModesCapability(
        StoredModeActions(TestAccess(Role.ADULT), rig.house, rig.settings, emptySet()), rig.house, rig.settings,
        ModesShelf(rig.settings, Provider { emptySet() }, backgroundScope),
        Optional.of(rig.music), Optional.of(rig.lights), Optional.of(rig.plugs), emptySet(), emptySet(), RecordingToaster(),
    )

    /** 13 §2.7: the modes sit in the tray, not on a tab. Fails if there is a tab, a card, or another id, label, icon or order. */
    @Test
    fun noTabAndNoCard() = runTest {
        val capability = capability()

        assertThat(capability.hasTab.first()).isFalse()
        assertThat(capability.cards().first()).isEmpty()
        assertThat(listOf(capability.id, capability.label, capability.icon, capability.order))
            .containsExactly("party", "Party mode", "celebration", 76).inOrder()
        assertThat(capability.icon).isEqualTo(Icons.CELEBRATION)
    }

    /** 15b decision 3: fails if a shown mode has no half-width button after Holiday's place, or a hidden one has one. */
    @Test
    fun eachShownModeIsAHalfButtonByPlace() = runTest {
        val rig = HouseRig(backgroundScope)
        rig.choose(PARTY_MODE, FILM_NIGHT, FILM_NIGHT.copy(id = "modeb", showOnHome = false))

        val buttons = capability(rig).trayButtons().first()

        assertThat(buttons.map { Triple(it.id, it.order, it.width) })
            .containsExactly(Triple("mode.party", 11, TrayWidth.HALF), Triple("mode.$FILM_ID", 12, TrayWidth.HALF)).inOrder()
    }

    /** 15b §4.1: Settings › Modes alone sits between Lights (470) and Door (500); Party mode's old page is gone. */
    @Test
    fun theSettingsPageSitsAfterLights() = runTest {
        val pages = capability().settingsPages()

        assertThat(pages.map { Triple(it.id, it.title, it.order) }).containsExactly(Triple("modes", "Modes", 475))
    }
}
