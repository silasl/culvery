package uk.co.siland.culvery.capability.lights.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uk.co.siland.culvery.capability.lights.room

/** 9 §6.9: the rooms' order is taken once, on rooms first, and only grows or shrinks after that. */
class RoomOrderTest {
    private val lounge = room("Lounge", on = true)
    private val kitchen = room("Kitchen")
    private val hall = room("Hall", on = true)
    private val garden = room("Garden")
    private val opened = listOf("Lounge-id", "Hall-id", "Kitchen-id")

    /** Fails if an off room comes before an on one, or either group leaves Hue's order. */
    @Test
    fun onRoomsComeFirstInHuesOrder() {
        assertThat(openedOrder(null, listOf(lounge, kitchen, hall))).containsExactly("Lounge-id", "Hall-id", "Kitchen-id").inOrder()
    }

    /** Fails if an empty list before the first read is taken as the order. */
    @Test
    fun nothingUntilANonEmptyList() {
        assertThat(openedOrder(null, emptyList())).isNull()
    }

    /** Fails if a room turning on moves up. */
    @Test
    fun theOrderStaysWhenARoomTurnsOn() {
        assertThat(openedOrder(opened, listOf(lounge, kitchen.copy(on = true), hall))).containsExactlyElementsIn(opened).inOrder()
    }

    /** Fails if a room paired later is put anywhere but at the end. */
    @Test
    fun aNewRoomGoesAtTheEnd() {
        assertThat(openedOrder(opened, listOf(garden.copy(on = true), lounge, kitchen, hall)))
            .containsExactly("Lounge-id", "Hall-id", "Kitchen-id", "Garden-id").inOrder()
    }

    /** Fails if a room Hue no longer lists stays in the order. */
    @Test
    fun aRoomThatGoesIsDropped() {
        assertThat(openedOrder(opened, listOf(lounge, kitchen))).containsExactly("Lounge-id", "Kitchen-id").inOrder()
    }

    /** Fails if a room going and another arriving in one update leaves a gap or puts the new one in the old one's place. */
    @Test
    fun aRoomThatGoesAndOneThatArrivesInOneUpdate() {
        assertThat(openedOrder(opened, listOf(lounge, kitchen, garden))).containsExactly("Lounge-id", "Kitchen-id", "Garden-id").inOrder()
    }

    /** Fails if the bridge dropping (an empty list) and coming back sorts the rooms again. */
    @Test
    fun bridgeDropAndReturnKeepsOrder() {
        val before = listOf("Hall-id", "Lounge-id")
        val dropped = openedOrder(before, emptyList())
        assertThat(dropped).containsExactlyElementsIn(before).inOrder()
        assertThat(openedOrder(dropped, listOf(lounge, hall.copy(on = false)))).containsExactlyElementsIn(before).inOrder()
    }
}
