package uk.co.siland.culvery.shell.ui

import uk.co.siland.culvery.core.plugin.ShellNavigator

internal object NoNavigation : ShellNavigator {
    override fun openTab(id: String) = Unit
    override fun openSettings() = Unit
    override fun exitKiosk() = Unit
    override fun chooseHomeApp() = Unit
    override fun changeHomeApp() = Unit
    override fun leavePinning() = Unit
    override fun returnToPinning() = Unit
}
