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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.co.siland.culvery.core.house.AddedPlug
import uk.co.siland.culvery.core.house.HousePlugs
import uk.co.siland.culvery.core.house.HouseStep
import uk.co.siland.culvery.core.house.ModeGrant
import uk.co.siland.culvery.core.house.PlugChange
import uk.co.siland.culvery.core.house.PlugOption
import uk.co.siland.culvery.core.house.allows
import uk.co.siland.culvery.core.plugin.attempt

private const val TAG = "PlugsHouse"

/**
 * What the modes do to the plugs (12 §5): a mode begins with its own grant, then `enter` switches its plugs and `leave`
 * puts them back. `enter` and `leave` take no grant and share one lock; Remove and the PIN setting don't, so every
 * write here is a transform that re-checks the plug is still added. A wall's run never switches a plug that needs a
 * PIN (15b Ruling 4).
 */
@Singleton
class PlugsHouse internal constructor(
    private val settings: PlugSettings,
    private val system: PlugSystem?,
) : HousePlugs {
    @Inject
    constructor(
        settings: PlugSettings,
        systems: Set<@JvmSuppressWildcards PlugSystem>,
    ) : this(settings, systems.firstOrNull())

    private val mutex = Mutex()

    override fun ready(modeId: String): Flow<Boolean> =
        if (system == null) flowOf(false) else settings.prefs.map { listed(it, modeId).isNotEmpty() }.distinctUntilChanged()

    override suspend fun preview(modeId: String): List<PlugChange> {
        val prefs = settings.current()
        return coroutineScope {
            listed(prefs, modeId).map { (id, on) -> async { PlugChange(read(id).name, on) } }.awaitAll()
        }
    }

    override suspend fun begin(modeId: String, grant: ModeGrant) {
        require(isModeId(modeId) && grant.allows(modeId)) { "A mode begins with its own grant" }
        val wall = grant is ModeGrant.Wall
        mutex.withLock {
            // A run still here is a leave whose last write threw: put it back before the new snapshot (12 §5.2).
            if (modeId in settings.current().runs) leaveLocked(modeId)
            settings.update { p ->
                val notes = p.notes.mapValues { (_, note) -> note.copy(holders = note.holders.filter { it.mode != modeId }) }
                    .filterValues { it.holders.isNotEmpty() }
                p.copy(
                    notes = notes,
                    runs = p.runs + (modeId to ModeRun(p.next, snapshot(p, modeId, wall), wall)),
                    next = p.next + 1,
                )
            }
        }
    }

    override suspend fun enter(modeId: String): HouseStep? = mutex.withLock {
        val prefs = settings.current()
        val run = prefs.runs[modeId] ?: return@withLock null
        val locked = if (run.wall) dropLocked(modeId, prefs) else emptySet()
        fanOut(run.pending.entries.sortedBy { prefs.plugs.indexOf(it.key) }, PlugsWords::enterLine) { (id, wanted) ->
            if (id in locked) missed(id, wanted) else reach(modeId, id, wanted)
        }
    }

    override suspend fun leave(modeId: String): HouseStep = mutex.withLock { leaveLocked(modeId) }

    override suspend fun options(): List<PlugOption> {
        if (system == null) return emptyList()
        val prefs = settings.current()
        return coroutineScope {
            prefs.plugs.map { id -> async { PlugOption(id, read(id).name, id in prefs.needsPin) } }.awaitAll()
        }
    }

    override fun added(modeId: String): Flow<List<AddedPlug>> = settings.prefs.map { p ->
        listed(p, modeId).map { (id, on) -> AddedPlug(id, on, id in p.needsPin) }
    }.distinctUntilChanged()

    override suspend fun choose(modeId: String, plugId: String, on: Boolean?) {
        val choice = when (on) {
            true -> ModeChoice.ON
            false -> ModeChoice.OFF
            null -> ModeChoice.IGNORE
        }
        settings.update { if (plugId in it.plugs) it.with(modeId, plugId, choice) else it }
    }

    override suspend fun forget(modeId: String) = mutex.withLock {
        leaveLocked(modeId)
        settings.update { it.copy(modes = it.modes - modeId) }
    }

    override suspend fun holdsPinPlug(modeId: String): Boolean = attempt({
        val p = settings.current()
        val noted = p.notes.filterValues { note -> note.holders.any { it.mode == modeId } }.keys
        (noted + p.runs[modeId]?.pending?.keys.orEmpty()).any { it in p.needsPin }
    }) { e ->
        Log.w(TAG, "Plugs: couldn't read the runs (${e::class.simpleName}); counting a plug that needs a PIN")
        true
    }

    /** The plugs a run begins with: a wall's leaves out those that need a PIN. */
    private fun snapshot(p: PlugPrefs, modeId: String, wall: Boolean): Map<String, Boolean> =
        listed(p, modeId).filterNot { wall && it.first in p.needsPin }.toMap()

    /** Drops the pending plugs of [modeId]'s wall run that have come to need a PIN, in one write; the ids dropped. */
    private suspend fun dropLocked(modeId: String, seen: PlugPrefs): Set<String> {
        if (seen.runs[modeId]?.pending.orEmpty().keys.none { it in seen.needsPin }) return emptySet()
        var dropped = emptySet<String>()
        settings.update { p ->
            val run = p.runs[modeId]?.takeIf { it.wall } ?: return@update p
            dropped = run.pending.keys intersect p.needsPin
            p.copy(runs = p.runs + (modeId to run.copy(pending = run.pending - dropped)))
        }
        if (dropped.isNotEmpty()) Log.w(TAG, "Plugs: a no-PIN run left out a plug that needs a PIN")
        return dropped
    }

    /** The change for a plug a wall's run left out, named by a read. */
    private suspend fun missed(id: String, wanted: Boolean): PlugChange? = guarded(wanted) { touched ->
        touched.name = read(id).name
        false
    }

    private suspend fun leaveLocked(modeId: String): HouseStep {
        val prefs = settings.current()
        val held = prefs.plugs.filter { id -> prefs.notes[id]?.holders?.any { it.mode == modeId } == true }
        if (modeId !in prefs.runs && held.isEmpty()) return HouseStep.Done
        val step = fanOut(held, PlugsWords::leaveLine) { putBack(modeId, it) }
        // Last, so a leave cut short runs again (12 §5.4).
        settings.update { it.copy(runs = it.runs - modeId) }
        return step
    }

    /** [step] for each of [items] at once; Failed with [line] over the changes it returned, Done when it returned none. */
    private suspend fun <T> fanOut(items: List<T>, line: (List<PlugChange>) -> String, step: suspend (T) -> PlugChange?): HouseStep {
        val missed = coroutineScope { items.map { async { step(it) } }.awaitAll() }.filterNotNull()
        return if (missed.isEmpty()) HouseStep.Done else HouseStep.Failed(line(missed))
    }

    /** The added plugs [modeId] sets to On or Off, in tab order. */
    private fun listed(p: PlugPrefs, modeId: String) =
        p.plugs.mapNotNull { id -> p.modes[modeId]?.get(id)?.let { id to it } }

    private suspend fun read(id: String) = system?.read(id) ?: PlugState(id, PlugsWords.A_PLUG, PlugPower.UNKNOWN, null)

    /** What a guarded step knows of its plug when it ends: [name] once read, and the state it was after, [on]. */
    private class Touched(var name: String = PlugsWords.A_PLUG, var on: Boolean)

    /** The change for the plug [block] didn't get to, null when it did; a throw is logged and counts as not got to. */
    private suspend fun guarded(on: Boolean, block: suspend (Touched) -> Boolean): PlugChange? {
        val touched = Touched(on = on)
        val done = try {
            block(touched)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Plugs: a plug step threw (${e::class.simpleName})")
            false
        }
        return if (done) null else PlugChange(touched.name, touched.on)
    }

    /** The change for a plug [modeId] hasn't reached yet, null once it has. */
    private suspend fun reach(modeId: String, id: String, wanted: Boolean): PlugChange? =
        guarded(wanted) { touched ->
            val state = read(id)
            touched.name = state.name
            reachFrom(modeId, id, wanted, state).also { if (it) dropPending(modeId, id) }
        }

    private suspend fun reachFrom(modeId: String, id: String, wanted: Boolean, state: PlugState): Boolean {
        val now = state.isOn ?: return false
        var written: PlugNote? = null
        var locked = false
        settings.update { p ->
            if (id !in p.plugs || modeId !in p.runs) return@update p
            if (p.runs[modeId]?.wall == true && id in p.needsPin) return@update p.also { locked = true }
            val note = noted(p, id, modeId, wanted, now)
            written = note
            p.copy(notes = p.notes + (id to note))
        }
        if (locked) return false
        val note = written ?: return true
        if (note.holders.last().mode != modeId || now == note.expected) return true
        return send(id, note.expected).let { it == null || it == PlugResult.Done }
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
    private suspend fun putBack(modeId: String, id: String): PlugChange? =
        guarded(on = false) { touched ->
            val prefs = settings.current()
            val note = prefs.notes[id]?.takeIf { n -> n.holders.any { it.mode == modeId } } ?: return@guarded true
            val state = read(id)
            touched.name = state.name
            val target = note.holders.filter { it.mode != modeId }.lastOrNull()?.on ?: note.before
            touched.on = target
            val now = state.isOn
            val pending = prefs.runs.values.any { id in it.pending }
            when {
                now == null -> (note.holders.last().mode != modeId).also { dropHolder(id, modeId) }
                !pending && now != note.expected -> true.also { settings.update { it.copy(notes = it.notes - id) } }
                else -> {
                    val result = if (note.holders.last().mode == modeId && now != target) send(id, target) else null
                    dropHolder(id, modeId)
                    result == null || result == PlugResult.Done
                }
            }
        }

    /** Null when the plug was removed, which a read queued behind the removal shows; a throw is not switched. */
    private suspend fun send(id: String, on: Boolean): PlugResult? {
        if (id !in settings.current().plugs) return null
        return try {
            system?.switch(id, on) ?: PlugResult.Unreachable
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Plugs: a switch threw (${e::class.simpleName})")
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
