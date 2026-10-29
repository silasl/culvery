package uk.co.siland.culvery.capability.lights

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.Authorised
import uk.co.siland.culvery.core.house.HousePermissions
import uk.co.siland.culvery.core.access.CorePermissionSource
import uk.co.siland.culvery.core.access.Identified
import uk.co.siland.culvery.core.access.PermissionRegistry
import uk.co.siland.culvery.core.access.PinReason
import uk.co.siland.culvery.core.access.Refusal
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.ProviderDescriptor
import uk.co.siland.culvery.core.plugin.settingsDataStore
import uk.co.siland.culvery.core.ui.Icons

internal val RELAX = LightScene("relax-id", "Relax")
internal val BRIGHT = LightScene("bright-id", "Bright")
internal val MIMIC = Automation("mimic-id", "Mimic presence", on = false)
internal val AWAY = Automation("away-id", "Away from home", on = false)

/** A room named [name]; its id is the name with "-id". */
internal fun room(
    name: String,
    on: Boolean = false,
    brightness: Int = 70,
    scenes: List<LightScene> = emptyList(),
    active: String? = null,
    dimmable: Boolean = true,
) = LightRoom("$name-id", name, on, brightness, scenes, active, dimmable)

internal fun link(bridgeId: String = "001788fffe00a1b2", address: String = "192.168.1.30", key: String = "test-key") =
    LightsLink(bridgeId, address, key)

internal fun lightsSettings(dir: File, scope: CoroutineScope) =
    LightsSettings(settingsDataStore(scope) { File(dir, "lights_settings.preferences_pb") })

/** A test's folder, a scope on Dispatchers.IO cancelled after the test, and [settings] kept in that folder. */
class LightsFixture : TestRule {
    private val folder = TemporaryFolder()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val settings by lazy { lightsSettings(folder.root, scope) }

    override fun apply(base: Statement, description: Description): Statement = folder.apply(
        object : Statement() {
            override fun evaluate() = try {
                base.evaluate()
            } finally {
                scope.cancel()
            }
        },
        description,
    )
}

/** A lights system a test drives: its rooms and problem, the calls made of it in order, and calls that fail or wait. */
internal class TestLightsSystem(rooms: List<LightRoom> = emptyList()) : LightsSystem {
    override val rooms = MutableStateFlow(rooms)
    override val problem = MutableStateFlow<LightsProblem?>(null)

    /** Written from whatever thread a command runs on; read by the test: "setOn Lounge-id true", "allOff [Garden-id]". */
    val calls: MutableList<String> = CopyOnWriteArrayList()

    /** What a call throws, by its name as [calls] writes it, every time. */
    val failures: MutableMap<String, Exception> = ConcurrentHashMap()

    /** What a call throws the first time only. */
    val failOnce: MutableMap<String, Exception> = ConcurrentHashMap()

    /** Holds a call, by its name, until completed. */
    val gates: MutableMap<String, CompletableDeferred<Unit>> = ConcurrentHashMap()

    @Volatile
    var automationList: List<Automation> = listOf(MIMIC, AWAY)

    /** What setAutomation answers, by automation id; one not here is confirmed. */
    val results: MutableMap<String, AutomationResult> = ConcurrentHashMap()
    var allOffAnswer: AllOffResult = AllOffResult.Done

    /** One per open stream, in order; completing one ends that stream. */
    val streams: MutableList<CompletableDeferred<Unit>> = CopyOnWriteArrayList()

    /** Each address the system was told to use, in order. */
    val addresses = MutableStateFlow<List<String>>(emptyList())

    private suspend fun record(call: String) {
        calls += call
        gates[call]?.await()
        failures[call]?.let { throw it }
        failOnce.remove(call)?.let { throw it }
    }

    /** Runs as a read completes, to change what the bridge shows. */
    @Volatile
    var onRead: (() -> Unit)? = null

    override suspend fun readAll() {
        record("readAll")
        onRead?.invoke()
    }

    override fun stream(): Flow<Unit> = flow {
        calls += "stream"
        val ended = CompletableDeferred<Unit>().also { streams += it }
        try {
            emit(Unit)
            ended.await()
        } finally {
            calls += "stream closed"
        }
    }

    override suspend fun setOn(room: LightRoom, on: Boolean) = record("setOn ${room.id} $on")

    override suspend fun setBrightness(room: LightRoom, percent: Int) = record("setBrightness ${room.id} $percent")

    override suspend fun recallScene(room: LightRoom, scene: LightScene) = record("recallScene ${room.id} ${scene.id}")

    override suspend fun allOff(leaveOut: Set<String>): AllOffResult {
        record("allOff ${leaveOut.sorted()}")
        return allOffAnswer
    }

    override suspend fun automations(): List<Automation> {
        record("automations")
        return automationList
    }

    override suspend fun setAutomation(automation: Automation, on: Boolean): AutomationResult {
        record("setAutomation ${automation.id} $on")
        val result = results[automation.id] ?: AutomationResult.CONFIRMED
        if (result == AutomationResult.CONFIRMED) {
            automationList = automationList.map { if (it.id == automation.id) it.copy(on = on) else it }
        }
        return result
    }

    override fun useAddress(address: String) {
        addresses.value += address
    }
}

/** A provider whose answers a test sets; it opens a [TestLightsSystem] and keeps each. */
internal class TestLightsProvider(id: String = "lights.hue", connectable: Boolean = true) : LightsProvider {
    override val descriptor = ProviderDescriptor(id, "Hue", Icons.LIGHTBULB, setOf(Feature.READ, Feature.WRITE), userConnectable = connectable)
    var found: BridgeFound? = null

    /** What check(address) answers, by address. */
    val bridges = mutableMapOf<String, BridgeFound>()

    /** What each pair() answers, in order; null is a button not pressed. Empty answers null. */
    val pairAnswers = ArrayDeque<LightsLink?>()

    /** Thrown by find(), check() and pair(): offline, say. */
    var failure: Exception? = null
    val asked = mutableListOf<String>()
    val opened = mutableListOf<LightsLink>()
    val systems = mutableListOf<TestLightsSystem>()
    var onMoved: (suspend (LightsLink) -> Unit)? = null

    override suspend fun find(): BridgeFound? {
        asked += "find"
        failure?.let { throw it }
        return found
    }

    override suspend fun check(address: String): BridgeFound? {
        asked += "check $address"
        failure?.let { throw it }
        return bridges[address]
    }

    override suspend fun pair(bridge: BridgeFound): LightsLink? {
        asked += "pair"
        failure?.let { throw it }
        return pairAnswers.removeFirstOrNull()
    }

    override fun open(link: LightsLink, onMoved: suspend (LightsLink) -> Unit): LightsSystem {
        opened += link
        this.onMoved = onMoved
        return TestLightsSystem().also { systems += it }
    }
}

private val person = Person(PersonId("p"), "P", 0)

/** Someone who entered an Adult PIN for Holiday lights. */
internal fun adult() = Authorised(person, Role.ADULT, setOf(LightsPermissions.HOLIDAY))

/** Someone who entered an Adult PIN for the house's Holiday mode. */
internal fun houseAdult() = Authorised(person, Role.ADULT, setOf(HousePermissions.HOLIDAY_MODE))

/** Someone identified who holds nothing. */
internal fun nobody() = Authorised(person, Role.ADULT, emptySet())

/** A PIN pad answered by someone with [role] (null: cancelled); [asked] holds each call's permissions in order. */
internal class TestAccess(var role: Role?) : AccessControl {
    private val registry = PermissionRegistry(setOf(CorePermissionSource(), LightsPermissionSource()))
    val asked: MutableList<List<String>> = CopyOnWriteArrayList()

    override val session: StateFlow<Identified?> = MutableStateFlow(null)
    override val expiresAt: StateFlow<Long?> = MutableStateFlow(null)

    override suspend fun authorise(
        vararg anyOf: String,
        reason: PinReason,
        allow: (Identified, Set<String>) -> Boolean,
        refusal: Refusal,
        label: String?,
    ): Authorised? {
        asked += anyOf.toList()
        val role = role ?: return null
        val granted = anyOf.filter { registry.isGranted(role, it) }.toSet()
        return if (granted.isEmpty()) null else Authorised(person, role, granted)
    }

    override fun beginSetupSession(person: Identified) = Unit

    override fun endSetupSession() = Unit

    override fun touch(holdForEdits: Boolean) = Unit

    override fun checkExpiry() = Unit

    override fun lock() = Unit
}



