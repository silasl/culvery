package uk.co.siland.culvery.core.house

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Test

class HouseSeamsTest {
    private class Switch(override val modeId: String, on: Boolean?) : HouseModeSwitch {
        override val modeName = "$modeId mode"
        override val isOn: StateFlow<Boolean?> = MutableStateFlow(on)

        override suspend fun tick() = Unit
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
            SceneChoice("r1", "Kitchen", "s1", "Bright"),
            RoomBefore("r1", "Kitchen", "s1", true, 40, "s2"),
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
}
