package uk.co.siland.culvery.shell

import android.util.Log
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.plugin.retryWithBackoff
import uk.co.siland.culvery.core.plugin.wallTimeEachMinute

/** The wall time, each minute; `AppModule` gives it in the household's zone. */
fun interface MinuteTicker {
    fun ticks(): Flow<LocalDateTime>
}

/**
 * The wall time in the latest of [zones] (4b design §3.8). The zone is read from Room, which can fail: the read is
 * retried, so the clock never stops and `ShellViewModel.now` never sees the failure (plan review 1).
 */
internal fun householdTicker(zones: Flow<ZoneId>, clock: WallClock): MinuteTicker = MinuteTicker {
    val retried = zones.retryWithBackoff { Log.w(TAG, "Couldn't read the household's time zone (${it::class.simpleName}); retrying") }
    wallTimeEachMinute(retried, clock)
}

private const val TAG = "MinuteTicker"
