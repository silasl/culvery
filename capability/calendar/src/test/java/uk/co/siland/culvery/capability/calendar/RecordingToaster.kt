package uk.co.siland.culvery.capability.calendar

import uk.co.siland.culvery.core.plugin.Toaster

class RecordingToaster : Toaster {
    val messages = mutableListOf<String>()

    override fun show(message: String, icon: String) {
        messages += message
    }
}
