package uk.co.siland.culvery.core.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import uk.co.siland.culvery.core.ui.Culvery

/** A step's or page's title, with an optional line under it in `mute`. */
@Composable
internal fun StepTitle(title: String, line: String? = null) {
    val c = Culvery.colors
    Column(verticalArrangement = Arrangement.spacedBy(SetupDimens.titleLineGap)) {
        Text(title, style = SetupType.title, color = c.ink)
        if (line != null) Text(line, style = SetupType.line, color = c.mute)
    }
}
