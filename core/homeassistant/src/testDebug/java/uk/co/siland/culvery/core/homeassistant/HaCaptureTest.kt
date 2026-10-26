package uk.co.siland.culvery.core.homeassistant

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog

/** 11 §8.2: off unless the log property is set; on, the shape only, of the entities asked for. */
@RunWith(AndroidJUnit4::class)
class HaCaptureTest {
    @After
    fun tearDown() {
        HaCapture.keep = emptySet()
    }

    /** Fails if the capture logs without `setprop log.tag.HaCapture DEBUG`. */
    @Test
    fun offByDefault() {
        ShadowLog.clear()
        ShadowLog.setLoggable("HaCapture", Log.INFO)
        HaCapture.keep = setOf("vacuum.hoover")
        HaCapture.log(HaCall.STATES, HaFixtures.STATES)
        assertThat(ShadowLog.getLogsForTag("HaCapture")).isEmpty()
    }

    /** Fails if a switched-on capture logs other than one redacted line, labelled with the call. */
    @Test
    fun onItLogsOneRedactedLine() {
        ShadowLog.clear()
        ShadowLog.setLoggable("HaCapture", Log.DEBUG)
        HaCapture.keep = setOf("vacuum.hoover", "climate.hall")
        HaCapture.log(HaCall.STATES, HaFixtures.STATES)
        val lines = ShadowLog.getLogsForTag("HaCapture")
        assertThat(lines).hasSize(1)
        val text = lines.single().msg
        assertThat(text).startsWith("states [0] ")
        assertThat(text).contains("\"vacuum.[1]\"")
        listOf("hoover", "Hall", "alex", "latitude", "192.168").forEach { assertThat(text).doesNotContain(it) }
    }

    /** Fails if an entity shows when none is watched. */
    @Test
    fun withNothingWatchedNoEntityShows() {
        ShadowLog.clear()
        ShadowLog.setLoggable("HaCapture", Log.DEBUG)
        HaCapture.log(HaCall.STATES, HaFixtures.STATES)
        val text = ShadowLog.getLogsForTag("HaCapture").joinToString("") { it.msg }
        assertThat(text).doesNotContain("vacuum")
        assertThat(text).doesNotContain("climate")
    }
}
