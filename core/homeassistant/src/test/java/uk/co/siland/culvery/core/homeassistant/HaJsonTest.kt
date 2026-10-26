package uk.co.siland.culvery.core.homeassistant

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

/** 11 §4.2, §4.3: what Home Assistant's answers say, and no more. */
class HaJsonTest {
    private val states get() = HaJson.states(HaFixtures.STATES).associateBy { it.entityId }

    /** Fails if the running answer isn't recognised, or anything else is. */
    @Test
    fun onlyTheRunningAnswerIsHomeAssistant() {
        assertThat(HaJson.apiRunning(HaFixtures.API)).isTrue()
        assertThat(HaJson.apiRunning("""{"message":"Hello"}""")).isFalse()
        assertThat(HaJson.apiRunning("<html>")).isFalse()
    }

    /** Fails if a vacuum's state is misread. */
    @Test
    fun everyVacuumStateIsRead() {
        assertThat(states.filterKeys { it.startsWith("vacuum.") }.values.map { it.state }).containsExactly(
            "docked", "cleaning", "returning", "paused", "idle", "error", "unavailable", "unknown",
        )
    }

    /** Fails if the battery sensor's state or unit, or the thermostat's temperatures, preset or name, are misread. */
    @Test
    fun theSensorAndTheThermostatAreRead() {
        val sensor = states.getValue("sensor.hoover_battery")
        assertThat(sensor.state).isEqualTo("86")
        assertThat(sensor.texts["unit_of_measurement"]).isEqualTo("%")
        val hall = states.getValue("climate.hall")
        assertThat(hall.numbers["current_temperature"]).isEqualTo(20.8)
        assertThat(hall.numbers["temperature"]).isEqualTo(21.2)
        assertThat(hall.texts["preset_mode"]).isEqualTo("eco")
        assertThat(hall.texts["hvac_action"]).isEqualTo("heating")
        assertThat(hall.friendlyName).isEqualTo("Hall")
        assertThat(states.getValue("climate.landing").texts["preset_mode"]).isEqualTo("none")
        assertThat(states.getValue("climate.study").state).isEqualTo("unavailable")
    }

    /** Fails if the domain splits wrongly, or a list is kept as an attribute. */
    @Test
    fun theDomainSplitsAndOnlyTextAndNumbersAreKept() {
        val hoover = states.getValue("vacuum.hoover")
        assertThat(hoover.domain).isEqualTo("vacuum")
        val hall = states.getValue("climate.hall")
        assertThat(hall.texts).doesNotContainKey("preset_modes")
        assertThat(hall.numbers).doesNotContainKey("preset_modes")
    }

    /** Fails if a single entity isn't read as the list's entry is, or a flag is kept. */
    @Test
    fun oneStateReadsAsTheListEntryDoes() {
        val one = HaJson.state("""{"entity_id":"climate.hall","state":"heat","attributes":{"preset_mode":"eco","temperature":21.2,"flag":true}}""")
        assertThat(one.entityId).isEqualTo("climate.hall")
        assertThat(one.texts).containsExactly("preset_mode", "eco")
        assertThat(one.numbers).containsExactly("temperature", 21.2)
    }

    /** Fails if a socket message isn't read as its type. */
    @Test
    fun socketMessagesAreReadAsTheirType() {
        assertThat(HaJson.socket(HaFixtures.text("ws_auth_required.json"))).isEqualTo(SocketMessage.AuthRequired)
        assertThat(HaJson.socket(HaFixtures.text("ws_auth_ok.json"))).isEqualTo(SocketMessage.AuthOk)
        assertThat(HaJson.socket(HaFixtures.text("ws_auth_invalid.json"))).isEqualTo(SocketMessage.AuthInvalid)
        assertThat(HaJson.socket(HaFixtures.text("ws_result_ok.json"))).isEqualTo(SocketMessage.Result(1, true))
        assertThat(HaJson.socket(HaFixtures.ADDED)).isInstanceOf(SocketMessage.Entities::class.java)
        assertThat(HaJson.socket("""{"type":"pong","id":3}""")).isEqualTo(SocketMessage.Other)
    }

    /** Fails if the first event's entities, with their attributes, are misread. */
    @Test
    fun addedEntitiesAreReadWhole() {
        val added = (HaJson.socket(HaFixtures.ADDED) as SocketMessage.Entities).added
        assertThat(added.getValue("vacuum.hoover").state).isEqualTo("docked")
        assertThat(added.getValue("vacuum.hoover").numbers["battery_level"]).isEqualTo(86.0)
        assertThat(added.getValue("climate.hall").texts["preset_mode"]).isEqualTo("eco")
        assertThat(added.getValue("sensor.hoover_battery").state).isEqualTo("86")
    }

    /** Fails if a change doesn't move the state, keep the other attributes, update the ones it names and drop the ones it removes. */
    @Test
    fun aChangeIsAppliedToTheStateItNames() {
        val event = HaJson.socket(HaFixtures.CHANGED) as SocketMessage.Entities
        val before = (HaJson.socket(HaFixtures.ADDED) as SocketMessage.Entities).added.getValue("vacuum.hoover")
        assertThat(before.state).isEqualTo("docked")
        val after = before.apply(event.changed.getValue("vacuum.hoover"))
        assertThat(after.state).isEqualTo("cleaning")
        assertThat(after.friendlyName).isEqualTo("Hoover")
        assertThat(after.numbers["battery_level"]).isEqualTo(80.0)
        assertThat(after.texts).doesNotContainKey("fan_speed")
        assertThat(before.texts).containsKey("fan_speed")
    }

    /** Fails if a change with no state moves the state, or loses the attributes it doesn't name. */
    @Test
    fun aChangeWithoutAStateKeepsIt() {
        val event = HaJson.socket(HaFixtures.CHANGED) as SocketMessage.Entities
        val hall = (HaJson.socket(HaFixtures.ADDED) as SocketMessage.Entities).added.getValue("climate.hall")
        val after = hall.apply(event.changed.getValue("climate.hall"))
        assertThat(after.state).isEqualTo("heat")
        assertThat(after.numbers["current_temperature"]).isEqualTo(20.9)
        assertThat(after.numbers["temperature"]).isEqualTo(21.2)
    }

    /** Fails if an attribute a change sets to null, a flag or a list keeps its old value in the cache. */
    @Test
    fun anAttributeChangedToNothingIsDropped() {
        val hall = (HaJson.socket(HaFixtures.ADDED) as SocketMessage.Entities).added.getValue("climate.hall")
        val change = (
            HaJson.socket("""{"id":1,"type":"event","event":{"c":{"climate.hall":{"+":{"a":{"preset_mode":null,"temperature":false,"hvac_action":["x"]}}}}}}""")
                as SocketMessage.Entities
            ).changed.getValue("climate.hall")
        val after = hall.apply(change)
        assertThat(after.texts).doesNotContainKey("preset_mode")
        assertThat(after.texts).doesNotContainKey("hvac_action")
        assertThat(after.numbers).doesNotContainKey("temperature")
        assertThat(after.numbers["current_temperature"]).isEqualTo(20.8)
    }

    /** Fails if a removed entity's id isn't read. */
    @Test
    fun removedIdsAreRead() {
        assertThat((HaJson.socket(HaFixtures.REMOVED) as SocketMessage.Entities).removed).containsExactly("vacuum.hoover")
    }

    /** Fails if an unreadable answer doesn't throw, throws with a cause, or quotes the input. */
    @Test
    fun anUnreadableAnswerThrowsWithoutQuotingIt() {
        listOf<() -> Any>(
            { HaJson.states("not json") },
            { HaJson.states("""{"entity_id":"secret.thing"}""") },
            { HaJson.state("""[{"secret":"thing"}]""") },
            { HaJson.socket("""{"type":"result","secret":"thing"}""") },
        ).forEach { read ->
            val e = assertThrows(HaJsonException::class.java) { read() }
            assertThat(e.cause).isNull()
            assertThat(e.message).doesNotContain("secret")
            assertThat(e.message).doesNotContain("not json")
        }
    }

    /** Fails if any text the reader produces holds an id or a name. */
    @Test
    fun noTextNamesAnEntity() {
        val added = HaJson.socket(HaFixtures.ADDED) as SocketMessage.Entities
        val changed = HaJson.socket(HaFixtures.CHANGED) as SocketMessage.Entities
        val all: List<Any> = HaJson.states(HaFixtures.STATES) + added.added.values + listOf(added, changed) + changed.changed.values +
            HaJson.socket(HaFixtures.text("ws_result_ok.json"))
        all.forEach {
            val text = it.toString().lowercase()
            listOf("hoover", "hall", "alex", "landing", "battery").forEach { word -> assertThat(text).doesNotContain(word) }
        }
    }
}
