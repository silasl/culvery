package uk.co.siland.culvery.core.ui.test

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import uk.co.siland.culvery.core.ui.SpokenNameKey

/**
 * 4d-2 §4.1: every clickable has a role and a name (its text or description), no click label that repeats the name or
 * the visible text, and a button's spoken name ([SpokenNameKey]) starts with the text a sighted helper reads out. Text
 * fields are left out: they have a click action but no role, and their label is checked where they are made.
 *
 * It sees only nodes with a click action, in the state the test has drawn: a `pointerInput` tap detector, a
 * long-click-only or custom-action-only node, and any state the test doesn't draw are invisible to it.
 */
fun SemanticsNodeInteractionsProvider.assertClickablesHaveRoleAndLabel() {
    val nodes = onAllNodes(hasClickAction()).fetchSemanticsNodes()
    val problems = nodes.mapNotNull { node ->
        val c = node.config
        if (c.getOrNull(SemanticsProperties.EditableText) != null) return@mapNotNull null
        val text = c.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
        val description = c.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")
        val name = description.ifBlank { text }
        val label = c.getOrNull(SemanticsActions.OnClick)?.label
        val spoken = c.getOrNull(SpokenNameKey)
        val where = c.getOrNull(SemanticsProperties.TestTag) ?: name.ifBlank { "node ${node.id}" }
        when {
            c.getOrNull(SemanticsProperties.Role) == null -> "$where: no role"
            name.isBlank() -> "$where: no name"
            label != null && (label.equals(name, ignoreCase = true) || label.equals(text, ignoreCase = true)) ->
                "$where: click label repeats its name \"$label\""
            spoken != null && !spoken.startsWith(text) -> "$where: spoken name \"$spoken\" doesn't start with its text \"$text\""
            else -> null
        }
    }
    if (problems.isNotEmpty()) throw AssertionError("Clickables TalkBack can't name or place:\n" + problems.joinToString("\n"))
}

/** The one call a screen test makes for each state it renders. */
fun ComposeContentTestRule.assertAccessible() {
    assertClickablesHaveRoleAndLabel()
}
