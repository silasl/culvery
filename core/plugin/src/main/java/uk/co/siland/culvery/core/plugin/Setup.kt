package uk.co.siland.culvery.core.plugin

import androidx.compose.runtime.Composable
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** The wizard's forward button, unless a step names its own. */
const val NEXT_LABEL = "Next"

/** 4a design §5, 4d-2 §7.2: a setting that didn't save; nothing changed. */
const val COULD_NOT_SAVE = "Couldn’t save that change — try again."

/** A device on the home network can't be reached because the tablet has no network. */
const val TABLET_OFFLINE = "The tablet is offline. Check the Wi-Fi."

/** A typed address's button while the address is checked. */
const val CHECKING = "Checking…"

/** A typed address that isn't four numbers on the home network; [example] is the field's placeholder. */
fun typeFourNumbers(example: String): String = "Type the address as four numbers, like $example."

/** A service that couldn’t be reached says check the Wi-Fi; any other failure, try again. */
fun couldNotConnect(service: String, unreachable: Boolean): String =
    if (unreachable) "Couldn’t reach $service — check the tablet’s Wi-Fi, then try again." else "Couldn’t connect to $service — try again."

/** Runs [write]; a setting that didn't save is [log]ged and says [COULD_NOT_SAVE], and nothing changed. */
suspend fun Toaster.save(write: suspend () -> Unit, log: (IOException) -> Unit) {
    try {
        write()
    } catch (e: IOException) {
        log(e)
        show(COULD_NOT_SAVE, urgent = true)
    }
}

/** A settings page's writer: each write runs in this scope through [Toaster.save]. */
fun CoroutineScope.saving(toaster: Toaster, log: (IOException) -> Unit): (suspend () -> Unit) -> Unit =
    { write -> launch { toaster.save(write, log) } }

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

    /** True once the step's required input is saved: a restart resumes at the first shown step neither done nor passed. */
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

/** One section of Settings (4a design §3.2, §4.6): Home location 0, People 100, Calendars 400, Display 440, Music 450, Lights 470, Door 500, Tablet lock 900. */
interface SettingsPage {
    val id: String

    /** Its name in Settings' left-hand list. */
    val title: String
    val order: Int

    @Composable
    fun Content()
}
