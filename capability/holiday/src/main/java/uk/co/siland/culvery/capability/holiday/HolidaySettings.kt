package uk.co.siland.culvery.capability.holiday

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.retryWithBackoff
import uk.co.siland.culvery.core.plugin.settingsDataStore

private const val FILE = "holiday_settings"
private const val TAG = "HolidaySettings"

/** In the order they run; the tile's line looks at them the other way round (11 §5.3). */
enum class HolidayStep { LIGHTS_ON, ECO_ON, HOOVER, ECO_OFF }

enum class StepState { PENDING, DONE, MISSED }

/**
 * Holiday mode's record (11 §5.7): [steps] holds only the steps that will happen, chosen at turn-on. [since] is wall
 * millis. [toString] leaves out [back] and [since].
 */
data class HolidayRecord(
    val on: Boolean = false,
    val back: LocalDate? = null,
    val since: Long? = null,
    val steps: Map<HolidayStep, StepState> = emptyMap(),
) {
    override fun toString() = "HolidayRecord(on=$on, steps=$steps)"
}

/** A small DataStore file under `files/datastore/`, which backup and transfer already leave alone. Writes throw `IOException`. */
@Singleton
class HolidaySettings(private val store: DataStore<Preferences>, scope: CoroutineScope) {
    @Inject
    constructor(@ApplicationContext context: Context, @ApplicationScope scope: CoroutineScope) :
        this(settingsDataStore { context.preferencesDataStoreFile(FILE) }, scope)

    val prefs: Flow<HolidayRecord> = store.data
        .retryWithBackoff { Log.w(TAG, "Couldn't read the Holiday mode record (${it::class.simpleName}); retrying") }
        .map { it.toRecord() }
        .distinctUntilChanged()

    /** Null until the file is first read, so nothing takes "off" for the saved answer. */
    val read: StateFlow<HolidayRecord?> = prefs.stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun save(record: HolidayRecord) {
        store.edit {
            it.clear()
            it[ON] = record.on
            record.back?.let { back -> it[BACK] = back.toString() }
            record.since?.let { since -> it[SINCE] = since }
            it[STEPS] = record.steps.map { (step, state) -> "${step.name}=${state.name}" }.toSet()
        }
    }

    suspend fun clear() {
        store.edit { it.clear() }
    }

    private fun Preferences.toRecord() = HolidayRecord(
        on = this[ON] ?: false,
        back = this[BACK]?.let(::parseDate),
        since = this[SINCE],
        steps = this[STEPS].orEmpty().mapNotNull(::parseStep).toMap(),
    )

    private fun parseDate(text: String): LocalDate? = runCatching { LocalDate.parse(text) }.getOrNull()

    /** "HOOVER=DONE"; one this version doesn't know is skipped. */
    private fun parseStep(text: String): Pair<HolidayStep, StepState>? {
        val (step, state) = text.split('=').takeIf { it.size == 2 } ?: return null
        return (HolidayStep.entries.find { it.name == step } ?: return null) to (StepState.entries.find { it.name == state } ?: return null)
    }

    private companion object {
        val ON = booleanPreferencesKey("on")
        val BACK = stringPreferencesKey("back")
        val SINCE = longPreferencesKey("since")
        val STEPS = stringSetPreferencesKey("steps")
    }
}
