package uk.co.siland.culvery.provider.lights_hue

import java.io.IOException
import kotlin.math.roundToInt
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
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

/**
 * The household's Hue rooms through the bridge's CLIP v2 API (7 §4.2, §4.3): a full read builds them in Hue's order,
 * the stream keeps them up to date, and a command shows at once. Every call is built from the saved bridge id, so only
 * a certificate naming it is trusted. Every change to what the screen shows is made, and [rooms] published, under one lock.
 */
internal class HueSystem(
    private val client: HueClient,
    private val search: suspend () -> List<BridgeFound>,
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

    override suspend fun readAll() = refusing { reaching { read() } }

    private suspend fun read() = coroutineScope {
        val call = call()
        val roomsRead = async { client.rooms(call) }
        val groupsRead = async { client.groupedLights(call) }
        val lightsRead = async { client.lights(call) }
        val scenesRead = async { client.scenes(call) }
        val bridgeId = async { client.bridgeId(call) }
        if (!bridgeId.await().equals(call.bridgeId, ignoreCase = true)) throw WrongBridge()
        val read = roomsRead.await()
        val groupsNow = groupsRead.await()
        val dimming = lightsRead.await().filter { it.dimmable }.map { it.deviceId }.toSet()
        val scenesNow = scenesRead.await()
        change {
            hueRooms = read
            groups.clear()
            groupsNow.forEach { groups[it.id] = it }
            scenes = scenesNow
            dimmingDevices = dimming
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

    override suspend fun setBrightness(room: LightRoom, percent: Int) {
        val brightness = percent.coerceIn(0, 100)
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

    override suspend fun allOff(leaveOut: Set<String>): AllOffResult = throw UnsupportedOperationException()

    override suspend fun automations(): List<Automation> = throw UnsupportedOperationException()

    override suspend fun setAutomation(automation: Automation, on: Boolean): AutomationResult = throw UnsupportedOperationException()

    override fun useAddress(address: String) {
        link = link.copy(address = address)
    }

    private fun call(): BridgeCall = link.let { BridgeCall(it.address, it.bridgeId, it.key) }

    /**
     * Shows [shown] at once (7 §4.3), then sends; a command replaced while waiting its turn returns quietly, as the newer
     * one shows its own value. A refusal reads everything again, so the room shows what the bridge did.
     */
    private suspend fun command(room: LightRoom, shown: (HueRoom) -> Unit, send: suspend (BridgeCall, HueRoom) -> Boolean) {
        try {
            val hue = synchronized(lock) { hueRooms.firstOrNull { it.id == room.id } } ?: throw LightsRefusedException()
            change { shown(hue) }
            refusing { reaching { send(call(), hue) } }
        } catch (e: LightsRefusedException) {
            withContext(NonCancellable) {
                try {
                    readAll()
                } catch (read: LightsRefusedException) {
                    // Left as shown; the next full read corrects it.
                } catch (read: LightsUnreachableException) {
                    // The problem is now set.
                }
            }
            throw e
        }
    }

    /** Applies [block] to the state and publishes [rooms] from it, as one step. */
    private inline fun change(block: () -> Unit) = synchronized(lock) {
        block()
        publish()
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

    /** A bridge that answered but didn't do what was asked, or answered in a shape that isn't Hue's (7 §6). */
    private suspend fun <T> refusing(block: suspend () -> T): T = try {
        block()
    } catch (e: HueRefused) {
        throw LightsRefusedException()
    } catch (e: HueJsonException) {
        throw LightsRefusedException()
    }

    /** A call that succeeds clears the problem; one the bridge doesn't answer, or answers as not paired, sets it (7 §6). */
    private suspend fun <T> reaching(block: suspend () -> T): T = try {
        block().also { _problem.value = null }
    } catch (e: HueJsonException) {
        // An answer, though not one of Hue's shapes: refused, not unreachable.
        throw e
    } catch (e: HueUnauthorised) {
        fail(LightsProblem.UNPAIRED)
    } catch (e: WrongBridge) {
        fail(LightsProblem.OTHER_BRIDGE)
    } catch (e: IOException) {
        fail(if (online()) LightsProblem.UNREACHABLE else LightsProblem.OFFLINE)
    }

    private fun fail(problem: LightsProblem): Nothing {
        _problem.value = problem
        throw LightsUnreachableException(problem)
    }
}
