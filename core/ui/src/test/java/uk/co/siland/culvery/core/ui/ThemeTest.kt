package uk.co.siland.culvery.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun darkThemeProvidesDarkTokens() {
        var seen: HhColors? = null
        compose.setContent { CulveryTheme(dark = true) { seen = Culvery.colors } }
        compose.waitForIdle()
        assertThat(seen).isEqualTo(DarkColors)
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
}
