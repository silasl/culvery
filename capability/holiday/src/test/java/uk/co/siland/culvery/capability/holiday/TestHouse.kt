package uk.co.siland.culvery.capability.holiday

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import uk.co.siland.culvery.core.access.Authorised
import uk.co.siland.culvery.core.house.HousePlugs
import uk.co.siland.culvery.core.house.HouseStep
import uk.co.siland.culvery.core.house.MultiDayEvent
import uk.co.siland.culvery.core.house.MultiDayEvents
import uk.co.siland.culvery.core.house.PlugChange
import uk.co.siland.culvery.core.plugin.WallClock

/** A real [HolidayHouse] over an in-memory store and fake seams, at 09:00 on Wednesday 28 October 2026. */
internal class HouseRig(scope: CoroutineScope) {
    val lights = FakeLights()
    val heating = FakeHeating()
    val hoover = FakeHoover()
    val plugs = FakePlugs()
    var now: LocalDateTime = TODAY.atTime(9, 0)
    val house = HolidayHouse(
        HolidaySettings(MemoryStore(), scope), lights, heating, hoover, plugs,
        WallClock { now.atZone(ZoneId.of("Europe/London")).toInstant().toEpochMilli() }, { now },
    )

    /** Reads the stored record, as the scheduler's first tick does. */
    suspend fun loaded(): HolidayHouse = house.also { it.tick() }

    companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 10, 28)
        val CORNWALL = MultiDayEvent("Cornwall", TODAY.minusDays(3), TODAY.plusDays(4))
    }
}

internal class FakeEvents(var events: List<MultiDayEvent> = emptyList()) : MultiDayEvents {
    override suspend fun around(today: LocalDate): List<MultiDayEvent> = events
}

/**
 * A plug seam that behaves as the real one does about runs: [begin] of a begun mode does nothing, [enter] of one never
 * begun is null, [leave] ends the run. [calls] holds "begin:holiday", "enter:holiday" and "leave:holiday" in order.
 * Plugs are off by default, so a rig's Holiday mode plans no plug step until a test turns [readyNow] on.
 */
internal class FakePlugs : HousePlugs {
    val readyNow = MutableStateFlow(false)
    @Volatile var changes: List<PlugChange> = emptyList()
    val begun: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    val begins = CopyOnWriteArrayList<Authorised>()
    val enters = ConcurrentLinkedQueue<HouseStep?>()
    val leaves = ConcurrentLinkedQueue<HouseStep>()
    val calls = CopyOnWriteArrayList<String>()
    @Volatile var enterOtherwise: HouseStep? = HouseStep.Done
    @Volatile var leaveOtherwise: HouseStep = HouseStep.Done
    @Volatile var throwOnBegin = false
    @Volatile var throwOnEnter = false
    @Volatile var throwOnLeave = false
    @Volatile var onCall: suspend (String) -> Unit = {}

    override fun ready(modeId: String): Flow<Boolean> = readyNow

    override suspend fun preview(modeId: String): List<PlugChange> = changes

    override suspend fun begin(modeId: String, by: Authorised) {
        record("begin:$modeId")
        check(!throwOnBegin) { "The store is full" }
        if (begun.add(modeId)) begins += by
    }

    override suspend fun enter(modeId: String): HouseStep? {
        record("enter:$modeId")
        check(!throwOnEnter) { "Home Assistant is down" }
        if (modeId !in begun) return null
        return enters.poll() ?: enterOtherwise
    }

    override suspend fun leave(modeId: String): HouseStep {
        record("leave:$modeId")
        check(!throwOnLeave) { "Home Assistant is down" }
        begun -= modeId
        return leaves.poll() ?: leaveOtherwise
    }

    private suspend fun record(call: String) {
        calls += call
        onCall(call)
    }
}
