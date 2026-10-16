package uk.co.siland.culvery.capability.lights

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.retryWithBackoff

private const val FILE = "lights_settings"
private const val TAG = "LightsSettings"

/** The bridge, its key and the choices made for it (7 §3.3): ids only, no names; [toString] leaves the key out. */
data class LightsPrefs(
    val providerId: String? = null,
    val bridgeId: String? = null,
    val address: String? = null,
    val key: String? = null,
    val mainRoomId: String? = null,
    val leftOut: Set<String> = emptySet(),
    val holidayAutomations: Set<String> = emptySet(),
    val holidayOn: Boolean = false,
    /** The automations Holiday mode switched on, so that only those are switched off (7 §4.5). */
    val holidayStarted: Set<String> = emptySet(),
) {
    /** The paired bridge; null before Find Hue Bridge. */
    val link: LightsLink? get() = if (bridgeId != null && address != null && key != null) LightsLink(bridgeId, address, key) else null

    override fun toString() =
        "LightsPrefs(providerId=$providerId, bridgeId=$bridgeId, address=$address, mainRoomId=$mainRoomId, leftOut=$leftOut, " +
            "holidayAutomations=$holidayAutomations, holidayOn=$holidayOn, holidayStarted=$holidayStarted)"
}

/** The settings file's store; one that can't be read is replaced by an empty one, the defaults. */
internal fun lightsSettingsStore(scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO), produceFile: () -> File): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }, scope = scope, produceFile = produceFile)

/** A small DataStore file under `files/datastore/`, which backup and transfer already leave alone. Writes throw `IOException`. */
@Singleton
class LightsSettings(private val store: DataStore<Preferences>, scope: CoroutineScope) {
    @Inject
    constructor(@ApplicationContext context: Context, @ApplicationScope scope: CoroutineScope) :
        this(lightsSettingsStore { context.preferencesDataStoreFile(FILE) }, scope)

    val prefs: Flow<LightsPrefs> = store.data
        .retryWithBackoff { Log.w(TAG, "Couldn't read the lights' settings (${it::class.simpleName}); retrying") }
        .map { it.toPrefs() }
        .distinctUntilChanged()

    val current: StateFlow<LightsPrefs> = prefs.stateIn(scope, SharingStarted.Eagerly, LightsPrefs())

    /** Pairing or a typed address found [link]; another bridge's choices aren't this one's, and Holiday mode starts off. */
    suspend fun connect(providerId: String, link: LightsLink) {
        store.edit {
            if (it[BRIDGE] != link.bridgeId) {
                it.remove(MAIN_ROOM)
                it.remove(LEFT_OUT)
                it.remove(HOLIDAY_AUTOMATIONS)
                it.remove(HOLIDAY_STARTED)
                it.remove(HOLIDAY_ON)
            }
            it[PROVIDER] = providerId
            it[BRIDGE] = link.bridgeId
            it[ADDRESS] = link.address
            it[KEY] = link.key
        }
    }

    /** The bridge answered at a new address after a search. */
    suspend fun moved(link: LightsLink) {
        store.edit { if (it[BRIDGE] == link.bridgeId) it[ADDRESS] = link.address }
    }

    suspend fun setMainRoom(id: String?) {
        store.edit { if (id == null) it.remove(MAIN_ROOM) else it[MAIN_ROOM] = id }
    }

    suspend fun setLeftOut(ids: Set<String>) {
        store.edit { it[LEFT_OUT] = ids }
    }

    suspend fun setHolidayAutomations(ids: Set<String>) {
        store.edit { it[HOLIDAY_AUTOMATIONS] = ids }
    }

    suspend fun setHolidayOn(on: Boolean) {
        store.edit { it[HOLIDAY_ON] = on }
    }

    suspend fun setHolidayStarted(ids: Set<String>) {
        store.edit { it[HOLIDAY_STARTED] = ids }
    }

    private fun Preferences.toPrefs() = LightsPrefs(
        this[PROVIDER], this[BRIDGE], this[ADDRESS], this[KEY], this[MAIN_ROOM],
        this[LEFT_OUT] ?: emptySet(), this[HOLIDAY_AUTOMATIONS] ?: emptySet(), this[HOLIDAY_ON] ?: false, this[HOLIDAY_STARTED] ?: emptySet(),
    )

    private companion object {
        val PROVIDER = stringPreferencesKey("provider")
        val BRIDGE = stringPreferencesKey("bridge")
        val ADDRESS = stringPreferencesKey("address")
        val KEY = stringPreferencesKey("key")
        val MAIN_ROOM = stringPreferencesKey("main_room")
        val LEFT_OUT = stringSetPreferencesKey("left_out")
        val HOLIDAY_AUTOMATIONS = stringSetPreferencesKey("holiday_automations")
        val HOLIDAY_ON = booleanPreferencesKey("holiday_on")
        val HOLIDAY_STARTED = stringSetPreferencesKey("holiday_started")
    }
}
