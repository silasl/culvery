package uk.co.siland.culvery.capability.calendar

import uk.co.siland.culvery.core.plugin.Toaster

/** For an engine built in a test that doesn't look at toasts. */
internal object SilentToaster : Toaster {
    override fun show(message: String, icon: String) = Unit
}
