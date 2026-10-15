package uk.co.siland.culvery.provider.music_sonos

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog

/** 6 §8.2: off unless the log property is set; on, the shape only. */
@RunWith(AndroidJUnit4::class)
class SonosCaptureTest {
    private val answer = SonosFixtures.topology("192.168.1.20:1400", "192.168.1.21:1400", "192.168.1.22:1400")

    @Test
    fun offByDefault() {
        ShadowLog.clear()
        ShadowLog.setLoggable("SonosCapture", Log.INFO)
        SonosCapture.log("GetZoneGroupState", answer)
        assertThat(ShadowLog.getLogsForTag("SonosCapture")).isEmpty()
    }

    @Test
    fun onItLogsTheShapeWithoutNames() {
        ShadowLog.clear()
        ShadowLog.setLoggable("SonosCapture", Log.DEBUG)
        SonosCapture.log("GetZoneGroupState", answer)
        val text = ShadowLog.getLogsForTag("SonosCapture").joinToString("") { it.msg }
        assertThat(text).contains("GetZoneGroupState")
        assertThat(text).contains("ZoneName=\"[room]\"")
        listOf("Kitchen", "192.168.1.2").forEach { assertThat(text).doesNotContain(it) }
    }
}
