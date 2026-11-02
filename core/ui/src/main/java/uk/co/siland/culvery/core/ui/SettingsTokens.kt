package uk.co.siland.culvery.core.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Every Settings page's spacing (15b §6 W34): the handoff's 18 px/700 labels with 1.5 px tracking, and 10 px between rows. */
object SettingsTokens {
    val sectionLabel = HhType.sectionLabel.copy(fontSize = 12.sp, letterSpacing = 1.sp)
    val rowGap = 7.dp
}
