package uk.co.siland.culvery.display

import java.io.File
import kotlinx.coroutines.CoroutineScope
import uk.co.siland.culvery.core.plugin.settingsDataStore

internal const val FILE_NAME = "display_settings.preferences_pb"

internal fun displaySettings(dir: File, scope: CoroutineScope) =
    DisplaySettings(settingsDataStore(scope) { File(dir, FILE_NAME) }, scope)
