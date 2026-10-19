package uk.co.siland.culvery.shell.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The rail's width, line included (9 §4.1). */
internal val RAIL_WIDTH = 107.dp

/** The mark's width; the space above it and to its sides is `(RAIL_WIDTH − MARK_WIDTH) / 2`. */
internal val MARK_WIDTH = 56.dp

/** The shell's frame: the rail, the top bar and the main area (9 §4.1, §6.7). */
internal object ShellDimens {
    val railLine = 1.dp
    val railBottom = 20.dp
    val markGapBelow = 51.dp

    val navItemWidth = 88.dp
    val navItemGap = 15.dp
    val navPillWidth = 64.dp
    val navPillHeight = 37.dp
    val navPillToLabel = 6.dp
    val navIcon = 26.dp
    val navLabel = 13.sp

    val settingsTile = 80.dp
    val settingsTileRadius = 20.dp
    val settingsIcon = 30.dp
    val settingsGap = 5.dp

    val badgeMin = 19.dp
    val badgeRing = 2.dp
    val badgePaddingH = 5.dp
    val badgeText = 11.sp
    val badgeDot = 10.dp

    val mainStart = 29.dp
    val mainEnd = 28.dp
    val mainBottom = 23.dp

    /** A control's whole tap area: on the SM-T510 Compose didn't widen smaller targets (4e). */
    val topBarHeight = 48.dp
    val topBarGap = 19.dp

    val pillHeight = 29.dp
    val pillDot = 7.dp
    val pillIcon = 18.dp
    val pillGap = 6.dp
    val pillPaddingH = 12.dp
    val pillText = 13.sp

    val signedInIcon = 20.dp
    val signedInGap = 8.dp
    val signOutHeight = 29.dp
    val signOutPaddingH = 14.dp
    val signOutText = 13.sp
    val divider = 19.dp
    val dividerWidth = 1.dp

    val gridGap = 15.dp
    val leftColumn = 400.dp
    val clockToDate = 15.dp

    val themeIcon = 20.dp
    val themeGap = 6.dp
}
