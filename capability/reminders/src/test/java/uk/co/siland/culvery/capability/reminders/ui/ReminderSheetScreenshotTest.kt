package uk.co.siland.culvery.capability.reminders.ui

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.reminders.ReminderForm
import uk.co.siland.culvery.core.household.Member
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.repeat.RepeatChoice
import uk.co.siland.culvery.core.ui.test.snap

/** The add form in its sheet with the sample household's people. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A window as tall as the form, which scrolls on the wall's 800 dp.
@Config(qualifiers = "w613dp-h2000dp-hdpi")
class ReminderSheetScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val alex = PersonId("alex")
    private val household = listOf(
        Member(Person(alex, "Alex", 0xFF4CB387), Role.ADMIN, hasPin = true),
        Member(Person(PersonId("sam"), "Sam", 0xFF5B9BE0), Role.ADULT, hasPin = true),
        Member(Person(PersonId("mia"), "Mia", 0xFFE07BA8), Role.CHILD, hasPin = true),
    )

    private fun once() = ReminderForm(null, LocalDate.of(2026, 11, 18), LocalTime.of(16, 28), null).apply {
        words = "PE kit"
        toggleWho(alex)
    }

    // Weekly on Wednesday, the day's own, and Friday.
    private fun repeats() = once().apply {
        repeat.choose(RepeatChoice.Weekly)
        repeat.toggleDay(DayOfWeek.FRIDAY, own = date.dayOfWeek)
    }

    private fun snap(name: String, dark: Boolean, form: ReminderForm) =
        compose.snap(name, dark, Modifier.size(613.dp, 2000.dp)) {
            ReminderSheet(
                form = form, people = household, editing = false, childName = null, zone = ZoneId.of("Europe/London"), line = null, busy = false,
                onSave = {}, onDelete = {}, onClose = {}, onRefusedWho = {},
            )
        }

    @Test fun onceLight() = snap("reminder_form_once_light", false, once())
    @Test fun onceDark() = snap("reminder_form_once_dark", true, once())
    @Test fun repeatsLight() = snap("reminder_form_repeats_light", false, repeats())
    @Test fun repeatsDark() = snap("reminder_form_repeats_dark", true, repeats())
}
