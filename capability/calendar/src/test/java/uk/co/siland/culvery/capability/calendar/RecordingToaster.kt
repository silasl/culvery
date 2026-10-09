package uk.co.siland.culvery.capability.calendar

import java.util.concurrent.CopyOnWriteArrayList
import uk.co.siland.culvery.core.plugin.Toaster

class RecordingToaster : Toaster {
    // Written from Room's and the app scope's threads, read on the test's.
    val messages: MutableList<String> = CopyOnWriteArrayList()

    /** The messages shown as urgent (errors and refusals). */
    val urgent: MutableList<String> = CopyOnWriteArrayList()

    override fun show(message: String, icon: String, urgent: Boolean) {
        messages += message
        if (urgent) this.urgent += message
    }
}
