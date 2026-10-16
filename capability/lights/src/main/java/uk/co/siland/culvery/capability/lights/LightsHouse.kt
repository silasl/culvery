package uk.co.siland.culvery.capability.lights

import android.util.Log
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import uk.co.siland.culvery.core.access.Authorised

private const val TAG = "LightsHouse"

/** What the one house-wide run is doing (7 §4.5): the Holiday mode button says "Turning on…" or "Turning off…". */
enum class HouseRun { TURNING_ON, TURNING_OFF, ALL_OFF }

/** How a Holiday mode run went (7 §4.5); [Busy] is a tap ignored while another run holds the house. */
sealed interface HolidayResult {
    data object Done : HolidayResult

    data object Busy : HolidayResult

    /** Nothing changed. */
    data class CantReach(val problem: LightsProblem) : HolidayResult

    /** Holiday mode is on, but [roomsLeftOn] rooms stayed on, or the automations in [notStarted], by id, didn't start. */
    data class Partly(val roomsLeftOn: Int, val notStarted: List<String>) : HolidayResult

    /** Hue refused: All lights off when turning on (nothing changed), or a stop when turning off (still on). */
    data object Refused : HolidayResult
}

/**
 * All lights off and Holiday mode (7 §4.4, §4.5), one run at a time across both: a call while another runs is ignored.
 * Holiday mode's on/off and the automations it started are Culvery's own record, whatever the bridge says later.
 */
@Singleton
class LightsHouse internal constructor(
    private val system: () -> LightsSystem?,
    private val settings: LightsSettings,
) {
    @Inject
    constructor(bridges: Bridges, settings: LightsSettings) : this({ bridges.system.value }, settings)

    private val mutex = Mutex()
    private val _running = MutableStateFlow<HouseRun?>(null)
    val running: StateFlow<HouseRun?> = _running.asStateFlow()

    @Volatile
    private var lastList: List<Automation> = emptyList()

    /** The automation's name in the last list read from the bridge; null when it wasn't in it. */
    fun nameOf(automationId: String): String? = lastList.firstOrNull { it.id == automationId }?.name

    /** Every room off except those Settings › Lights leaves out; null when another run holds the house. */
    suspend fun allOff(): AllOffResult? = holding({ HouseRun.ALL_OFF }) { prefs, _ -> offExcept(prefs.leftOut) }

    /** Turns Holiday mode [on] or off; null turns it the other way from the saved record, as read once this run holds the house. */
    suspend fun setHoliday(on: Boolean?, by: Authorised): HolidayResult {
        require(LightsPermissions.HOLIDAY in by.granted) { "Holiday mode needs ${LightsPermissions.HOLIDAY}" }
        return holding({ if (on ?: !it.holidayOn) HouseRun.TURNING_ON else HouseRun.TURNING_OFF }) { prefs, run ->
            if (run == HouseRun.TURNING_ON) turnOn(prefs) else turnOff(prefs)
        } ?: HolidayResult.Busy
    }

    private suspend fun <T> holding(run: (LightsPrefs) -> HouseRun, block: suspend (LightsPrefs, HouseRun) -> T): T? {
        if (!mutex.tryLock()) return null
        try {
            val prefs = settings.prefs.first()
            val running = run(prefs)
            _running.value = running
            return block(prefs, running)
        } finally {
            _running.value = null
            mutex.unlock()
        }
    }

    private suspend fun offExcept(leftOut: Set<String>): AllOffResult {
        val s = system() ?: return AllOffResult.CantReach(LightsProblem.UNREACHABLE)
        return try {
            s.allOff(leftOut)
        } catch (e: LightsUnreachableException) {
            failed(LightsCommand.ALL_OFF, e)
            AllOffResult.CantReach(e.problem)
        } catch (e: LightsRefusedException) {
            failed(LightsCommand.ALL_OFF, e)
            AllOffResult.Refused
        }
    }

    private suspend fun turnOn(prefs: LightsPrefs): HolidayResult {
        val roomsLeftOn = when (val off = offExcept(prefs.leftOut)) {
            AllOffResult.Done -> 0
            is AllOffResult.RoomsLeftOn -> off.count
            AllOffResult.Refused -> return HolidayResult.Refused
            is AllOffResult.CantReach -> return HolidayResult.CantReach(off.problem)
        }
        save { settings.setHolidayOn(true) }
        val notStarted = if (prefs.holidayAutomations.isEmpty()) emptyList() else start(prefs)
        return if (roomsLeftOn == 0 && notStarted.isEmpty()) HolidayResult.Done else HolidayResult.Partly(roomsLeftOn, notStarted)
    }

    /** Starts each chosen automation that is off, recording each one confirmed; returns the ids that didn't start. */
    private suspend fun start(prefs: LightsPrefs): List<String> {
        val chosen = prefs.holidayAutomations
        val s = system() ?: return chosen.toList()
        val list = try {
            s.automations().also { lastList = it }
        } catch (e: LightsUnreachableException) {
            failed(LightsCommand.HOLIDAY_ON, e)
            return chosen.toList()
        } catch (e: LightsRefusedException) {
            failed(LightsCommand.HOLIDAY_ON, e)
            return chosen.toList()
        }
        val toStart = list.filter { it.id in chosen && !it.on }
        var started = prefs.holidayStarted
        val notStarted = mutableListOf<String>()
        for ((i, automation) in toStart.withIndex()) {
            val result = try {
                s.setAutomation(automation, true)
            } catch (e: LightsRefusedException) {
                failed(LightsCommand.HOLIDAY_ON, e)
                AutomationResult.NOT_CONFIRMED
            } catch (e: LightsUnreachableException) {
                failed(LightsCommand.HOLIDAY_ON, e)
                notStarted += toStart.drop(i).map { it.id }
                break
            }
            if (result == AutomationResult.CONFIRMED) {
                started = started + automation.id
                val record = started
                save { settings.setHolidayStarted(record) }
            } else {
                notStarted += automation.id
            }
        }
        return notStarted
    }

    /** Stops exactly what Culvery started (7 §4.5); saved off only when every one still on the bridge is confirmed off. */
    private suspend fun turnOff(prefs: LightsPrefs): HolidayResult {
        if (prefs.holidayStarted.isEmpty()) {
            save { settings.setHolidayOn(false) }
            return HolidayResult.Done
        }
        val s = system() ?: return HolidayResult.CantReach(LightsProblem.UNREACHABLE)
        val list = try {
            s.automations().also { lastList = it }
        } catch (e: LightsUnreachableException) {
            failed(LightsCommand.HOLIDAY_OFF, e)
            return HolidayResult.CantReach(e.problem)
        } catch (e: LightsRefusedException) {
            failed(LightsCommand.HOLIDAY_OFF, e)
            return HolidayResult.Refused
        }
        val toStop = list.filter { it.id in prefs.holidayStarted && it.on }
        val kept = toStop.map { it.id }.toMutableSet()
        var unreachable: LightsProblem? = null
        for (automation in toStop) {
            val result = try {
                s.setAutomation(automation, false)
            } catch (e: LightsRefusedException) {
                failed(LightsCommand.HOLIDAY_OFF, e)
                AutomationResult.NOT_CONFIRMED
            } catch (e: LightsUnreachableException) {
                failed(LightsCommand.HOLIDAY_OFF, e)
                unreachable = e.problem
                break
            }
            if (result == AutomationResult.CONFIRMED) kept -= automation.id
        }
        save { settings.setHolidayStarted(kept) }
        unreachable?.let { return HolidayResult.CantReach(it) }
        if (kept.isNotEmpty()) return HolidayResult.Refused
        save { settings.setHolidayOn(false) }
        return HolidayResult.Done
    }

    private fun failed(command: LightsCommand, e: Exception) = Log.w(TAG, "${command.name} failed (${e::class.simpleName})")

    private suspend fun save(write: suspend () -> Unit) {
        try {
            write()
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't save Holiday mode (${e::class.simpleName})")
        }
    }
}
