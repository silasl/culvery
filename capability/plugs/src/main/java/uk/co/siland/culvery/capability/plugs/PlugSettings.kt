package uk.co.siland.culvery.capability.plugs

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.core.plugin.retryWithBackoff
import uk.co.siland.culvery.core.plugin.settingsDataStore

private const val FILE = "plug_settings"
private const val TAG = "PlugSettings"

private val PLUG_ID = Regex("^switch\\.[a-z0-9_]+$")
private val MODE_ID = Regex("^[a-z]+$")

internal fun isPlugId(text: String) = PLUG_ID.matches(text)

internal fun isModeId(text: String) = MODE_ID.matches(text)

enum class ModeChoice { LEAVE, ON, OFF }

/** A mode that switched the plug it is noted on, to [on]. [toString] leaves out the mode. */
data class Holder(val mode: String, val on: Boolean) {
    override fun toString() = "Holder"
}

/** How a plug was before the first mode that touched it ([before]), and the modes holding it, in the order they began. */
data class PlugNote(val before: Boolean, val holders: List<Holder>) {
    /** The newest holder's setting, else [before] (12 §2.12). */
    val expected: Boolean get() = holders.lastOrNull()?.on ?: before

    override fun toString() = "PlugNote"
}

/** A begun mode: [place] orders it among the others, [pending] are the plugs it has yet to reach. [toString] leaves out the plugs. */
data class ModeRun(val place: Long, val pending: Map<String, Boolean>) {
    override fun toString() = "ModeRun"
}

/** Everything the Plugs tab and the modes remember (12 §5.2). [toString] leaves out every id. */
data class PlugPrefs(
    val plugs: List<String> = emptyList(),
    val needsPin: Set<String> = emptySet(),
    val modes: Map<String, Map<String, Boolean>> = emptyMap(),
    val runs: Map<String, ModeRun> = emptyMap(),
    val notes: Map<String, PlugNote> = emptyMap(),
    val next: Long = 0,
) {
    fun choice(modeId: String, id: String): ModeChoice = when (modes[modeId]?.get(id)) {
        true -> ModeChoice.ON
        false -> ModeChoice.OFF
        null -> ModeChoice.LEAVE
    }

    fun with(modeId: String, id: String, choice: ModeChoice): PlugPrefs {
        if (!isModeId(modeId) || !isPlugId(id)) return this
        val list = modes[modeId].orEmpty().let {
            when (choice) {
                ModeChoice.LEAVE -> it - id
                ModeChoice.ON -> it + (id to true)
                ModeChoice.OFF -> it + (id to false)
            }
        }
        return copy(modes = if (list.isEmpty()) modes - modeId else modes + (modeId to list))
    }

    fun adding(id: String) = if (!isPlugId(id) || id in plugs) this else copy(plugs = plugs + id)

    /** The plug itself is left as it is; a begun mode stays begun with one plug fewer to reach. */
    fun without(id: String) = copy(
        plugs = plugs - id,
        needsPin = needsPin - id,
        modes = modes.mapValues { (_, list) -> list - id }.filterValues { it.isNotEmpty() },
        runs = runs.mapValues { (_, run) -> run.copy(pending = run.pending - id) },
        notes = notes - id,
    )

    override fun toString() = "PlugPrefs"
}

/** A small DataStore file under `files/datastore/`, which backup and transfer already leave alone. Writes throw `IOException`. */
@Singleton
class PlugSettings(private val store: DataStore<Preferences>) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(settingsDataStore { context.preferencesDataStoreFile(FILE) })

    val prefs: Flow<PlugPrefs> = store.data
        .retryWithBackoff { Log.w(TAG, "Couldn't read the plug settings (${it::class.simpleName}); retrying") }
        .map { it.toPlugPrefs() }
        .distinctUntilChanged()

    /** One transform in one edit, written whole over a cleared file, so removed keys go and no older copy comes back. */
    suspend fun update(change: (PlugPrefs) -> PlugPrefs) {
        store.edit { it.write(change(it.toPlugPrefs())) }
    }

    private fun Preferences.toPlugPrefs(): PlugPrefs {
        val values = asMap().mapKeys { it.key.name }
        fun entries(name: String) = (values[name] as? Set<*>).orEmpty().filterIsInstance<String>().mapNotNull(::parseEntry).toMap()
        fun named(prefix: String) = values.keys.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }

        return PlugPrefs(
            plugs = (values[PLUGS.name] as? String).orEmpty().split(',').filter(::isPlugId).distinct(),
            needsPin = (values[PIN.name] as? Set<*>).orEmpty().filterIsInstance<String>().filter(::isPlugId).toSet(),
            modes = named("mode.").filter(::isModeId).associateWith { entries("mode.$it") }.filterValues { it.isNotEmpty() },
            runs = named("started.").filter(::isModeId).mapNotNull { mode ->
                (values["started.$mode"] as? Long)?.let { mode to ModeRun(it, entries("pending.$mode")) }
            }.toMap(),
            notes = named("note.").filter(::isPlugId).mapNotNull { id ->
                parseNote(values["note.$id"])?.let { id to it }
            }.toMap(),
            next = values[NEXT.name] as? Long ?: 0,
        )
    }

    private fun MutablePreferences.write(prefs: PlugPrefs) {
        clear()
        if (prefs.plugs.isNotEmpty()) this[PLUGS] = prefs.plugs.joinToString(",")
        if (prefs.needsPin.isNotEmpty()) this[PIN] = prefs.needsPin
        prefs.modes.forEach { (mode, list) -> if (list.isNotEmpty()) this[stringSetPreferencesKey("mode.$mode")] = list.toEntries() }
        prefs.runs.forEach { (mode, run) ->
            this[longPreferencesKey("started.$mode")] = run.place
            if (run.pending.isNotEmpty()) this[stringSetPreferencesKey("pending.$mode")] = run.pending.toEntries()
        }
        prefs.notes.forEach { (id, note) ->
            if (note.holders.isNotEmpty()) {
                this[stringPreferencesKey("note.$id")] = "${word(note.before)};${note.holders.joinToString(",") { "${it.mode}=${word(it.on)}" }}"
            }
        }
        if (prefs.next != 0L) this[NEXT] = prefs.next
    }

    private fun Map<String, Boolean>.toEntries() = map { (id, on) -> "$id=${word(on)}" }.toSet()

    private fun word(on: Boolean) = if (on) "on" else "off"

    private fun parseWord(text: String?) = when (text) {
        "on" -> true
        "off" -> false
        else -> null
    }

    /** "switch.lamp=on"; anything else is skipped. */
    private fun parseEntry(text: String): Pair<String, Boolean>? {
        val (id, word) = text.split('=').takeIf { it.size == 2 } ?: return null
        return if (isPlugId(id)) parseWord(word)?.let { id to it } else null
    }

    /** "off;holiday=on,party=off"; a note with a bad part, or no holder, is skipped whole. */
    private fun parseNote(value: Any?): PlugNote? {
        val (before, rest) = (value as? String)?.split(';')?.takeIf { it.size == 2 } ?: return null
        val holders = rest.split(',').map { part ->
            val (mode, word) = part.split('=').takeIf { it.size == 2 } ?: return null
            if (!isModeId(mode)) return null
            Holder(mode, parseWord(word) ?: return null)
        }
        return PlugNote(parseWord(before) ?: return null, holders)
    }

    private companion object {
        val PLUGS = stringPreferencesKey("plugs")
        val PIN = stringSetPreferencesKey("pin")
        val NEXT = longPreferencesKey("next")
    }
}
