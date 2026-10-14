package uk.co.siland.culvery.core.plugin

import android.content.Intent
import androidx.compose.runtime.staticCompositionLocalOf

/** Lets capability UI move the shell without depending on :app. */
interface ShellNavigator {
    /** Selects a rail tab by capability id; ignored if that tab is not shown. */
    fun openTab(id: String)

    /** Opens Settings, asking for a PIN if needed, on [page] (one of [SettingsPageIds]) or the first page. */
    fun openSettings(page: String? = null)

    /** Settings › Tablet lock (4a design §4.7): Use other apps, after a fresh Admin PIN. */
    fun exitKiosk()

    /** The wizard's Done step and Settings › Tablet lock (4c §5.1): asks Android to make Culvery the home app (the open session). */
    fun chooseHomeApp()

    /** Settings › Tablet lock (4c §5.1): opens the home-app setting to go back to another launcher, after a fresh Admin PIN. */
    fun changeHomeApp()

    /**
     * Lets a system screen such as Google's account chooser open over the kiosk (4c design §5.3, D5): unpins at once,
     * or as device owner allows Play services in lock-task. Culvery pins and restricts again when it is next in front,
     * or when [returnToPinning] says the screen never opened. Unpinning does nothing when not pinned (the wizard, debug
     * builds).
     */
    fun leavePinning()

    /** The system screen ended or never opened: restores what [leavePinning] gave up, if Culvery is already in front. */
    fun returnToPinning()

    /**
     * Opens the first of [intents] the tablet has, one of Android's own screens such as notification access (5 §3.4),
     * after a fresh Admin PIN: Culvery unpins while it shows and pins again when back in front; when none opens, it pins
     * again at once and says [missing].
     */
    fun openSystemScreen(intents: List<Intent>, missing: String)
}

/** Settings' page ids, so a capability can open a page without depending on `:core:setup` (4d-2 §7.6). */
object SettingsPageIds {
    const val LOCATION = "location"
    const val PEOPLE = "people"
    const val CALENDARS = "calendars"

    /** Tablet lock's; stored nowhere, so the old name costs nothing. */
    const val TABLET_LOCK = "kiosk"
}

val LocalShellNavigator = staticCompositionLocalOf<ShellNavigator> {
    error("LocalShellNavigator not provided: wrap the content in CompositionLocalProvider(LocalShellNavigator provides …)")
}
