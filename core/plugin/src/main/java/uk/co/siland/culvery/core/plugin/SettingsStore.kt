package uk.co.siland.culvery.core.plugin

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * A settings file's store; one that can't be read is replaced by [replacement], which a setting can set so a corrupt
 * file keeps the longest history rather than cutting it. [migrations] run before the first read.
 */
fun settingsDataStore(
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    replacement: () -> Preferences = ::emptyPreferences,
    migrations: List<DataMigration<Preferences>> = emptyList(),
    produceFile: () -> File,
): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { replacement() },
        migrations = migrations,
        scope = scope,
        produceFile = produceFile,
    )
