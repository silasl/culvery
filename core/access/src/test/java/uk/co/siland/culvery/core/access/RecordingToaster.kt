package uk.co.siland.culvery.core.access

import uk.co.siland.culvery.core.plugin.ToastAction
import uk.co.siland.culvery.core.plugin.Toaster

class RecordingToaster : Toaster {
    val messages = mutableListOf<String>()

    /** The messages shown as urgent (errors and refusals). */
    val urgent = mutableListOf<String>()

    override fun show(message: String, icon: String, urgent: Boolean, action: ToastAction?) {
        messages += message
        if (urgent) this.urgent += message
    }
}
