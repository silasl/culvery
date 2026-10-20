package uk.co.siland.culvery.shell.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.door.BitmapSnapshot
import uk.co.siland.culvery.capability.door.ui.DoorCard
import uk.co.siland.culvery.capability.door.ui.DoorCardUi
import uk.co.siland.culvery.core.ui.CulveryTheme

/** 5 §5.1: a real door card, picture and all, in the alert layer on the SM-T510 with its keyboard up. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h800dp-land-hdpi")
class DoorCardAboveKeyboardTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val folder = TemporaryFolder()

    private val keyboard = 360.dp

    /** Fails if the picture takes the height Got it needs (squeezed under 48 dp or under the keys), or the card isn't 747 dp and centred. */
    @Test
    fun gotItKeepsItsHeightAboveTheKeyboardWhenTheCardHasAPicture() {
        val file = File(folder.root, "1.jpg")
        BitmapSnapshot(Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }).saveTo(file)
        compose.setContent {
            CulveryTheme(dark = true) {
                AlertLayer({ DoorCard(DoorCardUi(false, "Front Door", "14:02", file), onGotIt = {}) }, hidden = false, keyboard = WindowInsets(bottom = keyboard))
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("door_snapshot").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        val screen = compose.onRoot().getUnclippedBoundsInRoot()
        val gotIt = compose.onNodeWithTag("door_got_it").getUnclippedBoundsInRoot()
        val card = compose.onNodeWithTag("door_card").getUnclippedBoundsInRoot()
        assertThat((screen.bottom - screen.top).value).isWithin(0.5f).of(800f)
        assertThat(gotIt.bottom.value).isAtMost((screen.bottom - keyboard).value + 0.5f)
        assertThat((gotIt.bottom - gotIt.top).value).isAtLeast(48f)
        assertThat(card.top.value).isAtLeast(-0.5f)
        assertThat((card.right - card.left).value).isWithin(0.5f).of(747f)
        assertThat(card.left.value).isWithin(0.5f).of((1280f - 747f) / 2f)
    }
}
