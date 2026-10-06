package uk.co.siland.culvery.core.ui

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** A quiet fill carries no WCAG ratio (the label names the button) but must not vanish into its surface (4d-1 Decision 9). */
private const val QUIET_FILL_MIN = 1.1

private const val TEXT = 4.5

/** WCAG 2.x: large text (≥ 18.66 sp bold or 24 sp), icons and other meaningful parts. */
private const val NON_TEXT = 3.0

/**
 * Every text and meaningful non-text pair the screens draw, in both themes, with alphas blended as drawn. A ratio that
 * sits near its line is pinned at its value today, so a token change that lowers it fails here first.
 */
class ContrastTest {
    private val themes = listOf("dark" to DarkColors, "light" to LightColors)

    private fun surfaceOf(on: ButtonSurface, t: HhColors) = when (on) {
        ButtonSurface.Bg -> t.bg
        ButtonSurface.Surf -> t.surf
        ButtonSurface.DangerSoft -> t.dangerSoft
    }

    private fun check(what: String, fg: Color, bg: Color, min: Double) =
        assertWithMessage(what).that(contrast(fg, bg)).isAtLeast(min)

    @Test
    fun everyQuietFillStandsOffItsSurface() {
        themes.forEach { (name, t) ->
            ButtonSurface.entries.forEach { on -> check("$name quiet fill on $on", quietFill(on, t), surfaceOf(on, t), QUIET_FILL_MIN) }
        }
    }

    @Test
    fun everyButtonsLabelOnItsFill() {
        themes.forEach { (name, t) ->
            ButtonTone.entries.forEach { tone ->
                listOf(true, false).forEach { enabled ->
                    ButtonSurface.entries.forEach { on ->
                        val (fill, ink) = buttonColors(tone, enabled, on, t)
                        check("$name $tone enabled=$enabled on $on", ink, fill, TEXT)
                    }
                }
            }
        }
    }

    @Test
    fun inkAndMuteOnEverySurface() {
        themes.forEach { (name, t) ->
            listOf("bg" to t.bg, "surf" to t.surf, "surf2" to t.surf2, "surf3" to t.surf3, "accentSoft" to t.accentSoft, "dangerSoft" to t.dangerSoft)
                .forEach { (surface, bg) ->
                    check("$name ink on $surface", t.ink, bg, TEXT)
                    check("$name mute on $surface", t.mute, bg, TEXT)
                }
        }
    }

    @Test
    fun accentAsText() {
        themes.forEach { (name, t) ->
            listOf("bg" to t.bg, "surf" to t.surf, "surf2" to t.surf2, "accentSoft" to t.accentSoft)
                .forEach { (surface, bg) -> check("$name accent on $surface", t.accent, bg, TEXT) }
        }
    }

    @Test
    fun dangerTextIconsAndToasts() {
        themes.forEach { (name, t) ->
            listOf("bg" to t.bg, "surf" to t.surf, "surf2" to t.surf2).forEach { (surface, bg) -> check("$name danger on $surface", t.danger, bg, TEXT) }
            // Only icons draw danger on dangerSoft: the reconnect chip, the failure card, the confirm card.
            check("$name danger icon on dangerSoft", t.danger, t.dangerSoft, NON_TEXT)
            check("$name toast: bg on ink", t.bg, t.ink, TEXT)
        }
    }

    @Test
    fun theWeatherColourReadsAsAnIconAndAsText() {
        themes.forEach { (name, t) ->
            listOf("bg" to t.bg, "surf" to t.surf, "surf2" to t.surf2).forEach { (surface, bg) -> check("$name weather on $surface", t.weather, bg, TEXT) }
        }
    }

    @Test
    fun aChipFilledWithAPersonsColourReadsOnEveryColour() {
        (PersonPalette.colors + FAMILY_AMBER).forEach { colour ->
            check("chip ink on ${colour.toString(16)}", PersonChipInk, Color(colour), TEXT)
        }
    }

    @Test
    fun theEdgesStayWhereTheyAre() {
        check("light danger on dangerSoft (4.497)", LightColors.danger, LightColors.dangerSoft, 4.49)
        check("light mute on surf3 (4.705)", LightColors.mute, LightColors.surf3, 4.70)
    }

    @Test
    fun namesAndTitlesOnEveryPersonTint() {
        themes.forEach { (name, t) ->
            (PersonPalette.colors + FAMILY_AMBER).forEach { colour ->
                val tint = Color(colour).over(HhAlpha.tint(t), t.surf)
                check("$name mute on the ${colour.toString(16)} tint", t.mute, tint, TEXT)
                check("$name ink on the ${colour.toString(16)} tint", t.ink, tint, TEXT)
            }
        }
    }

    @Test
    fun theTintEdgesStayWhereTheyAre() {
        fun darkTint(colour: Long) = Color(colour).over(HhAlpha.tint(DarkColors), DarkColors.surf)
        check("dark mute on the lime tint (4.548)", DarkColors.mute, darkTint(0xFF9CC44E), 4.54)
        check("dark mute on the Family tint (4.602)", DarkColors.mute, darkTint(FAMILY_AMBER), 4.60)
    }

    @Test
    fun aPastDayReadsAtItsAlpha() {
        themes.forEach { (name, t) ->
            check("$name past day: the cell at its alpha over surf", t.ink.over(HhAlpha.PAST_DAY, t.surf), t.surf2.over(HhAlpha.PAST_DAY, t.surf), TEXT)
        }
        val light = LightColors
        check("light past day (4.830)", light.ink.over(HhAlpha.PAST_DAY, light.surf), light.surf2.over(HhAlpha.PAST_DAY, light.surf), 4.82)
    }
}
