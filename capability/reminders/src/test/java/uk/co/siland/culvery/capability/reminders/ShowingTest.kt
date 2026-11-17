package uk.co.siland.culvery.capability.reminders

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ShowingTest {
    private val day = Wall(ready = true, holiday = false, night = false, lit = true, quiet = false)

    @Test
    fun awayThenHolidayThenNight() {
        assertThat(heldBy(day)).isNull()
        val all = Wall(ready = false, holiday = true, night = true, lit = false, quiet = false)
        assertThat(heldBy(all)).isEqualTo(Held.AWAY)
        assertThat(heldBy(all.copy(ready = true))).isEqualTo(Held.HOLIDAY)
        assertThat(heldBy(all.copy(ready = true, holiday = false))).isEqualTo(Held.NIGHT)
    }

    @Test
    fun nightHoldsOnlyWhileTheWallIsDark() {
        assertThat(heldBy(day.copy(night = true, lit = false))).isEqualTo(Held.NIGHT)
        assertThat(heldBy(day.copy(night = true, lit = true))).isNull()
    }

    @Test
    fun anUnknownNightHoldsEvenOnALitWall() {
        assertThat(heldBy(day.copy(night = null, lit = true))).isEqualTo(Held.NIGHT)
    }

    @Test
    fun anUnknownHolidayHolds() {
        assertThat(heldBy(day.copy(holiday = null))).isEqualTo(Held.HOLIDAY)
    }

    @Test
    fun soundNeedsDayOutsideQuietHoursOnALitWall() {
        assertThat(soundAllowed(day)).isTrue()
        assertThat(soundAllowed(day.copy(night = true))).isFalse()
        assertThat(soundAllowed(day.copy(night = null))).isFalse()
        assertThat(soundAllowed(day.copy(quiet = true))).isFalse()
        assertThat(soundAllowed(day.copy(lit = false))).isFalse()
    }
}
