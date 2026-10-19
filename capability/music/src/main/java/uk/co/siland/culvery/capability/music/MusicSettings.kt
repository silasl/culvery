package uk.co.siland.culvery.capability.music

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.settingsDataStore

private const val FILE = "music_settings"
private const val TAG = "MusicSettings"

/** The doorbell's share of a group's volume (6 §2.3, §5.4). */
const val DEFAULT_LOWER_TO = 20
const val MAX_LOWER_TO = 50

/** Music's settings and its connection (6 §3.2): ids and an address, nothing secret, no names. */
data class MusicPrefs(
    val providerId: String? = null,
    val householdId: String? = null,
    val address: String? = null,
    /** The usual room's Sonos id (6 §2.2). */
    val usualRoomId: String? = null,
    val lowerTo: Int = DEFAULT_LOWER_TO,
) {
    /** The saved household and the speaker that last answered for it; null before Find Sonos. */
    val link: MusicLink? get() = if (householdId != null && address != null) MusicLink(householdId, address) else null
}

/**
 * A small DataStore file, as the door's: under `files/datastore/`, which backup and transfer already leave alone (6 §3.5).
 * Writes throw [IOException]; the caller says so or logs it.
 */
@Singleton
class MusicSettings(private val store: DataStore<Preferences>, scope: CoroutineScope) {
    @Inject
    constructor(@ApplicationContext context: Context, @ApplicationScope scope: CoroutineScope) :
        this(settingsDataStore { context.preferencesDataStoreFile(FILE) }, scope)

    val prefs: Flow<MusicPrefs> = store.data
        .catch { e ->
            if (e !is IOException) throw e
            Log.w(TAG, "Couldn't read music's settings (${e::class.simpleName})")
            emit(emptyPreferences())
        }
        .map { it.toPrefs() }
        .distinctUntilChanged()

    /** For the lowering, which can't wait: the defaults until the file is first read. */
    val current: StateFlow<MusicPrefs> = prefs.stateIn(scope, SharingStarted.Eagerly, MusicPrefs())

    /** Find Sonos or a typed address found [link] (6 §4.1); another household's usual room isn't this one's. */
    suspend fun connect(providerId: String, link: MusicLink) {
        store.edit {
            if (it[HOUSEHOLD] != link.householdId) it.remove(USUAL_ROOM)
            it[PROVIDER] = providerId
            it[HOUSEHOLD] = link.householdId
            it[ADDRESS] = link.address
        }
    }

    /** The household answered at a new address after a rescan (6 §4.1). */
    suspend fun moved(link: MusicLink) {
        store.edit { if (it[HOUSEHOLD] == link.householdId) it[ADDRESS] = link.address }
    }

    suspend fun setUsualRoom(roomId: String?) {
        store.edit { if (roomId == null) it.remove(USUAL_ROOM) else it[USUAL_ROOM] = roomId }
    }

    suspend fun setLowerTo(percent: Int) {
        store.edit { it[LOWER_TO] = percent.coerceIn(0, MAX_LOWER_TO) }
    }

    private fun Preferences.toPrefs() = MusicPrefs(this[PROVIDER], this[HOUSEHOLD], this[ADDRESS], this[USUAL_ROOM], this[LOWER_TO] ?: DEFAULT_LOWER_TO)

    private companion object {
        val PROVIDER = stringPreferencesKey("provider")
        val HOUSEHOLD = stringPreferencesKey("household")
        val ADDRESS = stringPreferencesKey("address")
        val USUAL_ROOM = stringPreferencesKey("usual_room")
        val LOWER_TO = intPreferencesKey("lower_to")
    }
}
