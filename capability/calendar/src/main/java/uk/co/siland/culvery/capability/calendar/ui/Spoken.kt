package uk.co.siland.culvery.capability.calendar.ui

import java.time.LocalDate
import java.util.Locale
import uk.co.siland.culvery.capability.calendar.CONFIG_ACCOUNT
import uk.co.siland.culvery.capability.calendar.CalendarRow
import uk.co.siland.culvery.capability.calendar.EventUi
import uk.co.siland.culvery.capability.calendar.FULL_DATE
import uk.co.siland.culvery.capability.calendar.FULL_WEEKDAY
import uk.co.siland.culvery.capability.calendar.badges
import uk.co.siland.culvery.capability.calendar.spokenTime
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.ui.FAMILY_COLOUR_NAME
import uk.co.siland.culvery.core.ui.PersonPalette

// What TalkBack says for the calendar's controls. Click labels follow "double-tap to", in lower case.

internal const val OPEN_EVENT_DETAILS = "open event details"
internal const val RECONNECT_ACTION = "reconnect"
internal const val EVENT_DETAILS = "Event details"
internal const val DELETE_QUESTION = "Delete this event?"
internal const val ALWAYS_SHOWN = "always shown: it’s the main calendar"
internal const val EXPANDED = "expanded"
internal const val COLLAPSED = "collapsed"

internal fun changeWhoFor(calendar: String): String = "change who $calendar is for"

/** A repeated action's spoken name: its visible words first, then what it acts on ("Hide this calendar, School terms"). */
internal fun withSubject(action: String, subject: String): String = "$action, $subject"

/** "Disconnect Google Calendar, alex@example.com", or "Disconnect Google Calendar" when the connection names no account. */
internal fun forAccount(action: String, row: CalendarRow): String =
    row.connection.config[CONFIG_ACCOUNT]?.let { "$action ${row.service}, $it" } ?: "$action ${row.service}"

internal fun connectingPane(service: String): String = "Connecting to $service"

internal fun showAsEvent(name: String): String = "show as $name’s event"

internal fun addEventOn(date: LocalDate): String = "Add event on ${date.format(FULL_DATE)}"

/** A day as a heading: "Today, Tuesday 6 October", "Tomorrow, Wednesday 7 October", "Thursday 8 October". */
internal fun dayHeading(date: LocalDate, prefix: String? = null): String = listOfNotNull(prefix, date.format(FULL_DATE)).joinToString(", ")

/** "Tuesday 7 October, today": a date cell's name (its selected state is the tree's). */
internal fun dateCellName(date: LocalDate, today: LocalDate): String = dayHeading(date) + if (date == today) ", today" else ""

private fun EventUi.badgeWords(): String = badges().joinToString("") { ", ${it.spoken}" }

/** A week chip or a Coming up row: "Swimming, Sam, Tuesday 09:00", then its badges. */
internal fun eventOnDay(event: EventUi, day: LocalDate): String =
    "${event.title}, ${event.person.name}, ${day.format(FULL_WEEKDAY)} ${spokenTime(event.startLabel)}${event.badgeWords()}"

/** A Today row (the card is the day): "Swimming, Sam, 09:00 to 10:30", then its badges. */
internal fun todayRowName(event: EventUi): String = "${event.title}, ${event.person.name}, ${spokenTime(event.timeLabel)}${event.badgeWords()}"

/** "Colours: Alex, green; Sam, blue; Family, amber". */
internal fun legendName(people: List<Person>): String = people.joinToString("; ", prefix = "Colours: ") { person ->
    val colour = if (person.isFamily) FAMILY_COLOUR_NAME else PersonPalette.nameOf(person.color)
    if (colour == null) person.name else "${person.name}, ${colour.lowercase(Locale.ENGLISH)}"
}
