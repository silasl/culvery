package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import uk.co.siland.culvery.core.plugin.HomePlacement
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Culvery

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")
private val DATE = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK)

@Composable
fun HomeScreen(now: LocalDateTime, placements: List<HomePlacement>) {
    val c = Culvery.colors
    Column(verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth()) {
            Text(now.format(CLOCK), style = HhType.clock, color = c.ink)
            Spacer(Modifier.height(12.dp))
            Text(now.format(DATE), style = HhType.date, color = c.mute)
        }
        HomeGrid(placements, Modifier.fillMaxWidth().weight(1f))
    }
}
