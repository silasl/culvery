package uk.co.siland.culvery.core.sound

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.siland.culvery.core.ui.SeasonalTheme

/** The built-in sounds followed by the ones in the tablet's sounds folder. The folder is read at start and on [refresh]. */
@Singleton
class SoundLibrary internal constructor(private val root: () -> File?, private val io: CoroutineDispatcher) {
    @Inject
    constructor(@ApplicationContext context: Context) :
        this({ context.getExternalFilesDir(null)?.resolve("sounds") }, Dispatchers.IO)

    private val builtIns = BuiltInSound.entries.map { Sound(it.id, it.label, SoundSource.BuiltIn(it), it.theme) }
    private val state = MutableStateFlow(builtIns)

    val sounds: StateFlow<List<Sound>> = state.asStateFlow()

    init {
        CoroutineScope(SupervisorJob() + io).launch { state.value = builtIns + readSoundsFolder(root()) }
    }

    suspend fun refresh() {
        state.value = builtIns + withContext(io) { readSoundsFolder(root()) }
    }

    fun byId(id: String?): Sound? = id?.let { wanted -> sounds.value.firstOrNull { it.id == wanted } }

    /** Happy Birthday plays on the birthday pad only. */
    val choices: Flow<List<Sound>> = sounds.map { all -> all.filter { it.id != BuiltInSound.HAPPY_BIRTHDAY.id } }

    /** A tablet file in the theme's folder, else the theme's built-in sound, else the classic chime. */
    fun forTheme(theme: SeasonalTheme?): Sound {
        val all = sounds.value
        val themed = theme?.let { t -> all.firstOrNull { it.onTablet && it.theme == t } ?: all.firstOrNull { !it.onTablet && it.theme == t } }
        return themed ?: all.first { it.id == BuiltInSound.CLASSIC.id }
    }
}
