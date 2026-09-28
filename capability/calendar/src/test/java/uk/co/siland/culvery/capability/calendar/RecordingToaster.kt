package uk.co.siland.culvery.capability.calendar

import java.util.concurrent.CopyOnWriteArrayList
import uk.co.siland.culvery.core.plugin.Toaster

class RecordingToaster : Toaster {
    // Written from Room's and the app scope's threads, read on the test's.
    val messages: MutableList<String> = CopyOnWriteArrayList()

    override fun show(message: String, icon: String) {
        messages += message
    }
}
