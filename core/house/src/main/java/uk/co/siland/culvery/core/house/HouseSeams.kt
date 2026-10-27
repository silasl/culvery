package uk.co.siland.culvery.core.house

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import uk.co.siland.culvery.core.access.Authorised

/** What a device step did: [Failed.line] is the line to show. */
sealed interface HouseStep {
    data object Done : HouseStep
    data class Failed(val line: String) : HouseStep
}

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

interface MultiDayEvents {
    suspend fun around(today: LocalDate): List<MultiDayEvent>
}

/** An all-day event over two or more days, [last] being its last day. [toString] leaves out [title]. */
data class MultiDayEvent(val title: String, val first: LocalDate, val last: LocalDate) {
    override fun toString() = "MultiDayEvent(first=$first, last=$last)"
}

object HousePermissions {
    const val HOLIDAY_MODE = "holiday.mode"
}
