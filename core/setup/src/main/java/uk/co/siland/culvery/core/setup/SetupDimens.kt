package uk.co.siland.culvery.core.setup

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.co.siland.culvery.core.ui.HhType

/** Layout values for the wizard and Settings (4a design §4), named once; those the spec doesn't give say so. */
internal object SetupDimens {
    // Wizard frame (9 §4.11): the step in a centred column 640 wide; Settings' pane keeps 720. Not in the spec: padding
    // 37 top, 32 bottom, 48 at the sides; dots 11 dp, 9 apart, the current one a 29 dp pill, 43 above the step; 24
    // above the buttons.
    val wizardColumn = 640.dp
    val settingsPane = 720.dp
    val wizardPaddingTop = 37.dp
    val wizardPaddingBottom = 32.dp
    val wizardPaddingH = 48.dp
    val dot = 11.dp
    val dotCurrentWidth = 29.dp
    val dotGap = 9.dp
    val dotsBottom = 43.dp
    val buttonsTop = 24.dp

    // A step or page (not in the spec): blocks 20 apart.
    val blockGap = 20.dp

    // The home-app prompt (4c §5.1; not in the spec): its button 12 below its line.
    val homeAppPromptGap = 12.dp

    // List rows are ListRowTokens'. Not in the spec: 12 between rows. A person's row is 75 dp with a 15 dp colour dot (9 §4.11).
    val rowGap = 12.dp
    val personRowHeight = 75.dp
    val rowDot = 15.dp
    val rowDotGap = 16.dp

    // Move up / Move down (4c §7.1; not in the spec): 48 dp touch targets around the row's 24 dp icon.
    val moveButton = 48.dp

    // The person sheet (9 §4.11: HhSheet, 613 dp), padded as handoff 5b: 29 top, 32 at the sides, 27 foot. Sections 16 apart,
    // a label 8 above its control; the taken line 9 under the swatches; the PIN hint 13 after Set PIN. Not in the spec:
    // footer buttons 10 apart.
    val sheetPaddingTop = 29.dp
    val sheetPaddingH = 32.dp
    val sheetPaddingBottom = 27.dp
    val sectionGap = 16.dp
    val labelGap = 8.dp
    val takenLineGap = 9.dp
    val pinHintGap = 13.dp
    val footerGap = 10.dp

    // Settings (§4.6): the left column 320 wide. Not in the spec: `surf`, padding 32×28; the title 24 above the list;
    // items 56 dp, radius 16, padding 0 18, 4 apart; the page padded 40×48, at most [settingsPane] wide.
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

    /** 17 sp / 600: a Settings item. */
    val rowTitle = HhType.rowTitle

    /** 17 sp / 700: the chosen Settings page, bold as well as tinted (4d-1 §5.2). */
    val chosenItem = HhType.rowTitle.copy(fontWeight = FontWeight.W700)

    /** 14 sp / 400: a line beside or under a control (PIN set, what the town is for). */
    val secondary = HhType.secondary

    /** 35 sp / 700, −0.5 tracking: the person sheet's heading (handoff 5b). */
    val sheetTitle = HhType.screenTitle.copy(fontSize = 35.sp, letterSpacing = (-0.5).sp)

    /** 13 sp / 400: the taken colours under the swatches. */
    val takenLine = HhType.secondary.copy(fontSize = 13.sp)

    /** 14 sp / 700: a refusal in `danger`. */
    val message = HhType.secondary.copy(fontWeight = FontWeight.W700)
}
