package uk.co.siland.culvery.core.plugin

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow

/**
 * TALL is Home's left column; REGULAR cards share the right column, two at most; WIDE fills the right column only when no
 * REGULAR card shows (9 §6.1). UTILITY takes the top of the right column, one at most, with the REGULAR cards in a row
 * beneath it; while one shows, WIDE never does (10 §4.9). Tray buttons (11 §4.4) share that top row with it, or stand at
 * its left without one, and WIDE gives way to them too.
 */
enum class HomeCardSize { TALL, WIDE, REGULAR, UTILITY }

/** A card a capability contributes to Home. Higher [priority] is placed first; cards that don't fit are dropped. */
class HomeCard(
    val id: String,
    val size: HomeCardSize,
    val priority: Int,
    val content: @Composable () -> Unit,
)

interface HomeCardContributor {
    fun cards(): Flow<List<HomeCard>>
}
