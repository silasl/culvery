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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import uk.co.siland.culvery.core.house.GroupBefore
import uk.co.siland.culvery.core.house.HouseRoom
import uk.co.siland.culvery.core.house.PartyGroup
import uk.co.siland.culvery.core.house.RoomBefore
import uk.co.siland.culvery.core.house.SceneChoice
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.retryWithBackoff
import uk.co.siland.culvery.core.plugin.settingsDataStore

private const val FILE = "party_settings"
private const val TAG = "PartySettings"

/** The playlist or favourite Party plays. [toString] leaves out the id and name. */
data class MusicChoice(val pickId: String, val pickName: String) {
    override fun toString() = "MusicChoice"
}

/**
 * What the family chose in Settings (13 §2.3–§2.5). [rooms] is the speakers to play on, null for every speaker, kept
 * whether or not there is a pick. [toString] leaves out the choices.
 */
data class PartyPrefs(
    val music: MusicChoice? = null,
    val rooms: List<HouseRoom>? = null,
    val scenes: List<SceneChoice> = emptyList(),
) {
    fun withScene(choice: SceneChoice) = copy(scenes = scenes.filterNot { it.roomId == choice.roomId } + choice)

    fun withoutScene(roomId: String) = copy(scenes = scenes.filterNot { it.roomId == roomId })

    override fun toString() = "PartyPrefs"
}

/**
 * What Party changed and must put back (13 §4.3). [since] is wall millis. Each of [musicBefore], [made] and [lights]
 * is null until its step has read. [toString] leaves out all but [on].
 */
data class PartyRecord(
    val on: Boolean = false,
    val since: Long? = null,
    val musicBefore: List<GroupBefore>? = null,
    val made: PartyGroup? = null,
    val lights: List<RoomBefore>? = null,
) {
    override fun toString() = "PartyRecord(on=$on)"
}

/** A small DataStore file under `files/datastore/`, which backup and transfer already leave alone. Writes throw `IOException`. */
@Singleton
class PartySettings(private val store: DataStore<Preferences>, scope: CoroutineScope) {
    @Inject
    constructor(@ApplicationContext context: Context, @ApplicationScope scope: CoroutineScope) :
        this(settingsDataStore { context.preferencesDataStoreFile(FILE) }, scope)

    private val data: Flow<Preferences> = store.data
        .retryWithBackoff { Log.w(TAG, "Couldn't read the Party mode settings (${it::class.simpleName}); retrying") }

    val prefs: Flow<PartyPrefs> = data.map { it.toPrefs() }.distinctUntilChanged()

    /** Null until the file is first read, so nothing takes "off" for the saved answer. */
    val record: StateFlow<PartyRecord?> = data.map { it.toRecord() }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, null)

    /** Replaces the choices only; the record's keys stay. */
    suspend fun update(change: (PartyPrefs) -> PartyPrefs) {
        store.edit {
            val next = change(it.toPrefs())
            for (key in CHOICE_KEYS) it.remove(key)
            next.music?.let { music ->
                it[PICK] = music.pickId
                it[PICK_NAME] = music.pickName
            }
            next.rooms?.let { rooms -> it[ROOMS] = rooms.map { room -> join(room.id, room.name) }.toSet() }
            if (next.scenes.isNotEmpty()) {
                it[SCENES] = next.scenes.map { s -> join(s.roomId, s.roomName, s.sceneId, s.sceneName) }.toSet()
            }
        }
    }

    /** Replaces the record only; the choices stay. */
    suspend fun save(record: PartyRecord) {
        store.edit {
            it.removeRecord()
            it[ON] = record.on
            record.since?.let { since -> it[SINCE] = since }
            record.musicBefore?.let { before -> it[MUSIC_BEFORE] = before.map { g -> join(g.coordinatorId, g.roomIds.joinToString(",")) }.toSet() }
            record.made?.let { made -> it[MUSIC_MADE] = join(made.coordinatorId, made.roomIds.joinToString(",")) }
            record.lights?.let { lights ->
                it[LIGHTS_BEFORE] = lights.map { r ->
                    join(r.roomId, r.name, r.sceneId, r.on.toString(), r.brightness.toString(), r.activeSceneId.orEmpty())
                }.toSet()
            }
        }
    }

    suspend fun clearRecord() {
        store.edit { it.removeRecord() }
    }

    private fun MutablePreferences.removeRecord() {
        for (key in RECORD_KEYS) remove(key)
    }

    private fun Preferences.toPrefs() = PartyPrefs(
        music = this[PICK]?.let { id -> this[PICK_NAME]?.let { name -> MusicChoice(id, name) } },
        rooms = this[ROOMS]?.mapNotNull { text -> fields(text, 2)?.let { HouseRoom(it[0], it[1]) } },
        scenes = this[SCENES].orEmpty().mapNotNull { text -> fields(text, 4)?.let { SceneChoice(it[0], it[1], it[2], it[3]) } },
    )

    private fun Preferences.toRecord() = PartyRecord(
        on = this[ON] ?: false,
        since = this[SINCE],
        musicBefore = this[MUSIC_BEFORE]?.mapNotNull { text -> parseGroup(text)?.let { (id, rooms) -> GroupBefore(id, rooms) } },
        made = this[MUSIC_MADE]?.let(::parseGroup)?.let { (id, rooms) -> PartyGroup(id, rooms.toSet()) },
        lights = this[LIGHTS_BEFORE]?.mapNotNull(::parseRoomBefore),
    )

    private fun parseGroup(text: String): Pair<String, List<String>>? {
        val f = fields(text, 2) ?: return null
        return f[0] to f[1].split(',').filter { it.isNotEmpty() }
    }

    private fun parseRoomBefore(text: String): RoomBefore? {
        val f = fields(text, 6) ?: return null
        return RoomBefore(
            roomId = f[0],
            name = f[1],
            sceneId = f[2],
            on = f[3].toBooleanStrictOrNull() ?: return null,
            brightness = f[4].toIntOrNull() ?: return null,
            activeSceneId = f[5].ifEmpty { null },
        )
    }

    private companion object {
        /** No name holds it, so a value with several fields can be split again. */
        const val SEPARATOR = '\u001F'

        fun join(vararg fields: String) = fields.joinToString(SEPARATOR.toString())

        fun fields(text: String, count: Int) = text.split(SEPARATOR).takeIf { it.size == count }

        val PICK = stringPreferencesKey("music.pick")
        val PICK_NAME = stringPreferencesKey("music.pick_name")
        val ROOMS = stringSetPreferencesKey("music.rooms")
        val SCENES = stringSetPreferencesKey("scenes")
        val ON = booleanPreferencesKey("on")
        val SINCE = longPreferencesKey("since")
        val MUSIC_BEFORE = stringSetPreferencesKey("music.before")
        val MUSIC_MADE = stringPreferencesKey("music.made")
        val LIGHTS_BEFORE = stringSetPreferencesKey("lights.before")
        val CHOICE_KEYS = listOf(PICK, PICK_NAME, ROOMS, SCENES)
        val RECORD_KEYS = listOf(ON, SINCE, MUSIC_BEFORE, MUSIC_MADE, LIGHTS_BEFORE)
    }
}
