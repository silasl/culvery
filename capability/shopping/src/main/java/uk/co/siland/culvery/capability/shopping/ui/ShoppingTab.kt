package uk.co.siland.culvery.capability.shopping.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import uk.co.siland.culvery.capability.shopping.ADD_MAX_CHARS
import uk.co.siland.culvery.capability.shopping.AddResult
import uk.co.siland.culvery.capability.shopping.Learned
import uk.co.siland.culvery.capability.shopping.ShoppingActions
import uk.co.siland.culvery.capability.shopping.ShoppingChecks
import uk.co.siland.culvery.capability.shopping.ShoppingFocus
import uk.co.siland.culvery.capability.shopping.ShoppingLearning
import uk.co.siland.culvery.capability.shopping.ShoppingState
import uk.co.siland.culvery.capability.shopping.ShoppingView
import uk.co.siland.culvery.capability.shopping.ShownItem
import uk.co.siland.culvery.capability.shopping.favourites
import uk.co.siland.culvery.capability.shopping.itemsLine
import uk.co.siland.culvery.capability.shopping.suggest
import uk.co.siland.culvery.core.plugin.textKey
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhBottomFade
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhChoiceChip
import uk.co.siland.culvery.core.ui.HhMessageLine
import uk.co.siland.culvery.core.ui.HhPageHeader
import uk.co.siland.culvery.core.ui.HhTextField
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.PageHeaderTokens
import uk.co.siland.culvery.core.ui.hhToggleable

internal const val SHOPPING = "Shopping"
private const val ADD = "Add"
internal const val ADD_LABEL = "Add to the shopping list"
private const val EMPTY_LIST = "Nothing on the list. Add something above, or tap a favourite."
private const val FAVOURITES_HINT = "Favourites appear as you add things."
internal const val TICKED = "ticked"
internal const val NOT_TICKED = "not ticked"

/**
 * The Shopping tab (10 §4.8, layout B revised): the header, the add card (the box with Add, then one line of suggestions
 * and favourites), a problem line when the list is unwell, and the list in three columns filled down then across.
 * Nothing on it leaves the tab. [focusAdd] puts the cursor in the box with the keyboard up, once.
 */
@Composable
internal fun ShoppingTab(
    state: ShoppingState,
    typed: String,
    onTyped: (String) -> Unit,
    onAdd: () -> Unit,
    suggestions: List<Learned>,
    favourites: List<Learned>,
    onChip: (Learned) -> Unit,
    onForget: (Learned) -> Unit,
    onTick: (ShownItem) -> Unit,
    focusAdd: Boolean,
    nowMillis: Long,
    nothingLearned: Boolean = false,
) {
    val c = Culvery.colors
    val box = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(focusAdd) {
        if (focusAdd) {
            box.requestFocus()
            keyboard?.show()
        }
    }
    Column(Modifier.fillMaxSize().testTag("shopping_tab")) {
        HhPageHeader(
            title = SHOPPING,
            subtitle = null,
            titleModifier = Modifier.testTag("shopping_title"),
            under = {
                Text(
                    itemsLine(state.open, state.service, state.lastReadMillis, nowMillis),
                    style = PageHeaderTokens.subtitleStyle,
                    color = c.mute,
                    modifier = Modifier.testTag("shopping_line"),
                )
            },
        )
        AddCard(typed, onTyped, onAdd, suggestions, favourites, nothingLearned, onChip, onForget, box)
        HhMessageLine(state.problem, HhType.body, c.danger, "shopping_status", gapAbove = ShoppingDimens.cardGap)
        ListCard(state, onTick, Modifier.weight(1f).padding(top = ShoppingDimens.cardGap))
    }
}

@Composable
private fun AddCard(
    typed: String,
    onTyped: (String) -> Unit,
    onAdd: () -> Unit,
    suggestions: List<Learned>,
    favourites: List<Learned>,
    nothingLearned: Boolean,
    onChip: (Learned) -> Unit,
    onForget: (Learned) -> Unit,
    box: FocusRequester,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(ShoppingDimens.addCardGap),
        modifier = Modifier.fillMaxWidth().clip(card()).background(Culvery.colors.surf).padding(ShoppingDimens.cardPaddingH, ShoppingDimens.cardPaddingV),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ShoppingDimens.addGap)) {
            Box(Modifier.weight(1f)) {
                HhTextField(
                    value = typed,
                    onValueChange = { onTyped(it.take(ADD_MAX_CHARS)) },
                    placeholder = ADD_LABEL,
                    label = ADD_LABEL,
                    tag = "shopping_field",
                    modifier = Modifier.focusRequester(box),
                    onDone = onAdd,
                    clearsFocusOnDone = false,
                    fill = Culvery.colors.surf2,
                )
            }
            HhButton(
                ADD,
                onClick = {
                    onAdd()
                    box.requestFocus()
                },
                tone = ButtonTone.Primary,
                on = ButtonSurface.Surf,
                modifier = Modifier.testTag("shopping_add"),
            )
        }
        ChipRow(typed, suggestions, favourites, nothingLearned, onChip, onForget)
    }
}

@Composable
private fun ChipRow(
    typed: String,
    suggestions: List<Learned>,
    favourites: List<Learned>,
    nothingLearned: Boolean,
    onChip: (Learned) -> Unit,
    onForget: (Learned) -> Unit,
) {
    val c = Culvery.colors
    Box(Modifier.fillMaxWidth().heightIn(min = ShoppingDimens.chipRowHeight), contentAlignment = Alignment.CenterStart) {
        if (typed.isEmpty() && nothingLearned) {
            Text(FAVOURITES_HINT, style = HhType.body, color = c.mute, modifier = Modifier.testTag("shopping_favourites_hint"))
            return@Box
        }
        FitRow(ShoppingDimens.chipGap, Modifier.fillMaxWidth()) {
            suggestions.forEach { learned ->
                key("suggest_${learned.key}") {
                    HhChoiceChip(
                        learned.text, selected = false, tag = "shopping_suggest_${learned.key}", onClick = { onChip(learned) },
                        unselectedFill = c.accentSoft, unselectedInk = c.accent, role = Role.Button, maxLines = 1,
                    )
                }
            }
            favourites.forEachIndexed { i, learned ->
                key("favourite_${learned.key}") {
                    // The rule and the first favourite are one piece, so a rule is never left at the line's end.
                    val ruled = i == 0 && suggestions.isNotEmpty()
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = if (ruled) Modifier.pullLeft(ShoppingDimens.chipGap - ShoppingDimens.ruleGap) else Modifier) {
                        if (ruled) {
                            Box(Modifier.size(ShoppingDimens.ruleWidth, ShoppingDimens.ruleHeight).background(c.surf3).testTag("shopping_chip_rule"))
                            Spacer(Modifier.width(ShoppingDimens.ruleGap))
                        }
                        HhChoiceChip(
                            learned.text, selected = false, tag = "shopping_favourite_${learned.key}", onClick = { onChip(learned) },
                            unselectedFill = c.surf2, unselectedInk = c.ink, role = Role.Button, maxLines = 1,
                            onLongClick = { onForget(learned) }, longClickLabel = "Forget ${learned.text}",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ListCard(state: ShoppingState, onTick: (ShownItem) -> Unit, modifier: Modifier) {
    val c = Culvery.colors
    val scroll = rememberScrollState()
    Box(modifier.fillMaxWidth().clip(card()).background(c.surf)) {
        if (state.items.isEmpty()) {
            Text(
                EMPTY_LIST, style = HhType.body, color = c.mute,
                modifier = Modifier.padding(ShoppingDimens.cardPaddingH, ShoppingDimens.cardPaddingV).testTag("shopping_empty"),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(ShoppingDimens.columnGap),
            modifier = Modifier
                .fillMaxSize()
                .testTag("shopping_list")
                .verticalScroll(scroll)
                .padding(ShoppingDimens.cardPaddingH, ShoppingDimens.cardPaddingV),
        ) {
            // Filled down each column, then across: ceil(n / 3) rows a column.
            val perColumn = (state.items.size + ShoppingDimens.COLUMNS - 1) / ShoppingDimens.COLUMNS
            val columns = if (perColumn == 0) emptyList() else state.items.chunked(perColumn)
            repeat(ShoppingDimens.COLUMNS) { i ->
                Column(Modifier.weight(1f)) {
                    columns.getOrNull(i)?.forEach { item ->
                        key(item.key) { ItemRow(item, flashing = item.key == state.flashing, onTick = { onTick(item) }) }
                    }
                }
            }
        }
        if (scroll.canScrollForward) {
            HhBottomFade(c.surf, ShoppingDimens.listFade, Modifier.align(Alignment.BottomCenter).testTag("shopping_list_fade"))
        }
    }
}

@Composable
private fun ItemRow(item: ShownItem, flashing: Boolean, onTick: () -> Unit) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ShoppingDimens.circleGap),
        modifier = Modifier
            .fillMaxWidth()
            .height(ShoppingDimens.itemHeight)
            .clip(RoundedCornerShape(ShoppingDimens.itemRadius))
            .background(if (flashing) c.accentSoft else Color.Transparent)
            .testTag("shopping_item_${item.key}")
            .hhToggleable(item.ticked, role = Role.Checkbox) { onTick() }
            .semantics { stateDescription = if (item.ticked) TICKED else NOT_TICKED },
    ) {
        Box(
            Modifier
                .testTag("shopping_circle_${item.key}")
                .size(ShoppingDimens.circle)
                .clip(CircleShape)
                .then(
                    if (item.ticked) {
                        Modifier.background(c.accent)
                    } else {
                        Modifier.border(ShoppingDimens.circleRing, c.surf3, CircleShape)
                    },
                ),
        )
        Text(
            item.text,
            style = HhType.body.copy(fontSize = ShoppingDimens.itemName),
            color = if (item.ticked) c.mute else c.ink,
            textDecoration = if (item.ticked) TextDecoration.LineThrough else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Draws a child [by] to the left of its slot and gives that much back to its neighbour, so the gap before it is [by] smaller. */
private fun Modifier.pullLeft(by: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val back = by.roundToPx()
    layout(placeable.width - back, placeable.height) { placeable.place(-back, 0) }
}

private fun card() = RoundedCornerShape(ShoppingDimens.cardRadius)

/**
 * The tab as the rail opens it. Watches the list while shown (10 §4.4), and takes Home's + once to focus the box. A
 * tap on Add or Done adds what is typed; the box clears only if the add took, and only if nothing was typed since.
 */
@Composable
internal fun ShoppingTabHost(
    view: ShoppingView,
    actions: ShoppingActions,
    learning: ShoppingLearning,
    checks: ShoppingChecks,
    focus: ShoppingFocus,
    onForget: (Learned) -> Unit,
    nowMillis: Long,
) {
    DisposableEffect(checks) {
        val stop = checks.watch(checkNow = true)
        onDispose(stop)
    }
    val focusAdd = remember { focus.takeAddAsk() }
    val scope = rememberCoroutineScope()
    var typed by remember { mutableStateOf("") }
    val state by view.state.collectAsStateWithLifecycle()
    val learnedOrNull by learning.learned.collectAsStateWithLifecycle<List<Learned>?>(null)
    val learned = learnedOrNull.orEmpty()
    val shown = state ?: return
    val onList = remember(shown.items) { shown.items.filter { !it.ticked }.mapTo(HashSet()) { textKey(it.text) } }
    val suggestions = remember(typed, learned, onList) { suggest(typed, learned, onList) }
    val favourites = remember(learned, onList, suggestions) { favourites(learned, onList, suggestions.mapTo(HashSet()) { it.key }).take(ShoppingDimens.FAVOURITES_SHOWN_MAX) }

    // [boxAtTap] is what the box held when the add began, or null when the box has nothing to do with it.
    fun add(text: String, boxAtTap: String?) {
        scope.launch {
            val result = actions.add(text)
            val took = result is AddResult.Added || result is AddResult.AlreadyThere
            if (took && boxAtTap != null && typed == boxAtTap) typed = ""
        }
    }
    ShoppingTab(
        state = shown,
        typed = typed,
        onTyped = { typed = it },
        onAdd = { add(typed, typed) },
        suggestions = suggestions,
        favourites = favourites,
        onChip = { chip -> add(chip.text, typed.takeIf { chip in suggestions }) },
        onForget = onForget,
        onTick = { row -> scope.launch { actions.tick(row) } },
        focusAdd = focusAdd,
        nowMillis = nowMillis,
        nothingLearned = learnedOrNull?.isEmpty() == true,
    )
}
