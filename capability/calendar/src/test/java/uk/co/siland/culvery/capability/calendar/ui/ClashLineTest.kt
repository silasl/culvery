package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.EventWhen
import uk.co.siland.culvery.capability.calendar.FreeCheck
import uk.co.siland.culvery.capability.calendar.Held
import uk.co.siland.culvery.capability.calendar.HeldEvent
import uk.co.siland.culvery.capability.calendar.lastShownDay
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel

@RunWith(AndroidJUnit4::class)
class ClashLineTest {
    @get:Rule val compose = createComposeRule()

    private val friday = LocalDate.of(2026, 9, 25)
    private val household = EditorPeople(SampleUi.members)
    private val who = listOf(SampleUi.mia.id)

    /** Mia's Piano from 00:10 to 00:25 on Saturday 26 September. */
    private val earlyPiano = Held(
        listOf(
            HeldEvent(
                EventRef("c1", "s1", "piano"), "Piano", who,
                EventTime.Timed(friday.plusDays(1).atTime(0, 10).atZone(LONDON).toInstant()),
                EventTime.Timed(friday.plusDays(1).atTime(0, 25).atZone(LONDON).toInstant()),
            ),
        ),
        lastDay = lastShownDay(SampleUi.TODAY),
        unhealthy = emptySet(),
        firstDay = SampleUi.TODAY,
    )

    private fun show(eventWhen: EventWhen): FreeCheck {
        val check = earlyPiano.checkWhen(eventWhen, who, LONDON, null, household.order)
        compose.setContent {
            CulveryTheme(dark = true) {
                ClashResult(check, who, eventWhen.overDays(LONDON), LONDON, { household.byId[it]?.name.orEmpty() }, {})
            }
        }
        return check
    }

    @Test
    fun aDraftRunningPastMidnightDatesTheClash() {
        val draft = EventWhen(false, friday, LocalTime.of(23, 30), 1, LocalTime.of(0, 30))
        assertThat(draft.overDays(LONDON)).isTrue()
        assertThat(show(draft)).isInstanceOf(FreeCheck.Busy::class.java)
        compose.onNodeWithTag("time_result").assertTextEquals("Overlaps Piano, Sat 26 Sep 00:10–00:25")
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** An end at exactly midnight belongs to the day before, so the draft is one day and the check finds Saturday's piano out of reach. */
    @Test
    fun aDraftEndingAtMidnightIsOneDayAndFindsNoClash() {
        val draft = EventWhen(false, friday, LocalTime.of(23, 0), 1, LocalTime.MIDNIGHT)
        assertThat(draft.overDays(LONDON)).isFalse()
        assertThat(show(draft)).isInstanceOf(FreeCheck.Free::class.java)
        compose.onNodeWithTag("time_result").assertTextEquals("Mia is free then")
        compose.assertClickablesHaveRoleAndLabel()
    }
}
