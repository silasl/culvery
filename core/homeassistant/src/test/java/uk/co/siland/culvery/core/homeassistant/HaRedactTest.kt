package uk.co.siland.culvery.core.homeassistant

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 11 §8.2: what the debug capture may show. */
class HaRedactTest {
    private val keep = setOf("vacuum.hoover", "sensor.hoover_battery", "climate.hall")

    // An address and the token in a text, as an attribute might carry them.
    private fun withAddress(text: String) =
        text.replace("\"friendly_name\"", "\"entity_picture\":\"http://192.168.1.20:8123/x?t=test-token\",\"friendly_name\"")

    private val answers = listOf(
        HaFixtures.STATES,
        HaFixtures.ADDED,
        HaFixtures.CHANGED,
        HaFixtures.REMOVED,
        HaFixtures.text("ws_auth_invalid.json"),
        HaFixtures.API,
    )

    /** Fails if a name, an id, the address or the token survives, or a state, a mode, a unit, a temperature or a kept id doesn't. */
    @Test
    fun namesIdsAndAddressesGo() {
        val out = listOf(HaFixtures.STATES, HaFixtures.ADDED).map { HaJson.redact(withAddress(it), keep) }
        out.forEach { text ->
            listOf("Hall", "hoover", "192.168", "test-token").forEach { assertThat(text).doesNotContain(it) }
            listOf("\"docked\"", "\"eco\"", "\"heating\"", "\"%\"", "20.8", "\"86\"", "\"vacuum.[1]\"").forEach { assertThat(text).contains(it) }
        }
        assertThat(out[1]).contains("\"vacuum.[1]\":")
        assertThat(out[1]).contains("\"sensor.[1]\":")
        assertThat(out[1]).contains("\"climate.[1]\":")
    }

    /** Fails if people, trackers, coordinates, unlisted numbers or another vacuum's state survive. */
    @Test
    fun otherDomainsAndCoordinatesGo() {
        answers.forEach { answer ->
            val text = HaJson.redact(answer, keep)
            listOf("person", "alex", "device_tracker", "latitude", "longitude", "51.5", "0.1276", "gps_accuracy", "v_cleaning").forEach {
                assertThat(text.lowercase()).doesNotContain(it)
            }
        }
        val states = HaJson.redact(HaFixtures.STATES, keep)
        assertThat(states).contains("\"battery_level\":0")
        assertThat(states).doesNotContain("\"battery_level\":86")
        assertThat(states).doesNotContain("\"returning\"")
        assertThat(states).doesNotContain("19.5")
    }

    /** Fails if an id in a removal list or a change map that isn't kept survives, or a kept one isn't masked. */
    @Test
    fun removalsAndChangesNameOnlyKeptEntities() {
        assertThat(HaJson.redact(HaFixtures.REMOVED, keep)).contains("\"r\":[\"vacuum.[1]\"]")
        assertThat(HaJson.redact(HaFixtures.REMOVED, emptySet())).contains("\"r\":[]")
        val changed = HaJson.redact(HaFixtures.CHANGED, setOf("vacuum.hoover"))
        assertThat(changed).contains("\"vacuum.[1]\":")
        assertThat(changed).doesNotContain("climate")
    }

    /** Fails if the message id, success and the auth type are lost, or the server's message text is kept. */
    @Test
    fun theSocketsFramingStays() {
        val result = HaJson.redact(HaFixtures.text("ws_result_ok.json"), keep)
        assertThat(result).contains("\"id\":1")
        assertThat(result).contains("\"type\":\"result\"")
        val invalid = HaJson.redact(HaFixtures.text("ws_auth_invalid.json"), keep)
        assertThat(invalid).contains("auth_invalid")
        assertThat(invalid).doesNotContain("Invalid access token")
    }

    /** Fails if text that isn't JSON comes through. */
    @Test
    fun unreadableTextIsNotShown() {
        assertThat(HaJson.redact("<html>192.168.1.20 test-token</html>", keep)).isEqualTo("[unreadable]")
    }

    /** Fails if a kept sensor's numeric state is lost, or another domain's number is shown. */
    @Test
    fun aKeptSensorsNumberStaysAndNothingElses() {
        val text = HaJson.redact("""{"entity_id":"sensor.hoover_battery","state":86,"attributes":{"x":5}}""", keep)
        assertThat(text).contains("\"state\":86")
        assertThat(text).contains("\"x\":0")
        assertThat(HaJson.redact("""{"entity_id":"vacuum.hoover","state":86}""", keep)).contains("\"state\":0")
    }
}
