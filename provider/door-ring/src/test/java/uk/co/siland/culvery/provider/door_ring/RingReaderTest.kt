package uk.co.siland.culvery.provider.door_ring

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.capability.door.BitmapSnapshot
import uk.co.siland.culvery.capability.door.DoorEventSink
import uk.co.siland.culvery.capability.door.DoorKind
import uk.co.siland.culvery.capability.door.DoorNotice

private class RecordingSink(override var capturing: Boolean = false) : DoorEventSink {
    val heard = mutableListOf<Long>()
    val posted = mutableListOf<DoorNotice>()
    val bound = mutableListOf<Boolean>()
    override fun heard(atMillis: Long) { heard += atMillis }
    override fun post(notice: DoorNotice) { posted += notice }
    override fun listening(bound: Boolean) { this.bound += bound }
}

/** 5 §4.1–4.2, §6, §7: only Ring's package; heard for every notice; the outcome logged, never the words. */
@RunWith(AndroidJUnit4::class)
class RingReaderTest {
    private val sink = RecordingSink()
    private val reader = RingReader(sink)

    private fun post(notice: RingNotice, picture: Bitmap? = null) = RingPost(notice, picture, setOf("android.title", "android.text"))

    @Before
    fun clearLogs() = ShadowLog.clear()

    /** Unit tests run the debug variant, where the adb shell's fake rings count (5 §7). */
    @Test
    fun onlyRingAndTheShellsFakeRingsAreRead() {
        assertThat(reader.reads(RING_PACKAGE, null)).isTrue()
        assertThat(reader.reads(SHELL_PACKAGE, FAKE_RING_TAG)).isEqualTo(FakeRings.ENABLED)
        assertThat(reader.reads(SHELL_PACKAGE, "something else")).isFalse()
        assertThat(reader.reads("com.whatsapp", null)).isFalse()
    }

    @Test
    fun aRingIsPostedWithItsTimePictureAndHeard() {
        reader.read(post(RingFixtures.ring, Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)))
        val notice = sink.posted.single()
        assertThat(notice.copy(snapshot = null))
            .isEqualTo(DoorNotice(RingFixtures.ring.key, RingFixtures.ring.postTime, DoorKind.RING, "Front Door", whenMillis = RingFixtures.ring.whenMillis))
        assertThat(notice.snapshot).isInstanceOf(BitmapSnapshot::class.java)
        assertThat(sink.heard).containsExactly(RingFixtures.ring.postTime)
    }

    @Test
    fun motionAndAnUnknownAlertArePostedAsSuch() {
        reader.read(post(RingFixtures.motion))
        reader.read(post(RingFixtures.unknown))
        assertThat(sink.posted.map { it.kind to it.unrecognised }).containsExactly(DoorKind.MOTION to false, DoorKind.RING to true).inOrder()
    }

    /** Ignored notices still count as heard from Ring (5 §4.2). */
    @Test
    fun anIgnoredNoticeIsOnlyHeard() {
        reader.read(post(RingFixtures.appUpdate))
        assertThat(sink.posted).isEmpty()
        assertThat(sink.heard).hasSize(1)
    }

    @Test
    fun theLogSaysTheOutcomeNeverTheWords() {
        reader.read(post(RingFixtures.ring))
        assertThat(ShadowLog.getLogs().filter { it.tag == TAG_RING }.map { it.msg }).containsExactly("A Ring notice was read as a ring")
    }

    /** 5 §5.4, §8.3: while the switch is on, each notice's whole shape goes to the log; the one exception (§6). */
    @Test
    fun theCaptureWritesTheWholeNoticeOnlyWhileOn() {
        sink.capturing = true
        reader.read(post(RingFixtures.ring))
        val capture = ShadowLog.getLogs().single { it.tag == TAG_RING && it.msg.startsWith("Ring notice: ") }
        assertThat(capture.msg).contains("Front Door")
        assertThat(capture.msg).contains("extras=[android.text, android.title]")
    }

    /** A notice the adapter can't read is logged by its type and dropped; the listener lives on. */
    @Test
    fun aNoticeThatCantBeReadIsDropped() {
        reader.onPosted(RING_PACKAGE, null) { throw IllegalStateException("Front Door") }
        assertThat(sink.posted).isEmpty()
        val line = ShadowLog.getLogs().single { it.tag == TAG_RING }.msg
        assertThat(line).isEqualTo("Couldn't read a Ring notice (IllegalStateException)")
    }

    @Test
    fun anotherAppsNoticeIsNeverBuilt() {
        reader.onPosted("com.whatsapp", null) { error("built") }
        assertThat(ShadowLog.getLogs().filter { it.tag == TAG_RING }).isEmpty()
    }

    @Test
    fun bindingIsPassedOn() {
        reader.listening(false)
        reader.listening(true)
        assertThat(sink.bound).containsExactly(false, true).inOrder()
    }
}
