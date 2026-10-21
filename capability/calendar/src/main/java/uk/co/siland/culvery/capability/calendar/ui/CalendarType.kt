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

    /** 11 sp / 600: week-view chip times and names. */
    val chipTime = HhType.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.W600)

    /** 14 sp / 600 at 1.25 line height: week-view chip titles. */
    val chipTitle = strong14.copy(lineHeight = 17.5.sp)

    /** 14 sp / 700 and 11 sp / 500: a lane bar's title and its range. */
    val laneTitle = HhType.secondary.copy(fontWeight = FontWeight.W700)
    val laneRange = HhType.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.W500)

    /** 23 sp / 700: a week column's date number. */
    val weekDate = HhType.dateNumber.copy(fontSize = 23.sp)

    /** 45 sp / 700, −1 tracking: the week's month; its year is 400 and muted, [monthYearGap] after it (9 §3.2). */
    val monthTitle = HhType.screenTitle.copy(fontSize = 45.sp, fontWeight = FontWeight.W700, letterSpacing = (-1).sp)
    val monthTitleYear = monthTitle.copy(fontWeight = FontWeight.W400, letterSpacing = 0.sp)

    /** 11 sp / 700: "Oct" over the 1st's date. */
    val monthLabel = HhType.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.W700)

    /** 15 sp / 400: the week-view subtitle. */
    val subtitle = HhType.secondary.copy(fontSize = 15.sp)

    /** 14 sp / 600: the reconnect chip. */
    val pill = strong14

    /** 34 sp / 700, −0.5 tracking: the detail sheet's title. */
    val sheetTitle = HhType.screenTitle.copy(letterSpacing = (-0.5).sp)

    /** 15 sp / 400: info-row labels. */
    val infoLabel = subtitle

    /** 17 sp / 600: info-row values. */
    val infoValue = HhType.rowTitle

    /** 23 sp / 700: Today's "Today" (9 §4.2). */
    val todayHeader = HhType.cardTitle.copy(fontSize = 23.sp)

    /** 21 sp / 600: Today's event titles on Home; other rows keep HhType.rowTitle. */
    val todayTitle = HhType.rowTitle.copy(fontSize = 21.sp)

    /** 16 sp / 400: Today's time and person line. */
    val todayTime = HhType.secondary.copy(fontSize = 16.sp)

    /** 18 sp / 600 and 15 sp / 400: tomorrow's rows topping up Today. */
    val tomorrowTitle = HhType.rowTitle.copy(fontSize = 18.sp)
    val tomorrowTime = HhType.secondary.copy(fontSize = 15.sp)

    /** 15 sp / 700, 1 sp tracking: the TOMORROW divider. */
    val tomorrowDivider = HhType.label.copy(fontSize = 15.sp, fontWeight = FontWeight.W700, letterSpacing = 1.sp)

    /** 19 sp / 400: "Nothing on today". */
    val nothingToday = HhType.body.copy(fontSize = 19.sp)

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

    /** 35 sp / 700, −0.5 tracking: "Add event", "Change event" (9 §4.8). */
    val editorTitle = HhType.screenTitle.copy(fontSize = 35.sp, fontWeight = FontWeight.W700, letterSpacing = (-0.5).sp)

    /** 15 sp / 400: the add/edit sheet's live summary. */
    val editorSummary = HhType.secondary.copy(fontSize = 15.sp)

    /** 13 sp / 400: the line under a child's Who chips (9 §4.8). */
    val whoOnly = HhType.secondary.copy(fontSize = 13.sp)

    /** 16 sp / 600: a locked multi-day edit's dates line. */
    val lockedDates = personChip

    /** 22 sp / 700: "Connecting to Google Calendar…" and Settings' "Calendars" (3a design §4). */
    val blockTitle = HhType.sectionTitle

    /** 34 sp / 700: "Your calendars" and "Calendars", as the wizard's and Settings' titles (4a design §4.5). */
    val reviewTitle = HhType.screenTitle

    /** 13 sp / 700: the "Main" badge. */
    val badge = syncingPill

    /** 29 sp / 700 and 14 sp: the full-screen editor's "New event" and its context line (9 §3.1). */
    val editorBarTitle = HhType.screenTitle.copy(fontSize = 29.sp)
    val editorContext = HhType.secondary

    /** 24 sp / 600: the editor's title field. */
    val editorField = HhType.dateNumber.copy(fontWeight = FontWeight.W600)

    /** 12 sp / 700, 1 sp tracking, and 19 sp / 600: a row's label and its value. */
    val rowLabel = HhType.labelSmall.copy(letterSpacing = 1.sp)
    val rowValue = HhType.rowTitle.copy(fontSize = 19.sp)

    /** 12 sp / 700: a row's tag ("Clash"). */
    val rowTag = HhType.labelSmall

    /** 14 sp: "Saves to the Family calendar". */
    val footLine = HhType.secondary

    /** 27 sp / 700 and 15 sp / 700: the pane's question and its Skip. */
    val paneQuestion = HhType.screenTitle.copy(fontSize = 27.sp)
    val paneSkip = HhType.buttonLabel

    /** 28 sp / 700, 20 sp / 700 and 13 sp: a Who tile's initial, name and role (handoff 6b). */
    val whoInitial = HhType.dateNumber.copy(fontSize = 28.sp)
    val whoName = HhType.sectionTitle.copy(fontSize = 20.sp)
    val whoRole = HhType.secondary.copy(fontSize = 13.sp)

    /** 19 sp / 700 and 14 sp: "Everyone" and "Shows as a Family event". */
    val everyoneName = HhType.cardTitle
    val everyoneLine = HhType.secondary

    /** 14 sp: the line under the Who pane. */
    val whoHint = HhType.secondary

    /** 20 sp / 700, 12 sp / 700 and 18 sp: the Day pane's month, its weekday letters and a day's date (handoff 6c). */
    val dayMonth = HhType.sectionTitle.copy(fontSize = 20.sp)
    val dayLetter = HhType.labelSmall
    val dayDate = HhType.body.copy(fontSize = 18.sp, fontWeight = FontWeight.W500)

    /** 14 sp: "Tap the first day, then the last". */
    val dayHint = HhType.secondary
}

/** The full-screen editor's layout (handoff §1 Layout, px ÷ 1.5). */
internal object EditorDimens {
    // Top bar: 85 tall, 32 at the sides; ✕ then the title 16 after it, the context line 1 below the title.
    val barHeight = 85.dp
    val sides = 32.dp
    val barGap = 16.dp
    val contextTop = 1.dp

    // Body: 29 above the window's foot; the event column 507 wide, the pane 21 after it.
    val bodyBottom = 29.dp
    val columnWidth = 507.dp
    val columnGap = 21.dp

    // Title field: 75 tall, `surf`, radius 19, 21 at the sides; focused, a 2 dp `accent` ring inside.
    val fieldHeight = 75.dp
    val fieldRadius = 19.dp
    val fieldPaddingH = 21.dp
    val ring = 2.dp

    // Rows: 77 tall (61 with the keyboard up), 9 apart, radius 19, 20 in at the start and 16 at the end; a 25 dp icon 15
    // before the words, the label 3 above the value; a 24 dp chevron or lock 15 after them.
    val rowGap = 9.dp
    val rowHeight = 77.dp
    val rowHeightTyping = 61.dp
    val rowRadius = 19.dp
    val rowPaddingStart = 20.dp
    val rowPaddingEnd = 16.dp
    val rowIcon = 25.dp
    val rowIconGap = 15.dp
    val rowLabelGap = 3.dp
    val rowEndIcon = 24.dp

    // A row's tag: 27 tall, a pill, 11 at the sides, a 16 dp icon 4 before its word.
    val tagHeight = 27.dp
    val tagPaddingH = 11.dp
    val tagIcon = 16.dp
    val tagIconGap = 4.dp

    // The foot: 9 between its parts; "Saves to" 5 in from the column's sides, a 16 dp `event_note` 8 before the words.
    val footGap = 9.dp
    val footLinePaddingH = 5.dp
    val footIcon = 16.dp
    val footIconGap = 8.dp

    // The pane: `surf`, radius 24, padding 24 / 29 / 27, 15 between its parts; the header at least 48 tall, the question
    // 13 from Skip and Next, which are 8 apart; Skip 19 at its sides.
    val paneRadius = 24.dp
    val panePaddingTop = 24.dp
    val panePaddingH = 29.dp
    val panePaddingBottom = 27.dp
    val paneGap = 15.dp
    val paneHeader = 48.dp
    val paneHeaderGap = 13.dp
    val paneButtonGap = 8.dp
    val skipPaddingH = 19.dp

    // Who tiles: three a row, 13 apart, 160 tall, radius 20; a 64 circle 8 above the name, the role 8 below it. Chosen, a
    // 3 ring inside and a 32 badge 11 in from the top right; locked, a 20 lock 11 in from the top left.
    val tileGap = 13.dp
    val tileHeight = 160.dp
    val tileRadius = 20.dp
    val tileCircle = 64.dp
    val tileInnerGap = 8.dp
    val tileRing = 3.dp
    val tileBadge = 32.dp
    val tileBadgeIcon = 21.dp
    val tileCorner = 11.dp
    val tileLock = 20.dp

    // The Everyone row: 69 tall, radius 19, 19 in at the sides, a 40 Family circle with a 24 icon 13 before the words.
    val everyoneHeight = 69.dp
    val everyoneRadius = 19.dp
    val everyonePaddingH = 19.dp
    val everyoneGap = 13.dp
    val everyoneCircle = 40.dp
    val everyoneIcon = 24.dp
    val everyoneLineGap = 1.dp

    // The Day pane: quick picks 8 apart; the month's header, letters and grid 6 apart. Cells 56 tall, a 53 circle with a
    // 2 today ring, the date 3 above up to three 5 dots 3 apart; a range's band is the circle's height, its caps r 27.
    val quickGap = 8.dp
    val monthGap = 6.dp
    val dayCell = 56.dp
    val dayCircle = 53.dp
    val todayRing = 2.dp
    val dayDotsTop = 3.dp
    val dayDot = 5.dp
    val dayDotGap = 3.dp
    val bandRadius = 27.dp
}

/** Layout values from Culvery.dc.html, named once so no card or view carries magic numbers. */
internal object CalendarDimens {
    // The reconnect chip: a 48 dp touch target, its radius half that.
    val touchTarget = 48.dp
    val pillRadius = 24.dp
    val pillPaddingH = 16.dp

    // Today card (9 §4.2): padding 20 × 21; header→first row = 10 gap + 4 margin; rows 9 apart; row bar→text 14; time
    // line 3 below the title.
    val todayPaddingV = 20.dp
    val todayPaddingH = 21.dp
    val todayHeaderGap = 14.dp
    val todayRowGap = 9.dp
    val todayBarGap = 14.dp
    val todayTimeTop = 3.dp
    val todayBadge = 20.dp

    // Shared card radius (Today, Coming up, Connect): Home's, as the weather card's.
    val cardRadius = ShellTokens.homeCardRadius

    // Today row: surf2 pill, radius 15, 13 × 15 padding, 5 dp person-colour bar.
    val todayRowRadius = 15.dp
    val todayRowPaddingH = 15.dp
    val todayRowPaddingV = 13.dp
    val todayBarWidth = 5.dp

    // Tomorrow's rows: a 1 dp `surf2` edge, 11 × 13 padding, the bar at 75%. The divider's 1 dp rule starts 10 after
    // TOMORROW (not in the handoff's numbers).
    val tomorrowEdge = 1.dp
    val tomorrowRowPaddingH = 13.dp
    val tomorrowRowPaddingV = 11.dp
    const val TOMORROW_BAR_ALPHA = 0.75f
    val tomorrowRule = 1.dp
    val tomorrowRuleGap = 10.dp

    // Today's scroll: a 133 dp fade at the foot, 73 dp below the last row; the pill 20 above the card's foot (its sides
    // are the card's), with an 8 dp shadow.
    val todayFade = 133.dp
    val todayScrollBottom = 73.dp
    val todayPillShadow = 8.dp

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

    // Week view header: the sync line sits where a subtitle would (4 below the title); reconnect chip 8 below it.
    val reconnectTop = 8.dp
    val reconnectIcon = 20.dp
    val staleIcon = 16.dp
    val staleIconGap = 6.dp

    // Week columns: header inset 4 at the sides and bottom; chip title 2 below the time; badge 15.
    val columnHeaderInset = 4.dp
    val monthYearGap = 12.dp
    val monthLabelTop = 2.dp
    val columnGap = 8.dp
    val chipTitleTop = 2.dp
    val chipBadge = 15.dp
    val badgeGap = 4.dp

    // Week view layout (9 §4.7): header→columns 15 (the title block's own gap), between columns 9; column radius 20, padding 12 above and 9 at
    // the sides and foot; today's ring 2 dp wide, 2 dp in from the edge; weekday label→date number 6; chip radius
    // 12, padding 9×8, a 7 dp person dot 5 before the time; reconnect icon→label 8.
    val weekColumnGap = 9.dp
    val weekColumnRadius = 20.dp
    val todayRingWidth = 2.dp
    val todayRingInset = 2.dp
    val weekColumnPaddingTop = 12.dp
    val weekColumnPaddingSide = 9.dp
    val weekDayDateGap = 6.dp
    val chipRadius = 12.dp
    val chipPaddingH = 9.dp
    val chipPaddingV = 8.dp
    val chipDot = 7.dp
    val chipDotGap = 5.dp

    // A shared event's chip (9 §3.3): `surf2`, a 4 dp striped edge, content 12 from the chip's left, overlapping
    // 8 dp dots 2 dp over each other with a 1 dp ring in the surface behind; the detail sheet's For row uses the same overlap.
    val sharedChipEdge = 4.dp
    val sharedChipPaddingStart = 12.dp
    val sharedDot = 8.dp
    val sharedDotOverlap = 2.dp
    val sharedDotRing = 1.dp
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

    // Add/edit sheet (9 §4.8, handoff 4a): padding 29 above, 32 at the sides, 27 below; the summary 4 below the title and
    // ✕ 16 away; the body 4 below the Title field, 16 between sections, each label 8 above its chips; Time and Length 27 apart.
    val editorPaddingTop = 29.dp
    val editorPaddingH = 32.dp
    val editorPaddingBottom = 27.dp
    val editorHeaderGap = 16.dp
    val editorSummaryTop = 4.dp
    val editorBodyTop = 4.dp
    val editorBodyGap = 16.dp
    val sectionGap = 8.dp
    val timeLengthGap = 27.dp

    // The locked dates line and the failure card: radius 18, padding 14×16, the icon 12 from the text;
    // `date_range` 22 dp, `cloud_off` 24 dp, the failure body 2 below its title.
    val editorCardRadius = 18.dp
    val editorCardPaddingV = 14.dp
    val editorCardPaddingH = 16.dp
    val editorCardIconGap = 12.dp
    val lockedIcon = 22.dp
    val whoOnlyIcon = 15.dp
    val whoOnlyGap = 5.dp
    val whoOnlyTop = 8.dp
    val failureIcon = 24.dp
    val failureBodyTop = 2.dp

    // Footers: 10 between Delete and the main button.
    val footerGap = 10.dp

    // Entry points (hand-off §7): Today's 48 dp + with a 26 dp add, 8 from Week.
    val todayAddIcon = 26.dp
    val todayHeaderButtonGap = 8.dp

    // The week's pressed ghost (9 §4.7): a 1 dp dashed `accent` outline, radius 12, an 18 dp add before the label, the
    // dashes 6 on and 4 off.
    val ghostRadius = 12.dp
    val ghostStroke = 1.dp
    val ghostIcon = 18.dp
    val ghostIconGap = 4.dp
    val ghostDash = 6.dp
    val ghostGap = 4.dp

    // The week's lane of multi-day bars (9 §2.1): rows 41 high and 4 apart; a bar 7 in from its first and last columns'
    // edges, radius 12, a 4 dp colour edge at its start; an 18 dp date_range 15 from its start, 8 before the title,
    // the range 7 after it, 12 clear of the end.
    val laneRow = 41.dp
    val laneRowGap = 4.dp
    val laneBarInset = 7.dp
    val laneBarRadius = 12.dp
    val laneEdge = 4.dp
    val laneIcon = 18.dp
    val lanePaddingStart = 15.dp
    val lanePaddingEnd = 12.dp
    val laneIconGap = 8.dp
    val laneTextGap = 7.dp

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
    /** The Show row's touch height: its switch is draw-only, so it brings no touch size of its own. */
    val reviewShowMinHeight = 48.dp
    val badgeRadius = 12.dp
    val badgePaddingV = 4.dp
    val badgePaddingH = 10.dp
    val connectStepWidth = 380.dp
    val connectStepHeight = 460.dp
}
