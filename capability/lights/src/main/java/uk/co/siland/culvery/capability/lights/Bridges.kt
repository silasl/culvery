package uk.co.siland.culvery.capability.lights

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

private const val TAG = "Bridges"

/**
 * The home's lights system: the saved connection's provider, opened on its bridge. A new address for the same bridge and
 * key is passed to the open system, whether typed in Settings › Lights or found by its own search (7 §4.1); another
 * bridge, key or provider opens afresh.
 */
@Singleton
class Bridges @Inject constructor(
    providers: Set<@JvmSuppressWildcards LightsProvider>,
    private val settings: LightsSettings,
    @ApplicationScope scope: CoroutineScope,
) {
    val providers: List<LightsProvider> = providers.toList()

    /** What Find Hue Bridge and a typed address ask: the first a household may connect to, so a debug sample never answers them. */
    val connectable: LightsProvider? = this.providers.firstOrNull { it.descriptor.userConnectable }

    private data class Opening(val providerId: String, val link: LightsLink) {
        fun sameBridge(other: Opening) =
            providerId == other.providerId && link.bridgeId == other.link.bridgeId && link.key == other.link.key
    }

    private class Open(val opening: Opening, val system: LightsSystem?)

    val system: StateFlow<LightsSystem?> = settings.prefs
        .map { p -> p.providerId?.let { id -> p.link?.let { Opening(id, it) } } }
        .distinctUntilChanged()
        .runningFold(null as Open?) { open, opening ->
            when {
                opening == null -> null
                open != null && open.opening.sameBridge(opening) -> open.also { it.system?.useAddress(opening.link.address) }
                else -> Open(opening, open(opening))
            }
        }
        .map { it?.system }
        .stateIn(scope, SharingStarted.Eagerly, null)

    private fun open(opening: Opening): LightsSystem? =
        providers.firstOrNull { it.descriptor.id == opening.providerId }?.open(opening.link) { moved ->
            try {
                settings.moved(moved)
            } catch (e: IOException) {
                Log.w(TAG, "Couldn't save the bridge's new address (${e::class.simpleName})")
            }
        }
}
