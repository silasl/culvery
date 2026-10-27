package uk.co.siland.culvery.capability.lights.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import uk.co.siland.culvery.capability.lights.moreRooms
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

/** From seven rooms the rows keep a fixed height and scroll (9 §4.4). */
internal fun scrolls(count: Int): Boolean = count >= 7

/** The rooms after the last row wholly in the viewport; the header is item 0. */
private fun roomsBelow(info: LazyGridLayoutInfo, count: Int): Int {
    val rooms = info.visibleItemsInfo.filter { it.index > 0 }
    val whole = rooms.filter { it.offset.y >= info.viewportStartOffset && it.offset.y + it.size.height <= info.viewportEndOffset }
    val shownUpTo = whole.maxOfOrNull { it.index } ?: ((rooms.minOfOrNull { it.index } ?: 1) - 1)
    return count - shownUpTo
}

private class Kept(var order: List<String>? = null)

/**
 * The tab's rooms in the order they had when it opened (9 §4.4, §6.9). Up to six share the space under [header] in
 * [gridColumns] columns and nothing scrolls; from seven, [header] is the first item of a three-column grid of fixed rows
 * that scrolls under a fade, with a pill counting the rooms below. A [focus] naming a listed room is brought into view
 * once the order is set, then handed back through [onFocused].
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
) {
    val kept = remember { Kept() }
    val order = remember(rooms) { openedOrder(kept.order, rooms) }
    SideEffect { kept.order = order }
    val shown = remember(order, rooms) {
        val byId = rooms.associateBy { it.id }
        order.orEmpty().mapNotNull { byId[it] }
    }
    // Laid out by the kept order, so a bridge that drops and comes back keeps the grid and its place.
    val scrolling = scrolls(order.orEmpty().size)
    val grid = rememberLazyGridState()
    val handBack by rememberUpdatedState(onFocused)
    LaunchedEffect(focus, order, shown.isEmpty()) {
        if (focus == null || shown.isEmpty()) return@LaunchedEffect
        val at = shown.indexOfFirst { it.id == focus }
        if (at >= 0 && scrolling) grid.scrollToItem(at + 1)
        handBack()
    }
    if (!scrolling) {
        Column(modifier.fillMaxSize()) {
            header()
            Box(Modifier.weight(1f)) { SharedRows(shown, lines, actions) }
        }
        return
    }
    val c = Culvery.colors
    val scope = rememberCoroutineScope()
    val below by remember(grid, shown.size) { derivedStateOf { roomsBelow(grid.layoutInfo, shown.size) } }
    Box(modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(GRID_COLUMNS),
            state = grid,
            horizontalArrangement = Arrangement.spacedBy(LightsDimens.gridGap),
            contentPadding = PaddingValues(bottom = ButtonTokens.regularHeight + LightsDimens.pillFoot),
            modifier = Modifier.fillMaxSize().testTag("lights_grid"),
        ) {
            item(key = HEADER_KEY, span = { GridItemSpan(maxLineSpan) }) { header() }
            // The header ends with its own gap, so only the rows after the first take one above.
            itemsIndexed(shown, key = { _, it -> it.id }) { i, it ->
                val above = if (i >= GRID_COLUMNS) LightsDimens.gridGap else 0.dp
                LightsRoomCard(it, lines[it.id], actions, Modifier.padding(top = above).height(LightsDimens.fixedRow))
            }
        }
        if (below > 0) {
            HhBottomFade(c.bg, LightsDimens.gridFade, Modifier.align(Alignment.BottomCenter))
            HhButton(
                moreRooms(below),
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
private const val GRID_COLUMNS = 3

/** Up to six rooms in rows that share the height, the last row's empty places left blank so the columns hold. */
@Composable
private fun SharedRows(rooms: List<LightRoom>, lines: Map<String, String>, actions: RoomActions) {
    val columns = gridColumns(rooms.size)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(LightsDimens.gridGap)) {
        rooms.chunked(columns).forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LightsDimens.gridGap)) {
                row.forEach { key(it.id) { LightsRoomCard(it, lines[it.id], actions, Modifier.weight(1f).fillMaxHeight()) } }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
