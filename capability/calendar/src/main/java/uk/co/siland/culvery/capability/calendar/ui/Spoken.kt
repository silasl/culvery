package uk.co.siland.culvery.capability.calendar.ui

import java.time.LocalDate
import uk.co.siland.culvery.capability.calendar.CONFIG_ACCOUNT
import uk.co.siland.culvery.capability.calendar.CalendarRow
import uk.co.siland.culvery.capability.calendar.FULL_DATE

// What TalkBack says for the calendar's controls. Click labels follow "double-tap to", in lower case.

internal const val OPEN_EVENT_DETAILS = "open event details"
internal const val RECONNECT_ACTION = "reconnect"
internal const val EVENT_DETAILS = "Event details"
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
