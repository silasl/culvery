package uk.co.siland.culvery.capability.door

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import uk.co.siland.culvery.core.plugin.RailBadge
import uk.co.siland.culvery.core.ui.clockText
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

/** "14:02" on the household's clock. */
fun clockTime(atMillis: Long, zone: ZoneId): String = clockText(Instant.ofEpochMilli(atMillis).atZone(zone).toLocalTime())

/** The card's spoken words: what, where, when (5 §5.1), leaving out a device Ring did not name. */
fun cardWords(title: String, device: String, time: String): String = listOf(title, device, time).filter { it.isNotEmpty() }.joinToString(", ")

internal const val TODAY = "Today"
internal const val YESTERDAY = "Yesterday"
internal const val NO_RINGS_TODAY = "No rings or motion today."
internal const val SNAPSHOT = "Snapshot"
internal const val SHOW_FULL_SIZE = "show it full size"

/** "Ring", "Motion", or "Ring alert" for one the parser couldn't place (5 §5.2). */
fun kindLabel(event: DoorEvent): String = when {
    event.kind == DoorKind.MOTION -> "Motion"
    event.unrecognised -> RING_ALERT
    else -> "Ring"
}

/** "14:02 · Ring": a row's line and its sheet's heading. */
fun eventLine(event: DoorEvent, zone: ZoneId): String = "${clockTime(event.atMillis, zone)} · ${kindLabel(event)}"

internal const val DOOR = "Door"
internal const val CHIME = "Chime"
internal const val CHIME_VOLUME = "Chime volume"
internal const val PLAY_CHIME = "Play chime"
internal const val QUIET_HOURS = "Quiet hours"
internal const val ACCESS_STEP = "Allow notification access"
internal const val ACCESS_STEP_LINE = "Opens Android’s notification access, where you allow Culvery."
internal const val OPEN_NOTIFICATION_ACCESS = "open notification access"
internal const val DONE = "done"
internal const val NOT_DONE_YET = "not done yet"
internal const val CAPTURE_LINE = "Writes each notification’s details to the tablet’s log for 30 minutes, so Culvery can learn to read them."
internal const val NOTIFICATION_ACCESS_MISSING =
    "Culvery can’t open notification access. Choose Settings › Tablet lock › Use other apps, then allow Culvery in the tablet’s Settings › Notifications."

private val DAY_AND_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE HH:mm", Locale.ENGLISH)

/** 5 §6's three messages. */
internal fun accessOff(service: String) = "Culvery can’t see $service’s notifications. Allow notification access in Settings › Door."
internal fun appMissing(service: String) = "The $service app isn’t on this tablet. Install it and sign in, then check Settings › Door."
internal fun cantFullyRead(service: String) = "Culvery can’t fully read $service’s notifications, so rings show as ‘$RING_ALERT’."

/** "Last heard from Ring: Tuesday 14:02" (5 §5.4), or that it hasn't been yet. */
internal fun lastHeard(service: String, atMillis: Long?, zone: ZoneId): String =
    atMillis?.let { "Last heard from $service: ${Instant.ofEpochMilli(it).atZone(zone).format(DAY_AND_TIME)}" } ?: "Culvery hasn’t heard from $service yet."

internal fun appStep(service: String) = "$service app installed and signed in"
internal fun checkNotifications(service: String) = "Check $service’s notifications"
internal fun quietHoursLine(from: LocalTime, until: LocalTime) = "No chime from ${clockText(from)} until ${clockText(until)}."
