package uk.co.siland.culvery.core.plugin

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow

enum class HomeCardSize { TALL, WIDE, REGULAR }

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
