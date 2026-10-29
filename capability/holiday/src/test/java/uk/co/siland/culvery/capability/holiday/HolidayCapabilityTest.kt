package uk.co.siland.culvery.capability.holiday

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.ui.Icons

@RunWith(AndroidJUnit4::class)
class HolidayCapabilityTest {
    private fun capability(scope: CoroutineScope): HolidayCapability {
        val house = HouseRig(scope).house
        return HolidayCapability(HolidayActions(TestAccess(Role.ADULT), house, null, emptySet(), { HouseRig.TODAY }), house)
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

    /** Fails if the tile is missing, has another id or order, or waits for a seam to be set up. */
    @Test
    fun theTileIsRow1() = runTest {
        assertThat(capability(backgroundScope).trayButtons().first().map { it.id to it.order }).containsExactly("holiday.mode" to 10)
    }
}
