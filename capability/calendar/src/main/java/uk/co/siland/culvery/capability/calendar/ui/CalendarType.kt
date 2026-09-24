package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.co.siland.culvery.core.ui.HhType

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

    /** 14 sp / 600: the "Week" pill and the reconnect chip. */
    val pill = strong14

    /** 14 sp / 400: header text links ("Week ›"). */
    val link = HhType.secondary

    /** 34 sp / 700, −0.5 tracking: the detail sheet's title. */
    val sheetTitle = weekTitle

    /** 15 sp / 400: info-row labels. */
    val infoLabel = subtitle

    /** 17 sp / 600: info-row values. */
    val infoValue = HhType.rowTitle

    /** 13 sp / 700: "Syncing to …". */
    val syncingPill = HhType.label.copy(fontWeight = FontWeight.W700)

    /** 16 sp / 700: explanation card titles. */
    val noteTitle = HhType.body.copy(fontWeight = FontWeight.W700)

    /** 14 sp / 400: explanation card text. */
    val noteBody = HhType.secondary

    /** 15 sp / 700: "Assign to…". */
    val assignButton = HhType.buttonLabel

    /** 16 sp / 600: person chips. */
    val personChip = HhType.body.copy(fontWeight = FontWeight.W600)

    /** 17 sp / 700: Delete, Keep event, Delete event. */
    val footerButton = HhType.rowTitle.copy(fontWeight = FontWeight.W700)

    /** 18 sp / 700: "Delete this event?". */
    val confirmTitle = HhType.body.copy(fontSize = 18.sp, fontWeight = FontWeight.W700)
}

/** Layout values from Culvery.dc.html, named once so no card or view carries magic numbers. */
internal object CalendarDimens {
    // Pills and touch targets (hand-off §7: "Week" chip 44 dp tall, radius 22).
    val touchTarget = 44.dp
    val pillRadius = 22.dp
    val pillPaddingH = 16.dp

    // Today card: header→first row = 10 gap + 4 margin; row bar→text 14; time line 3 below the title.
    val todayHeaderGap = 14.dp
    val todayRowGap = 10.dp
    val todayBarGap = 14.dp
    val todayTimeTop = 3.dp
    val todayBadge = 20.dp

    // Shared card radius (Today, Coming up, Connect).
    val cardRadius = 26.dp

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

    // Week columns: header inset 4 at the sides and bottom; chip title 2 below the time; badge 15.
    val columnHeaderInset = 4.dp
    val columnGap = 8.dp
    val chipTitleTop = 2.dp
    val chipBadge = 15.dp

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
    // 22 dp icon, 104 dp label column, 12 dp person dot.
    val infoRadius = 22.dp
    val infoPaddingV = 2.dp
    val infoPaddingH = 18.dp
    val infoRowMin = 58.dp
    val infoDivider = 1.dp
    val infoIcon = 22.dp
    val infoIconGap = 12.dp
    val infoLabelWidth = 104.dp
    val infoDot = 12.dp
    val infoDotGap = 8.dp

    // Explanation cards (read-only, repeating, untagged): radius 22, padding 16×18, 26 dp icon.
    val noteRadius = 22.dp
    val notePaddingV = 16.dp
    val notePaddingH = 18.dp
    val noteIcon = 26.dp
    val noteIconGap = 14.dp
    val noteTextGap = 2.dp

    // Assign: "Assign to…" 44 dp, radius 22; person chips 48 dp, `surf`, radius 24, 12 dp dot, 8 apart.
    val assignTop = 12.dp
    val assignButtonHeight = 44.dp
    val assignButtonRadius = 22.dp
    val assignButtonPaddingH = 18.dp
    val personChipHeight = 48.dp
    val personChipRadius = 24.dp
    val personChipPaddingH = 18.dp
    val personChipGap = 8.dp
    val personChipDot = 12.dp
    val personChipDotGap = 8.dp

    // Footer: Delete 60 dp, padding 0 26, radius 30, `surf2`, `danger` text.
    val footerButtonHeight = 60.dp
    val footerButtonRadius = 30.dp
    val deleteButtonPaddingH = 26.dp
    val footerIcon = 24.dp
    val footerIconGap = 8.dp

    // Delete confirmation: `dangerSoft`, radius 24, padding 20, 16 between blocks; buttons 12 apart.
    val confirmRadius = 24.dp
    val confirmPadding = 20.dp
    val confirmGap = 16.dp
    val confirmButtonGap = 12.dp

    /** Chip tint: the person's colour at 0x2E alpha on dark, 0x26 on light. */
    const val CHIP_ALPHA_DARK = 0x2E / 255f
    const val CHIP_ALPHA_LIGHT = 0x26 / 255f
}
