package uk.co.siland.culvery.capability.calendar

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
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

private const val FILE = "calendar_settings"
private const val TAG = "CalendarSettings"

/** How many weeks the Calendar tab lets the family step through, this one included (15a §2.3); the sync reads 12 whatever is chosen. */
enum class WeeksShown(val weeks: Int) {
    FOUR(4),
    EIGHT(8),
    TWELVE(12),
    ;

    /** How many weeks past this one the Calendar tab steps to. */
    val weeksAfter: Int get() = weeks - 1
}

/** The Calendar's one setting, in a DataStore file of its own. Writes throw [IOException]; the caller says so or logs it. */
@Singleton
class CalendarSettings internal constructor(private val store: DataStore<Preferences>, scope: CoroutineScope) {
    @Inject
    constructor(@ApplicationContext context: Context, @ApplicationScope scope: CoroutineScope) :
        this(settingsDataStore { context.preferencesDataStoreFile(FILE) }, scope)

    val shown: Flow<WeeksShown> = store.data
        .catch { e ->
            if (e !is IOException) throw e
            Log.w(TAG, "Couldn't read the calendar's settings (${e::class.simpleName})")
            emit(emptyPreferences())
        }
        .map { prefs -> WeeksShown.entries.firstOrNull { it.name == prefs[SHOWN] } ?: WeeksShown.EIGHT }
        .distinctUntilChanged()

    /** For the week view and the editor, which can't wait: the default until the file is first read. */
    internal val current: StateFlow<WeeksShown> = shown.stateIn(scope, SharingStarted.Eagerly, WeeksShown.EIGHT)

    suspend fun setShown(shown: WeeksShown) {
        store.edit { it[SHOWN] = shown.name }
    }

    private companion object {
        val SHOWN = stringPreferencesKey("weeks_shown")
    }
}
