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

    // Coming up card: padding 20 × 22, like the hand-off's WIDE Scenes card.
    val comingUpPaddingV = 20.dp
    val comingUpPaddingH = 22.dp

    // Connect card: padding 20 × 22, 4 dp above the subtitle, like the hand-off's Holiday tile.
    val connectPaddingV = 20.dp
    val connectPaddingH = 22.dp
    val connectSubtitleTop = 4.dp

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

    /** Chip tint: the person's colour at 0x2E alpha on dark, 0x26 on light. */
    const val CHIP_ALPHA_DARK = 0x2E / 255f
    const val CHIP_ALPHA_LIGHT = 0x26 / 255f
}
