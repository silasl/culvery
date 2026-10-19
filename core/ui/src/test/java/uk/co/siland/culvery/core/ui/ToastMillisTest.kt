package uk.co.siland.culvery.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ToastMillisTest {
    @Test
    fun aToastStaysSixSecondsPlusOneForEveryTenWords() {
        fun words(n: Int) = List(n) { "word" }.joinToString(" ")
        assertThat(toastMillis("")).isEqualTo(6_000L)
        assertThat(toastMillis(words(9))).isEqualTo(6_000L)
        assertThat(toastMillis(words(10))).isEqualTo(7_000L)
        assertThat(toastMillis(words(59))).isEqualTo(11_000L)
        assertThat(toastMillis(words(60))).isEqualTo(12_000L)
        assertThat(toastMillis(words(200))).isEqualTo(12_000L)
        assertThat(toastMillis("  two   words  ")).isEqualTo(6_000L)
    }
}
