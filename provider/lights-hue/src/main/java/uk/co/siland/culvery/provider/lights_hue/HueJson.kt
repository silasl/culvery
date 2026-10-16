package uk.co.siland.culvery.provider.lights_hue

import java.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** An answer that isn't the shape asked for. No cause and no text: either could quote the answer. */
internal class HueJsonException : IOException("A Hue Bridge answer couldn't be read")

internal data class HueRoom(val id: String, val name: String, val deviceIds: List<String>, val groupedLightId: String)

/** [ownerType] is `room` or `bridge_home`; [brightness] is 0 to 100, null when the group has none. */
internal data class HueGroupedLight(val id: String, val ownerId: String, val ownerType: String, val on: Boolean, val brightness: Double?)

internal data class HueLight(val id: String, val deviceId: String, val dimmable: Boolean)

internal data class HueScene(val id: String, val name: String, val roomId: String, val active: Boolean)

/**
 * A behavior_instance. Mimic presence stays `enabled` and `running` whether or not it is on; it is recognised by
 * `state.pm_state` being present, which no other instance has, and is on when that reads `started`. Any other
 * automation is on when `enabled`, whatever its `status` says.
 */
internal data class HueBehavior(val id: String, val name: String?, val scriptId: String, val enabled: Boolean, val pmState: String?) {
    val isMimic: Boolean get() = pmState != null
    val on: Boolean get() = if (isMimic) pmState == "started" else enabled
}

internal data class HueConfig(val bridgeId: String)

internal sealed interface PairAnswer {
    data class Paired(val key: String) : PairAnswer

    data object ButtonNotPressed : PairAnswer

    data class Failed(val type: Int?) : PairAnswer
}

/** What the stream says changed; a null [GroupedLight.on] or [GroupedLight.brightness] was not in the update. */
internal sealed interface HueEvent {
    data class GroupedLight(val id: String, val on: Boolean?, val brightness: Double?) : HueEvent

    data class Scene(val id: String, val active: Boolean) : HueEvent

    data object Reread : HueEvent
}

@Serializable
private class Page<T>(val data: List<T>)

@Serializable
private class Ref(val rid: String, val rtype: String)

@Serializable
private class Named(val name: String? = null)

@Serializable
private class OnState(val on: Boolean)

@Serializable
private class Dimming(val brightness: Double? = null)

@Serializable
private class SceneStatus(val active: String)

@Serializable
private class RoomWire(val id: String, val children: List<Ref>, val services: List<Ref>, val metadata: Named)

@Serializable
private class GroupedLightWire(val id: String, val owner: Ref, val on: OnState? = null, val dimming: Dimming? = null)

@Serializable
private class LightWire(val id: String, val owner: Ref, val dimming: JsonElement? = null)

@Serializable
private class SceneWire(val id: String, val metadata: Named, val group: Ref, val status: SceneStatus? = null)

@Serializable
private class BehaviorWire(
    val id: String,
    @SerialName("script_id") val scriptId: String,
    val enabled: Boolean,
    val state: JsonObject? = null,
    val metadata: Named? = null,
)

@Serializable
private class BridgeWire(@SerialName("bridge_id") val bridgeId: String)

@Serializable
private class ConfigWire(@SerialName("bridgeid") val bridgeId: String)

@Serializable
private class PairWire(val success: PairSuccess? = null, val error: PairError? = null)

@Serializable
private class PairSuccess(val username: String)

@Serializable
private class PairError(val type: Int? = null)

@Serializable
private class ChangeSet(val type: String, val data: List<Change> = emptyList())

@Serializable
private class Change(
    val id: String = "",
    val type: String = "",
    val on: OnState? = null,
    val dimming: Dimming? = null,
    val status: SceneStatus? = null,
)

/** The Hue Bridge's CLIP v2 answers and the bodies sent to it (7 §3.2). Plain JVM: no Android, so its tests need no emulator. */
internal object HueJson {
    private val JSON = Json { ignoreUnknownKeys = true }

    private const val PM_STATE = "pm_state"
    private const val BUTTON_NOT_PRESSED = 101
    private const val NOT_AUTHORISED = 1

    fun rooms(text: String): List<HueRoom> = page<RoomWire>(text).mapNotNull { room ->
        val group = room.services.firstOrNull { it.rtype == "grouped_light" } ?: return@mapNotNull null
        HueRoom(room.id, room.metadata.name ?: throw HueJsonException(), room.children.filter { it.rtype == "device" }.map { it.rid }, group.rid)
    }

    fun groupedLights(text: String): List<HueGroupedLight> = page<GroupedLightWire>(text).map {
        HueGroupedLight(it.id, it.owner.rid, it.owner.rtype, it.on?.on ?: false, it.dimming?.brightness)
    }

    fun lights(text: String): List<HueLight> =
        page<LightWire>(text).map { HueLight(it.id, it.owner.rid, it.dimming != null && it.dimming !is JsonNull) }

    fun scenes(text: String): List<HueScene> = page<SceneWire>(text).map {
        HueScene(it.id, it.metadata.name ?: throw HueJsonException(), it.group.rid, it.status.isActive())
    }

    fun behaviors(text: String): List<HueBehavior> = page<BehaviorWire>(text).map {
        val pmState = (it.state?.get(PM_STATE) as? JsonPrimitive)?.takeIf { value -> value.isString }?.content
        HueBehavior(it.id, it.metadata?.name, it.scriptId, it.enabled, pmState)
    }

    /** The bridge id from the `bridge` resource, lower case. */
    fun bridgeId(text: String): String = page<BridgeWire>(text).firstOrNull()?.bridgeId?.lowercase() ?: throw HueJsonException()

    /** The bridge id from `/api/config`, lower case. */
    fun config(text: String): HueConfig = HueConfig(decode<ConfigWire>(text).bridgeId.lowercase())

    fun pair(text: String): PairAnswer {
        val first = decode<List<PairWire>>(text).firstOrNull() ?: throw HueJsonException()
        first.success?.let { return PairAnswer.Paired(it.username) }
        val error = first.error ?: throw HueJsonException()
        return if (error.type == BUTTON_NOT_PRESSED) PairAnswer.ButtonNotPressed else PairAnswer.Failed(error.type)
    }

    /** True for a v1 error of type 1 or a v2 error saying unauthorized; false for anything else, unparseable text included. */
    fun isUnauthorised(text: String): Boolean {
        val root = try {
            JSON.parseToJsonElement(text)
        } catch (e: SerializationException) {
            return false
        }
        return when (root) {
            is JsonArray -> root.any { ((it as? JsonObject)?.get("error") as? JsonObject)?.get("type").asInt() == NOT_AUTHORISED }
            is JsonObject -> (root["errors"] as? JsonArray).orEmpty().any {
                ((it as? JsonObject)?.get("description") as? JsonPrimitive)?.takeIf { d -> d.isString }?.content
                    ?.contains("unauthorized", ignoreCase = true) == true
            }
            else -> false
        }
    }

    private fun JsonElement?.asInt(): Int? = (this as? JsonPrimitive)?.intOrNull

    /** The `data:` payloads of a server-sent stream, one per line; comment, `id:` and other lines are skipped. */
    fun sse(lines: Sequence<String>): Sequence<String> =
        lines.filter { it.startsWith("data:") }.map { it.removePrefix("data:").removePrefix(" ") }.filter { it.isNotBlank() }

    /** The changes in one stream payload; an add, a delete or a room update asks for a full read, once. */
    fun events(data: String): List<HueEvent> {
        val events = mutableListOf<HueEvent>()
        for (set in decode<List<ChangeSet>>(data)) {
            if (set.type == "add" || set.type == "delete") {
                events.add(HueEvent.Reread)
                continue
            }
            if (set.type != "update") continue
            for (change in set.data) {
                when (change.type) {
                    "grouped_light" -> if (change.on != null || change.dimming?.brightness != null) {
                        events.add(HueEvent.GroupedLight(change.id, change.on?.on, change.dimming?.brightness))
                    }
                    "scene" -> change.status?.let { events.add(HueEvent.Scene(change.id, it.isActive())) }
                    "room" -> events.add(HueEvent.Reread)
                }
            }
        }
        return events.distinct()
    }

    fun onBody(on: Boolean): String = buildJsonObject { putJsonObject("on") { put("on", on) } }.toString()

    /** [turnOn] puts `on` in the same request, as dimming alone doesn't switch lights on. */
    fun brightnessBody(percent: Int, turnOn: Boolean): String = buildJsonObject {
        if (turnOn) putJsonObject("on") { put("on", true) }
        putJsonObject("dimming") { put("brightness", percent) }
    }.toString()

    fun recallBody(): String = buildJsonObject { putJsonObject("recall") { put("action", "active") } }.toString()

    fun enabledBody(enabled: Boolean): String = buildJsonObject { put("enabled", enabled) }.toString()

    fun triggerBody(start: Boolean): String =
        buildJsonObject { putJsonObject("trigger") { put(if (start) "start" else "stop", true) } }.toString()

    fun pairBody(): String = buildJsonObject {
        put("devicetype", "culvery#tablet")
        put("generateclientkey", true)
    }.toString()

    private val VALUE_KEYS = listOf(
        "name", "username", "clientkey", "bridgeid", "bridge_id", "replacesbridgeid", "mac", "mac_address", "ipaddress",
        "gateway", "netmask", "proxyaddress",
    ).joinToString("|")
    private val KEYED_VALUE = Regex("\"($VALUE_KEYS)\"\\s*:\\s*\"(?:[^\"\\\\]|\\\\.)*\"")
    private val MAC = Regex("""(?:[0-9A-Fa-f]{2}:){5,7}[0-9A-Fa-f]{2}""")
    private val BRIDGE_ID_SHAPE = Regex("""(?<![0-9A-Za-z])[0-9A-Fa-f]{16}(?![0-9A-Za-z])""")
    private val IPV4 = Regex("""\b\d{1,3}(?:\.\d{1,3}){3}\b""")

    private val PLACEHOLDERS = mapOf(
        "name" to "[name]", "username" to "[key]", "clientkey" to "[key]", "bridgeid" to "[bridge id]",
        "bridge_id" to "[bridge id]", "replacesbridgeid" to "[bridge id]", "mac" to "[mac]", "mac_address" to "[mac]",
    )

    /**
     * An answer's shape for a capture (7 §8.2): the values of the keys that name a person's things, the bridge, the key or
     * a network address replaced, and by shape anywhere a bridge id, a MAC address or an IPv4 address. Resource ids stay.
     */
    fun redact(text: String): String = text
        .replace(KEYED_VALUE) { "\"${it.groupValues[1]}\":\"${PLACEHOLDERS[it.groupValues[1]] ?: "[address]"}\"" }
        .replace(MAC, "[mac]")
        .replace(BRIDGE_ID_SHAPE, "[bridge id]")
        .replace(IPV4, "[address]")

    private inline fun <reified T> page(text: String): List<T> = decode<Page<T>>(text).data

    private fun SceneStatus?.isActive() = this != null && active != "inactive"

    private inline fun <reified T> decode(text: String): T = try {
        JSON.decodeFromString<T>(text)
    } catch (e: SerializationException) {
        throw HueJsonException()
    } catch (e: IllegalArgumentException) {
        throw HueJsonException()
    }
}
