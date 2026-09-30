package uk.co.siland.culvery.core.plugin

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** The wizard's forward button, unless a step names its own. */
const val NEXT_LABEL = "Next"

/** 4a design §5: a setting that didn't save; nothing changed. */
const val COULD_NOT_SAVE = "Couldn't save — try again."

/** The button that closes a confirmation and changes nothing. */
const val KEEP = "Keep"

/**
 * One page of the first-run wizard (4a design §3.2, §3.3). Core steps use orders 0–399, capabilities 400 and up in
 * rail order, Done 1000.
 */
interface SetupStep {
    val id: String
    val order: Int

    /** False hides the step (e.g. Review calendars before a connection exists). */
    val shown: Flow<Boolean> get() = flowOf(true)

    /** True once the step's required input is saved: the wizard resumes at the first shown step that isn't. */
    val done: Flow<Boolean>

    /** Enables the forward button; by default once [done]. */
    val canGoOn: Flow<Boolean> get() = done

    /** A skippable step that isn't [done] offers "Skip for now" in place of the forward button. */
    val skippable: Boolean get() = false

    val nextLabel: String get() = NEXT_LABEL

    /** Runs when the forward button is tapped, before the wizard moves on; false keeps the wizard on this step. */
    suspend fun onNext(): Boolean = true

    /** [onNext] does what the forward button does, for a step with a button of its own. */
    @Composable
    fun Content(onNext: () -> Unit)
}

/** One section of Settings (4a design §3.2, §4.6): Home location 0, People 100, Calendars 400, Kiosk 900. */
interface SettingsPage {
    val id: String

    /** Its name in Settings' left-hand list. */
    val title: String
    val order: Int

    @Composable
    fun Content()
}
