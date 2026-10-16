package uk.co.siland.culvery.capability.lights

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 7 §5.1: the main room is the chosen one while Hue lists it, else Hue's first. */
class MainRoomTest {
    private val rooms = listOf(room("Lounge"), room("Kitchen"), room("Hall"))

    /** Fails if the choice is ignored in favour of the first room. */
    @Test
    fun theChosenRoomWhenHueListsIt() {
        assertThat(mainRoom(rooms, "Kitchen-id")?.name).isEqualTo("Kitchen")
    }

    /** Fails if no choice gives anything but Hue's first room. */
    @Test
    fun noChoiceIsHuesFirstRoom() {
        assertThat(mainRoom(rooms, null)?.name).isEqualTo("Lounge")
    }

    /** Fails if a chosen room Hue no longer lists leaves the card empty. */
    @Test
    fun aGoneMainRoomFallsBackToTheFirst() {
        assertThat(mainRoom(rooms, "Garden-id")?.name).isEqualTo("Lounge")
    }

    /** Fails if an empty list gives a room. */
    @Test
    fun noRoomsIsNothing() {
        assertThat(mainRoom(emptyList(), "Lounge-id")).isNull()
        assertThat(mainRoom(emptyList(), null)).isNull()
    }
}
