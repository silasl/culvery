package uk.co.siland.culvery.core.plugin

import androidx.compose.runtime.staticCompositionLocalOf

/** Lets capability UI move the shell without depending on :app. */
interface ShellNavigator {
    /** Selects a rail tab by capability id; ignored if that tab is not shown. */
    fun openTab(id: String)

    /** Opens Settings, asking for a PIN if needed. */
    fun openSettings()

    /** Settings › Kiosk (4a design §4.7): leaves kiosk mode after a fresh Admin PIN. */
    fun exitKiosk()

    /** The wizard's Done step and Settings › Kiosk (4c §5.1): asks Android to make Culvery the home app (the open session). */
    fun chooseHomeApp()

    /** Settings › Kiosk (4c §5.1): opens the home-app setting to go back to another launcher, after a fresh Admin PIN. */
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
}

val LocalShellNavigator = staticCompositionLocalOf<ShellNavigator> {
    error("LocalShellNavigator not provided: wrap the content in CompositionLocalProvider(LocalShellNavigator provides …)")
}
