package uk.co.siland.culvery.provider.calendar_midsussex

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import uk.co.siland.culvery.core.plugin.settingsDataStore

private const val FILE = "bins_reads"

/**
 * A connection's last read and how today's attempts went. [addressKey] tells the connection's address from an earlier
 * one's without holding it. [failure] is the last read's failure, null once a read works; [failuresToday] counts the failed
 * attempts on the day of [lastAttemptMillis], unreachable ones included.
 */
@Serializable
internal data class BinsRead(
    val addressKey: String,
    val lastAttemptMillis: Long,
    val lastGoodMillis: Long? = null,
    val failuresToday: Int = 0,
    val failure: BinsFailure? = null,
    val collections: List<BinDay> = emptyList(),
)

/** Each bins connection's last read, in a DataStore file of its own, so a restart answers without asking the council. */
@Singleton
class BinsReads internal constructor(private val store: DataStore<Preferences>) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(settingsDataStore { context.preferencesDataStoreFile(FILE) })

    /** Null when nothing is stored, or what is stored can't be read. */
    internal suspend fun load(connectionId: String): BinsRead? {
        val text = try {
            store.data.first()[keyOf(connectionId)]
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't read the last bins read (${e::class.simpleName})")
            null
        } ?: return null
        return try {
            Json.decodeFromString(BinsRead.serializer(), text)
        } catch (e: SerializationException) {
            Log.w(TAG, "The last bins read is unreadable (${e::class.simpleName})")
            null
        }
    }

    /** A failed write is logged: the provider still holds [read] until a restart. */
    internal suspend fun save(connectionId: String, read: BinsRead) {
        try {
            store.edit { it[keyOf(connectionId)] = Json.encodeToString(BinsRead.serializer(), read) }
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't save the bins read (${e::class.simpleName})")
        }
    }

    private fun keyOf(connectionId: String) = stringPreferencesKey("last_read_$connectionId")
}
