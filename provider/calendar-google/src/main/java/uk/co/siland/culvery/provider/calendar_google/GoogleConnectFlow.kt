package uk.co.siland.culvery.provider.calendar_google

import android.content.Intent
import android.util.Log
import java.util.UUID
import uk.co.siland.culvery.capability.calendar.CONFIG_ACCOUNT
import uk.co.siland.culvery.core.google.Authorization
import uk.co.siland.culvery.core.google.Authorizer
import uk.co.siland.culvery.core.google.ConnectStep
import uk.co.siland.culvery.core.google.GoogleConnectSteps
import uk.co.siland.culvery.core.google.PlayServicesCheck
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Toaster

/** Stored with every Google connection: never change it. */
const val GOOGLE_PROVIDER_ID = "calendar.google"

internal const val GOOGLE_DISPLAY_NAME = "Google Calendar"

/** The short connection label: the syncing pill and the reconnect chip (3a design D11). */
internal const val GOOGLE_LABEL = "Google"

/** 3a design §3.2: a reconnect must be the same account. */
fun differentAccount(email: String): String = "That’s a different Google account. Reconnect with $email."

/**
 * Checks Play services can sign in, then the connect and reconnect flow's logic, apart from the screens it launches
 * (3a design §3.2): ask Play services for the calendar scopes (for the stored account on a reconnect); once both are
 * granted, ask Google whose primary calendar this is, which is the account's email, and check Play services grants
 * that account silently, as every later call will ask. A reconnect to a different account is refused. Backing out says
 * nothing; any other failure says "Couldn't connect", and nothing is stored.
 */
internal class GoogleConnectFlow(
    private val authorizer: Authorizer,
    private val api: GoogleApi,
    private val toaster: Toaster,
    playServices: PlayServicesCheck,
) {
    private val steps = GoogleConnectSteps(authorizer, toaster, playServices, TAG, logName = GOOGLE_LABEL, displayName = GOOGLE_DISPLAY_NAME)

    suspend fun start(existing: Connection?): ConnectStep =
        steps.start(existing?.config?.get(CONFIG_ACCOUNT), CALENDAR_SCOPES) { finish(existing, it) }

    suspend fun afterScreens(existing: Connection?, data: Intent?): ConnectStep =
        steps.afterScreens(data) { finish(existing, it) }

    private suspend fun finish(existing: Connection?, grant: Authorization.Granted): ConnectStep {
        if (!grant.scopes.containsAll(CALENDAR_SCOPES)) {
            // An unticked box on the consent screen: the tablet could read but not write, or the reverse.
            Log.w(TAG, "Google granted ${grant.scopes.size} of the ${CALENDAR_SCOPES.size} calendar scopes; nothing stored")
            return steps.stopWithToast()
        }
        val email = api.sendWithToken(grant.token, "GET", api.url("calendars", "primary"))
            .readOrUnreachable(GoogleCall.PRIMARY_CALENDAR)
            .decode(CalendarResource.serializer(), GoogleCall.PRIMARY_CALENDAR)
            .id
        val stored = existing?.config?.get(CONFIG_ACCOUNT)
        if (stored != null && !email.equals(stored, ignoreCase = true)) {
            toaster.show(differentAccount(stored), urgent = true)
            return ConnectStep.Stopped
        }
        // Every later call asks for this email silently; an alias Play services doesn't know would need the user forever.
        if (authorizer.authorize(email, CALENDAR_SCOPES) !is Authorization.Granted) {
            Log.w(TAG, "Play services won't grant the primary calendar's account silently; nothing stored")
            return steps.stopWithToast()
        }
        val id = existing?.id ?: UUID.randomUUID().toString()
        return ConnectStep.Done(Connection(id, GOOGLE_PROVIDER_ID, GOOGLE_LABEL, mapOf(CONFIG_ACCOUNT to email)))
    }
}
