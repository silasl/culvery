package uk.co.siland.culvery.capability.party

import java.util.Optional
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import uk.co.siland.culvery.core.house.DoorQuiet
import uk.co.siland.culvery.core.house.HouseMode
import uk.co.siland.culvery.core.plugin.NightHours
import uk.co.siland.culvery.core.plugin.WallClock

private const val MINUTE_MS = 60_000L

/** Why the sound pads are greyed out, in the order it is checked; [line] shows under the row (18 §7, Ruling 6). */
enum class PadBlock(val line: String) {
    DOORBELL("Doorbell"),
    NIGHT("Night"),
    HOLIDAY("Holiday on"),
    QUIET("Quiet hours"),
}

/** When the sound pads may play (18 §7): not while the ring card shows, at night, on Holiday, or in the door's quiet hours. */
class PadGate @Inject constructor(
    private val door: Optional<DoorQuiet>,
    private val house: HouseMode,
    private val night: NightHours,
    private val clock: WallClock,
) {
    /** Null when the pads may play. A night not known yet counts as night, as the door counts it. */
    suspend fun blockedNow(): PadBlock? {
        val door = door.orElse(null)
        return when {
            door != null && door.ringShowing.first() -> PadBlock.DOORBELL
            night.isNight() != false -> PadBlock.NIGHT
            house.holiday.value == true -> PadBlock.HOLIDAY
            door != null && door.quietNow() -> PadBlock.QUIET
            else -> null
        }
    }

    /** [blockedNow], read again at each whole minute of the wall clock and whenever the ring card or Holiday changes. */
    val blocked: Flow<PadBlock?> = merge<Any?>(
        flow {
            while (true) {
                emit(Unit)
                delay(MINUTE_MS - Math.floorMod(clock.nowMillis(), MINUTE_MS))
            }
        },
        door.map { it.ringShowing }.orElse(emptyFlow()),
        house.holiday,
    ).map { blockedNow() }.distinctUntilChanged()
}
