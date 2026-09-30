package uk.co.siland.culvery

import androidx.compose.runtime.Composable
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Test
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.HomeCard
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.plugin.SetupStep
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

class SetupWiringTest {
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
        // Not behind the scenes, not after Exit kiosk, not for a read that changed nothing, never for the wizard.
        assertThat(pinOnSetupRead(previous = false, now = true, resumed = false, kioskExited = false)).isFalse()
        assertThat(pinOnSetupRead(previous = false, now = true, resumed = true, kioskExited = true)).isFalse()
        assertThat(pinOnSetupRead(previous = true, now = true, resumed = true, kioskExited = false)).isFalse()
        assertThat(pinOnSetupRead(previous = null, now = false, resumed = true, kioskExited = false)).isFalse()
    }

    @Test
    fun touchesCountWhileSettingsIsOpenOrTheWizardShows() {
        val access = FakeAccessControl()
        assertThat(touchTarget(complete = null, settingsOpen = false, access = access)).isNull()
        assertThat(touchTarget(complete = true, settingsOpen = false, access = access)).isNull()
        touchTarget(complete = true, settingsOpen = true, access = access)!!.invoke()
        touchTarget(complete = false, settingsOpen = false, access = access)!!.invoke()
        assertThat(access.touches).isEqualTo(2)
    }
}
