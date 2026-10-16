package uk.co.siland.culvery.capability.lights

import android.util.Log
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import uk.co.siland.culvery.core.plugin.COULD_NOT_SAVE
import uk.co.siland.culvery.core.plugin.homeNetworkHost

private const val TAG = "LightsConnect"

const val PAIR_EVERY_MS = 2_000L
const val PAIR_FOR_MS = 30_000L

sealed interface FindOutcome {
    data class Found(val bridge: BridgeFound) : FindOutcome

    data class Failed(val line: String) : FindOutcome
}

sealed interface PairOutcome {
    data object Paired : PairOutcome

    data class Failed(val line: String) : PairOutcome
}

/**
 * Settings › Lights' ways to the bridge (7 §4.1): Find Hue Bridge or a typed address give the bridge, and pairing asks
 * every [PAIR_EVERY_MS] for [PAIR_FOR_MS] until its button is pressed. A pairing is saved as the connection.
 */
@Singleton
class LightsConnect internal constructor(private val provider: LightsProvider?, private val save: suspend (String, LightsLink) -> Unit) {
    @Inject
    constructor(bridges: Bridges, settings: LightsSettings) : this(bridges.providers.firstOrNull(), settings::connect)

    suspend fun find(): FindOutcome = look(NO_BRIDGE_ANSWERED) { it.find() }

    suspend fun enterAddress(typed: String): FindOutcome {
        val host = homeNetworkHost(typed) ?: return FindOutcome.Failed(TYPE_FOUR_NUMBERS)
        return look(NO_BRIDGE_THERE) { it.check(host) }
    }

    suspend fun pair(bridge: BridgeFound): PairOutcome {
        val p = provider ?: return PairOutcome.Failed(NOT_PRESSED_IN_TIME)
        val link = try {
            withTimeoutOrNull(PAIR_FOR_MS) {
                var pressed: LightsLink? = null
                while (pressed == null) {
                    pressed = p.pair(bridge)
                    if (pressed == null) delay(PAIR_EVERY_MS)
                }
                pressed
            }
        } catch (e: LightsUnreachableException) {
            return PairOutcome.Failed(problemLine(e.problem))
        } ?: return PairOutcome.Failed(NOT_PRESSED_IN_TIME)
        return try {
            save(p.descriptor.id, link)
            PairOutcome.Paired
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't save the bridge (${e::class.simpleName})")
            PairOutcome.Failed(COULD_NOT_SAVE)
        }
    }

    private suspend fun look(nothing: String, ask: suspend (LightsProvider) -> BridgeFound?): FindOutcome {
        val p = provider ?: return FindOutcome.Failed(nothing)
        val bridge = try {
            ask(p)
        } catch (e: LightsUnreachableException) {
            return FindOutcome.Failed(problemLine(e.problem))
        }
        return bridge?.let(FindOutcome::Found) ?: FindOutcome.Failed(nothing)
    }
}
