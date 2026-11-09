package uk.co.siland.culvery.core.sound

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.ui.SeasonalTheme

private const val TAG = "SoundLibrary"

/** The built-in sounds followed by the ones in the tablet's sounds folder. The folder is read at start and on [refresh]. */
@Singleton
class SoundLibrary @VisibleForTesting constructor(
    private val root: () -> File?,
    scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) {
    @Inject
    constructor(@ApplicationContext context: Context, @ApplicationScope scope: CoroutineScope) :
        this({ context.getExternalFilesDir(null)?.resolve("sounds") }, scope, Dispatchers.IO)

    private val builtIns = BuiltInSound.entries.map { it.asSound() }
    private val state = MutableStateFlow(builtIns)
    // Guarded by state.
    private var reads = 0L
    private var shown = 0L

    private val firstRead = MutableStateFlow(false)

    val sounds: StateFlow<List<Sound>> = state.asStateFlow()

    /** False until a read of the folder has landed or failed; a tablet sound that isn't listed before then may only not have been read yet. */
    val firstReadDone: StateFlow<Boolean> = firstRead.asStateFlow()

    init {
        scope.launch { refresh() }
    }

    /** When reads overlap, the one that began last wins. A failed read keeps the list as it was. */
    suspend fun refresh() {
        val read = synchronized(state) { ++reads }
        try {
            val found = withContext(io) { readSoundsFolder(root()) }
            synchronized(state) {
                if (read > shown) {
                    shown = read
                    state.value = builtIns + found
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "The sounds folder couldn't be read (${e::class.simpleName})")
        }
        firstRead.value = true
    }

    fun byId(id: String?): Sound? = sounds.value.soundFor(id)

    /** Happy Birthday plays on the birthday pad only. */
    val choices: Flow<List<Sound>> = sounds.map { all -> all.filter { it.id != BuiltInSound.HAPPY_BIRTHDAY.id } }

    /** A tablet file in the theme's folder, else the theme's built-in sound, else the classic chime. */
    fun forTheme(theme: SeasonalTheme?): Sound {
        val all = sounds.value
        val themed = theme?.let { t -> all.firstOrNull { it.onTablet && it.theme == t } ?: all.firstOrNull { !it.onTablet && it.theme == t } }
        return themed ?: all.first { it.id == BuiltInSound.CLASSIC.id }
    }
}
