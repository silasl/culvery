package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import java.time.LocalDate
import kotlinx.coroutines.delay
import uk.co.siland.culvery.capability.calendar.MAX_WEEKS_AHEAD
import uk.co.siland.culvery.capability.calendar.isStaleAt
import uk.co.siland.culvery.capability.calendar.weekLine
import uk.co.siland.culvery.capability.calendar.weekTitle
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhPageHeader
import uk.co.siland.culvery.core.ui.HhRoundButton
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.hhClickable

/**
 * The week's title block (9 §4.7): the title with ‹ › in one pill, This week past the current week and Add event beside
 * it; under it the sync line with Update now, and the reconnect chip. While the week is out of date, Update now runs
 * [onUpdateNow] and the line says "Updating…" until the sync lands or [UPDATING_SHOWN_MS] passes (4e).
 */
@Composable
internal fun WeekHeader(
    state: WeekViewState,
    onWeeksAhead: (Int) -> Unit,
    onAdd: ((LocalDate) -> Unit)?,
    onReconnect: () -> Unit,
    onUpdateNow: () -> Unit,
) {
    val c = Culvery.colors
    // Reset by a sync landing (a new age), or after a while if it failed and the age stayed the same.
    var updating by remember(state.sync.lastSyncMillis) { mutableStateOf(false) }
    if (updating) LaunchedEffect(Unit) {
        delay(UPDATING_SHOWN_MS)
        updating = false
    }
    // A failed pass shows as soon as it happens, not only once the week is out of date (5 §A.3).
    val failing = state.sync.isStaleAt(state.nowMillis) || state.sync.problem != null
    HhPageHeader(
        weekTitle(state.weeksAhead),
        subtitle = null,
        titleModifier = Modifier.testTag("week_title").semantics { liveRegion = LiveRegionMode.Polite },
        under = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CalendarDimens.staleIconGap),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (failing && !updating) {
                    HhIcon(Icons.SYNC_PROBLEM, size = CalendarDimens.staleIcon, tint = c.danger, modifier = Modifier.testTag("week_out_of_date"))
                }
                Text(
                    if (updating) UPDATING else weekLine(state.sync, state.nowMillis),
                    style = CalendarType.subtitle,
                    color = if (failing && !updating) c.danger else c.mute,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).testTag("week_subtitle").semantics { liveRegion = LiveRegionMode.Polite },
                )
                if (failing && !updating) {
                    HhButton(
                        UPDATE_NOW,
                        {
                            updating = true
                            onUpdateNow()
                        },
                        Modifier.testTag("week_update_now"),
                        on = ButtonSurface.Bg,
                    )
                }
            }
            if (state.sync.needsSignIn.isNotEmpty()) {
                Spacer(Modifier.height(CalendarDimens.reconnectTop))
                ReconnectChip(reconnectLabel(state.sync.needsSignIn), onClick = onReconnect)
            }
        },
    ) {
        NavPill(state.weeksAhead, onWeeksAhead)
        if (state.weeksAhead > 0) {
            HhButton("This week", { onWeeksAhead(0) }, Modifier.testTag("week_this_week"), on = ButtonSurface.Bg)
        }
        if (onAdd != null) {
            HhButton(
                "Add event", { onAdd(state.week.start) }, Modifier.testTag("week_add_event"),
                tone = ButtonTone.Primary, icon = Icons.ADD, on = ButtonSurface.Bg,
            )
        }
    }
}

/** ‹ and › on one `surf2` pill; an arrow at its end is the shared disabled button. */
@Composable
private fun NavPill(weeksAhead: Int, onWeeksAhead: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .testTag("week_nav_pill")
            .clip(RoundedCornerShape(percent = 50))
            .background(Culvery.colors.surf2),
    ) {
        HhRoundButton(
            Icons.CHEVRON_LEFT, "Earlier week", { onWeeksAhead(weeksAhead - 1) }, Modifier.testTag("week_earlier"),
            enabled = weeksAhead > 0, on = ButtonSurface.Surf,
        )
        HhRoundButton(
            Icons.CHEVRON_RIGHT, "Later week", { onWeeksAhead(weeksAhead + 1) }, Modifier.testTag("week_later"),
            enabled = weeksAhead < MAX_WEEKS_AHEAD, on = ButtonSurface.Surf,
        )
    }
}

/** One chip for every connection that needs signing in again: a `dangerSoft` pill, 48 dp, radius 24. */
@Composable
private fun ReconnectChip(label: String, onClick: () -> Unit) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.reconnectIconGap),
        modifier = Modifier
            .height(CalendarDimens.touchTarget)
            .clip(RoundedCornerShape(CalendarDimens.pillRadius))
            .background(c.dangerSoft)
            .hhClickable(onClick, label = RECONNECT_ACTION)
            .padding(horizontal = CalendarDimens.pillPaddingH),
    ) {
        HhIcon(Icons.SYNC_PROBLEM, size = CalendarDimens.reconnectIcon, tint = c.danger)
        Text(
            label,
            style = CalendarType.pill,
            color = c.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** How long "Updating…" stands in for an out-of-date line before a failed sync shows it again. */
internal const val UPDATING_SHOWN_MS = 30_000L
private const val UPDATE_NOW = "Update now"
private const val UPDATING = "Updating…"
