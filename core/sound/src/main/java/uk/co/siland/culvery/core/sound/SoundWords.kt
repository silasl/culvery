package uk.co.siland.culvery.core.sound

// The line under a tablet sound's row, wherever a sound is chosen (18 §5).
const val ON_THIS_TABLET = "On this tablet"
const val NOT_ON_THIS_TABLET = "Not on this tablet any more"

/** The line under a sound's row: null for a built-in, "On this tablet", and for a theme's file "On this tablet · Halloween". */
val Sound.tabletLine: String?
    get() = when {
        !onTablet -> null
        theme != null -> "$ON_THIS_TABLET · ${theme.label}"
        else -> ON_THIS_TABLET
    }
