package uk.co.siland.culvery.core.ui

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** A quiet fill carries no WCAG ratio (the label names the button) but must not vanish into its surface (4d-1 Decision 9). */
private const val QUIET_FILL_MIN = 1.1

/** The moon and bats carry no information; they only have to be seen. */
private const val SKY_MIN = 1.2

private const val TEXT = 4.5

/** A disabled control is exempt from WCAG contrast; its `mute` label must still read as text on the surface it sits on. */
private const val DISABLED_TEXT_MIN = 1.8

/** WCAG 2.x: large text (≥ 18.66 sp bold or 24 sp), icons and other meaningful parts. */
private const val NON_TEXT = 3.0

/**
 * Every text and meaningful non-text pair the screens draw, in both themes, with alphas blended as drawn. A ratio that
 * sits near its line is pinned at its value today, so a token change that lowers it fails here first.
 */
class ContrastTest {
    private val themes = (listOf(null) + SeasonalTheme.entries).flatMap { seasonal ->
        listOf(true, false).map { dark -> "${seasonal?.label ?: "normal"} ${if (dark) "night" else "day"}" to colorsFor(seasonal, dark) }
    }

    private val halloween = SeasonalTheme.entries.flatMap { listOf("${it.label} night" to colorsFor(it, true), "${it.label} day" to colorsFor(it, false)) }

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
                        if (enabled) check("$name $tone enabled on $on", ink, fill, TEXT)
                        else check("$name $tone disabled on $on", ink, surfaceOf(on, t), DISABLED_TEXT_MIN)
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

    /** The toast pill is inverted, so its action takes the other theme's accent. */
    @Test
    fun accentOnInk() {
        themes.forEach { (name, t) -> check("$name toast action on ink", t.accentOnInk, t.ink, TEXT) }
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
    fun rainIconsReadOnTheirSurfaces() {
        themes.forEach { (name, t) ->
            listOf("bg" to t.bg, "surf" to t.surf).forEach { (surface, bg) -> check("$name rain on $surface", t.rain, bg, NON_TEXT) }
        }
    }

    @Test
    fun aChipFilledWithAPersonsColourReadsOnEveryColour() {
        (PersonPalette.colors + FAMILY_VIOLET).forEach { colour ->
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
            (PersonPalette.colors + FAMILY_VIOLET).forEach { colour ->
                val tint = Color(colour).over(HhAlpha.tint(t), t.surf)
                check("$name mute on the ${colour.toString(16)} tint", t.mute, tint, TEXT)
                check("$name ink on the ${colour.toString(16)} tint", t.ink, tint, TEXT)
            }
        }
    }

    @Test
    fun barTintMatchesTheHandoff() {
        assertWithMessage("dark bar tint").that(HhAlpha.bar(DarkColors)).isEqualTo(0x4D / 255f)
        assertWithMessage("light bar tint").that(HhAlpha.bar(LightColors)).isEqualTo(0x40 / 255f)
    }

    @Test
    fun inkOnEveryPersonsBarTint() {
        themes.forEach { (name, t) ->
            (PersonPalette.colors + FAMILY_VIOLET).forEach { colour ->
                listOf("surf" to t.surf, "bg" to t.bg).forEach { (under, base) ->
                    val bar = Color(colour).over(HhAlpha.bar(t), base)
                    check("$name ink on the ${colour.toString(16)} bar over $under", t.ink, bar, TEXT)
                }
            }
        }
    }

    @Test
    fun theTintEdgesStayWhereTheyAre() {
        fun darkTint(colour: Long) = Color(colour).over(HhAlpha.tint(DarkColors), DarkColors.surf)
        check("dark mute on the lime tint (4.548)", DarkColors.mute, darkTint(0xFF9CC44E), 4.54)
    }

    @Test
    fun aPastDayReadsAtItsAlpha() {
        themes.forEach { (name, t) ->
            check("$name past day: the cell at its alpha over surf", t.ink.over(HhAlpha.PAST_DAY, t.surf), t.surf2.over(HhAlpha.PAST_DAY, t.surf), TEXT)
        }
        val light = LightColors
        check("light past day (4.830)", light.ink.over(HhAlpha.PAST_DAY, light.surf), light.surf2.over(HhAlpha.PAST_DAY, light.surf), 4.82)
    }

    /** The Day pane draws its cells straight on the pane's `surf`; the value is the lowest that keeps 4.5:1 in both themes. */
    @Test
    fun outsideMonthDaysKeepTheirContrast() {
        themes.forEach { (name, t) ->
            check("$name outside-month day: the date at its alpha over surf", t.ink.over(HhAlpha.OUTSIDE_MONTH, t.surf), t.surf, TEXT)
        }
        val lower = HhAlpha.OUTSIDE_MONTH - 0.01f
        val failing = themes.filter { (_, t) -> contrast(t.ink.over(lower, t.surf), t.surf) < TEXT }.map { it.first }
        assertWithMessage("a hundredth lower fails somewhere").that(failing).isNotEmpty()
    }

    /** The editor's Clash tag: `weather` words on `weather` at its alpha over `surf`, drawn opaque so an active row's fill doesn't show through. */
    @Test
    fun theClashTagReadsOnTheRow() {
        themes.forEach { (name, t) -> check("$name clash tag", t.weather, t.weather.over(HhAlpha.clash(t), t.surf), TEXT) }
        val light = LightColors
        check("light clash tag (4.552)", light.weather, light.weather.over(HhAlpha.clash(light), light.surf), 4.55)
    }

    /** Warning red must not read as the theme's accent (14 §1). **pin** for the Halloween entries: they passed against the stubs. */
    @Test
    fun dangerAndAccentStayApart() {
        themes.forEach { (name, t) ->
            assertWithMessage("$name: the hue gap between danger and accent").that(hueGap(t.danger, t.accent)).isAtLeast(35f)
        }
    }

    /** No text is drawn over the sky (it sits in the header's open band), so the moon and bats only need to stand off the background (14 §4.3). **pin**: it passed against the stubs. */
    @Test
    fun theMoonAndBatsStandOffTheBackground() {
        halloween.forEach { (name, t) ->
            check("$name moon on bg", HalloweenInks.moon(t), t.bg, SKY_MIN)
            check("$name bat on bg", HalloweenInks.bat(t), t.bg, SKY_MIN)
        }
    }

    /** **pin**: the web sits under card text, and the text reads over it (14 §4.3). It passed against the stubs. */
    @Test
    fun theWebReadsUnderCardText() {
        halloween.forEach { (name, t) ->
            val web = HalloweenInks.web(t)
            check("$name ink on the web", t.ink, web, TEXT)
            check("$name mute on the web", t.mute, web, TEXT)
        }
    }
}
