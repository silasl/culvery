package uk.co.siland.culvery.core.google

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.UnreachableException
import uk.co.siland.culvery.core.plugin.couldNotConnect

/** One step of connecting: done, screens the user must see, or stopped (after saying why, if there is a why). */
sealed interface ConnectStep {
    data class Done(val connection: Connection) : ConnectStep

    class ShowScreens(val intent: PendingIntent) : ConnectStep

    data object Stopped : ConnectStep
}

/**
 * The steps every Google connect flow shares: check Play services, ask it for a grant, show its screens once, and
 * stop with the right words when it fails. The provider keeps what it does with a grant. Backing out says nothing;
 * any other failure says "Couldn't connect" and stores nothing. [logName] is the name in the log, [displayName] the one in the toast.
 */
class GoogleConnectSteps(
    private val authorizer: Authorizer,
    private val toaster: Toaster,
    private val playServices: PlayServicesCheck,
    private val tag: String,
    private val logName: String,
    private val displayName: String,
) {
    /** Asks Play services for [scopes] for [account] ([account] null on a first connect); a grant goes to [finish]. */
    suspend fun start(account: String?, scopes: List<String>, finish: suspend (Authorization.Granted) -> ConnectStep): ConnectStep {
        if (!playServices.usable()) {
            Log.w(tag, "Play services can't run Google's sign-in on this tablet; nothing started")
            toaster.show(UPDATE_PLAY_SERVICES, urgent = true)
            return ConnectStep.Stopped
        }
        return step(screensAllowed = true, finish) { authorizer.authorize(account, scopes) }
    }

    /** Reads what the screens answered; screens asked for a second time read as a stop. */
    suspend fun afterScreens(data: Intent?, finish: suspend (Authorization.Granted) -> ConnectStep): ConnectStep =
        step(screensAllowed = false, finish) { authorizer.authorizationFrom(data) }

    fun stopWithToast(unreachable: Boolean = false): ConnectStep {
        toaster.show(couldNotConnect(displayName, unreachable), urgent = true)
        return ConnectStep.Stopped
    }

    private suspend fun step(
        screensAllowed: Boolean,
        finish: suspend (Authorization.Granted) -> ConnectStep,
        ask: suspend () -> Authorization,
    ): ConnectStep =
        try {
            when (val answer = ask()) {
                is Authorization.NeedsUser -> if (screensAllowed) {
                    ConnectStep.ShowScreens(answer.intent)
                } else {
                    // The screens were shown and still didn't grant: only a cancel is silent.
                    Log.w(tag, "Play services wants its screens again after they were shown; nothing stored")
                    stopWithToast()
                }
                is Authorization.Granted -> finish(answer)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.isUserCancel()) {
                Log.i(tag, "Google sign-in was cancelled")
            } else {
                Log.w(tag, "Couldn't connect to ${this.logName} (${e::class.simpleName})")
                // Play services answering with a status is not the network failing.
                stopWithToast(unreachable = e is UnreachableException && e !is PlayServicesStatusException)
            }
            ConnectStep.Stopped
        }
}

/**
 * Runs the account chooser and Google's consent through Play services and follows the flow; draws nothing. [start]
 * runs when [key] changes; [afterScreens] reads what the screens answered. Backing out of either closes the card
 * with nothing said.
 */
@Composable
fun GoogleConnectHost(
    key: Any?,
    onConnected: (Connection) -> Unit,
    onCancel: () -> Unit,
    start: suspend () -> ConnectStep,
    afterScreens: suspend (Intent?) -> ConnectStep,
) {
    val scope = rememberCoroutineScope()
    val connected by rememberUpdatedState(onConnected)
    val cancelled by rememberUpdatedState(onCancel)
    val starting by rememberUpdatedState(start)
    val finishing by rememberUpdatedState(afterScreens)
    val screens = rememberGoogleScreens(
        onAnswer = { resultCode, data ->
            if (resultCode != Activity.RESULT_OK) {
                cancelled()
            } else {
                scope.launch {
                    when (val step = finishing(data)) {
                        is ConnectStep.Done -> connected(step.connection)
                        else -> cancelled()
                    }
                }
            }
        },
        onNotOpened = { cancelled() },
    )
    LaunchedEffect(key) {
        when (val step = starting()) {
            is ConnectStep.Done -> connected(step.connection)
            is ConnectStep.ShowScreens -> screens.show(step.intent)
            ConnectStep.Stopped -> cancelled()
        }
    }
}
