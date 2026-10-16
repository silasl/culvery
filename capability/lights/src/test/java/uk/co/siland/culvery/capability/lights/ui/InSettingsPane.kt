package uk.co.siland.culvery.capability.lights.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Hosts a page as SettingsScreen does: inside a vertical scroll, in a column 720 dp wide. Add no scroll of the page's own. */
@Composable
internal fun InSettingsPane(page: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 40.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.widthIn(max = 720.dp)) { page() }
    }
}
