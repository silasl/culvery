package uk.co.siland.culvery.core.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Dp
import uk.co.siland.culvery.core.household.Member
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.ui.DarkColors
import uk.co.siland.culvery.core.ui.HhToast
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.LocalToastClearance
import uk.co.siland.culvery.core.ui.ShellTokens
import uk.co.siland.culvery.core.ui.ToastClearance
import uk.co.siland.culvery.core.ui.WithFontScale
import uk.co.siland.culvery.core.ui.test.pixelAt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.access.Identified
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.MAX_PEOPLE
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.ToastAction
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.setup.steps.HouseholdStep
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.ButtonToneKey
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.PersonPalette
import uk.co.siland.culvery.core.ui.test.TouchModeRule
import uk.co.siland.culvery.core.ui.test.assertAccessible
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel

/** The people list and sheet over the real editor; Alex is in the setup session, so no PIN pad shows. */
@RunWith(AndroidJUnit4::class)
class PeopleUiTest {
    @get:Rule(order = 0) val touchMode = TouchModeRule()
    @get:Rule(order = 1) val compose = createComposeRule()
    private lateinit var db: HouseholdDatabase
    private lateinit var household: HouseholdRepository
    private lateinit var access: TestAccess
    private lateinit var editor: PeopleEditor
    private val overlay = RecordingOverlay()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Before
    fun setUp() {
        db = householdDb()
        household = HouseholdRepository(db)
        access = TestAccess(household, WallClock { System.currentTimeMillis() }, scope).also { it.listen(scope) }
        editor = PeopleEditor(household, access.pins, access.control, access.toasts)
        val alex = runBlocking { access.addAdmin() }
        access.control.beginSetupSession(Identified(alex, Role.ADMIN))
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    /** The list reads Room: wait for Alex's row before the first check or tap. */
    private fun show(content: @Composable () -> Unit = { PeoplePane(editor, reorder = true) }) {
        compose.setContent {
            CulveryTheme(dark = true) {
                CompositionLocalProvider(LocalOverlayHost provides overlay) {
                    Box {
                        content()
                        overlay.content?.invoke()
                    }
                }
            }
        }
        compose.awaitTag("person_Alex")
    }

    private fun addSam(pin: String? = null) = runBlocking { access.pins.addPerson("Sam", PersonPalette.colors[1], Role.ADULT, pin) }

    @Test
    fun theListShowsEachPersonsRoleAndWhetherTheyHaveAPin() {
        addSam()
        runBlocking { access.pins.addPerson("Mia", PersonPalette.colors[2], Role.ADMIN, null) }
        show()
        compose.awaitText("Adult · no PIN yet, so Sam can only look")
        compose.onNodeWithText("Admin · PIN set").assertExists()
        compose.onNodeWithText("Adult · no PIN yet, so Sam can only look").assertExists()
        compose.onNodeWithText("Admin · No PIN").assertExists()
    }

    /** Pin: Task 2 made Add person quiet with its icon, and Task 3 keeps it so. */
    @Test
    fun addPersonIsQuietWithItsIcon() {
        show()
        compose.onNodeWithTag("people_add").assertHeightIsEqualTo(48.dp)
            .assert(SemanticsMatcher.expectValue(ButtonToneKey, ButtonTone.Quiet))
        // The icon's glyph is hidden from TalkBack, so it is the one node without text.
        val parts = compose.onAllNodes(hasAnyAncestor(hasTestTag("people_add")), useUnmergedTree = true).fetchSemanticsNodes()
        val label = parts.single { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Add person" } == true }
        val icon = parts.single { node -> node.config.getOrNull(SemanticsProperties.Text) == null }
        assertThat(icon.boundsInRoot.left).isLessThan(label.boundsInRoot.left)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aPersonRowIsSeventyFiveHigh() {
        addSam()
        show()
        compose.awaitTag("person_Sam")
        assertThat(compose.onNodeWithTag("person_Sam").getUnclippedBoundsInRoot().height.value).isWithin(1f).of(75f)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun aLongNameAtLargeTextGrowsTheRowAndEndsInAnEllipsis() {
        val long = "Alexandrina Montgomery-Feather"
        runBlocking { access.pins.addPerson(long, PersonPalette.colors[1], Role.ADULT, null) }
        compose.setContent {
            CulveryTheme(dark = true) {
                CompositionLocalProvider(LocalOverlayHost provides overlay, LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                    Box(Modifier.width(360.dp)) { PeoplePane(editor, reorder = true) }
                }
            }
        }
        compose.awaitTag("person_$long")
        val row = compose.onNodeWithTag("person_$long").getUnclippedBoundsInRoot()
        val subtitle = compose.onNodeWithText("Adult · no PIN yet, so $long can only look", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertThat(row.height.value).isGreaterThan(76f)
        assertThat(subtitle.bottom.value).isAtMost(row.bottom.value)
        val layout = compose.onNodeWithText(long, useUnmergedTree = true).textLayout()
        assertThat(layout.isLineEllipsized(layout.lineCount - 1)).isTrue()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun personRowsSitTwelveApart() {
        addSam()
        show()
        compose.awaitTag("person_Sam")
        val gap = compose.onNodeWithTag("person_Sam").getUnclippedBoundsInRoot().top - compose.onNodeWithTag("person_Alex").getUnclippedBoundsInRoot().bottom
        assertThat(gap.value).isWithin(0.5f).of(12f)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun aLongNameInTheWizardColumnAtLargeTextGrowsTheRowAndCutsNothing() {
        val long = "Alexandrina Montgomery-Feather"
        runBlocking { access.pins.addPerson(long, PersonPalette.colors[1], Role.ADULT, null) }
        compose.setContent {
            CulveryTheme(dark = true) {
                CompositionLocalProvider(LocalOverlayHost provides overlay, LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                    Box(Modifier.width(SetupDimens.wizardColumn)) { PeoplePane(editor, reorder = false) }
                }
            }
        }
        compose.awaitTag("person_$long")
        val row = compose.onNodeWithTag("person_$long").getUnclippedBoundsInRoot()
        val title = compose.onNodeWithText(long, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val subtitle = compose.onNodeWithText("Adult · no PIN yet, so $long can only look", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertThat(row.height.value).isGreaterThan(76f)
        assertThat(title.top.value).isAtLeast(row.top.value)
        assertThat(subtitle.bottom.value).isAtMost(row.bottom.value)
        assertThat(compose.onNodeWithText(long, useUnmergedTree = true).textLayout().isLineEllipsized(0)).isFalse()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theHouseholdStepSaysWhyColoursAndPins() {
        show(content = { HouseholdStep(editor).Content(onNext = {}) })
        compose.onNodeWithText("Who else lives here?").assertExists()
        compose.onNodeWithText(WHO_ELSE_LINE).assertExists()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun withoutAPinAnAdultOrChildCanOnlyLookAndTheSheetSaysSo() {
        addSam()
        show()
        compose.awaitTag("person_Sam")
        compose.onNodeWithTag("person_Sam").performClick()
        compose.onNodeWithText("Without a PIN, Sam can only look.").assertExists()
        compose.onNodeWithTag("person_set_pin").performClick()
        repeat(2) { "2468".forEach { d -> compose.onNodeWithTag("pin_key_$d").performClick() } }
        compose.onNodeWithText("Without a PIN, Sam can only look.").assertDoesNotExist()
    }

    @Test
    fun anAdminWithoutAPinIsNeverToldTheyCanOnlyLook() {
        show()
        compose.onNodeWithTag("people_add").performClick()
        compose.onNodeWithText("Without a PIN, they can only look.").assertExists()
        compose.onNodeWithTag("role_ADMIN").performClick()
        compose.onNodeWithTag("person_set_pin").assertExists()
        compose.onNodeWithTag("person_look_only").assertDoesNotExist()
    }

    @Test
    fun aChildWithoutAPinIsToldTheyCanOnlyLook() {
        show()
        compose.onNodeWithTag("people_add").performClick()
        compose.onNodeWithTag("role_CHILD").performClick()
        compose.onNodeWithTag("person_look_only").assertExists()
    }

    @Test
    fun theMoveButtonsMoveSomeoneAndTheEndsSayWhyTheyAreOff() {
        addSam()
        show()
        compose.awaitTag("person_up_Sam")
        compose.onNodeWithTag("person_up_Alex").assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "already first"))
        compose.onNodeWithTag("person_down_Sam").assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "already last"))
        compose.onNodeWithContentDescription("Move Sam up").performClick()
        compose.waitUntil(5_000) { runBlocking { household.people.first().map { it.name } } == listOf("Sam", "Alex") }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("person_up_Sam").fetchSemanticsNodes().singleOrNull()?.config?.contains(SemanticsProperties.Disabled) == true }
        compose.onNodeWithTag("person_down_Sam").assertIsEnabled()
    }

    /** The row is keyed by the person, so TalkBack's focus moves with them, not with the position. */
    @Test
    fun movingSomeoneKeepsTheirRowAndItsActions() {
        addSam()
        show()
        compose.awaitTag("person_Sam")
        val samRow = compose.onNodeWithTag("person_Sam").fetchSemanticsNode()
        assertThat(samRow.config[SemanticsActions.CustomActions].map { it.label }).containsExactly("Move Sam up")
        compose.onNodeWithContentDescription("Move Sam up").performClick()
        // singleOrNull: a fetch mid-recomposition must not throw out of waitUntil.
        fun top(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().singleOrNull()?.boundsInRoot?.top
        compose.waitUntil(5_000) {
            val sam = top("person_Sam")
            val alex = top("person_Alex")
            sam != null && alex != null && sam < alex
        }
        assertThat(compose.onNodeWithTag("person_Sam").fetchSemanticsNode().id).isEqualTo(samRow.id)
        assertThat(compose.onNodeWithTag("person_Sam").fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label })
            .containsExactly("Move Sam down")
    }

    @Test
    fun aNewPersonStartsOnTheFirstFreeColour() {
        show()
        compose.onNodeWithTag("people_add").performClick()
        compose.onNodeWithTag("swatch_0").assertIsNotEnabled()
        compose.onNodeWithTag("swatch_1").assertIsEnabled().assertIsSelected()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun saveWaitsForAName() {
        show()
        compose.onNodeWithTag("people_add").performClick()
        compose.onNodeWithTag("person_save").assertIsNotEnabled()
        compose.onNodeWithTag("person_name").performTextInput("Sam")
        compose.onNodeWithTag("person_save").assertIsEnabled()
    }

    private fun openNew() {
        show()
        compose.onNodeWithTag("people_add").performClick()
    }

    private fun waitForText(tag: String) = compose.waitUntil(5_000) {
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull()?.config?.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text.orEmpty().isNotEmpty()
    }

    private fun assertNoFieldErrors() =
        listOf("person_name_error", "person_colour_error", "person_role_error", "person_pin_error").forEach { compose.onNodeWithTag(it).assertDoesNotExist() }

    private fun refuseSamAsNew() {
        addSam()
        openNew()
        compose.onNodeWithTag("person_name").performTextInput("Sam")
        compose.onNodeWithTag("person_save").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("person_name_error").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun aTakenNameShowsUnderTheNameAndTypingClearsIt() {
        refuseSamAsNew()
        val taken = someoneCalled("Sam")
        compose.onNodeWithTag("person_name_error").assert(hasText(taken))
        compose.onNodeWithTag("person_message").assert(hasText(""))
        compose.onNodeWithTag("person_name")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, taken))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Name")))
            .assert(hasText("Sam"))
        compose.onNodeWithTag("person_name").performTextInput(" B")
        compose.onNodeWithTag("person_name_error").assertDoesNotExist()
        compose.onNodeWithTag("person_name").assert(hasText("Sam B")).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aTakenNameMovesFocusToTheNameField() {
        refuseSamAsNew()
        compose.waitForIdle()
        compose.onNodeWithTag("person_name").assertIsFocused()
    }

    @Test
    fun aTakenColourShowsUnderColourAndIsBlocked() {
        openNew()
        compose.onNodeWithTag("swatch_1").assertIsSelected()
        addSam()
        compose.onNodeWithTag("person_name").performTextInput("Mia")
        compose.onNodeWithTag("person_save").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("person_colour_error").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.onNodeWithTag("person_colour_error").assert(hasText(COLOUR_TAKEN)).assertIsFocused()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithTag("swatch_1").assertIsNotEnabled()
        compose.onNodeWithTag("swatch_1_block", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("person_name_error").assertDoesNotExist()
        compose.onNodeWithTag("person_message").assert(hasText(""))
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aPinRefusalShowsUnderPin() {
        openNew()
        compose.onNodeWithTag("role_ADMIN").performClick()
        compose.onNodeWithTag("person_name").performTextInput("Mia")
        compose.onNodeWithTag("person_save").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("person_pin_error").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.onNodeWithTag("person_pin_error").assert(hasText(ADMIN_NEEDS_PIN)).assertIsFocused()
        compose.onNodeWithTag("person_message").assert(hasText(""))
        compose.onNodeWithTag("person_name_error").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** The sheet alone on the tablet's canvas, so a test can refuse by hand or put the keyboard up. */
    private fun showSheet(
        form: PersonForm,
        scale: Float = 1f,
        keyboard: Dp = 0.dp,
        canRemove: Boolean = form.existing != null,
    ) {
        compose.setContent {
            CulveryTheme(dark = true) {
                WithFontScale(scale) {
                    Box(Modifier.size(1280.dp, 800.dp)) {
                        Box(Modifier.align(Alignment.CenterEnd)) {
                            PersonEditorSheet(
                                form = form, busy = false, canRemove = canRemove, confirmingRemove = false, choosingPin = false,
                                onChoosePin = {}, onPinChosen = {}, onPinCancelled = {}, onSave = {}, onRemove = {}, onKeep = {},
                                onConfirmRemove = {}, onClose = {}, keyboard = WindowInsets(bottom = keyboard),
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun aFieldRefusalIsScrolledIntoViewWithTheKeyboardUp() {
        val form = PersonForm(existing = null, taken = mapOf(PersonPalette.colors[0] to "Alex"))
        showSheet(form, scale = 1.3f, keyboard = 320.dp)
        val refusal = PeopleOutcome.Refused(ADMIN_NEEDS_PIN, PersonField.PIN)
        compose.runOnIdle { form.refuse(refusal) }
        compose.waitForIdle()
        compose.onNodeWithTag("person_pin_error").assertIsDisplayed()
        assertThat(compose.onNodeWithTag("person_pin_error").getUnclippedBoundsInRoot().bottom.value).isAtMost(480f)
        // The same refusal again, after the field has been scrolled away, brings it back.
        compose.onNodeWithTag("person_name").performScrollTo()
        compose.onNodeWithTag("person_pin_error").assertIsNotDisplayed()
        compose.runOnIdle { form.refuse(refusal) }
        compose.waitForIdle()
        compose.onNodeWithTag("person_pin_error").assertIsDisplayed()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aLastAdminRemoveRefusalShowsAboveTheFooter() {
        val mia = runBlocking { access.pins.addPerson("Mia", PersonPalette.colors[2], Role.ADMIN, "2468") }
        show()
        compose.awaitTag("person_Mia")
        compose.onNodeWithTag("person_Alex").performClick()
        runBlocking { household.removePerson(mia.id) }
        compose.onNodeWithTag("person_remove").performClick()
        compose.onNodeWithTag("person_confirm_remove").performClick()
        waitForText("person_message")
        compose.onNodeWithTag("person_message").assert(hasText(NEEDS_AN_ADMIN))
        compose.onNodeWithTag("person_confirm").assertDoesNotExist()
        assertNoFieldErrors()
        val message = compose.onNodeWithTag("person_message").getUnclippedBoundsInRoot()
        assertThat(message.bottom.value).isAtMost(compose.onNodeWithTag("person_save").getUnclippedBoundsInRoot().top.value)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun savingANewPersonAddsThemAndClosesTheSheet() {
        show()
        compose.onNodeWithTag("people_add").performClick()
        compose.onNodeWithTag("person_name").performTextInput("Sam")
        compose.onNodeWithTag("person_save").performClick()
        compose.waitUntil(5_000) { overlay.content == null }
        assertThat(runBlocking { household.members.first() }.map { it.person.name }).containsExactly("Alex", "Sam").inOrder()
    }

    @Test
    fun theLastAdminCannotRemoveThemself() {
        show()
        compose.onNodeWithTag("person_Alex").performClick()
        compose.onNodeWithTag("person_save").assertExists()
        compose.onNodeWithTag("person_remove").assertDoesNotExist()
    }

    @Test
    fun removingSomeoneAsksFirst() {
        addSam()
        show()
        compose.awaitTag("person_Sam")
        compose.onNodeWithTag("person_Sam").performClick()
        compose.onNodeWithTag("person_remove").performClick()
        compose.onNodeWithText("Remove Sam? Sam’s events and calendars will show as Family.").assertExists()
        compose.onNodeWithTag("person_keep").performClick()
        compose.onNodeWithTag("person_confirm_remove").assertDoesNotExist()
        compose.onNodeWithTag("person_remove").performClick()
        compose.onNodeWithTag("person_confirm_remove").performClick()
        compose.waitUntil(5_000) { overlay.content == null }
        assertThat(access.toasts.messages).containsExactly("Sam removed.")
    }

    @Test
    fun aPinChosenInTheSheetCanBeChangedOrRemoved() {
        addSam()
        show()
        compose.awaitTag("person_Sam")
        compose.onNodeWithTag("person_Sam").performClick()
        compose.onNodeWithTag("person_set_pin").performClick()
        compose.onNodeWithText("Choose a 4-digit PIN").assertExists()
        repeat(2) { "2468".forEach { d -> compose.onNodeWithTag("pin_key_$d").performClick() } }
        compose.onNodeWithTag("person_change_pin").assertExists()
        compose.onNodeWithTag("person_remove_pin").performClick()
        compose.onNodeWithTag("person_set_pin").assertExists()
    }

    @Test
    fun addPersonGoesOnceTheHouseholdIsFull() {
        runBlocking { (1 until MAX_PEOPLE).forEach { i -> access.pins.addPerson("P$i", PersonPalette.colors[i], Role.ADULT, null) } }
        show()
        compose.awaitTag("person_P${MAX_PEOPLE - 1}")
        compose.onNodeWithTag("people_add").assertDoesNotExist()
    }

    @Test
    fun aSaveFailureStaysAboveTheFooter() {
        addSam()
        show()
        compose.awaitTag("person_Sam")
        compose.onNodeWithTag("person_Sam").performClick()
        compose.onNodeWithTag("person_name").performTextInput("my")
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER no_updates BEFORE UPDATE ON person BEGIN SELECT RAISE(ABORT, 'x'); END")
        compose.onNodeWithTag("person_save").performClick()
        compose.waitUntil(5_000) { compose.onNodeWithTag("person_message").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text.orEmpty().isNotEmpty() }
        compose.onNodeWithTag("person_message").assert(hasText("Couldn’t save Sam’s details — try again."))
        compose.onNodeWithTag("person_name").assert(hasText("mySam"))
        assertNoFieldErrors()
        assertNoSecretsLogged("People", listOf("Sam"))
    }

    @Test
    fun anErrorThatEscapesTheEditorShowsCouldNotSaveAndLogsOnlyItsClass() {
        addSam()
        val failing = PeopleEditor(household, access.pins, access.control, object : uk.co.siland.culvery.core.plugin.Toaster {
            override fun show(message: String, icon: String, urgent: Boolean, action: ToastAction?) = throw IllegalStateException("Sam removed")
        })
        compose.setContent {
            CulveryTheme(dark = true) {
                CompositionLocalProvider(LocalOverlayHost provides overlay) {
                    Box {
                        PeoplePane(failing, reorder = true)
                        overlay.content?.invoke()
                    }
                }
            }
        }
        compose.awaitTag("person_Sam")
        compose.onNodeWithTag("person_Sam").performClick()
        compose.onNodeWithTag("person_remove").performClick()
        compose.onNodeWithTag("person_confirm_remove").performClick()
        compose.waitUntil(5_000) { compose.onNodeWithTag("person_message").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text.orEmpty().isNotEmpty() }
        compose.onNodeWithTag("person_message").assert(hasText("Couldn’t save Sam’s details — try again."))
        assertNoSecretsLogged("PersonSheet", listOf("Sam"))
    }

    @Test
    fun aPersonsRowSaysItChangesTheirDetailsAndTheMoveButtonsAreNamedOnce() {
        addSam()
        show()
        compose.awaitTag("person_Sam")
        assertThat(compose.onNodeWithTag("person_Sam").fetchSemanticsNode().config[SemanticsActions.OnClick].label).isEqualTo("change Sam’s details")
        assertThat(compose.onNodeWithTag("person_up_Sam").fetchSemanticsNode().config[SemanticsActions.OnClick].label).isNull()
        compose.assertAccessible()
    }

    @Test
    fun thePersonSheetsTitleIsAHeading() {
        show()
        compose.onNodeWithTag("people_add").performClick()
        compose.onAllNodesWithText("Add person", useUnmergedTree = true).filterToOne(isHeading()).assertExists()
    }

    @Test
    fun aPersonsSheetIsAPaneThatItsPinChooserHides() {
        addSam()
        show()
        compose.awaitTag("person_Sam")
        compose.onNodeWithTag("person_Sam").performClick()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Sam")).assertExists()
        compose.onNodeWithTag("person_set_pin").performClick()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Choose a 4-digit PIN")).assertExists()
        compose.onNodeWithTag("person_save").assertDoesNotExist()
    }

    @Test
    fun thePersonSheetPassesTheSharedCheck() {
        addSam()
        show()
        compose.awaitTag("person_Sam")
        compose.onNodeWithTag("person_Sam").performClick()
        compose.onNodeWithTag("swatch_0").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "used by Alex"))
        compose.assertAccessible()
    }

    @Test
    fun theMoveUpActionFromTalkBackMovesSomeone() {
        addSam()
        show()
        compose.awaitTag("person_Sam")
        val up = compose.onNodeWithTag("person_Sam").fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == "Move Sam up" }
        compose.runOnUiThread { up.action() }
        compose.waitUntil(5_000) { runBlocking { household.people.first().map { it.name } } == listOf("Sam", "Alex") }
    }

    @Test
    fun typingANameMarksThePersonSheetEdited() {
        show()
        compose.onNodeWithTag("people_add").performClick()
        compose.onNodeWithTag("person_name").performTextInput("Sam")
        compose.waitUntil(5_000) { overlay.edited }
        compose.onNodeWithTag("person_name").performTextReplacement("")
        compose.waitUntil(5_000) { !overlay.edited }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun rolesAreRadioRows() {
        openNew()
        listOf("ADMIN", "ADULT", "CHILD").forEach {
            compose.onNodeWithTag("role_$it").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, SemanticsRole.RadioButton))
        }
        compose.onNodeWithTag("role_ADULT").assertIsSelected().assertTextEquals("Adult", "Can add and change any event")
        compose.onNodeWithTag("role_ADMIN").assertIsNotSelected().assertTextEquals("Admin", "Can change settings and people")
        compose.onNodeWithTag("role_CHILD").assertIsNotSelected().assertTextEquals("Child", "Can add their own events")
        compose.onNodeWithTag("role_ADULT").onParent().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
        assertThat(pixelAt(compose.onNodeWithTag("role_ADULT"), 8.dp, 8.dp)).isEqualTo(DarkColors.accentSoft.toArgb())
        compose.onNodeWithTag("role_ADMIN").performClick()
        compose.onNodeWithTag("role_ADMIN").assertIsSelected()
        compose.onNodeWithTag("role_ADULT").assertIsNotSelected()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun takenColoursAreBlockedAndNamed() {
        addSam()
        openNew()
        listOf(0, 1).forEach { i ->
            compose.onNodeWithTag("swatch_$i").assertIsNotEnabled().performClick()
            compose.onNodeWithTag("swatch_$i").assertIsNotSelected()
            compose.onNodeWithTag("swatch_${i}_block", useUnmergedTree = true).assertExists()
        }
        compose.onNodeWithTag("person_taken_line").assert(hasText("Green and blue are taken by Alex and Sam."))
        compose.onNodeWithTag("swatch_2").assertIsSelected()
        compose.onNodeWithTag("swatch_2_check", useUnmergedTree = true).assertExists()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun editingYourselfNeverListsYourOwnColour() {
        addSam()
        show()
        compose.awaitTag("person_Sam")
        compose.onNodeWithTag("person_Sam").performClick()
        compose.onNodeWithTag("swatch_1").assertIsEnabled().assertIsSelected()
        compose.onNodeWithTag("swatch_1_block", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("person_taken_line").assert(hasText("Green is taken by Alex."))
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun thePinHintSitsBesideSetPin() {
        openNew()
        compose.onNodeWithTag("person_name").performTextInput("Sam")
        val hint = compose.onNodeWithTag("person_look_only").assert(hasText("Without a PIN, Sam can only look.")).getUnclippedBoundsInRoot()
        val button = compose.onNodeWithTag("person_set_pin").getUnclippedBoundsInRoot()
        assertThat(hint.left.value - button.right.value).isWithin(1f).of(13f)
        assertThat((hint.top.value + hint.bottom.value) / 2).isWithin(1f).of((button.top.value + button.bottom.value) / 2)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun aLongNameWrapsThePinHintAndLeavesSetPinAlone() {
        val form = PersonForm(existing = null, taken = emptyMap()).apply { name = "Alexandrina Montgomery-Feather" }
        showSheet(form, scale = 1.3f)
        compose.onNodeWithTag("person_sheet").assertWidthIsEqualTo(ShellTokens.sheetWidth)
        compose.onNodeWithTag("person_set_pin").assertHeightIsEqualTo(48.dp)
        assertThat(compose.onNodeWithTag("person_look_only").textLayout().lineCount).isGreaterThan(1)
        val hint = compose.onNodeWithTag("person_look_only").getUnclippedBoundsInRoot()
        val button = compose.onNodeWithTag("person_set_pin").getUnclippedBoundsInRoot()
        assertThat(hint.left.value).isAtLeast(button.right.value)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun saveFillsTheFooter() {
        val body = ShellTokens.sheetWidth - SetupDimens.sheetPaddingH * 2
        val form = PersonForm(existing = null, taken = emptyMap()).apply { name = "Sam" }
        showSheet(form)
        compose.onNodeWithTag("person_save").assertTextEquals("Save person").assertHeightIsEqualTo(60.dp).assertWidthIsEqualTo(body)
        compose.onNodeWithTag("person_remove").assertDoesNotExist()
    }

    @Test
    fun anExistingPersonHasRemoveBesideASaveThatFillsTheRest() {
        val body = ShellTokens.sheetWidth - SetupDimens.sheetPaddingH * 2
        val sam = Member(Person(PersonId("sam"), "Sam", PersonPalette.colors[1]), Role.ADULT, hasPin = false)
        showSheet(PersonForm(sam, taken = emptyMap()))
        val remove = compose.onNodeWithTag("person_remove").assertHeightIsEqualTo(60.dp).getUnclippedBoundsInRoot()
        val save = compose.onNodeWithTag("person_save").assertTextEquals("Save changes").assertHeightIsEqualTo(60.dp).getUnclippedBoundsInRoot()
        assertThat(save.left.value - remove.right.value).isWithin(1f).of(SetupDimens.footerGap.value)
        assertThat(save.right.value - remove.left.value).isWithin(1f).of(body.value)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theFootDoesNotMoveWhileSaving() {
        val sam = Member(Person(PersonId("sam"), "Sam", PersonPalette.colors[1]), Role.ADULT, hasPin = false)
        val form = PersonForm(sam, taken = emptyMap())
        var busy by mutableStateOf(false)
        compose.setContent {
            CulveryTheme(dark = true) {
                Box(Modifier.size(1280.dp, 800.dp)) {
                    PersonEditorSheet(
                        form = form, busy = busy, canRemove = true, confirmingRemove = false, choosingPin = false,
                        onChoosePin = {}, onPinChosen = {}, onPinCancelled = {}, onSave = {}, onRemove = {}, onKeep = {},
                        onConfirmRemove = {}, onClose = {},
                    )
                }
            }
        }
        val before = listOf("person_save", "person_remove").map { compose.onNodeWithTag(it).getUnclippedBoundsInRoot() }
        busy = true
        compose.waitForIdle()
        val after = listOf("person_save", "person_remove").map { compose.onNodeWithTag(it).getUnclippedBoundsInRoot() }
        assertThat(after).isEqualTo(before)
    }

    @Test
    fun aToastSitsAboveThePersonSheetsFoot() {
        val clearance = ToastClearance()
        val form = PersonForm(existing = null, taken = emptyMap()).apply { name = "Sam" }
        compose.setContent {
            CulveryTheme(dark = true) {
                CompositionLocalProvider(LocalToastClearance provides clearance) {
                    BoxWithConstraints(Modifier.size(1280.dp, 800.dp)) {
                        Box(Modifier.align(Alignment.CenterEnd)) {
                            PersonEditorSheet(
                                form = form, busy = false, canRemove = false, confirmingRemove = false, choosingPin = false,
                                onChoosePin = {}, onPinChosen = {}, onPinCancelled = {}, onSave = {}, onRemove = {}, onKeep = {},
                                onConfirmRemove = {}, onClose = {},
                            )
                        }
                        val top = clearance.top
                        if (top != null) {
                            val above = maxHeight - with(LocalDensity.current) { top.toDp() } + ShellTokens.toastAboveFoot
                            Box(Modifier.fillMaxSize().padding(bottom = above), contentAlignment = Alignment.BottomCenter) { HhToast("Saved", Icons.CHECK) }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val toast = compose.onNodeWithTag("toast").getUnclippedBoundsInRoot()
        val save = compose.onNodeWithTag("person_save").getUnclippedBoundsInRoot()
        assertThat(save.top.value - toast.bottom.value).isAtLeast(ShellTokens.toastAboveFoot.value - 0.5f)
    }

    @Test
    fun aNewPersonWithNoFreeColourStartsUnchosen() {
        val form = PersonForm(existing = null, taken = PersonPalette.colors.associateWith { "Someone" }).apply { name = "Mia" }
        showSheet(form)
        PersonPalette.colors.indices.forEach { compose.onNodeWithTag("swatch_$it").assertIsNotSelected() }
        compose.onNodeWithTag("person_save").assertIsNotEnabled()
    }

    /** Pin: the pad over the sheet already hid it from TalkBack. */
    @Test
    fun thePinPadHidesTheSheetFromTalkBack() {
        openNew()
        compose.onNodeWithTag("person_set_pin").performClick()
        compose.onNodeWithText("Choose a 4-digit PIN").assertExists()
        listOf("person_name", "person_save", "role_ADMIN", "swatch_1", "person_set_pin").forEach { compose.onNodeWithTag(it).assertDoesNotExist() }
    }
}
