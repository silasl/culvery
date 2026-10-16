package uk.co.siland.culvery.provider.lights_hue

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog

/** 7 §8.2: off unless the log property is set; on, the shape only. */
@RunWith(AndroidJUnit4::class)
class HueCaptureTest {
    /** Fails if the capture logs without `setprop log.tag.HueCapture DEBUG`. */
    @Test
    fun offByDefault() {
        ShadowLog.clear()
        ShadowLog.setLoggable("HueCapture", Log.INFO)
        HueCapture.log("config", HueFixtures.CONFIG)
        assertThat(ShadowLog.getLogsForTag("HueCapture")).isEmpty()
    }

    /** Fails if a switched-on capture leaves out what was asked, or logs a name, the bridge id, an address or the key. */
    @Test
    fun onItLogsTheShapeWithoutNames() {
        ShadowLog.clear()
        ShadowLog.setLoggable("HueCapture", Log.DEBUG)
        HueCapture.log("config", HueFixtures.CONFIG)
        HueCapture.log("room", HueFixtures.ROOMS)
        val text = ShadowLog.getLogsForTag("HueCapture").joinToString("") { it.msg }
        assertThat(text).contains("config")
        assertThat(text).contains("\"bridgeid\":\"[bridge id]\"")
        assertThat(text).contains("\"name\":\"[name]\"")
        listOf("Lounge", "Kitchen", HueFixtures.BRIDGE_ID, HueFixtures.KEY, "192.168.1.30", "00:17:88").forEach {
            assertThat(text).doesNotContain(it)
        }
    }
}
