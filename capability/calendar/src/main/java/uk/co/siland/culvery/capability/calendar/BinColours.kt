package uk.co.siland.culvery.capability.calendar

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
import uk.co.siland.culvery.core.ui.BinInk
import uk.co.siland.culvery.core.ui.BinInks

private const val FILE = "bin_colours"
private const val TAG = "BinColours"
private const val KEY_PREFIX = "ink_"
private val SEEN = stringSetPreferencesKey("seen_kinds")

/** The ink a kind is drawn in: the [choices] entry, else the kind's own default; null for a kind we do not know. */
internal fun binInkFor(choices: Map<String, String>, kind: String): BinInk? =
    BinInks.byId(choices[kind]) ?: BinInks.byId(binKindOf(kind)?.defaultInk)

/**
 * The ink chosen for each kind of bin, and the unknown kinds seen so far (spec 4.4), in a DataStore file of its own. Writes
 * throw [IOException]; the caller says so or logs it.
 */
@Singleton
class BinColours internal constructor(private val store: DataStore<Preferences>, scope: CoroutineScope) {
    @Inject
    constructor(@ApplicationContext context: Context, @ApplicationScope scope: CoroutineScope) :
        this(settingsDataStore { context.preferencesDataStoreFile(FILE) }, scope)

    private val prefs: Flow<Preferences> = store.data
        .catch { e ->
            if (e !is IOException) throw e
            Log.w(TAG, "Couldn't read the bin colours (${e::class.simpleName})")
            emit(emptyPreferences())
        }

    private val chosen: Flow<Map<String, String>> = prefs
        .map { prefs ->
            prefs.asMap().mapNotNull { (key, value) ->
                (value as? String)?.takeIf { key.name.startsWith(KEY_PREFIX) }?.let { key.name.removePrefix(KEY_PREFIX) to it }
            }.toMap()
        }
        .distinctUntilChanged()

    /** Kind to ink id for the kinds the family has changed: the repository puts these inks on events; [inkFor] reads it at once. */
    internal val current: StateFlow<Map<String, String>> = chosen.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    suspend fun set(kind: String, inkId: String) {
        store.edit { it[stringPreferencesKey(KEY_PREFIX + kind)] = inkId }
    }

    fun inkFor(kind: String): BinInk? = binInkFor(current.value, kind)

    /** The unknown kinds remembered, so one stays listed after a later read drops it. */
    internal val seen: Flow<Set<String>> = prefs.map { it[SEEN].orEmpty() }.distinctUntilChanged()

    /** Adds the unknown kinds among [kinds] to [seen]; known ones are always listed. */
    suspend fun remember(kinds: Collection<String>) {
        val unknown = kinds.filter { binKindOf(it) == null }
        if (unknown.isEmpty()) return
        store.edit { prefs ->
            val now = prefs[SEEN].orEmpty()
            if (!now.containsAll(unknown)) prefs[SEEN] = now + unknown
        }
    }

    /** Forgets [seen]; the inks chosen stay. */
    suspend fun forgetSeen() {
        store.edit { it.remove(SEEN) }
    }
}
