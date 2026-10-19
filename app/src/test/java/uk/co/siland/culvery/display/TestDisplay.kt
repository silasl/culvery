package uk.co.siland.culvery.display

import java.io.File
import kotlinx.coroutines.CoroutineScope

internal const val FILE_NAME = "display_settings.preferences_pb"

internal fun displaySettings(dir: File, scope: CoroutineScope) =
    DisplaySettings(displaySettingsStore(scope) { File(dir, FILE_NAME) }, scope)
