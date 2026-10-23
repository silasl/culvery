package uk.co.siland.culvery.core.ui.test

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult

/**
 * Every clickable and text lies inside the node tagged [cardTag], no two clickables overlap, and no text line is clipped
 * (ending in … is not). With [textStaysOffControls], no text runs across a control it isn't inside.
 */
fun ComposeContentTestRule.assertFitsTheCard(cardTag: String = "fit_card", textStaysOffControls: Boolean = true) {
    val card = onNodeWithTag(cardTag).fetchSemanticsNode().boundsInRoot
    val texts = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes().filter { !it.boundsInRoot.isEmpty }
    val clickables = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).fetchSemanticsNodes()
    check(texts.isNotEmpty()) { "no text on the card" }
    check(clickables.isNotEmpty()) { "no clickable on the card" }
    for (node in texts + clickables) {
        val b = node.boundsInRoot
        check(b.left >= card.left - 0.5f && b.top >= card.top - 0.5f && b.right <= card.right + 0.5f && b.bottom <= card.bottom + 0.5f) {
            "$b is outside the card $card"
        }
    }
    for (i in clickables.indices) for (j in i + 1 until clickables.size) {
        val a = clickables[i].boundsInRoot
        val b = clickables[j].boundsInRoot
        check(!a.deflate(0.5f).overlaps(b.deflate(0.5f))) { "clickables $a and $b overlap" }
    }
    if (textStaysOffControls) {
        for (text in texts) for (click in clickables) {
            val t = text.boundsInRoot
            val c = click.boundsInRoot
            val inside = t.left >= c.left - 0.5f && t.top >= c.top - 0.5f && t.right <= c.right + 0.5f && t.bottom <= c.bottom + 0.5f
            check(inside || !t.deflate(0.5f).overlaps(c)) { "text at $t runs across the control at $c" }
        }
    }
    for (node in texts) {
        val results = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        results.forEach {
            check(!(it.didOverflowHeight && !it.isLineEllipsized(it.lineCount - 1))) {
                "\"${node.config.getOrNull(SemanticsProperties.Text)?.joinToString { t -> t.text }}\" is cut at ${node.boundsInRoot} in $card"
            }
        }
    }
}
