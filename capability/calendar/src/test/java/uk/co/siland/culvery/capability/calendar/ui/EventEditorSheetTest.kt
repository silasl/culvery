package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.calendar.EditableEvent
import uk.co.siland.culvery.capability.calendar.EventForm
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.capability.calendar.DEFAULT_TIMES
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.MAX_TIMES
import uk.co.siland.culvery.capability.calendar.MIN_TIMES
import uk.co.siland.culvery.capability.calendar.RepeatChoice
import uk.co.siland.culvery.capability.calendar.RepeatEnd
import uk.co.siland.culvery.capability.calendar.TimeChoice
import uk.co.siland.culvery.capability.calendar.WriteProblem
import uk.co.siland.culvery.capability.calendar.couldNotSave
import uk.co.siland.culvery.capability.calendar.saveFailedBody
import uk.co.siland.culvery.capability.calendar.whenLabel
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.ShellTokens
import uk.co.siland.culvery.core.ui.WithFontScale
import uk.co.siland.culvery.core.ui.test.TouchModeRule
import uk.co.siland.culvery.core.ui.test.assertAccessible
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.pixelAt

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EventEditorSheetTest {
    /** Focus and the keyboard behave as on the tablet only in touch mode, set before the activity launches. */
    @get:Rule(order = 0) val touchMode = TouchModeRule()

    @get:Rule(order = 1) val compose = createComposeRule()

    private val london = ZoneId.of("Europe/London")
    private val calls = mutableListOf<String>()
    private var picker by mutableStateOf(EditorPicker.None)
    private var busy by mutableStateOf(false)
    private var failure by mutableStateOf<SaveFailure?>(null)

    /** A new event opened at 11:54 on the hand-off's Wednesday. */
    private fun newForm(signedIn: PersonId? = null) =
        EventForm(EventForm.Mode.New, SampleUi.TODAY, LocalTime.of(11, 54), london, signedIn, preselectedDay = null)

    private fun editForm(start: EventTime, end: EventTime, title: String = "Dinner with Jo & Priya", forPerson: String? = "alex") =
        EventForm(
            EventForm.Mode.Edit(EditableEvent(EventRef("c1", "s1", "e1"), title, start, end, forPerson)),
            SampleUi.TODAY, LocalTime.of(11, 54), london, signedIn = null, preselectedDay = null,
        )

    private fun dinner(): EventForm {
        val start = SampleUi.TODAY.atTime(19, 30).atZone(london).toInstant()
        return editForm(EventTime.Timed(start), EventTime.Timed(start.plusSeconds(5_400)))
    }

    private fun show(form: EventForm, childOnly: PersonId? = null, focus: Boolean = false) = compose.setContent {
        CulveryTheme(dark = true) {
            EventEditorSheet(
                form = form,
                people = SampleUi.household,
                service = "Google Calendar",
                childOnly = childOnly,
                busy = busy,
                failure = failure,
                picker = picker,
                onClose = { calls += "close" },
                onSave = { calls += "save" },
                onDelete = { calls += "delete" },
                onRefusedWho = { calls += "refused" },
                onPicker = {
                    picker = it
                    calls += "picker:$it"
                },
                focusTitleOnOpen = focus,
            )
        }
    }

    @Test
    fun aNewEventSaysNewEventAndOffersNoDelete() {
        show(newForm())
        compose.onAllNodesWithText("Add event").filterToOne(isHeading()).assertExists()
        compose.onNodeWithTag("editor_summary").assertTextEquals("Today · All day · Family")
        compose.onNodeWithTag("editor_save").assertTextEquals("Add event")
        compose.onNodeWithTag("editor_delete").assertDoesNotExist()
        compose.onNodeWithText("What’s happening?").assertExists()
    }

    @Test
    fun saveIsDisabledUntilThereIsATitle() {
        show(newForm())
        compose.onNodeWithTag("editor_save").assertIsNotEnabled().performClick()
        compose.onNodeWithTag("editor_save_reason").assertTextEquals("Add a title first")
        assertThat(bounds("editor_save_reason").bottom.value).isAtMost(bounds("editor_save").top.value)
        compose.onNodeWithTag("editor_title").performTextInput("   ")
        compose.onNodeWithTag("editor_save").assertIsNotEnabled()
        compose.onNodeWithTag("editor_title").performTextReplacement("Parents evening")
        compose.onNodeWithTag("editor_save").assertIsEnabled()
        compose.onNodeWithTag("editor_save_reason").assertDoesNotExist()
        compose.onNodeWithTag("editor_save").performClick()
        assertThat(calls).containsExactly("save")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun anEditWithItsTitleClearedShowsNoReason() {
        val form = dinner()
        show(form)
        compose.onNodeWithTag("editor_title").performTextReplacement("")
        compose.onNodeWithTag("editor_save").assertIsNotEnabled()
        compose.onNodeWithTag("editor_save_reason").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** A pin of the reason being only for a missing title (9 §6.4). */
    @Test
    fun anEndBeforeTheDayHasNoFooterReason() {
        val form = newForm()
        form.updateTitle("Swimming")
        form.chooseRepeat(RepeatChoice.Weekly)
        form.chooseEnd(RepeatEnd.OnDate(SampleUi.TODAY.minusDays(1)))
        show(form)
        compose.onNodeWithTag("editor_save").assertIsNotEnabled()
        compose.onNodeWithTag("editor_save_reason").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun saveIsDisabledWhileBusy() {
        busy = true
        show(dinner())
        compose.onNodeWithTag("editor_save").assertIsNotEnabled()
        compose.onNodeWithTag("editor_delete").assertIsNotEnabled()
    }

    @Test
    fun anEditSaysEditEventAndOffersDelete() {
        show(dinner())
        compose.onNodeWithText("Change event").assertExists()
        compose.onNodeWithText("Save changes").assertExists()
        compose.onNodeWithTag("time_pick").assertTextEquals("19:30")
        compose.onNodeWithTag("length_90").assertIsSelected()
        compose.onNodeWithTag("who_Alex").assertIsSelected()
        compose.onNodeWithTag("editor_delete").performClick()
        assertThat(calls).containsExactly("delete")
    }

    @Test
    fun chipsChangeTheFormAndTheSummary() {
        show(newForm())
        // Today, the picker opens on 12:00 for a sheet opened at 11:54; two hours up is 14:00.
        compose.onNodeWithTag("time_pick").performClick()
        compose.onNodeWithTag("hour_up").performClick()
        compose.onNodeWithTag("hour_up").performClick()
        compose.onNodeWithTag("picker_set").performClick()
        compose.onNodeWithTag("day_${SampleUi.TODAY.plusDays(1)}").performClick()
        compose.onNodeWithTag("who_Mia").performClick()
        compose.onNodeWithTag("editor_summary").assertTextEquals("Tomorrow · 14:00–15:00 · Mia")
        compose.onNodeWithTag("length_120").performClick()
        compose.onNodeWithTag("editor_summary").assertTextEquals("Tomorrow · 14:00–16:00 · Mia")
    }

    @Test
    fun lengthShowsOnlyForAPickedTime() {
        show(newForm())
        compose.onNodeWithTag("length_60").assertDoesNotExist()
        compose.onNodeWithTag("time_pick").performClick()
        compose.onNodeWithTag("picker_set").performClick()
        compose.onNodeWithTag("length_60").assertExists()
        compose.onNodeWithTag("time_all_day").performClick()
        compose.onNodeWithTag("length_60").assertDoesNotExist()
    }

    /** The sheet at the hand-off's 613 × 800 dp, optionally at a larger font. */
    private fun showAt(
        form: EventForm,
        childOnly: PersonId? = null,
        fontScale: Float = 1f,
        height: Dp = 800.dp,
        people: List<Person> = SampleUi.household,
    ) = compose.setContent {
        CulveryTheme(dark = true) {
            WithFontScale(fontScale) {
                Box(Modifier.size(ShellTokens.sheetWidth, height)) {
                    EventEditorSheet(
                        form = form, people = people, service = "Google Calendar", childOnly = childOnly, busy = false,
                        failure = null, picker = EditorPicker.None, onClose = {}, onSave = {}, onDelete = {}, onRefusedWho = {},
                        onPicker = {}, focusTitleOnOpen = false,
                    )
                }
            }
        }
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun label(text: String) = compose.onNode(hasContentDescription(text) and isHeading())

    @Test
    fun theHeaderSitsAsDrawn() {
        showAt(newForm())
        val title = compose.onAllNodesWithText("Add event").filterToOne(isHeading()).getUnclippedBoundsInRoot()
        val summary = bounds("editor_summary")
        val close = bounds("sheet_close")
        assertThat(summary.top.value).isWithin(0.5f).of(title.bottom.value + 4f)
        assertThat(close.top.value).isWithin(1f).of(title.top.value)
        assertThat(title.top.value).isWithin(0.5f).of(29f)
        assertThat(title.left.value).isWithin(0.5f).of(32f)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theChipsSayPickADateAndPickATime() {
        showAt(newForm())
        compose.onNodeWithTag("day_pick").assertTextEquals(PICK_A_DATE)
        compose.onNodeWithTag("time_pick").assertTextEquals(PICK_A_TIME)
        // The glyphs are cleared from semantics, so the leading icon is checked as a node of its own; its name by image.
        compose.onNodeWithTag("day_pick", useUnmergedTree = true).onChildren().assertCountEquals(2)
        compose.onNodeWithTag("time_pick", useUnmergedTree = true).onChildren().assertCountEquals(2)
        val ellipsis = SemanticsMatcher("has an ellipsis") { node ->
            val said = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            said.any { "…" in it }
        }
        compose.onAllNodes(ellipsis, useUnmergedTree = true).assertCountEquals(0)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun timeAndLengthSitSideBySide() {
        val form = newForm()
        form.chooseTime(TimeChoice.Custom(LocalTime.of(18, 0)))
        showAt(form)
        val time = label("Time").getUnclippedBoundsInRoot()
        val length = label("Length").getUnclippedBoundsInRoot()
        val timeEnd = bounds("time_pick").right
        assertThat(length.left.value).isAtLeast(timeEnd.value + 27f)
        assertThat(length.top.value).isWithin(1f).of(time.top.value)
        compose.onNodeWithTag("editor_time_length").assertExists()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun anAllDayEventHasNoLengthSection() {
        showAt(newForm())
        label("Time").assertExists()
        label("Length").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun timeAndLengthWrapAtLargeFont() {
        val form = newForm()
        form.chooseTime(TimeChoice.Custom(LocalTime.of(18, 0)))
        showAt(form, fontScale = 1.3f)
        val body = bounds("editor_body")
        val timeEnd = maxOf(bounds("time_all_day").right.value, bounds("time_pick").right.value)
        form.lengths.forEach { length ->
            val chip = bounds("length_${length.toMinutes()}")
            assertThat(chip.left.value).isAtLeast(timeEnd)
            assertThat(chip.right.value).isAtMost(body.right.value)
        }
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Both fit cases have an empty title, so the footer's reason shows and is where the room is measured to. */
    private fun assertClosedSheetFits(form: EventForm, childOnly: PersonId?) {
        showAt(form, childOnly)
        // A child has no Repeats, so the Time and Length row is the last block.
        val lastBottom = if (childOnly != null) {
            compose.onNodeWithTag("editor_time_length").fetchSemanticsNode().boundsInRoot.bottom
        } else {
            compose.onAllNodes(SemanticsMatcher("a Repeats chip") { node ->
                node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("repeat_") == true
            }).fetchSemanticsNodes().maxOf { it.boundsInRoot.bottom }
        }
        val density = compose.density.density
        val reasonTop = compose.onNodeWithTag("editor_save_reason").fetchSemanticsNode().boundsInRoot.top
        assertThat((reasonTop - lastBottom) / density).isAtLeast(24f)
        val scroll = compose.onNodeWithTag("editor_body").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertThat(scroll.maxValue()).isEqualTo(0f)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theClosedSheetFitsWithRoomToSpareForAnAdult() = assertClosedSheetFits(newForm(signedIn = SampleUi.alex.id), childOnly = null)

    @Test
    fun theClosedSheetFitsWithRoomToSpareForAChild() = assertClosedSheetFits(newForm(signedIn = SampleUi.mia.id), childOnly = SampleUi.mia.id)

    private val mia = SampleUi.mia.id

    private fun showAsMia(form: EventForm = newForm(signedIn = mia)) = show(form, childOnly = mia)

    @Test
    fun aChildsOtherChipsAreLockedWithTheLine() {
        val form = newForm(signedIn = mia)
        showAsMia(form)
        // 4 dp in is inside the padding and clear of the lock; the gap beside the chip shows the sheet's own background.
        listOf("Family", "Alex", "Sam").forEach { name ->
            val chip = compose.onNodeWithTag("who_$name")
            assertThat(pixelAt(chip, 4.dp, 24.dp)).isEqualTo(pixelAt(chip, (-4).dp, 24.dp))
            chip.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "not available"))
            compose.onNodeWithTag("who_${name}_lock", useUnmergedTree = true).assertExists()
        }
        compose.onNodeWithTag("who_Mia").assertIsSelected()
        assertThat(pixelAt(compose.onNodeWithTag("who_Mia"), 4.dp, 24.dp)).isEqualTo(SampleUi.mia.color.toInt())
        compose.onNodeWithTag("who_Mia_lock", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("editor_who_only").assertTextEquals("Mia can add events for Mia only.")
        compose.onNodeWithTag("editor_who_only_icon", useUnmergedTree = true).assertExists()
        assertThat(bounds("editor_who_only").top.value).isAtLeast(bounds("who_Sam").bottom.value)
        compose.onNodeWithTag("who_Sam").performClick()
        compose.onNodeWithTag("who_Family").performClick()
        assertThat(calls).containsExactly("refused", "refused")
        assertThat(form.who).isEqualTo(mia)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aTapOnALockedChipExplainsAndChangesNothing() {
        val form = newForm(signedIn = mia)
        showAsMia(form)
        compose.onNodeWithTag("who_Alex").performClick()
        assertThat(calls).containsExactly("refused")
        assertThat(form.who).isEqualTo(mia)
        compose.onNodeWithTag("who_Mia").assertIsSelected()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aChosenLockedChipIsDrawnChosenWithItsLock() {
        val form = editForm(EventTime.AllDay(SampleUi.TODAY), EventTime.AllDay(SampleUi.TODAY.plusDays(1)), forPerson = "alex")
        showAsMia(form)
        val alex = compose.onNodeWithTag("who_Alex").assertIsSelected()
        assertThat(pixelAt(alex, 4.dp, 24.dp)).isEqualTo(SampleUi.alex.color.toInt())
        compose.onNodeWithTag("who_Alex_lock", useUnmergedTree = true).assertExists()
        alex.performClick()
        assertThat(calls).isEmpty()
        compose.onNodeWithTag("who_Alex").assertIsSelected()
        assertThat(form.who).isEqualTo(SampleUi.alex.id)
        compose.onNodeWithTag("who_Mia_lock", useUnmergedTree = true).assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Pin: a save in flight fades no chip and calls none unavailable. */
    @Test
    fun busyFadesNoChip() {
        show(newForm(signedIn = SampleUi.alex.id))
        val names = listOf("Family", "Alex", "Sam", "Mia")
        val idle = names.map { pixelAt(compose.onNodeWithTag("who_$it"), 4.dp, 24.dp) }
        busy = true
        compose.waitForIdle()
        names.forEachIndexed { i, name ->
            assertThat(pixelAt(compose.onNodeWithTag("who_$name"), 4.dp, 24.dp)).isEqualTo(idle[i])
            compose.onNodeWithTag("who_$name").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        }
        compose.onNodeWithTag("who_Sam").performClick()
        compose.onNodeWithTag("who_Alex").assertIsSelected()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun anAdultSeesNoLine() {
        show(newForm(signedIn = SampleUi.alex.id))
        compose.onNodeWithTag("editor_who_only").assertDoesNotExist()
        compose.onNodeWithTag("who_Sam_lock", useUnmergedTree = true).assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aChildWithALongNameKeepsTheLineWhole() {
        val long = Person(PersonId("long"), "Mia Mia Mia Mia Mia Mia Mia Mi", 0xFFE07BA8)
        showAt(newForm(signedIn = long.id), childOnly = long.id, fontScale = 1.3f, people = SampleUi.household + long)
        val line = bounds("editor_who_only")
        val body = bounds("editor_body")
        assertThat((line.bottom - line.top).value).isGreaterThan(30f)
        assertThat(line.right.value).isAtMost(body.right.value)
        compose.onNodeWithTag("editor_who_only").assertTextEquals("${long.name} can add events for ${long.name} only.")
        compose.onNodeWithTag("editor_save").assertIsDisplayed()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aChildSeesNoRepeats() {
        val form = EventForm(EventForm.Mode.New, SampleUi.TODAY, LocalTime.of(11, 54), london, mia, preselectedDay = null, repeats = false)
        showAsMia(form)
        compose.onAllNodes(SemanticsMatcher("a Repeats or Ends node") { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)?.let { it.startsWith("repeat_") || it.startsWith("ends_") } == true
        }).assertCountEquals(0)
        compose.onNodeWithText("Repeats").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aTimedSpanEditStillShowsOneLine() {
        val visit = editForm(
            EventTime.Timed(LocalDate.of(2026, 9, 28).atTime(18, 0).atZone(london).toInstant()),
            EventTime.Timed(LocalDate.of(2026, 9, 30).atTime(10, 0).atZone(london).toInstant()),
            "Grandma visiting", "family",
        )
        show(visit)
        compose.onNodeWithTag("editor_locked_dates").assertExists()
        compose.onNodeWithText("Mon 28 – Wed 30 · change dates in Google Calendar on your phone").assertExists()
        compose.onNodeWithTag("day_pick").assertDoesNotExist()
        compose.onNodeWithTag("time_all_day").assertDoesNotExist()
        compose.onNodeWithTag("time_until").assertDoesNotExist()
        compose.onNodeWithTag("who_Family").assertIsSelected()
        compose.assertClickablesHaveRoleAndLabel()
    }

    private fun oct(d: Int) = LocalDate.of(2026, 10, d)

    /** A new all-day event on Monday 19 October (or [on]), the sheet opened on Wednesday 23 September. */
    private fun newOn(on: LocalDate = oct(19)) =
        EventForm(EventForm.Mode.New, SampleUi.TODAY, LocalTime.of(11, 54), london, null, preselectedDay = on)

    private fun halfTerm() = editForm(EventTime.AllDay(oct(19)), EventTime.AllDay(oct(24)), "Half term", "family")

    private fun SemanticsNodeInteraction.stateDescription() = fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

    @Test
    fun untilShowsOnlyForAllDay() {
        val form = newOn()
        show(form)
        compose.onNodeWithTag("time_until").assertTextEquals("Until")
        compose.onNodeWithTag("time_until", useUnmergedTree = true).onChildren().assertCountEquals(2)
        assertThat(compose.onNodeWithTag("time_until").role()).isEqualTo(Role.Button)
        val pick = bounds("time_pick")
        val until = bounds("time_until")
        assertThat(until.left.value >= pick.right.value || until.top.value >= pick.bottom.value).isTrue()
        compose.runOnIdle { form.chooseTime(TimeChoice.Custom(LocalTime.of(18, 0))) }
        compose.onNodeWithTag("time_until").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun untilOpensTheDatePickerAndAPickSetsTheLastDay() {
        val form = newOn()
        show(form)
        compose.onNodeWithTag("time_until").performClick()
        assertThat(calls).containsExactly("picker:Until")
        compose.onNodeWithTag("date_picker").assertExists()
        // With no Until the picker opens on the day.
        compose.onNodeWithTag("date_cell_2026-10-19").assertIsSelected()
        compose.onNodeWithTag("date_cell_2026-10-22").performClick()
        assertThat(form.until).isEqualTo(oct(22))
        compose.onNodeWithTag("date_picker").assertDoesNotExist()
        compose.onNodeWithTag("time_until").assertTextEquals("Until Thu 22 Oct").assertIsSelected()
        compose.onNodeWithTag("editor_summary").assertTextEquals(
            "${whenLabel(EventTime.AllDay(oct(19)), EventTime.AllDay(oct(23)), london, SampleUi.TODAY)} · Family",
        )
        // Now it opens on Until's date.
        compose.onNodeWithTag("time_until").performClick()
        compose.onNodeWithTag("date_cell_2026-10-22").assertIsSelected()
        compose.onNodeWithTag("picker_cancel").performClick()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun anUntilBeforeTheDaySaysSoAndSaveWaits() {
        val form = newOn(oct(21)).apply {
            updateTitle("Holiday")
            chooseUntil(oct(20))
        }
        show(form)
        val sentence = "The last day is before the first day. Pick a later one."
        compose.onNodeWithTag("until_error").assertTextEquals(sentence)
        assertThat(compose.onNodeWithTag("time_until").stateDescription()).isEqualTo(sentence)
        compose.onNodeWithTag("editor_save").assertIsNotEnabled()
        compose.onNodeWithTag("editor_save_reason").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun anUntilPastSixtyDaysSaysSoAndSaveWaits() {
        val form = newOn().apply {
            updateTitle("Holiday")
            chooseUntil(oct(19).plusDays(60))
        }
        show(form)
        val sentence = "An event can last up to 60 days. Pick an earlier last day."
        compose.onNodeWithTag("until_error").assertTextEquals(sentence)
        assertThat(compose.onNodeWithTag("time_until").stateDescription()).isEqualTo(sentence)
        compose.onNodeWithTag("editor_save").assertIsNotEnabled()
        compose.runOnIdle { form.chooseUntil(oct(19).plusDays(59)) }
        compose.onNodeWithTag("until_error").assertDoesNotExist()
        assertThat(compose.onNodeWithTag("time_until").stateDescription()).isNull()
        compose.onNodeWithTag("editor_save").assertIsEnabled()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun noUntilErrorTakesRoomWhenThereIsNone() {
        val form = newOn().apply { chooseUntil(oct(22)) }
        assertClosedSheetFits(form, childOnly = null)
        compose.onNodeWithTag("until_error").assertDoesNotExist()
    }

    @Test
    fun aShownUntilErrorStillFits() {
        val form = newOn(oct(21)).apply { chooseUntil(oct(20)) }
        assertClosedSheetFits(form, childOnly = null)
        compose.onNodeWithTag("until_error").assertExists()
    }

    @Test
    fun anAllDaySpanEditShowsItsUntil() {
        show(halfTerm())
        compose.onNodeWithTag("editor_locked_dates").assertDoesNotExist()
        compose.onNodeWithTag("day_pick").assertTextEquals("Mon 19 Oct").assertIsSelected()
        compose.onNodeWithTag("time_all_day").assertIsSelected()
        compose.onNodeWithTag("time_until").assertTextEquals("Until Fri 23 Oct").assertIsSelected()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun openingUntilClosesTheKeyboard() {
        show(newOn(), focus = true)
        compose.onNodeWithTag("editor_title").assertIsFocused()
        compose.onNodeWithTag("time_until").performClick()
        compose.onNodeWithTag("editor_title", useUnmergedTree = true).assertIsNotFocused()
    }

    @Test
    fun theFailureCardIsSpokenPolitelyAndTheTitleFieldIsNamed() {
        failure = SaveFailure("Google Calendar", WriteProblem.Tablet)
        show(newForm())
        compose.onNodeWithTag("editor_failure").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithTag("editor_title").assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Event name")))
    }

    @Test
    fun aFailureShowsTheCardAndSaveSaysTryAgain() {
        failure = SaveFailure("Sample calendar", WriteProblem.Refused("calendar is full"))
        val form = newForm()
        form.updateTitle("Car MOT")
        show(form)
        compose.onNodeWithTag("editor_failure").assertExists()
        compose.onNodeWithText(couldNotSave("Sample calendar", WriteProblem.Refused("calendar is full"))).assertExists()
        compose.onNodeWithText(saveFailedBody("Sample calendar", WriteProblem.Refused("calendar is full"))).assertExists()
        compose.onNodeWithText("Try again").assertExists()
        compose.onNodeWithTag("editor_save").assertTextEquals("Try again")
    }

    @Test
    fun pickDateOpensTheDatePickerAndAPickSetsTheDay() {
        val form = newForm()
        show(form)
        compose.onNodeWithTag("day_pick").performClick()
        compose.onNodeWithTag("date_picker").assertExists()
        compose.onNodeWithTag("date_cell_2026-10-05").performClick()
        assertThat(form.day).isEqualTo(LocalDate.of(2026, 10, 5))
        compose.onNodeWithTag("date_picker").assertDoesNotExist()
        compose.onNodeWithTag("day_pick").assertTextEquals("Mon 5 Oct").assertIsSelected()
        assertThat(calls).containsExactly("picker:Date", "picker:None").inOrder()
    }

    @Test
    fun pickTimeSetsACustomTime() {
        val form = newForm()
        show(form)
        compose.onNodeWithTag("time_pick").performClick()
        compose.onNodeWithTag("hour_value").assertTextEquals("12")
        compose.onNodeWithTag("hour_up").performClick()
        compose.onNodeWithTag("minute_up").performClick()
        compose.onNodeWithTag("picker_set").performClick()
        assertThat(form.time).isEqualTo(TimeChoice.Custom(LocalTime.of(13, 15)))
        compose.onNodeWithTag("time_pick").assertTextEquals("13:15").assertIsSelected()
    }

    private fun SemanticsNodeInteraction.role() = fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role)

    @Test
    fun repeatsShowsTheCommonThreeAndMoreOptions() {
        show(newForm())
        compose.onNodeWithTag("time_all_day").assertIsSelected()
        compose.onNodeWithTag("repeat_Never").assertIsSelected()
        compose.onNodeWithTag("repeat_Weekly").assertIsDisplayed()
        compose.onNodeWithTag("repeat_Fortnightly").assertIsDisplayed()
        compose.onNodeWithTag("repeat_more").assertTextEquals("More options")
        assertThat(compose.onNodeWithTag("repeat_more").role()).isEqualTo(Role.Button)
        listOf("repeat_Daily", "repeat_Monthly", "repeat_Yearly", "ends_never").forEach {
            compose.onNodeWithTag(it).assertDoesNotExist()
        }
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun moreOptionsOpensInPlaceAndStaysOpen() {
        show(newForm())
        compose.onNodeWithTag("repeat_more").performClick()
        compose.waitForIdle()
        listOf("repeat_Daily", "repeat_Monthly", "repeat_Yearly").forEach { compose.onNodeWithTag(it).assertIsDisplayed() }
        assertThat(bounds("repeat_Daily").top.value).isGreaterThan(bounds("repeat_Never").bottom.value - 1f)
        compose.onNodeWithTag("repeat_more").assertDoesNotExist()
        compose.onNodeWithTag("repeat_Daily").assertIsFocused()
        compose.onNodeWithTag("repeat_Weekly").performClick()
        compose.onNodeWithTag("repeat_Never").performClick()
        compose.onNodeWithTag("repeat_Monthly").assertIsDisplayed()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theEndsRowShowsOnlyWhileItRepeats() {
        val form = newForm()
        form.showMoreRepeats()
        show(form)
        compose.onNodeWithTag("ends_never").assertDoesNotExist()
        compose.onNodeWithTag("repeat_Weekly").performClick()
        compose.onNodeWithTag("ends_never").assertIsSelected()
        compose.onNodeWithTag("ends_pick").assertTextEquals("On a date")
        compose.onNodeWithTag("ends_after").assertTextEquals("After 10 times")
        compose.onNodeWithTag("ends_pick").performClick()
        assertThat(calls).contains("picker:EndDate")
        compose.runOnIdle { picker = EditorPicker.None }
        compose.onNodeWithTag("repeat_Never").performClick()
        compose.onNodeWithTag("ends_never").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aWeeklyRepeatWithMoreOptionsClosedShowsNoEnds() {
        val form = newForm()
        form.chooseRepeat(RepeatChoice.Weekly)
        show(form)
        compose.onNodeWithTag("ends_never").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun afterNTimesShowsItsCountButtons() {
        val form = newForm()
        form.showMoreRepeats()
        form.chooseRepeat(RepeatChoice.Weekly)
        show(form)
        compose.onNodeWithTag("ends_fewer").assertDoesNotExist()
        compose.onNodeWithTag("ends_after").performScrollTo().performClick()
        compose.onNodeWithTag("ends_after").assertIsSelected()
        compose.onNodeWithTag("ends_fewer").assert(hasContentDescription("Fewer times"))
        compose.onNodeWithTag("ends_more").assert(hasContentDescription("More times"))
        compose.onNodeWithTag("ends_more").performClick()
        compose.onNodeWithTag("ends_after").assertTextEquals("After 11 times")
        compose.onNodeWithTag("ends_after").performClick()
        compose.onNodeWithTag("ends_after").assertTextEquals("After 11 times")
        compose.runOnIdle { form.chooseEnd(RepeatEnd.OnDate(SampleUi.TODAY.plusDays(9))) }
        compose.onNodeWithTag("ends_after").assertTextEquals("After 10 times")
        compose.onNodeWithTag("ends_fewer").assertDoesNotExist()
        compose.onNodeWithTag("ends_after").performClick()
        compose.onNodeWithTag("ends_after").assertTextEquals("After 10 times")
        compose.onNodeWithTag("ends_never").performClick()
        compose.onNodeWithTag("ends_fewer").assertDoesNotExist()
        compose.onNodeWithTag("ends_more").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theCountButtonsStopAtTheirEnds() {
        val form = newForm()
        form.chooseRepeat(RepeatChoice.Weekly)
        form.chooseEnd(RepeatEnd.After(MIN_TIMES))
        show(form)
        compose.onNodeWithTag("ends_fewer").assertIsNotEnabled()
        compose.onNodeWithTag("ends_more").assertIsEnabled()
        compose.runOnIdle { form.chooseEnd(RepeatEnd.After(MAX_TIMES)) }
        compose.onNodeWithTag("ends_more").assertIsNotEnabled()
        compose.onNodeWithTag("ends_fewer").assertIsEnabled()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Later than RoundButtonHoldTest's 500 ms: inside the scrolling body a press is recognised one tap-timeout (about 100 ms) late, so steps land near 620, 720 and 820. */
    @Test
    fun holdingMoreStepsTheCount() {
        val form = newForm()
        form.chooseRepeat(RepeatChoice.Weekly)
        form.chooseEnd(RepeatEnd.After(DEFAULT_TIMES))
        showAt(form)
        compose.onNodeWithTag("ends_more").performScrollTo()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("ends_more").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(592)
        assertThat(form.repeatEnd).isEqualTo(RepeatEnd.After(10))
        compose.mainClock.advanceTimeBy(64)
        assertThat(form.repeatEnd).isEqualTo(RepeatEnd.After(11))
        compose.mainClock.advanceTimeBy(64)
        assertThat(form.repeatEnd).isEqualTo(RepeatEnd.After(11))
        compose.mainClock.advanceTimeBy(32)
        assertThat(form.repeatEnd).isEqualTo(RepeatEnd.After(12))
        compose.onNodeWithTag("ends_more").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(400)
        assertThat(form.repeatEnd).isEqualTo(RepeatEnd.After(12))
        compose.onNodeWithTag("ends_after").assertTextEquals("After 12 times")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theSheetScrollsOnlyOnceMoreOptionsOpens() {
        val form = newForm(signedIn = SampleUi.alex.id)
        showAt(form)
        fun maxScroll() = compose.onNodeWithTag("editor_body").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue()
        compose.onNodeWithTag("editor_save_reason").assertIsDisplayed()
        assertThat(maxScroll()).isEqualTo(0f)
        compose.onNodeWithTag("repeat_more").performClick()
        compose.onNodeWithTag("repeat_Weekly").performClick()
        compose.waitForIdle()
        assertThat(maxScroll()).isGreaterThan(0f)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theEndsRowFitsAtLargeFont() {
        val form = newForm()
        form.chooseRepeat(RepeatChoice.Weekly)
        form.chooseEnd(RepeatEnd.After(DEFAULT_TIMES))
        showAt(form, fontScale = 1.3f, height = 1400.dp)
        val body = bounds("editor_body")
        listOf("ends_never", "ends_pick", "ends_after", "ends_fewer", "ends_more").forEach {
            val b = bounds(it)
            assertThat(b.left.value).isAtLeast(body.left.value)
            assertThat(b.right.value).isAtMost(body.right.value)
        }
        compose.onNodeWithTag("ends_after").assertTextEquals("After 10 times")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun afterTimesSaysTheCount() {
        assertThat(afterTimes(10)).isEqualTo("After 10 times")
        assertThat(afterTimes(52)).isEqualTo("After 52 times")
    }

    /** A sheet short enough that the Ends row starts below the fold. */
    private fun showShort(form: EventForm) = compose.setContent {
        CulveryTheme(dark = true) {
            Box(Modifier.size(ShellTokens.sheetWidth, 520.dp)) {
                EventEditorSheet(
                    form = form, people = SampleUi.household, service = "Google Calendar", childOnly = null, busy = false,
                    failure = null, picker = EditorPicker.None, onClose = {}, onSave = {}, onDelete = {}, onRefusedWho = {},
                    onPicker = {}, focusTitleOnOpen = false,
                )
            }
        }
    }

    @Test
    fun openingMoreOptionsBringsEndsIntoView() {
        showShort(newForm())
        compose.onNodeWithTag("repeat_more").performScrollTo().performClick()
        compose.onNodeWithTag("repeat_Weekly").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("ends_after").assertIsDisplayed()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun anEndBeforeTheDayScrollsItsLineIntoView() {
        val form = newForm()
        form.showMoreRepeats()
        form.chooseRepeat(RepeatChoice.Weekly)
        showShort(form)
        compose.onNodeWithTag("ends_never").performScrollTo()
        compose.runOnIdle { form.chooseEnd(RepeatEnd.OnDate(SampleUi.TODAY.minusDays(1))) }
        compose.onNodeWithTag("ends_error").assertIsDisplayed()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun anEditHasNoRepeats() {
        show(dinner())
        compose.onNodeWithTag("repeat_Never").assertDoesNotExist()
    }

    @Test
    fun anEndBeforeTheDaySaysSoAndSaveWaits() {
        val form = newForm()
        form.updateTitle("Swimming")
        form.showMoreRepeats()
        form.chooseRepeat(RepeatChoice.Weekly)
        form.chooseEnd(RepeatEnd.OnDate(SampleUi.TODAY.minusDays(1)))
        show(form)
        compose.onNodeWithTag("ends_error").assertTextEquals("The end date is before the event’s day. Pick a later one.")
        compose.onNodeWithTag("editor_save").assertIsNotEnabled()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aNewEventFocusesTheTitle() {
        show(newForm(), focus = true)
        compose.onNodeWithTag("editor_title").assertIsFocused()
    }

    @Test
    fun anEditOpensWithNothingFocused() {
        show(dinner(), focus = false)
        compose.onNodeWithTag("editor_title").assertIsNotFocused()
    }

    @Test
    fun doneClosesTheKeyboardWithoutSaving() {
        val form = newForm()
        form.updateTitle("Parents evening")
        show(form, focus = true)
        compose.onNodeWithTag("editor_title").performImeAction()
        compose.onNodeWithTag("editor_title").assertIsNotFocused()
        assertThat(calls).doesNotContain("save")
    }

    @Test
    fun openingAPickerClosesTheKeyboardButAChipDoesNot() {
        show(newForm(), focus = true)
        compose.onNodeWithTag("day_${SampleUi.TODAY.plusDays(1)}").performClick()
        compose.onNodeWithTag("editor_title").assertIsFocused()
        compose.onNodeWithTag("day_pick").performClick()
        // The picker hides the sheet from TalkBack, so the focus is read from the unmerged tree.
        compose.onNodeWithTag("editor_title", useUnmergedTree = true).assertIsNotFocused()
    }

    @Test
    fun theCloseButtonCloses() {
        show(newForm())
        compose.onNodeWithTag("sheet_close").performClick()
        assertThat(calls).containsExactly("close")
    }

    /** `WindowInsets.ime` is zero under Robolectric, so the hand-off's 320 dp keyboard on its 800 dp screen is passed in. */
    @Test
    fun saveStaysAboveTheKeyboard() {
        val screen = 800.dp
        val keyboard = 320.dp
        val form = newForm()
        form.updateTitle("Parents evening")
        compose.setContent {
            CulveryTheme(dark = true) {
                Box(Modifier.size(ShellTokens.sheetWidth, screen)) {
                    EventEditorSheet(
                        form = form, people = SampleUi.household, service = "Google Calendar", childOnly = null, busy = false, failure = null,
                        picker = EditorPicker.None, onClose = {}, onSave = { calls += "save" }, onDelete = {},
                        onRefusedWho = {}, onPicker = {}, keyboard = WindowInsets(bottom = keyboard),
                    )
                }
            }
        }
        val save = compose.onNodeWithTag("editor_save").getUnclippedBoundsInRoot()
        assertThat(save.bottom).isAtMost(screen - keyboard)
        // Hand-off 10: the header, the Title and the first rows of chips stay in view; the rest scrolls.
        compose.onAllNodesWithText("Add event").filterToOne(isHeading()).assertIsDisplayed()
        compose.onNodeWithTag("editor_title").assertIsDisplayed()
        compose.onNodeWithTag("who_Family").assertIsDisplayed()
        compose.onNodeWithTag("day_${SampleUi.TODAY}").assertIsDisplayed()
        compose.onNodeWithTag("editor_save").performClick()
        assertThat(calls).containsExactly("save")
    }

    @Test
    fun theFooterIsFullWidthAndLarge() {
        showAt(newForm())
        val body = bounds("editor_body")
        val save = bounds("editor_save")
        assertThat((save.bottom - save.top).value).isWithin(0.5f).of(60f)
        assertThat((save.right - save.left).value).isWithin(0.5f).of((body.right - body.left).value)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun anEditsDeleteSitsLeftOfSaveWithTheirBottomsLevel() {
        showAt(dinner())
        val delete = bounds("editor_delete")
        val save = bounds("editor_save")
        assertThat(delete.right.value).isAtMost(save.left.value)
        assertThat(delete.bottom.value).isWithin(1f).of(save.bottom.value)
        assertThat(save.right.value).isWithin(0.5f).of(bounds("editor_body").right.value)
        compose.assertClickablesHaveRoleAndLabel()
    }

    private fun assertFooterAndReasonAboveKeyboard(scale: Float) {
        val screen = 800.dp
        val keyboard = 320.dp
        val form = newForm()
        compose.setContent {
            CulveryTheme(dark = true) {
                WithFontScale(scale) {
                    Box(Modifier.size(ShellTokens.sheetWidth, screen)) {
                        EventEditorSheet(
                            form = form, people = SampleUi.household, service = "Google Calendar", childOnly = null, busy = false,
                            failure = null, picker = EditorPicker.None, onClose = {}, onSave = {}, onDelete = {},
                            onRefusedWho = {}, onPicker = {}, keyboard = WindowInsets(bottom = keyboard),
                        )
                    }
                }
            }
        }
        assertThat(bounds("editor_save").bottom).isAtMost(screen - keyboard)
        assertThat(bounds("editor_save_reason").bottom).isAtMost(screen - keyboard)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theFooterAndItsReasonStayAboveTheKeyboard() = assertFooterAndReasonAboveKeyboard(1f)

    @Test
    fun theFooterAndItsReasonStayAboveTheKeyboardAtLargeFont() = assertFooterAndReasonAboveKeyboard(1.3f)

    @Test
    fun theSheetsTitleIsAHeading() {
        show(newForm())
        compose.onAllNodesWithText("Add event").filterToOne(isHeading()).assertExists()
    }

    @Test
    fun theSheetIsAPaneAndAPickerHidesIt() {
        show(newForm())
        compose.onNodeWithTag("editor_sheet").assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Add event"))
        compose.onNodeWithTag("day_pick").performClick()
        compose.onNodeWithTag("date_picker").assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Pick a date"))
        compose.onNodeWithTag("editor_save").assertDoesNotExist()
        compose.onNodeWithTag("picker_cancel").performClick()
        compose.onNodeWithTag("editor_save").assertExists()
    }

    @Test
    fun whoChipsAreARadioGroupAndAChildsOthersSayWhyNot() {
        show(newForm(signedIn = SampleUi.mia.id), childOnly = SampleUi.mia.id)
        compose.onNodeWithTag("who_Mia").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertIsSelected()
        compose.onNodeWithTag("who_Mia").onParent().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
        val sam = compose.onNodeWithTag("who_Sam").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "not available"))
        assertThat(sam.fetchSemanticsNode().config[SemanticsActions.OnClick].label).isEqualTo("find out why")
        sam.performClick()
        assertThat(calls).contains("refused")
        compose.onNodeWithTag("day_pick").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        compose.onNodeWithTag("time_pick").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        compose.assertAccessible()
    }
}
