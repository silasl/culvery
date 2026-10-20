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
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** An answer that isn't the shape asked for. No cause and no text: either could quote the answer. */
internal class HueJsonException : IOException("A Hue Bridge answer couldn't be read")

internal data class HueRoom(val id: String, val name: String, val deviceIds: List<String>, val groupedLightId: String)

/** [ownerType] is `room` or `bridge_home`; [brightness] is 0 to 100, null when the group has none. */
internal data class HueGroupedLight(val id: String, val ownerId: String, val ownerType: String, val on: Boolean, val brightness: Double?)

/** [brightness] is the light's own level, 0 to 100, which it keeps while off; null when it doesn't dim. */
internal data class HueLight(val id: String, val deviceId: String, val dimmable: Boolean, val brightness: Double?)

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
private class ConfigWire(@SerialName("bridgeid") val bridgeId: String)

@Serializable
private class PairWire(val success: PairSuccess? = null, val error: JsonObject? = null)

@Serializable
private class PairSuccess(val username: String)

@Serializable
private class ChangeSet(val type: String, val data: List<Change> = emptyList())

@Serializable
private class Change(
    val id: String = "",
    val type: String = "",
    val on: OnState? = null,
    val dimming: Dimming? = null,
    val status: JsonElement? = null,
)

/**
 * The Hue Bridge's CLIP v2 answers and the bodies sent to it (7 §3.2). An answer is parsed once, by [tree], and each
 * reader takes that tree, null when the answer wasn't JSON. Plain JVM: no Android, so its tests need no emulator.
 */
internal object HueJson {
    private val JSON = Json { ignoreUnknownKeys = true }

    private const val PM_STATE = "pm_state"
    private const val NOT_AUTHORISED = 1

    /** [text] as JSON; null when it isn't. */
    fun tree(text: String): JsonElement? = try {
        JSON.parseToJsonElement(text)
    } catch (e: SerializationException) {
        null
    }

    fun rooms(root: JsonElement?): List<HueRoom> = page<RoomWire>(root).mapNotNull { room ->
        val group = room.services.firstOrNull { it.rtype == "grouped_light" } ?: return@mapNotNull null
        HueRoom(room.id, room.metadata.name ?: throw HueJsonException(), room.children.filter { it.rtype == "device" }.map { it.rid }, group.rid)
    }

    fun groupedLights(root: JsonElement?): List<HueGroupedLight> = page<GroupedLightWire>(root).map {
        HueGroupedLight(it.id, it.owner.rid, it.owner.rtype, it.on?.on ?: false, it.dimming?.brightness)
    }

    fun lights(root: JsonElement?): List<HueLight> =
        page<LightWire>(root).map {
            val level = ((it.dimming as? JsonObject)?.get("brightness") as? JsonPrimitive)?.doubleOrNull
            HueLight(it.id, it.owner.rid, it.dimming != null && it.dimming !is JsonNull, level)
        }

    fun scenes(root: JsonElement?): List<HueScene> = page<SceneWire>(root).map {
        HueScene(it.id, it.metadata.name ?: throw HueJsonException(), it.group.rid, it.status.isActive())
    }

    fun behaviors(root: JsonElement?): List<HueBehavior> = page<BehaviorWire>(root).map {
        val pmState = (it.state?.get(PM_STATE) as? JsonPrimitive)?.takeIf { value -> value.isString }?.content
        HueBehavior(it.id, it.metadata?.name, it.scriptId, it.enabled, pmState)
    }

    /** The bridge id from `/api/config`, lower case. */
    fun config(root: JsonElement?): String = decode<ConfigWire>(root).bridgeId.lowercase()

    /** The application key; null when the bridge answered with an error, such as its button not pressed. */
    fun pair(root: JsonElement?): String? {
        val first = decode<List<PairWire>>(root).firstOrNull() ?: throw HueJsonException()
        first.success?.let { return it.username }
        return if (first.error != null) null else throw HueJsonException()
    }

    /** True for a v1 error of type 1 or a v2 error saying unauthorized; false for anything else, an answer that isn't JSON included. */
    fun isUnauthorised(root: JsonElement?): Boolean = when (root) {
        is JsonArray -> root.any { ((it as? JsonObject)?.get("error") as? JsonObject)?.get("type").asInt() == NOT_AUTHORISED }
        is JsonObject -> (root["errors"] as? JsonArray).orEmpty().any {
            ((it as? JsonObject)?.get("description") as? JsonPrimitive)?.takeIf { d -> d.isString }?.content
                ?.contains("unauthorized", ignoreCase = true) == true
        }
        else -> false
    }

    /** True when a CLIP v2 answer lists any error, whatever its status: the bridge didn't do all it was asked. */
    fun hasErrors(root: JsonElement?): Boolean = ((root as? JsonObject)?.get("errors") as? JsonArray).orEmpty().isNotEmpty()

    private fun JsonElement?.asInt(): Int? = (this as? JsonPrimitive)?.intOrNull

    /** The `data:` payloads of a server-sent stream, one per line; comment, `id:` and other lines are skipped. */
    fun sse(lines: Sequence<String>): Sequence<String> =
        lines.filter { it.startsWith("data:") }.map { it.removePrefix("data:").removePrefix(" ") }.filter { it.isNotBlank() }

    /** The changes in one stream payload; an add, a delete or a room update asks for a full read, once. */
    fun events(data: String): List<HueEvent> {
        val events = mutableListOf<HueEvent>()
        for (set in decode<List<ChangeSet>>(tree(data))) {
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
                    "scene" -> sceneActive(change.status)?.let { events.add(HueEvent.Scene(change.id, it)) }
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
        "gateway", "netmask", "proxyaddress", "archetype",
    ).joinToString("|")
    private val KEYED_VALUE = Regex("\"($VALUE_KEYS)\"\\s*:\\s*\"(?:[^\"\\\\]|\\\\.)*\"")
    // The bridge's whitelist is an object keyed by application keys.
    private val WHITELIST = Regex("\"whitelist\"\\s*:\\s*\\{(?:[^{}]|\\{[^{}]*\\})*\\}")
    private val MAC = Regex("""(?:[0-9A-Fa-f]{2}:){5,7}[0-9A-Fa-f]{2}""")
    private val BRIDGE_ID_SHAPE = Regex("""(?<![0-9A-Za-z])[0-9A-Fa-f]{16}(?![0-9A-Za-z])""")
    private val IPV4 = Regex("""\b\d{1,3}(?:\.\d{1,3}){3}\b""")

    private val PLACEHOLDERS = mapOf(
        "name" to "[name]", "username" to "[key]", "clientkey" to "[key]", "bridgeid" to "[bridge id]",
        "bridge_id" to "[bridge id]", "replacesbridgeid" to "[bridge id]", "mac" to "[mac]", "mac_address" to "[mac]",
        "archetype" to "[kind]",
    )

    /**
     * An answer's shape for a capture (7 §8.2): the values of the keys that name or describe a person's things (names, room and light kinds), the bridge, the key or
     * a network address replaced, and by shape anywhere a bridge id, a MAC address or an IPv4 address. Resource ids stay.
     */
    fun redact(text: String): String = text
        .replace(WHITELIST, "\"whitelist\":{}")
        .replace(KEYED_VALUE) { "\"${it.groupValues[1]}\":\"${PLACEHOLDERS[it.groupValues[1]] ?: "[address]"}\"" }
        .replace(MAC, "[mac]")
        .replace(BRIDGE_ID_SHAPE, "[bridge id]")
        .replace(IPV4, "[address]")

    private inline fun <reified T> page(root: JsonElement?): List<T> = decode<Page<T>>(root).data

    private fun SceneStatus?.isActive() = this != null && active != "inactive"

    /** Only a scene's `status` object with an `active` says anything; other resources send `status` as a word. */
    private fun sceneActive(status: JsonElement?): Boolean? =
        ((status as? JsonObject)?.get("active") as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { it != "inactive" }

    private inline fun <reified T> decode(root: JsonElement?): T = try {
        JSON.decodeFromJsonElement<T>(root ?: throw HueJsonException())
    } catch (e: SerializationException) {
        throw HueJsonException()
    } catch (e: IllegalArgumentException) {
        throw HueJsonException()
    }
}
