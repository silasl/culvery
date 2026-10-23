package uk.co.siland.culvery.capability.music.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
import uk.co.siland.culvery.capability.music.FAVOURITES_SHEET_TITLE
import uk.co.siland.culvery.capability.music.Favourite
import uk.co.siland.culvery.capability.music.FavouritesLoad
import uk.co.siland.culvery.capability.music.GETTING_FAVOURITES
import uk.co.siland.culvery.capability.music.MusicActions
import uk.co.siland.culvery.capability.music.NO_FAVOURITES
import uk.co.siland.culvery.capability.music.PLAYLISTS
import uk.co.siland.culvery.capability.music.SpeakerGroup
import uk.co.siland.culvery.capability.music.playInAction
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhCloseButton
import uk.co.siland.culvery.core.ui.HhListRow
import uk.co.siland.culvery.core.ui.HhMessageLine
import uk.co.siland.culvery.core.ui.HhSectionLabel
import uk.co.siland.culvery.core.ui.HhSheet
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.ListRowTokens

/**
 * The Favourites and playlists sheet (6 §5.3): the household's Sonos Favourites, then its playlists, by name, with art; a tap
 * plays one in [groupName]'s group. [load] is null while they are read; [line] says a Favourite couldn't play here (6 §6).
 */
@Composable
fun FavouritesSheet(groupName: String, load: FavouritesLoad?, line: String?, onPick: (Favourite) -> Unit, onClose: () -> Unit) {
    val c = Culvery.colors
    HhSheet(
        padding = PaddingValues(horizontal = MusicDimens.sheetPaddingH, vertical = MusicDimens.sheetPaddingV),
        modifier = Modifier.testTag("favourites_sheet").semantics { paneTitle = FAVOURITES_SHEET_TITLE },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(FAVOURITES_SHEET_TITLE, style = HhType.sectionTitle, color = c.ink, modifier = Modifier.semantics { heading() })
                Text(groupName, style = HhType.body, color = c.mute)
            }
            HhCloseButton(onClose)
        }
        HhMessageLine(line, HhType.body, c.danger, "favourites_line")
        when (load) {
            null -> Text(GETTING_FAVOURITES, style = HhType.body, color = c.mute)
            is FavouritesLoad.Failed -> MusicStatusLine(load.line)
            is FavouritesLoad.Ready -> if (load.favourites.isEmpty() && load.playlists.isEmpty()) {
                Text(NO_FAVOURITES, style = HhType.body, color = c.mute, modifier = Modifier.testTag("favourites_empty"))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(ListRowTokens.belowGap), modifier = Modifier.weight(1f)) {
                    items(load.favourites, key = { it.id }) { PickRow(it, "favourite", groupName, onPick) }
                    if (load.playlists.isNotEmpty()) {
                        item { HhSectionLabel(PLAYLISTS) }
                        items(load.playlists, key = { it.id }) { PickRow(it, "playlist", groupName, onPick) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickRow(favourite: Favourite, tag: String, groupName: String, onPick: (Favourite) -> Unit) {
    HhListRow(
        title = favourite.name,
        modifier = Modifier.testTag("${tag}_${favourite.id}"),
        leading = { ArtImage(favourite.artUrl, MusicDimens.favouriteArt) },
        onClick = { onPick(favourite) },
        clickLabel = playInAction(groupName),
    )
}

/** Reads the Favourites as it opens (6 §4.5); a tap plays and closes, or says why and stays. */
@Composable
internal fun FavouritesSheetHost(group: SpeakerGroup, actions: MusicActions, onClose: () -> Unit) {
    var load by remember { mutableStateOf<FavouritesLoad?>(null) }
    var line by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(group.id) { load = actions.favourites() }
    FavouritesSheet(
        group.name, load, line,
        onPick = { favourite ->
            scope.launch {
                line = null
                val failed = actions.playFavourite(group, favourite)
                if (failed == null) onClose() else line = failed
            }
        },
        onClose = onClose,
    )
}
