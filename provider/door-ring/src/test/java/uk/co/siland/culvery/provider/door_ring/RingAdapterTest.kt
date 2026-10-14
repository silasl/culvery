package uk.co.siland.culvery.provider.door_ring

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Bundle
import androidx.core.os.BundleCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** 5 §3.2: a notification becomes a RingNotice, with its picture kept apart for the store. */
@RunWith(AndroidJUnit4::class)
class RingAdapterTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun builder() = Notification.Builder(context, "ding").setSmallIcon(android.R.drawable.ic_dialog_info).setWhen(42L)

    @Test
    fun theWordsChannelCategoryAndTimeAreRead() {
        val notification = builder()
            .setContentTitle("Front Door")
            .setContentText("Someone is at your Front Door")
            .setCategory(Notification.CATEGORY_CALL)
            .setStyle(Notification.BigTextStyle().bigText("Someone is at your Front Door."))
            .build()
        val post = ringPost(RING_PACKAGE, "k1", 5L, notification, context)
        assertThat(post.notice).isEqualTo(
            RingNotice(RING_PACKAGE, "k1", "ding", "call", "Front Door", "Someone is at your Front Door", "Someone is at your Front Door.", hasPicture = false, postTime = 5L, whenMillis = 42L),
        )
        assertThat(post.picture).isNull()
        assertThat(post.extraKeys).contains(Notification.EXTRA_TITLE)
    }

    @Test
    fun aBigPictureIsTheSnapshot() {
        val picture = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        val notification = builder().setContentTitle("Front Door").setStyle(Notification.BigPictureStyle().bigPicture(picture)).build()
        val post = ringPost(RING_PACKAGE, "k1", 5L, notification, context)
        assertThat(post.notice.hasPicture).isTrue()
        val extra = BundleCompat.getParcelable(notification.extras, Notification.EXTRA_PICTURE, Bitmap::class.java)
        assertThat(post.picture).isNotNull()
        assertThat(post.picture?.width).isEqualTo(extra?.width)
    }

    private fun iconPost(width: Int, height: Int): RingPost {
        val icon = Icon.createWithBitmap(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888))
        val notification = builder().setStyle(Notification.BigPictureStyle().bigPicture(icon)).build()
        return ringPost(RING_PACKAGE, "k1", 5L, notification, context)
    }

    /** Fails if an Icon picture is not read, or is left larger than 640 px on either side. */
    @Test
    @Config(sdk = [34])
    fun anIconPictureIsKeptWithinTheSnapshotSize() {
        val post = iconPost(1280, 720)
        assertThat(post.notice.hasPicture).isTrue()
        assertThat(post.picture!!.width).isAtMost(640)
        assertThat(post.picture!!.height).isAtMost(640)
    }

    private fun iconBundle(width: Int, height: Int) = Bundle().apply {
        putParcelable(Notification.EXTRA_PICTURE_ICON, Icon.createWithBitmap(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)))
    }

    /** Fails if pictureIcon leaves a 1280 × 720 icon unscaled or scales it to anything but 640 × 360. */
    @Test
    @Config(sdk = [34])
    fun pictureIconScalesALargeIconToFitTheCap() {
        val picture = pictureIcon(iconBundle(1280, 720), context)!!
        assertThat(picture.width to picture.height).isEqualTo(640 to 360)
    }

    /** Fails if a side that scales below 1 px is not clamped to 1, which makes toBitmap throw. */
    @Test
    @Config(sdk = [34])
    fun pictureIconKeepsEverySideAtLeastOnePixel() {
        val picture = pictureIcon(iconBundle(10_000, 1), context)!!
        assertThat(picture.width).isEqualTo(640)
        assertThat(picture.height).isEqualTo(1)
    }

    /** Fails if an extreme aspect ratio scales a side to 0 px and the read throws. */
    @Test
    @Config(sdk = [34])
    fun anExtremelyWideIconPictureStillReads() {
        val post = iconPost(10_000, 1)
        assertThat(post.picture).isNotNull()
        assertThat(post.picture!!.height).isAtLeast(1)
    }

    @Test
    fun ongoingAndBundleSummariesAreFlagged() {
        val ongoing = ringPost(RING_PACKAGE, "k1", 5L, builder().setOngoing(true).build(), context)
        val summary = ringPost(RING_PACKAGE, "k2", 5L, builder().setGroup("g").setGroupSummary(true).build(), context)
        assertThat(ongoing.notice.ongoing).isTrue()
        assertThat(summary.notice.groupSummary).isTrue()
    }

    /** The capture's line (5 §8.3): the whole notice and its extras' names. */
    @Test
    fun theCaptureLineHoldsTheNoticeAndItsExtrasNames() {
        val post = ringPost(RING_PACKAGE, "k1", 5L, builder().setContentTitle("Front Door").build(), context)
        assertThat(post.captureLine).startsWith("RingNotice(packageName=com.ringapp, key=k1,")
        assertThat(post.captureLine).contains("extras=[")
        assertThat(post.captureLine).contains(Notification.EXTRA_TITLE)
    }
}
