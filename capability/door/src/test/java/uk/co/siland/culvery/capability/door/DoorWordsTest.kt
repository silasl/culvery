package uk.co.siland.culvery.capability.door

import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import org.junit.Test

/** 9 §4.6: the plainer words for each kind, a notice's sub-line, and the Door tab's subtitle. */
class DoorWordsTest {
    private val london = ZoneId.of("Europe/London")

    private fun event(id: Long, kind: DoorKind = DoorKind.RING, device: String = "Front Door", unrecognised: Boolean = false) =
        DoorEvent(id, NOW, kind, device, unrecognised, null, seen = false)

    @Test
    fun aRingSaysSomeoneRangWithItsDevice() {
        assertThat(eventLine(event(1), london)).isEqualTo("14:02 · Someone rang")
        assertThat(subLine(event(1))).isEqualTo("Front Door")
    }

    @Test
    fun motionSaysMovement() {
        assertThat(kindLabel(event(1, DoorKind.MOTION))).isEqualTo("Movement")
        assertThat(subLine(event(1, DoorKind.MOTION, device = ""))).isNull()
    }

    @Test
    fun anUnrecognisedNoticeSaysCulveryCouldntTell() {
        assertThat(kindLabel(event(1, unrecognised = true))).isEqualTo("Notice from Ring")
        assertThat(subLine(event(1, unrecognised = true))).isEqualTo("Front Door · Culvery couldn’t tell what this was")
        assertThat(subLine(event(1, device = "", unrecognised = true))).isEqualTo("Culvery couldn’t tell what this was")
    }

    @Test
    fun newRingsCountOneAndMany() {
        assertThat(newRings(1)).isEqualTo("1 new ring")
        assertThat(newRings(2)).isEqualTo("2 new rings")
    }

    /** Fails if a device repeats, an empty device shows, "0 new rings" shows, or devices lose their first-seen order. */
    @Test
    fun theSubtitleNamesEachDeviceOnceThenTheNewRings() {
        val events = listOf(event(1), event(2, device = ""), event(3, DoorKind.MOTION, "Back Garden"), event(4))
        assertThat(doorSubtitle(events, newCount = 2)).isEqualTo("Front Door · Back Garden · 2 new rings")
        assertThat(doorSubtitle(events, newCount = 0)).isEqualTo("Front Door · Back Garden")
        assertThat(doorSubtitle(listOf(event(1, device = "")), newCount = 1)).isEqualTo("1 new ring")
        assertThat(doorSubtitle(emptyList(), newCount = 0)).isNull()
    }
}
