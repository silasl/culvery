package uk.co.siland.culvery.provider.door_ring

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import androidx.core.os.BundleCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

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
