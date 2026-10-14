package uk.co.siland.culvery.capability.door

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Quiet from [from] until just before [until], across midnight when [until] is earlier (5 §4.1); never when they're equal. */
fun isQuiet(now: LocalTime, from: LocalTime, until: LocalTime): Boolean = when {
    from == until -> false
    from < until -> now >= from && now < until
    else -> now >= from || now < until
}

/** On the household's wall clock, so an hour the clocks repeat is quiet both times. */
internal fun quietAt(atMillis: Long, zone: ZoneId, prefs: DoorPrefs): Boolean =
    prefs.quietOn && isQuiet(Instant.ofEpochMilli(atMillis).atZone(zone).toLocalTime(), prefs.quietFrom, prefs.quietUntil)
