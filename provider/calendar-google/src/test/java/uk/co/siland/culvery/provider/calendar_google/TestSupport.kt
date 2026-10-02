package uk.co.siland.culvery.provider.calendar_google

import android.app.PendingIntent
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertWithMessage
import kotlin.coroutines.cancellation.CancellationException
import org.robolectric.shadows.ShadowLog
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

/** Toasts nobody reads. */
internal object NoToasts : Toaster {
    override fun show(message: String, icon: String) = Unit
}

/** Records every toast's message. */
internal class Toasts : Toaster {
    val messages = mutableListOf<String>()

    override fun show(message: String, icon: String) {
        messages += message
    }
}

/** The provider over [api], with Play services usable unless a test says otherwise. */
internal fun testProvider(
    api: GoogleApi,
    authorizer: Authorizer = FakeAuthorizer(),
    toaster: Toaster = NoToasts,
    playServices: PlayServicesCheck = PlayServicesCheck { true },
) = GoogleCalendarProvider(api, authorizer, toaster, playServices)

/** Stands in for Play services' account chooser and consent screens; needs Robolectric. */
internal fun screens(): PendingIntent =
    PendingIntent.getActivity(ApplicationProvider.getApplicationContext(), 0, Intent(), PendingIntent.FLAG_IMMUTABLE)

/**
 * P8/P11, for a test class's tearDown: nothing this module logged (its tags start "Google"), with its whole chain of
 * causes, holds an email, a token, or Google's own error text (the fake server's is "Google's own words for …"), nor
 * any of [also]. Other tags are the framework's, whose object ids hold "@". A failure-path test passes [minLines], so
 * a check that saw no log at all can't pass by default.
 */
internal fun assertLogsHoldNoPersonalData(vararg also: String, minLines: Int = 0) {
    val logs = ShadowLog.getLogs().filter { it.tag.startsWith("Google") }
    assertWithMessage("lines this module logged").that(logs.size).isAtLeast(minLines)
    logs.forEach { log ->
        val causes = generateSequence(log.throwable) { it.cause }.joinToString(" ")
        val text = "${log.msg} $causes"
        assertWithMessage(text).that(text).doesNotContain("@")
        assertWithMessage(text).that(text).doesNotContain("token-")
        assertWithMessage(text).that(text).doesNotContain("Google's own words")
        also.forEach { assertWithMessage(text).that(text).doesNotContain(it) }
    }
}
