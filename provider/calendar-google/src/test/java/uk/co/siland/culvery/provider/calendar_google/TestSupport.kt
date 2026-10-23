package uk.co.siland.culvery.provider.calendar_google

import uk.co.siland.culvery.capability.calendar.StoredSeries
import uk.co.siland.culvery.core.google.Authorization
import uk.co.siland.culvery.core.google.Authorizer
import uk.co.siland.culvery.core.google.PlayServicesCheck
import uk.co.siland.culvery.core.google.test.FakeAuthorizer
import uk.co.siland.culvery.core.google.test.granted
import uk.co.siland.culvery.core.plugin.ToastAction
import uk.co.siland.culvery.core.plugin.Toaster

/** A grant of both calendar scopes. */
internal fun granted(token: String): Authorization = granted(token, CALENDAR_SCOPES)

/** Toasts nobody reads. */
internal object NoToasts : Toaster {
    override fun show(message: String, icon: String, urgent: Boolean, action: ToastAction?) = Unit
}

/** Records every toast's message. */
internal class Toasts : Toaster {
    val messages = mutableListOf<String>()

    /** The messages shown as urgent (errors and refusals). */
    val urgent = mutableListOf<String>()

    override fun show(message: String, icon: String, urgent: Boolean, action: ToastAction?) {
        messages += message
        if (urgent) this.urgent += message
    }
}

/** The provider over [api], with Play services usable unless a test says otherwise. */
internal fun testProvider(
    api: GoogleApi,
    authorizer: Authorizer = FakeAuthorizer(),
    toaster: Toaster = NoToasts,
    playServices: PlayServicesCheck = PlayServicesCheck { true },
    stored: StoredSeries = StoredSeries { _, _ -> emptyMap() },
) = GoogleCalendarProvider(api, authorizer, toaster, playServices, stored)
