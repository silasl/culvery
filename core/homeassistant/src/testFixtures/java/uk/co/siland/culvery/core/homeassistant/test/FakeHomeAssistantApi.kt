package uk.co.siland.culvery.core.homeassistant.test

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import uk.co.siland.culvery.core.homeassistant.EntityState
import uk.co.siland.culvery.core.homeassistant.HaProblem
import uk.co.siland.culvery.core.homeassistant.HomeAssistantApi
import uk.co.siland.culvery.core.homeassistant.HomeAssistantException
import uk.co.siland.culvery.core.homeassistant.HomeAssistantLink
import uk.co.siland.culvery.core.homeassistant.RegistryEntry

/** Home Assistant in memory: [states] answers the reads, [socket] feeds every open follow, and each call is recorded. */
class FakeHomeAssistantApi : HomeAssistantApi {
    val states: MutableMap<String, EntityState> = mutableMapOf()

    private val _calls = mutableListOf<String>()

    /** `"check"`, `"states"`, `"state vacuum.hoover"`, `"service vacuum/start vacuum.hoover"`, `"… preset_mode=eco"`. */
    val calls: List<String> get() = _calls

    private val _follows = mutableListOf<Set<String>>()

    /** The ids each socket opened for, in order. */
    val follows: List<Set<String>> get() = _follows

    /** How many open sockets were closed by their collector. */
    var cancels: Int = 0
        private set

    /** The next call of any kind fails with this. */
    var failNext: HaProblem? = null

    /** Every call fails with this until cleared. */
    var failAll: HaProblem? = null

    /** Runs as a service call succeeds; may throw. */
    var onService: (domain: String, service: String, entityId: String, data: Map<String, String>) -> Unit = { _, _, _, _ -> }

    /** What [registry] returns, unless [registryProblem] is set. */
    var registry: List<RegistryEntry> = emptyList()

    /** [registry] fails with this when set. */
    var registryProblem: HaProblem? = null

    /** What every open follow emits. */
    val socket = MutableSharedFlow<Map<String, EntityState?>>(extraBufferCapacity = 16)

    /** When set, [check] waits for it. */
    var gate: CompletableDeferred<Unit>? = null

    private var drop: CompletableDeferred<HaProblem>? = null

    /** Ends the open follow with [problem], as a socket that closes or is refused. */
    fun dropSocket(problem: HaProblem) {
        checkNotNull(drop) { "No socket is open" }.complete(problem)
    }

    override suspend fun check(link: HomeAssistantLink) {
        _calls += "check"
        gate?.await()
        failIfAsked()
    }

    override suspend fun states(link: HomeAssistantLink): List<EntityState> {
        _calls += "states"
        failIfAsked()
        return states.values.toList()
    }

    override suspend fun state(link: HomeAssistantLink, entityId: String): EntityState? {
        _calls += "state $entityId"
        failIfAsked()
        return states[entityId]
    }

    override suspend fun callService(link: HomeAssistantLink, domain: String, service: String, entityId: String, data: Map<String, String>) {
        _calls += (listOf("service $domain/$service $entityId") + data.map { (k, v) -> "$k=$v" }).joinToString(" ")
        failIfAsked()
        onService(domain, service, entityId, data)
    }

    override suspend fun registry(link: HomeAssistantLink): List<RegistryEntry> {
        _calls += "registry"
        failIfAsked()
        registryProblem?.let { throw HomeAssistantException(it) }
        return registry
    }

    override fun follow(link: HomeAssistantLink, entityIds: Set<String>): Flow<Map<String, EntityState?>> = channelFlow {
        _follows += entityIds
        val dropped = CompletableDeferred<HaProblem>().also { drop = it }
        launch { socket.collect { send(it) } }
        val problem = try {
            dropped.await()
        } catch (e: CancellationException) {
            cancels++
            throw e
        } finally {
            if (drop === dropped) drop = null
        }
        throw HomeAssistantException(problem)
    }

    private fun failIfAsked() {
        val problem = failNext ?: failAll ?: return
        failNext = null
        throw HomeAssistantException(problem)
    }
}
