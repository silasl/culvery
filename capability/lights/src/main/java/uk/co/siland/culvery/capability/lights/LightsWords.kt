package uk.co.siland.culvery.capability.lights

internal const val LIGHTS = "Lights"
internal const val HUE_REFUSED = "Hue didn’t do that. Try again."
internal const val ALL_LIGHTS_OFF = "All lights off"
internal const val HOLIDAY_MODE = "Holiday mode"
internal const val ON = "On"
internal const val OFF = "Off"
internal const val TURNING_ON = "Turning on…"
internal const val TURNING_OFF = "Turning off…"
internal const val CANT_REACH_HUE = "Culvery can’t reach the Hue Bridge. Check it’s plugged in and the tablet is on the home Wi-Fi."
internal const val TABLET_OFFLINE = "The tablet is offline. Check the Wi-Fi."
internal const val HUE_FORGOT = "The Hue Bridge no longer knows Culvery. Pair it again in Settings › Lights."
internal const val OTHER_BRIDGE = "Culvery found a different Hue Bridge. Find Hue Bridge again in Settings › Lights."
private const val AN_AUTOMATION = "an automation"

internal fun problemLine(problem: LightsProblem): String = when (problem) {
    LightsProblem.UNREACHABLE -> CANT_REACH_HUE
    LightsProblem.OFFLINE -> TABLET_OFFLINE
    LightsProblem.UNPAIRED -> HUE_FORGOT
    LightsProblem.OTHER_BRIDGE -> OTHER_BRIDGE
}

internal fun roomsLeftOnLine(count: Int): String = if (count == 1) "1 room didn’t turn off. Try again." else "$count rooms didn’t turn off. Try again."

/** [names] by the last list read from the bridge; a null is one whose name isn't known (7 §4.5). */
internal fun notStartedLine(names: List<String?>): String {
    val said = names.map { it ?: AN_AUTOMATION }.distinct()
    val joined = if (said.size == 1) said[0] else said.dropLast(1).joinToString(", ") + " and " + said.last()
    return "Lights are off, but Culvery couldn’t start $joined. Start it in the Hue app."
}

internal fun houseLine(result: AllOffResult): String? = when (result) {
    AllOffResult.Done -> null
    is AllOffResult.RoomsLeftOn -> roomsLeftOnLine(result.count)
    AllOffResult.Refused -> HUE_REFUSED
    is AllOffResult.CantReach -> problemLine(result.problem)
}

/** [nameOf] gives an automation's name from its id. */
internal fun houseLine(result: HolidayResult, nameOf: (String) -> String?): String? = when (result) {
    HolidayResult.Done, HolidayResult.Busy -> null
    HolidayResult.Refused -> HUE_REFUSED
    is HolidayResult.CantReach -> problemLine(result.problem)
    is HolidayResult.Partly -> listOfNotNull(
        result.roomsLeftOn.takeIf { it > 0 }?.let(::roomsLeftOnLine),
        result.notStarted.takeIf { it.isNotEmpty() }?.let { ids -> notStartedLine(ids.map(nameOf)) },
    ).joinToString(" ").ifEmpty { null }
}
