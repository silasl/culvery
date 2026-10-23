package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.ButtonToneKey
import uk.co.siland.culvery.core.ui.WithFontScale
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.height
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
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
import uk.co.siland.culvery.core.ui.HhPageTitle
import uk.co.siland.culvery.core.ui.test.assertAccessible

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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

    private fun showSources(vararg sources: StoredSource, picking: String? = null) = compose.setContent {
        CulveryTheme(dark = true) {
            ReviewCalendars(
                title = "Calendars",
                connections = listOf(ReviewConnection(row(), sources.toList())),
                people = listOf(Person.Family, mia),
                nowMillis = now,
                busy = false,
                picking = picking,
                confirming = null,
                connectable = emptyList(),
                actions = ReviewActions(onShown = { source, visible -> shown = source.source.id to visible }),
            )
        }
    }

    @Test
    fun theTitleIsTheSharedPageTitle() {
        compose.setContent {
            CulveryTheme(dark = true) {
                Column {
                    Box(Modifier.testTag("reference")) { HhPageTitle("Reference") }
                    Box(Modifier.testTag("review")) {
                        ReviewCalendars(
                            title = "Calendars", connections = emptyList(), people = listOf(Person.Family), nowMillis = now, busy = false,
                            picking = null, confirming = null, connectable = emptyList(), actions = ReviewActions(),
                        )
                    }
                }
            }
        }
        fun size(tag: String): TextUnit {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onAllNodes(isHeading() and hasAnyAncestor(hasTestTag(tag)))[0].fetchSemanticsNode()
                .config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            return layouts.single().layoutInput.style.fontSize
        }
        assertThat(size("review")).isEqualTo(size("reference"))
    }

    /** The strip says so and offers Hide inside it; the connection's line stays "Updated 2 minutes ago". */
    @Test
    fun aCalendarThatCantBeReadSaysSoAndOffersToHideIt() {
        showSources(family, swim.copy(readProblem = READ_REFUSED))
        compose.onNodeWithText("Can’t read this calendar. Check it’s still shared with family@example.com.").assertExists()
        compose.onNodeWithText("Updated 2 minutes ago").assertExists()
        compose.onNodeWithText("Hide this calendar").performClick()
        assertThat(shown).isEqualTo("swim" to false)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aHiddenCalendarShowsNoReadProblem() {
        showSources(family, swim.copy(mapping = swim.mapping.copy(visible = false), readProblem = READ_REFUSED))
        compose.onNodeWithTag("review_read_problem_swim").assertDoesNotExist()
        compose.onNodeWithText("Hide this calendar").assertDoesNotExist()
    }

    @Test
    fun theMainCalendarsProblemSaysChooseAnotherFirst() {
        showSources(family.copy(readProblem = READ_REFUSED), swim)
        compose.onNodeWithText(
            "Can’t read this calendar. Check it’s still shared with family@example.com. To hide it, choose another main calendar first.",
        ).assertExists()
        compose.onNodeWithTag("review_hide_family").assertDoesNotExist()
        compose.onNodeWithText("Hide this calendar").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aCalendarsProblemOffersHideInsideTheStrip() {
        showSources(family, swim.copy(readProblem = READ_REFUSED))
        compose.onNode(hasTestTag("review_hide_swim") and hasAnyAncestor(hasTestTag("review_read_problem_swim"))).assertExists().performClick()
        assertThat(shown).isEqualTo("swim" to false)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aProblemWithNoAccountSaysThisAccount() {
        val unnamed = google.copy(config = emptyMap())
        compose.setContent {
            CulveryTheme(dark = true) {
                ReviewCalendars(
                    "Calendars",
                    listOf(ReviewConnection(CalendarRow(unnamed, "Google Calendar", "calendar_month", ConnectionHealth.Ok, now - 2 * 60_000), listOf(family, swim.copy(readProblem = READ_REFUSED)))),
                    listOf(Person.Family, mia), now, false, null, null, emptyList(), ReviewActions(),
                )
            }
        }
        compose.onNodeWithText("Can’t read this calendar. Check it’s still shared with this account.").assertExists()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun cantReadNamesTheAccountAndTheMainCalendarsWayOut() {
        assertThat(cantRead("family@example.com", main = false)).isEqualTo("Can’t read this calendar. Check it’s still shared with family@example.com.")
        assertThat(cantRead(null, main = true)).isEqualTo(
            "Can’t read this calendar. Check it’s still shared with this account. To hide it, choose another main calendar first.",
        )
    }

    @Test
    fun theStripSitsAtTheFootUnderTheChips() {
        val problem = swim.copy(readProblem = READ_REFUSED)
        showSources(family, problem, picking = sourceKey(problem))
        val card = compose.onNodeWithTag("review_source_swim").getBoundsInRoot()
        val strip = compose.onNodeWithTag("review_read_problem_swim").getBoundsInRoot()
        val chips = compose.onNodeWithTag("review_pick_Mia").getBoundsInRoot()
        assertThat(strip.top.value).isAtLeast(chips.bottom.value)
        assertThat(strip.bottom.value).isWithin(1f).of(card.bottom.value)
        assertThat(strip.left.value).isWithin(1f).of(card.left.value)
        assertThat(strip.right.value).isWithin(1f).of(card.right.value)
        compose.assertClickablesHaveRoleAndLabel()
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
    fun theMainBadgeIsInline() {
        show()
        val badge = compose.onNodeWithTag("review_main_family").getBoundsInRoot()
        val name = compose.onAllNodesWithText("Family", useUnmergedTree = true).onFirst().getBoundsInRoot()
        compose.onNodeWithTag("review_main_family").assert(hasAnyDescendant(hasText(MAIN_NEW_EVENTS_GO_HERE)))
        assertThat(badge.left.value).isAtLeast(name.right.value)
        assertThat((badge.top + badge.bottom).value / 2).isWithin(2f).of((name.top + name.bottom).value / 2)
        compose.onNodeWithText("New events go here").assertDoesNotExist()
        compose.onNodeWithTag("review_main_swim").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theMainCalendarSaysAlwaysShown() {
        show()
        compose.onNodeWithText("Always shown", useUnmergedTree = true).assertExists()
        compose.onNode(hasText("Show") and hasAnyAncestor(hasTestTag("review_show_family")), useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("review_show_family")
            .assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Show Family")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, ALWAYS_SHOWN))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
        compose.onNodeWithTag("review_show_switch_family", useUnmergedTree = true).assertExists()
        compose.onNode(hasText("Show") and hasAnyAncestor(hasTestTag("review_show_chores")), useUnmergedTree = true).assertExists()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theMainCalendarsSwitchCannotBeToggled() {
        show()
        compose.onNodeWithTag("review_show_family").performClick().assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        assertThat(shown).isNull()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun otherCalendarsToggleShownAtFortyEightDpHigh() {
        show()
        compose.onNodeWithTag("review_show_chores").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("review_show_chores").assertIsEnabled().performClick()
        assertThat(shown).isEqualTo("chores" to true)
    }

    @Test
    fun aCalendarsLineIsSixtyNineHigh() {
        show()
        listOf("family", "swim", "chores").forEach {
            assertThat(compose.onNodeWithTag("review_line_$it").getBoundsInRoot().height.value).isWithin(1f).of(69f)
        }
        compose.onNodeWithTag("settings_row_g1").assertHeightIsAtLeast(75.dp)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aLongNameWithTheBadgeFitsAtLargeFont() {
        val long = "Birthdays, holidays and school term dates"
        assertThat(long.length).isAtLeast(40)
        val longPerson = Person(PersonId("long"), "Bartholomew Featherstonehaugh", 0xFF4F8EF7)
        var pane by mutableStateOf(720.dp)
        var who by mutableStateOf(Person.Family)
        compose.setContent {
            CulveryTheme(dark = true) {
                WithFontScale(1.3f) {
                    Box(Modifier.width(pane)) {
                        val source = family.copy(source = family.source.copy(name = long), mapping = SourceMapping(who.id, visible = true))
                        ReviewCalendars(
                            "Calendars", listOf(ReviewConnection(row(), listOf(source))),
                            listOf(Person.Family, longPerson), now, false, null, null, emptyList(), ReviewActions(),
                        )
                    }
                }
            }
        }
        listOf(Person.Family, longPerson).forEach { person ->
            listOf(720.dp, 640.dp).forEach { width ->
                who = person
                pane = width
                compose.waitForIdle()
                val name = compose.onNodeWithText(long, useUnmergedTree = true)
                val boxes = listOf(
                    name.getBoundsInRoot(),
                    compose.onNodeWithTag("review_main_family").getBoundsInRoot(),
                    compose.onNodeWithTag("review_person_family").getBoundsInRoot(),
                    compose.onNodeWithTag("review_show_family").getBoundsInRoot(),
                )
                boxes.forEachIndexed { i, a ->
                    boxes.drop(i + 1).forEach { b ->
                        assertThat(a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom).isFalse()
                    }
                }
                assertThat(name.textLayout().isLineEllipsized(0)).isTrue()
                assertThat(boxes[0].right.value - boxes[0].left.value).isAtLeast(if (width == 640.dp) 25f else 100f)
                val badge = compose.onNodeWithText(MAIN_NEW_EVENTS_GO_HERE, useUnmergedTree = true).textLayout()
                assertThat(badge.lineCount).isEqualTo(1)
                assertThat(badge.isLineEllipsized(0)).isFalse()
                if (person == longPerson) {
                    assertThat(compose.onNodeWithText(person.name, useUnmergedTree = true).textLayout().isLineEllipsized(0)).isTrue()
                }
                compose.assertClickablesHaveRoleAndLabel()
            }
        }
    }

    @Test
    fun useForNewEventsSitsUnderTheLine() {
        show()
        val line = compose.onNodeWithTag("review_line_chores").getBoundsInRoot()
        val button = compose.onNodeWithTag("review_make_master_chores")
        button.assertHeightIsEqualTo(48.dp)
        assertThat(button.getBoundsInRoot().top.value).isAtLeast(line.bottom.value)
        compose.onNodeWithTag("review_make_master_family").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aConnectionNeedingSignInHasOneGreenButton() {
        show(ConnectionHealth.NeedsSignIn)
        val primaries = compose.onAllNodes(SemanticsMatcher("is primary") { it.config.getOrNull(ButtonToneKey) == ButtonTone.Primary })
        primaries.assertCountEquals(1)
        primaries[0].assert(hasTestTag("settings_reconnect"))
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun nothingShowsUntilTheConnectionsAnswer() {
        var answer by mutableStateOf<List<ReviewConnection>?>(null)
        var people by mutableStateOf<List<Person>?>(null)
        compose.setContent {
            CulveryTheme(dark = true) {
                ReviewCalendars("Calendars", answer, people, now, false, null, null, emptyList(), ReviewActions())
            }
        }
        compose.onNodeWithTag("review_none").assertDoesNotExist()
        compose.onNodeWithTag("review_source_family").assertDoesNotExist()
        compose.onNodeWithText("Calendars").assertDoesNotExist()
        answer = emptyList()
        compose.onNodeWithTag("review_none").assertDoesNotExist()
        compose.onNodeWithText("Calendars").assertDoesNotExist()
        people = listOf(Person.Family, mia)
        compose.onNodeWithTag("review_none").assertExists()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theLineSitsUnderTheTitleInThePageTitleLook() {
        compose.setContent {
            CulveryTheme(dark = true) {
                ReviewCalendars("Your calendars", emptyList(), listOf(Person.Family), now, false, null, null, emptyList(), ReviewActions(), line = "Pick who each one is for.")
            }
        }
        val title = compose.onNodeWithText("Your calendars").getBoundsInRoot()
        val line = compose.onNodeWithText("Pick who each one is for.").getBoundsInRoot()
        assertThat(line.top.value - title.bottom.value).isWithin(1f).of(8f)
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
    fun disconnectSaysWhenShoppingUsesTheAccount() {
        compose.setContent {
            CulveryTheme(dark = true) {
                ReviewCalendars(
                    "Calendars", listOf(ReviewConnection(row(), listOf(family))), listOf(Person.Family), now, false, null,
                    Confirming(row(), 0, shoppingUses = true), emptyList(), ReviewActions(),
                )
            }
        }
        compose.onNodeWithText(
            "Disconnect Google Calendar? Its calendars stop showing here. Shopping uses this account too, and stops until it’s connected again.",
        ).assertExists()
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
            assertThat(style(it).fontSize).isEqualTo(18.sp)
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
    fun thePersonPillOpensTheChipsUnderTheLine() {
        var picking by mutableStateOf<String?>(null)
        compose.setContent {
            CulveryTheme(dark = true) {
                ReviewCalendars(
                    "Calendars", listOf(ReviewConnection(row(), listOf(swim))), listOf(Person.Family, mia), now, false, picking, null, emptyList(),
                    ReviewActions(onPick = { picking = if (picking == it) null else it }),
                )
            }
        }
        compose.onNodeWithTag("review_person_swim").assertHeightIsEqualTo(48.dp).performClick()
        compose.onNodeWithTag("review_person_swim").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "expanded"))
        val line = compose.onNodeWithTag("review_line_swim").getBoundsInRoot()
        assertThat(line.height.value).isWithin(1f).of(69f)
        assertThat(compose.onNodeWithTag("review_pick_Mia").getBoundsInRoot().top.value).isAtLeast(line.bottom.value)
        compose.assertClickablesHaveRoleAndLabel()
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
