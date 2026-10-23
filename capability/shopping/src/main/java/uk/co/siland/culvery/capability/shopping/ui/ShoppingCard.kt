package uk.co.siland.culvery.capability.shopping.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import uk.co.siland.culvery.capability.shopping.SHOPPING_ID
import uk.co.siland.culvery.capability.shopping.ShoppingActions
import uk.co.siland.culvery.capability.shopping.ShoppingChecks
import uk.co.siland.culvery.capability.shopping.ShoppingFocus
import uk.co.siland.culvery.capability.shopping.ShoppingState
import uk.co.siland.culvery.capability.shopping.ShoppingView
import uk.co.siland.culvery.capability.shopping.ShownItem
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhCard
import uk.co.siland.culvery.core.ui.HhRoundButton
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.ShellTokens
import uk.co.siland.culvery.core.ui.hhClickable
import uk.co.siland.culvery.core.ui.hhToggleable
import uk.co.siland.culvery.core.ui.plural

private const val EMPTY = "Nothing on the list"
private const val OPEN_LABEL = "open the list"
private const val SECOND_COLUMN = 1

/** How many items show and how many are left out; when some are, the last slot (the foot of the second column) is the footer's. */
internal fun cardSlots(count: Int, rows: Int): Pair<Int, Int> {
    val slots = rows.coerceAtLeast(0) * 2
    if (count <= slots) return count to 0
    val shown = (slots - 1).coerceAtLeast(0)
    return shown to count - shown
}

/**
 * The Shopping card on Home (10 §4.9): the header opens the tab and the + opens it ready to type; the items run down two
 * columns and across, a tap on a row ticks it, and what doesn't fit is counted in the footer. A problem's line takes the
 * last row.
 */
@Composable
internal fun ShoppingCard(state: ShoppingState, onTick: (ShownItem) -> Unit, onOpen: () -> Unit, onAdd: () -> Unit) {
    HhCard(
        modifier = Modifier.fillMaxSize().testTag("shopping_card"),
        radius = ShellTokens.homeCardRadius,
        padding = PaddingValues(horizontal = ShoppingDimens.cardPaddingH, vertical = ShoppingDimens.cardPaddingV),
    ) {
        CardHeader(state.open, onOpen, onAdd)
        Spacer(Modifier.height(ShoppingDimens.cardHeaderGap))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val rows = (maxHeight / ShoppingDimens.cardRowHeight).toInt()
            CardBody(state, rows, onTick, onOpen)
        }
    }
}

@Composable
private fun CardHeader(open: Int, onOpen: () -> Unit, onAdd: () -> Unit) {
    val c = Culvery.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ShoppingDimens.cardTitleGap),
            modifier = Modifier.weight(1f).hhClickable(onOpen, label = OPEN_LABEL).testTag("shopping_card_title"),
        ) {
            Text(
                SHOPPING, style = HhType.cardTitle.copy(fontSize = ShoppingDimens.cardTitle), color = c.ink,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                plural(open.toLong(), "item"), style = HhType.secondary.copy(fontSize = ShoppingDimens.cardCount), color = c.mute,
                modifier = Modifier.testTag("shopping_card_count"),
            )
        }
        HhRoundButton(Icons.ADD, ADD_LABEL, onAdd, Modifier.testTag("shopping_card_add"), on = ButtonSurface.Surf)
    }
}

@Composable
private fun CardBody(state: ShoppingState, rows: Int, onTick: (ShownItem) -> Unit, onOpen: () -> Unit) {
    val c = Culvery.colors
    val problem = state.problem
    val gridRows = rows - if (problem != null) 1 else 0
    val (shown, more) = cardSlots(state.items.size, gridRows)
    Column {
        if (state.items.isEmpty()) {
            CardLine(Modifier.testTag("shopping_card_empty")) { Text(EMPTY, style = HhType.body, color = c.mute) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(ShoppingDimens.cardColumnGap)) {
                repeat(2) { column ->
                    Column(Modifier.weight(1f)) {
                        val from = minOf(column * gridRows, shown)
                        val to = minOf((column + 1) * gridRows, shown)
                        state.items.subList(from, to).forEach { item -> CardItem(item, onTick = { onTick(item) }) }
                        if (more > 0 && column == SECOND_COLUMN) {
                            CardLine(Modifier.hhClickable(onOpen).testTag("shopping_card_more")) {
                                Text(moreLine(more), style = footStyle(), color = c.mute, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        if (problem != null) {
            CardLine(Modifier.testTag("shopping_card_status")) {
                Text(problem, style = footStyle(), color = c.mute, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun footStyle() = HhType.secondary.copy(fontSize = ShoppingDimens.cardFoot)

private fun moreLine(more: Int) = "+$more more · Open $SHOPPING"

/** One row-high slot. */
@Composable
private fun CardLine(modifier: Modifier, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(ShoppingDimens.cardRowHeight).then(modifier), verticalAlignment = Alignment.CenterVertically) { content() }
}

@Composable
private fun CardItem(item: ShownItem, onTick: () -> Unit) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(ShoppingDimens.cardRowHeight)
            .testTag("shopping_card_item_${item.key}")
            .hhToggleable(item.ticked, role = Role.Checkbox) { onTick() }
            .semantics { stateDescription = if (item.ticked) TICKED else NOT_TICKED },
    ) {
        Box(
            Modifier
                .size(ShoppingDimens.circle)
                .clip(CircleShape)
                .then(if (item.ticked) Modifier.background(c.accent) else Modifier.border(ShoppingDimens.circleRing, c.surf3, CircleShape)),
        )
        Spacer(Modifier.width(ShoppingDimens.cardCircleGap))
        Text(
            item.text,
            style = HhType.body.copy(fontSize = ShoppingDimens.cardName),
            color = if (item.ticked) c.mute else c.ink,
            textDecoration = if (item.ticked) TextDecoration.LineThrough else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** The card over the view; while it shows the list is watched (10 §4.4). The + asks the tab for the box, then opens it. */
@Composable
internal fun ShoppingCardHost(view: ShoppingView, actions: ShoppingActions, checks: ShoppingChecks, focus: ShoppingFocus) {
    DisposableEffect(checks) {
        val stop = checks.watch(checkNow = true)
        onDispose(stop)
    }
    val navigator = LocalShellNavigator.current
    val scope = rememberCoroutineScope()
    val state by view.state.collectAsStateWithLifecycle()
    val shown = state ?: return
    ShoppingCard(
        state = shown,
        onTick = { row -> scope.launch { actions.tick(row) } },
        onOpen = { navigator.openTab(SHOPPING_ID) },
        onAdd = {
            focus.askForAdd()
            navigator.openTab(SHOPPING_ID)
        },
    )
}
