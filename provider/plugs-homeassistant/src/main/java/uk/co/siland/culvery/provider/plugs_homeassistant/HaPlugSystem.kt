package uk.co.siland.culvery.provider.plugs_homeassistant

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import uk.co.siland.culvery.capability.plugs.PlugCandidate
import uk.co.siland.culvery.capability.plugs.PlugCandidates
import uk.co.siland.culvery.capability.plugs.PlugPower
import uk.co.siland.culvery.capability.plugs.PlugPrefs
import uk.co.siland.culvery.capability.plugs.PlugResult
import uk.co.siland.culvery.capability.plugs.PlugSettings
import uk.co.siland.culvery.capability.plugs.PlugState
import uk.co.siland.culvery.capability.plugs.PlugSystem
import uk.co.siland.culvery.capability.plugs.PlugsWords
import uk.co.siland.culvery.core.homeassistant.CONFIRM_FOR_MS
import uk.co.siland.culvery.core.homeassistant.EntityState
import uk.co.siland.culvery.core.homeassistant.HaProblem
import uk.co.siland.culvery.core.homeassistant.HomeAssistant
import uk.co.siland.culvery.core.homeassistant.HomeAssistantException
import uk.co.siland.culvery.core.homeassistant.NOT_LINKED
import uk.co.siland.culvery.core.homeassistant.problemLine

private const val TAG = "HaPlugSystem"

/** The power sensor is found by name, with no registry read (12 §4.1). */
internal fun wattsIdOf(switchId: String): String = "sensor." + switchId.substringAfter('.') + "_current_consumption"

internal fun powerOf(state: String): PlugPower = when (state) {
    "on" -> PlugPower.ON
    "off" -> PlugPower.OFF
    "unavailable" -> PlugPower.UNREACHABLE
    else -> PlugPower.UNKNOWN
}

private fun wattsOf(sensor: EntityState?): Int? =
    sensor?.state?.toDoubleOrNull()?.takeIf { it in 0.0..100_000.0 }?.roundToInt()

/** The plugs' states, in the store's order; null with no link, so no tab (12 §4.3). */
internal fun plugStates(
    prefs: PlugPrefs,
    linked: Boolean,
    entities: Map<String, EntityState?>,
    problem: HaProblem?,
): List<PlugState>? {
    if (!linked) return null
    return prefs.plugs.map { id ->
        val switch = entities[id]
        val power = when {
            problem != null -> PlugPower.UNKNOWN
            id in entities && switch == null -> PlugPower.GONE
            else -> switch?.let { powerOf(it.state) } ?: PlugPower.UNKNOWN
        }
        val watts = if (power == PlugPower.ON) wattsOf(entities[wattsIdOf(id)]) else null
        PlugState(id, switch?.friendlyName ?: PlugsWords.A_PLUG, power, watts)
    }
}

/** The plugs through Home Assistant (12 §4.1, §6). */
@Singleton
class HaPlugSystem internal constructor(
    private val ha: HomeAssistant,
    private val prefs: Flow<PlugPrefs>,
) : PlugSystem {
    @Inject
    constructor(ha: HomeAssistant, settings: PlugSettings) : this(ha, settings.prefs)

    private val switching = mutableSetOf<String>()

    override val plugs: Flow<List<PlugState>?> =
        combine(prefs, ha.link, ha.entities, ha.problem) { p, link, entities, problem -> plugStates(p, link != null, entities, problem) }
            .distinctUntilChanged()

    override val problem: Flow<String?> = ha.problem.map { it?.let(::problemLine) }.distinctUntilChanged()

    override fun watch(): () -> Unit = ha.watch(prefs.map { p -> p.plugs.flatMap { listOf(it, wattsIdOf(it)) }.toSet() })

    override suspend fun candidates(): PlugCandidates = try {
        PlugCandidates.Found(
            ha.states().filter { it.domain == "switch" }.map { PlugCandidate(it.entityId, it.friendlyName ?: it.entityId) },
        )
    } catch (e: HomeAssistantException) {
        Log.w(TAG, "candidates failed (${e::class.simpleName})")
        PlugCandidates.Failed(problemLine(e.problem))
    }

    override suspend fun read(entityId: String): PlugState {
        val unreachable = PlugState(entityId, ha.entities.value[entityId]?.friendlyName ?: PlugsWords.A_PLUG, PlugPower.UNREACHABLE, null)
        return try {
            val state = ha.current(entityId)
            plugStates(PlugPrefs(plugs = listOf(entityId)), true, ha.entities.value + (entityId to state), null).orEmpty().single()
        } catch (_: HomeAssistantException) {
            unreachable
        } catch (e: TimeoutCancellationException) {
            if (!currentCoroutineContext().isActive) throw e
            unreachable
        }
    }

    /** One switch per plug at a time; the lock goes when the check ends or is cancelled. */
    override suspend fun switch(entityId: String, on: Boolean): PlugResult {
        if (!synchronized(switching) { switching.add(entityId) }) return PlugResult.Busy
        try {
            return switchOnce(entityId, on)
        } finally {
            synchronized(switching) { switching.remove(entityId) }
        }
    }

    private suspend fun switchOnce(entityId: String, on: Boolean): PlugResult {
        if (ha.savedLink() == null) return PlugResult.Failed(NOT_LINKED)
        val wanted = if (on) "on" else "off"
        try {
            val now = ha.current(entityId) ?: return PlugResult.Failed(GONE_PLUG)
            if (now.state == "unavailable") return PlugResult.Unreachable
            if (now.state == wanted) return PlugResult.Done
            try {
                ha.call("switch", if (on) "turn_on" else "turn_off", entityId)
            } catch (e: HomeAssistantException) {
                if (e.problem != HaProblem.REFUSED) throw e
                Log.w(TAG, "switch failed (${e::class.simpleName})")
                return PlugResult.NotSwitched
            }
            if (ha.awaitEntity(entityId, CONFIRM_FOR_MS) { it.state == wanted } != null) return PlugResult.Done
            Log.w(TAG, "the plug didn't switch")
            return if (ha.entities.value[entityId]?.state == "unavailable") PlugResult.Unreachable else PlugResult.NotSwitched
        } catch (e: HomeAssistantException) {
            Log.w(TAG, "switch failed (${e::class.simpleName})")
            return PlugResult.Failed(problemLine(e.problem))
        } catch (e: TimeoutCancellationException) {
            // A timeout of the caller's has cancelled this coroutine; one that hasn't is ours, and mustn't cancel the caller's siblings.
            if (!currentCoroutineContext().isActive) throw e
            Log.w(TAG, "switch failed (${e::class.simpleName})")
            return PlugResult.Unreachable
        }
    }
}
