package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * A clickable TalkBack can name (4d-2 §4.1): a [role], `Role.Button` unless said, and [label], what follows "double-tap
 * to", lower case ("open event details"). Leave [label] null when the control's name already says what it does.
 * [spoken] is what TalkBack hears as its name when that isn't what it shows.
 */
fun Modifier.hhClickable(
    onClick: () -> Unit,
    label: String? = null,
    role: Role = Role.Button,
    enabled: Boolean = true,
    spoken: String? = null,
): Modifier = then(if (spoken != null) Modifier.semantics { contentDescription = spoken } else Modifier)
    .clickable(enabled = enabled, onClickLabel = label, role = role, onClick = onClick)
