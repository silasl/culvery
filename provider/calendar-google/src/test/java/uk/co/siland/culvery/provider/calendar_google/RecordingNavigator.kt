package uk.co.siland.culvery.provider.calendar_google

import uk.co.siland.culvery.core.plugin.ShellNavigator

/** Records what the connect screen asked of the shell into [calls], which a test may share with other recorders. */
internal class RecordingNavigator(private val calls: MutableList<String>) : ShellNavigator {
    override fun openTab(id: String) = Unit

    override fun openSettings() = Unit

    override fun exitKiosk() = Unit

    override fun chooseHomeApp() = Unit

    override fun changeHomeApp() = Unit

    override fun leavePinning() {
        calls += "leave pinning"
    }
}
