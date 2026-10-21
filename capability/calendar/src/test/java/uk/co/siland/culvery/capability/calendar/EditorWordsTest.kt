package uk.co.siland.culvery.capability.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId

/** Today is Wednesday 23 September 2026 in London. Robolectric backs the form's `mutableStateOf`. */
@RunWith(AndroidJUnit4::class)
class EditorWordsTest {
    private val london = ZoneId.of("Europe/London")
    private val today = LocalDate.of(2026, 9, 23)
    private val alex = Person(PersonId("alex"), "Alex", 0xFF4CB387)
    private val sam = Person(PersonId("sam"), "Sam", 0xFF5B9BE0)
    private val mia = Person(PersonId("mia"), "Mia", 0xFFE07BA8)
    private val people = listOf(alex, sam, mia)
    private val order = people.map { it.id }
    private val names = { id: PersonId -> people.single { it.id == id }.name }
    private val sat26 = LocalDate.of(2026, 9, 26)

    private fun new() = EditorForm(EventForm.Mode.New, today, LocalTime.of(10, 0), london, alex.id, null, null, order, calendarPerson = null)

    private fun guitar(times: Int? = null) = new().apply {
        updateTitle("Guitar lesson")
        toggleWho(mia.id)
        tapDay(sat26)
        chooseHour(10)
        chooseLength(Duration.ofMinutes(45))
        if (times != null) {
            chooseRepeat(RepeatChoice.Weekly)
            stopsAfter()
            kotlin.repeat(times - DEFAULT_TIMES) { moreTimes() }
        }
    }

    private fun halfTerm() = new().apply {
        updateTitle("Half term")
        toggleWho(sam.id)
        toggleWho(mia.id)
        setMultiDay(true)
        tapDay(LocalDate.of(2026, 10, 19))
        tapDay(LocalDate.of(2026, 10, 23))
    }

    private fun bins() = new().apply {
        updateTitle("Bins")
        toggleWho(PersonId.FAMILY)
        tapDay(LocalDate.of(2026, 9, 28))
        chooseRepeat(RepeatChoice.Weekly)
    }

    private fun held(title: String, day: LocalDate, from: String, to: String, person: Person): HeldEvent {
        val start = LocalDateTime.of(day, LocalTime.parse(from)).atZone(london).toInstant()
        val end = LocalDateTime.of(day, LocalTime.parse(to)).atZone(london).toInstant()
        return HeldEvent(EventRef("c1", "s1", title), title, listOf(person.id), EventTime.Timed(start), EventTime.Timed(end))
    }

    @Test
    fun theRowValues() {
        val form = new()
        assertThat(whoValue(form, people)).isEqualTo("Choose who")
        form.toggleWho(mia.id)
        form.toggleWho(sam.id)
        assertThat(whoValue(form, people)).isEqualTo("Sam + Mia")
        form.toggleWho(PersonId.FAMILY)
        assertThat(whoValue(form, people)).isEqualTo("Everyone")

        assertThat(dayValue(form)).isEqualTo("Choose a day")
        form.tapDay(sat26)
        assertThat(dayValue(form)).isEqualTo("Sat 26 Sep")
        val range = new().apply {
            setMultiDay(true)
            tapDay(LocalDate.of(2026, 10, 29))
            tapDay(LocalDate.of(2026, 11, 1))
        }
        assertThat(dayValue(range)).isEqualTo("Thu 29 Oct – Sun 1 Nov")

        assertThat(timeValue(form)).isEqualTo("All day")
        assertThat(timeValue(guitar())).isEqualTo("10:00–10:45")
    }

    @Test
    fun theFreeAndClashLines() {
        assertThat(freeLine(FreeCheck.Free(listOf(mia.id)), names)).isEqualTo("Mia is free then")
        assertThat(freeLine(FreeCheck.Free(listOf(sam.id, mia.id)), names)).isEqualTo("Sam and Mia are free then")
        val piano = held("Piano", LocalDate.of(2026, 10, 1), "15:30", "16:15", mia)
        assertThat(clashLine(FreeCheck.Busy(mia.id, piano, emptyList()), single = true, names, london)).isEqualTo("Overlaps Piano, 15:30–16:15")
        val dentist = held("Dentist", LocalDate.of(2026, 10, 1), "12:30", "13:00", sam)
        assertThat(clashLine(FreeCheck.Busy(sam.id, dentist, emptyList()), single = false, names, london)).isEqualTo("Sam has Dentist, 12:30–13:00")
        assertThat(freeAtLine(listOf("Mia"))).isEqualTo("Mia is free at")
        assertThat(freeAtLine(listOf("Sam", "Mia"))).isEqualTo("Sam and Mia are free at")
    }

    @Test
    fun theCheckSentence() {
        assertThat(checkSentence(guitar(times = 12), people))
            .isEqualTo("Guitar lesson for Mia, every Saturday, 10:00–10:45, from 26 Sep to 12 Dec. 12 times in all.")
        assertThat(checkSentence(guitar(), people)).isEqualTo("Guitar lesson for Mia, Sat 26 Sep, 10:00–10:45.")
        assertThat(checkSentence(halfTerm(), people)).isEqualTo("Half term for Sam and Mia, Mon 19 – Fri 23 Oct, all day.")
        assertThat(checkSentence(bins(), people)).isEqualTo("Bins for everyone, every Monday, all day, from 28 Sep.")
    }

    @Test
    fun theFirstFewDates() {
        val (counted, more) = firstDates(guitar(times = 12))
        assertThat(counted).containsExactly(sat26, sat26.plusWeeks(1), sat26.plusWeeks(2), sat26.plusWeeks(3)).inOrder()
        assertThat(more).isEqualTo(8)
        val mon28 = LocalDate.of(2026, 9, 28)
        assertThat(firstDates(bins())).isEqualTo(listOf(mon28, mon28.plusWeeks(1), mon28.plusWeeks(2), mon28.plusWeeks(3)) to 0)
        assertThat(firstDates(guitar())).isEqualTo(listOf(sat26) to 0)
    }
}
