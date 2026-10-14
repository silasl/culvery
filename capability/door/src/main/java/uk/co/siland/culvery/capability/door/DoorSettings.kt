package uk.co.siland.culvery.capability.door

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import uk.co.siland.culvery.core.plugin.ApplicationScope

private const val FILE = "door_settings"
private const val TAG = "DoorSettings"
private const val SECONDS_PER_MINUTE = 60

/** Check Ring's notifications runs this long at most (5 §5.4). */
const val CAPTURE_MS = 30 * 60 * 1_000L

/** The door's settings. Defaults: 5 §2.6, §4.1, §5.4. */
data class DoorPrefs(
    /** Both connect steps were done: the Door tab shows (5 §5.5). */
    val connected: Boolean = false,
    val volume: Float = 0.7f,
    val quietOn: Boolean = true,
    val quietFrom: LocalTime = LocalTime.of(21, 0),
    val quietUntil: LocalTime = LocalTime.of(7, 0),
    /** Check Ring's notifications runs until then; 0 when off. */
    val captureUntilMillis: Long = 0L,
    val lastHeardMillis: Long? = null,
    /** New unrecognised alerts since the last recognised one: three in a row mean Ring's words changed (5 §6). */
    val unrecognisedRun: Int = 0,
) {
    /** Check Ring's notifications is on at [nowMillis]. */
    fun capturingAt(nowMillis: Long): Boolean = nowMillis < captureUntilMillis
}

/** The settings file's store; one that can't be read is replaced by an empty one, the defaults. */
internal fun doorSettingsStore(scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO), produceFile: () -> File): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }, scope = scope, produceFile = produceFile)

/**
 * A small DataStore file of its own, as the setup flags are: `door.db` keeps its one table (5 §3.1). Writes throw
 * [IOException]; the caller says so or logs it.
 */
@Singleton
class DoorSettings(private val store: DataStore<Preferences>, scope: CoroutineScope) {
    @Inject
    constructor(@ApplicationContext context: Context, @ApplicationScope scope: CoroutineScope) :
        this(doorSettingsStore { context.preferencesDataStoreFile(FILE) }, scope)

    val prefs: Flow<DoorPrefs> = store.data
        .catch { e ->
            if (e !is IOException) throw e
            Log.w(TAG, "Couldn't read the door's settings (${e::class.simpleName})")
            emit(emptyPreferences())
        }
        .map { it.toPrefs() }
        .distinctUntilChanged()

    /** For the listener and the alert decision, which can't wait: the defaults until the file is first read. */
    val current: StateFlow<DoorPrefs> = prefs.stateIn(scope, SharingStarted.Eagerly, DoorPrefs())

    suspend fun setConnected(connected: Boolean) = edit { it[CONNECTED] = connected }

    suspend fun setVolume(volume: Float) = edit { it[VOLUME] = volume.coerceIn(0f, 1f) }

    suspend fun setQuietHours(on: Boolean, from: LocalTime, until: LocalTime) = edit {
        it[QUIET_ON] = on
        it[QUIET_FROM] = from.toSecondOfDay() / SECONDS_PER_MINUTE
        it[QUIET_UNTIL] = until.toSecondOfDay() / SECONDS_PER_MINUTE
    }

    /** Check Ring's notifications runs until [untilMillis]; 0 turns it off (5 §5.4). */
    suspend fun setCapture(untilMillis: Long) = edit { it[CAPTURE_UNTIL] = untilMillis }

    suspend fun heard(atMillis: Long) = edit { it[LAST_HEARD] = atMillis }

    /** A new ring or motion: a recognised one ends the run of unrecognised ones, an unrecognised one adds to it. */
    suspend fun countAlert(recognised: Boolean) = edit { it[UNRECOGNISED_RUN] = if (recognised) 0 else (it[UNRECOGNISED_RUN] ?: 0) + 1 }

    private suspend fun edit(change: (MutablePreferences) -> Unit) {
        store.edit(change)
    }

    private fun Preferences.toPrefs(): DoorPrefs {
        val defaults = DoorPrefs()
        return DoorPrefs(
            connected = this[CONNECTED] ?: defaults.connected,
            volume = this[VOLUME] ?: defaults.volume,
            quietOn = this[QUIET_ON] ?: defaults.quietOn,
            quietFrom = this[QUIET_FROM]?.let { LocalTime.ofSecondOfDay(it * SECONDS_PER_MINUTE.toLong()) } ?: defaults.quietFrom,
            quietUntil = this[QUIET_UNTIL]?.let { LocalTime.ofSecondOfDay(it * SECONDS_PER_MINUTE.toLong()) } ?: defaults.quietUntil,
            captureUntilMillis = this[CAPTURE_UNTIL] ?: defaults.captureUntilMillis,
            lastHeardMillis = this[LAST_HEARD],
            unrecognisedRun = this[UNRECOGNISED_RUN] ?: defaults.unrecognisedRun,
        )
    }

    private companion object {
        val CONNECTED = booleanPreferencesKey("connected")
        val VOLUME = floatPreferencesKey("chime_volume")
        val QUIET_ON = booleanPreferencesKey("quiet_on")
        val QUIET_FROM = intPreferencesKey("quiet_from_minute")
        val QUIET_UNTIL = intPreferencesKey("quiet_until_minute")
        val CAPTURE_UNTIL = longPreferencesKey("capture_until")
        val LAST_HEARD = longPreferencesKey("last_heard")
        val UNRECOGNISED_RUN = intPreferencesKey("unrecognised_run")
    }
}
