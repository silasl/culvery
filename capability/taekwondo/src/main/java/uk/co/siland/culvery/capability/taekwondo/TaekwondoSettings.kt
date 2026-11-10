package uk.co.siland.culvery.capability.taekwondo

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.core.plugin.retryWithBackoff
import uk.co.siland.culvery.core.plugin.settingsDataStore

private const val FILE = "taekwondo_settings"
private const val TAG = "TaekwondoSettings"

/** A small DataStore file under `files/datastore/`, which backup and transfer already leave alone. Writes throw `IOException`. */
@Singleton
class TaekwondoSettings(private val store: DataStore<Preferences>) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(settingsDataStore { context.preferencesDataStoreFile(FILE) })

    private val prefs: Flow<Preferences> = store.data
        .retryWithBackoff { Log.w(TAG, "Couldn't read the Taekwondo settings (${it::class.simpleName}); retrying") }

    /** Whether each move's description is read after its name; off until switched on. */
    val detailed: Flow<Boolean> = prefs.map { it[DETAILED] ?: false }.distinctUntilChanged()

    suspend fun setDetailed(on: Boolean) {
        store.edit { it[DETAILED] = on }
    }

    /** How each move's name is read aloud; Korean until chosen. */
    val language: Flow<Language> = prefs
        .map { p -> Language.entries.firstOrNull { it.name == p[LANGUAGE] } ?: Language.KOREAN }
        .distinctUntilChanged()

    suspend fun setLanguage(language: Language) {
        store.edit { it[LANGUAGE] = language.name }
    }

    private companion object {
        val DETAILED = booleanPreferencesKey("detailed")
        val LANGUAGE = stringPreferencesKey("language")
    }
}
