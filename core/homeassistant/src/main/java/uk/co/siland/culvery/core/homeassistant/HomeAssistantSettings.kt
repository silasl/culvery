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

/** A save without a pin was refused because one is stored. */
class PinStoredException : IllegalStateException("A pin is stored")

/** The saved [HomeAssistantLink], pin included, in a small DataStore file under `files/datastore/`, which backup and transfer already leave alone. Writes throw `IOException`. */
@Singleton
class HomeAssistantSettings(private val store: DataStore<Preferences>) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(settingsDataStore { context.preferencesDataStoreFile(FILE) })

    /** Null until a link is saved. */
    val link: Flow<HomeAssistantLink?> = store.data
        .retryWithBackoff { Log.w(TAG, "Couldn't read the Home Assistant link (${it::class.simpleName}); retrying") }
        .map { it.toLink() }
        .distinctUntilChanged()

    /** The pin as stored, whatever the rest of the record holds. */
    val storedPin: Flow<String?> = store.data
        .retryWithBackoff { Log.w(TAG, "Couldn't read the Home Assistant link (${it::class.simpleName}); retrying") }
        .map { it[PIN] }
        .distinctUntilChanged()

    /**
     * A pin goes only with an https address; saving one with http throws `IllegalArgumentException`. Saving without a
     * pin while one is stored throws [PinStoredException] and writes nothing: [forget] is the way to clear it.
     */
    suspend fun save(link: HomeAssistantLink) {
        require(link.pin == null || link.baseUrl.isHttps) { "A pin needs an https address" }
        store.edit {
            if (link.pin == null && it[PIN] != null) throw PinStoredException()
            it[ADDRESS] = link.baseUrl.toString()
            it[TOKEN] = link.token
            if (link.pin == null) it.remove(PIN) else it[PIN] = link.pin
        }
    }

    /** Removes the address, token and pin in one write. */
    suspend fun forget() {
        store.edit {
            it.remove(ADDRESS)
            it.remove(TOKEN)
            it.remove(PIN)
        }
    }

    private fun Preferences.toLink(): HomeAssistantLink? {
        val url = this[ADDRESS]?.toHttpUrlOrNull() ?: return null
        val token = this[TOKEN] ?: return null
        val pin = this[PIN]
        if (pin != null && !url.isHttps) return null
        return HomeAssistantLink(url, token, pin)
    }

    private companion object {
        val ADDRESS = stringPreferencesKey("address")
        val TOKEN = stringPreferencesKey("token")
        val PIN = stringPreferencesKey("certificate_pin")
    }
}
