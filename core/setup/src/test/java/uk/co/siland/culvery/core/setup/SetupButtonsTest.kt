package uk.co.siland.culvery.core.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.household.Member
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role as HouseholdRole
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.test.RecordingNavigator
import uk.co.siland.culvery.core.setup.pages.KioskPage
import uk.co.siland.culvery.core.setup.steps.WelcomeContent
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.HhToast
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.LocalToastClearance
import uk.co.siland.culvery.core.ui.PersonPalette
import uk.co.siland.culvery.core.ui.ShellTokens
import uk.co.siland.culvery.core.ui.ToastClearance
import uk.co.siland.culvery.core.ui.WithFontScale
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel

/** Every button in the wizard, Settings and the person sheet is the shared one: a button, 48 dp, or 60 in a footer. */
@RunWith(AndroidJUnit4::class)
class SetupButtonsTest {
    @get:Rule val compose = createComposeRule()

    private val sam = Member(Person(PersonId("sam"), "Sam", PersonPalette.colors[1]), HouseholdRole.ADULT, hasPin = false)

    private fun show(content: @Composable () -> Unit) = compose.setContent {
        CulveryTheme(dark = false) {
            CompositionLocalProvider(LocalShellNavigator provides RecordingNavigator()) { content() }
        }
    }

    private fun assertButton(tag: String, height: Dp) {
        compose.onNodeWithTag(tag)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHeightIsEqualTo(height)
    }

    @Test
    fun theWizardsButtonsAreButtons() {
        show {
            Box(Modifier.size(1280.dp, 800.dp)) {
                WizardFrame(6, 2, back = {}, forward = Forward.Next("Next", enabled = false), busy = false, onForward = {}, onSkip = {}) {
                    WelcomeContent(onSample = {}, busy = false)
                    PeopleList(listOf(sam), onEdit = {}, onAdd = {}, onMove = null)
                }
            }
        }
        listOf("wizard_back", "wizard_next").forEach { assertButton(it, 60.dp) }
        listOf("welcome_sample", "people_add").forEach { assertButton(it, 48.dp) }
        // The arrow (an icon with no semantics of its own) is the one node inside Next that is not its label, and follows it.
        val inNext = hasAnyAncestor(hasTestTag("wizard_next"))
        val label = compose.onNode(hasText("Next") and inNext, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val arrow = compose.onAllNodes(inNext and !hasText("Next"), useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }
        assertThat(arrow.map { it.left >= label.right }).contains(true)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Back and Next sit inside the 640 dp column, not at the window's edges. */
    @Test
    fun backAndNextSitInsideTheColumn() {
        show {
            Box(Modifier.size(1280.dp, 800.dp)) {
                WizardFrame(6, 2, back = {}, forward = Forward.Next("Next", enabled = true), busy = false, onForward = {}, onSkip = {}) {}
            }
        }
        val column = compose.onNodeWithTag("wizard_step").assertWidthIsEqualTo(640.dp).getUnclippedBoundsInRoot()
        assertThat(compose.onNodeWithTag("wizard_back").getUnclippedBoundsInRoot().left.value).isWithin(1f).of(column.left.value)
        assertThat(compose.onNodeWithTag("wizard_next").getUnclippedBoundsInRoot().right.value).isWithin(1f).of(column.right.value)
        compose.assertClickablesHaveRoleAndLabel()
    }

    private fun assertAboveKeyboardAt(scale: Float) {
        compose.setContent {
            CulveryTheme(dark = false) {
                WithFontScale(scale) {
                    Box(Modifier.size(1280.dp, 800.dp)) {
                        WizardFrame(6, 2, back = {}, forward = Forward.Next("Next", enabled = true), busy = false, onForward = {}, onSkip = {}, keyboard = WindowInsets(bottom = 320.dp)) {}
                    }
                }
            }
        }
        assertThat(compose.onNodeWithTag("wizard_next").getUnclippedBoundsInRoot().bottom).isAtMost(480.dp)
        assertThat(compose.onNodeWithTag("wizard_back").getUnclippedBoundsInRoot().bottom).isAtMost(480.dp)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** 4e session 1: the Samsung keyboard covered Back and Next, and the hidden navigation bar left no way to close it. */
    @Test
    fun theWizardsButtonsStayAboveTheKeyboard() = assertAboveKeyboardAt(1.0f)

    @Test
    fun theWizardsButtonsStayAboveTheKeyboardAtLargeText() = assertAboveKeyboardAt(1.3f)

    /** The Back, Skip and Next row keeps toasts above it (9c §5). */
    @Test
    fun aToastSitsAboveTheWizardsNext() {
        val clearance = ToastClearance()
        compose.setContent {
            CulveryTheme(dark = false) {
                CompositionLocalProvider(LocalToastClearance provides clearance) {
                    BoxWithConstraints(Modifier.size(1280.dp, 800.dp)) {
                        WizardFrame(6, 2, back = {}, forward = Forward.Next("Next", enabled = true), busy = false, onForward = {}, onSkip = {}) {}
                        val top = clearance.top
                        if (top != null) {
                            val above = maxHeight - with(LocalDensity.current) { top.toDp() } + ShellTokens.toastAboveFoot
                            Box(Modifier.fillMaxSize().padding(bottom = above), contentAlignment = Alignment.BottomCenter) {
                                HhToast("Saved", Icons.CHECK)
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val toast = compose.onNodeWithTag("toast").getUnclippedBoundsInRoot()
        val next = compose.onNodeWithTag("wizard_next").getUnclippedBoundsInRoot()
        assertThat(next.top.value - toast.bottom.value).isAtLeast(ShellTokens.toastAboveFoot.value - 0.5f)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun skipIsAButton() {
        show { WizardFrame(6, 2, back = null, forward = Forward.Skip, busy = false, onForward = {}, onSkip = {}) {} }
        assertButton("wizard_skip", 60.dp)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun enterPinIsAButton() {
        show { PinGateContent(busy = false, onEnterPin = {}) }
        assertButton("wizard_enter_pin", 48.dp)
    }

    @Test
    fun settingsCloseAndTheKioskPageAreButtons() {
        show { SettingsScreen(listOf(KioskPage(FakeHomeApp(default = false))), onClose = {}) }
        listOf("settings_close", "choose_home_app", "settings_exit_kiosk").forEach { assertButton(it, 48.dp) }
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun thePersonSheetsButtonsAreButtons() {
        show {
            PersonEditorSheet(
                form = PersonForm(sam, taken = mapOf(PersonPalette.colors[0] to "Alex")),
                busy = false, canRemove = true, confirmingRemove = false, choosingPin = false,
                onChoosePin = {}, onPinChosen = {}, onPinCancelled = {}, onSave = {}, onRemove = {}, onKeep = {},
                onConfirmRemove = {}, onClose = {},
            )
        }
        assertButton("person_set_pin", 48.dp)
        assertButton("person_remove", 60.dp)
        assertButton("person_save", 60.dp)
    }
}
