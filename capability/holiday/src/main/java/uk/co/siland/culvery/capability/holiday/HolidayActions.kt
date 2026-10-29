package uk.co.siland.culvery.capability.holiday

import android.util.Log
import java.time.LocalDate
import java.util.Optional
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.Authorised
import uk.co.siland.culvery.core.house.HouseModeSwitch
import uk.co.siland.culvery.core.house.HousePermissions
import uk.co.siland.culvery.core.house.ModeRefusal
import uk.co.siland.culvery.core.house.MultiDayEvents
import uk.co.siland.culvery.core.plugin.HouseholdClock
import uk.co.siland.culvery.core.plugin.attempt

private const val TAG = "HolidayActions"

/** What the Holiday mode tile does (11 §5.2): a fresh Adult or Admin PIN each time, to open the sheet or to turn off. */
@Singleton
class HolidayActions internal constructor(
    private val access: AccessControl,
    private val house: HolidayHouse,
    private val events: MultiDayEvents?,
    switches: Set<HouseModeSwitch>,
    private val todayNow: () -> LocalDate,
) {
    @Inject
    constructor(
        access: AccessControl,
        house: HolidayHouse,
        events: Optional<MultiDayEvents>,
        switches: Set<@JvmSuppressWildcards HouseModeSwitch>,
        clock: HouseholdClock,
    ) : this(access, house, events.orElse(null), switches, { clock.now.value?.toLocalDate() ?: LocalDate.now() })

    /** "Turn Party mode off first." while a tap or confirm was refused and Party mode is still on. */
    val refusal = ModeRefusal(house, switches)

    fun today(): LocalDate = todayNow()

    /** Null when the pad was cancelled or refused; nothing changes then. */
    suspend fun ask(): Authorised? = access.authorise(HousePermissions.HOLIDAY_MODE)

    /** The calendar's guess at the back date; none without a calendar, an event that fits, or an answer. */
    suspend fun pick(): BackDatePick? {
        val today = today()
        val calendar = events ?: return null
        return attempt({ backDatePick(calendar.around(today), today) }) { e ->
            Log.w(TAG, "Couldn't read the calendar for the back date (${e::class.simpleName})")
            null
        }
    }

    /**
     * Off: [open] the sheet once the PIN is right. On: turn off once it is. Nothing is asked before the record is read,
     * while a change runs, since the house drops a turn-off that meets a turn-on, or when turning on is refused because
     * Party mode is on.
     */
    suspend fun toggle(open: (Authorised) -> Unit) {
        if (house.running.value != null) return
        val on = house.holiday.value ?: return
        if (refusal.refusesTap(turningOn = !on)) return
        val by = ask() ?: return
        // A turn-off cut short by the tile leaving Home would leave some parts changed and the record on.
        if (on) withContext(NonCancellable) { house.turnOff(by) } else open(by)
    }

    /** False, with the refusal showing, when Party mode went on after the sheet opened. */
    suspend fun turnOn(back: LocalDate, by: Authorised): Boolean = refusal.turnOnUnlessRefused { house.turnOn(back, by) }
}
