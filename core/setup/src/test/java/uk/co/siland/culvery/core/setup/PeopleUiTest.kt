package uk.co.siland.culvery.core.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
import uk.co.siland.culvery.core.access.Identified
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.MAX_PEOPLE
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.PersonPalette

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
    private fun show() {
        compose.setContent {
            CulveryTheme(dark = true) {
                CompositionLocalProvider(LocalOverlayHost provides overlay) {
                    Box {
                        PeoplePane(editor)
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
        show()
        compose.awaitText("Adult · No PIN")
        compose.onNodeWithText("Admin · PIN set").assertExists()
        compose.onNodeWithText("Adult · No PIN").assertExists()
    }

    @Test
    fun aNewPersonStartsOnTheFirstFreeColourWithTakenOnesStruck() {
        show()
        compose.onNodeWithTag("people_add").performClick()
        compose.onNodeWithTag("swatch_0").assertIsNotEnabled()
        compose.onNodeWithTag("swatch_1").assertIsEnabled().assertIsSelected()
    }

    @Test
    fun saveWaitsForAName() {
        show()
        compose.onNodeWithTag("people_add").performClick()
        compose.onNodeWithTag("person_save").assertIsNotEnabled()
        compose.onNodeWithTag("person_name").performTextInput("Sam")
        compose.onNodeWithTag("person_save").assertIsEnabled()
    }

    @Test
    fun aRefusalShowsInTheSheetAndKeepsWhatWasTyped() {
        show()
        compose.onNodeWithTag("people_add").performClick()
        compose.onNodeWithTag("person_name").performTextInput("alex")
        compose.onNodeWithTag("person_save").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("person_message").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("person_message").assert(hasText("Someone is already called alex."))
        compose.onNodeWithTag("person_name").assert(hasText("alex"))
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
        compose.onNodeWithText("Remove Sam? Sam's events and calendars show as Family.").assertExists()
        compose.onNodeWithTag("person_keep").performClick()
        compose.onNodeWithTag("person_confirm_remove").assertDoesNotExist()
        compose.onNodeWithTag("person_remove").performClick()
        compose.onNodeWithTag("person_confirm_remove").performClick()
        compose.waitUntil(5_000) { overlay.content == null }
        assertThat(access.toasts.messages).containsExactly("Sam removed")
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
    fun addPersonGoesOnceEightPeopleLiveHere() {
        runBlocking { (1 until MAX_PEOPLE).forEach { i -> access.pins.addPerson("P$i", PersonPalette.colors[i], Role.ADULT, null) } }
        show()
        compose.awaitTag("person_P7")
        compose.onNodeWithTag("people_add").assertDoesNotExist()
    }

    @Test
    fun aWriteThatFailsKeepsTheSheetAndWhatWasTyped() {
        addSam()
        show()
        compose.awaitTag("person_Sam")
        compose.onNodeWithTag("person_Sam").performClick()
        compose.onNodeWithTag("person_name").performTextInput("my")
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER no_updates BEFORE UPDATE ON person BEGIN SELECT RAISE(ABORT, 'x'); END")
        compose.onNodeWithTag("person_save").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("person_message").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("person_message").assert(hasText("Couldn't save — try again."))
        compose.onNodeWithTag("person_name").assert(hasText("mySam"))
        assertNoSecretsLogged("People", listOf("Sam"))
    }

    @Test
    fun anErrorThatEscapesTheEditorShowsCouldNotSaveAndLogsOnlyItsClass() {
        addSam()
        val failing = PeopleEditor(household, access.pins, access.control, object : uk.co.siland.culvery.core.plugin.Toaster {
            override fun show(message: String, icon: String) = throw IllegalStateException("Sam removed")
        })
        compose.setContent {
            CulveryTheme(dark = true) {
                CompositionLocalProvider(LocalOverlayHost provides overlay) {
                    Box {
                        PeoplePane(failing)
                        overlay.content?.invoke()
                    }
                }
            }
        }
        compose.awaitTag("person_Sam")
        compose.onNodeWithTag("person_Sam").performClick()
        compose.onNodeWithTag("person_remove").performClick()
        compose.onNodeWithTag("person_confirm_remove").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("person_message").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("person_message").assert(hasText("Couldn't save — try again."))
        assertNoSecretsLogged("PersonSheet", listOf("Sam"))
    }
}
