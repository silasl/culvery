package uk.co.siland.culvery.core.homeassistant

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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import uk.co.siland.culvery.core.plugin.retryWithBackoff
import uk.co.siland.culvery.core.plugin.settingsDataStore

private const val FILE = "homeassistant_settings"
private const val TAG = "HomeAssistantSettings"

/** The saved [HomeAssistantLink] in a small DataStore file under `files/datastore/`, which backup and transfer already leave alone. Writes throw `IOException`. */
@Singleton
class HomeAssistantSettings(private val store: DataStore<Preferences>) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(settingsDataStore { context.preferencesDataStoreFile(FILE) })

    /** Null until a link is saved. */
    val link: Flow<HomeAssistantLink?> = store.data
        .retryWithBackoff { Log.w(TAG, "Couldn't read the Home Assistant link (${it::class.simpleName}); retrying") }
        .map { it.toLink() }
        .distinctUntilChanged()

    suspend fun save(link: HomeAssistantLink) {
        store.edit {
            it[ADDRESS] = link.baseUrl.toString()
            it[TOKEN] = link.token
        }
    }

    private fun Preferences.toLink(): HomeAssistantLink? {
        val url = this[ADDRESS]?.toHttpUrlOrNull() ?: return null
        val token = this[TOKEN] ?: return null
        return HomeAssistantLink(url, token)
    }

    private companion object {
        val ADDRESS = stringPreferencesKey("address")
        val TOKEN = stringPreferencesKey("token")
    }
}
