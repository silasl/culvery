package uk.co.siland.culvery.core.setup.steps

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import java.util.Optional
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.plugin.SetupStep
import uk.co.siland.culvery.core.setup.SampleHousehold
import uk.co.siland.culvery.core.setup.SetupState
import uk.co.siland.culvery.core.setup.START
import uk.co.siland.culvery.core.setup.StepTitle
import uk.co.siland.culvery.core.setup.USE_SAMPLE_HOUSEHOLD
import uk.co.siland.culvery.core.setup.WELCOME
import uk.co.siland.culvery.core.setup.WELCOME_LINE
import uk.co.siland.culvery.core.ui.HhPillButton
import uk.co.siland.culvery.core.ui.rememberSingleAction

/** 4a design §4.2: what setup covers, and Start. Done once passed, so a start after a kill resumes past it (§6). */
@Singleton
class WelcomeStep @Inject constructor(
    private val state: SetupState,
    private val household: HouseholdRepository,
    private val sample: Optional<SampleHousehold>,
) : SetupStep {
    override val id = "welcome"
    override val order = 0
    override val done: Flow<Boolean> = state.welcomed
    override val canGoOn: Flow<Boolean> = flowOf(true)
    override val nextLabel = START

    override suspend fun onNext(): Boolean {
        state.markWelcomed()
        return true
    }

    @Composable
    override fun Content(onNext: () -> Unit) {
        val nobodyYet by remember { household.members.map { it.isEmpty() } }.collectAsState(initial = false)
        val action = rememberSingleAction(Unit) { e -> Log.w(TAG, "Couldn't make the sample household (${e::class.simpleName})") }
        WelcomeContent(
            onSample = if (sample.isPresent && nobodyYet) ({ action.run { sample.get().create() } }) else null,
            busy = action.busy,
        )
    }

    private companion object {
        const val TAG = "WelcomeStep"
    }
}

/** [onSample] is null except in a debug build with nobody set up yet (4a design D11). */
@Composable
internal fun WelcomeContent(onSample: (() -> Unit)?, busy: Boolean) {
    StepTitle(WELCOME, WELCOME_LINE)
    if (onSample != null) HhPillButton(USE_SAMPLE_HOUSEHOLD, onSample, Modifier.testTag("welcome_sample"), enabled = !busy)
}
