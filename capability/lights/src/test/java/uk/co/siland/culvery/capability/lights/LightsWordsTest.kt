package uk.co.siland.culvery.capability.lights

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 7 §6: the lines All lights off and Holiday mode show. */
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

    /** Fails if a result that went well shows a line, or one that didn't shows the wrong one. */
    @Test
    fun allOffLines() {
        assertThat(houseLine(AllOffResult.Done)).isNull()
        assertThat(houseLine(AllOffResult.RoomsLeftOn(2))).isEqualTo("2 rooms didn’t turn off. Try again.")
        assertThat(houseLine(AllOffResult.Refused)).isEqualTo("Hue didn’t do that. Try again.")
        assertThat(houseLine(AllOffResult.CantReach(LightsProblem.OFFLINE))).isEqualTo("The tablet is offline. Check the Wi-Fi.")
    }

    /** Fails if a Holiday mode result's line is missing, wrong, or shown for a run that went well or was ignored. */
    @Test
    fun holidayLines() {
        val names = mapOf("mimic-id" to "Mimic presence")
        assertThat(houseLine(HolidayResult.Done, names::get)).isNull()
        assertThat(houseLine(HolidayResult.Busy, names::get)).isNull()
        assertThat(houseLine(HolidayResult.Refused, names::get)).isEqualTo("Hue didn’t do that. Try again.")
        assertThat(houseLine(HolidayResult.CantReach(LightsProblem.UNPAIRED), names::get))
            .isEqualTo("The Hue Bridge no longer knows Culvery. Pair it again in Settings › Lights.")
        assertThat(houseLine(HolidayResult.Partly(1, emptyList()), names::get)).isEqualTo("1 room didn’t turn off. Try again.")
        assertThat(houseLine(HolidayResult.Partly(0, listOf("mimic-id", "gone-id")), names::get))
            .isEqualTo("Lights are off, but Culvery couldn’t start Mimic presence and an automation. Start it in the Hue app.")
    }
}
