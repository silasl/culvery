package uk.co.siland.culvery.core.homeassistant

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import uk.co.siland.culvery.core.plugin.AlertHost
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.Startable
import uk.co.siland.culvery.core.plugin.retryWithBackoff

/** After a command, Home Assistant is read this often to see it take (11 §6). */
const val CONFIRM_EVERY_MS = 5_000L

/** A command that hasn't shown by then didn't take (11 §6). */
const val CONFIRM_FOR_MS = 30_000L

private const val TAG = "HomeAssistant"

/** The problems that are the link's, not one device's or Check's. */
private val LINK_PROBLEMS = setOf(HaProblem.UNREACHABLE, HaProblem.OFFLINE, HaProblem.TOKEN_REFUSED, HaProblem.BLOCKED)

private class SocketEnded : Exception("The Home Assistant socket ended")

/** What the socket follows, and for which link; a new generation reopens it. */
private data class Follow(val link: HomeAssistantLink, val generation: Int, val ids: Set<String>)

/**
 * The one way to Home Assistant (11 §4.3): its entities as last read, the link's [problem], and one socket following
 * what screens [watch], open only while a link is saved and Culvery is in front. After a refused token nothing but
 * [check] reaches Home Assistant, which can ban an address after failed logins, until a Check of the saved link succeeds.
 */
@Singleton
class HomeAssistant(
    private val api: HomeAssistantApi,
    private val savedLink: Flow<HomeAssistantLink?>,
    private val inFront: StateFlow<Boolean>,
    private val scope: CoroutineScope,
) : Startable {
    @Inject
    constructor(api: HomeAssistantApi, settings: HomeAssistantSettings, host: AlertHost, @ApplicationScope scope: CoroutineScope) :
        this(api, settings.link, host.inFront, scope)

    // Guards the link against the cache and the problem, so nothing read under an old link lands after a new one.
    private val lock = Any()

    private val _link = MutableStateFlow<HomeAssistantLink?>(null)
    val link: StateFlow<HomeAssistantLink?> = _link.asStateFlow()

    private val _entities = MutableStateFlow<Map<String, EntityState?>>(emptyMap())

    /** Each entity as last read; a null value is one Home Assistant doesn't have. */
    val entities: StateFlow<Map<String, EntityState?>> = _entities.asStateFlow()

    private val _problem = MutableStateFlow<HaProblem?>(null)

    /** What is wrong with the link: unreachable, offline, a refused token or a blocked tablet (11 §6). */
    val problem: StateFlow<HaProblem?> = _problem.asStateFlow()

    private val generation = MutableStateFlow(0)
    private val watchers = MutableStateFlow<Map<Int, Set<String>>>(emptyMap())
    private val nextWatcher = AtomicInteger()

    /** How many screens watch, for their tests. */
    internal val watching: Int get() = watchers.value.size

    /** `GET /api/` with [link]. When [link] is the saved one, success clears the problem and reopens the socket. */
    suspend fun check(link: HomeAssistantLink) {
        api.check(link)
        synchronized(lock) {
            if (link == _link.value) {
                generation.update { it + 1 }
                _problem.value = null
            }
        }
    }

    /** One entity, null when Home Assistant has none by that id. */
    suspend fun read(entityId: String): EntityState? = calling { link ->
        api.state(link, entityId).also { state -> cache(link) { it + (entityId to state) } }
    }

    /** Every entity; one cached but no longer listed is cached as gone. */
    suspend fun states(): List<EntityState> = calling { link ->
        api.states(link).also { states ->
            cache(link) { old -> old.mapValues { null } + states.associateBy { it.entityId } }
        }
    }

    suspend fun call(domain: String, service: String, entityId: String, data: Map<String, String> = emptyMap()) = calling { link ->
        api.callService(link, domain, service, entityId, data)
    }

    /**
     * Waits until [done] holds for [entityId] (null: Home Assistant has none), reading it every [CONFIRM_EVERY_MS]
     * and counting what the socket brings. False when [timeoutMs] pass first; a failed read is thrown.
     */
    suspend fun awaitEntity(entityId: String, timeoutMs: Long, done: (EntityState?) -> Boolean): Boolean =
        withTimeoutOrNull(timeoutMs) {
            coroutineScope {
                val polling = launch {
                    while (true) {
                        delay(CONFIRM_EVERY_MS)
                        read(entityId)
                    }
                }
                _entities.first { entityId in it && done(it[entityId]) }
                polling.cancel()
                true
            }
        } ?: false

    /** A screen follows [ids] while it shows; call the result when it goes. */
    fun watch(ids: Flow<Set<String>>): () -> Unit {
        val key = nextWatcher.getAndIncrement()
        watchers.update { it + (key to emptySet()) }
        val job = scope.launch { ids.collect { set -> watchers.update { if (key in it) it + (key to set) else it } } }
        val stopped = AtomicBoolean(false)
        return {
            if (stopped.compareAndSet(false, true)) {
                job.cancel()
                watchers.update { it - key }
            }
        }
    }

    /** Records a link problem; a refused token stays until a Check or a new link, whatever fails after it. */
    internal fun failed(problem: HaProblem) {
        if (problem in LINK_PROBLEMS) _problem.update { if (it == HaProblem.TOKEN_REFUSED) it else problem }
    }

    override fun start() {
        scope.launch {
            savedLink.collect { new ->
                synchronized(lock) {
                    if (new != _link.value) {
                        _link.value = new
                        _entities.value = emptyMap()
                        _problem.value = null
                    }
                }
            }
        }
        val union = watchers.map { it.values.flatten().toSet() }.distinctUntilChanged()
        scope.launch { union.collect { HaCapture.keep = it } }
        scope.launch {
            val refused = _problem.map { it == HaProblem.TOKEN_REFUSED }.distinctUntilChanged()
            combine(_link, generation, inFront, union, refused) { link, gen, front, ids, isRefused ->
                link?.takeIf { front && ids.isNotEmpty() && !isRefused }?.let { Follow(it, gen, ids) }
            }
                .distinctUntilChanged()
                .collectLatest { follow -> if (follow != null) following(follow) }
        }
    }

    /** The socket for [follow], reopened with backoff; its first event after each open is the full read (11 §4.3). */
    private suspend fun following(follow: Follow) {
        flow {
            emitAll(
                api.follow(follow.link, follow.ids).catch { e ->
                    // Retrying would send the refused token again; a Check or a new link restarts this.
                    if (e is HomeAssistantException && e.problem == HaProblem.TOKEN_REFUSED) {
                        record(follow.link, e.problem)
                        awaitCancellation()
                    }
                    throw e
                },
            )
            throw SocketEnded()
        }
            .retryWithBackoff { e ->
                if (e is HomeAssistantException) record(follow.link, e.problem)
                Log.w(TAG, "The socket dropped (${e::class.simpleName}); reopening")
            }
            .collect { states ->
                synchronized(lock) {
                    if (follow.link == _link.value) {
                        _entities.update { it + states }
                        cleared()
                    }
                }
            }
    }

    private suspend fun <T> calling(block: suspend (HomeAssistantLink) -> T): T {
        val link = _link.value
        if (link == null || _problem.value == HaProblem.TOKEN_REFUSED) throw HomeAssistantException(HaProblem.TOKEN_REFUSED)
        val result = try {
            block(link)
        } catch (e: HomeAssistantException) {
            record(link, e.problem)
            throw e
        }
        synchronized(lock) { if (link == _link.value) cleared() }
        return result
    }

    private fun record(link: HomeAssistantLink, problem: HaProblem) = synchronized(lock) {
        if (link == _link.value) failed(problem)
    }

    /** A refused token stays until a Check: a call that raced it doesn't clear it. */
    private fun cleared() = _problem.update { if (it == HaProblem.TOKEN_REFUSED) it else null }

    private fun cache(link: HomeAssistantLink, change: (Map<String, EntityState?>) -> Map<String, EntityState?>) = synchronized(lock) {
        if (link == _link.value) _entities.update(change)
    }
}
