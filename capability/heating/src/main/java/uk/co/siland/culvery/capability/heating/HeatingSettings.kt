package uk.co.siland.culvery.capability.heating

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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

private const val FILE = "heating_settings"
private const val TAG = "HeatingSettings"

/** The chosen thermostat's entity id; [toString] leaves it out. */
data class HeatingPrefs(val device: String? = null) {
    override fun toString() = "HeatingPrefs(chosen=${device != null})"
}

/** A small DataStore file under `files/datastore/`, which backup and transfer already leave alone. Writes throw `IOException`. */
@Singleton
class HeatingSettings(private val store: DataStore<Preferences>) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(settingsDataStore { context.preferencesDataStoreFile(FILE) })

    val prefs: Flow<HeatingPrefs> = store.data
        .retryWithBackoff { Log.w(TAG, "Couldn't read the heating settings (${it::class.simpleName}); retrying") }
        .map { HeatingPrefs(it[DEVICE]) }
        .distinctUntilChanged()

    /** Null is "None". */
    suspend fun choose(device: String?) {
        store.edit { if (device == null) it.remove(DEVICE) else it[DEVICE] = device }
    }

    private companion object {
        val DEVICE = stringPreferencesKey("device")
    }
}
