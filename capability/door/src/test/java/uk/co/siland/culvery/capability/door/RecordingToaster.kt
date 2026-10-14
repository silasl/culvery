package uk.co.siland.culvery.capability.door

import uk.co.siland.culvery.core.plugin.Toaster

internal class RecordingToaster : Toaster {
    val messages = mutableListOf<String>()
    override fun show(message: String, icon: String, urgent: Boolean) {
        messages += message
    }
}
