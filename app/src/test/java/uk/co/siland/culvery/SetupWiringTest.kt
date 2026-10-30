package uk.co.siland.culvery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.door.DoorbellChoice
import uk.co.siland.culvery.capability.door.ui.DoorSettingsActions
import uk.co.siland.culvery.capability.door.ui.DoorSettingsScreen
import uk.co.siland.culvery.capability.door.ui.DoorSettingsUi
import uk.co.siland.culvery.capability.lights.ui.LightsSettingsPage
import uk.co.siland.culvery.capability.lights.ui.LightsSettingsUi
import uk.co.siland.culvery.capability.music.ui.MusicSettingsActions
import uk.co.siland.culvery.capability.music.ui.MusicSettingsScreen
import uk.co.siland.culvery.capability.music.ui.MusicSettingsUi
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.HomeApp
import uk.co.siland.culvery.core.plugin.HomeCard
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.plugin.SetupStep
import uk.co.siland.culvery.core.plugin.test.RecordingNavigator
import uk.co.siland.culvery.core.plugin.test.TestOverlay
import uk.co.siland.culvery.core.setup.pages.KioskPage
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.HhPageTitle
import uk.co.siland.culvery.display.CameraState
import uk.co.siland.culvery.display.DisplayPrefs
import uk.co.siland.culvery.display.ui.DisplaySettingsActions
import uk.co.siland.culvery.display.ui.DisplaySettingsScreen
import uk.co.siland.culvery.display.ui.DisplaySettingsUi
import uk.co.siland.culvery.shell.FakeAccessControl
import uk.co.siland.culvery.shell.FakeCapability

private class Step(override val id: String, override val order: Int) : SetupStep {
    override val done: Flow<Boolean> = flowOf(false)

    @Composable
    override fun Content(onNext: () -> Unit) = Unit
}

private class Page(override val id: String, override val order: Int) : SettingsPage {
    override val title = id

    @Composable
    override fun Content() = Unit
}

private class WithSetup(private val steps: List<SetupStep>, private val pages: List<SettingsPage>) : Capability {
    override val id = "calendar"
    override val label = "Calendar"
    override val icon = "calendar_month"
    override val order = 10
    override val hasTab: Flow<Boolean> = flowOf(false)
    override fun cards(): Flow<List<HomeCard>> = flowOf(emptyList())
    override fun setupSteps() = steps
    override fun settingsPages() = pages

    @Composable
    override fun TabContent() = Unit
}

@RunWith(AndroidJUnit4::class)
class SetupWiringTest {
    @get:Rule val compose = createComposeRule()

    private val calendar = WithSetup(listOf(Step("calendar.review", 410), Step("calendar.connect", 400)), listOf(Page("calendars", 400)))

    @Test
    fun theWizardRunsTheCoreStepsAndEachCapabilitysInOrder() {
        val core = setOf(Step("done", 1000), Step("welcome", 0), Step("household", 300), Step("you", 200), Step("location", 100))
        assertThat(wizardSteps(core, setOf(calendar)).map { it.id })
            .containsExactly("welcome", "location", "you", "household", "calendar.connect", "calendar.review", "done").inOrder()
    }

    @Test
    fun settingsListsTheCorePagesAndEachCapabilitysInOrder() {
        val core = setOf(Page("kiosk", 900), Page("location", 0), Page("people", 100))
        assertThat(settingsPages(core, setOf(calendar)).map { it.id }).containsExactly("location", "people", "calendars", "kiosk").inOrder()
    }

    @Test
    fun aCapabilityWithoutStepsOrPagesAddsNone() {
        val plain = FakeCapability("lights", order = 20, shown = true)
        assertThat(wizardSteps(setOf(Step("welcome", 0)), setOf(plain)).map { it.id }).containsExactly("welcome")
        assertThat(settingsPages(emptySet(), setOf(plain))).isEmpty()
    }

    @Test
    fun onResumeTheKioskPinsOnlyOnceSetupIsCompleteAndNotAfterExitKiosk() {
        assertThat(shouldPin(setupComplete = false, kioskExited = false)).isFalse()
        assertThat(shouldPin(setupComplete = true, kioskExited = false)).isTrue()
        assertThat(shouldPin(setupComplete = true, kioskExited = true)).isFalse()
    }

    @Test
    fun theKioskPinsAsSoonAsSetupCompletesWhileResumed() {
        // Open Culvery on Done: false then true, with the activity in front.
        assertThat(pinOnSetupRead(previous = false, now = true, resumed = true, kioskExited = false)).isTrue()
        // An existing install's first read, after onResume has run.
        assertThat(pinOnSetupRead(previous = null, now = true, resumed = true, kioskExited = false)).isTrue()
        // Not behind the scenes, not after Use other apps, not for a read that changed nothing, never for the wizard.
        assertThat(pinOnSetupRead(previous = false, now = true, resumed = false, kioskExited = false)).isFalse()
        assertThat(pinOnSetupRead(previous = false, now = true, resumed = true, kioskExited = true)).isFalse()
        assertThat(pinOnSetupRead(previous = true, now = true, resumed = true, kioskExited = false)).isFalse()
        assertThat(pinOnSetupRead(previous = null, now = false, resumed = true, kioskExited = false)).isFalse()
    }

    @Test
    fun touchesCountWhileSettingsIsOpenOrTheWizardShows() {
        assertThat(countsTouches(complete = null, settingsOpen = false)).isFalse()
        assertThat(countsTouches(complete = true, settingsOpen = false)).isFalse()
        assertThat(countsTouches(complete = true, settingsOpen = true)).isTrue()
        assertThat(countsTouches(complete = false, settingsOpen = false)).isTrue()
    }

    @Test
    fun theSplashHoldsUntilSetupIsKnown() {
        assertThat(holdSplash(complete = null, cardsLoaded = false, shownMillis = 0)).isTrue()
    }

    @Test
    fun theWizardShowsAtOnce() {
        assertThat(holdSplash(complete = false, cardsLoaded = false, shownMillis = 0)).isFalse()
    }

    @Test
    fun homeWaitsForItsFirstCards() {
        assertThat(holdSplash(complete = true, cardsLoaded = false, shownMillis = 0)).isTrue()
        assertThat(holdSplash(complete = true, cardsLoaded = true, shownMillis = 0)).isFalse()
    }

    @Test
    fun theSplashNeverHoldsForTwoSeconds() {
        assertThat(holdSplash(complete = true, cardsLoaded = false, shownMillis = SPLASH_MAX_MS - 1)).isTrue()
        assertThat(holdSplash(complete = null, cardsLoaded = false, shownMillis = SPLASH_MAX_MS)).isFalse()
    }

    @Test
    fun theSessionsEndIsCheckedOnStartAndOnResume() {
        val access = FakeAccessControl()
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this)
            override val lifecycle: Lifecycle get() = registry
        }
        owner.registry.addObserver(checksExpiryOnReturn(access))
        owner.registry.currentState = Lifecycle.State.CREATED
        assertThat(access.expiryChecks).isEqualTo(0)
        owner.registry.currentState = Lifecycle.State.STARTED
        assertThat(access.expiryChecks).isEqualTo(1)
        owner.registry.currentState = Lifecycle.State.RESUMED
        assertThat(access.expiryChecks).isEqualTo(2)
    }

    private fun SemanticsNodeInteraction.fontSize() = mutableListOf<TextLayoutResult>()
        .also { fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(it) }
        .single().layoutInput.style.fontSize

    /** Display, Door, Lights, Music and Tablet lock draw their title through the same `HhPageTitle` as the wizard's steps; Home location and People are checked in core:setup, Calendars in core:ui. */
    @Test
    fun everySettingsPageTitleIsTheSharedTitle() {
        val pages: List<Pair<String, @Composable () -> Unit>> = listOf(
            "display" to {
                DisplaySettingsScreen(
                    DisplaySettingsUi(DisplayPrefs(), CameraState.ALLOWED, sunKnown = true, today = LocalDate.of(2026, 10, 29)),
                    DisplaySettingsActions({ _, _ -> }, {}, {}, {}, {}, {}, {}),
                )
            },
            "door" to {
                DoorSettingsScreen(
                    DoorSettingsUi("Ring", null, 0.5f, DoorbellChoice.FOLLOW_THEME, false, LocalTime.of(22, 0), LocalTime.of(7, 0), null, "never", true, true, false),
                    DoorSettingsActions({}, {}, {}, { _, _, _ -> }, {}, {}),
                )
            },
            "lights" to {
                LightsSettingsPage(
                    LightsSettingsUi(true, null, null, false, emptyList(), null, emptySet(), 0, null, emptySet()),
                    {}, {}, {}, {}, {},
                )
            },
            "music" to {
                MusicSettingsScreen(
                    MusicSettingsUi(true, null, null, false, emptyList(), null, 50),
                    MusicSettingsActions({}, {}, {}, {}),
                )
            },
            "kiosk" to { KioskPage(object : HomeApp { override val isDefault = MutableStateFlow(false) }).Content() },
        )
        compose.setContent {
            CulveryTheme(dark = true) {
                CompositionLocalProvider(LocalShellNavigator provides RecordingNavigator(), LocalOverlayHost provides TestOverlay()) {
                    Column {
                        Box(Modifier.testTag("reference")) { HhPageTitle("Reference") }
                        pages.forEach { (id, page) -> Box(Modifier.testTag("page_$id")) { page() } }
                    }
                }
            }
        }
        val shared = compose.onAllNodes(isHeading() and hasAnyAncestor(hasTestTag("reference")))[0].fontSize()
        assertThat(shared).isEqualTo(37.sp)
        pages.forEach { (id, _) ->
            val title = compose.onAllNodes(isHeading() and hasAnyAncestor(hasTestTag("page_$id")))[0]
            assertThat(title.fontSize()).isEqualTo(shared)
        }
    }
}
