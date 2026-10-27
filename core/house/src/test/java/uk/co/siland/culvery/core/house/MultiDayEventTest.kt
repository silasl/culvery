package uk.co.siland.culvery.core.house

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class MultiDayEventTest {
    /** 11 §6: fails if the title, or a day that may become the back date, can be read out of a log line. */
    @Test
    fun toStringHasNoTitleAndNoDates() {
        val text = MultiDayEvent("Cornwall", LocalDate.of(2026, 10, 24), LocalDate.of(2026, 11, 1)).toString()

        assertThat(text).doesNotContain("Cornwall")
        assertThat(text).doesNotContain("2026")
    }
}
