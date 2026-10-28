package uk.co.siland.culvery.core.homeassistant

import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** An answer that isn't the shape asked for. No cause and no text: either could quote the answer. */
internal class HaJsonException : IOException("A Home Assistant answer couldn't be read")

/**
 * One entity as Home Assistant reports it: its attributes split by kind, any other kind dropped. [toString] names the
 * domain and the state only; the id and the name are the household's.
 */
data class EntityState(
    val entityId: String,
    val state: String,
    val texts: Map<String, String>,
    val numbers: Map<String, Double>,
) {
    val friendlyName: String? get() = texts["friendly_name"]
    val domain: String get() = entityId.substringBefore('.')

    override fun toString() = "EntityState(domain=$domain, state=$state)"
}

/** What a socket event changed about one entity: a null [state] is unchanged. */
internal data class EntityChange(
    val state: String?,
    val texts: Map<String, String>,
    val numbers: Map<String, Double>,
    val removedAttributes: Set<String>,
) {
    override fun toString() = "EntityChange"
}

/** [change] applied: the attributes it doesn't name stay, the ones it removes go. */
internal fun EntityState.apply(change: EntityChange): EntityState = EntityState(
    entityId,
    change.state ?: state,
    (texts - change.numbers.keys + change.texts) - change.removedAttributes,
    (numbers - change.texts.keys + change.numbers) - change.removedAttributes,
)

/**
 * One entry of Home Assistant's entity registry (12 §4.1). [toString] names the platform only; the entity, its device and
 * its category are the household's.
 */
data class RegistryEntry(
    val entityId: String,
    val platform: String,
    val deviceId: String?,
    val category: String?,
    val translationKey: String?,
) {
    override fun toString() = "RegistryEntry(platform=$platform)"
}

val RegistryEntry.domain: String get() = entityId.substringBefore('.')

/** A message on the WebSocket; [toString] names the type only. */
internal sealed interface SocketMessage {
    data object AuthRequired : SocketMessage

    data object AuthOk : SocketMessage

    data object AuthInvalid : SocketMessage

    data object Other : SocketMessage

    data class Result(val id: Int, val success: Boolean) : SocketMessage {
        override fun toString() = "Result"
    }

    /** A `subscribe_entities` event: the entities [added] whole, the [changed] ones in part, and the [removed] ids. */
    data class Entities(
        val id: Int,
        val added: Map<String, EntityState>,
        val changed: Map<String, EntityChange>,
        val removed: Set<String>,
    ) : SocketMessage {
        override fun toString() = "Entities"
    }
}

/** Reads what Home Assistant answers over REST and the socket (11 §4.2, §4.3) and redacts it for the debug capture (11 §8.2). */
internal object HaJson {
    /** Whether [text] is Home Assistant's `GET /api/` answer; anything else, even unreadable, is not. */
    fun apiRunning(text: String): Boolean = try {
        Json.parseToJsonElement(text).jsonObject["message"]?.jsonPrimitive?.takeIf { it.isString }?.content == "API running."
    } catch (_: Exception) {
        false
    }

    fun states(text: String): List<EntityState> = read { Json.parseToJsonElement(text).jsonArray.map { entity(it.jsonObject) } }

    fun state(text: String): EntityState = read { entity(Json.parseToJsonElement(text).jsonObject) }

    /** The `entities` of a `config/entity_registry/list_for_display` result, each `ec` mapped through the message's own categories. */
    fun registry(text: String): List<RegistryEntry> = read {
        val result = Json.parseToJsonElement(text).jsonObject.getValue("result").jsonObject
        val categories = result["entity_categories"]?.jsonObject.orEmpty().mapValues { it.value.jsonPrimitive.content }
        result.getValue("entities").jsonArray.map { it.jsonObject }.map { body ->
            RegistryEntry(
                entityId = body.getValue("ei").jsonPrimitive.content,
                platform = body.getValue("pl").jsonPrimitive.content,
                deviceId = body["di"]?.jsonPrimitive?.contentOrNull,
                category = body["ec"]?.jsonPrimitive?.intOrNull?.let { categories[it.toString()] },
                translationKey = body["tk"]?.jsonPrimitive?.contentOrNull,
            )
        }
    }

    fun socket(text: String): SocketMessage = read {
        val message = Json.parseToJsonElement(text).jsonObject
        when (message["type"]?.jsonPrimitive?.content) {
            "auth_required" -> SocketMessage.AuthRequired
            "auth_ok" -> SocketMessage.AuthOk
            "auth_invalid" -> SocketMessage.AuthInvalid
            "result" -> SocketMessage.Result(message.getValue("id").jsonPrimitive.int, message.getValue("success").jsonPrimitive.boolean)
            "event" -> entities(message)
            else -> SocketMessage.Other
        }
    }

    private fun entities(message: JsonObject): SocketMessage {
        val event = message.getValue("event").jsonObject
        val added = event["a"]?.jsonObject.orEmpty().mapValues { (id, value) ->
            val body = value.jsonObject
            val (texts, numbers) = attributes(body["a"]?.jsonObject)
            EntityState(id, body.getValue("s").jsonPrimitive.content, texts, numbers)
        }
        val changed = event["c"]?.jsonObject.orEmpty().mapValues { (_, value) ->
            val body = value.jsonObject
            val plus = body["+"]?.jsonObject
            val set = plus?.get("a")?.jsonObject
            val (texts, numbers) = attributes(set)
            // An attribute set to null, a flag or a list is absent from a full read, so it must leave the cache too.
            val unreadable = set.orEmpty().keys - texts.keys - numbers.keys
            val gone = body["-"]?.jsonObject?.get("a")?.jsonArray.orEmpty().map { it.jsonPrimitive.content }.toSet() + unreadable
            EntityChange(plus?.get("s")?.jsonPrimitive?.content, texts, numbers, gone)
        }
        val removed = event["r"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }.toSet()
        return SocketMessage.Entities(message.getValue("id").jsonPrimitive.int, added, changed, removed)
    }

    private fun entity(body: JsonObject): EntityState {
        val (texts, numbers) = attributes(body["attributes"]?.jsonObject)
        return EntityState(body.getValue("entity_id").jsonPrimitive.content, body.getValue("state").jsonPrimitive.content, texts, numbers)
    }

    /** Text and number attributes; booleans, lists, objects and nulls are dropped. */
    private fun attributes(source: JsonObject?): Pair<Map<String, String>, Map<String, Double>> {
        val texts = mutableMapOf<String, String>()
        val numbers = mutableMapOf<String, Double>()
        source?.forEach { (name, value) ->
            if (value is JsonPrimitive && value !is JsonNull) {
                if (value.isString) texts[name] = value.content else value.doubleOrNull?.let { numbers[name] = it }
            }
        }
        return texts to numbers
    }

    private fun <T> read(block: () -> T): T = try {
        block()
    } catch (_: Exception) {
        throw HaJsonException()
    }

    /**
     * [text] with only what the capture may show (11 §8.2): entities not in [keep] are dropped, kept ones become
     * `<domain>.[n]`, text survives only under the keys that say a state, a mode or a unit, and a number only under the
     * temperature keys, a message id and a kept sensor's state; every other number is 0. The state of a sensor whose
     * `device_class` is `power` or `energy` is 0 as well (12 §6): its id goes into [blanked], which the caller keeps across
     * messages, since a change carries no class.
     */
    fun redact(text: String, keep: Set<String>, blanked: MutableSet<String> = mutableSetOf()): String {
        val element = try {
            Json.parseToJsonElement(text)
        } catch (_: Exception) {
            return UNREADABLE
        }
        learn(element, id = null, blanked)
        return Redactor(keep, blanked).value(element, key = null, domain = null, id = null)?.toString() ?: EMPTY
    }

    private fun entityIdOf(element: JsonObject): String? = (element["entity_id"] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private val BLANKED_CLASSES = setOf("power", "energy")

    /** Adds to [blanked] every entity under [element] that says it is a power or energy sensor. */
    private fun learn(element: JsonElement, id: String?, blanked: MutableSet<String>) {
        when (element) {
            is JsonObject -> {
                val own = entityIdOf(element) ?: id
                val reading = (element["device_class"] as? JsonPrimitive)?.takeIf { it.isString }?.content in BLANKED_CLASSES
                if (reading && own != null) blanked += own
                element.forEach { (name, child) -> learn(child, if ('.' in name) name else own, blanked) }
            }
            is JsonArray -> element.forEach { learn(it, id, blanked) }
            else -> Unit
        }
    }

    private const val UNREADABLE = "[unreadable]"
    private const val EMPTY = "{}"
    private val KEEP_TEXT = setOf(
        "state", "s", "preset_mode", "preset_modes", "hvac_action", "hvac_mode", "hvac_modes",
        "device_class", "unit_of_measurement", "type", "success",
    )
    private val KEEP_NUMBER = setOf("current_temperature", "temperature", "target_temp_low", "target_temp_high", "id")

    private class Redactor(keep: Set<String>, private val blanked: Set<String>) {
        private val alias: Map<String, String> = keep.groupBy { it.substringBefore('.') }.flatMap { (domain, ids) ->
            ids.sorted().mapIndexed { i, id -> id to "$domain.[${i + 1}]" }
        }.toMap()

        /** The redacted [element] under [key] in entity [id], or null when it is an entity that isn't kept. */
        fun value(element: JsonElement, key: String?, domain: String?, id: String?): JsonElement? = when (element) {
            is JsonObject -> obj(element, domain, id)
            is JsonArray -> JsonArray(element.mapNotNull { value(it, key, domain, id) })
            is JsonNull -> element
            is JsonPrimitive -> primitive(element, key, domain, id)
        }

        private fun obj(element: JsonObject, domain: String?, id: String?): JsonElement? {
            val own = entityIdOf(element)
            if (own != null && own !in alias) return null
            val here = own?.substringBefore('.') ?: domain
            val entity = own ?: id
            val out = LinkedHashMap<String, JsonElement>()
            for ((name, child) in element) {
                if ('.' in name) {
                    val shown = alias[name] ?: continue
                    value(child, name, name.substringBefore('.'), name)?.let { out[shown] = it }
                } else {
                    value(child, name, here, entity)?.let { out[name] = it }
                }
            }
            return JsonObject(out)
        }

        private fun primitive(element: JsonPrimitive, key: String?, domain: String?, id: String?): JsonElement? {
            if ((key == "state" || key == "s") && id in blanked) return JsonPrimitive(0)
            if (element.isString) {
                alias[element.content]?.let { return JsonPrimitive(it) }
                // An id in a removal list that isn't kept is dropped, not masked.
                if (key == "r") return null
                return if (key in KEEP_TEXT) element else JsonPrimitive("[s]")
            }
            if (element.doubleOrNull == null) return element
            val shown = key in KEEP_NUMBER || (domain == "sensor" && (key == "state" || key == "s"))
            return if (shown) element else JsonPrimitive(0)
        }
    }
}
