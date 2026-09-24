package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
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
    fun reconnectChipOpensSettings() {
        show { WeekView(state(sync(agoMinutes = 0, needsSignIn = listOf("Google")))) }
        compose.onNodeWithText("Google needs reconnecting").assertHeightIsAtLeast(44.dp)
        compose.onNodeWithText("Google needs reconnecting").performClick()
        assertThat(navigator.settingsOpened).isEqualTo(1)
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
}
