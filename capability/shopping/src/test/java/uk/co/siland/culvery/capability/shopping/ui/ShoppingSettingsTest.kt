package uk.co.siland.culvery.capability.shopping.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.shopping.FakeAccess
import uk.co.siland.culvery.capability.shopping.ListGoneException
import uk.co.siland.culvery.capability.shopping.ProviderAccounts
import uk.co.siland.culvery.capability.shopping.QueuedKind
import uk.co.siland.culvery.capability.shopping.SAMPLE_DESCRIPTOR
import uk.co.siland.culvery.capability.shopping.SHOPPING_CONFIG_ACCOUNT
import uk.co.siland.culvery.capability.shopping.SampleShoppingProvider
import uk.co.siland.culvery.capability.shopping.ShoppingConnections
import uk.co.siland.culvery.capability.shopping.ShoppingDrainLoop
import uk.co.siland.culvery.capability.shopping.ShoppingHealth
import uk.co.siland.culvery.capability.shopping.ShoppingLearning
import uk.co.siland.culvery.capability.shopping.ShoppingLock
import uk.co.siland.culvery.capability.shopping.ShoppingStore
import uk.co.siland.culvery.capability.shopping.ShoppingSync
import uk.co.siland.culvery.capability.shopping.ToastLog
import uk.co.siland.culvery.capability.shopping.db.ShoppingDatabase
import uk.co.siland.culvery.core.access.CorePermissions
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.HouseholdClock
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.plugin.test.RecordingNavigator
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.TouchModeRule
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.waitFor

private const val FAMILY = "family@example.com"
private const val SAM = "sam@example.com"
private const val BLOCKED = "Connect Google Calendar first. Shopping uses the same account."
private const val ACCOUNT_GONE = "Shopping’s Google account was disconnected. Connect it again in Settings › Calendars."
private const val STOP_QUESTION = "Stop using Google Tasks? The list stays in Google Tasks, and suggestions stay here."

/** The sample provider under the name Settings shows for Google. */
internal val GOOGLE_NAMED = SAMPLE_DESCRIPTOR.copy(displayName = "Google Tasks")

/** The tags Settings › Shopping's taps carry; nothing else on the page can be pressed. */
private val PAGE_TAGS = setOf(
    "shopping_use", "shopping_account_0", "shopping_account_1", "shopping_allow", "shopping_make_list", "shopping_clear",
    "shopping_confirm_clear", "shopping_stop", "shopping_confirm_stop", "shopping_keep",
)

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShoppingSettingsTest {
    @get:Rule(order = 0) val touchMode = TouchModeRule()
    @get:Rule(order = 1) val compose = createComposeRule()
    private val navigator = RecordingNavigator()
    private val rigs = mutableListOf<SettingsRig>()

    @After fun closeRigs() = rigs.forEach { it.close() }

    private fun rig(accounts: ProviderAccounts = ProviderAccounts.Ready(listOf(FAMILY)), admin: Boolean = true) =
        SettingsRig(accounts, admin).also { rigs += it }

    private fun setContent(content: @Composable () -> Unit) = compose.setContent {
        CulveryTheme(dark = true) {
            CompositionLocalProvider(LocalShellNavigator provides navigator) {
                Box(Modifier.size(PAGE_WIDTH, PAGE_HEIGHT)) { content() }
            }
        }
    }

    private fun setHost(rig: SettingsRig) = setContent { ShoppingSettingsHost(rig.connections, rig.store, rig.householdClock) }

    private fun awaitTag(tag: String) = compose.waitFor { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    private fun awaitNoTag(tag: String) = compose.waitFor { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isEmpty() }

    private fun awaitThat(condition: () -> Boolean) = compose.waitFor { condition() }

    private fun stateless(state: SettingsState, now: Long = NOW) = setContent { ShoppingSettings(state, now, false, ShoppingSettingsActions()) }

    private fun on(rig: SettingsRig, health: ShoppingHealth = ShoppingHealth.OK, lastRead: Long? = NOW - 2 * 60_000) =
        SettingsState.On(rig.provider, FAMILY, health, lastRead)

    @Test
    fun withNoGoogleAccountItSaysConnectTheCalendarFirst() {
        val rig = rig()
        stateless(SettingsState.Off(rig.provider, ProviderAccounts.Blocked(BLOCKED)))
        compose.onNodeWithTag("shopping_blocked").assertTextEquals(BLOCKED)
        compose.onNodeWithTag("shopping_use").assertIsNotEnabled()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun oneAccountIsUsedWithoutAsking() {
        val rig = rig()
        setHost(rig)
        awaitTag("shopping_use")
        compose.onNodeWithText(FAMILY, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("shopping_use").performClick()
        awaitTag("shopping_stop")

        assertThat(rig.access.asked).containsExactly(listOf(CorePermissions.CONNECTIONS_MANAGE))
        awaitThat { runBlocking { rig.store.connectionNow() }?.lastReadMillis != null }
        assertThat(runBlocking { rig.store.connectionNow() }!!.connection.config[SHOPPING_CONFIG_ACCOUNT]).isEqualTo(FAMILY)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun twoAccountsAskWhich() {
        val rig = rig(ProviderAccounts.Ready(listOf(FAMILY, SAM)))
        setHost(rig)
        awaitTag("shopping_use")
        compose.onNodeWithTag("shopping_use").performClick()
        awaitTag("shopping_account_1")

        compose.onNodeWithText("Which Google account holds the list?").assertIsDisplayed()
        compose.onNodeWithTag("shopping_account_0").assertTextContains(FAMILY)
        compose.onNodeWithTag("shopping_account_1").assertTextContains(SAM)
        compose.onNodeWithTag("shopping_use").assertIsNotEnabled()
        compose.assertClickablesHaveRoleAndLabel()

        compose.onNodeWithTag("shopping_account_1").performClick()
        compose.onNodeWithTag("shopping_use").performClick()
        awaitTag("shopping_stop")

        assertThat(rig.access.asked).hasSize(1)
        awaitThat { runBlocking { rig.store.connectionNow() }?.lastReadMillis != null }
        assertThat(runBlocking { rig.store.connectionNow() }!!.connection.config[SHOPPING_CONFIG_ACCOUNT]).isEqualTo(SAM)
    }

    @Test
    fun aRefusedPinTurnsNothingOn() {
        val rig = rig(admin = false)
        setHost(rig)
        awaitTag("shopping_use")
        compose.onNodeWithTag("shopping_use").performClick()
        awaitThat { rig.access.asked.isNotEmpty() }
        compose.waitForIdle()

        assertThat(rig.provider.connectScreensComposed).isEqualTo(0)
        compose.onNodeWithTag("shopping_use").assertIsEnabled()
        compose.onNodeWithTag("shopping_use").assertIsOff()
        assertThat(compose.onAllNodes(hasTestTag("shopping_stop")).fetchSemanticsNodes()).isEmpty()
        assertThat(runBlocking { rig.store.connectionNow() }).isNull()
    }

    @Test
    fun aRefusedPinDoesNotReallow() {
        val rig = rig(admin = false)
        runBlocking {
            rig.store.connect(rig.connection)
            rig.store.setHealth(ShoppingHealth.NEEDS_PERMISSION)
        }
        setHost(rig)
        awaitTag("shopping_allow")
        compose.onNodeWithTag("shopping_allow").performClick()
        awaitThat { rig.access.asked.isNotEmpty() }
        compose.waitForIdle()

        assertThat(rig.provider.connectScreensComposed).isEqualTo(0)
        compose.onNodeWithTag("shopping_allow").assertIsEnabled()
        assertThat(runBlocking { rig.store.connectionNow() }!!.health).isEqualTo(ShoppingHealth.NEEDS_PERMISSION)
    }

    @Test
    fun theSwitchStaysOnAndDisabledUntilTheConnectionIsStored() {
        val rig = rig()
        val held = CompletableDeferred<Unit>()
        rig.holdLockUntil(held)
        setHost(rig)
        awaitTag("shopping_use")
        compose.onNodeWithTag("shopping_use").performClick()
        // connected() is waiting for the lock, so nothing is stored yet.
        awaitThat { rig.provider.connectScreensComposed == 1 }
        compose.waitForIdle()
        compose.onNodeWithTag("shopping_use").assertIsNotEnabled()
        compose.onNodeWithTag("shopping_use").assertIsOn()
        assertThat(runBlocking { rig.store.connectionNow() }).isNull()

        held.complete(Unit)
        awaitTag("shopping_stop")
        assertThat(runBlocking { rig.store.connectionNow() }).isNotNull()
    }

    @Test
    fun onShowsTheAccountTheHealthAndHowToStop() {
        val rig = rig()
        stateless(on(rig))
        compose.onNodeWithTag("shopping_use").assertTextContains(FAMILY)
        compose.onNodeWithTag("shopping_health").assertTextEquals("Updated 2 minutes ago")
        compose.onNodeWithTag("shopping_clear").assertTextEquals("Clear suggestions")
        compose.onNodeWithTag("shopping_stop").assertTextEquals("Stop using Google Tasks")
        compose.onNodeWithTag("shopping_kept_line")
            .assertTextEquals("Google Tasks stays allowed for $FAMILY until someone removes it in their Google account settings.")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun missingPermissionOffersAllow() {
        val rig = rig()
        runBlocking {
            rig.store.connect(rig.connection)
            rig.store.setHealth(ShoppingHealth.NEEDS_PERMISSION)
            rig.store.enqueue(QueuedKind.ADD, "k1", null, "Milk", 0)
            rig.store.enqueue(QueuedKind.ADD, "k2", null, "Bread", 0)
        }
        setHost(rig)
        awaitTag("shopping_allow")
        compose.onNodeWithTag("shopping_allow").assertTextEquals("Allow Google Tasks")
        compose.assertClickablesHaveRoleAndLabel()

        compose.onNodeWithTag("shopping_allow").performClick()
        awaitThat { runBlocking { rig.store.connectionNow() }?.health == ShoppingHealth.OK }

        assertThat(rig.access.asked).hasSize(1)
        assertThat(runBlocking { rig.store.connectionNow() }!!.connection.id).isEqualTo(rig.connection.id)
        assertThat(runBlocking { rig.store.queueNow() }.map { it.text }).containsExactly("Milk", "Bread").inOrder()
    }

    @Test
    fun makeANewListThenTheQueueDrains() {
        val rig = rig()
        runBlocking {
            rig.store.connect(rig.connection)
            rig.store.setHealth(ShoppingHealth.LIST_GONE)
            rig.store.enqueue(QueuedKind.ADD, "k1", null, "Milk", 0)
            rig.store.enqueue(QueuedKind.ADD, "k2", null, "Bread", 0)
        }
        rig.provider.failAll = ListGoneException()
        rig.loop.start()
        setHost(rig)
        awaitTag("shopping_make_list")
        compose.onNodeWithTag("shopping_health").assertTextEquals("The Shopping list isn’t in Google Tasks any more.")
        compose.onNodeWithTag("shopping_make_list").assertTextEquals("Make a new list")
        compose.assertClickablesHaveRoleAndLabel()

        compose.onNodeWithTag("shopping_make_list").performClick()
        awaitThat { runBlocking { rig.store.queueNow() }.isEmpty() }

        assertThat(rig.provider.calls).contains("remakeList")
        assertThat(runBlocking { rig.store.connectionNow() }!!.health).isEqualTo(ShoppingHealth.OK)
        assertThat(runBlocking { rig.provider.items(rig.connection) }.map { it.text }).containsExactly("Bread", "Milk")
        awaitNoTag("shopping_make_list")
    }

    @Test
    fun aDisconnectedAccountSaysWhereToFixIt() {
        val rig = rig()
        stateless(on(rig, ShoppingHealth.ACCOUNT_GONE))
        compose.onNodeWithTag("shopping_health").assertTextEquals(ACCOUNT_GONE)
        assertThat(compose.onAllNodes(hasTestTag("shopping_allow")).fetchSemanticsNodes()).isEmpty()
        assertThat(compose.onAllNodes(hasTestTag("shopping_make_list")).fetchSemanticsNodes()).isEmpty()
        compose.onNodeWithTag("shopping_stop").assertIsDisplayed()
    }

    @Test
    fun stopAsksFirstAndSaysWhatIsLost() {
        val rig = rig()
        runBlocking {
            rig.store.connect(rig.connection)
            rig.store.enqueue(QueuedKind.ADD, "k1", null, "Milk", 0)
        }
        setHost(rig)
        awaitTag("shopping_stop")
        compose.onNodeWithTag("shopping_stop").performClick()
        awaitTag("shopping_confirm")

        compose.onNodeWithText("$STOP_QUESTION 1 unsent change will be lost.", useUnmergedTree = true).assertIsDisplayed()
        assertThat(rig.access.asked).isEmpty()
        compose.assertClickablesHaveRoleAndLabel()

        compose.onNodeWithTag("shopping_keep").performClick()
        awaitNoTag("shopping_confirm")
        assertThat(runBlocking { rig.store.connectionNow() }).isNotNull()
        assertThat(runBlocking { rig.store.queueNow() }).hasSize(1)
        assertThat(rig.access.asked).isEmpty()
    }

    @Test
    fun confirmingStopAsksForThePinThenTurnsTheListOff() {
        val rig = rig()
        runBlocking { rig.store.connect(rig.connection) }
        setHost(rig)
        awaitTag("shopping_stop")
        compose.onNodeWithTag("shopping_stop").performClick()
        awaitTag("shopping_confirm")
        compose.onNodeWithText(STOP_QUESTION, useUnmergedTree = true).assertIsDisplayed()

        compose.onNodeWithTag("shopping_confirm_stop").performClick()
        awaitNoTag("shopping_confirm")

        assertThat(rig.access.asked).containsExactly(listOf(CorePermissions.CONNECTIONS_MANAGE))
        assertThat(runBlocking { rig.store.connectionNow() }).isNull()
        awaitTag("shopping_use")
        assertThat(rig.toasts.messages).containsExactly("Stopped using Google Tasks.")
    }

    @Test
    fun clearAsksFirst() {
        val rig = rig()
        runBlocking {
            rig.store.connect(rig.connection)
            rig.learning.added("Butter")
        }
        setHost(rig)
        awaitTag("shopping_clear")
        compose.onNodeWithTag("shopping_clear").performClick()
        awaitTag("shopping_confirm")

        compose.onNodeWithText("Clear suggestions? Favourites start again from what’s added next.", useUnmergedTree = true).assertIsDisplayed()
        assertThat(rig.access.asked).isEmpty()
        assertThat(runBlocking { rig.store.learned.first() }).hasSize(1)

        compose.onNodeWithTag("shopping_confirm_clear").performClick()
        awaitNoTag("shopping_confirm")

        assertThat(rig.access.asked).hasSize(1)
        assertThat(runBlocking { rig.store.learned.first() }).isEmpty()
        assertThat(rig.toasts.messages).containsExactly("Suggestions cleared.")
    }

    @Test
    fun nothingOnThePageOpensAnythingElse() {
        val rig = rig()
        val states = listOf(
            SettingsState.Off(rig.provider, ProviderAccounts.Ready(listOf(FAMILY))),
            SettingsState.Off(rig.provider, ProviderAccounts.Blocked(BLOCKED)),
            SettingsState.ChoosingAccount(rig.provider, listOf(FAMILY, SAM), SAM),
            on(rig),
            on(rig, ShoppingHealth.NEEDS_PERMISSION),
            on(rig, ShoppingHealth.LIST_GONE),
            on(rig, ShoppingHealth.ACCOUNT_GONE),
            on(rig, ShoppingHealth.CANT_REACH),
            SettingsState.ConfirmingStop(on(rig), 2),
            SettingsState.ConfirmingClear(on(rig)),
        )
        var shown by mutableStateOf<SettingsState>(states.first())
        setContent { ShoppingSettings(shown, NOW, false, ShoppingSettingsActions()) }
        states.forEach { state ->
            shown = state
            compose.waitForIdle()
            val clickables = compose.onAllNodes(hasClickAction())
            val tags = clickables.fetchSemanticsNodes().map { it.config.getOrNull(SemanticsProperties.TestTag) }
            assertThat(PAGE_TAGS).containsAtLeastElementsIn(tags)
            repeat(tags.size) { clickables[it].performClick() }
        }
        assertThat(navigator.tabs).isEmpty()
        assertThat(navigator.settingsOpened).isEqualTo(0)
        assertThat(navigator.calls).isEmpty()
        assertThat(navigator.systemScreens).isEmpty()
    }
}

private val PAGE_WIDTH = 760.dp
private val PAGE_HEIGHT = 700.dp

/** The real shopping classes over an in-memory database on real dispatchers, with the sample provider named Google Tasks. */
internal class SettingsRig(accounts: ProviderAccounts, admin: Boolean) {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, ShoppingDatabase::class.java).allowMainThreadQueries().build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clock = WallClock { System.currentTimeMillis() }
    val provider = SampleShoppingProvider(clock = clock, descriptor = GOOGLE_NAMED, accounts = flowOf(accounts), accountGoneLine = ACCOUNT_GONE)
    val connection = Connection("c1", GOOGLE_NAMED.id, GOOGLE_NAMED.displayName, mapOf(SHOPPING_CONFIG_ACCOUNT to FAMILY))
    val store = ShoppingStore(db)
    val learning = ShoppingLearning(store, clock)
    private val lock = ShoppingLock()
    val access = FakeAccess(admin)
    val toasts = ToastLog()
    private val sync = ShoppingSync(store, setOf(provider), learning, lock, clock)
    val loop = ShoppingDrainLoop(sync, clock, scope)
    val connections = ShoppingConnections(store, setOf(provider), sync, lock, loop, learning, access, toasts, scope)
    val householdClock = HouseholdClock(flowOf(ZoneOffset.UTC), clock, scope, ticks = emptyFlow())

    /** Takes the shopping lock until [release] completes, so a write that needs it waits. */
    fun holdLockUntil(release: CompletableDeferred<Unit>) {
        val taken = CompletableDeferred<Unit>()
        scope.launch { lock.withLock { taken.complete(Unit); release.await() } }
        runBlocking { taken.await() }
    }

    fun close() {
        scope.cancel()
        db.close()
    }
}
