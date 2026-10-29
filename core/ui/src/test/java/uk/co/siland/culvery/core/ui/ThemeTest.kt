package uk.co.siland.culvery.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private var probeCompositions = 0

/** Reads the theme as content does; counts its compositions. */
@Composable
private fun Probe() {
    probeCompositions++
    Text("probe", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge.copy(color = Culvery.colors.ink))
}

@RunWith(AndroidJUnit4::class)
class ThemeTest {
    @get:Rule val compose = createComposeRule()
    private var nightOn by mutableStateOf(true)

    @Test
    fun darkThemeProvidesDarkTokens() {
        var seen: HhColors? = null
        compose.setContent { CulveryTheme(dark = true) { seen = Culvery.colors } }
        compose.waitForIdle()
        assertThat(seen).isEqualTo(DarkColors)
    }

    @Test
    fun theCardEdgeIsLightOnly() {
        assertThat(DarkColors.border).isEqualTo(Color.Transparent)
        assertThat(LightColors.border).isEqualTo(Color(0xFFD5DCD8))
    }

    @Test
    fun rainIsBlueInBothThemes() {
        assertThat(DarkColors.rain).isEqualTo(Color(0xFF7FB2E5))
        assertThat(LightColors.rain).isEqualTo(Color(0xFF2F6FAE))
    }

    @Test
    fun lightThemeProvidesLightTokens() {
        var seen: HhColors? = null
        compose.setContent { CulveryTheme(dark = false) { seen = Culvery.colors } }
        compose.waitForIdle()
        assertThat(seen).isEqualTo(LightColors)
    }

    @Test
    fun bundledFontsAreRealFiles() {
        val res = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        val dmSans = res.openRawResource(R.font.dm_sans).use { it.readBytes().size }
        val symbols = res.openRawResource(R.font.material_symbols_rounded).use { it.readBytes().size }
        assertThat(dmSans).isGreaterThan(100_000)
        // The subset of the glyphs Icons names (4c §3.5): about 160 KB; the full font is 15 MB.
        assertThat(symbols).isGreaterThan(50_000)
        assertThat(symbols).isLessThan(1_000_000)
    }

    @Test
    fun dangerTokensMatchTheHandOff() {
        assertThat(listOf(DarkColors.danger, DarkColors.dangerSoft, DarkColors.dangerInk))
            .containsExactly(Color(0xFFEE7B6A), Color(0xFF3A211D), Color(0xFF1A0906)).inOrder()
        assertThat(listOf(LightColors.danger, LightColors.dangerSoft, LightColors.dangerInk))
            .containsExactly(Color(0xFFB83A28), Color(0xFFF7DFDA), Color(0xFFFFFFFF)).inOrder()
    }

    /** P1: the theme rebuilt with the same colours hands its content the same scheme, so content that hasn't changed skips. */
    @Test
    fun aRecomposedThemeKeepsItsSchemeSoUnchangedContentSkips() {
        var tick by mutableIntStateOf(0)
        compose.setContent {
            val t = tick
            // A new lambda instance each tick, so CulveryTheme itself runs again while Probe's group is unchanged. The
            // `t < 0` branch never runs: it only makes the lambda capture the tick.
            val content: @Composable () -> Unit = remember(t) { { Probe(); if (t < 0) Text("never") } }
            CulveryTheme(dark = true, content = content)
        }
        compose.waitForIdle()
        val before = probeCompositions
        tick++
        compose.waitForIdle()
        assertThat(probeCompositions).isEqualTo(before)
    }

    @Test
    fun halloweenProvidesItsSets() {
        var night: Triple<HhColors, SeasonalTheme?, SeasonalLook?>? = null
        var day: HhColors? = null
        compose.setContent {
            if (nightOn) CulveryTheme(dark = true, seasonal = SeasonalTheme.HALLOWEEN) { night = Triple(Culvery.colors, Culvery.seasonal, Culvery.look) }
            else CulveryTheme(dark = false, seasonal = SeasonalTheme.HALLOWEEN) { day = Culvery.colors }
        }
        compose.waitForIdle()
        assertThat(night!!.first).isEqualTo(HalloweenNight)
        assertThat(night!!.second).isEqualTo(SeasonalTheme.HALLOWEEN)
        assertThat(night!!.third).isSameInstanceAs(HalloweenLook)
        nightOn = false
        compose.waitForIdle()
        assertThat(day).isEqualTo(HalloweenDay)
    }

    /** **pin**: no theme is the look the family has today. */
    @Test
    fun noThemeIsTheNormalLook() {
        var seen: Triple<HhColors, SeasonalTheme?, SeasonalLook?>? = null
        compose.setContent { CulveryTheme(dark = true) { seen = Triple(Culvery.colors, Culvery.seasonal, Culvery.look) } }
        compose.waitForIdle()
        assertThat(seen!!.first).isEqualTo(DarkColors)
        assertThat(seen!!.second).isNull()
        assertThat(seen!!.third).isNull()
        assertThat(colorsFor(null, false)).isEqualTo(LightColors)
    }

    @Test
    fun halloweenKeepsTheLightOrDarkReading() {
        assertThat(HalloweenDay.isLight).isTrue()
        assertThat(HalloweenNight.isLight).isFalse()
        assertThat(HalloweenNight.border).isEqualTo(Color.Transparent)
    }

    /** **pin**: person colours are the same in every theme (14 §3). */
    @Test
    fun personColoursDontChange() {
        assertThat(PersonPalette.colors)
            .containsExactly(0xFF4CB387, 0xFF5B9BE0, 0xFFE07BA8, 0xFF9CC44E, 0xFFE04A26, 0xFFCEC018).inOrder()
        assertThat(PersonChipInk).isEqualTo(Color(0xFF0E1011))
    }

    @Test
    fun halloweenKeepsWeatherAndRain() {
        assertThat(HalloweenNight.weather).isEqualTo(DarkColors.weather)
        assertThat(HalloweenNight.rain).isEqualTo(DarkColors.rain)
        assertThat(HalloweenDay.weather).isEqualTo(LightColors.weather)
        assertThat(HalloweenDay.rain).isEqualTo(LightColors.rain)
    }
}
