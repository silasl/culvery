package uk.co.siland.culvery.capability.plugs

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.co.siland.culvery.core.access.Authorised
import uk.co.siland.culvery.core.house.HouseModeInfo
import uk.co.siland.culvery.core.house.HousePlugs
import uk.co.siland.culvery.core.house.HouseStep
import uk.co.siland.culvery.core.house.PlugChange

private const val TAG = "PlugsHouse"

/**
 * What the modes do to the plugs (12 §5): a mode begins with its own grant, then `enter` switches its plugs and `leave`
 * puts them back. `enter` and `leave` take no grant and share one lock; Remove and the PIN setting don't, so every
 * write here is a transform that re-checks the plug is still added.
 */
@Singleton
class PlugsHouse internal constructor(
    private val settings: PlugSettings,
    private val system: PlugSystem?,
    private val modes: Set<HouseModeInfo>,
) : HousePlugs {
    @Inject
    constructor(
        settings: PlugSettings,
        systems: Set<@JvmSuppressWildcards PlugSystem>,
        modes: Set<HouseModeInfo>,
    ) : this(settings, systems.firstOrNull(), modes)

    private val mutex = Mutex()

    override fun ready(modeId: String): Flow<Boolean> =
        if (system == null) flowOf(false) else settings.prefs.map { listed(it, modeId).isNotEmpty() }.distinctUntilChanged()

    override suspend fun preview(modeId: String): List<PlugChange> {
        val prefs = settings.prefs.first()
        return coroutineScope {
            listed(prefs, modeId).map { (id, on) -> async { PlugChange(read(id).name, on) } }.awaitAll()
        }
    }

    override suspend fun begin(modeId: String, by: Authorised) {
        require(modes.any { it.id == modeId && it.permission in by.granted }) { "A mode begins with its own grant" }
        mutex.withLock {
            settings.update { p ->
                if (modeId in p.runs) return@update p
                val notes = p.notes.mapValues { (_, note) -> note.copy(holders = note.holders.filter { it.mode != modeId }) }
                    .filterValues { it.holders.isNotEmpty() }
                p.copy(
                    notes = notes,
                    runs = p.runs + (modeId to ModeRun(p.next, listed(p, modeId).toMap())),
                    next = p.next + 1,
                )
            }
        }
    }

    override suspend fun enter(modeId: String): HouseStep? = mutex.withLock {
        val prefs = settings.prefs.first()
        val run = prefs.runs[modeId] ?: return@withLock null
        val missed = coroutineScope {
            run.pending.entries.sortedBy { prefs.plugs.indexOf(it.key) }
                .map { (id, wanted) -> async { reach(modeId, id, wanted) } }.awaitAll()
        }.filterNotNull()
        if (missed.isEmpty()) HouseStep.Done else HouseStep.Failed(PlugsWords.enterLine(missed))
    }

    override suspend fun leave(modeId: String): HouseStep = mutex.withLock {
        val prefs = settings.prefs.first()
        val held = prefs.plugs.filter { id -> prefs.notes[id]?.holders?.any { it.mode == modeId } == true }
        val missed = coroutineScope { held.map { id -> async { putBack(modeId, id) } }.awaitAll() }.filterNotNull()
        // Last, so a leave cut short runs again (12 §5.4).
        settings.update { it.copy(runs = it.runs - modeId) }
        if (missed.isEmpty()) HouseStep.Done else HouseStep.Failed(PlugsWords.leaveLine(missed))
    }

    /** The added plugs [modeId] sets to On or Off, in tab order. */
    private fun listed(p: PlugPrefs, modeId: String) =
        p.plugs.mapNotNull { id -> p.modes[modeId]?.get(id)?.let { id to it } }

    private suspend fun read(id: String) = system?.read(id) ?: PlugState(id, PlugsWords.A_PLUG, PlugPower.UNKNOWN, null)

    /** The change for a plug [modeId] hasn't reached yet, null once it has. */
    private suspend fun reach(modeId: String, id: String, wanted: Boolean): PlugChange? {
        var name = PlugsWords.A_PLUG
        val reached = try {
            if (id !in settings.prefs.first().plugs) {
                dropPending(modeId, id)
                true
            } else {
                val state = read(id)
                name = state.name
                reachFrom(modeId, id, wanted, state).also { if (it) dropPending(modeId, id) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Plugs enter: some not switched (${e::class.simpleName})")
            false
        }
        return if (reached) null else PlugChange(name, wanted)
    }

    private suspend fun reachFrom(modeId: String, id: String, wanted: Boolean, state: PlugState): Boolean {
        val now = when (state.power) {
            PlugPower.ON -> true
            PlugPower.OFF -> false
            else -> return false
        }
        var written: PlugNote? = null
        settings.update { p ->
            if (id !in p.plugs || modeId !in p.runs) return@update p
            val note = noted(p, id, modeId, wanted, now)
            written = note
            p.copy(notes = p.notes + (id to note))
        }
        val note = written ?: return true
        if (note.holders.last().mode != modeId || now == note.expected) return true
        return send(id, note.expected, entering = true).let { it == null || it == PlugResult.Done }
    }

    /** The note after [modeId] reaches [id], which is [now] on or off (12 §2.12). */
    private fun noted(p: PlugPrefs, id: String, modeId: String, wanted: Boolean, now: Boolean): PlugNote {
        val note = p.notes[id] ?: return PlugNote(now, listOf(Holder(modeId, wanted)))
        if (note.holders.any { it.mode == modeId }) return note
        val anyPending = note.holders.any { id in p.runs[it.mode]?.pending.orEmpty() }
        if (!anyPending && now != note.expected) return PlugNote(now, listOf(Holder(modeId, wanted)))
        val holders = (note.holders + Holder(modeId, wanted)).sortedBy { p.runs[it.mode]?.place ?: Long.MIN_VALUE }
        return note.copy(holders = holders)
    }

    /** The change for a plug [modeId] didn't put back, null when it did or was left. */
    private suspend fun putBack(modeId: String, id: String): PlugChange? {
        var name = PlugsWords.A_PLUG
        var target = false
        val done = try {
            val prefs = settings.prefs.first()
            val note = prefs.notes[id]?.takeIf { n -> n.holders.any { it.mode == modeId } } ?: return null
            val state = read(id)
            name = state.name
            val remaining = note.holders.filter { it.mode != modeId }
            target = remaining.lastOrNull()?.on ?: note.before
            val now = when (state.power) {
                PlugPower.ON -> true
                PlugPower.OFF -> false
                else -> null
            }
            val pending = prefs.runs.values.any { id in it.pending }
            when {
                now == null -> false.also { dropHolder(id, modeId) }
                !pending && now != note.expected -> true.also { settings.update { it.copy(notes = it.notes - id) } }
                else -> {
                    val result = if (note.holders.last().mode == modeId && now != target) send(id, target, entering = false) else null
                    dropHolder(id, modeId)
                    result == null || result == PlugResult.Done
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Plugs leave: some not put back (${e::class.simpleName})")
            false
        }
        return if (done) null else PlugChange(name, target)
    }

    /** Null when the plug was removed, which a write queued behind it would show but a plain read wouldn't; a throw is not switched. */
    private suspend fun send(id: String, on: Boolean, entering: Boolean): PlugResult? {
        var added = false
        settings.update { p -> p.also { added = id in p.plugs } }
        if (!added) return null
        return try {
            system?.switch(id, on) ?: PlugResult.Unreachable
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (entering) Log.w(TAG, "Plugs enter: some not switched (${e::class.simpleName})")
            else Log.w(TAG, "Plugs leave: some not put back (${e::class.simpleName})")
            PlugResult.NotSwitched
        }
    }

    private suspend fun dropPending(modeId: String, id: String) {
        settings.update { p -> p.runs[modeId]?.let { run -> p.copy(runs = p.runs + (modeId to run.copy(pending = run.pending - id))) } ?: p }
    }

    private suspend fun dropHolder(id: String, modeId: String) {
        settings.update { p ->
            val note = p.notes[id] ?: return@update p
            val holders = note.holders.filter { it.mode != modeId }
            p.copy(notes = if (holders.isEmpty()) p.notes - id else p.notes + (id to note.copy(holders = holders)))
        }
    }
}
