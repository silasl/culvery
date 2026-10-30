package uk.co.siland.culvery.capability.lights

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 7 §6: the lines All lights off and Holiday lights show. */
class LightsWordsTest {
    /** Fails if a problem's line isn't the spec's, word for word. */
    @Test
    fun theProblemLines() {
        assertThat(problemLine(LightsProblem.UNREACHABLE))
            .isEqualTo("Culvery can’t reach the Hue Bridge. Check it’s plugged in and the tablet is on the home Wi-Fi.")
        assertThat(problemLine(LightsProblem.OFFLINE)).isEqualTo("The tablet is offline. Check the Wi-Fi.")
        assertThat(problemLine(LightsProblem.UNPAIRED))
            .isEqualTo("The Hue Bridge no longer knows Culvery. Pair it again in Settings › Lights.")
        assertThat(problemLine(LightsProblem.OTHER_BRIDGE))
            .isEqualTo("Culvery found a different Hue Bridge. Find Hue Bridge again in Settings › Lights.")
    }

    /** Fails if one room is called "1 rooms", or the count isn't in the line. */
    @Test
    fun roomsLeftOn() {
        assertThat(roomsLeftOnLine(2)).isEqualTo("2 rooms didn’t turn off. Try again.")
        assertThat(roomsLeftOnLine(1)).isEqualTo("1 room didn’t turn off. Try again.")
    }

    /** Fails if the automation's name isn't in the spec's line, or several names aren't joined as a sentence. */
    @Test
    fun anAutomationNotStarted() {
        assertThat(notStartedLine(listOf("Mimic presence")))
            .isEqualTo("Lights are off, but Culvery couldn’t start Mimic presence. Start it in the Hue app.")
        assertThat(notStartedLine(listOf("A", "B"))).isEqualTo("Lights are off, but Culvery couldn’t start A and B. Start it in the Hue app.")
        assertThat(notStartedLine(listOf("A", "B", "C")))
            .isEqualTo("Lights are off, but Culvery couldn’t start A, B and C. Start it in the Hue app.")
    }

    /** Fails if an automation whose name isn't known shows a blank, "null" or an id. */
    @Test
    fun anAutomationWithNoKnownName() {
        assertThat(notStartedLine(listOf(null)))
            .isEqualTo("Lights are off, but Culvery couldn’t start an automation. Start it in the Hue app.")
    }

    /** Fails if a result that went well shows a line, one that didn't shows the wrong one, or can't reach outlasts the bridge's return. */
    @Test
    fun allOffLines() {
        assertThat(houseLine(AllOffResult.Done)).isNull()
        assertThat(houseLine(AllOffResult.RoomsLeftOn(2))).isEqualTo("2 rooms didn’t turn off. Try again.")
        assertThat(houseLine(AllOffResult.Refused)).isEqualTo("Hue didn’t do that. Try again.")
        assertThat(houseLine(AllOffResult.CantReach(LightsProblem.OFFLINE))).isNull()
        assertThat(houseLine(AllOffResult.CantReach(LightsProblem.UNREACHABLE))).isNull()
    }

    /** Fails if a Holiday lights result's line is missing, wrong, or shown for a run that went well, was ignored or couldn't reach the bridge. */
    @Test
    fun holidayLines() {
        val names = mapOf("mimic-id" to "Mimic presence")
        assertThat(houseLine(HolidayResult.Done, names::get)).isNull()
        assertThat(houseLine(HolidayResult.Busy, names::get)).isNull()
        assertThat(houseLine(HolidayResult.Refused, names::get)).isEqualTo("Hue didn’t do that. Try again.")
        assertThat(houseLine(HolidayResult.CantReach(LightsProblem.UNPAIRED), names::get)).isNull()
        assertThat(houseLine(HolidayResult.CantReach(LightsProblem.UNREACHABLE), names::get)).isNull()
        assertThat(houseLine(HolidayResult.Partly(1, emptyList()), names::get)).isEqualTo("1 room didn’t turn off. Try again.")
        assertThat(houseLine(HolidayResult.Partly(0, listOf("mimic-id", "gone-id")), names::get))
            .isEqualTo("Lights are off, but Culvery couldn’t start Mimic presence and an automation. Start it in the Hue app.")
    }

    /** Fails if the line after rooms that didn't turn off still says the lights are off. */
    @Test
    fun roomsLeftOnAndNotStartedDontSayTheLightsAreOff() {
        assertThat(houseLine(HolidayResult.Partly(2, listOf("mimic-id"))) { "Mimic presence" })
            .isEqualTo("2 rooms didn’t turn off. Try again. Culvery couldn’t start Mimic presence. Start it in the Hue app.")
    }

    /** 9 §4.4. Fails if the rooms-on count is wrong or still ends with the bridge's name. */
    @Test
    fun roomsOnCounts() {
        assertThat(roomsOn(listOf(room("Lounge", on = true), room("Kitchen"), room("Hall", on = true)))).isEqualTo("2 of 3 rooms on")
        assertThat(roomsOn(listOf(room("Kitchen")))).isEqualTo("0 of 1 room on")
        assertThat(roomsOn(listOf(room("Lounge", on = true)))).isEqualTo("1 of 1 room on")
    }

    /** 15a §4.5. Fails if a house with both, lights only or plugs only is titled anything else. */
    @Test
    fun titles() {
        assertThat(tabTitle(hasLights = true, hasPlugs = true)).isEqualTo("Lights and plugs")
        assertThat(tabTitle(hasLights = true, hasPlugs = false)).isEqualTo("Lights")
        assertThat(tabTitle(hasLights = false, hasPlugs = true)).isEqualTo("Plugs")
    }

    /** 15a §4.5. Fails if the subtitle isn't both counts joined by a dot, or the part there is, or null with neither. */
    @Test
    fun summaryJoinsWhatThereIs() {
        val rooms = listOf(room("Lounge", on = true), room("Kitchen", on = true))
        assertThat(tabSummary(rooms, "1 of 2 plugs on")).isEqualTo("2 of 2 rooms on · 1 of 2 plugs on")
        assertThat(tabSummary(rooms, null)).isEqualTo("2 of 2 rooms on")
        assertThat(tabSummary(emptyList(), "1 of 2 plugs on")).isEqualTo("1 of 2 plugs on")
        assertThat(tabSummary(emptyList(), null)).isNull()
    }

    /** 15a §4.5. Fails if the pill stops counting rooms while some are below, or has no words once only plugs are. */
    @Test
    fun morePill() {
        assertThat(morePill(1)).isEqualTo("1 more room")
        assertThat(morePill(2)).isEqualTo("2 more rooms")
        assertThat(morePill(0)).isEqualTo("More below")
    }

    /** 9 §4.4. Fails if the pill says "1 more rooms", or loses the plural past one. */
    @Test
    fun moreRoomsCounts() {
        assertThat(moreRooms(1)).isEqualTo("1 more room")
        assertThat(moreRooms(3)).isEqualTo("3 more rooms")
    }

    /** Fails if a room that can't dim shows a percentage, or an off room shows its brightness. */
    @Test
    fun aRoomsStateAndHowItIsSpoken() {
        assertThat(roomState(room("Lounge", on = true, brightness = 70))).isEqualTo("On · 70%")
        assertThat(roomState(room("Lounge"))).isEqualTo("Off")
        assertThat(roomState(room("Hall", on = true, dimmable = false))).isEqualTo("On")
        assertThat(roomSpoken(room("Lounge", on = true, brightness = 70))).isEqualTo("Lounge, on, 70 percent")
        assertThat(roomSpoken(room("Lounge"))).isEqualTo("Lounge, off")
        assertThat(roomSpoken(room("Hall", on = true, dimmable = false))).isEqualTo("Hall, on")
    }

    /** 9 §2.5. Fails if the scene is dropped, shown on a room that is off, or shown on a room that can't dim as a percentage. */
    @Test
    fun theCardStatusNamesTheScene() {
        val relax = listOf(RELAX)
        assertThat(cardStatus(room("Lounge", on = true, brightness = 70, scenes = relax, active = RELAX.id))).isEqualTo("On · 70% · Relax")
        assertThat(cardStatus(room("Lounge", on = true, brightness = 70, scenes = relax))).isEqualTo("On · 70%")
        assertThat(cardStatus(room("Hall", on = true, dimmable = false, scenes = relax, active = RELAX.id))).isEqualTo("On · Relax")
        assertThat(cardStatus(room("Lounge", on = false, scenes = relax, active = RELAX.id))).isEqualTo("Off")
    }

    /** Fails if an action names the wrong state, or the chevron's words differ from the spec's. */
    @Test
    fun whatEachControlDoes() {
        assertThat(turnSpoken(room("Lounge"), true)).isEqualTo("Turn the Lounge on")
        assertThat(turnSpoken(room("Lounge"), false)).isEqualTo("Turn the Lounge off")
        assertThat(TURN_ALL_OFF).isEqualTo("Turn all lights off")
        assertThat(holidaySpoken(true)).isEqualTo("Turn Holiday lights on")
        assertThat(holidaySpoken(false)).isEqualTo("Turn Holiday lights off")
        assertThat(openRoomSpoken(room("Lounge"))).isEqualTo("Open the Lounge on the Lights tab")
        assertThat(LOOKING_FOR_LIGHTS).isEqualTo("Looking for the lights…")
    }

    /** Fails if a run doesn't replace Holiday lights' record with "Turning on…" or "Turning off…", or All lights off hides it. */
    @Test
    fun holidayModesStateWhileItRuns() {
        assertThat(holidayState(false, null)).isEqualTo("Off")
        assertThat(holidayState(true, null)).isEqualTo("On")
        assertThat(holidayState(false, HouseRun.TURNING_ON)).isEqualTo("Turning on…")
        assertThat(holidayState(true, HouseRun.TURNING_OFF)).isEqualTo("Turning off…")
        assertThat(holidayState(true, HouseRun.ALL_OFF)).isEqualTo("On")
        assertThat(holidayNamed(false, null)).isEqualTo("Holiday lights, off")
        assertThat(holidayNamed(false, HouseRun.TURNING_ON)).isEqualTo("Holiday lights, turning on")
    }

    @Test fun nextSceneFollowsTheActiveOne() = assertThat(nextScene(room("Lounge", scenes = listOf(RELAX, READ, BRIGHT), active = READ.id))).isEqualTo(BRIGHT)

    @Test fun nextSceneWrapsRound() = assertThat(nextScene(room("Lounge", scenes = listOf(RELAX, READ), active = READ.id))).isEqualTo(RELAX)

    @Test fun withNoActiveSceneTheFirstIsNext() = assertThat(nextScene(room("Lounge", scenes = listOf(RELAX, READ), active = null))).isEqualTo(RELAX)

    @Test fun anUnknownActiveSceneCountsAsNone() = assertThat(nextScene(room("Lounge", scenes = listOf(RELAX), active = "gone"))).isEqualTo(RELAX)

    @Test fun noScenesNoNext() = assertThat(nextScene(room("Lounge"))).isNull()

    @Test fun theNameIsNoneWithoutAnActiveScene() = assertThat(currentSceneName(room("Lounge", scenes = listOf(RELAX), active = null))).isEqualTo("None")

    @Test fun thePillIsSpokenWithTheScene() =
        assertThat(scenePillSpoken(room("Lounge", scenes = listOf(RELAX), active = RELAX.id))).isEqualTo("Scene, Relax")
}
