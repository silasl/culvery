package uk.co.siland.culvery.core.plugin

import androidx.compose.runtime.Composable

/** How much of the tray's width a [TrayButton] takes: two [HALF]s share a row (15a §2.1). */
enum class TrayWidth { HALF, FULL }

/** A One-tap tray row a capability adds to Home (11 §4.4), placed by [order]: the modes 10–13 by place (15b Ruling 11), Hoover 20. */
class TrayButton(val id: String, val order: Int, val width: TrayWidth = TrayWidth.FULL, val content: @Composable () -> Unit)
