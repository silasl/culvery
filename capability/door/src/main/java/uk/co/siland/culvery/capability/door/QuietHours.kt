package uk.co.siland.culvery.capability.door

import java.time.Instant
import java.time.ZoneId
import uk.co.siland.culvery.core.plugin.inDailyWindow

/** Quiet from its start until just before its end, across midnight (5 §4.1), on the household's wall clock, so an hour the clocks repeat is quiet both times. */
internal fun quietAt(atMillis: Long, zone: ZoneId, prefs: DoorPrefs): Boolean =
    prefs.quietOn && inDailyWindow(Instant.ofEpochMilli(atMillis).atZone(zone).toLocalTime(), prefs.quietFrom, prefs.quietUntil)
