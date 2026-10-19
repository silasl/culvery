package uk.co.siland.culvery.core.plugin

import com.google.common.truth.Truth.assertThat
import java.time.LocalTime
import org.junit.Test

class DailyWindowTest {
    private fun t(text: String) = LocalTime.parse(text)
    private fun inside(window: Pair<String, String>, vararg times: String) =
        times.map { inDailyWindow(t(it), t(window.first), t(window.second)) }

    /** Fails if: a window inside one day is treated as crossing midnight, or its end is inclusive or its start exclusive. */
    @Test
    fun aWindowWithinTheDay() {
        assertThat(inside("13:00" to "15:00", "12:59", "13:00", "14:59", "15:00"))
            .containsExactly(false, true, true, false).inOrder()
    }

    /** Fails if: the window is read as same-day only, or its end is inclusive or its start exclusive. */
    @Test
    fun aWindowAcrossMidnight() {
        assertThat(inside("21:00" to "07:00", "20:59", "21:00", "23:59", "00:00", "06:59", "07:00"))
            .containsExactly(false, true, true, true, true, false).inOrder()
    }

    /** Fails if: equal times read as the whole day. */
    @Test
    fun equalTimesAreNeverInside() {
        assertThat(inside("21:00" to "21:00", "20:59", "21:00", "21:01", "00:00")).containsExactly(false, false, false, false)
    }
}
