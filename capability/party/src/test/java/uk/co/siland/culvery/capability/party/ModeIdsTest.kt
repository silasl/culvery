package uk.co.siland.culvery.capability.party

import com.google.common.truth.Truth.assertThat
import kotlin.random.Random
import org.junit.Test
import uk.co.siland.culvery.core.house.HouseModes

class ModeIdsTest {
    /** 15b Ruling 7: fails if an id wouldn't pass the plug store's mode id rule, `^[a-z]+$`. */
    @Test
    fun idsFitThePlugStore() {
        val random = Random(15)
        repeat(1_000) {
            val id = newModeId(emptySet(), random)
            assertThat(id).matches("^[a-z]+$")
            assertThat(id).startsWith("mode")
            assertThat(id).hasLength(10)
        }
    }

    /** Fails if an id that is taken is given out again. */
    @Test
    fun aClashDrawsAgain() {
        // Six zeros twice ("modeaaaaaa" both times), then ones.
        val repeats = object : Random() {
            var draws = 0
            override fun nextBits(bitCount: Int) = error("not drawn")
            override fun nextInt(until: Int) = if (draws++ < 12) 0 else 1
        }

        val id = newModeId(setOf("modeaaaaaa"), repeats)

        assertThat(id).isEqualTo("modebbbbbb")
        assertThat(repeats.draws).isEqualTo(18)
    }

    @Test
    fun neverAReservedId() {
        val reserved = setOf(HouseModes.HOLIDAY, HouseModes.PARTY, HouseModes.STORED)
        val random = Random(7)
        repeat(1_000) { assertThat(newModeId(reserved, random)).isNotIn(reserved) }
    }
}
