package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.ShellTokens

/** Calendar text styles from the hand-off and Culvery.dc.html that HhType has no name for; all derived from HhType. */
internal object CalendarType {
    /** 14 sp / 600: day labels, compact titles. */
    val strong14 = HhType.secondary.copy(fontWeight = FontWeight.W600)

    /** 12 sp / 400: compact times, "+N more". */
    val small12 = HhType.secondary.copy(fontSize = 12.sp)

    /** 12 sp / 700: week-view chip times. */
    val chipTime = HhType.labelSmall

    /** 14 sp / 600 at 1.25 line height: week-view chip titles. */
    val chipTitle = strong14.copy(lineHeight = 17.5.sp)

    /** 34 sp / 700, −0.5 tracking: "This week". */
    val weekTitle = HhType.screenTitle.copy(letterSpacing = (-0.5).sp)

    /** 15 sp / 400: the week-view subtitle. */
    val subtitle = HhType.secondary.copy(fontSize = 15.sp)

    /** 14 sp / 500: legend names. */
    val legend = HhType.secondary.copy(fontWeight = FontWeight.W500)

    /** 14 sp / 600: the reconnect chip. */
    val pill = strong14

    /** 34 sp / 700, −0.5 tracking: the detail sheet's title. */
    val sheetTitle = weekTitle

    /** 15 sp / 400: info-row labels. */
    val infoLabel = subtitle

    /** 17 sp / 600: info-row values. */
    val infoValue = HhType.rowTitle

    /** 13 sp / 700: "Saving to …". */
    val syncingPill = HhType.label.copy(fontWeight = FontWeight.W700)

    /** 16 sp / 700: explanation card titles. */
    val noteTitle = HhType.body.copy(fontWeight = FontWeight.W700)

    /** 14 sp / 400: explanation card text. */
    val noteBody = HhType.secondary

    /** 16 sp / 600: person chips. */
    val personChip = HhType.body.copy(fontWeight = FontWeight.W600)

    /** 24 sp / 700: "Pick a date", "Pick a time". */
    val pickerTitle = HhType.dateNumber

    /** 15 sp / 400: the date picker's range, "Mon 21 Sep – Sun 25 Oct". */
    val pickerRange = subtitle

    /** 13 sp / 700: the date picker's weekday header. */
    val pickerWeekday = HhType.label.copy(fontWeight = FontWeight.W700)

    /** 16 sp / 600: date cells. */
    val pickerCell = personChip

    /** 72 sp / 600, tabular, −2 tracking, line height 1: the time picker's hour and minute. */
    val timeValue = HhType.headerValue.copy(fontSize = 72.sp, letterSpacing = (-2).sp, lineHeight = 72.sp)

    /** 64 sp / 600: the ":" between them. */
    val timeColon = HhType.headerValue.copy(fontSize = 64.sp)

    /** 30 sp / 700, −0.5 tracking: "New event", "Edit event". */
    val editorTitle = HhType.screenTitle.copy(fontSize = 30.sp, letterSpacing = (-0.5).sp)

    /** 15 sp / 400: the add/edit sheet's live summary. */
    val editorSummary = subtitle

    /** 16 sp / 600: a locked multi-day edit's dates line. */
    val lockedDates = personChip

    /** 22 sp / 700: "Connecting to Google Calendar…" and Settings' "Calendars" (3a design §4). */
    val blockTitle = HhType.sectionTitle

    /** 34 sp / 700: "Your calendars" and "Calendars", as the wizard's and Settings' titles (4a design §4.5). */
    val reviewTitle = HhType.screenTitle

    /** 13 sp / 700: the "Main" badge. */
    val badge = syncingPill
}

/** Layout values from Culvery.dc.html, named once so no card or view carries magic numbers. */
internal object CalendarDimens {
    // The reconnect chip and Today's +: 48 dp touch targets; the chip's radius half that.
    val touchTarget = 48.dp
    val pillRadius = 24.dp
    val pillPaddingH = 16.dp

    // Today card: header→first row = 10 gap + 4 margin; row bar→text 14; time line 3 below the title.
    val todayHeaderGap = 14.dp
    val todayRowGap = 10.dp
    val todayBarGap = 14.dp
    val todayTimeTop = 3.dp
    val todayBadge = 20.dp

    // Shared card radius (Today, Coming up, Connect): Home's, as the weather card's.
    val cardRadius = ShellTokens.homeCardRadius

    // Today row: surf2 pill, radius 16, 14×12 padding, 4 dp person-colour bar.
    val todayRowRadius = 16.dp
    val todayRowPaddingH = 14.dp
    val todayRowPaddingV = 12.dp
    val todayBarWidth = 4.dp

    // Coming up card: padding 20 × 22, like the hand-off's WIDE Scenes card.
    val comingUpPaddingV = 20.dp
    val comingUpPaddingH = 22.dp

    // Coming up: header→columns gap, gap between the three day columns, gap within a day column.
    val comingUpHeaderGap = 6.dp
    val comingUpColumnGap = 12.dp
    val comingUpRowGap = 6.dp

    // Coming up compact row: surf2 pill, radius 12, 10×8 padding, 3 dp person-colour bar, bar→text 8.
    val compactRowRadius = 12.dp
    val compactRowPaddingH = 10.dp
    val compactRowPaddingV = 8.dp
    val compactBarWidth = 3.dp
    val compactBarGap = 8.dp

    // Connect card: padding 20 × 22, 4 dp above the subtitle, like the hand-off's Holiday tile.
    val connectPaddingV = 20.dp
    val connectPaddingH = 22.dp
    val connectSubtitleTop = 4.dp
    val connectIconSize = 34.dp
    val connectButtonTop = 18.dp

    // Week view header: subtitle 4 below the title; legend 18 between people, 7 dot→name, 24 to the
    // right-hand button slot (2b's Add event); reconnect chip 8 below the subtitle.
    val subtitleTop = 4.dp
    val legendGap = 18.dp
    val legendDotGap = 7.dp
    val legendDot = 10.dp
    val legendNameMax = 120.dp
    val headerTrailingGap = 24.dp
    val reconnectTop = 8.dp
    val reconnectIcon = 20.dp
    val staleIcon = 16.dp
    val staleIconGap = 6.dp

    // Looking ahead (4c §6.6): ‹ › are the date picker's 48 dp round buttons, 8 apart and 16 before the title; the
    // This week chip is a 44 dp `surf2` pill 16 after it. An arrow at its end shows at 30%.
    val weekStepGap = 8.dp
    val weekTitleGap = 16.dp
    const val DISABLED_ALPHA = 0.3f

    // Week columns: header inset 4 at the sides and bottom; chip title 2 below the time; badge 15.
    val columnHeaderInset = 4.dp
    val columnGap = 8.dp
    val chipTitleTop = 2.dp
    val chipBadge = 15.dp
    val badgeGap = 4.dp

    // Week view layout: header→columns 18, between columns 10; column radius 22, today ring 2 dp inset;
    // column padding 10×14; weekday label→date number 6; chip radius 12, chip padding 10×8; reconnect icon→label 8.
    val weekHeaderGap = 18.dp
    val weekColumnGap = 10.dp
    val weekColumnRadius = 22.dp
    val todayRingWidth = 2.dp
    val weekColumnPaddingH = 10.dp
    val weekColumnPaddingV = 14.dp
    val weekDayDateGap = 6.dp
    val chipRadius = 12.dp
    val chipPaddingH = 10.dp
    val chipPaddingV = 8.dp
    val reconnectIconGap = 8.dp

    // Event detail sheet (hand-off §7): padding 28×30×26; a 6 dp colour bar 14 dp from the title; close 12 dp away.
    val sheetPaddingTop = 28.dp
    val sheetPaddingH = 30.dp
    val sheetPaddingBottom = 26.dp
    val sheetTitleBar = 6.dp
    val sheetTitleBarGap = 14.dp
    val sheetHeaderGap = 12.dp

    // Syncing pill: 30 dp, radius 15, `surf2`, an 18 dp cloud_upload; 8 dp above the title.
    val syncingPillHeight = 30.dp
    val syncingPillRadius = 15.dp
    val syncingPillPaddingH = 12.dp
    val syncingPillIcon = 18.dp
    val syncingPillIconGap = 6.dp
    val syncingPillBottom = 8.dp

    // Info card: `surf`, radius 22, padding 2×18, rows at least 58 dp with 1 dp `line` dividers;
    // 22 dp icon, a label column at least 104 dp, 12 dp person dot.
    val infoRadius = 22.dp
    val infoPaddingV = 2.dp
    val infoPaddingH = 18.dp
    val infoRowMin = 58.dp
    val infoDivider = 1.dp
    val infoIcon = 22.dp
    val infoIconGap = 12.dp
    val infoLabelWidth = 104.dp
    // Hand-off: the value column starts 204 dp from the sheet's edge, 18 dp after the label column.
    val infoLabelGap = 18.dp
    val infoDot = 12.dp
    val infoDotGap = 8.dp

    // Explanation cards (read-only, repeating, untagged): radius 22, padding 16×18, 26 dp icon.
    val noteRadius = 22.dp
    val notePaddingV = 16.dp
    val notePaddingH = 18.dp
    val noteIcon = 26.dp
    val noteIconGap = 14.dp
    val noteTextGap = 2.dp

    // Assign: the button 12 below the note's text; person chips 8 apart (ControlTokens has the chip).
    val assignTop = 12.dp
    val personChipGap = 8.dp

    // The confirm card's delete icon.
    val footerIcon = 24.dp

    // Pickers (hand-off §7 and Culvery.dc.html): card `surf`, radius 30, padding 26. Date: 520 wide, 14 between blocks,
    // the range 4 below "Pick a date", ‹ › 48 dp circles 8 apart; weekday header padding 4; cells 56 dp, radius 16,
    // 6 apart, today's ring 2 dp; Cancel a Large button. Time: 440 wide, 18 between blocks; the columns 14 apart;
    // steppers 88×52, radius 18, a 32 dp icon, 8 from the value; ":" 10 above the baseline; Cancel and Set time
    // Large buttons 10 apart.
    val pickerRadius = 30.dp
    val pickerPadding = 26.dp
    val datePickerWidth = 520.dp
    val datePickerGap = 14.dp
    val pickerRangeTop = 4.dp
    val pageButton = 48.dp
    val pageButtonIcon = 24.dp
    val pageButtonGap = 8.dp
    val weekdayPaddingV = 4.dp
    val dateCellHeight = 56.dp
    val dateCellRadius = 16.dp
    val dateCellGap = 6.dp
    val dateRing = 2.dp
    val timePickerWidth = 440.dp
    val timePickerGap = 18.dp
    val timeColumnGap = 14.dp
    val stepperWidth = 88.dp
    val stepperHeight = 52.dp
    val stepperRadius = 18.dp
    val stepperIcon = 32.dp
    val stepperGap = 8.dp
    val colonBottom = 10.dp
    val timeButtonGap = 10.dp

    // Add/edit sheet (hand-off §7 Sheet 2 and Culvery.dc.html): padding 24×30×20; the summary 4 below the title and
    // ✕ 16 away; the body 4 below the Title field, 20 between sections, each label 10 above its chips.
    val editorPaddingTop = 24.dp
    val editorPaddingBottom = 20.dp
    val editorHeaderGap = 16.dp
    val editorSummaryTop = 4.dp
    val editorBodyTop = 4.dp
    val editorBodyGap = 20.dp
    val sectionGap = 10.dp

    // The locked dates line and the failure card: radius 18, padding 14×16, the icon 12 from the text;
    // `date_range` 22 dp, `cloud_off` 24 dp, the failure body 2 below its title.
    val editorCardRadius = 18.dp
    val editorCardPaddingV = 14.dp
    val editorCardPaddingH = 16.dp
    val editorCardIconGap = 12.dp
    val lockedIcon = 22.dp
    val failureIcon = 24.dp
    val failureBodyTop = 2.dp

    // Footers: 10 between Delete and the main button.
    val footerGap = 10.dp

    // Entry points (hand-off §7): Today's + (touchTarget) with a 26 dp add, 8 from Week. Each week column ends with a
    // 24 dp add hint in at least 48 dp.
    val todayAddIcon = 26.dp
    val todayHeaderButtonGap = 8.dp
    val addHintIcon = 24.dp
    val addHintMinHeight = 48.dp

    // Connecting card (3a design §4.4): the pickers' card (radius 30, padding 26) 440 wide, a 32 dp icon; 14 between
    // blocks and 4 between its two lines (not in the spec: the date picker's spacing); Cancel as the date picker's.
    val connectingWidth = 440.dp
    val connectingGap = 14.dp
    val connectingTextGap = 4.dp
    val connectingIcon = 32.dp

    // Settings' Calendars block (3a design §4.1): rows are ListRowTokens'; a connection's 26 dp icon leads it.
    val settingsIcon = 26.dp

    // Review calendars (4a design §4.5; not in the spec): blocks 12 apart; a calendar row's parts 12 apart; the Main badge
    // `accentSoft`, radius 12, padding 4×10. The wizard's Connect card 380×460, about a Home card's size.
    val reviewBlockGap = 12.dp
    val reviewItemGap = 12.dp
    val badgeRadius = 12.dp
    val badgePaddingV = 4.dp
    val badgePaddingH = 10.dp
    val connectStepWidth = 380.dp
    val connectStepHeight = 460.dp
}
