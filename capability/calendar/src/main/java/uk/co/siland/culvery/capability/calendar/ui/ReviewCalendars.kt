package uk.co.siland.culvery.capability.calendar.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import uk.co.siland.culvery.capability.calendar.CONFIG_ACCOUNT
import uk.co.siland.culvery.capability.calendar.CalendarConnections
import uk.co.siland.culvery.capability.calendar.CalendarReview
import uk.co.siland.culvery.capability.calendar.CalendarRow
import uk.co.siland.culvery.capability.calendar.ReviewConnection
import uk.co.siland.culvery.capability.calendar.StoredSource
import uk.co.siland.culvery.capability.calendar.disconnectQuestion
import uk.co.siland.culvery.capability.calendar.syncedLabel
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth
import uk.co.siland.culvery.core.plugin.ProviderDescriptor
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.ControlTokens
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.DarkColors
import uk.co.siland.culvery.core.ui.HhChoiceChip
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhPillButton
import uk.co.siland.culvery.core.ui.HhSheetButton
import uk.co.siland.culvery.core.ui.HhSwitch
import uk.co.siland.culvery.core.ui.rememberSingleAction

/** 3a design §4.1. */
internal const val NEEDS_RECONNECTING = "Needs reconnecting"
internal const val SOMETHING_WENT_WRONG = "Something went wrong"

// 4a design §4.5.
internal const val DISCONNECT = "Disconnect"
internal const val SHOW = "Show"
internal const val MASTER = "Master"
internal const val NEW_EVENTS_GO_HERE = "New events go here"
internal const val MAKE_MASTER = "Make master"
internal const val KEEP = "Keep"

private const val TAG = "ReviewCalendars"

/** A row's health in words: "Synced 5 min ago", "Can't reach Google Calendar", "Needs reconnecting" or "Something went wrong". */
internal fun healthWords(row: CalendarRow, nowMillis: Long): String = when (row.health) {
    ConnectionHealth.Ok -> syncedLabel(row.lastSyncMillis, nowMillis).replaceFirstChar { it.uppercase() }
    ConnectionHealth.Unreachable -> "Can't reach ${row.service}"
    ConnectionHealth.NeedsSignIn -> NEEDS_RECONNECTING
    is ConnectionHealth.Error -> SOMETHING_WENT_WRONG
}

/** Which calendar's person chips are open; NUL never appears in a provider id. */
internal fun sourceKey(source: StoredSource): String = "${source.connectionId}\u0000${source.source.id}"

/** The disconnect being confirmed, with the queued changes it would drop. */
internal data class Confirming(val row: CalendarRow, val queued: Int)

/** The wizard's Connect step: the Home screen's Connect card at about its Home size (4a design §3.9). */
@Composable
internal fun ConnectStepHost(connections: CalendarConnections) {
    val connectable by connections.connectable.collectAsState(initial = emptyList())
    val connector = rememberConnector(connections)
    val first = connectable.firstOrNull()
    ConnectStepCard(first?.descriptor?.displayName, onConnect = { first?.let { connector.connect(it.descriptor.id) } })
}

@Composable
internal fun ConnectStepCard(connectService: String?, onConnect: () -> Unit) {
    Box(Modifier.size(CalendarDimens.connectStepWidth, CalendarDimens.connectStepHeight)) { ConnectCalendarCard(connectService, onConnect) }
}

@Composable
internal fun ReviewCalendarsHost(review: CalendarReview, connections: CalendarConnections, clock: WallClock, title: String, offerConnect: Boolean) {
    val list by review.connections.collectAsState(initial = emptyList())
    val people by review.people.collectAsState(initial = listOf(Person.Family))
    val connectable by connections.connectable.collectAsState(initial = emptyList())
    val connector = rememberConnector(connections)
    var picking by remember { mutableStateOf<String?>(null) }
    var confirming by remember { mutableStateOf<Confirming?>(null) }
    val action = rememberSingleAction(Unit) { e -> Log.w(TAG, "A calendar change failed (${e::class.simpleName})") }
    ReviewCalendars(
        title = title,
        connections = list,
        people = people,
        nowMillis = rememberNowMillis(clock),
        busy = action.busy,
        picking = picking,
        confirming = confirming,
        connectable = if (offerConnect) connectable.map { it.descriptor } else emptyList(),
        actions = ReviewActions(
            onConnect = { connector.connect(it.id) },
            onReconnect = connector::reconnect,
            onPick = { key -> picking = if (picking == key) null else key },
            onPerson = { source, person ->
                picking = null
                action.run { review.setPerson(source, person) }
            },
            onShown = { source, shown -> action.run { review.setShown(source, shown) } },
            onMakeMaster = { source -> action.run { review.makeMaster(source) } },
            onDisconnect = { row -> action.run { confirming = Confirming(row, review.queuedChanges(row.connection.id)) } },
            onKeep = { confirming = null },
            onConfirmDisconnect = { row -> action.run { if (review.disconnect(row)) confirming = null } },
        ),
    )
}

/** What Review calendars' taps do; each does nothing unless given, so a test or screenshot passes only what it checks. */
internal class ReviewActions(
    val onConnect: (ProviderDescriptor) -> Unit = {},
    val onReconnect: (Connection) -> Unit = {},
    val onPick: (String) -> Unit = {},
    val onPerson: (StoredSource, Person) -> Unit = { _, _ -> },
    val onShown: (StoredSource, Boolean) -> Unit = { _, _ -> },
    val onMakeMaster: (StoredSource) -> Unit = {},
    val onDisconnect: (CalendarRow) -> Unit = {},
    val onKeep: () -> Unit = {},
    val onConfirmDisconnect: (CalendarRow) -> Unit = {},
)

/**
 * 4a design §4.5: per connection, "{Service} · {account}", its health, Reconnect when needed and Disconnect (confirmed
 * first); per calendar, its name, who it is for (tap for Family and the people), Show, and Master or Make master.
 */
@Composable
internal fun ReviewCalendars(
    title: String,
    connections: List<ReviewConnection>,
    people: List<Person>,
    nowMillis: Long,
    busy: Boolean,
    picking: String?,
    confirming: Confirming?,
    connectable: List<ProviderDescriptor>,
    actions: ReviewActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CalendarDimens.reviewBlockGap), modifier = Modifier.testTag("review_calendars")) {
        Text(title, style = CalendarType.reviewTitle, color = Culvery.colors.ink)
        connections.forEach { connection ->
            ConnectionHeader(connection.row, nowMillis, busy, actions.onReconnect, actions.onDisconnect)
            if (confirming != null && confirming.row.connection.id == connection.row.connection.id) {
                DisconnectConfirmation(confirming, busy, actions.onKeep, actions.onConfirmDisconnect)
            }
            connection.sources.forEach { source ->
                SourceRow(source, people, busy, picking == sourceKey(source), actions.onPick, actions.onPerson, actions.onShown, actions.onMakeMaster)
            }
        }
        connectable.forEach { d -> AddButton("Connect ${d.displayName}", "settings_connect_${d.id}") { actions.onConnect(d) } }
    }
}

@Composable
private fun ConnectionHeader(row: CalendarRow, nowMillis: Long, busy: Boolean, onReconnect: (Connection) -> Unit, onDisconnect: (CalendarRow) -> Unit) {
    val c = Culvery.colors
    val needsReconnect = row.health == ConnectionHealth.NeedsSignIn
    val account = row.connection.config[CONFIG_ACCOUNT]
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.settingsIconGap),
        modifier = Modifier
            .testTag("settings_row_${row.connection.id}")
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalendarDimens.settingsRowRadius))
            .background(c.surf)
            .padding(horizontal = CalendarDimens.settingsRowPaddingH, vertical = CalendarDimens.settingsRowPaddingV),
    ) {
        HhIcon(row.icon, size = CalendarDimens.settingsIcon, tint = c.ink)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CalendarDimens.settingsStatusTop)) {
            Text(
                if (account == null) row.service else "${row.service} · $account",
                style = CalendarType.settingsRowTitle,
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(healthWords(row, nowMillis), style = CalendarType.subtitle, color = if (needsReconnect) c.danger else c.mute, maxLines = 1)
        }
        if (needsReconnect) AddButton("Reconnect", "settings_reconnect", icon = null) { onReconnect(row.connection) }
        HhPillButton(DISCONNECT, { onDisconnect(row) }, Modifier.testTag("review_disconnect_${row.connection.id}"), enabled = !busy)
    }
}

@Composable
private fun SourceRow(
    source: StoredSource,
    people: List<Person>,
    busy: Boolean,
    picking: Boolean,
    onPick: (String) -> Unit,
    onPerson: (StoredSource, Person) -> Unit,
    onShown: (StoredSource, Boolean) -> Unit,
    onMakeMaster: (StoredSource) -> Unit,
) {
    val c = Culvery.colors
    val id = source.source.id
    // A person removed since is Family until HouseholdFollower catches up.
    val person = people.firstOrNull { it.id == source.mapping.person } ?: Person.Family
    Column(
        verticalArrangement = Arrangement.spacedBy(CalendarDimens.reviewItemGap),
        modifier = Modifier
            .testTag("review_source_$id")
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalendarDimens.reviewRowRadius))
            .background(c.surf)
            .padding(horizontal = CalendarDimens.reviewRowPaddingH, vertical = CalendarDimens.reviewRowPaddingV),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CalendarDimens.reviewItemGap)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CalendarDimens.settingsStatusTop)) {
                Text(source.source.name, style = CalendarType.settingsRowTitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (source.isMaster) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CalendarDimens.reviewItemGap)) {
                        Text(
                            MASTER,
                            style = CalendarType.badge,
                            color = c.accent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(CalendarDimens.badgeRadius))
                                .background(c.accentSoft)
                                .padding(horizontal = CalendarDimens.badgePaddingH, vertical = CalendarDimens.badgePaddingV),
                        )
                        Text(NEW_EVENTS_GO_HERE, style = CalendarType.subtitle, color = c.mute)
                    }
                } else if (source.source.writable) {
                    HhPillButton(MAKE_MASTER, { onMakeMaster(source) }, Modifier.testTag("review_make_master_$id"), enabled = !busy)
                }
            }
            HhChoiceChip(person.name, selected = false, tag = "review_person_$id", onClick = { onPick(sourceKey(source)) }, leading = { _ -> Dot(person) })
            Text(SHOW, style = CalendarType.subtitle, color = c.mute)
            // The master is always shown (4a design §3.9).
            HhSwitch(source.mapping.visible, { onShown(source, it) }, tag = "review_show_$id", enabled = !source.isMaster && !busy)
        }
        if (picking) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(ControlTokens.chipGap), verticalArrangement = Arrangement.spacedBy(ControlTokens.chipGap)) {
                people.forEach { p ->
                    HhChoiceChip(
                        p.name,
                        selected = p.id == source.mapping.person,
                        tag = "review_pick_${p.name}",
                        onClick = { onPerson(source, p) },
                        enabled = !busy,
                        selectedColor = Color(p.color),
                        selectedInk = DarkColors.bg,
                        leading = { _ -> Dot(p) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Dot(person: Person) {
    Box(Modifier.size(ControlTokens.chipDot).clip(CircleShape).background(Color(person.color)))
}

@Composable
private fun DisconnectConfirmation(confirming: Confirming, busy: Boolean, onKeep: () -> Unit, onConfirm: (CalendarRow) -> Unit) {
    val c = Culvery.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(CalendarDimens.confirmGap),
        modifier = Modifier
            .testTag("review_confirm")
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalendarDimens.confirmRadius))
            .background(c.dangerSoft)
            .padding(CalendarDimens.confirmPadding),
    ) {
        Text(disconnectQuestion(confirming.row.service, confirming.queued), style = CalendarType.confirmTitle, color = c.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(CalendarDimens.confirmButtonGap), modifier = Modifier.fillMaxWidth()) {
            HhSheetButton(KEEP, ButtonTone.Quiet, enabled = !busy, tag = "review_keep", onClick = onKeep, modifier = Modifier.weight(1f))
            HhSheetButton(
                DISCONNECT, ButtonTone.Destroy, enabled = !busy, tag = "review_confirm_disconnect",
                onClick = { onConfirm(confirming.row) }, modifier = Modifier.weight(1f),
            )
        }
    }
}
