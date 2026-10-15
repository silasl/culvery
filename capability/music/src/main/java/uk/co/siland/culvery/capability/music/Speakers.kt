package uk.co.siland.culvery.capability.music

import android.util.Log
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import uk.co.siland.culvery.core.plugin.ApplicationScope

private const val TAG = "Speakers"

/**
 * The household's music system: the saved connection's provider, opened on its household. A new address for the same
 * household keeps the open system, which moved there itself (6 §4.1); another household or provider opens afresh.
 */
@Singleton
class Speakers @Inject constructor(
    providers: Set<@JvmSuppressWildcards MusicProvider>,
    private val settings: MusicSettings,
    @ApplicationScope scope: CoroutineScope,
) {
    /** Find Sonos and a typed address use the first (Sonos, the only one bound, 6 §3.4). */
    val providers: List<MusicProvider> = providers.toList()

    private data class Opening(val providerId: String, val link: MusicLink)

    val system: StateFlow<MusicSystem?> = settings.prefs
        .map { p -> p.providerId?.let { id -> p.link?.let { Opening(id, it) } } }
        .distinctUntilChanged { a, b -> a?.providerId == b?.providerId && a?.link?.householdId == b?.link?.householdId }
        .map { it?.let(::open) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    private fun open(opening: Opening): MusicSystem? =
        providers.firstOrNull { it.descriptor.id == opening.providerId }?.open(opening.link) { moved ->
            try {
                settings.moved(moved)
            } catch (e: IOException) {
                Log.w(TAG, "Couldn't save the speakers' new address (${e::class.simpleName})")
            }
        }
}
