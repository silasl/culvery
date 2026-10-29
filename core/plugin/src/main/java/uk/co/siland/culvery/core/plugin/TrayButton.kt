package uk.co.siland.culvery.core.plugin

import androidx.compose.runtime.Composable

/** A One-tap tray row a capability adds to Home (11 §4.4), placed by [order]: Holiday mode 10, Party mode 15, Hoover 20. */
class TrayButton(val id: String, val order: Int, val content: @Composable () -> Unit)
