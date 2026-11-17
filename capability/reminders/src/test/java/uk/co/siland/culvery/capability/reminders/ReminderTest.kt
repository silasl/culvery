package uk.co.siland.culvery.capability.reminders

import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Test
import uk.co.siland.culvery.capability.reminders.db.ReminderEntity
import uk.co.siland.culvery.core.household.PersonId

class ReminderTest {
    private val who = PersonId("person-7731")

    @Test
    fun storedTypesHideTheirWordsAndPeople() {
        val once = Schedule.Once(LocalDate.of(2026, 11, 20))
        val weekly = Schedule.Weekly(setOf(DayOfWeek.TUESDAY))
        val draft = ReminderDraft("Dentist at four", listOf(who), LocalTime.of(16, 35), once)
        val reminder = Reminder(9, "Dentist at four", listOf(who), who, LocalTime.of(16, 35), weekly, 0, null, null)
        val entity = ReminderEntity(9, "Dentist at four", "person-7731", "person-7731", 995, "2026-11-20", 0, 0, null, null)
        listOf(draft.toString(), reminder.toString(), once.toString(), weekly.toString(), entity.toString()).forEach {
            assertThat(it).doesNotContain("Dentist")
            assertThat(it).doesNotContain("7731")
            assertThat(it).doesNotContain("16:35")
            assertThat(it).doesNotContain("2026")
            assertThat(it).doesNotContain("TUESDAY")
        }
        assertThat(reminder.toString()).contains("Reminder")
        assertThat(reminder.toString()).contains("9")
    }

    @Test
    fun wordsAreTidied() {
        assertThat(tidyWords("  PE\nkit ")).isEqualTo("PE kit")
        assertThat(tidyWords("a\t\tb   c")).isEqualTo("a b c")
    }
}
