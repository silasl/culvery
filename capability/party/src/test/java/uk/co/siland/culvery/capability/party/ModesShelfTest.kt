package uk.co.siland.culvery.capability.party

import androidx.compose.runtime.Composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import javax.inject.Provider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
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
        return ModesShelf(store, Provider { capabilities.toSet() }, scope)
    }

    private suspend fun ModesShelf.places(vararg ids: String) = ids.map { place(it).first() }

    @Test
    fun holidayFirstThenStoredInOrder() = runTest {
        val shelf = shelf(backgroundScope, ModesPrefs(modes = listOf(mode("party"))))

        assertThat(shelf.shown.first()).containsExactly(HouseModes.HOLIDAY, "party").inOrder()
        assertThat(shelf.places(HouseModes.HOLIDAY, "party")).containsExactly(0, 1).inOrder()
    }

    @Test
    fun hiddenModesTakeNoPlace() = runTest {
        val shelf = shelf(backgroundScope, ModesPrefs(holidayOnHome = false, modes = listOf(mode("party", shown = false), mode("modeb"))))

        assertThat(shelf.places(HouseModes.HOLIDAY, "party", "modeb")).containsExactly(null, null, 0).inOrder()
    }

    /** Spec 18 §4.1: fails if the modes keep a spot in the tray's last row, which is the sounds'. */
    @Test
    fun modesHaveTwoSpots() = runTest {
        val shelf = shelf(backgroundScope, ModesPrefs(modes = listOf(mode("party"), mode("modeb"), mode("modec"))))

        assertThat(shelf.room.first()).isEqualTo(2)
        assertThat(shelf.places(HouseModes.HOLIDAY, "party", "modeb", "modec")).containsExactly(0, 1, null, null).inOrder()
    }

    /** Review Focus 3: fails if a half-width button that isn't a mode doesn't take a mode's place. */
    @Test
    fun anotherHalfButtonLeavesOne() = runTest {
        val board = Buttons("board", listOf(TrayButton("board.start", 90, TrayWidth.HALF) {}, TrayButton("board.full", 91) {}))
        val modes = ModesPrefs(holidayOnHome = false, modes = listOf(mode("party"), mode("modeb"), mode("modec")))
        val shelf = shelf(backgroundScope, modes, board)

        // The room is two until the board's buttons are read.
        assertThat(shelf.room.first { it != 2 }).isEqualTo(1)
        assertThat(shelf.shown.first { it.size != 2 }).containsExactly("party")
    }

    /** Ruling 2: fails if a third-width button of another capability, such as Taekwondo's, doesn't take a pad's spot, or takes a mode's. */
    @Test
    fun aThirdOfAnotherCapabilityTakesASoundSpot() = runTest {
        val taekwondo = Buttons("taekwondo", listOf(TrayButton("taekwondo", 90, TrayWidth.THIRD) {}))
        val shelf = shelf(backgroundScope, ModesPrefs(), taekwondo)

        assertThat(shelf.soundRoom.first()).isEqualTo(2)
        assertThat(shelf.room.first()).isEqualTo(2)
    }

    /** Fails if half or full buttons, or the modes' own thirds (the pads), take a sound spot. */
    @Test
    fun noOtherThirdsLeavesThreeSoundSpots() = runTest {
        val board = Buttons("board", listOf(TrayButton("board.start", 20, TrayWidth.HALF) {}, TrayButton("board.full", 21) {}))
        val pads = Buttons(HouseModes.PARTY, listOf(TrayButton("pad.1", 81, TrayWidth.THIRD) {}))
        val shelf = shelf(backgroundScope, ModesPrefs(), board, pads)

        assertThat(shelf.soundRoom.first()).isEqualTo(3)
    }

    /** Fails if the room is announced as two while a half-width button is still on its way, which flashes a second mode on Home. */
    @Test
    fun theRoomNeverSaysTwoBeforeTheButtonsAreKnown() = runTest {
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
        assertThat(rooms).containsExactly(1)
        assertThat(shown.flatten()).containsExactly("party")
    }

    /** Fails if a capability that never answers keeps the room, and so every mode, off Home. */
    @Test
    fun aSilentCapabilityCountsAsNoneAfterASecond() = runTest {
        val silent = object : Capability by Buttons("silent", emptyList()) {
            override fun trayButtons(): Flow<List<TrayButton>> = flow { awaitCancellation() }
        }
        val shelf = shelf(backgroundScope, ModesPrefs(holidayOnHome = false, modes = listOf(mode("party"))), silent)
        val rooms = mutableListOf<Int>()
        val shown = mutableListOf<List<String>>()
        backgroundScope.launch { shelf.room.toList(rooms) }
        backgroundScope.launch { shelf.shown.toList(shown) }

        advanceTimeBy(999)
        assertThat(rooms).isEmpty()
        assertThat(shown).isEmpty()
        advanceTimeBy(2)
        assertThat(rooms).containsExactly(2)
        assertThat(shown.last()).containsExactly("party")
    }

    /** Fails if two readers, or the room and the sound room, each ask the capabilities again, or get counts of their own that can put a third mode on Home. */
    @Test
    fun everyReaderSharesOneCount() = runTest {
        var asked = 0
        val late = object : Capability by Buttons("late", emptyList()) {
            override fun trayButtons(): Flow<List<TrayButton>> = flow {
                asked++
                delay(50)
                emit(listOf(TrayButton("late.start", 90, TrayWidth.HALF) {}))
            }
        }
        val shelf = shelf(backgroundScope, ModesPrefs(modes = listOf(mode("party"))), late)
        val rooms = mutableListOf<Int>()
        val places = mutableListOf<Int?>()
        val soundRooms = mutableListOf<Int>()
        backgroundScope.launch { shelf.room.toList(rooms) }
        advanceTimeBy(30)
        backgroundScope.launch { shelf.place("party").toList(places) }
        backgroundScope.launch { shelf.soundRoom.toList(soundRooms) }

        advanceTimeBy(100)

        assertThat(asked).isEqualTo(1)
        assertThat(rooms).containsExactly(1)
        assertThat(places).containsExactly(null)
        assertThat(soundRooms).containsExactly(3)
    }

    /** Fails if a half button that arrives after the wait doesn't take its spot back from the modes. */
    @Test
    fun aHalfButtonAfterTheWaitBringsTheRoomToOne() = runTest {
        val late = object : Capability by Buttons("late", emptyList()) {
            override fun trayButtons(): Flow<List<TrayButton>> = flow {
                delay(1_500)
                emit(listOf(TrayButton("late.start", 90, TrayWidth.HALF) {}))
            }
        }
        val shelf = shelf(backgroundScope, ModesPrefs(holidayOnHome = false, modes = listOf(mode("party"))), late)
        val rooms = mutableListOf<Int>()
        backgroundScope.launch { shelf.room.toList(rooms) }

        advanceTimeBy(2_000)

        assertThat(rooms).containsExactly(2, 1).inOrder()
    }

    /** Review Focus 3: fails if Holiday mode back on Home doesn't take the first place and push the last mode off. */
    @Test
    fun holidayBackOnHomePushesTheLastOff() = runTest {
        val store = ModesStore(MemoryStore(), backgroundScope)
        store.update { ModesPrefs(holidayOnHome = false, modes = listOf(mode("party"), mode("modeb"))) }
        val shelf = ModesShelf(store, Provider { emptySet() }, backgroundScope)
        assertThat(shelf.places("party", "modeb")).containsExactly(0, 1).inOrder()

        store.update { it.copy(holidayOnHome = true) }
        // The shared shelf catches up with the store as its readers do.
        shelf.shown.first { HouseModes.HOLIDAY in it }

        assertThat(shelf.places(HouseModes.HOLIDAY, "party", "modeb")).containsExactly(0, 1, null).inOrder()
    }

    /** Fails if the modes' own buttons count against the room, which would also read the shelf from inside itself. */
    @Test
    fun theModesOwnButtonsDontCount() = runTest {
        val holiday = Buttons(HouseModes.HOLIDAY, listOf(TrayButton("holiday.mode", 10, TrayWidth.HALF) {}))
        val stored = Buttons("party", listOf(TrayButton("mode.party", 11, TrayWidth.HALF) {}))
        val shelf = shelf(backgroundScope, ModesPrefs(modes = listOf(mode("party"))), holiday, stored)

        assertThat(shelf.room.first()).isEqualTo(2)
    }
}
