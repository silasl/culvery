package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/**
 * A clickable TalkBack can name (4d-2 §4.1): a [role], `Role.Button` unless said, and [label], what follows "double-tap
 * to", lower case ("open event details"). Leave [label] null when the control's name already says what it does.
 */
fun Modifier.hhClickable(onClick: () -> Unit, label: String? = null, role: Role = Role.Button, enabled: Boolean = true): Modifier =
    clickable(enabled = enabled, onClickLabel = label, role = role, onClick = onClick)
