package uk.co.siland.culvery.core.plugin

import androidx.compose.runtime.staticCompositionLocalOf

/** Lets capability UI move the shell without depending on :app. */
interface ShellNavigator {
    /** Selects a rail tab by capability id; ignored if that tab is not shown. */
    fun openTab(id: String)

    /** Opens Settings, asking for a PIN if needed. */
    fun openSettings()
}

val LocalShellNavigator = staticCompositionLocalOf<ShellNavigator> {
    error("LocalShellNavigator not provided: wrap the content in CompositionLocalProvider(LocalShellNavigator provides …)")
}
