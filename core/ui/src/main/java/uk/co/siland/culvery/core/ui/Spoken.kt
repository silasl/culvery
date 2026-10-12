package uk.co.siland.culvery.core.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics

/**
 * Text that shows under a node TalkBack reads by its description. TalkBack reads a description and then the text merged
 * under it, so the text is its own merging node, left out of the parent's merge and hidden from TalkBack; tests still
 * find it in the unmerged tree.
 */
fun Modifier.shownNotSpoken(): Modifier = semantics(mergeDescendants = true) { hideFromAccessibility() }

/** A block TalkBack reads through before moving on, instead of by screen position across its neighbours. */
fun Modifier.readAsOne(): Modifier = semantics { isTraversalGroup = true }
