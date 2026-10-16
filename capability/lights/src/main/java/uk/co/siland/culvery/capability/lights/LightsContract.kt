package uk.co.siland.culvery.capability.lights

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import uk.co.siland.culvery.core.plugin.ProviderDescriptor

data class LightScene(val id: String, val name: String)

/** A Hue room as the bridge holds it (7 §3.3): [brightness] is 0 to 100, [dimmable] false when none of its lights dim. */
data class LightRoom(
    val id: String,
    val name: String,
    val on: Boolean,
    val brightness: Int,
    val scenes: List<LightScene>,
    val activeSceneId: String?,
    val dimmable: Boolean,
)

/** A Hue automation Holiday mode may start (7 §2.6); the provider decides what [on] means for it. */
data class Automation(val id: String, val name: String, val on: Boolean)

/** [CONFIRMED] when the bridge's read-back showed the new state (7 §4.5). */
enum class AutomationResult { CONFIRMED, NOT_CONFIRMED }

/** What the status line says is wrong (7 §6). */
enum class LightsProblem { UNREACHABLE, OFFLINE, UNPAIRED, OTHER_BRIDGE }

/** How All lights off went (7 §4.4); [Refused] and [CantReach] mean nothing changed. */
sealed interface AllOffResult {
    data object Done : AllOffResult

    data class RoomsLeftOn(val count: Int) : AllOffResult

    data object Refused : AllOffResult

    data class CantReach(val problem: LightsProblem) : AllOffResult
}

/** The bridge refused a command. */
class LightsRefusedException : Exception("The bridge refused the command")

/** The bridge can't be reached, the tablet is offline, or the bridge is not the paired one: [problem] says which. */
class LightsUnreachableException(val problem: LightsProblem) : Exception("The bridge can't be reached (${problem.name})")

data class BridgeFound(val bridgeId: String, val address: String)

/** A paired bridge and its application key; [toString] leaves the key out. */
data class LightsLink(val bridgeId: String, val address: String, val key: String) {
    override fun toString() = "LightsLink(bridgeId=$bridgeId, address=$address)"
}

/**
 * What a lights provider implements (7 §3.1). [rooms] and [problem] are kept by the system; the capability says when to
 * read and when to stream. Commands change [rooms] at once and throw [LightsRefusedException] or [LightsUnreachableException].
 */
interface LightsSystem {
    val rooms: StateFlow<List<LightRoom>>
    val problem: StateFlow<LightsProblem?>

    /** A full read of the rooms and scenes. */
    suspend fun readAll()

    /** Emits once the stream is open; collecting keeps it open and cancelling closes it, and it ends when the stream drops. */
    fun stream(): Flow<Unit>

    suspend fun setOn(room: LightRoom, on: Boolean)

    suspend fun setBrightness(room: LightRoom, percent: Int)

    suspend fun recallScene(room: LightRoom, scene: LightScene)

    /** Every room off except those in [leaveOut], by room id. */
    suspend fun allOff(leaveOut: Set<String>): AllOffResult

    /** The automations the bridge has; unnamed ones are left out. */
    suspend fun automations(): List<Automation>

    suspend fun setAutomation(automation: Automation, on: Boolean): AutomationResult

    /** Reads and commands go to the bridge at [address] from now on. */
    fun useAddress(address: String)
}

/** A way to the home's bridge (7 §3.1), for Settings › Lights: find it, check an address, pair, open the system. */
interface LightsProvider {
    val descriptor: ProviderDescriptor

    /** Looks for the bridge on the home Wi-Fi; null when none answered. Throws [LightsUnreachableException] offline. */
    suspend fun find(): BridgeFound?

    /** Asks the bridge at [address] for its id; null when nothing there answers as a bridge. */
    suspend fun check(address: String): BridgeFound?

    /** Tries once to pair; null when the bridge's button wasn't pressed. */
    suspend fun pair(bridge: BridgeFound): LightsLink?

    /** The system behind [link]; [onMoved] saves the address the system found the bridge at after a search. */
    fun open(link: LightsLink, onMoved: suspend (LightsLink) -> Unit): LightsSystem
}
