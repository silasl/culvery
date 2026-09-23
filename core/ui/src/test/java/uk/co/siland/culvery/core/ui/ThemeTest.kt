package uk.co.siland.culvery.core.ui

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
        assertThat(symbols).isGreaterThan(1_000_000)
    }
}
