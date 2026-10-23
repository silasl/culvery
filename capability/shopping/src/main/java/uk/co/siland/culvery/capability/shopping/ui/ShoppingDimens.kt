package uk.co.siland.culvery.capability.shopping.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The Shopping tab's layout values (10 §4.8), handoff px ÷ 1.5; provisional until the end-of-v1 design walk-through. */
internal object ShoppingDimens {
    // The add card and the list card: `surf`.
    val cardRadius = 24.dp
    val cardPaddingH = 20.dp
    val cardPaddingV = 19.dp

    /** Header to add card, and add card to list. */
    val cardGap = 15.dp

    // The add card: the field row and the chips 12 apart, Add 12 after the field.
    val addCardGap = 12.dp
    val addGap = 12.dp
    val chipGap = 9.dp
    val ruleWidth = 1.dp
    val ruleHeight = 28.dp
    val ruleGap = 6.dp
    val chipRowHeight = 48.dp

    /** A full row holds about 15 chips, so more favourites than this are never placed. */
    const val FAVOURITES_SHOWN_MAX = 40

    // The list: three columns, rows 48, a 20 circle with a 2 ring, the name 18 sp 12 after it.
    const val COLUMNS = 3
    val columnGap = 15.dp
    val itemHeight = 48.dp
    val itemRadius = 12.dp
    val circle = 20.dp
    val circleRing = 2.dp
    val circleGap = 12.dp
    val itemName = 18.sp
    val listFade = 48.dp
}
