package uk.co.siland.culvery.capability.party

import androidx.compose.runtime.Composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import javax.inject.Provider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.house.HouseModes
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.HomeCard
import uk.co.siland.culvery.core.plugin.TrayButton
import uk.co.siland.culvery.core.plugin.TrayWidth

/** 15b decision 3 and Ruling 12: where the modes sit in Home's tray. */
@RunWith(AndroidJUnit4::class)
class ModesShelfTest {
    /** A capability with tray buttons of its own and nothing else. */
    private class Buttons(override val id: String, private val buttons: List<TrayButton>) : Capability {
        override val label = id
        override val icon = id
        override val order = 0
        override val hasTab: Flow<Boolean> = flowOf(false)

        override fun cards(): Flow<List<HomeCard>> = flowOf(emptyList())

        @Composable
        override fun TabContent() = Unit

        override fun trayButtons(): Flow<List<TrayButton>> = flowOf(buttons)
    }

    private fun mode(id: String, shown: Boolean = true) = StoredMode(id, id, PARTY_ICON, showOnHome = shown, needsPin = true)

    private suspend fun shelf(scope: CoroutineScope, prefs: ModesPrefs, vararg capabilities: Capability): ModesShelf {
        val store = ModesStore(MemoryStore(), scope)
        store.update { prefs }
        return ModesShelf(store, Provider { capabilities.toSet() })
    }

    private suspend fun ModesShelf.places(vararg ids: String) = ids.map { place(it).first() }

    @Test
    fun holidayFirstThenStoredInOrder() = runTest {
        val shelf = shelf(backgroundScope, ModesPrefs(modes = listOf(mode("party"), mode("modeb"))))

        assertThat(shelf.shown.first()).containsExactly(HouseModes.HOLIDAY, "party", "modeb").inOrder()
        assertThat(shelf.places(HouseModes.HOLIDAY, "party", "modeb")).containsExactly(0, 1, 2).inOrder()
    }

    @Test
    fun hiddenModesTakeNoPlace() = runTest {
        val shelf = shelf(backgroundScope, ModesPrefs(holidayOnHome = false, modes = listOf(mode("party", shown = false), mode("modeb"))))

        assertThat(shelf.places(HouseModes.HOLIDAY, "party", "modeb")).containsExactly(null, null, 0).inOrder()
    }

    @Test
    fun fourAtMost() = runTest {
        val shelf = shelf(backgroundScope, ModesPrefs(modes = listOf(mode("party"), mode("modeb"), mode("modec"), mode("moded"))))

        assertThat(shelf.room.first()).isEqualTo(4)
        assertThat(shelf.places(HouseModes.HOLIDAY, "party", "modeb", "modec", "moded")).containsExactly(0, 1, 2, 3, null).inOrder()
    }

    /** Review Focus 3: fails if a half-width button that isn't a mode doesn't take a mode's place. */
    @Test
    fun anotherHalfButtonLeavesThree() = runTest {
        val board = Buttons("board", listOf(TrayButton("board.start", 90, TrayWidth.HALF) {}, TrayButton("board.full", 91) {}))
        val modes = ModesPrefs(holidayOnHome = false, modes = listOf(mode("party"), mode("modeb"), mode("modec"), mode("moded")))
        val shelf = shelf(backgroundScope, modes, board)

        // The room is four until the board's buttons are read.
        assertThat(shelf.room.first { it != 4 }).isEqualTo(3)
        assertThat(shelf.shown.first { it.size != 4 }).containsExactly("party", "modeb", "modec").inOrder()
    }

    /** Fails if the room is announced as four while a half-width button is still on its way, which flashes a fourth mode on Home. */
    @Test
    fun theRoomNeverSaysFourBeforeTheButtonsAreKnown() = runTest {
        val late = object : Capability by Buttons("late", emptyList()) {
            override fun trayButtons(): Flow<List<TrayButton>> = flow {
                delay(50)
                emit(listOf(TrayButton("late.start", 90, TrayWidth.HALF) {}))
            }
        }
        val shelf = shelf(backgroundScope, ModesPrefs(holidayOnHome = false, modes = listOf(mode("party"))), late)
        val rooms = mutableListOf<Int>()
        val shown = mutableListOf<List<String>>()
        backgroundScope.launch { shelf.room.toList(rooms) }
        backgroundScope.launch { shelf.shown.toList(shown) }

        advanceTimeBy(10)
        assertThat(rooms).isEmpty()
        assertThat(shown).isEmpty()
        advanceTimeBy(100)
        assertThat(rooms).containsExactly(3)
        assertThat(shown.flatten()).containsExactly("party")
    }

    /** Review Focus 3: fails if Holiday mode back on Home doesn't take the first place and push the last mode off. */
    @Test
    fun holidayBackOnHomePushesTheLastOff() = runTest {
        val store = ModesStore(MemoryStore(), backgroundScope)
        store.update { ModesPrefs(holidayOnHome = false, modes = listOf(mode("party"), mode("modeb"), mode("modec"), mode("moded"))) }
        val shelf = ModesShelf(store, Provider { emptySet() })
        assertThat(shelf.places("party", "moded")).containsExactly(0, 3).inOrder()

        store.update { it.copy(holidayOnHome = true) }

        assertThat(shelf.places(HouseModes.HOLIDAY, "party", "moded")).containsExactly(0, 1, null).inOrder()
    }

    /** Fails if the modes' own buttons count against the room, which would also read the shelf from inside itself. */
    @Test
    fun theModesOwnButtonsDontCount() = runTest {
        val holiday = Buttons(HouseModes.HOLIDAY, listOf(TrayButton("holiday.mode", 10, TrayWidth.HALF) {}))
        val stored = Buttons("party", listOf(TrayButton("mode.party", 11, TrayWidth.HALF) {}))
        val shelf = shelf(backgroundScope, ModesPrefs(modes = listOf(mode("party"))), holiday, stored)

        assertThat(shelf.room.first()).isEqualTo(4)
    }
}
