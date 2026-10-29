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
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import uk.co.siland.culvery.core.plugin.ApplicationScope

private const val TAG = "Speakers"

/**
 * The household's music system: the saved connection's provider, opened on its household. A new address for the same
 * household is passed to the open system, whether typed in Settings › Music or found by its own rescan (6 §4.1);
 * another household or provider opens afresh.
 */
@Singleton
class Speakers @Inject constructor(
    providers: Set<@JvmSuppressWildcards MusicProvider>,
    private val settings: MusicSettings,
    @ApplicationScope scope: CoroutineScope,
) {
    val providers: List<MusicProvider> = providers.toList()

    /** What Find Sonos and a typed address ask: the first a household may connect to, so a debug sample never answers them (6 §3.4). */
    val connectable: MusicProvider? = this.providers.firstOrNull { it.descriptor.userConnectable }

    private data class Opening(val providerId: String, val link: MusicLink) {
        fun sameHousehold(other: Opening) = providerId == other.providerId && link.householdId == other.link.householdId
    }

    private class Open(val opening: Opening, val system: MusicSystem?)

    val system: StateFlow<MusicSystem?> = settings.prefs
        .map { p -> p.providerId?.let { id -> p.link?.let { Opening(id, it) } } }
        .distinctUntilChanged()
        .runningFold(null as Open?) { open, opening ->
            when {
                opening == null -> null
                open != null && open.opening.sameHousehold(opening) -> open.also { it.system?.useAddress(opening.link.address) }
                else -> Open(opening, open(opening))
            }
        }
        .map { it?.system }
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
