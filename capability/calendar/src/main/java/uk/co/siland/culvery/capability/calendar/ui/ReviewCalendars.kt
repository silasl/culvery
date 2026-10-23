package uk.co.siland.culvery.capability.calendar.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.siland.culvery.capability.calendar.CONFIG_ACCOUNT
import uk.co.siland.culvery.capability.calendar.CalendarConnections
import uk.co.siland.culvery.capability.calendar.CalendarProvider
import uk.co.siland.culvery.capability.calendar.CalendarReview
import uk.co.siland.culvery.capability.calendar.CalendarRow
import uk.co.siland.culvery.capability.calendar.ReviewConnection
import uk.co.siland.culvery.capability.calendar.StoredSource
import uk.co.siland.culvery.capability.calendar.disconnectQuestion
import uk.co.siland.culvery.capability.calendar.updatedLabel
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth
import uk.co.siland.culvery.core.plugin.HouseholdClock
import uk.co.siland.culvery.core.plugin.KEEP
import uk.co.siland.culvery.core.plugin.ProviderDescriptor
import uk.co.siland.culvery.core.plugin.rememberNowMillis
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.ControlTokens
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhChoiceChip
import uk.co.siland.culvery.core.ui.HhConfirmCard
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhListRow
import uk.co.siland.culvery.core.ui.HhPageTitle
import uk.co.siland.culvery.core.ui.HhSwitch
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.ListRowTokens
import uk.co.siland.culvery.core.ui.PersonChipInk
import uk.co.siland.culvery.core.ui.hhClickable
import uk.co.siland.culvery.core.ui.hhToggleable
import uk.co.siland.culvery.core.ui.listRowCard
import uk.co.siland.culvery.core.ui.rememberSingleAction
import uk.co.siland.culvery.core.ui.shownNotSpoken

/** 3a design §4.1: a status ("Needs reconnecting") or a sentence that says what to do. */
internal const val NEEDS_RECONNECTING = "Needs reconnecting"
internal const val STOPPED_UPDATING = "Stopped updating — disconnect and connect again."

// 4a design §4.5; 4d-2 Decision 2a; 9 §4.12.
internal const val DISCONNECT = "Disconnect"
internal const val SHOW = "Show"
internal const val ALWAYS_SHOWN_LABEL = "Always shown"
internal const val MAIN_NEW_EVENTS_GO_HERE = "Main · new events go here"
internal const val USE_FOR_NEW_EVENTS = "Use for new events"
internal const val HIDE_THIS_CALENDAR = "Hide this calendar"

/** The read problem's sentence (9 §4.12); screen text only, never a toast or a log. */
internal fun cantRead(account: String?, main: Boolean): String {
    val sentence = "Can’t read this calendar. Check it’s still shared with ${account ?: "this account"}."
    return if (main) "$sentence To hide it, choose another main calendar first." else sentence
}

private const val TAG = "ReviewCalendars"

/** A row's health in words: "Updated 5 minutes ago", "Can’t reach Google Calendar — check the tablet’s Wi-Fi.", "Needs reconnecting" or what to do when it stopped. */
internal fun healthWords(row: CalendarRow, nowMillis: Long): String = when (row.health) {
    ConnectionHealth.Ok -> updatedLabel(row.lastSyncMillis, nowMillis)
    ConnectionHealth.Unreachable -> "Can’t reach ${row.service} — check the tablet’s Wi-Fi."
    ConnectionHealth.Offline -> "The tablet is offline — check the Wi-Fi."
    ConnectionHealth.NeedsSignIn -> NEEDS_RECONNECTING
    is ConnectionHealth.Error -> STOPPED_UPDATING
}

/** "{Service} · {account}", or the service alone when the connection names no account. */
internal fun serviceAndAccount(row: CalendarRow): String = row.connection.config[CONFIG_ACCOUNT]?.let { "${row.service} · $it" } ?: row.service

/** Which calendar's person chips are open; NUL never appears in a provider id. */
internal fun sourceKey(source: StoredSource): String = "${source.connectionId}\u0000${source.source.id}"

/** The disconnect being confirmed, with the queued changes it would drop. */
internal data class Confirming(val row: CalendarRow, val queued: Int)

/**
 * The wizard's Connect step: the Home screen's Connect card at about its Home size (4a design §3.9), or, once a calendar
 * is connected, what is connected.
 */
@Composable
internal fun ConnectStepHost(connections: CalendarConnections) {
    // Null until the store answers, so neither the Connect card nor its no-services line flashes up first.
    val rows = connections.rows.collectAsState<List<CalendarRow>, List<CalendarRow>?>(initial = null).value
    val connectable = connections.connectable.collectAsState<List<CalendarProvider>, List<CalendarProvider>?>(initial = null).value
    val connector = rememberConnector(connections)
    when {
        rows == null || connectable == null -> Unit
        rows.isNotEmpty() -> ConnectedStepCard(rows)
        else -> {
            val first = connectable.firstOrNull()
            ConnectStepCard(first?.descriptor?.displayName, onConnect = { first?.let { connector.connect(it.descriptor.id) } })
        }
    }
}

@Composable
internal fun ConnectStepCard(connectService: String?, onConnect: () -> Unit) {
    Box(Modifier.size(CalendarDimens.connectStepWidth, CalendarDimens.connectStepHeight)) { ConnectCalendarCard(connectService, onConnect) }
}

@Composable
internal fun ConnectedStepCard(rows: List<CalendarRow>) {
    Box(Modifier.size(CalendarDimens.connectStepWidth, CalendarDimens.connectStepHeight)) { ConnectedCalendarCard(rows) }
}

@Composable
internal fun ReviewCalendarsHost(review: CalendarReview, connections: CalendarConnections, clock: HouseholdClock, title: String, offerConnect: Boolean, line: String? = null) {
    // Null until the store answers, so the page draws nothing rather than "Connect…" first.
    val list by review.connections.collectAsStateWithLifecycle<List<ReviewConnection>?>(null)
    val people by review.people.collectAsStateWithLifecycle(listOf(Person.Family))
    val connectable by connections.connectable.collectAsStateWithLifecycle<List<CalendarProvider>?>(null)
    val descriptors = remember(connectable, offerConnect) { if (offerConnect) connectable.orEmpty().map { it.descriptor } else emptyList() }
    val connector = rememberConnector(connections)
    var picking by remember { mutableStateOf<String?>(null) }
    var confirming by remember { mutableStateOf<Confirming?>(null) }
    val action = rememberSingleAction(Unit) { e -> Log.w(TAG, "A calendar change failed (${e::class.simpleName})") }
    ReviewCalendars(
        title = title,
        connections = if (connectable == null) null else list,
        people = people,
        nowMillis = rememberNowMillis(clock),
        busy = action.busy,
        picking = picking,
        confirming = confirming,
        connectable = descriptors,
        line = line,
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
 * 9 §4.12: per connection, "{Service} · {account}", its health, Reconnect when needed and Disconnect (confirmed first);
 * per calendar, a card: its name and, on the main one, the badge; who it is for (a dropdown of Family and the people);
 * Show; Use for new events; and a read problem in a strip at the foot. Draws nothing while [connections] is null.
 */
@Composable
internal fun ReviewCalendars(
    title: String,
    connections: List<ReviewConnection>?,
    people: List<Person>,
    nowMillis: Long,
    busy: Boolean,
    picking: String?,
    confirming: Confirming?,
    connectable: List<ProviderDescriptor>,
    actions: ReviewActions,
    line: String? = null,
) {
    if (connections == null) return
    Column(verticalArrangement = Arrangement.spacedBy(CalendarDimens.reviewBlockGap), modifier = Modifier.testTag("review_calendars")) {
        HhPageTitle(title)
        if (line != null) Text(line, style = CalendarType.subtitle, color = Culvery.colors.mute)
        if (connections.isEmpty()) {
            Text(
                "Connect your family’s Google Calendar to see it on Home.",
                style = CalendarType.subtitle,
                color = Culvery.colors.mute,
                modifier = Modifier.testTag("review_none"),
            )
        }
        connections.forEach { connection ->
            ConnectionHeader(connection.row, nowMillis, busy, actions.onReconnect, actions.onDisconnect)
            if (confirming != null && confirming.row.connection.id == connection.row.connection.id) {
                HhConfirmCard(
                    tag = "review_confirm",
                    title = disconnectQuestion(confirming.row.service, confirming.queued),
                    keep = KEEP, keepTag = "review_keep",
                    confirm = DISCONNECT, confirmTag = "review_confirm_disconnect",
                    busy = busy, onKeep = actions.onKeep, onConfirm = { actions.onConfirmDisconnect(confirming.row) },
                )
            }
            connection.sources.forEach { source ->
                CalendarRowCard(source, connection.row, people, busy, picking == sourceKey(source), actions.onPick, actions.onPerson, actions.onShown, actions.onMakeMaster)
            }
        }
        connectable.forEach { d ->
            HhButton(
                "Connect ${d.displayName}", { actions.onConnect(d) }, Modifier.testTag("settings_connect_${d.id}"),
                tone = ButtonTone.Primary, icon = Icons.ADD, enabled = !busy, on = ButtonSurface.Bg,
            )
        }
    }
}

@Composable
private fun ConnectionHeader(row: CalendarRow, nowMillis: Long, busy: Boolean, onReconnect: (Connection) -> Unit, onDisconnect: (CalendarRow) -> Unit) {
    val c = Culvery.colors
    val needsReconnect = row.health == ConnectionHealth.NeedsSignIn
    HhListRow(
        title = serviceAndAccount(row),
        modifier = Modifier.testTag("settings_row_${row.connection.id}"),
        leading = { HhIcon(row.icon, size = CalendarDimens.settingsIcon, tint = c.ink) },
        subtitle = healthWords(row, nowMillis),
        minHeight = CalendarDimens.accountRowMinHeight,
        subtitleColor = if (needsReconnect) c.danger else c.mute,
        trailing = {
            if (needsReconnect) {
                HhButton(
                    "Reconnect", { onReconnect(row.connection) }, Modifier.testTag("settings_reconnect"),
                    tone = ButtonTone.Primary, enabled = !busy, on = ButtonSurface.Surf, spokenName = forAccount("Reconnect", row),
                )
            }
            HhButton(
                DISCONNECT, { onDisconnect(row) }, Modifier.testTag("review_disconnect_${row.connection.id}"),
                enabled = !busy, on = ButtonSurface.Surf, spokenName = forAccount(DISCONNECT, row),
            )
        },
    )
}

@Composable
private fun CalendarRowCard(
    source: StoredSource,
    connection: CalendarRow,
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
    val main = source.isMaster
    // A person removed since is Family until HouseholdFollower catches up.
    val person = people.firstOrNull { it.id == source.mapping.person } ?: Person.Family
    val offersMakeMain = !main && source.source.writable
    Column(
        Modifier
            .testTag("review_source_$id")
            .fillMaxWidth()
            .listRowCard(c),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .testTag("review_line_$id")
                .fillMaxWidth()
                .heightIn(min = ListRowTokens.minHeight)
                .padding(start = ListRowTokens.paddingStart, end = ListRowTokens.paddingEnd),
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    source.source.name,
                    style = ListRowTokens.titleStyle,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (main) {
                    Spacer(Modifier.width(CalendarDimens.mainBadgeGap))
                    MainBadge(id)
                }
            }
            Spacer(Modifier.width(CalendarDimens.reviewItemGap))
            PersonPill(source, person, picking, onPick)
            Spacer(Modifier.width(CalendarDimens.reviewItemGap))
            ShowSwitch(source, busy, onShown)
        }
        if (picking || offersMakeMain) {
            Column(
                verticalArrangement = Arrangement.spacedBy(CalendarDimens.belowGap),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = CalendarDimens.belowPaddingStart, end = CalendarDimens.belowPaddingEnd, bottom = CalendarDimens.belowPaddingBottom),
            ) {
                if (picking) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(ControlTokens.chipGap),
                        verticalArrangement = Arrangement.spacedBy(ControlTokens.chipGap),
                        modifier = Modifier.selectableGroup(),
                    ) {
                        people.forEach { p ->
                            val selected = p.id == source.mapping.person
                            HhChoiceChip(
                                p.name,
                                selected = selected,
                                tag = "review_pick_${p.name}",
                                onClick = { onPerson(source, p) },
                                enabled = !busy,
                                selectedColor = Color(p.color),
                                selectedInk = PersonChipInk,
                                leading = { ink -> if (selected) HhIcon(Icons.CHECK, size = ControlTokens.chipIcon, tint = ink) else Dot(p, ControlTokens.chipDot) },
                            )
                        }
                    }
                }
                if (offersMakeMain) {
                    HhButton(
                        USE_FOR_NEW_EVENTS, { onMakeMaster(source) }, Modifier.testTag("review_make_master_$id"),
                        enabled = !busy, on = ButtonSurface.Surf, spokenName = withSubject(USE_FOR_NEW_EVENTS, source.source.name),
                    )
                }
            }
        }
        // A calendar the service hid on its tick has no problem to show, and no Hide to offer.
        if (source.readProblem != null && source.mapping.visible) {
            ReadProblemStrip(source, connection.connection.config[CONFIG_ACCOUNT], busy, onShown)
        }
    }
}

@Composable
private fun MainBadge(id: String) {
    val c = Culvery.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .testTag("review_main_$id")
            .heightIn(min = CalendarDimens.badgeHeight)
            .clip(RoundedCornerShape(CalendarDimens.badgeRadius))
            .background(c.accentSoft)
            .padding(horizontal = CalendarDimens.badgePaddingH),
    ) {
        Text(MAIN_NEW_EVENTS_GO_HERE, style = CalendarType.badge, color = c.accent)
    }
}

/** 37 dp drawn in a 48 dp tap: the dropdown that opens the person chips. */
@Composable
private fun PersonPill(source: StoredSource, person: Person, picking: Boolean, onPick: (String) -> Unit) {
    val c = Culvery.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .testTag("review_person_${source.source.id}")
            .heightIn(min = CalendarDimens.reviewTapHeight)
            .semantics { stateDescription = if (picking) EXPANDED else COLLAPSED }
            .hhClickable({ onPick(sourceKey(source)) }, label = changeWhoFor(source.source.name), role = Role.Button),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CalendarDimens.pillGap),
            modifier = Modifier
                .heightIn(min = CalendarDimens.pillHeight)
                .clip(RoundedCornerShape(percent = 50))
                .background(c.surf2)
                .padding(horizontal = CalendarDimens.personPillPaddingH),
        ) {
            Dot(person, CalendarDimens.pillDot)
            Text(person.name, style = CalendarType.personPill, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            HhIcon(Icons.EXPAND_MORE, size = CalendarDimens.pillChevron, tint = c.mute)
        }
    }
}

/** Text and switch are one switch: named for the calendar, with why the main one can't be hidden (4a design §3.9). */
@Composable
private fun ShowSwitch(source: StoredSource, busy: Boolean, onShown: (StoredSource, Boolean) -> Unit) {
    val main = source.isMaster
    val toggles = !main && !busy
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.showLabelGap, Alignment.End),
        modifier = Modifier
            .testTag("review_show_${source.source.id}")
            .widthIn(min = CalendarDimens.showGroupMinWidth)
            .heightIn(min = CalendarDimens.reviewTapHeight)
            .semantics {
                contentDescription = "$SHOW ${source.source.name}"
                if (main) stateDescription = ALWAYS_SHOWN
            }
            .hhToggleable(source.mapping.visible, enabled = toggles) { onShown(source, it) },
    ) {
        Text(if (main) ALWAYS_SHOWN_LABEL else SHOW, style = CalendarType.reviewSmall, color = Culvery.colors.mute, modifier = Modifier.shownNotSpoken())
        HhSwitch(source.mapping.visible, tag = "review_show_switch_${source.source.id}", enabled = toggles)
    }
}

@Composable
private fun Dot(person: Person, size: Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(Color(person.color)))
}

/** One calendar that can't be read, full width at the card's foot; Hide it, or, for the main one, choose another first (9 §4.12). */
@Composable
private fun ReadProblemStrip(source: StoredSource, account: String?, busy: Boolean, onShown: (StoredSource, Boolean) -> Unit) {
    val c = Culvery.colors
    val id = source.source.id
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.stripGap),
        modifier = Modifier
            .testTag("review_read_problem_$id")
            .fillMaxWidth()
            .background(c.dangerSoft)
            .padding(
                start = CalendarDimens.stripPaddingStart, end = CalendarDimens.stripPaddingEnd,
                top = CalendarDimens.stripPaddingV, bottom = CalendarDimens.stripPaddingV,
            ),
    ) {
        HhIcon(Icons.SYNC_PROBLEM, size = CalendarDimens.stripIcon, tint = c.danger)
        Text(cantRead(account, source.isMaster), style = CalendarType.reviewSmall, color = c.ink, modifier = Modifier.weight(1f))
        if (!source.isMaster) {
            HhButton(
                HIDE_THIS_CALENDAR, { onShown(source, false) }, Modifier.testTag("review_hide_$id"),
                enabled = !busy, on = ButtonSurface.DangerSoft, spokenName = withSubject(HIDE_THIS_CALENDAR, source.source.name),
            )
        }
    }
}
