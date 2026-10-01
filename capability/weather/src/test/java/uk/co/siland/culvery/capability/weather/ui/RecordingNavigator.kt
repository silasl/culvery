package uk.co.siland.culvery.capability.weather.ui

import uk.co.siland.culvery.core.plugin.ShellNavigator

class RecordingNavigator : ShellNavigator {
    var settingsOpened = 0

    override fun openTab(id: String) = Unit

    override fun openSettings() {
        settingsOpened++
    }

    override fun exitKiosk() = Unit
}
