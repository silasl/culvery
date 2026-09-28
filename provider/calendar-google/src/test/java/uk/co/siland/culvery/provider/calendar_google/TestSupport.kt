package uk.co.siland.culvery.provider.calendar_google

import android.app.PendingIntent
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import kotlin.coroutines.cancellation.CancellationException
import uk.co.siland.culvery.core.plugin.Toaster

/** What [block] threw, or null if it returned; a cancellation is never swallowed. Shared by this module's tests. */
internal suspend fun failureOf(block: suspend () -> Unit): Throwable? =
    try {
        block()
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        e
    }

/** Records every toast's message. */
internal class Toasts : Toaster {
    val messages = mutableListOf<String>()

    override fun show(message: String, icon: String) {
        messages += message
    }
}

/** Stands in for Play services' account chooser and consent screens; needs Robolectric. */
internal fun screens(): PendingIntent =
    PendingIntent.getActivity(ApplicationProvider.getApplicationContext(), 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
