package uk.co.siland.culvery.capability.party

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import uk.co.siland.culvery.core.house.GroupBefore
import uk.co.siland.culvery.core.house.HouseRoom
import uk.co.siland.culvery.core.house.LightChoice
import uk.co.siland.culvery.core.house.PartyGroup
import uk.co.siland.culvery.core.house.RoomBefore
import uk.co.siland.culvery.core.house.RoomChoice
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.retryWithBackoff
import uk.co.siland.culvery.core.plugin.settingsDataStore

// Party mode's file since 13, widened in place (15b Ruling 5).
private const val FILE = "party_settings"
private const val TAG = "ModesStore"

/**
 * What the mode that is on changed (13 §4.3), one at a time; [modeId] null with none on. [since] is wall millis. Each
 * of [musicBefore], [made] and [lights] is null until its step has read. With no [made], [musicBefore] outlives [on]
 * until the speakers go back. [toString] leaves out all but [on].
 */
data class ModeRecord(
    val modeId: String? = null,
    val on: Boolean = false,
    val since: Long? = null,
    val musicBefore: List<GroupBefore>? = null,
    val made: PartyGroup? = null,
    val lights: List<RoomBefore>? = null,
) {
    override fun toString() = "ModeRecord(on=$on)"
}

/** The stored modes and the on mode's record, in a small DataStore file that backup and transfer leave alone. Writes throw `IOException`. */
@Singleton
class ModesStore(private val store: DataStore<Preferences>, scope: CoroutineScope) {
    @Inject
    constructor(@ApplicationContext context: Context, @ApplicationScope scope: CoroutineScope) :
        this(settingsDataStore(migrations = listOf(PartyMove)) { context.preferencesDataStoreFile(FILE) }, scope)

    private val data: Flow<Preferences> = store.data
        .retryWithBackoff { Log.w(TAG, "Couldn't read the modes settings (${it::class.simpleName}); retrying") }

    val prefs: Flow<ModesPrefs> = data.map { it.toModesPrefs() }.distinctUntilChanged()

    /** [prefs] as last read, for what can't wait for a read (a mode's name in another mode's refusal); null until then. */
    val prefsNow: StateFlow<ModesPrefs?> = prefs.stateIn(scope, SharingStarted.Eagerly, null)

    /** [prefsNow] once read, else the first read. */
    suspend fun prefsSoon(): ModesPrefs = prefsNow.value ?: prefs.first()

    /** Null until the file is first read, so nothing takes "off" for the saved answer. */
    val record: StateFlow<ModeRecord?> = data.map { it.toRecord() }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, null)

    /** Replaces the modes and Holiday's place on Home; a mode that needs a PIN still does, as only [setNeedsPin] turns that off. */
    suspend fun update(change: (ModesPrefs) -> ModesPrefs) {
        store.edit {
            val before = it.toModesPrefs()
            val next = change(before)
            val locked = before.modes.filter { m -> m.needsPin }.mapTo(HashSet()) { m -> m.id }
            it.writeModes(next.modes.map { m -> if (m.id in locked && !m.needsPin) m.copy(needsPin = true) else m })
            it[ModeKeys.HOLIDAY_HOME] = next.holidayOnHome
        }
    }

    /** Writes [id]'s "Needs a PIN" alone; nothing for a mode that isn't stored. The PIN is asked before this, never in it. */
    suspend fun setNeedsPin(id: String, on: Boolean) {
        store.edit {
            if (id in it.toModesPrefs().modes.map { m -> m.id }) it[ModeKeys.For(id).pin] = on
        }
    }

    /** Replaces the record only; the modes stay. */
    suspend fun save(record: ModeRecord) {
        store.edit {
            it.removeRecord()
            record.modeId?.let { id -> it[ModeKeys.RECORD_MODE] = id }
            it[ModeKeys.ON] = record.on
            record.since?.let { since -> it[ModeKeys.SINCE] = since }
            record.musicBefore?.let { before ->
                it[ModeKeys.MUSIC_BEFORE] = before.map { g -> joinGroup(g.coordinatorId, g.roomIds) }.toSet()
            }
            record.made?.let { made -> it[ModeKeys.MUSIC_MADE] = joinGroup(made.coordinatorId, made.roomIds) }
            record.lights?.let { lights ->
                it[ModeKeys.LIGHTS_BEFORE] = lights.map { r ->
                    ModeKeys.join(r.roomId, r.name, *ModeKeys.encoded(r.choice).recordFields(), r.on.toString(), r.brightness.toString(), r.activeSceneId.orEmpty())
                }.toSet()
            }
        }
    }

    suspend fun clearRecord() {
        store.edit { it.removeRecord() }
    }

    private fun MutablePreferences.removeRecord() {
        for (key in ModeKeys.RECORD_KEYS) remove(key)
    }

    private fun Preferences.toModesPrefs() = ModesPrefs(
        holidayOnHome = this[ModeKeys.HOLIDAY_HOME] ?: true,
        modes = this[ModeKeys.MODES].orEmpty().split(',').filter { it.isNotBlank() }.distinct().mapNotNull { readMode(it) },
    )

    /** Null for a mode listed without its name or icon; a missing flag reads as off Home and needing a PIN. */
    private fun Preferences.readMode(id: String): StoredMode? {
        val keys = ModeKeys.For(id)
        return StoredMode(
            id = id,
            name = this[keys.name] ?: return null,
            icon = this[keys.icon] ?: return null,
            showOnHome = this[keys.home] ?: false,
            needsPin = this[keys.pin] ?: true,
            music = this[keys.pick]?.let { pick -> this[keys.pickName]?.let { MusicChoice(pick, it) } },
            speakers = this[keys.rooms]?.mapNotNull(ModeKeys::parseRoom),
            lights = this[keys.lights].orEmpty().mapNotNull(ModeKeys::parseRoomChoice),
        )
    }

    private fun Preferences.toRecord() = ModeRecord(
        modeId = this[ModeKeys.RECORD_MODE],
        on = this[ModeKeys.ON] ?: false,
        since = this[ModeKeys.SINCE],
        musicBefore = this[ModeKeys.MUSIC_BEFORE]?.mapNotNull { text -> parseGroup(text)?.let { (id, rooms) -> GroupBefore(id, rooms) } },
        made = this[ModeKeys.MUSIC_MADE]?.let(::parseGroup)?.let { (id, rooms) -> PartyGroup(id, rooms.toSet()) },
        lights = this[ModeKeys.LIGHTS_BEFORE]?.mapNotNull(::parseRoomBefore),
    )

    private fun joinGroup(id: String, rooms: Collection<String>) = ModeKeys.join(id, rooms.joinToString(","))

    private fun parseGroup(text: String): Pair<String, List<String>>? {
        val f = ModeKeys.fields(text, 2) ?: return null
        return f[0] to f[1].split(',').filter { it.isNotEmpty() }
    }

    /** The record form is `roomId, name, kind, sceneId, percent, on, brightness, active`; a six-field entry from before 15b is a scene. */
    private fun parseRoomBefore(text: String): RoomBefore? {
        val f = text.split(ModeKeys.SEPARATOR)
        val choice = when (f.size) {
            6 -> LightChoice.Scene(f[2], "")
            8 -> ModeKeys.choiceOf(f[2], f[3], "", f[4])
            else -> null
        } ?: return null
        val (on, brightness, active) = f.takeLast(3)
        return RoomBefore(
            roomId = f[0],
            name = f[1],
            choice = choice,
            on = on.toBooleanStrictOrNull() ?: return null,
            brightness = brightness.toIntOrNull() ?: return null,
            activeSceneId = active.ifEmpty { null },
        )
    }
}

/** The file's keys and value forms, shared by [ModesStore] and [PartyMove]. */
internal object ModeKeys {
    /** No name holds it, so a value with several fields can be split again. */
    const val SEPARATOR = '\u001F'

    private const val KIND_SCENE = "scene"
    private const val KIND_ON = "on"
    private const val KIND_OFF = "off"
    const val MODE_PREFIX = "mode."

    /** The stored modes' ids in the order they were added, comma-joined; `""` with none, never absent once moved. */
    val MODES = stringPreferencesKey("modes")
    val HOLIDAY_HOME = booleanPreferencesKey("holiday.home")

    val RECORD_MODE = stringPreferencesKey("record.mode")
    val ON = booleanPreferencesKey("on")
    val SINCE = longPreferencesKey("since")
    val MUSIC_BEFORE = stringSetPreferencesKey("music.before")
    val MUSIC_MADE = stringPreferencesKey("music.made")
    val LIGHTS_BEFORE = stringSetPreferencesKey("lights.before")
    val RECORD_KEYS = listOf(RECORD_MODE, ON, SINCE, MUSIC_BEFORE, MUSIC_MADE, LIGHTS_BEFORE)

    /** One mode's keys, `mode.{id}.…`. */
    class For(id: String) {
        val name = stringPreferencesKey("$MODE_PREFIX$id.name")
        val icon = stringPreferencesKey("$MODE_PREFIX$id.icon")
        val home = booleanPreferencesKey("$MODE_PREFIX$id.home")
        val pin = booleanPreferencesKey("$MODE_PREFIX$id.pin")
        val pick = stringPreferencesKey("$MODE_PREFIX$id.pick")
        val pickName = stringPreferencesKey("$MODE_PREFIX$id.pick_name")
        val rooms = stringSetPreferencesKey("$MODE_PREFIX$id.rooms")
        val lights = stringSetPreferencesKey("$MODE_PREFIX$id.lights")
    }

    fun join(vararg fields: String) = fields.joinToString(SEPARATOR.toString())

    fun fields(text: String, count: Int) = text.split(SEPARATOR).takeIf { it.size == count }

    fun parseRoom(text: String): HouseRoom? = fields(text, 2)?.let { HouseRoom(it[0], it[1]) }

    /** The room form is `roomId, roomName, kind, sceneId, sceneName, percent`; a four-field entry from before 15b is a scene. */
    fun parseRoomChoice(text: String): RoomChoice? {
        val f = text.split(SEPARATOR)
        return when (f.size) {
            4 -> RoomChoice(f[0], f[1], LightChoice.Scene(f[2], f[3]))
            6 -> choiceOf(f[2], f[3], f[4], f[5])?.let { RoomChoice(f[0], f[1], it) }
            else -> null
        }
    }

    fun choiceOf(kind: String, sceneId: String, sceneName: String, percent: String): LightChoice? = when (kind) {
        KIND_SCENE -> LightChoice.Scene(sceneId, sceneName)
        KIND_ON -> percent.toIntOrNull()?.let { LightChoice.On(it) }
        KIND_OFF -> LightChoice.Off
        else -> null
    }

    /** A [LightChoice]'s stored fields; empty where the kind has none. */
    class Encoded(val kind: String, val sceneId: String = "", val sceneName: String = "", val percent: String = "") {
        /** `kind, sceneId, sceneName, percent`. */
        fun fields() = arrayOf(kind, sceneId, sceneName, percent)

        /** `kind, sceneId, percent`: the record keeps no scene name. */
        fun recordFields() = arrayOf(kind, sceneId, percent)
    }

    fun encoded(choice: LightChoice): Encoded = when (choice) {
        is LightChoice.Scene -> Encoded(KIND_SCENE, sceneId = choice.sceneId, sceneName = choice.sceneName)
        is LightChoice.On -> Encoded(KIND_ON, percent = choice.percent.toString())
        LightChoice.Off -> Encoded(KIND_OFF)
    }
}

/** Writes [modes] over every mode's keys, and always the `modes` key. */
internal fun MutablePreferences.writeModes(modes: List<StoredMode>) {
    asMap().keys.filter { it.name.startsWith(ModeKeys.MODE_PREFIX) }.forEach { remove(it) }
    this[ModeKeys.MODES] = modes.joinToString(",") { it.id }
    for (mode in modes) {
        val keys = ModeKeys.For(mode.id)
        this[keys.name] = mode.name
        this[keys.icon] = mode.icon
        this[keys.home] = mode.showOnHome
        this[keys.pin] = mode.needsPin
        mode.music?.let { music ->
            this[keys.pick] = music.pickId
            this[keys.pickName] = music.pickName
        }
        mode.speakers?.let { rooms -> this[keys.rooms] = rooms.map { ModeKeys.join(it.id, it.name) }.toSet() }
        if (mode.lights.isNotEmpty()) this[keys.lights] = mode.lights.map { ModeKeys.join(it.roomId, it.roomName, *ModeKeys.encoded(it.choice).fields()) }.toSet()
    }
}
