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
internal const val TURN_ALL_OFF = "Turn all lights off"
internal const val LOOKING_FOR_LIGHTS = "Looking for the lights…"
private const val AN_AUTOMATION = "an automation"
internal const val NO_BRIDGE_ANSWERED = "No Hue Bridge answered. Check it’s plugged in, or enter the bridge’s address."
internal const val TYPE_FOUR_NUMBERS = "Type the address as four numbers, like 192.168.1.30."
internal const val NO_BRIDGE_THERE = "No Hue Bridge answered at that address. Check it in the Hue app."
internal const val PRESS_THE_BUTTON = "Press the round button on top of the Hue Bridge"
internal const val NOT_PRESSED_IN_TIME = "The button wasn’t pressed in time. Try again."

/** What a room's button shows under its name: "On · 70%", "Off", or "On" for a room that can't dim (7 §5.1). */
internal fun roomState(room: LightRoom): String = when {
    !room.on -> OFF
    room.dimmable -> "$ON · ${room.brightness}%"
    else -> ON
}

/** "Lounge, on, 70 percent", "Lounge, off" (7 §5.4). */
internal fun roomSpoken(room: LightRoom): String = when {
    !room.on -> "${room.name}, off"
    room.dimmable -> "${room.name}, on, ${room.brightness} percent"
    else -> "${room.name}, on"
}

/** What tapping the room's button does: "Turn the Lounge on" when [on] is the state it turns to. */
internal fun turnSpoken(room: LightRoom, on: Boolean): String = "Turn the ${room.name} ${if (on) "on" else "off"}"

/** What tapping Holiday mode does: "Turn Holiday mode on" when [on] is the state it turns to. */
internal fun holidaySpoken(on: Boolean): String = "Turn $HOLIDAY_MODE ${if (on) "on" else "off"}"

/** "Lounge brightness": the slider's name; its state is the percent (7 §5.4). */
internal fun brightnessName(room: LightRoom): String = "${room.name} brightness"

/** "Relax scene in the Lounge" (7 §5.4). */
internal fun sceneSpoken(scene: LightScene, room: LightRoom): String = "${scene.name} scene in the ${room.name}"

internal fun openRoomSpoken(room: LightRoom): String = "Open the ${room.name} on the Lights tab"

/** Holiday mode's state under its name: its record, or what the run is doing (7 §2.6). */
internal fun holidayState(on: Boolean, run: HouseRun?): String = when (run) {
    HouseRun.TURNING_ON -> TURNING_ON
    HouseRun.TURNING_OFF -> TURNING_OFF
    HouseRun.ALL_OFF, null -> if (on) ON else OFF
}

/** "Holiday mode, off": the button's name before the action it does (7 §5.4). */
internal fun holidayNamed(on: Boolean, run: HouseRun?): String = "$HOLIDAY_MODE, ${holidayState(on, run).removeSuffix("…").lowercase()}"

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
