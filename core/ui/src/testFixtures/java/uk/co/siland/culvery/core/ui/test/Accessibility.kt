package uk.co.siland.culvery.core.ui.test

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
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
        val text = node.shownText()
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

/**
 * 4d-2 walk-through: TalkBack reads a described node's description and then the text merged into it from below, so it
 * is heard twice ("Now 10°, cloudy, …, 10°, High 16° · Low 7°"); its own text it leaves for the description ("Name"
 * over "NAME" is read once). The text that shows under a description goes in a [shownNotSpoken] child.
 */
fun SemanticsNodeInteractionsProvider.assertNothingReadTwice() {
    val any = SemanticsMatcher("any node") { true }
    val own = onAllNodes(any, useUnmergedTree = true).fetchSemanticsNodes().associate { it.id to it.config.texts() }
    val problems = onAllNodes(any).fetchSemanticsNodes().mapNotNull { node ->
        val c = node.config
        val description = c.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")
        val merged = c.texts() - own[node.id].orEmpty().toSet()
        // A field's placeholder is read after its name: a hint when it differs, the name again when it doesn't.
        val field = c.getOrNull(SemanticsProperties.EditableText) != null
        val twice = if (field) description in merged else merged.isNotEmpty()
        if (description.isBlank() || !twice) null else "\"$description\" then \"${merged.joinToString(" ")}\""
    }
    if (problems.isNotEmpty()) throw AssertionError("Read twice, as description and as text:\n" + problems.joinToString("\n"))
}

private fun SemanticsConfiguration.texts(): List<String> = getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }.filter { it.isNotBlank() }

/** What a sighted helper reads on the node: its own text and the text it shows but doesn't speak. */
private fun SemanticsNode.shownText(): String =
    (listOf(this) + children.filter { SemanticsProperties.HideFromAccessibility in it.config })
        .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }
        .joinToString(" ") { it.text }

/** A node [readAsOne][uk.co.siland.culvery.core.ui.readAsOne] made a traversal group. */
val isReadAsOne: SemanticsMatcher = SemanticsMatcher.expectValue(SemanticsProperties.IsTraversalGroup, true)

/** The clickable row or chip that shows [text]: its texts sit in a [shownNotSpoken][uk.co.siland.culvery.core.ui.shownNotSpoken] child. */
fun SemanticsNodeInteractionsProvider.onClickableShowing(text: String): SemanticsNodeInteraction =
    onNode(hasClickAction() and hasAnyDescendant(hasText(text)))

/** The calls a screen test makes for each state it renders. */
fun ComposeContentTestRule.assertAccessible() {
    assertClickablesHaveRoleAndLabel()
    assertNothingReadTwice()
}
