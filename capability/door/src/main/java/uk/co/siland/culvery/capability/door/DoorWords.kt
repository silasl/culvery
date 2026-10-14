package uk.co.siland.culvery.capability.door

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import uk.co.siland.culvery.core.plugin.RailBadge
import uk.co.siland.culvery.core.ui.plural

/** 5 §5.3: "Door, new motion". */
internal const val NEW_MOTION = "new motion"

/** Unseen rings as a count; unseen motion alone as a dot; else none (5 §4.8). */
fun badgeOf(unseen: Unseen): RailBadge? = when {
    unseen.rings > 0 -> RailBadge.Count(unseen.rings, plural(unseen.rings.toLong(), "unseen ring"))
    unseen.motion > 0 -> RailBadge.Dot(NEW_MOTION)
    else -> null
}

internal const val SOMEONE_AT_THE_DOOR = "Someone’s at the door"
internal const val RING_ALERT = "Ring alert"
internal const val GOT_IT = "Got it"

private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

fun clockText(time: LocalTime): String = time.format(CLOCK)

/** "14:02" on the household's clock. */
fun clockTime(atMillis: Long, zone: ZoneId): String = clockText(Instant.ofEpochMilli(atMillis).atZone(zone).toLocalTime())

/** The card's spoken words: what, where, when (5 §5.1), leaving out a device Ring did not name. */
fun cardWords(title: String, device: String, time: String): String = listOf(title, device, time).filter { it.isNotEmpty() }.joinToString(", ")
