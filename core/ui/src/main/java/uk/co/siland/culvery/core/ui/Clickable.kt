package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState

/**
 * A clickable TalkBack can name (4d-2 §4.1): a [role], `Role.Button` unless said, and [label], what follows "double-tap
 * to", lower case ("open event details"). Leave [label] null when the control's name already says what it does.
 * [spoken] is what TalkBack hears as its name when that isn't what it shows. [onLongClick] adds a long press, offered
 * to TalkBack as the action [longClickLabel].
 */
fun Modifier.hhClickable(
    onClick: () -> Unit,
    label: String? = null,
    role: Role = Role.Button,
    enabled: Boolean = true,
    spoken: String? = null,
    onLongClick: (() -> Unit)? = null,
    longClickLabel: String? = null,
): Modifier = then(if (spoken != null) Modifier.semantics { contentDescription = spoken } else Modifier)
    .then(
        if (onLongClick == null) {
            Modifier.clickable(enabled = enabled, onClickLabel = label, role = role, onClick = onClick)
        } else {
            Modifier.combinedClickable(
                enabled = enabled, onClickLabel = label, role = role,
                onLongClickLabel = longClickLabel, onLongClick = onLongClick, onClick = onClick,
            )
        },
    )

/** A toggle that, disabled, keeps its role and state but offers TalkBack no action (a disabled [toggleable] still does). [spoken] is as for [hhClickable]. */
fun Modifier.hhToggleable(
    value: Boolean,
    enabled: Boolean = true,
    role: Role = Role.Switch,
    spoken: String? = null,
    onValueChange: (Boolean) -> Unit,
): Modifier = then(if (spoken != null) Modifier.semantics { contentDescription = spoken } else Modifier)
    .then(
        if (enabled) {
            Modifier.toggleable(value = value, role = role, onValueChange = onValueChange)
        } else {
            Modifier.semantics(mergeDescendants = true) {
                this.role = role
                toggleableState = ToggleableState(value)
                disabled()
            }
        },
    )
