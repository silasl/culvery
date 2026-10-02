package uk.co.siland.culvery.capability.calendar.ui

import java.time.Instant
import java.time.LocalDate
import uk.co.siland.culvery.capability.calendar.ADDED_FROM_PHONE
import uk.co.siland.culvery.capability.calendar.ALL_DAY_LABEL
import uk.co.siland.culvery.capability.calendar.CALENDAR_FEED
import uk.co.siland.culvery.capability.calendar.DayUi
import uk.co.siland.culvery.capability.calendar.EventDetailUi
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.capability.calendar.EventUi
import uk.co.siland.culvery.capability.calendar.ReadOnlyReason
import uk.co.siland.culvery.capability.calendar.WeekUi
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId

/** The hand-off's sample week (Wednesday 23 September 2026, calendar-sheets/02-calendar-week-dark.png) as UI models. */
object SampleUi {
    val TODAY: LocalDate = LocalDate.of(2026, 9, 23)
    val NOW: Long = Instant.parse("2026-09-23T10:54:00Z").toEpochMilli()

    val alex = Person(PersonId("alex"), "Alex", 0xFF4CB387)
    val sam = Person(PersonId("sam"), "Sam", 0xFF5B9BE0)
    val mia = Person(PersonId("mia"), "Mia", 0xFFE07BA8)
    val family = Person.Family
    val people = listOf(alex, sam, mia, family)

    fun event(title: String, time: String, person: Person, recurring: Boolean = false) = EventUi(
        EventRef("sample", "family", title), title, time, time.substringBefore('–'), person,
        allDay = false, recurring = recurring, startSort = 0,
        readOnlyReason = if (recurring) ReadOnlyReason.Recurring else null,
    )

    fun allDay(title: String, person: Person, recurring: Boolean = false) = EventUi(
        EventRef("sample", "family", title), title, ALL_DAY_LABEL, ALL_DAY_LABEL, person,
        allDay = true, recurring = recurring, startSort = 0,
        readOnlyReason = if (recurring) ReadOnlyReason.Recurring else null,
    )

    private fun day(offset: Long, vararg events: EventUi) = DayUi(TODAY.plusDays(offset), events.toList())

    val today = listOf(
        event("School run", "07:45–08:30", sam),
        event("Boiler service", "10:00–11:00", family),
        event("Plumber quote call", "13:00–13:30", family),
        event("Swimming", "16:00–17:00", mia, recurring = true),
        event("Dinner with Jo & Priya", "19:30–21:00", alex),
    )

    val comingUp = listOf(
        day(1, event("Office day", "09:00–17:00", alex), event("Football", "18:00–19:00", mia)),
        day(2, allDay("Bin day", family, recurring = true), event("Dentist", "12:30–13:30", sam)),
        day(
            3,
            allDay("INSET day — no school", family).copy(readOnlyReason = ReadOnlyReason.OtherCalendar),
            event("Piano", "15:30–16:30", mia, recurring = true),
            event("Book club", "20:00–22:00", sam),
        ),
    )

    val comingUpBusy = listOf(
        day(
            1,
            event("Office day", "09:00–17:30", alex),
            event("Swim club", "16:00–17:00", mia),
            event("Football", "18:00–19:00", mia),
            event("Parents' evening", "19:00–20:00", sam),
        ),
        day(2),
        day(3, event("Piano", "15:30–16:00", mia)),
    )

    val week = WeekUi(
        TODAY,
        listOf(
            day(0, *today.toTypedArray()),
            comingUp[0],
            comingUp[1],
            comingUp[2],
            day(4, event("Pizza night", "19:00–23:00", family)),
            day(5, event("Parkrun", "09:30–11:00", alex), event("Birthday party", "14:00–17:00", mia)),
            day(6, event("Sunday lunch at Gran's", "12:00–15:00", family)),
        ),
        people,
    )

    /** [week] moved [weeks] on: the Calendar tab stepped ahead (4c D10). */
    fun weekAhead(weeks: Long) = week.copy(
        start = TODAY.plusWeeks(weeks),
        days = week.days.map { it.copy(date = it.date.plusWeeks(weeks)) },
    )

    /** The people who can be assigned (hand-off 06: Alex, Sam, Mia). */
    val household = listOf(alex, sam, mia)

    private fun EventUi.onFamilyCalendar(createdBy: String) =
        copy(sourceName = "Family calendar", connectionLabel = "Sample calendar", serviceName = "Google Calendar", createdBy = createdBy)

    /** Hand-off 03. */
    val detailEditable = EventDetailUi(event("Dinner with Jo & Priya", "19:30–21:00", alex).onFamilyCalendar("Alex"), "Today · 19:30–21:00")

    /** Hand-off 04. */
    val detailReadOnlyFeed = EventDetailUi(
        allDay("INSET day — no school", family).copy(
            sourceName = "School terms",
            connectionLabel = "Sample calendar",
            readOnlyReason = ReadOnlyReason.OtherCalendar,
            createdBy = CALENDAR_FEED,
        ),
        "Sat 26 Sep · All day",
    )

    /** One of the household's own calendars in the service, not the master: changed there, not here. */
    val detailNotMaster = EventDetailUi(
        event("Gym", "07:00–08:00", alex, recurring = true).copy(
            sourceName = "Alex",
            connectionLabel = "Google",
            serviceName = "Google Calendar",
            readOnlyReason = ReadOnlyReason.NotMaster,
            createdBy = "Google Calendar",
            repeats = "Every week",
        ),
        "Today · 07:00–08:00",
    )

    /** Hand-off 05. */
    val detailRecurring = EventDetailUi(
        event("Swimming", "16:00–17:00", mia, recurring = true).onFamilyCalendar("Sam").copy(repeats = "Every week"),
        "Today · 16:00–17:00",
    )

    /** Hand-off 06. */
    val detailUntagged = EventDetailUi(
        event("Plumber quote call", "13:00–13:30", family).onFamilyCalendar(ADDED_FROM_PHONE).copy(untagged = true),
        "Today · 13:00–13:30",
    )

    /** Hand-off 07. */
    val detailSyncing = detailEditable.copy(event = detailEditable.event.copy(syncing = true))

    /** Today rows with every badge: syncing, another calendar, repeating. */
    val todayWithBadges = listOf(
        event("Dinner with Jo & Priya", "19:30–21:00", alex).copy(syncing = true),
        allDay("INSET day — no school", family).copy(readOnlyReason = ReadOnlyReason.OtherCalendar),
        event("Swimming", "16:00–17:00", mia, recurring = true).copy(syncing = true),
    )

    /** Hand-off 07's week: Dinner with Jo & Priya is waiting to sync. */
    val weekWithSyncing = week.copy(
        days = week.days.mapIndexed { i, d ->
            if (i != 0) d else d.copy(events = d.events.map { if (it.title == "Dinner with Jo & Priya") it.copy(syncing = true) else it })
        },
    )
}
