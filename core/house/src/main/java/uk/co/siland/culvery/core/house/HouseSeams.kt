package uk.co.siland.culvery.core.house

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.core.access.Authorised

/** What a device step did: [Failed.line] is the line to show. */
sealed interface HouseStep {
    data object Done : HouseStep

    /** [line] can hold plug names, so it is shown and never logged. */
    data class Failed(val line: String) : HouseStep
}

/** The failure lines the seams and Holiday mode's tile both show (11 §6). */
object HouseLines {
    const val ECO_ON_FAILED = "The heating didn’t go to Eco. Check the Nest app."
    const val ECO_OFF_FAILED = "The heating didn’t come off Eco. Turn it off in the Nest app."
    const val HOOVER_DIDNT_RUN = "The hoover didn’t run."
}

/** Whether a seam's device is chosen: false with no system, else true while its state is known. */
fun Flow<*>?.chosen(): Flow<Boolean> = this?.map { it != null } ?: flowOf(false)

/** What Holiday mode reaches in the house; each is optional, and one that isn't [ready] is no step (11 §5.1). */
interface HouseLights {
    /** True once a Hue link is saved. */
    val ready: Flow<Boolean>
    suspend fun setHoliday(on: Boolean, by: Authorised): HouseStep
}

interface HouseHeating {
    /** True once a thermostat is chosen. */
    val ready: Flow<Boolean>
    suspend fun setEco(on: Boolean): HouseStep
}

interface HouseHoover {
    /** True once a hoover is chosen. */
    val ready: Flow<Boolean>
    suspend fun start(): HouseStep
}

interface HouseMode {
    /** Null until it has been read. */
    val holiday: StateFlow<Boolean?>
}

/** A mode that can list plugs (12 §5.1): [order] sorts its section on the Plugs tab, [permission] is what turning it on takes. */
data class HouseModeInfo(val id: String, val name: String, val order: Int, val permission: String)

object HouseModes {
    const val HOLIDAY = "holiday"
}

/** A plug a mode will switch, or put back, to [on]. [toString] leaves out the name. */
data class PlugChange(val name: String, val on: Boolean) {
    override fun toString() = "PlugChange"
}

/** What a mode reaches in the plugs; absent when no plug system is bound (12 §5.1). */
interface HousePlugs {
    /** True while [modeId]'s list has an On or Off for an added plug and a plug system is bound. */
    fun ready(modeId: String): Flow<Boolean>
    suspend fun preview(modeId: String): List<PlugChange>

    /** Takes the mode's snapshot (12 §5.2); [by] must hold the mode's permission (12 §2.13). Switches nothing. */
    suspend fun begin(modeId: String, by: Authorised)

    /** Switches a begun mode's pending plugs, with no grant; null with no snapshot, and nothing switched. */
    suspend fun enter(modeId: String): HouseStep?
    suspend fun leave(modeId: String): HouseStep
}

interface MultiDayEvents {
    suspend fun around(today: LocalDate): List<MultiDayEvent>
}

/** An all-day event over two or more days, [last] being its last day. [toString] leaves out the title and the days. */
data class MultiDayEvent(val title: String, val first: LocalDate, val last: LocalDate) {
    override fun toString() = "MultiDayEvent"
}

object HousePermissions {
    const val HOLIDAY_MODE = "holiday.mode"
}
