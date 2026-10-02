package uk.co.siland.culvery.core.setup

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.plugin.COULD_NOT_SAVE
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhTextField
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.rememberSingleAction

/** 4a design §3.8: search once the query has two letters and typing has paused for 400 ms. */
internal const val MIN_QUERY = 2
internal const val SEARCH_PAUSE_MS = 400L

private const val TAG = "LocationPane"

internal sealed interface TownResults {
    data object Idle : TownResults

    data class Found(val places: List<PlaceMatch>) : TownResults

    data class NoMatch(val query: String) : TownResults

    data object Failed : TownResults
}

/** Stored with the result's whole line as its name (ruling 17). */
internal fun PlaceMatch.toHome(): HomeLocation = HomeLocation(label, latitude, longitude, timeZoneId)

private fun HomeLocation?.isAt(place: PlaceMatch): Boolean =
    this != null && name == place.label && latitude == place.latitude && longitude == place.longitude

/**
 * 4a design §4.3: the town search and its results, the saved home ticked. A new query cancels the search before it, and
 * with it the request. [showCurrent] (Settings) lists the saved home above the field. [save] stores the chosen town and
 * says whether it did; if it throws, the pane says it couldn't save and keeps the results to try again. A town that
 * saved fills the field with its name and closes the list (one choice).
 */
@Composable
internal fun LocationPane(search: LocationSearch, current: HomeLocation?, showCurrent: Boolean, save: suspend (PlaceMatch) -> Boolean) {
    var query by rememberSaveable { mutableStateOf("") }
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    val results by produceState<TownResults>(TownResults.Idle, query) {
        val q = query.trim()
        if (q.length < MIN_QUERY || query == chosen) {
            value = TownResults.Idle
            return@produceState
        }
        delay(SEARCH_PAUSE_MS)
        value = try {
            search.search(q).let { if (it.isEmpty()) TownResults.NoMatch(q) else TownResults.Found(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: LocationSearchException) {
            // The message can hold a status code (ruling 4): the copy is fixed and nothing is logged.
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
    LocationContent(query, onQuery, results, current, showCurrent, action.busy, saveFailed) { place ->
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
    onChoose: (PlaceMatch) -> Unit,
) {
    val c = Culvery.colors
    Column(verticalArrangement = Arrangement.spacedBy(SetupDimens.blockGap)) {
        if (showCurrent && current != null) PlaceRow(current.name, ticked = true, tag = "location_current", onClick = null)
        HhTextField(query, onQuery, TOWN_OR_CITY, "location_query", capitalization = KeyboardCapitalization.Words)
        when (results) {
            is TownResults.Found -> Column(verticalArrangement = Arrangement.spacedBy(SetupDimens.rowGap)) {
                results.places.forEachIndexed { i, place ->
                    PlaceRow(place.label, ticked = current.isAt(place), tag = "place_$i", onClick = if (busy) null else ({ onChoose(place) }))
                }
            }
            is TownResults.NoMatch ->
                Text(noTownsMatch(results.query), style = SetupType.line, color = c.mute, modifier = Modifier.testTag("location_none"))
            TownResults.Failed ->
                Text(COULD_NOT_SEARCH, style = SetupType.line, color = c.danger, modifier = Modifier.testTag("location_failed"))
            TownResults.Idle -> Unit
        }
        if (saveFailed) Text(COULD_NOT_SAVE, style = SetupType.message, color = c.danger, modifier = Modifier.testTag("location_save_failed"))
        Text(USED_FOR, style = SetupType.secondary, color = c.mute)
    }
}

@Composable
private fun PlaceRow(label: String, ticked: Boolean, tag: String, onClick: (() -> Unit)?) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SetupDimens.rowDotGap),
        modifier = Modifier
            .testTag(tag)
            .semantics(mergeDescendants = true) { selected = ticked }
            .fillMaxWidth()
            .clip(RoundedCornerShape(SetupDimens.rowRadius))
            .background(c.surf)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = SetupDimens.rowPaddingH, vertical = SetupDimens.rowPaddingV),
    ) {
        Text(label, style = SetupType.rowTitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (ticked) HhIcon(Icons.CHECK, size = SetupDimens.rowIcon, tint = c.accent)
    }
}
