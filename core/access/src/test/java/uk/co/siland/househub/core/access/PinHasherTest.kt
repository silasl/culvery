package uk.co.siland.househub.core.access

import com.google.common.truth.Truth.assertThat
import java.util.Base64
import org.junit.Assert.assertThrows
import org.junit.Test

class PinHasherTest {
    private val hasher = PinHasher()

    @Test
    fun acceptsExactlyFourAsciiDigits() {
        assertThat(hasher.isWellFormed("0000")).isTrue()
        assertThat(hasher.isWellFormed("2468")).isTrue()
    }

    @Test
    fun rejectsOtherLengthsLettersAndNonAsciiDigits() {
        listOf("", "123", "12345", "12a4", "12 4", "١٢٣٤").forEach {
            assertThat(hasher.isWellFormed(it)).isFalse()
            assertThrows(IllegalArgumentException::class.java) { hasher.validate(it) }
        }
    }

    @Test
    fun matchesOnlyTheSamePinAndSalt() {
        val salt = hasher.newSalt()
        val hash = hasher.hash("2468", salt)
        assertThat(hasher.matches("2468", salt, hash)).isTrue()
        assertThat(hasher.matches("2469", salt, hash)).isFalse()
        assertThat(hasher.matches("2468", hasher.newSalt(), hash)).isFalse()
    }

    @Test
    fun saltsAreRandomSixteenBytes() {
        val a = hasher.newSalt()
        assertThat(Base64.getDecoder().decode(a)).hasLength(16)
        assertThat(a).isNotEqualTo(hasher.newSalt())
    }
}
