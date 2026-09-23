package uk.co.siland.househub.shell

import java.time.LocalTime
import uk.co.siland.househub.core.plugin.SunTimes

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
