package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.capability.calendar.SyncStatusUi
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.ui.CulveryTheme

@RunWith(AndroidJUnit4::class)
class WeekViewTest {
    @get:Rule val compose = createComposeRule()
    private val navigator = RecordingNavigator()
    private val now = SampleUi.NOW

    private fun show(content: @Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalShellNavigator provides navigator) {
            CulveryTheme(dark = true) { content() }
        }
    }

    private fun sync(
        agoMinutes: Long = 2,
        needsSignIn: List<String> = emptyList(),
        connections: List<String> = listOf("Google"),
    ) = SyncStatusUi(now - agoMinutes * 60_000, needsSignIn, connections, failingBeforeFirstSync = false)

    private fun state(sync: SyncStatusUi = sync()) = WeekViewState(SampleUi.week, SampleUi.TODAY, sync, now)

    @Test
    fun showsSevenDaysStartingTodayAsThisWeek() {
        show { WeekView(state()) }
        (0L..6L).forEach { compose.onNodeWithTag("week_day_${SampleUi.TODAY.plusDays(it)}").assertExists() }
        compose.onNodeWithText("This week").assertExists()
    }

    @Test
    fun subtitleNamesTheOnlyConnection() {
        show { WeekView(state()) }
        compose.onNodeWithText("Family calendar · synced with Google 2 min ago").assertExists()
    }

    @Test
    fun staleSubtitleStillShowsTheAge() {
        show { WeekView(state(sync(agoMinutes = 45))) }
        compose.onNodeWithText("Family calendar · synced with Google 45 min ago").assertExists()
    }

    @Test
    fun allDayEventsSayAllDay() {
        show { WeekView(state()) }
        compose.onAllNodesWithText("All day").assertCountEquals(2)
    }

    @Test
    fun recurringChipsShowTheRepeatBadge() {
        show { WeekView(state()) }
        // Swimming, Bin day and Piano.
        compose.onAllNodesWithContentDescription("Repeats").assertCountEquals(3)
    }

    @Test
    fun legendNamesEveryoneAndFamily() {
        show { WeekView(state()) }
        listOf("Alex", "Sam", "Mia", "Family").forEach { compose.onNodeWithText(it).assertExists() }
    }

    @Test
    fun theReconnectChipRunsTheReconnect() {
        var reconnects = 0
        show { WeekView(state(sync(agoMinutes = 0, needsSignIn = listOf("Google"))), onReconnect = { reconnects++ }) }
        compose.onNodeWithText("Google needs reconnecting").assertHeightIsAtLeast(44.dp)
        compose.onNodeWithText("Google needs reconnecting").performClick()
        assertThat(reconnects).isEqualTo(1)
        assertThat(navigator.settingsOpened).isEqualTo(0)
    }

    @Test
    fun severalCalendarsNeedingSignInShareOneChip() {
        val status = sync(needsSignIn = listOf("Google", "School"), connections = listOf("Google", "School"))
        show { WeekView(state(status)) }
        compose.onNodeWithText("2 calendars need reconnecting").assertExists()
        compose.onNodeWithText("Google needs reconnecting").assertDoesNotExist()
    }

    @Test
    fun noReconnectChipWhenEveryConnectionIsHealthy() {
        show { WeekView(state()) }
        compose.onNodeWithText("needs reconnecting", substring = true).assertDoesNotExist()
        compose.onNodeWithText("need reconnecting", substring = true).assertDoesNotExist()
    }

    @Test
    fun legendStaysLevelWithTheSubtitleWhetherOrNotTheReconnectChipShows() {
        val currentSync = mutableStateOf(sync())
        show {
            val s by currentSync
            WeekView(state(s))
        }
        val withoutChip = compose.onNodeWithTag("week_legend").fetchSemanticsNode().boundsInRoot.top

        currentSync.value = sync(agoMinutes = 0, needsSignIn = listOf("Google"))
        compose.waitForIdle()
        val withChip = compose.onNodeWithTag("week_legend").fetchSemanticsNode().boundsInRoot.top

        assertThat(withChip).isEqualTo(withoutChip)
    }

    @Test
    fun insetDayFromTheSchoolFeedShowsTheLockBadge() {
        show { WeekView(state()) }
        compose.onAllNodesWithContentDescription("Read-only calendar").assertCountEquals(1)
    }

    @Test
    fun aSyncingChipShowsCloudUpload() {
        show { WeekView(WeekViewState(SampleUi.weekWithSyncing, SampleUi.TODAY, sync(), now)) }
        compose.onAllNodesWithContentDescription("Syncing").assertCountEquals(1)
    }

    @Test
    fun tappingAChipOpensItsEvent() {
        val opened = mutableListOf<EventRef>()
        show { WeekView(state(), onOpen = { opened += it }) }
        compose.onNodeWithText("Parkrun").performClick()
        assertThat(opened).containsExactly(EventRef("sample", "family", "Parkrun"))
    }

    @Test
    fun addEventInTheHeaderAddsOnToday() {
        val added = mutableListOf<LocalDate>()
        show { WeekView(state(), onAdd = { added += it }) }
        compose.onNodeWithTag("week_add_event").assertHeightIsEqualTo(48.dp).performClick()
        assertThat(added).containsExactly(SampleUi.TODAY)
    }

    @Test
    fun aTapOnAColumnsAddHintAddsOnThatDay() {
        val added = mutableListOf<LocalDate>()
        show { WeekView(state(), onAdd = { added += it }) }
        val friday = SampleUi.TODAY.plusDays(2)
        compose.onNodeWithTag("week_add_$friday", useUnmergedTree = true).performClick()
        assertThat(added).containsExactly(friday)
    }

    @Test
    fun aTapOnTheSpaceBelowAColumnsLastChipAddsOnThatDay() {
        val added = mutableListOf<LocalDate>()
        show { WeekView(state(), onAdd = { added += it }) }
        // Sunday has just the one chip ("Sunday lunch at Gran's"), leaving plenty of empty space below it.
        val sunday = SampleUi.TODAY.plusDays(6)
        val columnTop = compose.onNodeWithTag("week_day_$sunday").fetchSemanticsNode().boundsInRoot.top
        val chipBottom = compose.onNodeWithText("Sunday lunch at Gran's").fetchSemanticsNode().boundsInRoot.bottom
        val hintTop = compose.onNodeWithTag("week_add_$sunday", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        val midY = (chipBottom + hintTop) / 2f
        compose.onNodeWithTag("week_day_$sunday").performTouchInput { click(Offset(centerX, midY - columnTop)) }
        assertThat(added).containsExactly(sunday)
    }

    @Test
    fun aTapAnywhereInAnEmptyColumnAddsOnThatDay() {
        val added = mutableListOf<LocalDate>()
        // Today's column with no chips at all: there's no last chip to compare against (the `last == null` branch).
        val emptyToday = SampleUi.week.copy(days = SampleUi.week.days.map { if (it.date == SampleUi.TODAY) it.copy(events = emptyList()) else it })
        show { WeekView(WeekViewState(emptyToday, SampleUi.TODAY, sync(), now), onAdd = { added += it }) }
        compose.onNodeWithTag("week_day_${SampleUi.TODAY}").performTouchInput { click(Offset(centerX, centerY)) }
        assertThat(added).containsExactly(SampleUi.TODAY)
    }

    @Test
    fun aTapInTheGapBetweenTwoChipsAddsNothing() {
        val added = mutableListOf<LocalDate>()
        show { WeekView(state(), onAdd = { added += it }) }
        // Saturday has three chips ("INSET day — no school", "Piano", "Book club"), so there's a gap to tap between the first two.
        val saturday = SampleUi.TODAY.plusDays(3)
        val columnTop = compose.onNodeWithTag("week_day_$saturday").fetchSemanticsNode().boundsInRoot.top
        val firstBottom = compose.onNodeWithText("INSET day — no school").fetchSemanticsNode().boundsInRoot.bottom
        val secondTop = compose.onNodeWithText("Piano").fetchSemanticsNode().boundsInRoot.top
        val midY = (firstBottom + secondTop) / 2f
        compose.onNodeWithTag("week_day_$saturday").performTouchInput { click(Offset(centerX, midY - columnTop)) }
        assertThat(added).isEmpty()
    }

    @Test
    fun aChipTapStillOpensItsEventAndAddsNothing() {
        val added = mutableListOf<LocalDate>()
        val opened = mutableListOf<EventRef>()
        show { WeekView(state(), onOpen = { opened += it }, onAdd = { added += it }) }
        compose.onNodeWithText("Dinner with Jo & Priya").performClick()
        assertThat(opened).hasSize(1)
        assertThat(added).isEmpty()
    }

    @Test
    fun eachColumnEndsWithAnAddHintAtLeast40DpTall() {
        show { WeekView(state(), onAdd = {}) }
        (0L..6L).forEach {
            compose.onNodeWithTag("week_add_${SampleUi.TODAY.plusDays(it)}", useUnmergedTree = true).assertHeightIsAtLeast(40.dp)
        }
    }

    @Test
    fun aTapOnAColumnsHeaderAddsNothing() {
        val added = mutableListOf<LocalDate>()
        show { WeekView(state(), onAdd = { added += it }) }
        // A missed tap near the day's name is not a request for a new event: only the space below the chips adds.
        compose.onNodeWithTag("week_day_${SampleUi.TODAY}").performTouchInput { click(Offset(centerX, 10f)) }
        assertThat(added).isEmpty()
    }
}
