package uk.co.siland.culvery.core.house

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertThrows
import org.junit.Test
import uk.co.siland.culvery.core.access.Authorised
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role

class HouseSeamsTest {
    private class Switch(override val modeId: String, on: Boolean?) : HouseModeSwitch {
        override val modeName = "$modeId mode"
        override val logName = "a stored mode"
        override val isOn: StateFlow<Boolean?> = MutableStateFlow(on)
        override val turningOn: StateFlow<Boolean> = MutableStateFlow(false)

        override suspend fun tick() = Unit
    }

    /** 15a §4.5: fails if a line still sends the user to a Plugs tab, which no longer exists. */
    @Test
    fun theLinesSendTheUserToTheLightsTab() {
        assertThat(HouseLines.PLUGS_MISSED).isEqualTo("The plugs didn’t switch. Switch them on the Lights tab.")
        assertThat(HouseLines.PLUGS_LEAVE_THREW).isEqualTo("Some plugs may not have gone back. Check them on the Lights tab.")
    }

    /** 12 §6: fails if a plug's name can be read out of a log line. */
    @Test
    fun aPlugChangeHidesItsName() {
        assertThat(PlugChange("Lamp", true).toString()).doesNotContain("Lamp")
    }

    /** 13 §2.14: fails if any of the new types lets a room, speaker, scene or playlist name or id into a log line. */
    @Test
    fun theNewTypesHideTheirIdsAndNames() {
        val scene = HouseScene("s1", "Bright")
        val texts = listOf(
            HouseRoom("Kitchen-id", "Kitchen"),
            MusicPick("FV:2/3", "Party mix"),
            GroupBefore("Kitchen-id", listOf("Kitchen-id")),
            PartyGroup("Kitchen-id", setOf("Kitchen-id")),
            scene,
            LightRoomOption("r1", "Kitchen", listOf(scene)),
            RoomChoice("r1", "Kitchen", LightChoice.Scene("s1", "Bright")),
            RoomBefore("r1", "Kitchen", LightChoice.Scene("s1", "Bright"), true, 40, "s2"),
        ).map { it.toString() }
        for (text in texts) {
            for (secret in listOf("Kitchen", "Kitchen-id", "Party mix", "FV:2/3", "r1", "s1", "Bright")) {
                assertThat(text).doesNotContain(secret)
            }
        }
    }

    @Test
    fun roomListJoinsAsTheCopySays() {
        assertThat(HouseLines.roomList(listOf("Kitchen"))).isEqualTo("Kitchen")
        assertThat(HouseLines.roomList(listOf("Kitchen", "Lounge"))).isEqualTo("Kitchen and Lounge")
        assertThat(HouseLines.roomList(listOf("Kitchen", "Lounge", "Dining"))).isEqualTo("Kitchen, Lounge and Dining")
    }

    @Test
    fun theLightsLinesNameTheRooms() {
        assertThat(HouseLines.lightsDidntChange(listOf("Kitchen", "Lounge"))).isEqualTo("Some lights didn’t change: Kitchen and Lounge.")
        assertThat(HouseLines.lightsDidntGoBack(listOf("Kitchen"))).isEqualTo("Some lights didn’t go back: Kitchen.")
    }

    @Test
    fun turnOffFirstNamesTheMode() {
        assertThat(HouseLines.turnOffFirst("Holiday mode")).isEqualTo("Turn Holiday mode off first.")
        assertThat(HouseLines.turnOffFirst("Party mode")).isEqualTo("Turn Party mode off first.")
    }

    @Test
    fun theMusicLinesAreTheSpecs() {
        assertThat(HouseLines.MUSIC_DIDNT_START).isEqualTo("The music didn’t start. Check the Sonos app.")
        assertThat(HouseLines.MUSIC_DIDNT_STOP).isEqualTo("The music didn’t stop. Stop it in the Sonos app.")
        assertThat(HouseLines.SPEAKERS_NOT_BACK).isEqualTo("The speakers didn’t go back to their groups. Regroup them in the Sonos app.")
    }

    /** 13 §2.6: fails if a mode blocks itself, an unread mode blocks, or the other mode being on doesn't. */
    @Test
    fun blockedByFindsAnotherModeOn() {
        val holiday = Switch(HouseModes.HOLIDAY, true)
        val party = Switch(HouseModes.PARTY, false)
        assertThat(setOf(holiday, party).blockedBy(HouseModes.PARTY)).isSameInstanceAs(holiday)
        assertThat(setOf(holiday, party).blockedBy(HouseModes.HOLIDAY)).isNull()
        assertThat(setOf(Switch(HouseModes.HOLIDAY, null), party).blockedBy(HouseModes.PARTY)).isNull()
    }

    private fun person(vararg granted: String) = ModeGrant.Person(Authorised(Person(PersonId("pat-id"), "Pat", 0), Role.ADULT, granted.toSet()))

    /** 15b Ruling 2: fails if Holiday mode loses its own permission, or a stored mode doesn't share Party mode's. */
    @Test
    fun holidayTakesItsOwnPermission() {
        assertThat(modePermission("holiday")).isEqualTo("holiday.mode")
        assertThat(modePermission("party")).isEqualTo("party.mode")
        assertThat(modePermission("modeabcdef")).isEqualTo("party.mode")
    }

    /** 15b Ruling 3: fails if Holiday mode's permission lets a person run a stored mode. */
    @Test
    fun aPersonNeedsTheModesPermission() {
        assertThat(person("holiday.mode").allows("party")).isFalse()
        assertThat(person("holiday.mode").allows("holiday")).isTrue()
        assertThat(person("party.mode").allows("modeabcdef")).isTrue()
    }

    /** 15b decision 7: fails if the wall vouches for any mode but its own. */
    @Test
    fun theWallVouchesForOneMode() {
        assertThat(ModeGrant.Wall("party").allows("party")).isTrue()
        assertThat(ModeGrant.Wall("party").allows("modeabcdef")).isFalse()
    }

    /** 15b decision 7: fails if the wall can be made to vouch for Holiday mode. */
    @Test
    fun theWallNeverVouchesForHoliday() {
        assertThrows(IllegalArgumentException::class.java) { ModeGrant.Wall("holiday") }
    }

    /** 15b §5: fails if a grant or a plug type lets a person, a plug's name or an id into a log line. */
    @Test
    fun theGrantsAndPlugTypesHideTheirIdsAndNames() {
        val texts = listOf(person("party.mode"), ModeGrant.Wall("modeabcdef"), PlugOption("switch.lamp", "Lamp", false), AddedPlug("switch.lamp", true, false))
            .map { it.toString() }
        for (text in texts) {
            for (secret in listOf("modeabcdef", "switch.lamp", "Lamp", "Pat", "pat-id")) assertThat(text).doesNotContain(secret)
        }
    }
}
