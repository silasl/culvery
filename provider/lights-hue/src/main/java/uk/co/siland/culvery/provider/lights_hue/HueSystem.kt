package uk.co.siland.culvery.provider.lights_hue

import java.io.IOException
import kotlin.math.roundToInt
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uk.co.siland.culvery.capability.lights.AllOffResult
import uk.co.siland.culvery.capability.lights.Automation
import uk.co.siland.culvery.capability.lights.AutomationResult
import uk.co.siland.culvery.capability.lights.BridgeFound
import uk.co.siland.culvery.capability.lights.LightRoom
import uk.co.siland.culvery.capability.lights.LightScene
import uk.co.siland.culvery.capability.lights.LightsLink
import uk.co.siland.culvery.capability.lights.LightsProblem
import uk.co.siland.culvery.capability.lights.LightsRefusedException
import uk.co.siland.culvery.capability.lights.LightsSystem
import uk.co.siland.culvery.capability.lights.LightsUnreachableException
import uk.co.siland.culvery.core.plugin.WallClock

/** An automation is read back this long after it was switched, and after each read that didn't show it yet (7 §4.5). */
internal const val READ_BACK_MS = 1_000L

/** About five seconds of read-back, then the switch counts as not done (7 §4.5). */
internal const val READ_BACK_TRIES = 6

/** The bridge is searched for by itself at most this often (7 §4.1). */
internal const val SEARCH_GAP_MS = 30_000L

private const val BRIDGE_HOME = "bridge_home"

/**
 * The household's Hue rooms through the bridge's CLIP v2 API (7 §4.2–§4.5): a full read builds them in Hue's order,
 * the stream keeps them up to date, and a command shows at once. Every call is built from the saved bridge id, so only
 * a certificate naming it is trusted; when the saved address fails, a search finds the bridge again (7 §4.1). Every
 * change to what the screen shows is made, and [rooms] published, under one lock.
 */
internal class HueSystem(
    private val client: HueClient,
    private val search: suspend (wanted: String?) -> List<BridgeFound>,
    private val online: () -> Boolean,
    link: LightsLink,
    private val onMoved: suspend (LightsLink) -> Unit,
    private val clock: WallClock,
    private val readBackMs: Long = READ_BACK_MS,
) : LightsSystem {
    private val _rooms = MutableStateFlow<List<LightRoom>>(emptyList())
    override val rooms: StateFlow<List<LightRoom>> = _rooms.asStateFlow()

    private val _problem = MutableStateFlow<LightsProblem?>(null)
    override val problem: StateFlow<LightsProblem?> = _problem.asStateFlow()

    @Volatile private var link = link

    private val lock = Any()

    // Guarded by lock.
    private var hueRooms: List<HueRoom> = emptyList()
    private val groups = HashMap<String, HueGroupedLight>()
    private var scenes: List<HueScene> = emptyList()
    private var dimmingDevices: Set<String> = emptySet()
    private var unroomedLights: List<String> = emptyList()

    /** The automations as last read, by id: whether each is Mimic presence decides how it is switched. */
    @Volatile private var behaviors: Map<String, HueBehavior> = emptyMap()

    private val recovering = Mutex()

    // Guarded by recovering.
    private var lastSearchMillis: Long? = null

    // A move is saved only after a full read at the new address, so that read needn't run again.
    override suspend fun readAll() = onBridge(movedResult = Unit) { read(it) }

    /** Every call trusts only a certificate naming [BridgeCall.bridgeId], so another bridge fails as [WrongBridge]. */
    private suspend fun read(call: BridgeCall) = coroutineScope {
        val roomsRead = async { client.rooms(call) }
        val groupsRead = async { client.groupedLights(call) }
        val lightsRead = async { client.lights(call) }
        val scenesRead = async { client.scenes(call) }
        val read = roomsRead.await()
        val groupsNow = groupsRead.await()
        val lightsNow = lightsRead.await()
        val scenesNow = scenesRead.await()
        val roomed = read.flatMap { it.deviceIds }.toSet()
        change {
            hueRooms = read
            groups.clear()
            groupsNow.forEach { groups[it.id] = it }
            scenes = scenesNow
            dimmingDevices = lightsNow.filter { it.dimmable }.map { it.deviceId }.toSet()
            unroomedLights = lightsNow.filter { it.deviceId !in roomed }.map { it.id }
        }
    }

    /** Emits once the bridge has opened the stream; an add, a delete or a room change reads everything again (7 §4.2). */
    override fun stream(): Flow<Unit> = channelFlow {
        client.events(call(), opened = { send(Unit) }).collect { events ->
            if (HueEvent.Reread in events) readAll() else change { events.forEach(::apply) }
        }
    }

    override suspend fun setOn(room: LightRoom, on: Boolean) = command(room, { group(it) { g -> g.copy(on = on) } }) { call, hue ->
        client.setOn(call, hue.groupedLightId, on)
    }

    /** At 0 the room is turned off: Hue would take 0 as its lowest brightness, with the lights still on. */
    override suspend fun setBrightness(room: LightRoom, percent: Int) {
        val brightness = percent.coerceIn(0, 100)
        if (brightness == 0) return setOn(room, false)
        command(room, { group(it) { g -> g.copy(on = true, brightness = brightness.toDouble()) } }) { call, hue ->
            client.setBrightness(call, hue.groupedLightId, brightness, turnOn = true)
        }
    }

    override suspend fun recallScene(room: LightRoom, scene: LightScene) = command(
        room,
        {
            group(it) { g -> g.copy(on = true) }
            activate(scene.id, roomId = it.id)
        },
    ) { call, _ -> client.recall(call, scene.id) }

    /**
     * Drops every room command still waiting, so none turns a room back on, then turns everything off (7 §4.4): the
     * whole home's group when no room still on the bridge is left out, else each other room's group and each light in no room.
     */
    override suspend fun allOff(leaveOut: Set<String>): AllOffResult {
        client.dropWaiting()
        return try {
            if (synchronized(lock) { groups.isEmpty() }) readAll()
            val (included, home, lights) = synchronized(lock) {
                val left = leaveOut.filter { id -> hueRooms.any { it.id == id } }.toSet()
                val home = groups.values.firstOrNull { it.ownerType == BRIDGE_HOME }?.takeIf { left.isEmpty() }
                Triple(hueRooms.filter { it.id !in left }, home, unroomedLights)
            }
            if (home != null) wholeHomeOff(home) else roomsOff(included, lights)
        } catch (e: LightsRefusedException) {
            AllOffResult.Refused
        } catch (e: LightsUnreachableException) {
            AllOffResult.CantReach(e.problem)
        }
    }

    private suspend fun wholeHomeOff(home: HueGroupedLight): AllOffResult {
        showThenSend({ groups.replaceAll { _, g -> g.copy(on = false) } }) { call -> client.setOn(call, home.id, false) }
        return AllOffResult.Done
    }

    /** Every room's command goes whatever the others do; the lights in no room count as one room when any fails. */
    private suspend fun roomsOff(rooms: List<HueRoom>, lights: List<String>): AllOffResult {
        val before = show { rooms.forEach { room -> group(room) { it.copy(on = false) } } }
        val (roomFailures, lightFailures) = coroutineScope {
            val roomSends = rooms.map { room -> async { attempt { client.setOn(it, room.groupedLightId, false) } } }
            val lightSends = lights.map { id -> async { attempt { client.setLightOn(it, id, false) } } }
            roomSends.awaitAll() to lightSends.awaitAll()
        }
        val failures = (roomFailures + lightFailures).filterNotNull()
        if (failures.isEmpty()) return AllOffResult.Done
        undo(before, rooms.zip(roomFailures).filter { it.second != null }.map { it.first.groupedLightId }.toSet())
        if (failures.size == rooms.size + lights.size && failures.all { it is LightsUnreachableException }) {
            return AllOffResult.CantReach((failures.first() as LightsUnreachableException).problem)
        }
        if (failures.any { it is LightsRefusedException }) rereadQuietly()
        val lightsLeftOn = if (lightFailures.any { it != null }) 1 else 0
        return AllOffResult.RoomsLeftOn(roomFailures.count { it != null } + lightsLeftOn)
    }

    override suspend fun automations(): List<Automation> =
        readBehaviors().mapNotNull { b -> b.name?.let { Automation(b.id, it, b.on) } }

    /** The bridge answers 200 even to a trigger it ignores, so only the read-back confirms it (7 §4.5). */
    override suspend fun setAutomation(automation: Automation, on: Boolean): AutomationResult {
        val behavior = behaviors[automation.id]
            ?: readBehaviors().firstOrNull { it.id == automation.id }
            ?: throw LightsRefusedException()
        val sent = onBridge { call -> client.setBehavior(call, behavior.id, behavior.isMimic, on) }
        if (!sent) return AutomationResult.NOT_CONFIRMED
        repeat(READ_BACK_TRIES) {
            delay(readBackMs)
            if (onBridge { client.behavior(it, behavior.id) }?.on == on) return AutomationResult.CONFIRMED
        }
        return AutomationResult.NOT_CONFIRMED
    }

    private suspend fun readBehaviors(): List<HueBehavior> =
        onBridge { client.behaviors(it) }.also { list -> behaviors = list.associateBy { it.id } }

    override fun useAddress(address: String) {
        link = link.copy(address = address)
    }

    private fun call(): BridgeCall = link.let { BridgeCall(it.address, it.bridgeId, it.key) }

    /** A command replaced while waiting its turn returns quietly, as the newer one shows its own value. */
    private suspend fun command(room: LightRoom, shown: (HueRoom) -> Unit, send: suspend (BridgeCall, HueRoom) -> Boolean) {
        val hue = synchronized(lock) { hueRooms.firstOrNull { it.id == room.id } }
        if (hue == null) {
            rereadQuietly()
            throw LightsRefusedException()
        }
        showThenSend({ shown(hue) }) { call -> send(call, hue) }
    }

    /**
     * Shows [shown] at once (7 §4.3), then sends. A refusal reads everything again, so the screen shows what the bridge
     * did; a bridge that can't be reached got nothing, so the screen goes back to how it was.
     */
    private suspend fun showThenSend(shown: () -> Unit, send: suspend (BridgeCall) -> Boolean) {
        val before = show(shown)
        try {
            onBridge(block = send)
        } catch (e: LightsRefusedException) {
            rereadQuietly()
            throw e
        } catch (e: LightsUnreachableException) {
            undo(before)
            throw e
        }
    }

    /** Null once sent; otherwise why it wasn't. */
    private suspend fun attempt(send: suspend (BridgeCall) -> Boolean): Exception? = try {
        onBridge(block = send)
        null
    } catch (e: LightsRefusedException) {
        e
    } catch (e: LightsUnreachableException) {
        e
    }

    private suspend fun rereadQuietly() = withContext(NonCancellable) {
        try {
            readAll()
        } catch (read: LightsRefusedException) {
            // Left as shown; the next full read corrects it.
        } catch (read: LightsUnreachableException) {
            // The problem is now set.
        }
    }

    /** Applies [block] to the state and publishes [rooms] from it, as one step. */
    private inline fun change(block: () -> Unit) = synchronized(lock) {
        block()
        publish()
    }

    /** The groups and scenes a change replaced, as they were before it. */
    private class Before(val groups: Map<String, HueGroupedLight>, val scenes: List<HueScene>)

    private inline fun show(block: () -> Unit): Before = synchronized(lock) {
        val groupsBefore = HashMap(groups)
        val scenesBefore = scenes
        block()
        publish()
        Before(groupsBefore.filter { (id, g) -> groups[id] != g }, scenesBefore.filter { it !in scenes })
    }

    /** Puts back what [before] replaced: only [groupIds]' groups when given, else every group and scene. */
    private fun undo(before: Before, groupIds: Set<String>? = null) = change {
        before.groups.forEach { (id, g) -> if (groupIds == null || id in groupIds) groups[id] = g }
        if (groupIds == null) {
            val old = before.scenes.associateBy { it.id }
            scenes = scenes.map { old[it.id] ?: it }
        }
    }

    // Under lock.
    private fun group(room: HueRoom, update: (HueGroupedLight) -> HueGroupedLight) {
        groups[room.groupedLightId]?.let { groups[room.groupedLightId] = update(it) }
    }

    // Under lock. A room shows one active scene: the one most recently made active.
    private fun activate(sceneId: String, roomId: String?, active: Boolean = true) {
        scenes = scenes.map {
            when {
                it.id == sceneId -> it.copy(active = active)
                active && it.roomId == roomId -> it.copy(active = false)
                else -> it
            }
        }
    }

    // Under lock.
    private fun apply(event: HueEvent) {
        when (event) {
            is HueEvent.GroupedLight -> groups[event.id]?.let {
                groups[event.id] = it.copy(on = event.on ?: it.on, brightness = event.brightness ?: it.brightness)
            }
            is HueEvent.Scene -> activate(event.id, scenes.firstOrNull { it.id == event.id }?.roomId, event.active)
            HueEvent.Reread -> Unit
        }
    }

    // Under lock.
    private fun publish() {
        _rooms.value = hueRooms.map { room ->
            val group = groups[room.groupedLightId]
            val own = scenes.filter { it.roomId == room.id }
            LightRoom(
                id = room.id,
                name = room.name,
                on = group?.on ?: false,
                brightness = group?.brightness?.roundToInt() ?: 0,
                scenes = own.map { LightScene(it.id, it.name) },
                activeSceneId = own.firstOrNull { it.active }?.id,
                dimmable = room.deviceIds.any { it in dimmingDevices },
            )
        }
    }

    /**
     * Runs [block] on the saved bridge. A success clears the problem. An answer that refuses, or isn't one of Hue's
     * shapes, is [LightsRefusedException] (7 §6). No answer, or another bridge's, searches for the bridge and, once it is
     * found at a new address, runs [block] there, or returns [movedResult] when given, as the search's full read there
     * did [block]'s work; only then is a problem set (7 §4.1, §6). Offline or not paired, it isn't searched for.
     */
    private suspend fun <T> onBridge(again: Boolean = true, movedResult: T? = null, block: suspend (BridgeCall) -> T): T {
        val call = call()
        return try {
            block(call).also { _problem.value = null }
        } catch (e: HueRefused) {
            throw LightsRefusedException()
        } catch (e: HueJsonException) {
            throw LightsRefusedException()
        } catch (e: HueUnauthorised) {
            fail(LightsProblem.UNPAIRED)
        } catch (e: IOException) {
            if (!online()) fail(LightsProblem.OFFLINE)
            when (if (again) recover(call) else Found.NOTHING) {
                Found.MOVED -> movedResult?.also { _problem.value = null } ?: onBridge(again = false, block = block)
                Found.ELSEWHERE -> onBridge(again = false, block = block)
                Found.OTHER -> fail(LightsProblem.OTHER_BRIDGE)
                Found.NOTHING -> fail(if (e is WrongBridge) LightsProblem.OTHER_BRIDGE else LightsProblem.UNREACHABLE)
            }
        }
    }

    /** [MOVED]: this search read everything at the new address; [ELSEWHERE]: the address changed while this call waited. */
    private enum class Found { MOVED, ELSEWHERE, OTHER, NOTHING }

    /**
     * Searches for the bridge [failed] went to. One with the same id counts as moved only once a full read at its address,
     * trusting only a certificate naming that id, has succeeded; only then is its address saved, with the key (7 §4.1).
     */
    private suspend fun recover(failed: BridgeCall): Found = recovering.withLock {
        if (link.address != failed.address) return@withLock Found.ELSEWHERE
        val now = clock.nowMillis()
        if (lastSearchMillis?.let { now - it in 0 until SEARCH_GAP_MS } == true) return@withLock Found.NOTHING
        lastSearchMillis = now
        val candidates = try {
            search(failed.bridgeId)
        } catch (e: LightsUnreachableException) {
            emptyList()
        } catch (e: IOException) {
            emptyList()
        }
        var other = false
        for (found in candidates) {
            if (!found.bridgeId.equals(failed.bridgeId, ignoreCase = true) || found.address == failed.address) continue
            try {
                read(failed.copy(address = found.address))
            } catch (e: WrongBridge) {
                other = true
                continue
            } catch (e: IOException) {
                continue
            } catch (e: HueRefused) {
                continue
            }
            link = link.copy(address = found.address)
            onMoved(link)
            return@withLock Found.MOVED
        }
        if (other) Found.OTHER else Found.NOTHING
    }

    private fun fail(problem: LightsProblem): Nothing {
        _problem.value = problem
        throw LightsUnreachableException(problem)
    }
}
