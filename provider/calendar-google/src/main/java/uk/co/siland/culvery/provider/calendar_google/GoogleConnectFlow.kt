package uk.co.siland.culvery.provider.calendar_google

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import java.util.UUID
import kotlinx.coroutines.CancellationException
import uk.co.siland.culvery.capability.calendar.CONFIG_ACCOUNT
import uk.co.siland.culvery.capability.calendar.couldNotConnect
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Toaster

/** Stored with every Google connection: never change it. */
const val GOOGLE_PROVIDER_ID = "calendar.google"

internal const val GOOGLE_DISPLAY_NAME = "Google Calendar"

/** The short connection label: the syncing pill and the reconnect chip (3a design D11). */
internal const val GOOGLE_LABEL = "Google"

/** 3a design §3.2: a reconnect must be the same account. */
fun differentAccount(email: String): String = "That's a different Google account. Reconnect with $email."

/** One step of connecting: done, screens the user must see, or stopped (after saying why, if there is a why). */
sealed interface ConnectStep {
    data class Done(val connection: Connection) : ConnectStep

    class ShowScreens(val intent: PendingIntent) : ConnectStep

    data object Stopped : ConnectStep
}

/**
 * Checks Play services can sign in, then the connect and reconnect flow's logic, apart from the screens it launches
 * (3a design §3.2): ask Play services for the calendar scopes (for the stored account on a reconnect); once both
 * are granted, ask Google whose primary calendar this is, which is the account's email, and check Play services
 * grants that account silently, as every later call will ask. A reconnect to a different account is refused. Backing out says nothing; any other failure
 * says "Couldn't connect", and nothing is stored.
 */
internal class GoogleConnectFlow(
    private val authorizer: Authorizer,
    private val api: GoogleApi,
    private val toaster: Toaster,
    private val playServices: PlayServicesCheck,
) {
    suspend fun start(existing: Connection?): ConnectStep {
        if (!playServices.usable()) {
            Log.w(TAG, "Play services can't run Google's sign-in on this tablet; nothing started")
            toaster.show(UPDATE_PLAY_SERVICES)
            return ConnectStep.Stopped
        }
        return step(existing, screensAllowed = true) { authorizer.authorize(existing?.config?.get(CONFIG_ACCOUNT)) }
    }

    suspend fun afterScreens(existing: Connection?, data: Intent?): ConnectStep =
        step(existing, screensAllowed = false) { authorizer.authorizationFrom(data) }

    private suspend fun step(existing: Connection?, screensAllowed: Boolean, ask: suspend () -> Authorization): ConnectStep =
        try {
            when (val answer = ask()) {
                is Authorization.NeedsUser -> if (screensAllowed) {
                    ConnectStep.ShowScreens(answer.intent)
                } else {
                    // The screens were shown and still didn't grant: only a cancel is silent (§3.2).
                    Log.w(TAG, "Play services wants its screens again after they were shown; nothing stored")
                    stopWithToast()
                }
                is Authorization.Granted -> finish(existing, answer)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.isUserCancel()) {
                Log.i(TAG, "Google sign-in was cancelled")
            } else {
                Log.w(TAG, "Couldn't connect to Google (${e::class.simpleName})")
                stopWithToast()
            }
            ConnectStep.Stopped
        }

    private suspend fun finish(existing: Connection?, grant: Authorization.Granted): ConnectStep {
        if (!grant.scopes.containsAll(CALENDAR_SCOPES)) {
            // An unticked box on the consent screen: the tablet could read but not write, or the reverse.
            Log.w(TAG, "Google granted ${grant.scopes.size} of the ${CALENDAR_SCOPES.size} calendar scopes; nothing stored")
            return stopWithToast()
        }
        val email = api.sendWithToken(grant.token, "GET", api.url("calendars", "primary"))
            .readOrUnreachable(GoogleCall.PRIMARY_CALENDAR)
            .decode(CalendarResource.serializer(), GoogleCall.PRIMARY_CALENDAR)
            .id
        val stored = existing?.config?.get(CONFIG_ACCOUNT)
        if (stored != null && !email.equals(stored, ignoreCase = true)) {
            toaster.show(differentAccount(stored))
            return ConnectStep.Stopped
        }
        // Every later call asks for this email silently; an alias Play services doesn't know would need the user forever.
        if (authorizer.authorize(email) !is Authorization.Granted) {
            Log.w(TAG, "Play services won't grant the primary calendar's account silently; nothing stored")
            return stopWithToast()
        }
        val id = existing?.id ?: UUID.randomUUID().toString()
        return ConnectStep.Done(Connection(id, GOOGLE_PROVIDER_ID, GOOGLE_LABEL, mapOf(CONFIG_ACCOUNT to email)))
    }

    private fun stopWithToast(): ConnectStep {
        toaster.show(couldNotConnect(GOOGLE_DISPLAY_NAME))
        return ConnectStep.Stopped
    }
}
