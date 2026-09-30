package uk.co.siland.culvery.core.setup

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.co.siland.culvery.core.ui.HhType

/** Layout values for the wizard and Settings (4a design §4), named once; those the spec doesn't give say so. */
internal object SetupDimens {
    // Wizard frame (§4.1): the step in a centred column 720 wide. Not in the spec: padding 36 top, 32 bottom, 48 at the
    // sides; dots 10 dp, 10 apart, 32 above the step; 24 above the buttons.
    val wizardColumn = 720.dp
    val wizardPaddingTop = 36.dp
    val wizardPaddingBottom = 32.dp
    val wizardPaddingH = 48.dp
    val dot = 10.dp
    val dotGap = 10.dp
    val dotsBottom = 32.dp
    val buttonsTop = 24.dp

    // A step or page (not in the spec): blocks 20 apart; a title's line 8 below it.
    val blockGap = 20.dp
    val titleLineGap = 8.dp

    // List rows, people and towns (not in the spec): `surf`, radius 18, padding 16×20, 10 apart; a 16 dp colour dot 16
    // from the text, whose second line is 2 below; a 24 dp icon at the end.
    val rowRadius = 18.dp
    val rowPaddingV = 16.dp
    val rowPaddingH = 20.dp
    val rowGap = 10.dp
    val rowDot = 16.dp
    val rowDotGap = 16.dp
    val rowLineGap = 2.dp
    val rowIcon = 24.dp

    // The person sheet (§4.4: HhSheet, 600 dp), padded as the calendar's sheets (28×30×26). Not in the spec: sections 20
    // apart, a label 10 above its controls; role chips 10 apart; footer buttons 10 apart.
    val sheetPaddingTop = 28.dp
    val sheetPaddingH = 30.dp
    val sheetPaddingBottom = 26.dp
    val sectionGap = 20.dp
    val labelGap = 10.dp
    val roleGap = 10.dp
    val footerGap = 10.dp

    // A confirmation, as the calendar's delete confirmation: `dangerSoft`, radius 24, padding 20, 16 between blocks,
    // buttons 12 apart.
    val confirmRadius = 24.dp
    val confirmPadding = 20.dp
    val confirmGap = 16.dp
    val confirmButtonGap = 12.dp

    // Settings (§4.6): the left column 320 wide. Not in the spec: `surf`, padding 32×28; the title 24 above the list;
    // items 56 dp, radius 16, padding 0 18, 4 apart; the page padded 40×48, at most the wizard's 720 wide.
    val settingsList = 320.dp
    val settingsListPaddingV = 32.dp
    val settingsListPaddingH = 28.dp
    val settingsTitleGap = 24.dp
    val settingsItemHeight = 56.dp
    val settingsItemRadius = 16.dp
    val settingsItemPaddingH = 18.dp
    val settingsItemGap = 4.dp
    val pagePaddingV = 40.dp
    val pagePaddingH = 48.dp
}

/** Setup and Settings text styles, derived from HhType. */
internal object SetupType {
    /** 34 sp / 700: a step's or page's title. */
    val title = HhType.screenTitle

    /** 16 sp / 400: a title's line, and body text. */
    val line = HhType.body

    /** 17 sp / 600: a row's name, a Settings item. */
    val rowTitle = HhType.rowTitle

    /** 14 sp / 400: a row's second line, a role's line. */
    val secondary = HhType.secondary

    /** 13 sp / 700: a sheet section's label. */
    val label = HhType.label.copy(fontWeight = FontWeight.W700)

    /** 30 sp / 700, −0.5 tracking: the person sheet's heading (the calendar editor's title). */
    val sheetTitle = HhType.screenTitle.copy(fontSize = 30.sp, letterSpacing = (-0.5).sp)

    /** 14 sp / 700: a refusal in `danger`. */
    val message = HhType.secondary.copy(fontWeight = FontWeight.W700)

    /** 18 sp / 700: a confirmation's question. */
    val confirm = HhType.body.copy(fontSize = 18.sp, fontWeight = FontWeight.W700)
}
