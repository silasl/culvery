package uk.co.siland.culvery.capability.weather.ui

import uk.co.siland.culvery.core.plugin.ShellNavigator

class RecordingNavigator : ShellNavigator {
    val pages = mutableListOf<String?>()
    var settingsOpened = 0

    override fun openTab(id: String) = Unit

    override fun openSettings(page: String?) {
        pages += page
        settingsOpened++
    }

    override fun exitKiosk() = Unit

    override fun chooseHomeApp() = Unit

    override fun changeHomeApp() = Unit

    override fun leavePinning() = Unit

    override fun returnToPinning() = Unit
}
