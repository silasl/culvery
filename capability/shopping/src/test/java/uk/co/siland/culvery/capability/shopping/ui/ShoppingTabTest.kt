package uk.co.siland.culvery.capability.shopping.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.shopping.Learned
import uk.co.siland.culvery.capability.shopping.QueuedKind
import uk.co.siland.culvery.capability.shopping.SAMPLE_DESCRIPTOR
import uk.co.siland.culvery.capability.shopping.SampleShoppingProvider
import uk.co.siland.culvery.capability.shopping.ShoppingActions
import uk.co.siland.culvery.capability.shopping.ShoppingChecks
import uk.co.siland.culvery.capability.shopping.ShoppingDrainLoop
import uk.co.siland.culvery.capability.shopping.ShoppingFocus
import uk.co.siland.culvery.capability.shopping.ShoppingHealth
import uk.co.siland.culvery.capability.shopping.ShoppingLearning
import uk.co.siland.culvery.capability.shopping.ShoppingLock
import uk.co.siland.culvery.capability.shopping.ShoppingState
import uk.co.siland.culvery.capability.shopping.ShoppingStore
import uk.co.siland.culvery.capability.shopping.ShoppingView
import uk.co.siland.culvery.capability.shopping.ShownItem
import uk.co.siland.culvery.capability.shopping.ToastLog
import uk.co.siland.culvery.capability.shopping.db.ShoppingDatabase
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.plugin.test.RecordingNavigator
import uk.co.siland.culvery.core.plugin.test.TestOverlay
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.HhColors
import uk.co.siland.culvery.core.ui.test.TouchModeRule
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.pixelAt
import uk.co.siland.culvery.core.ui.test.waitFor

internal const val NOW = 1_800_000_000_000L

/** The main area beside the rail on the 1280 × 800 canvas, as the shell gives a tab. */
internal val TAB_WIDTH = 1116.dp
internal val TAB_HEIGHT = 690.dp
private const val CANT_REACH_LINE = "Can’t reach Google Tasks. Items you add are kept and sent when it’s back."

internal fun row(n: Int, text: String = "Item $n", ticked: Boolean = false, queued: Boolean = false) =
    ShownItem(if (queued) "q:k$n" else "g$n", text, ticked, queued)

internal fun stateOf(
    items: List<ShownItem>,
    problem: String? = null,
    health: ShoppingHealth = ShoppingHealth.OK,
    flashing: String? = null,
    lastReadMillis: Long? = NOW,
) = ShoppingState(items, items.count { !it.ticked }, health, lastReadMillis, "Google Tasks", problem, flashing)

private val milk = row(1, "Milk")
private val eggs = Learned("eggs", "Eggs", 3, 0)
private val butter = Learned("butter", "Butter", 2, 0)

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShoppingTabTest {
    @get:Rule(order = 0) val touchMode = TouchModeRule()
    @get:Rule(order = 1) val compose = createComposeRule()
    private val navigator = RecordingNavigator()
    private val overlay = TestOverlay()
    private val rigs = mutableListOf<UiRig>()
    private lateinit var colors: HhColors

    @After fun closeRigs() = rigs.forEach { it.close() }

    private fun rig() = UiRig().also { rigs += it }

    private fun setTab(content: @Composable () -> Unit) = compose.setContent {
        CulveryTheme(dark = true) {
            colors = Culvery.colors
            CompositionLocalProvider(LocalShellNavigator provides navigator, LocalOverlayHost provides overlay) {
                Box(Modifier.size(TAB_WIDTH, TAB_HEIGHT).background(Culvery.colors.bg)) { content() }
            }
        }
    }

    private fun showTab(
        state: ShoppingState,
        typed: String = "",
        suggestions: List<Learned> = emptyList(),
        favourites: List<Learned> = emptyList(),
        onTick: (ShownItem) -> Unit = {},
        onChip: (Learned) -> Unit = {},
        onForget: (Learned) -> Unit = {},
        onAdd: () -> Unit = {},
    ) = setTab {
        ShoppingTab(state, typed, {}, onAdd, suggestions, favourites, onChip, onForget, onTick, focusAdd = false, nowMillis = NOW)
    }

    private fun showHost(rig: UiRig, shown: () -> Boolean = { true }) = setTab {
        if (shown()) ShoppingTabHost(rig.view, rig.actions, rig.learning, rig.checks, rig.focus, onForget = {}, nowMillis = NOW)
    }

    private fun waitForField() = compose.waitFor { compose.onAllNodes(hasTestTag("shopping_field")).fetchSemanticsNodes().isNotEmpty() }

    private fun textLayout(tag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(results)
        return results.first()
    }

    private fun typedText() = compose.onNodeWithTag("shopping_field").fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    @Test
    fun theHeaderSaysHowManyAndFromWhere() {
        showTab(stateOf((1..22).map { row(it) }))
        compose.onNodeWithTag("shopping_title").assertTextEquals("Shopping").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onNodeWithTag("shopping_line").assertTextEquals("22 items · from Google Tasks, just now")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theListFillsDownThenAcross() {
        showTab(stateOf((1..7).map { row(it) }))
        fun bounds(n: Int) = compose.onNodeWithTag("shopping_item_g$n").getUnclippedBoundsInRoot()
        assertThat(bounds(4).top).isEqualTo(bounds(1).top)
        assertThat(bounds(7).top).isEqualTo(bounds(1).top)
        assertThat(bounds(2).top).isGreaterThan(bounds(1).top)
        assertThat(bounds(3).top).isGreaterThan(bounds(2).top)
        assertThat(bounds(5).top).isEqualTo(bounds(2).top)
        assertThat(bounds(4).left).isGreaterThan(bounds(1).left)
        assertThat(bounds(7).left).isGreaterThan(bounds(4).left)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun thirtyItemsScrollWithAFade() {
        showTab(stateOf((1..30).map { row(it) }))
        compose.onNodeWithTag("shopping_item_g30").assertIsNotDisplayed()
        compose.onNodeWithTag("shopping_list_fade").assertIsDisplayed()
        compose.onNodeWithTag("shopping_list").performScrollToNode(hasTestTag("shopping_item_g30"))
        compose.onNodeWithTag("shopping_item_g30").assertIsDisplayed()
        compose.onNodeWithTag("shopping_list").performTouchInput { swipeUp() }
        compose.onNodeWithTag("shopping_list_fade").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun tappingTheNameTicks() {
        val ticked = mutableListOf<ShownItem>()
        showTab(stateOf(listOf(milk, row(2, "Bread"))), onTick = { ticked += it })
        compose.onNodeWithTag("shopping_item_g1")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "not ticked"))
            .assertTextEquals("Milk")
        compose.onNodeWithText("Milk").performClick()
        assertThat(ticked).containsExactly(milk)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aTickedRowIsMutedAndStruck() {
        showTab(stateOf(listOf(row(1, "Milk", ticked = true), row(2, "Bread"))))
        compose.onNodeWithTag("shopping_item_g1")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "ticked"))
        val struck = textLayout("shopping_item_g1").layoutInput.style
        assertThat(struck.textDecoration).isEqualTo(TextDecoration.LineThrough)
        assertThat(struck.color).isEqualTo(colors.mute)
        val open = textLayout("shopping_item_g2").layoutInput.style
        assertThat(open.textDecoration).isNotEqualTo(TextDecoration.LineThrough)
        assertThat(open.color).isEqualTo(colors.ink)
        assertThat(pixelAt(compose.onNodeWithTag("shopping_circle_g1", useUnmergedTree = true), 10.dp, 10.dp)).isEqualTo(colors.accent.toArgb())
        assertThat(pixelAt(compose.onNodeWithTag("shopping_circle_g2", useUnmergedTree = true), 10.dp, 10.dp)).isNotEqualTo(colors.accent.toArgb())
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theFlashingRowIsAccentSoft() {
        showTab(stateOf(listOf(milk, row(2, "Bread")), flashing = "g1"))
        assertThat(pixelAt(compose.onNodeWithTag("shopping_item_g1"), 2.dp, 24.dp)).isEqualTo(colors.accentSoft.toArgb())
        assertThat(pixelAt(compose.onNodeWithTag("shopping_item_g2"), 2.dp, 24.dp)).isNotEqualTo(colors.accentSoft.toArgb())
    }

    @Test
    fun aChipTapCallsOnChip() {
        val chosen = mutableListOf<Learned>()
        showTab(stateOf(listOf(milk)), typed = "b", suggestions = listOf(butter), favourites = listOf(eggs), onChip = { chosen += it })
        compose.onNodeWithTag("shopping_suggest_butter").performClick()
        compose.onNodeWithTag("shopping_favourite_eggs").performClick()
        assertThat(chosen).containsExactly(butter, eggs).inOrder()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aLongPressOnAFavouriteOffersForget() {
        val forgotten = mutableListOf<Learned>()
        showTab(stateOf(listOf(milk)), favourites = listOf(eggs), onForget = { forgotten += it })
        val chip = compose.onNodeWithTag("shopping_favourite_eggs")
        assertThat(chip.fetchSemanticsNode().config[SemanticsActions.OnLongClick].label).isEqualTo("Forget Eggs")
        chip.performTouchInput { longClick() }
        assertThat(forgotten).containsExactly(eggs)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun nothingLearnedSaysHowFavouritesCome() {
        showTab(stateOf(listOf(milk)))
        compose.onNodeWithTag("shopping_favourites_hint").assertTextEquals("Favourites appear as you add things.")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun anEmptyListSaysWhatToDo() {
        showTab(stateOf(emptyList()))
        compose.onNodeWithTag("shopping_empty").assertTextEquals("Nothing on the list. Add something above, or tap a favourite.")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aProblemShowsUnderTheAddCard() {
        var adds = 0
        showTab(stateOf(listOf(milk), problem = CANT_REACH_LINE, health = ShoppingHealth.CANT_REACH), onAdd = { adds++ })
        compose.onNodeWithTag("shopping_status").assertTextEquals(CANT_REACH_LINE)
        val status = compose.onNodeWithTag("shopping_status").getUnclippedBoundsInRoot()
        assertThat(status.top).isAtLeast(compose.onNodeWithTag("shopping_add").getUnclippedBoundsInRoot().bottom)
        assertThat(status.bottom).isAtMost(compose.onNodeWithTag("shopping_list").getUnclippedBoundsInRoot().top)
        compose.onNodeWithTag("shopping_add").performClick()
        assertThat(adds).isEqualTo(1)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aLongNameIsOneLineEndingInAnEllipsis() {
        showTab(stateOf(listOf(row(1, "Milk".repeat(250)), row(2, "Bread"))))
        val height = compose.onNodeWithTag("shopping_item_g1").getUnclippedBoundsInRoot().height.value
        assertThat(height).isWithin(1f).of(48f)
        val layout = textLayout("shopping_item_g1")
        assertThat(layout.lineCount).isEqualTo(1)
        assertThat(layout.isLineEllipsized(0)).isTrue()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theAddBoxTakesAHundredCharacters() {
        setTab {
            var typed by remember { mutableStateOf("") }
            ShoppingTab(stateOf(listOf(milk)), typed, { typed = it }, {}, emptyList(), emptyList(), {}, {}, {}, focusAdd = false, nowMillis = NOW)
        }
        compose.onNodeWithTag("shopping_field").performTextInput("a".repeat(120))
        assertThat(typedText()).hasLength(100)
        compose.onNodeWithTag("shopping_field").performTextInput("b")
        assertThat(typedText()).hasLength(100)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun doneThenAddAddsOnce() {
        val rig = rig()
        // The first add is held at the lock while Add is tapped, so a second one really is in flight beside it.
        val held = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        rig.scope.launch { rig.lock.withLock { held.complete(Unit); release.await() } }
        runBlocking { held.await() }
        showHost(rig)
        waitForField()
        compose.onNodeWithTag("shopping_field").performTextInput("Milk")
        compose.onNodeWithTag("shopping_field").performImeAction()
        compose.onNodeWithTag("shopping_add").performClick()
        release.complete(Unit)
        // Adds run one at a time in order, so an empty one returns after both taps' adds have.
        runBlocking { rig.actions.add(" ") }
        assertThat(rig.queuedAdds()).containsExactly("Milk")
        compose.waitFor { typedText().isEmpty() }
        compose.onNodeWithTag("shopping_field").assertIsFocused()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun suggestionsComeFirstThenFavourites() {
        val rig = rig()
        val day = 24 * 60 * 60_000L
        runBlocking {
            rig.store.learnUse("Bread", 0)
            rig.store.learnUse("Bread", 2 * day)
            rig.store.learnUse("Bin bags", day)
            rig.store.learnUse("Butter", 3 * day)
            rig.store.learnUse("Eggs", 4 * day)
            rig.store.learnUse("Tea bags", 5 * day)
        }
        showHost(rig)
        waitForField()
        compose.onNodeWithTag("shopping_field").performTextInput("b")
        compose.waitFor { compose.onAllNodes(hasTestTag("shopping_favourite_eggs")).fetchSemanticsNodes().isNotEmpty() }
        fun left(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().left
        assertThat(left("shopping_suggest_bread")).isLessThan(left("shopping_suggest_butter"))
        assertThat(left("shopping_suggest_butter")).isLessThan(left("shopping_suggest_bin bags"))
        assertThat(left("shopping_suggest_tea bags")).isLessThan(left("shopping_favourite_eggs"))
        listOf("bread", "butter", "bin bags", "tea bags").forEach { compose.onNodeWithTag("shopping_favourite_$it").assertDoesNotExist() }
        compose.onNodeWithTag("shopping_suggest_butter").performClick()
        compose.waitFor { rig.queuedAdds().isNotEmpty() }
        assertThat(rig.queuedAdds()).containsExactly("Butter")
        compose.waitFor { typedText().isEmpty() }
    }

    @Test
    fun homesPlusFocusesTheBox() {
        val rig = rig()
        rig.focus.askForAdd()
        var shown by mutableStateOf(true)
        lateinit var focusManager: FocusManager
        setTab {
            focusManager = LocalFocusManager.current
            if (shown) ShoppingTabHost(rig.view, rig.actions, rig.learning, rig.checks, rig.focus, onForget = {}, nowMillis = NOW)
        }
        waitForField()
        compose.onNodeWithTag("shopping_field").assertIsFocused()
        compose.runOnIdle { focusManager.clearFocus() }
        shown = false
        compose.waitForIdle()
        shown = true
        waitForField()
        compose.onNodeWithTag("shopping_field").assertIsNotFocused()
    }

    @Test
    fun theTabWatchesWhileShown() {
        val rig = rig()
        var shown by mutableStateOf(true)
        showHost(rig) { shown }
        compose.waitForIdle()
        assertThat(rig.checks.watching).isEqualTo(1)
        shown = false
        compose.waitForIdle()
        assertThat(rig.checks.watching).isEqualTo(0)
    }

    @Test
    fun nothingOnTheTabLeavesTheList() {
        val states = listOf(
            stateOf((1..9).map { row(it) }),
            stateOf(emptyList()),
            stateOf(listOf(milk, row(2, "Bread", ticked = true)), problem = CANT_REACH_LINE, health = ShoppingHealth.CANT_REACH),
        )
        var shown by mutableStateOf(states.first())
        setTab { ShoppingTab(shown, "b", {}, {}, listOf(butter), listOf(eggs), {}, {}, {}, focusAdd = false, nowMillis = NOW) }
        states.forEach { state ->
            shown = state
            compose.waitForIdle()
            val clickables = compose.onAllNodes(hasClickAction())
            repeat(clickables.fetchSemanticsNodes().size) { clickables[it].performClick() }
        }
        assertThat(navigator.tabs).isEmpty()
        assertThat(navigator.settingsOpened).isEqualTo(0)
        assertThat(navigator.calls).isEmpty()
        assertThat(overlay.shown).isEqualTo(0)
    }
}

/** The real shopping classes over an in-memory database on real dispatchers; nothing drains the queue. */
private class UiRig {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, ShoppingDatabase::class.java).allowMainThreadQueries().build()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clock = WallClock { System.currentTimeMillis() }
    private val provider = SampleShoppingProvider(clock = clock)
    val store = ShoppingStore(db)
    val learning = ShoppingLearning(store, clock)
    val lock = ShoppingLock()
    val checks = ShoppingChecks(flowOf(false), MutableStateFlow(false), {}, scope)
    val actions = ShoppingActions(store, learning, lock, ShoppingDrainLoop({ null }, { clock.nowMillis() }, scope), checks, ToastLog(), clock, scope)
    val view = ShoppingView(store, actions, setOf(provider), scope)
    val focus = ShoppingFocus()

    init {
        runBlocking { store.connect(Connection("c1", SAMPLE_DESCRIPTOR.id, "the sample list", emptyMap())) }
    }

    fun queuedAdds(): List<String> = runBlocking { store.queueNow() }.filter { it.kind == QueuedKind.ADD.name }.map { it.text }

    fun close() {
        scope.cancel()
        db.close()
    }
}
