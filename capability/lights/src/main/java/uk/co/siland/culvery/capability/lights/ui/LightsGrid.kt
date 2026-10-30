package uk.co.siland.culvery.capability.lights.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uk.co.siland.culvery.capability.lights.LightRoom
import uk.co.siland.culvery.capability.lights.morePill
import uk.co.siland.culvery.core.plugin.LightsTabSection
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTokens
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhBottomFade
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.gridColumns

/**
 * The order the cards keep while the tab is open (9 §6.9): taken from the first non-empty list, on rooms first, each
 * group in Hue's order; after that a room that goes is dropped and a new one goes at the end. An empty list (before the
 * first read, or the bridge dropping) keeps [previous].
 */
internal fun openedOrder(previous: List<String>?, rooms: List<LightRoom>): List<String>? {
    if (rooms.isEmpty()) return previous
    val ids = rooms.map { it.id }
    if (previous == null) return rooms.sortedBy { !it.on }.map { it.id }
    val listed = ids.toSet()
    val kept = previous.toSet()
    return previous.filter { it in listed } + ids.filter { it !in kept }
}

/** The rooms after the last row wholly in the viewport; the header is item 0, the rooms 1…[count], the sections after. */
private fun roomsBelow(info: LazyGridLayoutInfo, count: Int): Int {
    if (count == 0) return 0
    val visible = info.visibleItemsInfo
    val rooms = visible.filter { it.index in 1..count }
    if (rooms.isEmpty()) return if ((visible.minOfOrNull { it.index } ?: 0) > count) 0 else count
    val whole = rooms.filter { it.offset.y >= info.viewportStartOffset && it.offset.y + it.size.height <= info.viewportEndOffset }
    val shownUpTo = whole.maxOfOrNull { it.index } ?: (rooms.minOf { it.index } - 1)
    return count - shownUpTo
}

private class Kept(var order: List<String>? = null)

/**
 * The tab's rooms in the order they had when it opened (9 §4.4, §6.9), then the [sections], in one grid that scrolls
 * (15a §4.5): [header] is the first item, the rooms follow in [gridColumns] columns and wrap their content, each section
 * takes a full row. While there is more below, a fade and a pill count the rooms still to come. A [focus] naming a
 * listed room is brought into view once the order is set, then handed back through [onFocused].
 */
@Composable
internal fun LightsGrid(
    rooms: List<LightRoom>,
    lines: Map<String, String>,
    actions: RoomActions,
    focus: String?,
    onFocused: () -> Unit,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    sections: List<LightsTabSection> = emptyList(),
) {
    val kept = remember { Kept() }
    val order = remember(rooms) { openedOrder(kept.order, rooms) }
    SideEffect { kept.order = order }
    val shown = remember(order, rooms) {
        val byId = rooms.associateBy { it.id }
        order.orEmpty().mapNotNull { byId[it] }
    }
    // Laid out by the kept order, so a bridge that drops and comes back keeps the grid and its place.
    val columns = gridColumns(order.orEmpty().size)
    val grid = rememberLazyGridState()
    val handBack by rememberUpdatedState(onFocused)
    LaunchedEffect(focus, order, shown.isEmpty()) {
        if (focus == null || shown.isEmpty()) return@LaunchedEffect
        val at = shown.indexOfFirst { it.id == focus }
        if (at >= 0) grid.scrollToItem(at + 1)
        handBack()
    }
    val c = Culvery.colors
    val scope = rememberCoroutineScope()
    val below by remember(grid, shown.size) { derivedStateOf { roomsBelow(grid.layoutInfo, shown.size) } }
    Box(modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = grid,
            horizontalArrangement = Arrangement.spacedBy(LightsDimens.gridGap),
            contentPadding = PaddingValues(bottom = ButtonTokens.regularHeight + LightsDimens.pillFoot),
            modifier = Modifier.fillMaxSize().testTag("lights_grid"),
        ) {
            item(key = HEADER_KEY, span = { GridItemSpan(maxLineSpan) }) { header() }
            // The header ends with its own gap, so the first row of cards takes none above.
            itemsIndexed(shown, key = { _, it -> it.id }) { i, it ->
                val above = if (i >= columns) LightsDimens.gridGap else 0.dp
                LightsRoomCard(it, lines[it.id], actions, Modifier.padding(top = above))
            }
            items(sections, key = { it.javaClass.name }, span = { GridItemSpan(maxLineSpan) }) {
                val above = if (shown.isEmpty()) 0.dp else LightsDimens.gridGap
                it.Content(Modifier.padding(top = above).testTag("lights_section"))
            }
        }
        if (grid.canScrollForward) {
            HhBottomFade(c.bg, LightsDimens.gridFade, Modifier.align(Alignment.BottomCenter))
            HhButton(
                morePill(below),
                { scope.launch { grid.animateScrollBy(grid.layoutInfo.viewportSize.height.toFloat()) } },
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = LightsDimens.pillFoot)
                    .shadow(LightsDimens.pillShadow, RoundedCornerShape(percent = 50))
                    .testTag("lights_more_rooms"),
                icon = Icons.KEYBOARD_ARROW_DOWN,
                on = ButtonSurface.Bg,
            )
        }
    }
}

private const val HEADER_KEY = "header"
