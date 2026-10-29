package uk.co.siland.culvery.capability.music

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uk.co.siland.culvery.core.house.PartyGroup

/** 13 §4.1, §2.11: which room leads Party's group, and when a group is still the one Party made. */
class PartyMusicTest {
    private val kitchen = group("Kitchen-id", "Kitchen")
    private val lounge = group("Lounge-id", "Lounge", "Dining")
    private val all = setOf("Kitchen-id", "Lounge-id", "Dining-id")

    /** Fails if the coordinator isn't the leader of the group holding most chosen rooms, or a member leads, or a tie isn't the layout's first. */
    @Test
    fun theCoordinatorLeadsTheMostChosenRooms() {
        val layout = listOf(kitchen, lounge)
        assertThat(partyCoordinator(layout, all)).isEqualTo("Lounge-id")
        assertThat(partyCoordinator(layout, setOf("Kitchen-id"))).isEqualTo("Kitchen-id")
        assertThat(partyCoordinator(layout, setOf("Dining-id"))).isEqualTo("Dining-id")
        assertThat(partyCoordinator(layout, setOf("Kitchen-id", "Lounge-id"))).isEqualTo("Kitchen-id")
        assertThat(partyCoordinator(layout, setOf("Gone-id"))).isNull()
    }

    /** Fails if a changed room count or leader, or an absent group, still counts as Party's. */
    @Test
    fun stillPartyNeedsTheSameRoomsAndLeader() {
        val made = PartyGroup("Kitchen-id", all)
        val same = group("Kitchen-id", "Kitchen", "Lounge", "Dining")
        assertThat(stillParty(listOf(same), made)).isTrue()
        assertThat(stillParty(listOf(group("Kitchen-id", "Kitchen", "Lounge")), made)).isFalse()
        assertThat(stillParty(listOf(group("Kitchen-id", "Kitchen", "Lounge", "Dining", "Study")), made)).isFalse()
        assertThat(stillParty(listOf(group("Lounge-id", "Lounge", "Kitchen", "Dining")), made)).isFalse()
        assertThat(stillParty(listOf(kitchen, lounge), made)).isFalse()
        assertThat(stillParty(emptyList(), made)).isFalse()
    }
}
