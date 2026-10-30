package uk.co.siland.culvery.capability.door.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.door.BitmapSnapshot
import uk.co.siland.culvery.capability.door.picture

/** A picture is read at about the size it is shown, never whole if larger: the tablet has little memory to spare. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SnapshotsTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun aPictureIsSampledDownWhileItIsRead() {
        val file = File(folder.root, "1.jpg").also { BitmapSnapshot(picture(640, 360)).saveTo(it) }
        assertThat(decodeSnapshot(file, maxPx = 160)?.width).isEqualTo(160)
        assertThat(decodeSnapshot(file, maxPx = 640)?.width).isEqualTo(640)
    }

    @Test
    fun aFileThatIsntAPictureIsNothing() {
        val file = File(folder.root, "2.jpg").also { it.writeBytes(byteArrayOf(1, 2, 3)) }
        assertThat(decodeSnapshot(file, maxPx = 160)).isNull()
    }

    /** A ring card shown just after a clear has deleted its picture: nothing to read is nothing drawn. */
    @Test
    fun aMissingFileIsNothing() {
        assertThat(decodeSnapshot(File(folder.root, "gone.jpg"), maxPx = 160)).isNull()
    }

    @Test
    fun aMissingFileDrawsNoPictureAndDoesNotCrash() {
        compose.setContent {
            Box(Modifier.size(100.dp).testTag("box")) {
                SnapshotImage(File(folder.root, "gone.jpg"), Modifier.size(100.dp).testTag("picture"))
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("box").assertExists()
        compose.onNodeWithTag("picture").assertDoesNotExist()
    }
}
