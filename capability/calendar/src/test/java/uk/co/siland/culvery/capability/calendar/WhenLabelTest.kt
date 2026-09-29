package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId

class WhenLabelTest {
    private val london = ZoneId.of("Europe/London")
    private val today = LocalDate.of(2026, 9, 23)

    private fun at(day: Int, hour: Int, minute: Int = 0) =
        EventTime.Timed(LocalDateTime.of(2026, 9, day, hour, minute).atZone(london).toInstant())

    private fun allDay(month: Int, day: Int) = EventTime.AllDay(LocalDate.of(2026, month, day))

    @Test
    fun dayLabelsAreRelativeNearTodayAndShortDatesOtherwise() {
        assertThat(dayLabel(today, today)).isEqualTo("Today")
        assertThat(dayLabel(today.plusDays(1), today)).isEqualTo("Tomorrow")
        assertThat(dayLabel(today.minusDays(1), today)).isEqualTo("Yesterday")
        assertThat(dayLabel(LocalDate.of(2026, 9, 26), today)).isEqualTo("Sat 26 Sep")
    }

    @Test
    fun aTimedEventOnOneDay() {
        assertThat(whenLabel(at(23, 19, 30), at(23, 21), london, today)).isEqualTo("Today · 19:30–21:00")
    }

    @Test
    fun aOneDayAllDayEvent() {
        assertThat(whenLabel(allDay(9, 26), allDay(9, 27), london, today)).isEqualTo("Sat 26 Sep · All day")
    }

    @Test
    fun aSeveralDayAllDayEventNamesItsLastDayNotItsExclusiveEnd() {
        assertThat(whenLabel(allDay(10, 1), allDay(10, 4), london, today)).isEqualTo("Thu 1 Oct – Sat 3 Oct · All day")
    }

    @Test
    fun anEventEndingAtMidnightStaysOnItsDay() {
        assertThat(whenLabel(at(23, 22), at(24, 0), london, today)).isEqualTo("Today · 22:00–00:00")
    }

    @Test
    fun anOvernightEventNamesBothDays() {
        assertThat(whenLabel(at(23, 22), at(24, 1), london, today)).isEqualTo("Today 22:00 – Tomorrow 01:00")
    }

    private fun ui(syncing: Boolean = false, readOnly: ReadOnlyReason? = null, recurring: Boolean = false) = EventUi(
        EventRef("c", "s", "e"), "Swim", "16:00–17:00", "16:00", Person.Family,
        allDay = false, recurring = recurring, startSort = 0, readOnlyReason = readOnly, syncing = syncing,
    )

    @Test
    fun badgesShowSyncingFirstThenLockOrRepeat() {
        assertThat(ui().badges()).isEmpty()
        assertThat(ui(recurring = true, readOnly = ReadOnlyReason.Recurring).badges()).containsExactly(Badge.Repeats)
        assertThat(ui(readOnly = ReadOnlyReason.OtherCalendar).badges()).containsExactly(Badge.OtherCalendar)
        assertThat(ui(recurring = true, readOnly = ReadOnlyReason.OtherCalendar).badges()).containsExactly(Badge.OtherCalendar)
        assertThat(ui(recurring = true, readOnly = ReadOnlyReason.NotMaster).badges()).containsExactly(Badge.OtherCalendar)
        assertThat(ui(syncing = true, recurring = true, readOnly = ReadOnlyReason.Recurring).badges())
            .containsExactly(Badge.Syncing, Badge.Repeats).inOrder()
    }

    @Test
    fun badgeIconsMatchTheHandOff() {
        assertThat(Badge.entries.map { it.icon }).containsExactly("cloud_upload", "lock", "repeat").inOrder()
    }

    @Test
    fun editableMeansNoReadOnlyReason() {
        assertThat(ui().editable).isTrue()
        assertThat(ui(readOnly = ReadOnlyReason.Recurring).editable).isFalse()
    }

    @Test
    fun onlyTheMasterCalendarNamesWhoAddedAnEvent() {
        val alex = Person(PersonId("alex-id"), "Alex", 0xFF4CB387)
        val people = mapOf(alex.id to alex)
        assertThat(createdByLabel("alex-id", null, people, "Google Calendar")).isEqualTo("Alex")
        assertThat(createdByLabel("alex-id", ReadOnlyReason.Recurring, people, "Google Calendar")).isEqualTo("Alex")
        assertThat(createdByLabel(null, null, people, "Google Calendar")).isEqualTo(ADDED_FROM_PHONE)
        assertThat(createdByLabel("alex-id", ReadOnlyReason.NotMaster, people, "Google Calendar")).isEqualTo("Google Calendar")
        assertThat(createdByLabel("alex-id", ReadOnlyReason.OtherCalendar, people, "Google Calendar")).isEqualTo(CALENDAR_FEED)
    }

    @Test
    fun onlyAWritableCalendarWithAWriterIsNotMaster() {
        val at = EventTime.Timed(Instant.EPOCH)
        val event = StoredEvent(
            "c", "s2", "e1", "Gym", at, at, recurring = true, forPerson = null, createdBy = null,
            sourcePerson = PersonId.FAMILY, startSort = 0, endSort = 0,
        )
        val writableOther = StoredSource("c", CalendarSource("s2", "Work", writable = true), SourceMapping.Default, isMaster = false)
        val subscribed = StoredSource("c", CalendarSource("s2", "School terms", writable = false), SourceMapping.Default, isMaster = false)
        assertThat(readOnlyReason(event, writableOther, hasWriter = true)).isEqualTo(ReadOnlyReason.NotMaster)
        assertThat(readOnlyReason(event, writableOther, hasWriter = false)).isEqualTo(ReadOnlyReason.OtherCalendar)
        assertThat(readOnlyReason(event, subscribed, hasWriter = true)).isEqualTo(ReadOnlyReason.OtherCalendar)
        assertThat(readOnlyReason(event, null, hasWriter = true)).isEqualTo(ReadOnlyReason.OtherCalendar)
    }
}
