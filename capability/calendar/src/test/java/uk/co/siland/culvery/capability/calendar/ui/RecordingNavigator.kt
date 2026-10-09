package uk.co.siland.culvery.capability.calendar.ui

import uk.co.siland.culvery.core.plugin.ShellNavigator

class RecordingNavigator : ShellNavigator {
    val tabs = mutableListOf<String>()
    val pages = mutableListOf<String?>()
    var settingsOpened = 0

    override fun openTab(id: String) {
        tabs += id
    }

    override fun openSettings(page: String?) {
        pages += page
        settingsOpened++
    }

    var kioskExits = 0

    override fun exitKiosk() {
        kioskExits++
    }

    override fun chooseHomeApp() = Unit

    override fun changeHomeApp() = Unit

    override fun leavePinning() = Unit

    override fun returnToPinning() = Unit
}
