package uk.co.siland.culvery.core.setup

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhListRow
import uk.co.siland.culvery.core.ui.HhMessageLine
import uk.co.siland.culvery.core.ui.HhTextField
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.ListRowTokens
import uk.co.siland.culvery.core.ui.rememberSingleAction

/** 4a design §3.8: search once the query has two letters and typing has paused for 400 ms. */
internal const val MIN_QUERY = 2
internal const val SEARCH_PAUSE_MS = 400L

private const val TAG = "LocationPane"

internal sealed interface TownResults {
    data object Idle : TownResults

    data object Searching : TownResults

    data class Found(val places: List<PlaceMatch>) : TownResults

    data class NoMatch(val query: String) : TownResults

    data object Failed : TownResults
}

/** Stored with the result's whole line as its name. */
internal fun PlaceMatch.toHome(): HomeLocation = HomeLocation(label, latitude, longitude, timeZoneId)

private fun HomeLocation?.isAt(place: PlaceMatch): Boolean =
    this != null && name == place.label && latitude == place.latitude && longitude == place.longitude

/**
 * 4a design §4.3: the town search and its results, the saved home ticked. A new query cancels the search before it, and
 * with it the request. It says "Searching…" through the pause and the request, and a failed search offers Try again.
 * [showCurrent] (Settings) lists the saved home above the field. [save] stores the chosen town and says whether it did;
 * if it throws, the pane says it couldn't save and keeps the results to try again. A town that saved fills the field
 * with its name and closes the list (one choice).
 */
@Composable
internal fun LocationPane(search: LocationSearch, current: HomeLocation?, showCurrent: Boolean, save: suspend (PlaceMatch) -> Boolean) {
    var query by rememberSaveable { mutableStateOf("") }
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    var retries by rememberSaveable { mutableStateOf(0) }
    val results by produceState<TownResults>(TownResults.Idle, query, retries) {
        val q = query.trim()
        if (q.length < MIN_QUERY || query == chosen) {
            value = TownResults.Idle
            return@produceState
        }
        // Shown through the pause and the request: something is happening.
        value = TownResults.Searching
        delay(SEARCH_PAUSE_MS)
        value = try {
            search.search(q).let { if (it.isEmpty()) TownResults.NoMatch(q) else TownResults.Found(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: LocationSearchException) {
            // The message can hold a status code, so the copy is fixed and nothing is logged.
            TownResults.Failed
        } catch (e: Exception) {
            Log.w(TAG, "Town search failed (${e::class.simpleName})")
            TownResults.Failed
        }
    }
    var saveFailed by remember { mutableStateOf(false) }
    val action = rememberSingleAction(Unit) { e ->
        Log.w(TAG, "Couldn't save the home (${e::class.simpleName})")
        saveFailed = true
    }
    val onQuery = { q: String ->
        query = q
        saveFailed = false
    }
    LocationContent(query, onQuery, results, current, showCurrent, action.busy, saveFailed, onRetry = { retries++ }) { place ->
        saveFailed = false
        action.run {
            if (save(place)) {
                chosen = place.label
                query = place.label
            }
        }
    }
}

@Composable
internal fun LocationContent(
    query: String,
    onQuery: (String) -> Unit,
    results: TownResults,
    current: HomeLocation?,
    showCurrent: Boolean,
    busy: Boolean,
    saveFailed: Boolean = false,
    onRetry: () -> Unit = {},
    onChoose: (PlaceMatch) -> Unit,
) {
    val c = Culvery.colors
    val focusManager = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(SetupDimens.blockGap)) {
        if (showCurrent && current != null) PlaceRow(current.name, ticked = true, tag = "location_current", onClick = null)
        // The line takes room only while it has text; field and line are one block so the empty line adds no gap.
        val status = statusLine(results, saveFailed)
        Column {
            HhTextField(query, onQuery, TOWN_OR_CITY, TOWN_OR_CITY, "location_query", capitalization = KeyboardCapitalization.Words)
            HhMessageLine(
                status.text,
                if (status.isSave) SetupType.message else SetupType.line,
                if (status.isError) c.danger else c.mute,
                "location_status",
                modifier = if (status.text != null) Modifier.padding(top = SetupDimens.blockGap) else Modifier.height(0.dp),
                spoken = status.spoken,
            )
        }
        if (results is TownResults.Failed && !saveFailed) {
            HhButton(TRY_AGAIN, onRetry, Modifier.testTag("location_retry"), on = ButtonSurface.Bg)
        }
        if (results is TownResults.Found) {
            Column(verticalArrangement = Arrangement.spacedBy(SetupDimens.rowGap)) {
                results.places.forEachIndexed { i, place ->
                    PlaceRow(place.label, ticked = current.isAt(place), tag = "place_$i", onClick = if (busy) null else ({
                        // Choosing ends the typing, so the keyboard goes too.
                        focusManager.clearFocus()
                        onChoose(place)
                    }))
                }
            }
        }
        Text(USED_FOR, style = SetupType.secondary, color = c.mute)
    }
}

private class StatusLine(val text: String?, val spoken: String? = text, val isError: Boolean = false, val isSave: Boolean = false)

/** The one line under the field: a failed save first, then what the search found. */
private fun statusLine(results: TownResults, saveFailed: Boolean): StatusLine = when {
    saveFailed -> StatusLine(HOME_NOT_SAVED, isError = true, isSave = true)
    results is TownResults.NoMatch -> StatusLine(noTownsMatch(results.query))
    results is TownResults.Failed -> StatusLine(COULD_NOT_SEARCH, isError = true)
    results is TownResults.Searching -> StatusLine(SEARCHING)
    results is TownResults.Found -> StatusLine(null, spoken = townsFound(results.places.size))
    else -> StatusLine(null)
}

@Composable
private fun PlaceRow(label: String, ticked: Boolean, tag: String, onClick: (() -> Unit)?) {
    val tick: (@Composable RowScope.() -> Unit)? = if (ticked) ({ HhIcon(Icons.CHECK, size = ListRowTokens.icon, tint = Culvery.colors.accent) }) else null
    HhListRow(
        title = label,
        modifier = Modifier.testTag(tag).semantics(mergeDescendants = true) { selected = ticked },
        trailing = tick,
        onClick = onClick,
        clickLabel = USE_AS_HOME,
    )
}
