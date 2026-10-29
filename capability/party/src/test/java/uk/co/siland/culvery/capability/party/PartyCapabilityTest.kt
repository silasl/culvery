package uk.co.siland.culvery.capability.party

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.util.Optional
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.ui.Icons

@RunWith(AndroidJUnit4::class)
class PartyCapabilityTest {
    private fun capability(scope: CoroutineScope): PartyCapability {
        val rig = HouseRig(scope)
        return PartyCapability(
            PartyActions(TestAccess(Role.ADULT), rig.house, emptySet()), rig.house, rig.settings,
            Optional.of(rig.music), Optional.of(rig.lights), Optional.of(rig.plugs),
        )
    }

    /** 13 §2.7: Party mode is a tray tile beside Holiday's, not a tab. Fails if it has a tab, a card, or another id, label, icon, button or order. */
    @Test
    fun theTileSitsBesideHolidays() = runTest {
        val capability = capability(backgroundScope)

        assertThat(capability.hasTab.first()).isFalse()
        assertThat(capability.cards().first()).isEmpty()
        assertThat(listOf(capability.id, capability.label, capability.icon, capability.order))
            .containsExactly("party", "Party mode", "celebration", 76).inOrder()
        assertThat(capability.icon).isEqualTo(Icons.CELEBRATION)
        assertThat(capability.trayButtons().first().map { it.id to it.order }).containsExactly("party.mode" to 20)
    }
}
