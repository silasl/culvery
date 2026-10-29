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
internal const val GOT_IT = "Got it"
internal const val DOOR_HISTORY = "Door history"
internal const val MUSIC_LOWERED = "Music lowered"
internal const val JUST_NOW = "just now"

/** "14:02" on the household's clock. */
fun clockTime(atMillis: Long, zone: ZoneId): String = clockText(Instant.ofEpochMilli(atMillis).atZone(zone).toLocalTime())

/** The card's spoken words: what, where, when (5 §5.1), leaving out a device Ring did not name. */
fun cardWords(title: String, device: String, time: String): String = listOf(title, device, time, JUST_NOW).filter { it.isNotEmpty() }.joinToString(", ")

/** "Front Door · 11:54 · just now", the card's line under its title (handoff 2a), leaving out a device Ring did not name. */
fun cardLine(device: String, time: String): String = listOf(device, time, JUST_NOW).filter { it.isNotEmpty() }.joinToString(" · ")

internal const val TODAY = "Today"
internal const val YESTERDAY = "Yesterday"
internal const val NO_RINGS_TODAY = "No rings or motion today."
internal const val SNAPSHOT = "Snapshot"
internal const val SHOW_FULL_SIZE = "show it full size"

internal const val SOMEONE_RANG = "Someone rang"
internal const val MOVEMENT = "Movement"
internal const val NOTICE_FROM_RING = "Notice from Ring"
internal const val COULDNT_TELL = "Culvery couldn’t tell what this was"
internal const val NEW = "NEW"
internal const val NEW_SPOKEN = "new"

/** A ring the parser couldn't place: shown and alerted as a ring, said as a notice (9 §4.6). */
internal val DoorEvent.isNotice: Boolean get() = kind == DoorKind.RING && unrecognised

/** "Someone rang", "Movement", or "Notice from Ring" for one the parser couldn't place (9 §4.6). */
fun kindLabel(event: DoorEvent): String = when {
    event.kind == DoorKind.MOTION -> MOVEMENT
    event.isNotice -> NOTICE_FROM_RING
    else -> SOMEONE_RANG
}

/** "14:02 · Someone rang": a row's line and its sheet's heading. */
fun eventLine(event: DoorEvent, zone: ZoneId): String = "${clockTime(event.atMillis, zone)} · ${kindLabel(event)}"

/** The line under [eventLine]: the device, then for a notice that Culvery couldn't tell what it was; null when empty. */
fun subLine(event: DoorEvent): String? =
    listOfNotNull(event.device.ifEmpty { null }, COULDNT_TELL.takeIf { event.isNotice }).joinToString(" · ").ifEmpty { null }

/** "1 new ring", "2 new rings". */
fun newRings(count: Int): String = plural(count.toLong(), "new ring")

/** "Front Door · Back Garden · 2 new rings": each device once in first-seen order, then the new rings if any; null when neither. */
fun doorSubtitle(events: List<DoorEvent>, newCount: Int): String? {
    val devices = events.map { it.device }.filter { it.isNotEmpty() }.distinct()
    return (devices + listOfNotNull(newCount.takeIf { it > 0 }?.let(::newRings))).joinToString(" · ").ifEmpty { null }
}

internal const val DOOR = "Door"
internal const val DOORBELL_SOUND = "Doorbell sound"
internal const val FOLLOW_THE_THEME = "Follow the theme"
internal const val FOLLOW_THE_THEME_LINE = "The haunted organ while Halloween shows, the classic chime otherwise."
internal const val CLASSIC_CHIME = "Classic chime"
internal const val HAUNTED_ORGAN = "Haunted organ"
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
internal fun cantFullyRead(service: String) = "Culvery can’t fully read $service’s notifications, so rings show as ‘$NOTICE_FROM_RING’."

/** "Last heard from Ring: Tuesday 14:02" (5 §5.4), or that it hasn't been yet. */
internal fun lastHeard(service: String, atMillis: Long?, zone: ZoneId): String =
    atMillis?.let { "Last heard from $service: ${Instant.ofEpochMilli(it).atZone(zone).format(DAY_AND_TIME)}" } ?: "Culvery hasn’t heard from $service yet."

internal fun appStep(service: String) = "$service app installed and signed in"
internal fun checkNotifications(service: String) = "Check $service’s notifications"
internal fun quietHoursLine(from: LocalTime, until: LocalTime) = "No chime from ${clockText(from)} until ${clockText(until)}."
