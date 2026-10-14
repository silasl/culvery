package uk.co.siland.culvery.capability.door

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Test

/** 5 §4.1: From 21:00, Until 07:00 means 21:00–06:59, on the household's wall clock. */
class QuietHoursTest {
    private val from = LocalTime.of(21, 0)
    private val until = LocalTime.of(7, 0)
    private fun t(text: String) = LocalTime.parse(text)

    /** Fails if: the window is read as same-day only, or its end is inclusive or its start exclusive. */
    @Test
    fun acrossMidnightFromItsStartUntilJustBeforeItsEnd() {
        assertThat(listOf("20:59", "21:00", "23:59", "00:00", "06:59", "07:00").map { isQuiet(t(it), from, until) })
            .containsExactly(false, true, true, true, true, false).inOrder()
    }

    /** Fails if: a window inside one day is treated as crossing midnight. */
    @Test
    fun withinADay() {
        assertThat(listOf("12:59", "13:00", "14:59", "15:00").map { isQuiet(t(it), t("13:00"), t("15:00")) })
            .containsExactly(false, true, true, false).inOrder()
    }

    /** Nothing to be quiet between. Fails if: equal times read as the whole day. */
    @Test
    fun fromEqualToUntilIsNeverQuiet() = assertThat(isQuiet(t("21:00"), t("21:00"), t("21:00"))).isFalse()

    /** Fails if: quietAt ignores the switch. */
    @Test
    fun switchedOffIsNeverQuiet() {
        val london = ZoneId.of("Europe/London")
        assertThat(quietAt(Instant.parse("2026-10-14T22:00:00Z").toEpochMilli(), london, DoorPrefs(quietOn = false))).isFalse()
    }

    /** 01:30 happens twice on 25 October and is quiet both times; 07:00 BST ends it on 29 March. Fails if: the instant is read in UTC or the device zone. */
    @Test
    fun quietHoursFollowTheWallClockOnTheNightsTheClocksChange() {
        val london = ZoneId.of("Europe/London")
        fun at(utc: String) = quietAt(Instant.parse(utc).toEpochMilli(), london, DoorPrefs())
        assertThat(at("2026-10-25T00:30:00Z")).isTrue()
        assertThat(at("2026-10-25T01:30:00Z")).isTrue()
        assertThat(at("2026-10-25T06:59:00Z")).isTrue()
        assertThat(at("2026-10-25T07:00:00Z")).isFalse()
        assertThat(at("2026-03-28T20:59:00Z")).isFalse()
        assertThat(at("2026-03-28T21:00:00Z")).isTrue()
        assertThat(at("2026-03-29T05:59:00Z")).isTrue()
        assertThat(at("2026-03-29T06:00:00Z")).isFalse()
    }
}
