package uk.co.siland.culvery.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
    private val looks: List<Pair<String, SeasonalLook?>> =
        listOf<Pair<String, SeasonalLook?>>("normal" to null) +
            SeasonalTheme.entries.map { it.label to it.look } +
            PersonPalette.colors.map { "${PersonPalette.nameOf(it)} birthday" to birthdayLook(it) }

    private val themes = looks.flatMap { (name, look) ->
        listOf(true, false).map { dark -> "$name ${if (dark) "night" else "day"}" to colorsOf(look, dark) }
    }

    private val birthdays = PersonPalette.colors.flatMap { c ->
        listOf(true, false).map { dark -> "${PersonPalette.nameOf(c)} birthday ${if (dark) "night" else "day"}" to (c to colorsOf(birthdayLook(c), dark)) }
    }

    private fun setsOf(theme: SeasonalTheme): List<Pair<String, HhColors>> =
        listOf("${theme.label} night" to theme.look.night, "${theme.label} day" to theme.look.day)

    private val halloween = setsOf(SeasonalTheme.HALLOWEEN)
    private val christmas = setsOf(SeasonalTheme.CHRISTMAS)

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

    /** Warnings must not read as the weather (21 §3.3). */
    @Test
    fun dangerAndWeatherStayApart() {
        SeasonalTheme.entries.flatMap { setsOf(it) }.forEach { (name, t) ->
            assertWithMessage("$name: the hue gap between danger and weather").that(hueGap(t.danger, t.weather)).isAtLeast(35f)
        }
    }

    /**
     * **pin** (21 §3.3): the normal and birthday sets keep the gap they measure today, rounded down to 0.1 degree; Christmas
     * does not widen them. The end-of-v1 design review decides whether they move.
     */
    @Test
    fun theNormalAndBirthdayWarningGapsStayWhereTheyAre() {
        val today = mapOf(
            "normal night" to 34.2f, "normal day" to 35.9f,
            "Green birthday night" to 34.2f, "Green birthday day" to 35.9f,
            "Blue birthday night" to 34.2f, "Blue birthday day" to 35.9f,
            "Pink birthday night" to 22.4f, "Pink birthday day" to 26.7f,
            "Lime birthday night" to 34.2f, "Lime birthday day" to 35.9f,
            "Orange birthday night" to 72.6f, "Orange birthday day" to 75.5f,
            "Yellow birthday night" to 34.2f, "Yellow birthday day" to 35.9f,
        )
        themes.filter { it.first in today }.forEach { (name, t) ->
            assertWithMessage("$name: the hue gap between danger and weather").that(hueGap(t.danger, t.weather)).isAtLeast(today.getValue(name))
        }
        assertWithMessage("every normal and birthday set is pinned").that(themes.map { it.first }.filter { it.startsWith("normal") || "birthday" in it })
            .containsExactlyElementsIn(today.keys)
    }

    /** **pin**: rain and the weather's ice blue are told apart in every set (21 §3.3). It passes against the stub. */
    @Test
    fun rainAndWeatherStayApart() {
        themes.forEach { (name, t) ->
            assertWithMessage("$name: the hue gap between rain and weather").that(hueGap(t.rain, t.weather)).isAtLeast(35f)
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

    /** **pin**: the holly sits under card text and stands off the card (21 §3.7); the inks were added with this test, so it passed before the drawing. */
    @Test
    fun theHollyReadsUnderCardText() {
        christmas.forEach { (name, t) ->
            listOf("leaf" to ChristmasInks.hollyLeaf(t), "berry" to ChristmasInks.hollyBerry(t)).forEach { (part, ink) ->
                check("$name ink on the holly $part", t.ink, ink, TEXT)
                check("$name mute on the holly $part", t.mute, ink, TEXT)
                check("$name holly $part on surf", ink, t.surf, SKY_MIN)
            }
        }
    }

    /** The greeting pill is a soft fill on the background. */
    @Test
    fun theGreetingPillStandsOffTheBackground() {
        birthdays.forEach { (name, set) -> check("$name pill on bg", set.second.accentSoft, set.second.bg, QUIET_FILL_MIN) }
    }

    @Test
    fun eachBirthdayAccentKeepsThePersonsHue() {
        birthdays.forEach { (name, set) ->
            assertWithMessage("$name: the hue gap between accent and the person's colour")
                .that(hueGap(set.second.accent, Color(set.first))).isAtMost(12f)
        }
    }

    @Test
    fun balloonsAndFlagsStandOffTheBackground() {
        birthdays.forEach { (name, set) ->
            PersonPalette.colors.forEach { colour ->
                check("$name balloon ${colour.toString(16)} on bg", BirthdayInks.balloon(colour, set.second), set.second.bg, SKY_MIN)
                check("$name flag ${colour.toString(16)} on bg", BirthdayInks.flag(colour), set.second.bg, SKY_MIN)
            }
        }
    }

    /** An outlined stripe's boundary is its edge. */
    @Test
    fun everyBinInkStandsOffTheChip() {
        themes.forEach { (name, set) ->
            BinInks.all.forEach { ink ->
                val visible = BinInks.edge(ink, set) ?: BinInks.colour(ink, set)
                check("$name ${ink.id} on surf2", visible, set.surf2, NON_TEXT)
                check("$name ${ink.id} on surf", visible, set.surf, NON_TEXT)
            }
        }
    }

    /** Night black is a dark grey, so it can't be taken for the `mute` stripe of a bin we don't know. */
    @Test
    fun nightBlackIsDarkerThanTheUnknownStripe() {
        val black = BinInks.byId("black")!!
        themes.filter { !it.second.isLight }.forEach { (name, set) ->
            val fill = BinInks.colour(black, set)
            assertWithMessage("$name black is darker than mute").that(fill.luminance()).isLessThan(set.mute.luminance())
            check("$name black against mute", fill, set.mute, NON_TEXT)
        }
    }

    /** Spec 21 section 4.4: the snow drifts behind the clock, the date and the header items, so each still reads over a flake. **pin** against the stub, which put the snow on `bg`. */
    @Test
    fun textReadsOverTheSnow() {
        christmas.forEach { (name, t) ->
            val snow = ChristmasInks.snow(t)
            check("$name clock on the snow", t.accent, snow, NON_TEXT)
            check("$name ink on the snow", t.ink, snow, TEXT)
            check("$name mute on the snow", t.mute, snow, TEXT)
            check("$name weather icon on the snow", t.weather, snow, NON_TEXT)
            check("$name rain icon on the snow", t.rain, snow, NON_TEXT)
        }
    }

    /** The snow, the wire and each bulb, at full and at its dimmest, only have to be seen; an outlined bulb's outline is what stands off. */
    @Test
    fun theSnowAndLightsStandOffTheBackground() {
        christmas.forEach { (name, t) ->
            check("$name snow on bg", ChristmasInks.snow(t), t.bg, SKY_MIN)
            check("$name wire on bg", ChristmasInks.wire(t), t.bg, SKY_MIN)
            val outline = ChristmasInks.bulbOutline(t)
            ChristmasInks.bulbs(t).forEachIndexed { i, bulb ->
                if (outline != null) {
                    check("$name bulb $i outline on bg", outline, t.bg, SKY_MIN)
                } else {
                    check("$name bulb $i on bg", bulb, t.bg, SKY_MIN)
                    check("$name bulb $i dimmed on bg", bulb.over(ChristmasInks.BULB_DIM, t.bg), t.bg, SKY_MIN)
                }
            }
        }
    }

    /** **pin**: the inks are fixed values that already pass. */
    @Test
    fun theChristmasMarkStandsOffTheBackground() {
        christmas.forEach { (name, t) ->
            check("$name snow cap on bg", ChristmasInks.markSnow(t), t.bg, SKY_MIN)
            check("$name wreath on the house", ChristmasInks.wreath(t), t.ink, NON_TEXT)
            check("$name robin on bg", ChristmasInks.robin(t), t.bg, NON_TEXT)
        }
    }
}
