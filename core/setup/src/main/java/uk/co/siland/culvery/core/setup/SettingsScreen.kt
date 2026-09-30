package uk.co.siland.culvery.core.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhPillButton

/**
 * Settings (4a design §4.6): the pages' titles on the left with Close, the chosen page on the right, the first chosen on
 * open. Taps on empty space stop here rather than reaching the shell underneath. The touches that keep it open are
 * counted on the shell's layers (Task 12), so its sheets and PIN pads count too.
 */
@Composable
fun SettingsScreen(pages: List<SettingsPage>, onClose: () -> Unit) {
    val c = Culvery.colors
    var chosen by rememberSaveable { mutableStateOf(pages.firstOrNull()?.id) }
    val page = pages.firstOrNull { it.id == chosen } ?: pages.firstOrNull()
    Row(
        Modifier
            .fillMaxSize()
            .testTag("settings")
            .background(c.bg)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(
            Modifier
                .width(SetupDimens.settingsList)
                .fillMaxHeight()
                .background(c.surf)
                .padding(horizontal = SetupDimens.settingsListPaddingH, vertical = SetupDimens.settingsListPaddingV),
        ) {
            Text(SETTINGS, style = SetupType.title, color = c.ink)
            Spacer(Modifier.height(SetupDimens.settingsTitleGap))
            Column(verticalArrangement = Arrangement.spacedBy(SetupDimens.settingsItemGap)) {
                pages.forEach { p -> PageItem(p.title, chosen = p.id == page?.id, tag = "settings_page_${p.id}") { chosen = p.id } }
            }
            Spacer(Modifier.weight(1f))
            HhPillButton(CLOSE, onClose, Modifier.testTag("settings_close"))
        }
        // Each page scrolls from its top.
        key(page?.id) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = SetupDimens.pagePaddingH, vertical = SetupDimens.pagePaddingV),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(SetupDimens.blockGap),
                    modifier = Modifier.widthIn(max = SetupDimens.wizardColumn).testTag("settings_pane"),
                ) {
                    page?.Content()
                }
            }
        }
    }
}

@Composable
private fun PageItem(title: String, chosen: Boolean, tag: String, onClick: () -> Unit) {
    val c = Culvery.colors
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .testTag(tag)
            .semantics { selected = chosen }
            .fillMaxWidth()
            .height(SetupDimens.settingsItemHeight)
            .clip(RoundedCornerShape(SetupDimens.settingsItemRadius))
            .then(if (chosen) Modifier.background(c.accentSoft) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = SetupDimens.settingsItemPaddingH),
    ) {
        Text(title, style = SetupType.rowTitle, color = if (chosen) c.accent else c.ink, maxLines = 1)
    }
}
