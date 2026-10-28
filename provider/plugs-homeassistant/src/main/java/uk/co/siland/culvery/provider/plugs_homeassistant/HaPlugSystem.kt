package uk.co.siland.culvery.provider.plugs_homeassistant

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
import uk.co.siland.culvery.core.homeassistant.RegistryEntry
import uk.co.siland.culvery.core.homeassistant.domain
import uk.co.siland.culvery.core.homeassistant.problemLine
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.WallClock

private const val TAG = "HaPlugSystem"

/** How long the socket must have been steady before a plug missing from it is called gone (12 §6). */
const val GONE_AFTER_MS = 120_000L

/** A reconnect within this long of a good registry read keeps it; Settings always reads afresh. */
const val REGISTRY_FRESH_MS = 300_000L

private const val CAPTURE_TAG = "HaCapture"

/** The plugs, and for each the id of its watts sensor (12 §4.1). */
internal data class PlugRegistry(val plugs: Set<String>, val watts: Map<String, String>)

/** A plug is a TP-Link switch with no entity category; its watts are the same device's `current_consumption` sensor. */
internal fun plugRegistry(entries: List<RegistryEntry>): PlugRegistry {
    val tpLink = entries.filter { it.platform == "tplink" }
    val plugs = tpLink.filter { it.domain == "switch" && it.category == null }
    val sensors = tpLink
        .filter { it.domain == "sensor" && it.translationKey == "current_consumption" && it.deviceId != null }
        .associateBy { it.deviceId }
    return PlugRegistry(
        plugs = plugs.map { it.entityId }.toSet(),
        watts = plugs.mapNotNull { plug -> sensors[plug.deviceId]?.let { plug.entityId to it.entityId } }.toMap(),
    )
}

internal fun powerOf(state: String): PlugPower = when (state) {
    "on" -> PlugPower.ON
    "off" -> PlugPower.OFF
    "unavailable" -> PlugPower.UNREACHABLE
    else -> PlugPower.UNKNOWN
}

private fun wattsOf(sensor: EntityState?): Int? =
    sensor?.state?.toDoubleOrNull()?.takeIf { it in 0.0..100_000.0 }?.roundToInt()

/**
 * One plug. [switch] is what was read for it. A plug is gone only once the socket is [steady]; before that, missing is
 * unknown, so a Home Assistant restart never shows it.
 */
internal fun stateOf(
    id: String,
    entities: Map<String, EntityState?>,
    problem: HaProblem?,
    registry: PlugRegistry?,
    steady: Boolean,
    switch: EntityState? = entities[id],
): PlugState {
    val power = when {
        problem != null -> PlugPower.NO_LINK
        switch == null || (registry != null && id !in registry.plugs) -> if (steady) PlugPower.GONE else PlugPower.UNKNOWN
        else -> powerOf(switch.state)
    }
    val watts = if (power == PlugPower.ON) wattsOf(registry?.watts?.get(id)?.let(entities::get)) else null
    return PlugState(id, switch?.friendlyName ?: PlugsWords.A_PLUG, power, watts)
}

/** The plugs' states, in the store's order; null with no link, so no tab (12 §4.3). */
internal fun plugStates(
    ids: List<String>,
    linked: Boolean,
    entities: Map<String, EntityState?>,
    problem: HaProblem?,
    registry: PlugRegistry?,
    steady: Boolean,
): List<PlugState>? = if (linked) ids.map { stateOf(it, entities, problem, registry, steady) } else null

/** The plugs through Home Assistant (12 §4.1, §6). */
@Singleton
class HaPlugSystem internal constructor(
    private val ha: HomeAssistant,
    private val prefs: Flow<PlugPrefs>,
    private val clock: WallClock,
    scope: CoroutineScope,
) : PlugSystem {
    @Inject
    constructor(ha: HomeAssistant, settings: PlugSettings, clock: WallClock, @ApplicationScope scope: CoroutineScope) :
        this(ha, settings.prefs, clock, scope)

    private val switching = mutableSetOf<String>()

    /** A mode's or a PIN setting's write leaves it alone. */
    private val plugIds = prefs.map { it.plugs }.distinctUntilChanged()

    /** The last registry read; a failed read leaves it. */
    private val registry = MutableStateFlow<PlugRegistry?>(null)

    @Volatile private var readAt: Long? = null

    init {
        scope.launch {
            ha.liveSince.filterNotNull().collect {
                try {
                    val last = readAt
                    if (last == null || clock.nowMillis() - last >= REGISTRY_FRESH_MS) readRegistry()
                } catch (e: HomeAssistantException) {
                    Log.w(TAG, "registry failed (${e::class.simpleName})")
                }
            }
        }
    }

    private suspend fun readRegistry(): PlugRegistry {
        val entries = ha.registry()
        val read = plugRegistry(entries)
        registry.value = read
        readAt = clock.nowMillis()
        if (Log.isLoggable(CAPTURE_TAG, Log.DEBUG)) {
            Log.d(CAPTURE_TAG, "registry: ${read.plugs.size} plugs, ${read.watts.size} with watts, ${entries.size} entries")
        }
        return read
    }

    /** True once the open socket has been live for [GONE_AFTER_MS]. */
    private val steady: Flow<Boolean> = ha.liveSince.flatMapLatest { since ->
        if (since == null) {
            flowOf(false)
        } else {
            flow {
                val wait = since + GONE_AFTER_MS - clock.nowMillis()
                if (wait > 0) {
                    emit(false)
                    delay(wait)
                }
                emit(true)
            }
        }
    }.distinctUntilChanged()

    private class Sources(val entities: Map<String, EntityState?>, val problem: HaProblem?, val registry: PlugRegistry?, val steady: Boolean)

    override val plugs: Flow<List<PlugState>?> =
        combine(plugIds, ha.link, combine(ha.entities, ha.problem, registry, steady, ::Sources)) { ids, link, s ->
            plugStates(ids, link != null, s.entities, s.problem, s.registry, s.steady)
        }.distinctUntilChanged()

    override val problem: Flow<String?> = ha.problem.map { it?.let(::problemLine) }.distinctUntilChanged()

    override fun watch(): () -> Unit = ha.watch(
        combine(plugIds, registry) { ids, registry -> ids.flatMap { listOfNotNull(it, registry?.watts?.get(it)) }.toSet() },
    )

    override suspend fun available(): PlugCandidates = try {
        coroutineScope {
            val registry = async { readRegistry().plugs }
            val states = async { ha.states() }
            val plugs = registry.await()
            PlugCandidates.Found(
                states.await().filter { it.entityId in plugs }.map { PlugCandidate(it.entityId, it.friendlyName ?: it.entityId) }.sortedBy { it.name },
            )
        }
    } catch (e: HomeAssistantException) {
        Log.w(TAG, "available failed (${e::class.simpleName})")
        PlugCandidates.Failed(if (e.problem == HaProblem.REFUSED) PlugsWords.CANT_LIST_PLUGS else problemLine(e.problem))
    }

    override suspend fun read(entityId: String): PlugState {
        val unreachable = PlugState(entityId, ha.entities.value[entityId]?.friendlyName ?: PlugsWords.A_PLUG, PlugPower.UNREACHABLE, null)
        return try {
            stateOf(entityId, ha.entities.value, null, registry.value, steady = true, switch = ha.current(entityId))
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
            val now = ha.current(entityId) ?: return PlugResult.Failed(PlugsWords.GONE_PLUG)
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
