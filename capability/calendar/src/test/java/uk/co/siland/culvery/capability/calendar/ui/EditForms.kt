package uk.co.siland.culvery.capability.calendar.ui

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import uk.co.siland.culvery.capability.calendar.EditableEvent
import uk.co.siland.culvery.capability.calendar.EventForm
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.capability.calendar.EventTime

val LONDON: ZoneId = ZoneId.of("Europe/London")

/** A timed event from Monday 28 September 18:00 to Wednesday 30 at 10:00: its dates stay locked. */
val LOCKED_START = EventTime.Timed(LocalDateTime.of(2026, 9, 28, 18, 0).atZone(LONDON).toInstant())
val LOCKED_END = EventTime.Timed(LocalDateTime.of(2026, 9, 30, 10, 0).atZone(LONDON).toInstant())

/** An edit form opened at 11:54 on the sample Wednesday. */
fun editFormOf(start: EventTime, end: EventTime, title: String = "Dinner with Jo & Priya", forPerson: String? = "alex") =
    EventForm(
        EventForm.Mode.Edit(EditableEvent(EventRef("c1", "s1", "e1"), title, start, end, forPerson)),
        SampleUi.TODAY, LocalTime.of(11, 54), LONDON, signedIn = null, preselectedDay = null,
    )

fun timedSpan(from: LocalDateTime, to: LocalDateTime, title: String = "Half term", forPerson: String? = "family") =
    editFormOf(EventTime.Timed(from.atZone(LONDON).toInstant()), EventTime.Timed(to.atZone(LONDON).toInstant()), title, forPerson)

fun dinnerOf(length: Duration): EventForm {
    val start = SampleUi.TODAY.atTime(19, 30).atZone(LONDON).toInstant()
    return editFormOf(EventTime.Timed(start), EventTime.Timed(start.plus(length)))
}

fun lockedVisit() = editFormOf(LOCKED_START, LOCKED_END, "Grandma visiting", "family")

/** The half term, all day Monday 19 to Friday 23 October (the end is exclusive). */
fun halfTerm(title: String = "Half term") =
    editFormOf(EventTime.AllDay(LocalDate.of(2026, 10, 19)), EventTime.AllDay(LocalDate.of(2026, 10, 24)), title, "family")
