package uk.co.siland.culvery.capability.door

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** 5 §4.4: at most 640 px wide, as JPEG, and never taller than that either. Native graphics, for a real encoder. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BitmapSnapshotTest {
    @get:Rule val folder = TemporaryFolder()

    private fun savedSize(width: Int, height: Int): Pair<Int, Int> {
        val file = File(folder.root, "1.jpg").also { BitmapSnapshot(picture(width, height)).saveTo(it) }
        assertThat(file.readBytes().take(2)).containsExactly(0xFF.toByte(), 0xD8.toByte()).inOrder()
        val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(file.path, it) }
        return size.outWidth to size.outHeight
    }

    @Test
    fun aWidePictureIsScaledTo640Wide() = assertThat(savedSize(1280, 720)).isEqualTo(640 to 360)

    @Test
    fun aTallPictureFitsIn640Too() = assertThat(savedSize(480, 1280)).isEqualTo(240 to 640)

    /** The one fit the saved picture and any decoded-to-fit provider share: the longer side to 640, never enlarged. */
    @Test
    fun theFitScaleHoldsTheLongerSideTo640AndNeverEnlarges() {
        assertThat(fitScale(1280, 720)).isEqualTo(0.5f)
        assertThat(fitScale(480, 1280)).isEqualTo(0.5f)
        assertThat(fitScale(640, 640)).isEqualTo(1f)
        assertThat(fitScale(480, 270)).isEqualTo(1f)
    }

    @Test
    fun aSmallPictureKeepsItsSize() = assertThat(savedSize(480, 270)).isEqualTo(480 to 270)
}
