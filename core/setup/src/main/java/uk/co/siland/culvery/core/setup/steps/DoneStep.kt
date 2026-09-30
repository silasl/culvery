package uk.co.siland.culvery.core.setup.steps

import androidx.compose.runtime.Composable
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.CorePermissions
import uk.co.siland.culvery.core.plugin.SetupStep
import uk.co.siland.culvery.core.setup.CULVERY_IS_READY
import uk.co.siland.culvery.core.setup.OPEN_CULVERY
import uk.co.siland.culvery.core.setup.SetupSessionGate
import uk.co.siland.culvery.core.setup.SetupState
import uk.co.siland.culvery.core.setup.StepTitle

/** 4a design §3.3, §4.2: "Culvery is ready". Never done, so a resume that gets this far stops here. */
@Singleton
class DoneStep @Inject constructor(
    private val state: SetupState,
    private val access: AccessControl,
    private val gate: SetupSessionGate,
) : SetupStep {
    override val id = "done"
    override val order = 1000
    override val done: Flow<Boolean> = flowOf(false)
    override val canGoOn: Flow<Boolean> = flowOf(true)
    override val nextLabel = OPEN_CULVERY

    /**
     * An Admin finishes setup (silent in the setup session). Signed out before setup is marked complete, so Home never
     * opens with the setup session (4a design §9); the gate is told first, so the sign-out doesn't bring it back.
     */
    override suspend fun onNext(): Boolean {
        access.authorise(CorePermissions.SETTINGS_MANAGE) ?: return false
        gate.finish()
        try {
            access.endSetupSession()
            access.lock()
            state.markComplete()
        } catch (e: Throwable) {
            gate.finishFailed()
            throw e
        }
        return true
    }

    @Composable
    override fun Content(onNext: () -> Unit) {
        StepTitle(CULVERY_IS_READY)
    }
}
