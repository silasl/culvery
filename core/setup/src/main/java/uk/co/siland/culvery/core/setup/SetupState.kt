package uk.co.siland.culvery.core.setup

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.core.household.HouseholdRepository

private const val TAG = "SetupState"

/** The setup file's store. A file that can't be read is replaced by an empty one; SetupState decides it again. */
fun setupStore(scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO), produceFile: () -> File): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope,
        produceFile = produceFile,
    )

/**
 * Whether first-run setup has finished, and whether Welcome was passed (4a design §3.1, D8): a small DataStore file of
 * its own, not Room, as it is two flags.
 */
@Singleton
class SetupState(private val store: DataStore<Preferences>, private val household: HouseholdRepository) {
    @Inject
    constructor(@ApplicationContext context: Context, household: HouseholdRepository) :
        this(setupStore { context.preferencesDataStoreFile(FILE) }, household)

    private val prefs: Flow<Preferences> = store.data.catch { e ->
        if (e !is IOException) throw e
        Log.w(TAG, "Couldn't read the setup file (${e::class.simpleName})")
        emit(emptyPreferences())
    }

    /**
     * D8: the first read on an install that has never stored the flag decides it, once: complete when an active Admin
     * exists (an install from before 4a), not complete otherwise. A fresh install stores false at that first read, so
     * the Admin its wizard makes later never marks setup complete (ruling 2). A flag that can't be read or written is
     * decided the same way, from the household, every time.
     */
    val setupComplete: Flow<Boolean> = flow {
        try {
            store.edit { stored -> if (COMPLETE !in stored) stored[COMPLETE] = activeAdmin() }
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't write the setup file (${e::class.simpleName})")
        }
        emitAll(prefs.map { it[COMPLETE] ?: activeAdmin() })
    }.distinctUntilChanged()

    /** Welcome was passed, so a start after a kill resumes past it (4a design §6). */
    val welcomed: Flow<Boolean> = prefs.map { it[WELCOMED] == true }.distinctUntilChanged()

    suspend fun markWelcomed() {
        store.edit { it[WELCOMED] = true }
    }

    suspend fun markComplete() {
        store.edit { it[COMPLETE] = true }
    }

    private suspend fun activeAdmin(): Boolean = household.credentials().any { it.isActiveAdmin }

    private companion object {
        const val FILE = "setup"
        val COMPLETE = booleanPreferencesKey("setupComplete")
        val WELCOMED = booleanPreferencesKey("welcomed")
    }
}
