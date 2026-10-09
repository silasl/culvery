package uk.co.siland.culvery.capability.calendar.ui

import uk.co.siland.culvery.core.ui.test.assertAccessible
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.CONFIG_ACCOUNT
import uk.co.siland.culvery.capability.calendar.CalendarRow
import uk.co.siland.culvery.capability.calendar.CalendarSource
import uk.co.siland.culvery.capability.calendar.READ_REFUSED
import uk.co.siland.culvery.capability.calendar.ReviewConnection
import uk.co.siland.culvery.capability.calendar.SourceMapping
import uk.co.siland.culvery.capability.calendar.StoredSource
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth
import uk.co.siland.culvery.core.plugin.ProviderDescriptor
import uk.co.siland.culvery.core.ui.CulveryTheme
import androidx.compose.ui.test.isHeading

@RunWith(AndroidJUnit4::class)
class ReviewCalendarsTest {
    @get:Rule val compose = createComposeRule()

    private val now = 100_000_000L
    private val mia = Person(PersonId("mia"), "Mia", 0xFFE07BA8)
    private val google = Connection("g1", "calendar.google", "Google", mapOf(CONFIG_ACCOUNT to "family@example.com"))
    private val family = StoredSource("g1", CalendarSource("family", "Family", writable = true), SourceMapping(PersonId.FAMILY, visible = true), isMaster = true)
    private val swim = StoredSource("g1", CalendarSource("swim", "Mia's swimming", writable = false), SourceMapping(mia.id, visible = true))
    private val chores = StoredSource("g1", CalendarSource("chores", "Chores", writable = true), SourceMapping(PersonId.FAMILY, visible = false))

    private fun row(health: ConnectionHealth = ConnectionHealth.Ok) =
        CalendarRow(google, "Google Calendar", "calendar_month", health, lastSyncMillis = now - 2 * 60_000)

    private var picked: String? = null
    private var chosen: Pair<String, String>? = null
    private var shown: Pair<String, Boolean>? = null
    private var mastered: String? = null
    private var reconnected: Connection? = null
    private var kept = 0
    private var confirmed: CalendarRow? = null

    private fun show(
        health: ConnectionHealth = ConnectionHealth.Ok,
        picking: String? = null,
        confirming: Confirming? = null,
        busy: Boolean = false,
        connectable: List<ProviderDescriptor> = emptyList(),
    ) = compose.setContent {
        CulveryTheme(dark = true) {
            ReviewCalendars(
                title = "Calendars",
                connections = listOf(ReviewConnection(row(health), listOf(family, swim, chores))),
                people = listOf(Person.Family, mia),
                nowMillis = now,
                busy = busy,
                picking = picking,
                confirming = confirming,
                connectable = connectable,
                actions = ReviewActions(
                    onReconnect = { reconnected = it },
                    onPick = { picked = it },
                    onPerson = { source, person -> chosen = source.source.id to person.name },
                    onShown = { source, visible -> shown = source.source.id to visible },
                    onMakeMaster = { mastered = it.source.id },
                    onKeep = { kept++ },
                    onConfirmDisconnect = { confirmed = it },
                ),
            )
        }
    }

    private fun showSources(vararg sources: StoredSource) = compose.setContent {
        CulveryTheme(dark = true) {
            ReviewCalendars(
                title = "Calendars",
                connections = listOf(ReviewConnection(row(), sources.toList())),
                people = listOf(Person.Family, mia),
                nowMillis = now,
                busy = false,
                picking = null,
                confirming = null,
                connectable = emptyList(),
                actions = ReviewActions(onShown = { source, visible -> shown = source.source.id to visible }),
            )
        }
    }

    /** D6: the row says so and offers Hide; the connection's line stays "Updated 2 minutes ago". */
    @Test
    fun aCalendarThatCantBeReadSaysSoAndOffersToHideIt() {
        showSources(family, swim.copy(readProblem = READ_REFUSED))
        compose.onNodeWithText("Can’t read this calendar — check it’s still shared with this account.").assertExists()
        compose.onNodeWithText("Updated 2 minutes ago").assertExists()
        compose.onNodeWithText("Hide this calendar").performClick()
        assertThat(shown).isEqualTo("swim" to false)
    }

    @Test
    fun aHiddenCalendarShowsNoReadProblem() {
        showSources(family, swim.copy(mapping = swim.mapping.copy(visible = false), readProblem = READ_REFUSED))
        compose.onNodeWithText("Can’t read this calendar — check it’s still shared with this account.").assertDoesNotExist()
        compose.onNodeWithText("Hide this calendar").assertDoesNotExist()
    }

    @Test
    fun theMasterThatCantBeReadAsksForAnotherMasterFirst() {
        showSources(family.copy(readProblem = READ_REFUSED), swim)
        compose.onNodeWithText("Choose another main calendar first.").assertExists()
        compose.onNodeWithText("Hide this calendar").assertDoesNotExist()
    }

    @Test
    fun theConnectionNamesItsAccountAndHealth() {
        show()
        compose.onNodeWithText("Calendars").assertExists()
        compose.onNodeWithText("Google Calendar · family@example.com").assertExists()
        compose.onNodeWithText("Updated 2 minutes ago").assertExists()
        compose.onNodeWithTag("settings_reconnect").assertDoesNotExist()
    }

    @Test
    fun aConnectionNeedingSignInOffersReconnect() {
        show(ConnectionHealth.NeedsSignIn)
        compose.onNodeWithText("Needs reconnecting").assertExists()
        compose.onNodeWithTag("settings_reconnect").performClick()
        assertThat(reconnected).isEqualTo(google)
    }

    @Test
    fun theMasterIsMarkedAndAlwaysShown() {
        show()
        compose.onNodeWithText("Main").assertExists()
        compose.onNodeWithText("New events go here").assertExists()
        compose.onNodeWithTag("review_show_family").assertIsNotEnabled()
        compose.onNodeWithTag("review_show_chores").assertIsEnabled().performClick()
        assertThat(shown).isEqualTo("chores" to true)
    }

    @Test
    fun onlyAnotherWritableCalendarOffersMakeMaster() {
        show()
        compose.onNodeWithTag("review_make_master_family").assertDoesNotExist()
        compose.onNodeWithTag("review_make_master_swim").assertDoesNotExist()
        compose.onNodeWithTag("review_make_master_chores").performClick()
        assertThat(mastered).isEqualTo("chores")
    }

    @Test
    fun aCalendarsPersonOpensFamilyAndThePeople() {
        show()
        compose.onNodeWithTag("review_person_swim").performClick()
        assertThat(picked).isEqualTo(sourceKey(swim))
    }

    @Test
    fun choosingSomeoneFromTheOpenChips() {
        show(picking = sourceKey(swim))
        compose.onNodeWithTag("review_pick_Mia").assertIsSelected()
        compose.onNodeWithTag("review_pick_Family").performClick()
        assertThat(chosen).isEqualTo("swim" to "Family")
    }

    @Test
    fun disconnectAsksFirstAndCountsWhatWaits() {
        var confirming by mutableStateOf<Confirming?>(null)
        compose.setContent {
            CulveryTheme(dark = true) {
                ReviewCalendars(
                    "Calendars", listOf(ReviewConnection(row(), listOf(family))), listOf(Person.Family), now, false, null, confirming, emptyList(),
                    ReviewActions(
                        onDisconnect = { confirming = Confirming(it, 3) },
                        onKeep = {
                            kept++
                            confirming = null
                        },
                        onConfirmDisconnect = { confirmed = it },
                    ),
                )
            }
        }
        compose.onNodeWithTag("review_disconnect_g1").performClick()
        compose.onNodeWithText("Disconnect Google Calendar? Its calendars stop showing here, and 3 changes not yet saved to Google Calendar will be lost.").assertExists()
        compose.onNodeWithTag("review_keep").performClick()
        assertThat(kept).isEqualTo(1)
        compose.onNodeWithTag("review_disconnect_g1").performClick()
        compose.onNodeWithTag("review_confirm_disconnect").performClick()
        assertThat(confirmed).isEqualTo(row())
    }

    @Test
    fun disconnectAndMakeMasterAreButtons() {
        show()
        listOf("review_disconnect_g1", "review_make_master_chores").forEach {
            compose.onNodeWithTag(it)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .assertHeightIsEqualTo(48.dp)
        }
    }

    @Test
    fun connectionAndCalendarRowsUseTheSharedRowStyles() {
        show()
        fun style(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).textLayout().layoutInput.style
        listOf("Google Calendar · family@example.com", "Chores").forEach {
            assertThat(style(it).fontSize).isEqualTo(17.sp)
            assertThat(style(it).fontWeight).isEqualTo(FontWeight.W600)
        }
        assertThat(style("Updated 2 minutes ago").fontSize).isEqualTo(14.sp)
    }

    @Test
    fun connectAndReconnectAreDisabledWhileBusy() {
        show(ConnectionHealth.NeedsSignIn, busy = true, connectable = listOf(ProviderDescriptor("calendar.google", "Google Calendar", "event", emptySet())))
        compose.onNodeWithTag("settings_connect_calendar.google").assertIsNotEnabled()
        compose.onNodeWithTag("settings_reconnect").assertIsNotEnabled()
    }

    @Test
    fun theTitleIsAHeading() {
        show()
        compose.onNodeWithText("Calendars").assert(isHeading())
    }

    @Test
    fun showIsOneNamedSwitchAndTheMainCalendarsSaysWhyItIsOff() {
        show()
        compose.onNodeWithTag("review_show_chores")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Show Chores")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
        compose.onNodeWithTag("review_show_family")
            .assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "always shown: it’s the main calendar"))
    }

    @Test
    fun whoItIsForIsAButtonThatSaysWhetherItIsOpen() {
        var picking by mutableStateOf<String?>(null)
        compose.setContent {
            CulveryTheme(dark = true) {
                ReviewCalendars(
                    "Calendars", listOf(ReviewConnection(row(), listOf(swim))), listOf(Person.Family, mia), now, false, picking, null, emptyList(),
                    ReviewActions(onPick = { picking = if (picking == it) null else it }),
                )
            }
        }
        val chip = compose.onNodeWithTag("review_person_swim")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "collapsed"))
        assertThat(chip.fetchSemanticsNode().config[SemanticsActions.OnClick].label).isEqualTo("change who Mia's swimming is for")
        chip.performClick()
        compose.onNodeWithTag("review_person_swim").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "expanded"))
        compose.onNodeWithTag("review_pick_Mia").onParent().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
    }

    @Test
    fun eachRepeatedActionNamesItsSubject() {
        show(ConnectionHealth.NeedsSignIn)
        compose.onNodeWithContentDescription("Disconnect Google Calendar, family@example.com").assertExists()
        compose.onNodeWithContentDescription("Reconnect Google Calendar, family@example.com").assertExists()
        compose.onNodeWithContentDescription("Use for new events, Chores").assertExists()
        compose.assertAccessible()
    }

    @Test
    fun withNothingConnectedTheCalendarsPageSaysWhatToDo() {
        compose.setContent {
            CulveryTheme(dark = true) {
                ReviewCalendars(
                    title = "Calendars", connections = emptyList(), people = listOf(Person.Family), nowMillis = now, busy = false,
                    picking = null, confirming = null, connectable = emptyList(), actions = ReviewActions(),
                )
            }
        }
        compose.onNodeWithText("Connect your family’s Google Calendar to see it on Home.").assertExists()
    }

    @Test
    fun theWizardsStepHasItsLine() {
        compose.setContent {
            CulveryTheme(dark = true) {
                ReviewCalendars(
                    title = "Your calendars", connections = listOf(ReviewConnection(row(), listOf(family))), people = listOf(Person.Family),
                    nowMillis = now, busy = false, picking = null, confirming = null, connectable = emptyList(), actions = ReviewActions(),
                    line = "Choose who each calendar is for, and which to show. New events go to the main calendar.",
                )
            }
        }
        compose.onNodeWithText("Choose who each calendar is for, and which to show. New events go to the main calendar.").assertExists()
    }

    @Test
    fun hideNamesItsCalendar() {
        showSources(family, swim.copy(readProblem = READ_REFUSED))
        compose.onNodeWithContentDescription("Hide this calendar, Mia's swimming").assertExists()
    }
}
