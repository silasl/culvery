package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpokenTimeTest {
    @Test
    fun aTimeIsReadAsAPersonWouldSayIt() {
        assertThat(spokenTime("09:00")).isEqualTo("09:00")
        assertThat(spokenTime("09:00–10:30")).isEqualTo("09:00 to 10:30")
        assertThat(spokenTime("22:00–")).isEqualTo("from 22:00")
        assertThat(spokenTime("until 01:00")).isEqualTo("until 01:00")
        assertThat(spokenTime(ALL_DAY_LABEL)).isEqualTo("all day")
    }
}
