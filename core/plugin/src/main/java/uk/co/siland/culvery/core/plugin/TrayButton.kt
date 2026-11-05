package uk.co.siland.culvery.core.plugin

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** How much of the tray's width a [TrayButton] takes: two [HALF]s share the first row, three [THIRD]s the last (18 §4.1). */
enum class TrayWidth { HALF, THIRD, FULL }

/**
 * A One-tap tray row a capability adds to Home (11 §4.4), placed by [order]: the modes 10–11 by place (15b Ruling 11), Hoover 20,
 * the sound pads 81–83 and Taekwondo 90 (18 Ruling 3).
 * [line] is what didn't happen, drawn under the button's whole row and tagged [lineTag].
 */
class TrayButton(
    val id: String,
    val order: Int,
    val width: TrayWidth = TrayWidth.FULL,
    val line: Flow<String?> = flowOf(null),
    val lineTag: String = "${id}_line",
    val content: @Composable () -> Unit,
)
