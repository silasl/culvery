package uk.co.siland.culvery.capability.door.ui

import androidx.compose.runtime.Composable
import uk.co.siland.culvery.core.ui.HhStatusLine

/** The one line saying what is wrong and how to fix it (5 §6), on the Door tab and in Settings › Door. */
@Composable
internal fun DoorStatusLine(text: String) = HhStatusLine(text, "door_status")
