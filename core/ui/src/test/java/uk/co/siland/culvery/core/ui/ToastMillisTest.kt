package uk.co.siland.culvery.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ToastMillisTest {
    @Test
    fun aToastLastsLongEnoughToReadAndNoLonger() {
        assertThat(toastMillis("Event added.", urgent = false)).isEqualTo(4_000L)
        assertThat(toastMillis("x".repeat(30), urgent = true)).isEqualTo(6_000L)
        assertThat(toastMillis("x".repeat(100), urgent = false)).isEqualTo(7_000L)
        assertThat(toastMillis("x".repeat(200), urgent = false)).isEqualTo(8_000L)
        assertThat(toastMillis("x".repeat(200), urgent = true)).isEqualTo(8_000L)
    }
}
