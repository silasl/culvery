package uk.co.siland.culvery.core.plugin.test

import android.content.Intent
import uk.co.siland.culvery.core.plugin.ShellNavigator

/** A [ShellNavigator] that records what was asked of the shell; [calls] may be shared with a test's other recorders. */
class RecordingNavigator(val calls: MutableList<String> = mutableListOf()) : ShellNavigator {
    val tabs = mutableListOf<String>()
    val pages = mutableListOf<String?>()
    var settingsOpened = 0
    var kioskExits = 0
    var homeAppChoices = 0
    var homeAppChanges = 0
    val systemScreens = mutableListOf<Pair<List<String?>, String>>()

    override fun openTab(id: String) {
        tabs += id
    }

    override fun openSettings(page: String?) {
        pages += page
        settingsOpened++
    }

    override fun exitKiosk() {
        kioskExits++
    }

    override fun chooseHomeApp() {
        homeAppChoices++
    }

    override fun changeHomeApp() {
        homeAppChanges++
    }

    override fun leavePinning() {
        calls += "leave pinning"
    }

    override fun returnToPinning() {
        calls += "return to pinning"
    }

    override fun openSystemScreen(intents: List<Intent>, missing: String) {
        systemScreens += intents.map { it.action } to missing
    }
}
