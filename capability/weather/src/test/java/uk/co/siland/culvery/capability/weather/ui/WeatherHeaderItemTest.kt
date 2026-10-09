package uk.co.siland.culvery.capability.weather.ui

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.weather.Condition
import uk.co.siland.culvery.capability.weather.HeaderWeather
import uk.co.siland.culvery.core.ui.CulveryTheme

@RunWith(AndroidJUnit4::class)
class WeatherHeaderItemTest {
    @get:Rule val compose = createComposeRule()

    /** One item that says the condition, not "17°" and "High 19° · Low 11°" apart. */
    @Test
    fun theHeaderIsOneItemThatSaysTheCondition() {
        compose.setContent { CulveryTheme(dark = true) { WeatherHeaderItem(HeaderWeather(Condition.PARTLY_CLOUDY, night = false, temperature = 17.0, high = 19.0, low = 11.0)) } }
        compose.onNodeWithTag("weather_header").assertContentDescriptionEquals("Now 17°, partly cloudy, high 19°, low 11°")
        // One merged node holds both texts and the description; without the merge each text is its own node.
        compose.onAllNodes(hasText("17°", substring = true)).assertCountEquals(1)
        compose.onAllNodes(hasText("17°", substring = true))[0].assertContentDescriptionEquals("Now 17°, partly cloudy, high 19°, low 11°")
        compose.onAllNodes(hasText("High 19°", substring = true))[0].assertContentDescriptionEquals("Now 17°, partly cloudy, high 19°, low 11°")
        compose.onNodeWithTag("weather_header").onChildren().assertCountEquals(0)
    }
}
