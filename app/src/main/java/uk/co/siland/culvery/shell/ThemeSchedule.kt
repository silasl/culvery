package uk.co.siland.culvery.shell

import android.util.Log
import java.time.LocalTime
import java.util.Optional
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import uk.co.siland.culvery.core.plugin.ApplicationScope
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

/** Today's sun times for the whole app, from one reader of Daylight; null without Daylight or until they're known. */
@Singleton
class SunToday(daylight: Daylight?, scope: CoroutineScope) {
    @Inject
    constructor(daylight: Optional<Daylight>, @ApplicationScope scope: CoroutineScope) : this(daylight.orElse(null), scope)

    val times: StateFlow<SunTimes?> = (
        daylight?.today?.retryWithBackoff { Log.w(TAG, "Couldn't read today's sun times (${it::class.simpleName}); retrying") }
            ?: flowOf(null)
        ).stateIn(scope, SharingStarted.Eagerly, null)
}
