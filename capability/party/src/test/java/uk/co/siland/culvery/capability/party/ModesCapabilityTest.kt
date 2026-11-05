package uk.co.siland.culvery.capability.party

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.util.Optional
import javax.inject.Provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.test.RecordingToaster
import uk.co.siland.culvery.core.plugin.TrayWidth
import uk.co.siland.culvery.core.sound.SoundLibrary
import uk.co.siland.culvery.core.ui.Icons

@RunWith(AndroidJUnit4::class)
class ModesCapabilityTest {
    private val player = FakePlayer()

    private fun TestScope.capability(rig: HouseRig = HouseRig(backgroundScope), padRoom: Int = 3) = ModesCapability(
        StoredModeActions(TestAccess(Role.ADULT), rig.house, rig.settings, emptySet()), rig.house, rig.settings,
        ModesShelf(rig.settings, Provider { emptySet() }, backgroundScope),
        SoundPads(flowOf(listOf(TROMBONE.id, null, HONK.id)), flowOf(padRoom), flowOf(LIBRARY), flowOf(false)),
        PadTaps(player::play, player::stop, player.playing, GateRig().gate, { 1f }, backgroundScope, Dispatchers.Unconfined),
        SoundPadsStore(MemoryStore()), SoundLibrary({ null }, backgroundScope, Dispatchers.Unconfined),
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

    /** 15b decision 3, 18 §4.1: fails if a shown mode has no half-width button after Holiday's, or a hidden one or one past the two spots has one. */
    @Test
    fun eachShownModeIsAHalfButtonByPlace() = runTest {
        val rig = HouseRig(backgroundScope)
        rig.choose(FILM_NIGHT.copy(id = "modeb", showOnHome = false), PARTY_MODE, FILM_NIGHT)

        val buttons = capability(rig).trayButtons().first()

        assertThat(buttons.filter { it.width == TrayWidth.HALF }.map { Triple(it.id, it.order, it.width) })
            .containsExactly(Triple("mode.party", 11, TrayWidth.HALF))
    }

    /** 18 §4.1, Ruling 3: fails if a shown pad isn't a third-width button at 80 plus its number, or one with no room shows. */
    @Test
    fun eachShownPadIsAThirdButtonByNumber() = runTest {
        suspend fun thirds(room: Int) = capability(padRoom = room).trayButtons().first().filter { it.width == TrayWidth.THIRD }.map { it.id to it.order }

        assertThat(thirds(3)).containsExactly("sound.pad1" to 81, "sound.pad3" to 83).inOrder()
        assertThat(thirds(2)).containsExactly("sound.pad1" to 81)
    }

    /** 15b §4.1: Settings › Modes alone sits between Lights (470) and Door (500); Party mode's old page is gone. */
    @Test
    fun theSettingsPageSitsAfterLights() = runTest {
        val pages = capability().settingsPages()

        assertThat(pages.map { Triple(it.id, it.title, it.order) }).containsExactly(Triple("modes", "Modes", 475))
    }
}
