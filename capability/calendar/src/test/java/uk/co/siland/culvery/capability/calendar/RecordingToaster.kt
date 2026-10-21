package uk.co.siland.culvery.capability.calendar

import java.util.concurrent.CopyOnWriteArrayList
import uk.co.siland.culvery.core.plugin.ToastAction
import uk.co.siland.culvery.core.plugin.Toaster

class RecordingToaster : Toaster {
    // Written from Room's and the app scope's threads, read on the test's.
    val messages: MutableList<String> = CopyOnWriteArrayList()

    /** The messages shown as urgent (errors and refusals). */
    val urgent: MutableList<String> = CopyOnWriteArrayList()

    /** The action of each toast shown with one, in order; a plain toast adds nothing. */
    val actions: MutableList<ToastAction> = CopyOnWriteArrayList()

    override fun show(message: String, icon: String, urgent: Boolean, action: ToastAction?) {
        messages += message
        if (urgent) this.urgent += message
        if (action != null) actions += action
    }
}
