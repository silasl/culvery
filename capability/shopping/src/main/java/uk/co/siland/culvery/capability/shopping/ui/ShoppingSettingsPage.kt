package uk.co.siland.culvery.capability.shopping.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.capability.shopping.CLEAR_QUESTION
import uk.co.siland.culvery.capability.shopping.CLEAR_SUGGESTIONS
import uk.co.siland.culvery.capability.shopping.MAKE_A_NEW_LIST
import uk.co.siland.culvery.capability.shopping.ProviderAccounts
import uk.co.siland.culvery.capability.shopping.SHOPPING_CONFIG_ACCOUNT
import uk.co.siland.culvery.capability.shopping.ShoppingConnections
import uk.co.siland.culvery.capability.shopping.ShoppingHealth
import uk.co.siland.culvery.capability.shopping.ShoppingProvider
import uk.co.siland.culvery.capability.shopping.ShoppingStore
import uk.co.siland.culvery.capability.shopping.StoredShopping
import uk.co.siland.culvery.capability.shopping.WHICH_ACCOUNT
import uk.co.siland.culvery.capability.shopping.allow
import uk.co.siland.culvery.capability.shopping.listGoneOnSettings
import uk.co.siland.culvery.capability.shopping.problemLine
import uk.co.siland.culvery.capability.shopping.stayedAllowed
import uk.co.siland.culvery.capability.shopping.stopQuestion
import uk.co.siland.culvery.capability.shopping.stopUsing
import uk.co.siland.culvery.capability.shopping.useService
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.HouseholdClock
import uk.co.siland.culvery.core.plugin.KEEP
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.plugin.rememberNowMillis
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhConfirmCard
import uk.co.siland.culvery.core.ui.HhPageTitle
import uk.co.siland.culvery.core.ui.HhRadioRow
import uk.co.siland.culvery.core.ui.HhStatusLine
import uk.co.siland.culvery.core.ui.HhSwitchRow
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.agoWords
import uk.co.siland.culvery.core.ui.rememberSingleAction

private const val TAG = "ShoppingSettings"

internal const val SHOPPING_PAGE_ID = "shopping"

/** Settings › Shopping (10 §4.10): after Calendars (400), before Door (500). */
class ShoppingPage internal constructor(private val content: @Composable () -> Unit) : SettingsPage {
    override val id = SHOPPING_PAGE_ID
    override val title = SHOPPING
    override val order = 420

    @Composable
    override fun Content() = content()
}

/** A question the page asks before it acts; it draws over the page's foot. */
internal sealed interface Confirm {
    data class Stop(val queued: Int) : Confirm

    data object Clear : Confirm
}

/** What Settings › Shopping shows. [On] is the page itself, with [On.confirm] when a question is up. */
internal sealed interface SettingsState {
    val provider: ShoppingProvider

    data class Off(override val provider: ShoppingProvider, val accounts: ProviderAccounts) : SettingsState

    /** More than one account: [chosen] is null until one is picked. */
    data class ChoosingAccount(override val provider: ShoppingProvider, val accounts: List<String>, val chosen: String?) : SettingsState

    /** The provider's own screens are up; [existing] is the stored connection when the permission is being given again. */
    data class Connecting(override val provider: ShoppingProvider, val account: String?, val existing: Connection? = null) : SettingsState

    /** Connected in the provider's screens and waiting for the store to hold it. */
    data class Finishing(override val provider: ShoppingProvider, val account: String?) : SettingsState

    data class On(
        override val provider: ShoppingProvider,
        val account: String?,
        val health: ShoppingHealth,
        val lastReadMillis: Long?,
        val confirm: Confirm? = null,
    ) : SettingsState
}

/** What Settings › Shopping's taps do; each does nothing unless given, so a test or screenshot passes only what it checks. */
internal class ShoppingSettingsActions(
    val onTurnOn: () -> Unit = {},
    val onChoose: (String) -> Unit = {},
    val onUseChosen: () -> Unit = {},
    val onConnected: (Connection) -> Unit = {},
    val onCancelConnect: () -> Unit = {},
    val onAllow: () -> Unit = {},
    val onMakeList: () -> Unit = {},
    val onAskClear: () -> Unit = {},
    val onConfirmClear: () -> Unit = {},
    val onAskStop: () -> Unit = {},
    val onConfirmStop: () -> Unit = {},
    val onKeep: () -> Unit = {},
)

/** The page for [state], with the health's words as of [nowMillis]; taps do nothing while [busy]. */
@Composable
internal fun ShoppingSettings(state: SettingsState, nowMillis: Long, busy: Boolean, actions: ShoppingSettingsActions) {
    val service = state.provider.descriptor.displayName
    Column(verticalArrangement = Arrangement.spacedBy(ShoppingDimens.settingsGap), modifier = Modifier.testTag("settings_shopping")) {
        HhPageTitle(SHOPPING)
        when (state) {
            is SettingsState.Off -> Off(state, service, busy, actions)
            is SettingsState.ChoosingAccount -> Choosing(state, service, busy, actions)
            is SettingsState.Connecting -> {
                LockedUseRow(service, state.account, checked = true)
                state.provider.ConnectScreen(state.account, state.existing, actions.onConnected, actions.onCancelConnect)
            }
            is SettingsState.Finishing -> LockedUseRow(service, state.account, checked = true)
            is SettingsState.On -> On(state, service, busy, nowMillis, actions)
        }
    }
}

/** The switch while it can't be used: connecting is blocked, or under way. */
@Composable
private fun LockedUseRow(service: String, account: String?, checked: Boolean) =
    HhSwitchRow(useService(service), account, checked = checked, tag = "shopping_use", onChange = {}, enabled = false)

@Composable
private fun Off(state: SettingsState.Off, service: String, busy: Boolean, actions: ShoppingSettingsActions) {
    when (val accounts = state.accounts) {
        is ProviderAccounts.Blocked -> {
            LockedUseRow(service, null, checked = false)
            Text(accounts.line, style = HhType.body, color = Culvery.colors.mute, modifier = Modifier.testTag("shopping_blocked"))
        }
        is ProviderAccounts.Ready -> HhSwitchRow(
            useService(service), accounts.accounts.singleOrNull(), checked = false, tag = "shopping_use",
            onChange = { if (it) actions.onTurnOn() }, enabled = !busy,
        )
    }
}

@Composable
private fun Choosing(state: SettingsState.ChoosingAccount, service: String, busy: Boolean, actions: ShoppingSettingsActions) {
    HhSwitchRow(
        useService(service), null, checked = false, tag = "shopping_use",
        onChange = { if (it) actions.onUseChosen() }, enabled = state.chosen != null && !busy,
    )
    Text(WHICH_ACCOUNT, style = HhType.body, color = Culvery.colors.ink, modifier = Modifier.testTag("shopping_which"))
    Column(verticalArrangement = Arrangement.spacedBy(ShoppingDimens.settingsGap), modifier = Modifier.selectableGroup()) {
        state.accounts.forEachIndexed { index, account ->
            HhRadioRow(account, selected = account == state.chosen, tag = "shopping_account_$index", locked = busy) { actions.onChoose(account) }
        }
    }
}

@Composable
private fun On(state: SettingsState.On, service: String, busy: Boolean, nowMillis: Long, actions: ShoppingSettingsActions) {
    val c = Culvery.colors
    val confirm = state.confirm
    HhSwitchRow(
        useService(service), state.account, checked = true, tag = "shopping_use",
        onChange = { if (!it) actions.onAskStop() }, enabled = !busy && confirm == null,
    )
    Health(state, service, nowMillis)
    when (state.health) {
        ShoppingHealth.NEEDS_PERMISSION -> HhButton(
            allow(service), actions.onAllow, Modifier.testTag("shopping_allow"),
            tone = ButtonTone.Primary, enabled = !busy && confirm == null, on = ButtonSurface.Bg,
        )
        ShoppingHealth.LIST_GONE -> HhButton(
            MAKE_A_NEW_LIST, actions.onMakeList, Modifier.testTag("shopping_make_list"),
            tone = ButtonTone.Primary, enabled = !busy && confirm == null, on = ButtonSurface.Bg,
        )
        else -> Unit
    }
    when (confirm) {
        is Confirm.Stop -> HhConfirmCard(
            tag = "shopping_confirm", title = stopQuestion(service, confirm.queued),
            keep = KEEP, keepTag = "shopping_keep",
            confirm = stopUsing(service), confirmTag = "shopping_confirm_stop",
            busy = busy, onKeep = actions.onKeep, onConfirm = actions.onConfirmStop,
        )
        Confirm.Clear -> HhConfirmCard(
            tag = "shopping_confirm", title = CLEAR_QUESTION,
            keep = KEEP, keepTag = "shopping_keep",
            confirm = CLEAR_SUGGESTIONS, confirmTag = "shopping_confirm_clear",
            busy = busy, onKeep = actions.onKeep, onConfirm = actions.onConfirmClear,
        )
        null -> {
            HhButton(CLEAR_SUGGESTIONS, actions.onAskClear, Modifier.testTag("shopping_clear"), enabled = !busy, on = ButtonSurface.Bg)
            HhButton(stopUsing(service), actions.onAskStop, Modifier.testTag("shopping_stop"), enabled = !busy, on = ButtonSurface.Bg)
        }
    }
    if (state.account != null) {
        Text(stayedAllowed(service, state.account), style = HhType.body, color = c.mute, modifier = Modifier.testTag("shopping_kept_line"))
    }
}

/** "Updated 2 minutes ago" when it is fine; else what is wrong, as the tab and card say it. */
@Composable
private fun Health(state: SettingsState.On, service: String, nowMillis: Long) {
    val problem = when (state.health) {
        ShoppingHealth.OK -> null
        ShoppingHealth.LIST_GONE -> listGoneOnSettings(service)
        else -> problemLine(state.health, state.provider)
    }
    if (problem != null) {
        HhStatusLine(problem, "shopping_health")
    } else {
        val words = state.lastReadMillis?.let { "Updated ${agoWords(it, nowMillis)}" } ?: "Not updated yet"
        Text(words, style = HhType.body, color = Culvery.colors.mute, modifier = Modifier.testTag("shopping_health"))
    }
}

/** What the page is doing besides showing the stored connection. */
private sealed interface Step {
    data object Idle : Step

    data class Choosing(val chosen: String?) : Step

    data class Connecting(val account: String?, val existing: Connection?) : Step

    data class Finishing(val account: String?) : Step

    data class Asking(val confirm: Confirm) : Step
}

/** What the store holds, once it has answered: a bare null would be "not connected" before it is known. */
private class Known(val stored: StoredShopping?)

/** Settings › Shopping with its data. The Admin PIN comes first; the provider's own screens do the rest of connecting. */
@Composable
internal fun ShoppingSettingsHost(connections: ShoppingConnections, store: ShoppingStore, clock: HouseholdClock) {
    val provider = connections.offered ?: return
    val known by remember(store) { store.connection.map { Known(it) } }.collectAsStateWithLifecycle<Known?>(null)
    val accounts by provider.accounts.collectAsStateWithLifecycle<ProviderAccounts?>(null)
    var step by remember { mutableStateOf<Step>(Step.Idle) }
    val action = rememberSingleAction(Unit) { e -> Log.w(TAG, "A shopping change failed (${e::class.simpleName})") }
    val nowMillis = rememberNowMillis(clock)
    val stored = (known ?: return).stored
    LaunchedEffect(stored) { if (stored != null && step is Step.Finishing) step = Step.Idle }
    val offered = accounts ?: return

    val state = when {
        stored == null -> when (val s = step) {
            is Step.Choosing -> SettingsState.ChoosingAccount(provider, (offered as? ProviderAccounts.Ready)?.accounts.orEmpty(), s.chosen)
            is Step.Connecting -> SettingsState.Connecting(provider, s.account, null)
            is Step.Finishing -> SettingsState.Finishing(provider, s.account)
            else -> SettingsState.Off(provider, offered)
        }
        else -> {
            val on = SettingsState.On(provider, stored.connection.config[SHOPPING_CONFIG_ACCOUNT], stored.health, stored.lastReadMillis)
            when (val s = step) {
                is Step.Connecting -> SettingsState.Connecting(provider, s.account, s.existing)
                is Step.Asking -> on.copy(confirm = s.confirm)
                else -> on
            }
        }
    }
    ShoppingSettings(
        state = state,
        nowMillis = nowMillis,
        busy = action.busy,
        actions = ShoppingSettingsActions(
            onTurnOn = {
                action.run {
                    if (!connections.mayConnect()) return@run
                    val ready = (offered as? ProviderAccounts.Ready)?.accounts.orEmpty()
                    step = if (ready.size > 1) Step.Choosing(null) else Step.Connecting(ready.singleOrNull(), null)
                }
            },
            onChoose = { account -> step = Step.Choosing(account) },
            onUseChosen = { (step as? Step.Choosing)?.chosen?.let { step = Step.Connecting(it, null) } },
            onConnected = { connection ->
                if (stored != null) {
                    step = Step.Idle
                    connections.reallowed(connection)
                } else {
                    step = Step.Finishing(connection.config[SHOPPING_CONFIG_ACCOUNT])
                    connections.connected(connection)
                }
            },
            onCancelConnect = { step = Step.Idle },
            onAllow = {
                action.run {
                    if (connections.mayConnect() && stored != null) {
                        step = Step.Connecting(stored.connection.config[SHOPPING_CONFIG_ACCOUNT], stored.connection)
                    }
                }
            },
            onMakeList = { action.run { connections.remakeList() } },
            onAskStop = { action.run { step = Step.Asking(Confirm.Stop(connections.queued())) } },
            onConfirmStop = {
                action.run {
                    if (connections.stop()) step = Step.Idle
                }
            },
            onAskClear = { step = Step.Asking(Confirm.Clear) },
            onConfirmClear = {
                action.run {
                    if (connections.clearSuggestions()) step = Step.Idle
                }
            },
            onKeep = { step = Step.Idle },
        ),
    )
}
