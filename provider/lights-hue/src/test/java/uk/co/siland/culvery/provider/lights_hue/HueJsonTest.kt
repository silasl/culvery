package uk.co.siland.culvery.provider.lights_hue

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import uk.co.siland.culvery.provider.lights_hue.HueFixtures as F

class HueJsonTest {
    private fun tree(text: String) = HueJson.tree(text)

    /** Fails if a room loses its devices, its group or its name, or a non-device child counts as a device. */
    @Test
    fun roomsWithTheirDevicesAndGroup() {
        assertThat(HueJson.rooms(tree(F.ROOMS))).containsExactly(
            HueRoom(F.LOUNGE, "Lounge", listOf(F.LOUNGE_LAMP_DEVICE, F.LOUNGE_STRIP_DEVICE), F.LOUNGE_GROUP),
            HueRoom(F.KITCHEN, "Kitchen", listOf(F.KITCHEN_SPOT_DEVICE), F.KITCHEN_GROUP),
            HueRoom(F.GARDEN, "Garden", listOf(F.GARDEN_PLUG_DEVICE), F.GARDEN_GROUP),
        ).inOrder()
    }

    /** Fails if the whole-home group is left out, its owner type is lost, or a missing brightness reads as 0. */
    @Test
    fun groupedLightsWithOwnerAndBrightness() {
        assertThat(HueJson.groupedLights(tree(F.GROUPED_LIGHTS))).containsExactly(
            HueGroupedLight(F.LOUNGE_GROUP, F.LOUNGE, "room", on = true, brightness = 70.4),
            HueGroupedLight(F.KITCHEN_GROUP, F.KITCHEN, "room", on = false, brightness = 0.0),
            HueGroupedLight(F.GARDEN_GROUP, F.GARDEN, "room", on = true, brightness = null),
            HueGroupedLight(F.HOME_GROUP, "77777777-aaaa-4bbb-8ccc-000000000001", "bridge_home", on = true, brightness = null),
        ).inOrder()
    }

    /** Fails if a light with no `dimming` is called dimmable, or a light's device is read from the wrong field. */
    @Test
    fun lightsSayWhetherTheyDim() {
        val lights = HueJson.lights(tree(F.LIGHTS))
        assertThat(lights.map { it.deviceId }).containsExactly(
            F.LOUNGE_LAMP_DEVICE, F.LOUNGE_STRIP_DEVICE, F.KITCHEN_SPOT_DEVICE, F.GARDEN_PLUG_DEVICE, F.SHED_LIGHT_DEVICE,
        ).inOrder()
        assertThat(lights.map { it.dimmable }).containsExactly(true, true, true, false, true).inOrder()
    }

    /** Fails if a light's own level, which an off light keeps, isn't read, or one with no `dimming` gets one. */
    @Test
    fun lightsKeepTheirOwnLevel() {
        assertThat(HueJson.lights(tree(F.LIGHTS)).map { it.brightness }).containsExactly(70.4, 70.4, 45.0, null, 100.0).inOrder()
    }

    /** Fails if `inactive` reads as active, or a scene loses its room. */
    @Test
    fun scenesAndWhichIsActive() {
        assertThat(HueJson.scenes(tree(F.SCENES))).containsExactly(
            HueScene(F.RELAX, "Relax", F.LOUNGE, active = true),
            HueScene(F.BRIGHT, "Bright", F.LOUNGE, active = false),
        ).inOrder()
    }

    /** Fails if `on` reads `enabled` or `status`, which are true and running for both Mimic presence instances. */
    @Test
    fun mimicPresenceIsOnByItsPmState() {
        val mimic = HueJson.behaviors(tree(F.BEHAVIORS)).filter { it.id == F.PM_STOPPED || it.id == F.PM_STARTED }
        assertThat(mimic.map { it.enabled }).containsExactly(true, true)
        assertThat(mimic.map { it.isMimic }).containsExactly(true, true)
        assertThat(mimic.map { it.on }).containsExactly(false, true).inOrder()
    }

    /** Fails if `on` reads `status` (running in both) instead of `enabled`, or a Schedule is taken for Mimic presence. */
    @Test
    fun anOrdinaryAutomationIsOnByEnabled() {
        val schedules = HueJson.behaviors(tree(F.BEHAVIORS)).filter { it.id == F.SCHEDULE_ON || it.id == F.SCHEDULE_OFF }
        assertThat(schedules.map { it.isMimic }).containsExactly(false, false)
        assertThat(schedules.map { it.on }).containsExactly(true, false).inOrder()
    }

    /** Fails if a switch's state without `pm_state` makes it Mimic, or an unnamed automation gets a made-up name. */
    @Test
    fun otherInstancesAndUnnamedOnesAreRead() {
        val all = HueJson.behaviors(tree(F.BEHAVIORS)).associateBy { it.id }
        assertThat(all.getValue(F.SWITCH)).isEqualTo(HueBehavior(F.SWITCH, "Hall dimmer", F.SWITCH_SCRIPT, enabled = true, pmState = null))
        assertThat(all.getValue(F.UNNAMED).name).isNull()
        assertThat(all.getValue(F.PM_STARTED).pmState).isEqualTo("started")
    }

    /** Fails if the config's id is read from the wrong field or kept in its own case. */
    @Test
    fun theBridgeIdFromTheConfig() {
        assertThat(HueJson.config(tree(F.CONFIG))).isEqualTo(F.BRIDGE_ID)
        assertThat(HueJson.config(tree(F.CONFIG.replace(F.BRIDGE_ID, F.BRIDGE_ID.uppercase())))).isEqualTo(F.BRIDGE_ID)
    }

    /** Fails if the key is not the `username`, or a button not pressed or another error gives a key. */
    @Test
    fun pairingAnswers() {
        assertThat(HueJson.pair(tree(F.PAIRED))).isEqualTo(F.KEY)
        assertThat(HueJson.pair(tree(F.NOT_PRESSED))).isNull()
        assertThat(HueJson.pair(tree(F.UNAUTHORISED_V1))).isNull()
    }

    /** Fails if either unauthorised shape is missed, or an ordinary answer is called unauthorised. */
    @Test
    fun unauthorisedInEitherShape() {
        assertThat(HueJson.isUnauthorised(tree(F.UNAUTHORISED))).isTrue()
        assertThat(HueJson.isUnauthorised(tree(F.UNAUTHORISED_V1))).isTrue()
        assertThat(HueJson.isUnauthorised(tree(F.ROOMS))).isFalse()
        assertThat(HueJson.isUnauthorised(tree(F.NOT_PRESSED))).isFalse()
        assertThat(HueJson.isUnauthorised(tree("not json"))).isFalse()
    }

    /** Fails if a comment or id line reaches the parser, or the events are read wrongly. */
    @Test
    fun theStreamSkipsCommentsAndIds() {
        val payloads = HueJson.sse(F.EVENTS.lineSequence()).toList()
        assertThat(payloads).hasSize(2)
        assertThat(payloads.all { it.startsWith("[{") }).isTrue()
        assertThat(payloads.flatMap(HueJson::events)).containsExactly(
            HueEvent.GroupedLight(F.LOUNGE_GROUP, on = false, brightness = null),
            HueEvent.Scene(F.RELAX, active = true),
            HueEvent.Reread,
        ).inOrder()
    }

    /** Fails if a brightness-only update turns the room off, or an event for another resource is applied. */
    @Test
    fun partialAndOtherUpdatesKeepWhatTheyDoNotSay() {
        val update = """[{"type":"update","data":[
            {"id":"g","type":"grouped_light","dimming":{"brightness":30.0}},
            {"id":"l","type":"light","on":{"on":false}},
            {"id":"s","type":"scene","status":{"active":"inactive"}}]}]"""
        assertThat(HueJson.events(update)).containsExactly(
            HueEvent.GroupedLight("g", on = null, brightness = 30.0),
            HueEvent.Scene("s", active = false),
        ).inOrder()
        assertThat(HueJson.events("""[{"type":"update","data":[{"id":"r","type":"room","metadata":{"name":"x"}}]}]"""))
            .containsExactly(HueEvent.Reread)
        assertThat(HueJson.events("""[{"type":"delete","data":[{"id":"r","type":"scene"}]}]""")).containsExactly(HueEvent.Reread)
    }

    /** Fails if a string `status` on another resource, or a scene update without `active`, loses the whole payload. */
    @Test
    fun statusesOfOtherResourcesDoNotSpoilAPayload() {
        val payload = """[{"type":"update","data":[
            {"id":"z","type":"zigbee_connectivity","status":"connected"},
            {"id":"b","type":"behavior_instance","status":"running","state":{"pm_state":"started"}},
            {"id":"s1","type":"scene","last_recall":"2026-10-16T10:00:00Z"},
            {"id":"g","type":"grouped_light","on":{"on":true}},
            {"id":"s2","type":"scene","status":{"active":"dynamic_palette"}}]}]"""
        assertThat(HueJson.events(payload)).containsExactly(
            HueEvent.GroupedLight("g", on = true, brightness = null),
            HueEvent.Scene("s2", active = true),
        ).inOrder()
    }

    /** Fails if a wrong shape escapes as another exception type, or the exception carries the answer's text. */
    @Test
    fun aWrongShapeIsAHueJsonException() {
        listOf("", "not json", "{}", F.UNAUTHORISED, """{"data":[{"id":1}]}""", "[]").forEach {
            assertThrows(HueJsonException::class.java) { HueJson.rooms(tree(it)) }
        }
        assertThrows(HueJsonException::class.java) { HueJson.behaviors(tree(F.ROOMS)) }
        assertThrows(HueJsonException::class.java) { HueJson.config(tree("[]")) }
        assertThrows(HueJsonException::class.java) { HueJson.pair(tree("[]")) }
        assertThrows(HueJsonException::class.java) { HueJson.pair(tree("""[{"other":1}]""")) }
        assertThrows(HueJsonException::class.java) { HueJson.events("{}") }
        val thrown = assertThrows(HueJsonException::class.java) { HueJson.config(tree("""{"bridgeid":7,"secret-name":"Lounge"}""")) }
        assertThat(thrown.cause).isNull()
        assertThat(thrown.message.orEmpty()).doesNotContain("Lounge")
    }

    /** Fails if any request body differs by a character from what the bridge is sent. */
    @Test
    fun bodies() {
        assertThat(HueJson.onBody(false)).isEqualTo("""{"on":{"on":false}}""")
        assertThat(HueJson.brightnessBody(70, true)).isEqualTo("""{"on":{"on":true},"dimming":{"brightness":70}}""")
        assertThat(HueJson.brightnessBody(70, false)).isEqualTo("""{"dimming":{"brightness":70}}""")
        assertThat(HueJson.recallBody()).isEqualTo("""{"recall":{"action":"active"}}""")
        assertThat(HueJson.enabledBody(true)).isEqualTo("""{"enabled":true}""")
        assertThat(HueJson.triggerBody(true)).isEqualTo("""{"trigger":{"start":true}}""")
        assertThat(HueJson.triggerBody(false)).isEqualTo("""{"trigger":{"stop":true}}""")
        assertThat(HueJson.pairBody()).isEqualTo("""{"devicetype":"culvery#tablet","generateclientkey":true}""")
    }

    /** Fails if a name, room or light kind, key, bridge id, MAC or address survives in any fixture, a resource id is lost, or the output stops parsing. */
    @Test
    fun theCaptureShapeReplacesNamesKeysIdsAndAddresses() {
        val answers = listOf(
            F.ROOMS, F.GROUPED_LIGHTS, F.LIGHTS, F.SCENES, F.BEHAVIORS, F.CONFIG, F.PAIRED, F.NOT_PRESSED,
            F.UNAUTHORISED, F.UNAUTHORISED_V1, F.EVENTS,
        )
        val shapes = answers.joinToString("\n", transform = HueJson::redact)
        listOf("Lounge", "Kitchen spot", "\"Relax\"", "\"Bright\"", "Evening", "Mimic presence", "Hall dimmer", "test-key", F.KEY, "0123456789ABCDEF")
            .forEach { assertThat(shapes).doesNotContain(it) }
        listOf(F.BRIDGE_ID, "ecb5fafffe9a1b2c", "192.168.", "255.255", "00:17:88").forEach {
            assertThat(shapes.lowercase()).doesNotContain(it)
        }
        listOf(F.LOUNGE, F.LOUNGE_GROUP, F.RELAX, F.PM_STOPPED, F.PM_SCRIPT, F.LOUNGE_LAMP_DEVICE).forEach {
            assertThat(shapes).contains(it)
        }

        listOf("living_room", "\"kitchen\"", "\"garden\"", "table_shade", "recessed_ceiling").forEach { assertThat(shapes).doesNotContain(it) }
        assertThat(HueJson.redact("""{"metadata":{"name":"Utility","archetype":"laundry_room"}}""")).isEqualTo("""{"metadata":{"name":"[name]","archetype":"[kind]"}}""")

        val config = HueJson.redact(F.CONFIG)
        assertThat(config).contains("\"replacesbridgeid\":\"[bridge id]\"")
        assertThat(config).contains("\"proxyaddress\":\"[address]\"")
        assertThat(config).contains("\"whitelist\":{}")
        assertThat(config).doesNotContain("culvery#tablet")
        assertThat(config).contains("\"apiversion\":\"1.78.0\"")
        assertThat(HueJson.config(tree(config))).isEqualTo("[bridge id]")

        val inside = HueJson.redact("""{"description":"Hue Bridge ECB5FAFFFE9A1B2C at 10.0.0.7 was not found"}""")
        assertThat(inside).doesNotContain("ECB5FAFFFE9A1B2C")
        assertThat(inside).doesNotContain("10.0.0.7")
        assertThat(inside).contains("was not found")

        assertThat(HueJson.rooms(tree(HueJson.redact(F.ROOMS))).map { it.name }).containsExactly("[name]", "[name]", "[name]")
        assertThat(HueJson.groupedLights(tree(HueJson.redact(F.GROUPED_LIGHTS)))).hasSize(4)
        assertThat(HueJson.lights(tree(HueJson.redact(F.LIGHTS)))).hasSize(5)
        assertThat(HueJson.scenes(tree(HueJson.redact(F.SCENES)))).hasSize(2)
        assertThat(HueJson.behaviors(tree(HueJson.redact(F.BEHAVIORS))).map { it.on }).containsExactly(false, true, true, false, true, true).inOrder()
        assertThat(HueJson.pair(tree(HueJson.redact(F.PAIRED)))).isEqualTo("[key]")
        assertThat(HueJson.sse(HueJson.redact(F.EVENTS).lineSequence()).flatMap { HueJson.events(it).asSequence() }.toList()).hasSize(3)
    }
}
