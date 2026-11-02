package uk.co.siland.culvery.capability.holiday

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.house.HouseModes
import uk.co.siland.culvery.core.house.ModeShelf
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.TrayWidth
import uk.co.siland.culvery.core.ui.Icons

@RunWith(AndroidJUnit4::class)
class HolidayCapabilityTest {
    private fun capability(scope: CoroutineScope, shelf: ModeShelf? = null): HolidayCapability {
        val house = HouseRig(scope).house
        return HolidayCapability(HolidayActions(TestAccess(Role.ADULT), house, null, emptySet(), { HouseRig.TODAY }), house, shelf)
    }

    /** A shelf that puts Holiday mode at [place], or off Home with null. */
    private fun shelf(place: Int?) = object : ModeShelf {
        override fun place(modeId: String): Flow<Int?> = flowOf(place.takeIf { modeId == HouseModes.HOLIDAY })
    }

    /** 11 §5.2: Holiday mode is a tray tile, not a tab. Fails if it has a tab or a Home card, or its id, label, icon or order differ. */
    @Test
    fun itHasNoTabAndNoCards() = runTest {
        val capability = capability(backgroundScope)

        assertThat(capability.hasTab.first()).isFalse()
        assertThat(capability.cards().first()).isEmpty()
        assertThat(listOf(capability.id, capability.label, capability.icon, capability.order))
            .containsExactly("holiday", "Holiday mode", "beach_access", 75).inOrder()
        assertThat(capability.icon).isEqualTo(Icons.BEACH_ACCESS)
    }

    /** Fails if the tile is missing, has another id or order, or waits for a seam to be set up, with no shelf bound. */
    @Test
    fun theTileIsRow1() = runTest {
        assertThat(capability(backgroundScope).trayButtons().first().map { it.id to it.order }).containsExactly("holiday.mode" to 10)
    }

    /** 15b decision 3: fails if Holiday mode off Home still has a tile. */
    @Test
    fun offHomeNoButton() = runTest {
        assertThat(capability(backgroundScope, shelf(null)).trayButtons().first()).isEmpty()
    }

    /** Ruling 12: fails if Holiday mode's tile isn't half-width at order 10 when the shelf puts it first. */
    @Test
    fun placedFirstWhenShown() = runTest {
        val buttons = capability(backgroundScope, shelf(0)).trayButtons().first()

        assertThat(buttons.map { Triple(it.id, it.order, it.width) }).containsExactly(Triple("holiday.mode", 10, TrayWidth.HALF))
    }
}
