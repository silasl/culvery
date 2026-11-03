package uk.co.siland.culvery.shell.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.plugin.HeaderItem
import uk.co.siland.culvery.core.ui.Birthday
import uk.co.siland.culvery.core.ui.BirthdayInks
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.HomeSkyBand
import uk.co.siland.culvery.core.ui.LocalHomeSkyBand
import uk.co.siland.culvery.core.ui.PersonPalette

/** Needs real glyph metrics and pixels: legacy Robolectric graphics fakes both. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GreetingTest {
    @get:Rule val compose = createComposeRule()
    private val at = LocalDateTime.of(2026, 3, 9, 11, 54)
    private val pink = PersonPalette.colors[2]
    private var birthday by mutableStateOf<Birthday?>(Birthday(pink, listOf("Sam")))
    private val item = HeaderItem("sample", 10) { Box(Modifier.size(360.dp, 60.dp)) }

    private fun show(width: Int? = null, band: HomeSkyBand? = null) = compose.setContent {
        CompositionLocalProvider(LocalHomeSkyBand provides band) {
            CulveryTheme(dark = true, birthday = birthday) {
                HomeHeader({ at }, listOf(item), if (width == null) Modifier.fillMaxWidth() else Modifier.width(width.dp))
            }
        }
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    @Test
    fun theGreetingSitsUnderTheDate() {
        show()
        val date = bounds("home_date")
        val greeting = bounds("home_greeting")
        assertThat((greeting.top - date.bottom).value).isWithin(1f).of(ShellDimens.greetingGap.value)
        assertThat(greeting.left.value).isWithin(0.5f).of(date.left.value)
    }

    @Test
    fun talkBackReadsTheGreeting() {
        show()
        compose.onNodeWithTag("home_greeting").assertTextEquals("Happy birthday Sam")
        val index = { tag: String -> compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.TraversalIndex] }
        assertThat(listOf("home_clock", "home_date", "home_greeting", "home_header_items").map(index))
            .isInStrictOrder()
    }

    @Test
    fun twoAndThreeNames() {
        birthday = Birthday(pink, listOf("Sam", "Alex"))
        show()
        compose.onNodeWithTag("home_greeting").assertTextEquals("Happy birthday Sam and Alex")
        birthday = Birthday(pink, listOf("Sam", "Alex", "Mia"))
        compose.waitForIdle()
        compose.onNodeWithTag("home_greeting").assertTextEquals("Happy birthday Sam, Alex and Mia")
    }

    @Test
    fun aLongGreetingWrapsClearOfTheItems() {
        birthday = Birthday(pink, listOf("Sam", "Alex", "Mia", "Jo"))
        show(width = 700)
        val lines = compose.onAllNodesWithText("Happy birthday Sam, Alex, Mia and Jo", useUnmergedTree = true).filterToOne(!hasTestTag("home_greeting")).textLayout().lineCount
        assertThat(lines).isEqualTo(2)
        assertThat(bounds("home_greeting").right.value).isAtMost(bounds("home_header_items").left.value - 28f + 1f)
    }

    /** **pin** */
    @Test
    fun theHeaderItemsDontMove() {
        show()
        val with = bounds("home_header_items")
        birthday = null
        compose.waitForIdle()
        assertThat(bounds("home_header_items")).isEqualTo(with)
    }

    /** **pin** */
    @Test
    fun noGreetingWithoutABirthday() {
        birthday = null
        show()
        compose.onAllNodesWithTag("home_greeting").assertCountEquals(0)
    }

    @Test
    fun theBandForgetsTheGreeting() {
        val band = HomeSkyBand()
        show(band = band)
        compose.waitForIdle()
        assertThat(band.greetingWidth).isGreaterThan(0)
        birthday = null
        compose.waitForIdle()
        assertThat(band.greetingWidth).isEqualTo(0)
    }

    @Test
    fun thePillIsThePersonsSoftColour() {
        show()
        compose.waitForIdle()
        val pill = bounds("home_greeting")
        val view = (compose.onRoot().fetchSemanticsNode().root as ViewRootForTest).view
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        val density = view.resources.displayMetrics.density
        val x = ((pill.right.value - 7f) * density).toInt()
        val y = ((pill.top.value + pill.bottom.value) / 2f * density).toInt()
        assertThat(image.getPixel(x, y)).isEqualTo(androidx.compose.ui.graphics.Color(0xFF382B33).toArgb())
    }

    @Test
    fun theIcingShowsOnThePill() {
        show()
        compose.waitForIdle()
        val cake = compose.onNodeWithTag("cake_mark", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val view = (compose.onRoot().fetchSemanticsNode().root as ViewRootForTest).view
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        val density = view.resources.displayMetrics.density
        val icing = BirthdayInks.ICING.toArgb()
        val found = ((cake.top.value * density).toInt() until (cake.bottom.value * density).toInt()).any { y ->
            ((cake.left.value * density).toInt() until (cake.right.value * density).toInt()).any { x -> image.getPixel(x, y) == icing }
        }
        assertThat(found).isTrue()
    }
}
