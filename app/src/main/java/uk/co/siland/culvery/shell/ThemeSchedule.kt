package uk.co.siland.culvery.shell

import android.util.Log
import java.time.LocalTime
import java.util.Optional
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import uk.co.siland.culvery.core.plugin.Daylight
import uk.co.siland.culvery.core.plugin.SunTimes
import uk.co.siland.culvery.core.plugin.retryWithBackoff

private const val TAG = "ThemeSchedule"

object ThemeSchedule {
    val DEFAULT_DAY_START: LocalTime = LocalTime.of(7, 0)
    val DEFAULT_DAY_END: LocalTime = LocalTime.of(19, 0)

    fun isDark(now: LocalTime, sun: SunTimes?): Boolean {
        val usable = sun?.takeIf { it.sunrise < it.sunset }
        val start = usable?.sunrise ?: DEFAULT_DAY_START
        val end = usable?.sunset ?: DEFAULT_DAY_END
        return now < start || now >= end
    }
}

/** Today's sun times, null without Daylight or until they're known. */
internal fun sunToday(daylight: Optional<Daylight>): Flow<SunTimes?> = daylight.orElse(null)?.today
    ?.retryWithBackoff { Log.w(TAG, "Couldn't read today's sun times (${it::class.simpleName}); retrying") }
    ?.onStart { emit(null) }
    ?: flowOf(null)
